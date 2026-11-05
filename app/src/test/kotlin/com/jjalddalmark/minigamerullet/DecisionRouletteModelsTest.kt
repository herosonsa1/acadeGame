package com.jjalddalmark.minigamerullet

import com.jjalddalmark.minigamerullet.audio.AcousticBgmSynthesizer
import com.jjalddalmark.minigamerullet.audio.BgmMoodStage
import com.jjalddalmark.minigamerullet.audio.BgmTheme
import com.jjalddalmark.minigamerullet.model.BuiltInRouletteTemplates
import com.jjalddalmark.minigamerullet.model.RouletteColorPalette
import com.jjalddalmark.minigamerullet.model.RouletteDecisionSound
import com.jjalddalmark.minigamerullet.model.RouletteEffectFrequency
import com.jjalddalmark.minigamerullet.model.RouletteEngine
import com.jjalddalmark.minigamerullet.model.RouletteHistoryRecord
import com.jjalddalmark.minigamerullet.model.RouletteSliceItem
import com.jjalddalmark.minigamerullet.model.RouletteSpecialEffect
import com.jjalddalmark.minigamerullet.model.RouletteSpinResult
import com.jjalddalmark.minigamerullet.model.RouletteSpinStage
import com.jjalddalmark.minigamerullet.model.RouletteStorageCodec
import com.jjalddalmark.minigamerullet.model.RouletteWheelPreset
import com.jjalddalmark.minigamerullet.share.RouletteExportVisualStyle
import com.jjalddalmark.minigamerullet.share.RouletteShareManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class DecisionRouletteModelsTest {

    @Test
    fun verifyBuiltInEverydayTemplatesAreGolfIndependentAndValid() {
        val templates = BuiltInRouletteTemplates.defaultTemplates()
        assertEquals(10, templates.size)
        templates.forEach { tpl ->
            assertTrue(tpl.title.isNotBlank())
            assertTrue(tpl.items.size >= 4)
            assertFalse(tpl.title.contains("캐디백"))
            val arcs = RouletteEngine.computeSliceArcs(tpl.activeItems)
            assertEquals(tpl.activeItems.size, arcs.size)
            val sumSweep = arcs.sumOf { it.sweepDeg.toDouble() }.toFloat()
            assertEquals(360f, sumSweep, 0.05f)
            val sumPct = arcs.sumOf { it.probabilityPercent.toDouble() }.toFloat()
            assertEquals(100f, sumPct, 0.05f)
        }
    }

    @Test
    fun verifyWeightedProbabilityAndPointerAngleResolution() {
        val items = listOf(
            RouletteSliceItem(id = "a", label = "1등 경품", weight = 1),
            RouletteSliceItem(id = "b", label = "2등 상품권", weight = 3),
            RouletteSliceItem(id = "c", label = "3등 쿠폰", weight = 6)
        )
        val arcs = RouletteEngine.computeSliceArcs(items)
        assertEquals(3, arcs.size)
        assertEquals(36f, arcs[0].sweepDeg, 0.01f)
        assertEquals(108f, arcs[1].sweepDeg, 0.01f)
        assertEquals(216f, arcs[2].sweepDeg, 0.01f)
        assertEquals(10f, arcs[0].probabilityPercent, 0.01f)
        assertEquals(30f, arcs[1].probabilityPercent, 0.01f)
        assertEquals(60f, arcs[2].probabilityPercent, 0.01f)

        // 각 조각 중앙을 12시 포인터(270도)로 맞추는 목표 회전 각도 정확성 검증
        arcs.forEach { arc ->
            val rot = RouletteEngine.computeClockwiseTargetRotation(
                fromRotationDeg = 15f,
                targetLocalDeg = arc.midDeg,
                minExtraSpins = 5
            )
            assertTrue(rot > 15f + 5 * 360f - 0.01f)
            assertEquals(arc.index, RouletteEngine.resolveSliceIndexAtRotation(arcs, rot))
        }

        // 몬테카를로 5000회 추첨 시 가중치 비율(1:3:6) 수렴 검증
        val rng = Random(20261029)
        val counts = IntArray(3)
        repeat(5000) {
            counts[RouletteEngine.pickWeightedArcIndex(arcs, rng)]++
        }
        assertTrue(counts[0] in 380..620)
        assertTrue(counts[1] in 1320..1680)
        assertTrue(counts[2] in 2800..3200)
    }

    @Test
    fun verifyAllTwelveSpecialEffectsProduceConsistentSpinPlans() {
        assertEquals(12, RouletteSpecialEffect.entries.size)
        val basePreset = BuiltInRouletteTemplates.defaultTemplates().first().copy(
            effectFrequency = RouletteEffectFrequency.ALWAYS
        )
        val n = basePreset.activeItems.size

        RouletteSpecialEffect.entries.forEachIndexed { idx, effect ->
            val preset = basePreset.copy(specialEffect = effect)
            val plan = RouletteEngine.buildSpinPlan(
                preset = preset,
                initialRotationDeg = 42f,
                rng = Random(1000L + idx)
            )
            val endInst = plan.evaluateAt(plan.totalDurationSec)
            assertEquals(plan.finalTargetRotationDeg, endInst.wheelRotationDeg, 0.01f)
            assertEquals(
                plan.finalArcIndex,
                RouletteEngine.resolveSliceIndexAtRotation(plan.arcs, plan.finalTargetRotationDeg)
            )

            when (effect) {
                RouletteSpecialEffect.NONE -> {
                    assertEquals(plan.primaryArcIndex, plan.finalArcIndex)
                    assertEquals(0f, plan.twistMoveSec, 0.001f)
                }
                RouletteSpecialEffect.SHIFT_1_AFTER ->
                    assertEquals((plan.primaryArcIndex + 1) % n, plan.finalArcIndex)
                RouletteSpecialEffect.SHIFT_2_AFTER ->
                    assertEquals((plan.primaryArcIndex + 2) % n, plan.finalArcIndex)
                RouletteSpecialEffect.SHIFT_3_AFTER ->
                    assertEquals((plan.primaryArcIndex + 3) % n, plan.finalArcIndex)
                RouletteSpecialEffect.SHIFT_1_BEFORE ->
                    assertEquals((plan.primaryArcIndex - 1 + n) % n, plan.finalArcIndex)
                RouletteSpecialEffect.SHIFT_2_BEFORE ->
                    assertEquals((plan.primaryArcIndex - 2 + n) % n, plan.finalArcIndex)
                RouletteSpecialEffect.SHIFT_3_BEFORE ->
                    assertEquals((plan.primaryArcIndex - 3 + n) % n, plan.finalArcIndex)
                RouletteSpecialEffect.FAKE_SHIFT ->
                    assertEquals(plan.primaryArcIndex, plan.finalArcIndex)
                RouletteSpecialEffect.BLACK_OUT -> {
                    val midLate = plan.evaluateAt(plan.mainSpinDurationSec * 0.80f)
                    assertTrue(midLate.isBlackOut)
                }
                else -> Unit
            }

            val samples = RouletteEngine.sampleTrajectoryFromPlan(plan, fps = 24)
            assertTrue(samples.size >= 24)
            assertEquals(plan.finalTargetRotationDeg, samples.last().wheelRotationDeg, 0.05f)
        }
    }

    @Test
    fun verifyBulkItemParserAndNumberGenerator() {
        val raw = "김치찌개 * 3, 돈까스:2\n파스타; 마라탕 x 5"
        val parsed = RouletteEngine.parseBulkItems(raw, RouletteColorPalette.WARM_PICNIC)
        assertEquals(4, parsed.size)
        assertEquals("김치찌개", parsed[0].label)
        assertEquals(3, parsed[0].safeWeight)
        assertEquals("돈까스", parsed[1].label)
        assertEquals(2, parsed[1].safeWeight)
        assertEquals("파스타", parsed[2].label)
        assertEquals(1, parsed[2].safeWeight)
        assertEquals("마라탕", parsed[3].label)
        assertEquals(5, parsed[3].safeWeight)

        val numbers = RouletteEngine.generateNumberItems(1, 12, "번")
        assertEquals(12, numbers.size)
        assertEquals("1번", numbers.first().label)
        assertEquals("12번", numbers.last().label)
    }

    @Test
    fun verifyStorageCodecLosslessRoundTripWithSpecialCharacters() {
        val trickyPreset = RouletteWheelPreset(
            id = "custom_id_01",
            title = "금요일 | 회식 : 메뉴, 결정!",
            items = listOf(
                RouletteSliceItem(
                    id = "s1",
                    label = "삼겹살 | 목살:세트",
                    weight = 4,
                    colorArgb = 0xFFB85435.toInt(),
                    isExcluded = true,
                    secretNote = "볶음밥 필수 | 사이다 포함"
                ),
                RouletteSliceItem(
                    id = "s2",
                    label = "치킨, 맥주",
                    weight = 2,
                    colorArgb = 0xFF386E9B.toInt(),
                    isExcluded = false,
                    secretNote = ""
                ),
                RouletteSliceItem(
                    id = "s3",
                    label = "초밥 모둠",
                    weight = 1,
                    colorArgb = 0xFF2F6F4E.toInt(),
                    isExcluded = false,
                    secretNote = "우동 서비스"
                )
            ),
            spinDurationSec = 12,
            tapToStop = true,
            removeOnceChosen = true,
            mysteryMaskOnWheel = true,
            specialEffect = RouletteSpecialEffect.SHIFT_180_DEGREES,
            effectFrequency = RouletteEffectFrequency.OFTEN,
            decisionSound = RouletteDecisionSound.WOOD_CLAPPER,
            palette = RouletteColorPalette.NORDIC_SLATE,
            passcodePin = "2580",
            playCount = 7,
            updatedAtMillis = 1765432100000L
        )

        val encoded = RouletteStorageCodec.encodePresetList(listOf(trickyPreset))
        val decoded = RouletteStorageCodec.decodePresetList(encoded)
        assertEquals(1, decoded.size)
        val restored = decoded.first()
        assertEquals(trickyPreset, restored)
        assertTrue(restored.isLocked)
        // s1이 제외 상태이므로 activeItems는 s2, s3 2개가 남아야 함
        assertEquals(2, restored.activeItems.size)
        assertEquals(3, restored.resetExcludedItems().activeItems.size)

        val history = listOf(
            RouletteHistoryRecord(
                id = "h1",
                wheelTitle = trickyPreset.title,
                winnerLabel = "삼겹살 | 목살:세트",
                winnerColorArgb = 0xFFB85435.toInt(),
                probabilityPercent = 57.1f,
                specialEffectTitle = "이동 180도",
                secretNote = "볶음밥 필수 | 사이다 포함",
                createdAtMillis = 1765432199000L
            )
        )
        val historyDecoded = RouletteStorageCodec.decodeHistoryList(
            RouletteStorageCodec.encodeHistoryList(history)
        )
        assertEquals(history, historyDecoded)
    }

    @Test
    fun verifyShareSummaryTextFormatting() {
        val tpl = BuiltInRouletteTemplates.defaultTemplates().first()
        val arcs = RouletteEngine.computeSliceArcs(tpl.activeItems)
        val winnerArc = arcs.first().copy(
            item = arcs.first().item.copy(secretNote = "곱빼기 주문 가능")
        )
        val result = RouletteSpinResult(
            presetSnapshot = tpl,
            arcs = arcs,
            primaryArcIndex = 1,
            winnerArcIndex = 0,
            winnerItem = winnerArc.item,
            winnerProbabilityPercent = winnerArc.probabilityPercent,
            appliedSpecialEffect = RouletteSpecialEffect.SHIFT_1_BEFORE,
            finalWheelRotationDeg = 1800f,
            trajectory = emptyList()
        )
        val imgSummary = RouletteShareManager.buildShareSummaryText(result, isVideo = false)
        val vidSummary = RouletteShareManager.buildShareSummaryText(result, isVideo = true)
        assertNotNull(imgSummary)
        assertTrue(imgSummary.contains("미니게임 - 만능 룰렛 & 골프 복불복"))
        assertTrue(imgSummary.contains("추첨 결과 카드"))
        assertTrue(imgSummary.contains("이동 1번 뒤로"))
        assertTrue(imgSummary.contains("곱빼기 주문 가능"))
        assertTrue(vidSummary.contains("회전 전 과정 영상"))
    }

    @Test
    fun verifyHillsideWindmillTemplatesAndPlayerSync() {
        val initialPlayers = listOf("김프로", "박원장", "이대표", "최싱글")
        val windmillTemplates = BuiltInRouletteTemplates.defaultWindmillTemplates(
            playerNames = initialPlayers,
            defaultDurationSec = 6
        )
        assertEquals(10, windmillTemplates.size)

        val playerDriven = windmillTemplates.filter {
            BuiltInRouletteTemplates.isPlayerSyncedWindmillPreset(it)
        }
        val nonPlayerDriven = windmillTemplates.filterNot {
            BuiltInRouletteTemplates.isPlayerSyncedWindmillPreset(it)
        }
        assertEquals(4, playerDriven.size)
        assertEquals(6, nonPlayerDriven.size)

        val coffeePreset = playerDriven.first()
        assertEquals(RouletteColorPalette.HILLSIDE_WINDMILL, coffeePreset.palette)
        assertEquals(initialPlayers, coffeePreset.items.map { it.label })

        // 가중치를 수정한 뒤 선수입장 명단이 5명으로 변경되어도 기존 선수 가중치 보존 및 신규 명단 동기화 검증
        val customWeightedCoffee = coffeePreset.copy(
            items = coffeePreset.items.mapIndexed { idx, item ->
                if (idx == 0) item.copy(weight = 3, secretNote = "오늘 오너") else item
            }
        )
        val updatedPlayers = listOf("김프로", "정회장", "박원장", "강선배", "윤대표")
        val syncedCoffee = BuiltInRouletteTemplates.syncPlayerDrivenPreset(
            preset = customWeightedCoffee,
            playerNames = updatedPlayers
        )
        assertEquals(updatedPlayers, syncedCoffee.items.map { it.label })
        assertEquals(3, syncedCoffee.items.first { it.label == "김프로" }.safeWeight)
        assertEquals("오늘 오너", syncedCoffee.items.first { it.label == "김프로" }.secretNote)

        // 일반 그늘집 간식 메뉴 템플릿은 선수 명단 변경 시에도 원래 메뉴 항목을 그대로 유지해야 함
        val menuPreset = nonPlayerDriven.first()
        val unchangedMenu = BuiltInRouletteTemplates.syncPlayerDrivenPreset(
            preset = menuPreset,
            playerNames = updatedPlayers
        )
        assertEquals(menuPreset.items, unchangedMenu.items)

        // HILLSIDE_WINDMILL 팔레트 직렬화 왕복 보존 검증
        val encoded = RouletteStorageCodec.encodePreset(syncedCoffee)
        val decoded = RouletteStorageCodec.decodePreset(encoded)
        assertEquals(syncedCoffee, decoded)
    }

    @Test
    fun verifyDecisionWindmillExportStyleAndDefaultPalette() {
        assertTrue(RouletteExportVisualStyle.DECISION_WINDMILL.usesWindmillScene)
        assertTrue(RouletteExportVisualStyle.HILLSIDE_WINDMILL.usesWindmillScene)
        assertFalse(RouletteExportVisualStyle.STUDIO_DECISION.usesWindmillScene)
        assertEquals("DECISION ROULETTE+", RouletteExportVisualStyle.DECISION_WINDMILL.headerBadgeText)

        val defaultPreset = RouletteWheelPreset(
            title = "기본",
            items = listOf(RouletteSliceItem(label = "항목"))
        )
        assertEquals(RouletteColorPalette.HILLSIDE_WINDMILL, defaultPreset.palette)
        val lunchTemplate = BuiltInRouletteTemplates.defaultTemplates().first()
        assertEquals(RouletteColorPalette.HILLSIDE_WINDMILL, lunchTemplate.palette)
    }

    @Test
    fun verifyNarrativeHealingBgmWarmthLengthAndStages() {
        // 5개 테마 × 3단계 서사 악장 = 총 15곡 전수 검증
        for (theme in BgmTheme.entries) {
            for (mood in BgmMoodStage.entries) {
                val score = AcousticBgmSynthesizer.buildScore(theme, mood)
                // 짧은 8~12초 반복이 아닌 16마디(64박, 약 34~45초) 장편 서사 구조 확인
                assertEquals(64.0, score.totalBeats, 0.001)
                val durationSec = score.totalBeats * 60.0 / score.bpm
                assertTrue("악장 길이는 최소 33초 이상이어야 함: $theme/$mood ($durationSec)", durationSec >= 33.0)

                // 귀가 따가운 고음(MIDI 73 이상) 완전 배제 검증 (선율·화음 악기 0..4)
                val tonalEvents = score.notes.filter { it.instrument in 0..4 }
                assertTrue(tonalEvents.isNotEmpty())
                val maxMidi = tonalEvents.maxOf { it.midi }
                assertTrue("모든 선율·화성 음역은 C5(MIDI 72) 이하여야 함: $theme/$mood max=$maxMidi", maxMidi <= 72)

                // 전반부(0..32박)와 후반부(32..64박)가 단순 복붙 반복이 아닌 A->A'->B->C 전개인지 확인
                val firstHalf = tonalEvents.filter { it.startBeat < 32.0 }
                    .map { Triple(it.instrument, it.midi, (it.startBeat * 4).toInt()) }
                val secondHalf = tonalEvents.filter { it.startBeat >= 32.0 }
                    .map { Triple(it.instrument, it.midi, ((it.startBeat - 32.0) * 4).toInt()) }
                assertFalse("전반부와 후반부 편곡이 달라야 함: $theme/$mood", firstHalf == secondHalf)

                // 단계별 리듬 특성 확인: 평온한 시작에는 타악기 없음, 진행·기쁨 단계에는 심장박동 퍼커션 포함
                val percussionEvents = score.notes.filter { it.instrument == 5 }
                if (mood == BgmMoodStage.CALM_INTRO) {
                    assertTrue("평온한 시작 악장에는 타악기가 없어야 함", percussionEvents.isEmpty())
                } else {
                    assertTrue("진행 및 기쁨 악장에는 따뜻한 퍼커션이 포함되어야 함", percussionEvents.isNotEmpty())
                }
            }
        }

        // 대표 테마 3단계 실제 PCM 파형 합성 및 클릭 노이즈 없는 루프 경계 검증
        for (mood in BgmMoodStage.entries) {
            val pcm = AcousticBgmSynthesizer.synthesizeTrackPcm(BgmTheme.HOME_MEADOW, mood)
            assertTrue("PCM 샘플 수가 33초 이상이어야 함", pcm.size >= 33 * AcousticBgmSynthesizer.BGM_SAMPLE_RATE)
            val peak = pcm.maxOf { abs(it.toInt()) }
            assertTrue("무음이 아니어야 하며 과도한 클리핑이 없어야 함: peak=$peak", peak in 2500..31500)
            // 루프 끝 잔향 폴딩 및 필터 워밍업으로 루프 끝 샘플과 첫 샘플 사이 불연속 점프(틱 잡음)가 없어야 함
            val seamDelta = abs(pcm.first().toInt() - pcm.last().toInt())
            assertTrue("루프 경계 샘플 간 급격한 점프가 없어야 함: delta=$seamDelta", seamDelta < 2200)
        }
    }
}
