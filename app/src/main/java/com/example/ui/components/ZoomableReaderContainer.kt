package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset as ComposeOffset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object ReaderZoomMath {
    const val MIN_SCALE = 1f
    const val MAX_SCALE = 4f
    const val STEP_SCALE = 0.25f

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    fun stepZoomIn(currentScale: Float): Float {
        val next = ((currentScale / STEP_SCALE).toInt() + 1) * STEP_SCALE
        return clampScale(if (next - currentScale < 0.05f) next + STEP_SCALE else next)
    }

    fun stepZoomOut(currentScale: Float): Float {
        val next = ((currentScale / STEP_SCALE).toInt()) * STEP_SCALE
        val target = if (currentScale - next < 0.05f) next - STEP_SCALE else next
        return clampScale(target)
    }

    fun clampOffset(
        offsetX: Float,
        offsetY: Float,
        scale: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Float, Float> {
        if (scale <= 1.01f || viewportWidthPx <= 0f || viewportHeightPx <= 0f) {
            return 0f to 0f
        }
        val maxX = ((viewportWidthPx * (scale - 1f)) / 2f).coerceAtLeast(0f)
        val maxY = ((viewportHeightPx * (scale - 1f)) / 2f).coerceAtLeast(0f)
        return offsetX.coerceIn(-maxX, maxX) to offsetY.coerceIn(-maxY, maxY)
    }

    fun doubleTapTargetOffset(
        tapX: Float,
        tapY: Float,
        targetScale: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Float, Float> {
        val rawX = (viewportWidthPx / 2f - tapX) * (targetScale - 1f)
        val rawY = (viewportHeightPx / 2f - tapY) * (targetScale - 1f)
        return clampOffset(rawX, rawY, targetScale, viewportWidthPx, viewportHeightPx)
    }
}

@Stable
class ReaderZoomState(
    private val doubleTapZoom: Float,
    initialScale: Float = ReaderZoomMath.MIN_SCALE,
    private var onScaleCommitted: (Float) -> Unit = {}
) {
    private val scaleAnim = Animatable(ReaderZoomMath.clampScale(initialScale))
    private val offsetAnim = Animatable(ComposeOffset.Zero, ComposeOffset.VectorConverter)

    var viewportWidthPx: Float = 0f
        private set
    var viewportHeightPx: Float = 0f
        private set

    private var multiTouchState by mutableStateOf(false)
    private var hasUserInteracted = false

    val scale: Float get() = scaleAnim.value
    val offset: ComposeOffset get() = offsetAnim.value
    val zoomPercent: Int get() = (scale * 100f).roundToInt()
    val canZoomIn: Boolean get() = scale < ReaderZoomMath.MAX_SCALE - 0.01f
    val canZoomOut: Boolean get() = scale > ReaderZoomMath.MIN_SCALE + 0.01f
    val canInnerScroll: Boolean get() = !multiTouchState && scale <= 1.02f

    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        viewportWidthPx = widthPx.toFloat().coerceAtLeast(0f)
        viewportHeightPx = heightPx.toFloat().coerceAtLeast(0f)
    }

    fun updateOnScaleCommitted(callback: (Float) -> Unit) {
        onScaleCommitted = callback
    }

    suspend fun syncInitialScaleIfUntouched(initialScale: Float) {
        val target = ReaderZoomMath.clampScale(initialScale)
        if (!hasUserInteracted && kotlin.math.abs(scaleAnim.value - target) > 0.01f) {
            scaleAnim.snapTo(target)
            offsetAnim.snapTo(ComposeOffset.Zero)
        }
    }

    fun notifyScaleCommitted() {
        onScaleCommitted(ReaderZoomMath.clampScale(scaleAnim.value))
    }

    fun setMultiTouchActive(active: Boolean) {
        if (multiTouchState != active) {
            multiTouchState = active
        }
    }

    suspend fun applyTransform(zoomChange: Float, panChange: ComposeOffset) {
        hasUserInteracted = true
        val newScale = ReaderZoomMath.clampScale(scaleAnim.value * zoomChange)
        val (clampedX, clampedY) = ReaderZoomMath.clampOffset(
            offsetX = offsetAnim.value.x + panChange.x,
            offsetY = offsetAnim.value.y + panChange.y,
            scale = newScale,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx
        )
        scaleAnim.snapTo(newScale)
        offsetAnim.snapTo(ComposeOffset(clampedX, clampedY))
    }

    suspend fun animateToScale(targetScale: Float) {
        hasUserInteracted = true
        val clampedScale = ReaderZoomMath.clampScale(targetScale)
        onScaleCommitted(clampedScale)
        val (clampedX, clampedY) = ReaderZoomMath.clampOffset(
            offsetX = offsetAnim.value.x,
            offsetY = offsetAnim.value.y,
            scale = clampedScale,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx
        )
        val scaleSpec = spring<Float>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
        val offsetSpec = spring<ComposeOffset>(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow)
        coroutineScope {
            launch { scaleAnim.animateTo(clampedScale, scaleSpec) }
            launch { offsetAnim.animateTo(ComposeOffset(clampedX, clampedY), offsetSpec) }
        }
    }

    suspend fun zoomIn() = animateToScale(ReaderZoomMath.stepZoomIn(scaleAnim.value))

    suspend fun zoomOut() = animateToScale(ReaderZoomMath.stepZoomOut(scaleAnim.value))

    suspend fun reset() = animateToScale(ReaderZoomMath.MIN_SCALE)

    suspend fun toggleDoubleTap(tapOffset: ComposeOffset) {
        hasUserInteracted = true
        val scaleSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        val offsetSpec = spring<ComposeOffset>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)
        if (scaleAnim.value > 1.05f) {
            onScaleCommitted(ReaderZoomMath.MIN_SCALE)
            coroutineScope {
                launch { scaleAnim.animateTo(ReaderZoomMath.MIN_SCALE, scaleSpec) }
                launch { offsetAnim.animateTo(ComposeOffset.Zero, offsetSpec) }
            }
        } else {
            val targetScale = doubleTapZoom.coerceIn(1.5f, 3.5f)
            onScaleCommitted(targetScale)
            val (tx, ty) = ReaderZoomMath.doubleTapTargetOffset(
                tapX = tapOffset.x,
                tapY = tapOffset.y,
                targetScale = targetScale,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
            coroutineScope {
                launch { scaleAnim.animateTo(targetScale, scaleSpec) }
                launch { offsetAnim.animateTo(ComposeOffset(tx, ty), offsetSpec) }
            }
        }
    }
}

@Composable
fun rememberReaderZoomState(
    key: Any?,
    doubleTapZoom: Float,
    initialScale: Float = ReaderZoomMath.MIN_SCALE,
    onScaleCommitted: (Float) -> Unit = {}
): ReaderZoomState {
    val state = remember(key, doubleTapZoom) {
        ReaderZoomState(
            doubleTapZoom = doubleTapZoom,
            initialScale = initialScale,
            onScaleCommitted = onScaleCommitted
        )
    }
    state.updateOnScaleCommitted(onScaleCommitted)
    LaunchedEffect(state, initialScale) {
        state.syncInitialScaleIfUntouched(initialScale)
    }
    return state
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ZoomableReaderViewport(
    zoomState: ReaderZoomState,
    onSingleTap: () -> Unit,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        if (zoomChange != 1f || panChange != ComposeOffset.Zero) {
            onUserInteraction()
            scope.launch {
                zoomState.applyTransform(zoomChange = zoomChange, panChange = panChange)
            }
        }
    }
    var wasTransforming by remember(zoomState) { mutableStateOf(false) }
    LaunchedEffect(transformState.isTransformInProgress, zoomState) {
        val inProgress = transformState.isTransformInProgress
        if (wasTransforming && !inProgress) {
            zoomState.notifyScaleCommitted()
        }
        wasTransforming = inProgress
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                zoomState.updateViewportSize(size.width, size.height)
            }
            .pointerInput(zoomState) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        zoomState.setMultiTouchActive(event.changes.count { it.pressed } >= 2)
                    }
                }
            }
            .pointerInput(zoomState, onSingleTap, onUserInteraction) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = { tapOffset ->
                        onUserInteraction()
                        scope.launch {
                            zoomState.toggleDoubleTap(tapOffset)
                        }
                    }
                )
            }
            .transformable(
                state = transformState,
                canPan = { zoomState.scale > 1.02f }
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = zoomState.scale,
                    scaleY = zoomState.scale,
                    translationX = zoomState.offset.x,
                    translationY = zoomState.offset.y
                ),
            content = content
        )
    }
}

@Composable
fun ReaderZoomControlsPill(
    zoomState: ReaderZoomState,
    onUserInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val fg = MaterialTheme.colorScheme.inverseOnSurface

    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
        shadowElevation = 4.dp,
        modifier = modifier.testTag("viewer_zoom_controls")
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(enabled = zoomState.canZoomOut) {
                        onUserInteraction()
                        scope.launch { zoomState.zoomOut() }
                    }
                    .alpha(if (zoomState.canZoomOut) 1f else 0.4f)
                    .testTag("viewer_zoom_out_button")
            ) {
                Icon(
                    imageVector = AppIcons.ZoomOut,
                    contentDescription = stringResource(R.string.action_zoom_out),
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(enabled = zoomState.canZoomOut) {
                        onUserInteraction()
                        scope.launch { zoomState.reset() }
                    }
                    .padding(horizontal = 4.dp, vertical = 6.dp)
                    .testTag("viewer_zoom_label")
            ) {
                Text(
                    text = "${zoomState.zoomPercent}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = fg
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(CircleShape)
                    .clickable(enabled = zoomState.canZoomIn) {
                        onUserInteraction()
                        scope.launch { zoomState.zoomIn() }
                    }
                    .alpha(if (zoomState.canZoomIn) 1f else 0.4f)
                    .testTag("viewer_zoom_in_button")
            ) {
                Icon(
                    imageVector = AppIcons.ZoomIn,
                    contentDescription = stringResource(R.string.action_zoom_in),
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
