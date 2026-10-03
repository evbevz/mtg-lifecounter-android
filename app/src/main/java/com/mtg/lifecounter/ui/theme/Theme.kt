package com.mtg.lifecounter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.mtg.lifecounter.R

val GaramondFamily = FontFamily(Font(R.font.garamond))

val DarkBackground = Color(0xFF0D0D1A)
val CardNumbersColor = Color(0xFFE4B272)
val CardNumbersBorderColor = Color(0xFF392204)
val GoldAccent = Color(0xFFE4B272)
val Player1Color = Color(0xFF3D7AC7)
val Player2Color = Color(0xFFC73D3D)
val SurfaceDark = Color(0xFF16162B)
val OnSurfaceDark = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = GoldAccent,
    secondary = CardNumbersBorderColor,
    background = DarkBackground,
    surface = SurfaceDark,
    onPrimary = DarkBackground,
    onSecondary = DarkBackground,
    onBackground = OnSurfaceDark,
    onSurface = OnSurfaceDark
)

private val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 32.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp
    ),
    titleLarge = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 18.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = GaramondFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = GaramondFamily,
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
