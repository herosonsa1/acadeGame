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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
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
import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.RankedYutResult
import com.jjalddalmark.minigamerullet.model.YutGameLogic
import com.jjalddalmark.minigamerullet.model.YutPlayerState
import com.jjalddalmark.minigamerullet.share.CommonGameSharePayload
import com.jjalddalmark.minigamerullet.share.GameExtraSceneData
import com.jjalddalmark.minigamerullet.share.YutStateSnapshot
import com.jjalddalmark.minigamerullet.ui.components.CommonGameShareDialog
import com.jjalddalmark.minigamerullet.ui.components.GameResultDialog
import com.jjalddalmark.minigamerullet.ui.components.GameStageScaffold
import com.jjalddalmark.minigamerullet.ui.components.StageCinemaBackdrop
import com.jjalddalmark.minigamerullet.ui.theme.CloverLeaf
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
import kotlin.math.sin
import kotlin.random.Random

private enum class YutStagePhase {
    IDLE,
    THROWING,
    SLOW_MO_FINAL,
    FINISHED
}

/**
 * 미니 윷놀이 대결 게임 9: 잔디밭 민속 윷놀이
 * 도·개·걸·윷·모·빽도와 '윷·모 한 번 더', '앞선 말 잡기(+2칸)'로 승자와 패자를 가르는 미니게임
 */
@Composable
fun YutNoriGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    showIntroCutscene: Boolean = false,
    onIntroCutsceneFinished: () -> Unit = {},
    onToggleBgmMute: () -> Unit = {},
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val effectiveSettings = remember(settings) { settings.ensureMinPlayersForGame(GolfGameType.YUT_THROW) }
    val players = effectiveSettings.players
    val count = players.size.coerceAtLeast(2)

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    var phase by remember(count) { mutableStateOf(YutStagePhase.IDLE) }
    val yutStates = remember(count) {
        mutableStateListOf<YutPlayerState>().apply {
            for (i in 0 until count) {
                add(YutPlayerState(playerIndex = i))
            }
        }
    }
    val turnCompleted = remember(count) {
        mutableStateListOf<Boolean>().apply {
            repeat(count) { add(false) }
        }
    }

    var activePlayerIdx by remember(count) { mutableIntStateOf(0) }
    var hasBonusTurnPending by remember(count) { mutableStateOf(false) }
    var shownSticksFlat by remember { mutableStateOf(listOf(true, false, true, false)) }
    var stickJumpRatio by remember { mutableFloatStateOf(0f) }
    var stickSpinDeg by remember { mutableFloatStateOf(0f) }
    var lastEventHeadline by remember {
        mutableStateOf("윷·모가 나오면 한 번 더! 앞선 말을 잡으면 +2칸 전진합니다")
    }

    var rankedResults by remember(count) { mutableStateOf<List<RankedYutResult>>(emptyList()) }
    var actionNonce by remember { mutableIntStateOf(0) }
    var requestedAutoAll by remember { mutableStateOf(false) }
    var impactBurstKey by remember { mutableIntStateOf(0) }
    var showResultModal by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }

    fun resetMatch() {
        soundEngine.setClimaxDucking(false)
        for (i in 0 until count) {
            yutStates[i] = YutPlayerState(playerIndex = i)
            turnCompleted[i] = false
        }
        activePlayerIdx = 0
        hasBonusTurnPending = false
        shownSticksFlat = listOf(true, false, true, false)
        stickJumpRatio = 0f
        stickSpinDeg = 0f
        lastEventHeadline = "윷·모가 나오면 한 번 더! 앞선 말을 잡으면 +2칸 전진합니다"
        rankedResults = emptyList()
        showResultModal = false
        phase = YutStagePhase.IDLE
    }

    fun triggerManualThrow() {
        if (phase == YutStagePhase.THROWING || phase == YutStagePhase.SLOW_MO_FINAL) return
        if (turnCompleted.all { it }) return
        onIntroCutsceneFinished()
        requestedAutoAll = false
        actionNonce++
    }

    fun triggerAutoMatch() {
        if (phase == YutStagePhase.THROWING || phase == YutStagePhase.SLOW_MO_FINAL) return
        if (turnCompleted.all { it }) {
            resetMatch()
        }
        onIntroCutsceneFinished()
        requestedAutoAll = true
        actionNonce++
    }

    LaunchedEffect(actionNonce) {
        if (actionNonce == 0) return@LaunchedEffect

        val totalTargetSec = effectiveSettings.normalizedDurationSeconds.toFloat()
        val remainingPlayers = turnCompleted.count { !it }.coerceAtLeast(1)
        val baseThrowSec = if (requestedAutoAll) {
            (totalTargetSec / (remainingPlayers * 1.25f)).coerceIn(1.25f, 3.2f)
        } else {
            1.65f
        }

        while (true) {
            val targetIdx = if (hasBonusTurnPending && !turnCompleted[activePlayerIdx]) {
                activePlayerIdx
            } else {
                turnCompleted.indexOfFirst { !it }
            }
            if (targetIdx < 0) break

            activePlayerIdx = targetIdx
            val isPotentiallyLastPlayer = turnCompleted.count { !it } == 1
            val singleThrow = YutGameLogic.throwSticksOnce()
            val willFinishAllAfterThis = isPotentiallyLastPlayer &&
                !(singleThrow.outcome.grantsBonusTurn && yutStates[targetIdx].throws.size < YutGameLogic.MAX_BONUS_THROWS_PER_PLAYER)

            phase = YutStagePhase.THROWING
            soundEngine.playYutTossWhoosh()

            var elapsed = 0f
            var prevNanos = 0L
            var flipTick = -1
            var slowMoTriggered = false

            while (elapsed < baseThrowSec) {
                withFrameNanos { now ->
                    if (prevNanos != 0L) {
                        elapsed += ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.045f)
                    }
                    prevNanos = now
                }
                val u = (elapsed / baseThrowSec).coerceIn(0f, 1f)

                if (willFinishAllAfterThis && effectiveSettings.slowMotionEnabled && u >= 0.56f) {
                    if (!slowMoTriggered) {
                        slowMoTriggered = true
                        phase = YutStagePhase.SLOW_MO_FINAL
                        soundEngine.setClimaxDucking(true)
                        soundEngine.playSlowMoEnter()
                        soundEngine.playHeartbeat(intense = true)
                    }
                }

                // 공중 체공(0~0.76) -> 멍석 착지 반동(0.76~1.0)
                if (u < 0.76f) {
                    val airU = u / 0.76f
                    stickJumpRatio = sin(airU * PI).toFloat()
                    stickSpinDeg = airU * 720f
                    val step = (airU * 10f).toInt()
                    if (step != flipTick) {
                        flipTick = step
                        shownSticksFlat = List(4) { Random.nextBoolean() }
                    }
                } else {
                    val landU = (u - 0.76f) / 0.24f
                    stickJumpRatio = sin(landU * PI).toFloat() * 0.14f
                    stickSpinDeg = 0f
                    shownSticksFlat = singleThrow.sticksFlat
                }
            }

            stickJumpRatio = 0f
            stickSpinDeg = 0f
            shownSticksFlat = singleThrow.sticksFlat

            val turnEvent = YutGameLogic.applySingleThrow(
                currentStates = yutStates.toList(),
                throwerIndex = targetIdx,
                singleThrow = singleThrow
            )
            turnEvent.updatedStates.forEachIndexed { idx, st ->
                yutStates[idx] = st
            }

            val isExciting = singleThrow.outcome.grantsBonusTurn || turnEvent.capturedPlayerIndices.isNotEmpty()
            soundEngine.playYutSticksClack(isBonusYutMo = isExciting)
            impactBurstKey++

            val throwerName = players.getOrElse(targetIdx) { players.first() }.name
            val deltaSign = if (singleThrow.outcome.stepDelta > 0) "+${singleThrow.outcome.stepDelta}" else "${singleThrow.outcome.stepDelta}"
            val captureMsg = if (turnEvent.capturedPlayerIndices.isNotEmpty()) {
                val victimNames = turnEvent.capturedPlayerIndices.joinToString(",") { v ->
                    players.getOrElse(v) { players.first() }.name
                }
                " · ${victimNames} 말 잡기(+2칸)!"
            } else ""
            val bonusMsg = if (turnEvent.earnedBonusThrow) " · 한 번 더!" else ""

            lastEventHeadline = "${throwerName}: ${singleThrow.outcome.koreanName}(${deltaSign}칸)${captureMsg}${bonusMsg}"

            if (turnEvent.earnedBonusThrow) {
                hasBonusTurnPending = true
            } else {
                hasBonusTurnPending = false
                turnCompleted[targetIdx] = true
            }

            if (!requestedAutoAll) {
                break
            } else if (!turnCompleted.all { it }) {
                delay(340)
            }
        }

        if (turnCompleted.all { it }) {
            soundEngine.setClimaxDucking(false)
            val ranked = YutGameLogic.rankYutPlayers(yutStates.toList())
            rankedResults = ranked
            phase = YutStagePhase.FINISHED
            soundEngine.playGhibliFanfare()
            delay(880)
            showResultModal = true
        } else {
            val nextIdx = if (hasBonusTurnPending) activePlayerIdx else turnCompleted.indexOfFirst { !it }.coerceAtLeast(0)
            activePlayerIdx = nextIdx
            phase = YutStagePhase.IDLE
        }
    }

    val isActionNow = phase == YutStagePhase.THROWING || phase == YutStagePhase.SLOW_MO_FINAL
    val isSlowMo = phase == YutStagePhase.SLOW_MO_FINAL

    GameStageScaffold(
        gameType = GolfGameType.YUT_THROW,
        settings = effectiveSettings,
        isSlowMotion = isSlowMo,
        slowMotionLabel = "마지막 윷가락이 멍석에 떨어지는 순간 · 슬로우모션",
        showCelebration = phase == YutStagePhase.FINISHED,
        isActionActive = isActionNow,
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
                    gameType = GolfGameType.YUT_THROW,
                    videoEffectsEnabled = effectiveSettings.videoEffectsEnabled,
                    active = isActionNow,
                    isSlowMotion = isSlowMo,
                    activeVideoAlpha = 0.34f,
                    idleBackdropRes = R.drawable.bg_fairway_meadow,
                    idleBackdropAlpha = 0.28f
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // 1. 미니 오솔길 윷판 레이스 트랙 (빽도 -1칸 ~ 출발 0칸 ~ 10칸 홀컵 골인)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(PaperCream.copy(alpha = 0.94f))
                            .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "미니 윷판 홀컵 레이스 (최다 전진 승자 · 최하위 패자)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PineDeep
                            )
                            Text(
                                text = "목표 ${YutGameLogic.GOAL_STEP}칸",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TerracottaWood
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clipToBounds()
                        ) {
                            drawMiniYutRaceTrack(
                                states = yutStates.toList(),
                                playerColors = players.map { it.badgeColor }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // 2. 참가자별 윷 기록 및 현재 칸수 2열 카드
                    players.chunked(2).forEach { pair ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 5.dp)
                        ) {
                            pair.forEach { player ->
                                val st = yutStates.getOrElse(player.index) { YutPlayerState(player.index) }
                                val isTurn = activePlayerIdx == player.index && phase != YutStagePhase.FINISHED
                                val done = turnCompleted.getOrElse(player.index) { false }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(11.dp))
                                        .background(
                                            when {
                                                isTurn && hasBonusTurnPending -> SunlightSoft
                                                isTurn -> MeadowGreenSoft.copy(alpha = 0.94f)
                                                else -> PureCardWhite.copy(alpha = 0.93f)
                                            }
                                        )
                                        .border(
                                            width = if (isTurn) 1.6.dp else 1.dp,
                                            color = if (isTurn) MeadowGreen else SubtleDivider,
                                            shape = RoundedCornerShape(11.dp)
                                        )
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = player.avatarRes),
                                        contentDescription = player.name,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(26.dp)
                                            .clip(CircleShape)
                                            .border(1.2.dp, player.badgeColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = player.name,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InkPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = when {
                                                isTurn && hasBonusTurnPending -> "${st.outcomeHistoryText} · 한 번 더!"
                                                st.throws.isNotEmpty() -> st.outcomeHistoryText
                                                isTurn -> "이번 투구 차례"
                                                else -> "대기"
                                            },
                                            fontSize = 10.sp,
                                            color = if (hasBonusTurnPending && isTurn) TerracottaWood else InkSecondary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(7.dp))
                                            .background(
                                                when {
                                                    st.position >= YutGameLogic.GOAL_STEP -> TerracottaWood
                                                    st.position < 0 -> OakWood
                                                    done -> MeadowGreen
                                                    else -> WarmIvory
                                                }
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.5.dp)
                                    ) {
                                        Text(
                                            text = "${st.position}칸",
                                            fontSize = 10.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (done || st.position != 0) PureCardWhite else InkMuted
                                        )
                                    }
                                }
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }

                    // 3. 중앙 전통 멍석 매트 & 자작나무 윷가락 4개 투구 캔버스
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
                            drawYutMatAndFourSticks(
                                sticksFlat = shownSticksFlat,
                                jumpRatio = stickJumpRatio,
                                spinDeg = stickSpinDeg
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 2.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(PaperCream.copy(alpha = 0.96f))
                                .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = lastEventHeadline,
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
            if (phase == YutStagePhase.FINISHED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            resetMatch()
                            triggerAutoMatch()
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
                            text = "승패표",
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
                val activePlayer = players.getOrElse(activePlayerIdx) { players.first() }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = { triggerManualThrow() },
                        enabled = !isActionNow,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, OakWood),
                        modifier = Modifier
                            .weight(0.46f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (hasBonusTurnPending) {
                                "${activePlayer.name} 한 번 더!"
                            } else {
                                "${activePlayer.name} 윷 던지기"
                            },
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (!isActionNow) OakWood else InkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Button(
                        onClick = { triggerAutoMatch() },
                        enabled = !isActionNow,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SunlightAmber),
                        modifier = Modifier
                            .weight(0.54f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (turnCompleted.none { it } && !hasBonusTurnPending) {
                                "전원 윷 대결 (${effectiveSettings.normalizedDurationSeconds}초)"
                            } else {
                                "남은 인원 연속 투구"
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

    val sharePayload = remember(rankedResults, players, shownSticksFlat, lastEventHeadline, effectiveSettings.normalizedDurationSeconds) {
        if (rankedResults.isEmpty()) null
        else {
            val winner = rankedResults.first()
            val loser = rankedResults.last()
            val winnerPlayer = players.getOrElse(winner.state.playerIndex) { players.first() }
            val loserPlayer = players.getOrElse(loser.state.playerIndex) { players.last() }
            val rows = rankedResults.mapIndexed { idx, res ->
                val p = players.getOrElse(res.state.playerIndex) { players.first() }
                GameResultRow(
                    badgeText = res.badgeLabel,
                    playerName = p.name,
                    detailText = res.summaryDetail,
                    isHighlighted = res.isWinner || res.isLoser,
                    avatarRes = p.avatarRes,
                    badgeColor = distinctRankBadgeColor(idx)
                )
            }
            val snapshots = rankedResults.map { res ->
                val p = players.getOrElse(res.state.playerIndex) { players.first() }
                YutStateSnapshot(
                    playerName = p.name,
                    badgeColorArgb = p.badgeColor.toArgb(),
                    finalPosition = res.state.position,
                    historyText = res.state.outcomeHistoryText,
                    isWinner = res.isWinner,
                    isLoser = res.isLoser
                )
            }
            CommonGameSharePayload(
                gameType = GolfGameType.YUT_THROW,
                resultTitle = "잔디밭 민속 윷놀이 승패 결과",
                resultSubtitle = "승자: ${winnerPlayer.name}(${winner.state.position}칸) · 패자: ${loserPlayer.name}(${loser.state.position}칸)",
                rows = rows,
                durationSec = effectiveSettings.normalizedDurationSeconds,
                extraSceneData = GameExtraSceneData.YutNori(
                    statesInOrder = snapshots,
                    finalSticksFlat = shownSticksFlat,
                    headlineText = lastEventHeadline
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
                resetMatch()
                triggerAutoMatch()
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
                resetMatch()
                triggerAutoMatch()
            },
            onDismiss = { showShareModal = false }
        )
    }
}

private fun DrawScope.drawMiniYutRaceTrack(
    states: List<YutPlayerState>,
    playerColors: List<Color>
) {
    val totalNodes = 12 // -1(빽도), 0(출발), 1..9, 10+(홀컵 완주)
    val leftX = 16.dp.toPx()
    val rightX = size.width - 16.dp.toPx()
    val cy = size.height * 0.56f
    val stepX = (rightX - leftX) / (totalNodes - 1).coerceAtLeast(1)

    drawLine(
        color = SubtleDivider,
        start = Offset(leftX, cy),
        end = Offset(rightX, cy),
        strokeWidth = 3.dp.toPx(),
        cap = StrokeCap.Round
    )

    for (node in 0 until totalNodes) {
        val x = leftX + node * stepX
        val cellValue = node - 1
        val nodeColor = when {
            cellValue < 0 -> OakWood.copy(alpha = 0.65f)
            cellValue == 0 -> MeadowGreen
            cellValue >= YutGameLogic.GOAL_STEP -> TerracottaWood
            cellValue == 5 -> SunlightAmber
            else -> SoftSand
        }
        val radius = if (cellValue == 0 || cellValue == 5 || cellValue >= YutGameLogic.GOAL_STEP) {
            6.5.dp.toPx()
        } else {
            4.5.dp.toPx()
        }
        drawCircle(color = nodeColor, radius = radius, center = Offset(x, cy))
        drawCircle(
            color = OakWood.copy(alpha = 0.55f),
            radius = radius,
            center = Offset(x, cy),
            style = Stroke(width = 1.dp.toPx())
        )

        // 골인 홀컵 깃발 표시
        if (cellValue == YutGameLogic.GOAL_STEP) {
            drawLine(
                color = PineDeep,
                start = Offset(x, cy - 15.dp.toPx()),
                end = Offset(x, cy - 3.dp.toPx()),
                strokeWidth = 1.6.dp.toPx()
            )
            val flag = Path().apply {
                moveTo(x, cy - 15.dp.toPx())
                lineTo(x + 8.dp.toPx(), cy - 11.dp.toPx())
                lineTo(x, cy - 7.dp.toPx())
                close()
            }
            drawPath(flag, TerracottaWood)
        }
    }

    // 각 참가자의 말(Mal) 토큰 표시 (같은 칸일 경우 살짝 오프셋)
    val groupedByNode = states.groupBy { (it.position + 1).coerceIn(0, totalNodes - 1) }
    groupedByNode.forEach { (nodeIdx, group) ->
        val baseX = leftX + nodeIdx * stepX
        group.forEachIndexed { stackIdx, st ->
            val offsetX = (stackIdx - (group.size - 1) * 0.5f) * 7.dp.toPx()
            val offsetY = -10.dp.toPx() - (stackIdx % 2) * 3.dp.toPx()
            val color = playerColors.getOrElse(st.playerIndex) { CloverLeaf }
            drawCircle(
                color = PureCardWhite,
                radius = 6.2.dp.toPx(),
                center = Offset(baseX + offsetX, cy + offsetY)
            )
            drawCircle(
                color = color,
                radius = 4.8.dp.toPx(),
                center = Offset(baseX + offsetX, cy + offsetY)
            )
        }
    }
}

private fun DrawScope.drawYutMatAndFourSticks(
    sticksFlat: List<Boolean>,
    jumpRatio: Float,
    spinDeg: Float
) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.46f
    val matR = minOf(size.width, size.height) * 0.43f

    // 짚풀 멍석 원형 매트
    drawCircle(
        color = OakWood.copy(alpha = 0.72f),
        radius = matR * 1.06f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = SunlightSoft.copy(alpha = 0.95f),
        radius = matR,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = LightBirch.copy(alpha = 0.55f),
        radius = matR * 0.86f,
        center = Offset(cx, cy),
        style = Stroke(width = 1.6.dp.toPx())
    )

    val stickH = (matR * 1.16f).coerceIn(82.dp.toPx(), 138.dp.toPx()) * (1f + jumpRatio * 0.18f)
    val stickW = (stickH * 0.21f).coerceIn(16.dp.toPx(), 28.dp.toPx())
    val spacing = stickW * 1.52f
    val jumpOffsetY = -jumpRatio * matR * 0.36f

    for (i in 0 until 4) {
        val isFlat = sticksFlat.getOrElse(i) { true }
        val sx = cx + (i - 1.5f) * spacing
        val sy = cy + jumpOffsetY + (if (i % 2 == 0) -3.dp.toPx() else 3.dp.toPx())
        val baseTilt = when (i) {
            0 -> -4f
            1 -> 2.5f
            2 -> -2f
            else -> 4.5f
        }
        val angle = baseTilt + spinDeg * (if (i % 2 == 0) 1f else -1f) * 0.12f

        // 그림자
        drawRoundRect(
            color = InkPrimary.copy(alpha = 0.15f * (1f - jumpRatio * 0.5f)),
            topLeft = Offset(sx - stickW * 0.48f, cy + stickH * 0.04f - stickH * 0.5f + 6.dp.toPx()),
            size = Size(stickW * 0.96f, stickH),
            cornerRadius = CornerRadius(stickW * 0.45f)
        )

        rotate(degrees = angle, pivot = Offset(sx, sy)) {
            val topLeft = Offset(sx - stickW * 0.5f, sy - stickH * 0.5f)
            if (isFlat) {
                // 평평한 앞면(배): 밝은 자작나무 결 + 0번 가락 붉은 빽도 표식
                drawRoundRect(
                    color = PureCardWhite,
                    topLeft = topLeft,
                    size = Size(stickW, stickH),
                    cornerRadius = CornerRadius(stickW * 0.35f)
                )
                drawRoundRect(
                    color = WarmIvory,
                    topLeft = Offset(topLeft.x + 2.dp.toPx(), topLeft.y + 2.dp.toPx()),
                    size = Size(stickW - 4.dp.toPx(), stickH - 4.dp.toPx()),
                    cornerRadius = CornerRadius(stickW * 0.30f)
                )
                drawRoundRect(
                    color = OakWood,
                    topLeft = topLeft,
                    size = Size(stickW, stickH),
                    cornerRadius = CornerRadius(stickW * 0.35f),
                    style = Stroke(width = 1.8.dp.toPx())
                )
                if (i == 0) {
                    // 빽도 명패 원형 붉은 인장
                    drawCircle(
                        color = TerracottaWood,
                        radius = stickW * 0.28f,
                        center = Offset(sx, sy)
                    )
                    drawCircle(
                        color = SunlightSoft,
                        radius = stickW * 0.11f,
                        center = Offset(sx, sy)
                    )
                } else {
                    drawLine(
                        color = LightBirch,
                        start = Offset(sx, sy - stickH * 0.30f),
                        end = Offset(sx, sy + stickH * 0.30f),
                        strokeWidth = 1.1.dp.toPx()
                    )
                }
            } else {
                // 둥근 뒷면(등): 구운 참나무 색 + 전통 X자 문양 3개
                drawRoundRect(
                    color = OakWood,
                    topLeft = topLeft,
                    size = Size(stickW, stickH),
                    cornerRadius = CornerRadius(stickW * 0.48f)
                )
                drawRoundRect(
                    color = PineDeep.copy(alpha = 0.5f),
                    topLeft = topLeft,
                    size = Size(stickW, stickH),
                    cornerRadius = CornerRadius(stickW * 0.48f),
                    style = Stroke(width = 1.5.dp.toPx())
                )
                for (ry in listOf(-0.26f, 0f, 0.26f)) {
                    val my = sy + stickH * ry
                    val rCross = stickW * 0.22f
                    drawLine(
                        color = SunlightSoft,
                        start = Offset(sx - rCross, my - rCross),
                        end = Offset(sx + rCross, my + rCross),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = SunlightSoft,
                        start = Offset(sx + rCross, my - rCross),
                        end = Offset(sx - rCross, my + rCross),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }
    }
}
