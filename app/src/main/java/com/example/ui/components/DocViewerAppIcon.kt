package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin Compose vector/Canvas app icon for D0C View.
 * Scales cleanly at any size with zero PNG/WebP image assets.
 */
@Composable
fun DocViewerAppIcon(
    modifier: Modifier = Modifier,
    size: Dp = 180.dp
) {
    val shape = RoundedCornerShape(size * 0.22f)
    Canvas(
        modifier = modifier
            .size(size)
            .clip(shape)
            .semantics { contentDescription = "D0C View Icon" }
    ) {
        val w = this.size.width
        val h = this.size.height

        // Vibrant sweep gradient backdrop matching the adaptive launcher background
        drawRect(
            brush = Brush.sweepGradient(
                colorStops = arrayOf(
                    0.00f to Color(0xFFE53935),
                    0.17f to Color(0xFF1E88E5),
                    0.33f to Color(0xFFFB8C00),
                    0.50f to Color(0xFF16A085),
                    0.67f to Color(0xFF8E24AA),
                    0.83f to Color(0xFF1E88E5),
                    1.00f to Color(0xFFE53935)
                ),
                center = Offset(w * 0.5f, h * 0.5f)
            )
        )

        // Subtle inner radial sheen for depth
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.16f),
                    Color.Black.copy(alpha = 0.12f)
                ),
                center = Offset(w * 0.35f, h * 0.28f),
                radius = w * 0.85f
            )
        )

        // Back folder silhouette (dark slate)
        val backFolderPath = Path().apply {
            val left = w * 0.20f
            val right = w * 0.80f
            val top = h * 0.20f
            val bottom = h * 0.78f
            val tabRight = w * 0.48f
            val tabStepY = h * 0.27f
            val r = w * 0.045f

            moveTo(left + r, top)
            lineTo(tabRight - r * 0.5f, top)
            lineTo(tabRight + w * 0.04f, tabStepY)
            lineTo(right - r, tabStepY)
            quadraticTo(right, tabStepY, right, tabStepY + r)
            lineTo(right, bottom - r)
            quadraticTo(right, bottom, right - r, bottom)
            lineTo(left + r, bottom)
            quadraticTo(left, bottom, left, bottom - r)
            lineTo(left, top + r)
            quadraticTo(left, top, left + r, top)
            close()
        }
        drawPath(path = backFolderPath, color = Color(0xFF1E293B))

        // Stacked category document sheets peeking above the front pocket
        // Sheet 1 (PPT - Orange, back-most visible)
        drawRoundRect(
            color = Color(0xFFEA580C),
            topLeft = Offset(w * 0.25f, h * 0.29f),
            size = Size(w * 0.50f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        // Sheet 2 (XML - Green)
        drawRoundRect(
            color = Color(0xFF16A34A),
            topLeft = Offset(w * 0.24f, h * 0.34f),
            size = Size(w * 0.52f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        // Sheet 3 (DOC - Blue)
        drawRoundRect(
            color = Color(0xFF2563EB),
            topLeft = Offset(w * 0.23f, h * 0.39f),
            size = Size(w * 0.54f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )
        // Sheet 4 (PDF - Red)
        drawRoundRect(
            color = Color(0xFFDC2626),
            topLeft = Offset(w * 0.22f, h * 0.44f),
            size = Size(w * 0.56f, h * 0.32f),
            cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
        )

        // Subtle shadow behind the front folder pocket
        val pocketLeft = w * 0.16f
        val pocketRight = w * 0.84f
        val pocketTop = h * 0.48f
        val pocketStepY = h * 0.535f
        val pocketBottom = h * 0.82f
        val pocketTabRight = w * 0.39f
        val rPocket = w * 0.055f

        val frontPocketPath = Path().apply {
            moveTo(pocketLeft + rPocket, pocketTop)
            lineTo(pocketTabRight - rPocket * 0.4f, pocketTop)
            lineTo(pocketTabRight + w * 0.04f, pocketStepY)
            lineTo(pocketRight - rPocket, pocketStepY)
            quadraticTo(pocketRight, pocketStepY, pocketRight, pocketStepY + rPocket)
            lineTo(pocketRight, pocketBottom - rPocket)
            quadraticTo(pocketRight, pocketBottom, pocketRight - rPocket, pocketBottom)
            lineTo(pocketLeft + rPocket, pocketBottom)
            quadraticTo(pocketLeft, pocketBottom, pocketLeft, pocketBottom - rPocket)
            lineTo(pocketLeft, pocketTop + rPocket)
            quadraticTo(pocketLeft, pocketTop, pocketLeft + rPocket, pocketTop)
            close()
        }

        // Soft drop shadow offset
        drawRoundRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(pocketLeft, pocketStepY + h * 0.018f),
            size = Size(pocketRight - pocketLeft, pocketBottom - pocketStepY),
            cornerRadius = CornerRadius(rPocket, rPocket)
        )

        // Front white pocket
        drawPath(
            path = frontPocketPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFF1F5F9)),
                startY = pocketTop,
                endY = pocketBottom
            )
        )

        // Crisp magnifying glass centered on the front pocket
        val lensCenter = Offset(w * 0.485f, h * 0.67f)
        val lensRadius = w * 0.078f
        val strokeWidth = w * 0.034f

        drawCircle(
            color = Color(0xFFDBEAFE),
            radius = lensRadius,
            center = lensCenter
        )
        drawCircle(
            color = Color(0xFF0F172A),
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(
                lensCenter.x + lensRadius * 0.72f,
                lensCenter.y + lensRadius * 0.72f
            ),
            end = Offset(
                lensCenter.x + lensRadius * 1.48f,
                lensCenter.y + lensRadius * 1.48f
            ),
            strokeWidth = strokeWidth * 1.05f,
            cap = StrokeCap.Round
        )

        // Subtle outer squircle hairline highlight
        drawRoundRect(
            color = Color.White.copy(alpha = 0.22f),
            topLeft = Offset(w * 0.008f, h * 0.008f),
            size = Size(w * 0.984f, h * 0.984f),
            cornerRadius = CornerRadius(w * 0.215f, h * 0.215f),
            style = Stroke(width = w * 0.016f)
        )
    }
}
