package com.jjalddalmark.minigamerullet.model

import java.util.Base64
import java.util.UUID
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * 룰렛 원판 색상 팔레트 7종 (라이트 테마 위에서 텍스트 가독성이 검증된 저채도·클래식 조합)
 */
enum class RouletteColorPalette(
    val title: String,
    val colorsArgb: List<Int>
) {
    HILLSIDE_WINDMILL(
        title = "언덕 풍차 원목",
        colorsArgb = listOf(
            0xFF367452.toInt(), // 언덕 초원 세이지
            0xFFB5583B.toInt(), // 풍차 지붕 테라코타
            0xFF9C7228.toInt(), // 밀밭 도토리 앰버
            0xFF356A8A.toInt(), // 연못 하늘 블루
            0xFF2B563F.toInt(), // 솔숲 딥그린
            0xFF7B5237.toInt(), // 참나무 오크 브라운
            0xFF964A5E.toInt(), // 들장미 클레이
            0xFF4B6472.toInt()  // 돌담 슬레이트
        )
    ),
    MEADOW_WOOD(
        title = "숲속 우드",
        colorsArgb = listOf(
            0xFF2F6F4E.toInt(), // 세이지 그린
            0xFF386E9B.toInt(), // 호수 블루
            0xFFB85435.toInt(), // 단풍 테라코타
            0xFF986B26.toInt(), // 도토리 앰버
            0xFF6B5288.toInt(), // 산들꽃 퍼플
            0xFF2E787A.toInt(), // 비취 청록
            0xFF8C4A52.toInt(), // 로즈 클레이
            0xFF56733C.toInt()  // 올리브 모스
        )
    ),
    WARM_PICNIC(
        title = "햇살 소풍",
        colorsArgb = listOf(
            0xFFC65D47.toInt(),
            0xFFC48829.toInt(),
            0xFF4A7C59.toInt(),
            0xFF4A789C.toInt(),
            0xFF8E5A7B.toInt(),
            0xFF3C8482.toInt(),
            0xFF8C6239.toInt(),
            0xFF6B7B3F.toInt()
        )
    ),
    NORDIC_SLATE(
        title = "북유럽 슬레이트",
        colorsArgb = listOf(
            0xFF2B4C3F.toInt(),
            0xFF34566F.toInt(),
            0xFF8D5B4C.toInt(),
            0xFF6D597A.toInt(),
            0xFF436F70.toInt(),
            0xFF9A6D38.toInt(),
            0xFF525E65.toInt(),
            0xFF7B4B52.toInt()
        )
    ),
    SOFT_WATERCOLOR(
        title = "부드러운 수채화",
        colorsArgb = listOf(
            0xFF588B76.toInt(),
            0xFF5D8AA8.toInt(),
            0xFFC87A65.toInt(),
            0xFFB88B4A.toInt(),
            0xFF846FA0.toInt(),
            0xFF4F9092.toInt(),
            0xFFA96B75.toInt(),
            0xFF748B54.toInt()
        )
    ),
    CLASSIC_BOARD(
        title = "클래식 보드",
        colorsArgb = listOf(
            0xFFA63A3A.toInt(),
            0xFF2E5B88.toInt(),
            0xFF2F7347.toInt(),
            0xFFB07822.toInt(),
            0xFF63458A.toInt(),
            0xFF25747A.toInt(),
            0xFF754C29.toInt(),
            0xFF4D6B31.toInt()
        )
    ),
    MINIMAL_MONO(
        title = "미니멀 모노",
        colorsArgb = listOf(
            0xFF2E3832.toInt(),
            0xFF4E5C53.toInt(),
            0xFF6B5E4C.toInt(),
            0xFF3E5663.toInt(),
            0xFF7A6855.toInt(),
            0xFF586961.toInt()
        )
    );

    fun colorAt(index: Int): Int {
        val safe = ((index % colorsArgb.size) + colorsArgb.size) % colorsArgb.size
        return colorsArgb[safe]
    }
}

/**
 * REA 역공학(`com.komorebi.roulette` -> `ja.U`)으로 추출한 12종 특수 효과 전원 무료 개방
 */
enum class RouletteSpecialEffect(
    val title: String,
    val summary: String
) {
    NONE("안 함", "특수 반전 없이 감속하여 멈춘 위치 그대로 결정"),
    SHIFT_1_BEFORE("이동 1번 뒤로", "멈춘 직후 이전 칸으로 1칸 되감기 반전"),
    SHIFT_2_BEFORE("이동 2번 뒤로", "멈춘 직후 이전 칸으로 2칸 되감기 반전"),
    SHIFT_3_BEFORE("이동 3번 뒤로", "멈춘 직후 이전 칸으로 3칸 되감기 반전"),
    SHIFT_1_AFTER("이동 1번 앞으로", "멈춘 직후 다음 칸으로 1칸 전진 반전"),
    SHIFT_2_AFTER("이동 2번 앞으로", "멈춘 직후 다음 칸으로 2칸 전진 반전"),
    SHIFT_3_AFTER("이동 3번 앞으로", "멈춘 직후 다음 칸으로 3칸 전진 반전"),
    SHIFT_180_DEGREES("이동 180도", "멈춘 직후 정반대편(180도) 위치로 반전"),
    SPINS_AGAIN("다시 회전", "멈춘 줄 알았던 순간 한 번 더 강하게 재회전"),
    BLACK_OUT("정전", "클라이맥스 구간에서 조명이 꺼졌다가 켜지며 결과 공개"),
    FAKE_SHIFT("가짜 이동", "옆 칸으로 넘어갈 듯 흔들렸다가 원래 자리로 복귀"),
    RANDOM("무작위", "10가지 반전 특수 효과 중 하나를 무작위로 발동");

    companion object {
        val concreteEffects: List<RouletteSpecialEffect> = listOf(
            SHIFT_1_BEFORE,
            SHIFT_2_BEFORE,
            SHIFT_3_BEFORE,
            SHIFT_1_AFTER,
            SHIFT_2_AFTER,
            SHIFT_3_AFTER,
            SHIFT_180_DEGREES,
            SPINS_AGAIN,
            BLACK_OUT,
            FAKE_SHIFT
        )
    }
}

/**
 * 특수 효과 발동 빈도 5단계 (`SettingRouletteEntity.SEFrequencyIdx` 대응)
 */
enum class RouletteEffectFrequency(
    val label: String,
    val probability: Float
) {
    NEVER("0% · 안 함", 0.0f),
    RARE("25% · 가끔", 0.25f),
    NORMAL("50% · 보통", 0.50f),
    OFTEN("75% · 자주", 0.75f),
    ALWAYS("100% · 항상", 1.0f)
}

/**
 * 최종 당첨 결정 효과음 7종 (`SettingRouletteEntity.decisionSoundIdx` 유료 잠금 해제)
 */
enum class RouletteDecisionSound(val label: String) {
    FANFARE("축하 팡파레"),
    CHIME_BELL("맑은 차임벨"),
    DRUM_ROLL_HIT("북소리 울림"),
    WOOD_CLAPPER("나무 클래퍼"),
    COMIC_STAB("익살 마림바"),
    HEARTBEAT_CHIME("심장박동 벨"),
    RANDOM("무작위 효과음")
}

/**
 * 전 과정 동영상(MP4) 공유 시 화질·용량 균형 프리셋
 */
enum class RouletteVideoQuality(
    val title: String,
    val subtitle: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrate: Int
) {
    HD_720P(
        title = "선명한 표준 HD (720x1280)",
        subtitle = "카카오톡 · 텔레그램 권장 (약 1.0~2.2MB)",
        width = 720,
        height = 1280,
        fps = 30,
        videoBitrate = 1_350_000
    ),
    COMPACT_540P(
        title = "초경량 문자 전송용 (540x960)",
        subtitle = "문자 MMS · 저용량 빠른 전송 (약 0.5~1.0MB)",
        width = 540,
        height = 960,
        fps = 24,
        videoBitrate = 720_000
    )
}

/**
 * 룰렛 개별 조각 모델 (`ItemRouletteEntity` 확장: 가중치 비율, 색상, 제외 상태, 숨김 메모)
 */
data class RouletteSliceItem(
    val id: String = UUID.randomUUID().toString(),
    val label: String,
    val weight: Int = 1,
    val colorArgb: Int = RouletteColorPalette.HILLSIDE_WINDMILL.colorAt(0),
    val isExcluded: Boolean = false,
    val secretNote: String = ""
) {
    val safeWeight: Int
        get() = weight.coerceIn(1, 20)
}

/**
 * 저장·불러오기 가능한 룰렛 프리셋 (`RouletteEntity` + `SettingRouletteEntity` 통합)
 */
data class RouletteWheelPreset(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val items: List<RouletteSliceItem>,
    val spinDurationSec: Int = 5,
    val tapToStop: Boolean = false,
    val removeOnceChosen: Boolean = false,
    val mysteryMaskOnWheel: Boolean = false,
    val specialEffect: RouletteSpecialEffect = RouletteSpecialEffect.NONE,
    val effectFrequency: RouletteEffectFrequency = RouletteEffectFrequency.NORMAL,
    val decisionSound: RouletteDecisionSound = RouletteDecisionSound.FANFARE,
    val palette: RouletteColorPalette = RouletteColorPalette.HILLSIDE_WINDMILL,
    val passcodePin: String = "",
    val playCount: Int = 0,
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val activeItems: List<RouletteSliceItem>
        get() {
            val nonExcluded = items.filter { !it.isExcluded && it.label.isNotBlank() }
            return if (nonExcluded.size >= 2) {
                nonExcluded
            } else {
                // 제외 항목이 많아 1개 이하만 남으면 전체 항목을 활성 후보로 안전 폴백
                items.filter { it.label.isNotBlank() }
            }
        }

    val excludedCount: Int
        get() = items.count { it.isExcluded }

    val totalActiveWeight: Int
        get() = activeItems.sumOf { it.safeWeight }.coerceAtLeast(1)

    val normalizedDurationSec: Int
        get() = spinDurationSec.coerceIn(3, 20)

    val isLocked: Boolean
        get() = passcodePin.length == 4 && passcodePin.all { it.isDigit() }

    fun applyPalette(targetPalette: RouletteColorPalette): RouletteWheelPreset {
        return copy(
            palette = targetPalette,
            items = items.mapIndexed { idx, item ->
                item.copy(colorArgb = targetPalette.colorAt(idx))
            }
        )
    }

    fun resetExcludedItems(): RouletteWheelPreset {
        return copy(items = items.map { it.copy(isExcluded = false) })
    }

    companion object {
        val supportedDurationsSec: List<Int> = listOf(3, 5, 7, 9, 12, 15, 20)
    }
}

/**
 * 원판 로컬 각도 구간 정보 (`0도` = 3시 방향, 시계 방향 증가; 화면 상단 포인터 = `270도`)
 */
data class RouletteSliceArc(
    val index: Int,
    val item: RouletteSliceItem,
    val startDeg: Float,
    val sweepDeg: Float,
    val probabilityPercent: Float
) {
    val midDeg: Float
        get() = startDeg + sweepDeg * 0.5f

    val pegBoundaryDeg: Float
        get() = startDeg
}

/**
 * 스핀 단계 구분 (라이브 화면 및 MP4 프레임 렌더러 공용)
 */
enum class RouletteSpinStage {
    SETUP_READY,
    SPINNING,
    CLIMAX_SLOWMO,
    SPECIAL_TWIST,
    FINAL_DECISION
}

/**
 * 실시간 스핀 중 기록되어 MP4 동영상 인코더가 그대로 재현하는 프레임 샘플
 */
data class SpinTrajectorySample(
    val elapsedSec: Float,
    val wheelRotationDeg: Float,
    val flapperTiltDeg: Float,
    val stage: RouletteSpinStage,
    val isSlowMotion: Boolean = false,
    val isBlackOut: Boolean = false,
    val statusBanner: String = ""
)

/**
 * 1회 추첨 결과 및 녹화 궤적 전체 패키지
 */
data class RouletteSpinResult(
    val id: String = UUID.randomUUID().toString(),
    val presetSnapshot: RouletteWheelPreset,
    val arcs: List<RouletteSliceArc>,
    val primaryArcIndex: Int,
    val winnerArcIndex: Int,
    val winnerItem: RouletteSliceItem,
    val winnerProbabilityPercent: Float,
    val appliedSpecialEffect: RouletteSpecialEffect,
    val finalWheelRotationDeg: Float,
    val trajectory: List<SpinTrajectorySample>,
    val finishedAtMillis: Long = System.currentTimeMillis()
) {
    val hadTwistChange: Boolean
        get() = appliedSpecialEffect != RouletteSpecialEffect.NONE && primaryArcIndex != winnerArcIndex
}

/**
 * 로컬 저장 추첨 이력 레코드
 */
data class RouletteHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val wheelTitle: String,
    val winnerLabel: String,
    val winnerColorArgb: Int,
    val probabilityPercent: Float,
    val specialEffectTitle: String,
    val secretNote: String = "",
    val createdAtMillis: Long = System.currentTimeMillis()
)

/**
 * 결정론적 자동 스핀 계획 (초기 각도 -> 1차 착지 -> 특수효과 반전 -> 최종 당첨 각도)
 */
data class PlannedRouletteSpin(
    val arcs: List<RouletteSliceArc>,
    val initialRotationDeg: Float,
    val primaryArcIndex: Int,
    val primaryTargetRotationDeg: Float,
    val appliedEffect: RouletteSpecialEffect,
    val finalArcIndex: Int,
    val finalTargetRotationDeg: Float,
    val mainSpinDurationSec: Float,
    val twistHoldSec: Float,
    val twistMoveSec: Float
) {
    val totalDurationSec: Float
        get() = mainSpinDurationSec + twistHoldSec + twistMoveSec

    /**
     * 경과 시간(`0..totalDurationSec`)에 따른 정확한 원판 회전 각도, 단계, 배너, 정전 여부 계산
     */
    fun evaluateAt(elapsedSec: Float): PlannedSpinInstant {
        val t = elapsedSec.coerceIn(0f, totalDurationSec)
        val mainDur = mainSpinDurationSec.coerceAtLeast(0.5f)

        if (t <= mainDur) {
            val u = (t / mainDur).coerceIn(0f, 1f)
            // 부드럽고 긴장감 넘치는 4차 감속 곡선: 1 - (1 - u)^4
            val eased = 1f - (1f - u).pow(4)
            val rot = initialRotationDeg + (primaryTargetRotationDeg - initialRotationDeg) * eased
            val isClimax = u >= 0.68f
            val isBlackOutActive = appliedEffect == RouletteSpecialEffect.BLACK_OUT && u in 0.64f..0.985f
            val banner = when {
                isBlackOutActive -> "특수효과 발동 · 정전 속 운명의 회전!"
                isClimax -> "결정적 순간 · 과연 어디에 멈출까요?"
                else -> "힘차게 돌아가는 중..."
            }
            return PlannedSpinInstant(
                wheelRotationDeg = rot,
                stage = if (isClimax) RouletteSpinStage.CLIMAX_SLOWMO else RouletteSpinStage.SPINNING,
                isSlowMotion = isClimax,
                isBlackOut = isBlackOutActive,
                banner = banner
            )
        }

        // 특수효과 없는 경우 즉시 종료 상태 반환
        if (twistHoldSec + twistMoveSec <= 0.001f) {
            return PlannedSpinInstant(
                wheelRotationDeg = finalTargetRotationDeg,
                stage = RouletteSpinStage.FINAL_DECISION,
                isSlowMotion = false,
                isBlackOut = false,
                banner = "최종 당첨 결정!"
            )
        }

        val postT = t - mainDur
        if (postT <= twistHoldSec) {
            return PlannedSpinInstant(
                wheelRotationDeg = primaryTargetRotationDeg,
                stage = RouletteSpinStage.SPECIAL_TWIST,
                isSlowMotion = true,
                isBlackOut = false,
                banner = "잠깐! 특수효과 [${appliedEffect.title}] 발동!"
            )
        }

        val moveU = ((postT - twistHoldSec) / twistMoveSec.coerceAtLeast(0.1f)).coerceIn(0f, 1f)
        val rot = if (appliedEffect == RouletteSpecialEffect.FAKE_SHIFT) {
            // 옆 칸으로 넘어갈 듯 갔다가 다시 원래 자리로 복귀하는 사인 곡선 페이크 연출
            val primaryArc = arcs[primaryArcIndex]
            val peekDelta = (primaryArc.sweepDeg * 0.62f).coerceIn(14f, 48f)
            primaryTargetRotationDeg - sin(moveU * PI.toFloat()) * peekDelta
        } else {
            // 스무스스텝 또는 재회전 감속 보간
            val easedMove = if (appliedEffect == RouletteSpecialEffect.SPINS_AGAIN) {
                1f - (1f - moveU).pow(3)
            } else {
                moveU * moveU * (3f - 2f * moveU)
            }
            primaryTargetRotationDeg + (finalTargetRotationDeg - primaryTargetRotationDeg) * easedMove
        }

        return PlannedSpinInstant(
            wheelRotationDeg = rot,
            stage = if (moveU >= 0.999f) RouletteSpinStage.FINAL_DECISION else RouletteSpinStage.SPECIAL_TWIST,
            isSlowMotion = true,
            isBlackOut = false,
            banner = "특수효과 발동 · ${appliedEffect.title}!"
        )
    }
}

data class PlannedSpinInstant(
    val wheelRotationDeg: Float,
    val stage: RouletteSpinStage,
    val isSlowMotion: Boolean,
    val isBlackOut: Boolean,
    val banner: String
)

/**
 * 룰렛 수학 · 확률 · 특수효과 · 텍스트 일괄 파서 엔진
 */
object RouletteEngine {
    const val POINTER_SCREEN_DEG = 270f // 12시 정각 포인터

    fun normalizeDeg(deg: Float): Float {
        val m = deg % 360f
        return if (m < 0f) m + 360f else m
    }

    /**
     * 활성 조각들의 가중치(`safeWeight`)에 비례하여 360도 원판 호(`RouletteSliceArc`) 생성
     */
    fun computeSliceArcs(items: List<RouletteSliceItem>): List<RouletteSliceArc> {
        val valid = items.filter { it.label.isNotBlank() }
        if (valid.isEmpty()) return emptyList()
        val totalWeight = valid.sumOf { it.safeWeight }.coerceAtLeast(1)

        var cursor = 0f
        return valid.mapIndexed { idx, item ->
            val ratio = item.safeWeight.toFloat() / totalWeight.toFloat()
            val sweep = if (idx == valid.lastIndex) {
                (360f - cursor).coerceAtLeast(0.5f)
            } else {
                (ratio * 360f).coerceAtLeast(0.5f)
            }
            val arc = RouletteSliceArc(
                index = idx,
                item = item,
                startDeg = cursor,
                sweepDeg = sweep,
                probabilityPercent = ratio * 100f
            )
            cursor += sweep
            arc
        }
    }

    /**
     * 원판이 `wheelRotationDeg`만큼 회전했을 때 12시 포인터(`270도`)가 가리키는 원판 로컬 각도(`0..360`)
     */
    fun pointerLocalAngle(wheelRotationDeg: Float): Float {
        return normalizeDeg(POINTER_SCREEN_DEG - normalizeDeg(wheelRotationDeg))
    }

    /**
     * 현재 `wheelRotationDeg`에서 12시 포인터 아래에 위치한 조각 인덱스 반환
     */
    fun resolveSliceIndexAtRotation(arcs: List<RouletteSliceArc>, wheelRotationDeg: Float): Int {
        if (arcs.isEmpty()) return 0
        val localDeg = pointerLocalAngle(wheelRotationDeg)
        for (arc in arcs) {
            val end = arc.startDeg + arc.sweepDeg
            if (localDeg >= arc.startDeg && localDeg < end) {
                return arc.index
            }
        }
        return arcs.lastIndex
    }

    /**
     * 특정 로컬 각도(`targetLocalDeg`)가 12시 포인터(`270도`)에 정확히 오도록 최소 `minExtraSpins` 바퀴 이상 시계 방향 회전하는 목표 각도 계산
     */
    fun computeClockwiseTargetRotation(
        fromRotationDeg: Float,
        targetLocalDeg: Float,
        minExtraSpins: Int
    ): Float {
        val desiredMod = normalizeDeg(POINTER_SCREEN_DEG - normalizeDeg(targetLocalDeg))
        val currentMod = normalizeDeg(fromRotationDeg)
        val deltaMod = normalizeDeg(desiredMod - currentMod)
        return fromRotationDeg + minExtraSpins.coerceAtLeast(1) * 360f + deltaMod
    }

    /**
     * 가중치에 비례하여 무작위로 당첨 조각 인덱스를 추출
     */
    fun pickWeightedArcIndex(arcs: List<RouletteSliceArc>, rng: Random = Random.Default): Int {
        if (arcs.isEmpty()) return 0
        val totalWeight = arcs.sumOf { it.item.safeWeight }.coerceAtLeast(1)
        val roll = rng.nextInt(totalWeight)
        var acc = 0
        for (arc in arcs) {
            acc += arc.item.safeWeight
            if (roll < acc) return arc.index
        }
        return arcs.lastIndex
    }

    /**
     * 설정된 특수 효과와 발동 빈도에 따라 이번 스핀에서 실제 발동할 특수 효과를 결정
     */
    fun resolveTriggeredEffect(
        configured: RouletteSpecialEffect,
        frequency: RouletteEffectFrequency,
        sliceCount: Int,
        rng: Random = Random.Default
    ): RouletteSpecialEffect {
        if (configured == RouletteSpecialEffect.NONE || sliceCount < 2) {
            return RouletteSpecialEffect.NONE
        }
        if (frequency == RouletteEffectFrequency.NEVER) {
            return RouletteSpecialEffect.NONE
        }
        if (frequency.probability < 1.0f && rng.nextFloat() >= frequency.probability) {
            return RouletteSpecialEffect.NONE
        }
        return if (configured == RouletteSpecialEffect.RANDOM) {
            RouletteSpecialEffect.concreteEffects[rng.nextInt(RouletteSpecialEffect.concreteEffects.size)]
        } else {
            configured
        }
    }

    /**
     * 자동 감속 또는 수동 정지 이후 실행될 결정론적 스핀 플랜 생성
     */
    fun buildSpinPlan(
        preset: RouletteWheelPreset,
        initialRotationDeg: Float,
        durationOverrideSec: Float? = null,
        extraSpinsOverride: Int? = null,
        rng: Random = Random.Default
    ): PlannedRouletteSpin {
        val arcs = computeSliceArcs(preset.activeItems)
        require(arcs.size >= 2) { "룰렛을 돌리려면 최소 2개 이상의 항목이 필요합니다." }

        val mainDuration = (durationOverrideSec ?: preset.normalizedDurationSec.toFloat()).coerceIn(1.6f, 22f)
        val primaryIdx = pickWeightedArcIndex(arcs, rng)
        val primaryArc = arcs[primaryIdx]
        // 경계선 바로 위에 멈춰 헷갈리지 않도록 조각 내부 22%~78% 구간에 자연스럽게 안착
        val innerFraction = 0.22f + rng.nextFloat() * 0.56f
        val primaryLocalDeg = normalizeDeg(primaryArc.startDeg + primaryArc.sweepDeg * innerFraction)

        val baseSpins = extraSpinsOverride ?: (5 + (mainDuration * 0.9f).roundToInt()).coerceIn(5, 18)
        val primaryTargetRot = computeClockwiseTargetRotation(
            fromRotationDeg = initialRotationDeg,
            targetLocalDeg = primaryLocalDeg,
            minExtraSpins = baseSpins
        )

        val effect = resolveTriggeredEffect(
            configured = preset.specialEffect,
            frequency = preset.effectFrequency,
            sliceCount = arcs.size,
            rng = rng
        )

        var finalIdx = primaryIdx
        var finalTargetRot = primaryTargetRot
        var twistHold = 0f
        var twistMove = 0f

        fun shiftArcBy(deltaIndex: Int) {
            val n = arcs.size
            val targetIdx = (((primaryIdx + deltaIndex) % n) + n) % n
            val targetArc = arcs[targetIdx]
            val targetLocal = normalizeDeg(targetArc.midDeg)
            val desiredMod = normalizeDeg(POINTER_SCREEN_DEG - targetLocal)
            val primaryMod = normalizeDeg(primaryTargetRot)

            // 조각 인덱스가 커질수록 원판 로컬 각도가 증가하므로, 시계 방향 회전(`+rot`)은 로컬 각도를 감소시킴
            // 따라서 `deltaIndex > 0`(다음 칸 전진)은 반시계(`-delta`) 방향 또는 정확한 해당 칸 최단 이동으로 연출
            var signedDiff = desiredMod - primaryMod
            if (deltaIndex > 0 && signedDiff > 0f) signedDiff -= 360f
            if (deltaIndex < 0 && signedDiff < 0f) signedDiff += 360f

            // 같은 인덱스로 한 바퀴 도는 특수 케이스(예: 항목 2개인데 2칸 이동) 보정
            if (abs(signedDiff) < 1f && deltaIndex != 0) {
                signedDiff = if (deltaIndex > 0) -360f else 360f
            }

            finalIdx = targetIdx
            finalTargetRot = primaryTargetRot + signedDiff
            twistHold = 0.38f
            twistMove = (0.65f + abs(deltaIndex) * 0.16f).coerceAtMost(1.25f)
        }

        when (effect) {
            RouletteSpecialEffect.NONE -> Unit
            RouletteSpecialEffect.BLACK_OUT -> {
                // 메인 스핀 중 정전 연출만 적용되고 착지 위치는 그대로 유지
                finalIdx = primaryIdx
                finalTargetRot = primaryTargetRot
            }
            RouletteSpecialEffect.SHIFT_1_AFTER -> shiftArcBy(+1)
            RouletteSpecialEffect.SHIFT_2_AFTER -> shiftArcBy(+2)
            RouletteSpecialEffect.SHIFT_3_AFTER -> shiftArcBy(+3)
            RouletteSpecialEffect.SHIFT_1_BEFORE -> shiftArcBy(-1)
            RouletteSpecialEffect.SHIFT_2_BEFORE -> shiftArcBy(-2)
            RouletteSpecialEffect.SHIFT_3_BEFORE -> shiftArcBy(-3)
            RouletteSpecialEffect.SHIFT_180_DEGREES -> {
                finalTargetRot = primaryTargetRot + 180f
                finalIdx = resolveSliceIndexAtRotation(arcs, finalTargetRot)
                twistHold = 0.38f
                twistMove = 0.92f
            }
            RouletteSpecialEffect.SPINS_AGAIN -> {
                val secondIdx = pickWeightedArcIndex(arcs, rng)
                val secondArc = arcs[secondIdx]
                val secondLocal = normalizeDeg(secondArc.startDeg + secondArc.sweepDeg * (0.25f + rng.nextFloat() * 0.50f))
                finalTargetRot = computeClockwiseTargetRotation(
                    fromRotationDeg = primaryTargetRot,
                    targetLocalDeg = secondLocal,
                    minExtraSpins = 3
                )
                finalIdx = secondIdx
                twistHold = 0.36f
                twistMove = 1.65f
            }
            RouletteSpecialEffect.FAKE_SHIFT -> {
                finalIdx = primaryIdx
                finalTargetRot = primaryTargetRot
                twistHold = 0.32f
                twistMove = 0.88f
            }
            RouletteSpecialEffect.RANDOM -> Unit
        }

        // 부동소수점 경계 오차 없이 최종 회전 각도가 가리키는 실제 조각 인덱스와 일치하도록 재검증
        val verifiedFinalIdx = resolveSliceIndexAtRotation(arcs, finalTargetRot)
        return PlannedRouletteSpin(
            arcs = arcs,
            initialRotationDeg = initialRotationDeg,
            primaryArcIndex = primaryIdx,
            primaryTargetRotationDeg = primaryTargetRot,
            appliedEffect = effect,
            finalArcIndex = verifiedFinalIdx,
            finalTargetRotationDeg = finalTargetRot,
            mainSpinDurationSec = mainDuration,
            twistHoldSec = twistHold,
            twistMoveSec = twistMove
        )
    }

    /**
     * 결정론적 스핀 플랜으로부터 지정 FPS의 스핀 궤적 샘플 리스트를 생성
     */
    fun sampleTrajectoryFromPlan(
        plan: PlannedRouletteSpin,
        fps: Int = 30
    ): List<SpinTrajectorySample> {
        val safeFps = fps.coerceIn(15, 60)
        val totalFrames = kotlin.math.ceil((plan.totalDurationSec * safeFps).toDouble()).toInt().coerceAtLeast(10)
        val out = ArrayList<SpinTrajectorySample>(totalFrames + 1)
        var prevSliceIdx = resolveSliceIndexAtRotation(plan.arcs, plan.initialRotationDeg)
        var flapper = 0f

        for (f in 0..totalFrames) {
            val sec = if (f == totalFrames) {
                plan.totalDurationSec
            } else {
                (f.toFloat() / safeFps.toFloat()).coerceAtMost(plan.totalDurationSec)
            }
            val inst = plan.evaluateAt(sec)
            val curSlice = resolveSliceIndexAtRotation(plan.arcs, inst.wheelRotationDeg)
            flapper = if (curSlice != prevSliceIdx) {
                prevSliceIdx = curSlice
                -24f
            } else {
                flapper * 0.72f
            }
            out.add(
                SpinTrajectorySample(
                    elapsedSec = sec,
                    wheelRotationDeg = inst.wheelRotationDeg,
                    flapperTiltDeg = flapper,
                    stage = inst.stage,
                    isSlowMotion = inst.isSlowMotion,
                    isBlackOut = inst.isBlackOut,
                    statusBanner = inst.banner
                )
            )
        }
        return out
    }

    /**
     * 줄바꿈·쉼표·세미콜론으로 구분된 여러 항목 텍스트를 한 번에 조각 리스트로 파싱
     * `항목명 * 3` 또는 `항목명:3` 형태로 입력하면 가중치(1~20배)까지 자동 인식
     */
    fun parseBulkItems(
        rawText: String,
        palette: RouletteColorPalette = RouletteColorPalette.MEADOW_WOOD
    ): List<RouletteSliceItem> {
        val weightSuffixRegex = Regex("""^(.+?)\s*[*xX:]\s*(\d{1,2})$""")
        val tokens = rawText
            .split('\n', ',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        return tokens.mapIndexed { idx, token ->
            val match = weightSuffixRegex.matchEntire(token)
            val label: String
            val weight: Int
            if (match != null && match.groupValues[1].isNotBlank()) {
                label = match.groupValues[1].trim().take(28)
                weight = (match.groupValues[2].toIntOrNull() ?: 1).coerceIn(1, 20)
            } else {
                label = token.take(28)
                weight = 1
            }
            RouletteSliceItem(
                label = label,
                weight = weight,
                colorArgb = palette.colorAt(idx)
            )
        }
    }

    /**
     * 숫자 범위(`start..end`) 항목 자동 생성기 (번호 추첨·행운 번호·순번 뽑기용)
     */
    fun generateNumberItems(
        start: Int,
        end: Int,
        suffix: String = "번",
        palette: RouletteColorPalette = RouletteColorPalette.MEADOW_WOOD
    ): List<RouletteSliceItem> {
        val lo = minOf(start, end)
        val hi = maxOf(start, end)
        val clampedEnd = (lo + 99).coerceAtMost(hi) // 한 번에 최대 100개 안전 제한
        return (lo..clampedEnd).mapIndexed { idx, num ->
            RouletteSliceItem(
                label = "$num$suffix",
                weight = 1,
                colorArgb = palette.colorAt(idx)
            )
        }
    }
}

/**
 * 골프와 무관하게 일상·모임·직장·학교·가정에서 즉시 쓸 수 있는 10가지 기본 템플릿
 */
object BuiltInRouletteTemplates {
    fun defaultTemplates(): List<RouletteWheelPreset> {
        val now = 1_760_000_000_000L
        return listOf(
            createTemplate(
                id = "tpl_lunch_menu",
                title = "오늘 점심 메뉴 결정",
                labelsAndWeights = listOf(
                    "김치찌개 · 백반" to 1,
                    "돈까스 · 일식" to 1,
                    "마라탕 · 중식" to 1,
                    "파스타 · 브런치" to 1,
                    "국밥 · 해장국" to 1,
                    "샐러드 · 포케" to 1
                ),
                palette = RouletteColorPalette.HILLSIDE_WINDMILL,
                spinDurationSec = 5,
                timestamp = now
            ),
            createTemplate(
                id = "tpl_coffee_treat",
                title = "커피 · 디저트 쏘기",
                labelsAndWeights = listOf(
                    "아메리카노 전원" to 2,
                    "디저트까지 쏘기" to 1,
                    "오늘 완전 면제!" to 2,
                    "음료 사이즈업" to 2,
                    "다음 기회에" to 2
                ),
                palette = RouletteColorPalette.WARM_PICNIC,
                spinDurationSec = 7,
                specialEffect = RouletteSpecialEffect.RANDOM,
                effectFrequency = RouletteEffectFrequency.NORMAL,
                timestamp = now - 1000L
            ),
            createTemplate(
                id = "tpl_yes_or_no",
                title = "예 · 아니오 빠른 결정",
                labelsAndWeights = listOf(
                    "예 · 지금 바로!" to 2,
                    "아니오 · 잠시 보류" to 2,
                    "조건부 진행" to 1,
                    "한 번 더 생각하기" to 1
                ),
                palette = RouletteColorPalette.CLASSIC_BOARD,
                spinDurationSec = 3,
                timestamp = now - 2000L
            ),
            createTemplate(
                id = "tpl_dinner_night",
                title = "저녁 회식 · 야식 메뉴",
                labelsAndWeights = listOf(
                    "삼겹살 · 숯불구이" to 1,
                    "치킨 · 시원한 맥주" to 1,
                    "족발 · 보쌈 세트" to 1,
                    "모둠회 · 해산물" to 1,
                    "피자 · 수제버거" to 1,
                    "곱창 · 전골 요리" to 1
                ),
                palette = RouletteColorPalette.WARM_PICNIC,
                spinDurationSec = 5,
                timestamp = now - 3000L
            ),
            createTemplate(
                id = "tpl_speaker_order",
                title = "발표 · 당번 순서 뽑기",
                labelsAndWeights = listOf(
                    "1번 참가자" to 1,
                    "2번 참가자" to 1,
                    "3번 참가자" to 1,
                    "4번 참가자" to 1,
                    "5번 참가자" to 1
                ),
                palette = RouletteColorPalette.NORDIC_SLATE,
                spinDurationSec = 5,
                removeOnceChosen = true,
                timestamp = now - 4000L
            ),
            createTemplate(
                id = "tpl_party_mission",
                title = "모임 · 파티 유쾌한 미션",
                labelsAndWeights = listOf(
                    "노래 한 소절 부르기" to 1,
                    "멋진 건배사 선창" to 2,
                    "시원한 음료 사오기" to 1,
                    "옆 사람 1분 칭찬" to 2,
                    "재미있는 에피소드 공개" to 1,
                    "행운의 완전 면제권" to 2
                ),
                palette = RouletteColorPalette.SOFT_WATERCOLOR,
                spinDurationSec = 7,
                specialEffect = RouletteSpecialEffect.FAKE_SHIFT,
                effectFrequency = RouletteEffectFrequency.NORMAL,
                decisionSound = RouletteDecisionSound.COMIC_STAB,
                timestamp = now - 5000L
            ),
            createTemplate(
                id = "tpl_housework_split",
                title = "집안일 · 역할 분담",
                labelsAndWeights = listOf(
                    "설거지 깔끔 담당" to 1,
                    "청소기 · 바닥 닦기" to 1,
                    "분리수거 · 쓰레기" to 1,
                    "요리 · 상차림 보조" to 1,
                    "세탁물 정리하기" to 1,
                    "오늘 하루 자유 휴식" to 1
                ),
                palette = RouletteColorPalette.MEADOW_WOOD,
                spinDurationSec = 5,
                removeOnceChosen = true,
                timestamp = now - 6000L
            ),
            createTemplate(
                id = "tpl_weekend_plan",
                title = "주말 나들이 · 여가 코스",
                labelsAndWeights = listOf(
                    "근교 드라이브 · 카페" to 1,
                    "영화관 · 전시회 관람" to 1,
                    "공원 산책 · 피크닉" to 1,
                    "제철 맛집 투어" to 1,
                    "집콕 힐링 · 정주행" to 1
                ),
                palette = RouletteColorPalette.SOFT_WATERCOLOR,
                spinDurationSec = 5,
                timestamp = now - 7000L
            ),
            createTemplate(
                id = "tpl_lucky_numbers",
                title = "행운의 숫자 1~10",
                labelsAndWeights = (1..10).map { "${it}번" to 1 },
                palette = RouletteColorPalette.CLASSIC_BOARD,
                spinDurationSec = 5,
                timestamp = now - 8000L
            ),
            createTemplate(
                id = "tpl_weighted_prize",
                title = "경품 · 선물 가중치 추첨",
                labelsAndWeights = listOf(
                    "1등 프리미엄 선물" to 1,
                    "2등 백화점 상품권" to 2,
                    "3등 디저트 세트" to 4,
                    "행운상 아메리카노" to 8
                ),
                palette = RouletteColorPalette.NORDIC_SLATE,
                spinDurationSec = 9,
                specialEffect = RouletteSpecialEffect.BLACK_OUT,
                effectFrequency = RouletteEffectFrequency.OFTEN,
                decisionSound = RouletteDecisionSound.FANFARE,
                timestamp = now - 9000L
            )
        )
    }

    const val WINDMILL_PLAYER_PRESET_PREFIX = "windmill_players_"

    fun isPlayerSyncedWindmillPreset(preset: RouletteWheelPreset): Boolean =
        preset.id.startsWith(WINDMILL_PLAYER_PRESET_PREFIX)

    fun normalizeWindmillPlayerNames(playerNames: List<String>): List<String> {
        val cleaned = playerNames.map { it.trim().take(24) }.filter { it.isNotEmpty() }
        return when {
            cleaned.size >= 2 -> cleaned
            cleaned.size == 1 -> listOf(cleaned[0], "2번캐디백")
            else -> listOf("1번캐디백", "2번캐디백", "3번캐디백", "4번캐디백")
        }
    }

    fun buildPlayerSliceItems(
        playerNames: List<String>,
        palette: RouletteColorPalette = RouletteColorPalette.HILLSIDE_WINDMILL,
        existingItems: List<RouletteSliceItem> = emptyList(),
        presetIdStem: String = "windmill_players",
        defaultSecretNote: String = ""
    ): List<RouletteSliceItem> {
        val safeNames = normalizeWindmillPlayerNames(playerNames)
        return safeNames.mapIndexed { idx, name ->
            val matchedByName = existingItems.firstOrNull { it.label == name }
            val matchedByIndex = existingItems.getOrNull(idx)
            val prev = matchedByName ?: matchedByIndex
            RouletteSliceItem(
                id = prev?.id ?: "${presetIdStem}_p_$idx",
                label = name,
                weight = prev?.safeWeight ?: 1,
                colorArgb = palette.colorAt(idx),
                isExcluded = false,
                secretNote = prev?.secretNote?.ifBlank { defaultSecretNote } ?: defaultSecretNote
            )
        }
    }

    fun syncPlayerDrivenPreset(
        preset: RouletteWheelPreset,
        playerNames: List<String>
    ): RouletteWheelPreset {
        if (!isPlayerSyncedWindmillPreset(preset)) return preset
        val syncedItems = buildPlayerSliceItems(
            playerNames = playerNames,
            palette = preset.palette,
            existingItems = preset.items,
            presetIdStem = preset.id
        )
        return preset.copy(items = syncedItems)
    }

    /**
     * 언덕 위 풍차 룰렛 전용 기본 템플릿 10종 (선수 명단 자동 연동 4종 + 그늘집·뒤풀이·홀미션·경품 6종)
     */
    fun defaultWindmillTemplates(
        playerNames: List<String> = listOf("1번캐디백", "2번캐디백", "3번캐디백", "4번캐디백"),
        defaultDurationSec: Int = 5
    ): List<RouletteWheelPreset> {
        val now = System.currentTimeMillis()
        val safeDuration = defaultDurationSec.coerceIn(3, 20)
        val safePlayers = normalizeWindmillPlayerNames(playerNames)
        val playerPairs = safePlayers.map { it to 1 }

        return listOf(
            createTemplate(
                id = "${WINDMILL_PLAYER_PRESET_PREFIX}coffee",
                title = "그늘집 커피 · 음료 쏘기",
                labelsAndWeights = playerPairs,
                palette = RouletteColorPalette.HILLSIDE_WINDMILL,
                spinDurationSec = safeDuration,
                decisionSound = RouletteDecisionSound.WOOD_CLAPPER,
                timestamp = now
            ),
            createTemplate(
                id = "${WINDMILL_PLAYER_PRESET_PREFIX}snack",
                title = "그늘집 간식 · 만두 쏘기",
                labelsAndWeights = playerPairs,
                palette = RouletteColorPalette.WARM_PICNIC,
                spinDurationSec = (safeDuration + 1).coerceIn(3, 20),
                specialEffect = RouletteSpecialEffect.FAKE_SHIFT,
                effectFrequency = RouletteEffectFrequency.NORMAL,
                decisionSound = RouletteDecisionSound.FANFARE,
                timestamp = now - 1000L
            ),
            createTemplate(
                id = "${WINDMILL_PLAYER_PRESET_PREFIX}caddie",
                title = "캐디피 · 카트비 끝전 담당",
                labelsAndWeights = playerPairs,
                palette = RouletteColorPalette.MEADOW_WOOD,
                spinDurationSec = (safeDuration + 2).coerceIn(3, 20),
                specialEffect = RouletteSpecialEffect.SHIFT_1_AFTER,
                effectFrequency = RouletteEffectFrequency.RARE,
                decisionSound = RouletteDecisionSound.HEARTBEAT_CHIME,
                timestamp = now - 2000L
            ),
            createTemplate(
                id = "${WINDMILL_PLAYER_PRESET_PREFIX}cart",
                title = "카트 · 팀 순번 하나씩 뽑기",
                labelsAndWeights = playerPairs,
                palette = RouletteColorPalette.HILLSIDE_WINDMILL,
                spinDurationSec = safeDuration,
                removeOnceChosen = true,
                decisionSound = RouletteDecisionSound.CHIME_BELL,
                timestamp = now - 3000L
            ),
            createTemplate(
                id = "windmill_tpl_shadehouse_menu",
                title = "그늘집 인기 간식 메뉴",
                labelsAndWeights = listOf(
                    "수제 고기·김치만두" to 1,
                    "바삭 해물파전·도토리묵" to 1,
                    "옛날 떡볶이·순대 모둠" to 1,
                    "얼음 동동 식혜·미숫가루" to 1,
                    "시원한 제철 과일 접시" to 1,
                    "아이스 아메리카노 전원" to 1
                ),
                palette = RouletteColorPalette.WARM_PICNIC,
                spinDurationSec = 5,
                timestamp = now - 4000L
            ),
            createTemplate(
                id = "windmill_tpl_19th_dinner",
                title = "라운딩 후 19홀 뒤풀이 메뉴",
                labelsAndWeights = listOf(
                    "한우 등심·갈비살 구이" to 1,
                    "토종 백숙·오리불고기" to 1,
                    "제철 활어회·매운탕" to 1,
                    "참숯 삼겹살·목살" to 1,
                    "얼큰 버섯전골·칼국수" to 1,
                    "장어구이 몸보신" to 1
                ),
                palette = RouletteColorPalette.HILLSIDE_WINDMILL,
                spinDurationSec = 6,
                timestamp = now - 5000L
            ),
            createTemplate(
                id = "windmill_tpl_hole_event",
                title = "이번 홀 스페셜 미션 풍차",
                labelsAndWeights = listOf(
                    "버디 성공 시 축하 보너스" to 1,
                    "니어핀 1위 음료 면제" to 1,
                    "롱기스트 티샷 주인공" to 1,
                    "전원 온그린 협동 미션" to 1,
                    "해저드·벙커 탈출 응원" to 1,
                    "평화로운 기본 스트로크" to 2
                ),
                palette = RouletteColorPalette.MEADOW_WOOD,
                spinDurationSec = 6,
                specialEffect = RouletteSpecialEffect.RANDOM,
                effectFrequency = RouletteEffectFrequency.RARE,
                timestamp = now - 6000L
            ),
            createTemplate(
                id = "windmill_tpl_club_strategy",
                title = "고민될 때 공략 · 클럽 결정",
                labelsAndWeights = listOf(
                    "자신 있게 드라이버 정공법" to 1,
                    "안전하게 우드·유틸리티" to 1,
                    "핀 직접 겨냥 공격 샷" to 1,
                    "그린 중앙 안전 레이업" to 1,
                    "가볍게 스리쿼터 리듬 스윙" to 1
                ),
                palette = RouletteColorPalette.NORDIC_SLATE,
                spinDurationSec = 4,
                timestamp = now - 7000L
            ),
            createTemplate(
                id = "windmill_tpl_club_prize",
                title = "월례회 · 모임 경품 가중치 추첨",
                labelsAndWeights = listOf(
                    "1등 거리측정기·보스턴백" to 1,
                    "2등 프리미엄 볼 1더즌" to 2,
                    "3등 양피 골프장갑" to 4,
                    "행운상 그늘집 커피 쿠폰" to 8
                ),
                palette = RouletteColorPalette.HILLSIDE_WINDMILL,
                spinDurationSec = 8,
                specialEffect = RouletteSpecialEffect.BLACK_OUT,
                effectFrequency = RouletteEffectFrequency.OFTEN,
                decisionSound = RouletteDecisionSound.FANFARE,
                timestamp = now - 8000L
            ),
            createTemplate(
                id = "windmill_tpl_mulligan_ok",
                title = "멀리건 · 컨시드 인정 풍차",
                labelsAndWeights = listOf(
                    "기분 좋게 멀리건 인정!" to 2,
                    "쿨하게 오케이(OK) 인정!" to 2,
                    "이번 홀은 정정당당 노멀리건" to 1,
                    "동반자 전원 박수 응원" to 1
                ),
                palette = RouletteColorPalette.SOFT_WATERCOLOR,
                spinDurationSec = 4,
                timestamp = now - 9000L
            )
        )
    }

    private fun createTemplate(
        id: String,
        title: String,
        labelsAndWeights: List<Pair<String, Int>>,
        palette: RouletteColorPalette,
        spinDurationSec: Int,
        removeOnceChosen: Boolean = false,
        specialEffect: RouletteSpecialEffect = RouletteSpecialEffect.NONE,
        effectFrequency: RouletteEffectFrequency = RouletteEffectFrequency.NORMAL,
        decisionSound: RouletteDecisionSound = RouletteDecisionSound.FANFARE,
        timestamp: Long
    ): RouletteWheelPreset {
        val slices = labelsAndWeights.mapIndexed { idx, (label, weight) ->
            RouletteSliceItem(
                id = "${id}_item_$idx",
                label = label,
                weight = weight,
                colorArgb = palette.colorAt(idx)
            )
        }
        return RouletteWheelPreset(
            id = id,
            title = title,
            items = slices,
            spinDurationSec = spinDurationSec,
            removeOnceChosen = removeOnceChosen,
            specialEffect = specialEffect,
            effectFrequency = effectFrequency,
            decisionSound = decisionSound,
            palette = palette,
            updatedAtMillis = timestamp
        )
    }
}

/**
 * 외부 라이브러리 및 Android 스텁 의존성 제로(Zero-Dependency) 순수 Kotlin 직렬화 코덱
 * Base64 UTF-8 필드 인코딩으로 줄바꿈·구분자·특수문자가 포함된 항목도 100% 무손실 저장·복원
 */
object RouletteStorageCodec {
    private const val PRESET_HEADER = "RWP1"
    private const val HISTORY_HEADER = "RWH1"

    private fun b64Encode(text: String): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(text.toByteArray(Charsets.UTF_8))

    private fun b64Decode(encoded: String): String =
        runCatching {
            String(Base64.getUrlDecoder().decode(encoded), Charsets.UTF_8)
        }.getOrDefault("")

    fun encodePreset(preset: RouletteWheelPreset): String {
        val itemsBlob = preset.items.joinToString(",") { item ->
            listOf(
                b64Encode(item.id),
                b64Encode(item.label),
                item.safeWeight.toString(),
                item.colorArgb.toString(),
                if (item.isExcluded) "1" else "0",
                b64Encode(item.secretNote)
            ).joinToString(":")
        }
        return listOf(
            PRESET_HEADER,
            b64Encode(preset.id),
            b64Encode(preset.title),
            preset.normalizedDurationSec.toString(),
            if (preset.tapToStop) "1" else "0",
            if (preset.removeOnceChosen) "1" else "0",
            if (preset.mysteryMaskOnWheel) "1" else "0",
            preset.specialEffect.name,
            preset.effectFrequency.name,
            preset.decisionSound.name,
            preset.palette.name,
            b64Encode(preset.passcodePin),
            preset.playCount.toString(),
            preset.updatedAtMillis.toString(),
            itemsBlob
        ).joinToString("|")
    }

    fun decodePreset(line: String): RouletteWheelPreset? {
        val parts = line.trim().split("|")
        if (parts.size < 15 || parts[0] != PRESET_HEADER) return null
        return runCatching {
            val palette = runCatching { RouletteColorPalette.valueOf(parts[10]) }
                .getOrDefault(RouletteColorPalette.MEADOW_WOOD)
            val itemsRaw = parts[14]
            val items = if (itemsRaw.isBlank()) {
                emptyList()
            } else {
                itemsRaw.split(",").mapIndexedNotNull { idx, token ->
                    val f = token.split(":")
                    if (f.size < 5) null else {
                        RouletteSliceItem(
                            id = b64Decode(f[0]).ifBlank { UUID.randomUUID().toString() },
                            label = b64Decode(f[1]),
                            weight = (f[2].toIntOrNull() ?: 1).coerceIn(1, 20),
                            colorArgb = f[3].toIntOrNull() ?: palette.colorAt(idx),
                            isExcluded = f[4] == "1",
                            secretNote = if (f.size >= 6) b64Decode(f[5]) else ""
                        )
                    }
                }
            }
            if (items.size < 2) return null
            RouletteWheelPreset(
                id = b64Decode(parts[1]).ifBlank { UUID.randomUUID().toString() },
                title = b64Decode(parts[2]).ifBlank { "나만의 룰렛" },
                items = items,
                spinDurationSec = (parts[3].toIntOrNull() ?: 5).coerceIn(3, 20),
                tapToStop = parts[4] == "1",
                removeOnceChosen = parts[5] == "1",
                mysteryMaskOnWheel = parts[6] == "1",
                specialEffect = runCatching { RouletteSpecialEffect.valueOf(parts[7]) }
                    .getOrDefault(RouletteSpecialEffect.NONE),
                effectFrequency = runCatching { RouletteEffectFrequency.valueOf(parts[8]) }
                    .getOrDefault(RouletteEffectFrequency.NORMAL),
                decisionSound = runCatching { RouletteDecisionSound.valueOf(parts[9]) }
                    .getOrDefault(RouletteDecisionSound.FANFARE),
                palette = palette,
                passcodePin = b64Decode(parts[11]),
                playCount = (parts[12].toIntOrNull() ?: 0).coerceAtLeast(0),
                updatedAtMillis = parts[13].toLongOrNull() ?: System.currentTimeMillis()
            )
        }.getOrNull()
    }

    fun encodePresetList(presets: List<RouletteWheelPreset>): String =
        presets.joinToString("\n") { encodePreset(it) }

    fun decodePresetList(text: String): List<RouletteWheelPreset> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { decodePreset(it) }
            .toList()

    fun encodeHistoryList(records: List<RouletteHistoryRecord>): String =
        records.joinToString("\n") { rec ->
            listOf(
                HISTORY_HEADER,
                b64Encode(rec.id),
                b64Encode(rec.wheelTitle),
                b64Encode(rec.winnerLabel),
                rec.winnerColorArgb.toString(),
                rec.probabilityPercent.toString(),
                b64Encode(rec.specialEffectTitle),
                b64Encode(rec.secretNote),
                rec.createdAtMillis.toString()
            ).joinToString("|")
        }

    fun decodeHistoryList(text: String): List<RouletteHistoryRecord> =
        text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val p = line.split("|")
                if (p.size < 9 || p[0] != HISTORY_HEADER) null else {
                    runCatching {
                        RouletteHistoryRecord(
                            id = b64Decode(p[1]).ifBlank { UUID.randomUUID().toString() },
                            wheelTitle = b64Decode(p[2]),
                            winnerLabel = b64Decode(p[3]),
                            winnerColorArgb = p[4].toIntOrNull() ?: RouletteColorPalette.MEADOW_WOOD.colorAt(0),
                            probabilityPercent = p[5].toFloatOrNull() ?: 0f,
                            specialEffectTitle = b64Decode(p[6]),
                            secretNote = b64Decode(p[7]),
                            createdAtMillis = p[8].toLongOrNull() ?: 0L
                        )
                    }.getOrNull()
                }
            }
            .toList()
}
