package com.jjalddalmark.minigamerullet.ui.games

import android.graphics.Paint
import android.graphics.Typeface
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.data.RouletteStorageRepository
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.BuiltInRouletteTemplates
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.PlannedRouletteSpin
import com.jjalddalmark.minigamerullet.model.RouletteColorPalette
import com.jjalddalmark.minigamerullet.model.RouletteEngine
import com.jjalddalmark.minigamerullet.model.RouletteHistoryRecord
import com.jjalddalmark.minigamerullet.model.RouletteSliceArc
import com.jjalddalmark.minigamerullet.model.RouletteSliceItem
import com.jjalddalmark.minigamerullet.model.RouletteSpecialEffect
import com.jjalddalmark.minigamerullet.model.RouletteSpinResult
import com.jjalddalmark.minigamerullet.model.RouletteSpinStage
import com.jjalddalmark.minigamerullet.model.RouletteWheelPreset
import com.jjalddalmark.minigamerullet.model.SpinTrajectorySample
import com.jjalddalmark.minigamerullet.share.RouletteExportVisualStyle
import com.jjalddalmark.minigamerullet.ui.components.GameStageScaffold
import com.jjalddalmark.minigamerullet.ui.components.StageCinemaBackdrop
import com.jjalddalmark.minigamerullet.ui.theme.InkMuted
import com.jjalddalmark.minigamerullet.ui.theme.InkPrimary
import com.jjalddalmark.minigamerullet.ui.theme.InkSecondary
import com.jjalddalmark.minigamerullet.ui.theme.LightBirch
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreen
import com.jjalddalmark.minigamerullet.ui.theme.MeadowGreenSoft
import com.jjalddalmark.minigamerullet.ui.theme.OakWood
import com.jjalddalmark.minigamerullet.ui.theme.PineDeep
import com.jjalddalmark.minigamerullet.ui.theme.PureCardWhite
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondBlue
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondSoft
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class WindmillSpinPhase {
    IDLE,
    FREE_SPIN_WAIT_TAP,
    PLANNED_SPINNING,
    FINISHED
}

/**
 * 커피 · 꼴등 복불복 게임: 언덕 위 풍차 룰렛 (만능 결정 룰렛+ 전 기능 100% 이식 + 지브리 언덕 원목 풍차 UI)
 * - 출전 선수(선수입장) 자동 동기화 및 1탭 선수 채우기
 * - 무제한 맞춤 항목, 가중치(1~20배) 확률, 일괄 입력, 1~N 숫자 생성, 비밀 당첨 메모, 비밀번호 잠금
 * - 12종 반전 특수 효과 & 발동 빈도, 회전 시간(3~20초), 탭하여 직접 멈추기, 당첨 제외, ? 바람 장막 모드
 * - 독립 풍차 보관함 영구 저장 · 재실행 · 복제 · 복원 및 추첨 통계 기록
 * - 언덕 위 풍차 일러스트가 담긴 고해상도 결과 이미지(JPEG) + 셋팅~회전~결정 전 과정 영상(MP4) 메신저 공유
 */
@Composable
fun WindmillRouletteGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    showIntroCutscene: Boolean = false,
    onIntroCutsceneFinished: () -> Unit = {},
    onToggleBgmMute: () -> Unit = {},
    onBack: () -> Unit,
    onOpenPlayerDialog: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val playerNames = remember(settings.players) {
        BuiltInRouletteTemplates.normalizeWindmillPlayerNames(settings.players.map { it.name })
    }

    val storageRepo = remember(context) {
        RouletteStorageRepository(
            context = context,
            storageNamespace = "windmill",
            defaultTemplatesProvider = {
                BuiltInRouletteTemplates.defaultWindmillTemplates(
                    playerNames = playerNames,
                    defaultDurationSec = settings.normalizedDurationSeconds
                )
            }
        )
    }

    val fallbackPreset = remember {
        BuiltInRouletteTemplates.defaultWindmillTemplates(
            playerNames = playerNames,
            defaultDurationSec = settings.normalizedDurationSeconds
        ).first()
    }

    var activePreset by remember { mutableStateOf(fallbackPreset) }
    var savedPresets by remember { mutableStateOf<List<RouletteWheelPreset>>(emptyList()) }
    var historyRecords by remember { mutableStateOf<List<RouletteHistoryRecord>>(emptyList()) }
    var isVaultLoaded by remember { mutableStateOf(false) }

    // 회전 및 풍차 애니메이션 상태
    var phase by remember { mutableStateOf(WindmillSpinPhase.IDLE) }
    var wheelRotationDeg by remember { mutableFloatStateOf(0f) }
    var flapperTiltDeg by remember { mutableFloatStateOf(0f) }
    var liveStatusBanner by remember { mutableStateOf("풍차 바퀴 또는 아래 버튼을 눌러 힘껏 돌려보세요") }
    var isSlowMotion by remember { mutableStateOf(false) }
    var isBlackOut by remember { mutableStateOf(false) }
    var showCelebration by remember { mutableStateOf(false) }
    var impactBurstKey by remember { mutableIntStateOf(0) }

    var activeSpinPlan by remember { mutableStateOf<PlannedRouletteSpin?>(null) }
    var spinPresetSnapshot by remember { mutableStateOf<RouletteWheelPreset?>(null) }
    val recordedSamples = remember { mutableListOf<SpinTrajectorySample>() }
    var spinRunId by remember { mutableIntStateOf(0) }
    var lastSpinResult by remember { mutableStateOf<RouletteSpinResult?>(null) }

    // 모달 상태
    var showEditorDialog by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showResultShareDialog by remember { mutableStateOf(false) }

    // 4자리 비밀번호 잠금 해제 대기 액션
    var pendingPinCheck by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    // 초기 보관함 로드 및 선수입장(settings.players) 변경 시 선수 연동형 풍차 프리셋 자동 갱신
    LaunchedEffect(storageRepo, playerNames) {
        if (!isVaultLoaded) {
            storageRepo.loadInitialSnapshot()
        }
        val syncedSnapshot = storageRepo.syncPlayerPresetsInVault(playerNames)
        savedPresets = syncedSnapshot.savedPresets
        historyRecords = syncedSnapshot.historyRecords
        if (!isVaultLoaded) {
            activePreset = syncedSnapshot.activePreset
            isVaultLoaded = true
        } else if (
            phase != WindmillSpinPhase.FREE_SPIN_WAIT_TAP &&
            phase != WindmillSpinPhase.PLANNED_SPINNING &&
            BuiltInRouletteTemplates.isPlayerSyncedWindmillPreset(activePreset)
        ) {
            val updatedActive = BuiltInRouletteTemplates.syncPlayerDrivenPreset(activePreset, playerNames)
            activePreset = updatedActive
            storageRepo.saveActivePreset(updatedActive)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    val arcs = remember(activePreset.items) {
        RouletteEngine.computeSliceArcs(activePreset.activeItems)
    }

    val currentPointerSliceIndex = remember(arcs, wheelRotationDeg) {
        RouletteEngine.resolveSliceIndexAtRotation(arcs, wheelRotationDeg)
    }
    val currentPointerLabel = arcs.getOrNull(currentPointerSliceIndex)?.item?.label.orEmpty()

    fun updateActivePresetAndPersist(updated: RouletteWheelPreset, resetStage: Boolean = false) {
        activePreset = updated
        if (resetStage) {
            phase = WindmillSpinPhase.IDLE
            isSlowMotion = false
            isBlackOut = false
            showCelebration = false
            soundEngine.setClimaxDucking(false)
            liveStatusBanner = "언덕 위 풍차 준비 완료 · 후보 ${updated.activeItems.size}개"
        }
        scope.launch {
            storageRepo.saveActivePreset(updated)
        }
    }

    fun requestProtectedAction(preset: RouletteWheelPreset, actionTitle: String, onUnlocked: () -> Unit) {
        if (preset.isLocked) {
            pendingPinCheck = actionTitle to onUnlocked
        } else {
            onUnlocked()
        }
    }

    fun triggerSpinStart(initialVelocityBoost: Boolean = false) {
        if (phase == WindmillSpinPhase.FREE_SPIN_WAIT_TAP || phase == WindmillSpinPhase.PLANNED_SPINNING) {
            return
        }
        var workingPreset = activePreset
        if (workingPreset.removeOnceChosen && lastSpinResult != null && phase == WindmillSpinPhase.FINISHED) {
            val prevWinnerId = lastSpinResult!!.winnerItem.id
            val candidateExcluded = workingPreset.items.map { item ->
                if (item.id == prevWinnerId) item.copy(isExcluded = true) else item
            }
            val remainingCount = candidateExcluded.count { !it.isExcluded && it.label.isNotBlank() }
            workingPreset = if (remainingCount >= 2) {
                workingPreset.copy(items = candidateExcluded)
            } else {
                Toast.makeText(
                    context,
                    "모든 후보가 한 번씩 선택되어 전체 항목을 다시 복원합니다.",
                    Toast.LENGTH_SHORT
                ).show()
                workingPreset.copy(items = workingPreset.items.map { it.copy(isExcluded = false) })
            }
            activePreset = workingPreset
            scope.launch { storageRepo.saveActivePreset(workingPreset) }
        }

        val arcsNow = RouletteEngine.computeSliceArcs(workingPreset.activeItems)
        if (arcsNow.size < 2) {
            Toast.makeText(context, "최소 2개 이상의 항목을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return
        }

        recordedSamples.clear()
        spinPresetSnapshot = workingPreset
        lastSpinResult = null
        showCelebration = false
        showResultShareDialog = false
        isSlowMotion = false
        isBlackOut = false
        soundEngine.setClimaxDucking(false)
        soundEngine.playWindmillStart()
        if (initialVelocityBoost) {
            soundEngine.playStickSlide(0)
        }

        if (workingPreset.tapToStop) {
            activeSpinPlan = null
            phase = WindmillSpinPhase.FREE_SPIN_WAIT_TAP
            liveStatusBanner = "언덕 바람을 타고 회전 중! 멈추고 싶을 때 터치하세요"
            spinRunId++
        } else {
            val plan = RouletteEngine.buildSpinPlan(
                preset = workingPreset,
                initialRotationDeg = wheelRotationDeg
            )
            activeSpinPlan = plan
            phase = WindmillSpinPhase.PLANNED_SPINNING
            liveStatusBanner = "언덕 위 원목 풍차 회전 시작!"
            spinRunId++
        }
    }

    fun triggerTapStopNow() {
        if (phase != WindmillSpinPhase.FREE_SPIN_WAIT_TAP) return
        val snapshot = spinPresetSnapshot ?: activePreset
        soundEngine.playWoodTick(highPitch = true)
        val stopPlan = RouletteEngine.buildSpinPlan(
            preset = snapshot,
            initialRotationDeg = wheelRotationDeg,
            durationOverrideSec = 2.8f,
            extraSpinsOverride = 4
        )
        activeSpinPlan = stopPlan
        phase = WindmillSpinPhase.PLANNED_SPINNING
        spinRunId++
    }

    // 1. 탭하여 중지 대기(등속 회전) 루프 (spinRunId 단독 키로 조기 코루틴 취소 차단)
    LaunchedEffect(spinRunId) {
        if (spinRunId == 0 || phase != WindmillSpinPhase.FREE_SPIN_WAIT_TAP) return@LaunchedEffect
        val snapshot = spinPresetSnapshot ?: activePreset
        val spinArcs = RouletteEngine.computeSliceArcs(snapshot.activeItems)
        if (spinArcs.size < 2) return@LaunchedEffect

        var prevNanos = 0L
        var elapsed = 0f
        var prevSlice = RouletteEngine.resolveSliceIndexAtRotation(spinArcs, wheelRotationDeg)
        var lastSampleRecordedAt = -1f

        recordedSamples.add(
            SpinTrajectorySample(
                elapsedSec = 0f,
                wheelRotationDeg = wheelRotationDeg,
                flapperTiltDeg = 0f,
                stage = RouletteSpinStage.SPINNING,
                statusBanner = "언덕 풍차 고속 회전 중 · 탭하여 중지 대기"
            )
        )

        while (phase == WindmillSpinPhase.FREE_SPIN_WAIT_TAP) {
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    val dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsed += dt
                    wheelRotationDeg += dt * 920f

                    val curSlice = RouletteEngine.resolveSliceIndexAtRotation(spinArcs, wheelRotationDeg)
                    if (curSlice != prevSlice) {
                        prevSlice = curSlice
                        flapperTiltDeg = -25f
                        soundEngine.playRoulettePegTick(highPitch = false)
                    } else {
                        flapperTiltDeg *= 0.80f
                    }

                    if (elapsed - lastSampleRecordedAt >= 0.033f) {
                        lastSampleRecordedAt = elapsed
                        if (recordedSamples.size > 540) {
                            recordedSamples.removeAt(0)
                        }
                        recordedSamples.add(
                            SpinTrajectorySample(
                                elapsedSec = elapsed,
                                wheelRotationDeg = wheelRotationDeg,
                                flapperTiltDeg = flapperTiltDeg,
                                stage = RouletteSpinStage.SPINNING,
                                statusBanner = "언덕 풍차 회전 중 · 탭하여 중지"
                            )
                        )
                    }
                }
                prevNanos = now
            }
        }
    }

    // 2. 결정론적 감속 · 슬로우모션 · 12종 반전 특수효과 · 최종 당첨 확정 루프 (spinRunId 단독 키)
    LaunchedEffect(spinRunId) {
        if (spinRunId == 0 || phase != WindmillSpinPhase.PLANNED_SPINNING) return@LaunchedEffect
        val plan = activeSpinPlan ?: return@LaunchedEffect
        val snapshot = spinPresetSnapshot ?: activePreset
        val timeOffset = if (recordedSamples.isNotEmpty()) recordedSamples.last().elapsedSec else 0f

        var prevNanos = 0L
        var localElapsed = 0f
        var prevSlice = RouletteEngine.resolveSliceIndexAtRotation(plan.arcs, plan.initialRotationDeg)
        var slowMoEntered = false
        var twistSoundPlayed = false
        var heartbeatAccum = 0f
        var lastSampleTime = -1f

        while (localElapsed < plan.totalDurationSec) {
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    val dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    localElapsed = (localElapsed + dt).coerceAtMost(plan.totalDurationSec)
                    val inst = plan.evaluateAt(localElapsed)

                    wheelRotationDeg = inst.wheelRotationDeg
                    isSlowMotion = inst.isSlowMotion && settings.slowMotionEnabled
                    isBlackOut = inst.isBlackOut
                    liveStatusBanner = inst.banner

                    if (inst.isSlowMotion && !slowMoEntered) {
                        slowMoEntered = true
                        soundEngine.setClimaxDucking(true)
                        if (settings.slowMotionEnabled) {
                            soundEngine.playSlowMoEnter()
                        }
                    }

                    if (inst.stage == RouletteSpinStage.SPECIAL_TWIST && !twistSoundPlayed) {
                        twistSoundPlayed = true
                        soundEngine.playRouletteSpecialTwist()
                    }

                    val curSlice = RouletteEngine.resolveSliceIndexAtRotation(plan.arcs, wheelRotationDeg)
                    if (curSlice != prevSlice) {
                        prevSlice = curSlice
                        flapperTiltDeg = -25f
                        soundEngine.playRoulettePegTick(highPitch = inst.isSlowMotion)
                    } else {
                        flapperTiltDeg *= 0.80f
                    }

                    if (inst.isSlowMotion) {
                        heartbeatAccum += dt
                        if (heartbeatAccum >= 0.62f) {
                            heartbeatAccum = 0f
                            soundEngine.playHeartbeat(intense = inst.stage == RouletteSpinStage.SPECIAL_TWIST)
                        }
                    }

                    if (localElapsed - lastSampleTime >= 0.032f || localElapsed >= plan.totalDurationSec) {
                        lastSampleTime = localElapsed
                        recordedSamples.add(
                            SpinTrajectorySample(
                                elapsedSec = timeOffset + localElapsed,
                                wheelRotationDeg = wheelRotationDeg,
                                flapperTiltDeg = flapperTiltDeg,
                                stage = inst.stage,
                                isSlowMotion = inst.isSlowMotion,
                                isBlackOut = inst.isBlackOut,
                                statusBanner = inst.banner
                            )
                        )
                    }
                }
                prevNanos = now
            }
        }

        // 최종 정착 및 결과 확정
        wheelRotationDeg = plan.finalTargetRotationDeg
        flapperTiltDeg = 0f
        isSlowMotion = false
        isBlackOut = false
        soundEngine.setClimaxDucking(false)

        val winnerArc = plan.arcs[plan.finalArcIndex]
        val finalResult = RouletteSpinResult(
            id = UUID.randomUUID().toString(),
            presetSnapshot = snapshot,
            arcs = plan.arcs,
            primaryArcIndex = plan.primaryArcIndex,
            winnerArcIndex = plan.finalArcIndex,
            winnerItem = winnerArc.item,
            winnerProbabilityPercent = winnerArc.probabilityPercent,
            appliedSpecialEffect = plan.appliedEffect,
            finalWheelRotationDeg = plan.finalTargetRotationDeg,
            trajectory = recordedSamples.toList(),
            finishedAtMillis = System.currentTimeMillis()
        )

        lastSpinResult = finalResult
        liveStatusBanner = "최종 당첨: ${winnerArc.item.label} (${winnerArc.probabilityPercent.roundToInt()}%)"
        phase = WindmillSpinPhase.FINISHED
        showCelebration = true
        impactBurstKey++
        showResultShareDialog = true
        soundEngine.playRouletteDecisionSound(snapshot.decisionSound)

        val updatedPreset = activePreset.copy(playCount = activePreset.playCount + 1)
        activePreset = updatedPreset

        val historyEntry = RouletteHistoryRecord(
            wheelTitle = snapshot.title,
            winnerLabel = winnerArc.item.label,
            winnerColorArgb = winnerArc.item.colorArgb,
            probabilityPercent = winnerArc.probabilityPercent,
            specialEffectTitle = plan.appliedEffect.title,
            secretNote = winnerArc.item.secretNote,
            createdAtMillis = finalResult.finishedAtMillis
        )
        historyRecords = withContext(NonCancellable) {
            storageRepo.appendHistoryRecord(historyEntry, updatedPreset)
        }
    }

    val isBusySpinning =
        phase == WindmillSpinPhase.FREE_SPIN_WAIT_TAP || phase == WindmillSpinPhase.PLANNED_SPINNING
    val excludedCount = activePreset.items.count { it.isExcluded }
    val durationTag = if (activePreset.tapToStop) "탭 직접중지" else "${activePreset.normalizedDurationSec}초"

    GameStageScaffold(
        gameType = GolfGameType.WINDMILL_ROULETTE,
        settings = settings,
        isSlowMotion = isSlowMotion,
        slowMotionLabel = if (isBlackOut) {
            "구름 그림자 정전 발동 · 누구에게 멈출까요"
        } else {
            "풍차 톱니 감속 중 · 오늘의 주인공 결정 직전"
        },
        showCelebration = showCelebration,
        isActionActive = isBusySpinning,
        impactBurstKey = impactBurstKey,
        showIntroCutscene = showIntroCutscene,
        onIntroCutsceneFinished = onIntroCutsceneFinished,
        onToggleBgmMute = onToggleBgmMute,
        onBack = {
            if (!isBusySpinning) {
                onBack()
            } else {
                Toast.makeText(context, "풍차 회전이 멈춘 뒤 이동할 수 있습니다.", Toast.LENGTH_SHORT).show()
            }
        },
        onOpenPlayerDialog = {
            if (!isBusySpinning) {
                onOpenPlayerDialog()
            }
        },
        onOpenSoundDialog = onOpenSoundDialog,
        subtitleOverride = "${activePreset.title} · 후보 ${arcs.size}개 · $durationTag",
        customSubHeader = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                // 1단: 풍차 보관함 · 현재 저장 · 새 풍차 · 출전 선수 채우기 · 추첨 기록
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    QuickToolChip(
                        text = "풍차 보관함 (${savedPresets.size})",
                        emphasized = true,
                        enabled = !isBusySpinning,
                        onClick = {
                            soundEngine.playWoodTick(true)
                            showVaultDialog = true
                        }
                    )
                    QuickToolChip(
                        text = "현재 풍차 저장",
                        emphasized = false,
                        enabled = !isBusySpinning,
                        onClick = {
                            scope.launch {
                                val list = storageRepo.upsertSavedPreset(activePreset, saveAsNewCopy = false)
                                savedPresets = list
                                soundEngine.playStickSlide(1)
                                Toast.makeText(
                                    context,
                                    "'${activePreset.title}' 풍차 설정을 보관함에 저장했습니다.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    QuickToolChip(
                        text = "+ 새 풍차 만들기",
                        emphasized = false,
                        enabled = !isBusySpinning,
                        onClick = {
                            val blankPreset = RouletteWheelPreset(
                                id = UUID.randomUUID().toString(),
                                title = "언덕 위 맞춤 풍차",
                                items = listOf(
                                    RouletteSliceItem(
                                        label = playerNames.getOrElse(0) { "항목 1" },
                                        weight = 1,
                                        colorArgb = RouletteColorPalette.HILLSIDE_WINDMILL.colorAt(0)
                                    ),
                                    RouletteSliceItem(
                                        label = playerNames.getOrElse(1) { "항목 2" },
                                        weight = 1,
                                        colorArgb = RouletteColorPalette.HILLSIDE_WINDMILL.colorAt(1)
                                    ),
                                    RouletteSliceItem(
                                        label = playerNames.getOrElse(2) { "항목 3" },
                                        weight = 1,
                                        colorArgb = RouletteColorPalette.HILLSIDE_WINDMILL.colorAt(2)
                                    ),
                                    RouletteSliceItem(
                                        label = playerNames.getOrElse(3) { "항목 4" },
                                        weight = 1,
                                        colorArgb = RouletteColorPalette.HILLSIDE_WINDMILL.colorAt(3)
                                    )
                                ),
                                spinDurationSec = settings.normalizedDurationSeconds,
                                palette = RouletteColorPalette.HILLSIDE_WINDMILL
                            )
                            updateActivePresetAndPersist(blankPreset, resetStage = true)
                            showEditorDialog = true
                            soundEngine.playWoodTick(true)
                        }
                    )
                    QuickToolChip(
                        text = "선수 채우기 (${playerNames.size}명)",
                        emphasized = BuiltInRouletteTemplates.isPlayerSyncedWindmillPreset(activePreset),
                        enabled = !isBusySpinning,
                        onClick = {
                            val syncedItems = BuiltInRouletteTemplates.buildPlayerSliceItems(
                                playerNames = playerNames,
                                palette = activePreset.palette,
                                existingItems = activePreset.items,
                                presetIdStem = activePreset.id
                            )
                            val updated = activePreset.copy(
                                items = syncedItems,
                                updatedAtMillis = System.currentTimeMillis()
                            )
                            updateActivePresetAndPersist(updated, resetStage = true)
                            soundEngine.playStickCaseShuffle()
                            Toast.makeText(
                                context,
                                "현재 출전 선수 ${playerNames.size}명으로 풍차 날개 칸을 채웠습니다.",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    )
                    QuickToolChip(
                        text = "기록 (${historyRecords.size})",
                        emphasized = false,
                        enabled = !isBusySpinning,
                        onClick = {
                            soundEngine.playWoodTick(false)
                            showHistoryDialog = true
                        }
                    )
                }

                // 2단: 저장된 풍차 프리셋 원터치 전환 바 (그늘집 커피·간식·캐디피·뒤풀이·경품·맞춤 풍차)
                if (savedPresets.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        savedPresets.forEach { preset ->
                            val selected = preset.id == activePreset.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) TerracottaWood else PureCardWhite)
                                    .border(
                                        1.dp,
                                        if (selected) TerracottaWood else SubtleDivider,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isBusySpinning) {
                                        soundEngine.playWoodTick(true)
                                        updateActivePresetAndPersist(preset, resetStage = true)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (preset.isLocked) "${preset.title} [잠금]" else preset.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (selected) PureCardWhite else InkSecondary,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomControls = {
            Column(
                verticalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                // 상단 빠른 조작 스트립: 회전 시간(3·5·7·12초·직접중지) + 제외 복원 + 직전 결과 이미지·영상 공유
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    listOf(3, 5, 7, 12).forEach { sec ->
                        val isSel = !activePreset.tapToStop && activePreset.normalizedDurationSec == sec
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSel) MeadowGreenSoft else PureCardWhite)
                                .border(
                                    1.dp,
                                    if (isSel) MeadowGreen else SubtleDivider,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable(enabled = !isBusySpinning) {
                                    soundEngine.playWoodTick(true)
                                    updateActivePresetAndPersist(
                                        activePreset.copy(tapToStop = false, spinDurationSec = sec)
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "${sec}초",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSel) PineDeep else InkSecondary
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (activePreset.tapToStop) SunlightSoft else PureCardWhite)
                            .border(
                                1.dp,
                                if (activePreset.tapToStop) SunlightAmber else SubtleDivider,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable(enabled = !isBusySpinning) {
                                soundEngine.playWoodTick(true)
                                updateActivePresetAndPersist(
                                    activePreset.copy(tapToStop = !activePreset.tapToStop)
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "직접중지",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (activePreset.tapToStop) OakWood else InkSecondary
                        )
                    }

                    if (excludedCount > 0 && !isBusySpinning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmIvory)
                                .border(1.dp, OakWood.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val restored = activePreset.copy(
                                        items = activePreset.items.map { it.copy(isExcluded = false) }
                                    )
                                    updateActivePresetAndPersist(restored, resetStage = true)
                                    soundEngine.playStickCaseShuffle()
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "제외 ${excludedCount}개 복원",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OakWood
                            )
                        }
                    }

                    if (lastSpinResult != null && !isBusySpinning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(TerracottaSoft)
                                .border(1.dp, TerracottaWood, RoundedCornerShape(8.dp))
                                .clickable {
                                    soundEngine.playWoodTick(true)
                                    showResultShareDialog = true
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "결과 · 사진·영상 공유",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = TerracottaWood
                            )
                        }
                    }
                }

                // 하단 메인 듀얼 액션 버튼
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isBusySpinning) {
                                requestProtectedAction(activePreset, "풍차 항목 · 옵션 잠금 해제") {
                                    showEditorDialog = true
                                }
                            }
                        },
                        enabled = !isBusySpinning,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.2.dp, SubtleDivider),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = PureCardWhite),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        modifier = Modifier
                            .weight(0.38f)
                            .height(50.dp)
                    ) {
                        Text(
                            text = if (activePreset.isLocked) "풍차 설정 (잠금)" else "풍차 항목 · 옵션",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBusySpinning) InkMuted else InkPrimary,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            if (phase == WindmillSpinPhase.FREE_SPIN_WAIT_TAP) {
                                triggerTapStopNow()
                            } else if (!isBusySpinning) {
                                triggerSpinStart(initialVelocityBoost = false)
                            }
                        },
                        enabled = phase != WindmillSpinPhase.PLANNED_SPINNING,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (phase) {
                                WindmillSpinPhase.FREE_SPIN_WAIT_TAP -> MeadowGreen
                                else -> TerracottaWood
                            },
                            contentColor = PureCardWhite
                        ),
                        modifier = Modifier
                            .weight(0.62f)
                            .height(50.dp)
                    ) {
                        val mainBtnLabel = when (phase) {
                            WindmillSpinPhase.FREE_SPIN_WAIT_TAP -> "탭하여 풍차 멈추기!"
                            WindmillSpinPhase.PLANNED_SPINNING -> "풍차 바퀴 회전 중..."
                            WindmillSpinPhase.FINISHED -> "다시 풍차 돌리기"
                            WindmillSpinPhase.IDLE -> "힘껏 풍차 돌리기"
                        }
                        Text(
                            text = mainBtnLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 목가적 페어웨이 초원 시네마 배경 일러스트
            StageCinemaBackdrop(
                gameType = GolfGameType.WINDMILL_ROULETTE,
                videoEffectsEnabled = settings.videoEffectsEnabled,
                active = isBusySpinning
            )

            // 언덕 능선 + 돌담 원목 풍차 탑 + 4장 풍차 날개 + 원목 룰렛 휠 캔버스
            HillsideWindmillWheelCanvas(
                arcs = arcs,
                wheelRotationDeg = wheelRotationDeg,
                flapperTiltDeg = flapperTiltDeg,
                mysteryMaskActive = activePreset.mysteryMaskOnWheel && phase != WindmillSpinPhase.FINISHED,
                showWeightPercent = activePreset.items.any { !it.isExcluded && it.safeWeight > 1 },
                highlightWinnerIndex = if (phase == WindmillSpinPhase.FINISHED) {
                    lastSpinResult?.winnerArcIndex ?: -1
                } else {
                    -1
                },
                isSlowMotion = isSlowMotion,
                isBlackOut = isBlackOut,
                hubState = when (phase) {
                    WindmillSpinPhase.IDLE -> WindmillHubDisplayState.IDLE
                    WindmillSpinPhase.FREE_SPIN_WAIT_TAP -> WindmillHubDisplayState.FREE_SPIN_WAIT_TAP
                    WindmillSpinPhase.PLANNED_SPINNING -> WindmillHubDisplayState.PLANNED_SPINNING
                    WindmillSpinPhase.FINISHED -> WindmillHubDisplayState.FINISHED
                },
                onSwipeSpinStart = {
                    if (!isBusySpinning) {
                        triggerSpinStart(initialVelocityBoost = true)
                    }
                },
                onCenterHubTap = {
                    when (phase) {
                        WindmillSpinPhase.IDLE, WindmillSpinPhase.FINISHED ->
                            triggerSpinStart(initialVelocityBoost = false)
                        WindmillSpinPhase.FREE_SPIN_WAIT_TAP ->
                            triggerTapStopNow()
                        WindmillSpinPhase.PLANNED_SPINNING -> Unit
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 44.dp)
            )

            // 상단 플로팅 원목 명패 배너 (현재 풍차 바늘 지목 항목 · 적용 중인 반전 옵션 안내)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        when {
                            isBlackOut -> Color(0xE61F2721)
                            phase == WindmillSpinPhase.FINISHED -> PureCardWhite.copy(alpha = 0.95f)
                            else -> PureCardWhite.copy(alpha = 0.90f)
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            isBlackOut -> SunlightAmber
                            phase == WindmillSpinPhase.FINISHED -> TerracottaWood
                            else -> OakWood.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val liveMask = activePreset.mysteryMaskOnWheel && phase != WindmillSpinPhase.FINISHED
                val bannerText = when {
                    isBlackOut -> "구름 그림자 정전 발동! 멈추는 순간 공개됩니다"
                    isBusySpinning -> {
                        val pointerTxt = if (liveMask) "?" else currentPointerLabel
                        "$liveStatusBanner · 바늘: $pointerTxt"
                    }
                    phase == WindmillSpinPhase.FINISHED -> liveStatusBanner
                    else -> buildString {
                        append("바늘 지목: ${if (liveMask) "?" else currentPointerLabel}")
                        if (activePreset.specialEffect != RouletteSpecialEffect.NONE) {
                            append(" · 반전(${activePreset.specialEffect.title})")
                        }
                        if (activePreset.removeOnceChosen) {
                            append(" · 당첨 제외")
                        }
                        if (activePreset.mysteryMaskOnWheel) {
                            append(" · ? 장막")
                        }
                    }
                }
                Text(
                    text = bannerText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = when {
                        isBlackOut -> SunlightSoft
                        phase == WindmillSpinPhase.FINISHED -> TerracottaWood
                        else -> InkPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // 1. 당첨 결과 확인 + 풍차 일러스트 결과 카드 이미지(JPEG) + 회전 전 과정 영상(MP4) 공유 모달
    if (showResultShareDialog && lastSpinResult != null) {
        val currentResult = lastSpinResult!!
        val companionExempt = remember(currentResult.id) {
            if (BuiltInRouletteTemplates.isPlayerSyncedWindmillPreset(currentResult.presetSnapshot)) {
                currentResult.arcs
                    .filterIndexed { idx, _ -> idx != currentResult.winnerArcIndex }
                    .map { it.item.label }
            } else {
                emptyList()
            }
        }
        RouletteResultShareDialog(
            result = currentResult,
            soundEnabled = settings.soundEnabled,
            soundEngine = soundEngine,
            visualStyle = RouletteExportVisualStyle.HILLSIDE_WINDMILL,
            companionExemptNames = companionExempt,
            onSpinAgain = {
                showResultShareDialog = false
                triggerSpinStart(initialVelocityBoost = false)
            },
            onDismiss = {
                showResultShareDialog = false
            }
        )
    }

    // 2. 언덕 위 풍차 항목 · 비율 · 12종 반전 효과 · 색상 · 사운드 · 잠금 종합 편집 모달
    if (showEditorDialog) {
        RouletteEditorDialog(
            initialPreset = activePreset,
            soundEngine = soundEngine,
            isWindmillTheme = true,
            windmillPlayerNames = playerNames,
            onApplyOnly = { edited ->
                updateActivePresetAndPersist(edited, resetStage = true)
                showEditorDialog = false
            },
            onSaveToVaultAndApply = { edited ->
                activePreset = edited
                phase = WindmillSpinPhase.IDLE
                showEditorDialog = false
                scope.launch {
                    val updatedVault = storageRepo.upsertSavedPreset(edited, saveAsNewCopy = false)
                    savedPresets = updatedVault
                    Toast.makeText(
                        context,
                        "'${edited.title}' 풍차 설정을 보관함에 저장했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDismiss = {
                showEditorDialog = false
            }
        )
    }

    // 3. 내 풍차 룰렛 보관함 모달
    if (showVaultDialog) {
        SavedRouletteVaultDialog(
            activePreset = activePreset,
            savedPresets = savedPresets,
            soundEngine = soundEngine,
            isWindmillTheme = true,
            onLoadPreset = { chosen ->
                updateActivePresetAndPersist(chosen, resetStage = true)
                showVaultDialog = false
                Toast.makeText(context, "'${chosen.title}' 풍차를 불러왔습니다.", Toast.LENGTH_SHORT).show()
            },
            onSaveCurrentOverwrite = {
                scope.launch {
                    val updatedList = storageRepo.upsertSavedPreset(activePreset, saveAsNewCopy = false)
                    savedPresets = updatedList
                    Toast.makeText(context, "현재 풍차 구성을 보관함에 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveCurrentAsNew = { customTitle ->
                val copyTarget = activePreset.copy(
                    title = customTitle.ifBlank { "${activePreset.title} 보관본" }.take(32)
                )
                scope.launch {
                    val updatedList = storageRepo.upsertSavedPreset(copyTarget, saveAsNewCopy = true)
                    savedPresets = updatedList
                    activePreset = updatedList.first()
                    Toast.makeText(
                        context,
                        "'${copyTarget.title}' 새 풍차로 저장했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onDuplicatePreset = { target ->
                scope.launch {
                    savedPresets = storageRepo.duplicateSavedPreset(target)
                }
            },
            onDeletePreset = { target ->
                requestProtectedAction(target, "'${target.title}' 삭제 잠금 해제") {
                    scope.launch {
                        val nextList = storageRepo.deleteSavedPreset(target.id)
                        savedPresets = nextList
                        if (activePreset.id == target.id) {
                            updateActivePresetAndPersist(nextList.first(), resetStage = true)
                        }
                    }
                }
            },
            onRestoreTemplates = {
                scope.launch {
                    savedPresets = storageRepo.restoreBuiltInTemplates()
                    Toast.makeText(context, "풍차 기본 템플릿 10종을 복원했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showVaultDialog = false
            }
        )
    }

    // 4. 최근 풍차 추첨 기록 및 당첨 통계 모달
    if (showHistoryDialog) {
        RouletteHistoryDialog(
            records = historyRecords,
            onClearAll = {
                scope.launch {
                    historyRecords = storageRepo.clearHistory()
                }
            },
            onDismiss = {
                showHistoryDialog = false
            }
        )
    }

    // 5. 4자리 비밀번호 확인 모달
    if (pendingPinCheck != null) {
        val (title, action) = pendingPinCheck!!
        PinUnlockDialog(
            title = title,
            expectedPin = activePreset.passcodePin,
            onUnlocked = {
                pendingPinCheck = null
                action()
            },
            onDismiss = {
                pendingPinCheck = null
            }
        )
    }
}

/**
 * 언덕 풍차 중앙 허브 표시 상태 (언덕 위 풍차 룰렛 & 만능 결정 룰렛+ 공용)
 */
internal enum class WindmillHubDisplayState {
    IDLE,
    FREE_SPIN_WAIT_TAP,
    PLANNED_SPINNING,
    FINISHED
}

/**
 * 지브리 목가적 언덕 능선 + 돌담 원목 풍차 오두막 탑 + 원판과 함께 도는 4장 원목 격자 날개 + 원목 룰렛 휠 캔버스
 */
@Composable
internal fun HillsideWindmillWheelCanvas(
    arcs: List<RouletteSliceArc>,
    wheelRotationDeg: Float,
    flapperTiltDeg: Float,
    mysteryMaskActive: Boolean,
    showWeightPercent: Boolean,
    highlightWinnerIndex: Int,
    isSlowMotion: Boolean,
    isBlackOut: Boolean,
    hubState: WindmillHubDisplayState,
    onSwipeSpinStart: () -> Unit,
    onCenterHubTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val labelPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }
    val subPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xEEFFFFFF.toInt()
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
    }
    val hubPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
    }

    var dragAccumulatedDeg by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier = modifier
            .pointerInput(hubState) {
                detectDragGestures(
                    onDragStart = { dragAccumulatedDeg = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val cx = size.width * 0.5f
                        val cy = size.height * 0.46f
                        val prev = change.previousPosition
                        val curr = change.position
                        val a1 = atan2(prev.y - cy, prev.x - cx)
                        val a2 = atan2(curr.y - cy, curr.x - cx)
                        var diffDeg = Math.toDegrees((a2 - a1).toDouble()).toFloat()
                        if (diffDeg > 180f) diffDeg -= 360f
                        if (diffDeg < -180f) diffDeg += 360f
                        dragAccumulatedDeg += abs(diffDeg) + (abs(dragAmount.x) + abs(dragAmount.y)) * 0.08f
                    },
                    onDragEnd = {
                        if (dragAccumulatedDeg >= 14f) {
                            onSwipeSpinStart()
                        }
                    }
                )
            }
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) {
                onCenterHubTap()
            }
    ) {
        val w = size.width
        val h = size.height
        val cx = w * 0.5f
        // 상단 풍차 날개 끝단과 하단 돌담 풍차 탑 기단이 모두 여유 있게 들어오도록 중심점과 반지름 설정
        val cy = h * 0.455f
        val radius = (min(w * 0.74f, h * 0.68f) * 0.5f).coerceAtLeast(68f)

        // 1. 뒷편 완만한 초원 능선과 앞쪽 언덕 능선
        val backKnoll = Path().apply {
            moveTo(0f, h * 0.80f)
            quadraticTo(w * 0.30f, h * 0.69f, w, h * 0.82f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(backKnoll, color = Color(0xFFD5E6D0).copy(alpha = 0.82f))

        val frontHill = Path().apply {
            moveTo(0f, h * 0.87f)
            quadraticTo(cx, h * 0.74f, w, h * 0.88f)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(frontHill, color = Color(0xFFBFD9B9).copy(alpha = 0.90f))

        // 언덕 위 자그마한 들꽃 포인트
        val flowerSpots = listOf(
            Offset(w * 0.16f, h * 0.89f),
            Offset(w * 0.24f, h * 0.86f),
            Offset(w * 0.77f, h * 0.87f),
            Offset(w * 0.85f, h * 0.91f)
        )
        flowerSpots.forEachIndexed { idx, pt ->
            drawCircle(
                color = if (idx % 2 == 0) SunlightSoft else PureCardWhite,
                radius = 3.dp.toPx(),
                center = pt
            )
        }

        // 2. 언덕 위 돌담 & 참나무 A자형 풍차 오두막 탑 기단 (휠 뒤쪽에 단단히 고정)
        val towerTopY = cy + radius * 0.08f
        val towerBotY = h * 0.965f
        val towerTopHalf = radius * 0.30f
        val towerBotHalf = radius * 0.56f
        val towerBody = Path().apply {
            moveTo(cx - towerTopHalf, towerTopY)
            lineTo(cx + towerTopHalf, towerTopY)
            lineTo(cx + towerBotHalf, towerBotY)
            lineTo(cx - towerBotHalf, towerBotY)
            close()
        }
        drawPath(towerBody, color = Color(0xFFEBE2CE))
        drawPath(
            towerBody,
            color = OakWood,
            style = Stroke(width = 3.2.dp.toPx())
        )

        // 원목 X자 대각 버팀목과 가로 보
        val braceTopY = cy + radius * 0.58f
        val braceBotY = towerBotY - 8.dp.toPx()
        if (braceBotY > braceTopY + 12.dp.toPx()) {
            drawLine(
                color = OakWood.copy(alpha = 0.75f),
                start = Offset(cx - towerTopHalf * 0.95f, braceTopY),
                end = Offset(cx + towerBotHalf * 0.84f, braceBotY),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawLine(
                color = OakWood.copy(alpha = 0.75f),
                start = Offset(cx + towerTopHalf * 0.95f, braceTopY),
                end = Offset(cx - towerBotHalf * 0.84f, braceBotY),
                strokeWidth = 2.4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // 풍차 탑 하단 아치형 원목 오두막 문
        val doorW = (radius * 0.30f).coerceAtLeast(22.dp.toPx())
        val doorH = (radius * 0.38f).coerceAtMost((towerBotY - towerTopY) * 0.45f)
        if (doorH > 14.dp.toPx()) {
            drawRoundRect(
                color = Color(0xFF5D4028),
                topLeft = Offset(cx - doorW * 0.5f, towerBotY - doorH),
                size = Size(doorW, doorH),
                cornerRadius = CornerRadius(doorW * 0.5f, doorW * 0.5f)
            )
            drawRoundRect(
                color = LightBirch,
                topLeft = Offset(cx - doorW * 0.5f, towerBotY - doorH),
                size = Size(doorW, doorH),
                cornerRadius = CornerRadius(doorW * 0.5f, doorW * 0.5f),
                style = Stroke(width = 1.5.dp.toPx())
            )
            drawCircle(
                color = SunlightAmber,
                radius = 2.2.dp.toPx(),
                center = Offset(cx + doorW * 0.24f, towerBotY - doorH * 0.44f)
            )
        }

        // 3. 원판과 일체형으로 회전하는 4장의 원목 격자 풍차 날개 (Sails)
        for (sailIdx in 0 until 4) {
            val sailAngle = wheelRotationDeg + sailIdx * 90f + 45f
            rotate(degrees = sailAngle, pivot = Offset(cx, cy)) {
                val sparStart = cx + radius * 0.78f
                val sparEnd = cx + radius * 1.21f
                // 중심 참나무 날개 기둥
                drawLine(
                    color = OakWood,
                    start = Offset(sparStart, cy),
                    end = Offset(sparEnd, cy),
                    strokeWidth = 4.8.dp.toPx(),
                    cap = StrokeCap.Round
                )

                // 크림 린넨 & 원목 격자 돛 프레임
                val sailLeft = cx + radius * 0.90f
                val sailWidth = radius * 0.29f
                val sailHalfH = 11.dp.toPx()
                drawRoundRect(
                    color = Color(0xFFFBF6EA),
                    topLeft = Offset(sailLeft, cy - sailHalfH),
                    size = Size(sailWidth, sailHalfH * 2f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                )
                drawRoundRect(
                    color = OakWood,
                    topLeft = Offset(sailLeft, cy - sailHalfH),
                    size = Size(sailWidth, sailHalfH * 2f),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                    style = Stroke(width = 1.8.dp.toPx())
                )

                // 격자 가로살 3줄
                val rungStep = sailWidth / 4f
                for (rIdx in 1..3) {
                    val rx = sailLeft + rungStep * rIdx
                    drawLine(
                        color = OakWood.copy(alpha = 0.82f),
                        start = Offset(rx, cy - sailHalfH),
                        end = Offset(rx, cy + sailHalfH),
                        strokeWidth = 1.2.dp.toPx()
                    )
                }
            }
        }

        // 4. 슬로우모션 발동 시 앰버 점선 포커스 링
        if (isSlowMotion && hubState != WindmillHubDisplayState.FINISHED) {
            drawCircle(
                color = SunlightAmber,
                radius = radius + 16.dp.toPx(),
                center = Offset(cx, cy),
                style = Stroke(
                    width = 2.6.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f), wheelRotationDeg)
                )
            )
        }

        // 5. 이중 원목 림 테두리 (바깥 참나무 테 + 황동 리벳 + 안쪽 자작나무 테)
        drawCircle(
            color = OakWood,
            radius = radius + 9.5.dp.toPx(),
            center = Offset(cx, cy)
        )
        for (rivetIdx in 0 until 12) {
            val rRad = Math.toRadians((wheelRotationDeg + rivetIdx * 30f).toDouble())
            val rx = cx + cos(rRad).toFloat() * (radius + 6.dp.toPx())
            val ry = cy + sin(rRad).toFloat() * (radius + 6.dp.toPx())
            drawCircle(
                color = SunlightAmber.copy(alpha = 0.85f),
                radius = 1.8.dp.toPx(),
                center = Offset(rx, ry)
            )
        }
        drawCircle(
            color = LightBirch,
            radius = radius + 3.dp.toPx(),
            center = Offset(cx, cy)
        )

        // 6. 가중치 비례 원판 부채꼴 렌더링
        val topLeft = Offset(cx - radius, cy - radius)
        val wheelSize = Size(radius * 2f, radius * 2f)

        arcs.forEach { arc ->
            val startDeg = arc.startDeg + wheelRotationDeg
            drawArc(
                color = Color(arc.item.colorArgb),
                startAngle = startDeg,
                sweepAngle = arc.sweepDeg,
                useCenter = true,
                topLeft = topLeft,
                size = wheelSize
            )
            drawArc(
                color = LightBirch.copy(alpha = 0.92f),
                startAngle = startDeg,
                sweepAngle = arc.sweepDeg,
                useCenter = true,
                topLeft = topLeft,
                size = wheelSize,
                style = Stroke(width = 1.9.dp.toPx())
            )
            if (arc.index == highlightWinnerIndex) {
                drawArc(
                    color = SunlightAmber,
                    startAngle = startDeg,
                    sweepAngle = arc.sweepDeg,
                    useCenter = true,
                    topLeft = topLeft,
                    size = wheelSize,
                    style = Stroke(width = 4.6.dp.toPx())
                )
            }
        }

        // 7. 각 조각 내부 한글 텍스트 및 가중치 확률 (좌반구 90~270도 정방향 자동 보정)
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            arcs.forEach { arc ->
                if (arc.sweepDeg >= 10f) {
                    val midDeg = arc.midDeg + wheelRotationDeg
                    val normMid = ((midDeg % 360f) + 360f) % 360f
                    val flipLeft = normMid > 90f && normMid < 270f

                    native.save()
                    native.translate(cx, cy)
                    if (flipLeft) {
                        native.rotate(midDeg + 180f)
                    } else {
                        native.rotate(midDeg)
                    }

                    val textR = if (flipLeft) -(radius * 0.585f) else (radius * 0.585f)
                    val baseFontPx = when {
                        arcs.size <= 4 -> 15.5.sp.toPx()
                        arcs.size <= 8 -> 13.2.sp.toPx()
                        arcs.size <= 12 -> 11.2.sp.toPx()
                        else -> 9.6.sp.toPx()
                    }
                    val rawLabel = if (mysteryMaskActive) "?" else arc.item.label
                    val maxW = radius * 0.65f
                    var fontPx = baseFontPx
                    labelPaint.textSize = fontPx
                    while (labelPaint.measureText(rawLabel) > maxW && fontPx > baseFontPx * 0.78f) {
                        fontPx *= 0.94f
                        labelPaint.textSize = fontPx
                    }

                    val fitted = fitCanvasLabel(rawLabel, labelPaint, maxW)
                    val baseOffset = -(labelPaint.descent() + labelPaint.ascent()) * 0.5f
                    native.drawText(fitted, textR, baseOffset, labelPaint)

                    if (!mysteryMaskActive && showWeightPercent && arc.sweepDeg >= 24f) {
                        subPaint.textSize = (fontPx * 0.72f).coerceAtLeast(8.8.sp.toPx())
                        val pctText = "${arc.probabilityPercent.roundToInt()}%"
                        native.drawText(pctText, textR, baseOffset + fontPx * 0.96f, subPaint)
                    }
                    native.restore()
                }
            }
        }

        // 8. 조각 경계 황동 핀(Peg)
        arcs.forEach { arc ->
            val rad = Math.toRadians((arc.pegBoundaryDeg + wheelRotationDeg).toDouble())
            val px = cx + cos(rad).toFloat() * (radius - 4.5.dp.toPx())
            val py = cy + sin(rad).toFloat() * (radius - 4.5.dp.toPx())
            drawCircle(
                color = WarmIvory,
                radius = 3.6.dp.toPx(),
                center = Offset(px, py)
            )
            drawCircle(
                color = OakWood,
                radius = 3.6.dp.toPx(),
                center = Offset(px, py),
                style = Stroke(width = 1.2.dp.toPx())
            )
        }

        // 9. 정전(BLACK_OUT) 구름 그림자 오버레이
        if (isBlackOut) {
            drawCircle(
                color = Color(0xEB18201B),
                radius = radius + 7.dp.toPx(),
                center = Offset(cx, cy)
            )
            drawIntoCanvas { canvas ->
                val native = canvas.nativeCanvas
                labelPaint.textSize = 17.sp.toPx()
                native.drawText("구름 그림자 정전 발동", cx, cy - 42.dp.toPx(), labelPaint)
            }
        }

        // 10. 원목 톱니 허브 캡
        val hubOuterR = (radius * 0.21f).coerceAtLeast(25.dp.toPx())
        for (tooth in 0 until 8) {
            val tDeg = wheelRotationDeg + tooth * 45f
            rotate(degrees = tDeg, pivot = Offset(cx, cy)) {
                drawRoundRect(
                    color = OakWood,
                    topLeft = Offset(cx - 4.dp.toPx(), cy - hubOuterR - 3.dp.toPx()),
                    size = Size(8.dp.toPx(), 7.dp.toPx()),
                    cornerRadius = CornerRadius(2.dp.toPx())
                )
            }
        }
        drawCircle(
            color = LightBirch,
            radius = hubOuterR,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = OakWood,
            radius = hubOuterR,
            center = Offset(cx, cy),
            style = Stroke(width = 2.8.dp.toPx())
        )
        val hubInnerColor = when (hubState) {
            WindmillHubDisplayState.FREE_SPIN_WAIT_TAP -> MeadowGreen
            WindmillHubDisplayState.FINISHED -> arcs.getOrNull(highlightWinnerIndex)?.let { Color(it.item.colorArgb) } ?: TerracottaWood
            else -> TerracottaWood
        }
        drawCircle(
            color = hubInnerColor,
            radius = hubOuterR * 0.72f,
            center = Offset(cx, cy)
        )
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            hubPaint.textSize = 11.sp.toPx()
            val hubText = when (hubState) {
                WindmillHubDisplayState.FREE_SPIN_WAIT_TAP -> "멈추기"
                WindmillHubDisplayState.PLANNED_SPINNING -> "회전중"
                WindmillHubDisplayState.FINISHED -> "당첨"
                WindmillHubDisplayState.IDLE -> "풍차"
            }
            val offset = -(hubPaint.descent() + hubPaint.ascent()) * 0.5f
            native.drawText(hubText, cx, cy + offset, hubPaint)
        }

        // 11. 상단 12시 테라코타 지붕 처마형 포인터 & 탄성 플래퍼
        val pivotX = cx
        val pivotY = cy - radius - 11.dp.toPx()
        rotate(degrees = flapperTiltDeg, pivot = Offset(pivotX, pivotY)) {
            val pointerPath = Path().apply {
                moveTo(pivotX - 15.dp.toPx(), pivotY - 9.dp.toPx())
                lineTo(pivotX + 15.dp.toPx(), pivotY - 9.dp.toPx())
                lineTo(pivotX, pivotY + 24.dp.toPx())
                close()
            }
            drawPath(pointerPath, color = TerracottaWood)
            drawPath(
                pointerPath,
                color = LightBirch,
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = SunlightAmber,
                radius = 3.8.dp.toPx(),
                center = Offset(pivotX, pivotY - 2.dp.toPx())
            )
        }
    }
}
