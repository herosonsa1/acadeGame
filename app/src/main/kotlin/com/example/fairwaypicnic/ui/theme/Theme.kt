package com.example.fairwaypicnic.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val GhibliLightColorScheme = lightColorScheme(
    primary = MeadowGreen,
    onPrimary = PureCardWhite,
    primaryContainer = MeadowGreenSoft,
    onPrimaryContainer = PineDeep,
    secondary = TerracottaWood,
    onSecondary = PureCardWhite,
    secondaryContainer = TerracottaSoft,
    onSecondaryContainer = OakWood,
    tertiary = SkyPondBlue,
    onTertiary = PureCardWhite,
    tertiaryContainer = SkyPondSoft,
    onTertiaryContainer = InkPrimary,
    background = PaperCream,
    onBackground = InkPrimary,
    surface = PureCardWhite,
    onSurface = InkPrimary,
    surfaceVariant = WarmIvory,
    onSurfaceVariant = InkSecondary,
    outline = SubtleDivider
)

@Composable
fun FairwayPicnicTheme(
    // 항상 선명한 라이트 수채화 테마를 기본값으로 유지
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = GhibliLightColorScheme,
        typography = Typography,
        content = content
    )
}
