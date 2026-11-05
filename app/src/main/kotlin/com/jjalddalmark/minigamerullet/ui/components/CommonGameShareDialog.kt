package com.jjalddalmark.minigamerullet.ui.components

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.model.RouletteVideoQuality
import com.jjalddalmark.minigamerullet.share.CommonGameMediaExporter
import com.jjalddalmark.minigamerullet.share.CommonGameSharePayload
import com.jjalddalmark.minigamerullet.share.RouletteMediaExporter
import com.jjalddalmark.minigamerullet.share.RouletteShareChannel
import com.jjalddalmark.minigamerullet.share.RouletteShareManager
import com.jjalddalmark.minigamerullet.ui.theme.InkMuted
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.PaperCream
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondBlue
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondSoft
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import com.jjalddalmark.minigamerullet.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 전체 미니게임(룰렛 외 9종 공통) 결과 이미지(JPEG) & 전 과정 진행 영상(MP4) 미리보기 및 메신저 발송 다이얼로그
 */
@Composable
fun CommonGameShareDialog(
    payload: CommonGameSharePayload,
    soundEnabled: Boolean = true,
    soundEngine: GhibliSoundEngine? = null,
    initialTab: Int = 0,
    onReplay: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    // 0: 고해상도 결과 이미지(JPEG) 공유, 1: 셋팅~진행~결정 전 과정 영상(MP4) 공유
    var shareTab by remember(payload.id) { mutableIntStateOf(initialTab.coerceIn(0, 1)) }
    var imageFile by remember(payload.id) { mutableStateOf<File?>(null) }
    var isImageGenerating by remember(payload.id) { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf(RouletteVideoQuality.HD_720P) }
    var videoFilesByQuality by remember(payload.id) {
        mutableStateOf<Map<RouletteVideoQuality, File>>(emptyMap())
    }
    var isVideoEncoding by remember(payload.id) { mutableStateOf(false) }
    var videoProgress by remember(payload.id) { mutableFloatStateOf(0f) }
    var videoErrorText by remember(payload.id) { mutableStateOf<String?>(null) }

    // 모달 오픈 즉시 결과 카드 이미지(1080x1350) 자동 렌더링
    LaunchedEffect(payload.id) {
        isImageGenerating = true
        runCatching {
            CommonGameMediaExporter.renderResultImageToCache(
                context = context,
                payload = payload
            )
        }.onSuccess { file ->
            imageFile = file
        }
        isImageGenerating = false
    }

    fun ensureVideoGenerated(
        quality: RouletteVideoQuality,
        afterReady: ((File) -> Unit)? = null
    ) {
        val cached = videoFilesByQuality[quality]
        if (cached != null && cached.exists() && cached.length() > 0L) {
            afterReady?.invoke(cached)
            return
        }
        if (isVideoEncoding) return

        scope.launch {
            isVideoEncoding = true
            videoProgress = 0.02f
            videoErrorText = null
            runCatching {
                CommonGameMediaExporter.exportGameVideoToCache(
                    context = context,
                    payload = payload,
                    quality = quality,
                    includeSound = soundEnabled,
                    onProgress = { p -> videoProgress = p }
                )
            }.onSuccess { mp4 ->
                videoFilesByQuality = videoFilesByQuality + (quality to mp4)
                isVideoEncoding = false
                afterReady?.invoke(mp4)
            }.onFailure { err ->
                isVideoEncoding = false
                videoErrorText = "영상 생성 중 오류가 발생했습니다: ${err.localizedMessage ?: "알 수 없는 오류"}"
            }
        }
    }

    // 영상 공유 탭 진입 시 자동 MP4 인코딩 시작
    LaunchedEffect(shareTab, selectedQuality, payload.id) {
        if (shareTab == 1) {
            ensureVideoGenerated(selectedQuality)
        }
    }

    val primary = payload.primaryRow

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = screenHeightDp * 0.90f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 상단 대표 결과 요약 카드
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PureCardWhite)
                        .border(1.8.dp, MeadowGreen, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${payload.gameType.title} · 사진 & 영상 공유",
                            style = MaterialTheme.typography.labelLarge,
                            color = TerracottaWood,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (primary != null) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SunlightSoft)
                                    .border(1.dp, SunlightAmber, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = primary.badgeText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (primary != null) {
                            "${primary.playerName} · ${primary.detailText}"
                        } else {
                            payload.resultTitle
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = payload.resultSubtitle,
                        fontSize = 12.sp,
                        color = InkSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (payload.rows.size > 1) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            payload.rows.forEachIndexed { idx, row ->
                                val col = distinctRankBadgeColor(idx)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(WarmIvory)
                                        .border(1.dp, col.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "${row.badgeText} ${row.playerName}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = InkPrimary
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 이미지 공유 / 전 과정 영상 공유 탭 전환
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    CommonShareModeTabButton(
                        title = "결과 이미지로 공유",
                        subtitle = "선명한 결과 카드 사진",
                        selected = shareTab == 0,
                        onClick = {
                            soundEngine?.playWoodTick(true)
                            shareTab = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                    CommonShareModeTabButton(
                        title = "전 과정 진행 영상 공유",
                        subtitle = "셋팅~진행~결정 MP4",
                        selected = shareTab == 1,
                        onClick = {
                            soundEngine?.playWoodTick(true)
                            shareTab = 1
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 중앙 스크롤 미리보기 및 메신저 발송 영역
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (shareTab == 0) {
                        val readyImg = imageFile
                        if (readyImg != null && readyImg.exists()) {
                            val bmp = remember(readyImg.absolutePath) {
                                BitmapFactory.decodeFile(readyImg.absolutePath)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(235.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PureCardWhite)
                                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "미니게임 결과 카드 미리보기",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "고해상도 결과 이미지 준비 완료 (1080x1350 · ${formatCommonFileSize(readyImg.length())})",
                                fontSize = 11.5.sp,
                                color = InkSecondary
                            )
                        } else if (isImageGenerating) {
                            Text(
                                text = "결과 카드 이미지를 생성하고 있습니다...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        CommonMessengerShareGrid(
                            enabled = imageFile != null && !isImageGenerating,
                            onSelectChannel = { channel ->
                                val f = imageFile ?: return@CommonMessengerShareGrid
                                soundEngine?.playWoodTick(true)
                                RouletteShareManager.shareCommonGameMediaFile(
                                    context = context,
                                    file = f,
                                    mimeType = "image/jpeg",
                                    payload = payload,
                                    isVideo = false,
                                    channel = channel
                                )
                            },
                            onSaveToGallery = {
                                val f = imageFile ?: return@CommonMessengerShareGrid
                                scope.launch {
                                    val savedUri = RouletteMediaExporter.saveMediaToGallery(context, f, isVideo = false)
                                    val msg = if (savedUri != null) {
                                        "갤러리(Pictures/MinigameRullet)에 결과 이미지를 저장했습니다."
                                    } else {
                                        "갤러리 저장에 실패했습니다."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        Text(
                            text = "영상 화질 및 용량 옵션",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RouletteVideoQuality.entries.forEach { q ->
                                val sel = selectedQuality == q
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) MeadowGreenSoft else PureCardWhite)
                                        .border(
                                            if (sel) 1.6.dp else 1.dp,
                                            if (sel) MeadowGreen else SubtleDivider,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(enabled = !isVideoEncoding) {
                                            selectedQuality = q
                                            soundEngine?.playWoodTick(true)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = q.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sel) PineDeep else InkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = q.subtitle,
                                        fontSize = 10.5.sp,
                                        color = InkSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val readyVideo = videoFilesByQuality[selectedQuality]
                        if (isVideoEncoding) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "셋팅 -> 게임 진행 -> 최종 결정 전 과정을 영상으로 인코딩 중... ${(videoProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PineDeep
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { videoProgress.coerceIn(0f, 1f) },
                                    color = MeadowGreen,
                                    trackColor = WarmIvory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "고효율 H.264 하드웨어 압축으로 화질 손실 없이 용량을 최적화하고 있습니다.",
                                    fontSize = 11.sp,
                                    color = InkSecondary
                                )
                            }
                        } else if (readyVideo != null && readyVideo.exists()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(235.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                    .padding(6.dp)
                            ) {
                                CommonMp4PreviewPlayer(
                                    videoFile = readyVideo,
                                    modifier = Modifier
                                        .width(132.dp)
                                        .height(222.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "전 과정 영상 준비 완료 (${selectedQuality.width}x${selectedQuality.height} · ${formatCommonFileSize(readyVideo.length())})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PineDeep
                            )
                        } else if (videoErrorText != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TerracottaSoft)
                                    .padding(12.dp)
                            ) {
                                Text(text = videoErrorText!!, fontSize = 12.sp, color = TerracottaWood)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(onClick = { ensureVideoGenerated(selectedQuality) }) {
                                    Text("다시 생성 시도", color = TerracottaWood)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        CommonMessengerShareGrid(
                            enabled = !isVideoEncoding,
                            onSelectChannel = { channel ->
                                ensureVideoGenerated(selectedQuality) { mp4File ->
                                    soundEngine?.playWoodTick(true)
                                    RouletteShareManager.shareCommonGameMediaFile(
                                        context = context,
                                        file = mp4File,
                                        mimeType = "video/mp4",
                                        payload = payload,
                                        isVideo = true,
                                        channel = channel
                                    )
                                }
                            },
                            onSaveToGallery = {
                                ensureVideoGenerated(selectedQuality) { mp4File ->
                                    scope.launch {
                                        val savedUri = RouletteMediaExporter.saveMediaToGallery(
                                            context = context,
                                            sourceFile = mp4File,
                                            isVideo = true
                                        )
                                        val msg = if (savedUri != null) {
                                            "갤러리(Movies/MinigameRullet)에 게임 진행 영상을 저장했습니다."
                                        } else {
                                            "갤러리 영상 저장에 실패했습니다."
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SubtleDivider)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleDivider),
                        modifier = Modifier
                            .weight(if (onReplay != null) 0.42f else 1f)
                            .height(46.dp)
                    ) {
                        Text("닫기", color = InkSecondary, fontWeight = FontWeight.SemiBold)
                    }
                    if (onReplay != null) {
                        Button(
                            onClick = {
                                onDismiss()
                                onReplay()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(0.58f)
                                .height(46.dp)
                        ) {
                            Text(
                                text = "다시 하기",
                                color = PureCardWhite,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CommonMp4PreviewPlayer(
    videoFile: File,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerHolder = remember(videoFile.absolutePath) { arrayOfNulls<MediaPlayer>(1) }

    DisposableEffect(lifecycleOwner, videoFile.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = playerHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { if (mp.isPlaying) mp.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching { if (!mp.isPlaying) mp.start() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val mp = playerHolder[0]
            playerHolder[0] = null
            if (mp != null) {
                runCatching { mp.stop() }
                runCatching { mp.release() }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val surface = Surface(st)
                        val mp = MediaPlayer()
                        playerHolder[0] = mp
                        runCatching {
                            mp.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build()
                            )
                            mp.setDataSource(videoFile.absolutePath)
                            mp.setSurface(surface)
                            mp.isLooping = true
                            mp.setVolume(0.7f, 0.7f)
                            mp.setOnPreparedListener { prepared ->
                                val vw = prepared.videoWidth.toFloat()
                                val vh = prepared.videoHeight.toFloat()
                                if (vw > 0f && vh > 0f && w > 0 && h > 0) {
                                    val viewAspect = w.toFloat() / h.toFloat()
                                    val vidAspect = vw / vh
                                    val sx = if (vidAspect > viewAspect) 1f else vidAspect / viewAspect
                                    val sy = if (vidAspect > viewAspect) viewAspect / vidAspect else 1f
                                    val m = Matrix()
                                    m.setScale(sx, sy, w * 0.5f, h * 0.5f)
                                    setTransform(m)
                                }
                                prepared.start()
                            }
                            mp.prepareAsync()
                        }.onFailure {
                            runCatching { surface.release() }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        val mp = playerHolder[0]
                        playerHolder[0] = null
                        if (mp != null) {
                            runCatching { mp.stop() }
                            runCatching { mp.release() }
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun CommonMessengerShareGrid(
    enabled: Boolean,
    onSelectChannel: (RouletteShareChannel) -> Unit,
    onSaveToGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "메신저로 바로 공유하기",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CommonShareChannelButton(
                label = "카카오톡",
                accentBg = SunlightSoft,
                accentBorder = SunlightAmber,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.KAKAOTALK) },
                modifier = Modifier.weight(1f)
            )
            CommonShareChannelButton(
                label = "문자 메시지",
                accentBg = MeadowGreenSoft,
                accentBorder = MeadowGreen,
                textColor = PineDeep,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.SMS_MMS) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            CommonShareChannelButton(
                label = "텔레그램",
                accentBg = SkyPondSoft,
                accentBorder = SkyPondBlue,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.TELEGRAM) },
                modifier = Modifier.weight(1f)
            )
            CommonShareChannelButton(
                label = "더보기 공유",
                accentBg = WarmIvory,
                accentBorder = SubtleDivider,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.SYSTEM_MORE) },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = onSaveToGallery,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text(
                text = "휴대폰 갤러리에 저장",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary
            )
        }
    }
}

@Composable
private fun CommonShareChannelButton(
    label: String,
    accentBg: Color,
    accentBorder: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) accentBg else WarmIvory)
            .border(1.2.dp, if (enabled) accentBorder else SubtleDivider, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) textColor else InkMuted
        )
    }
}

@Composable
private fun CommonShareModeTabButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PineDeep else WarmIvory)
            .border(1.dp, if (selected) PineDeep else SubtleDivider, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureCardWhite else InkPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = if (selected) PureCardWhite.copy(alpha = 0.84f) else InkSecondary,
            maxLines = 1
        )
    }
}

private fun formatCommonFileSize(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.KOREA, "%.0fKB", kb)
    val mb = kb / 1024.0
    return String.format(Locale.KOREA, "%.2fMB", mb)
}
