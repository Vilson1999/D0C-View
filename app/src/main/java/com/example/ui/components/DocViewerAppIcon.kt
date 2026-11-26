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
 * Pure Kotlin Compose vector/Canvas app icon for D0C View:
 * Clean white backdrop with frosted glassmorphic grey folder pocket,
 * stacked category sheets, and a frosted glass magnifier.
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

        // Pure crisp white background
        drawRect(color = Color.White)

        // Soft ambient glass reflection across background
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFF8FAFC),
                    Color(0xFFFFFFFF)
                ),
                center = Offset(w * 0.35f, h * 0.28f),
                radius = w * 0.85f
            )
        )

        // Back folder plate (cool light neutral slate behind sheets)
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
        drawPath(
            path = backFolderPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)),
                startY = h * 0.20f,
                endY = h * 0.78f
            )
        )

        // Stacked vibrant category document sheets peeking above the frosted grey pocket
        // Sheet 1 (PPT - Orange)
        drawRoundRect(
            color = Color(0xFFEA580C),
            topLeft = Offset(w * 0.24f, h * 0.25f),
            size = Size(w * 0.52f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.035f, w * 0.035f)
        )
        // Sheet 2 (EXCEL - Green)
        drawRoundRect(
            color = Color(0xFF16A34A),
            topLeft = Offset(w * 0.23f, h * 0.305f),
            size = Size(w * 0.54f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.035f, w * 0.035f)
        )
        // Sheet 3 (DOC - Blue)
        drawRoundRect(
            color = Color(0xFF2563EB),
            topLeft = Offset(w * 0.22f, h * 0.36f),
            size = Size(w * 0.56f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.035f, w * 0.035f)
        )
        // Sheet 4 (PDF - Red)
        drawRoundRect(
            color = Color(0xFFDC2626),
            topLeft = Offset(w * 0.21f, h * 0.415f),
            size = Size(w * 0.58f, h * 0.34f),
            cornerRadius = CornerRadius(w * 0.035f, w * 0.035f)
        )

        val pocketLeft = w * 0.16f
        val pocketRight = w * 0.84f
        val pocketTop = h * 0.46f
        val pocketStepY = h * 0.52f
        val pocketBottom = h * 0.82f
        val pocketTabRight = w * 0.39f
        val rPocket = w * 0.055f

        val frontPocketPath = Path().apply {
            moveTo(pocketLeft + rPocket, pocketTop)
            lineTo(pocketTabRight - rPocket * 0.4f, pocketTop)
            lineTo(pocketTabRight + w * 0.045f, pocketStepY)
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

        // Soft ambient elevation shadow beneath the frosted grey pocket
        drawRoundRect(
            color = Color(0xFF0F172A).copy(alpha = 0.12f),
            topLeft = Offset(pocketLeft, pocketStepY + h * 0.022f),
            size = Size(pocketRight - pocketLeft, pocketBottom - pocketStepY),
            cornerRadius = CornerRadius(rPocket, rPocket)
        )

        // Frosted translucent grey glass pocket
        drawPath(
            path = frontPocketPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color(0xE8CBD5E1),
                    Color(0xEC94A3B8),
                    Color(0xF0788496)
                ),
                start = Offset(pocketLeft, pocketTop),
                end = Offset(pocketRight, pocketBottom)
            )
        )

        // Diagonal frosted glass sheen inside the grey folder pocket
        drawPath(
            path = frontPocketPath,
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color.White.copy(alpha = 0.06f),
                    Color.White.copy(alpha = 0.18f)
                ),
                start = Offset(pocketLeft, pocketTop),
                end = Offset(pocketRight * 0.8f, pocketBottom)
            )
        )

        // Crisp specular glass rim around the grey pocket
        drawPath(
            path = frontPocketPath,
            color = Color.White.copy(alpha = 0.72f),
            style = Stroke(width = w * 0.014f)
        )

        // Frosted glass magnifying glass centered on the grey pocket
        val lensCenter = Offset(w * 0.485f, h * 0.665f)
        val lensRadius = w * 0.082f
        val strokeWidth = w * 0.032f

        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.45f),
                    Color(0xFFDBEAFE).copy(alpha = 0.25f)
                ),
                start = Offset(lensCenter.x - lensRadius, lensCenter.y - lensRadius),
                end = Offset(lensCenter.x + lensRadius, lensCenter.y + lensRadius)
            ),
            radius = lensRadius,
            center = lensCenter
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.92f),
            radius = lensRadius,
            center = lensCenter,
            style = Stroke(width = strokeWidth)
        )
        drawLine(
            color = Color.White.copy(alpha = 0.92f),
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

        // Subtle outer border so white icon stands out cleanly on light cards too
        drawRoundRect(
            color = Color(0xFFE2E8F0),
            topLeft = Offset(w * 0.008f, h * 0.008f),
            size = Size(w * 0.984f, h * 0.984f),
            cornerRadius = CornerRadius(w * 0.215f, h * 0.215f),
            style = Stroke(width = w * 0.016f)
        )
    }
}
