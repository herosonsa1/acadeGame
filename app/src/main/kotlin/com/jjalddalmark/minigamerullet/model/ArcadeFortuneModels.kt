package com.jjalddalmark.minigamerullet.model

import kotlin.random.Random

// ============================================================================
// 1. 행운의 더블 주사위 (2개 원목 주사위 합산 승부 및 동점 자동 해소 엔진)
// ============================================================================

data class DicePlayerRoll(
    val playerIndex: Int,
    val die1: Int,
    val die2: Int,
    val suddenDeathBonus: Int = 0
) {
    val sum: Int
        get() = die1 + die2

    val isDouble: Boolean
        get() = die1 == die2

    val highDie: Int
        get() = maxOf(die1, die2)

    val compositeSortScore: Int
        get() = sum * 10_000 + (if (isDouble) 1_000 else 0) + highDie * 100 + suddenDeathBonus
}

data class RankedDiceResult(
    val rankOneBased: Int,
    val roll: DicePlayerRoll,
    val badgeLabel: String,
    val summaryDetail: String
)

object DiceGameLogic {
    fun rollTwoDice(playerIndex: Int, rng: Random = Random.Default): DicePlayerRoll {
        return DicePlayerRoll(
            playerIndex = playerIndex,
            die1 = rng.nextInt(1, 7),
            die2 = rng.nextInt(1, 7),
            suddenDeathBonus = 0
        )
    }

    /**
     * 참가자 전원의 2개 주사위 결과를 비교해 1위 승리부터 최하위까지 중복 없는 등수를 산정합니다.
     * 합계 -> 더블(같은 눈) 우선 -> 높은 단일 주사위 눈 순으로 판정하며,
     * 완전히 동일한 주사위 조합이 겹칠 경우 결승 보너스 주사위 눈(1~6)을 자동 부여해 순위를 가립니다.
     */
    fun rankRolls(
        rawRolls: List<DicePlayerRoll>,
        rng: Random = Random.Default
    ): List<RankedDiceResult> {
        if (rawRolls.isEmpty()) return emptyList()

        // 동일 (sum, isDouble, highDie) 그룹에만 서로 다른 결승 보너스 눈 부여
        val grouped = rawRolls.groupBy { Triple(it.sum, it.isDouble, it.highDie) }
        val resolved = mutableListOf<DicePlayerRoll>()
        grouped.values.forEach { group ->
            if (group.size == 1) {
                resolved.add(group.first().copy(suddenDeathBonus = 0))
            } else {
                val bonusPool = (1..maxOf(6, group.size)).shuffled(rng)
                group.forEachIndexed { idx, r ->
                    resolved.add(r.copy(suddenDeathBonus = bonusPool[idx]))
                }
            }
        }

        val sorted = resolved.sortedByDescending { it.compositeSortScore }
        val total = sorted.size
        return sorted.mapIndexed { idx, roll ->
            val rank = idx + 1
            val badge = when {
                rank == 1 -> "1위 승리"
                rank == total && total >= 2 -> "${rank}위 꼴등"
                else -> "${rank}위"
            }
            val doubleTag = if (roll.isDouble) " · 더블 보너스!" else ""
            val tieTag = if (roll.suddenDeathBonus > 0) " · 결승눈 +${roll.suddenDeathBonus}" else ""
            val detail = "주사위 ${roll.die1} + ${roll.die2} = 합계 ${roll.sum}${doubleTag}${tieTag}"
            RankedDiceResult(
                rankOneBased = rank,
                roll = roll,
                badgeLabel = badge,
                summaryDetail = detail
            )
        }
    }
}

// ============================================================================
// 2. 잔디밭 민속 윷놀이 (도·개·걸·윷·모·빽도 + 한 번 더 보너스 + 말 잡기 승패 엔진)
// ============================================================================

enum class YutOutcome(
    val koreanName: String,
    val stepDelta: Int,
    val grantsBonusTurn: Boolean,
    val shortGuide: String
) {
    MO("모", 5, true, "네 가락 모두 배 · +5칸 전진 & 한 번 더!"),
    YUT("윷", 4, true, "네 가락 모두 등 · +4칸 전진 & 한 번 더!"),
    GEOL("걸", 3, false, "배 셋 등 하나 · +3칸 시원하게 전진"),
    GAE("개", 2, false, "배 둘 등 둘 · +2칸 경쾌하게 전진"),
    DO("도", 1, false, "배 하나 등 셋 · +1칸 차분하게 전진"),
    BACK_DO("빽도", -1, false, "붉은 명패 가락 단독 배 · -1칸 뒷걸음!")
}

data class YutSingleThrow(
    val sticksFlat: List<Boolean>, // 길이 4: true = 평평한 배(앞면), false = 둥근 X문양 등(뒷면)
    val markedStickIndex: Int = 0, // 0번 가락에 붉은 빽도 표식
    val outcome: YutOutcome
)

data class YutPlayerState(
    val playerIndex: Int,
    val position: Int = 0, // 미니 오솔길 윷판 위치 (출발 0칸, 10칸 이상 홀컵 골인권, 빽도 시 음수 가능)
    val throws: List<YutSingleThrow> = emptyList(),
    val captureCount: Int = 0, // 앞선 상대 말을 잡은 횟수 (+2칸 보너스)
    val caughtCount: Int = 0,  // 상대에게 잡혀 후퇴한 횟수
    val tieBreakPoint: Int = 0
) {
    val yutMoCount: Int
        get() = throws.count { it.outcome.grantsBonusTurn }

    val outcomeHistoryText: String
        get() = if (throws.isEmpty()) "투구 대기" else throws.joinToString(" → ") { it.outcome.koreanName }
}

data class YutTurnEvent(
    val updatedStates: List<YutPlayerState>,
    val singleThrow: YutSingleThrow,
    val capturedPlayerIndices: List<Int>,
    val earnedBonusThrow: Boolean
)

data class RankedYutResult(
    val rankOneBased: Int,
    val state: YutPlayerState,
    val isWinner: Boolean,
    val isLoser: Boolean,
    val badgeLabel: String,
    val summaryDetail: String
)

object YutGameLogic {
    const val GOAL_STEP = 10
    const val MAX_BONUS_THROWS_PER_PLAYER = 2 // 1턴당 기본 1회 + 보너스 최대 2회 (최대 3연속 투구)

    /**
     * 윷가락 4개의 앞·뒷면 배열로 도·개·걸·윷·모·빽도를 판정합니다.
     */
    fun evaluateSticks(
        sticksFlat: List<Boolean>,
        markedStickIndex: Int = 0
    ): YutOutcome {
        require(sticksFlat.size == 4) { "윷가락은 반드시 4개여야 합니다." }
        val flatCount = sticksFlat.count { it }
        return when (flatCount) {
            4 -> YutOutcome.MO
            0 -> YutOutcome.YUT
            3 -> YutOutcome.GEOL
            2 -> YutOutcome.GAE
            1 -> {
                val safeMark = markedStickIndex.coerceIn(0, 3)
                if (sticksFlat[safeMark]) YutOutcome.BACK_DO else YutOutcome.DO
            }
            else -> YutOutcome.GAE
        }
    }

    /**
     * 실제 윷가락 물리 비율(평평한 면 52%, 둥근 면 48%)을 반영해 1회 투구를 생성합니다.
     */
    fun throwSticksOnce(rng: Random = Random.Default): YutSingleThrow {
        val sticks = List(4) { rng.nextFloat() < 0.52f }
        val markedIdx = 0
        return YutSingleThrow(
            sticksFlat = sticks,
            markedStickIndex = markedIdx,
            outcome = evaluateSticks(sticks, markedIdx)
        )
    }

    /**
     * 특정 참가자의 1회 투구 결과를 미니 윷판 전체 상태에 반영하고 '말 잡기' 및 '한 번 더' 여부를 반환합니다.
     */
    fun applySingleThrow(
        currentStates: List<YutPlayerState>,
        throwerIndex: Int,
        singleThrow: YutSingleThrow
    ): YutTurnEvent {
        val mutable = currentStates.toMutableList()
        val me = mutable[throwerIndex]
        val rawLanding = (me.position + singleThrow.outcome.stepDelta).coerceAtLeast(-2)

        // 양수 칸(1칸 이상, 아직 홀컵 완주 전 1..9칸)에서 다른 참가자의 말이 서 있으면 '말 잡기' 발동!
        val victims = if (rawLanding in 1 until GOAL_STEP) {
            mutable.filter { it.playerIndex != throwerIndex && it.position == rawLanding }
                .map { it.playerIndex }
        } else {
            emptyList()
        }

        val captureBonus = if (victims.isNotEmpty()) 2 else 0
        val finalPos = rawLanding + captureBonus

        // 잡힌 상대 말은 1칸 뒤로 밀려남 (최소 0칸 출발석)
        victims.forEach { vIdx ->
            val vState = mutable[vIdx]
            mutable[vIdx] = vState.copy(
                position = (vState.position - 1).coerceAtLeast(0),
                caughtCount = vState.caughtCount + 1
            )
        }

        val updatedThrows = me.throws + singleThrow
        val canTakeBonus = singleThrow.outcome.grantsBonusTurn &&
            updatedThrows.size <= MAX_BONUS_THROWS_PER_PLAYER

        mutable[throwerIndex] = me.copy(
            position = finalPos,
            throws = updatedThrows,
            captureCount = me.captureCount + victims.size
        )

        return YutTurnEvent(
            updatedStates = mutable,
            singleThrow = singleThrow,
            capturedPlayerIndices = victims,
            earnedBonusThrow = canTakeBonus
        )
    }

    /**
     * 미니게임 윷놀이 최종 승자(1위 우승)와 패자(최하위 꼴등)를 명확하게 순위화합니다.
     */
    fun rankYutPlayers(
        states: List<YutPlayerState>,
        rng: Random = Random.Default
    ): List<RankedYutResult> {
        if (states.isEmpty()) return emptyList()

        val grouped = states.groupBy { Triple(it.position, it.yutMoCount, it.captureCount) }
        val resolved = mutableListOf<YutPlayerState>()
        grouped.values.forEach { group ->
            if (group.size == 1) {
                resolved.add(group.first().copy(tieBreakPoint = 0))
            } else {
                val tiePool = (1..group.size).shuffled(rng)
                group.forEachIndexed { i, st ->
                    resolved.add(st.copy(tieBreakPoint = tiePool[i]))
                }
            }
        }

        val sorted = resolved.sortedWith(
            compareByDescending<YutPlayerState> { it.position }
                .thenByDescending { it.yutMoCount }
                .thenByDescending { it.captureCount }
                .thenByDescending { it.tieBreakPoint }
        )

        val total = sorted.size
        return sorted.mapIndexed { idx, st ->
            val rank = idx + 1
            val isWinner = rank == 1
            val isLoser = total >= 2 && rank == total
            val badge = when {
                isWinner -> "1위 승자"
                isLoser -> "${rank}위 패자"
                else -> "${rank}위 통과"
            }
            val posText = when {
                st.position >= GOAL_STEP -> "홀컵 완주 (${st.position}칸)"
                st.position < 0 -> "뒷걸음 (${st.position}칸)"
                else -> "${st.position}칸 도달"
            }
            val captureTag = if (st.captureCount > 0) " · 잡기 ${st.captureCount}회" else ""
            val detail = "${st.outcomeHistoryText} · ${posText}${captureTag}"
            RankedYutResult(
                rankOneBased = rank,
                state = st,
                isWinner = isWinner,
                isLoser = isLoser,
                badgeLabel = badge,
                summaryDetail = detail
            )
        }
    }
}

// ============================================================================
// 3. 숲속 오두막 타로 운세 (6종 운세 선택 · 22장 메이저 아르카나 · 개인별 점수 및 1~N위 등수)
// ============================================================================

enum class TarotTopic(
    val label: String,
    val shortLabel: String,
    val subtitle: String,
    val focusNoun: String
) {
    TODAY_TOTAL("오늘의 종합운", "종합운", "하루 전체의 기운과 행운 타이밍", "하루 전반의 흐름"),
    ROUNDING_SCORE("라운딩·스코어운", "라운딩운", "티샷·퍼팅 리듬과 동반자 호흡", "필드 샷 감각과 스코어"),
    WEALTH_MONEY("재물·금전운", "재물운", "수익·재테크·뜻밖의 금전 행운", "재물 흐름과 실속"),
    LOVE_RELATION("애정·대인운", "애정·인연운", "소중한 인연·동반자·귀인과의 화합", "인연과 따뜻한 소통"),
    WORK_SUCCESS("직장·사업·성취운", "사업·성취운", "중요한 결정·계약·목표 달성 기운", "추진력과 성과 달성"),
    HEALTH_VITALITY("건강·컨디션운", "건강·활력운", "몸과 마음의 활력·회복 밸런스", "체력 리듬과 컨디션")
}

enum class TarotEmblemType {
    FOOL_BREEZE,
    MAGICIAN_WAND,
    PRIESTESS_CRESCENT,
    EMPRESS_CROWN_LEAF,
    EMPEROR_SHIELD,
    HIEROPHANT_KEY,
    LOVERS_TWIN_STAR,
    CHARIOT_WINGS,
    STRENGTH_INFINITY,
    HERMIT_LANTERN,
    WHEEL_FORTUNE,
    JUSTICE_SCALES,
    HANGED_PENDULUM,
    REBIRTH_DAWN,
    TEMPERANCE_CHALICE,
    FOCUS_FLAME,
    TOWER_LIGHTNING,
    STAR_EIGHT,
    MOON_LAKE,
    SUN_RADIANT,
    JUDGEMENT_HORN,
    WORLD_WREATH
}

data class TarotArcanaCard(
    val number: Int,
    val romanNumeral: String,
    val koreanName: String,
    val englishName: String,
    val emblem: TarotEmblemType,
    val uprightKeywords: String,
    val reversedKeywords: String,
    val uprightCore: String,
    val reversedCore: String,
    val luckyColorName: String,
    val luckyItemName: String,
    val uprightBaseScore: Int,
    val reversedBaseScore: Int,
    val favoredTopics: Set<TarotTopic>
)

data class PlayerTarotReading(
    val playerIndex: Int,
    val playerName: String,
    val topic: TarotTopic,
    val card: TarotArcanaCard,
    val isUpright: Boolean,
    val fortuneScore: Int,      // 52 ~ 99점
    val rankOneBased: Int,      // 1 ~ 참가자 수(N)
    val gradeLabel: String,     // 대길 · 길운 · 호조 · 평온 · 신중
    val headline: String,       // 한 줄 핵심 메시지
    val detailedReading: String,// 선택 운세 맞춤 상세 풀이
    val adviceLine: String,     // 실천 조언
    val luckyGuide: String      // 행운 색상 · 숫자 · 아이템
) {
    val directionLabel: String
        get() = if (isUpright) "정방향" else "역방향"

    val cardFullTitle: String
        get() = "${card.romanNumeral}. ${card.koreanName} (${directionLabel})"
}

object TarotFortuneCatalog {
    val majorArcana: List<TarotArcanaCard> = listOf(
        TarotArcanaCard(
            number = 0,
            romanNumeral = "0",
            koreanName = "광대",
            englishName = "The Fool",
            emblem = TarotEmblemType.FOOL_BREEZE,
            uprightKeywords = "자유로운 출발 · 설렘 · 순수한 직관",
            reversedKeywords = "성급함 주의 · 호흡 정돈 · 기본기 점검",
            uprightCore = "부담을 내려놓을수록 기분 좋은 행운이 자연스럽게 따라붙는 흐름입니다.",
            reversedCore = "마음이 앞서기 쉬운 날이니 한 템포 천천히 준비하면 실수를 깔끔하게 줄일 수 있습니다.",
            luckyColorName = "봄하늘 민트",
            luckyItemName = "새 하얀 골프티",
            uprightBaseScore = 85,
            reversedBaseScore = 66,
            favoredTopics = setOf(TarotTopic.TODAY_TOTAL, TarotTopic.ROUNDING_SCORE)
        ),
        TarotArcanaCard(
            number = 1,
            romanNumeral = "I",
            koreanName = "마법사",
            englishName = "The Magician",
            emblem = TarotEmblemType.MAGICIAN_WAND,
            uprightKeywords = "창의적 감각 · 주도권 · 재능 발휘",
            reversedKeywords = "과신 경계 · 집중력 분산 주의",
            uprightCore = "손끝의 감각과 판단력이 또렷하게 살아나 원하는 방향대로 판을 이끌어 갈 수 있습니다.",
            reversedCore = "여러 가지를 한꺼번에 시도하기보다 가장 자신 있는 한 가지 방법에 집중하는 편이 유리합니다.",
            luckyColorName = "골든 앰버",
            luckyItemName = "가죽 볼마커",
            uprightBaseScore = 92,
            reversedBaseScore = 71,
            favoredTopics = setOf(TarotTopic.WORK_SUCCESS, TarotTopic.ROUNDING_SCORE, TarotTopic.WEALTH_MONEY)
        ),
        TarotArcanaCard(
            number = 2,
            romanNumeral = "II",
            koreanName = "여사제",
            englishName = "The High Priestess",
            emblem = TarotEmblemType.PRIESTESS_CRESCENT,
            uprightKeywords = "깊은 통찰 · 침착함 · 현명한 판단",
            reversedKeywords = "지나친 고민 · 결정 지연 주의",
            uprightCore = "주변이 분주해도 흔들리지 않는 차분한 안목이 결정적인 순간에 빛을 발합니다.",
            reversedCore = "생각이 많아지면 타이밍을 놓치기 쉬우니 첫 직관을 믿고 담백하게 결정해 보세요.",
            luckyColorName = "안개빛 코발트",
            luckyItemName = "은빛 텀블러",
            uprightBaseScore = 86,
            reversedBaseScore = 68,
            favoredTopics = setOf(TarotTopic.WORK_SUCCESS, TarotTopic.HEALTH_VITALITY)
        ),
        TarotArcanaCard(
            number = 3,
            romanNumeral = "III",
            koreanName = "여황제",
            englishName = "The Empress",
            emblem = TarotEmblemType.EMPRESS_CROWN_LEAF,
            uprightKeywords = "풍요로움 · 여유 · 넉넉한 결실",
            reversedKeywords = "과소비 주의 · 컨디션 페이스 조절",
            uprightCore = "따뜻한 여유와 풍성한 결실이 함께 찾아오며 사람들과의 자리에서도 웃음이 끊이지 않습니다.",
            reversedCore = "기분 내키는 대로 에너지를 쓰기보다 실속과 휴식을 함께 챙기면 만족도가 훨씬 높아집니다.",
            luckyColorName = "세이지 그린",
            luckyItemName = "달콤한 제철 과일",
            uprightBaseScore = 94,
            reversedBaseScore = 74,
            favoredTopics = setOf(TarotTopic.WEALTH_MONEY, TarotTopic.LOVE_RELATION, TarotTopic.TODAY_TOTAL)
        ),
        TarotArcanaCard(
            number = 4,
            romanNumeral = "IV",
            koreanName = "황제",
            englishName = "The Emperor",
            emblem = TarotEmblemType.EMPEROR_SHIELD,
            uprightKeywords = "탄탄한 안정 · 리더십 · 흔들림 없는 성과",
            reversedKeywords = "고집 완화 · 유연한 타협 필요",
            uprightCore = "원칙과 중심이 단단히 잡혀 있어 중요한 승부처나 협상에서 든든한 우위를 점합니다.",
            reversedCore = "내 방식만 고집하기보다 동반자나 주변의 조언을 한 걸음 수용하면 막혔던 흐름이 풀립니다.",
            luckyColorName = "단풍 테라코타",
            luckyItemName = "클래식 가죽 벨트",
            uprightBaseScore = 90,
            reversedBaseScore = 70,
            favoredTopics = setOf(TarotTopic.WORK_SUCCESS, TarotTopic.WEALTH_MONEY)
        ),
        TarotArcanaCard(
            number = 5,
            romanNumeral = "V",
            koreanName = "교황",
            englishName = "The Hierophant",
            emblem = TarotEmblemType.HIEROPHANT_KEY,
            uprightKeywords = "귀인의 조언 · 신뢰 · 정석의 승리",
            reversedKeywords = "고정관념 탈피 · 새로운 시각 필요",
            uprightCore = "믿을 수 있는 선배나 동료의 조언 속에 해답이 있으며 정석대로 움직일수록 좋은 결과가 따릅니다.",
            reversedCore = "익숙한 관성에서 살짝 벗어나 새로운 루틴을 시도하면 의외의 돌파구가 열립니다.",
            luckyColorName = "아이보리 크림",
            luckyItemName = "메모 수첩",
            uprightBaseScore = 84,
            reversedBaseScore = 69,
            favoredTopics = setOf(TarotTopic.LOVE_RELATION, TarotTopic.WORK_SUCCESS)
        ),
        TarotArcanaCard(
            number = 6,
            romanNumeral = "VI",
            koreanName = "연인",
            englishName = "The Lovers",
            emblem = TarotEmblemType.LOVERS_TWIN_STAR,
            uprightKeywords = "최고의 호흡 · 호감 상승 · 기분 좋은 선택",
            reversedKeywords = "사소한 오해 주의 · 명확한 의사표현",
            uprightCore = "함께하는 사람들과 호흡이 찰떡처럼 맞아떨어지며 좋은 인연과 반가운 소식이 이어집니다.",
            reversedCore = "배려심에서 아낀 말이 오해를 부를 수 있으니 솔직하고 다정하게 뜻을 전해 보세요.",
            luckyColorName = "코랄 피치",
            luckyItemName = "따뜻한 아메리카노",
            uprightBaseScore = 93,
            reversedBaseScore = 72,
            favoredTopics = setOf(TarotTopic.LOVE_RELATION, TarotTopic.ROUNDING_SCORE, TarotTopic.TODAY_TOTAL)
        ),
        TarotArcanaCard(
            number = 7,
            romanNumeral = "VII",
            koreanName = "전차",
            englishName = "The Chariot",
            emblem = TarotEmblemType.CHARIOT_WINGS,
            uprightKeywords = "거침없는 돌파 · 승부사 기질 · 목표 달성",
            reversedKeywords = "속도 조절 · 무리한 공략 자제",
            uprightCore = "강력한 추진력과 집중력이 최고조에 달해 경쟁 구도에서 당당히 승기를 잡는 날입니다.",
            reversedCore = "힘이 너무 들어가면 방향이 흔들릴 수 있으니 80%의 힘으로 부드럽게 컨트롤하세요.",
            luckyColorName = "청록 비취",
            luckyItemName = "스포츠 선글라스",
            uprightBaseScore = 95,
            reversedBaseScore = 70,
            favoredTopics = setOf(TarotTopic.ROUNDING_SCORE, TarotTopic.WORK_SUCCESS)
        ),
        TarotArcanaCard(
            number = 8,
            romanNumeral = "VIII",
            koreanName = "힘",
            englishName = "Strength",
            emblem = TarotEmblemType.STRENGTH_INFINITY,
            uprightKeywords = "내면의 여유 · 끈기 · 부드러운 카리스마",
            reversedKeywords = "체력 안배 · 잠깐의 재충전 필요",
            uprightCore = "어려운 상황도 미소와 여유로 부드럽게 제압하며 후반부로 갈수록 뒷심이 강해집니다.",
            reversedCore = "지친 기색을 참기보다 수분 보충과 짧은 휴식으로 컨디션을 먼저 끌어올려 주세요.",
            luckyColorName = "구운 도토리 골드",
            luckyItemName = "견과류 에너지바",
            uprightBaseScore = 91,
            reversedBaseScore = 72,
            favoredTopics = setOf(TarotTopic.HEALTH_VITALITY, TarotTopic.ROUNDING_SCORE)
        ),
        TarotArcanaCard(
            number = 9,
            romanNumeral = "IX",
            koreanName = "은둔자",
            englishName = "The Hermit",
            emblem = TarotEmblemType.HERMIT_LANTERN,
            uprightKeywords = "신중한 집중 · 내실 다지기 · 지혜로운 해답",
            reversedKeywords = "혼자만의 고민 해소 · 가벼운 대화 추천",
            uprightCore = "소란스러운 분위기에 휩쓸리지 않고 나만의 템포를 지킬 때 가장 만족스러운 결과를 얻습니다.",
            reversedCore = "혼자 속으로만 계산하기보다 곁에 있는 사람과 가볍게 웃으며 대화를 나누면 긴장이 풀립니다.",
            luckyColorName = "딥 파인 그린",
            luckyItemName = "우드 티 홀더",
            uprightBaseScore = 82,
            reversedBaseScore = 65,
            favoredTopics = setOf(TarotTopic.HEALTH_VITALITY, TarotTopic.WEALTH_MONEY)
        ),
        TarotArcanaCard(
            number = 10,
            romanNumeral = "X",
            koreanName = "운명의 수레바퀴",
            englishName = "Wheel of Fortune",
            emblem = TarotEmblemType.WHEEL_FORTUNE,
            uprightKeywords = "행운의 전환점 · 절호의 찬스 · 순풍",
            reversedKeywords = "흐름 대기 · 무리수 대신 안전 선택",
            uprightCore = "기분 좋은 우연과 타이밍이 겹치며 예상치 못한 보너스 같은 행운이 굴러들어오는 대길 흐름입니다.",
            reversedCore = "잠시 숨을 고르는 구간이니 욕심을 줄이고 안정적인 선택을 이어가면 곧 상승세로 돌아섭니다.",
            luckyColorName = "로열 골드",
            luckyItemName = "행운의 동전 마커",
            uprightBaseScore = 96,
            reversedBaseScore = 73,
            favoredTopics = setOf(TarotTopic.WEALTH_MONEY, TarotTopic.TODAY_TOTAL, TarotTopic.ROUNDING_SCORE)
        ),
        TarotArcanaCard(
            number = 11,
            romanNumeral = "XI",
            koreanName = "정의",
            englishName = "Justice",
            emblem = TarotEmblemType.JUSTICE_SCALES,
            uprightKeywords = "균형 감각 · 공정한 보상 · 정확한 계산",
            reversedKeywords = "치우침 점검 · 손익 재확인",
            uprightCore = "노력하고 준비한 만큼 정직하고 깔끔한 보상이 돌아오며 밸런스가 아주 훌륭합니다.",
            reversedCore = "사소한 조건이나 거리를 눈대중으로 넘기지 말고 한 번 더 체크하면 손해를 예방합니다.",
            luckyColorName = "슬레이트 블루",
            luckyItemName = "거리측정기 케이스",
            uprightBaseScore = 87,
            reversedBaseScore = 69,
            favoredTopics = setOf(TarotTopic.WORK_SUCCESS, TarotTopic.WEALTH_MONEY)
        ),
        TarotArcanaCard(
            number = 12,
            romanNumeral = "XII",
            koreanName = "매달린 사람",
            englishName = "The Hanged Man",
            emblem = TarotEmblemType.HANGED_PENDULUM,
            uprightKeywords = "발상의 전환 · 인내 후 반전 · 새로운 시선",
            reversedKeywords = "미련 내려놓기 · 빠른 전환 추천",
            uprightCore = "남들과 다른 각도에서 상황을 바라보면 오히려 까다로운 위기를 멋진 기회로 바꿀 수 있습니다.",
            reversedCore = "이미 지나간 실수에 마음을 두지 말고 곧바로 다음 기회에 시선을 맞추는 것이 상책입니다.",
            luckyColorName = "라벤더 그레이",
            luckyItemName = "스트레칭 타월",
            uprightBaseScore = 79,
            reversedBaseScore = 63,
            favoredTopics = setOf(TarotTopic.HEALTH_VITALITY, TarotTopic.WORK_SUCCESS)
        ),
        TarotArcanaCard(
            number = 13,
            romanNumeral = "XIII",
            koreanName = "재탄생",
            englishName = "Rebirth",
            emblem = TarotEmblemType.REBIRTH_DAWN,
            uprightKeywords = "과감한 리셋 · 새 국면 시작 · 반전의 서막",
            reversedKeywords = "옛 습관 정리 · 변화 수용",
            uprightCore = "답답했던 흐름을 깨끗이 털어내고 완전히 새로운 상승 흐름으로 갈아타는 반전의 카드입니다.",
            reversedCore = "예전 방식을 고수하기보다 과감히 전략을 바꾸면 후반부에 훨씬 가벼운 발걸음이 됩니다.",
            luckyColorName = "새벽빛 베이지",
            luckyItemName = "새 장갑",
            uprightBaseScore = 81,
            reversedBaseScore = 61,
            favoredTopics = setOf(TarotTopic.TODAY_TOTAL, TarotTopic.WORK_SUCCESS)
        ),
        TarotArcanaCard(
            number = 14,
            romanNumeral = "XIV",
            koreanName = "절제",
            englishName = "Temperance",
            emblem = TarotEmblemType.TEMPERANCE_CHALICE,
            uprightKeywords = "부드러운 조화 · 평정심 · 흔들림 없는 리듬",
            reversedKeywords = "리듬 회복 · 과욕 금물",
            uprightCore = "마음과 몸의 조화가 이상적이라 무리하지 않아도 자연스럽게 상위권 결과를 만들어냅니다.",
            reversedCore = "조급함이 리듬을 깨뜨릴 수 있으니 평소 루틴과 호흡을 일정하게 유지하는 데 집중하세요.",
            luckyColorName = "맑은 호수 블루",
            luckyItemName = "허브티·보리차",
            uprightBaseScore = 89,
            reversedBaseScore = 71,
            favoredTopics = setOf(TarotTopic.HEALTH_VITALITY, TarotTopic.ROUNDING_SCORE, TarotTopic.LOVE_RELATION)
        ),
        TarotArcanaCard(
            number = 15,
            romanNumeral = "XV",
            koreanName = "몰입의 불꽃",
            englishName = "Intense Focus",
            emblem = TarotEmblemType.FOCUS_FLAME,
            uprightKeywords = "강렬한 승부욕 · 집중 몰입 · 과감한 베팅",
            reversedKeywords = "무리한 욕심 절제 · 가벼운 마음 유지",
            uprightCore = "승부욕과 몰입도가 강하게 타올라 단기 승부나 집중력이 필요한 일에서 놀라운 폭발력을 냅니다.",
            reversedCore = "한 번에 만회하려는 큰 욕심만 내려놓으면 까다로운 함정을 가볍게 피해 갈 수 있습니다.",
            luckyColorName = "버건디 브라운",
            luckyItemName = "다크 초콜릿",
            uprightBaseScore = 83,
            reversedBaseScore = 59,
            favoredTopics = setOf(TarotTopic.WEALTH_MONEY, TarotTopic.WORK_SUCCESS)
        ),
        TarotArcanaCard(
            number = 16,
            romanNumeral = "XVI",
            koreanName = "번개와 각성",
            englishName = "Awakening",
            emblem = TarotEmblemType.TOWER_LIGHTNING,
            uprightKeywords = "깜짝 변수 · 틀을 깨는 한 방 · 빠른 대처",
            reversedKeywords = "안전 위주 운영 · 리스크 사전 차단",
            uprightCore = "예상 밖의 변수가 등장하지만 빠르게 적응하면 오히려 판세를 단숨에 뒤집는 짜릿한 한 방이 됩니다.",
            reversedCore = "모험적인 선택보다는 가장 안전하고 확실한 길을 택할 때 내 실속을 든든히 지킬 수 있습니다.",
            luckyColorName = "오크 우드",
            luckyItemName = "방풍 윈드브레이커",
            uprightBaseScore = 78,
            reversedBaseScore = 56,
            favoredTopics = setOf(TarotTopic.TODAY_TOTAL)
        ),
        TarotArcanaCard(
            number = 17,
            romanNumeral = "XVII",
            koreanName = "별",
            englishName = "The Star",
            emblem = TarotEmblemType.STAR_EIGHT,
            uprightKeywords = "희망 성취 · 반짝이는 영감 · 인기 상승",
            reversedKeywords = "기대치 조율 · 작은 성취부터 축적",
            uprightCore = "맑은 밤하늘의 길잡이 별처럼 소망하던 일이 순조롭게 풀리고 주변의 호감과 응원이 모입니다.",
            reversedCore = "처음부터 완벽을 기대하기보다 작은 성공을 하나씩 쌓아가면 어느새 목표에 닿아 있습니다.",
            luckyColorName = "스타라이트 실버",
            luckyItemName = "별 무늬 볼마커",
            uprightBaseScore = 95,
            reversedBaseScore = 76,
            favoredTopics = setOf(TarotTopic.TODAY_TOTAL, TarotTopic.LOVE_RELATION, TarotTopic.ROUNDING_SCORE)
        ),
        TarotArcanaCard(
            number = 18,
            romanNumeral = "XVIII",
            koreanName = "달",
            englishName = "The Moon",
            emblem = TarotEmblemType.MOON_LAKE,
            uprightKeywords = "섬세한 감수성 · 직관적 캐치 · 유연한 흐름",
            reversedKeywords = "막연한 걱정 해소 · 안개 걷힘",
            uprightCore = "미세한 분위기와 흐름 변화를 남들보다 먼저 읽어내어 섬세한 감각이 필요한 순간에 강합니다.",
            reversedCore = "막연했던 고민이나 안개가 서서히 걷히며 오후로 갈수록 판단이 한결 명확해집니다.",
            luckyColorName = "문라이트 퍼플",
            luckyItemName = "은은한 핸드크림",
            uprightBaseScore = 82,
            reversedBaseScore = 73,
            favoredTopics = setOf(TarotTopic.LOVE_RELATION, TarotTopic.HEALTH_VITALITY)
        ),
        TarotArcanaCard(
            number = 19,
            romanNumeral = "XIX",
            koreanName = "태양",
            englishName = "The Sun",
            emblem = TarotEmblemType.SUN_RADIANT,
            uprightKeywords = "최상의 대길운 · 활력 폭발 · 눈부신 성공",
            reversedKeywords = "따뜻한 순항 · 체력 오버페이스만 주의",
            uprightCore = "구름 한 점 없는 햇살처럼 에너지와 행운이 가득 차올라 무엇을 해도 주인공이 되는 최고의 카드입니다.",
            reversedCore = "기본 운기가 워낙 밝아 충분히 좋은 날이니 초반에 힘을 다 쓰지 않도록 페이스만 조절하세요.",
            luckyColorName = "선샤인 옐로우",
            luckyItemName = "화이트 캡 모자",
            uprightBaseScore = 98,
            reversedBaseScore = 81,
            favoredTopics = TarotTopic.entries.toSet()
        ),
        TarotArcanaCard(
            number = 20,
            romanNumeral = "XX",
            koreanName = "심판",
            englishName = "Judgement",
            emblem = TarotEmblemType.JUDGEMENT_HORN,
            uprightKeywords = "기쁜 소식 · 재도약 · 노력의 결실 확인",
            reversedKeywords = "최종 점검 · 타이밍 한 번 더 확인",
            uprightCore = "그동안 공들여 온 실력과 노력이 마침내 당당한 결과와 반가운 소식으로 보답받는 날입니다.",
            reversedCore = "마무리 직전에 한 번만 더 꼼꼼히 확인하면 아까운 실점을 막고 기분 좋게 매듭지을 수 있습니다.",
            luckyColorName = "메도우 그린",
            luckyItemName = "스코어카드 홀더",
            uprightBaseScore = 91,
            reversedBaseScore = 72,
            favoredTopics = setOf(TarotTopic.WORK_SUCCESS, TarotTopic.ROUNDING_SCORE, TarotTopic.TODAY_TOTAL)
        ),
        TarotArcanaCard(
            number = 21,
            romanNumeral = "XXI",
            koreanName = "세계",
            englishName = "The World",
            emblem = TarotEmblemType.WORLD_WREATH,
            uprightKeywords = "완벽한 완성 · 원만한 성취 · 최상위 조화",
            reversedKeywords = "99% 달성 · 마지막 한 걸음 마무리",
            uprightCore = "모든 퍼즐이 제자리를 찾듯 처음부터 끝까지 막힘없이 완성도 높은 해피엔딩을 맞이합니다.",
            reversedCore = "거의 다 완성된 좋은 흐름이니 끝까지 방심하지 않고 마무리 루틴을 지키면 완벽해집니다.",
            luckyColorName = "에메랄드 포레스트",
            luckyItemName = "클로버 행운 배지",
            uprightBaseScore = 97,
            reversedBaseScore = 80,
            favoredTopics = TarotTopic.entries.toSet()
        )
    )

    fun scoreToGradeLabel(score: Int): String = when {
        score >= 92 -> "대길(大吉)"
        score >= 84 -> "길운(吉運)"
        score >= 75 -> "호조(好調)"
        score >= 65 -> "평온(平穩)"
        else -> "신중(愼重)"
    }

    private fun buildTopicSpecificGuidance(
        topic: TarotTopic,
        card: TarotArcanaCard,
        isUpright: Boolean
    ): Pair<String, String> {
        return when (topic) {
            TarotTopic.TODAY_TOTAL -> if (isUpright) {
                "오늘 하루는 '${card.uprightKeywords.substringBefore(" ·")}'의 기운이 중심을 잡아줍니다. ${card.uprightCore}" to
                    "오전보다 오후로 갈수록 운기가 더 맑아지니 중요한 약속이나 선택을 자신 있게 진행해 보세요."
            } else {
                "오늘은 '${card.reversedKeywords.substringBefore(" ·")}'을 의식하면 한결 편안해집니다. ${card.reversedCore}" to
                    "급한 결정은 잠시 미루고 여유 있는 미소로 템포를 늦추면 복이 자연스레 머뭅니다."
            }

            TarotTopic.ROUNDING_SCORE -> if (isUpright) {
                "필드 위 ${topic.focusNoun}에서 '${card.koreanName}' 정방향의 상승 기류가 강합니다. ${card.uprightCore}" to
                    "티샷 전 가벼운 빈 스윙 한 번과 그린 위 침착한 라이 확인이 오늘의 베스트 스코어를 열어줍니다."
            } else {
                "오늘 라운딩에서는 '${card.reversedKeywords.substringBefore(" ·")}'이 스코어를 지키는 열쇠입니다. ${card.reversedCore}" to
                    "무리한 투온이나 핀 직접 공략보다 안전한 페어웨이·그린 중앙 공략이 동반자들 사이에서 승리를 부릅니다."
            }

            TarotTopic.WEALTH_MONEY -> if (isUpright) {
                "${topic.focusNoun} 면에서 '${card.koreanName}' 카드가 기분 좋은 실속과 보상을 예고합니다. ${card.uprightCore}" to
                    "작은 내기나 실속 있는 거래에서 유리한 흐름이며 계획적인 지출이 더 큰 만족으로 돌아옵니다."
            } else {
                "금전과 재물에서는 '${card.reversedKeywords.substringBefore(" ·")}' 포인트만 챙기면 안전합니다. ${card.reversedCore}" to
                    "충동적인 지출이나 무리한 베팅을 피하고 이미 가진 강점을 지키는 수비형 운영이 이득입니다."
            }

            TarotTopic.LOVE_RELATION -> if (isUpright) {
                "${topic.focusNoun}에서 따뜻한 호감과 신뢰가 깊어지는 시간입니다. ${card.uprightCore}" to
                    "먼저 건네는 칭찬 한마디와 밝은 리액션이 오늘 모임의 분위기를 가장 빛나게 만듭니다."
            } else {
                "대인관계와 인연에서는 상대의 속도를 존중할수록 조화로워집니다. ${card.reversedCore}" to
                    "내 의견을 앞세우기보다 상대방의 이야기를 한 번 더 들어주면 뜻밖의 든든한 내 편이 생깁니다."
            }

            TarotTopic.WORK_SUCCESS -> if (isUpright) {
                "${topic.focusNoun}에서 '${card.koreanName}'의 추진력이 분명한 성과를 만들어냅니다. ${card.uprightCore}" to
                    "미뤄두었던 핵심 과제나 제안을 오늘 매듭지으면 주변의 높은 평가를 받게 됩니다."
            } else {
                "업무와 목표 달성에서는 기초 점검이 곧 지름길입니다. ${card.reversedCore}" to
                    "속도전보다 우선순위를 두세 가지로 압축해 차근차근 완수하면 안정적인 결과가 따릅니다."
            }

            TarotTopic.HEALTH_VITALITY -> if (isUpright) {
                "${topic.focusNoun}이 맑고 가볍게 깨어나 기분 좋은 활력이 돌고 있습니다. ${card.uprightCore}" to
                    "가벼운 산책이나 스트레칭으로 몸을 예열하면 하루 종일 상쾌한 집중력이 유지됩니다."
            } else {
                "컨디션 관리에서는 에너지를 아껴 쓰는 지혜가 필요한 시점입니다. ${card.reversedCore}" to
                    "카페인보다는 따뜻한 물과 충분한 호흡으로 긴장을 풀어주면 피로가 빠르게 회복됩니다."
            }
        }
    }

    /**
     * 참가자 명단(1~6명)과 선택된 운세 종류에 맞춰 중복 없는 타로 카드를 뽑고,
     * 각 참가자의 행운 점수와 1위~N위 등수를 산출합니다.
     */
    fun drawReadingsForPlayers(
        playerNames: List<String>,
        topic: TarotTopic,
        rng: Random = Random.Default
    ): List<PlayerTarotReading> {
        val cleanNames = if (playerNames.isEmpty()) listOf("1번캐디백") else playerNames.take(6)
        val shuffledDeck = majorArcana.shuffled(rng)

        data class RawEntry(
            val playerIndex: Int,
            val playerName: String,
            val card: TarotArcanaCard,
            val isUpright: Boolean,
            var score: Int
        )

        val rawList = cleanNames.mapIndexed { idx, name ->
            val card = shuffledDeck[idx % shuffledDeck.size]
            // 정방향 확률 72%로 설정해 현실적이면서도 긍정적인 타로 경험 제공
            val upright = rng.nextFloat() < 0.72f
            val base = if (upright) card.uprightBaseScore else card.reversedBaseScore
            val affinityBonus = if (topic in card.favoredTopics) 2 else 0
            val jitter = rng.nextInt(-2, 3)
            val computed = (base + affinityBonus + jitter).coerceIn(52, 99)
            RawEntry(
                playerIndex = idx,
                playerName = name.ifBlank { "${idx + 1}번캐디백" },
                card = card,
                isUpright = upright,
                score = computed
            )
        }

        // 여러 명이 함께 볼 때 동일한 점수로 등수가 겹치지 않도록 1점 단위로 미세 보정 (1~N위 명확 분리)
        val usedScores = mutableSetOf<Int>()
        rawList.sortedByDescending { it.score }.forEach { entry ->
            var candidate = entry.score
            while (candidate in usedScores && candidate > 50) {
                candidate--
            }
            while (candidate in usedScores && candidate < 99) {
                candidate++
            }
            usedScores.add(candidate)
            entry.score = candidate
        }

        val rankedOrder = rawList.sortedByDescending { it.score }
        val rankByPlayerIndex = rankedOrder.mapIndexed { rIdx, e -> e.playerIndex to (rIdx + 1) }.toMap()

        return rawList.map { entry ->
            val rank = rankByPlayerIndex[entry.playerIndex] ?: 1
            val grade = scoreToGradeLabel(entry.score)
            val keywords = if (entry.isUpright) entry.card.uprightKeywords else entry.card.reversedKeywords
            val (detail, advice) = buildTopicSpecificGuidance(topic, entry.card, entry.isUpright)
            val luckyNum = ((entry.card.number + topic.ordinal * 3 + if (entry.isUpright) 7 else 4) % 9) + 1
            PlayerTarotReading(
                playerIndex = entry.playerIndex,
                playerName = entry.playerName,
                topic = topic,
                card = entry.card,
                isUpright = entry.isUpright,
                fortuneScore = entry.score,
                rankOneBased = rank,
                gradeLabel = grade,
                headline = keywords,
                detailedReading = detail,
                adviceLine = advice,
                luckyGuide = "행운색: ${entry.card.luckyColorName} · 행운 숫자: ${luckyNum} · 행운 아이템: ${entry.card.luckyItemName}"
            )
        }
    }
}
