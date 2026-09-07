package com.mtg.lifecounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val DarkBackground = Color(0xFF0D0D1A)
val CardBackground = Color(0xFFF5E6C8)
val CardBorder = Color(0xFF8B6914)
val CardNumberColor = Color(0xFF392204)
val GoldAccent = Color(0xFFE4B272)
val GoldAccentDark = Color(0xFFB8860B)
val Player1Color = Color(0xFF3D7AC7)
val Player2Color = Color(0xFFC73D3D)
val PoisonGreen = Color(0xFF6B8E23)
val SurfaceDark = Color(0xFF16162B)
val OnSurfaceDark = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    secondary = GoldAccentDark,
    tertiary = Player1Color,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    )
)

@Composable
fun MTGLifeCounterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = AppTypography,
        content = content
    )
}
