package com.example.fairwaypicnic.ui.theme

import androidx.compose.ui.graphics.Color

// 지브리 수채화 라이트 테마 전용 저채도 자연 색채 팔레트
val PaperCream = Color(0xFFFAF7F0)
val WarmIvory = Color(0xFFF3EFE4)
val PureCardWhite = Color(0xFFFFFFFF)
val SoftSand = Color(0xFFEBE5D5)
val SubtleDivider = Color(0xFFE0D9C8)

val MeadowGreen = Color(0xFF3B6E4C)
val MeadowGreenSoft = Color(0xFFDCE9DF)
val CloverLeaf = Color(0xFF5A8F69)
val PineDeep = Color(0xFF284B34)

val TerracottaWood = Color(0xFFB5633F)
val TerracottaSoft = Color(0xFFF4E2D9)
val OakWood = Color(0xFF7C583B)
val LightBirch = Color(0xFFD8BE9E)

val SkyPondBlue = Color(0xFF5286A3)
val SkyPondSoft = Color(0xFFDCEAF2)
val SunlightAmber = Color(0xFFC48829)
val SunlightSoft = Color(0xFFF7ECD5)

val InkPrimary = Color(0xFF1F2721)
val InkSecondary = Color(0xFF57635A)
val InkMuted = Color(0xFF838E85)

// 참가자 아바타 테두리 구분용 6색
val PlayerBadgeColors = listOf(
    Color(0xFF2F6F4E), // 솔숲 세이지 그린
    Color(0xFF386E9B), // 맑은 호수 블루
    Color(0xFFB85435), // 단풍 테라코타
    Color(0xFF986B26), // 구운 도토리 브라운
    Color(0xFF6B5288), // 산들꽃 라벤더 퍼플
    Color(0xFF2E787A)  // 비취 연못 청록
)

// 결과 순위 및 행별 배지 전용 고유 6색 팔레트 (모든 순위 배지가 서로 다른 색상을 갖도록 분리)
val RankBadgeColors = listOf(
    Color(0xFFB85233), // 1순위 · 당첨 강조: 단풍 코랄 테라코타
    Color(0xFF2E6F4E), // 2순위: 솔숲 에메랄드 그린
    Color(0xFF356B96), // 3순위: 호수 코발트 블루
    Color(0xFF9A6B24), // 4순위: 도토리 골든 브라운
    Color(0xFF6B5087), // 5순위: 산들꽃 플럼 퍼플
    Color(0xFF2F7678)  // 6순위: 비취 연못 청록
)

fun distinctRankBadgeColor(rankIndexZeroBased: Int): Color {
    val safeIdx = ((rankIndexZeroBased % RankBadgeColors.size) + RankBadgeColors.size) % RankBadgeColors.size
    return RankBadgeColors[safeIdx]
}
