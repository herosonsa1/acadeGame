package com.jjalddalmark.minigamerullet.share

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
import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.RouletteVideoQuality
import com.jjalddalmark.minigamerullet.model.TarotEmblemType
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
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * 전체 미니게임(룰렛 외 9종 포함 공통)의 고해상도 결과 이미지(JPEG, 1080x1350) 및
 * 셋팅 -> 진행 -> 클라이맥스 슬로우모션 -> 최종 결과 발표 전 과정 동영상(MP4, H.264 + AAC) 생성 엔진.
 */
object CommonGameMediaExporter {

    private const val SHARED_CACHE_SUBDIR = "shared_games"

    // 라이트 테마 고정 내추럴 팔레트 (고대비 가독성 및 노 네온/노 이모지 원칙 준수)
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
    private const val COL_SKY_BLUE = 0xFF3B7A9E.toInt()
    private const val COL_SKY_SOFT = 0xFFDCECF5.toInt()
    private const val COL_OAK_WOOD = 0xFF7C583B.toInt()
    private const val COL_BIRCH_WOOD = 0xFFE8D8BE.toInt()
    private const val COL_PLUM = 0xFF6B538C.toInt()

    private val DISTINCT_BADGE_COLORS = intArrayOf(
        COL_MEADOW_GREEN,
        COL_TERRACOTTA,
        COL_SKY_BLUE,
        COL_AMBER,
        COL_PLUM,
        COL_OAK_WOOD
    )

    private enum class TimelinePhase {
        SETUP_READY,
        ACTION_PLAYING,
        CLIMAX_SLOWMO,
        FINAL_REVEAL
    }

    private data class GameVideoFrameState(
        val timeSec: Float,
        val phase: TimelinePhase,
        val actionProgress: Float,   // 0f ~ 1f (게임 진행도)
        val revealProgress: Float,   // 0f ~ 1f (최종 결과 카드 등장도)
        val isSlowMotion: Boolean,
        val statusLabel: String,
        val confettiTimeSec: Float
    )

    private data class EncodedSample(
        val data: ByteArray,
        val presentationTimeUs: Long,
        val flags: Int
    )

    fun ensureSharedDir(context: Context): File {
        val dir = File(context.cacheDir, SHARED_CACHE_SUBDIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun pruneOldSharedCache(context: Context) {
        runCatching {
            val dir = ensureSharedDir(context)
            val files = dir.listFiles()?.sortedByDescending { it.lastModified() } ?: return
            if (files.size > 16) {
                files.drop(16).forEach { it.delete() }
            }
        }
    }

    /**
     * 공유용 고해상도(`1080 x 1350`) 미니게임 결과 카드 이미지(JPEG) 생성
     */
    suspend fun renderResultImageToCache(
        context: Context,
        payload: CommonGameSharePayload
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
            payload = payload,
            state = GameVideoFrameState(
                timeSec = 6.5f,
                phase = TimelinePhase.FINAL_REVEAL,
                actionProgress = 1f,
                revealProgress = 1f,
                isSlowMotion = false,
                statusLabel = "최종 결과 카드",
                confettiTimeSec = 0.65f
            ),
            isStaticResultCard = true
        )

        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(payload.finishedAtMillis))
        val outFile = File(ensureSharedDir(context), "${payload.filePrefix}결과_$stamp.jpg")

        withContext(Dispatchers.IO) {
            FileOutputStream(outFile).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, fos)
                fos.flush()
            }
        }
        bitmap.recycle()
        outFile
    }

    /**
     * 출전 셋팅 -> 게임 진행 -> 슬로우모션 클라이맥스 -> 최종 결과 확정 전 과정을 담은 고화질·저용량 MP4 생성
     */
    suspend fun exportGameVideoToCache(
        context: Context,
        payload: CommonGameSharePayload,
        quality: RouletteVideoQuality = RouletteVideoQuality.HD_720P,
        includeSound: Boolean = true,
        onProgress: (Float) -> Unit = {}
    ): File = withContext(Dispatchers.Default) {
        pruneOldSharedCache(context)
        onProgress(0.03f)

        val frames = buildGameVideoTimeline(payload, quality.fps)

        // 1단계: 하드웨어 H.264 Surface 비디오 트랙 인코딩
        val (videoFormat, videoSamples) = encodeVideoTrack(
            payload = payload,
            frames = frames,
            quality = quality,
            onFrameProgress = { frac ->
                onProgress(0.05f + frac * 0.76f)
            }
        )

        coroutineContext.ensureActive()

        // 2단계: 동기화된 효과음(출발 차임 · 액션 틱 · 두근두근 심장박동 · 축하 팡파레) AAC 인코딩
        val audioResult: Pair<MediaFormat, List<EncodedSample>>? = if (includeSound) {
            runCatching {
                val pcm = synthesizeGameAudioPcm(frames, quality.fps, sampleRate = 44100)
                encodeAacAudioTrack(pcm, sampleRate = 44100, bitrate = 64_000)
            }.getOrNull()
        } else {
            null
        }

        onProgress(0.89f)
        coroutineContext.ensureActive()

        // 3단계: 2단계 버퍼드 MediaMuxer 멀티플렉싱 (데드락 제로)
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date(payload.finishedAtMillis))
        val tag = if (quality == RouletteVideoQuality.HD_720P) "720p" else "540p"
        val outFile = File(ensureSharedDir(context), "${payload.filePrefix}영상_${tag}_$stamp.mp4")

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

        onProgress(1.0f)
        outFile
    }

    /**
     * 총 약 6.8초 분량의 균형 잡힌 4단계 타임라인 구성 (메신저 공유 최적 용량 1~2.5MB 보장)
     */
    private fun buildGameVideoTimeline(
        payload: CommonGameSharePayload,
        fps: Int
    ): List<GameVideoFrameState> {
        val setupSec = 0.85f
        val playingSec = when (payload.durationSec) {
            in 1..7 -> 2.55f
            in 8..12 -> 2.95f
            else -> 3.35f
        }
        val climaxSec = 1.35f
        val revealSec = 1.75f

        val setupFrames = (setupSec * fps).roundToInt().coerceAtLeast(15)
        val playingFrames = (playingSec * fps).roundToInt().coerceAtLeast(45)
        val climaxFrames = (climaxSec * fps).roundToInt().coerceAtLeast(24)
        val revealFrames = (revealSec * fps).roundToInt().coerceAtLeast(30)

        val out = ArrayList<GameVideoFrameState>(setupFrames + playingFrames + climaxFrames + revealFrames + 4)
        var frameCursor = 0

        // 구간 A: 출전 동반자 셋팅 완료 (0.00s ~ 0.85s)
        for (i in 0 until setupFrames) {
            val t = frameCursor.toFloat() / fps.toFloat()
            out.add(
                GameVideoFrameState(
                    timeSec = t,
                    phase = TimelinePhase.SETUP_READY,
                    actionProgress = 0f,
                    revealProgress = 0f,
                    isSlowMotion = false,
                    statusLabel = "출전 셋팅 완료 · 시작",
                    confettiTimeSec = 0f
                )
            )
            frameCursor++
        }

        // 구간 B: 본격 게임 액션 진행 (actionProgress 0.00 -> 0.78)
        for (i in 0 until playingFrames) {
            val t = frameCursor.toFloat() / fps.toFloat()
            val u = (i + 1).toFloat() / playingFrames.toFloat()
            val eased = u * (2f - u) * 0.78f
            out.add(
                GameVideoFrameState(
                    timeSec = t,
                    phase = TimelinePhase.ACTION_PLAYING,
                    actionProgress = eased.coerceIn(0f, 0.78f),
                    revealProgress = 0f,
                    isSlowMotion = false,
                    statusLabel = "${payload.gameType.title} 진행 중",
                    confettiTimeSec = 0f
                )
            )
            frameCursor++
        }

        // 구간 C: 클라이맥스 슬로우모션 (actionProgress 0.78 -> 1.00)
        for (i in 0 until climaxFrames) {
            val t = frameCursor.toFloat() / fps.toFloat()
            val u = (i + 1).toFloat() / climaxFrames.toFloat()
            val smoothU = 1f - (1f - u).pow(2.4f)
            val prog = 0.78f + smoothU * 0.22f
            out.add(
                GameVideoFrameState(
                    timeSec = t,
                    phase = TimelinePhase.CLIMAX_SLOWMO,
                    actionProgress = prog.coerceIn(0.78f, 1f),
                    revealProgress = 0f,
                    isSlowMotion = true,
                    statusLabel = "결정적 순간 · 슬로우모션",
                    confettiTimeSec = 0f
                )
            )
            frameCursor++
        }

        // 구간 D: 최종 결과 확정 및 축하 홀드
        for (i in 0 until revealFrames) {
            val t = frameCursor.toFloat() / fps.toFloat()
            val postT = (i + 1).toFloat() / fps.toFloat()
            val reveal = (postT / 0.36f).coerceIn(0f, 1f)
            out.add(
                GameVideoFrameState(
                    timeSec = t,
                    phase = TimelinePhase.FINAL_REVEAL,
                    actionProgress = 1f,
                    revealProgress = reveal,
                    isSlowMotion = false,
                    statusLabel = "최종 결과 확정!",
                    confettiTimeSec = postT
                )
            )
            frameCursor++
        }

        return out
    }

    private suspend fun encodeVideoTrack(
        payload: CommonGameSharePayload,
        frames: List<GameVideoFrameState>,
        quality: RouletteVideoQuality,
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
                        payload = payload,
                        state = frameState,
                        isStaticResultCard = false
                    )
                } finally {
                    surface.unlockCanvasAndPost(canvas)
                }

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
        val frameStepUs = 1_000_000L / quality.fps.toLong()
        val normalizedSamples = samples.mapIndexed { idx, sample ->
            sample.copy(presentationTimeUs = idx * frameStepUs)
        }
        return finalFormat to normalizedSamples
    }

    /**
     * 출발 차임벨 -> 우드 액션 틱 -> 슬로우모션 두근두근 심장박동 -> 결과 발표 팡파레 PCM 합성
     */
    private fun synthesizeGameAudioPcm(
        frames: List<GameVideoFrameState>,
        fps: Int,
        sampleRate: Int
    ): ShortArray {
        val totalSec = frames.size.toDouble() / fps.toDouble()
        val totalSamples = (totalSec * sampleRate).toInt().coerceAtLeast(sampleRate)
        val mix = FloatArray(totalSamples)

        val woodTickLow = ShortArray((sampleRate * 0.055).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 66.0)
            val v = (sin(2.0 * PI * 740.0 * t) * 0.70 + sin(2.0 * PI * 1680.0 * t) * 0.30 * exp(-t * 90.0)) * env * 0.56
            (v * Short.MAX_VALUE).toInt().toShort()
        }
        val woodTickHigh = ShortArray((sampleRate * 0.055).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val env = exp(-t * 64.0)
            val v = (sin(2.0 * PI * 940.0 * t) * 0.70 + sin(2.0 * PI * 2120.0 * t) * 0.30 * exp(-t * 90.0)) * env * 0.60
            (v * Short.MAX_VALUE).toInt().toShort()
        }

        // 두근-두근 클라이맥스 심장박동 (0.34초)
        val heartbeatClip = ShortArray((sampleRate * 0.34).toInt()) { i ->
            val t = i.toDouble() / sampleRate
            val pulse1 = if (t < 0.14) sin(2.0 * PI * 105.0 * t) * exp(-t * 26.0) * 0.68 else 0.0
            val pulse2 = if (t >= 0.16) {
                val dt = t - 0.16
                sin(2.0 * PI * 88.0 * dt) * exp(-dt * 24.0) * 0.58
            } else 0.0
            ((pulse1 + pulse2).coerceIn(-0.9, 0.9) * Short.MAX_VALUE).toInt().toShort()
        }

        // 최종 결과 확정 5음 팡파레 (1.35초)
        val fanfareLen = (sampleRate * 1.35).toInt()
        val fanfareChord = listOf(523.25, 659.25, 783.99, 1046.50, 1318.51)
        val fanfarePcm = ShortArray(fanfareLen) { i ->
            val t = i.toDouble() / sampleRate
            var sum = 0.0
            fanfareChord.forEachIndexed { idx, freq ->
                val onset = idx * 0.070
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

        // 시작 차임
        overlayPcm((0.06 * sampleRate).toInt(), woodTickHigh, 0.75f)
        overlayPcm((0.16 * sampleRate).toInt(), woodTickLow, 0.65f)

        var lastActionTickSample = -sampleRate
        var lastHeartbeatSample = -sampleRate
        var finalFanfareTriggered = false

        frames.forEachIndexed { frameIdx, frame ->
            val samplePos = ((frameIdx.toDouble() / fps.toDouble()) * sampleRate).toInt()
            when (frame.phase) {
                TimelinePhase.SETUP_READY -> Unit
                TimelinePhase.ACTION_PLAYING -> {
                    val intervalSec = 0.14 + frame.actionProgress * 0.10
                    if (samplePos - lastActionTickSample >= (sampleRate * intervalSec).toInt()) {
                        val useHigh = ((frameIdx / 4) % 2 == 0)
                        overlayPcm(samplePos, if (useHigh) woodTickHigh else woodTickLow, 0.74f)
                        lastActionTickSample = samplePos
                    }
                }
                TimelinePhase.CLIMAX_SLOWMO -> {
                    if (samplePos - lastHeartbeatSample >= (sampleRate * 0.56).toInt()) {
                        overlayPcm(samplePos, heartbeatClip, 0.92f)
                        overlayPcm(samplePos + (sampleRate * 0.04).toInt(), woodTickHigh, 0.55f)
                        lastHeartbeatSample = samplePos
                    }
                }
                TimelinePhase.FINAL_REVEAL -> {
                    if (!finalFanfareTriggered) {
                        finalFanfareTriggered = true
                        overlayPcm(samplePos, fanfarePcm, 0.96f)
                    }
                }
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

    // =========================================================================
    // 전체 장면 렌더링 (정적 결과 이미지 1080x1350 & 동영상 720x1280 / 540x960 공통)
    // =========================================================================

    private fun drawFullFrameScene(
        canvas: Canvas,
        w: Float,
        h: Float,
        payload: CommonGameSharePayload,
        state: GameVideoFrameState,
        isStaticResultCard: Boolean
    ) {
        val s = w / 720f
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // 1. 크림 라이트 배경 및 은은한 구릉지 풍경
        fillPaint.color = COL_BG_CREAM
        canvas.drawRect(0f, 0f, w, h, fillPaint)

        fillPaint.color = 0xFFF2ECE0.toInt()
        canvas.drawCircle(w * 0.86f, h * 0.08f, 120f * s, fillPaint)

        val hillPath = Path().apply {
            moveTo(0f, h * 0.84f)
            quadTo(w * 0.35f, h * 0.79f, w, h * 0.85f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        fillPaint.color = 0xFFEAE3D3.toInt()
        canvas.drawPath(hillPath, fillPaint)

        // 2. 메인 화이트 카드 컨테이너
        val pad = 22f * s
        val cardRect = RectF(pad, pad, w - pad, h - pad)
        fillPaint.color = COL_CARD_WHITE
        canvas.drawRoundRect(cardRect, 24f * s, 24f * s, fillPaint)

        strokePaint.color = if (state.isSlowMotion) COL_AMBER else COL_DIVIDER
        strokePaint.strokeWidth = if (state.isSlowMotion) 3.2f * s else 1.6f * s
        canvas.drawRoundRect(cardRect, 24f * s, 24f * s, strokePaint)

        val innerLeft = cardRect.left + 22f * s
        val innerRight = cardRect.right - 22f * s
        var cursorY = cardRect.top + 22f * s

        // 3. 상단 영문 스탬프 + 진행 상태 배지
        val stampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_TERRACOTTA
            textSize = 13.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            letterSpacing = 0.05f
        }
        canvas.drawText(
            "${payload.englishHeaderStamp} · FAIRWAY PICNIC",
            innerLeft,
            cursorY + 14f * s,
            stampPaint
        )

        val badgeBg = when (state.phase) {
            TimelinePhase.SETUP_READY -> COL_WARM_IVORY
            TimelinePhase.ACTION_PLAYING -> COL_MEADOW_SOFT
            TimelinePhase.CLIMAX_SLOWMO -> COL_AMBER_SOFT
            TimelinePhase.FINAL_REVEAL -> COL_MEADOW_GREEN
        }
        val badgeFg = when (state.phase) {
            TimelinePhase.SETUP_READY -> COL_INK_SECONDARY
            TimelinePhase.ACTION_PLAYING -> COL_PINE_DEEP
            TimelinePhase.CLIMAX_SLOWMO -> COL_TERRACOTTA
            TimelinePhase.FINAL_REVEAL -> COL_CARD_WHITE
        }
        val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = badgeFg
            textSize = 13f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val badgeW = (badgeTextPaint.measureText(state.statusLabel) + 22f * s).coerceAtLeast(110f * s)
        val badgeRect = RectF(innerRight - badgeW, cursorY - 2f * s, innerRight, cursorY + 24f * s)
        fillPaint.color = badgeBg
        canvas.drawRoundRect(badgeRect, 12f * s, 12f * s, fillPaint)
        canvas.drawText(
            state.statusLabel,
            badgeRect.centerX(),
            badgeRect.centerY() + 4.8f * s,
            badgeTextPaint
        )

        cursorY += 36f * s

        // 4. 게임 결과 메인 타이틀 & 서브타이틀
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_PRIMARY
            textSize = 27f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            ellipsizeText(payload.resultTitle, titlePaint, innerRight - innerLeft),
            innerLeft,
            cursorY + 24f * s,
            titlePaint
        )
        cursorY += 33f * s

        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_SECONDARY
            textSize = 15.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val subtitleToShow = when (state.phase) {
            TimelinePhase.SETUP_READY -> "참가 동반자 배치를 마치고 게임을 시작합니다."
            TimelinePhase.ACTION_PLAYING -> "실시간 물리·확률 연출로 승부가 진행되고 있습니다."
            TimelinePhase.CLIMAX_SLOWMO -> "누가 주인공이 될지 결정되는 마지막 순간입니다!"
            TimelinePhase.FINAL_REVEAL -> payload.resultSubtitle
        }
        canvas.drawText(
            ellipsizeText(subtitleToShow, subPaint, innerRight - innerLeft),
            innerLeft,
            cursorY + 15f * s,
            subPaint
        )
        cursorY += 26f * s

        // 5. 중앙 게임별 전용 스테이지 일러스트 박스
        val stageHeightRatio = if (isStaticResultCard) 0.37f else 0.45f
        val stageHeight = (h * stageHeightRatio).coerceIn(290f * s, 560f * s)
        val stageRect = RectF(innerLeft, cursorY, innerRight, cursorY + stageHeight)

        fillPaint.color = COL_WARM_IVORY
        canvas.drawRoundRect(stageRect, 18f * s, 18f * s, fillPaint)
        strokePaint.color = if (state.isSlowMotion) COL_AMBER else COL_DIVIDER
        strokePaint.strokeWidth = if (state.isSlowMotion) 2.2f * s else 1.2f * s
        canvas.drawRoundRect(stageRect, 18f * s, 18f * s, strokePaint)

        canvas.save()
        val clipPath = Path().apply { addRoundRect(stageRect, 18f * s, 18f * s, Path.Direction.CW) }
        canvas.clipPath(clipPath)
        drawGameStageContent(canvas, stageRect, s, payload, state)
        if (state.phase == TimelinePhase.FINAL_REVEAL) {
            drawCelebrationPetals(canvas, stageRect, s, state.confettiTimeSec)
        }
        canvas.restore()

        cursorY = stageRect.bottom + 16f * s

        // 6. 하단 결과 요약 영역 (진행 중일 때는 출전 명단 & 게이지, 완료 시에는 순위표 & 대표 당첨 배너)
        val footerReserve = 34f * s
        val bottomAvailBottom = cardRect.bottom - footerReserve

        if (state.phase != TimelinePhase.FINAL_REVEAL) {
            drawInProgressBottomPanel(
                canvas = canvas,
                left = innerLeft,
                top = cursorY,
                right = innerRight,
                bottom = bottomAvailBottom,
                s = s,
                payload = payload,
                state = state
            )
        } else {
            drawFinalResultsBottomPanel(
                canvas = canvas,
                left = innerLeft,
                top = cursorY,
                right = innerRight,
                bottom = bottomAvailBottom,
                s = s,
                payload = payload,
                revealProgress = state.revealProgress
            )
        }

        // 7. 하단 브랜드 푸터
        val footerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_MUTED
            textSize = 12.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val stampHuman = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.KOREA).format(Date(payload.finishedAtMillis))
        canvas.drawText(
            "미니게임 - 만능 룰렛 & 골프 복불복 · ${payload.gameType.title}",
            innerLeft,
            cardRect.bottom - 14f * s,
            footerPaint
        )
        footerPaint.textAlign = Paint.Align.RIGHT
        canvas.drawText(
            stampHuman,
            innerRight,
            cardRect.bottom - 14f * s,
            footerPaint
        )
    }

    private fun drawInProgressBottomPanel(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        s: Float,
        payload: CommonGameSharePayload,
        state: GameVideoFrameState
    ) {
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val box = RectF(left, top, right, bottom.coerceAtLeast(top + 110f * s))
        fillPaint.color = COL_BG_CREAM
        canvas.drawRoundRect(box, 16f * s, 16f * s, fillPaint)
        strokePaint.color = COL_DIVIDER
        strokePaint.strokeWidth = 1.2f * s
        canvas.drawRoundRect(box, 16f * s, 16f * s, strokePaint)

        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_PINE_DEEP
            textSize = 16.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            "출전 동반자 및 실시간 진행률",
            box.left + 18f * s,
            box.top + 28f * s,
            headPaint
        )

        val pct = (state.actionProgress * 100f).roundToInt().coerceIn(0, 100)
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (state.isSlowMotion) COL_TERRACOTTA else COL_MEADOW_GREEN
            textSize = 16f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(
            "${pct}%",
            box.right - 18f * s,
            box.top + 28f * s,
            pctPaint
        )

        // 진행 바
        val barRect = RectF(box.left + 18f * s, box.top + 40f * s, box.right - 18f * s, box.top + 52f * s)
        fillPaint.color = COL_CARD_WHITE
        canvas.drawRoundRect(barRect, 6f * s, 6f * s, fillPaint)
        strokePaint.color = COL_DIVIDER
        strokePaint.strokeWidth = 1f * s
        canvas.drawRoundRect(barRect, 6f * s, 6f * s, strokePaint)

        val progWidth = (barRect.width() * state.actionProgress.coerceIn(0.04f, 1f))
        val progRect = RectF(barRect.left, barRect.top, barRect.left + progWidth, barRect.bottom)
        fillPaint.color = if (state.isSlowMotion) COL_AMBER else COL_MEADOW_GREEN
        canvas.drawRoundRect(progRect, 6f * s, 6f * s, fillPaint)

        // 참가자 명단 칩 그리드
        val names = payload.rows.map { it.playerName }.distinct().take(6)
        val cols = min(3, names.size.coerceAtLeast(1))
        val chipGap = 10f * s
        val totalChipW = box.width() - 36f * s
        val chipW = (totalChipW - chipGap * (cols - 1)) / cols.toFloat()
        val chipH = 36f * s
        var startY = barRect.bottom + 16f * s

        val chipTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_PRIMARY
            textSize = 14f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        names.forEachIndexed { idx, name ->
            val col = idx % cols
            val row = idx / cols
            val cxLeft = box.left + 18f * s + col * (chipW + chipGap)
            val cyTop = startY + row * (chipH + 8f * s)
            if (cyTop + chipH <= box.bottom - 10f * s) {
                val r = RectF(cxLeft, cyTop, cxLeft + chipW, cyTop + chipH)
                fillPaint.color = COL_CARD_WHITE
                canvas.drawRoundRect(r, 10f * s, 10f * s, fillPaint)
                strokePaint.color = DISTINCT_BADGE_COLORS[idx % DISTINCT_BADGE_COLORS.size]
                strokePaint.strokeWidth = 1.5f * s
                canvas.drawRoundRect(r, 10f * s, 10f * s, strokePaint)
                canvas.drawText(
                    ellipsizeText(name, chipTextPaint, chipW - 12f * s),
                    r.centerX(),
                    r.centerY() + 5f * s,
                    chipTextPaint
                )
            }
        }

        startY += ((names.size + cols - 1) / cols) * (chipH + 8f * s) + 8f * s
        if (startY + 20f * s < box.bottom) {
            val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_INK_SECONDARY
                textSize = 13.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val hint = if (state.isSlowMotion) {
                "심장박동 클라이맥스 구간입니다. 곧 최종 결과가 공개됩니다!"
            } else {
                "동반자 전원이 공정한 확률로 승부를 겨루고 있습니다."
            }
            canvas.drawText(
                ellipsizeText(hint, hintPaint, box.width() - 36f * s),
                box.left + 18f * s,
                startY + 14f * s,
                hintPaint
            )
        }
    }

    private fun drawFinalResultsBottomPanel(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        s: Float,
        payload: CommonGameSharePayload,
        revealProgress: Float
    ) {
        val alphaInt = (revealProgress.coerceIn(0f, 1f) * 255f).roundToInt()
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        var curY = top
        val primary: GameResultRow? = payload.primaryRow

        // 대표 주인공 배너
        if (primary != null) {
            val heroH = 66f * s
            val heroRect = RectF(left, curY, right, curY + heroH)
            fillPaint.color = applyAlpha(COL_AMBER_SOFT, alphaInt)
            canvas.drawRoundRect(heroRect, 14f * s, 14f * s, fillPaint)
            strokePaint.color = applyAlpha(COL_AMBER, alphaInt)
            strokePaint.strokeWidth = 2f * s
            canvas.drawRoundRect(heroRect, 14f * s, 14f * s, strokePaint)

            val heroBadgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_CARD_WHITE, alphaInt)
                textSize = 13.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bW = (heroBadgePaint.measureText(primary.badgeText) + 22f * s).coerceAtLeast(78f * s)
            val bRect = RectF(heroRect.left + 14f * s, heroRect.top + 16f * s, heroRect.left + 14f * s + bW, heroRect.bottom - 16f * s)
            fillPaint.color = applyAlpha(COL_TERRACOTTA, alphaInt)
            canvas.drawRoundRect(bRect, 9f * s, 9f * s, fillPaint)
            canvas.drawText(primary.badgeText, bRect.centerX(), bRect.centerY() + 4.8f * s, heroBadgePaint)

            val nameX = bRect.right + 12f * s
            val heroNamePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_INK_PRIMARY, alphaInt)
                textSize = 19f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val heroDetailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_INK_SECONDARY, alphaInt)
                textSize = 13.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val availTextW = (heroRect.right - 14f * s - nameX).coerceAtLeast(60f * s)
            canvas.drawText(
                ellipsizeText(primary.playerName, heroNamePaint, availTextW),
                nameX,
                heroRect.top + 28f * s,
                heroNamePaint
            )
            canvas.drawText(
                ellipsizeText(primary.detailText, heroDetailPaint, availTextW),
                nameX,
                heroRect.top + 49f * s,
                heroDetailPaint
            )

            curY = heroRect.bottom + 10f * s
        }

        // 전체 순위/결과 행 목록 (최대 6행 자동 높이 맞춤)
        val rows = payload.rows.take(6)
        if (rows.isEmpty()) return

        val availH = (bottom - curY).coerceAtLeast(60f * s)
        val gap = 6f * s
        val rawRowH = (availH - gap * (rows.size - 1)) / rows.size.toFloat()
        val rowH = rawRowH.coerceIn(34f * s, 50f * s)

        rows.forEachIndexed { idx, row ->
            if (curY + rowH > bottom + 4f * s) return@forEachIndexed
            val rRect = RectF(left, curY, right, curY + rowH)
            val badgeCol = DISTINCT_BADGE_COLORS[idx % DISTINCT_BADGE_COLORS.size]

            fillPaint.color = applyAlpha(if (row.isHighlighted) COL_CARD_WHITE else COL_BG_CREAM, alphaInt)
            canvas.drawRoundRect(rRect, 11f * s, 11f * s, fillPaint)
            strokePaint.color = applyAlpha(if (row.isHighlighted) badgeCol else COL_DIVIDER, alphaInt)
            strokePaint.strokeWidth = if (row.isHighlighted) 1.6f * s else 1f * s
            canvas.drawRoundRect(rRect, 11f * s, 11f * s, strokePaint)

            // 좌측 순번 원형 도트
            val dotR = 11f * s
            val dotCx = rRect.left + 20f * s
            val dotCy = rRect.centerY()
            fillPaint.color = applyAlpha(badgeCol, alphaInt)
            canvas.drawCircle(dotCx, dotCy, dotR, fillPaint)

            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_CARD_WHITE, alphaInt)
                textSize = 11.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${idx + 1}", dotCx, dotCy + 4f * s, numPaint)

            // 우측 배지
            val bTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_CARD_WHITE, alphaInt)
                textSize = 11.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            val bWidth = (bTextPaint.measureText(row.badgeText) + 18f * s).coerceAtLeast(66f * s)
            val bBox = RectF(rRect.right - 10f * s - bWidth, rRect.centerY() - 12f * s, rRect.right - 10f * s, rRect.centerY() + 12f * s)
            fillPaint.color = applyAlpha(badgeCol, alphaInt)
            canvas.drawRoundRect(bBox, 7f * s, 7f * s, fillPaint)
            canvas.drawText(row.badgeText, bBox.centerX(), bBox.centerY() + 4f * s, bTextPaint)

            // 이름 + 상세 텍스트
            val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_INK_PRIMARY, alphaInt)
                textSize = 14.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            }
            val detPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_INK_SECONDARY, alphaInt)
                textSize = 12.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            }
            val textStartX = dotCx + dotR + 10f * s
            val nameW = namePaint.measureText(row.playerName)
            canvas.drawText(row.playerName, textStartX, rRect.centerY() + 5f * s, namePaint)

            val detStartX = textStartX + nameW + 10f * s
            val maxDetW = (bBox.left - 10f * s - detStartX).coerceAtLeast(0f)
            if (maxDetW > 30f * s) {
                canvas.drawText(
                    ellipsizeText(row.detailText, detPaint, maxDetW),
                    detStartX,
                    rRect.centerY() + 4.5f * s,
                    detPaint
                )
            }

            curY += rowH + gap
        }
    }

    // =========================================================================
    // 9종 미니게임별 전용 벡터 스테이지 일러스트 & 애니메이션 렌더러
    // =========================================================================

    private fun drawGameStageContent(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        payload: CommonGameSharePayload,
        state: GameVideoFrameState
    ) {
        when (val extra = payload.extraSceneData) {
            is GameExtraSceneData.TeeBounce -> drawTeeBounceStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.NotchStick -> drawNotchStickStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.ForestLadder -> drawForestLadderStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.NearPinPutting -> drawNearPinPuttingStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.BallBasket -> drawBallBasketStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.LuckyWoodTile -> drawLuckyWoodTileStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.DoubleDice -> drawDoubleDiceStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.YutNori -> drawYutNoriStage(canvas, rect, s, extra, state)
            is GameExtraSceneData.TarotFortune -> drawTarotFortuneStage(canvas, rect, s, extra, state)
            GameExtraSceneData.None -> drawGenericStageFallback(canvas, rect, s, payload, state)
        }
    }

    /**
     * 1. 드라이버 티 바운스: 원형 잔디 위 동반자 좌석 배치 + 드라이버 헤드 낙하 + 자작나무 티 회전 지목
     */
    private fun drawTeeBounceStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.TeeBounce,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val cx = rect.centerX()
        val cy = rect.centerY() + 8f * s
        val radius = min(rect.width(), rect.height()) * 0.36f

        // 잔디 원형 매트
        fill.color = COL_MEADOW_SOFT
        canvas.drawCircle(cx, cy, radius * 1.12f, fill)
        stroke.color = COL_MEADOW_GREEN
        stroke.strokeWidth = 2f * s
        canvas.drawCircle(cx, cy, radius * 1.12f, stroke)

        stroke.color = applyAlpha(COL_MEADOW_GREEN, 90)
        stroke.pathEffect = DashPathEffect(floatArrayOf(8f * s, 6f * s), 0f)
        canvas.drawCircle(cx, cy, radius * 0.72f, stroke)
        stroke.pathEffect = null

        val names = data.seatPlayerNames.ifEmpty { listOf("동반자1", "동반자2", "동반자3", "동반자4") }
        val n = names.size
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 13f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // 동반자 좌석 칩
        for (i in 0 until n) {
            val angleDeg = -90f + (360f / n.toFloat()) * i
            val rad = Math.toRadians(angleDeg.toDouble())
            val px = cx + cos(rad).toFloat() * (radius * 1.02f)
            val py = cy + sin(rad).toFloat() * (radius * 1.02f)
            val isWinner = (state.phase == TimelinePhase.FINAL_REVEAL) && (i == data.winnerSeatIndex)

            val pillW = 86f * s
            val pillH = 30f * s
            val pRect = RectF(px - pillW * 0.5f, py - pillH * 0.5f, px + pillW * 0.5f, py + pillH * 0.5f)
            fill.color = if (isWinner) COL_TERRACOTTA else COL_CARD_WHITE
            canvas.drawRoundRect(pRect, 15f * s, 15f * s, fill)
            stroke.color = if (isWinner) COL_AMBER else DISTINCT_BADGE_COLORS[i % DISTINCT_BADGE_COLORS.size]
            stroke.strokeWidth = if (isWinner) 2.5f * s else 1.5f * s
            canvas.drawRoundRect(pRect, 15f * s, 15f * s, stroke)

            labelPaint.color = if (isWinner) COL_CARD_WHITE else COL_INK_PRIMARY
            canvas.drawText(
                ellipsizeText(names[i], labelPaint, pillW - 12f * s),
                pRect.centerX(),
                pRect.centerY() + 4.5f * s,
                labelPaint
            )
        }

        // 회전 각도 계산 (초반 바운스 후 감속 정지)
        val u = state.actionProgress.coerceIn(0f, 1f)
        val decel = 1f - (1f - u).pow(3.3f)
        val currentAngle = if (state.phase == TimelinePhase.SETUP_READY) {
            -90f
        } else {
            (data.finalAngleDeg - 1620f) + 1620f * decel
        }
        val bounceScale = if (u in 0.01f..0.28f) {
            1f + abs(sin(u * 26f)) * 0.18f * (1f - u * 3f).coerceAtLeast(0f)
        } else {
            1f
        }

        // 초반 드라이버 우드 헤드 터치 연출
        if (u < 0.20f) {
            val headOffset = (u / 0.20f) * 45f * s
            val headRect = RectF(
                cx - 95f * s - headOffset,
                cy - 68f * s - headOffset,
                cx - 25f * s - headOffset,
                cy - 22f * s - headOffset
            )
            fill.color = COL_OAK_WOOD
            canvas.drawRoundRect(headRect, 18f * s, 18f * s, fill)
        }

        // 중앙 자작나무 골프 티 회전 렌더링 (티 뾰족한 끝이 +X 방향 = 0도 기준)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(currentAngle)
        canvas.scale(bounceScale, bounceScale)

        val teeLen = radius * 0.54f
        // 티 컵 머리 (반대편)
        val cupRect = RectF(-teeLen * 0.65f, -11f * s, -teeLen * 0.42f, 11f * s)
        fill.color = COL_OAK_WOOD
        canvas.drawRoundRect(cupRect, 4f * s, 4f * s, fill)

        // 티 몸통 -> 뾰족한 팁
        val shaftPath = Path().apply {
            moveTo(-teeLen * 0.44f, -6.5f * s)
            lineTo(teeLen * 0.55f, -3.5f * s)
            lineTo(teeLen * 0.86f, 0f)
            lineTo(teeLen * 0.55f, 3.5f * s)
            lineTo(-teeLen * 0.44f, 6.5f * s)
            close()
        }
        fill.color = COL_BIRCH_WOOD
        canvas.drawPath(shaftPath, fill)
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 1.6f * s
        canvas.drawPath(shaftPath, stroke)

        // 팁 끝 레드 포인트 마커
        fill.color = COL_TERRACOTTA
        canvas.drawCircle(teeLen * 0.72f, 0f, 4.2f * s, fill)
        canvas.restore()

        // 상단 방향 규칙 배지
        val ruleText = if (data.rightHandRule) "순서 규칙: 1번 오너 기준 우측 방향" else "순서 규칙: 1번 오너 기준 좌측 방향"
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_PINE_DEEP
            textSize = 12.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(ruleText, rect.left + 16f * s, rect.top + 24f * s, rulePaint)
    }

    /**
     * 2. 홈 뽑기 막대(오너 스틱): 하단 원통 케이스 흔들림 후 막대가 위로 솟아오르며 1~N개 홈 공개
     */
    private fun drawNotchStickStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.NotchStick,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val owners = data.stickOwnerNames.ifEmpty { listOf("동반자1", "동반자2", "동반자3", "동반자4") }
        val notches = data.stickNotchCounts.ifEmpty { owners.indices.map { it + 1 } }
        val count = owners.size.coerceAtMost(6)

        val u = state.actionProgress.coerceIn(0f, 1f)
        val shakeX = if (u in 0.01f..0.24f) sin(u * 65f) * 8f * s else 0f
        val pullFrac = ((u - 0.22f) / 0.74f).coerceIn(0f, 1f)

        val caseRect = RectF(
            rect.left + rect.width() * 0.12f + shakeX,
            rect.bottom - rect.height() * 0.34f,
            rect.right - rect.width() * 0.12f + shakeX,
            rect.bottom - 16f * s
        )

        val stickAreaW = caseRect.width() - 36f * s
        val stickSpacing = stickAreaW / count.toFloat()
        val stickW = min(34f * s, stickSpacing * 0.62f)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_PRIMARY
            textSize = 12.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        // 막대 렌더링 (케이스 뒤편에서 위로 뽑혀 올라옴)
        for (i in 0 until count) {
            val staggeredPull = ((pullFrac * 1.25f) - i * 0.06f).coerceIn(0f, 1f)
            val liftY = staggeredPull * (rect.height() * 0.34f)
            val cx = caseRect.left + 18f * s + stickSpacing * (i + 0.5f)
            val stickTop = (caseRect.top - 52f * s) - liftY
            val stickBottom = caseRect.bottom - 18f * s
            val sRect = RectF(cx - stickW * 0.5f, stickTop, cx + stickW * 0.5f, stickBottom)

            fill.color = COL_BIRCH_WOOD
            canvas.drawRoundRect(sRect, 8f * s, 8f * s, fill)
            stroke.color = COL_OAK_WOOD
            stroke.strokeWidth = 1.5f * s
            canvas.drawRoundRect(sRect, 8f * s, 8f * s, stroke)

            // 막대 하단 붉은 홈(Notch) 눈금 (뽑혀 올라왔을 때 노출)
            val nCount = notches.getOrElse(i) { i + 1 }
            if (staggeredPull > 0.35f) {
                fill.color = COL_TERRACOTTA
                for (k in 0 until nCount) {
                    val ny = sRect.top + 48f * s + k * 10.5f * s
                    if (ny + 5f * s < caseRect.top - 2f * s) {
                        val nRect = RectF(sRect.left + 4f * s, ny, sRect.right - 4f * s, ny + 5.5f * s)
                        canvas.drawRoundRect(nRect, 2.5f * s, 2.5f * s, fill)
                    }
                }
            }

            // 막대 상단 이름표 및 순번
            canvas.drawText(
                ellipsizeText(owners[i], namePaint, stickSpacing - 4f * s),
                cx,
                stickTop - 8f * s,
                namePaint
            )

            if (staggeredPull > 0.78f || state.phase == TimelinePhase.FINAL_REVEAL) {
                val isFirst = nCount == 1
                val tagRect = RectF(cx - 24f * s, stickTop + 8f * s, cx + 24f * s, stickTop + 30f * s)
                fill.color = if (isFirst) COL_TERRACOTTA else COL_MEADOW_GREEN
                canvas.drawRoundRect(tagRect, 7f * s, 7f * s, fill)
                val rankPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = COL_CARD_WHITE
                    textSize = 11f * s
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                    textAlign = Paint.Align.CENTER
                }
                canvas.drawText("${nCount}번", cx, tagRect.centerY() + 4f * s, rankPaint)
            }
        }

        // 앞면 오크 원통 케이스 덮어 그리기
        fill.color = COL_OAK_WOOD
        canvas.drawRoundRect(caseRect, 16f * s, 16f * s, fill)
        stroke.color = COL_PINE_DEEP
        stroke.strokeWidth = 2f * s
        canvas.drawRoundRect(caseRect, 16f * s, 16f * s, stroke)

        val plateRect = RectF(
            caseRect.centerX() - 96f * s,
            caseRect.centerY() - 16f * s,
            caseRect.centerX() + 96f * s,
            caseRect.centerY() + 16f * s
        )
        fill.color = COL_WARM_IVORY
        canvas.drawRoundRect(plateRect, 8f * s, 8f * s, fill)
        val platePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_OAK_WOOD
            textSize = 12f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("HONOR STICK CASE", plateRect.centerX(), plateRect.centerY() + 4.5f * s, platePaint)
    }

    /**
     * 3. 숲속 통나무 사다리: 세로 소나무 줄기 + 가로 통나무 다리 + 안개 걷힘 + 골프공 경로 주행
     */
    private fun drawForestLadderStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.ForestLadder,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val names = data.topPlayerNames.ifEmpty { listOf("동반자1", "동반자2", "동반자3", "동반자4") }
        val slots = data.bottomSlots.ifEmpty { names.indices.map { "${it + 1}번" } }
        val cols = names.size.coerceAtLeast(2)
        val totalRows = data.totalRows.coerceAtLeast(4)

        val boardLeft = rect.left + 38f * s
        val boardRight = rect.right - 38f * s
        val boardTop = rect.top + 48f * s
        val boardBottom = rect.bottom - 48f * s
        val colStep = (boardRight - boardLeft) / (cols - 1).toFloat()
        val rowStep = (boardBottom - boardTop) / (totalRows + 1).toFloat()

        // 세로 기둥
        stroke.color = COL_PINE_DEEP
        stroke.strokeWidth = 3.2f * s
        for (c in 0 until cols) {
            val x = boardLeft + c * colStep
            canvas.drawLine(x, boardTop, x, boardBottom, stroke)
        }

        // 가로 통나무 다리
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 4.5f * s
        data.rungs.forEach { (r, leftCol) ->
            if (leftCol in 0 until (cols - 1)) {
                val y = boardTop + (r + 1) * rowStep
                val x1 = boardLeft + leftCol * colStep
                val x2 = boardLeft + (leftCol + 1) * colStep
                canvas.drawLine(x1, y, x2, y, stroke)
            }
        }

        // 상단 참가자 칩 & 하단 결과 슬롯 칩
        val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val chipW = min(82f * s, colStep * 0.88f)
        for (c in 0 until cols) {
            val cx = boardLeft + c * colStep
            val topChip = RectF(cx - chipW * 0.5f, rect.top + 12f * s, cx + chipW * 0.5f, rect.top + 38f * s)
            val pCol = data.playerBadgeColorArgbs.getOrElse(c) { DISTINCT_BADGE_COLORS[c % DISTINCT_BADGE_COLORS.size] }
            fill.color = COL_CARD_WHITE
            canvas.drawRoundRect(topChip, 9f * s, 9f * s, fill)
            stroke.color = pCol
            stroke.strokeWidth = 1.8f * s
            canvas.drawRoundRect(topChip, 9f * s, 9f * s, stroke)
            chipPaint.color = COL_INK_PRIMARY
            canvas.drawText(ellipsizeText(names[c], chipPaint, chipW - 8f * s), cx, topChip.centerY() + 4f * s, chipPaint)

            val slotLabel = slots.getOrElse(c) { "${c + 1}번" }
            val isAccentSlot = slotLabel.contains("커피") || slotLabel.startsWith("1번")
            val botChip = RectF(cx - chipW * 0.5f, rect.bottom - 38f * s, cx + chipW * 0.5f, rect.bottom - 12f * s)
            fill.color = if (isAccentSlot && state.phase == TimelinePhase.FINAL_REVEAL) COL_TERRACOTTA else COL_CARD_WHITE
            canvas.drawRoundRect(botChip, 9f * s, 9f * s, fill)
            stroke.color = if (isAccentSlot) COL_TERRACOTTA else COL_DIVIDER
            stroke.strokeWidth = 1.6f * s
            canvas.drawRoundRect(botChip, 9f * s, 9f * s, stroke)
            chipPaint.color = if (isAccentSlot && state.phase == TimelinePhase.FINAL_REVEAL) COL_CARD_WHITE else COL_PINE_DEEP
            canvas.drawText(ellipsizeText(slotLabel, chipPaint, chipW - 8f * s), cx, botChip.centerY() + 4f * s, chipPaint)
        }

        // 공 이동 궤적 및 현재 위치 렌더링
        val u = state.actionProgress.coerceIn(0f, 1f)
        if (u > 0.01f) {
            for (pIdx in 0 until cols) {
                val pCol = data.playerBadgeColorArgbs.getOrElse(pIdx) { DISTINCT_BADGE_COLORS[pIdx % DISTINCT_BADGE_COLORS.size] }
                val rawPts = data.playerWaypoints.getOrNull(pIdx)?.takeIf { it.size >= 2 }
                    ?: listOf(
                        (pIdx.toFloat() / (cols - 1).toFloat()) to 0f,
                        (data.destCols.getOrElse(pIdx) { pIdx }.toFloat() / (cols - 1).toFloat()) to 1f
                    )

                val screenPts = rawPts.map { (nx, ny) ->
                    (boardLeft + nx * (boardRight - boardLeft)) to (boardTop + ny * (boardBottom - boardTop))
                }
                val (ballX, ballY, partialPath) = samplePolylineProgress(screenPts, u)

                stroke.color = applyAlpha(pCol, 195)
                stroke.strokeWidth = 3f * s
                canvas.drawPath(partialPath, stroke)

                fill.color = COL_CARD_WHITE
                canvas.drawCircle(ballX, ballY, 8.5f * s, fill)
                stroke.color = pCol
                stroke.strokeWidth = 2.4f * s
                canvas.drawCircle(ballX, ballY, 8.5f * s, stroke)
            }
        }

        // 초중반 안개 가림막 연출 (서서히 걷힘)
        val mistAlpha = ((0.58f - u) / 0.58f).coerceIn(0f, 1f)
        if (mistAlpha > 0.03f && state.phase != TimelinePhase.FINAL_REVEAL) {
            val mistRect = RectF(
                boardLeft - 14f * s,
                boardTop + (boardBottom - boardTop) * 0.25f,
                boardRight + 14f * s,
                boardTop + (boardBottom - boardTop) * 0.75f
            )
            fill.color = applyAlpha(0xFFF5F2E9.toInt(), (mistAlpha * 235f).roundToInt())
            canvas.drawRoundRect(mistRect, 14f * s, 14f * s, fill)
            stroke.color = applyAlpha(COL_DIVIDER, (mistAlpha * 220f).roundToInt())
            stroke.strokeWidth = 1.2f * s
            canvas.drawRoundRect(mistRect, 14f * s, 14f * s, stroke)

            val mistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = applyAlpha(COL_INK_SECONDARY, (mistAlpha * 240f).roundToInt())
                textSize = 14f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("숲속 안개 구간 통과 중...", mistRect.centerX(), mistRect.centerY() + 5f * s, mistPaint)
        }
    }

    /**
     * 4. 니어핀 퍼팅 치킨게임: 상단 연못 해저드 + 그린 동심원 + 깃대 홀컵 + 참가자별 퍼팅 궤적
     */
    private fun drawNearPinPuttingStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.NearPinPutting,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // 상단 연못 해저드 영역 (y < 0.23)
        val pondBottom = rect.top + rect.height() * 0.24f
        val pondRect = RectF(rect.left + 14f * s, rect.top + 10f * s, rect.right - 14f * s, pondBottom)
        fill.color = COL_SKY_SOFT
        canvas.drawRoundRect(pondRect, 14f * s, 14f * s, fill)
        stroke.color = COL_SKY_BLUE
        stroke.strokeWidth = 1.5f * s
        canvas.drawRoundRect(pondRect, 14f * s, 14f * s, stroke)

        val pondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_SKY_BLUE
            textSize = 12f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText("WATER HAZARD · 연못 해저드", pondRect.left + 12f * s, pondRect.top + 20f * s, pondPaint)

        // 퍼팅 그린 타원
        val greenRect = RectF(
            rect.left + 20f * s,
            pondBottom + 6f * s,
            rect.right - 20f * s,
            rect.bottom - 12f * s
        )
        fill.color = COL_MEADOW_SOFT
        canvas.drawRoundRect(greenRect, 28f * s, 28f * s, fill)
        stroke.color = COL_MEADOW_GREEN
        stroke.strokeWidth = 1.8f * s
        canvas.drawRoundRect(greenRect, 28f * s, 28f * s, stroke)

        // 홀컵 좌표 (정규화 0.50, 0.30 기준)
        val cupX = rect.left + rect.width() * 0.50f
        val cupY = rect.top + rect.height() * 0.33f
        stroke.color = applyAlpha(COL_MEADOW_GREEN, 95)
        stroke.strokeWidth = 1.2f * s
        canvas.drawCircle(cupX, cupY, 38f * s, stroke)
        canvas.drawCircle(cupX, cupY, 76f * s, stroke)

        fill.color = COL_PINE_DEEP
        canvas.drawCircle(cupX, cupY, 8f * s, fill)
        // 깃대
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 2.5f * s
        canvas.drawLine(cupX, cupY, cupX, cupY - 38f * s, stroke)
        val flagPath = Path().apply {
            moveTo(cupX, cupY - 38f * s)
            lineTo(cupX + 22f * s, cupY - 30f * s)
            lineTo(cupX, cupY - 22f * s)
            close()
        }
        fill.color = COL_TERRACOTTA
        canvas.drawPath(flagPath, fill)

        // 참가자별 퍼팅 샷 애니메이션
        val shots = data.shots
        if (shots.isEmpty()) return
        val startX = rect.left + rect.width() * 0.50f
        val startY = rect.bottom - 24f * s
        val u = state.actionProgress.coerceIn(0f, 1f)

        val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        shots.forEachIndexed { idx, shot ->
            val segStart = (idx.toFloat() / shots.size.toFloat()) * 0.72f
            val segSpan = (1f - 0.72f) + (0.72f / shots.size.toFloat())
            val localU = ((u - segStart) / segSpan.coerceAtLeast(0.18f)).coerceIn(0f, 1f)
            if (localU <= 0.001f && state.phase != TimelinePhase.FINAL_REVEAL) return@forEachIndexed

            val eased = 1f - (1f - localU).pow(2.8f)
            val tx = rect.left + 28f * s + shot.finalX.coerceIn(0.08f, 0.92f) * (rect.width() - 56f * s)
            val ty = rect.top + 18f * s + shot.finalY.coerceIn(0.08f, 0.90f) * (rect.height() - 36f * s)

            val bx = startX + (tx - startX) * eased
            val by = startY + (ty - startY) * eased

            // 점선 굴러간 흔적
            stroke.color = applyAlpha(shot.badgeColorArgb, 130)
            stroke.strokeWidth = 2f * s
            canvas.drawLine(startX, startY, bx, by, stroke)

            if (shot.inWater && eased > 0.92f) {
                stroke.color = COL_SKY_BLUE
                stroke.strokeWidth = 2f * s
                canvas.drawCircle(bx, by, 13f * s, stroke)
            } else {
                fill.color = COL_CARD_WHITE
                canvas.drawCircle(bx, by, 7.5f * s, fill)
                stroke.color = shot.badgeColorArgb
                stroke.strokeWidth = 2.2f * s
                canvas.drawCircle(bx, by, 7.5f * s, stroke)
            }

            if (eased > 0.85f || state.phase == TimelinePhase.FINAL_REVEAL) {
                val statusTxt = when {
                    shot.holeIn -> "${shot.playerName} 홀인원!"
                    shot.inWater -> "${shot.playerName} 풍덩"
                    else -> "${shot.playerName} ${shot.distanceCm}cm"
                }
                tagPaint.color = if (shot.inWater) COL_SKY_BLUE else COL_INK_PRIMARY
                canvas.drawText(statusTxt, bx, by - 11f * s, tagPaint)
            }
        }
    }

    /**
     * 5. 숲속 골프공 바구니: 3x3 라탄 바구니 속 9개 골프공 순차 오픈 -> 황금 도토리 공 당첨 공개
     */
    private fun drawBallBasketStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.BallBasket,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val cx = rect.centerX()
        val cy = rect.centerY() + 4f * s
        val basketR = min(rect.width(), rect.height()) * 0.43f

        // 오크 바구니 테두리
        fill.color = 0xFFE6DAC3.toInt()
        canvas.drawCircle(cx, cy, basketR, fill)
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 4f * s
        canvas.drawCircle(cx, cy, basketR, stroke)

        val pickedOrder = data.pickedBallOrder
        val pickedNames = data.pickedPlayerNames
        val totalPicked = pickedOrder.size.coerceAtLeast(1)
        val u = state.actionProgress.coerceIn(0f, 1f)
        val revealedSteps = if (state.phase == TimelinePhase.FINAL_REVEAL) {
            totalPicked
        } else {
            (u * totalPicked.toFloat()).toInt().coerceIn(0, totalPicked)
        }

        val stepSpacing = basketR * 0.54f
        val ballR = basketR * 0.21f
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        for (cellIdx in 0 until 9) {
            val r = cellIdx / 3 - 1
            val c = cellIdx % 3 - 1
            val bx = cx + c * stepSpacing
            val by = cy + r * stepSpacing

            val orderPos = pickedOrder.indexOf(cellIdx)
            val isOpened = orderPos >= 0 && orderPos < revealedSteps
            val isGoldenHit = isOpened && cellIdx == data.goldenBallIndex

            when {
                isGoldenHit -> {
                    fill.color = COL_AMBER_SOFT
                    canvas.drawCircle(bx, by, ballR * 1.16f, fill)
                    fill.color = COL_AMBER
                    canvas.drawCircle(bx, by, ballR, fill)
                    stroke.color = COL_TERRACOTTA
                    stroke.strokeWidth = 2.5f * s
                    canvas.drawCircle(bx, by, ballR, stroke)

                    txtPaint.color = COL_CARD_WHITE
                    canvas.drawText("당첨!", bx, by - 2f * s, txtPaint)
                    val pName = pickedNames.getOrElse(orderPos) { data.hitPlayerName }
                    canvas.drawText(ellipsizeText(pName, txtPaint, ballR * 1.8f), bx, by + 12f * s, txtPaint)
                }
                isOpened -> {
                    fill.color = COL_MEADOW_SOFT
                    canvas.drawCircle(bx, by, ballR, fill)
                    stroke.color = COL_MEADOW_GREEN
                    stroke.strokeWidth = 1.8f * s
                    canvas.drawCircle(bx, by, ballR, stroke)

                    txtPaint.color = COL_PINE_DEEP
                    val pName = pickedNames.getOrElse(orderPos) { "통과" }
                    canvas.drawText(ellipsizeText(pName, txtPaint, ballR * 1.8f), bx, by - 1f * s, txtPaint)
                    txtPaint.color = COL_MEADOW_GREEN
                    canvas.drawText("통과", bx, by + 12f * s, txtPaint)
                }
                else -> {
                    fill.color = COL_CARD_WHITE
                    canvas.drawCircle(bx, by, ballR, fill)
                    stroke.color = COL_DIVIDER
                    stroke.strokeWidth = 1.6f * s
                    canvas.drawCircle(bx, by, ballR, stroke)
                    txtPaint.color = COL_INK_MUTED
                    canvas.drawText("${cellIdx + 1}", bx, by + 4f * s, txtPaint)
                }
            }
        }
    }

    /**
     * 6. 행운 나무패 뒤집기: 2x2 / 3x2 자작나무 패가 순차적으로 3D 회전하며 앞면(A팀/B팀 또는 당첨/통과) 공개
     */
    private fun drawLuckyWoodTileStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.LuckyWoodTile,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val players = data.tilePlayerNames.ifEmpty { listOf("동반자1", "동반자2", "동반자3", "동반자4") }
        val count = players.size.coerceAtMost(6)
        val cols = if (count <= 4) 2 else 3
        val rows = (count + cols - 1) / cols

        val padX = 28f * s
        val padY = 26f * s
        val gapX = 16f * s
        val gapY = 14f * s
        val tileW = (rect.width() - padX * 2f - gapX * (cols - 1)) / cols.toFloat()
        val tileH = (rect.height() - padY * 2f - gapY * (rows - 1)) / rows.toFloat()

        val u = state.actionProgress.coerceIn(0f, 1f)
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 15f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
        }

        for (i in 0 until count) {
            val r = i / cols
            val c = i % cols
            val left = rect.left + padX + c * (tileW + gapX)
            val top = rect.top + padY + r * (tileH + gapY)
            val cx = left + tileW * 0.5f
            val cy = top + tileH * 0.5f

            val flipStart = 0.20f + i * (0.62f / count.toFloat())
            val flipFrac = if (state.phase == TimelinePhase.FINAL_REVEAL) {
                1f
            } else {
                ((u - flipStart) / 0.22f).coerceIn(0f, 1f)
            }
            val scaleX = abs(cos(flipFrac * PI)).toFloat().coerceAtLeast(0.08f)
            val showFront = flipFrac >= 0.5f

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scaleX, 1f)
            val localRect = RectF(-tileW * 0.5f, -tileH * 0.5f, tileW * 0.5f, tileH * 0.5f)

            if (!showFront) {
                // 나무패 뒷면 (소나무 잎 각인 + 참가자 선택 표시)
                fill.color = COL_OAK_WOOD
                canvas.drawRoundRect(localRect, 14f * s, 14f * s, fill)
                stroke.color = COL_BIRCH_WOOD
                stroke.strokeWidth = 2f * s
                canvas.drawRoundRect(localRect, 14f * s, 14f * s, stroke)

                titlePaint.color = COL_CARD_WHITE
                canvas.drawText(ellipsizeText(players[i], titlePaint, tileW - 16f * s), 0f, -2f * s, titlePaint)
                subPaint.color = COL_BIRCH_WOOD
                canvas.drawText("선택 나무패", 0f, 18f * s, subPaint)
            } else {
                val isHi = data.tileHighlights.getOrElse(i) { false }
                val isTeamB = data.tileIsTeamB.getOrElse(i) { false }
                val roleTitle = data.tileRoleTitles.getOrElse(i) { "공개" }
                val roleSub = data.tileRoleSubtitles.getOrElse(i) { "" }

                fill.color = when {
                    data.isTeamSplitMode && isTeamB -> COL_TERRACOTTA_SOFT
                    data.isTeamSplitMode -> COL_MEADOW_SOFT
                    isHi -> COL_AMBER_SOFT
                    else -> COL_CARD_WHITE
                }
                canvas.drawRoundRect(localRect, 14f * s, 14f * s, fill)

                stroke.color = when {
                    data.isTeamSplitMode && isTeamB -> COL_TERRACOTTA
                    data.isTeamSplitMode -> COL_MEADOW_GREEN
                    isHi -> COL_AMBER
                    else -> COL_DIVIDER
                }
                stroke.strokeWidth = 2.2f * s
                canvas.drawRoundRect(localRect, 14f * s, 14f * s, stroke)

                subPaint.color = COL_INK_SECONDARY
                canvas.drawText(ellipsizeText(players[i], subPaint, tileW - 14f * s), 0f, -18f * s, subPaint)

                titlePaint.color = when {
                    data.isTeamSplitMode && isTeamB -> COL_TERRACOTTA
                    isHi -> COL_PINE_DEEP
                    else -> COL_INK_PRIMARY
                }
                canvas.drawText(ellipsizeText(roleTitle, titlePaint, tileW - 14f * s), 0f, 5f * s, titlePaint)

                if (roleSub.isNotBlank()) {
                    subPaint.color = COL_INK_SECONDARY
                    canvas.drawText(ellipsizeText(roleSub, subPaint, tileW - 14f * s), 0f, 24f * s, subPaint)
                }
            }
            canvas.restore()
        }
    }

    /**
     * 7. 행운의 더블 주사위: 상단 동반자별 스코어칩 + 하단 참나무 트레이 위 더블 주사위 굴림 및 눈금 확정
     */
    private fun drawDoubleDiceStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.DoubleDice,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val rolls = data.rollsInPlayerOrder
        val u = state.actionProgress.coerceIn(0f, 1f)
        val activeRollCount = if (state.phase == TimelinePhase.FINAL_REVEAL) {
            rolls.size
        } else {
            (u * (rolls.size + 0.6f)).toInt().coerceIn(0, rolls.size)
        }

        // 상단 참가자 스코어보드 (최대 6명 3열 2행)
        val cols = 3
        val gap = 8f * s
        val cardW = (rect.width() - 32f * s - gap * (cols - 1)) / cols.toFloat()
        val cardH = 38f * s
        val txtPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        rolls.take(6).forEachIndexed { idx, roll ->
            val r = idx / cols
            val c = idx % cols
            val cRect = RectF(
                rect.left + 16f * s + c * (cardW + gap),
                rect.top + 12f * s + r * (cardH + 6f * s),
                rect.left + 16f * s + c * (cardW + gap) + cardW,
                rect.top + 12f * s + r * (cardH + 6f * s) + cardH
            )
            val revealed = idx < activeRollCount
            fill.color = when {
                revealed && roll.isLeader && state.phase == TimelinePhase.FINAL_REVEAL -> COL_AMBER_SOFT
                revealed -> COL_CARD_WHITE
                else -> COL_BG_CREAM
            }
            canvas.drawRoundRect(cRect, 9f * s, 9f * s, fill)
            stroke.color = if (revealed) roll.badgeColorArgb else COL_DIVIDER
            stroke.strokeWidth = if (revealed && roll.isLeader) 2f * s else 1.2f * s
            canvas.drawRoundRect(cRect, 9f * s, 9f * s, stroke)

            txtPaint.color = COL_INK_PRIMARY
            val label = if (revealed) {
                val dbl = if (roll.isDouble) " 더블" else ""
                "${roll.playerName} ${roll.sum}점$dbl"
            } else {
                "${roll.playerName} 대기"
            }
            canvas.drawText(ellipsizeText(label, txtPaint, cardW - 10f * s), cRect.centerX(), cRect.centerY() + 4f * s, txtPaint)
        }

        // 하단 우드 트레이 + 주사위 2개 굴림 연출
        val trayTop = rect.top + 104f * s
        val trayRect = RectF(rect.left + 36f * s, trayTop, rect.right - 36f * s, rect.bottom - 14f * s)
        fill.color = 0xFFE8DEC9.toInt()
        canvas.drawRoundRect(trayRect, 24f * s, 24f * s, fill)
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 4f * s
        canvas.drawRoundRect(trayRect, 24f * s, 24f * s, stroke)

        val settling = u >= 0.86f || state.phase == TimelinePhase.FINAL_REVEAL
        val dieSize = min(trayRect.height() * 0.56f, 94f * s)
        val bounceY = if (!settling && u > 0.01f) -abs(sin(u * 28f)) * 18f * s else 0f
        val tiltDeg = if (!settling && u > 0.01f) sin(u * 34f) * 22f else 0f

        val shownDie1 = if (settling) data.winnerDie1 else (((u * 37f).toInt() % 6) + 1)
        val shownDie2 = if (settling) data.winnerDie2 else ((((u * 43f).toInt() + 2) % 6) + 1)

        drawSingleDie(
            canvas = canvas,
            cx = trayRect.centerX() - dieSize * 0.68f,
            cy = trayRect.centerY() - 6f * s + bounceY,
            size = dieSize,
            value = shownDie1.coerceIn(1, 6),
            tiltDeg = tiltDeg,
            s = s
        )
        drawSingleDie(
            canvas = canvas,
            cx = trayRect.centerX() + dieSize * 0.68f,
            cy = trayRect.centerY() - 6f * s - bounceY * 0.7f,
            size = dieSize,
            value = shownDie2.coerceIn(1, 6),
            tiltDeg = -tiltDeg * 0.85f,
            s = s
        )

        if (settling && data.winnerName.isNotBlank()) {
            val winPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_PINE_DEEP
                textSize = 13.5f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                "최고 눈금: ${data.winnerName} (${data.winnerDie1} + ${data.winnerDie2} = ${data.winnerDie1 + data.winnerDie2})",
                trayRect.centerX(),
                trayRect.bottom - 12f * s,
                winPaint
            )
        }
    }

    private fun drawSingleDie(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        value: Int,
        tiltDeg: Float,
        s: Float
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(tiltDeg)

        val r = RectF(-size * 0.5f, -size * 0.5f, size * 0.5f, size * 0.5f)
        fill.color = COL_CARD_WHITE
        canvas.drawRoundRect(r, 16f * s, 16f * s, fill)
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 2.5f * s
        canvas.drawRoundRect(r, 16f * s, 16f * s, stroke)

        val off = size * 0.25f
        val pipR = size * 0.082f
        val coords: List<Pair<Float, Float>> = when (value) {
            1 -> listOf(0f to 0f)
            2 -> listOf(-off to -off, off to off)
            3 -> listOf(-off to -off, 0f to 0f, off to off)
            4 -> listOf(-off to -off, off to -off, -off to off, off to off)
            5 -> listOf(-off to -off, off to -off, 0f to 0f, -off to off, off to off)
            else -> listOf(-off to -off, off to -off, -off to 0f, off to 0f, -off to off, off to off)
        }
        fill.color = if (value == 1 || value == 4) COL_TERRACOTTA else COL_INK_PRIMARY
        coords.forEach { (px, py) ->
            canvas.drawCircle(px, py, if (value == 1) pipR * 1.35f else pipR, fill)
        }
        canvas.restore()
    }

    /**
     * 8. 잔디밭 민속 윷놀이: 상단 10칸 말판 전진 트랙 + 하단 멍석 위 자작나무 윷가락 4개 투구
     */
    private fun drawYutNoriStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.YutNori,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val u = state.actionProgress.coerceIn(0f, 1f)

        // 상단 윷판 레이스 트랙 (-1칸 ~ 10칸 완주)
        val trackRect = RectF(rect.left + 16f * s, rect.top + 12f * s, rect.right - 16f * s, rect.top + 88f * s)
        fill.color = COL_MEADOW_SOFT
        canvas.drawRoundRect(trackRect, 14f * s, 14f * s, fill)
        stroke.color = COL_MEADOW_GREEN
        stroke.strokeWidth = 1.5f * s
        canvas.drawRoundRect(trackRect, 14f * s, 14f * s, stroke)

        val stepStart = trackRect.left + 26f * s
        val stepEnd = trackRect.right - 26f * s
        val totalCells = 12 // -1..10
        val cellStep = (stepEnd - stepStart) / (totalCells - 1).toFloat()
        val trackLineY = trackRect.top + 48f * s

        stroke.color = applyAlpha(COL_PINE_DEEP, 90)
        stroke.strokeWidth = 2f * s
        canvas.drawLine(stepStart, trackLineY, stepEnd, trackLineY, stroke)

        val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_INK_SECONDARY
            textSize = 9.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        for (c in 0 until totalCells) {
            val stepVal = c - 1
            val sx = stepStart + c * cellStep
            fill.color = when (stepVal) {
                0 -> COL_CARD_WHITE
                10 -> COL_TERRACOTTA
                else -> COL_WARM_IVORY
            }
            canvas.drawCircle(sx, trackLineY, if (stepVal == 10 || stepVal == 0) 8f * s else 5.5f * s, fill)
            val lbl = when (stepVal) {
                -1 -> "빽"
                0 -> "출발"
                10 -> "홀컵"
                else -> "$stepVal"
            }
            canvas.drawText(lbl, sx, trackRect.bottom - 8f * s, cellPaint)
        }

        // 참가자 말 토큰 이동
        data.statesInOrder.forEachIndexed { idx, st ->
            val curStep = (st.finalPosition.coerceIn(-1, 10).toFloat() * u)
            val normIdx = (curStep + 1f).coerceIn(0f, 11f)
            val mx = stepStart + normIdx * cellStep
            val my = trackLineY - 12f * s - (idx % 2) * 11f * s
            fill.color = st.badgeColorArgb
            canvas.drawCircle(mx, my, 7.5f * s, fill)
            stroke.color = COL_CARD_WHITE
            stroke.strokeWidth = 1.5f * s
            canvas.drawCircle(mx, my, 7.5f * s, stroke)
        }

        // 하단 멍석 원형 매트 + 자작나무 윷가락 4개
        val matCy = (trackRect.bottom + rect.bottom) * 0.5f + 2f * s
        val matRx = (rect.width() - 64f * s) * 0.5f
        val matRy = (rect.bottom - trackRect.bottom - 24f * s) * 0.48f
        val matRect = RectF(rect.centerX() - matRx, matCy - matRy, rect.centerX() + matRx, matCy + matRy)
        fill.color = 0xFFEADBC0.toInt()
        canvas.drawOval(matRect, fill)
        stroke.color = COL_OAK_WOOD
        stroke.strokeWidth = 3f * s
        canvas.drawOval(matRect, stroke)

        val sticks = data.finalSticksFlat.takeIf { it.size == 4 } ?: listOf(true, true, true, false)
        val stickSettled = u >= 0.84f || state.phase == TimelinePhase.FINAL_REVEAL
        val stickW = 28f * s
        val stickH = min(matRy * 1.45f, 118f * s)
        val stickGap = 22f * s

        for (i in 0 until 4) {
            val baseX = rect.centerX() + (i - 1.5f) * (stickW + stickGap)
            val tossY = if (!stickSettled && u > 0.02f) -abs(sin(u * 24f + i * 0.6f)) * 22f * s else 0f
            val rot = if (!stickSettled && u > 0.02f) sin(u * 30f + i) * 18f else (i - 1.5f) * 3.5f
            val isFlat = if (stickSettled) sticks[i] else (((u * 25f + i).toInt() % 2) == 0)

            canvas.save()
            canvas.translate(baseX, matCy + tossY)
            canvas.rotate(rot)
            val sRect = RectF(-stickW * 0.5f, -stickH * 0.5f, stickW * 0.5f, stickH * 0.5f)
            if (isFlat) {
                // 평평한 배 면 (0번 윷가락은 빽도 붉은 점 표시)
                fill.color = COL_CARD_WHITE
                canvas.drawRoundRect(sRect, 10f * s, 10f * s, fill)
                stroke.color = COL_OAK_WOOD
                stroke.strokeWidth = 2f * s
                canvas.drawRoundRect(sRect, 10f * s, 10f * s, stroke)
                if (i == 0) {
                    fill.color = COL_TERRACOTTA
                    canvas.drawCircle(0f, 0f, 5.5f * s, fill)
                }
            } else {
                // 둥근 등 면 (전통 X 문양)
                fill.color = COL_OAK_WOOD
                canvas.drawRoundRect(sRect, 12f * s, 12f * s, fill)
                stroke.color = COL_BIRCH_WOOD
                stroke.strokeWidth = 1.8f * s
                for (m in -1..1) {
                    val my = m * (stickH * 0.24f)
                    canvas.drawLine(-6f * s, my - 5f * s, 6f * s, my + 5f * s, stroke)
                    canvas.drawLine(6f * s, my - 5f * s, -6f * s, my + 5f * s, stroke)
                }
            }
            canvas.restore()
        }

        if (stickSettled && data.headlineText.isNotBlank()) {
            val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COL_PINE_DEEP
                textSize = 13f * s
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                ellipsizeText(data.headlineText, headPaint, rect.width() - 40f * s),
                rect.centerX(),
                rect.bottom - 8f * s,
                headPaint
            )
        }
    }

    /**
     * 9. 숲속 오두막 타로 운세: 22장 메이저 아르카나 카드가 별빛 뒷면에서 3D 회전 오픈되며 문양·점수·등수 표시
     */
    private fun drawTarotFortuneStage(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        data: GameExtraSceneData.TarotFortune,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        // 상단 운세 주제 배너
        val topicPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_PINE_DEEP
            textSize = 13.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        canvas.drawText(
            "선택 운세: ${data.topicLabel} · 22장 메이저 아르카나 리딩",
            rect.left + 18f * s,
            rect.top + 24f * s,
            topicPaint
        )

        val readings = data.readings.take(6)
        if (readings.isEmpty()) return

        val count = readings.size
        val cols = when {
            count <= 3 -> count
            count == 4 -> 4
            else -> 3
        }
        val rows = (count + cols - 1) / cols
        val areaTop = rect.top + 36f * s
        val areaBottom = rect.bottom - 14f * s
        val padX = 18f * s
        val gapX = 10f * s
        val gapY = 10f * s
        val cardW = (rect.width() - padX * 2f - gapX * (cols - 1)) / cols.toFloat()
        val cardH = ((areaBottom - areaTop) - gapY * (rows - 1)) / rows.toFloat()

        val u = state.actionProgress.coerceIn(0f, 1f)
        val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11f * s
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        val scorePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 11.5f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        readings.forEachIndexed { idx, reading ->
            val r = idx / cols
            val c = idx % cols
            val cx = rect.left + padX + c * (cardW + gapX) + cardW * 0.5f
            val cy = areaTop + r * (cardH + gapY) + cardH * 0.5f

            val flipStart = 0.18f + idx * (0.64f / count.toFloat())
            val flipFrac = if (state.phase == TimelinePhase.FINAL_REVEAL) {
                1f
            } else {
                ((u - flipStart) / 0.22f).coerceIn(0f, 1f)
            }
            val scaleX = abs(cos(flipFrac * PI)).toFloat().coerceAtLeast(0.08f)
            val showFront = flipFrac >= 0.5f

            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(scaleX, 1f)
            val cRect = RectF(-cardW * 0.5f, -cardH * 0.5f, cardW * 0.5f, cardH * 0.5f)

            if (!showFront) {
                // 타로 카드 뒷면 (솔방울 그린 + 황금 별문양)
                fill.color = COL_PINE_DEEP
                canvas.drawRoundRect(cRect, 12f * s, 12f * s, fill)
                stroke.color = COL_AMBER
                stroke.strokeWidth = 2f * s
                canvas.drawRoundRect(cRect, 12f * s, 12f * s, stroke)

                drawTarotEmblemSimple(canvas, 0f, -6f * s, min(cardW, cardH) * 0.22f, TarotEmblemType.STAR_EIGHT, COL_AMBER, s)
                namePaint.color = COL_CARD_WHITE
                canvas.drawText(ellipsizeText(reading.playerName, namePaint, cardW - 10f * s), 0f, cardH * 0.30f, namePaint)
            } else {
                val isFirst = reading.rankOneBased == 1
                fill.color = if (isFirst) COL_AMBER_SOFT else COL_CARD_WHITE
                canvas.drawRoundRect(cRect, 12f * s, 12f * s, fill)
                stroke.color = if (isFirst) COL_AMBER else COL_OAK_WOOD
                stroke.strokeWidth = if (isFirst) 2.4f * s else 1.5f * s
                canvas.drawRoundRect(cRect, 12f * s, 12f * s, stroke)

                numPaint.color = COL_TERRACOTTA
                canvas.drawText(
                    "${reading.card.romanNumeral} · ${if (reading.isUpright) "정" else "역"}",
                    0f,
                    -cardH * 0.33f,
                    numPaint
                )

                val emblemR = min(cardW, cardH) * 0.19f
                drawTarotEmblemSimple(
                    canvas = canvas,
                    cx = 0f,
                    cy = -cardH * 0.05f,
                    r = emblemR,
                    emblem = reading.card.emblem,
                    color = if (isFirst) COL_TERRACOTTA else COL_PINE_DEEP,
                    s = s
                )

                namePaint.color = COL_INK_PRIMARY
                canvas.drawText(
                    ellipsizeText(reading.card.koreanName, namePaint, cardW - 10f * s),
                    0f,
                    cardH * 0.22f,
                    namePaint
                )

                scorePaint.color = if (isFirst) COL_TERRACOTTA else COL_MEADOW_GREEN
                canvas.drawText(
                    "${reading.playerName} ${reading.fortuneScore}점(${reading.rankOneBased}위)",
                    0f,
                    cardH * 0.39f,
                    scorePaint
                )
            }
            canvas.restore()
        }
    }

    private fun drawTarotEmblemSimple(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        emblem: TarotEmblemType,
        color: Int,
        s: Float
    ) {
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            this.color = color
            strokeWidth = 2f * s
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }

        when (emblem) {
            TarotEmblemType.SUN_RADIANT -> {
                canvas.drawCircle(cx, cy, r * 0.52f, stroke)
                for (i in 0 until 8) {
                    val rad = i * (PI / 4.0)
                    canvas.drawLine(
                        cx + cos(rad).toFloat() * r * 0.65f,
                        cy + sin(rad).toFloat() * r * 0.65f,
                        cx + cos(rad).toFloat() * r,
                        cy + sin(rad).toFloat() * r,
                        stroke
                    )
                }
            }
            TarotEmblemType.PRIESTESS_CRESCENT, TarotEmblemType.MOON_LAKE -> {
                canvas.drawCircle(cx, cy, r * 0.72f, stroke)
                fill.color = COL_CARD_WHITE
                canvas.drawCircle(cx + r * 0.28f, cy - r * 0.12f, r * 0.56f, fill)
            }
            TarotEmblemType.WHEEL_FORTUNE, TarotEmblemType.WORLD_WREATH -> {
                canvas.drawCircle(cx, cy, r * 0.82f, stroke)
                canvas.drawCircle(cx, cy, r * 0.32f, stroke)
                canvas.drawLine(cx - r * 0.82f, cy, cx + r * 0.82f, cy, stroke)
                canvas.drawLine(cx, cy - r * 0.82f, cx, cy + r * 0.82f, stroke)
            }
            else -> {
                // 8각 별 심볼
                val path = Path()
                for (i in 0 until 8) {
                    val rad = -PI / 2.0 + i * (PI / 4.0)
                    val rr = if (i % 2 == 0) r * 0.90f else r * 0.38f
                    val px = cx + cos(rad).toFloat() * rr
                    val py = cy + sin(rad).toFloat() * rr
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                canvas.drawPath(path, stroke)
            }
        }
    }

    private fun drawGenericStageFallback(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        payload: CommonGameSharePayload,
        state: GameVideoFrameState
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

        val cx = rect.centerX()
        val cy = rect.centerY()
        val r = min(rect.width(), rect.height()) * 0.32f
        fill.color = COL_MEADOW_SOFT
        canvas.drawCircle(cx, cy, r, fill)
        stroke.color = COL_MEADOW_GREEN
        stroke.strokeWidth = 3f * s
        canvas.drawCircle(cx, cy, r, stroke)

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COL_PINE_DEEP
            textSize = 20f * s
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(payload.gameType.title, cx, cy + 7f * s, p)
        if (state.isSlowMotion) {
            p.textSize = 14f * s
            p.color = COL_TERRACOTTA
            canvas.drawText("결정적 순간", cx, cy + 32f * s, p)
        }
    }

    private fun drawCelebrationPetals(
        canvas: Canvas,
        rect: RectF,
        s: Float,
        confettiTimeSec: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val colors = intArrayOf(COL_AMBER, COL_TERRACOTTA, COL_MEADOW_GREEN, COL_SKY_BLUE)
        for (i in 0 until 18) {
            val seedX = ((i * 73 + 19) % 100) / 100f
            val baseSpeed = 38f + (i % 5) * 14f
            val x = rect.left + 20f * s + seedX * (rect.width() - 40f * s) + sin(confettiTimeSec * 4f + i) * 10f * s
            val y = rect.top + ((confettiTimeSec * baseSpeed * s + i * 24f * s) % (rect.height() - 16f * s))
            paint.color = applyAlpha(colors[i % colors.size], 185)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(confettiTimeSec * 90f + i * 25f)
            canvas.drawOval(RectF(-5.5f * s, -3f * s, 5.5f * s, 3f * s), paint)
            canvas.restore()
        }
    }

    private fun samplePolylineProgress(
        points: List<Pair<Float, Float>>,
        progress: Float
    ): Triple<Float, Float, Path> {
        val path = Path()
        if (points.isEmpty()) return Triple(0f, 0f, path)
        if (points.size == 1) {
            path.moveTo(points[0].first, points[0].second)
            return Triple(points[0].first, points[0].second, path)
        }

        val segLengths = FloatArray(points.size - 1)
        var totalLen = 0f
        for (i in 0 until points.size - 1) {
            val dx = points[i + 1].first - points[i].first
            val dy = points[i + 1].second - points[i].second
            val d = kotlin.math.sqrt(dx * dx + dy * dy)
            segLengths[i] = d
            totalLen += d
        }

        path.moveTo(points[0].first, points[0].second)
        if (totalLen <= 0.001f) {
            return Triple(points.last().first, points.last().second, path)
        }

        val targetDist = totalLen * progress.coerceIn(0f, 1f)
        var acc = 0f
        for (i in segLengths.indices) {
            val seg = segLengths[i]
            if (acc + seg >= targetDist) {
                val local = if (seg > 0.0001f) (targetDist - acc) / seg else 1f
                val x = points[i].first + (points[i + 1].first - points[i].first) * local
                val y = points[i].second + (points[i + 1].second - points[i].second) * local
                path.lineTo(x, y)
                return Triple(x, y, path)
            } else {
                path.lineTo(points[i + 1].first, points[i + 1].second)
                acc += seg
            }
        }
        val last = points.last()
        return Triple(last.first, last.second, path)
    }

    private fun applyAlpha(argb: Int, alpha: Int): Int {
        val a = alpha.coerceIn(0, 255)
        return (argb and 0x00FFFFFF) or (a shl 24)
    }

    private fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (maxWidth <= 0f || paint.measureText(text) <= maxWidth) return text
        val suffix = "..."
        var end = text.length
        while (end > 1 && paint.measureText(text.substring(0, end) + suffix) > maxWidth) {
            end--
        }
        return text.substring(0, end) + suffix
    }
}
