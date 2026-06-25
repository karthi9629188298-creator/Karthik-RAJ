package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ClinicalTealAccentDark,
    secondary = ClinicalEmeraldGreenDark,
    tertiary = InfoIndigo,
    background = ClinicalBackgroundDark,
    surface = ClinicalSurfaceDark,
    onPrimary = ClinicalBackgroundDark,
    onSecondary = ClinicalBackgroundDark,
    onBackground = ClinicalBackground,
    onSurface = ClinicalBackground
)

private val LightColorScheme = lightColorScheme(
    primary = ClinicalSlatePrimary,
    secondary = ClinicalTealAccent,
    tertiary = ClinicalEmeraldGreen,
    background = ClinicalBackground,
    surface = ClinicalSurface,
    onPrimary = ClinicalSurface,
    onSecondary = ClinicalSurface,
    onBackground = ClinicalSlatePrimary,
    onSurface = ClinicalSlatePrimary
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Turn off dynamic colors to enforce clinical branding integrity!
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
