package com.jjalddalmark.minigamerullet.ui.games

import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.jjalddalmark.minigamerullet.audio.GhibliSoundEngine
import com.jjalddalmark.minigamerullet.data.RouletteStorageRepository
import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.BuiltInRouletteTemplates
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.PlannedRouletteSpin
import com.jjalddalmark.minigamerullet.model.RouletteColorPalette
import com.jjalddalmark.minigamerullet.model.RouletteDecisionSound
import com.jjalddalmark.minigamerullet.model.RouletteEffectFrequency
import com.jjalddalmark.minigamerullet.model.RouletteEngine
import com.jjalddalmark.minigamerullet.model.RouletteHistoryRecord
import com.jjalddalmark.minigamerullet.model.RouletteSliceArc
import com.jjalddalmark.minigamerullet.model.RouletteSliceItem
import com.jjalddalmark.minigamerullet.model.RouletteSpecialEffect
import com.jjalddalmark.minigamerullet.model.RouletteSpinResult
import com.jjalddalmark.minigamerullet.model.RouletteSpinStage
import com.jjalddalmark.minigamerullet.model.RouletteVideoQuality
import com.jjalddalmark.minigamerullet.model.RouletteWheelPreset
import com.jjalddalmark.minigamerullet.model.SpinTrajectorySample
import com.jjalddalmark.minigamerullet.share.RouletteExportVisualStyle
import com.jjalddalmark.minigamerullet.share.RouletteMediaExporter
import com.jjalddalmark.minigamerullet.share.RouletteShareChannel
import com.jjalddalmark.minigamerullet.share.RouletteShareManager
import com.jjalddalmark.minigamerullet.ui.components.GameStageScaffold
import com.jjalddalmark.minigamerullet.ui.components.StageCinemaBackdrop
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
import com.jjalddalmark.minigamerullet.ui.theme.SkyPondSoft
import com.jjalddalmark.minigamerullet.ui.theme.SubtleDivider
import com.jjalddalmark.minigamerullet.ui.theme.SunlightAmber
import com.jjalddalmark.minigamerullet.ui.theme.SunlightSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaSoft
import com.jjalddalmark.minigamerullet.ui.theme.TerracottaWood
import com.jjalddalmark.minigamerullet.ui.theme.WarmIvory
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private enum class LiveRoulettePhase {
    IDLE,
    FREE_SPIN_WAIT_TAP,
    PLANNED_SPINNING,
    FINISHED
}

/**
 * 골프와 무관하게 일상 속 모든 선택과 추첨에 사용하는 만능 결정 룰렛+ 메인 화면
 * - REA 역공학 기반 12종 특수효과 · 가중치 비율 · 탭하여 중지 · 자동 제외 · 영구 저장 · PIN 잠금 전체 무료 제공
 * - 결과 이미지(JPEG) 및 셋팅~회전~최종결정 전 과정 영상(MP4) 카카오톡·문자·텔레그램 공유 지원
 */
@Composable
fun DecisionRouletteGameScreen(
    settings: AppSettings,
    soundEngine: GhibliSoundEngine,
    onToggleBgmMute: () -> Unit,
    onBack: () -> Unit,
    onOpenSoundDialog: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val storageRepo = remember { RouletteStorageRepository(context) }

    var activePreset by remember {
        mutableStateOf(BuiltInRouletteTemplates.defaultTemplates().first())
    }
    var savedPresets by remember {
        mutableStateOf(BuiltInRouletteTemplates.defaultTemplates())
    }
    var historyRecords by remember {
        mutableStateOf<List<RouletteHistoryRecord>>(emptyList())
    }

    var phase by remember { mutableStateOf(LiveRoulettePhase.IDLE) }
    var spinRunId by remember { mutableIntStateOf(0) }
    var impactBurstKey by remember { mutableIntStateOf(0) }
    var wheelRotationDeg by remember { mutableFloatStateOf(0f) }
    var flapperTiltDeg by remember { mutableFloatStateOf(0f) }
    var isSlowMotion by remember { mutableStateOf(false) }
    var isBlackOut by remember { mutableStateOf(false) }
    var statusBannerText by remember { mutableStateOf("풍차 날개를 쓸어내리거나 하단 버튼을 눌러 돌리세요") }

    var activePlan by remember { mutableStateOf<PlannedRouletteSpin?>(null) }
    var spinPresetSnapshot by remember { mutableStateOf<RouletteWheelPreset?>(null) }
    val recordedSamples = remember { ArrayList<SpinTrajectorySample>(512) }
    var freeSpinElapsedSec by remember { mutableFloatStateOf(0f) }

    var latestResult by remember { mutableStateOf<RouletteSpinResult?>(null) }
    var showResultShareDialog by remember { mutableStateOf(false) }
    var showEditorDialog by remember { mutableStateOf(false) }
    var showVaultDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    // 잠금(PIN) 확인 모달 상태
    var pendingPinProtectedAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var pendingPinExpected by remember { mutableStateOf("") }
    var pendingPinTitle by remember { mutableStateOf("") }

    // 저장소 초기 로드
    LaunchedEffect(Unit) {
        val snap = storageRepo.loadInitialSnapshot()
        activePreset = snap.activePreset
        savedPresets = snap.savedPresets
        historyRecords = snap.historyRecords
    }

    DisposableEffect(Unit) {
        onDispose {
            soundEngine.setClimaxDucking(false)
        }
    }

    val currentArcs = remember(activePreset) {
        RouletteEngine.computeSliceArcs(activePreset.activeItems)
    }

    val pointerSliceIndex = remember(currentArcs, wheelRotationDeg) {
        RouletteEngine.resolveSliceIndexAtRotation(currentArcs, wheelRotationDeg)
    }

    fun requestProtectedAction(preset: RouletteWheelPreset, label: String, action: () -> Unit) {
        if (preset.isLocked) {
            pendingPinExpected = preset.passcodePin
            pendingPinTitle = label
            pendingPinProtectedAction = action
        } else {
            action()
        }
    }

    fun applyActivePresetChange(updated: RouletteWheelPreset, autoPersistActive: Boolean = true) {
        activePreset = updated
        if (phase != LiveRoulettePhase.FREE_SPIN_WAIT_TAP && phase != LiveRoulettePhase.PLANNED_SPINNING) {
            phase = LiveRoulettePhase.IDLE
            isSlowMotion = false
            isBlackOut = false
            statusBannerText = "셋팅 완료 · 원판을 밀거나 돌리기 버튼을 누르세요"
        }
        if (autoPersistActive) {
            scope.launch {
                storageRepo.saveActivePreset(updated)
            }
        }
    }

    fun startSpinNow(initialVelocityBoost: Boolean = false) {
        if (phase == LiveRoulettePhase.FREE_SPIN_WAIT_TAP || phase == LiveRoulettePhase.PLANNED_SPINNING) {
            return
        }
        // 이전 라운드에서 자동 제외 옵션이 켜져 있었고 방금 끝난 결과가 있다면 다음 스핀 시작 시 해당 당첨 항목을 제외 반영
        var workingPreset = activePreset
        if (workingPreset.removeOnceChosen && latestResult != null && phase == LiveRoulettePhase.FINISHED) {
            val prevWinnerId = latestResult!!.winnerItem.id
            val candidateExcluded = workingPreset.items.map { item ->
                if (item.id == prevWinnerId) item.copy(isExcluded = true) else item
            }
            val remainingCount = candidateExcluded.count { !it.isExcluded && it.label.isNotBlank() }
            workingPreset = if (remainingCount >= 2) {
                workingPreset.copy(items = candidateExcluded)
            } else {
                Toast.makeText(context, "모든 항목이 한 번씩 뽑혀 전체 항목을 다시 복원합니다.", Toast.LENGTH_SHORT).show()
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
        latestResult = null
        showResultShareDialog = false
        isSlowMotion = false
        isBlackOut = false
        soundEngine.setClimaxDucking(false)
        soundEngine.playWindmillStart()
        if (initialVelocityBoost) {
            soundEngine.playStickSlide(0)
        }

        if (workingPreset.tapToStop) {
            freeSpinElapsedSec = 0f
            activePlan = null
            statusBannerText = "언덕 바람을 타고 회전 중! 멈추고 싶을 때 터치하세요"
            phase = LiveRoulettePhase.FREE_SPIN_WAIT_TAP
            spinRunId++
        } else {
            val plan = RouletteEngine.buildSpinPlan(
                preset = workingPreset,
                initialRotationDeg = wheelRotationDeg
            )
            activePlan = plan
            statusBannerText = "언덕 위 원목 풍차 회전 시작!"
            phase = LiveRoulettePhase.PLANNED_SPINNING
            spinRunId++
        }
    }

    fun triggerManualStopNow() {
        if (phase != LiveRoulettePhase.FREE_SPIN_WAIT_TAP) return
        val snapshot = spinPresetSnapshot ?: activePreset
        soundEngine.playWoodTick(highPitch = true)
        val stopPlan = RouletteEngine.buildSpinPlan(
            preset = snapshot,
            initialRotationDeg = wheelRotationDeg,
            durationOverrideSec = 2.8f,
            extraSpinsOverride = 4
        )
        activePlan = stopPlan
        phase = LiveRoulettePhase.PLANNED_SPINNING
        spinRunId++
    }

    // 1. 탭하여 중지 대기(고속 등속 회전) 애니메이션 루프 (spinRunId 단독 키로 phase 전환 시 조기 취소 방지)
    LaunchedEffect(spinRunId) {
        if (spinRunId == 0 || phase != LiveRoulettePhase.FREE_SPIN_WAIT_TAP) return@LaunchedEffect
        val snapshot = spinPresetSnapshot ?: activePreset
        val arcs = RouletteEngine.computeSliceArcs(snapshot.activeItems)
        if (arcs.size < 2) return@LaunchedEffect

        var prevNanos = 0L
        var elapsed = 0f
        var prevSlice = RouletteEngine.resolveSliceIndexAtRotation(arcs, wheelRotationDeg)
        var lastSampleRecordedAt = -1f

        recordedSamples.add(
            SpinTrajectorySample(
                elapsedSec = 0f,
                wheelRotationDeg = wheelRotationDeg,
                flapperTiltDeg = 0f,
                stage = RouletteSpinStage.SPINNING,
                statusBanner = "고속 회전 중 · 탭하여 중지 대기"
            )
        )

        while (phase == LiveRoulettePhase.FREE_SPIN_WAIT_TAP) {
            withFrameNanos { now ->
                if (prevNanos != 0L) {
                    val dt = ((now - prevNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    elapsed += dt
                    freeSpinElapsedSec = elapsed
                    wheelRotationDeg += dt * 920f

                    val curSlice = RouletteEngine.resolveSliceIndexAtRotation(arcs, wheelRotationDeg)
                    if (curSlice != prevSlice) {
                        prevSlice = curSlice
                        flapperTiltDeg = -25f
                        soundEngine.playRoulettePegTick(highPitch = false)
                    } else {
                        flapperTiltDeg *= 0.80f
                    }

                    // 30fps 간격으로 궤적 기록 (최대 18초까지 버퍼 유지)
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
                                statusBanner = "고속 회전 중 · 탭하여 중지"
                            )
                        )
                    }
                }
                prevNanos = now
            }
        }
    }

    // 2. 결정론적 감속 · 클라이맥스 · 특수효과 반전 · 최종 당첨 확정 루프 (spinRunId 단독 키로 phase 전환 시 코루틴 취소 차단)
    LaunchedEffect(spinRunId) {
        if (spinRunId == 0 || phase != LiveRoulettePhase.PLANNED_SPINNING) return@LaunchedEffect
        val plan = activePlan ?: return@LaunchedEffect
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
                    statusBannerText = inst.banner

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

        // 최종 착지 상태 정리 및 당첨 결과 생성
        wheelRotationDeg = plan.finalTargetRotationDeg
        flapperTiltDeg = 0f
        isSlowMotion = false
        isBlackOut = false
        soundEngine.setClimaxDucking(false)

        val winnerArc = plan.arcs[plan.finalArcIndex]
        val finalResult = RouletteSpinResult(
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

        latestResult = finalResult
        statusBannerText = "최종 당첨: ${winnerArc.item.label} (${winnerArc.probabilityPercent.roundToInt()}%)"
        phase = LiveRoulettePhase.FINISHED
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
        historyRecords = kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
            storageRepo.appendHistoryRecord(historyEntry, updatedPreset)
        }
    }

    val isBusySpinning = phase == LiveRoulettePhase.FREE_SPIN_WAIT_TAP || phase == LiveRoulettePhase.PLANNED_SPINNING
    val subMeta = buildString {
        append("후보 ${currentArcs.size}개")
        if (activePreset.excludedCount > 0) append(" (제외 ${activePreset.excludedCount}개)")
        append(" · ")
        append(if (activePreset.tapToStop) "탭 직접중지" else "${activePreset.normalizedDurationSec}초")
        if (activePreset.specialEffect != RouletteSpecialEffect.NONE) {
            append(" · ${activePreset.specialEffect.title}")
        }
    }
    val currentPointerLabel = currentArcs.getOrNull(pointerSliceIndex)?.item?.label ?: "-"

    GameStageScaffold(
        gameType = GolfGameType.DECISION_ROULETTE_PLUS,
        settings = settings,
        isSlowMotion = isSlowMotion && phase == LiveRoulettePhase.PLANNED_SPINNING,
        slowMotionLabel = if (isBlackOut) {
            "구름 그림자 정전 발동 · 어디에 멈출까요"
        } else {
            "풍차 톱니 감속 중 · 오늘의 선택 결정 직전"
        },
        showCelebration = phase == LiveRoulettePhase.FINISHED,
        isActionActive = isBusySpinning,
        impactBurstKey = impactBurstKey,
        onToggleBgmMute = onToggleBgmMute,
        onBack = {
            if (!isBusySpinning) {
                onBack()
            } else {
                soundEngine.setClimaxDucking(false)
                onBack()
            }
        },
        onOpenSoundDialog = onOpenSoundDialog,
        titleOverride = activePreset.title,
        showPlayerEntryButton = false,
        subtitleOverride = subMeta,
        customSubHeader = {
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                // 1단: 보관함 · 현재 풍차 룰렛 저장 · 새 풍차 룰렛 · 추첨 기록
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    QuickToolChip(
                        text = "보관함 (${savedPresets.size})",
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
                                savedPresets = storageRepo.upsertSavedPreset(activePreset, saveAsNewCopy = false)
                                soundEngine.playStickSlide(1)
                                Toast.makeText(
                                    context,
                                    "[${activePreset.title}] 설정을 보관함에 저장했습니다.",
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
                            val freshPalette = RouletteColorPalette.HILLSIDE_WINDMILL
                            val fresh = RouletteWheelPreset(
                                id = UUID.randomUUID().toString(),
                                title = "새 맞춤 풍차 룰렛",
                                items = listOf(
                                    RouletteSliceItem(label = "선택지 1", weight = 1, colorArgb = freshPalette.colorAt(0)),
                                    RouletteSliceItem(label = "선택지 2", weight = 1, colorArgb = freshPalette.colorAt(1)),
                                    RouletteSliceItem(label = "선택지 3", weight = 1, colorArgb = freshPalette.colorAt(2)),
                                    RouletteSliceItem(label = "선택지 4", weight = 1, colorArgb = freshPalette.colorAt(3))
                                ),
                                palette = freshPalette
                            )
                            applyActivePresetChange(fresh)
                            soundEngine.playWoodTick(true)
                            showEditorDialog = true
                        }
                    )
                    QuickToolChip(
                        text = "추첨 기록 (${historyRecords.size})",
                        emphasized = false,
                        enabled = !isBusySpinning,
                        onClick = {
                            soundEngine.playWoodTick(false)
                            showHistoryDialog = true
                        }
                    )
                }

                // 2단: 저장된 룰렛 프리셋 원터치 스위처 (언덕 위 풍차 룰렛과 동일한 테라코타 원목 칩)
                if (savedPresets.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        savedPresets.forEach { saved ->
                            val isCurrent = saved.id == activePreset.id
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isCurrent) TerracottaWood else PureCardWhite)
                                    .border(
                                        1.dp,
                                        if (isCurrent) TerracottaWood else SubtleDivider,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable(enabled = !isBusySpinning) {
                                        soundEngine.playWoodTick(true)
                                        applyActivePresetChange(saved)
                                    }
                                    .padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = if (saved.isLocked) "${saved.title} [잠금]" else saved.title,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) PureCardWhite else InkSecondary,
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
                    .padding(vertical = 2.dp)
            ) {
                // 상단 빠른 조작 스트립: 회전 시간(3·5·7·12초·직접중지) + 제외 복원 + 직전 결과 사진·영상 공유
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
                                    applyActivePresetChange(
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
                                applyActivePresetChange(
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

                    if (activePreset.excludedCount > 0 && !isBusySpinning) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(WarmIvory)
                                .border(1.dp, OakWood.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                .clickable {
                                    val restored = activePreset.resetExcludedItems()
                                    applyActivePresetChange(restored)
                                    soundEngine.playStickCaseShuffle()
                                }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = "제외 ${activePreset.excludedCount}개 복원",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OakWood
                            )
                        }
                    }

                    if (latestResult != null && !isBusySpinning) {
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

                // 하단 메인 듀얼 액션 버튼 (언덕 위 풍차 룰렛 디자인과 완전 통일)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = {
                            if (!isBusySpinning) {
                                requestProtectedAction(activePreset, "항목 · 옵션 편집 잠금 해제") {
                                    soundEngine.playWoodTick(true)
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
                            text = if (activePreset.isLocked) "풍차 설정 [잠금]" else "풍차 항목 · 옵션",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isBusySpinning) InkMuted else InkPrimary,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = {
                            if (phase == LiveRoulettePhase.FREE_SPIN_WAIT_TAP) {
                                triggerManualStopNow()
                            } else if (!isBusySpinning) {
                                startSpinNow(initialVelocityBoost = false)
                            }
                        },
                        enabled = phase != LiveRoulettePhase.PLANNED_SPINNING,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (phase) {
                                LiveRoulettePhase.FREE_SPIN_WAIT_TAP -> MeadowGreen
                                else -> TerracottaWood
                            },
                            contentColor = PureCardWhite
                        ),
                        modifier = Modifier
                            .weight(0.62f)
                            .height(50.dp)
                    ) {
                        val mainBtnLabel = when (phase) {
                            LiveRoulettePhase.FREE_SPIN_WAIT_TAP -> "탭하여 풍차 멈추기!"
                            LiveRoulettePhase.PLANNED_SPINNING -> "풍차 바퀴 회전 중..."
                            LiveRoulettePhase.FINISHED -> "다시 풍차 돌리기"
                            LiveRoulettePhase.IDLE -> "힘껏 풍차 돌리기"
                        }
                        Text(
                            text = mainBtnLabel,
                            style = MaterialTheme.typography.titleMedium,
                            color = PureCardWhite,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 언덕 위 풍차 룰렛과 완전히 동일한 목가적 페어웨이 초원 시네마 배경 일러스트
            StageCinemaBackdrop(
                gameType = GolfGameType.WINDMILL_ROULETTE,
                videoEffectsEnabled = settings.videoEffectsEnabled,
                active = isBusySpinning
            )

            // 언덕 능선 + 돌담 원목 풍차 탑 + 4장 풍차 날개 + 원목 룰렛 바퀴 캔버스
            HillsideWindmillWheelCanvas(
                arcs = currentArcs,
                wheelRotationDeg = wheelRotationDeg,
                flapperTiltDeg = flapperTiltDeg,
                mysteryMaskActive = activePreset.mysteryMaskOnWheel && phase != LiveRoulettePhase.FINISHED,
                showWeightPercent = activePreset.items.any { !it.isExcluded && it.safeWeight > 1 },
                highlightWinnerIndex = if (phase == LiveRoulettePhase.FINISHED) {
                    latestResult?.winnerArcIndex ?: -1
                } else {
                    -1
                },
                isSlowMotion = isSlowMotion,
                isBlackOut = isBlackOut,
                hubState = when (phase) {
                    LiveRoulettePhase.IDLE -> WindmillHubDisplayState.IDLE
                    LiveRoulettePhase.FREE_SPIN_WAIT_TAP -> WindmillHubDisplayState.FREE_SPIN_WAIT_TAP
                    LiveRoulettePhase.PLANNED_SPINNING -> WindmillHubDisplayState.PLANNED_SPINNING
                    LiveRoulettePhase.FINISHED -> WindmillHubDisplayState.FINISHED
                },
                onSwipeSpinStart = {
                    if (!isBusySpinning) {
                        startSpinNow(initialVelocityBoost = true)
                    }
                },
                onCenterHubTap = {
                    when (phase) {
                        LiveRoulettePhase.IDLE, LiveRoulettePhase.FINISHED ->
                            startSpinNow(initialVelocityBoost = false)
                        LiveRoulettePhase.FREE_SPIN_WAIT_TAP ->
                            triggerManualStopNow()
                        LiveRoulettePhase.PLANNED_SPINNING -> Unit
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
                            phase == LiveRoulettePhase.FINISHED -> PureCardWhite.copy(alpha = 0.95f)
                            else -> PureCardWhite.copy(alpha = 0.90f)
                        }
                    )
                    .border(
                        1.dp,
                        when {
                            isBlackOut -> SunlightAmber
                            phase == LiveRoulettePhase.FINISHED -> TerracottaWood
                            else -> OakWood.copy(alpha = 0.35f)
                        },
                        RoundedCornerShape(12.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                val liveMask = activePreset.mysteryMaskOnWheel && phase != LiveRoulettePhase.FINISHED
                val bannerText = when {
                    isBlackOut -> "구름 그림자 정전 발동! 멈추는 순간 공개됩니다"
                    isBusySpinning -> {
                        val pointerTxt = if (liveMask) "?" else currentPointerLabel
                        "$statusBannerText · 바늘: $pointerTxt"
                    }
                    phase == LiveRoulettePhase.FINISHED -> statusBannerText
                    else -> buildString {
                        append("바늘 지목: ${if (liveMask) "?" else currentPointerLabel}")
                        if (activePreset.specialEffect != RouletteSpecialEffect.NONE) {
                            append(" · 반전[${activePreset.specialEffect.title}]")
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
                        phase == LiveRoulettePhase.FINISHED -> TerracottaWood
                        else -> InkPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // --- 모달 1: 추첨 결과 확인 및 카카오톡·문자·텔레그램 이미지/전 과정 영상 공유 모달 ---
    if (showResultShareDialog && latestResult != null) {
        RouletteResultShareDialog(
            result = latestResult!!,
            soundEnabled = settings.soundEnabled,
            soundEngine = soundEngine,
            visualStyle = RouletteExportVisualStyle.DECISION_WINDMILL,
            onSpinAgain = {
                showResultShareDialog = false
                startSpinNow(initialVelocityBoost = false)
            },
            onDismiss = {
                showResultShareDialog = false
            }
        )
    }

    // --- 모달 2: 항목 · 가중치 · 12종 특수효과 · 팔레트 · PIN 편집 모달 ---
    if (showEditorDialog) {
        RouletteEditorDialog(
            initialPreset = activePreset,
            soundEngine = soundEngine,
            onApplyOnly = { edited ->
                applyActivePresetChange(edited)
                showEditorDialog = false
            },
            onSaveToVaultAndApply = { edited ->
                applyActivePresetChange(edited)
                scope.launch {
                    savedPresets = storageRepo.upsertSavedPreset(edited, saveAsNewCopy = false)
                    Toast.makeText(
                        context,
                        "[${edited.title}] 설정을 보관함에 저장하고 적용했습니다.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                showEditorDialog = false
            },
            onDismiss = {
                showEditorDialog = false
            }
        )
    }

    // --- 모달 3: 저장된 룰렛 보관함 (불러오기 · 새 이름으로 저장 · 사본 · 삭제 · 템플릿 복원) ---
    if (showVaultDialog) {
        SavedRouletteVaultDialog(
            activePreset = activePreset,
            savedPresets = savedPresets,
            soundEngine = soundEngine,
            onLoadPreset = { target ->
                applyActivePresetChange(target)
                showVaultDialog = false
                Toast.makeText(context, "[${target.title}] 룰렛을 불러왔습니다.", Toast.LENGTH_SHORT).show()
            },
            onSaveCurrentOverwrite = {
                scope.launch {
                    savedPresets = storageRepo.upsertSavedPreset(activePreset, saveAsNewCopy = false)
                    Toast.makeText(context, "현재 룰렛 구성을 저장했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onSaveCurrentAsNew = { customTitle ->
                val titled = activePreset.copy(title = customTitle.ifBlank { activePreset.title })
                scope.launch {
                    val list = storageRepo.upsertSavedPreset(titled, saveAsNewCopy = true)
                    savedPresets = list
                    activePreset = list.first()
                    Toast.makeText(context, "새 룰렛으로 보관함에 추가했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onDuplicatePreset = { target ->
                scope.launch {
                    savedPresets = storageRepo.duplicateSavedPreset(target)
                }
            },
            onDeletePreset = { target ->
                requestProtectedAction(target, "[${target.title}] 삭제 잠금 해제") {
                    scope.launch {
                        savedPresets = storageRepo.deleteSavedPreset(target.id)
                    }
                }
            },
            onRestoreTemplates = {
                scope.launch {
                    savedPresets = storageRepo.restoreBuiltInTemplates()
                    Toast.makeText(context, "기본 일상 템플릿 10종을 복원했습니다.", Toast.LENGTH_SHORT).show()
                }
            },
            onDismiss = {
                showVaultDialog = false
            }
        )
    }

    // --- 모달 4: 추첨 이력 및 항목별 당첨 통계 모달 ---
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

    // --- 모달 5: 4자리 비밀번호(PIN) 잠금 확인 모달 ---
    if (pendingPinProtectedAction != null) {
        PinUnlockDialog(
            title = pendingPinTitle,
            expectedPin = pendingPinExpected,
            onUnlocked = {
                val action = pendingPinProtectedAction
                pendingPinProtectedAction = null
                action?.invoke()
            },
            onDismiss = {
                pendingPinProtectedAction = null
            }
        )
    }
}

internal fun fitCanvasLabel(text: String, paint: Paint, maxWidth: Float): String {
    if (maxWidth <= 8f || paint.measureText(text) <= maxWidth) return text
    val suffix = ".."
    val sw = paint.measureText(suffix)
    var end = text.length
    while (end > 1 && paint.measureText(text, 0, end) + sw > maxWidth) {
        end--
    }
    return text.substring(0, end) + suffix
}

/**
 * 추첨 결과 확인 + 이미지(JPEG) 공유 + 셋팅~회전~최종결정 전 과정 영상(MP4) 공유 통합 모달
 */
@Composable
internal fun RouletteResultShareDialog(
    result: RouletteSpinResult,
    soundEnabled: Boolean,
    soundEngine: GhibliSoundEngine,
    visualStyle: RouletteExportVisualStyle = RouletteExportVisualStyle.DECISION_WINDMILL,
    companionExemptNames: List<String> = emptyList(),
    onSpinAgain: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    // 0: 결과 이미지 공유, 1: 돌아가는 전 과정 영상(MP4) 공유
    var shareTab by remember { mutableIntStateOf(0) }
    var imageFile by remember(result.id, visualStyle) { mutableStateOf<File?>(null) }
    var isImageGenerating by remember(result.id, visualStyle) { mutableStateOf(false) }

    var selectedQuality by remember { mutableStateOf(RouletteVideoQuality.HD_720P) }
    var videoFilesByQuality by remember(result.id, visualStyle) {
        mutableStateOf<Map<RouletteVideoQuality, File>>(emptyMap())
    }
    var isVideoEncoding by remember(result.id, visualStyle) { mutableStateOf(false) }
    var videoProgress by remember(result.id, visualStyle) { mutableFloatStateOf(0f) }
    var videoErrorText by remember(result.id, visualStyle) { mutableStateOf<String?>(null) }

    // 결과 다이얼로그 오픈 즉시 고해상도 결과 이미지 자동 생성
    LaunchedEffect(result.id, visualStyle) {
        isImageGenerating = true
        runCatching {
            RouletteMediaExporter.renderResultImageToCache(
                context = context,
                result = result,
                visualStyle = visualStyle
            )
        }.onSuccess { file ->
            imageFile = file
        }
        isImageGenerating = false
    }

    fun ensureVideoGenerated(
        quality: RouletteVideoQuality,
        afterReady: ((File) -> Unit)? = null
    ) {
        val cached = videoFilesByQuality[quality]
        if (cached != null && cached.exists() && cached.length() > 0L) {
            afterReady?.invoke(cached)
            return
        }
        if (isVideoEncoding) return

        scope.launch {
            isVideoEncoding = true
            videoProgress = 0.02f
            videoErrorText = null
            runCatching {
                RouletteMediaExporter.exportSpinVideoToCache(
                    context = context,
                    result = result,
                    quality = quality,
                    includeSound = soundEnabled,
                    visualStyle = visualStyle,
                    onProgress = { p -> videoProgress = p }
                )
            }.onSuccess { mp4 ->
                videoFilesByQuality = videoFilesByQuality + (quality to mp4)
                isVideoEncoding = false
                afterReady?.invoke(mp4)
            }.onFailure { err ->
                isVideoEncoding = false
                videoErrorText = "영상 생성 중 오류가 발생했습니다: ${err.localizedMessage ?: "알 수 없는 오류"}"
            }
        }
    }

    // 영상 탭으로 전환하거나 화질 프리셋 변경 시 즉시 전 과정 MP4 인코딩 시작
    LaunchedEffect(shareTab, selectedQuality, result.id) {
        if (shareTab == 1) {
            ensureVideoGenerated(selectedQuality)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = screenHeightDp * 0.90f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 상단 당첨 요약 배너
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(PureCardWhite)
                        .border(2.dp, Color(result.winnerItem.colorArgb), RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "${result.presetSnapshot.title} · 최종 결정",
                            style = MaterialTheme.typography.labelLarge,
                            color = TerracottaWood,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        if (result.appliedSpecialEffect != RouletteSpecialEffect.NONE) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TerracottaSoft)
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "특수효과: ${result.appliedSpecialEffect.title}",
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = TerracottaWood
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = result.winnerItem.label,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = InkPrimary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = String.format(
                            Locale.KOREA,
                            "당첨 확률 %.1f%% · 가중치 %d배 · 전체 후보 %d개",
                            result.winnerProbabilityPercent,
                            result.winnerItem.safeWeight,
                            result.arcs.size
                        ),
                        fontSize = 12.sp,
                        color = InkSecondary
                    )

                    if (result.winnerItem.secretNote.isNotBlank()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(WarmIvory)
                                .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            Text(
                                text = "당첨 메모: ${result.winnerItem.secretNote}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PineDeep
                            )
                        }
                    }

                    if (companionExemptNames.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "무사 통과:",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PineDeep
                            )
                            companionExemptNames.forEach { safeName ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MeadowGreenSoft)
                                        .border(1.dp, MeadowGreen.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "$safeName 면제",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PineDeep
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 이미지 공유 / 전 과정 영상 공유 모드 전환 탭
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ShareModeTabButton(
                        title = "결과 이미지로 공유",
                        subtitle = "선명한 결과 카드 사진",
                        selected = shareTab == 0,
                        onClick = {
                            soundEngine.playWoodTick(true)
                            shareTab = 0
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ShareModeTabButton(
                        title = "회전 전 과정 영상 공유",
                        subtitle = "셋팅~회전~결정 MP4",
                        selected = shareTab == 1,
                        onClick = {
                            soundEngine.playWoodTick(true)
                            shareTab = 1
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 중앙 스크롤 미리보기 및 메신저 발송 영역
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (shareTab == 0) {
                        val readyImg = imageFile
                        if (readyImg != null && readyImg.exists()) {
                            val bmp = remember(readyImg.absolutePath) {
                                BitmapFactory.decodeFile(readyImg.absolutePath)?.asImageBitmap()
                            }
                            if (bmp != null) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(235.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(PureCardWhite)
                                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                        .padding(6.dp)
                                ) {
                                    Image(
                                        bitmap = bmp,
                                        contentDescription = "룰렛 결과 카드 미리보기",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "고해상도 결과 이미지 준비 완료 (1080x1350 · ${formatFileSize(readyImg.length())})",
                                fontSize = 11.5.sp,
                                color = InkSecondary
                            )
                        } else if (isImageGenerating) {
                            Text(
                                text = "결과 카드 이미지를 생성하고 있습니다...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = InkSecondary,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MessengerShareActionGrid(
                            enabled = imageFile != null && !isImageGenerating,
                            onSelectChannel = { channel ->
                                val f = imageFile ?: return@MessengerShareActionGrid
                                soundEngine.playWoodTick(true)
                                RouletteShareManager.shareMediaFile(
                                    context = context,
                                    file = f,
                                    mimeType = "image/jpeg",
                                    result = result,
                                    isVideo = false,
                                    channel = channel
                                )
                            },
                            onSaveToGallery = {
                                val f = imageFile ?: return@MessengerShareActionGrid
                                scope.launch {
                                    val savedUri = RouletteMediaExporter.saveMediaToGallery(context, f, isVideo = false)
                                    val msg = if (savedUri != null) {
                                        "갤러리(Pictures/MinigameRullet)에 결과 이미지를 저장했습니다."
                                    } else {
                                        "갤러리 저장에 실패했습니다."
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    } else {
                        // 화질·용량 균형 선택
                        Text(
                            text = "영상 화질 및 용량 옵션",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            RouletteVideoQuality.entries.forEach { q ->
                                val sel = selectedQuality == q
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (sel) MeadowGreenSoft else PureCardWhite)
                                        .border(
                                            if (sel) 1.6.dp else 1.dp,
                                            if (sel) MeadowGreen else SubtleDivider,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable(enabled = !isVideoEncoding) {
                                            selectedQuality = q
                                            soundEngine.playWoodTick(true)
                                        }
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = q.title,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sel) PineDeep else InkPrimary
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = q.subtitle,
                                        fontSize = 10.5.sp,
                                        color = InkSecondary
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        val readyVideo = videoFilesByQuality[selectedQuality]
                        if (isVideoEncoding) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = "셋팅 -> 회전 -> 최종 결정 전 과정을 영상으로 인코딩 중... ${(videoProgress * 100).roundToInt()}%",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = PineDeep
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { videoProgress.coerceIn(0f, 1f) },
                                    color = MeadowGreen,
                                    trackColor = WarmIvory,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "고효율 H.264 하드웨어 압축으로 화질 손실 없이 용량을 최적화하고 있습니다.",
                                    fontSize = 11.sp,
                                    color = InkSecondary
                                )
                            }
                        } else if (readyVideo != null && readyVideo.exists()) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(235.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                                    .padding(6.dp)
                            ) {
                                ExportedMp4PreviewPlayer(
                                    videoFile = readyVideo,
                                    modifier = Modifier
                                        .width(132.dp)
                                        .height(222.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "전 과정 영상 준비 완료 (${selectedQuality.width}x${selectedQuality.height} · ${formatFileSize(readyVideo.length())})",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PineDeep
                            )
                        } else if (videoErrorText != null) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(TerracottaSoft)
                                    .padding(12.dp)
                            ) {
                                Text(text = videoErrorText!!, fontSize = 12.sp, color = TerracottaWood)
                                Spacer(modifier = Modifier.height(6.dp))
                                OutlinedButton(onClick = { ensureVideoGenerated(selectedQuality) }) {
                                    Text("다시 생성 시도", color = TerracottaWood)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        MessengerShareActionGrid(
                            enabled = !isVideoEncoding,
                            onSelectChannel = { channel ->
                                ensureVideoGenerated(selectedQuality) { mp4File ->
                                    soundEngine.playWoodTick(true)
                                    RouletteShareManager.shareMediaFile(
                                        context = context,
                                        file = mp4File,
                                        mimeType = "video/mp4",
                                        result = result,
                                        isVideo = true,
                                        channel = channel
                                    )
                                }
                            },
                            onSaveToGallery = {
                                ensureVideoGenerated(selectedQuality) { mp4File ->
                                    scope.launch {
                                        val savedUri = RouletteMediaExporter.saveMediaToGallery(
                                            context = context,
                                            sourceFile = mp4File,
                                            isVideo = true
                                        )
                                        val msg = if (savedUri != null) {
                                            "갤러리(Movies/MinigameRullet)에 회전 전 과정 영상을 저장했습니다."
                                        } else {
                                            "갤러리 영상 저장에 실패했습니다."
                                        }
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SubtleDivider)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleDivider),
                        modifier = Modifier
                            .weight(0.38f)
                            .height(46.dp)
                    ) {
                        Text("닫기", color = InkSecondary)
                    }
                    Button(
                        onClick = onSpinAgain,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(0.62f)
                            .height(46.dp)
                    ) {
                        Text(
                            text = if (result.presetSnapshot.removeOnceChosen) {
                                "당첨 항목 제외 후 다음 추첨"
                            } else {
                                "한 번 더 돌리기"
                            },
                            color = PureCardWhite,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

/**
 * 인코딩된 로컬 MP4 파일을 다이얼로그 내부에서 부드럽게 루프 재생하는 미리보기 플레이어
 */
@Composable
private fun ExportedMp4PreviewPlayer(
    videoFile: File,
    modifier: Modifier = Modifier
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerHolder = remember(videoFile.absolutePath) { arrayOfNulls<MediaPlayer>(1) }

    DisposableEffect(lifecycleOwner, videoFile.absolutePath) {
        val observer = LifecycleEventObserver { _, event ->
            val mp = playerHolder[0] ?: return@LifecycleEventObserver
            when (event) {
                Lifecycle.Event.ON_PAUSE -> runCatching { if (mp.isPlaying) mp.pause() }
                Lifecycle.Event.ON_RESUME -> runCatching { if (!mp.isPlaying) mp.start() }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            val mp = playerHolder[0]
            playerHolder[0] = null
            if (mp != null) {
                runCatching { mp.stop() }
                runCatching { mp.release() }
            }
        }
    }

    AndroidView(
        factory = { ctx ->
            TextureView(ctx).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) {
                        val surface = Surface(st)
                        val mp = MediaPlayer()
                        playerHolder[0] = mp
                        runCatching {
                            mp.setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                                    .build()
                            )
                            mp.setDataSource(videoFile.absolutePath)
                            mp.setSurface(surface)
                            mp.isLooping = true
                            mp.setVolume(0.7f, 0.7f)
                            mp.setOnPreparedListener { prepared ->
                                val vw = prepared.videoWidth.toFloat()
                                val vh = prepared.videoHeight.toFloat()
                                if (vw > 0f && vh > 0f && w > 0 && h > 0) {
                                    val viewAspect = w.toFloat() / h.toFloat()
                                    val vidAspect = vw / vh
                                    val sx = if (vidAspect > viewAspect) 1f else vidAspect / viewAspect
                                    val sy = if (vidAspect > viewAspect) viewAspect / vidAspect else 1f
                                    val m = Matrix()
                                    m.setScale(sx, sy, w * 0.5f, h * 0.5f)
                                    setTransform(m)
                                }
                                prepared.start()
                            }
                            mp.prepareAsync()
                        }.onFailure {
                            runCatching { surface.release() }
                        }
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) = Unit

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        val mp = playerHolder[0]
                        playerHolder[0] = null
                        if (mp != null) {
                            runCatching { mp.stop() }
                            runCatching { mp.release() }
                        }
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) = Unit
                }
            }
        },
        modifier = modifier
    )
}

@Composable
private fun MessengerShareActionGrid(
    enabled: Boolean,
    onSelectChannel: (RouletteShareChannel) -> Unit,
    onSaveToGallery: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(
            text = "메신저로 바로 공유하기",
            style = MaterialTheme.typography.labelLarge,
            color = InkPrimary
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ShareChannelButton(
                label = "카카오톡",
                accentBg = SunlightSoft,
                accentBorder = SunlightAmber,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.KAKAOTALK) },
                modifier = Modifier.weight(1f)
            )
            ShareChannelButton(
                label = "문자 메시지",
                accentBg = MeadowGreenSoft,
                accentBorder = MeadowGreen,
                textColor = PineDeep,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.SMS_MMS) },
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            ShareChannelButton(
                label = "텔레그램",
                accentBg = SkyPondSoft,
                accentBorder = SkyPondBlue,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.TELEGRAM) },
                modifier = Modifier.weight(1f)
            )
            ShareChannelButton(
                label = "더보기 공유",
                accentBg = WarmIvory,
                accentBorder = SubtleDivider,
                textColor = InkPrimary,
                enabled = enabled,
                onClick = { onSelectChannel(RouletteShareChannel.SYSTEM_MORE) },
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedButton(
            onClick = onSaveToGallery,
            enabled = enabled,
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
        ) {
            Text(
                text = "휴대폰 갤러리에 저장",
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = InkPrimary
            )
        }
    }
}

@Composable
private fun ShareChannelButton(
    label: String,
    accentBg: Color,
    accentBorder: Color,
    textColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(if (enabled) accentBg else WarmIvory)
            .border(1.2.dp, if (enabled) accentBorder else SubtleDivider, RoundedCornerShape(11.dp))
            .clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) textColor else InkMuted
        )
    }
}

@Composable
private fun ShareModeTabButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) PineDeep else WarmIvory)
            .border(1.dp, if (selected) PineDeep else SubtleDivider, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp, horizontal = 8.dp)
    ) {
        Text(
            text = title,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            color = if (selected) PureCardWhite else InkPrimary,
            maxLines = 1
        )
        Text(
            text = subtitle,
            fontSize = 10.sp,
            color = if (selected) PureCardWhite.copy(alpha = 0.84f) else InkSecondary,
            maxLines = 1
        )
    }
}

/**
 * 항목 · 비율 · 12종 특수효과 · 팔레트 · 효과음 · 비밀번호 잠금 종합 편집 모달
 */
@Composable
internal fun RouletteEditorDialog(
    initialPreset: RouletteWheelPreset,
    soundEngine: GhibliSoundEngine,
    isWindmillTheme: Boolean = false,
    windmillPlayerNames: List<String> = emptyList(),
    onApplyOnly: (RouletteWheelPreset) -> Unit,
    onSaveToVaultAndApply: (RouletteWheelPreset) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp

    var editTab by remember { mutableIntStateOf(0) } // 0: 항목·비율, 1: 회전·특수효과, 2: 팔레트·사운드·잠금
    var title by remember { mutableStateOf(initialPreset.title) }
    val items = remember { mutableStateListOf<RouletteSliceItem>().apply { addAll(initialPreset.items) } }
    var spinDurationSec by remember { mutableIntStateOf(initialPreset.normalizedDurationSec) }
    var tapToStop by remember { mutableStateOf(initialPreset.tapToStop) }
    var removeOnceChosen by remember { mutableStateOf(initialPreset.removeOnceChosen) }
    var mysteryMask by remember { mutableStateOf(initialPreset.mysteryMaskOnWheel) }
    var specialEffect by remember { mutableStateOf(initialPreset.specialEffect) }
    var effectFrequency by remember { mutableStateOf(initialPreset.effectFrequency) }
    var decisionSound by remember { mutableStateOf(initialPreset.decisionSound) }
    var palette by remember { mutableStateOf(initialPreset.palette) }
    var passcodePin by remember { mutableStateOf(initialPreset.passcodePin) }

    var showBulkInputBox by remember { mutableStateOf(false) }
    var bulkRawText by remember { mutableStateOf("") }
    var showNumberRangeBox by remember { mutableStateOf(false) }
    var rangeStartText by remember { mutableStateOf("1") }
    var rangeEndText by remember { mutableStateOf("10") }
    var expandedNoteItemId by remember { mutableStateOf<String?>(null) }

    val totalWeight = items.filter { it.label.isNotBlank() }.sumOf { it.safeWeight }.coerceAtLeast(1)

    fun buildEditedPreset(): RouletteWheelPreset? {
        val cleaned = items.mapIndexedNotNull { idx, item ->
            val trimmed = item.label.trim()
            if (trimmed.isEmpty()) null else {
                item.copy(
                    label = trimmed.take(28),
                    weight = item.safeWeight,
                    colorArgb = item.colorArgb.takeIf { it != 0 } ?: palette.colorAt(idx),
                    secretNote = item.secretNote.trim().take(60)
                )
            }
        }
        if (cleaned.size < 2) {
            Toast.makeText(context, "최소 2개 이상의 항목 이름을 입력해 주세요.", Toast.LENGTH_SHORT).show()
            return null
        }
        val cleanPin = passcodePin.filter { it.isDigit() }.let { if (it.length == 4) it else "" }
        if (passcodePin.isNotBlank() && cleanPin.isEmpty()) {
            Toast.makeText(context, "비밀번호 잠금은 숫자 4자리로 입력하거나 비워 주세요.", Toast.LENGTH_SHORT).show()
            return null
        }
        return initialPreset.copy(
            title = title.trim().ifEmpty {
                if (isWindmillTheme) "언덕 위 풍차 룰렛" else "나만의 결정 룰렛"
            }.take(32),
            items = cleaned,
            spinDurationSec = spinDurationSec,
            tapToStop = tapToStop,
            removeOnceChosen = removeOnceChosen,
            mysteryMaskOnWheel = mysteryMask,
            specialEffect = specialEffect,
            effectFrequency = effectFrequency,
            decisionSound = decisionSound,
            palette = palette,
            passcodePin = cleanPin,
            updatedAtMillis = System.currentTimeMillis()
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = screenHeightDp * 0.90f)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isWindmillTheme) "언덕 위 풍차 항목 및 고급 옵션" else "룰렛 항목 및 고급 옵션 설정",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = "항목 ${items.size}개",
                        style = MaterialTheme.typography.labelLarge,
                        color = MeadowGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 3단 설정 탭 바
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    listOf("항목 · 비율", "회전 · 12종 반전", "색상 · 사운드 · 잠금").forEachIndexed { idx, tabLabel ->
                        val sel = editTab == idx
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (sel) MeadowGreen else WarmIvory)
                                .border(1.dp, if (sel) MeadowGreen else SubtleDivider, RoundedCornerShape(10.dp))
                                .clickable {
                                    editTab = idx
                                    soundEngine.playWoodTick(true)
                                }
                                .padding(vertical = 8.dp)
                        ) {
                            Text(
                                text = tabLabel,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sel) PureCardWhite else InkSecondary,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (editTab == 0) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it.take(32) },
                        singleLine = true,
                        label = { Text("룰렛 제목") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MeadowGreen,
                            unfocusedBorderColor = SubtleDivider,
                            focusedContainerColor = PureCardWhite,
                            unfocusedContainerColor = PureCardWhite
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // 빠른 항목 도구 모음
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        QuickToolChip(
                            text = "+ 항목 추가",
                            emphasized = true,
                            onClick = {
                                val nextIdx = items.size
                                items.add(
                                    RouletteSliceItem(
                                        label = "선택지 ${nextIdx + 1}",
                                        weight = 1,
                                        colorArgb = palette.colorAt(nextIdx)
                                    )
                                )
                                soundEngine.playWoodTick(true)
                            }
                        )
                        if (isWindmillTheme && windmillPlayerNames.size >= 2) {
                            QuickToolChip(
                                text = "출전 선수 채우기 (${windmillPlayerNames.size}명)",
                                emphasized = true,
                                onClick = {
                                    val synced = BuiltInRouletteTemplates.buildPlayerSliceItems(
                                        playerNames = windmillPlayerNames,
                                        palette = palette,
                                        existingItems = items
                                    )
                                    items.clear()
                                    items.addAll(synced)
                                    soundEngine.playStickCaseShuffle()
                                }
                            )
                        }
                        QuickToolChip(
                            text = "여러 줄 일괄 입력",
                            emphasized = showBulkInputBox,
                            onClick = {
                                showBulkInputBox = !showBulkInputBox
                                showNumberRangeBox = false
                                soundEngine.playWoodTick(false)
                            }
                        )
                        QuickToolChip(
                            text = "1~N 숫자 자동 생성",
                            emphasized = showNumberRangeBox,
                            onClick = {
                                showNumberRangeBox = !showNumberRangeBox
                                showBulkInputBox = false
                                soundEngine.playWoodTick(false)
                            }
                        )
                        QuickToolChip(
                            text = "순서 섞기",
                            emphasized = false,
                            onClick = {
                                val shuffled = items.shuffled()
                                items.clear()
                                items.addAll(shuffled)
                                soundEngine.playStickCaseShuffle()
                            }
                        )
                        QuickToolChip(
                            text = "비율 1배 통일",
                            emphasized = false,
                            onClick = {
                                for (i in items.indices) {
                                    items[i] = items[i].copy(weight = 1, isExcluded = false)
                                }
                                soundEngine.playWoodTick(true)
                            }
                        )
                    }

                    if (showBulkInputBox) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarmIvory)
                                .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            Text(
                                text = "줄바꿈 또는 쉼표(,)로 여러 항목을 한 번에 붙여넣으세요 (예: 치킨*2, 피자, 족발*3)",
                                fontSize = 11.sp,
                                color = InkSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            OutlinedTextField(
                                value = bulkRawText,
                                onValueChange = { bulkRawText = it },
                                placeholder = { Text("김치찌개\n돈까스 * 2\n파스타") },
                                maxLines = 4,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = PureCardWhite,
                                    unfocusedContainerColor = PureCardWhite
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val parsed = RouletteEngine.parseBulkItems(bulkRawText, palette)
                                        if (parsed.isNotEmpty()) {
                                            items.addAll(parsed)
                                            bulkRawText = ""
                                            showBulkInputBox = false
                                            soundEngine.playWoodTick(true)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("기존 목록 뒤에 추가", fontSize = 12.sp, color = PineDeep)
                                }
                                Button(
                                    onClick = {
                                        val parsed = RouletteEngine.parseBulkItems(bulkRawText, palette)
                                        if (parsed.size >= 2) {
                                            items.clear()
                                            items.addAll(parsed)
                                            bulkRawText = ""
                                            showBulkInputBox = false
                                            soundEngine.playWoodTick(true)
                                        } else {
                                            Toast.makeText(context, "2개 이상 입력해 주세요.", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("전체 교체 적용", fontSize = 12.sp, color = PureCardWhite)
                                }
                            }
                        }
                    }

                    if (showNumberRangeBox) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(WarmIvory)
                                .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                                .padding(10.dp)
                        ) {
                            OutlinedTextField(
                                value = rangeStartText,
                                onValueChange = { rangeStartText = it.filter { ch -> ch.isDigit() }.take(3) },
                                singleLine = true,
                                label = { Text("시작") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = rangeEndText,
                                onValueChange = { rangeEndText = it.filter { ch -> ch.isDigit() }.take(3) },
                                singleLine = true,
                                label = { Text("끝") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            Button(
                                onClick = {
                                    val s = rangeStartText.toIntOrNull() ?: 1
                                    val e = rangeEndText.toIntOrNull() ?: 10
                                    val generated = RouletteEngine.generateNumberItems(s, e, "번", palette)
                                    if (generated.size >= 2) {
                                        items.clear()
                                        items.addAll(generated)
                                        showNumberRangeBox = false
                                        soundEngine.playWoodTick(true)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen)
                            ) {
                                Text("생성", color = PureCardWhite)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 개별 조각 리스트 편집
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { idx, item ->
                            val pct = (item.safeWeight.toFloat() / totalWeight.toFloat()) * 100f
                            val isNoteOpen = expandedNoteItemId == item.id || item.secretNote.isNotBlank()

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // 색상 점 (누르면 팔레트 내 다음 색상으로 변경)
                                    Box(
                                        modifier = Modifier
                                            .size(22.dp)
                                            .clip(CircleShape)
                                            .background(Color(item.colorArgb))
                                            .border(1.dp, SubtleDivider, CircleShape)
                                            .clickable {
                                                val curColorIdx = palette.colorsArgb.indexOf(item.colorArgb)
                                                val nextCol = palette.colorAt(if (curColorIdx >= 0) curColorIdx + 1 else idx + 1)
                                                items[idx] = item.copy(colorArgb = nextCol)
                                                soundEngine.playWoodTick(true)
                                            }
                                    )

                                    OutlinedTextField(
                                        value = item.label,
                                        onValueChange = { typed ->
                                            items[idx] = item.copy(label = typed.take(28))
                                        },
                                        singleLine = true,
                                        placeholder = { Text("항목 ${idx + 1}") },
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MeadowGreen,
                                            unfocusedBorderColor = SubtleDivider
                                        ),
                                        modifier = Modifier.weight(1f)
                                    )

                                    // 가중치(rate) 감소/증가 버튼
                                    SmallStepCircle(
                                        symbol = "-",
                                        enabled = item.safeWeight > 1,
                                        onClick = {
                                            items[idx] = item.copy(weight = (item.safeWeight - 1).coerceAtLeast(1))
                                            soundEngine.playWoodTick(false)
                                        }
                                    )
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "${item.safeWeight}배",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = PineDeep
                                        )
                                        Text(
                                            text = "${pct.roundToInt()}%",
                                            fontSize = 10.sp,
                                            color = InkMuted
                                        )
                                    }
                                    SmallStepCircle(
                                        symbol = "+",
                                        enabled = item.safeWeight < 20,
                                        onClick = {
                                            items[idx] = item.copy(weight = (item.safeWeight + 1).coerceAtMost(20))
                                            soundEngine.playWoodTick(true)
                                        }
                                    )

                                    // 삭제 버튼
                                    if (items.size > 2) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(TerracottaSoft)
                                                .clickable {
                                                    items.removeAt(idx)
                                                    soundEngine.playWoodTick(false)
                                                }
                                                .padding(horizontal = 7.dp, vertical = 6.dp)
                                        ) {
                                            Text("삭제", fontSize = 11.sp, color = TerracottaWood, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp)
                                ) {
                                    Text(
                                        text = if (isNoteOpen) "당첨 메모 닫기" else "+ 당첨 시 공개할 상세 메모 추가",
                                        fontSize = 11.sp,
                                        color = MeadowGreen,
                                        modifier = Modifier.clickable {
                                            expandedNoteItemId = if (expandedNoteItemId == item.id) null else item.id
                                        }
                                    )
                                    if (item.isExcluded) {
                                        Text(
                                            text = "현재 제외 상태 (터치 시 복원)",
                                            fontSize = 11.sp,
                                            color = TerracottaWood,
                                            modifier = Modifier.clickable {
                                                items[idx] = item.copy(isExcluded = false)
                                            }
                                        )
                                    }
                                }

                                if (isNoteOpen) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    OutlinedTextField(
                                        value = item.secretNote,
                                        onValueChange = { note ->
                                            items[idx] = item.copy(secretNote = note.take(60))
                                        },
                                        singleLine = true,
                                        placeholder = { Text("당첨되었을 때 함께 보여줄 메모·벌칙·설명") },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                } else if (editTab == 1) {
                    // 탭 2: 회전 속도 · 동작 스위치 · 12종 반전 특수효과 & 빈도
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "회전 시간 (자동 감속 시)",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            RouletteWheelPreset.supportedDurationsSec.forEach { sec ->
                                val selected = !tapToStop && spinDurationSec == sec
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (selected) MeadowGreen else PureCardWhite)
                                        .border(1.dp, if (selected) MeadowGreen else SubtleDivider, RoundedCornerShape(10.dp))
                                        .clickable {
                                            tapToStop = false
                                            spinDurationSec = sec
                                            soundEngine.playWoodTick(true)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "${sec}초",
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) PureCardWhite else InkPrimary
                                    )
                                }
                            }
                        }

                        OptionToggleCard(
                            title = "탭하여 직접 중지 (수동 스톱)",
                            subtitle = "버튼을 다시 누를 때까지 최고 속도로 회전한 뒤 감속합니다",
                            checked = tapToStop,
                            onCheckedChange = { tapToStop = it }
                        )
                        OptionToggleCard(
                            title = "일단 선택되면 다음 스핀에서 자동 제외",
                            subtitle = "발표 순서·경품 추첨처럼 중복 당첨 없이 한 명씩 뽑을 때 유용합니다",
                            checked = removeOnceChosen,
                            onCheckedChange = { removeOnceChosen = it }
                        )
                        OptionToggleCard(
                            title = "회전 중 원판 항목 숨기기 (? 미스터리 모드)",
                            subtitle = "멈추기 전까지 각 조각 이름을 ? 로 가려 긴장감을 높입니다",
                            checked = mysteryMask,
                            onCheckedChange = { mysteryMask = it }
                        )

                        HorizontalDivider(color = SubtleDivider)

                        Text(
                            text = "클라이맥스 반전 특수효과 12종 (전체 무료 개방)",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        Text(
                            text = "선택 효과: ${specialEffect.title} — ${specialEffect.summary}",
                            fontSize = 11.5.sp,
                            color = TerracottaWood
                        )

                        RouletteSpecialEffect.entries.chunked(3).forEach { rowEffects ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                rowEffects.forEach { eff ->
                                    val sel = specialEffect == eff
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) PineDeep else PureCardWhite)
                                            .border(1.dp, if (sel) PineDeep else SubtleDivider, RoundedCornerShape(10.dp))
                                            .clickable {
                                                specialEffect = eff
                                                soundEngine.playWoodTick(true)
                                            }
                                            .padding(vertical = 9.dp, horizontal = 4.dp)
                                    ) {
                                        Text(
                                            text = eff.title,
                                            fontSize = 11.5.sp,
                                            fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                                            color = if (sel) PureCardWhite else InkPrimary,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        if (specialEffect != RouletteSpecialEffect.NONE) {
                            Text(
                                text = "특수효과 발동 확률 빈도",
                                style = MaterialTheme.typography.labelLarge,
                                color = InkPrimary
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState())
                            ) {
                                RouletteEffectFrequency.entries.forEach { freq ->
                                    val sel = effectFrequency == freq
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) TerracottaWood else PureCardWhite)
                                            .border(1.dp, if (sel) TerracottaWood else SubtleDivider, RoundedCornerShape(10.dp))
                                            .clickable {
                                                effectFrequency = freq
                                                soundEngine.playWoodTick(true)
                                            }
                                            .padding(horizontal = 11.dp, vertical = 8.dp)
                                    ) {
                                        Text(
                                            text = freq.label,
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sel) PureCardWhite else InkPrimary
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // 탭 3: 색상 팔레트 · 결정 효과음 · 4자리 비밀번호 잠금
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "원판 컬러 팔레트 일괄 적용",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        RouletteColorPalette.entries.forEach { pal ->
                            val sel = palette == pal
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) MeadowGreenSoft else PureCardWhite)
                                    .border(1.dp, if (sel) MeadowGreen else SubtleDivider, RoundedCornerShape(12.dp))
                                    .clickable {
                                        palette = pal
                                        for (i in items.indices) {
                                            items[i] = items[i].copy(colorArgb = pal.colorAt(i))
                                        }
                                        soundEngine.playWoodTick(true)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 9.dp)
                            ) {
                                Text(
                                    text = pal.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (sel) PineDeep else InkPrimary
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    pal.colorsArgb.take(6).forEach { c ->
                                        Box(
                                            modifier = Modifier
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(Color(c))
                                        )
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = SubtleDivider)

                        Text(
                            text = "최종 당첨 결정 효과음 (선택 시 미리듣기)",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        RouletteDecisionSound.entries.chunked(2).forEach { soundRow ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                soundRow.forEach { snd ->
                                    val sel = decisionSound == snd
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (sel) OakWood else PureCardWhite)
                                            .border(1.dp, if (sel) OakWood else SubtleDivider, RoundedCornerShape(10.dp))
                                            .clickable {
                                                decisionSound = snd
                                                soundEngine.playRouletteDecisionSound(snd)
                                            }
                                            .padding(vertical = 9.dp)
                                    ) {
                                        Text(
                                            text = snd.label,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (sel) PureCardWhite else InkPrimary
                                        )
                                    }
                                }
                                if (soundRow.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }

                        HorizontalDivider(color = SubtleDivider)

                        Text(
                            text = "룰렛 편집 · 삭제 보호용 4자리 비밀번호 (선택)",
                            style = MaterialTheme.typography.labelLarge,
                            color = InkPrimary
                        )
                        OutlinedTextField(
                            value = passcodePin,
                            onValueChange = { typed ->
                                passcodePin = typed.filter { it.isDigit() }.take(4)
                            },
                            singleLine = true,
                            placeholder = { Text("잠금 없이 사용하려면 비워 두세요 (숫자 4자리)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = PureCardWhite,
                                unfocusedContainerColor = PureCardWhite
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = SubtleDivider)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, SubtleDivider),
                        modifier = Modifier
                            .weight(0.26f)
                            .height(46.dp)
                    ) {
                        Text("취소", fontSize = 12.5.sp, color = InkSecondary)
                    }
                    OutlinedButton(
                        onClick = {
                            buildEditedPreset()?.let(onApplyOnly)
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.2.dp, MeadowGreen),
                        modifier = Modifier
                            .weight(0.34f)
                            .height(46.dp)
                    ) {
                        Text("바로 적용", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PineDeep)
                    }
                    Button(
                        onClick = {
                            buildEditedPreset()?.let(onSaveToVaultAndApply)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier
                            .weight(0.40f)
                            .height(46.dp)
                    ) {
                        Text("저장 후 적용", fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = PureCardWhite)
                    }
                }
            }
        }
    }
}

/**
 * 저장된 룰렛 보관함 모달 (언제든 저장·재실행·복제·삭제)
 */
@Composable
internal fun SavedRouletteVaultDialog(
    activePreset: RouletteWheelPreset,
    savedPresets: List<RouletteWheelPreset>,
    soundEngine: GhibliSoundEngine,
    isWindmillTheme: Boolean = false,
    onLoadPreset: (RouletteWheelPreset) -> Unit,
    onSaveCurrentOverwrite: () -> Unit,
    onSaveCurrentAsNew: (String) -> Unit,
    onDuplicatePreset: (RouletteWheelPreset) -> Unit,
    onDeletePreset: (RouletteWheelPreset) -> Unit,
    onRestoreTemplates: () -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    var newSaveTitle by remember(activePreset.title) { mutableStateOf(activePreset.title) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(22.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider),
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = screenHeightDp * 0.88f)
                .imePadding()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isWindmillTheme) {
                            "내 풍차 룰렛 보관함 (${savedPresets.size}개)"
                        } else {
                            "내 룰렛 보관함 (${savedPresets.size}개)"
                        },
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    Text(
                        text = if (isWindmillTheme) "풍차 템플릿 복원" else "기본 템플릿 복원",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MeadowGreen,
                        modifier = Modifier.clickable {
                            soundEngine.playWoodTick(true)
                            onRestoreTemplates()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "한 번 만든 룰렛을 저장해 두면 언제든 터치 한 번으로 다시 실행할 수 있습니다",
                    fontSize = 12.sp,
                    color = InkSecondary
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 현재 활성 룰렛 저장 박스
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PureCardWhite)
                        .border(1.dp, SubtleDivider, RoundedCornerShape(14.dp))
                        .padding(10.dp)
                ) {
                    OutlinedTextField(
                        value = newSaveTitle,
                        onValueChange = { newSaveTitle = it.take(32) },
                        singleLine = true,
                        label = { Text("현재 룰렛 저장 이름") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedButton(
                            onClick = {
                                soundEngine.playWoodTick(true)
                                onSaveCurrentOverwrite()
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("현재 항목에 덮어쓰기", fontSize = 12.sp, color = PineDeep)
                        }
                        Button(
                            onClick = {
                                soundEngine.playStickSlide(2)
                                onSaveCurrentAsNew(newSaveTitle.trim())
                            },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("새 룰렛으로 추가 저장", fontSize = 12.sp, color = PureCardWhite)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                ) {
                    items(savedPresets, key = { it.id }) { preset ->
                        val isActive = preset.id == activePreset.id
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isActive) MeadowGreenSoft else PureCardWhite)
                                .border(
                                    if (isActive) 1.6.dp else 1.dp,
                                    if (isActive) MeadowGreen else SubtleDivider,
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    soundEngine.playWoodTick(true)
                                    onLoadPreset(preset)
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = if (preset.isLocked) "${preset.title} [잠금]" else preset.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = InkPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = if (isActive) "사용 중" else "${preset.playCount}회 실행",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isActive) PineDeep else InkMuted
                                )
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            val previewItems = preset.items.take(5).joinToString(" · ") { it.label } +
                                if (preset.items.size > 5) " 외 ${preset.items.size - 5}개" else ""
                            Text(
                                text = previewItems,
                                fontSize = 11.5.sp,
                                color = InkSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val optSummary = buildString {
                                    append("${preset.items.size}개 항목")
                                    append(" · ")
                                    append(if (preset.tapToStop) "탭 중지" else "${preset.normalizedDurationSec}초")
                                    if (preset.specialEffect != RouletteSpecialEffect.NONE) {
                                        append(" · ${preset.specialEffect.title}")
                                    }
                                }
                                Text(
                                    text = optSummary,
                                    fontSize = 11.sp,
                                    color = OakWood
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "복제",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PineDeep,
                                        modifier = Modifier.clickable {
                                            soundEngine.playWoodTick(true)
                                            onDuplicatePreset(preset)
                                        }
                                    )
                                    Text(
                                        text = "삭제",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TerracottaWood,
                                        modifier = Modifier.clickable {
                                            soundEngine.playWoodTick(false)
                                            onDeletePreset(preset)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("닫기", color = InkSecondary)
                }
            }
        }
    }
}

/**
 * 추첨 기록 및 항목별 누적 당첨 통계 모달
 */
@Composable
internal fun RouletteHistoryDialog(
    records: List<RouletteHistoryRecord>,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit
) {
    val screenHeightDp = LocalConfiguration.current.screenHeightDp.dp
    val topCounts = remember(records) {
        records.groupingBy { it.winnerLabel }.eachCount().entries
            .sortedByDescending { it.value }
            .take(5)
    }

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
                .heightIn(max = screenHeightDp * 0.85f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "최근 추첨 기록 (${records.size}건)",
                        style = MaterialTheme.typography.titleLarge,
                        color = InkPrimary
                    )
                    if (records.isNotEmpty()) {
                        Text(
                            text = "기록 비우기",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TerracottaWood,
                            modifier = Modifier.clickable { onClearAll() }
                        )
                    }
                }

                if (topCounts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                    ) {
                        topCounts.forEach { (label, cnt) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(WarmIvory)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 9.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    text = "$label · ${cnt}회",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = PineDeep
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (records.isEmpty()) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp)
                    ) {
                        Text("아직 저장된 추첨 기록이 없습니다.", color = InkMuted)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .fillMaxWidth()
                    ) {
                        items(records, key = { it.id }) { rec ->
                            val timeStr = remember(rec.createdAtMillis) {
                                SimpleDateFormat("MM.dd HH:mm", Locale.KOREA).format(Date(rec.createdAtMillis))
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(PureCardWhite)
                                    .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
                                    .padding(11.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(rec.winnerColorArgb))
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = rec.winnerLabel,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = InkPrimary
                                    )
                                    Text(
                                        text = "${rec.wheelTitle} · 확률 ${rec.probabilityPercent.roundToInt()}%",
                                        fontSize = 11.sp,
                                        color = InkSecondary
                                    )
                                }
                                Text(
                                    text = timeStr,
                                    fontSize = 11.sp,
                                    color = InkMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Text("닫기", color = InkSecondary)
                }
            }
        }
    }
}

@Composable
internal fun PinUnlockDialog(
    title: String,
    expectedPin: String,
    onUnlocked: () -> Unit,
    onDismiss: () -> Unit
) {
    var typedPin by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = PaperCream,
            border = BorderStroke(1.dp, SubtleDivider)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = InkPrimary)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "설정된 숫자 4자리 비밀번호를 입력해 주세요.",
                    fontSize = 12.sp,
                    color = InkSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = typedPin,
                    onValueChange = {
                        typedPin = it.filter { c -> c.isDigit() }.take(4)
                        errorMsg = null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    label = { Text("4자리 비밀번호") },
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorMsg != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = errorMsg!!, fontSize = 11.5.sp, color = TerracottaWood)
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("취소", color = InkSecondary)
                    }
                    Button(
                        onClick = {
                            if (typedPin == expectedPin) {
                                onUnlocked()
                            } else {
                                errorMsg = "비밀번호가 일치하지 않습니다."
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MeadowGreen),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("확인", color = PureCardWhite)
                    }
                }
            }
        }
    }
}

@Composable
private fun OptionToggleCard(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(PureCardWhite)
            .border(1.dp, SubtleDivider, RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 12.dp, vertical = 9.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
            Text(text = subtitle, fontSize = 11.sp, color = InkSecondary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PureCardWhite,
                checkedTrackColor = MeadowGreen
            )
        )
    }
}

@Composable
internal fun HeaderPillButton(
    label: String,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(PureCardWhite)
            .border(1.dp, SubtleDivider, RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = InkPrimary
        )
    }
}

@Composable
internal fun QuickToolChip(
    text: String,
    emphasized: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (emphasized) MeadowGreenSoft else PureCardWhite)
            .border(
                1.dp,
                if (emphasized) MeadowGreen else SubtleDivider,
                RoundedCornerShape(10.dp)
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (!enabled) InkMuted else if (emphasized) PineDeep else InkPrimary,
            maxLines = 1
        )
    }
}

@Composable
private fun SmallStepCircle(
    symbol: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(if (enabled) WarmIvory else PaperCream)
            .border(1.dp, SubtleDivider, CircleShape)
            .clickable(enabled = enabled) { onClick() }
    ) {
        Text(
            text = symbol,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) InkPrimary else InkMuted
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    return if (bytes >= 1024L * 1024L) {
        String.format(Locale.KOREA, "%.2f MB", bytes.toDouble() / (1024.0 * 1024.0))
    } else {
        String.format(Locale.KOREA, "%d KB", (bytes / 1024L).coerceAtLeast(1L))
    }
}
