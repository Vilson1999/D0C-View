package com.example.ui.theme

import android.app.Activity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0A192F),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = Color(0xFF1F2736),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = Color(0xFFE8ECF4),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
private fun animateColor(target: Color): Color {
    val color by animateColorAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 260),
        label = "themeColor"
    )
    return color
}

@Composable
private fun rememberAnimatedColorScheme(target: ColorScheme): ColorScheme {
    return target.copy(
        primary = animateColor(target.primary),
        onPrimary = animateColor(target.onPrimary),
        primaryContainer = animateColor(target.primaryContainer),
        onPrimaryContainer = animateColor(target.onPrimaryContainer),
        secondary = animateColor(target.secondary),
        background = animateColor(target.background),
        onBackground = animateColor(target.onBackground),
        surface = animateColor(target.surface),
        onSurface = animateColor(target.onSurface),
        surfaceVariant = animateColor(target.surfaceVariant),
        onSurfaceVariant = animateColor(target.onSurfaceVariant),
        outline = animateColor(target.outline),
        outlineVariant = animateColor(target.outlineVariant),
        error = animateColor(target.error)
    )
}

@Composable
fun DocVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = rememberAnimatedColorScheme(if (darkTheme) DarkColorScheme else LightColorScheme)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = AppShapes,
        content = content
    )
}
