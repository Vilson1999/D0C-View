package com.example.ui.theme

import android.app.Activity
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsEndWidth
import androidx.compose.foundation.layout.windowInsetsStartWidth
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val LocalMaterialUiEnabled = staticCompositionLocalOf { true }
val LocalMaterialIconsEnabled = staticCompositionLocalOf { true }

object SystemBarContrastPolicy {
    fun shouldUseDarkIcons(backgroundLuminance: Float): Boolean = backgroundLuminance >= 0.5f

    fun styleForColor(color: Color): SystemBarStyle {
        val argb = color.toArgb()
        return if (shouldUseDarkIcons(color.luminance())) {
            SystemBarStyle.light(argb, argb)
        } else {
            SystemBarStyle.dark(argb)
        }
    }
}

@Composable
fun SyncSystemBarsAppearance(
    statusBarColor: Color,
    navigationBarColor: Color
) {
    val view = LocalView.current
    if (view.isInEditMode) return
    val useDarkStatusIcons = SystemBarContrastPolicy.shouldUseDarkIcons(statusBarColor.luminance())
    val useDarkNavIcons = SystemBarContrastPolicy.shouldUseDarkIcons(navigationBarColor.luminance())
    SideEffect {
        val activity = view.context as? ComponentActivity
        if (activity != null) {
            activity.enableEdgeToEdge(
                statusBarStyle = SystemBarContrastPolicy.styleForColor(statusBarColor),
                navigationBarStyle = SystemBarContrastPolicy.styleForColor(navigationBarColor)
            )
        } else {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = useDarkStatusIcons
                    isAppearanceLightNavigationBars = useDarkNavIcons
                }
            }
        }
    }
}

@Composable
fun BoxScope.NavigationBarSideBackdrop(color: Color) {
    Box(
        modifier = Modifier
            .align(Alignment.CenterStart)
            .windowInsetsStartWidth(WindowInsets.navigationBars)
            .fillMaxHeight()
            .background(color)
    )
    Box(
        modifier = Modifier
            .align(Alignment.CenterEnd)
            .windowInsetsEndWidth(WindowInsets.navigationBars)
            .fillMaxHeight()
            .background(color)
    )
}

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

// Classic Teal Green Schemes (when Material UI toggle is turned off)
private val ClassicDarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = Color(0xFF5EEAD4),
    onSecondary = Color(0xFF00201C),
    secondaryContainer = Color(0xFF115E59),
    onSecondaryContainer = Color(0xFFCCFBF1),
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
    secondary = Color(0xFF00897B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE0F2F1),
    onSecondaryContainer = Color(0xFF00332C),
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
    useMaterialIcons: Boolean = true,
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
    SyncSystemBarsAppearance(
        statusBarColor = colorScheme.background,
        navigationBarColor = colorScheme.background
    )

    CompositionLocalProvider(
        LocalMaterialUiEnabled provides useMaterialUi,
        LocalMaterialIconsEnabled provides useMaterialIcons
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = if (useMaterialUi) MaterialAppShapes else ClassicAppShapes,
            content = content
        )
    }
}
