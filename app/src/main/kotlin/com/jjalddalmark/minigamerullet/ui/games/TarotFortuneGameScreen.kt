package com.jjalddalmark.minigamerullet.ui.games

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
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
import com.jjalddalmark.minigamerullet.R
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.PlayerInfo
import com.jjalddalmark.minigamerullet.model.PlayerTarotReading
import com.jjalddalmark.minigamerullet.model.TarotEmblemType
import com.jjalddalmark.minigamerullet.model.TarotFortuneCatalog
import com.jjalddalmark.minigamerullet.model.TarotTopic
import com.jjalddalmark.minigamerullet.share.CommonGameSharePayload
import com.jjalddalmark.minigamerullet.share.GameExtraSceneData
import com.jjalddalmark.minigamerullet.ui.components.CommonGameShareDialog
import com.jjalddalmark.minigamerullet.ui.components.GameStageScaffold
import com.jjalddalmark.minigamerullet.ui.components.GhibliRawVideoPlayer
import com.jjalddalmark.minigamerullet.ui.components.StageCinemaBackdrop
import com.jjalddalmark.minigamerullet.ui.theme.CloverLeaf
import com.jjalddalmark.minigamerullet.ui.theme.InkMuted
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.OakWood
import com.jjalddalmark.minigamerullet.ui.theme.PaperCream
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondBlue
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import com.jjalddalmark.minigamerullet.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class TarotStagePhase {
    SELECTING,
    REVEALING,
    SLOW_MO_CLIMAX,
    FINISHED
}

/**
 * 타로 운세 & 등수 미니게임 10: 숲속 오두막 타로 운세
 * 6가지 운세 종류 선택, 22장 메이저 아르카나 리딩, 다인 참가자 행운 점수 및 1~N위 등수 산출
 */
@Composable
fun TarotFortuneGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    showIntroCutscene: Boolean = false,
    onIntroCutsceneFinished: () -> Unit = {},
    onToggleBgmMute: () -> Unit = {},
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val effectiveSettings = remember(settings) { settings.ensureMinPlayersForGame(GolfGameType.TAROT_FORTUNE) }
    val players = effectiveSettings.players
    val count = players.size.coerceIn(1, 6)

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    var selectedTopic by remember { mutableStateOf(TarotTopic.TODAY_TOTAL) }
    var roundSeed by remember { mutableIntStateOf(1) }
    var phase by remember(count, selectedTopic, roundSeed) { mutableStateOf(TarotStagePhase.SELECTING) }

    val preparedReadings = remember(players, selectedTopic, roundSeed) {
        TarotFortuneCatalog.drawReadingsForPlayers(
            playerNames = players.map { it.name },
            topic = selectedTopic,
            rng = Random(System.nanoTime() xor roundSeed.toLong())
        )
    }

    // 부채꼴 덱(8장) 중 참가자별로 선택한 덱 인덱스
    val deckSize = 8
    val pickedDeckSlotByPlayer = remember(count, selectedTopic, roundSeed) {
        mutableStateListOf<Int>().apply { repeat(count) { add(-1) } }
    }
    val flipProgressByPlayer = remember(count, selectedTopic, roundSeed) {
        mutableStateListOf<Float>().apply { repeat(count) { add(0f) } }
    }

    var focusedReadingPlayerIdx by remember(count, selectedTopic, roundSeed) { mutableIntStateOf(0) }
    var revealTrigger by remember { mutableIntStateOf(0) }
    var impactBurstKey by remember { mutableIntStateOf(0) }
    var showResultModal by remember { mutableStateOf(false) }
    var showShareModal by remember { mutableStateOf(false) }

    fun resetSpread(newTopic: TarotTopic = selectedTopic) {
        soundEngine.setClimaxDucking(false)
        selectedTopic = newTopic
        roundSeed++
        showResultModal = false
        soundEngine.playTarotDeckSpread()
    }

    fun pickSpreadCardForNextWaiting(deckIdx: Int) {
        if (phase != TarotStagePhase.SELECTING) return
        if (deckIdx in pickedDeckSlotByPlayer) return
        val nextPlayer = pickedDeckSlotByPlayer.indexOfFirst { it == -1 }
        if (nextPlayer < 0) return

        onIntroCutsceneFinished()
        pickedDeckSlotByPlayer[nextPlayer] = deckIdx
        focusedReadingPlayerIdx = nextPlayer
        soundEngine.playCardFlip()

        // 전원이 카드를 골랐다면 즉시 타로 오픈 연출 시작
        if (pickedDeckSlotByPlayer.none { it == -1 }) {
            revealTrigger++
        }
    }

    fun autoPickAndRevealAll() {
        if (phase == TarotStagePhase.REVEALING || phase == TarotStagePhase.SLOW_MO_CLIMAX) return
        if (phase == TarotStagePhase.FINISHED) {
            resetSpread(selectedTopic)
            return
        }
        onIntroCutsceneFinished()
        val available = (0 until deckSize).filter { it !in pickedDeckSlotByPlayer }.shuffled()
        var cursor = 0
        for (p in 0 until count) {
            if (pickedDeckSlotByPlayer[p] == -1) {
                pickedDeckSlotByPlayer[p] = available.getOrElse(cursor++) { p % deckSize }
            }
        }
        soundEngine.playTarotDeckSpread()
        revealTrigger++
    }

    LaunchedEffect(revealTrigger) {
        if (revealTrigger == 0) return@LaunchedEffect

        val totalSec = effectiveSettings.normalizedDurationSeconds.toFloat()
        val perCardSec = (totalSec / count.coerceAtLeast(1)).coerceIn(1.25f, 3.6f)
        phase = TarotStagePhase.REVEALING

        for (pIdx in 0 until count) {
            focusedReadingPlayerIdx = pIdx
            val isLastCard = pIdx == count - 1
            val reading = preparedReadings[pIdx]
            var elapsed = 0f
            var prevNanos = 0L
            var slowMoEntered = false
            var chimePlayed = false

            while (elapsed < perCardSec) {
                withFrameNanos { now ->
                    if (prevNanos != 0L) {
                        elapsed += ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.045f)
                    }
                    prevNanos = now
                }
                val u = (elapsed / perCardSec).coerceIn(0f, 1f)

                if (isLastCard && effectiveSettings.slowMotionEnabled && u >= 0.35f && !slowMoEntered) {
                    slowMoEntered = true
                    phase = TarotStagePhase.SLOW_MO_CLIMAX
                    soundEngine.setClimaxDucking(true)
                    soundEngine.playSlowMoEnter()
                    soundEngine.playHeartbeat(intense = true)
                }

                // 부드러운 S-커브 3D 카드 뒤집기 (0f -> 1f)
                val smoothFlip = u * u * (3f - 2f * u)
                flipProgressByPlayer[pIdx] = smoothFlip

                if (smoothFlip >= 0.52f && !chimePlayed) {
                    chimePlayed = true
                    impactBurstKey++
                    soundEngine.playTarotMysticReveal(highFortune = reading.fortuneScore >= 84)
                }
            }
            flipProgressByPlayer[pIdx] = 1f
            if (!isLastCard) {
                delay(240)
            }
        }

        soundEngine.setClimaxDucking(false)
        // 오픈 완료 후 1위 참가자의 리딩 카드를 기본 포커스로 설정
        val topWinner = preparedReadings.minByOrNull { it.rankOneBased }
        if (topWinner != null) {
            focusedReadingPlayerIdx = topWinner.playerIndex
        }
        phase = TarotStagePhase.FINISHED
        soundEngine.playGhibliFanfare()
        delay(850)
        showResultModal = true
    }

    val isRevealing = phase == TarotStagePhase.REVEALING || phase == TarotStagePhase.SLOW_MO_CLIMAX
    val isSlowMo = phase == TarotStagePhase.SLOW_MO_CLIMAX

    GameStageScaffold(
        gameType = GolfGameType.TAROT_FORTUNE,
        settings = effectiveSettings,
        isSlowMotion = isSlowMo,
        slowMotionLabel = "마지막 운명의 아르카나 오픈 · 슬로우모션",
        showCelebration = phase == TarotStagePhase.FINISHED,
        isActionActive = isRevealing,
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
                    gameType = GolfGameType.TAROT_FORTUNE,
                    videoEffectsEnabled = effectiveSettings.videoEffectsEnabled,
                    active = isRevealing,
                    isSlowMotion = isSlowMo,
                    activeVideoAlpha = 0.32f,
                    idleBackdropRes = R.drawable.bg_wood_table,
                    idleBackdropAlpha = 0.26f
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(11.dp)
                ) {
                    // 1. 운세의 종류 6종 선택 바
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(PaperCream.copy(alpha = 0.95f))
                            .border(1.dp, SubtleDivider, RoundedCornerShape(13.dp))
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "운세 종류 선택: ${selectedTopic.label}",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PineDeep
                            )
                            Text(
                                text = selectedTopic.subtitle,
                                fontSize = 10.sp,
                                color = InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TarotTopic.entries.chunked(3).forEach { topicRow ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    topicRow.forEach { topic ->
                                        val isSelected = topic == selectedTopic
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(9.dp))
                                                .background(if (isSelected) PineDeep else WarmIvory)
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) PineDeep else SubtleDivider,
                                                    shape = RoundedCornerShape(9.dp)
                                                )
                                                .clickable(enabled = !isRevealing) {
                                                    if (topic != selectedTopic) {
                                                        resetSpread(topic)
                                                    }
                                                }
                                                .padding(horizontal = 6.dp, vertical = 5.5.dp)
                                        ) {
                                            Text(
                                                text = topic.shortLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isSelected) PureCardWhite else InkPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(7.dp))

                    // 2. 부채꼴 타로 스프레드 (선택 단계에서 직접 카드를 고를 수 있음)
                    if (phase == TarotStagePhase.SELECTING) {
                        val nextWaitingIdx = pickedDeckSlotByPlayer.indexOfFirst { it == -1 }.coerceAtLeast(0)
                        val waitingPlayer = players.getOrElse(nextWaitingIdx) { players.first() }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(WarmIvory.copy(alpha = 0.88f))
                                .border(1.dp, SubtleDivider, RoundedCornerShape(13.dp))
                                .padding(vertical = 8.dp, horizontal = 8.dp)
                        ) {
                            Text(
                                text = "${waitingPlayer.name}님이 끌리는 타로 카드를 터치하거나 하단 자동 오픈을 누르세요",
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                horizontalArrangement = Arrangement.spacedBy((-8).dp, Alignment.CenterHorizontally),
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                for (dIdx in 0 until deckSize) {
                                    val ownerPlayerIdx = pickedDeckSlotByPlayer.indexOf(dIdx)
                                    val isTaken = ownerPlayerIdx >= 0
                                    val tilt = (dIdx - 3.5f) * 3.8f
                                    val liftY = if (isTaken) (-6).dp else 0.dp
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .offset(y = liftY)
                                            .size(width = 40.dp, height = 60.dp)
                                            .graphicsLayer { rotationZ = tilt }
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isTaken) SunlightSoft else PineDeep)
                                            .border(
                                                width = 1.4.dp,
                                                color = if (isTaken) TerracottaWood else SunlightAmber,
                                                shape = RoundedCornerShape(6.dp)
                                            )
                                            .clickable(enabled = !isTaken) {
                                                pickSpreadCardForNextWaiting(dIdx)
                                            }
                                    ) {
                                        if (isTaken) {
                                            Text(
                                                text = "${ownerPlayerIdx + 1}번",
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = TerracottaWood
                                            )
                                        } else {
                                            Canvas(modifier = Modifier.size(18.dp)) {
                                                drawTarotBackStar(SunlightAmber)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                    }

                    // 3. 참가자별 타로 아르카나 카드 슬롯 그리드 (1명~6명 반응형 배치)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(7.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                        ) {
                            val cols = if (count == 1) 1 else 2
                            val chunks = players.take(count).chunked(cols)
                            chunks.forEach { rowPlayers ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowPlayers.forEach { p ->
                                        val reading = preparedReadings[p.index]
                                        val flip = flipProgressByPlayer.getOrElse(p.index) { 0f }
                                        val hasPicked = pickedDeckSlotByPlayer.getOrElse(p.index) { -1 } >= 0
                                        val isFocused = focusedReadingPlayerIdx == p.index

                                        TarotPlayerSlotCard(
                                            player = p,
                                            reading = reading,
                                            flipProgress = flip,
                                            hasPickedCard = hasPicked,
                                            totalPlayerCount = count,
                                            isFocused = isFocused,
                                            onClick = {
                                                focusedReadingPlayerIdx = p.index
                                                if (flip >= 0.95f) {
                                                    soundEngine.playWoodTick(highPitch = true)
                                                }
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(if (count == 1) 196.dp else if (count <= 4) 132.dp else 112.dp)
                                        )
                                    }
                                    if (rowPlayers.size < cols) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }

                            // 4. 오픈된 카드 터치 시 바로 읽는 하단 상세 타로 리딩 프리뷰 박스
                            val focusedReading = preparedReadings.getOrNull(focusedReadingPlayerIdx)
                            val focusedFlip = flipProgressByPlayer.getOrNull(focusedReadingPlayerIdx) ?: 0f
                            if (focusedReading != null && focusedFlip >= 0.85f) {
                                val rankColor = distinctRankBadgeColor(focusedReading.rankOneBased - 1)
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(13.dp))
                                        .background(PureCardWhite.copy(alpha = 0.96f))
                                        .border(1.4.dp, rankColor, RoundedCornerShape(13.dp))
                                        .padding(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "${focusedReading.playerName} · ${focusedReading.cardFullTitle}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = InkPrimary,
                                            modifier = Modifier.weight(1f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(7.dp))
                                                .background(rankColor)
                                                .padding(horizontal = 8.dp, vertical = 3.dp)
                                        ) {
                                            Text(
                                                text = if (count >= 2) {
                                                    "${focusedReading.rankOneBased}위 · ${focusedReading.fortuneScore}점"
                                                } else {
                                                    "${focusedReading.fortuneScore}점 · ${focusedReading.gradeLabel}"
                                                },
                                                fontSize = 10.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = PureCardWhite
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = focusedReading.detailedReading,
                                        fontSize = 11.5.sp,
                                        color = InkPrimary,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = focusedReading.luckyGuide,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MeadowGreen
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        bottomControls = {
            if (phase == TarotStagePhase.FINISHED) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = { showResultModal = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PineDeep),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (count >= 2) "등수 · 상세 풀이" else "타로 상세 풀이",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureCardWhite,
                            maxLines = 1
                        )
                    }
                    Button(
                        onClick = { showShareModal = true },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "공유",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureCardWhite
                        )
                    }
                    OutlinedButton(
                        onClick = { resetSpread(selectedTopic) },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, OakWood),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text(
                            text = "다시",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OakWood
                        )
                    }
                }
            } else {
                Button(
                    onClick = { autoPickAndRevealAll() },
                    enabled = !isRevealing,
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = PineDeep),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text(
                        text = when {
                            isRevealing -> "${selectedTopic.label} 타로 카드를 해석하는 중..."
                            count >= 2 -> "전원 ${selectedTopic.shortLabel} 타로점 · 등수 보기 (${effectiveSettings.normalizedDurationSeconds}초)"
                            else -> "${selectedTopic.label} 타로 카드 오픈 (${effectiveSettings.normalizedDurationSeconds}초)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = PureCardWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    )

    val sharePayload = remember(phase, selectedTopic, preparedReadings, players, effectiveSettings.normalizedDurationSeconds) {
        if (phase != TarotStagePhase.FINISHED || preparedReadings.isEmpty()) null
        else {
            val sorted = preparedReadings.sortedBy { it.rankOneBased }
            val topReading = sorted.first()
            val rows = sorted.mapIndexed { rIdx, item ->
                val pInfo = players.getOrElse(item.playerIndex) { players.first() }
                GameResultRow(
                    badgeText = if (sorted.size >= 2) "${item.rankOneBased}위 · ${item.fortuneScore}점" else "${item.fortuneScore}점 · ${item.gradeLabel}",
                    playerName = item.playerName,
                    detailText = "${item.cardFullTitle} · ${item.headline}",
                    isHighlighted = rIdx == 0,
                    avatarRes = pInfo.avatarRes,
                    badgeColor = distinctRankBadgeColor(rIdx)
                )
            }
            CommonGameSharePayload(
                gameType = GolfGameType.TAROT_FORTUNE,
                resultTitle = "${selectedTopic.label} 타로점 결과",
                resultSubtitle = if (sorted.size >= 2) {
                    "${selectedTopic.label} 1위는 ${topReading.fortuneScore}점(${topReading.gradeLabel})을 뽑은 ${topReading.playerName}님입니다."
                } else {
                    "${topReading.playerName}님의 ${topReading.cardFullTitle} · ${topReading.headline}"
                },
                rows = rows,
                durationSec = effectiveSettings.normalizedDurationSeconds,
                extraSceneData = GameExtraSceneData.TarotFortune(
                    topicLabel = selectedTopic.label,
                    topicShortLabel = selectedTopic.shortLabel,
                    readings = sorted
                )
            )
        }
    }

    if (showResultModal) {
        TarotReadingResultDialog(
            topic = selectedTopic,
            readings = preparedReadings,
            players = players,
            videoEffectsEnabled = effectiveSettings.videoEffectsEnabled,
            sharePayload = sharePayload,
            soundEnabled = effectiveSettings.soundEnabled,
            soundEngine = soundEngine,
            onReplaySameTopic = {
                resetSpread(selectedTopic)
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
            onReplay = { resetSpread(selectedTopic) },
            onDismiss = { showShareModal = false }
        )
    }
}

@Composable
private fun TarotPlayerSlotCard(
    player: PlayerInfo,
    reading: PlayerTarotReading,
    flipProgress: Float,
    hasPickedCard: Boolean,
    totalPlayerCount: Int,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFaceUp = flipProgress >= 0.5f
    val rotY = flipProgress * 180f
    val rankColor = distinctRankBadgeColor(reading.rankOneBased - 1)

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationY = rotY
                cameraDistance = 14f * density
            }
            .clip(RoundedCornerShape(14.dp))
            .background(if (isFaceUp) PureCardWhite else PineDeep)
            .border(
                width = if (isFocused && isFaceUp) 2.dp else 1.2.dp,
                color = when {
                    isFaceUp -> rankColor
                    hasPickedCard -> SunlightAmber
                    else -> SubtleDivider
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clickable { onClick() }
    ) {
        if (!isFaceUp) {
            // 타로 카드 뒷면
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp)
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
                            .size(22.dp)
                            .clip(CircleShape)
                            .border(1.dp, SunlightAmber, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = player.name,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = PureCardWhite,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(modifier = Modifier.size(34.dp)) {
                    drawTarotBackStar(SunlightAmber)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = if (hasPickedCard) "카드 선택 완료" else "카드 선택 대기",
                    fontSize = 10.sp,
                    color = if (hasPickedCard) SunlightSoft else PureCardWhite.copy(alpha = 0.72f)
                )
            }
        } else {
            // 타로 카드 앞면 (180도 회전 보정)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                PureCardWhite,
                                WarmIvory.copy(alpha = 0.75f)
                            )
                        )
                    )
                    .padding(horizontal = 8.dp, vertical = 7.dp)
            ) {
                // 상단: 참가자 이름 + 등수·점수 배지
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = player.name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(rankColor)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (totalPlayerCount >= 2) {
                                "${reading.rankOneBased}위 ${reading.fortuneScore}점"
                            } else {
                                "${reading.fortuneScore}점"
                            },
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = PureCardWhite
                        )
                    }
                }

                // 중앙: 메이저 아르카나 수채화 벡터 문양
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (totalPlayerCount == 1) 74.dp else 44.dp)
                        .graphicsLayer {
                            rotationZ = if (reading.isUpright) 0f else 180f
                        }
                ) {
                    TarotArcanaEmblemCanvas(
                        emblem = reading.card.emblem,
                        accentColor = rankColor,
                        sizeDp = if (totalPlayerCount == 1) 70.dp else 42.dp
                    )
                }

                // 하단: 카드 명칭 + 정/역방향 + 핵심 키워드
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${reading.card.romanNumeral}. ${reading.card.koreanName} (${reading.directionLabel})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = PineDeep,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = reading.headline,
                        fontSize = 9.5.sp,
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

/**
 * 타로 운세 최종 결과 모달:
 * 1) 참가자 수(1~6명)에 맞춘 행운 점수 순위표(1위, 2위, 3위, 4위...) 표시
 * 2) 각 참가자를 터치하면 선택한 운세 종류에 맞춘 상세 타로 풀이와 행운 가이드를 즉시 열람
 */
@Composable
private fun TarotReadingResultDialog(
    topic: TarotTopic,
    readings: List<PlayerTarotReading>,
    players: List<PlayerInfo>,
    videoEffectsEnabled: Boolean,
    sharePayload: CommonGameSharePayload? = null,
    soundEnabled: Boolean = true,
    soundEngine: GhibliSoundEngine? = null,
    onReplaySameTopic: () -> Unit,
    onCloseToHome: () -> Unit,
    onDismiss: () -> Unit
) {
    val sortedByRank = remember(readings) { readings.sortedBy { it.rankOneBased } }
    var selectedPlayerIdx by remember(sortedByRank) {
        mutableIntStateOf(sortedByRank.firstOrNull()?.playerIndex ?: 0)
    }
    var showShareModal by remember(sharePayload?.id) { mutableStateOf(false) }
    var initialShareTab by remember(sharePayload?.id) { mutableIntStateOf(0) }
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
                .heightIn(max = screenHeightDp * 0.90f)
        ) {
            Column(
                modifier = Modifier.padding(18.dp)
            ) {
                val topReading = sortedByRank.firstOrNull()
                Text(
                    text = "${topic.label} 타로점 결과",
                    style = MaterialTheme.typography.headlineMedium,
                    color = InkPrimary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (sortedByRank.size >= 2 && topReading != null) {
                        "${topic.label} 1위는 ${topReading.fortuneScore}점 · ${topReading.gradeLabel}을 뽑은 ${topReading.playerName}님입니다! 각 행을 눌러 개인별 상세 타로 풀이를 확인하세요."
                    } else {
                        "선택하신 ${topic.label}에 대한 메이저 아르카나 상세 해석과 행운 가이드입니다."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {

                if (videoEffectsEnabled) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                            .clipToBounds()
                    ) {
                        GhibliRawVideoPlayer(
                            videoRes = R.raw.vid_group_cheer,
                            posterRes = R.drawable.poster_group_cheer,
                            looping = true,
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomStart)
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, PaperCream.copy(alpha = 0.90f))
                                    )
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "동반자 모두에게 기분 좋은 행운이 함께하길 응원합니다!",
                                style = MaterialTheme.typography.labelLarge,
                                color = PineDeep
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 1. 참가자 수에 따른 타로 행운 점수 등수표 (1위, 2위, 3위, 4위...)
                if (sortedByRank.size >= 2) {
                    Text(
                        text = "참가자별 ${topic.shortLabel} 행운 점수 순위 (${sortedByRank.size}명)",
                        style = MaterialTheme.typography.titleMedium,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    sortedByRank.forEachIndexed { rIdx, item ->
                        val badgeColor = distinctRankBadgeColor(rIdx)
                        val pInfo = players.getOrElse(item.playerIndex) { players.first() }
                        val isSelected = selectedPlayerIdx == item.playerIndex

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(13.dp))
                                .background(if (isSelected) SunlightSoft else PureCardWhite)
                                .border(
                                    width = if (isSelected) 1.8.dp else 1.dp,
                                    color = if (isSelected) badgeColor else SubtleDivider,
                                    shape = RoundedCornerShape(13.dp)
                                )
                                .clickable { selectedPlayerIdx = item.playerIndex }
                                .padding(horizontal = 11.dp, vertical = 9.dp)
                        ) {
                            Image(
                                painter = painterResource(id = pInfo.avatarRes),
                                contentDescription = item.playerName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(9.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.playerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "${item.cardFullTitle} · ${item.gradeLabel}",
                                    fontSize = 11.5.sp,
                                    color = InkSecondary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(badgeColor)
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "${item.rankOneBased}위 · ${item.fortuneScore}점",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureCardWhite
                                )
                            }
                        }
                        if (rIdx < sortedByRank.lastIndex) {
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = SubtleDivider)
                    Spacer(modifier = Modifier.height(10.dp))
                }

                // 2. 선택된 참가자의 상세 타로점 리딩 카드
                val detailTarget = readings.firstOrNull { it.playerIndex == selectedPlayerIdx }
                    ?: sortedByRank.firstOrNull()
                if (detailTarget != null) {
                    val accent = distinctRankBadgeColor(detailTarget.rankOneBased - 1)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(15.dp))
                            .background(PureCardWhite)
                            .border(1.4.dp, accent, RoundedCornerShape(15.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(WarmIvory)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                                    .graphicsLayer {
                                        rotationZ = if (detailTarget.isUpright) 0f else 180f
                                    }
                            ) {
                                TarotArcanaEmblemCanvas(
                                    emblem = detailTarget.card.emblem,
                                    accentColor = accent,
                                    sizeDp = 42.dp
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${detailTarget.playerName}님의 ${topic.label}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = accent
                                )
                                Text(
                                    text = "${detailTarget.cardFullTitle} · ${detailTarget.card.englishName}",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "행운 지수 ${detailTarget.fortuneScore}점 · ${detailTarget.gradeLabel} · ${detailTarget.headline}",
                                    fontSize = 11.sp,
                                    color = InkSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = detailTarget.detailedReading,
                            style = MaterialTheme.typography.bodyMedium,
                            color = InkPrimary,
                            lineHeight = 19.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarmIvory)
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Column {
                                Text(
                                    text = "오늘의 조언: ${detailTarget.adviceLine}",
                                    fontSize = 11.5.sp,
                                    color = InkPrimary,
                                    lineHeight = 17.sp
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = detailTarget.luckyGuide,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MeadowGreen
                                )
                            }
                        }
                    }
                }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SubtleDivider)
                Spacer(modifier = Modifier.height(10.dp))

                if (sharePayload != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                soundEngine?.playWoodTick(true)
                                initialShareTab = 0
                                showShareModal = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.3.dp, TerracottaWood),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = TerracottaSoft),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "타로 결과 사진 공유",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerracottaWood,
                                maxLines = 1
                            )
                        }
                        Button(
                            onClick = {
                                soundEngine?.playWoodTick(true)
                                initialShareTab = 1
                                showShareModal = true
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                        ) {
                            Text(
                                text = "오픈 전 과정 영상 공유",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = PureCardWhite,
                                maxLines = 1
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

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
                        onClick = onReplaySameTopic,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PineDeep),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Text("다시 타로 보기", color = PureCardWhite, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showShareModal && sharePayload != null) {
        CommonGameShareDialog(
            payload = sharePayload,
            soundEnabled = soundEnabled,
            soundEngine = soundEngine,
            initialTab = initialShareTab,
            onReplay = onReplaySameTopic,
            onDismiss = { showShareModal = false }
        )
    }
}

private fun DrawScope.drawTarotBackStar(starColor: Color) {
    val cx = size.width * 0.5f
    val cy = size.height * 0.5f
    val r = minOf(size.width, size.height) * 0.44f
    drawCircle(
        color = starColor.copy(alpha = 0.25f),
        radius = r,
        center = Offset(cx, cy),
        style = Stroke(width = 1.2.dp.toPx())
    )
    for (i in 0 until 8) {
        val ang = i * (PI / 4.0)
        val len = if (i % 2 == 0) r else r * 0.62f
        drawLine(
            color = starColor,
            start = Offset(cx, cy),
            end = Offset(cx + cos(ang).toFloat() * len, cy + sin(ang).toFloat() * len),
            strokeWidth = 1.4.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
    drawCircle(color = starColor, radius = r * 0.20f, center = Offset(cx, cy))
}

@Composable
private fun TarotArcanaEmblemCanvas(
    emblem: TarotEmblemType,
    accentColor: Color,
    sizeDp: Dp
) {
    Canvas(modifier = Modifier.size(sizeDp).clipToBounds()) {
        val cx = size.width * 0.5f
        val cy = size.height * 0.5f
        val r = minOf(size.width, size.height) * 0.40f

        drawCircle(
            color = SunlightSoft,
            radius = r * 1.08f,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = accentColor.copy(alpha = 0.45f),
            radius = r * 1.08f,
            center = Offset(cx, cy),
            style = Stroke(width = 1.4.dp.toPx())
        )

        when (emblem) {
            TarotEmblemType.SUN_RADIANT -> {
                for (i in 0 until 12) {
                    val ang = i * (2.0 * PI / 12.0)
                    drawLine(
                        color = SunlightAmber,
                        start = Offset(cx + cos(ang).toFloat() * r * 0.52f, cy + sin(ang).toFloat() * r * 0.52f),
                        end = Offset(cx + cos(ang).toFloat() * r * 0.96f, cy + sin(ang).toFloat() * r * 0.96f),
                        strokeWidth = 1.8.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = SunlightAmber, radius = r * 0.44f, center = Offset(cx, cy))
            }

            TarotEmblemType.STAR_EIGHT,
            TarotEmblemType.LOVERS_TWIN_STAR -> {
                for (i in 0 until 8) {
                    val ang = i * (PI / 4.0)
                    val len = if (i % 2 == 0) r * 0.92f else r * 0.52f
                    drawLine(
                        color = accentColor,
                        start = Offset(cx, cy),
                        end = Offset(cx + cos(ang).toFloat() * len, cy + sin(ang).toFloat() * len),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                drawCircle(color = SunlightAmber, radius = r * 0.24f, center = Offset(cx, cy))
            }

            TarotEmblemType.MOON_LAKE,
            TarotEmblemType.PRIESTESS_CRESCENT -> {
                drawCircle(color = SkyPondBlue, radius = r * 0.68f, center = Offset(cx - r * 0.08f, cy))
                drawCircle(color = SunlightSoft, radius = r * 0.56f, center = Offset(cx + r * 0.18f, cy - r * 0.08f))
            }

            TarotEmblemType.WHEEL_FORTUNE -> {
                drawCircle(color = accentColor, radius = r * 0.78f, center = Offset(cx, cy), style = Stroke(width = 2.2.dp.toPx()))
                drawCircle(color = SunlightAmber, radius = r * 0.30f, center = Offset(cx, cy), style = Stroke(width = 1.8.dp.toPx()))
                for (i in 0 until 6) {
                    val ang = i * (PI / 3.0)
                    drawLine(
                        color = accentColor,
                        start = Offset(cx, cy),
                        end = Offset(cx + cos(ang).toFloat() * r * 0.78f, cy + sin(ang).toFloat() * r * 0.78f),
                        strokeWidth = 1.5.dp.toPx()
                    )
                }
            }

            TarotEmblemType.WORLD_WREATH,
            TarotEmblemType.EMPRESS_CROWN_LEAF -> {
                drawCircle(color = CloverLeaf, radius = r * 0.76f, center = Offset(cx, cy), style = Stroke(width = 2.8.dp.toPx()))
                drawCircle(color = SunlightAmber, radius = r * 0.26f, center = Offset(cx, cy))
            }

            TarotEmblemType.JUSTICE_SCALES -> {
                drawLine(color = OakWood, start = Offset(cx, cy - r * 0.72f), end = Offset(cx, cy + r * 0.72f), strokeWidth = 2.dp.toPx())
                drawLine(color = OakWood, start = Offset(cx - r * 0.68f, cy - r * 0.28f), end = Offset(cx + r * 0.68f, cy - r * 0.28f), strokeWidth = 2.dp.toPx())
                drawArc(color = accentColor, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = Offset(cx - r * 0.82f, cy + r * 0.05f), size = Size(r * 0.48f, r * 0.32f))
                drawArc(color = accentColor, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = Offset(cx + r * 0.34f, cy + r * 0.05f), size = Size(r * 0.48f, r * 0.32f))
            }

            else -> {
                val diamond = Path().apply {
                    moveTo(cx, cy - r * 0.80f)
                    lineTo(cx + r * 0.62f, cy)
                    lineTo(cx, cy + r * 0.80f)
                    lineTo(cx - r * 0.62f, cy)
                    close()
                }
                drawPath(path = diamond, color = accentColor.copy(alpha = 0.22f))
                drawPath(path = diamond, color = accentColor, style = Stroke(width = 2.dp.toPx()))
                drawCircle(color = SunlightAmber, radius = r * 0.22f, center = Offset(cx, cy))
            }
        }
    }
}
