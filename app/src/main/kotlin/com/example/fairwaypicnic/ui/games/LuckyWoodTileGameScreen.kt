package com.example.fairwaypicnic.ui.games

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.fairwaypicnic.ui.theme.SunlightSoft
import com.example.fairwaypicnic.ui.theme.TerracottaSoft
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.pow
import kotlin.math.sin

private enum class TileGameMode(val label: String) {
    COFFEE_PENALTY("꼴등 커피 1명 뽑기"),
    TEAM_SPLIT("청솔팀 vs 단풍팀 편성")
}

private data class WoodTileRole(
    val title: String,
    val subtitle: String,
    val isHighlight: Boolean,
    val isTeamB: Boolean = false
)

/**
 * 커피·꼴등 및 팀 편성 복불복 게임 7: 행운의 나무 패 뒤집기
 */
@Composable
fun LuckyWoodTileGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)

    var mode by remember { mutableStateOf(TileGameMode.COFFEE_PENALTY) }
    var roundSeed by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    val roles = remember(count, mode, roundSeed) {
        when (mode) {
            TileGameMode.COFFEE_PENALTY -> {
                val safeLabels = listOf(
                    WoodTileRole("버디 패", "시원하게 무사 통과", isHighlight = false),
                    WoodTileRole("이글 패", "최고의 행운 통과", isHighlight = false),
                    WoodTileRole("나이스 파", "안정적으로 통과", isHighlight = false),
                    WoodTileRole("굿 샷", "가볍게 통과", isHighlight = false),
                    WoodTileRole("세이프", "무사히 통과", isHighlight = false)
                )
                val list = mutableListOf(
                    WoodTileRole("커피 당첨", "오늘 커피·음료 쏘기 주인공", isHighlight = true)
                )
                for (i in 0 until count - 1) {
                    list.add(safeLabels[i % safeLabels.size])
                }
                list.shuffled()
            }

            TileGameMode.TEAM_SPLIT -> {
                val half = count / 2
                val list = mutableListOf<WoodTileRole>()
                var idxA = 1
                var idxB = 1
                repeat(half) {
                    list.add(WoodTileRole("청솔 A팀 ${idxA}번", "푸른 소나무 팀 합류", isHighlight = false, isTeamB = false))
                    idxA++
                }
                repeat(count - half) {
                    list.add(WoodTileRole("단풍 B팀 ${idxB}번", "붉은 단풍 팀 합류", isHighlight = true, isTeamB = true))
                    idxB++
                }
                list.shuffled()
            }
        }
    }

    val ownerByTile = remember(count, mode, roundSeed) {
        mutableStateListOf<Int>().apply { repeat(count) { add(-1) } }
    }
    var turnIdx by remember(count, mode, roundSeed) { mutableIntStateOf(0) }
    var flipDegrees by remember(count, mode, roundSeed) { mutableFloatStateOf(0f) }
    var tileShakeX by remember(count, mode, roundSeed) { mutableFloatStateOf(0f) }
    var isOpeningSequence by remember(count, mode, roundSeed) { mutableStateOf(false) }
    var isFlippingSlowMo by remember(count, mode, roundSeed) { mutableStateOf(false) }
    var isRevealed by remember(count, mode, roundSeed) { mutableStateOf(false) }
    var showResultModal by remember(count, mode, roundSeed) { mutableStateOf(false) }
    var flipTrigger by remember { mutableIntStateOf(0) }

    fun resetTiles() {
        soundEngine.setClimaxDucking(false)
        soundEngine.playTileShuffle()
        showResultModal = false
        roundSeed++
    }

    fun selectTile(tileIdx: Int) {
        if (isOpeningSequence || isFlippingSlowMo || isRevealed) return
        if (ownerByTile[tileIdx] != -1 || turnIdx >= count) return

        ownerByTile[tileIdx] = turnIdx
        soundEngine.playCardFlip()
        turnIdx++

        if (turnIdx == count - 1) {
            val lastIdx = ownerByTile.indexOfFirst { it == -1 }
            if (lastIdx >= 0) {
                ownerByTile[lastIdx] = turnIdx
                turnIdx++
            }
        }
        if (turnIdx >= count) {
            flipTrigger++
        }
    }

    fun quickAssignAndFlip() {
        if (isOpeningSequence || isFlippingSlowMo || isRevealed) return
        val free = ownerByTile.indices.filter { ownerByTile[it] == -1 }.shuffled()
        var ptr = 0
        while (turnIdx < count && ptr < free.size) {
            ownerByTile[free[ptr]] = turnIdx
            turnIdx++
            ptr++
        }
        soundEngine.playTileShuffle()
        flipTrigger++
    }

    LaunchedEffect(flipTrigger) {
        if (flipTrigger == 0) return@LaunchedEffect
        isOpeningSequence = true
        isFlippingSlowMo = false
        soundEngine.setClimaxDucking(false)

        val totalSec = settings.normalizedDurationSeconds.toFloat()
        var elapsed = 0f
        var rattleTimer = 0f
        var hbTimer = 0f
        var enteredFlipClimax = false
        var prev = 0L

        while (elapsed < totalSec) {
            var dt = 0f
            withFrameNanos { now ->
                if (prev != 0L) {
                    dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prev = now
            }
            elapsed = (elapsed + dt).coerceAtMost(totalSec)
            val u = (elapsed / totalSec).coerceIn(0f, 1f)

            if (u < 0.28f) {
                // 전반 28% 구간: 테이블 위 나무 패가 두근거리며 미세하게 진동하는 전조 연출
                val shakeAmp = (u / 0.28f) * 5.5f
                tileShakeX = sin(elapsed * 42f) * shakeAmp
                flipDegrees = (u / 0.28f) * 22f
                rattleTimer += dt
                if (rattleTimer >= 0.28f) {
                    rattleTimer = 0f
                    soundEngine.playWoodTick(highPitch = (elapsed * 10).toInt() % 2 == 0)
                }
            } else {
                // 후반 72% 구간: 나무 패가 슬로우모션으로 천천히 뒤집히며 각인이 드러나는 클라이맥스
                tileShakeX = 0f
                if (!enteredFlipClimax) {
                    enteredFlipClimax = true
                    soundEngine.playTileTurnWhoosh()
                    if (settings.slowMotionEnabled) {
                        isFlippingSlowMo = true
                        soundEngine.setClimaxDucking(true)
                        soundEngine.playSlowMoEnter()
                    }
                    soundEngine.playHeartbeat(intense = true)
                }

                val subU = ((u - 0.28f) / 0.72f).coerceIn(0f, 1f)
                // 90도 전후에서 숨 막히게 천천히 넘어가도록 완만한 감속 곡선 적용
                val eased = 1f - (1f - subU).pow(2.1f)
                flipDegrees = 22f + 158f * eased

                if (settings.slowMotionEnabled) {
                    hbTimer += dt
                    if (hbTimer >= 0.58f && u < 0.97f) {
                        hbTimer = 0f
                        soundEngine.playHeartbeat(intense = true)
                    }
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        tileShakeX = 0f
        flipDegrees = 180f
        isFlippingSlowMo = false
        isOpeningSequence = false
        isRevealed = true
        if (mode == TileGameMode.COFFEE_PENALTY) {
            soundEngine.playPenaltyReveal()
        } else {
            soundEngine.playGhibliFanfare()
        }
        delay(950)
        showResultModal = true
    }

    val resultRows = remember(isRevealed, roundSeed, mode, players) {
        if (!isRevealed) emptyList()
        else {
            val paired = roles.mapIndexedNotNull { tileIdx, role ->
                val pIdx = ownerByTile.getOrNull(tileIdx) ?: -1
                if (pIdx in players.indices) {
                    players[pIdx] to role
                } else null
            }
            val sorted = if (mode == TileGameMode.COFFEE_PENALTY) {
                paired.sortedByDescending { (_, role) -> role.isHighlight }
            } else {
                paired.sortedBy { (_, role) -> role.title }
            }
            sorted.mapIndexed { displayIdx, (pl, role) ->
                GameResultRow(
                    badgeText = role.title,
                    playerName = pl.name,
                    detailText = role.subtitle,
                    isHighlighted = role.isHighlight,
                    avatarRes = pl.avatarRes,
                    badgeColor = distinctRankBadgeColor(displayIdx)
                )
            }
        }
    }

    val colsPerRow = if (count <= 4) 2 else 3
    val rowChunks = (0 until count).chunked(colsPerRow)
    val curPlayer = players.getOrNull(turnIdx.coerceIn(0, count - 1))

    GameStageScaffold(
        gameType = GolfGameType.LUCKY_WOOD_TILE,
        settings = settings,
        isSlowMotion = isFlippingSlowMo,
        slowMotionLabel = "나무 패가 천천히 뒤집힙니다 · 슬로우모션",
        showCelebration = isRevealed,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TileGameMode.entries.forEach { item ->
                        val selected = mode == item
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MeadowGreen else WarmIvory)
                                .border(1.dp, if (selected) MeadowGreen else SubtleDivider, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isOpeningSequence && !isFlippingSlowMo) {
                                    mode = item
                                    resetTiles()
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = item.label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) PureCardWhite else InkSecondary
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (!isRevealed) {
                        Button(
                            onClick = { quickAssignAndFlip() },
                            enabled = !isOpeningSequence && !isFlippingSlowMo,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                text = if (isOpeningSequence) "운명의 패 오픈 중..."
                                else "패 자동 배분 · 오픈 (${settings.normalizedDurationSeconds}초)",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureCardWhite
                            )
                        }
                        OutlinedButton(
                            onClick = { resetTiles() },
                            enabled = !isOpeningSequence && !isFlippingSlowMo,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("다시 섞기", color = InkPrimary)
                        }
                    } else {
                        Button(
                            onClick = { resetTiles() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text(
                                text = "새로 패 섞어서 다시 하기",
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
                    .background(PaperCream.copy(alpha = 0.58f))
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp)
            ) {
                // 안내 배너
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureCardWhite.copy(alpha = 0.94f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    if (!isRevealed && !isOpeningSequence && curPlayer != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Image(
                                painter = painterResource(id = curPlayer.avatarRes),
                                contentDescription = curPlayer.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, curPlayer.badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${curPlayer.name}님, 마음에 드는 나무 패를 고르세요",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary
                                )
                                Text(
                                    text = "모든 동반자가 패를 고르면 ${settings.normalizedDurationSeconds}초 연출과 함께 뒤집힙니다",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = InkSecondary
                                )
                            }
                        }
                    } else if (isOpeningSequence) {
                        Text(
                            text = "운명의 나무 패가 천천히 열리고 있습니다...",
                            style = MaterialTheme.typography.titleMedium,
                            color = OakWood
                        )
                    } else {
                        Text(
                            text = if (mode == TileGameMode.COFFEE_PENALTY) "모든 패가 공개되었습니다" else "청솔 A팀과 단풍 B팀 편성이 완료되었습니다",
                            style = MaterialTheme.typography.titleMedium,
                            color = PineDeep
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 나무 패 그리드
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    rowChunks.forEach { rowIndices ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        ) {
                            rowIndices.forEach { tileIdx ->
                                val ownerIdx = ownerByTile[tileIdx]
                                val role = roles[tileIdx]
                                val showFront = flipDegrees >= 90f

                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            translationX = if (tileIdx % 2 == 0) tileShakeX else -tileShakeX
                                            rotationY = flipDegrees
                                            cameraDistance = 14f * density
                                        }
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (!showFront) {
                                                if (ownerIdx != -1) LightBirch else Color(0xFFE3CCA8)
                                            } else {
                                                when {
                                                    mode == TileGameMode.TEAM_SPLIT && role.isTeamB -> TerracottaSoft
                                                    mode == TileGameMode.TEAM_SPLIT && !role.isTeamB -> MeadowGreenSoft
                                                    role.isHighlight -> SunlightSoft
                                                    else -> PureCardWhite
                                                }
                                            }
                                        )
                                        .border(
                                            width = if (ownerIdx != -1 || showFront) 2.dp else 1.4.dp,
                                            color = if (!showFront) OakWood else {
                                                if (role.isHighlight) TerracottaWood else MeadowGreen
                                            },
                                            shape = RoundedCornerShape(16.dp)
                                        )
                                        .clickable(enabled = ownerIdx == -1 && !isOpeningSequence && !isFlippingSlowMo && !isRevealed) {
                                            selectTile(tileIdx)
                                        }
                                        .padding(10.dp)
                                ) {
                                    if (!showFront) {
                                        // 뒷면: 전통 나뭇잎 각인 문양 + 선택한 동반자 표시
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Canvas(modifier = Modifier.size(32.dp).clipToBounds()) {
                                                drawCircle(
                                                    color = OakWood.copy(alpha = 0.25f),
                                                    radius = size.minDimension * 0.46f,
                                                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                                                )
                                                drawCircle(
                                                    color = CloverLeaf.copy(alpha = 0.65f),
                                                    radius = size.minDimension * 0.24f,
                                                    center = Offset(size.width * 0.5f, size.height * 0.5f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                            if (ownerIdx in players.indices) {
                                                val pl = players[ownerIdx]
                                                Text(
                                                    text = "${pl.name} 선택",
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = InkPrimary,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            } else {
                                                Text(
                                                    text = "${tileIdx + 1}번 나무 패",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = OakWood,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            }
                                        }
                                    } else {
                                        // 앞면 (180도 뒤집혔으므로 텍스트 거울 반전 복구: rotationY = 180f)
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.graphicsLayer { rotationY = 180f }
                                        ) {
                                            if (ownerIdx in players.indices) {
                                                val pl = players[ownerIdx]
                                                Image(
                                                    painter = painterResource(id = pl.avatarRes),
                                                    contentDescription = pl.name,
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier
                                                        .size(38.dp)
                                                        .clip(CircleShape)
                                                        .border(1.5.dp, pl.badgeColor, CircleShape)
                                                )
                                                Spacer(modifier = Modifier.height(6.dp))
                                                Text(
                                                    text = pl.name,
                                                    style = MaterialTheme.typography.labelLarge,
                                                    color = InkSecondary
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = role.title,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = if (role.isHighlight) TerracottaWood else PineDeep,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = role.subtitle,
                                                fontSize = 11.sp,
                                                color = InkSecondary,
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

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = if (mode == TileGameMode.COFFEE_PENALTY) "나무 패 오픈 결과" else "팀 편성 결과",
            subtitle = if (mode == TileGameMode.COFFEE_PENALTY) "뒤집힌 나무 패에서 오늘의 커피 주인공이 가려졌습니다." else "오늘 라운딩 동반 팀 구성을 확인하세요.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                resetTiles()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
