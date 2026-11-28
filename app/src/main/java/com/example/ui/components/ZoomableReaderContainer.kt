package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.exp
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
        return 0f to 0f
    }

    fun doubleTapDragScale(
        startScale: Float,
        totalDeltaYPx: Float,
        sensitivityPx: Float
    ): Float {
        val safeStart = clampScale(startScale)
        if (sensitivityPx <= 0f || totalDeltaYPx == 0f) return safeStart
        return clampScale(safeStart * exp(totalDeltaYPx / sensitivityPx))
    }

    fun pinchTransformOffset(
        currentOffsetX: Float,
        currentOffsetY: Float,
        currentScale: Float,
        targetScale: Float,
        centroidX: Float,
        centroidY: Float,
        panX: Float,
        panY: Float,
        viewportWidthPx: Float,
        viewportHeightPx: Float
    ): Pair<Float, Float> {
        val safeCurrent = currentScale.coerceAtLeast(1f)
        val scaleFactor = targetScale / safeCurrent
        val centerX = viewportWidthPx / 2f
        val centerY = viewportHeightPx / 2f
        val rawX = (centroidX - centerX) * (1f - scaleFactor) + currentOffsetX * scaleFactor + panX
        val rawY = (centroidY - centerY) * (1f - scaleFactor) + currentOffsetY * scaleFactor + panY
        return clampOffset(rawX, rawY, targetScale, viewportWidthPx, viewportHeightPx)
    }

    fun fitPageSize(
        maxWidth: Float,
        maxHeight: Float,
        aspectRatio: Float
    ): Pair<Float, Float> {
        val safeW = maxWidth.coerceAtLeast(1f)
        val safeH = maxHeight.coerceAtLeast(1f)
        val safeAspect = aspectRatio.coerceIn(0.25f, 4f)
        val widthBoundHeight = safeW / safeAspect
        return if (widthBoundHeight <= safeH) {
            safeW to widthBoundHeight
        } else {
            (safeH * safeAspect) to safeH
        }
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

    var viewportWidthPx: Float by mutableFloatStateOf(0f)
        private set
    var viewportHeightPx: Float by mutableFloatStateOf(0f)
        private set

    private var multiTouchState by mutableStateOf(false)
    private var dragZoomState by mutableStateOf(false)
    private var hasUserInteracted = false

    val scale: Float get() = scaleAnim.value
    val offset: ComposeOffset get() = offsetAnim.value
    val zoomPercent: Int get() = (scale * 100f).roundToInt()
    val canZoomIn: Boolean get() = scale < ReaderZoomMath.MAX_SCALE - 0.01f
    val canZoomOut: Boolean get() = scale > ReaderZoomMath.MIN_SCALE + 0.01f
    val canInnerScroll: Boolean get() = !multiTouchState && !dragZoomState && scale <= 1.02f

    fun updateViewportSize(widthPx: Int, heightPx: Int) {
        viewportWidthPx = widthPx.toFloat().coerceAtLeast(0f)
        viewportHeightPx = heightPx.toFloat().coerceAtLeast(0f)
    }

    suspend fun reclampOffsetToViewport() {
        val (clampedX, clampedY) = ReaderZoomMath.clampOffset(
            offsetX = offsetAnim.value.x,
            offsetY = offsetAnim.value.y,
            scale = scaleAnim.value,
            viewportWidthPx = viewportWidthPx,
            viewportHeightPx = viewportHeightPx
        )
        if (abs(clampedX - offsetAnim.value.x) > 0.5f || abs(clampedY - offsetAnim.value.y) > 0.5f) {
            offsetAnim.snapTo(ComposeOffset(clampedX, clampedY))
        }
    }

    fun updateOnScaleCommitted(callback: (Float) -> Unit) {
        onScaleCommitted = callback
    }

    suspend fun syncInitialScaleIfUntouched(initialScale: Float) {
        val target = ReaderZoomMath.clampScale(initialScale)
        if (!hasUserInteracted && abs(scaleAnim.value - target) > 0.01f) {
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

    fun setDragZoomActive(active: Boolean) {
        if (dragZoomState != active) {
            dragZoomState = active
        }
    }

    suspend fun applyDoubleTapDragZoom(
        startScale: Float,
        startOffset: ComposeOffset,
        totalDeltaY: Float,
        sensitivityPx: Float
    ) {
        hasUserInteracted = true
        val safeStartScale = ReaderZoomMath.clampScale(startScale)
        val newScale = ReaderZoomMath.doubleTapDragScale(
            startScale = safeStartScale,
            totalDeltaYPx = totalDeltaY,
            sensitivityPx = sensitivityPx
        )
        val (clampedX, clampedY) = if (viewportWidthPx > 0f && viewportHeightPx > 0f) {
            ReaderZoomMath.pinchTransformOffset(
                currentOffsetX = startOffset.x,
                currentOffsetY = startOffset.y,
                currentScale = safeStartScale,
                targetScale = newScale,
                centroidX = viewportWidthPx / 2f,
                centroidY = viewportHeightPx / 2f,
                panX = 0f,
                panY = 0f,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        } else {
            0f to 0f
        }
        scaleAnim.snapTo(newScale)
        offsetAnim.snapTo(ComposeOffset(clampedX, clampedY))
    }

    suspend fun applyTransform(
        zoomChange: Float,
        panChange: ComposeOffset,
        centroid: ComposeOffset? = null,
        onHorizontalOverflowPan: ((Float) -> Float)? = null,
        onVerticalOverflowPan: ((Float) -> Float)? = null
    ) {
        hasUserInteracted = true
        val oldScale = scaleAnim.value
        val newScale = ReaderZoomMath.clampScale(oldScale * zoomChange)
        val isSingleFingerPan = zoomChange == 1f && newScale > 1.02f

        val (clampedX, clampedY) = if (centroid != null && zoomChange != 1f && viewportWidthPx > 0f && viewportHeightPx > 0f) {
            ReaderZoomMath.pinchTransformOffset(
                currentOffsetX = offsetAnim.value.x,
                currentOffsetY = offsetAnim.value.y,
                currentScale = oldScale,
                targetScale = newScale,
                centroidX = centroid.x,
                centroidY = centroid.y,
                panX = panChange.x,
                panY = panChange.y,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
        } else {
            val desiredX = offsetAnim.value.x + panChange.x
            val desiredY = offsetAnim.value.y + panChange.y
            val (cx, cy) = ReaderZoomMath.clampOffset(
                offsetX = desiredX,
                offsetY = desiredY,
                scale = newScale,
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx
            )
            if (isSingleFingerPan) {
                val overflowX = desiredX - cx
                val overflowY = desiredY - cy
                if (abs(overflowX) > 0.5f) {
                    onHorizontalOverflowPan?.invoke(overflowX)
                }
                if (abs(overflowY) > 0.5f) {
                    onVerticalOverflowPan?.invoke(overflowY)
                }
            }
            cx to cy
        }
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
    onVerticalOverflowPan: ((Float) -> Float)? = null,
    onHorizontalOverflowPan: ((Float) -> Float)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    val currentOnSingleTap by rememberUpdatedState(onSingleTap)
    val currentOnUserInteraction by rememberUpdatedState(onUserInteraction)
    val currentOnVerticalOverflowPan by rememberUpdatedState(onVerticalOverflowPan)
    val currentOnHorizontalOverflowPan by rememberUpdatedState(onHorizontalOverflowPan)

    LaunchedEffect(zoomState, zoomState.viewportWidthPx, zoomState.viewportHeightPx) {
        zoomState.reclampOffsetToViewport()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { size ->
                zoomState.updateViewportSize(size.width, size.height)
            }
            .pointerInput(zoomState) {
                val touchSlop = viewConfiguration.touchSlop
                val doubleTapSlopPx = maxOf(touchSlop * 6f, 48.dp.toPx())
                val dragZoomSensitivityPx = 180.dp.toPx()
                var lastTapUpTimeMs = 0L
                var lastTapUpPos = ComposeOffset.Zero
                var lastTapWasClean = false

                awaitEachGesture {
                    val firstDown = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val downTimeMs = firstDown.uptimeMillis
                    val downPos = firstDown.position
                    val doubleTapDt = downTimeMs - lastTapUpTimeMs
                    var isDoubleTapCandidate = lastTapWasClean &&
                        doubleTapDt in 0L..viewConfiguration.doubleTapTimeoutMillis &&
                        (downPos - lastTapUpPos).getDistance() <= doubleTapSlopPx
                    lastTapWasClean = false

                    var isDoubleTapDrag = false
                    var dragStartScale = zoomState.scale
                    var dragStartOffset = zoomState.offset
                    var dragStartY = downPos.y
                    var maxMoveDist = 0f
                    var sawMultiTouch = false
                    var panAccumulated = ComposeOffset.Zero
                    var pastTouchSlop = false
                    var didTransform = false

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val pressedCount = event.changes.count { it.pressed }
                            val isMultiTouch = pressedCount >= 2
                            if (isMultiTouch) {
                                sawMultiTouch = true
                                isDoubleTapCandidate = false
                                isDoubleTapDrag = false
                                zoomState.setDragZoomActive(false)
                            }
                            zoomState.setMultiTouchActive(isMultiTouch)

                            val primaryChange = event.changes.firstOrNull { it.id == firstDown.id }
                                ?: event.changes.firstOrNull()
                            if (primaryChange != null) {
                                val dist = (primaryChange.position - downPos).getDistance()
                                if (dist > maxMoveDist) maxMoveDist = dist
                            }

                            if (pressedCount == 0) {
                                val upTimeMs = primaryChange?.uptimeMillis ?: downTimeMs
                                val upPos = primaryChange?.position ?: downPos
                                val elapsedMs = upTimeMs - downTimeMs
                                if (!didTransform && !sawMultiTouch && !isDoubleTapCandidate &&
                                    maxMoveDist <= touchSlop &&
                                    elapsedMs <= viewConfiguration.longPressTimeoutMillis
                                ) {
                                    lastTapUpTimeMs = upTimeMs
                                    lastTapUpPos = upPos
                                    lastTapWasClean = true
                                }
                                break
                            }

                            if (isMultiTouch) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = false)

                                if (!pastTouchSlop) {
                                    panAccumulated += panChange
                                    if (abs(1f - zoomChange) > 0.005f || panAccumulated.getDistance() > touchSlop * 0.5f) {
                                        pastTouchSlop = true
                                    }
                                }
                                if (pastTouchSlop && (zoomChange != 1f || panChange != ComposeOffset.Zero)) {
                                    didTransform = true
                                    currentOnUserInteraction()
                                    val validCentroid = if (centroid != ComposeOffset.Unspecified) centroid else null
                                    scope.launch {
                                        zoomState.applyTransform(
                                            zoomChange = zoomChange,
                                            panChange = panChange,
                                            centroid = validCentroid
                                        )
                                    }
                                }
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) {
                                        change.consume()
                                    }
                                }
                            } else if (isDoubleTapCandidate) {
                                val change = primaryChange
                                if (change != null) {
                                    if (!isDoubleTapDrag) {
                                        if ((change.position - downPos).getDistance() > touchSlop) {
                                            isDoubleTapDrag = true
                                            didTransform = true
                                            dragStartScale = zoomState.scale
                                            dragStartOffset = zoomState.offset
                                            dragStartY = change.position.y
                                            zoomState.setDragZoomActive(true)
                                            currentOnUserInteraction()
                                            event.changes.forEach { it.consume() }
                                        }
                                    } else {
                                        val totalDy = change.position.y - dragStartY
                                        currentOnUserInteraction()
                                        scope.launch {
                                            zoomState.applyDoubleTapDragZoom(
                                                startScale = dragStartScale,
                                                startOffset = dragStartOffset,
                                                totalDeltaY = totalDy,
                                                sensitivityPx = dragZoomSensitivityPx
                                            )
                                        }
                                        event.changes.forEach { item ->
                                            if (item.positionChanged()) {
                                                item.consume()
                                            }
                                        }
                                    }
                                }
                            } else if (zoomState.scale > 1.02f) {
                                val panChange = event.calculatePan()
                                if (!pastTouchSlop) {
                                    panAccumulated += panChange
                                    if (panAccumulated.getDistance() > touchSlop) {
                                        pastTouchSlop = true
                                        didTransform = true
                                        currentOnUserInteraction()
                                        val firstPan = panAccumulated
                                        scope.launch {
                                            zoomState.applyTransform(
                                                zoomChange = 1f,
                                                panChange = firstPan,
                                                onHorizontalOverflowPan = currentOnHorizontalOverflowPan,
                                                onVerticalOverflowPan = currentOnVerticalOverflowPan
                                            )
                                        }
                                        event.changes.forEach { it.consume() }
                                    }
                                } else if (panChange != ComposeOffset.Zero) {
                                    didTransform = true
                                    currentOnUserInteraction()
                                    scope.launch {
                                        zoomState.applyTransform(
                                            zoomChange = 1f,
                                            panChange = panChange,
                                            onHorizontalOverflowPan = currentOnHorizontalOverflowPan,
                                            onVerticalOverflowPan = currentOnVerticalOverflowPan
                                        )
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        }
                    } finally {
                        zoomState.setMultiTouchActive(false)
                        zoomState.setDragZoomActive(false)
                        if (didTransform) {
                            zoomState.notifyScaleCommitted()
                        }
                    }
                }
            }
            .pointerInput(zoomState) {
                detectTapGestures(
                    onTap = { currentOnSingleTap() },
                    onDoubleTap = { tapOffset ->
                        currentOnUserInteraction()
                        scope.launch {
                            zoomState.toggleDoubleTap(tapOffset)
                        }
                    }
                )
            }
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
                    imageVector = AppIcons.zoomOut(),
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
                    imageVector = AppIcons.zoomIn(),
                    contentDescription = stringResource(R.string.action_zoom_in),
                    tint = fg,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
