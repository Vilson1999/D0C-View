package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalMaterialUiEnabled = staticCompositionLocalOf { true }

// Expressive Material Design 3 Light & Dark Schemes (fallback for Android 9-11 or fixed M3)
private val MaterialLightColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDCE6FF),
    onPrimaryContainer = Color(0xFF0A2463),
    secondary = Color(0xFF0F766E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCFBF1),
    onSecondaryContainer = Color(0xFF064E3B),
    tertiary = Color(0xFF7C3AED),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFEDE9FE),
    onTertiaryContainer = Color(0xFF3B0764),
    background = Color(0xFFF6F8FD),
    onBackground = Color(0xFF111827),
    surface = Color(0xFFFCFDFE),
    onSurface = Color(0xFF111827),
    surfaceVariant = Color(0xFFE7ECF7),
    onSurfaceVariant = Color(0xFF475569),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F4FB),
    surfaceContainer = Color(0xFFEBEFF9),
    surfaceContainerHigh = Color(0xFFE3E9F5),
    surfaceContainerHighest = Color(0xFFDCE3F1),
    outline = Color(0xFFCBD5E1),
    outlineVariant = Color(0xFFDCE2EF),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val MaterialDarkColorScheme = darkColorScheme(
    primary = Color(0xFF93C5FD),
    onPrimary = Color(0xFF0A2463),
    primaryContainer = Color(0xFF1D4ED8),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
    tertiary = Color(0xFFC4B5FD),
    onTertiary = Color(0xFF2E1065),
    tertiaryContainer = Color(0xFF5B21B6),
    onTertiaryContainer = Color(0xFFEDE9FE),
    background = Color(0xFF0D111A),
    onBackground = Color(0xFFF1F5F9),
    surface = Color(0xFF121824),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF222A3B),
    onSurfaceVariant = Color(0xFFA3B1C6),
    surfaceContainerLowest = Color(0xFF0A0E16),
    surfaceContainerLow = Color(0xFF171E2C),
    surfaceContainer = Color(0xFF1C2434),
    surfaceContainerHigh = Color(0xFF242E42),
    surfaceContainerHighest = Color(0xFF2B374F),
    outline = Color(0xFF3A465E),
    outlineVariant = Color(0xFF263044),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

// Classic Neutral / Minimal Schemes (when Material UI toggle is turned off)
private val ClassicDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Color(0xFF60A5FA),
    onSecondary = Color(0xFF0A192F),
    secondaryContainer = Color(0xFF1E293B),
    onSecondaryContainer = Color(0xFFE2E8F0),
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkBackground,
    surfaceContainerLow = DarkSurface,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = Color(0xFF232B3B),
    surfaceContainerHighest = Color(0xFF2A3447),
    outline = DarkOutline,
    outlineVariant = Color(0xFF1F2736),
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A)
)

private val ClassicLightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = Color(0xFF2563EB),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE2E8F0),
    onSecondaryContainer = Color(0xFF0F172A),
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightSurface,
    surfaceContainer = LightSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE6EAF2),
    surfaceContainerHighest = Color(0xFFDDE3EE),
    outline = LightOutline,
    outlineVariant = Color(0xFFE8ECF4),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF)
)

private val MaterialAppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

private val ClassicAppShapes = Shapes(
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
        onSecondary = animateColor(target.onSecondary),
        secondaryContainer = animateColor(target.secondaryContainer),
        onSecondaryContainer = animateColor(target.onSecondaryContainer),
        tertiary = animateColor(target.tertiary),
        onTertiary = animateColor(target.onTertiary),
        tertiaryContainer = animateColor(target.tertiaryContainer),
        onTertiaryContainer = animateColor(target.onTertiaryContainer),
        background = animateColor(target.background),
        onBackground = animateColor(target.onBackground),
        surface = animateColor(target.surface),
        onSurface = animateColor(target.onSurface),
        surfaceVariant = animateColor(target.surfaceVariant),
        onSurfaceVariant = animateColor(target.onSurfaceVariant),
        surfaceContainerLowest = animateColor(target.surfaceContainerLowest),
        surfaceContainerLow = animateColor(target.surfaceContainerLow),
        surfaceContainer = animateColor(target.surfaceContainer),
        surfaceContainerHigh = animateColor(target.surfaceContainerHigh),
        surfaceContainerHighest = animateColor(target.surfaceContainerHighest),
        outline = animateColor(target.outline),
        outlineVariant = animateColor(target.outlineVariant),
        error = animateColor(target.error)
    )
}

@Composable
fun DocVaultTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    useMaterialUi: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val targetScheme = if (useMaterialUi) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            if (darkTheme) MaterialDarkColorScheme else MaterialLightColorScheme
        }
    } else {
        if (darkTheme) ClassicDarkColorScheme else ClassicLightColorScheme
    }

    val colorScheme = rememberAnimatedColorScheme(targetScheme)
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

    CompositionLocalProvider(LocalMaterialUiEnabled provides useMaterialUi) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = if (useMaterialUi) MaterialAppShapes else ClassicAppShapes,
            content = content
        )
    }
}
