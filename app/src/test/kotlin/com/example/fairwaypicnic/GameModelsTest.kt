package com.example.fairwaypicnic

import com.example.fairwaypicnic.model.AppSettings
import com.example.fairwaypicnic.model.GameCategory
import com.example.fairwaypicnic.model.GolfGameType
import com.example.fairwaypicnic.model.KoreanGolfNameHelper
import com.example.fairwaypicnic.ui.theme.RankBadgeColors
import com.example.fairwaypicnic.ui.theme.distinctRankBadgeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GameModelsTest {
    @Test
    fun verifySevenGolfGamesConfigured() {
        assertEquals(7, GolfGameType.entries.size)
        assertEquals(3, GolfGameType.entries.count { it.category == GameCategory.TEE_ORDER })
        assertEquals(4, GolfGameType.entries.count { it.category == GameCategory.COFFEE_PENALTY })
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
    }

    @Test
    fun verifyTwoClickSurnameAndTitleCombination() {
        assertEquals(11, KoreanGolfNameHelper.recommendedSurnames.size)
        assertEquals(6, KoreanGolfNameHelper.recommendedTitles.size)

        val step1 = KoreanGolfNameHelper.applySurname("김씨")
        assertEquals("김씨", step1)

        val step2 = KoreanGolfNameHelper.applyTitle(step1, "프로")
        assertEquals("김프로", step2)

        // 이미 호칭이 붙은 상태에서 다른 호칭을 다시 클릭해도 자연스럽게 교체되는지 검증
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
}
