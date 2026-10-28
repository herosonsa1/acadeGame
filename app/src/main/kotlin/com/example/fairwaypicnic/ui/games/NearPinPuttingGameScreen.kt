package com.example.fairwaypicnic.ui.games

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
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
import com.example.fairwaypicnic.ui.theme.PineDeep
import com.example.fairwaypicnic.ui.theme.PureCardWhite
import com.example.fairwaypicnic.ui.theme.SkyPondBlue
import com.example.fairwaypicnic.ui.theme.SkyPondSoft
import com.example.fairwaypicnic.ui.theme.SubtleDivider
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.TerracottaWood
import com.example.fairwaypicnic.ui.theme.WarmIvory
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

private data class PuttRecord(
    val playerIdx: Int,
    val finalX: Float,
    val finalY: Float,
    val inWater: Boolean,
    val holeIn: Boolean,
    val distanceCm: Int
)

/**
 * 커피·꼴등 복불복 게임 4: 그늘집 니어핀 퍼팅 치킨게임
 */
@Composable
fun NearPinPuttingGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val players = settings.players
    val count = players.size.coerceAtLeast(2)
    val textMeasurer = rememberTextMeasurer()

    val holeX = 0.50f
    val holeY = 0.25f
    val pondEdgeY = 0.145f
    val startX = 0.50f
    val startY = 0.86f

    var currentTurn by remember { mutableIntStateOf(0) }
    var isRolling by remember { mutableStateOf(false) }
    var isSlowMotion by remember { mutableStateOf(false) }
    var isAllDone by remember { mutableStateOf(false) }
    var showResultModal by remember { mutableStateOf(false) }

    var ballX by remember { mutableFloatStateOf(startX) }
    var ballY by remember { mutableFloatStateOf(startY) }
    var dragDelta by remember { mutableStateOf(Offset.Zero) }
    var isDragging by remember { mutableStateOf(false) }

    var gaugePower by remember { mutableFloatStateOf(0.55f) }
    var shotVelX by remember { mutableFloatStateOf(0f) }
    var shotVelY by remember { mutableFloatStateOf(0f) }
    var shotTrigger by remember { mutableIntStateOf(0) }

    val records = remember { mutableStateListOf<PuttRecord>() }

    LaunchedEffect(isRolling, isAllDone, isDragging) {
        if (isRolling || isAllDone || isDragging) return@LaunchedEffect
        var phase = 0f
        var prev = 0L
        while (true) {
            withFrameNanos { now ->
                if (prev != 0L) {
                    val dt = ((now - prev) / 1_000_000_000f).coerceIn(0f, 0.04f)
                    phase += dt * 2.4f
                    gaugePower = 0.22f + ((sin(phase) + 1f) * 0.5f) * 0.78f
                }
                prev = now
            }
        }
    }

    fun resetMatch() {
        soundEngine.setClimaxDucking(false)
        soundEngine.playWoodTick(true)
        records.clear()
        currentTurn = 0
        ballX = startX
        ballY = startY
        isRolling = false
        isSlowMotion = false
        isAllDone = false
        showResultModal = false
    }

    fun launchWithVelocity(vx: Float, vy: Float) {
        if (isRolling || isAllDone) return
        shotVelX = vx
        shotVelY = vy
        soundEngine.playPuttStroke()
        shotTrigger++
    }

    fun launchFromGauge() {
        if (isRolling || isAllDone) return
        val vy = -(0.52f + gaugePower * 0.92f)
        val vx = (Random.nextFloat() - 0.5f) * 0.09f
        launchWithVelocity(vx, vy)
    }

    LaunchedEffect(shotTrigger) {
        if (shotTrigger == 0) return@LaunchedEffect
        isRolling = true
        isSlowMotion = false
        soundEngine.setClimaxDucking(false)

        // 설정된 진행 시간(5초·10초·15초)에 비례해 최종 정지 위치 변형 없이 구름 템포 스케일링
        val paceMultiplier = when (settings.normalizedDurationSeconds) {
            5 -> 0.92f
            15 -> 0.44f
            else -> 0.62f
        }

        var vx = shotVelX
        var vy = shotVelY
        var bx = startX
        var by = startY
        var fellInWater = false
        var sunkInCup = false
        var slowEntered = false
        var hbTimer = 0f
        var rollSoundTimer = 0f
        var prevNanos = 0L

        while (true) {
            var dt = 0f
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.04f)
                }
                prevNanos = now
            }
            if (dt == 0f) continue

            val speed = hypot(vx, vy)
            val nearDangerOrLastPlayer = (currentTurn == count - 1 && by < 0.48f) || (by < 0.24f && speed < 0.48f)
            val shouldSlow = settings.slowMotionEnabled && nearDangerOrLastPlayer && speed > 0.03f
            if (shouldSlow && !slowEntered) {
                slowEntered = true
                isSlowMotion = true
                soundEngine.setClimaxDucking(true)
                soundEngine.playSlowMoEnter()
                soundEngine.playHeartbeat(intense = true)
            } else if (!shouldSlow && isSlowMotion && speed <= 0.03f) {
                isSlowMotion = false
            }

            if (isSlowMotion) {
                hbTimer += dt
                if (hbTimer >= 0.58f) {
                    hbTimer = 0f
                    soundEngine.playHeartbeat(intense = true)
                }
            } else {
                rollSoundTimer += dt * speed
                if (rollSoundTimer >= 0.16f) {
                    rollSoundTimer = 0f
                    soundEngine.playBallRollTick()
                }
            }

            val simDt = dt * paceMultiplier * (if (isSlowMotion) 0.26f else 1.0f)
            bx = (bx + vx * simDt).coerceIn(0.08f, 0.92f)
            if (bx <= 0.08f || bx >= 0.92f) vx = -vx * 0.6f
            by += vy * simDt

            val friction = 0.82f
            vx -= vx * friction * simDt
            vy -= vy * friction * simDt
            if (hypot(vx, vy) < 0.22f) {
                vx *= (1f - 1.9f * simDt).coerceAtLeast(0f)
                vy *= (1f - 1.9f * simDt).coerceAtLeast(0f)
            }

            ballX = bx
            ballY = by

            val distToCup = hypot(bx - holeX, by - holeY)
            if (distToCup < 0.032f && hypot(vx, vy) < 0.42f) {
                sunkInCup = true
                ballX = holeX
                ballY = holeY
                soundEngine.playCupIn()
                break
            }

            if (by <= pondEdgeY) {
                fellInWater = true
                ballY = pondEdgeY - 0.03f
                soundEngine.playWaterSplash()
                break
            }

            if (hypot(vx, vy) < 0.018f) {
                soundEngine.playGrassLand()
                break
            }
        }

        soundEngine.setClimaxDucking(false)
        isSlowMotion = false
        val distCm = when {
            sunkInCup -> 0
            fellInWater -> 999
            else -> (hypot(ballX - holeX, ballY - holeY) * 620f).roundToInt().coerceAtLeast(3)
        }
        records.add(
            PuttRecord(
                playerIdx = currentTurn,
                finalX = ballX,
                finalY = ballY,
                inWater = fellInWater,
                holeIn = sunkInCup,
                distanceCm = distCm
            )
        )

        delay(550)
        if (currentTurn < count - 1) {
            currentTurn++
            ballX = startX
            ballY = startY
            isRolling = false
        } else {
            isRolling = false
            isAllDone = true
            soundEngine.playPenaltyReveal()
            delay(850)
            showResultModal = true
        }
    }

    // 커피 당첨(꼴등)을 최상단에 강조하되 모든 행의 배지 색상이 완전히 다르게 표시되도록 정렬 및 매핑
    val resultRows = remember(isAllDone, records.size, players) {
        if (!isAllDone || records.isEmpty()) emptyList()
        else {
            val rankedClosestFirst = records.sortedBy { it.distanceCm }
            val loser = rankedClosestFirst.last()
            val orderedForDisplay = listOf(loser) + rankedClosestFirst.filter { it.playerIdx != loser.playerIdx }

            orderedForDisplay.mapIndexed { displayIdx, rec ->
                val p = players[rec.playerIdx]
                val isLoser = rec.playerIdx == loser.playerIdx
                val rankAmongClosest = rankedClosestFirst.indexOfFirst { it.playerIdx == rec.playerIdx } + 1
                val detail = when {
                    rec.holeIn -> "환상의 홀인원 성공 (0cm)"
                    rec.inWater -> "홀컵 뒤 연못 해저드 빠짐"
                    else -> "홀컵까지 남은 거리 ${rec.distanceCm}cm"
                }
                val badge = when {
                    isLoser -> "꼴등 · 커피 당첨"
                    rankAmongClosest == 1 -> "1위 니어핀"
                    else -> "${rankAmongClosest}위 통과"
                }
                GameResultRow(
                    badgeText = badge,
                    playerName = p.name,
                    detailText = detail,
                    isHighlighted = isLoser,
                    avatarRes = p.avatarRes,
                    badgeColor = distinctRankBadgeColor(displayIdx)
                )
            }
        }
    }

    GameStageScaffold(
        gameType = GolfGameType.NEAR_PIN_PUTTING,
        settings = settings,
        isSlowMotion = isSlowMotion,
        slowMotionLabel = "홀컵과 연못 사이 결정적 퍼팅 · 슬로우모션",
        showCelebration = isAllDone,
        onBack = onBack,
        onOpenPlayerDialog = onOpenPlayerDialog,
        onOpenSoundDialog = onOpenSoundDialog,
        bottomControls = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isAllDone) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(WarmIvory)
                            .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val activePercent = if (isDragging) {
                            (hypot(dragDelta.x, dragDelta.y) / 220f).coerceIn(0.1f, 1f)
                        } else gaugePower

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "퍼팅 힘 게이지 (초록 구간이 홀컵 적정 거리)",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSecondary
                            )
                            Text(
                                text = "${(activePercent * 100).roundToInt()}%",
                                style = MaterialTheme.typography.labelLarge,
                                color = if (activePercent > 0.84f) TerracottaWood else PineDeep
                            )
                        }
                        Spacer(modifier = Modifier.height(5.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(PureCardWhite)
                                .border(1.dp, SubtleDivider, RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.78f)
                                    .height(12.dp)
                                    .padding(start = 150.dp)
                                    .background(MeadowGreen.copy(alpha = 0.22f))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(activePercent)
                                    .height(12.dp)
                                    .background(if (activePercent > 0.84f) TerracottaWood else MeadowGreen)
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { launchFromGauge() },
                            enabled = !isRolling,
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            val pName = players.getOrNull(currentTurn)?.name ?: ""
                            Text(
                                text = "${pName} 지금 퍼팅 스트로크",
                                style = MaterialTheme.typography.titleMedium,
                                color = PureCardWhite
                            )
                        }
                        OutlinedButton(
                            onClick = { resetMatch() },
                            enabled = !isRolling,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("초기화", color = InkPrimary)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { resetMatch() },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Text("새 퍼팅 대결 시작", style = MaterialTheme.typography.titleMedium, color = PureCardWhite)
                        }
                        Button(
                            onClick = { showResultModal = true },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = OakWood),
                            modifier = Modifier.height(52.dp)
                        ) {
                            Text("순위표 보기", color = PureCardWhite)
                        }
                    }
                }
            }
        }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFCFE4C9))
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .clipToBounds()
                    .pointerInput(isRolling, isAllDone, currentTurn) {
                        if (isRolling || isAllDone) return@pointerInput
                        detectDragGestures(
                            onDragStart = {
                                isDragging = true
                                dragDelta = Offset.Zero
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragDelta += dragAmount
                            },
                            onDragEnd = {
                                isDragging = false
                                val pullMag = hypot(dragDelta.x, dragDelta.y)
                                if (pullMag > 18f) {
                                    val power = (pullMag / 220f).coerceIn(0.18f, 1.05f)
                                    val dirX = if (dragDelta.y > 0f) -dragDelta.x else dragDelta.x
                                    val vx = (dirX / 260f).coerceIn(-0.45f, 0.45f)
                                    val vy = -(0.50f + power * 0.94f)
                                    dragDelta = Offset.Zero
                                    launchWithVelocity(vx, vy)
                                } else {
                                    dragDelta = Offset.Zero
                                }
                            },
                            onDragCancel = {
                                isDragging = false
                                dragDelta = Offset.Zero
                            }
                        )
                    }
            ) {
                val w = size.width
                val h = size.height

                drawRect(
                    color = SkyPondBlue,
                    topLeft = Offset(0f, 0f),
                    size = Size(w, h * pondEdgeY)
                )
                drawRect(
                    color = SkyPondSoft.copy(alpha = 0.45f),
                    topLeft = Offset(0f, h * (pondEdgeY - 0.02f)),
                    size = Size(w, h * 0.025f)
                )
                drawOval(
                    color = CloverLeaf,
                    topLeft = Offset(w * 0.14f, h * 0.04f),
                    size = Size(28.dp.toPx(), 16.dp.toPx())
                )
                drawOval(
                    color = CloverLeaf,
                    topLeft = Offset(w * 0.76f, h * 0.06f),
                    size = Size(24.dp.toPx(), 14.dp.toPx())
                )
                val hazardLabel = textMeasurer.measure(
                    text = "연못 워터 해저드 (빠지면 즉시 꼴등 후보)",
                    style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PureCardWhite)
                )
                drawText(
                    hazardLabel,
                    topLeft = Offset(w * 0.5f - hazardLabel.size.width * 0.5f, h * 0.045f)
                )

                drawOval(
                    color = Color(0xFF9CCB95),
                    topLeft = Offset(w * 0.06f, h * 0.155f),
                    size = Size(w * 0.88f, h * 0.78f)
                )
                val cupCenter = Offset(w * holeX, h * holeY)
                for (ring in listOf(0.08f, 0.16f, 0.25f)) {
                    drawCircle(
                        color = PureCardWhite.copy(alpha = 0.35f),
                        radius = w * ring,
                        center = cupCenter,
                        style = Stroke(
                            width = 1.2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
                        )
                    )
                }

                drawCircle(color = InkPrimary, radius = 11.dp.toPx(), center = cupCenter)
                drawCircle(
                    color = LightBirch,
                    radius = 11.dp.toPx(),
                    center = cupCenter,
                    style = Stroke(width = 1.8.dp.toPx())
                )
                drawLine(
                    color = OakWood,
                    start = cupCenter,
                    end = Offset(cupCenter.x, cupCenter.y - 46.dp.toPx()),
                    strokeWidth = 2.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
                val flagPath = Path().apply {
                    moveTo(cupCenter.x, cupCenter.y - 46.dp.toPx())
                    lineTo(cupCenter.x + 26.dp.toPx(), cupCenter.y - 37.dp.toPx())
                    lineTo(cupCenter.x, cupCenter.y - 28.dp.toPx())
                    close()
                }
                drawPath(flagPath, TerracottaWood)

                records.forEach { rec ->
                    if (rec.playerIdx in players.indices) {
                        val pl = players[rec.playerIdx]
                        val px = w * rec.finalX
                        val py = h * rec.finalY
                        drawCircle(color = pl.badgeColor, radius = 8.5.dp.toPx(), center = Offset(px, py))
                        drawCircle(color = PureCardWhite, radius = 5.5.dp.toPx(), center = Offset(px, py))
                        val tag = textMeasurer.measure(
                            text = if (rec.inWater) "${pl.name}(풍덩)" else "${pl.name}(${rec.distanceCm}cm)",
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
                        )
                        drawRoundRect(
                            color = PureCardWhite.copy(alpha = 0.88f),
                            topLeft = Offset(px - tag.size.width * 0.5f - 4.dp.toPx(), py + 10.dp.toPx()),
                            size = Size(tag.size.width + 8.dp.toPx(), tag.size.height + 4.dp.toPx()),
                            cornerRadius = CornerRadius(5.dp.toPx())
                        )
                        drawText(
                            tag,
                            topLeft = Offset(px - tag.size.width * 0.5f, py + 12.dp.toPx())
                        )
                    }
                }

                if (!isAllDone) {
                    val curCenter = Offset(w * ballX, h * ballY)
                    if (isDragging && hypot(dragDelta.x, dragDelta.y) > 15f) {
                        val aimX = (curCenter.x - dragDelta.x * 0.7f).coerceIn(w * 0.1f, w * 0.9f)
                        val aimY = (curCenter.y - abs(dragDelta.y) * 1.1f).coerceAtLeast(h * 0.08f)
                        drawLine(
                            color = SunlightAmber,
                            start = curCenter,
                            end = Offset(aimX, aimY),
                            strokeWidth = 3.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                        )
                    }

                    drawCircle(
                        color = Color.Black.copy(alpha = 0.18f),
                        radius = 10.dp.toPx(),
                        center = Offset(curCenter.x + 2.dp.toPx(), curCenter.y + 3.dp.toPx())
                    )
                    val curColor = players.getOrNull(currentTurn)?.badgeColor ?: MeadowGreen
                    drawCircle(color = curColor, radius = 10.5.dp.toPx(), center = curCenter)
                    drawCircle(color = PureCardWhite, radius = 8.dp.toPx(), center = curCenter)

                    if (!isRolling) {
                        val pullOffset = if (isDragging) (hypot(dragDelta.x, dragDelta.y) * 0.18f).coerceAtMost(28f) else 0f
                        drawRoundRect(
                            color = OakWood,
                            topLeft = Offset(curCenter.x - 20.dp.toPx(), curCenter.y + 14.dp.toPx() + pullOffset),
                            size = Size(40.dp.toPx(), 9.dp.toPx()),
                            cornerRadius = CornerRadius(4.dp.toPx())
                        )
                    }
                }
            }

            if (!isAllDone && currentTurn in players.indices) {
                val curP = players[currentTurn]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PureCardWhite.copy(alpha = 0.92f))
                        .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Image(
                        painter = painterResource(id = curP.avatarRes),
                        contentDescription = curP.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .border(1.2.dp, curP.badgeColor, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "${currentTurn + 1}/${count}번째 주자: ${curP.name} (공을 당기거나 버튼 터치)",
                        style = MaterialTheme.typography.labelLarge,
                        color = InkPrimary
                    )
                }
            }
        }
    }

    if (showResultModal && resultRows.isNotEmpty()) {
        GameResultDialog(
            title = "니어핀 퍼팅 대결 결과",
            subtitle = "홀컵에서 가장 멀거나 해저드에 빠진 선수가 오늘의 커피 주인공입니다.",
            rows = resultRows,
            onReplay = {
                showResultModal = false
                resetMatch()
            },
            onCloseToHome = {
                showResultModal = false
                onBack()
            },
            onDismiss = { showResultModal = false }
        )
    }
}
