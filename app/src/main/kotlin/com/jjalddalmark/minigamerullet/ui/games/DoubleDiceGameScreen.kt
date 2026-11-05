package com.jjalddalmark.minigamerullet.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjalddalmark.minigamerullet.R
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.DiceGameLogic
import com.jjalddalmark.minigamerullet.model.DicePlayerRoll
import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.RankedDiceResult
import com.jjalddalmark.minigamerullet.share.CommonGameSharePayload
import com.jjalddalmark.minigamerullet.share.DiceRollSnapshot
import com.jjalddalmark.minigamerullet.share.GameExtraSceneData
import com.jjalddalmark.minigamerullet.ui.components.CommonGameShareDialog
import com.jjalddalmark.minigamerullet.ui.components.GameResultDialog
import com.jjalddalmark.minigamerullet.ui.components.GameStageScaffold
import com.jjalddalmark.minigamerullet.ui.components.StageCinemaBackdrop
import com.jjalddalmark.minigamerullet.ui.theme.InkMuted
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.LightBirch
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.OakWood
import com.jjalddalmark.minigamerullet.ui.theme.PaperCream
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SoftSand
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import com.jjalddalmark.minigamerullet.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private enum class DiceStagePhase {
    IDLE,
    ROLLING_SINGLE,
    ROLLING_AUTO_ALL,
    SLOW_MO_FINAL,
    FINISHED
}

/**
 * 주사위 승부 미니게임 8: 행운의 더블 주사위
 * 2개의 원목 상아 주사위를 굴려 눈의 합계가 가장 높은 사람이 승리하는 대결
 */
@Composable
fun DoubleDiceGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    showIntroCutscene: Boolean = false,
    onIntroCutsceneFinished: () -> Unit = {},
    onToggleBgmMute: () -> Unit = {},
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val effectiveSettings = remember(settings) { settings.ensureMinPlayersForGame(GolfGameType.DOUBLE_DICE) }
    val players = effectiveSettings.players
    val count = players.size.coerceAtLeast(2)

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    var phase by remember(count) { mutableStateOf(DiceStagePhase.IDLE) }
    val playerRolls = remember(count) {
        mutableStateListOf<DicePlayerRoll?>().apply {
            repeat(count) { add(null) }
        }
    }
    var rankedResults by remember(count) { mutableStateOf<List<RankedDiceResult>>(emptyList()) }
    var activePlayerIdx by remember(count) { mutableIntStateOf(0) }

    // 실시간 애니메이션용 주사위 2개 눈 및 물리 좌표
    var shownDie1 by remember { mutableIntStateOf(5) }
    var shownDie2 by remember { mutableIntStateOf(6) }
    var die1Angle by remember { mutableFloatStateOf(-8f) }
    var die2Angle by remember { mutableFloatStateOf(9f) }
    var bounceOffsetRatio by remember { mutableFloatStateOf(0f) }
    var diceScale by remember { mutableFloatStateOf(1f) }

    var actionNonce by remember { mutableIntStateOf(0) }
    var requestedAutoAll by remember { mutableStateOf(false) }
    var impactBurstKey by remember { mutableIntStateOf(0) }
    var showResultModal by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }

    fun resetRound() {
        soundEngine.setClimaxDucking(false)
        for (i in 0 until count) {
            playerRolls[i] = null
        }
        rankedResults = emptyList()
        activePlayerIdx = 0
        shownDie1 = 5
        shownDie2 = 6
        die1Angle = -8f
        die2Angle = 9f
        bounceOffsetRatio = 0f
        diceScale = 1f
        showResultModal = false
        phase = DiceStagePhase.IDLE
    }

    fun triggerNextSingleRoll() {
        if (phase == DiceStagePhase.ROLLING_SINGLE ||
            phase == DiceStagePhase.ROLLING_AUTO_ALL ||
            phase == DiceStagePhase.SLOW_MO_FINAL
        ) return
        val nextIdx = playerRolls.indexOfFirst { it == null }
        if (nextIdx < 0) return
        onIntroCutsceneFinished()
        activePlayerIdx = nextIdx
        requestedAutoAll = false
        actionNonce++
    }

    fun triggerAutoRollRemaining() {
        if (phase == DiceStagePhase.ROLLING_SINGLE ||
            phase == DiceStagePhase.ROLLING_AUTO_ALL ||
            phase == DiceStagePhase.SLOW_MO_FINAL
        ) return
        if (playerRolls.all { it != null }) {
            resetRound()
        }
        val firstPending = playerRolls.indexOfFirst { it == null }.coerceAtLeast(0)
        onIntroCutsceneFinished()
        activePlayerIdx = firstPending
        requestedAutoAll = true
        actionNonce++
    }

    LaunchedEffect(actionNonce) {
        if (actionNonce == 0) return@LaunchedEffect

        val indicesToRoll = if (requestedAutoAll) {
            (0 until count).filter { playerRolls[it] == null }
        } else {
            listOfNotNull(playerRolls.indexOfFirst { it == null }.takeIf { it >= 0 })
        }
        if (indicesToRoll.isEmpty()) return@LaunchedEffect

        val totalTargetSec = effectiveSettings.normalizedDurationSeconds.toFloat()
        val perPlayerSec = if (requestedAutoAll) {
            (totalTargetSec / indicesToRoll.size.coerceAtLeast(1)).coerceIn(1.35f, 3.8f)
        } else {
            1.75f
        }

        for (pIdx in indicesToRoll) {
            activePlayerIdx = pIdx
            val isFinalOverallRoll = playerRolls.count { it == null } == 1
            val finalRoll = DiceGameLogic.rollTwoDice(pIdx)

            phase = if (requestedAutoAll) DiceStagePhase.ROLLING_AUTO_ALL else DiceStagePhase.ROLLING_SINGLE
            soundEngine.playDiceCupShake()
            impactBurstKey++

            var elapsed = 0f
            var prevNanos = 0L
            var lastFaceStep = -1
            var lastBounceTick = -1
            var slowMoTriggered = false

            while (elapsed < perPlayerSec) {
                withFrameNanos { now ->
                    if (prevNanos != 0L) {
                        elapsed += ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.045f)
                    }
                    prevNanos = now
                }

                val u = (elapsed / perPlayerSec).coerceIn(0f, 1f)
                if (isFinalOverallRoll && effectiveSettings.slowMotionEnabled && u >= 0.58f) {
                    if (!slowMoTriggered) {
                        slowMoTriggered = true
                        phase = DiceStagePhase.SLOW_MO_FINAL
                        soundEngine.setClimaxDucking(true)
                        soundEngine.playSlowMoEnter()
                        soundEngine.playHeartbeat(intense = true)
                    }
                }

                // 감쇠 포물선 바운스 및 회전 각도 계산
                val decay = (1f - u) * (1f - u)
                bounceOffsetRatio = -abs(sin(u * PI * 4.5)).toFloat() * 0.24f * decay
                diceScale = 1f + abs(sin(u * PI * 4.5)).toFloat() * 0.16f * decay
                die1Angle = -8f + (1f - u) * 540f * sin(u * 3.1f)
                die2Angle = 9f - (1f - u) * 600f * sin(u * 2.8f + 0.5f)

                val faceStep = (u * 14f).toInt()
                if (faceStep != lastFaceStep && u < 0.88f) {
                    lastFaceStep = faceStep
                    shownDie1 = Random.nextInt(1, 7)
                    shownDie2 = Random.nextInt(1, 7)
                } else if (u >= 0.88f) {
                    shownDie1 = finalRoll.die1
                    shownDie2 = finalRoll.die2
                }

                val bounceIndex = (u * 4.5f).toInt()
                if (bounceIndex != lastBounceTick && bounceIndex in 1..4) {
                    lastBounceTick = bounceIndex
                    soundEngine.playDiceBounce(highPitch = bounceIndex % 2 == 0)
                }
            }

            shownDie1 = finalRoll.die1
            shownDie2 = finalRoll.die2
            die1Angle = -6f
            die2Angle = 7f
            bounceOffsetRatio = 0f
            diceScale = 1f
            playerRolls[pIdx] = finalRoll
            impactBurstKey++
            soundEngine.playDiceBounce(highPitch = true)

            if (!isFinalOverallRoll && requestedAutoAll) {
                delay(280)
            }
        }

        // 모든 참가자의 주사위 투구가 끝난 경우 최종 순위 확정
        if (playerRolls.all { it != null }) {
            soundEngine.setClimaxDucking(false)
            val completed = playerRolls.filterNotNull()
            val ranked = DiceGameLogic.rankRolls(completed)
            rankedResults = ranked
            // 동점 결승 보너스가 반영된 경우 슬롯 상태도 최신화
            ranked.forEach { r ->
                playerRolls[r.roll.playerIndex] = r.roll
            }
            phase = DiceStagePhase.FINISHED
            soundEngine.playGhibliFanfare()
            delay(850)
            showResultModal = true
        } else {
            val nextWaiting = playerRolls.indexOfFirst { it == null }
            if (nextWaiting >= 0) {
                activePlayerIdx = nextWaiting
            }
            phase = DiceStagePhase.IDLE
        }
    }

    val isRollingNow = phase == DiceStagePhase.ROLLING_SINGLE ||
        phase == DiceStagePhase.ROLLING_AUTO_ALL ||
        phase == DiceStagePhase.SLOW_MO_FINAL
    val isSlowMo = phase == DiceStagePhase.SLOW_MO_FINAL

    // 현재까지 굴린 사람 중 최고 합계
    val currentMaxScore = playerRolls.filterNotNull().maxOfOrNull { it.compositeSortScore } ?: -1

    GameStageScaffold(
        gameType = GolfGameType.DOUBLE_DICE,
        settings = effectiveSettings,
        isSlowMotion = isSlowMo,
        slowMotionLabel = "마지막 주사위가 멈추는 순간 · 슬로우모션",
        showCelebration = phase == DiceStagePhase.FINISHED,
        isActionActive = isRollingNow,
        impactBurstKey = impactBurstKey,
        showIntroCutscene = showIntroCutscene,
        onIntroCutsceneFinished = onIntroCutsceneFinished,
        onToggleBgmMute = onToggleBgmMute,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        stageContent = {
            Box(modifier = Modifier.fillMaxSize()) {
                StageCinemaBackdrop(
                    gameType = GolfGameType.DOUBLE_DICE,
                    videoEffectsEnabled = effectiveSettings.videoEffectsEnabled,
                    active = isRollingNow,
                    isSlowMotion = isSlowMo,
                    activeVideoAlpha = 0.36f,
                    idleBackdropRes = R.drawable.bg_wood_table,
                    idleBackdropAlpha = 0.28f
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // 1. 참가자별 주사위 합계 현황판 (2열 배치)
                    val rows = players.chunked(2)
                    rows.forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                        ) {
                            pair.forEach { player ->
                                val roll = playerRolls.getOrNull(player.index)
                                val isTurn = activePlayerIdx == player.index && phase != DiceStagePhase.FINISHED
                                val isLeader = roll != null && roll.compositeSortScore == currentMaxScore
                                val cardBg = when {
                                    isLeader && phase == DiceStagePhase.FINISHED -> SunlightSoft
                                    isTurn -> MeadowGreenSoft.copy(alpha = 0.94f)
                                    else -> PureCardWhite.copy(alpha = 0.93f)
                                }
                                val borderColor = when {
                                    isLeader -> TerracottaWood
                                    isTurn -> MeadowGreen
                                    else -> SubtleDivider
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(cardBg)
                                        .border(if (isTurn || isLeader) 1.6.dp else 1.dp, borderColor, RoundedCornerShape(12.dp))
                                        .padding(horizontal = 8.dp, vertical = 7.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = player.avatarRes),
                                        contentDescription = player.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, player.badgeColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = player.name,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when {
                                                roll != null -> {
                                                    val dbl = if (roll.isDouble) " · 더블" else ""
                                                    "${roll.die1} + ${roll.die2}${dbl}"
                                                }
                                                isTurn && isRollingNow -> "굴리는 중..."
                                                isTurn -> "이번 차례"
                                                else -> "대기 중"
                                            },
                                            fontSize = 10.sp,
                                            color = if (roll?.isDouble == true) TerracottaWood else InkSecondary,
                                            maxLines = 1
                                        )
                                    }
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                when {
                                                    isLeader -> TerracottaWood
                                                    roll != null -> MeadowGreen
                                                    else -> WarmIvory
                                                }
                                            )
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = if (roll != null) "${roll.sum}점" else "-",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (roll != null) PureCardWhite else InkMuted
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // 2. 중앙 원목 트레이 및 2개 주사위 물리 투구 캔버스
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Canvas(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                        ) {
                            val cx = size.width * 0.5f
                            val cy = size.height * 0.48f
                            val trayR = minOf(size.width, size.height) * 0.42f

                            // 원목 팔각·원형 주사위 보드 매트
                            drawCircle(
                                color = OakWood.copy(alpha = 0.85f),
                                radius = trayR * 1.07f,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = SoftSand.copy(alpha = 0.95f),
                                radius = trayR,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = MeadowGreen.copy(alpha = 0.16f),
                                radius = trayR * 0.90f,
                                center = Offset(cx, cy)
                            )
                            drawCircle(
                                color = OakWood.copy(alpha = 0.45f),
                                radius = trayR * 0.90f,
                                center = Offset(cx, cy),
                                style = Stroke(width = 1.5.dp.toPx())
                            )

                            val dieBase = (trayR * 0.54f).coerceIn(58.dp.toPx(), 94.dp.toPx()) * diceScale
                            val yJump = bounceOffsetRatio * trayR
                            val leftCenter = Offset(cx - dieBase * 0.68f, cy + yJump)
                            val rightCenter = Offset(cx + dieBase * 0.68f, cy + yJump * 0.92f)

                            // 바닥 그림자
                            val shadowScale = (1f + bounceOffsetRatio * 0.8f).coerceIn(0.6f, 1f)
                            drawOval(
                                color = InkPrimary.copy(alpha = 0.16f * shadowScale),
                                topLeft = Offset(leftCenter.x - dieBase * 0.44f, cy + dieBase * 0.46f),
                                size = Size(dieBase * 0.88f, dieBase * 0.22f)
                            )
                            drawOval(
                                color = InkPrimary.copy(alpha = 0.16f * shadowScale),
                                topLeft = Offset(rightCenter.x - dieBase * 0.44f, cy + dieBase * 0.46f),
                                size = Size(dieBase * 0.88f, dieBase * 0.22f)
                            )

                            drawWoodenDie(
                                center = leftCenter,
                                sidePx = dieBase,
                                angleDeg = die1Angle,
                                faceValue = shownDie1,
                                highlightBorder = shownDie1 == shownDie2 && !isRollingNow
                            )
                            drawWoodenDie(
                                center = rightCenter,
                                sidePx = dieBase,
                                angleDeg = die2Angle,
                                faceValue = shownDie2,
                                highlightBorder = shownDie1 == shownDie2 && !isRollingNow
                            )
                        }

                        // 하단 상태 안내 캡슐
                        val currentTurnPlayer = players.getOrElse(activePlayerIdx) { players.first() }
                        val latestRoll = playerRolls.getOrNull(activePlayerIdx)
                        val bannerMessage = when {
                            phase == DiceStagePhase.FINISHED && rankedResults.isNotEmpty() -> {
                                val win = rankedResults.first()
                                val winName = players.getOrElse(win.roll.playerIndex) { players.first() }.name
                                "우승: ${winName} (${win.roll.die1} + ${win.roll.die2} = ${win.roll.sum}점)"
                            }
                            isRollingNow -> "${currentTurnPlayer.name}님의 주사위 2개가 구르는 중! (${shownDie1} + ${shownDie2})"
                            latestRoll != null -> {
                                val dbl = if (latestRoll.isDouble) " · 더블 보너스!" else ""
                                "${currentTurnPlayer.name}: ${latestRoll.die1} + ${latestRoll.die2} = ${latestRoll.sum}점${dbl}"
                            }
                            else -> "${currentTurnPlayer.name}님 차례 · 주사위 2개를 던져 더 높은 합계에 도전하세요"
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 4.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PaperCream.copy(alpha = 0.95f))
                                .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = bannerMessage,
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        bottomControls = {
            if (phase == DiceStagePhase.FINISHED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            resetRound()
                            triggerAutoRollRemaining()
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "다시 대결",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    OutlinedButton(
                        onClick = { showResultModal = true },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, OakWood),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "순위표",
                            style = MaterialTheme.typography.titleMedium,
                            color = OakWood
                        )
                    }
                    Button(
                        onClick = { showShareModal = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PineDeep),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("공유", color = PureCardWhite)
                    }
                }
            } else {
                val waitingPlayer = players.getOrElse(
                    playerRolls.indexOfFirst { it == null }.coerceAtLeast(0)
                ) { players.first() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { triggerNextSingleRoll() },
                        enabled = !isRollingNow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, TerracottaWood),
                        modifier = Modifier
                            .weight(0.46f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = "${waitingPlayer.name} 굴리기",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isRollingNow) TerracottaWood else InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { triggerAutoRollRemaining() },
                        enabled = !isRollingNow,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TerracottaWood),
                        modifier = Modifier
                            .weight(0.54f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (playerRolls.all { it == null }) {
                                "전원 연속 던지기 (${effectiveSettings.normalizedDurationSeconds}초)"
                            } else {
                                "남은 인원 연속 던지기"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureCardWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    )

    val sharePayload = remember(rankedResults, players, effectiveSettings.normalizedDurationSeconds) {
        if (rankedResults.isEmpty()) null
        else {
            val winner = rankedResults.first()
            val winnerPlayer = players.getOrElse(winner.roll.playerIndex) { players.first() }
            val resultRows = rankedResults.mapIndexed { idx, item ->
                val p = players.getOrElse(item.roll.playerIndex) { players.first() }
                GameResultRow(
                    badgeText = item.badgeLabel,
                    playerName = p.name,
                    detailText = item.summaryDetail,
                    isHighlighted = idx == 0,
                    avatarRes = p.avatarRes,
                    badgeColor = distinctRankBadgeColor(idx)
                )
            }
            val rollsByOrder = players.mapIndexedNotNull { pIdx, p ->
                val found = rankedResults.find { it.roll.playerIndex == pIdx } ?: return@mapIndexedNotNull null
                DiceRollSnapshot(
                    playerName = p.name,
                    badgeColorArgb = p.badgeColor.toArgb(),
                    die1 = found.roll.die1,
                    die2 = found.roll.die2,
                    sum = found.roll.sum,
                    isDouble = found.roll.isDouble,
                    isLeader = found.rankOneBased == 1
                )
            }
            CommonGameSharePayload(
                gameType = GolfGameType.DOUBLE_DICE,
                resultTitle = "더블 주사위 승부 결과",
                resultSubtitle = "합계 ${winner.roll.sum}점을 기록한 ${winnerPlayer.name}님이 1위로 승리했습니다.",
                rows = resultRows,
                durationSec = effectiveSettings.normalizedDurationSeconds,
                extraSceneData = GameExtraSceneData.DoubleDice(
                    rollsInPlayerOrder = rollsByOrder,
                    winnerName = winnerPlayer.name,
                    winnerDie1 = winner.roll.die1,
                    winnerDie2 = winner.roll.die2
                )
            )
        }
    }

    if (showResultModal && rankedResults.isNotEmpty() && sharePayload != null) {
        GameResultDialog(
            title = sharePayload.resultTitle,
            subtitle = sharePayload.resultSubtitle,
            rows = sharePayload.rows,
            videoEffectsEnabled = effectiveSettings.videoEffectsEnabled,
            sharePayload = sharePayload,
            soundEnabled = effectiveSettings.soundEnabled,
            soundEngine = soundEngine,
            onReplay = {
                resetRound()
                triggerAutoRollRemaining()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }

    if (showShareModal && sharePayload != null) {
        CommonGameShareDialog(
            payload = sharePayload,
            soundEnabled = effectiveSettings.soundEnabled,
            soundEngine = soundEngine,
            onReplay = {
                resetRound()
                triggerAutoRollRemaining()
            },
            onDismiss = { showShareModal = false }
        )
    }
}

private fun DrawScope.drawWoodenDie(
    center: Offset,
    sidePx: Float,
    angleDeg: Float,
    faceValue: Int,
    highlightBorder: Boolean
) {
    rotate(degrees = angleDeg, pivot = center) {
        val half = sidePx * 0.5f
        val topLeft = Offset(center.x - half, center.y - half)
        val corner = CornerRadius(sidePx * 0.18f, sidePx * 0.18f)

        // 외곽 원목 베벨
        drawRoundRect(
            color = LightBirch,
            topLeft = topLeft,
            size = Size(sidePx, sidePx),
            cornerRadius = corner
        )
        // 상아빛 앞면
        val inset = sidePx * 0.06f
        drawRoundRect(
            color = PureCardWhite,
            topLeft = Offset(topLeft.x + inset, topLeft.y + inset),
            size = Size(sidePx - inset * 2f, sidePx - inset * 2f),
            cornerRadius = CornerRadius(sidePx * 0.14f, sidePx * 0.14f)
        )
        drawRoundRect(
            color = if (highlightBorder) SunlightAmber else OakWood,
            topLeft = topLeft,
            size = Size(sidePx, sidePx),
            cornerRadius = corner,
            style = Stroke(width = if (highlightBorder) 3.dp.toPx() else 2.dp.toPx())
        )

        val offset = sidePx * 0.24f
        val pipPositions: List<Offset> = when (faceValue.coerceIn(1, 6)) {
            1 -> listOf(center)
            2 -> listOf(
                Offset(center.x - offset, center.y - offset),
                Offset(center.x + offset, center.y + offset)
            )
            3 -> listOf(
                Offset(center.x - offset, center.y - offset),
                center,
                Offset(center.x + offset, center.y + offset)
            )
            4 -> listOf(
                Offset(center.x - offset, center.y - offset),
                Offset(center.x + offset, center.y - offset),
                Offset(center.x - offset, center.y + offset),
                Offset(center.x + offset, center.y + offset)
            )
            5 -> listOf(
                Offset(center.x - offset, center.y - offset),
                Offset(center.x + offset, center.y - offset),
                center,
                Offset(center.x - offset, center.y + offset),
                Offset(center.x + offset, center.y + offset)
            )
            else -> listOf(
                Offset(center.x - offset, center.y - offset),
                Offset(center.x + offset, center.y - offset),
                Offset(center.x - offset, center.y),
                Offset(center.x + offset, center.y),
                Offset(center.x - offset, center.y + offset),
                Offset(center.x + offset, center.y + offset)
            )
        }

        val pipColor = if (faceValue == 1 || faceValue == 4) TerracottaWood else PineDeep
        val pipRadius = if (faceValue == 1) sidePx * 0.115f else sidePx * 0.072f
        pipPositions.forEach { pt ->
            drawCircle(
                color = pipColor,
                radius = pipRadius,
                center = pt
            )
        }
    }
}
