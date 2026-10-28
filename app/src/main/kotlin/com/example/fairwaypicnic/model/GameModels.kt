package com.example.fairwaypicnic.model

import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.example.fairwaypicnic.R
import com.example.fairwaypicnic.ui.theme.MeadowGreen
import com.example.fairwaypicnic.ui.theme.OakWood
import com.example.fairwaypicnic.ui.theme.PlayerBadgeColors
import com.example.fairwaypicnic.ui.theme.SkyPondBlue
import com.example.fairwaypicnic.ui.theme.SunlightAmber
import com.example.fairwaypicnic.ui.theme.TerracottaWood

enum class GameCategory(val label: String, val description: String) {
    TEE_ORDER("1홀 티샷 순서", "첫 홀 티잉 그라운드에서 기분 좋게 타순을 정하는 게임"),
    COFFEE_PENALTY("커피 · 꼴등 복불복", "그늘집 간식과 커피 내기, 꼴등 및 팀을 정하는 게임")
}

enum class GolfGameType(
    val title: String,
    val subtitle: String,
    val category: GameCategory,
    val tagLabel: String,
    val ruleSummary: String,
    val accentColor: Color
) {
    TEE_BOUNCE(
        title = "드라이버 티 바운스",
        subtitle = "드라이버 헤드에 티를 튕겨 가리키는 방향부터 오른쪽 순으로 1~4번 확정",
        category = GameCategory.TEE_ORDER,
        tagLabel = "티샷 순서 · 4인 표준",
        ruleSummary = "중앙 드라이버 페이스에 나무 티를 떨어뜨려 튀어 오른 뒤, 뾰족한 끝이 가리킨 선수가 1번 오너가 되고 오른쪽 방향으로 2·3·4번이 정해집니다.",
        accentColor = MeadowGreen
    ),
    NOTCH_STICK(
        title = "나무 홈 뽑기 막대",
        subtitle = "가죽 통에서 뽑은 막대 아랫부분의 홈 개수(1~4개)로 타순 결정",
        category = GameCategory.TEE_ORDER,
        tagLabel = "티샷 순서 · 클래식 오너 스틱",
        ruleSummary = "통 안에 숨겨진 나무 막대를 하나씩 선택해 뽑습니다. 막대가 올라올 때 슬로우모션과 함께 새겨진 홈(1~4개)으로 타순이 공개됩니다.",
        accentColor = OakWood
    ),
    FOREST_LADDER(
        title = "숲속 오솔길 사다리",
        subtitle = "통나무 다리 갈림길을 따라 홀컵으로 내려가는 타순·복불복 사다리",
        category = GameCategory.TEE_ORDER,
        tagLabel = "티샷 순서 · 커피 내기 겸용",
        ruleSummary = "출전 선수들이 위쪽 티잉 그라운드에서 출발해 통나무 다리를 건너며 홀컵으로 이동합니다. 마지막 갈림길에서 슬로우모션이 발동합니다.",
        accentColor = MeadowGreen
    ),
    NEAR_PIN_PUTTING(
        title = "니어핀 퍼팅 치킨게임",
        subtitle = "홀컵 뒤 연못을 피해 가장 가까이 붙이는 아케이드 퍼팅 대결",
        category = GameCategory.COFFEE_PENALTY,
        tagLabel = "아케이드 조작 · 꼴등 커피",
        ruleSummary = "순서대로 퍼터를 당겨 힘을 조절해 공을 굴립니다. 연못 해저드에 빠지거나 홀컵에서 가장 먼 선수가 오늘의 커피 당첨자가 됩니다.",
        accentColor = SkyPondBlue
    ),
    BALL_BASKET(
        title = "숲속 골프공 바구니",
        subtitle = "바구니 속 골프공 중 단 하나의 당첨 볼을 피하는 긴장감 복불복",
        category = GameCategory.COFFEE_PENALTY,
        tagLabel = "러시안 룰렛 · 커피 내기",
        ruleSummary = "버드나무 바구니 안의 골프공을 돌아가며 하나씩 꺼냅니다. 공이 줄어들수록 심장박동이 빨라지며 황금 도토리 도장 볼을 뽑으면 당첨입니다.",
        accentColor = SunlightAmber
    ),
    WINDMILL_ROULETTE(
        title = "언덕 위 풍차 룰렛",
        subtitle = "클럽하우스 나무 풍차 바퀴를 돌려 아슬아슬하게 멈추는 복불복",
        category = GameCategory.COFFEE_PENALTY,
        tagLabel = "원터치 스핀 · 간식·캐디피",
        ruleSummary = "풍차 바퀴를 힘껏 돌리면 나무 톱니 소리와 함께 회전하다가 멈추기 직전 슬로우모션 구간에서 오늘의 당첨자를 가리킵니다.",
        accentColor = TerracottaWood
    ),
    LUCKY_WOOD_TILE(
        title = "행운의 나무 패 뒤집기",
        subtitle = "섞인 나무 패를 골라 뒤집어 커피 당첨자 또는 2대2 팀을 결정",
        category = GameCategory.COFFEE_PENALTY,
        tagLabel = "패 오픈 · 팀 편성 겸용",
        ruleSummary = "테이블 위에서 섞인 나무 패를 각자 선택한 뒤 마지막 순간 슬로우모션으로 패를 뒤집어 커피 당첨자 또는 A·B팀 편성을 확인합니다.",
        accentColor = OakWood
    )
}

/**
 * 한국 골프 라운딩 전용 빠른 선수 표기 프리셋 및 성씨+호칭 2클릭 조합 도우미
 */
object KoreanGolfNameHelper {
    // 김·박·이·정 4대 성씨 + 국내 다빈도 상위 7개 성씨 (총 11개 원터치 제공)
    val recommendedSurnames: List<String> = listOf(
        "김씨", "박씨", "이씨", "정씨",
        "최씨", "강씨", "조씨", "윤씨", "장씨", "임씨", "한씨"
    )

    // 2단계 원터치 결합 호칭 6종
    val recommendedTitles: List<String> = listOf(
        "프로", "사장", "회장", "선배", "선수", "대표"
    )

    private val replaceableSuffixes = listOf(
        "프로", "사장", "회장", "선배", "선수", "대표", "원장", "싱글"
    )

    private val numberedPresetRegex = Regex("""^(\d+번)(캐디백|참가자)$""")

    fun defaultCaddieBagNames(count: Int = 4): List<String> =
        (1..count.coerceIn(2, 6)).map { "${it}번캐디백" }

    fun defaultParticipantNames(count: Int = 4): List<String> =
        (1..count.coerceIn(2, 6)).map { "${it}번참가자" }

    fun defaultSampleCustomNames(count: Int = 4): List<String> {
        val base = listOf("김프로", "박원장", "이대표", "최싱글", "정회장", "강선배")
        return (0 until count.coerceIn(2, 6)).map { idx ->
            base.getOrElse(idx) { "${idx + 1}번캐디백" }
        }
    }

    /**
     * 1단계 성씨 선택 시 그대로 '김씨' 형태로 반환
     */
    fun applySurname(surnameWithSsi: String): String {
        return surnameWithSsi.trim().take(10)
    }

    /**
     * 2단계 호칭 선택 시 앞선 성씨에서 '씨'를 제거하고 호칭을 합침 (예: '김씨' + '프로' -> '김프로')
     * 이미 '김프로' 상태일 때 '회장'을 누르면 '김회장'으로 자연스럽게 교체
     */
    fun applyTitle(currentName: String, title: String): String {
        val raw = currentName.trim()
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return raw
        if (raw.isEmpty()) return "김$cleanTitle"

        // '1번캐디백' 또는 '1번참가자' 상태에서 바로 호칭을 누른 경우 '1번프로'로 변환
        val numberedMatch = numberedPresetRegex.matchEntire(raw)
        if (numberedMatch != null) {
            return "${numberedMatch.groupValues[1]}$cleanTitle".take(10)
        }

        // 기존 호칭이 붙어 있는 경우 어간 분리 (예: '김프로' -> '김', '남궁대표' -> '남궁')
        var stem = raw
        for (suffix in replaceableSuffixes) {
            if (stem.endsWith(suffix) && stem.length > suffix.length) {
                stem = stem.removeSuffix(suffix)
                break
            }
        }

        // 성씨 뒤의 '씨' 제거 (예: '김씨' -> '김', '남궁씨' -> '남궁')
        if (stem.endsWith("씨") && stem.length > 1) {
            stem = stem.removeSuffix("씨")
        }

        return "${stem.trim()}$cleanTitle".take(10)
    }

    fun isAllCaddieBagPreset(names: List<String>): Boolean =
        names.isNotEmpty() && names.indices.all { i -> names[i].trim() == "${i + 1}번캐디백" }

    fun isAllParticipantPreset(names: List<String>): Boolean =
        names.isNotEmpty() && names.indices.all { i -> names[i].trim() == "${i + 1}번참가자" }
}

data class PlayerInfo(
    val index: Int,
    val name: String,
    @param:DrawableRes val avatarRes: Int,
    val badgeColor: Color
)

data class AppSettings(
    val playerNames: List<String> = KoreanGolfNameHelper.defaultCaddieBagNames(4),
    val savedCustomNames: List<String> = KoreanGolfNameHelper.defaultSampleCustomNames(4),
    val gameDurationSeconds: Int = 10,
    val soundEnabled: Boolean = true,
    val bgmEnabled: Boolean = true,
    val bgmVolume: Float = 0.65f,
    val sfxEnabled: Boolean = true,
    val sfxVolume: Float = 0.88f,
    val heartbeatEnabled: Boolean = true,
    val heartbeatVolume: Float = 0.92f,
    val resultFanfareEnabled: Boolean = true,
    val resultFanfareVolume: Float = 0.94f,
    val muteInVibrateMode: Boolean = false,
    val hapticEnabled: Boolean = true,
    val slowMotionEnabled: Boolean = true
) {
    val normalizedDurationSeconds: Int
        get() = when {
            gameDurationSeconds <= 6 -> 5
            gameDurationSeconds >= 13 -> 15
            else -> 10
        }

    val players: List<PlayerInfo>
        get() {
            val avatars = listOf(
                R.drawable.avatar_golfer_1,
                R.drawable.avatar_golfer_2,
                R.drawable.avatar_golfer_3,
                R.drawable.avatar_golfer_4
            )
            return playerNames.mapIndexed { idx, rawName ->
                val clean = rawName.trim().ifEmpty { "${idx + 1}번캐디백" }
                PlayerInfo(
                    index = idx,
                    name = clean,
                    avatarRes = avatars[idx % avatars.size],
                    badgeColor = PlayerBadgeColors[idx % PlayerBadgeColors.size]
                )
            }
        }
}

data class GameResultRow(
    val badgeText: String,
    val playerName: String,
    val detailText: String,
    val isHighlighted: Boolean = false,
    val avatarRes: Int = R.drawable.avatar_golfer_1,
    val badgeColor: Color = MeadowGreen
)
