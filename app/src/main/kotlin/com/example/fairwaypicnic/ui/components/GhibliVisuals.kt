package com.example.fairwaypicnic.ui.components

import android.media.AudioManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.fairwaypicnic.audio.GhibliSoundEngine
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameResultRow
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.model.KoreanGolfNameHelper
import com.example.fairwaypicnic.model.PlayerInfo
import com.example.fairwaypicnic.ui.theme.CloverLeaf
import com.example.fairwaypicnic.ui.theme.InkMuted
import com.example.fairwaypicnic.ui.theme.InkPrimary
import com.example.fairwaypicnic.ui.theme.InkSecondary
import com.example.fairwaypicnic.ui.theme.LightBirch
import com.example.fairwaypicnic.ui.theme.MeadowGreen
import com.example.fairwaypicnic.ui.theme.MeadowGreenSoft
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PaperCream
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PlayerBadgeColors
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SkyPondBlue
import com.example.fairwaypicnic.ui.theme.SoftSand
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.SunlightSoft
import com.example.fairwaypicnic.ui.theme.TerracottaSoft
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private data class SeedSpec(
    val startX: Float,
    val startY: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val swayPhase: Float
)

/**
 * 지브리 수채화 감성의 떠다니는 민들레 홀씨와 슬로우모션 포커스 연출 레이어
 */
@Composable
fun FloatingDandelionOverlay(
    isSlowMotion: Boolean,
    modifier: Modifier = Modifier
) {
    val seeds = remember {
        List(14) { i ->
            SeedSpec(
                startX = ((i * 73) % 100) / 100f,
                startY = ((i * 41) % 100) / 100f,
                speedX = 0.015f + (i % 4) * 0.006f,
                speedY = -0.022f - (i % 3) * 0.008f,
                radius = 3.2f + (i % 4) * 1.4f,
                swayPhase = i * 0.9f
            )
        }
    }

    var virtualTime by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(isSlowMotion) {
        var prevNanos = 0L
        while (true) {
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    val dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val scale = if (isSlowMotion) 0.18f else 1.0f
                    virtualTime += dt * scale
                }
                prevNanos = now
            }
        }
    }

    val focusAlpha by animateFloatAsState(
        targetValue = if (isSlowMotion) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "slowMoFocusAlpha"
    )

    val pulseTransition = rememberInfiniteTransition(label = "heartbeatPulse")
    val pulse by pulseTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 480, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseVal"
    )

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val h = size.height

        seeds.forEach { seed ->
            val rawX = (seed.startX + virtualTime * seed.speedX + 0.03f * sin(virtualTime * 1.3f + seed.swayPhase)) % 1.0f
            val rawY = ((seed.startY + virtualTime * seed.speedY) % 1.0f + 1.0f) % 1.0f
            val cx = ((rawX + 1.0f) % 1.0f) * w
            val cy = rawY * h
            val r = seed.radius * density * 0.55f

            drawCircle(
                color = PureCardWhite.copy(alpha = if (isSlowMotion) 0.82f else 0.55f),
                radius = r * 1.5f,
                center = Offset(cx, cy)
            )
            for (spoke in 0 until 5) {
                val ang = spoke * (2.0 * PI / 5.0) + virtualTime * 0.6
                val ex = cx + cos(ang).toFloat() * r * 2.2f
                val ey = cy - r * 0.6f + sin(ang).toFloat() * r * 2.2f
                drawLine(
                    color = PureCardWhite.copy(alpha = 0.65f),
                    start = Offset(cx, cy),
                    end = Offset(ex, ey),
                    strokeWidth = 1.1f * density
                )
            }
            drawCircle(
                color = SunlightAmber.copy(alpha = 0.55f),
                radius = r * 0.48f,
                center = Offset(cx, cy + r * 0.9f)
            )
        }

        if (focusAlpha > 0.01f) {
            val borderStroke = (4.5f + 3.5f * pulse) * density
            drawRoundRect(
                color = SunlightAmber.copy(alpha = 0.42f * focusAlpha * pulse),
                topLeft = Offset(borderStroke * 0.5f, borderStroke * 0.5f),
                size = Size(w - borderStroke, h - borderStroke),
                cornerRadius = CornerRadius(18f * density, 18f * density),
                style = Stroke(width = borderStroke)
            )
        }
    }
}

private data class PetalSpec(
    val xRatio: Float,
    val delayOffset: Float,
    val speed: Float,
    val spinSpeed: Float,
    val color: Color,
    val sizeDp: Float
)

/**
 * 결과 공개 순간 봄바람처럼 흩날리는 꽃잎·클로버 나뭇잎 파티클
 */
@Composable
fun CelebrationPetalOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val petals = remember {
        val palette = listOf(
            Color(0xFFE7A99B),
            Color(0xFF8AB892),
            Color(0xFFE5C16C),
            Color(0xFF9AC0D6)
        )
        List(28) { i ->
            PetalSpec(
                xRatio = ((i * 37 + 11) % 100) / 100f,
                delayOffset = (i % 7) * 0.11f,
                speed = 0.26f + (i % 5) * 0.06f,
                spinSpeed = 95f + (i % 4) * 45f,
                color = palette[i % palette.size],
                sizeDp = 7f + (i % 4) * 2.5f
            )
        }
    }

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(visible) {
        progress = 0f
        var prev = 0L
        while (progress < 4.5f) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    progress += ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.05f)
                }
                prev = now
            }
        }
    }

    Canvas(modifier = modifier.fillMaxSize().clipToBounds()) {
        val w = size.width
        val h = size.height
        petals.forEach { p ->
            val t = (progress - p.delayOffset).coerceAtLeast(0f)
            if (t > 0f && t < 3.6f) {
                val y = (t * p.speed) * h
                val sway = sin(t * 3.4f + p.xRatio * 10f) * 24f * density
                val x = (p.xRatio * w + sway).coerceIn(0f, w)
                if (y <= h) {
                    val alpha = (1f - (t / 3.6f)).coerceIn(0f, 0.9f)
                    rotate(degrees = t * p.spinSpeed, pivot = Offset(x, y)) {
                        drawOval(
                            color = p.color.copy(alpha = alpha),
                            topLeft = Offset(x - p.sizeDp * density * 0.5f, y - p.sizeDp * density * 0.3f),
                            size = Size(p.sizeDp * density, p.sizeDp * density * 0.62f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 슬로우모션 구간 상단 상태 배너
 */
@Composable
fun SlowMotionPulseBanner(
    visible: Boolean,
    message: String = "결정적 순간 · 슬로우모션 발동",
    modifier: Modifier = Modifier
) {
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(220),
        label = "slowBannerAlpha"
    )
    if (alpha <= 0.01f) return

    Box(
        modifier = modifier
            .alpha(alpha)
            .clip(RoundedCornerShape(20.dp))
            .background(PureCardWhite.copy(alpha = 0.94f))
            .border(1.5.dp, SunlightAmber, RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Canvas(modifier = Modifier.size(18.dp).clipToBounds()) {
                val path = Path().apply {
                    moveTo(0f, size.height * 0.55f)
                    lineTo(size.width * 0.25f, size.height * 0.55f)
                    lineTo(size.width * 0.40f, size.height * 0.15f)
                    lineTo(size.width * 0.58f, size.height * 0.88f)
                    lineTo(size.width * 0.74f, size.height * 0.35f)
                    lineTo(size.width, size.height * 0.55f)
                }
                drawPath(
                    path = path,
                    color = TerracottaWood,
                    style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            Text(
                text = message,
                style = MaterialTheme.typography.labelLarge,
                color = OakWood
            )
        }
    }
}

/**
 * 지브리풍 참가 선수 아바타 칩
 */
@Composable
fun GolferAvatarChip(
    player: PlayerInfo,
    badgeLabel: String? = null,
    isActive: Boolean = false,
    avatarSize: Dp = 42.dp,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val borderCol = if (isActive) player.badgeColor else SubtleDivider
    val bgCol = if (isActive) PureCardWhite else WarmIvory

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgCol)
            .border(if (isActive) 1.8.dp else 1.dp, borderCol, RoundedCornerShape(14.dp))
            .let { base -> if (onClick != null) base.clickable { onClick() } else base }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Image(
            painter = painterResource(id = player.avatarRes),
            contentDescription = player.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(avatarSize)
                .clip(CircleShape)
                .border(1.5.dp, player.badgeColor, CircleShape)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            if (!badgeLabel.isNullOrBlank()) {
                Text(
                    text = badgeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = player.badgeColor,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = player.name,
                style = MaterialTheme.typography.titleMedium,
                color = InkPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * 홈 화면 및 헤더에 사용되는 7종 미니게임 전용 커스텀 벡터 일러스트 아이콘
 */
@Composable
fun GameVectorThumbnail(
    gameType: GolfGameType,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(WarmIvory)
            .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
    ) {
        Canvas(modifier = Modifier.size(38.dp).clipToBounds()) {
            val w = size.width
            val h = size.height
            when (gameType) {
                GolfGameType.TEE_BOUNCE -> {
                    drawOval(
                        color = PineDeep,
                        topLeft = Offset(w * 0.14f, h * 0.56f),
                        size = Size(w * 0.72f, h * 0.34f)
                    )
                    drawLine(
                        color = PureCardWhite.copy(alpha = 0.7f),
                        start = Offset(w * 0.28f, h * 0.68f),
                        end = Offset(w * 0.72f, h * 0.68f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                    rotate(degrees = 32f, pivot = Offset(w * 0.54f, h * 0.30f)) {
                        val teePath = Path().apply {
                            moveTo(w * 0.47f, h * 0.14f)
                            lineTo(w * 0.61f, h * 0.14f)
                            lineTo(w * 0.56f, h * 0.46f)
                            lineTo(w * 0.52f, h * 0.46f)
                            close()
                        }
                        drawPath(teePath, color = TerracottaWood)
                    }
                }

                GolfGameType.NOTCH_STICK -> {
                    val stickXs = listOf(0.24f, 0.41f, 0.58f, 0.75f)
                    stickXs.forEachIndexed { idx, rx ->
                        val topY = if (idx == 1) h * 0.08f else h * 0.22f
                        drawRoundRect(
                            color = LightBirch,
                            topLeft = Offset(w * rx - 3.dp.toPx(), topY),
                            size = Size(6.dp.toPx(), h * 0.48f),
                            cornerRadius = CornerRadius(3.dp.toPx())
                        )
                        if (idx == 1) {
                            drawLine(
                                color = TerracottaWood,
                                start = Offset(w * rx - 2.5.dp.toPx(), topY + 8.dp.toPx()),
                                end = Offset(w * rx + 2.5.dp.toPx(), topY + 8.dp.toPx()),
                                strokeWidth = 1.6.dp.toPx()
                            )
                        }
                    }
                    drawRoundRect(
                        color = OakWood,
                        topLeft = Offset(w * 0.15f, h * 0.48f),
                        size = Size(w * 0.70f, h * 0.44f),
                        cornerRadius = CornerRadius(6.dp.toPx())
                    )
                }

                GolfGameType.FOREST_LADDER -> {
                    val cols = listOf(0.22f, 0.50f, 0.78f)
                    cols.forEach { cx ->
                        drawLine(
                            color = MeadowGreen,
                            start = Offset(w * cx, h * 0.12f),
                            end = Offset(w * cx, h * 0.88f),
                            strokeWidth = 2.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }
                    drawLine(
                        color = OakWood,
                        start = Offset(w * 0.22f, h * 0.36f),
                        end = Offset(w * 0.50f, h * 0.36f),
                        strokeWidth = 2.8.dp.toPx()
                    )
                    drawLine(
                        color = OakWood,
                        start = Offset(w * 0.50f, h * 0.62f),
                        end = Offset(w * 0.78f, h * 0.62f),
                        strokeWidth = 2.8.dp.toPx()
                    )
                }

                GolfGameType.NEAR_PIN_PUTTING -> {
                    drawOval(
                        color = SkyPondBlue.copy(alpha = 0.75f),
                        topLeft = Offset(w * 0.10f, h * 0.10f),
                        size = Size(w * 0.80f, h * 0.26f)
                    )
                    drawOval(
                        color = CloverLeaf,
                        topLeft = Offset(w * 0.08f, h * 0.34f),
                        size = Size(w * 0.84f, h * 0.56f)
                    )
                    drawLine(
                        color = OakWood,
                        start = Offset(w * 0.56f, h * 0.18f),
                        end = Offset(w * 0.56f, h * 0.58f),
                        strokeWidth = 2.dp.toPx()
                    )
                    val flag = Path().apply {
                        moveTo(w * 0.56f, h * 0.18f)
                        lineTo(w * 0.80f, h * 0.26f)
                        lineTo(w * 0.56f, h * 0.34f)
                        close()
                    }
                    drawPath(flag, TerracottaWood)
                    drawCircle(
                        color = PureCardWhite,
                        radius = 4.dp.toPx(),
                        center = Offset(w * 0.36f, h * 0.70f)
                    )
                }

                GolfGameType.BALL_BASKET -> {
                    drawCircle(PureCardWhite, radius = 5.dp.toPx(), center = Offset(w * 0.34f, h * 0.42f))
                    drawCircle(SunlightAmber, radius = 5.2.dp.toPx(), center = Offset(w * 0.52f, h * 0.36f))
                    drawCircle(PureCardWhite, radius = 5.dp.toPx(), center = Offset(w * 0.68f, h * 0.43f))
                    val basket = Path().apply {
                        moveTo(w * 0.14f, h * 0.46f)
                        lineTo(w * 0.86f, h * 0.46f)
                        lineTo(w * 0.74f, h * 0.86f)
                        lineTo(w * 0.26f, h * 0.86f)
                        close()
                    }
                    drawPath(basket, OakWood)
                }

                GolfGameType.WINDMILL_ROULETTE -> {
                    drawCircle(
                        color = LightBirch,
                        radius = w * 0.38f,
                        center = Offset(w * 0.5f, h * 0.52f)
                    )
                    drawCircle(
                        color = OakWood,
                        radius = w * 0.38f,
                        center = Offset(w * 0.5f, h * 0.52f),
                        style = Stroke(width = 2.2.dp.toPx())
                    )
                    for (deg in listOf(0f, 90f, 180f, 270f)) {
                        rotate(deg, Offset(w * 0.5f, h * 0.52f)) {
                            drawLine(
                                color = OakWood,
                                start = Offset(w * 0.5f, h * 0.52f),
                                end = Offset(w * 0.5f, h * 0.14f),
                                strokeWidth = 1.6.dp.toPx()
                            )
                        }
                    }
                    val pointer = Path().apply {
                        moveTo(w * 0.43f, h * 0.06f)
                        lineTo(w * 0.57f, h * 0.06f)
                        lineTo(w * 0.50f, h * 0.22f)
                        close()
                    }
                    drawPath(pointer, TerracottaWood)
                }

                GolfGameType.LUCKY_WOOD_TILE -> {
                    rotate(-10f, Offset(w * 0.38f, h * 0.52f)) {
                        drawRoundRect(
                            color = LightBirch,
                            topLeft = Offset(w * 0.18f, h * 0.20f),
                            size = Size(w * 0.40f, h * 0.62f),
                            cornerRadius = CornerRadius(5.dp.toPx())
                        )
                        drawRoundRect(
                            color = OakWood,
                            topLeft = Offset(w * 0.18f, h * 0.20f),
                            size = Size(w * 0.40f, h * 0.62f),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }
                    rotate(10f, Offset(w * 0.62f, h * 0.52f)) {
                        drawRoundRect(
                            color = PureCardWhite,
                            topLeft = Offset(w * 0.42f, h * 0.20f),
                            size = Size(w * 0.40f, h * 0.62f),
                            cornerRadius = CornerRadius(5.dp.toPx())
                        )
                        drawRoundRect(
                            color = MeadowGreen,
                            topLeft = Offset(w * 0.42f, h * 0.20f),
                            size = Size(w * 0.40f, h * 0.62f),
                            cornerRadius = CornerRadius(5.dp.toPx()),
                            style = Stroke(width = 1.8.dp.toPx())
                        )
                        drawCircle(
                            color = TerracottaWood,
                            radius = 4.5.dp.toPx(),
                            center = Offset(w * 0.62f, h * 0.51f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * 모든 미니게임이 공유하는 상단 헤더·선수입장·사운드 설정·슬로우모션·축하 파티클 통합 프레임
 */
@Composable
fun GameStageScaffold(
    gameType: GolfGameType,
    settings: AppSettings,
    isSlowMotion: Boolean,
    slowMotionLabel: String = "결정적 순간 · 슬로우모션 발동",
    showCelebration: Boolean,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit,
    bottomControls: @Composable () -> Unit,
    stageContent: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCream)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 상단 네비게이션 및 선수입장·사운드 설정 바
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(WarmIvory)
                    .border(1.dp, SubtleDivider, CircleShape)
                    .clickable { onBack() }
            ) {
                Canvas(modifier = Modifier.size(16.dp).clipToBounds()) {
                    val path = Path().apply {
                        moveTo(size.width * 0.68f, size.height * 0.16f)
                        lineTo(size.width * 0.28f, size.height * 0.50f)
                        lineTo(size.width * 0.68f, size.height * 0.84f)
                    }
                    drawPath(
                        path = path,
                        color = InkPrimary,
                        style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gameType.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${gameType.tagLabel} · ${settings.normalizedDurationSeconds}초 진행",
                    style = MaterialTheme.typography.labelMedium,
                    color = gameType.accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 선수입장(명단·시간 변경) 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(WarmIvory)
                    .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
                    .clickable { onOpenPlayerDialog() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "선수입장 ${settings.players.size}명",
                    style = MaterialTheme.typography.labelLarge,
                    color = InkPrimary
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            // 사운드 상세 설정 버튼
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (settings.soundEnabled) MeadowGreenSoft else WarmIvory)
                    .border(
                        1.dp,
                        if (settings.soundEnabled) MeadowGreen else SubtleDivider,
                        RoundedCornerShape(10.dp)
                    )
                    .clickable { onOpenSoundDialog() }
                    .padding(horizontal = 10.dp, vertical = 7.dp)
            ) {
                Text(
                    text = if (settings.soundEnabled) "사운드 설정" else "음소거",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (settings.soundEnabled) PineDeep else InkMuted
                )
            }
        }

        // 규칙 한 줄 안내 서브 배너
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(WarmIvory)
                .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = gameType.ruleSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = InkSecondary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 메인 인터랙티브 스테이지
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(18.dp))
                .border(1.dp, SubtleDivider, RoundedCornerShape(18.dp))
                .clipToBounds()
        ) {
            stageContent()

            FloatingDandelionOverlay(
                isSlowMotion = isSlowMotion,
                modifier = Modifier.fillMaxSize()
            )

            SlowMotionPulseBanner(
                visible = isSlowMotion,
                message = slowMotionLabel,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )

            CelebrationPetalOverlay(
                visible = showCelebration,
                modifier = Modifier.fillMaxSize()
            )
        }

        // 하단 컨트롤 바
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            bottomControls()
        }
    }
}

/**
 * 게임 최종 결과 요약 모달 다이얼로그 (모든 결과 순위 배지가 서로 다른 고유 색상을 표시)
 */
@Composable
fun GameResultDialog(
    title: String,
    subtitle: String,
    rows: List<GameResultRow>,
    onReplay: () -> Unit,
    onCloseToHome: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .padding(vertical = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(16.dp))

                rows.forEachIndexed { index, row ->
                    val distinctBadgeColor = distinctRankBadgeColor(index)
                    val bg = if (row.isHighlighted) SunlightSoft else PureCardWhite
                    val border = if (row.isHighlighted) distinctBadgeColor else SubtleDivider

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .border(if (row.isHighlighted) 1.6.dp else 1.dp, border, RoundedCornerShape(14.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Image(
                            painter = painterResource(id = row.avatarRes),
                            contentDescription = row.playerName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, distinctBadgeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = row.playerName,
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Text(
                                text = row.detailText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(distinctBadgeColor)
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = row.badgeText,
                                style = MaterialTheme.typography.labelLarge,
                                color = PureCardWhite,
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (index < rows.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onCloseToHome,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleDivider),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("다른 게임", color = InkPrimary, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onReplay,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("다시 하기", color = PureCardWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * 전 게임 공통 '선수입장' 및 2클릭 성씨+호칭 조합·프리셋·게임 진행 시간 설정 모달
 */
@Composable
fun PlayerEntryDialog(
    targetGame: GolfGameType?,
    isBeforeGameStart: Boolean,
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onConfirm: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val editableNames = remember(settings.playerNames) {
        mutableStateListOf<String>().apply { addAll(settings.playerNames) }
    }
    val customSnapshot = remember(settings.savedCustomNames, settings.playerNames) {
        mutableStateListOf<String>().apply {
            val base = settings.savedCustomNames.toMutableList()
            while (base.size < editableNames.size) {
                base.add("${base.size + 1}번캐디백")
            }
            addAll(base.take(6))
        }
    }
    var selectedSlot by remember { mutableIntStateOf(0) }
    var durationSec by remember(settings.normalizedDurationSeconds) {
        mutableIntStateOf(settings.normalizedDurationSeconds)
    }

    val avatars = remember {
        listOf(
            com.example.fairwaypicnic.R.drawable.avatar_golfer_1,
            com.example.fairwaypicnic.R.drawable.avatar_golfer_2,
            com.example.fairwaypicnic.R.drawable.avatar_golfer_3,
            com.example.fairwaypicnic.R.drawable.avatar_golfer_4
        )
    }

    val isCaddiePreset = KoreanGolfNameHelper.isAllCaddieBagPreset(editableNames)
    val isParticipantPreset = KoreanGolfNameHelper.isAllParticipantPreset(editableNames)
    val isCustomActive = !isCaddiePreset && !isParticipantPreset

    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    fun syncSlotToCustom(slot: Int, newName: String) {
        editableNames[slot] = newName
        while (customSnapshot.size <= slot) {
            customSnapshot.add("${customSnapshot.size + 1}번캐디백")
        }
        customSnapshot[slot] = newName
    }

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
                .heightIn(max = screenHeightDp * 0.88f)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // 상단 고정 헤더
                Text(
                    text = if (targetGame != null && isBeforeGameStart) {
                        "선수입장 · ${targetGame.title}"
                    } else {
                        "선수입장 · 참가자 표기 설정"
                    },
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (isBeforeGameStart) {
                        "설정된 선수 명단은 다음 게임에도 그대로 이어지며 언제든 변경할 수 있습니다"
                    } else {
                        "기본 캐디백·참가자 번호 또는 성씨+호칭 클릭 두 번으로 간편하게 설정하세요"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 중앙 스크롤 영역
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 1. 인원 증감 및 3단 프리셋 원터치 선택 바
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "출전 인원 (${editableNames.size}명)",
                            style = MaterialTheme.typography.titleMedium,
                            color = InkPrimary
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SmallOutlineActionButton(
                                text = "인원 -",
                                enabled = editableNames.size > 2,
                                onClick = {
                                    if (editableNames.size > 2) {
                                        editableNames.removeAt(editableNames.lastIndex)
                                        if (selectedSlot >= editableNames.size) {
                                            selectedSlot = editableNames.lastIndex
                                        }
                                        soundEngine.playWoodTick(false)
                                    }
                                }
                            )
                            SmallOutlineActionButton(
                                text = "인원 +",
                                enabled = editableNames.size < 6,
                                highlight = true,
                                onClick = {
                                    if (editableNames.size < 6) {
                                        val nextNum = editableNames.size + 1
                                        val nextLabel = when {
                                            isCaddiePreset -> "${nextNum}번캐디백"
                                            isParticipantPreset -> "${nextNum}번참가자"
                                            else -> customSnapshot.getOrElse(nextNum - 1) { "${nextNum}번캐디백" }
                                        }
                                        editableNames.add(nextLabel)
                                        soundEngine.playWoodTick(true)
                                    }
                                }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 기본값(캐디백) / 두번째값(참가자) / 설정된 이름 프리셋 탭
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        PresetModeChip(
                            title = "1~${editableNames.size}번캐디백",
                            subtitle = "기본값",
                            selected = isCaddiePreset,
                            onClick = {
                                val list = KoreanGolfNameHelper.defaultCaddieBagNames(editableNames.size)
                                list.forEachIndexed { i, v -> editableNames[i] = v }
                                soundEngine.playWoodTick(true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PresetModeChip(
                            title = "1~${editableNames.size}번참가자",
                            subtitle = "번호 표기",
                            selected = isParticipantPreset,
                            onClick = {
                                val list = KoreanGolfNameHelper.defaultParticipantNames(editableNames.size)
                                list.forEachIndexed { i, v -> editableNames[i] = v }
                                soundEngine.playWoodTick(true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        PresetModeChip(
                            title = "설정된 이름",
                            subtitle = "맞춤 선수명",
                            selected = isCustomActive,
                            onClick = {
                                val fallback = KoreanGolfNameHelper.defaultSampleCustomNames(editableNames.size)
                                for (i in editableNames.indices) {
                                    val saved = customSnapshot.getOrNull(i)?.trim().orEmpty()
                                    val validSaved = if (
                                        saved.isNotEmpty() &&
                                        !saved.endsWith("번캐디백") &&
                                        !saved.endsWith("번참가자")
                                    ) saved else fallback[i]
                                    editableNames[i] = validSaved
                                }
                                soundEngine.playWoodTick(true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 2. 참가 선수 슬롯 카드 2열 배치 (터치하여 이름 조합 대상 선택)
                    Text(
                        text = "참가자 슬롯 선택 (클릭 후 아래 추천 성씨·호칭으로 2번 만에 입력)",
                        style = MaterialTheme.typography.labelMedium,
                        color = InkSecondary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    val slotChunks = editableNames.indices.chunked(2)
                    slotChunks.forEach { rowIndices ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            rowIndices.forEach { idx ->
                                val isSelected = selectedSlot == idx
                                val badgeCol = PlayerBadgeColors[idx % PlayerBadgeColors.size]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isSelected) MeadowGreenSoft else PureCardWhite)
                                        .border(
                                            width = if (isSelected) 1.8.dp else 1.dp,
                                            color = if (isSelected) MeadowGreen else SubtleDivider,
                                            shape = RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            selectedSlot = idx
                                            soundEngine.playWoodTick(false)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = avatars[idx % avatars.size]),
                                        contentDescription = editableNames[idx],
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(30.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, badgeCol, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(7.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${idx + 1}번 선수",
                                            fontSize = 10.sp,
                                            color = if (isSelected) PineDeep else InkMuted,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = editableNames[idx],
                                            style = MaterialTheme.typography.titleMedium,
                                            color = InkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            if (rowIndices.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // 3. 선택된 선수 전용 2클릭 추천 조합기 (1단계 성씨 + 2단계 호칭 + 직접입력)
                    val safeSlot = selectedSlot.coerceIn(0, editableNames.lastIndex)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(WarmIvory)
                            .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "${safeSlot + 1}번 선수 빠른 이름 조합",
                                style = MaterialTheme.typography.labelLarge,
                                color = PineDeep
                            )
                            Text(
                                text = "현재 표기: ${editableNames[safeSlot]}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TerracottaWood
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "1단계 성씨 원터치 (선택 시 '김씨' 표기)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        // 11개 다빈도 성씨 버튼 그리드 (4열 배치)
                        KoreanGolfNameHelper.recommendedSurnames.chunked(4).forEach { surnameRow ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                            ) {
                                surnameRow.forEach { surname ->
                                    val currentVal = editableNames[safeSlot]
                                    val stem = surname.removeSuffix("씨")
                                    val matched = currentVal == surname ||
                                        (currentVal.startsWith(stem) && currentVal != surname && !currentVal.endsWith("캐디백") && !currentVal.endsWith("참가자"))
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (matched) MeadowGreen else PureCardWhite)
                                            .border(
                                                1.dp,
                                                if (matched) MeadowGreen else SubtleDivider,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val applied = KoreanGolfNameHelper.applySurname(surname)
                                                syncSlotToCustom(safeSlot, applied)
                                                soundEngine.playWoodTick(highPitch = true)
                                            }
                                            .padding(vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = surname,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (matched) PureCardWhite else InkPrimary
                                        )
                                    }
                                }
                                repeat(4 - surnameRow.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "2단계 호칭 결합 ('씨' 제외 후 합침 · 예: 김씨 → 김프로)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = InkSecondary
                        )
                        Spacer(modifier = Modifier.height(5.dp))

                        // 6개 호칭 버튼 그리드 (3열 x 2행)
                        KoreanGolfNameHelper.recommendedTitles.chunked(3).forEach { titleRow ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 5.dp)
                            ) {
                                titleRow.forEach { title ->
                                    val activeSuffix = editableNames[safeSlot].endsWith(title)
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (activeSuffix) OakWood else PureCardWhite)
                                            .border(
                                                1.dp,
                                                if (activeSuffix) OakWood else SubtleDivider,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable {
                                                val combined = KoreanGolfNameHelper.applyTitle(
                                                    editableNames[safeSlot],
                                                    title
                                                )
                                                syncSlotToCustom(safeSlot, combined)
                                                soundEngine.playStickSlide(safeSlot)
                                                // 다음 참가자 슬롯으로 자연스럽게 포커스 이동하여 연속 2클릭 입력 편의 극대화
                                                if (safeSlot < editableNames.lastIndex) {
                                                    selectedSlot = safeSlot + 1
                                                }
                                            }
                                            .padding(vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = title,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (activeSuffix) PureCardWhite else InkPrimary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = editableNames[safeSlot],
                            onValueChange = { typed ->
                                syncSlotToCustom(safeSlot, typed.take(10))
                            },
                            singleLine = true,
                            label = { Text("${safeSlot + 1}번 선수 직접 입력 · 수정") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MeadowGreen,
                                unfocusedBorderColor = SubtleDivider,
                                focusedContainerColor = PureCardWhite,
                                unfocusedContainerColor = PureCardWhite,
                                focusedTextColor = InkPrimary,
                                unfocusedTextColor = InkPrimary
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 4. 게임 진행 시간(결과 도출 시간) 5초 · 10초 · 15초 선택
                    Text(
                        text = "최종 결과 도출 시간 (게임 진행 속도)",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DurationSelectorRow(
                        selectedSeconds = durationSec,
                        onSelectSeconds = { chosen ->
                            durationSec = chosen
                            soundEngine.playWoodTick(true)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = SubtleDivider)
                Spacer(modifier = Modifier.height(10.dp))

                // 하단 고정 실행 버튼
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleDivider),
                        modifier = Modifier
                            .weight(0.36f)
                            .height(48.dp)
                    ) {
                        Text("취소", color = InkSecondary)
                    }
                    Button(
                        onClick = {
                            val cleaned = editableNames.mapIndexed { i, s ->
                                s.trim().ifEmpty { "${i + 1}번캐디백" }
                            }
                            val updatedCustom = if (
                                !KoreanGolfNameHelper.isAllCaddieBagPreset(cleaned) &&
                                !KoreanGolfNameHelper.isAllParticipantPreset(cleaned)
                            ) {
                                cleaned
                            } else {
                                customSnapshot.toList()
                            }
                            onConfirm(
                                settings.copy(
                                    playerNames = cleaned,
                                    savedCustomNames = updatedCustom,
                                    gameDurationSeconds = durationSec
                                )
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(0.64f)
                            .height(48.dp)
                    ) {
                        Text(
                            text = if (isBeforeGameStart) "선수입장 완료 · 게임 시작" else "선수 설정 적용",
                            color = PureCardWhite,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 사운드 채널별 개별 ON/OFF 및 볼륨 조절 · 휴대폰 시스템 볼륨 연동 설정 모달
 */
@Composable
fun SoundSettingsDialog(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val sysAudio by soundEngine.systemAudioState.collectAsState()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = screenHeightDp * 0.88f)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "사운드 및 진행 속도 설정",
                    style = MaterialTheme.typography.titleLarge,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "휴대폰 측면 볼륨 버튼이 전체 마스터 볼륨에 즉시 반영됩니다",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // 휴대폰 시스템 오디오·무음·외부 스피커 연동 상태 안내 박스
                    val isPhoneSilent = sysAudio.ringerMode == AudioManager.RINGER_MODE_SILENT ||
                        (settings.muteInVibrateMode && sysAudio.ringerMode == AudioManager.RINGER_MODE_VIBRATE)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isPhoneSilent) TerracottaSoft else MeadowGreenSoft)
                            .border(
                                1.dp,
                                if (isPhoneSilent) TerracottaWood else MeadowGreen,
                                RoundedCornerShape(12.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "${sysAudio.ringerModeLabel} · ${sysAudio.externalDeviceLabel ?: "내장 스피커 출력"}",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (isPhoneSilent) TerracottaWood else PineDeep
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isPhoneSilent) {
                                "현재 휴대폰 소리 설정이 무음 상태이므로 모든 배경음악과 효과음이 자동 음소거됩니다."
                            } else {
                                "외부 스피커·블루투스 및 휴대폰 미디어 볼륨(${(sysAudio.mediaFraction * 100).roundToInt()}%)에 맞춰 음량이 자동 조절됩니다."
                            },
                            fontSize = 11.sp,
                            color = InkSecondary
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // 1. 마스터 사운드 스위치 + 휴대폰 미디어 볼륨 직결 슬라이더
                    SoundChannelCard(
                        title = "전체 마스터 볼륨 (휴대폰 미디어 볼륨)",
                        subtitle = "핸드폰 볼륨 버튼 조작 시 실시간 동기화",
                        enabled = settings.soundEnabled,
                        volume = sysAudio.mediaFraction,
                        onToggle = { on ->
                            onUpdateSettings(settings.copy(soundEnabled = on))
                        },
                        onVolumeChange = { frac ->
                            soundEngine.setPhoneMediaVolumeFraction(frac)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 2. 배경음악(BGM) 개별 스위치 & 볼륨
                    SoundChannelCard(
                        title = "지브리 배경음악 (로비 · 게임 테마곡)",
                        subtitle = "화면 분위기에 맞춘 어쿠스틱 배경음",
                        enabled = settings.bgmEnabled,
                        volume = settings.bgmVolume,
                        onToggle = { on ->
                            onUpdateSettings(settings.copy(bgmEnabled = on))
                        },
                        onVolumeChange = { vol ->
                            onUpdateSettings(settings.copy(bgmVolume = vol))
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 3. 게임 시작 · 진행 효과음 개별 스위치 & 볼륨
                    SoundChannelCard(
                        title = "게임 시작 · 진행 효과음",
                        subtitle = "티 바운스, 막대 뽑기, 사다리, 퍼팅, 룰렛, 패 넘김",
                        enabled = settings.sfxEnabled,
                        volume = settings.sfxVolume,
                        onToggle = { on ->
                            onUpdateSettings(settings.copy(sfxEnabled = on))
                        },
                        onVolumeChange = { vol ->
                            onUpdateSettings(settings.copy(sfxVolume = vol))
                        },
                        onVolumeChangeFinished = {
                            soundEngine.playWoodTick(highPitch = true)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 4. 클라이맥스 심장박동 · 슬로우모션 음향
                    SoundChannelCard(
                        title = "클라이맥스 두근두근 심장박동",
                        subtitle = "결과 직전 슬로우모션 쉬머 및 심장박동음",
                        enabled = settings.heartbeatEnabled,
                        volume = settings.heartbeatVolume,
                        onToggle = { on ->
                            onUpdateSettings(settings.copy(heartbeatEnabled = on))
                        },
                        onVolumeChange = { vol ->
                            onUpdateSettings(settings.copy(heartbeatVolume = vol))
                        },
                        onVolumeChangeFinished = {
                            soundEngine.playHeartbeat(intense = false)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 5. 최종 결과 발표 팡파르
                    SoundChannelCard(
                        title = "최종 결과 발표 팡파르",
                        subtitle = "순서 확정 및 커피 당첨 축하 멜로디",
                        enabled = settings.resultFanfareEnabled,
                        volume = settings.resultFanfareVolume,
                        onToggle = { on ->
                            onUpdateSettings(settings.copy(resultFanfareEnabled = on))
                        },
                        onVolumeChange = { vol ->
                            onUpdateSettings(settings.copy(resultFanfareVolume = vol))
                        },
                        onVolumeChangeFinished = {
                            soundEngine.playGhibliFanfare()
                        }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // 게임 진행 시간 (5초 / 10초 / 15초)
                    Text(
                        text = "최종 결과 도출 시간 (게임 진행 속도)",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    DurationSelectorRow(
                        selectedSeconds = settings.normalizedDurationSeconds,
                        onSelectSeconds = { sec ->
                            onUpdateSettings(settings.copy(gameDurationSeconds = sec))
                            soundEngine.playWoodTick(true)
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    SettingToggleRow(
                        title = "휴대폰 진동 모드에서도 소리 끄기",
                        checked = settings.muteInVibrateMode,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(muteInVibrateMode = it))
                        }
                    )
                    SettingToggleRow(
                        title = "심장박동 진동 햅틱 반응",
                        checked = settings.hapticEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(hapticEnabled = it))
                        }
                    )
                    SettingToggleRow(
                        title = "결정적 순간 슬로우모션 연출",
                        checked = settings.slowMotionEnabled,
                        onCheckedChange = {
                            onUpdateSettings(settings.copy(slowMotionEnabled = it))
                        }
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("설정 완료", color = PureCardWhite, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun DurationSelectorRow(
    selectedSeconds: Int,
    onSelectSeconds: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        5 to "5초 (빠른 진행)",
        10 to "10초 (표준 긴장감)",
        15 to "15초 (극적 슬로우)"
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        options.forEach { (sec, label) ->
            val selected = selectedSeconds == sec
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) MeadowGreen else PureCardWhite)
                    .border(
                        width = 1.dp,
                        color = if (selected) MeadowGreen else SubtleDivider,
                        shape = RoundedCornerShape(10.dp)
                    )
                    .clickable { onSelectSeconds(sec) }
                    .padding(vertical = 9.dp, horizontal = 4.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) PureCardWhite else InkPrimary,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun SoundChannelCard(
    title: String,
    subtitle: String,
    enabled: Boolean,
    volume: Float,
    onToggle: (Boolean) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureCardWhite)
            .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = InkPrimary
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = InkSecondary
                )
            }
            Text(
                text = if (enabled) "${(volume * 100).roundToInt()}%" else "꺼짐",
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) MeadowGreen else InkMuted,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = PureCardWhite,
                    checkedTrackColor = MeadowGreen,
                    uncheckedThumbColor = PureCardWhite,
                    uncheckedTrackColor = SoftSand
                )
            )
        }
        Slider(
            value = volume.coerceIn(0f, 1f),
            onValueChange = onVolumeChange,
            onValueChangeFinished = onVolumeChangeFinished,
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MeadowGreen,
                activeTrackColor = MeadowGreen,
                inactiveTrackColor = WarmIvory
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun PresetModeChip(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MeadowGreen else PureCardWhite)
            .border(
                width = 1.dp,
                color = if (selected) MeadowGreen else SubtleDivider,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 7.dp, horizontal = 4.dp)
    ) {
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = if (selected) PureCardWhite.copy(alpha = 0.85f) else InkMuted
        )
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureCardWhite else InkPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmallOutlineActionButton(
    text: String,
    enabled: Boolean,
    highlight: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(PureCardWhite)
            .border(1.dp, SubtleDivider, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (!enabled) InkMuted else if (highlight) MeadowGreen else InkPrimary
        )
    }
}

@Composable
private fun SettingToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = InkPrimary
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureCardWhite,
                checkedTrackColor = MeadowGreen,
                uncheckedThumbColor = PureCardWhite,
                uncheckedTrackColor = SoftSand
            )
        )
    }
}
