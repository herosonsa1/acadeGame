package com.jjalddalmark.minigamerullet.share

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.jjalddalmark.minigamerullet.model.RouletteEngine
import com.jjalddalmark.minigamerullet.model.RouletteSliceArc
import com.jjalddalmark.minigamerullet.model.RouletteSpecialEffect
import com.jjalddalmark.minigamerullet.model.RouletteSpinResult
import com.jjalddalmark.minigamerullet.model.RouletteSpinStage
import com.jjalddalmark.minigamerullet.model.RouletteVideoQuality
import com.jjalddalmark.minigamerullet.model.SpinTrajectorySample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 이미지·영상 공유 시 렌더링 테마 (만능 결정 룰렛+ 및 언덕 위 풍차 룰렛 목가적 풍차 테마 지원)
 */
enum class RouletteExportVisualStyle(
    val usesWindmillScene: Boolean,
    val headerBadgeText: String,
    val imageFilePrefix: String,
    val videoFilePrefix: String
) {
    STUDIO_DECISION(
        usesWindmillScene = false,
        headerBadgeText = "DECISION ROULETTE+",
        imageFilePrefix = "룰렛결과",
        videoFilePrefix = "룰렛영상"
    ),
    DECISION_WINDMILL(
        usesWindmillScene = true,
        headerBadgeText = "DECISION ROULETTE+",
        imageFilePrefix = "결정룰렛결과",
        videoFilePrefix = "결정룰렛영상"
    ),
    HILLSIDE_WINDMILL(
        usesWindmillScene = true,
        headerBadgeText = "HILLSIDE WINDMILL ROULETTE",
        imageFilePrefix = "풍차룰렛결과",
        videoFilePrefix = "풍차룰렛영상"
    )
}

/**
 * 룰렛 결과 고해상도 이미지(JPEG) 및 셋팅~회전~반전~최종결정 전 과정 동영상(MP4) 생성 엔진
 * 외부 FFmpeg 의존성 없이 Android 표준 Canvas + 하드웨어 MediaCodec(H.264/AAC) + MediaMuxer로 동작
 */
object RouletteMediaExporter {

    private const val SHARED_CACHE_SUBDIR = "shared_roulette"

    // 라이트 테마 고정 팔레트 (고해상도 가독성 및 디자인 원칙 준수)
    private const val COL_BG_CREAM = 0xFFFAF7F0.toInt()
    private const val COL_CARD_WHITE = 0xFFFFFFFF.toInt()
    private const val COL_WARM_IVORY = 0xFFF3EFE4.toInt()
    private const val COL_DIVIDER = 0xFFE0D9C8.toInt()
    private const val COL_INK_PRIMARY = 0xFF1F2721.toInt()
    private const val COL_INK_SECONDARY = 0xFF57635A.toInt()
    private const val COL_INK_MUTED = 0xFF838E85.toInt()
    private const val COL_MEADOW_GREEN = 0xFF3B6E4C.toInt()
    private const val COL_MEADOW_SOFT = 0xFFDCE9DF.toInt()
    private const val COL_PINE_DEEP = 0xFF284B34.toInt()
    private const val COL_TERRACOTTA = 0xFFB5633F.toInt()
    private const val COL_TERRACOTTA_SOFT = 0xFFF4E2D9.toInt()
    private const val COL_AMBER = 0xFFC48829.toInt()
    private const val COL_AMBER_SOFT = 0xFFF7ECD5.toInt()
    private const val COL_OAK_WOOD = 0xFF7C583B.toInt()

    private data class EncodedSample(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int
    )

    private data class VideoFrameState(
        val timeSec: Float,
        val wheelRotationDeg: Float,
        val flapperTiltDeg: Float,
        val stage: RouletteSpinStage,
        val isSlowMotion: Boolean,
        val isBlackOut: Boolean,
        val topStatusText: String,
        val revealProgress: Float, // 0f ~ 1f (최종 당첨 카드 페이드 인)
        val confettiTimeSec: Float
    )

    fun ensureSharedDir(context: Context): File {
        val dir = File(context.cacheDir, SHARED_CACHE_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * 오래된 임시 공유 파일 정리 (최근 12개 유지)
     */
    fun pruneOldSharedCache(context: Context) {
        runCatching {
            val dir = ensureSharedDir(context)
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > 12) {
                files.drop(12).forEach { it.delete() }
            }
        }
    }

    /**
     * 공유용 고해상도(`1080 x 1350`) 룰렛 결과 이미지(JPEG) 생성
     */
    suspend fun renderResultImageToCache(
        context: Context,
        result: RouletteSpinResult,
        visualStyle: RouletteExportVisualStyle = RouletteExportVisualStyle.DECISION_WINDMILL
    ): File = withContext(Dispatchers.Default) {
        pruneOldSharedCache(context)
        val width = 1080
        val height = 1350
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        drawFullFrameScene(
            canvas = canvas,
            w = width.toFloat(),
            h = height.toFloat(),
            result = result,
            state = VideoFrameState(
                timeSec = 0f,
                wheelRotationDeg = result.finalWheelRotationDeg,
                flapperTiltDeg = 0f,
                stage = RouletteSpinStage.FINAL_DECISION,
                isSlowMotion = false,
                isBlackOut = false,
                topStatusText = if (visualStyle.usesWindmillScene) {
                    "언덕 위 풍차 최종 당첨 카드"
                } else {
                    "최종 당첨 결과 카드"
                },
                revealProgress = 1f,
                confettiTimeSec = 0.65f
            ),
            isStaticResultCard = true,
            visualStyle = visualStyle
        )

        val safeTitle = sanitizeFileStem(result.presetSnapshot.title)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(result.finishedAtMillis))
        val prefix = visualStyle.imageFilePrefix
        val outFile = File(ensureSharedDir(context), "${prefix}_${safeTitle}_$stamp.jpg")

        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 91, fos)
                fos.flush()
            }
        }
        bitmap.recycle()
        outFile
    }

    /**
     * 룰렛 셋팅 -> 회전 과정 -> 클라이맥스·특수효과 -> 최종 당첨 결정 전 과정을 담은 고화질·저용량 MP4 인코딩
     */
    suspend fun exportSpinVideoToCache(
        context: Context,
        result: RouletteSpinResult,
        quality: RouletteVideoQuality = RouletteVideoQuality.HD_720P,
        includeSound: Boolean = true,
        visualStyle: RouletteExportVisualStyle = RouletteExportVisualStyle.STUDIO_DECISION,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {
        pruneOldSharedCache(context)
        onProgress(0.03f)

        val frames = buildBalancedVideoTimeline(result, quality.fps)
        val totalFrames = frames.size.coerceAtLeast(2)
        val totalDurationSec = totalFrames.toFloat() / quality.fps.toFloat()

        // 1단계: 하드웨어 H.264 비디오 트랙 인코딩 (Surface Canvas 렌더링)
        val (videoFormat, videoSamples) = encodeVideoTrack(
            result = result,
            frames = frames,
            quality = quality,
            visualStyle = visualStyle,
            onFrameProgress = { frac ->
                onProgress(0.05f + frac * 0.75f)
            }
        )

        coroutineContext.ensureActive()

        // 2단계: 동기화된 틱·특수효과·팡파레 오디오 PCM 합성 및 AAC 인코딩 (실패 시 비디오 단독 폴백)
        val audioResult: Pair<MediaFormat, List<EncodedSample>>? = if (includeSound) {
            runCatching {
                val pcm = synthesizeTimelineAudioPcm(result, frames, quality.fps, sampleRate = 44100)
                encodeAacAudioTrack(pcm, sampleRate = 44100, bitrate = 64_000)
            }.getOrNull()
        } else {
            null
        }

        onProgress(0.88f)
        coroutineContext.ensureActive()

        // 3단계: 2단계 버퍼드 MediaMuxer 멀티플렉싱 (포맷 확정 후 시작하므로 데드락 제로)
        val safeTitle = sanitizeFileStem(result.presetSnapshot.title)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(result.finishedAtMillis))
        val tag = if (quality == RouletteVideoQuality.HD_720P) "720p" else "540p"
        val prefix = visualStyle.videoFilePrefix
        val outFile = File(ensureSharedDir(context), "${prefix}_${safeTitle}_${tag}_$stamp.mp4")

        withContext(Dispatchers.IO) {
            if (outFile.exists()) outFile.delete()
            val muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            try {
                val videoTrackIndex = muxer.addTrack(videoFormat)
                val audioTrackIndex = if (audioResult != null && audioResult.second.isNotEmpty()) {
                    muxer.addTrack(audioResult.first)
                } else {
                    -1
                }
                muxer.start()

                val bufferInfo = MediaCodec.BufferInfo()
                for (sample in videoSamples) {
                    if (sample.data.isEmpty()) continue
                    val byteBuf = ByteBuffer.wrap(sample.data)
                    bufferInfo.set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                    muxer.writeSampleData(videoTrackIndex, byteBuf, bufferInfo)
                }

                if (audioTrackIndex >= 0 && audioResult != null) {
                    for (sample in audioResult.second) {
                        if (sample.data.isEmpty()) continue
                        val byteBuf = ByteBuffer.wrap(sample.data)
                        bufferInfo.set(0, sample.data.size, sample.presentationTimeUs, sample.flags)
                        muxer.writeSampleData(audioTrackIndex, byteBuf, bufferInfo)
                    }
                }

                muxer.stop()
            } finally {
                runCatching { muxer.release() }
            }
        }

        // totalDurationSec 참조 유지 (디버그 확인용 최소 길이 검증)
        if (totalDurationSec > 0f) {
            onProgress(1.0f)
        }
        outFile
    }

    /**
     * 생성된 결과 이미지 또는 영상을 휴대폰 갤러리(`Pictures/MinigameRullet` 또는 `Movies/MinigameRullet`)에 영구 저장
     */
    suspend fun saveMediaToGallery(
        context: Context,
        sourceFile: File,
        isVideo: Boolean
    ): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
            val collection = if (isVideo) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            }
            val relPath = if (isVideo) {
                "${Environment.DIRECTORY_MOVIES}/MinigameRullet"
            } else {
                "${Environment.DIRECTORY_PICTURES}/MinigameRullet"
            }

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, sourceFile.name)
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }

            val itemUri = resolver.insert(collection, values) ?: return@runCatching null
            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
            }
            itemUri
        }.getOrNull()
    }

    /**
     * 셋팅 프리롤(`0.90초`) + 회전 및 특수효과 전 구간(장시간 스핀 시 초반부만 스마트 압축) + 최종 결정 홀드(`1.65초`) 타임라인 생성
     */
    private fun buildBalancedVideoTimeline(
        result: RouletteSpinResult,
        fps: Int
    ): List<VideoFrameState> {
        val rawSamples = result.trajectory.ifEmpty {
            listOf(
                SpinTrajectorySample(
                    elapsedSec = 0f,
                    wheelRotationDeg = result.finalWheelRotationDeg - 1440f,
                    flapperTiltDeg = 0f,
                    stage = RouletteSpinStage.SETUP_READY,
                    statusBanner = "룰렛 셋팅 완료"
                ),
                SpinTrajectorySample(
                    elapsedSec = 4.5f,
                    wheelRotationDeg = result.finalWheelRotationDeg,
                    flapperTiltDeg = 0f,
                    stage = RouletteSpinStage.FINAL_DECISION,
                    statusBanner = "최종 당첨 결정!"
                )
            )
        }

        val out = ArrayList<VideoFrameState>(fps * 12)
        val preRollSec = 0.90f
        val preRollFrames = (preRollSec * fps).roundToInt().coerceAtLeast(15)
        val initialRot = rawSamples.first().wheelRotationDeg

        // 구간 A: 초기 셋팅 화면 공개 (0.90초)
        for (i in 0 until preRollFrames) {
            val t = i.toFloat() / fps.toFloat()
            out.add(
                VideoFrameState(
                    timeSec = t,
                    wheelRotationDeg = initialRot,
                    flapperTiltDeg = 0f,
                    stage = RouletteSpinStage.SETUP_READY,
                    isSlowMotion = false,
                    isBlackOut = false,
                    topStatusText = "룰렛 셋팅 완료 · 추첨 시작",
                    revealProgress = 0f,
                    confettiTimeSec = 0f
                )
            )
        }

        // 구간 B: 회전 ~ 클라이맥스 ~ 특수효과 구간
        // 영상 용량이 커지지 않도록 회전 구간이 8.6초를 넘으면 초반 고속 구간만 가속하고 후반 4.4초(감속·반전)는 1배속 유지
        val rawDuration = rawSamples.last().elapsedSec.coerceAtLeast(0.5f)
        val preserveTailSec = min(4.4f, rawDuration * 0.55f)
        val rawHeadSec = (rawDuration - preserveTailSec).coerceAtLeast(0f)
        val compressedHeadSec = min(rawHeadSec, 4.2f)
        val renderedSpinSec = compressedHeadSec + preserveTailSec
        val spinFrames = (renderedSpinSec * fps).roundToInt().coerceAtLeast(fps * 2)

        var prevSliceIdx = RouletteEngine.resolveSliceIndexAtRotation(result.arcs, initialRot)
        var simulatedFlapper = 0f

        for (i in 0..spinFrames) {
            val renderT = (i.toFloat() / spinFrames.toFloat()) * renderedSpinSec
            val mappedSourceSec = if (renderT <= compressedHeadSec && compressedHeadSec > 0.001f) {
                (renderT / compressedHeadSec) * rawHeadSec
            } else {
                rawHeadSec + (renderT - compressedHeadSec)
            }.coerceIn(0f, rawDuration)

            val interp = interpolateTrajectoryAt(rawSamples, mappedSourceSec)
            val sliceNow = RouletteEngine.resolveSliceIndexAtRotation(result.arcs, interp.wheelRotationDeg)
            simulatedFlapper = if (sliceNow != prevSliceIdx) {
                prevSliceIdx = sliceNow
                -25f
            } else {
                (simulatedFlapper * 0.72f)
            }

            out.add(
                VideoFrameState(
                    timeSec = preRollSec + renderT,
                    wheelRotationDeg = interp.wheelRotationDeg,
                    flapperTiltDeg = if (abs(interp.flapperTiltDeg) > abs(simulatedFlapper)) {
                        interp.flapperTiltDeg
                    } else {
                        simulatedFlapper
                    },
                    stage = interp.stage,
                    isSlowMotion = interp.isSlowMotion,
                    isBlackOut = interp.isBlackOut,
                    topStatusText = interp.statusBanner.ifBlank { "돌아가는 중..." },
                    revealProgress = 0f,
                    confettiTimeSec = 0f
                )
            )
        }

        // 구간 C: 최종 당첨 결정 & 꽃잎 축하 홀드 (1.65초)
        val postRollSec = 1.65f
        val postRollFrames = (postRollSec * fps).roundToInt().coerceAtLeast(24)
        val baseTime = preRollSec + renderedSpinSec
        for (i in 1..postRollFrames) {
            val postT = i.toFloat() / fps.toFloat()
            val reveal = (postT / 0.34f).coerceIn(0f, 1f)
            out.add(
                VideoFrameState(
                    timeSec = baseTime + postT,
                    wheelRotationDeg = result.finalWheelRotationDeg,
                    flapperTiltDeg = 0f,
                    stage = RouletteSpinStage.FINAL_DECISION,
                    isSlowMotion = false,
                    isBlackOut = false,
                    topStatusText = "최종 당첨 결정 완료!",
                    revealProgress = reveal,
                    confettiTimeSec = postT
                )
            )
        }

        return out
    }

    private fun interpolateTrajectoryAt(
        samples: List<SpinTrajectorySample>,
        targetSec: Float
    ): SpinTrajectorySample {
        if (samples.size == 1 || targetSec <= samples.first().elapsedSec) return samples.first()
        if (targetSec >= samples.last().elapsedSec) return samples.last()

        var lo = 0
        var hi = samples.lastIndex
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val midVal = samples[mid].elapsedSec
            if (midVal < targetSec) {
                lo = mid + 1
            } else if (midVal > targetSec) {
                hi = mid - 1
            } else {
                return samples[mid]
            }
        }
        val idx0 = (lo - 1).coerceIn(0, samples.lastIndex)
        val idx1 = lo.coerceIn(0, samples.lastIndex)
        val s0 = samples[idx0]
        val s1 = samples[idx1]
        val span = (s1.elapsedSec - s0.elapsedSec)
        if (span <= 0.0001f) return s1
        val frac = ((targetSec - s0.elapsedSec) / span).coerceIn(0f, 1f)
        return s1.copy(
            elapsedSec = targetSec,
            wheelRotationDeg = s0.wheelRotationDeg + (s1.wheelRotationDeg - s0.wheelRotationDeg) * frac,
            flapperTiltDeg = s0.flapperTiltDeg + (s1.flapperTiltDeg - s0.flapperTiltDeg) * frac
        )
    }

    private suspend fun encodeVideoTrack(
        result: RouletteSpinResult,
        frames: List<VideoFrameState>,
        quality: RouletteVideoQuality,
        visualStyle: RouletteExportVisualStyle,
        onFrameProgress: (Float) -> Unit
    ): Pair<MediaFormat, List<EncodedSample>> {
        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            quality.width,
            quality.height
        ).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
            )
            setInteger(MediaFormat.KEY_BIT_RATE, quality.videoBitrate)
            setInteger(MediaFormat.KEY_FRAME_RATE, quality.fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var inputSurface: android.view.Surface? = null
        var outputFormat: MediaFormat? = null
        val samples = ArrayList<EncodedSample>(frames.size + 8)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val surface = encoder.createInputSurface()
            inputSurface = surface
            encoder.start()

            fun drainEncoder(endOfStream: Boolean) {
                if (endOfStream) {
                    encoder.signalEndOfInputStream()
                }
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
                    if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        if (!endOfStream) break
                    } else if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        outputFormat = encoder.outputFormat
                    } else if (outIdx >= 0) {
                        val encodedBuf = encoder.getOutputBuffer(outIdx)
                        if (encodedBuf != null) {
                            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                            if (!isConfig && bufferInfo.size > 0) {
                                encodedBuf.position(bufferInfo.offset)
                                encodedBuf.limit(bufferInfo.offset + bufferInfo.size)
                                val bytes = ByteArray(bufferInfo.size)
                                encodedBuf.get(bytes)
                                samples.add(
                                    EncodedSample(
                                        data = bytes,
                                        presentationTimeUs = bufferInfo.presentationTimeUs,
                                        flags = bufferInfo.flags
                                    )
                                )
                            }
                        }
                        encoder.releaseOutputBuffer(outIdx, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            break
                        }
                    }
                }
            }

            val total = frames.size.coerceAtLeast(1)
            frames.forEachIndexed { idx, frameState ->
                coroutineContext.ensureActive()
                val canvas = surface.lockCanvas(null)
                try {
                    drawFullFrameScene(
                        canvas = canvas,
                        w = quality.width.toFloat(),
                        h = quality.height.toFloat(),
                        result = result,
                        state = frameState,
                        isStaticResultCard = false,
                        visualStyle = visualStyle
                    )
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }

                // 일부 인코더가 입력 타임스탬프 대신 시스템 타임스탬프를 쓰는 경우를 보정하기 위해 사후 정규화도 함께 적용
                drainEncoder(endOfStream = false)
                if (idx % 6 == 0 || idx == frames.lastIndex) {
                    onFrameProgress((idx + 1).toFloat() / total.toFloat())
                }
            }

            drainEncoder(endOfStream = true)
        } finally {
            runCatching { inputSurface?.release() }
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }

        val finalFormat = requireNotNull(outputFormat) { "비디오 인코더 출력 포맷을 획득하지 못했습니다." }

        // Surface 인코더는 시스템 나노초를 PTS로 기록하므로 0us부터 정확히 1/fps 간격으로 정규화하여 재생 속도 일치 보장
        val frameStepUs = 1_000_000L / quality.fps.toLong()
        val normalizedSamples = samples.mapIndexed { idx, sample ->
            sample.copy(presentationTimeUs = idx * frameStepUs)
        }
        return finalFormat to normalizedSamples
    }

    /**
     * 비디오 프레임 시퀀스와 정확히 일치하는 틱 사운드 + 반전 스윕 + 축하 팡파레 오디오 PCM 합성
     */
    private fun synthesizeTimelineAudioPcm(
        result: RouletteSpinResult,
        frames: List<VideoFrameState>,
        fps: Int,
        sampleRate: Int
    ): ShortArray {
        val totalSec = frames.size.toDouble() / fps.toDouble()
        val totalSamples = (totalSec * sampleRate).toInt().coerceAtLeast(sampleRate)
        val mix = FloatArray(totalSamples)

        // 1. 경쾌한 황동/우드 핀 클릭 파형 (55ms)
        val pegClickLow = ShortArray((sampleRate * 0.055).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 68.0)
            val v = (sin(2.0 * PI * 780.0 * t) * 0.68 + sin(2.0 * PI * 1890.0 * t) * 0.32 * exp(-t * 95.0)) * env * 0.58
            (v * Short.MAX_VALUE).toInt().toShort()
        }
        val pegClickHigh = ShortArray((sampleRate * 0.055).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 68.0)
            val v = (sin(2.0 * PI * 960.0 * t) * 0.68 + sin(2.0 * PI * 2240.0 * t) * 0.32 * exp(-t * 95.0)) * env * 0.62
            (v * Short.MAX_VALUE).toInt().toShort()
        }

        // 2. 특수효과 반전 스윕 파형 (0.55초)
        val twistSweep = ShortArray((sampleRate * 0.55).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val u = (t / 0.55).coerceIn(0.0, 1.0)
            val freq = 420.0 + sin(PI * u) * 480.0
            val env = sin(PI * u)
            val v = sin(2.0 * PI * freq * t) * env * 0.52
            (v * Short.MAX_VALUE).toInt().toShort()
        }

        // 3. 최종 결정 팡파레 코드 (1.35초)
        val fanfareLen = (sampleRate * 1.35).toInt()
        val fanfareChord = listOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
        val fanfarePcm = ShortArray(fanfareLen) { i ->
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            fanfareChord.forEachIndexed { idx, freq ->
                val onset = idx * 0.072
                if (t >= onset) {
                    val dt = t - onset
                    val env = (1.0 - exp(-dt * 120.0)) * exp(-dt * 2.6)
                    sum += (sin(2.0 * PI * freq * dt) * 0.72 + sin(2.0 * PI * freq * 2.0 * dt) * 0.22) * env * 0.22
                }
            }
            (sum.coerceIn(-0.92, 0.92) * Short.MAX_VALUE).toInt().toShort()
        }

        fun overlayPcm(startSample: Int, clip: ShortArray, gain: Float = 1f) {
            if (startSample >= mix.size) return
            val safeStart = startSample.coerceAtLeast(0)
            val end = min(mix.size, safeStart + clip.size)
            for (s in safeStart until end) {
                mix[s] += (clip[s - safeStart].toFloat() / Short.MAX_VALUE.toFloat()) * gain
            }
        }

        // 셋팅 시작 차임벨 (0.05초 지점)
        overlayPcm((0.05 * sampleRate).toInt(), pegClickHigh, 0.7f)
        overlayPcm((0.14 * sampleRate).toInt(), pegClickLow, 0.6f)

        var prevSlice = if (frames.isNotEmpty()) {
            RouletteEngine.resolveSliceIndexAtRotation(result.arcs, frames.first().wheelRotationDeg)
        } else 0
        var lastTickSample = -sampleRate
        var twistSoundTriggered = false
        var finalSoundTriggered = false

        frames.forEachIndexed { frameIdx, frame ->
            val frameSamplePos = ((frameIdx.toDouble() / fps.toDouble()) * sampleRate).toInt()
            val curSlice = RouletteEngine.resolveSliceIndexAtRotation(result.arcs, frame.wheelRotationDeg)

            if (frame.stage != RouletteSpinStage.SETUP_READY &&
                frame.stage != RouletteSpinStage.FINAL_DECISION &&
                curSlice != prevSlice
            ) {
                prevSlice = curSlice
                // 너무 촘촘한 틱 중첩 방지 (최소 34ms 간격)
                if (frameSamplePos - lastTickSample >= (sampleRate * 0.034).toInt()) {
                    overlayPcm(
                        frameSamplePos,
                        if (frame.isSlowMotion) pegClickHigh else pegClickLow,
                        if (frame.isSlowMotion) 0.95f else 0.72f
                    )
                    lastTickSample = frameSamplePos
                }
            }

            if (!twistSoundTriggered && frame.stage == RouletteSpinStage.SPECIAL_TWIST) {
                twistSoundTriggered = true
                overlayPcm(frameSamplePos, twistSweep, 0.88f)
            }

            if (!finalSoundTriggered && frame.stage == RouletteSpinStage.FINAL_DECISION) {
                finalSoundTriggered = true
                overlayPcm(frameSamplePos, fanfarePcm, 0.95f)
            }
        }

        return ShortArray(totalSamples) { i ->
            (mix[i].coerceIn(-0.92f, 0.92f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun encodeAacAudioTrack(
        pcmSamples: ShortArray,
        sampleRate: Int,
        bitrate: Int
    ): Pair<MediaFormat, List<EncodedSample>> {
        val format = MediaFormat.createAudioFormat(
            MediaFormat.MIMETYPE_AUDIO_AAC,
            sampleRate,
            1
        ).apply {
            setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
            setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
        var outputFormat: MediaFormat? = null
        val samples = ArrayList<EncodedSample>(256)
        val bufferInfo = MediaCodec.BufferInfo()

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            var sampleCursor = 0
            var inputDone = false
            var outputDone = false

            while (!outputDone) {
                if (!inputDone) {
                    val inIdx = encoder.dequeueInputBuffer(5_000L)
                    if (inIdx >= 0) {
                        val inBuf = encoder.getInputBuffer(inIdx)!!
                        inBuf.clear()
                        val remainingSamples = pcmSamples.size - sampleCursor
                        if (remainingSamples <= 0) {
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate.toLong()
                            encoder.queueInputBuffer(
                                inIdx,
                                0,
                                0,
                                ptsUs,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            val maxShorts = (inBuf.remaining() / 2).coerceAtMost(1024)
                            val chunkShorts = min(remainingSamples, maxShorts)
                            for (i in 0 until chunkShorts) {
                                inBuf.putShort(pcmSamples[sampleCursor + i])
                            }
                            val ptsUs = (sampleCursor.toLong() * 1_000_000L) / sampleRate.toLong()
                            encoder.queueInputBuffer(inIdx, 0, chunkShorts * 2, ptsUs, 0)
                            sampleCursor += chunkShorts
                        }
                    }
                }

                val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 5_000L)
                if (outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    outputFormat = encoder.outputFormat
                } else if (outIdx >= 0) {
                    val outBuf = encoder.getOutputBuffer(outIdx)
                    val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
                    if (outBuf != null && !isConfig && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val bytes = ByteArray(bufferInfo.size)
                        outBuf.get(bytes)
                        samples.add(
                            EncodedSample(
                                data = bytes,
                                presentationTimeUs = bufferInfo.presentationTimeUs,
                                flags = bufferInfo.flags
                            )
                        )
                    }
                    encoder.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }
        } finally {
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
        }

        val finalFormat = requireNotNull(outputFormat) { "AAC 오디오 포맷을 획득하지 못했습니다." }
        return finalFormat to samples
    }

    /**
     * 결과 이미지(1080x1350) 및 동영상 프레임(720x1280 / 540x960) 공용 초고선명 벡터 캔버스 렌더러
     */
    private fun drawFullFrameScene(
        canvas: Canvas,
        w: Float,
        h: Float,
        result: RouletteSpinResult,
        state: VideoFrameState,
        isStaticResultCard: Boolean,
        visualStyle: RouletteExportVisualStyle = RouletteExportVisualStyle.STUDIO_DECISION
    ) {
        val u = w / 720f // 해상도 독립 스케일 계수 (720px 기준 1.0, 1080px 기준 1.5, 540px 기준 0.75)
        val preset = result.presetSnapshot
        val arcs = result.arcs
        val isWindmill = visualStyle.usesWindmillScene

        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // 1. 전체 크림 화이트 배경
        canvas.drawColor(COL_BG_CREAM)

        // 외곽 메인 프레임 카드
        val outerPad = 24f * u
        val outerRect = RectF(outerPad, outerPad, w - outerPad, h - outerPad)
        fillPaint.color = COL_CARD_WHITE
        canvas.drawRoundRect(outerRect, 28f * u, 28f * u, fillPaint)
        strokePaint.color = COL_DIVIDER
        strokePaint.strokeWidth = 2f * u
        canvas.drawRoundRect(outerRect, 28f * u, 28f * u, strokePaint)

        // 2. 상단 헤더 영역 (룰렛 제목, 상태 배지, 일시)
        val headerLeft = outerPad + 28f * u
        val headerRight = w - outerPad - 28f * u
        var cursorY = outerPad + 36f * u

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 17f * u
        textPaint.color = if (isWindmill) COL_TERRACOTTA else COL_MEADOW_GREEN
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(
            visualStyle.headerBadgeText,
            headerLeft,
            cursorY,
            textPaint
        )

        val dateText = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA)
            .format(Date(result.finishedAtMillis))
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        textPaint.textSize = 16f * u
        textPaint.color = COL_INK_MUTED
        textPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(dateText, headerRight, cursorY, textPaint)

        cursorY += 38f * u
        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 31f * u
        textPaint.color = COL_INK_PRIMARY
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText(ellipsizeText(preset.title, textPaint, headerRight - headerLeft), headerLeft, cursorY, textPaint)

        cursorY += 20f * u
        // 상태 안내 배너 칩
        val statusRect = RectF(headerLeft, cursorY, headerRight, cursorY + 46f * u)
        val isTwist = state.stage == RouletteSpinStage.SPECIAL_TWIST
        val isFinal = state.stage == RouletteSpinStage.FINAL_DECISION
        fillPaint.color = when {
            isTwist -> COL_TERRACOTTA_SOFT
            state.isSlowMotion -> COL_AMBER_SOFT
            isFinal -> COL_MEADOW_SOFT
            else -> COL_WARM_IVORY
        }
        canvas.drawRoundRect(statusRect, 14f * u, 14f * u, fillPaint)
        strokePaint.color = when {
            isTwist -> COL_TERRACOTTA
            state.isSlowMotion -> COL_AMBER
            isFinal -> COL_MEADOW_GREEN
            else -> COL_DIVIDER
        }
        strokePaint.strokeWidth = 1.8f * u
        canvas.drawRoundRect(statusRect, 14f * u, 14f * u, strokePaint)

        val currentSliceIdx = RouletteEngine.resolveSliceIndexAtRotation(arcs, state.wheelRotationDeg)
        val currentSliceLabel = arcs.getOrNull(currentSliceIdx)?.item?.label.orEmpty()
        val liveMask = preset.mysteryMaskOnWheel && !isFinal && !isStaticResultCard
        val bannerDisplay = when {
            isStaticResultCard -> "항목 ${arcs.size}개 중 최종 당첨 결과"
            state.stage == RouletteSpinStage.SETUP_READY ->
                "${state.topStatusText}  ·  후보 ${arcs.size}개"
            isFinal ->
                "최종 결정: ${result.winnerItem.label}"
            state.isBlackOut ->
                state.topStatusText
            else -> {
                val pointerReadout = if (liveMask) "?" else currentSliceLabel
                "${state.topStatusText}  ·  현재 포인터: $pointerReadout"
            }
        }

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 18.5f * u
        textPaint.color = when {
            isTwist -> COL_TERRACOTTA
            state.isSlowMotion -> COL_OAK_WOOD
            isFinal -> COL_PINE_DEEP
            else -> COL_INK_PRIMARY
        }
        textPaint.textAlign = Paint.Align.CENTER
        val statusTextY = statusRect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f
        canvas.drawText(
            ellipsizeText(bannerDisplay, textPaint, statusRect.width() - 28f * u),
            statusRect.centerX(),
            statusTextY,
            textPaint
        )

        // 3. 중앙 룰렛 원판 렌더링 (풍차 테마 시 목가적 언덕 + 돌담 원목 탑 + 회전 풍차 날개 4장 포함)
        val wheelAreaTop = statusRect.bottom + 20f * u
        val bottomCardHeight = if (isStaticResultCard) 320f * u else 310f * u
        val wheelAreaBottom = h - outerPad - bottomCardHeight - 18f * u
        val wheelCenterX = w * 0.5f
        val wheelCenterY = if (isWindmill) {
            (wheelAreaTop + wheelAreaBottom) * 0.5f - 8f * u
        } else {
            (wheelAreaTop + wheelAreaBottom) * 0.5f + 10f * u
        }
        val maxWheelR = if (isWindmill) {
            min((w - outerPad * 2f) * 0.355f, (wheelAreaBottom - wheelAreaTop) * 0.39f)
                .coerceAtLeast(124f * u)
        } else {
            min((w - outerPad * 2f) * 0.42f, (wheelAreaBottom - wheelAreaTop) * 0.47f)
                .coerceAtLeast(140f * u)
        }

        if (isWindmill) {
            val stageRect = RectF(headerLeft, wheelAreaTop, headerRight, wheelAreaBottom)
            val clipPath = Path().apply {
                addRoundRect(stageRect, 20f * u, 20f * u, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(clipPath)

            // 맑은 하늘 배경과 포근한 햇살 원반
            fillPaint.color = 0xFFF2F7F6.toInt()
            canvas.drawRect(stageRect, fillPaint)
            fillPaint.color = 0xFFF9EED2.toInt()
            canvas.drawCircle(stageRect.right - 68f * u, stageRect.top + 56f * u, 42f * u, fillPaint)

            // 뒷편 완만한 초원 능선
            val backHill = Path().apply {
                moveTo(stageRect.left, stageRect.bottom - 95f * u)
                quadTo(
                    stageRect.left + stageRect.width() * 0.32f,
                    stageRect.bottom - 155f * u,
                    stageRect.right,
                    stageRect.bottom - 82f * u
                )
                lineTo(stageRect.right, stageRect.bottom)
                lineTo(stageRect.left, stageRect.bottom)
                close()
            }
            fillPaint.color = 0xFFDCEAD8.toInt()
            canvas.drawPath(backHill, fillPaint)

            // 앞쪽 언덕 능선
            val frontHill = Path().apply {
                moveTo(stageRect.left, stageRect.bottom - 56f * u)
                quadTo(
                    stageRect.centerX(),
                    stageRect.bottom - 118f * u,
                    stageRect.right,
                    stageRect.bottom - 48f * u
                )
                lineTo(stageRect.right, stageRect.bottom)
                lineTo(stageRect.left, stageRect.bottom)
                close()
            }
            fillPaint.color = 0xFFC7DEC2.toInt()
            canvas.drawPath(frontHill, fillPaint)

            // 돌담 & 참나무 A자형 풍차 탑 기단
            val towerTopY = wheelCenterY + maxWheelR * 0.08f
            val towerBotY = stageRect.bottom + 4f * u
            val towerTopHalf = maxWheelR * 0.30f
            val towerBotHalf = maxWheelR * 0.56f
            val towerPath = Path().apply {
                moveTo(wheelCenterX - towerTopHalf, towerTopY)
                lineTo(wheelCenterX + towerTopHalf, towerTopY)
                lineTo(wheelCenterX + towerBotHalf, towerBotY)
                lineTo(wheelCenterX - towerBotHalf, towerBotY)
                close()
            }
            fillPaint.color = 0xFFEBE2CE.toInt()
            canvas.drawPath(towerPath, fillPaint)
            strokePaint.color = COL_OAK_WOOD
            strokePaint.strokeWidth = 4.2f * u
            canvas.drawPath(towerPath, strokePaint)

            // 원목 X자 보강 기둥
            strokePaint.strokeWidth = 3f * u
            canvas.drawLine(
                wheelCenterX - towerTopHalf * 0.85f,
                wheelCenterY + maxWheelR * 0.55f,
                wheelCenterX + towerBotHalf * 0.82f,
                towerBotY - 12f * u,
                strokePaint
            )
            canvas.drawLine(
                wheelCenterX + towerTopHalf * 0.85f,
                wheelCenterY + maxWheelR * 0.55f,
                wheelCenterX - towerBotHalf * 0.82f,
                towerBotY - 12f * u,
                strokePaint
            )

            // 풍차 오두막 아치형 원목 문
            val doorW = maxWheelR * 0.30f
            val doorH = maxWheelR * 0.40f
            val doorRect = RectF(
                wheelCenterX - doorW * 0.5f,
                stageRect.bottom - doorH,
                wheelCenterX + doorW * 0.5f,
                stageRect.bottom + 8f * u
            )
            fillPaint.color = 0xFF5D4028.toInt()
            canvas.drawRoundRect(doorRect, doorW * 0.5f, doorW * 0.5f, fillPaint)
            strokePaint.color = 0xFFEFE5D2.toInt()
            strokePaint.strokeWidth = 2f * u
            canvas.drawRoundRect(doorRect, doorW * 0.5f, doorW * 0.5f, strokePaint)
            fillPaint.color = COL_AMBER
            canvas.drawCircle(doorRect.right - 8f * u, doorRect.centerY() + 4f * u, 3f * u, fillPaint)

            // 원판과 함께 회전하는 4장의 원목 격자 풍차 날개
            for (sailIdx in 0 until 4) {
                val sailDeg = state.wheelRotationDeg + sailIdx * 90f + 45f
                canvas.save()
                canvas.translate(wheelCenterX, wheelCenterY)
                canvas.rotate(sailDeg)

                // 중심 날개 기둥
                strokePaint.color = COL_OAK_WOOD
                strokePaint.strokeWidth = 6.2f * u
                canvas.drawLine(maxWheelR * 0.78f, 0f, maxWheelR * 1.22f, 0f, strokePaint)

                // 격자 돛 프레임
                val bladeRect = RectF(
                    maxWheelR * 0.90f,
                    -15f * u,
                    maxWheelR * 1.20f,
                    15f * u
                )
                fillPaint.color = 0xFFFBF6EA.toInt()
                canvas.drawRoundRect(bladeRect, 4f * u, 4f * u, fillPaint)
                strokePaint.color = COL_OAK_WOOD
                strokePaint.strokeWidth = 2.4f * u
                canvas.drawRoundRect(bladeRect, 4f * u, 4f * u, strokePaint)

                // 격자 가로살 3개
                strokePaint.strokeWidth = 1.6f * u
                val stepR = bladeRect.width() / 4f
                for (rIdx in 1..3) {
                    val rx = bladeRect.left + stepR * rIdx
                    canvas.drawLine(rx, bladeRect.top, rx, bladeRect.bottom, strokePaint)
                }
                canvas.restore()
            }

            canvas.restore()
            strokePaint.color = COL_DIVIDER
            strokePaint.strokeWidth = 1.6f * u
            canvas.drawRoundRect(stageRect, 20f * u, 20f * u, strokePaint)
        }

        // 슬로우모션 포커스 점선 외곽 링
        if (state.isSlowMotion && !isFinal) {
            strokePaint.color = COL_AMBER
            strokePaint.strokeWidth = 3f * u
            strokePaint.pathEffect = DashPathEffect(floatArrayOf(12f * u, 8f * u), state.timeSec * 40f * u)
            canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR + 18f * u, strokePaint)
            strokePaint.pathEffect = null
        }

        // 외곽 원목 테두리 베젤 (풍차 테마 시 이중 참나무·자작나무 테와 황동 리벳 추가)
        fillPaint.color = COL_OAK_WOOD
        canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR + (if (isWindmill) 12f * u else 10f * u), fillPaint)
        fillPaint.color = if (isWindmill) 0xFFEFE5D2.toInt() else COL_WARM_IVORY
        canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR + (if (isWindmill) 4.2f * u else 3f * u), fillPaint)

        val arcRect = RectF(
            wheelCenterX - maxWheelR,
            wheelCenterY - maxWheelR,
            wheelCenterX + maxWheelR,
            wheelCenterY + maxWheelR
        )

        arcs.forEach { arc ->
            val startAngle = arc.startDeg + state.wheelRotationDeg
            fillPaint.color = arc.item.colorArgb
            canvas.drawArc(arcRect, startAngle, arc.sweepDeg, true, fillPaint)

            // 조각 경계선
            strokePaint.color = COL_CARD_WHITE
            strokePaint.strokeWidth = 2.4f * u
            canvas.drawArc(arcRect, startAngle, arc.sweepDeg, true, strokePaint)

            // 최종 당첨 조각 강조 테두리
            if (isFinal && arc.index == result.winnerArcIndex) {
                strokePaint.color = COL_AMBER
                strokePaint.strokeWidth = 6.2f * u
                canvas.drawArc(arcRect, startAngle, arc.sweepDeg, true, strokePaint)
            }
        }

        // 각 조각 내부 텍스트 및 확률 렌더링 (좌반구 90~270도 뒤집힘 방지 정방향 정렬 및 자동 폭 맞춤)
        arcs.forEach { arc ->
            if (arc.sweepDeg >= 11f) {
                val midAngleDeg = arc.midDeg + state.wheelRotationDeg
                val normMid = ((midAngleDeg % 360f) + 360f) % 360f
                val flipLeft = normMid > 90f && normMid < 270f

                canvas.save()
                canvas.translate(wheelCenterX, wheelCenterY)
                if (flipLeft) {
                    canvas.rotate(midAngleDeg + 180f)
                } else {
                    canvas.rotate(midAngleDeg)
                }

                val textRadius = if (flipLeft) -(maxWheelR * 0.585f) else (maxWheelR * 0.585f)
                val sliceText = if (liveMask) "?" else arc.item.label
                val baseFontSize = when {
                    arcs.size <= 4 -> 22f * u
                    arcs.size <= 8 -> 18.2f * u
                    arcs.size <= 12 -> 15.2f * u
                    else -> 12.8f * u
                }
                val maxLabelWidth = maxWheelR * 0.65f
                var dynamicFontSize = baseFontSize
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = dynamicFontSize
                textPaint.color = COL_CARD_WHITE
                textPaint.textAlign = Paint.Align.CENTER

                while (textPaint.measureText(sliceText) > maxLabelWidth && dynamicFontSize > baseFontSize * 0.76f) {
                    dynamicFontSize *= 0.94f
                    textPaint.textSize = dynamicFontSize
                }

                val fitted = ellipsizeText(sliceText, textPaint, maxLabelWidth)
                val baseOffset = -(textPaint.descent() + textPaint.ascent()) * 0.5f
                canvas.drawText(fitted, textRadius, baseOffset, textPaint)

                if (!liveMask && arc.sweepDeg >= 26f && preset.items.any { it.safeWeight > 1 }) {
                    textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                    textPaint.textSize = (dynamicFontSize * 0.68f).coerceAtLeast(11f * u)
                    textPaint.color = 0xEEFFFFFF.toInt()
                    val pctStr = "${arc.probabilityPercent.roundToInt()}%"
                    canvas.drawText(pctStr, textRadius, baseOffset + dynamicFontSize * 0.92f, textPaint)
                }
                canvas.restore()
            }
        }

        // 조각 경계 황동 핀(Peg)
        arcs.forEach { arc ->
            val pegRad = Math.toRadians((arc.pegBoundaryDeg + state.wheelRotationDeg).toDouble())
            val px = wheelCenterX + cos(pegRad).toFloat() * (maxWheelR - 6f * u)
            val py = wheelCenterY + sin(pegRad).toFloat() * (maxWheelR - 6f * u)
            fillPaint.color = COL_WARM_IVORY
            canvas.drawCircle(px, py, 4.8f * u, fillPaint)
            strokePaint.color = COL_OAK_WOOD
            strokePaint.strokeWidth = 1.5f * u
            canvas.drawCircle(px, py, 4.8f * u, strokePaint)
        }

        // 정전(BLACK_OUT) 특수효과 활성 시 원판 암전 스포트라이트 오버레이
        if (state.isBlackOut) {
            fillPaint.color = 0xE618201B.toInt()
            canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR + 8f * u, fillPaint)
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 28f * u
            textPaint.color = COL_AMBER_SOFT
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("정전 효과 진행 중", wheelCenterX, wheelCenterY - 8f * u, textPaint)
            textPaint.textSize = 18f * u
            textPaint.color = COL_CARD_WHITE
            canvas.drawText("멈추는 순간 결과가 공개됩니다!", wheelCenterX, wheelCenterY + 26f * u, textPaint)
        }

        // 중앙 허브 캡
        fillPaint.color = if (isWindmill) 0xFFEFE5D2.toInt() else COL_CARD_WHITE
        canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR * 0.19f, fillPaint)
        strokePaint.color = COL_OAK_WOOD
        strokePaint.strokeWidth = 3.5f * u
        canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR * 0.19f, strokePaint)

        fillPaint.color = if (isFinal) {
            result.winnerItem.colorArgb
        } else if (isWindmill) {
            COL_TERRACOTTA
        } else {
            COL_MEADOW_GREEN
        }
        canvas.drawCircle(wheelCenterX, wheelCenterY, maxWheelR * 0.12f, fillPaint)

        textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        textPaint.textSize = 13.5f * u
        textPaint.color = COL_CARD_WHITE
        textPaint.textAlign = Paint.Align.CENTER
        val hubLabel = when {
            isFinal -> "당첨"
            isWindmill -> "풍차"
            else -> "SPIN"
        }
        canvas.drawText(
            hubLabel,
            wheelCenterX,
            wheelCenterY - (textPaint.descent() + textPaint.ascent()) * 0.5f,
            textPaint
        )

        // 상단 12시 포인터 & 탄성 플래퍼
        val pointerPivotX = wheelCenterX
        val pointerPivotY = wheelCenterY - maxWheelR - 14f * u
        canvas.save()
        canvas.rotate(state.flapperTiltDeg, pointerPivotX, pointerPivotY)
        val pointerPath = Path().apply {
            moveTo(pointerPivotX - 18f * u, pointerPivotY - 12f * u)
            lineTo(pointerPivotX + 18f * u, pointerPivotY - 12f * u)
            lineTo(pointerPivotX, pointerPivotY + 32f * u)
            close()
        }
        fillPaint.color = COL_TERRACOTTA
        canvas.drawPath(pointerPath, fillPaint)
        strokePaint.color = COL_CARD_WHITE
        strokePaint.strokeWidth = 2.5f * u
        canvas.drawPath(pointerPath, strokePaint)
        fillPaint.color = COL_CARD_WHITE
        canvas.drawCircle(pointerPivotX, pointerPivotY - 3f * u, 5f * u, fillPaint)
        canvas.restore()

        // 4. 최종 결정 구간 축하 꽃잎 파티클
        if (isFinal && state.confettiTimeSec > 0f) {
            val confettiColors = intArrayOf(
                0xFFE08D79.toInt(),
                0xFF6FA37C.toInt(),
                0xFFD9B25F.toInt(),
                0xFF7BA7C2.toInt()
            )
            for (i in 0 until 22) {
                val seedX = ((i * 43 + 17) % 100) / 100f
                val speed = 150f + (i % 5) * 38f
                val px = outerPad + 30f * u + seedX * (w - (outerPad + 30f * u) * 2f) +
                    sin(state.confettiTimeSec * 4f + i) * 18f * u
                val py = (outerPad + 90f * u + ((state.confettiTimeSec * speed * u + i * 32f * u) % (h * 0.68f)))
                fillPaint.color = confettiColors[i % confettiColors.size]
                canvas.save()
                canvas.translate(px, py)
                canvas.rotate(state.confettiTimeSec * 120f + i * 25f)
                canvas.drawRoundRect(
                    RectF(-7f * u, -4f * u, 7f * u, 4f * u),
                    4f * u,
                    4f * u,
                    fillPaint
                )
                canvas.restore()
            }
        }

        // 5. 하단 요약 카드 (회전 중에는 셋팅·후보 정보 카드 / 결정 후에는 최종 당첨 결과 카드)
        val bottomRect = RectF(
            headerLeft,
            h - outerPad - bottomCardHeight,
            headerRight,
            h - outerPad - 24f * u
        )

        if (!isFinal) {
            fillPaint.color = COL_WARM_IVORY
            canvas.drawRoundRect(bottomRect, 20f * u, 20f * u, fillPaint)
            strokePaint.color = COL_DIVIDER
            strokePaint.strokeWidth = 1.6f * u
            canvas.drawRoundRect(bottomRect, 20f * u, 20f * u, strokePaint)

            var infoY = bottomRect.top + 36f * u
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 18f * u
            textPaint.color = COL_PINE_DEEP
            textPaint.textAlign = Paint.Align.LEFT
            val optDesc = buildString {
                append("설정 옵션: ")
                append(if (preset.tapToStop) "탭하여 중지" else "${preset.normalizedDurationSec}초 회전")
                if (preset.specialEffect != RouletteSpecialEffect.NONE) {
                    append(" · 특수효과(${preset.specialEffect.title})")
                }
                if (preset.removeOnceChosen) {
                    append(" · 당첨 시 자동 제외")
                }
            }
            canvas.drawText(
                ellipsizeText(optDesc, textPaint, bottomRect.width() - 40f * u),
                bottomRect.left + 20f * u,
                infoY,
                textPaint
            )

            infoY += 32f * u
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.textSize = 16.5f * u
            textPaint.color = COL_INK_SECONDARY
            canvas.drawText("참가 항목 및 당첨 비율", bottomRect.left + 20f * u, infoY, textPaint)

            // 후보 칩 최대 6개 + 외 N개 표기
            infoY += 18f * u
            val maxPreview = min(arcs.size, 6)
            val colWidth = (bottomRect.width() - 52f * u) / 2f
            for (idx in 0 until maxPreview) {
                val arc = arcs[idx]
                val col = idx % 2
                val row = idx / 2
                val cx = bottomRect.left + 20f * u + col * (colWidth + 12f * u)
                val cy = infoY + row * 50f * u
                val chipRect = RectF(cx, cy, cx + colWidth, cy + 40f * u)

                fillPaint.color = COL_CARD_WHITE
                canvas.drawRoundRect(chipRect, 10f * u, 10f * u, fillPaint)
                strokePaint.color = if (idx == currentSliceIdx && !liveMask) arc.item.colorArgb else COL_DIVIDER
                strokePaint.strokeWidth = if (idx == currentSliceIdx && !liveMask) 2.4f * u else 1.2f * u
                canvas.drawRoundRect(chipRect, 10f * u, 10f * u, strokePaint)

                fillPaint.color = arc.item.colorArgb
                canvas.drawCircle(chipRect.left + 16f * u, chipRect.centerY(), 6.5f * u, fillPaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 15f * u
                textPaint.color = COL_INK_PRIMARY
                textPaint.textAlign = Paint.Align.LEFT
                val itemLabel = if (liveMask) "숨김 항목 ${idx + 1}" else arc.item.label
                val pctLabel = "${arc.probabilityPercent.roundToInt()}%"
                canvas.drawText(
                    ellipsizeText(itemLabel, textPaint, colWidth - 82f * u),
                    chipRect.left + 30f * u,
                    chipRect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f,
                    textPaint
                )

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textPaint.textSize = 13.5f * u
                textPaint.color = COL_INK_MUTED
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    pctLabel,
                    chipRect.right - 12f * u,
                    chipRect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f,
                    textPaint
                )
            }
            if (arcs.size > 6) {
                textPaint.textSize = 14f * u
                textPaint.color = COL_INK_MUTED
                textPaint.textAlign = Paint.Align.RIGHT
                canvas.drawText(
                    "외 ${arcs.size - 6}개 항목 더 있음",
                    bottomRect.right - 20f * u,
                    bottomRect.bottom - 14f * u,
                    textPaint
                )
            }
        } else {
            // 최종 당첨 결과 배너 카드
            fillPaint.color = COL_WARM_IVORY
            canvas.drawRoundRect(bottomRect, 22f * u, 22f * u, fillPaint)
            strokePaint.color = result.winnerItem.colorArgb
            strokePaint.strokeWidth = 3.2f * u
            canvas.drawRoundRect(bottomRect, 22f * u, 22f * u, strokePaint)

            // 좌측 컬러 포인트 바
            val barRect = RectF(
                bottomRect.left + 16f * u,
                bottomRect.top + 18f * u,
                bottomRect.left + 26f * u,
                bottomRect.bottom - 18f * u
            )
            fillPaint.color = result.winnerItem.colorArgb
            canvas.drawRoundRect(barRect, 5f * u, 5f * u, fillPaint)

            val contentLeft = bottomRect.left + 44f * u
            val contentRight = bottomRect.right - 24f * u
            var winY = bottomRect.top + 42f * u

            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 17f * u
            textPaint.color = COL_TERRACOTTA
            textPaint.textAlign = Paint.Align.LEFT
            val badgeHeader = if (result.appliedSpecialEffect != RouletteSpecialEffect.NONE) {
                "최종 당첨 결과  ·  특수효과 [${result.appliedSpecialEffect.title}] 적용"
            } else {
                "최종 당첨 결과"
            }
            canvas.drawText(
                ellipsizeText(badgeHeader, textPaint, contentRight - contentLeft),
                contentLeft,
                winY,
                textPaint
            )

            winY += 50f * u
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textPaint.textSize = 38f * u
            textPaint.color = COL_INK_PRIMARY
            canvas.drawText(
                ellipsizeText(result.winnerItem.label, textPaint, contentRight - contentLeft),
                contentLeft,
                winY,
                textPaint
            )

            winY += 34f * u
            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.textSize = 18f * u
            textPaint.color = COL_INK_SECONDARY
            val probText = String.format(
                Locale.KOREA,
                "당첨 확률 %.1f%% (가중치 %d배 · 전체 후보 %d개)",
                result.winnerProbabilityPercent,
                result.winnerItem.safeWeight,
                arcs.size
            )
            canvas.drawText(
                ellipsizeText(probText, textPaint, contentRight - contentLeft),
                contentLeft,
                winY,
                textPaint
            )

            if (result.winnerItem.secretNote.isNotBlank()) {
                winY += 20f * u
                val noteRect = RectF(contentLeft, winY, contentRight, winY + 52f * u)
                fillPaint.color = COL_CARD_WHITE
                canvas.drawRoundRect(noteRect, 12f * u, 12f * u, fillPaint)
                strokePaint.color = COL_DIVIDER
                strokePaint.strokeWidth = 1.4f * u
                canvas.drawRoundRect(noteRect, 12f * u, 12f * u, strokePaint)

                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textPaint.textSize = 16.5f * u
                textPaint.color = COL_PINE_DEEP
                val noteStr = "당첨 메모: ${result.winnerItem.secretNote}"
                canvas.drawText(
                    ellipsizeText(noteStr, textPaint, noteRect.width() - 24f * u),
                    noteRect.left + 14f * u,
                    noteRect.centerY() - (textPaint.descent() + textPaint.ascent()) * 0.5f,
                    textPaint
                )
                winY = noteRect.bottom + 28f * u
            } else {
                winY += 38f * u
                val summaryItems = arcs.take(5).joinToString(" · ") { it.item.label } +
                    if (arcs.size > 5) " 외 ${arcs.size - 5}개" else ""
                textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                textPaint.textSize = 15.5f * u
                textPaint.color = COL_INK_MUTED
                canvas.drawText(
                    ellipsizeText("참가 목록: $summaryItems", textPaint, contentRight - contentLeft),
                    contentLeft,
                    winY,
                    textPaint
                )
            }

            textPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textPaint.textSize = 14f * u
            textPaint.color = COL_INK_MUTED
            textPaint.textAlign = Paint.Align.RIGHT
            canvas.drawText(
                if (isWindmill) "미니게임 - 만능 룰렛 & 골프 복불복 · 언덕 위 풍차 룰렛" else "미니게임 - 만능 룰렛 & 골프 복불복 · 만능 결정 룰렛+",
                contentRight,
                bottomRect.bottom - 16f * u,
                textPaint
            )
        }
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 10f || paint.measureText(text) <= maxWidth) return text
        val ellipsis = "..."
        val ellipsisW = paint.measureText(ellipsis)
        var end = text.length
        while (end > 1 && paint.measureText(text, 0, end) + ellipsisW > maxWidth) {
            end--
        }
        return text.substring(0, end) + ellipsis
    }

    private fun sanitizeFileStem(raw: String): String {
        val cleaned = raw.trim()
            .replace(Regex("""[\\/:*?"<>|\s]+"""), "_")
            .trim('_')
        return cleaned.ifEmpty { "결정룰렛" }.take(18)
    }
}
