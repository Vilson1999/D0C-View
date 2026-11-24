package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Pure Kotlin Compose Canvas illustration for empty document states.
 * Adapts dynamically to Material 3 light/dark theme colors with zero image files.
 */
@Composable
fun EmptyDocumentsIllustration(
    accentColor: Color,
    isHiddenState: Boolean = false,
    modifier: Modifier = Modifier,
    size: Dp = 156.dp
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val surfaceVariantColor = MaterialTheme.colorScheme.surfaceVariant
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height

        // Tonal circular halo
        drawCircle(
            color = accentColor.copy(alpha = 0.10f),
            radius = w * 0.44f,
            center = Offset(w * 0.5f, h * 0.5f)
        )
        drawCircle(
            color = surfaceVariantColor.copy(alpha = 0.65f),
            radius = w * 0.34f,
            center = Offset(w * 0.5f, h * 0.5f)
        )

        // Back tilted sheet
        rotate(degrees = -7f, pivot = Offset(w * 0.46f, h * 0.50f)) {
            drawRoundRect(
                color = surfaceVariantColor,
                topLeft = Offset(w * 0.25f, h * 0.20f),
                size = Size(w * 0.44f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.055f, w * 0.055f)
            )
            drawRoundRect(
                color = outlineColor.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.25f, h * 0.20f),
                size = Size(w * 0.44f, h * 0.56f),
                cornerRadius = CornerRadius(w * 0.055f, w * 0.055f),
                style = Stroke(width = w * 0.015f)
            )
        }

        // Front upright document sheet with folded top-right corner
        val docLeft = w * 0.30f
        val docRight = w * 0.72f
        val docTop = h * 0.19f
        val docBottom = h * 0.77f
        val fold = w * 0.11f
        val r = w * 0.05f

        val sheetPath = Path().apply {
            moveTo(docLeft + r, docTop)
            lineTo(docRight - fold, docTop)
            lineTo(docRight, docTop + fold)
            lineTo(docRight, docBottom - r)
            quadraticTo(docRight, docBottom, docRight - r, docBottom)
            lineTo(docLeft + r, docBottom)
            quadraticTo(docLeft, docBottom, docLeft, docBottom - r)
            lineTo(docLeft, docTop + r)
            quadraticTo(docLeft, docTop, docLeft + r, docTop)
            close()
        }

        drawPath(path = sheetPath, color = surfaceColor)
        drawPath(
            path = sheetPath,
            color = outlineColor,
            style = Stroke(width = w * 0.018f, join = StrokeJoin.Round)
        )

        // Folded corner flap
        val foldPath = Path().apply {
            moveTo(docRight - fold, docTop)
            lineTo(docRight - fold, docTop + fold)
            lineTo(docRight, docTop + fold)
            close()
        }
        drawPath(path = foldPath, color = accentColor.copy(alpha = 0.18f))
        drawPath(
            path = foldPath,
            color = outlineColor,
            style = Stroke(width = w * 0.014f, join = StrokeJoin.Round)
        )

        // Minimal document lines
        val lineStroke = w * 0.022f
        drawLine(
            color = accentColor.copy(alpha = 0.55f),
            start = Offset(w * 0.37f, h * 0.36f),
            end = Offset(w * 0.55f, h * 0.36f),
            strokeWidth = lineStroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.35f),
            start = Offset(w * 0.37f, h * 0.45f),
            end = Offset(w * 0.64f, h * 0.45f),
            strokeWidth = lineStroke * 0.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.28f),
            start = Offset(w * 0.37f, h * 0.53f),
            end = Offset(w * 0.60f, h * 0.53f),
            strokeWidth = lineStroke * 0.8f,
            cap = StrokeCap.Round
        )
        drawLine(
            color = onSurfaceVariant.copy(alpha = 0.22f),
            start = Offset(w * 0.37f, h * 0.61f),
            end = Offset(w * 0.52f, h * 0.61f),
            strokeWidth = lineStroke * 0.8f,
            cap = StrokeCap.Round
        )

        // Bottom-right circular badge (search or hidden eye)
        val badgeCenter = Offset(w * 0.68f, h * 0.69f)
        val badgeRadius = w * 0.145f
        drawCircle(color = surfaceColor, radius = badgeRadius, center = badgeCenter)
        drawCircle(
            color = accentColor.copy(alpha = 0.16f),
            radius = badgeRadius,
            center = badgeCenter
        )
        drawCircle(
            color = accentColor,
            radius = badgeRadius,
            center = badgeCenter,
            style = Stroke(width = w * 0.018f)
        )

        if (isHiddenState) {
            // Eye outline + slash for "all types hidden"
            val eyePath = Path().apply {
                moveTo(badgeCenter.x - badgeRadius * 0.56f, badgeCenter.y)
                quadraticTo(
                    badgeCenter.x,
                    badgeCenter.y - badgeRadius * 0.45f,
                    badgeCenter.x + badgeRadius * 0.56f,
                    badgeCenter.y
                )
                quadraticTo(
                    badgeCenter.x,
                    badgeCenter.y + badgeRadius * 0.45f,
                    badgeCenter.x - badgeRadius * 0.56f,
                    badgeCenter.y
                )
                close()
            }
            drawPath(
                path = eyePath,
                color = accentColor,
                style = Stroke(width = w * 0.018f, join = StrokeJoin.Round)
            )
            drawCircle(
                color = accentColor,
                radius = badgeRadius * 0.16f,
                center = badgeCenter
            )
            drawLine(
                color = accentColor,
                start = Offset(badgeCenter.x - badgeRadius * 0.44f, badgeCenter.y + badgeRadius * 0.44f),
                end = Offset(badgeCenter.x + badgeRadius * 0.44f, badgeCenter.y - badgeRadius * 0.44f),
                strokeWidth = w * 0.020f,
                cap = StrokeCap.Round
            )
        } else {
            // Magnifying glass for empty scan/search
            val glassCenter = Offset(badgeCenter.x - badgeRadius * 0.10f, badgeCenter.y - badgeRadius * 0.10f)
            val glassR = badgeRadius * 0.34f
            drawCircle(
                color = accentColor,
                radius = glassR,
                center = glassCenter,
                style = Stroke(width = w * 0.020f)
            )
            drawLine(
                color = accentColor,
                start = Offset(glassCenter.x + glassR * 0.72f, glassCenter.y + glassR * 0.72f),
                end = Offset(badgeCenter.x + badgeRadius * 0.45f, badgeCenter.y + badgeRadius * 0.45f),
                strokeWidth = w * 0.022f,
                cap = StrokeCap.Round
            )
        }
    }
}
