package com.example.fairwaypicnic.ui.games

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.example.fairwaypicnic.ui.theme.PaperCream
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.SunlightSoft
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * 커피·꼴등 복불복 게임 5: 숲속 골프공 바구니 (러시안 룰렛식 당첨 볼 뽑기)
 */
@Composable
fun ForestBallBasketGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)
    val totalBalls = 9 // 3x3 버드나무 바구니 구성

    var roundSeed by remember { mutableIntStateOf(0) }
    val goldenBallIndex = remember(roundSeed) { Random.nextInt(totalBalls) }
    // 각 공을 누가 뽑았는지 (-1이면 아직 바구니 안에 있음)
    val pickedByPlayer = remember(roundSeed) {
        mutableStateListOf<Int>().apply { repeat(totalBalls) { add(-1) } }
    }

    var turnCounter by remember(roundSeed) { mutableIntStateOf(0) }
    var inspectingBallIdx by remember(roundSeed) { mutableIntStateOf(-1) }
    var isSlowMotion by remember(roundSeed) { mutableStateOf(false) }
    var isAutoDrawing by remember(roundSeed) { mutableStateOf(false) }
    var hitPlayerIdx by remember(roundSeed) { mutableIntStateOf(-1) }
    var revealScale by remember(roundSeed) { mutableFloatStateOf(0f) }
    var showResultModal by remember(roundSeed) { mutableStateOf(false) }
    var hitTrigger by remember { mutableIntStateOf(0) }
    var autoDrawTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    fun resetBasket() {
        soundEngine.setClimaxDucking(false)
        soundEngine.playBasketShuffle()
        showResultModal = false
        roundSeed++
    }

    fun startAutoDraw() {
        if (isAutoDrawing || inspectingBallIdx != -1 || hitPlayerIdx != -1) return
        soundEngine.playBasketShuffle()
        autoDrawTrigger++
    }

    fun onPickBall(ballIdx: Int) {
        if (isAutoDrawing || pickedByPlayer[ballIdx] != -1 || inspectingBallIdx != -1 || hitPlayerIdx != -1) return
        val activePlayerIdx = turnCounter % count
        val remainingBeforePick = pickedByPlayer.count { it == -1 }
        soundEngine.playBallInspectPop()

        if (ballIdx == goldenBallIndex) {
            // 당첨 볼 선택: 즉시 슬로우모션 클라이맥스 시퀀스 돌입
            inspectingBallIdx = ballIdx
            pickedByPlayer[ballIdx] = activePlayerIdx
            hitTrigger++
        } else {
            pickedByPlayer[ballIdx] = activePlayerIdx
            turnCounter++
            soundEngine.playStickSlide(turnCounter % 4)
            // 남은 공이 5개 이하이면 긴장감 유발 심장박동 재생
            if (remainingBeforePick <= 5) {
                soundEngine.playHeartbeat(intense = remainingBeforePick <= 3)
            }
        }
    }

    // 수동으로 당첨 볼을 클릭했을 때의 클라이맥스 확인 시퀀스 (설정 시간에 비례)
    LaunchedEffect(hitTrigger) {
        if (hitTrigger == 0 || inspectingBallIdx < 0) return@LaunchedEffect
        val chosenPlayer = pickedByPlayer[inspectingBallIdx]
        val inspectDurationSec = when (settings.normalizedDurationSeconds) {
            5 -> 2.1f
            15 -> 4.8f
            else -> 3.4f
        }

        if (settings.slowMotionEnabled) {
            isSlowMotion = true
            soundEngine.setClimaxDucking(true)
            soundEngine.playSlowMoEnter()
            soundEngine.playHeartbeat(intense = true)
            var elapsed = 0f
            var hb = 0f
            var prev = 0L
            while (elapsed < inspectDurationSec) {
                var dt = 0f
                withFrameNanos { now ->
                    if (prev != 0L) {
                        dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.04f)
                    }
                    prev = now
                }
                elapsed = (elapsed + dt).coerceAtMost(inspectDurationSec)
                revealScale = (elapsed / inspectDurationSec).coerceIn(0f, 1f)
                hb += dt
                if (hb >= 0.58f && revealScale < 0.96f) {
                    hb = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
            isSlowMotion = false
        } else {
            revealScale = 1f
        }

        soundEngine.setClimaxDucking(false)
        hitPlayerIdx = chosenPlayer
        inspectingBallIdx = -1
        soundEngine.playPenaltyReveal()
        delay(900)
        showResultModal = true
    }

    // 자동 추첨 모드: 설정된 5초·10초·15초에 맞춰 순서대로 볼을 뽑다가 당첨 볼을 슬로우모션으로 공개
    LaunchedEffect(autoDrawTrigger) {
        if (autoDrawTrigger == 0) return@LaunchedEffect
        isAutoDrawing = true
        soundEngine.setClimaxDucking(false)

        val totalSec = settings.normalizedDurationSeconds.toFloat()
        val unpickedSafe = (0 until totalBalls)
            .filter { it != goldenBallIndex && pickedByPlayer[it] == -1 }
            .shuffled()
        val prePickCount = unpickedSafe.size.coerceAtMost(Random.nextInt(3, 7))
        val sequence = unpickedSafe.take(prePickCount) + goldenBallIndex

        val pickingPhaseSec = totalSec * 0.62f
        val climaxPhaseSec = totalSec * 0.38f
        val stepDelayMs = if (prePickCount > 0) {
            ((pickingPhaseSec * 1000f) / prePickCount).toLong().coerceAtLeast(260L)
        } else 250L

        for (i in 0 until prePickCount) {
            val idx = sequence[i]
            val pIdx = turnCounter % count
            pickedByPlayer[idx] = pIdx
            turnCounter++
            soundEngine.playBallInspectPop()
            soundEngine.playStickSlide(turnCounter % 4)
            if (pickedByPlayer.count { it == -1 } <= 5) {
                soundEngine.playHeartbeat(intense = pickedByPlayer.count { it == -1 } <= 3)
            }
            delay(stepDelayMs)
        }

        // 마지막 당첨 볼 오픈 및 슬로우모션 클라이맥스
        val finalPlayer = turnCounter % count
        inspectingBallIdx = goldenBallIndex
        pickedByPlayer[goldenBallIndex] = finalPlayer
        soundEngine.playBallInspectPop()

        if (settings.slowMotionEnabled) {
            isSlowMotion = true
            soundEngine.setClimaxDucking(true)
            soundEngine.playSlowMoEnter()
            soundEngine.playHeartbeat(intense = true)
        }

        var elapsed = 0f
        var hb = 0f
        var prev = 0L
        while (elapsed < climaxPhaseSec) {
            var dt = 0f
            withFrameNanos { now ->
                if (prev != 0L) {
                    dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prev = now
            }
            elapsed = (elapsed + dt).coerceAtMost(climaxPhaseSec)
            revealScale = (elapsed / climaxPhaseSec).coerceIn(0f, 1f)
            if (settings.slowMotionEnabled) {
                hb += dt
                if (hb >= 0.58f && revealScale < 0.96f) {
                    hb = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        isSlowMotion = false
        isAutoDrawing = false
        hitPlayerIdx = finalPlayer
        inspectingBallIdx = -1
        soundEngine.playPenaltyReveal()
        delay(900)
        showResultModal = true
    }

    val resultRows = remember(hitPlayerIdx, players, roundSeed) {
        if (hitPlayerIdx < 0) emptyList()
        else {
            val orderedPlayers = players.indices.sortedByDescending { it == hitPlayerIdx }
            var safeRank = 1
            orderedPlayers.mapIndexed { displayIdx, pIdx ->
                val pl = players[pIdx]
                val isHit = pIdx == hitPlayerIdx
                val badge = if (isHit) {
                    "커피 당첨"
                } else {
                    val label = "${safeRank}번 통과"
                    safeRank++
                    label
                }
                GameResultRow(
                    badgeText = badge,
                    playerName = pl.name,
                    detailText = if (isHit) "바구니 속 황금 도토리 도장 볼을 뽑았습니다" else "일반 골프공만 골라 무사히 피했습니다",
                    isHighlighted = isHit,
                    avatarRes = pl.avatarRes,
                    badgeColor = distinctRankBadgeColor(displayIdx)
                )
            }
        }
    }

    val activePlayer = players[turnCounter % count]
    val remainingCount = pickedByPlayer.count { it == -1 }

    GameStageScaffold(
        gameType = GolfGameType.BALL_BASKET,
        settings = settings,
        isSlowMotion = isSlowMotion,
        slowMotionLabel = "뽑아 든 골프공 바닥의 도장을 확인 중 · 슬로우모션",
        showCelebration = hitPlayerIdx >= 0,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (hitPlayerIdx < 0) {
                    Button(
                        onClick = { startAutoDraw() },
                        enabled = !isSlowMotion && !isAutoDrawing && inspectingBallIdx == -1,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isAutoDrawing) "순서대로 공을 뽑는 중..." else "자동 추첨 (${settings.normalizedDurationSeconds}초)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    OutlinedButton(
                        onClick = { resetBasket() },
                        enabled = !isSlowMotion && !isAutoDrawing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("새로 섞기", color = InkPrimary)
                    }
                } else {
                    Button(
                        onClick = { resetBasket() },
                        enabled = !isSlowMotion,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "새 바구니로 다시 하기",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
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
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_wood_table),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.36f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperCream.copy(alpha = 0.60f))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // 상단 턴 및 남은 공 긴장도 안내 카드
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureCardWhite.copy(alpha = 0.94f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (hitPlayerIdx >= 0) {
                        val hitP = players[hitPlayerIdx]
                        Image(
                            painter = painterResource(id = hitP.avatarRes),
                            contentDescription = hitP.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(2.dp, TerracottaWood, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "${hitP.name}님 당첨 확정",
                                style = MaterialTheme.typography.titleMedium,
                                color = TerracottaWood
                            )
                            Text(
                                text = "황금 도토리 도장이 찍힌 당첨 볼이 나왔습니다",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary
                            )
                        }
                    } else {
                        Image(
                            painter = painterResource(id = activePlayer.avatarRes),
                            contentDescription = activePlayer.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .border(2.dp, activePlayer.badgeColor, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (isAutoDrawing) "자동으로 바구니 속 볼을 확인 중 (${activePlayer.name} 차례)"
                                else "${activePlayer.name}님, 공 하나를 직접 꺼내세요",
                                style = MaterialTheme.typography.titleMedium,
                                color = InkPrimary
                            )
                            Text(
                                text = "남은 공 ${remainingCount}개 중 당첨 볼 1개 (당첨 확률 ${if (remainingCount > 0) 100 / remainingCount else 100}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (remainingCount <= 3) TerracottaWood else InkSecondary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 버드나무 둥근 바구니 안 3x3 골프공 그리드
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    // 바구니 원형 테두리 일러스트 배경
                    Canvas(modifier = Modifier.size(300.dp).clipToBounds()) {
                        val r = size.minDimension * 0.48f
                        val c = Offset(size.width * 0.5f, size.height * 0.5f)
                        drawCircle(color = OakWood, radius = r, center = c)
                        drawCircle(color = LightBirch, radius = r * 0.90f, center = c)
                        drawCircle(color = Color(0xFFD5E4CE), radius = r * 0.82f, center = c)
                        drawCircle(
                            color = OakWood,
                            radius = r * 0.90f,
                            center = c,
                            style = Stroke(width = 3.dp.toPx())
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        for (row in 0 until 3) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                for (col in 0 until 3) {
                                    val idx = row * 3 + col
                                    val owner = pickedByPlayer[idx]
                                    val isGolden = idx == goldenBallIndex
                                    val isBeingRevealed = idx == inspectingBallIdx
                                    val isRevealedHit = hitPlayerIdx >= 0 && isGolden

                                    val scaleAnim by animateFloatAsState(
                                        targetValue = when {
                                            isBeingRevealed -> 1.18f
                                            owner != -1 && !isGolden -> 0.92f
                                            else -> 1f
                                        },
                                        animationSpec = tween(220),
                                        label = "ballScale"
                                    )

                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .scale(scaleAnim)
                                            .clip(CircleShape)
                                            .background(
                                                when {
                                                    isBeingRevealed || isRevealedHit -> SunlightSoft
                                                    owner != -1 -> MeadowGreenSoft
                                                    else -> PureCardWhite
                                                }
                                            )
                                            .border(
                                                width = if (isBeingRevealed || isRevealedHit) 2.5.dp else 1.5.dp,
                                                color = when {
                                                    isBeingRevealed || isRevealedHit -> TerracottaWood
                                                    owner != -1 -> CloverLeaf
                                                    else -> SubtleDivider
                                                },
                                                shape = CircleShape
                                            )
                                            .clickable(
                                                enabled = !isAutoDrawing && owner == -1 && inspectingBallIdx == -1 && hitPlayerIdx == -1
                                            ) {
                                                onPickBall(idx)
                                            }
                                    ) {
                                        if (owner == -1) {
                                            // 아직 뽑지 않은 뽀얀 골프공 딤플 표현
                                            Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
                                                val c = Offset(size.width * 0.5f, size.height * 0.5f)
                                                for (dx in listOf(-9f, 0f, 9f)) {
                                                    for (dy in listOf(-9f, 0f, 9f)) {
                                                        drawCircle(
                                                            color = SubtleDivider,
                                                            radius = 2.dp.toPx(),
                                                            center = Offset(c.x + dx.dp.toPx(), c.y + dy.dp.toPx())
                                                        )
                                                    }
                                                }
                                            }
                                            Text(
                                                text = "${idx + 1}",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = InkSecondary
                                            )
                                        } else if (isBeingRevealed || isRevealedHit) {
                                            // 황금 도토리 당첨 볼 문양
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Canvas(modifier = Modifier.size(22.dp).clipToBounds()) {
                                                    drawOval(
                                                        color = OakWood,
                                                        topLeft = Offset(size.width * 0.2f, size.height * 0.12f),
                                                        size = Size(size.width * 0.6f, size.height * 0.32f)
                                                    )
                                                    drawOval(
                                                        color = SunlightAmber,
                                                        topLeft = Offset(size.width * 0.24f, size.height * 0.34f),
                                                        size = Size(size.width * 0.52f, size.height * 0.54f)
                                                    )
                                                }
                                                Text(
                                                    text = if (isBeingRevealed && revealScale < 0.72f) "확인 중" else "당첨",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = TerracottaWood
                                                )
                                            }
                                        } else {
                                            // 통과한 일반 공
                                            Column(
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            ) {
                                                Text(
                                                    text = "통과",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = PineDeep
                                                )
                                                Text(
                                                    text = players[owner].name,
                                                    fontSize = 10.sp,
                                                    color = InkSecondary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = "숲속 골프공 바구니 결과",
            subtitle = "황금 도토리 도장이 찍힌 골프공의 주인이 가려졌습니다.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                resetBasket()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
