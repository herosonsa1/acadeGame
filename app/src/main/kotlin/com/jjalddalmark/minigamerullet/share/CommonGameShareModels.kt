package com.jjalddalmark.minigamerullet.share

import com.jjalddalmark.minigamerullet.model.GameResultRow
import com.jjalddalmark.minigamerullet.model.GolfGameType
import com.jjalddalmark.minigamerullet.model.PlayerTarotReading
import java.util.UUID

/**
 * 퍼팅 치킨게임 개별 스트로크 스냅샷
 */
data class PuttingShotSnapshot(
    val playerName: String,
    val badgeColorArgb: Int,
    val finalX: Float,
    val finalY: Float,
    val inWater: Boolean,
    val holeIn: Boolean,
    val distanceCm: Int
)

/**
 * 더블 주사위 참가자별 투구 스냅샷
 */
data class DiceRollSnapshot(
    val playerName: String,
    val badgeColorArgb: Int,
    val die1: Int,
    val die2: Int,
    val sum: Int,
    val isDouble: Boolean,
    val isLeader: Boolean
)

/**
 * 민속 윷놀이 참가자별 최종 말 위치 및 투구 기록 스냅샷
 */
data class YutStateSnapshot(
    val playerName: String,
    val badgeColorArgb: Int,
    val finalPosition: Int,
    val historyText: String,
    val isWinner: Boolean,
    val isLoser: Boolean
)

/**
 * 각 미니게임의 실제 라운드 진행 장면 및 최종 결과를 1:1 재현하기 위한 전용 스냅샷 모델
 */
sealed interface GameExtraSceneData {
    data object None : GameExtraSceneData

    data class TeeBounce(
        val seatPlayerNames: List<String>,
        val winnerSeatIndex: Int,
        val rightHandRule: Boolean,
        val finalAngleDeg: Float
    ) : GameExtraSceneData

    data class NotchStick(
        val stickOwnerNames: List<String>,
        val stickNotchCounts: List<Int>
    ) : GameExtraSceneData

    data class ForestLadder(
        val topPlayerNames: List<String>,
        val playerBadgeColorArgbs: List<Int>,
        val bottomSlots: List<String>,
        val totalRows: Int,
        val rungs: List<Pair<Int, Int>>,
        val playerWaypoints: List<List<Pair<Float, Float>>>,
        val destCols: List<Int>,
        val isCoffeeMode: Boolean
    ) : GameExtraSceneData

    data class NearPinPutting(
        val shots: List<PuttingShotSnapshot>
    ) : GameExtraSceneData

    data class BallBasket(
        val pickedBallOrder: List<Int>,
        val pickedPlayerNames: List<String>,
        val goldenBallIndex: Int,
        val hitPlayerName: String
    ) : GameExtraSceneData

    data class LuckyWoodTile(
        val tilePlayerNames: List<String>,
        val tileRoleTitles: List<String>,
        val tileRoleSubtitles: List<String>,
        val tileHighlights: List<Boolean>,
        val tileIsTeamB: List<Boolean>,
        val isTeamSplitMode: Boolean
    ) : GameExtraSceneData

    data class DoubleDice(
        val rollsInPlayerOrder: List<DiceRollSnapshot>,
        val winnerName: String,
        val winnerDie1: Int,
        val winnerDie2: Int
    ) : GameExtraSceneData

    data class YutNori(
        val statesInOrder: List<YutStateSnapshot>,
        val finalSticksFlat: List<Boolean>,
        val headlineText: String
    ) : GameExtraSceneData

    data class TarotFortune(
        val topicLabel: String,
        val topicShortLabel: String,
        val readings: List<PlayerTarotReading>
    ) : GameExtraSceneData
}

/**
 * 모든 미니게임(룰렛 외 9종 포함 전체 공통)에서 결과 이미지(JPEG) 및 전 과정 진행 영상(MP4)을
 * 생성하고 카카오톡·문자·텔레그램 등으로 공유하기 위한 통합 페이로드
 */
data class CommonGameSharePayload(
    val id: String = UUID.randomUUID().toString(),
    val gameType: GolfGameType,
    val resultTitle: String,
    val resultSubtitle: String,
    val rows: List<GameResultRow>,
    val durationSec: Int = 10,
    val finishedAtMillis: Long = System.currentTimeMillis(),
    val extraSceneData: GameExtraSceneData = GameExtraSceneData.None
) {
    /**
     * 가장 주목해야 할 대표 결과 행 (강조 표시된 당첨자/1위 행 우선, 없으면 첫 번째 행)
     */
    val primaryRow: GameResultRow?
        get() = rows.firstOrNull { it.isHighlighted } ?: rows.firstOrNull()

    /**
     * 결과 카드 상단 영문 카테고리 스탬프
     */
    val englishHeaderStamp: String
        get() = when (gameType) {
            GolfGameType.DECISION_ROULETTE_PLUS -> "DECISION ROULETTE+"
            GolfGameType.WINDMILL_ROULETTE -> "HILLSIDE WINDMILL ROULETTE"
            GolfGameType.TEE_BOUNCE -> "DRIVER TEE BOUNCE"
            GolfGameType.NOTCH_STICK -> "HONOR NOTCH STICK"
            GolfGameType.FOREST_LADDER -> "FOREST LOG LADDER"
            GolfGameType.NEAR_PIN_PUTTING -> "NEAR-PIN PUTTING"
            GolfGameType.BALL_BASKET -> "ACORN BALL BASKET"
            GolfGameType.LUCKY_WOOD_TILE -> "LUCKY WOOD TILE"
            GolfGameType.DOUBLE_DICE -> "DOUBLE WOODEN DICE"
            GolfGameType.YUT_THROW -> "MEADOW YUT NORI"
            GolfGameType.TAROT_FORTUNE -> "CABIN TAROT FORTUNE"
        }

    /**
     * 파일명 접두어
     */
    val filePrefix: String
        get() = when (gameType) {
            GolfGameType.TEE_BOUNCE -> "티바운스"
            GolfGameType.NOTCH_STICK -> "홈뽑기막대"
            GolfGameType.FOREST_LADDER -> "숲속사다리"
            GolfGameType.NEAR_PIN_PUTTING -> "니어핀퍼팅"
            GolfGameType.BALL_BASKET -> "골프공바구니"
            GolfGameType.LUCKY_WOOD_TILE -> "나무패뒤집기"
            GolfGameType.DOUBLE_DICE -> "더블주사위"
            GolfGameType.YUT_THROW -> "민속윷놀이"
            GolfGameType.TAROT_FORTUNE -> "타로운세"
            GolfGameType.WINDMILL_ROULETTE -> "풍차룰렛"
            GolfGameType.DECISION_ROULETTE_PLUS -> "결정룰렛"
        }
}
