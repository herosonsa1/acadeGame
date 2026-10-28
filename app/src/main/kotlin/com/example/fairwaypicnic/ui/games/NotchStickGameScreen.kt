package com.example.fairwaypicnic.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PaperCream
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sin

private enum class StickStage {
    PICKING,
    REVEALING_SLOW_MO,
    DONE
}

/**
 * 1홀 티샷 순서 게임 2: 가죽 통에 꽂힌 나무 뽑기 막대를 뽑아 아래쪽 홈 개수(1~N개)로 순서를 정하는 게임
 */
@Composable
fun NotchStickGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)
    val textMeasurer = rememberTextMeasurer()

    var stage by remember { mutableStateOf(StickStage.PICKING) }
    var roundSeed by remember { mutableIntStateOf(0) }

    val stickNotches = remember(count, roundSeed) {
        (1..count).shuffled()
    }
    val assignedPlayerByStick = remember(count, roundSeed) {
        mutableStateListOf<Int>().apply { repeat(count) { add(-1) } }
    }
    var currentTurnPlayerIdx by remember(count, roundSeed) { mutableIntStateOf(0) }

    var pullProgress by remember(roundSeed) { mutableFloatStateOf(0f) }
    var tubeShake by remember(roundSeed) { mutableFloatStateOf(0f) }
    var showResultModal by remember(roundSeed) { mutableStateOf(false) }
    var revealTrigger by remember { mutableIntStateOf(0) }

    fun resetGame() {
        soundEngine.setClimaxDucking(false)
        soundEngine.playStickCaseShuffle()
        stage = StickStage.PICKING
        showResultModal = false
        roundSeed++
    }

    fun pickStick(stickIdx: Int) {
        if (stage != StickStage.PICKING) return
        if (assignedPlayerByStick[stickIdx] != -1) return
        if (currentTurnPlayerIdx >= count) return

        assignedPlayerByStick[stickIdx] = currentTurnPlayerIdx
        soundEngine.playStickSlide(currentTurnPlayerIdx)
        currentTurnPlayerIdx++

        if (currentTurnPlayerIdx == count - 1) {
            val lastFreeStick = assignedPlayerByStick.indexOfFirst { it == -1 }
            if (lastFreeStick >= 0) {
                assignedPlayerByStick[lastFreeStick] = currentTurnPlayerIdx
                currentTurnPlayerIdx++
            }
        }
        if (currentTurnPlayerIdx >= count) {
            revealTrigger++
        }
    }

    fun autoAssignAndOpen() {
        if (stage != StickStage.PICKING) return
        val remainingSticks = assignedPlayerByStick.indices.filter { assignedPlayerByStick[it] == -1 }.shuffled()
        var ptr = 0
        while (currentTurnPlayerIdx < count && ptr < remainingSticks.size) {
            assignedPlayerByStick[remainingSticks[ptr]] = currentTurnPlayerIdx
            currentTurnPlayerIdx++
            ptr++
        }
        soundEngine.playStickSlide(3)
        revealTrigger++
    }

    LaunchedEffect(revealTrigger) {
        if (revealTrigger == 0) return@LaunchedEffect
        val totalSec = settings.normalizedDurationSeconds.toFloat()
        stage = StickStage.REVEALING_SLOW_MO
        soundEngine.playStickCaseShuffle()

        var elapsed = 0f
        var hbTimer = 0f
        var shakeTickTimer = 0f
        var enteredClimax = false
        var lastNotchBellStep = 0
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

            if (u < 0.24f) {
                // 초반 24% 시간: 가죽 통 흔들림 및 나무 막대 달그락거리는 긴장 연출
                tubeShake = sin(elapsed * 36f) * 6.5f
                pullProgress = (u / 0.24f) * 0.14f
                shakeTickTimer += dt
                if (shakeTickTimer >= 0.42f) {
                    shakeTickTimer = 0f
                    soundEngine.playWoodTick(highPitch = true)
                }
            } else {
                // 후반 76% 시간: 슬로우모션으로 막대가 천천히 솟아오르며 눈금이 하나씩 공개됨
                tubeShake = 0f
                if (!enteredClimax) {
                    enteredClimax = true
                    if (settings.slowMotionEnabled) {
                        soundEngine.setClimaxDucking(true)
                        soundEngine.playSlowMoEnter()
                    }
                    soundEngine.playHeartbeat(intense = true)
                }
                val subU = ((u - 0.24f) / 0.76f).coerceIn(0f, 1f)
                pullProgress = 0.14f + 0.86f * (1f - (1f - subU).pow(1.85f))

                val notchStep = (pullProgress * count).toInt()
                if (notchStep > lastNotchBellStep && notchStep <= count) {
                    lastNotchBellStep = notchStep
                    soundEngine.playNotchRevealTick(notchStep - 1)
                }

                hbTimer += dt
                if (hbTimer >= 0.60f && u < 0.97f) {
                    hbTimer = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        pullProgress = 1f
        stage = StickStage.DONE
        soundEngine.playGhibliFanfare()
        delay(950)
        showResultModal = true
    }

    val resultRows = remember(stage, roundSeed, players) {
        if (stage != StickStage.DONE) emptyList()
        else {
            (1..count).mapNotNull { notchCount ->
                val stickIdx = stickNotches.indexOf(notchCount)
                val pIdx = assignedPlayerByStick.getOrNull(stickIdx) ?: -1
                if (pIdx in players.indices) {
                    val pl = players[pIdx]
                    GameResultRow(
                        badgeText = if (notchCount == 1) "1번 오너" else "${notchCount}번 타순",
                        playerName = pl.name,
                        detailText = "새겨진 나무 홈 ${notchCount}개 확인",
                        isHighlighted = notchCount == 1,
                        avatarRes = pl.avatarRes,
                        badgeColor = distinctRankBadgeColor(notchCount - 1)
                    )
                } else null
            }
        }
    }

    GameStageScaffold(
        gameType = GolfGameType.NOTCH_STICK,
        settings = settings,
        isSlowMotion = stage == StickStage.REVEALING_SLOW_MO && settings.slowMotionEnabled && pullProgress >= 0.14f,
        slowMotionLabel = "가죽 통 속 나무 홈이 드러납니다 · 슬로우모션",
        showCelebration = stage == StickStage.DONE,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (stage == StickStage.PICKING) {
                    Button(
                        onClick = { autoAssignAndOpen() },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "한 번에 모두 뽑아 결과 보기 (${settings.normalizedDurationSeconds}초)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    OutlinedButton(
                        onClick = { resetGame() },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("다시 섞기", color = InkPrimary)
                    }
                } else {
                    Button(
                        onClick = { resetGame() },
                        enabled = stage == StickStage.DONE,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "새로 섞어서 다시 뽑기",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    if (stage == StickStage.DONE) {
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
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.bg_wood_table),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.35f,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(PaperCream.copy(alpha = 0.62f))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 12.dp, start = 12.dp, end = 12.dp)
            ) {
                val activePlayer = players.getOrNull(currentTurnPlayerIdx.coerceIn(0, count - 1))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureCardWhite.copy(alpha = 0.92f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (stage == StickStage.PICKING && activePlayer != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = activePlayer.avatarRes),
                                contentDescription = activePlayer.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, activePlayer.badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${activePlayer.name} 차례입니다",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "원하는 나무 막대 하나를 터치해 찜해주세요",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary
                                )
                            }
                        }
                    } else if (stage == StickStage.REVEALING_SLOW_MO) {
                        Text(
                            text = "통 아래 숨겨진 홈 개수를 천천히 확인하고 있습니다...",
                            style = MaterialTheme.typography.titleMedium,
                            color = OakWood
                        )
                    } else {
                        Text(
                            text = "모든 막대의 홈 개수가 공개되었습니다 (홈 1개 = 1번 오너)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PineDeep
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .pointerInput(count, roundSeed, stage) {
                            detectTapGestures { tapOffset ->
                                if (stage != StickStage.PICKING) return@detectTapGestures
                                val slotWidth = size.width / count.toFloat()
                                val tappedIdx = (tapOffset.x / slotWidth).toInt().coerceIn(0, count - 1)
                                pickStick(tappedIdx)
                            }
                        }
                ) {
                    val w = size.width
                    val h = size.height
                    val slotW = w / count.toFloat()
                    val stickW = (slotW * 0.30f).coerceIn(16.dp.toPx(), 28.dp.toPx())
                    val tubeTopY = h * 0.56f
                    val tubeBottomY = h * 0.93f
                    val stickHeight = h * 0.48f

                    for (i in 0 until count) {
                        val cx = slotW * (i + 0.5f) + tubeShake * 0.4f
                        val ownerIdx = assignedPlayerByStick[i]
                        val isPicked = ownerIdx != -1

                        val baseTopY = if (isPicked && stage == StickStage.PICKING) {
                            tubeTopY - stickHeight * 0.46f
                        } else if (stage != StickStage.PICKING) {
                            val lifted = stickHeight * (0.46f + 0.62f * pullProgress)
                            tubeTopY - lifted
                        } else {
                            tubeTopY - stickHeight * 0.34f
                        }

                        drawRoundRect(
                            color = LightBirch,
                            topLeft = Offset(cx - stickW * 0.5f, baseTopY),
                            size = Size(stickW, stickHeight),
                            cornerRadius = CornerRadius(stickW * 0.35f)
                        )
                        drawRoundRect(
                            color = OakWood,
                            topLeft = Offset(cx - stickW * 0.5f, baseTopY),
                            size = Size(stickW, stickHeight),
                            cornerRadius = CornerRadius(stickW * 0.35f),
                            style = Stroke(width = 1.8.dp.toPx())
                        )

                        val capColor = if (isPicked && ownerIdx in players.indices) players[ownerIdx].badgeColor else MeadowGreen
                        drawRoundRect(
                            color = capColor,
                            topLeft = Offset(cx - stickW * 0.5f, baseTopY),
                            size = Size(stickW, 16.dp.toPx()),
                            cornerRadius = CornerRadius(stickW * 0.35f)
                        )

                        val notches = stickNotches[i]
                        val notchStartBottom = baseTopY + stickHeight - 16.dp.toPx()
                        for (n in 0 until notches) {
                            val ny = notchStartBottom - n * 11.dp.toPx()
                            if (ny < tubeTopY + 4.dp.toPx()) {
                                drawLine(
                                    color = TerracottaWood,
                                    start = Offset(cx - stickW * 0.36f, ny),
                                    end = Offset(cx + stickW * 0.36f, ny),
                                    strokeWidth = 3.4.dp.toPx(),
                                    cap = StrokeCap.Round
                                )
                            }
                        }

                        if (isPicked && ownerIdx in players.indices) {
                            val pName = players[ownerIdx].name
                            val nameLayout = textMeasurer.measure(
                                text = pName,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                            )
                            val tagW = (nameLayout.size.width + 16.dp.toPx()).coerceAtMost(slotW - 6.dp.toPx())
                            val tagH = nameLayout.size.height + 8.dp.toPx()
                            val tagTop = (baseTopY - tagH - 8.dp.toPx()).coerceAtLeast(6.dp.toPx())

                            drawRoundRect(
                                color = PureCardWhite,
                                topLeft = Offset(cx - tagW * 0.5f, tagTop),
                                size = Size(tagW, tagH),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                            drawRoundRect(
                                color = players[ownerIdx].badgeColor,
                                topLeft = Offset(cx - tagW * 0.5f, tagTop),
                                size = Size(tagW, tagH),
                                cornerRadius = CornerRadius(8.dp.toPx()),
                                style = Stroke(width = 1.5.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = nameLayout,
                                topLeft = Offset(
                                    cx - nameLayout.size.width * 0.5f,
                                    tagTop + 4.dp.toPx()
                                )
                            )
                        } else {
                            val numLayout = textMeasurer.measure(
                                text = "${i + 1}번 막대",
                                style = TextStyle(
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = InkSecondary
                                )
                            )
                            drawText(
                                textLayoutResult = numLayout,
                                topLeft = Offset(
                                    cx - numLayout.size.width * 0.5f,
                                    (baseTopY - 22.dp.toPx()).coerceAtLeast(6.dp.toPx())
                                )
                            )
                        }

                        // 결과 확정 시 모든 순위 배지가 서로 다른 색상을 표시
                        if (stage == StickStage.DONE) {
                            val rankStr = if (notches == 1) "1번 오너" else "${notches}번"
                            val rankLayout = textMeasurer.measure(
                                text = rankStr,
                                style = TextStyle(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureCardWhite
                                )
                            )
                            val bw = rankLayout.size.width + 16.dp.toPx()
                            val bh = rankLayout.size.height + 8.dp.toPx()
                            val by = tubeTopY - bh - 8.dp.toPx()
                            drawRoundRect(
                                color = distinctRankBadgeColor(notches - 1),
                                topLeft = Offset(cx - bw * 0.5f, by),
                                size = Size(bw, bh),
                                cornerRadius = CornerRadius(8.dp.toPx())
                            )
                            drawText(
                                textLayoutResult = rankLayout,
                                topLeft = Offset(cx - rankLayout.size.width * 0.5f, by + 4.dp.toPx())
                            )
                        }
                    }

                    val tubeLeft = w * 0.08f + tubeShake
                    val tubeWidth = w * 0.84f
                    val tubeHeight = tubeBottomY - tubeTopY

                    drawRoundRect(
                        color = OakWood,
                        topLeft = Offset(tubeLeft, tubeTopY),
                        size = Size(tubeWidth, tubeHeight),
                        cornerRadius = CornerRadius(18.dp.toPx())
                    )
                    drawRoundRect(
                        color = SunlightAmber,
                        topLeft = Offset(tubeLeft - 4.dp.toPx(), tubeTopY),
                        size = Size(tubeWidth + 8.dp.toPx(), 16.dp.toPx()),
                        cornerRadius = CornerRadius(8.dp.toPx())
                    )
                    drawLine(
                        color = LightBirch.copy(alpha = 0.65f),
                        start = Offset(tubeLeft + 18.dp.toPx(), tubeTopY + 34.dp.toPx()),
                        end = Offset(tubeLeft + tubeWidth - 18.dp.toPx(), tubeTopY + 34.dp.toPx()),
                        strokeWidth = 1.6.dp.toPx()
                    )
                    val plaqueLayout = textMeasurer.measure(
                        text = "HONOR STICK CASE",
                        style = TextStyle(
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = WarmIvory.copy(alpha = 0.88f),
                            letterSpacing = 1.4.sp
                        )
                    )
                    drawText(
                        textLayoutResult = plaqueLayout,
                        topLeft = Offset(
                            w * 0.5f - plaqueLayout.size.width * 0.5f + tubeShake,
                            tubeTopY + tubeHeight * 0.50f
                        )
                    )
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = "나무 홈 뽑기 타순 결과",
            subtitle = "통 속에서 드러난 눈금 개수(1~${count}개)에 따라 티샷 순서가 정해졌습니다.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                resetGame()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
