package com.ejemplo.parkeasy.presentation.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

private val LightColors: ColorScheme = lightColorScheme(
    primary = Color(0xFF0057D9),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD9E6FF),
    onPrimaryContainer = Color(0xFF001A41),
    secondary = Color(0xFFB4138F),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFD7F3),
    onSecondaryContainer = Color(0xFF3A0030),
    tertiary = Color(0xFF6D3CE0),
    tertiaryContainer = Color(0xFFE8DDFF),
    onTertiaryContainer = Color(0xFF210A5C),
    background = Color(0xFFF3F5FD),
    surface = Color(0xFFF3F5FD),
    surfaceVariant = Color(0xFFE0E4F3),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFEAEEFB),
    surfaceContainerHighest = Color(0xFFE0E5F7),
    outline = Color(0xFF7B84A8),
    error = Color(0xFFBA1A1A),
)

/** Azul noche con acentos de neón: cian, magenta y violeta. Los textos mantienen contraste alto. */
private val DarkColors: ColorScheme = darkColorScheme(
    primary = Color(0xFF00E5FF),
    onPrimary = Color(0xFF00222B),
    primaryContainer = Color(0xFF0B2F66),
    onPrimaryContainer = Color(0xFFBDF4FF),
    secondary = Color(0xFFFF2BD6),
    onSecondary = Color(0xFF2B0028),
    secondaryContainer = Color(0xFF3A0F44),
    onSecondaryContainer = Color(0xFFFFD1F7),
    tertiary = Color(0xFF8B5CF6),
    onTertiary = Color(0xFF1A0A4A),
    tertiaryContainer = Color(0xFF2A1A5E),
    onTertiaryContainer = Color(0xFFE4D9FF),
    background = Color(0xFF070A14),
    onBackground = Color(0xFFE8EEFF),
    surface = Color(0xFF0C1020),
    onSurface = Color(0xFFE8EEFF),
    surfaceVariant = Color(0xFF182040),
    onSurfaceVariant = Color(0xFFA9B6E0),
    surfaceContainerLowest = Color(0xFF080B18),
    surfaceContainerLow = Color(0xFF0D1326),
    surfaceContainer = Color(0xFF10172E),
    surfaceContainerHigh = Color(0xFF161F3C),
    surfaceContainerHighest = Color(0xFF1D2850),
    outline = Color(0xFF3C4A80),
    outlineVariant = Color(0xFF232C55),
    error = Color(0xFFFF4D6D),
    onError = Color(0xFF2B0008),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(26.dp),
)

private fun appTypography(neon: Boolean, glow: Color): Typography {
    val base = Typography()
    val titleGlow = if (neon) Shadow(glow.copy(alpha = 0.65f), Offset.Zero, 22f) else null
    return base.copy(
        headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp, shadow = titleGlow),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold, shadow = titleGlow),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
    )
}

/** Tema de PARKEASY. Claro (sobrio) u oscuro (con neón) según el parámetro. */
@Composable
fun ParkEasyTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkColors else LightColors
    CompositionLocalProvider(LocalNeon provides darkTheme) {
        MaterialTheme(
            colorScheme = colors,
            shapes = AppShapes,
            typography = appTypography(darkTheme, colors.primary),
            content = content,
        )
    }
}
