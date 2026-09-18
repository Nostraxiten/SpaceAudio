package com.spaceaudio.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = SpaceBlack,
    secondary = NebulaViolet,
    onSecondary = TextPrimary,
    tertiary = ElectricEmerald,
    background = SpaceBlack,
    onBackground = TextPrimary,
    surface = SpaceCardBg,
    onSurface = TextPrimary,
    surfaceVariant = SpaceSurfaceSubtle,
    onSurfaceVariant = TextSecondary,
    outline = SpaceCardBorder
)

@Composable
fun SpaceAudioTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = DarkColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = SpaceBlack.toArgb()
                window.navigationBarColor = SpaceBlack.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = SpaceTypography,
        content = content
    )
}
