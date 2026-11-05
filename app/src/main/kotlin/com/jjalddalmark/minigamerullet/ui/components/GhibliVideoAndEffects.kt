package com.jjalddalmark.minigamerullet.ui.components

import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.annotation.DrawableRes
import androidx.annotation.RawRes
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjalddalmark.minigamerullet.R
import com.jjalddalmark.minigamerullet.model.GameCategory
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.PlayerInfo
import com.jjalddalmark.minigamerullet.ui.theme.CloverLeaf
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.PaperCream
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 각 미니게임에 매칭되는 지브리 동영상 클립 및 첫 프레임 포스터 명세
 */
data class GhibliGameMediaSpec(
    @param:RawRes val introVideoRes: Int,
    @param:DrawableRes val introPosterRes: Int,
    @param:RawRes val previewVideoRes: Int,
    @param:DrawableRes val previewPosterRes: Int,
    @param:RawRes val gameplayVideoRes: Int,
    @param:DrawableRes val gameplayPosterRes: Int,
    @param:RawRes val resultVideoRes: Int = R.raw.vid_group_cheer,
    @param:DrawableRes val resultPosterRes: Int = R.drawable.poster_group_cheer,
    val introCaption: String
)

fun GolfGameType.mediaSpec(): GhibliGameMediaSpec {
    return when (this) {
        GolfGameType.DECISION_ROULETTE_PLUS -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_tee_bounce,
            previewPosterRes = R.drawable.poster_tee_bounce,
            gameplayVideoRes = R.raw.vid_group_cheer,
            gameplayPosterRes = R.drawable.poster_group_cheer,
            introCaption = "항목과 확률, 반전 특수효과를 자유롭게 설정해 돌리는 만능 결정 룰렛+"
        )

        GolfGameType.TEE_BOUNCE -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_select_order,
            introPosterRes = R.drawable.poster_select_order,
            previewVideoRes = R.raw.vid_tee_bounce,
            previewPosterRes = R.drawable.poster_tee_bounce,
            gameplayVideoRes = R.raw.vid_tee_bounce,
            gameplayPosterRes = R.drawable.poster_tee_bounce,
            introCaption = "드라이버 헤드 위 나무 티 바운스로 첫 홀 오너를 정합니다"
        )

        GolfGameType.NOTCH_STICK -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_select_order,
            introPosterRes = R.drawable.poster_select_order,
            previewVideoRes = R.raw.vid_stick_draw,
            previewPosterRes = R.drawable.poster_stick_draw,
            gameplayVideoRes = R.raw.vid_stick_draw,
            gameplayPosterRes = R.drawable.poster_stick_draw,
            introCaption = "통 속 나무 막대를 뽑아 숨겨진 눈금 개수로 타순을 확인합니다"
        )

        GolfGameType.FOREST_LADDER -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_select_order,
            introPosterRes = R.drawable.poster_select_order,
            previewVideoRes = R.raw.vid_select_order,
            previewPosterRes = R.drawable.poster_select_order,
            gameplayVideoRes = R.raw.vid_group_cheer,
            gameplayPosterRes = R.drawable.poster_group_cheer,
            introCaption = "숲속 통나무 다리 갈림길을 따라 홀컵으로 출발합니다"
        )

        GolfGameType.NEAR_PIN_PUTTING -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_group_cheer,
            previewPosterRes = R.drawable.poster_group_cheer,
            gameplayVideoRes = R.raw.vid_group_cheer,
            gameplayPosterRes = R.drawable.poster_group_cheer,
            introCaption = "연못 해저드를 피해 홀컵 가장 가까이 붙이는 치킨게임"
        )

        GolfGameType.BALL_BASKET -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_stick_draw,
            previewPosterRes = R.drawable.poster_stick_draw,
            gameplayVideoRes = R.raw.vid_stick_draw,
            gameplayPosterRes = R.drawable.poster_stick_draw,
            introCaption = "바구니 속 황금 도토리 당첨 볼을 피하는 긴장감 복불복"
        )

        GolfGameType.WINDMILL_ROULETTE -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_tee_bounce,
            previewPosterRes = R.drawable.poster_tee_bounce,
            gameplayVideoRes = R.raw.vid_tee_bounce,
            gameplayPosterRes = R.drawable.poster_tee_bounce,
            introCaption = "언덕 위 나무 풍차 바퀴가 멈추는 곳의 주인공은 누구일까요"
        )

        GolfGameType.LUCKY_WOOD_TILE -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_stick_draw,
            previewPosterRes = R.drawable.poster_stick_draw,
            gameplayVideoRes = R.raw.vid_stick_draw,
            gameplayPosterRes = R.drawable.poster_stick_draw,
            introCaption = "나무 패를 골라 동시에 뒤집어 오늘의 행운과 팀을 확인합니다"
        )

        GolfGameType.DOUBLE_DICE -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_group_cheer,
            introPosterRes = R.drawable.poster_group_cheer,
            previewVideoRes = R.raw.vid_tee_bounce,
            previewPosterRes = R.drawable.poster_tee_bounce,
            gameplayVideoRes = R.raw.vid_tee_bounce,
            gameplayPosterRes = R.drawable.poster_tee_bounce,
            introCaption = "원목 주사위 2개를 던져 가장 높은 눈의 합계가 나온 사람이 승리합니다"
        )

        GolfGameType.YUT_THROW -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_select_order,
            introPosterRes = R.drawable.poster_select_order,
            previewVideoRes = R.raw.vid_stick_draw,
            previewPosterRes = R.drawable.poster_stick_draw,
            gameplayVideoRes = R.raw.vid_stick_draw,
            gameplayPosterRes = R.drawable.poster_stick_draw,
            introCaption = "도·개·걸·윷·모·빽도와 말 잡기 보너스로 승자와 패자를 가립니다"
        )

        GolfGameType.TAROT_FORTUNE -> GhibliGameMediaSpec(
            introVideoRes = R.raw.vid_select_order,
            introPosterRes = R.drawable.poster_select_order,
            previewVideoRes = R.raw.vid_group_cheer,
            previewPosterRes = R.drawable.poster_group_cheer,
            gameplayVideoRes = R.raw.vid_group_cheer,
            gameplayPosterRes = R.drawable.poster_group_cheer,
            introCaption = "원하는 운세 종류를 골라 타로점 풀이와 행운 점수 등수를 확인합니다"
        )
    }
}

/**
 * 상단 바에서 배경음(BGM)만 음소거/해제 토글하고 음소거 상태 아이콘을 선명하게 표기하는 공통 컴포넌트
 */
@Composable
fun BgmMuteStatusToggleButton(
    isBgmMuted: Boolean,
    onToggleBgmMute: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isBgmMuted) {
        TerracottaSoft
    } else if (compact) {
        MeadowGreenSoft
    } else {
        PureCardWhite.copy(alpha = 0.94f)
    }

    val borderColor = if (isBgmMuted) {
        TerracottaWood
    } else if (compact) {
        MeadowGreen
    } else {
        SubtleDivider
    }

    val contentColor = if (isBgmMuted) TerracottaWood else PineDeep
    val statusText = when {
        isBgmMuted && compact -> "배경음 끔"
        isBgmMuted && !compact -> "배경음 음소거됨"
        !isBgmMuted && compact -> "배경음"
        else -> "배경음 켜짐"
    }
    val accessibilityDesc = if (isBgmMuted) {
        "배경음 음소거 상태 아이콘 · 한 번 더 누르면 배경음 음소거 해제"
    } else {
        "배경음 재생 중 · 누르면 배경음만 음소거"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .semantics { contentDescription = accessibilityDesc }
            .clip(RoundedCornerShape(if (compact) 10.dp else 18.dp))
            .background(bgColor)
            .border(
                width = if (isBgmMuted) 1.4.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(if (compact) 10.dp else 18.dp)
            )
            .clickable(role = Role.Switch) { onToggleBgmMute() }
            .padding(
                horizontal = if (compact) 9.dp else 11.dp,
                vertical = if (compact) 7.dp else 6.dp
            )
    ) {
        BgmMuteStatusVectorIcon(
            isMuted = isBgmMuted,
            tint = contentColor,
            iconSize = if (compact) 15.dp else 16.dp
        )
        Text(
            text = statusText,
            style = MaterialTheme.typography.labelLarge,
            fontSize = if (compact) 11.sp else 12.sp,
            fontWeight = if (isBgmMuted) FontWeight.Bold else FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1
        )
    }
}

/**
 * 음표 및 스피커 파형 / 음소거 사선(Slash + X)을 벡터로 렌더링하는 상단 상태 아이콘
 */
@Composable
fun BgmMuteStatusVectorIcon(
    isMuted: Boolean,
    tint: Color,
    iconSize: Dp = 16.dp,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.size(iconSize).clipToBounds()) {
        val w = size.width
        val h = size.height

        // 8분음표 헤드 + 기둥 + 깃발
        drawOval(
            color = tint,
            topLeft = Offset(w * 0.10f, h * 0.56f),
            size = androidx.compose.ui.geometry.Size(w * 0.34f, h * 0.28f)
        )
        drawLine(
            color = tint,
            start = Offset(w * 0.40f, h * 0.68f),
            end = Offset(w * 0.40f, h * 0.16f),
            strokeWidth = 1.9.dp.toPx(),
            cap = StrokeCap.Round
        )
        val flagPath = Path().apply {
            moveTo(w * 0.40f, h * 0.16f)
            cubicTo(
                w * 0.64f, h * 0.18f,
                w * 0.68f, h * 0.34f,
                w * 0.58f, h * 0.46f
            )
        }
        drawPath(
            path = flagPath,
            color = tint,
            style = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round)
        )

        if (!isMuted) {
            // 배경음 활성 시 우측 잔잔한 음향 파동 아크 2줄
            drawArc(
                color = tint.copy(alpha = 0.85f),
                startAngle = -42f,
                sweepAngle = 84f,
                useCenter = false,
                topLeft = Offset(w * 0.48f, h * 0.30f),
                size = androidx.compose.ui.geometry.Size(w * 0.32f, h * 0.44f),
                style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            )
            drawArc(
                color = tint.copy(alpha = 0.55f),
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(w * 0.58f, h * 0.18f),
                size = androidx.compose.ui.geometry.Size(w * 0.38f, h * 0.68f),
                style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round)
            )
        } else {
            // 배경음 음소거 상태: 우측 X 마크 + 선명한 대각선 음소거 슬래시
            drawLine(
                color = tint,
                start = Offset(w * 0.66f, h * 0.44f),
                end = Offset(w * 0.92f, h * 0.72f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.92f, h * 0.44f),
                end = Offset(w * 0.66f, h * 0.72f),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            // 흰색 외곽 후광선 후 테라코타 대각선 슬래시로 시인성 극대화
            drawLine(
                color = PureCardWhite.copy(alpha = 0.92f),
                start = Offset(w * 0.08f, h * 0.12f),
                end = Offset(w * 0.92f, h * 0.90f),
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = tint,
                start = Offset(w * 0.08f, h * 0.12f),
                end = Offset(w * 0.92f, h * 0.90f),
                strokeWidth = 2.0.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

/**
 * TextureView + 무음 MediaPlayer(AUDIOFOCUS_NONE) 기반 고성능 지브리 동영상 플레이어
 * SurfaceView와 달리 Compose 클리핑·알파·다이얼로그·Crossfade 내부에서도 검은 깜빡임 없이 매끄럽게 합성됨
 */
@Composable
fun GhibliRawVideoPlayer(
    @RawRes videoRes: Int,
    @DrawableRes posterRes: Int,
    looping: Boolean = true,
    playbackSpeed: Float = 1.0f,
    onCompleted: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCompleted by rememberUpdatedState(onCompleted)
    val currentSpeed by rememberUpdatedState(playbackSpeed)

    var firstFrameReady by remember(videoRes) { mutableStateOf(false) }
    val playerHolder = remember(videoRes) { arrayOfNulls<MediaPlayer>(1) }
    val textureHolder = remember(videoRes) { arrayOfNulls<TextureView>(1) }

    val videoAlpha by animateFloatAsState(
        targetValue = if (firstFrameReady) 1f else 0f,
        animationSpec = tween(durationMillis = 160),
        label = "ghibliVideoFadeIn"
    )

    fun applyCenterCropTransform(tv: TextureView, videoW: Int, videoH: Int) {
        val viewW = tv.width.toFloat()
        val viewH = tv.height.toFloat()
        if (viewW <= 1f || viewH <= 1f || videoW <= 0 || videoH <= 0) return

        val scaleX: Float
        val scaleY: Float
        val viewAspect = viewW / viewH
        val videoAspect = videoW.toFloat() / videoH.toFloat()

        // TextureView는 기본적으로 비디오를 뷰 크기에 맞춰 늘리므로 비율 보정 배율을 역산해 CenterCrop 적용
        if (videoAspect > viewAspect) {
            scaleX = videoAspect / viewAspect
            scaleY = 1f
        } else {
            scaleX = 1f
            scaleY = viewAspect / videoAspect
        }

        val matrix = Matrix()
        matrix.setScale(scaleX, scaleY, viewW * 0.5f, viewH * 0.5f)
        tv.setTransform(matrix)
    }

    LaunchedEffect(currentSpeed, firstFrameReady) {
        val mp = playerHolder[0] ?: return@LaunchedEffect
        if (firstFrameReady) {
            runCatching {
                if (mp.isPlaying) {
                    mp.playbackParams = mp.playbackParams.setSpeed(currentSpeed.coerceIn(0.45f, 1.5f))
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner, videoRes) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = playerHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching {
                    if (mp.isPlaying) mp.pause()
                }
                Lifecycle.Event.ON_RESUME -> runCatching {
                    if (!mp.isPlaying && firstFrameReady) mp.start()
                }
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
                runCatching { mp.reset() }
                runCatching { mp.release() }
            }
        }
    }

    Box(modifier = modifier.clipToBounds()) {
        // 첫 프레임 디코딩 전 즉시 노출되는 동일 장면 포스터 이미지 (검은 화면 원천 차단)
        Image(
            painter = painterResource(id = posterRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        AndroidView(
            factory = { ctx ->
                TextureView(ctx).apply {
                    isOpaque = false
                    textureHolder[0] = this

                    fun startMediaPlayerWithSurface(st: SurfaceTexture, w: Int, h: Int) {
                        val old = playerHolder[0]
                        playerHolder[0] = null
                        if (old != null) {
                            runCatching { old.release() }
                        }

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
                            // 무음(-an) MP4 및 볼륨 0 설정으로 GhibliSoundEngine 배경음악·효과음과 간섭 없이 재생
                            mp.setVolume(0f, 0f)
                            mp.isLooping = looping
                            mp.setSurface(surface)

                            ctx.resources.openRawResourceFd(videoRes)?.use { afd ->
                                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                            }

                            mp.setOnVideoSizeChangedListener { _, vw, vh ->
                                post { applyCenterCropTransform(this@apply, vw, vh) }
                            }
                            mp.setOnInfoListener { _, what, _ ->
                                if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                                    firstFrameReady = true
                                }
                                false
                            }
                            mp.setOnCompletionListener {
                                currentOnCompleted?.invoke()
                            }
                            mp.setOnPreparedListener { preparedMp ->
                                applyCenterCropTransform(
                                    this@apply,
                                    preparedMp.videoWidth,
                                    preparedMp.videoHeight
                                )
                                runCatching {
                                    preparedMp.setVolume(0f, 0f)
                                    preparedMp.start()
                                    if (currentSpeed != 1.0f) {
                                        preparedMp.playbackParams =
                                            preparedMp.playbackParams.setSpeed(currentSpeed.coerceIn(0.45f, 1.5f))
                                    }
                                }
                            }
                            mp.prepareAsync()
                        }.onFailure {
                            runCatching { surface.release() }
                        }
                    }

                    surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                            startMediaPlayerWithSurface(st, w, h)
                        }

                        override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {
                            val mp = playerHolder[0]
                            if (mp != null) {
                                runCatching {
                                    applyCenterCropTransform(this@apply, mp.videoWidth, mp.videoHeight)
                                }
                            }
                        }

                        override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                            val mp = playerHolder[0]
                            playerHolder[0] = null
                            if (mp != null) {
                                runCatching { mp.stop() }
                                runCatching { mp.release() }
                            }
                            return true
                        }

                        override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                            if (!firstFrameReady) {
                                firstFrameReady = true
                            }
                        }
                    }

                    if (isAvailable && surfaceTexture != null) {
                        startMediaPlayerWithSurface(surfaceTexture!!, width, height)
                    }
                }
            },
            update = { tv ->
                val mp = playerHolder[0]
                if (mp != null) {
                    runCatching {
                        if (mp.isLooping != looping) {
                            mp.isLooping = looping
                        }
                        applyCenterCropTransform(tv, mp.videoWidth, mp.videoHeight)
                    }
                }
            },
            onRelease = {
                val mp = playerHolder[0]
                playerHolder[0] = null
                if (mp != null) {
                    runCatching { mp.stop() }
                    runCatching { mp.release() }
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .alpha(videoAlpha)
        )
    }
}

/**
 * 게임 선택 후 입장 시 재생되는 지브리 동영상 & 동심원 터치 파동 시네마틱 컷신 (원탭 즉시 스킵 지원)
 */
@Composable
fun GameIntroCutsceneOverlay(
    gameType: GolfGameType,
    playerCount: Int,
    visible: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val spec = remember(gameType) { gameType.mediaSpec() }
    var progress by remember(gameType) { mutableFloatStateOf(0f) }

    LaunchedEffect(gameType, visible) {
        progress = 0f
        val durationSec = 1.38f
        var prev = 0L
        while (progress < 1f) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    progress = (progress + dt / durationSec).coerceIn(0f, 1f)
                }
                prev = now
            }
        }
        onFinished()
    }

    val containerAlpha = when {
        progress < 0.12f -> (progress / 0.12f).coerceIn(0f, 1f)
        progress > 0.80f -> ((1f - progress) / 0.20f).coerceIn(0f, 1f)
        else -> 1f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(containerAlpha)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                onFinished()
            }
    ) {
        GhibliRawVideoPlayer(
            videoRes = spec.introVideoRes,
            posterRes = spec.introPosterRes,
            looping = false,
            modifier = Modifier.fillMaxSize()
        )

        // 첨부 영상(0.5s~1.0s)과 동일한 동심원 아이리스 터치 파동 & 봄바람 나뭇잎 오버레이
        Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
            val cx = size.width * 0.5f
            val cy = size.height * 0.46f
            val maxR = max(size.width, size.height) * 0.85f

            if (progress > 0.32f) {
                val rippleP = ((progress - 0.32f) / 0.68f).coerceIn(0f, 1f)
                for (ring in 0..2) {
                    val rp = (rippleP - ring * 0.12f).coerceIn(0f, 1f)
                    if (rp > 0f) {
                        drawCircle(
                            color = PureCardWhite.copy(alpha = (1f - rp) * 0.78f),
                            radius = maxR * rp,
                            center = Offset(cx, cy),
                            style = Stroke(width = (3.4f - ring * 0.7f).dp.toPx())
                        )
                    }
                }
            }
        }

        AnimeSpeedLinesOverlay(
            active = true,
            intense = false,
            modifier = Modifier.fillMaxSize()
        )

        // 하단 그라데이션 및 게임 타이틀 안내 배너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            PaperCream.copy(alpha = 0.88f),
                            PaperCream.copy(alpha = 0.96f)
                        )
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${gameType.category.label} · 출전 ${playerCount}명",
                        style = MaterialTheme.typography.labelMedium,
                        color = gameType.accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = gameType.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = spec.introCaption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        maxLines = 1
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(PureCardWhite)
                        .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "터치 스킵",
                        style = MaterialTheme.typography.labelLarge,
                        color = PineDeep
                    )
                }
            }
        }
    }
}

/**
 * 홈 화면에서 게임을 선택하는 순간 손끝에서 퍼져나가는 지브리풍 동심원 파동 & 나뭇잎 이펙트
 */
@Composable
fun HomeSelectionBurstOverlay(
    burstTrigger: Int,
    modifier: Modifier = Modifier
) {
    if (burstTrigger <= 0) return

    var animP by remember(burstTrigger) { mutableFloatStateOf(0f) }
    LaunchedEffect(burstTrigger) {
        animP = 0f
        var prev = 0L
        while (animP < 1f) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    animP = (animP + dt / 0.55f).coerceIn(0f, 1f)
                }
                prev = now
            }
        }
    }
    if (animP >= 1f) return

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.52f
        val maxR = max(size.width, size.height) * 0.55f
        for (i in 0..2) {
            val p = (animP - i * 0.14f).coerceIn(0f, 1f)
            if (p > 0f) {
                drawCircle(
                    color = MeadowGreen.copy(alpha = (1f - p) * 0.40f),
                    radius = maxR * p,
                    center = Offset(cx, cy),
                    style = Stroke(width = (3.2f - i * 0.7f).dp.toPx())
                )
            }
        }
        for (leaf in 0 until 10) {
            val ang = leaf * (2.0 * PI / 10.0) + animP * 1.4
            val dist = maxR * 0.72f * animP
            val lx = cx + cos(ang).toFloat() * dist
            val ly = cy + sin(ang).toFloat() * dist
            rotate(degrees = (leaf * 36f + animP * 160f), pivot = Offset(lx, ly)) {
                val leafPath = Path().apply {
                    moveTo(lx - 7.dp.toPx(), ly)
                    quadraticTo(lx, ly - 5.dp.toPx(), lx + 7.dp.toPx(), ly)
                    quadraticTo(lx, ly + 5.dp.toPx(), lx - 7.dp.toPx(), ly)
                    close()
                }
                drawPath(
                    path = leafPath,
                    color = CloverLeaf.copy(alpha = (1f - animP) * 0.85f)
                )
            }
        }
    }
}

/**
 * 첨부 영상의 클라이맥스 장면과 동일한 방사형 애니메이션 집중선(Speed Lines) 레이어
 */
@Composable
fun AnimeSpeedLinesOverlay(
    active: Boolean,
    intense: Boolean,
    modifier: Modifier = Modifier
) {
    val overlayAlpha by animateFloatAsState(
        targetValue = when {
            intense -> 0.88f
            active -> 0.48f
            else -> 0f
        },
        animationSpec = tween(durationMillis = 220),
        label = "speedLinesAlpha"
    )
    if (overlayAlpha <= 0.01f) return

    val infinite = rememberInfiniteTransition(label = "speedLinesAnim")
    val phaseShift by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (intense) 260 else 420, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "speedPhase"
    )

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val outerR = max(size.width, size.height) * 0.78f
        val baseInnerR = minOf(size.width, size.height) * (if (intense) 0.34f else 0.41f)
        val rayCount = 32
        val phaseBucket = (phaseShift * 6f).toInt()

        for (i in 0 until rayCount) {
            val jitter = ((i * 19 + phaseBucket * 7) % 11) / 11f
            val baseAngle = (i.toFloat() / rayCount.toFloat()) * 2.0 * PI + jitter * 0.05
            val innerR = baseInnerR * (0.88f + jitter * 0.24f)
            val halfSpread = 0.012 + (if (intense) 0.008 else 0.004) * jitter

            val ix = cx + cos(baseAngle).toFloat() * innerR
            val iy = cy + sin(baseAngle).toFloat() * innerR
            val ox1 = cx + cos(baseAngle - halfSpread).toFloat() * outerR
            val oy1 = cy + sin(baseAngle - halfSpread).toFloat() * outerR
            val ox2 = cx + cos(baseAngle + halfSpread).toFloat() * outerR
            val oy2 = cy + sin(baseAngle + halfSpread).toFloat() * outerR

            val rayPath = Path().apply {
                moveTo(ix, iy)
                lineTo(ox1, oy1)
                lineTo(ox2, oy2)
                close()
            }
            val rayColor = if (intense && i % 4 == 0) {
                SunlightAmber.copy(alpha = 0.36f * overlayAlpha)
            } else {
                PureCardWhite.copy(alpha = 0.52f * overlayAlpha)
            }
            drawPath(path = rayPath, color = rayColor)
        }
    }
}

/**
 * 티 타격·막대 뽑기·퍼팅 스트로크·공 오픈 순간 터지는 만화적 스타버스트 & 잔디 스파크 이펙트
 */
@Composable
fun AnimeImpactBurstOverlay(
    impactBurstKey: Int,
    accentColor: Color = SunlightAmber,
    modifier: Modifier = Modifier
) {
    if (impactBurstKey <= 0) return

    var burstProgress by remember(impactBurstKey) { mutableFloatStateOf(0f) }
    LaunchedEffect(impactBurstKey) {
        burstProgress = 0f
        var prev = 0L
        while (burstProgress < 1f) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    burstProgress = (burstProgress + dt / 0.48f).coerceIn(0f, 1f)
                }
                prev = now
            }
        }
    }
    if (burstProgress >= 1f) return

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.50f
        val baseR = minOf(size.width, size.height) * 0.32f
        val fade = (1f - burstProgress).coerceIn(0f, 1f)

        // 외곽 충격파 원형 링
        drawCircle(
            color = PureCardWhite.copy(alpha = fade * 0.82f),
            radius = baseR * (0.28f + burstProgress * 0.95f),
            center = Offset(cx, cy),
            style = Stroke(width = (4.2f * fade + 1f).dp.toPx())
        )

        // 12각 만화 타격 스타버스트
        if (burstProgress < 0.72f) {
            val starP = (burstProgress / 0.72f).coerceIn(0f, 1f)
            val starFade = 1f - starP
            val spikes = 12
            val starPath = Path()
            for (k in 0 until spikes * 2) {
                val r = if (k % 2 == 0) {
                    baseR * (0.35f + 0.65f * starP)
                } else {
                    baseR * (0.16f + 0.25f * starP)
                }
                val ang = k * PI / spikes
                val px = cx + cos(ang).toFloat() * r
                val py = cy + sin(ang).toFloat() * r
                if (k == 0) starPath.moveTo(px, py) else starPath.lineTo(px, py)
            }
            starPath.close()
            drawPath(
                path = starPath,
                color = PureCardWhite.copy(alpha = starFade * 0.55f)
            )
            drawPath(
                path = starPath,
                color = accentColor.copy(alpha = starFade * 0.75f),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        // 사방으로 튀는 나뭇잎·햇살 스파크 입자
        for (s in 0 until 10) {
            val ang = s * (2.0 * PI / 10.0) + 0.25
            val dist = baseR * (0.24f + burstProgress * 1.05f)
            val sx = cx + cos(ang).toFloat() * dist
            val sy = cy + sin(ang).toFloat() * dist
            drawCircle(
                color = if (s % 2 == 0) CloverLeaf.copy(alpha = fade * 0.85f) else accentColor.copy(alpha = fade * 0.85f),
                radius = (4.5f * fade + 1.5f).dp.toPx(),
                center = Offset(sx, sy)
            )
        }
    }
}

/**
 * 지브리 페어웨이 위로 바람을 타고 흩날리는 초록 나뭇잎 애니메이션 레이어
 */
@Composable
fun FloatingFairwayLeavesOverlay(
    active: Boolean,
    isSlowMotion: Boolean,
    modifier: Modifier = Modifier
) {
    if (!active && !isSlowMotion) return

    var timeSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isSlowMotion) {
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val mult = if (isSlowMotion) 0.24f else 1.0f
                    timeSec += dt * mult
                }
                prev = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val h = size.height
        val leafCount = 7
        for (i in 0 until leafCount) {
            val speedX = 0.08f + (i % 3) * 0.03f
            val speedY = 0.11f + (i % 4) * 0.025f
            val rx = ((i * 0.17f + timeSec * speedX) % 1.16f) - 0.08f
            val ry = ((i * 0.23f + timeSec * speedY + sin(timeSec * 1.8f + i) * 0.03f) % 1.16f) - 0.08f
            val lx = rx * w
            val ly = ry * h
            val leafLen = (7.5f + (i % 3) * 2.2f).dp.toPx()
            val leafHalfW = leafLen * 0.42f

            rotate(degrees = (timeSec * 78f + i * 52f) % 360f, pivot = Offset(lx, ly)) {
                val path = Path().apply {
                    moveTo(lx - leafLen, ly)
                    quadraticTo(lx, ly - leafHalfW, lx + leafLen, ly)
                    quadraticTo(lx, ly + leafHalfW, lx - leafLen, ly)
                    close()
                }
                drawPath(
                    path = path,
                    color = (if (i % 2 == 0) CloverLeaf else MeadowGreen).copy(alpha = 0.58f)
                )
                drawLine(
                    color = PureCardWhite.copy(alpha = 0.45f),
                    start = Offset(lx - leafLen * 0.7f, ly),
                    end = Offset(lx + leafLen * 0.7f, ly),
                    strokeWidth = 0.9.dp.toPx()
                )
            }
        }
    }
}

/**
 * 첨부 영상의 클라이맥스 구간(3.7s~4.5s)처럼 상단 좌·우 코너에 등장하는 동반자 리액션 팝업 배지
 */
@Composable
fun AnimeGolferReactionOverlay(
    visible: Boolean,
    gameType: GolfGameType,
    players: List<PlayerInfo>,
    modifier: Modifier = Modifier
) {
    val popScale by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.58f, stiffness = 420f),
        label = "reactionPopScale"
    )
    if (popScale <= 0.02f || players.isEmpty()) return

    val leftPlayer = players.first()
    val rightPlayer = players.getOrElse(1) { players.last() }

    val (leftQuote, rightQuote) = remember(gameType) {
        when (gameType) {
            GolfGameType.DOUBLE_DICE -> "더블 가자!" to "몇 점 나올까?!"
            GolfGameType.YUT_THROW -> "윷이나 모!" to "빽도만 피해라!"
            GolfGameType.TAROT_FORTUNE -> "대길 카드야!" to "오늘 1등 운세 누구?!"
            else -> if (gameType.category == GameCategory.TEE_ORDER) {
                "두근두근!" to "1번 오너 누구?!"
            } else {
                "아슬아슬!" to "설마 나야?!"
            }
        }
    }

    var pulseTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(visible) {
        while (visible) {
            delay(380)
            pulseTick++
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp, vertical = 44.dp)
    ) {
        ReactionCornerBubble(
            player = leftPlayer,
            quote = leftQuote,
            isLeft = true,
            pulse = pulseTick % 2 == 0,
            modifier = Modifier
                .align(Alignment.TopStart)
                .scale(popScale)
        )
        ReactionCornerBubble(
            player = rightPlayer,
            quote = rightQuote,
            isLeft = false,
            pulse = pulseTick % 2 == 1,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .scale(popScale)
        )
    }
}

@Composable
private fun ReactionCornerBubble(
    player: PlayerInfo,
    quote: String,
    isLeft: Boolean,
    pulse: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(SunlightSoft.copy(alpha = 0.95f))
            .border(1.4.dp, SunlightAmber, RoundedCornerShape(20.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        if (isLeft) {
            ReactionAvatarWithSparks(player = player, pulse = pulse)
            Text(
                text = quote,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
        } else {
            Text(
                text = quote,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = InkPrimary
            )
            ReactionAvatarWithSparks(player = player, pulse = pulse)
        }
    }
}

@Composable
private fun ReactionAvatarWithSparks(
    player: PlayerInfo,
    pulse: Boolean
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(34.dp)) {
        Image(
            painter = painterResource(id = player.avatarRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .border(1.5.dp, player.badgeColor, CircleShape)
        )
        if (pulse) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                // 캐릭터 머리 위 만화적 깜짝 강조선 3줄
                drawLine(
                    color = TerracottaWood,
                    start = Offset(size.width * 0.12f, size.height * 0.16f),
                    end = Offset(0f, 0f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = SunlightAmber,
                    start = Offset(size.width * 0.50f, size.height * 0.08f),
                    end = Offset(size.width * 0.50f, -2.dp.toPx()),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = TerracottaWood,
                    start = Offset(size.width * 0.88f, size.height * 0.16f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/**
 * 게임 진행 중 무대 배경에서 실시간으로 살아 움직이는 지브리 동영상 시네마 백드롭
 */
@Composable
fun StageCinemaBackdrop(
    gameType: GolfGameType,
    videoEffectsEnabled: Boolean,
    active: Boolean,
    isSlowMotion: Boolean = false,
    activeVideoAlpha: Float = 0.50f,
    @DrawableRes idleBackdropRes: Int = R.drawable.bg_fairway_meadow,
    idleBackdropAlpha: Float = 0.28f,
    modifier: Modifier = Modifier
) {
    val spec = remember(gameType) { gameType.mediaSpec() }
    val showVideo = videoEffectsEnabled && active
    val targetAlpha by animateFloatAsState(
        targetValue = if (showVideo) activeVideoAlpha.coerceIn(0f, 1f) else 0f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "stageCinemaAlpha"
    )

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        Image(
            painter = painterResource(id = idleBackdropRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alpha = idleBackdropAlpha,
            modifier = Modifier.fillMaxSize()
        )
        if (showVideo || targetAlpha > 0.01f) {
            GhibliRawVideoPlayer(
                videoRes = spec.gameplayVideoRes,
                posterRes = spec.gameplayPosterRes,
                looping = true,
                playbackSpeed = if (isSlowMotion) 0.58f else 1.0f,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(targetAlpha)
            )
        }
    }
}

/**
 * 상단 바에서 사운드·연출 상세 모달을 여는 슬림 벡터 아이콘 버튼
 */
@Composable
fun SoundSettingsTopIconButton(
    soundEnabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderCol = if (soundEnabled) SubtleDivider else TerracottaWood
    val iconCol = if (soundEnabled) InkPrimary else TerracottaWood

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .semantics { contentDescription = "사운드 및 진행 속도 상세 설정 열기" }
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(PureCardWhite)
            .border(1.dp, borderCol, RoundedCornerShape(10.dp))
            .clickable { onClick() }
    ) {
        Canvas(modifier = Modifier.size(16.dp).clipToBounds()) {
            val w = size.width
            val h = size.height
            // 3단 이퀄라이저 슬라이더 아이콘
            val ys = listOf(0.24f, 0.52f, 0.80f)
            val knobs = listOf(0.32f, 0.68f, 0.44f)
            ys.forEachIndexed { idx, ry ->
                val y = h * ry
                drawLine(
                    color = iconCol.copy(alpha = 0.65f),
                    start = Offset(w * 0.10f, y),
                    end = Offset(w * 0.90f, y),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                drawCircle(
                    color = iconCol,
                    radius = 2.3.dp.toPx(),
                    center = Offset(w * knobs[idx], y)
                )
            }
        }
    }
}

