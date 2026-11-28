package com.example.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.DocViewerAppIcon
import com.example.ui.theme.DocPillColor
import com.example.ui.theme.ExcelPillColor
import com.example.ui.theme.LocalAnimationsEnabled
import com.example.ui.theme.PdfPillColor
import com.example.ui.theme.PptPillColor
import com.example.ui.theme.SyncSystemBarsAppearance
import com.example.ui.theme.TextPillColor
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

@Composable
fun AppOpenLoadingScreen(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    syncSystemBars: Boolean = true
) {
    if (syncSystemBars) {
        SyncSystemBarsAppearance(
            statusBarColor = MaterialTheme.colorScheme.background,
            navigationBarColor = MaterialTheme.colorScheme.background
        )
    }
    val animationsEnabled = LocalAnimationsEnabled.current
    var started by remember { mutableStateOf(!animationsEnabled) }

    LaunchedEffect(Unit) {
        started = true
        delay(2000L)
        onFinished()
    }

    val logoScale by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 1f else 0.72f,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessLow
            )
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "appOpenLogoScale"
    )
    val logoAlpha by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 360, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "appOpenLogoAlpha"
    )
    val titleAlpha by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 380, delayMillis = 120, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "appOpenTitleAlpha"
    )
    val titleOffsetY by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 0f else 22f,
        animationSpec = if (animationsEnabled) {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "appOpenTitleOffset"
    )
    val devAlpha by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 1f else 0f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 380, delayMillis = 220, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "appOpenDevAlpha"
    )
    val devOffsetY by animateFloatAsState(
        targetValue = if (started || !animationsEnabled) 0f else 18f,
        animationSpec = if (animationsEnabled) tween(durationMillis = 420, delayMillis = 180, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "appOpenDevOffset"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "appOpenPulse")
    val glowPulseAnim by infiniteTransition.animateFloat(
        initialValue = 0.88f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "appOpenGlowPulse"
    )
    val wavePhaseAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "appOpenDotsWave"
    )
    val glowPulse = if (animationsEnabled) glowPulseAnim else 1f
    val wavePhase = if (animationsEnabled) wavePhaseAnim else 0f

    val glowPrimary = MaterialTheme.colorScheme.primary
    val glowSecondary = DocPillColor
    val dotColors = remember {
        listOf(PdfPillColor, DocPillColor, ExcelPillColor, PptPillColor, TextPillColor)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .displayCutoutPadding()
            .testTag("app_open_loading_screen"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(horizontal = 32.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .drawBehind {
                        val radius = size.maxDimension * 0.85f * glowPulse
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    glowSecondary.copy(alpha = 0.18f),
                                    glowPrimary.copy(alpha = 0.08f),
                                    Color.Transparent
                                ),
                                center = center,
                                radius = radius
                            ),
                            radius = radius
                        )
                    }
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    shadowElevation = 10.dp,
                    color = Color.Transparent,
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = logoScale
                            scaleY = logoScale
                            alpha = logoAlpha
                        }
                        .testTag("app_open_logo")
                ) {
                    DocViewerAppIcon(size = 112.dp)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = titleAlpha
                        translationY = titleOffsetY
                    }
                    .testTag("app_open_title")
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = stringResource(R.string.developer_name),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = devAlpha
                        translationY = devOffsetY
                    }
                    .testTag("app_open_developer")
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.graphicsLayer { alpha = devAlpha }
            ) {
                dotColors.forEachIndexed { index, dotColor ->
                    val wave = (sin(wavePhase - index * 0.75f) + 1f) * 0.5f
                    val dotScale = 0.75f + 0.45f * wave
                    val dotAlpha = 0.45f + 0.55f * wave
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .graphicsLayer {
                                scaleX = dotScale
                                scaleY = dotScale
                                alpha = dotAlpha
                            }
                            .clip(CircleShape)
                            .background(dotColor)
                    )
                }
            }
        }
    }
}
