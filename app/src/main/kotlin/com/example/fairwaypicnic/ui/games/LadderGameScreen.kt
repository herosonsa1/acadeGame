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
import androidx.compose.ui.graphics.StrokeJoin
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
import kotlin.math.hypot
import kotlin.math.pow
import kotlin.random.Random

private data class LadderRung(val rowIdx: Int, val leftCol: Int)

private data class PathWaypoint(val xNorm: Float, val yNorm: Float)

private enum class LadderMode(val label: String) {
    TEE_ORDER("1~N번 타순 정하기"),
    COFFEE_PICK("커피 1명 당첨 뽑기")
}

/**
 * 1홀 티샷 순서 및 커피 겸용 게임 3: 숲속 오솔길 통나무 사다리 타기
 */
@Composable
fun LadderGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)
    val totalRows = if (settings.normalizedDurationSeconds >= 10) 9 else 7

    var mode by remember { mutableStateOf(LadderMode.TEE_ORDER) }
    var seed by remember { mutableIntStateOf(0) }
    var isRunning by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var isSlowMotion by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showResultModal by remember { mutableStateOf(false) }
    var animTrigger by remember { mutableIntStateOf(0) }

    val rungs = remember(count, seed, mode, totalRows) {
        val rng = Random(System.nanoTime() + seed)
        val generated = mutableListOf<LadderRung>()
        for (r in 0 until totalRows) {
            var col = 0
            while (col < count - 1) {
                if (rng.nextFloat() < 0.58f) {
                    generated.add(LadderRung(rowIdx = r, leftCol = col))
                    col += 2
                } else {
                    col += 1
                }
            }
        }
        for (c in 0 until count - 1) {
            if (generated.none { it.leftCol == c }) {
                val safeRow = (0 until totalRows).firstOrNull { r ->
                    generated.none { it.rowIdx == r && (it.leftCol == c - 1 || it.leftCol == c + 1) }
                }
                if (safeRow != null) {
                    generated.add(LadderRung(safeRow, c))
                }
            }
        }
        generated
    }

    val bottomSlots = remember(count, seed, mode) {
        when (mode) {
            LadderMode.TEE_ORDER -> (1..count).map { "${it}번" }.shuffled()
            LadderMode.COFFEE_PICK -> {
                val list = MutableList(count) { "통과" }
                list[Random.nextInt(count)] = "커피 당첨"
                list
            }
        }
    }

    val playerPaths: List<Pair<Int, List<PathWaypoint>>> = remember(rungs, count, totalRows) {
        (0 until count).map { startCol ->
            var curCol = startCol
            val pts = mutableListOf<PathWaypoint>()
            pts.add(PathWaypoint(curCol.toFloat(), 0f))
            for (r in 0 until totalRows) {
                val yRung = (r + 1f) / (totalRows + 1f)
                pts.add(PathWaypoint(curCol.toFloat(), yRung))
                val rightBridge = rungs.find { it.rowIdx == r && it.leftCol == curCol }
                val leftBridge = rungs.find { it.rowIdx == r && it.leftCol == curCol - 1 }
                if (rightBridge != null) {
                    curCol += 1
                    pts.add(PathWaypoint(curCol.toFloat(), yRung))
                } else if (leftBridge != null) {
                    curCol -= 1
                    pts.add(PathWaypoint(curCol.toFloat(), yRung))
                }
            }
            pts.add(PathWaypoint(curCol.toFloat(), 1f))
            curCol to pts
        }
    }

    fun resetLadder() {
        soundEngine.setClimaxDucking(false)
        soundEngine.playWoodTick(highPitch = true)
        isRunning = false
        isFinished = false
        isSlowMotion = false
        progress = 0f
        showResultModal = false
        seed++
    }

    fun startClimb() {
        if (isRunning) return
        if (isFinished) {
            seed++
        }
        progress = 0f
        isFinished = false
        showResultModal = false
        soundEngine.playLadderStartWhistle()
        animTrigger++
    }

    LaunchedEffect(animTrigger) {
        if (animTrigger == 0) return@LaunchedEffect
        val totalSec = settings.normalizedDurationSeconds.toFloat()
        isRunning = true
        isSlowMotion = false
        soundEngine.setClimaxDucking(false)

        var elapsed = 0f
        var tickBucket = 0
        var hbTimer = 0f
        var slowEntered = false
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

            val climax = settings.slowMotionEnabled && u >= 0.66f && u < 0.99f
            if (climax && !slowEntered) {
                slowEntered = true
                isSlowMotion = true
                soundEngine.setClimaxDucking(true)
                soundEngine.playSlowMoEnter()
                soundEngine.playHeartbeat(intense = true)
            } else if (!climax && isSlowMotion) {
                isSlowMotion = false
            }

            val mappedP = if (u < 0.66f) {
                (u / 0.66f) * 0.78f
            } else {
                val subU = ((u - 0.66f) / 0.34f).coerceIn(0f, 1f)
                0.78f + 0.22f * (1f - (1f - subU).pow(1.65f))
            }
            progress = mappedP.coerceIn(0f, 1f)

            val stepCountTarget = if (totalSec >= 10f) 24 else 16
            val newBucket = (progress * stepCountTarget).toInt()
            if (newBucket > tickBucket && !climax) {
                tickBucket = newBucket
                soundEngine.playWoodTick(highPitch = newBucket % 2 == 0)
            }
            if (climax) {
                hbTimer += dt
                if (hbTimer >= 0.58f) {
                    hbTimer = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            }
        }

        soundEngine.setClimaxDucking(false)
        progress = 1f
        isSlowMotion = false
        isRunning = false
        isFinished = true
        soundEngine.playCupIn()
        delay(220)
        if (mode == LadderMode.TEE_ORDER) {
            soundEngine.playGhibliFanfare()
        } else {
            soundEngine.playPenaltyReveal()
        }
        delay(850)
        showResultModal = true
    }

    val resultRows = remember(isFinished, playerPaths, bottomSlots, players, mode) {
        if (!isFinished) emptyList()
        else {
            val mapped = players.mapIndexed { idx, pl ->
                val destCol = playerPaths[idx].first
                val slotLabel = bottomSlots[destCol]
                Triple(pl, destCol, slotLabel)
            }
            if (mode == LadderMode.TEE_ORDER) {
                mapped.sortedBy { (_, _, label) -> label.filter { it.isDigit() }.toIntOrNull() ?: 99 }
                    .mapIndexed { rowIdx, (pl, _, label) ->
                        val isFirst = label == "1번"
                        GameResultRow(
                            badgeText = if (isFirst) "1번 오너" else "${label} 타순",
                            playerName = pl.name,
                            detailText = if (isFirst) "첫 번째 티샷 주인공으로 도착" else "오솔길 사다리 ${label} 홀컵 골인",
                            isHighlighted = isFirst,
                            avatarRes = pl.avatarRes,
                            badgeColor = distinctRankBadgeColor(rowIdx)
                        )
                    }
            } else {
                mapped.sortedByDescending { (_, _, label) -> label == "커피 당첨" }
                    .mapIndexed { rowIdx, (pl, _, label) ->
                        val hit = label == "커피 당첨"
                        GameResultRow(
                            badgeText = if (hit) "커피 당첨" else "${rowIdx}차 통과",
                            playerName = pl.name,
                            detailText = if (hit) "오늘 그늘집 커피·음료 당첨 주인공" else "기분 좋게 무사 통과",
                            isHighlighted = hit,
                            avatarRes = pl.avatarRes,
                            badgeColor = distinctRankBadgeColor(rowIdx)
                        )
                    }
            }
        }
    }

    GameStageScaffold(
        gameType = GolfGameType.FOREST_LADDER,
        settings = settings,
        isSlowMotion = isSlowMotion,
        slowMotionLabel = "마지막 통나무 갈림길 통과 중 · 슬로우모션",
        showCelebration = isFinished,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    LadderMode.entries.forEach { item ->
                        val selected = mode == item
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) MeadowGreen else WarmIvory)
                                .border(1.dp, if (selected) MeadowGreen else SubtleDivider, RoundedCornerShape(10.dp))
                                .clickable(enabled = !isRunning) {
                                    mode = item
                                    resetLadder()
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
                    Button(
                        onClick = { startClimb() },
                        enabled = !isRunning,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                    ) {
                        Text(
                            text = if (isFinished) "새 사다리로 다시 타기" else "골프공 동시 출발 (${settings.normalizedDurationSeconds}초)",
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite
                        )
                    }
                    OutlinedButton(
                        onClick = { resetLadder() },
                        enabled = !isRunning,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.height(52.dp)
                    ) {
                        Text("다리 재구성", color = InkPrimary)
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFE7F0E4))
        ) {
            Image(
                painter = painterResource(id = R.drawable.bg_fairway_meadow),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alpha = 0.22f,
                modifier = Modifier.fillMaxSize()
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    players.forEach { pl ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f)
                        ) {
                            Image(
                                painter = painterResource(id = pl.avatarRes),
                                contentDescription = pl.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .border(2.dp, pl.badgeColor, CircleShape)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = pl.name,
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Canvas(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                ) {
                    val w = size.width
                    val h = size.height
                    val colWidth = w / count.toFloat()

                    fun colX(col: Float): Float = colWidth * (col + 0.5f)
                    fun rowY(yNorm: Float): Float = 10.dp.toPx() + yNorm * (h - 20.dp.toPx())

                    for (c in 0 until count) {
                        val x = colX(c.toFloat())
                        drawLine(
                            color = PineDeep.copy(alpha = 0.35f),
                            start = Offset(x, rowY(0f)),
                            end = Offset(x, rowY(1f)),
                            strokeWidth = 6.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    val hideMiddle = !isRunning && !isFinished
                    rungs.forEach { rung ->
                        val y = rowY((rung.rowIdx + 1f) / (totalRows + 1f))
                        val x1 = colX(rung.leftCol.toFloat())
                        val x2 = colX((rung.leftCol + 1).toFloat())
                        drawLine(
                            color = OakWood,
                            start = Offset(x1, y),
                            end = Offset(x2, y),
                            strokeWidth = 7.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        drawLine(
                            color = LightBirch,
                            start = Offset(x1 + 4.dp.toPx(), y - 1.dp.toPx()),
                            end = Offset(x2 - 4.dp.toPx(), y - 1.dp.toPx()),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    if (hideMiddle) {
                        drawRoundRect(
                            color = PaperCream.copy(alpha = 0.93f),
                            topLeft = Offset(w * 0.04f, h * 0.22f),
                            size = Size(w * 0.92f, h * 0.54f),
                            cornerRadius = CornerRadius(20.dp.toPx())
                        )
                        drawRoundRect(
                            color = CloverLeaf.copy(alpha = 0.45f),
                            topLeft = Offset(w * 0.04f, h * 0.22f),
                            size = Size(w * 0.92f, h * 0.54f),
                            cornerRadius = CornerRadius(20.dp.toPx()),
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    if (isRunning || isFinished) {
                        playerPaths.forEachIndexed { idx, (_, pts) ->
                            val color = players[idx].badgeColor
                            val segLengths = FloatArray(pts.size - 1)
                            var totalLen = 0f
                            for (s in 0 until pts.size - 1) {
                                val xA = colX(pts[s].xNorm)
                                val yA = rowY(pts[s].yNorm)
                                val xB = colX(pts[s + 1].xNorm)
                                val yB = rowY(pts[s + 1].yNorm)
                                val d = hypot(xB - xA, yB - yA)
                                segLengths[s] = d
                                totalLen += d
                            }

                            val targetDist = totalLen * progress
                            var walked = 0f
                            val trailPath = Path()
                            var ballPos = Offset(colX(pts[0].xNorm), rowY(pts[0].yNorm))
                            trailPath.moveTo(ballPos.x, ballPos.y)

                            for (s in 0 until pts.size - 1) {
                                val xA = colX(pts[s].xNorm)
                                val yA = rowY(pts[s].yNorm)
                                val xB = colX(pts[s + 1].xNorm)
                                val yB = rowY(pts[s + 1].yNorm)
                                val seg = segLengths[s]
                                if (walked + seg <= targetDist) {
                                    trailPath.lineTo(xB, yB)
                                    ballPos = Offset(xB, yB)
                                    walked += seg
                                } else {
                                    val remain = (targetDist - walked) / seg.coerceAtLeast(0.001f)
                                    val curX = xA + (xB - xA) * remain
                                    val curY = yA + (yB - yA) * remain
                                    trailPath.lineTo(curX, curY)
                                    ballPos = Offset(curX, curY)
                                    break
                                }
                            }

                            drawPath(
                                path = trailPath,
                                color = color.copy(alpha = 0.72f),
                                style = Stroke(
                                    width = 3.5.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )

                            drawCircle(color = color, radius = 9.5.dp.toPx(), center = ballPos)
                            drawCircle(color = PureCardWhite, radius = 6.8.dp.toPx(), center = ballPos)
                        }
                    }
                }

                if (!isRunning && !isFinished) {
                    Text(
                        text = "출발 버튼을 누르면 숲속 안개가 걷히며 갈림길이 열립니다",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    bottomSlots.forEachIndexed { colIdx, slotText ->
                        val rankNum = slotText.filter { it.isDigit() }.toIntOrNull()
                        val slotAccent = when {
                            rankNum != null -> distinctRankBadgeColor(rankNum - 1)
                            slotText == "커피 당첨" -> TerracottaWood
                            else -> distinctRankBadgeColor((colIdx + 1).coerceAtMost(5))
                        }
                        val highlight = slotText == "1번" || slotText == "커피 당첨"
                        val arrivedPlayerIdx = if (isFinished) {
                            playerPaths.indexOfFirst { it.first == colIdx }
                        } else -1

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 3.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (highlight) SunlightAmber.copy(alpha = 0.22f) else PureCardWhite)
                                .border(
                                    width = if (highlight || isFinished) 1.6.dp else 1.dp,
                                    color = if (highlight || isFinished) slotAccent else SubtleDivider,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = slotText,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = slotAccent,
                                maxLines = 1
                            )
                            if (arrivedPlayerIdx in players.indices) {
                                Text(
                                    text = players[arrivedPlayerIdx].name,
                                    fontSize = 11.sp,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = if (mode == LadderMode.TEE_ORDER) "숲속 사다리 타순 결과" else "숲속 사다리 커피 내기 결과",
            subtitle = if (mode == LadderMode.TEE_ORDER) "통나무 다리를 건너 도착한 홀컵 번호로 타순이 확정되었습니다." else "통나무 사다리 끝에서 오늘의 커피 주인공이 정해졌습니다.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                startClimb()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
