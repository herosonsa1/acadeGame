package com.jjalddalmark.minigamerullet

import androidx.compose.ui.graphics.Color
import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.TarotFortuneCatalog
import com.jjalddalmark.minigamerullet.model.TarotTopic
import com.jjalddalmark.minigamerullet.share.CommonGameSharePayload
import com.jjalddalmark.minigamerullet.share.DiceRollSnapshot
import com.jjalddalmark.minigamerullet.share.GameExtraSceneData
import com.jjalddalmark.minigamerullet.share.PuttingShotSnapshot
import com.jjalddalmark.minigamerullet.share.RouletteShareManager
import com.jjalddalmark.minigamerullet.share.YutStateSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class CommonGameShareTest {

    @Test
    fun verifyAllElevenGamesHaveUniqueSharePrefixesAndStamps() {
        val prefixes = mutableSetOf<String>()
        val stamps = mutableSetOf<String>()

        GolfGameType.entries.forEach { gameType ->
            val payload = CommonGameSharePayload(
                gameType = gameType,
                resultTitle = "${gameType.title} 결과",
                resultSubtitle = "테스트 서브타이틀",
                rows = listOf(
                    GameResultRow(
                        badgeText = "1위",
                        playerName = "김프로",
                        detailText = "최종 우승",
                        isHighlighted = true,
                        avatarRes = 1,
                        badgeColor = Color(0xFF3B6E4C)
                    )
                )
            )
            assertTrue(payload.filePrefix.isNotBlank())
            assertTrue(payload.englishHeaderStamp.isNotBlank())
            prefixes.add(payload.filePrefix)
            stamps.add(payload.englishHeaderStamp)
        }

        assertEquals(11, prefixes.size)
        assertEquals(11, stamps.size)
    }

    @Test
    fun verifyPrimaryRowSelectionAndSummaryFormattingAcrossAllNineGames() {
        val sampleRows = listOf(
            GameResultRow("통과", "박대표", "무사 통과", isHighlighted = false, avatarRes = 1, badgeColor = Color.Green),
            GameResultRow("커피 당첨", "김프로", "황금 도토리 볼 오픈", isHighlighted = true, avatarRes = 2, badgeColor = Color.Red),
            GameResultRow("통과", "최싱글", "무사 통과", isHighlighted = false, avatarRes = 3, badgeColor = Color.Blue)
        )

        val readings = TarotFortuneCatalog.drawReadingsForPlayers(
            playerNames = listOf("김프로", "박대표", "최싱글"),
            topic = TarotTopic.ROUNDING_SCORE,
            rng = Random(42)
        )

        val payloads = listOf(
            CommonGameSharePayload(
                gameType = GolfGameType.TEE_BOUNCE,
                resultTitle = "1번 홀 티샷 순서 확정",
                resultSubtitle = "우측 순서 배정",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.TeeBounce(
                    seatPlayerNames = listOf("박대표", "김프로", "최싱글"),
                    winnerSeatIndex = 1,
                    rightHandRule = true,
                    finalAngleDeg = 1320f
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.NOTCH_STICK,
                resultTitle = "나무 홈 뽑기 타순 결과",
                resultSubtitle = "눈금 확인 완료",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.NotchStick(
                    stickOwnerNames = listOf("박대표", "김프로", "최싱글"),
                    stickNotchCounts = listOf(2, 1, 3)
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.FOREST_LADDER,
                resultTitle = "숲속 사다리 커피 내기 결과",
                resultSubtitle = "통나무 사다리 도착",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.ForestLadder(
                    topPlayerNames = listOf("박대표", "김프로", "최싱글"),
                    playerBadgeColorArgbs = listOf(0xFF3B6E4C.toInt(), 0xFFB5633F.toInt(), 0xFF3B7A9E.toInt()),
                    bottomSlots = listOf("통과", "커피 당첨", "통과"),
                    totalRows = 7,
                    rungs = listOf(1 to 0, 3 to 1),
                    playerWaypoints = emptyList(),
                    destCols = listOf(0, 1, 2),
                    isCoffeeMode = true
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.NEAR_PIN_PUTTING,
                resultTitle = "니어핀 퍼팅 대결 결과",
                resultSubtitle = "홀컵 거리 측정 완료",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.NearPinPutting(
                    shots = listOf(
                        PuttingShotSnapshot("박대표", 0xFF3B6E4C.toInt(), 0.48f, 0.28f, false, false, 18),
                        PuttingShotSnapshot("김프로", 0xFFB5633F.toInt(), 0.52f, 0.11f, true, false, 999)
                    )
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.BALL_BASKET,
                resultTitle = "숲속 골프공 바구니 결과",
                resultSubtitle = "황금 도토리 공 발견",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.BallBasket(
                    pickedBallOrder = listOf(0, 4, 2),
                    pickedPlayerNames = listOf("박대표", "최싱글", "김프로"),
                    goldenBallIndex = 2,
                    hitPlayerName = "김프로"
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.LUCKY_WOOD_TILE,
                resultTitle = "나무 패 오픈 결과",
                resultSubtitle = "모든 패 공개 완료",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.LuckyWoodTile(
                    tilePlayerNames = listOf("박대표", "김프로", "최싱글"),
                    tileRoleTitles = listOf("버디 패", "커피 당첨", "이글 패"),
                    tileRoleSubtitles = listOf("통과", "당첨", "통과"),
                    tileHighlights = listOf(false, true, false),
                    tileIsTeamB = listOf(false, false, false),
                    isTeamSplitMode = false
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.DOUBLE_DICE,
                resultTitle = "더블 주사위 승부 결과",
                resultSubtitle = "김프로 12점 우승",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.DoubleDice(
                    rollsInPlayerOrder = listOf(
                        DiceRollSnapshot("김프로", 0xFFB5633F.toInt(), 6, 6, 12, true, true),
                        DiceRollSnapshot("박대표", 0xFF3B6E4C.toInt(), 4, 3, 7, false, false)
                    ),
                    winnerName = "김프로",
                    winnerDie1 = 6,
                    winnerDie2 = 6
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.YUT_THROW,
                resultTitle = "잔디밭 민속 윷놀이 승패 결과",
                resultSubtitle = "홀컵 완주 성공",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.YutNori(
                    statesInOrder = listOf(
                        YutStateSnapshot("김프로", 0xFFB5633F.toInt(), 10, "모 -> 윷 -> 개", true, false),
                        YutStateSnapshot("박대표", 0xFF3B6E4C.toInt(), 2, "개", false, true)
                    ),
                    finalSticksFlat = listOf(true, true, true, true),
                    headlineText = "김프로: 모(+5칸) 홀컵 완주!"
                )
            ),
            CommonGameSharePayload(
                gameType = GolfGameType.TAROT_FORTUNE,
                resultTitle = "라운딩·스코어운 타로점 결과",
                resultSubtitle = "메이저 아르카나 리딩 완료",
                rows = sampleRows,
                extraSceneData = GameExtraSceneData.TarotFortune(
                    topicLabel = TarotTopic.ROUNDING_SCORE.label,
                    topicShortLabel = TarotTopic.ROUNDING_SCORE.shortLabel,
                    readings = readings
                )
            )
        )

        assertEquals(9, payloads.size)
        payloads.forEach { payload ->
            val primary = payload.primaryRow
            assertNotNull(primary)
            assertEquals("김프로", primary!!.playerName)

            val imgSummary = RouletteShareManager.buildCommonGameShareSummaryText(payload, isVideo = false)
            val vidSummary = RouletteShareManager.buildCommonGameShareSummaryText(payload, isVideo = true)

            assertTrue(imgSummary.contains("미니게임 - 만능 룰렛 & 골프 복불복"))
            assertTrue(imgSummary.contains("게임 결과 카드"))
            assertTrue(imgSummary.contains("김프로"))
            assertTrue(vidSummary.contains("게임 진행 전 과정 영상"))
            assertTrue(vidSummary.contains(payload.resultTitle))
        }
    }
}
