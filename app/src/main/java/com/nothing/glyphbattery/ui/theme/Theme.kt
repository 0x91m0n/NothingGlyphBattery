package com.nothing.glyphbattery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.nothing.glyphbattery.R

val NdotFont = FontFamily(
    Font(R.font.ndot, FontWeight.Normal),
    Font(R.font.ndot, FontWeight.Medium),
    Font(R.font.ndot, FontWeight.Bold)
)

private val DarkColorScheme = darkColorScheme(
    primary = NothingWhite,
    onPrimary = NothingBlack,
    secondary = NothingAccent,
    onSecondary = NothingBlack,
    background = NothingBlack,
    onBackground = NothingWhite,
    surface = NothingDarkGray,
    onSurface = NothingWhite,
    surfaceVariant = NothingMediumGray,
    onSurfaceVariant = NothingLightGray,
    error = NothingRed,
)

private val NothingTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 56.sp,
        letterSpacing = (-1).sp
    ),
    displayMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp
    ),
    titleLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        letterSpacing = 1.sp
    ),
    titleMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.5.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        letterSpacing = 0.4.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.3.sp
    ),
    bodySmall = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.3.sp,
        color = NothingLightGray
    ),
    labelLarge = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.sp
    ),
    labelMedium = TextStyle(
        fontFamily = NdotFont,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        letterSpacing = 0.5.sp
    )
)

@Composable
fun GlyphBatteryTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = NothingTypography,
        content = content
    )
}
