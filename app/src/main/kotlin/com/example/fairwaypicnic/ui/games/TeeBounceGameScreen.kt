package com.example.fairwaypicnic.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fairwaypicnic.R
import com.example.fairwaypicnic.audio.GhibliSoundEngine
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameResultRow
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.ui.components.GameResultDialog
import com.example.fairwaypicnic.ui.components.GameStageScaffold
import com.example.fairwaypicnic.ui.theme.CloverLeaf
import com.example.fairwaypicnic.ui.theme.InkPrimary
import com.example.fairwaypicnic.ui.theme.InkSecondary
import com.example.fairwaypicnic.ui.theme.LightBirch
import com.example.fairwaypicnic.ui.theme.MeadowGreen
import com.example.fairwaypicnic.ui.theme.MeadowGreenSoft
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private enum class TeePhase {
    READY,
    DROPPING,
    SPINNING,
    SLOW_MO_CLIMAX,
    FINISHED
}

/**
 * 1홀 티샷 순서 게임 1: 드라이버 헤드 위에 나무 티를 던져 가리키는 방향과 오른쪽 순서로 1~4번을 정하는 게임
 */
@Composable
fun TeeBounceGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)

    var phase by remember { mutableStateOf(TeePhase.READY) }
    var rightHandRule by remember { mutableStateOf(true) }

    var teeAngleDeg by remember { mutableFloatStateOf(-90f) }
    var targetAngleDeg by remember { mutableFloatStateOf(0f) }
    var startSpinAngle by remember { mutableFloatStateOf(0f) }
    var teeScale by remember { mutableFloatStateOf(1f) }
    var teeOffsetYRatio by remember { mutableFloatStateOf(-0.22f) }
    var driverRecoil by remember { mutableFloatStateOf(0f) }

    var winnerIndex by remember { mutableIntStateOf(-1) }
    var showResultModal by remember { mutableStateOf(false) }
    var runTrigger by remember { mutableIntStateOf(0) }

    fun computeLivePointedIndex(angleDeg: Float): Int {
        val normalizedTip = ((angleDeg + 90f) % 360f + 360f) % 360f
        val stepDeg = 360f / count
        return (((normalizedTip + stepDeg * 0.5f) % 360f) / stepDeg).toInt() % count
    }

    fun startToss() {
        if (phase == TeePhase.DROPPING || phase == TeePhase.SPINNING || phase == TeePhase.SLOW_MO_CLIMAX) return
        winnerIndex = -1
        showResultModal = false
        val durationSec = settings.normalizedDurationSeconds
        val chosenIdx = Random.nextInt(count)
        val sectorStep = 360f / count
        val baseSeatAngle = -90f + chosenIdx * sectorStep
        val jitter = Random.nextFloat() * (sectorStep * 0.44f) - (sectorStep * 0.22f)
        val baseTurns = when (durationSec) {
            5 -> 8
            15 -> 20
            else -> 14
        }
        val extraTurns = (baseTurns + Random.nextInt(3)) * 360f
        startSpinAngle = teeAngleDeg % 360f
        targetAngleDeg = extraTurns + baseSeatAngle + jitter
        winnerIndex = chosenIdx
        soundEngine.playTeeTossStart()
        runTrigger++
    }

    LaunchedEffect(runTrigger) {
        if (runTrigger == 0) return@LaunchedEffect

        val totalTargetSec = settings.normalizedDurationSeconds.toFloat()
        val dropPhaseSec = 0.85f
        val spinPhaseSec = (totalTargetSec - dropPhaseSec).coerceAtLeast(4.15f)

        // 1단계: 드라이버 헤드 페이스로 낙하 후 공중 바운스 (0.85초)
        phase = TeePhase.DROPPING
        soundEngine.setClimaxDucking(false)
        var t = 0f
        var hitPlayed = false
        var prevNanos = 0L
        while (t < dropPhaseSec) {
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    t += ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prevNanos = now
            }
            val impactAt = 0.36f
            if (t < impactAt) {
                val p = (t / impactAt).coerceIn(0f, 1f)
                teeOffsetYRatio = -0.32f * (1f - p * p)
                teeScale = 1.28f - 0.28f * p
            } else {
                if (!hitPlayed) {
                    hitPlayed = true
                    soundEngine.playTeeBounce()
                }
                val p = ((t - impactAt) / (dropPhaseSec - impactAt)).coerceIn(0f, 1f)
                driverRecoil = sin(p * PI).toFloat() * 11f
                teeOffsetYRatio = -sin(p * PI).toFloat() * 0.20f
                teeScale = 1.0f + sin(p * PI).toFloat() * 0.40f
                teeAngleDeg = startSpinAngle + p * 320f
            }
        }
        driverRecoil = 0f
        teeOffsetYRatio = 0f
        teeScale = 1f
        soundEngine.playGrassLand()

        // 2단계: 선택된 진행시간(5초·10초·15초)에 맞춘 잔디 회전 및 슬로우모션 감속
        phase = TeePhase.SPINNING
        val spinStart = teeAngleDeg
        val totalDelta = targetAngleDeg - spinStart
        var elapsed = 0f
        var enteredSlowMo = false
        var heartbeatAccum = 0f
        var lastSector = computeLivePointedIndex(spinStart)
        prevNanos = 0L

        while (elapsed < spinPhaseSec) {
            var dt = 0f
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prevNanos = now
            }
            elapsed = (elapsed + dt).coerceAtMost(spinPhaseSec)
            val u = (elapsed / spinPhaseSec).coerceIn(0f, 1f)

            // 후반 36% 시간 구간에서 슬로우모션 포커스 및 심장박동 돌입
            val inClimax = settings.slowMotionEnabled && u >= 0.64f && u < 0.992f
            if (inClimax && !enteredSlowMo) {
                enteredSlowMo = true
                phase = TeePhase.SLOW_MO_CLIMAX
                soundEngine.setClimaxDucking(true)
                soundEngine.playSlowMoEnter()
                soundEngine.playHeartbeat(intense = true)
            }

            // 3.4차 감속 곡선으로 초반 쾌속 회전 -> 후반 아슬아슬한 초저속 감속 자연 구현
            val eased = 1f - (1f - u).pow(3.4f)
            teeAngleDeg = spinStart + totalDelta * eased

            val curSector = computeLivePointedIndex(teeAngleDeg)
            if (curSector != lastSector) {
                lastSector = curSector
                soundEngine.playSpinWhooshTick(highPitch = curSector % 2 == 0)
            }

            if (inClimax) {
                heartbeatAccum += dt
                if (heartbeatAccum >= 0.62f) {
                    heartbeatAccum = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        phase = TeePhase.FINISHED
        soundEngine.playGhibliFanfare()
        delay(950)
        showResultModal = true
    }

    fun getOrderForSeat(seatIndex: Int): Int {
        if (winnerIndex < 0) return 0
        val diff = if (rightHandRule) {
            ((winnerIndex - seatIndex) % count + count) % count
        } else {
            ((seatIndex - winnerIndex) % count + count) % count
        }
        return diff + 1
    }

    val resultRows = remember(winnerIndex, rightHandRule, players, phase) {
        if (phase != TeePhase.FINISHED || winnerIndex < 0) emptyList()
        else {
            (1..count).map { order ->
                val seatIdx = (0 until count).first { getOrderForSeat(it) == order }
                val p = players[seatIdx]
                GameResultRow(
                    badgeText = if (order == 1) "1번 오너" else "${order}번 타순",
                    playerName = p.name,
                    detailText = if (order == 1) "나무 티 끝이 직접 가리킨 첫 티샷 주인공" else "1번 오너 기준 오른쪽 순서 배정",
                    isHighlighted = order == 1,
                    avatarRes = p.avatarRes,
                    badgeColor = distinctRankBadgeColor(order - 1)
                )
            }
        }
    }

    GameStageScaffold(
        gameType = GolfGameType.TEE_BOUNCE,
        settings = settings,
        isSlowMotion = phase == TeePhase.SLOW_MO_CLIMAX,
        slowMotionLabel = "티 끝이 누구를 가리킬지 감속 중 · 슬로우모션",
        showCelebration = phase == TeePhase.FINISHED,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(WarmIvory)
                        .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "2~${count}번 배정 방향",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        DirectionChip(
                            label = "1번의 오른쪽 사람 순",
                            selected = rightHandRule,
                            onClick = { rightHandRule = true }
                        )
                        DirectionChip(
                            label = "화면 시계방향",
                            selected = !rightHandRule,
                            onClick = { rightHandRule = false }
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { startToss() },
                        enabled = phase == TeePhase.READY || phase == TeePhase.FINISHED,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (phase == TeePhase.FINISHED) "다시 티 던지기" else "드라이버 헤드에 티 던지기 (${settings.normalizedDurationSeconds}초)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    if (phase == TeePhase.FINISHED) {
                        Button(
                            onClick = { showResultModal = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OakWood),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("순서표 보기", color = PureCardWhite)
                        }
                    }
                }
            }
        }
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFDCEAD8))
                .clickable(enabled = phase == TeePhase.READY || phase == TeePhase.FINISHED) {
                    startToss()
                }
        ) {
            val density = LocalDensity.current
            val widthPx = constraints.maxWidth.toFloat()
            val heightPx = constraints.maxHeight.toFloat()
            val centerX = widthPx * 0.5f
            val centerY = heightPx * 0.52f
            val ringRadius = minOf(widthPx, heightPx) * 0.34f

            Image(
                painter = painterResource(id = R.drawable.bg_fairway_meadow),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.24f,
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                drawCircle(
                    color = CloverLeaf.copy(alpha = 0.26f),
                    radius = ringRadius * 1.18f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = PureCardWhite.copy(alpha = 0.52f),
                    radius = ringRadius * 0.86f,
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = MeadowGreen.copy(alpha = 0.35f),
                    radius = ringRadius * 0.86f,
                    center = Offset(centerX, centerY),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))
                    )
                )

                val step = 360f / count
                for (i in 0 until count) {
                    val boundaryDeg = -90f + step * i - step * 0.5f
                    val rad = boundaryDeg * (PI / 180.0)
                    drawLine(
                        color = MeadowGreen.copy(alpha = 0.18f),
                        start = Offset(centerX, centerY),
                        end = Offset(
                            centerX + cos(rad).toFloat() * ringRadius * 0.84f,
                            centerY + sin(rad).toFloat() * ringRadius * 0.84f
                        ),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }

                if (phase == TeePhase.FINISHED && winnerIndex >= 0) {
                    val startDeg = -90f + winnerIndex * step
                    val sweep = if (rightHandRule) -(360f - step * 0.7f) else (360f - step * 0.7f)
                    val arcR = ringRadius * 0.64f
                    drawArc(
                        color = SunlightAmber.copy(alpha = 0.75f),
                        startAngle = startDeg,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(centerX - arcR, centerY - arcR),
                        size = Size(arcR * 2f, arcR * 2f),
                        style = Stroke(
                            width = 2.6.dp.toPx(),
                            cap = StrokeCap.Round,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 9f))
                        )
                    )
                }

                val driverCx = centerX - 18.dp.toPx()
                val driverCy = centerY + 14.dp.toPx() + driverRecoil
                drawLine(
                    color = OakWood,
                    start = Offset(driverCx - 95.dp.toPx(), driverCy + 68.dp.toPx()),
                    end = Offset(driverCx - 18.dp.toPx(), driverCy + 8.dp.toPx()),
                    strokeWidth = 6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                rotate(-18f, Offset(driverCx, driverCy)) {
                    drawOval(
                        color = PineDeep.copy(alpha = 0.90f),
                        topLeft = Offset(driverCx - 38.dp.toPx(), driverCy - 24.dp.toPx()),
                        size = Size(76.dp.toPx(), 50.dp.toPx())
                    )
                    drawRoundRect(
                        color = LightBirch,
                        topLeft = Offset(driverCx - 24.dp.toPx(), driverCy - 18.dp.toPx()),
                        size = Size(48.dp.toPx(), 13.dp.toPx()),
                        cornerRadius = CornerRadius(5.dp.toPx())
                    )
                    for (g in -14..14 step 7) {
                        drawLine(
                            color = OakWood,
                            start = Offset(driverCx + g.dp.toPx(), driverCy - 15.dp.toPx()),
                            end = Offset(driverCx + g.dp.toPx(), driverCy - 8.dp.toPx()),
                            strokeWidth = 1.2.dp.toPx()
                        )
                    }
                }

                val teeCenterY = centerY + teeOffsetYRatio * heightPx
                val teeLength = 66.dp.toPx() * teeScale
                val teeCupHalf = 9.dp.toPx() * teeScale

                drawOval(
                    color = Color.Black.copy(alpha = 0.14f),
                    topLeft = Offset(centerX - teeLength * 0.42f, centerY + 8.dp.toPx()),
                    size = Size(teeLength * 0.84f, 12.dp.toPx())
                )

                rotate(degrees = teeAngleDeg, pivot = Offset(centerX, teeCenterY)) {
                    val bodyPath = Path().apply {
                        moveTo(centerX - teeLength * 0.46f, teeCenterY - teeCupHalf)
                        lineTo(centerX - teeLength * 0.34f, teeCenterY - teeCupHalf * 0.48f)
                        lineTo(centerX + teeLength * 0.54f, teeCenterY)
                        lineTo(centerX - teeLength * 0.34f, teeCenterY + teeCupHalf * 0.48f)
                        lineTo(centerX - teeLength * 0.46f, teeCenterY + teeCupHalf)
                        close()
                    }
                    drawPath(path = bodyPath, color = LightBirch)
                    drawPath(
                        path = bodyPath,
                        color = OakWood,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                    val tipPath = Path().apply {
                        moveTo(centerX + teeLength * 0.22f, teeCenterY - teeCupHalf * 0.24f)
                        lineTo(centerX + teeLength * 0.54f, teeCenterY)
                        lineTo(centerX + teeLength * 0.22f, teeCenterY + teeCupHalf * 0.24f)
                        close()
                    }
                    drawPath(path = tipPath, color = TerracottaWood)
                }
            }

            val stepDeg = 360f / count
            val livePointedIdx = computeLivePointedIndex(teeAngleDeg)

            // 원형 둘레 참가 선수 아바타 카드 및 순위별 고유 컬러 배지 렌더링
            players.forEachIndexed { idx, player ->
                val seatDeg = -90f + idx * stepDeg
                val rad = seatDeg * (PI / 180.0)
                val cardW = 106.dp
                val cardH = 68.dp
                val px = centerX + cos(rad).toFloat() * ringRadius
                val py = centerY + sin(rad).toFloat() * ringRadius

                val isPointedLive = (phase == TeePhase.SPINNING || phase == TeePhase.SLOW_MO_CLIMAX) && livePointedIdx == idx
                val finalOrder = if (phase == TeePhase.FINISHED) getOrderForSeat(idx) else 0
                val isOwner = finalOrder == 1
                val rankColor = if (finalOrder >= 1) distinctRankBadgeColor(finalOrder - 1) else player.badgeColor

                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = (px - with(density) { (cardW / 2).toPx() }).roundToInt(),
                                y = (py - with(density) { (cardH / 2).toPx() }).roundToInt()
                            )
                        }
                        .width(cardW)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            when {
                                isOwner -> PureCardWhite
                                isPointedLive -> MeadowGreenSoft
                                else -> PureCardWhite.copy(alpha = 0.94f)
                            }
                        )
                        .border(
                            width = if (finalOrder >= 1 || isPointedLive) 2.dp else 1.dp,
                            color = when {
                                finalOrder >= 1 -> rankColor
                                isPointedLive -> MeadowGreen
                                else -> SubtleDivider
                            },
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(
                                painter = painterResource(id = player.avatarRes),
                                contentDescription = player.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .border(1.2.dp, rankColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = player.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        val statusText = when {
                            finalOrder == 1 -> "1번 오너"
                            finalOrder > 1 -> "${finalOrder}번 타순"
                            isPointedLive -> "가리키는 중"
                            else -> "대기"
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    when {
                                        finalOrder >= 1 -> rankColor
                                        isPointedLive -> MeadowGreen
                                        else -> WarmIvory
                                    }
                                )
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (finalOrder > 0 || isPointedLive) PureCardWhite else InkSecondary
                            )
                        }
                    }
                }
            }

            if (phase == TeePhase.READY) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(PureCardWhite.copy(alpha = 0.90f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "화면을 터치하면 드라이버 헤드 위로 나무 티가 떨어집니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkPrimary
                    )
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = "1번 홀 티샷 순서 확정",
            subtitle = if (rightHandRule) "티가 가리킨 오너부터 오른쪽 동반자 순서로 배정되었습니다." else "티가 가리킨 오너부터 시계방향으로 배정되었습니다.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                startToss()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}

@Composable
private fun DirectionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MeadowGreen else PureCardWhite)
            .border(1.dp, if (selected) MeadowGreen else SubtleDivider, RoundedCornerShape(8.dp))
            .clickable { onClick() }
            .padding(horizontal = 9.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) PureCardWhite else InkSecondary
        )
    }
}
