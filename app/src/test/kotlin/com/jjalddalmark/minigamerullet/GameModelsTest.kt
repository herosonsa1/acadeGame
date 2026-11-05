package com.jjalddalmark.minigamerullet

import com.jjalddalmark.minigamerullet.model.AppSettings
import com.jjalddalmark.minigamerullet.model.DiceGameLogic
import com.jjalddalmark.minigamerullet.model.DicePlayerRoll
import com.jjalddalmark.minigamerullet.model.GameCategory
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.KoreanGolfNameHelper
import com.jjalddalmark.minigamerullet.model.TarotFortuneCatalog
import com.jjalddalmark.minigamerullet.model.TarotTopic
import com.jjalddalmark.minigamerullet.model.YutGameLogic
import com.jjalddalmark.minigamerullet.model.YutOutcome
import com.jjalddalmark.minigamerullet.model.YutPlayerState
import com.jjalddalmark.minigamerullet.model.YutSingleThrow
import com.jjalddalmark.minigamerullet.ui.components.mediaSpec
import com.jjalddalmark.minigamerullet.ui.theme.RankBadgeColors
import com.jjalddalmark.minigamerullet.ui.theme.distinctRankBadgeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class GameModelsTest {
    @Test
    fun verifyElevenGamesIncludingDecisionRouletteConfigured() {
        assertEquals(11, GolfGameType.entries.size)
        assertEquals(1, GolfGameType.entries.count { it.category == GameCategory.DECISION_PICKER })
        assertEquals(3, GolfGameType.entries.count { it.category == GameCategory.TEE_ORDER })
        assertEquals(4, GolfGameType.entries.count { it.category == GameCategory.COFFEE_PENALTY })
        assertEquals(3, GolfGameType.entries.count { it.category == GameCategory.BOARD_FORTUNE })
    }

    @Test
    fun verifyDefaultCaddieBagAndParticipantPresets() {
        val defaults = AppSettings()
        assertEquals(
            listOf("1번캐디백", "2번캐디백", "3번캐디백", "4번캐디백"),
            defaults.players.map { it.name }
        )
        assertEquals(
            listOf("1번참가자", "2번참가자", "3번참가자", "4번참가자"),
            KoreanGolfNameHelper.defaultParticipantNames(4)
        )

        // 빈 문자열 슬롯은 자동으로 N번캐디백으로 복구되는지 검증
        val withBlank = AppSettings(playerNames = listOf("김프로", "", "이대표", "최싱글"))
        assertEquals("2번캐디백", withBlank.players[1].name)
        assertTrue(withBlank.slowMotionEnabled)

        // 타로 1인 모드 후 2인 이상 게임 전환 시 자동 최소 인원 복원 검증
        val solo = AppSettings(playerNames = listOf("김프로"))
        assertEquals(1, solo.ensureMinPlayersForGame(GolfGameType.TAROT_FORTUNE).players.size)
        assertEquals(2, solo.ensureMinPlayersForGame(GolfGameType.DOUBLE_DICE).players.size)
    }

    @Test
    fun verifyTwoClickSurnameAndTitleCombination() {
        assertEquals(11, KoreanGolfNameHelper.recommendedSurnames.size)
        assertEquals(6, KoreanGolfNameHelper.recommendedTitles.size)

        val step1 = KoreanGolfNameHelper.applySurname("김씨")
        assertEquals("김씨", step1)

        val step2 = KoreanGolfNameHelper.applyTitle(step1, "프로")
        assertEquals("김프로", step2)

        assertEquals("김회장", KoreanGolfNameHelper.applyTitle(step2, "회장"))
        assertEquals("박대표", KoreanGolfNameHelper.applyTitle("박씨", "대표"))
        assertEquals("이선배", KoreanGolfNameHelper.applyTitle("이씨", "선배"))
        assertEquals("정사장", KoreanGolfNameHelper.applyTitle("정씨", "사장"))
        assertEquals("최선수", KoreanGolfNameHelper.applyTitle("최씨", "선수"))
    }

    @Test
    fun verifyDistinctRankBadgeColorsAndDurationSettings() {
        assertEquals(6, RankBadgeColors.size)
        assertEquals(6, RankBadgeColors.toSet().size)
        for (i in 0 until 6) {
            for (j in i + 1 until 6) {
                assertNotEquals(distinctRankBadgeColor(i), distinctRankBadgeColor(j))
            }
        }

        assertEquals(5, AppSettings(gameDurationSeconds = 5).normalizedDurationSeconds)
        assertEquals(10, AppSettings(gameDurationSeconds = 10).normalizedDurationSeconds)
        assertEquals(15, AppSettings(gameDurationSeconds = 15).normalizedDurationSeconds)
        assertEquals(10, AppSettings(gameDurationSeconds = 9).normalizedDurationSeconds)
    }

    @Test
    fun verifyBgmOnlyMuteToggleBehavior() {
        val initial = AppSettings()
        assertFalse(initial.isBgmMuted)
        assertTrue(initial.bgmEnabled)
        assertTrue(initial.sfxEnabled)
        assertTrue(initial.heartbeatEnabled)
        assertTrue(initial.resultFanfareEnabled)

        val mutedOnce = initial.toggleBgmOnlyMute()
        assertTrue(mutedOnce.isBgmMuted)
        assertFalse(mutedOnce.bgmEnabled)
        assertTrue(mutedOnce.soundEnabled)
        assertTrue(mutedOnce.sfxEnabled)
        assertTrue(mutedOnce.heartbeatEnabled)
        assertTrue(mutedOnce.resultFanfareEnabled)

        val unmutedAgain = mutedOnce.toggleBgmOnlyMute()
        assertFalse(unmutedAgain.isBgmMuted)
        assertTrue(unmutedAgain.bgmEnabled)
        assertTrue(unmutedAgain.bgmVolume > 0.05f)
        assertTrue(unmutedAgain.sfxEnabled)

        val zeroVolMuted = AppSettings(bgmEnabled = true, bgmVolume = 0f)
        assertTrue(zeroVolMuted.isBgmMuted)
        val restoredFromZero = zeroVolMuted.toggleBgmOnlyMute()
        assertFalse(restoredFromZero.isBgmMuted)
        assertTrue(restoredFromZero.bgmEnabled)
        assertEquals(0.65f, restoredFromZero.bgmVolume, 0.001f)
    }

    @Test
    fun verifyAllTenGamesHaveValidVideoMediaSpecs() {
        assertTrue(AppSettings().videoEffectsEnabled)
        GolfGameType.entries.forEach { game ->
            val spec = game.mediaSpec()
            assertNotEquals(0, spec.introVideoRes)
            assertNotEquals(0, spec.introPosterRes)
            assertNotEquals(0, spec.previewVideoRes)
            assertNotEquals(0, spec.previewPosterRes)
            assertNotEquals(0, spec.gameplayVideoRes)
            assertNotEquals(0, spec.gameplayPosterRes)
            assertNotEquals(0, spec.resultVideoRes)
            assertNotEquals(0, spec.resultPosterRes)
            assertTrue(spec.introCaption.isNotBlank())
        }
    }

    @Test
    fun verifyDoubleDiceHigherSumWinsAndTieBreakWorks() {
        val rolls = listOf(
            DicePlayerRoll(playerIndex = 0, die1 = 4, die2 = 4), // 합 8 (더블)
            DicePlayerRoll(playerIndex = 1, die1 = 6, die2 = 5), // 합 11 -> 1위 승리
            DicePlayerRoll(playerIndex = 2, die1 = 5, die2 = 3), // 합 8 (일반, 더블에 밀려 3위)
            DicePlayerRoll(playerIndex = 3, die1 = 2, die2 = 2)  // 합 4 -> 4위 꼴등
        )
        val ranked = DiceGameLogic.rankRolls(rolls, Random(7))
        assertEquals(listOf(1, 0, 2, 3), ranked.map { it.roll.playerIndex })
        assertEquals("1위 승리", ranked.first().badgeLabel)
        assertEquals("4위 꼴등", ranked.last().badgeLabel)
    }

    @Test
    fun verifyYutOutcomesCaptureBonusAndWinnerLoserSplit() {
        assertEquals(YutOutcome.MO, YutGameLogic.evaluateSticks(listOf(true, true, true, true)))
        assertEquals(YutOutcome.YUT, YutGameLogic.evaluateSticks(listOf(false, false, false, false)))
        assertEquals(YutOutcome.GEOL, YutGameLogic.evaluateSticks(listOf(true, true, true, false)))
        assertEquals(YutOutcome.GAE, YutGameLogic.evaluateSticks(listOf(true, true, false, false)))
        assertEquals(YutOutcome.BACK_DO, YutGameLogic.evaluateSticks(listOf(true, false, false, false), markedStickIndex = 0))
        assertEquals(YutOutcome.DO, YutGameLogic.evaluateSticks(listOf(false, true, false, false), markedStickIndex = 0))

        // 0번 선수가 3칸(걸)에 서 있을 때 1번 선수가 걸(+3)을 던져 잡으면 +2칸 보너스로 5칸 전진, 0번은 2칸으로 후퇴
        val initial = listOf(YutPlayerState(0), YutPlayerState(1), YutPlayerState(2))
        val geolThrow = YutSingleThrow(listOf(false, true, true, true), 0, YutOutcome.GEOL)
        val afterP0 = YutGameLogic.applySingleThrow(initial, 0, geolThrow).updatedStates
        assertEquals(3, afterP0[0].position)

        val captureEvent = YutGameLogic.applySingleThrow(afterP0, 1, geolThrow)
        assertEquals(listOf(0), captureEvent.capturedPlayerIndices)
        assertEquals(5, captureEvent.updatedStates[1].position)
        assertEquals(2, captureEvent.updatedStates[0].position)

        val backDoThrow = YutSingleThrow(listOf(true, false, false, false), 0, YutOutcome.BACK_DO)
        val afterP2 = YutGameLogic.applySingleThrow(captureEvent.updatedStates, 2, backDoThrow).updatedStates
        val ranked = YutGameLogic.rankYutPlayers(afterP2, Random(99))
        assertTrue(ranked.first().isWinner)
        assertEquals(1, ranked.first().state.playerIndex)
        assertTrue(ranked.last().isLoser)
        assertEquals(2, ranked.last().state.playerIndex)
    }

    @Test
    fun verifyTarotTopicsArcanaAndMultiPlayerRankings() {
        assertEquals(6, TarotTopic.entries.size)
        assertEquals(22, TarotFortuneCatalog.majorArcana.size)

        // 참가자 수(1명 ~ 6명) 및 모든 운세 종류에서 1..N위 등수와 중복 없는 점수 검증
        for (playerCount in 1..6) {
            val names = KoreanGolfNameHelper.defaultCaddieBagNames(playerCount)
            for (topic in TarotTopic.entries) {
                val readings = TarotFortuneCatalog.drawReadingsForPlayers(
                    playerNames = names,
                    topic = topic,
                    rng = Random(playerCount * 31L + topic.ordinal)
                )
                assertEquals(playerCount, readings.size)
                assertEquals((1..playerCount).toList(), readings.map { it.rankOneBased }.sorted())
                assertEquals(playerCount, readings.map { it.fortuneScore }.toSet().size)
                readings.forEach { r ->
                    assertTrue(r.fortuneScore in 50..99)
                    assertTrue(r.detailedReading.isNotBlank())
                    assertTrue(r.adviceLine.isNotBlank())
                    assertTrue(r.luckyGuide.isNotBlank())
                }
            }
        }
    }
}
