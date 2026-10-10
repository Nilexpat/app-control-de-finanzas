package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandLime,
    onPrimary = BrandDark,
    primaryContainer = BrandDarkCard,
    onPrimaryContainer = Color.White,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueLight,
    onSecondaryContainer = BrandBlue,
    tertiary = BrandLime,
    onTertiary = BrandDark,
    background = DarkBackground,
    surface = DarkSurface,
    surfaceVariant = DarkSurfaceVariant,
    onBackground = Color.White,
    onSurface = Color.White,
    outline = Color(0xFF373A44)
)

private val LightColorScheme = lightColorScheme(
    primary = BrandDark,
    onPrimary = Color.White,
    primaryContainer = BrandLime,
    onPrimaryContainer = BrandDark,
    secondary = BrandBlue,
    onSecondary = Color.White,
    secondaryContainer = BrandBlueLight,
    onSecondaryContainer = BrandBlue,
    tertiary = BrandLime,
    onTertiary = BrandDark,
    tertiaryContainer = BrandLimeLight,
    onTertiaryContainer = BrandDark,
    background = BrandCanvas,
    surface = BrandSurface,
    surfaceVariant = BrandSurfaceMuted,
    onBackground = BrandTextPrimary,
    onSurface = BrandTextPrimary,
    onSurfaceVariant = BrandTextSecondary,
    outline = BrandBorder
)

@Composable
fun SocioFinanzasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
