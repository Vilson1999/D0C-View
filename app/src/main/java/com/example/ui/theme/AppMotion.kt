package com.example.ui.theme

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer

object AppMotionPolicy {
    val SmoothEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    const val SMOOTH_DURATION_MS = 260
    const val WINDOW_DURATION_MS = 300
    const val BOUNCY_DAMPING_RATIO = 0.48f
    const val GENTLE_BOUNCY_DAMPING_RATIO = 0.56f
    const val SMOOTH_SPRING_DAMPING = 0.88f

    fun <T> smoothTween(
        animationsEnabled: Boolean,
        durationMillis: Int = SMOOTH_DURATION_MS
    ): FiniteAnimationSpec<T> = if (animationsEnabled) {
        tween(durationMillis = durationMillis, easing = SmoothEasing)
    } else {
        snap()
    }

    fun <T> smoothSpring(
        animationsEnabled: Boolean,
        dampingRatio: Float = SMOOTH_SPRING_DAMPING,
        stiffness: Float = Spring.StiffnessMediumLow
    ): FiniteAnimationSpec<T> = if (animationsEnabled) {
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
    } else {
        snap()
    }

    fun <T> bouncySpring(
        animationsEnabled: Boolean,
        dampingRatio: Float = BOUNCY_DAMPING_RATIO,
        stiffness: Float = Spring.StiffnessMedium
    ): FiniteAnimationSpec<T> = if (animationsEnabled) {
        spring(dampingRatio = dampingRatio, stiffness = stiffness)
    } else {
        snap()
    }

    fun <T> bouncySpec(
        animationsEnabled: Boolean,
        dampingRatio: Float = BOUNCY_DAMPING_RATIO,
        stiffness: Float = Spring.StiffnessMedium
    ): AnimationSpec<T> = bouncySpring(animationsEnabled, dampingRatio, stiffness)
}

object AppMotion {

    @Composable
    fun rememberBouncyScale(
        pressed: Boolean,
        selected: Boolean = false,
        pressedScale: Float = 0.88f,
        selectedScale: Float = 1.05f,
        dampingRatio: Float = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
        stiffness: Float = Spring.StiffnessMedium,
        label: String = "bouncyScale"
    ): Float {
        val enabled = LocalAnimationsEnabled.current
        if (!enabled) return 1f
        val target = when {
            pressed -> pressedScale
            selected -> selectedScale
            else -> 1f
        }
        val scale by animateFloatAsState(
            targetValue = target,
            animationSpec = AppMotionPolicy.bouncySpring(
                animationsEnabled = true,
                dampingRatio = dampingRatio,
                stiffness = stiffness
            ),
            label = label
        )
        return scale
    }

    @Composable
    fun rememberSmoothColor(
        targetValue: Color,
        durationMillis: Int = 240,
        label: String = "smoothColor"
    ): Color {
        val enabled = LocalAnimationsEnabled.current
        if (!enabled) return targetValue
        val color by animateColorAsState(
            targetValue = targetValue,
            animationSpec = AppMotionPolicy.smoothTween(
                animationsEnabled = true,
                durationMillis = durationMillis
            ),
            label = label
        )
        return color
    }

    fun bouncyIconContentTransform(animationsEnabled: Boolean): ContentTransform {
        if (!animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        return (scaleIn(
            initialScale = 0.62f,
            animationSpec = spring(
                dampingRatio = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
                stiffness = Spring.StiffnessMedium
            )
        ) + fadeIn(tween(170, easing = AppMotionPolicy.SmoothEasing))).togetherWith(
            scaleOut(
                targetScale = 0.68f,
                animationSpec = tween(120, easing = AppMotionPolicy.SmoothEasing)
            ) + fadeOut(tween(110))
        )
    }

    fun smoothSlideHorizontalTransform(
        animationsEnabled: Boolean,
        forward: Boolean
    ): ContentTransform {
        if (!animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        val enterOffset: (Int) -> Int = { width -> if (forward) (width * 0.22f).toInt() else -(width * 0.16f).toInt() }
        val exitOffset: (Int) -> Int = { width -> if (forward) -(width * 0.16f).toInt() else (width * 0.22f).toInt() }
        return (slideInHorizontally(
            initialOffsetX = enterOffset,
            animationSpec = tween(AppMotionPolicy.SMOOTH_DURATION_MS, easing = AppMotionPolicy.SmoothEasing)
        ) + fadeIn(tween(220, easing = AppMotionPolicy.SmoothEasing))).togetherWith(
            slideOutHorizontally(
                targetOffsetX = exitOffset,
                animationSpec = tween(220, easing = AppMotionPolicy.SmoothEasing)
            ) + fadeOut(tween(160))
        )
    }

    fun smoothRevealTransform(animationsEnabled: Boolean): ContentTransform {
        if (!animationsEnabled) {
            return EnterTransition.None togetherWith ExitTransition.None
        }
        return (fadeIn(tween(240, easing = AppMotionPolicy.SmoothEasing)) +
            scaleIn(
                initialScale = 0.965f,
                animationSpec = tween(260, easing = AppMotionPolicy.SmoothEasing)
            )).togetherWith(fadeOut(tween(160)))
    }
}

@Composable
fun Modifier.bouncyPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.90f,
    selected: Boolean = false,
    selectedScale: Float = 1.0f,
    dampingRatio: Float = AppMotionPolicy.BOUNCY_DAMPING_RATIO
): Modifier {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = AppMotion.rememberBouncyScale(
        pressed = pressed,
        selected = selected,
        pressedScale = pressedScale,
        selectedScale = selectedScale,
        dampingRatio = dampingRatio,
        label = "modifierBouncyPressScale"
    )
    return if (scale == 1f) {
        this
    } else {
        this.graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
    }
}
