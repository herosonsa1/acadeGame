package com.example.fairwaypicnic.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fairwaypicnic.R
import com.example.fairwaypicnic.audio.GhibliSoundEngine
import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameResultRow
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.ui.components.GameResultDialog
import com.example.fairwaypicnic.ui.components.GameStageScaffold
import com.example.fairwaypicnic.ui.theme.InkPrimary
import com.example.fairwaypicnic.ui.theme.InkSecondary
import com.example.fairwaypicnic.ui.theme.LightBirch
import com.example.fairwaypicnic.ui.theme.MeadowGreen
import com.example.fairwaypicnic.ui.theme.MeadowGreenSoft
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PaperCream
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SkyPondSoft
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.SunlightSoft
import com.example.fairwaypicnic.ui.theme.TerracottaSoft
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

private enum class RouletteTopic(val label: String, val prizeCaption: String) {
    COFFEE("그늘집 커피 쏘기", "시원한 아이스 커피와 음료 내기"),
    SNACK("그늘집 간식 쏘기", "떡볶이·만두·간식 테이블 쏘기"),
    CADDIE_TIP("캐디피 끝전 내기", "오늘 라운딩 정산 끝전 깔끔하게 담당")
}

/**
 * 커피·꼴등 복불복 게임 6: 언덕 위 풍차 나무 룰렛
 */
@Composable
fun WindmillRouletteGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)
    val textMeasurer = rememberTextMeasurer()

    var topic by remember { mutableStateOf(RouletteTopic.COFFEE) }
    var wheelAngle by remember { mutableFloatStateOf(0f) }
    var startAngle by remember { mutableFloatStateOf(0f) }
    var targetAngle by remember { mutableFloatStateOf(0f) }
    var flapperTilt by remember { mutableFloatStateOf(0f) }

    var isSpinning by remember { mutableStateOf(false) }
    var isSlowMotion by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var winnerIdx by remember { mutableIntStateOf(-1) }
    var showResultModal by remember { mutableStateOf(false) }
    var spinTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    // 12시 방향(-90도) 포인터가 가리키는 조각 계산 함수
    fun resolveSliceIndex(angleDeg: Float): Int {
        val sliceDeg = 360f / count
        val rel = ((270f - angleDeg) % 360f + 360f) % 360f
        return (rel / sliceDeg).toInt().coerceIn(0, count - 1)
    }

    fun triggerSpin() {
        if (isSpinning) return
        isFinished = false
        showResultModal = false
        soundEngine.playWindmillStart()
        startAngle = wheelAngle % 360f
        val baseTurns = when (settings.normalizedDurationSeconds) {
            5 -> 7 + Random.nextInt(3)
            15 -> 17 + Random.nextInt(5)
            else -> 12 + Random.nextInt(4)
        }
        val extraSpins = baseTurns * 360f
        val randomStop = Random.nextFloat() * 360f
        targetAngle = startAngle + extraSpins + randomStop
        spinTrigger++
    }

    LaunchedEffect(spinTrigger) {
        if (spinTrigger == 0) return@LaunchedEffect
        isSpinning = true
        isSlowMotion = false
        soundEngine.setClimaxDucking(false)

        val totalSec = settings.normalizedDurationSeconds.toFloat()
        var lastSlice = resolveSliceIndex(startAngle)
        var elapsed = 0f
        var slowEntered = false
        var hbTimer = 0f
        var prevNanos = 0L

        while (elapsed < totalSec) {
            var dt = 0f
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prevNanos = now
            }

            elapsed = (elapsed + dt).coerceAtMost(totalSec)
            val u = (elapsed / totalSec).coerceIn(0f, 1f)

            // 멈추기 직전 아슬아슬하게 황동 못에 걸리는 후반 구간에서 슬로우모션 돌입
            val inClimax = settings.slowMotionEnabled && u >= 0.68f && u < 0.992f
            if (inClimax && !slowEntered) {
                slowEntered = true
                isSlowMotion = true
                soundEngine.setClimaxDucking(true)
                soundEngine.playSlowMoEnter()
                soundEngine.playHeartbeat(intense = true)
            } else if (!inClimax && isSlowMotion) {
                isSlowMotion = false
            }

            val eased = 1f - (1f - u).pow(3.6f)
            wheelAngle = startAngle + (targetAngle - startAngle) * eased

            val curSlice = resolveSliceIndex(wheelAngle)
            if (curSlice != lastSlice) {
                lastSlice = curSlice
                flapperTilt = -24f
                soundEngine.playRoulettePegTick(highPitch = curSlice % 2 == 0)
            } else {
                flapperTilt *= 0.84f
            }

            if (inClimax) {
                hbTimer += dt
                if (hbTimer >= 0.58f) {
                    hbTimer = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        flapperTilt = 0f
        isSlowMotion = false
        isSpinning = false
        winnerIdx = resolveSliceIndex(wheelAngle)
        isFinished = true
        soundEngine.playPenaltyReveal()
        delay(900)
        showResultModal = true
    }

    val slicePastels = remember {
        listOf(
            PureCardWhite,
            MeadowGreenSoft,
            SunlightSoft,
            SkyPondSoft,
            TerracottaSoft,
            WarmIvory
        )
    }

    val resultRows = remember(isFinished, winnerIdx, players, topic) {
        if (!isFinished || winnerIdx !in players.indices) emptyList()
        else {
            val orderedIndices = players.indices.sortedByDescending { it == winnerIdx }
            var safeRank = 1
            orderedIndices.mapIndexed { displayIdx, pIdx ->
                val pl = players[pIdx]
                val hit = pIdx == winnerIdx
                val badge = if (hit) {
                    topic.label
                } else {
                    val label = "면제 ${safeRank}"
                    safeRank++
                    label
                }
                GameResultRow(
                    badgeText = badge,
                    playerName = pl.name,
                    detailText = if (hit) topic.prizeCaption else "풍차 바늘을 비껴가며 무사 통과",
                    isHighlighted = hit,
                    avatarRes = pl.avatarRes,
                    badgeColor = distinctRankBadgeColor(displayIdx)
                )
            }
        }
    }

    val liveIdx = resolveSliceIndex(wheelAngle)

    GameStageScaffold(
        gameType = GolfGameType.WINDMILL_ROULETTE,
        settings = settings,
        isSlowMotion = isSlowMotion,
        slowMotionLabel = "풍차 바늘이 멈추기 직전 · 슬로우모션",
        showCelebration = isFinished,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    RouletteTopic.entries.forEach { item ->
                        val selected = topic == item
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MeadowGreen else WarmIvory)
                                .border(1.dp, if (selected) MeadowGreen else SubtleDivider, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isSpinning) {
                                    soundEngine.playWoodTick(true)
                                    topic = item
                                }
                                .padding(vertical = 8.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selected) PureCardWhite else InkSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Button(
                        onClick = { triggerSpin() },
                        enabled = !isSpinning,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        val sec = settings.normalizedDurationSeconds
                        Text(
                            text = when {
                                isSpinning -> "풍차 바퀴 회전 중..."
                                isFinished -> "다시 풍차 돌리기 (${sec}초)"
                                else -> "힘껏 풍차 바퀴 돌리기 (${sec}초)"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    if (isFinished) {
                        Button(
                            onClick = { showResultModal = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OakWood),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("결과 보기", color = PureCardWhite)
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PaperCream)
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_fairway_meadow),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.28f,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // 실시간 바늘이 가리키는 후보 표시
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureCardWhite.copy(alpha = 0.94f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val liveName = players.getOrNull(liveIdx)?.name ?: ""
                    Text(
                        text = if (isFinished) "당첨 확정: $liveName (${topic.label})"
                        else if (isSpinning) "현재 바늘 위치: $liveName"
                        else "룰렛을 손가락으로 스와이프하거나 아래 버튼을 누르세요",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isFinished) TerracottaWood else InkPrimary
                    )
                }

                // 풍차 룰렛 인터랙티브 캔버스
                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .pointerInput(isSpinning) {
                            if (isSpinning) return@pointerInput
                            detectDragGestures(
                                onDragEnd = { triggerSpin() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    wheelAngle += (dragAmount.x - dragAmount.y) * 0.25f
                                }
                            )
                        }
                ) {
                    val cx = size.width * 0.5f
                    val cy = size.height * 0.53f
                    val radius = minOf(size.width, size.height) * 0.39f
                    val sliceDeg = 360f / count

                    // 풍차 기둥 다리 받침대
                    val standPath = Path().apply {
                        moveTo(cx - radius * 0.26f, cy + radius * 0.65f)
                        lineTo(cx + radius * 0.26f, cy + radius * 0.65f)
                        lineTo(cx + radius * 0.42f, size.height)
                        lineTo(cx - radius * 0.42f, size.height)
                        close()
                    }
                    drawPath(standPath, OakWood.copy(alpha = 0.85f))

                    // 바깥 참나무 림
                    drawCircle(
                        color = OakWood,
                        radius = radius + 11.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                    drawCircle(
                        color = LightBirch,
                        radius = radius + 4.dp.toPx(),
                        center = Offset(cx, cy)
                    )

                    // 각 플레이어 부채꼴 조각 및 이름 렌더링
                    for (i in 0 until count) {
                        val startDeg = wheelAngle + i * sliceDeg
                        val fillCol = slicePastels[i % slicePastels.size]
                        drawArc(
                            color = fillCol,
                            startAngle = startDeg,
                            sweepAngle = sliceDeg,
                            useCenter = true,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = Size(radius * 2f, radius * 2f)
                        )
                        drawArc(
                            color = OakWood.copy(alpha = 0.45f),
                            startAngle = startDeg,
                            sweepAngle = sliceDeg,
                            useCenter = true,
                            topLeft = Offset(cx - radius, cy - radius),
                            size = Size(radius * 2f, radius * 2f),
                            style = Stroke(width = 1.6.dp.toPx())
                        )

                        // 경계선 황동 못(Peg)
                        val pegRad = startDeg * (PI / 180.0)
                        val pegX = cx + cos(pegRad).toFloat() * (radius - 6.dp.toPx())
                        val pegY = cy + sin(pegRad).toFloat() * (radius - 6.dp.toPx())
                        drawCircle(color = SunlightAmber, radius = 4.dp.toPx(), center = Offset(pegX, pegY))

                        // 조각 중앙 플레이어 이름 텍스트 회전 배치
                        val midDeg = startDeg + sliceDeg * 0.5f
                        rotate(degrees = midDeg + 90f, pivot = Offset(cx, cy)) {
                            val labelLayout = textMeasurer.measure(
                                text = players[i].name,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                            )
                            drawText(
                                textLayoutResult = labelLayout,
                                topLeft = Offset(
                                    cx - labelLayout.size.width * 0.5f,
                                    cy - radius * 0.66f - labelLayout.size.height * 0.5f
                                )
                            )
                            drawCircle(
                                color = players[i].badgeColor,
                                radius = 5.dp.toPx(),
                                center = Offset(cx, cy - radius * 0.44f)
                            )
                        }
                    }

                    // 중앙 풍차 날개 장식 허브
                    rotate(degrees = wheelAngle * 0.5f, pivot = Offset(cx, cy)) {
                        for (b in 0 until 4) {
                            rotate(b * 90f, Offset(cx, cy)) {
                                drawLine(
                                    color = OakWood.copy(alpha = 0.28f),
                                    start = Offset(cx, cy),
                                    end = Offset(cx, cy - radius * 0.28f),
                                    strokeWidth = 6.dp.toPx()
                                )
                            }
                        }
                    }
                    drawCircle(color = OakWood, radius = 20.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = SunlightAmber, radius = 10.dp.toPx(), center = Offset(cx, cy))

                    // 12시 방향 물리 플래퍼(포인터 바늘)
                    val pivotTop = Offset(cx, cy - radius - 14.dp.toPx())
                    rotate(degrees = flapperTilt, pivot = pivotTop) {
                        val pointerPath = Path().apply {
                            moveTo(pivotTop.x - 12.dp.toPx(), pivotTop.y - 6.dp.toPx())
                            lineTo(pivotTop.x + 12.dp.toPx(), pivotTop.y - 6.dp.toPx())
                            lineTo(pivotTop.x, pivotTop.y + 30.dp.toPx())
                            close()
                        }
                        drawPath(pointerPath, TerracottaWood)
                        drawPath(pointerPath, PureCardWhite, style = Stroke(width = 2.dp.toPx()))
                    }
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = "${topic.label} 룰렛 결과",
            subtitle = topic.prizeCaption,
            rows = resultRows,
            onReplay = {
                showResultModal = false
                triggerSpin()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
