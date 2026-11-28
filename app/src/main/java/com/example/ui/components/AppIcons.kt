package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalMaterialIconsEnabled

/**
 * Lightweight, tree-shaken Material vector icons so the app never depends on
 * the multi-megabyte material-icons-extended artifact.
 */
object AppIcons {

    val PictureAsPdf: ImageVector by lazy {
        materialIcon(name = "PictureAsPdf") {
            materialPath {
                moveTo(20f, 2f)
                horizontalLineTo(8f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(11.5f, 9.5f)
                curveToRelative(0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                horizontalLineTo(9f)
                verticalLineToRelative(2f)
                horizontalLineTo(7.5f)
                verticalLineTo(7f)
                horizontalLineTo(10f)
                curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(1f)
                close()
                moveTo(16.5f, 11.5f)
                curveToRelative(0f, 0.83f, -0.67f, 1.5f, -1.5f, 1.5f)
                horizontalLineToRelative(-2.5f)
                verticalLineTo(7f)
                horizontalLineTo(15f)
                curveToRelative(0.83f, 0f, 1.5f, 0.67f, 1.5f, 1.5f)
                verticalLineToRelative(3f)
                close()
                moveTo(20.5f, 8.5f)
                horizontalLineTo(19f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(1.5f)
                verticalLineTo(11f)
                horizontalLineTo(19f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-1.5f)
                verticalLineTo(7f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(1.5f)
                close()
                moveTo(9f, 9.5f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(-1f)
                horizontalLineTo(9f)
                verticalLineToRelative(1f)
                close()
                moveTo(4f, 6f)
                horizontalLineTo(2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(14f, 11.5f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-1f)
                verticalLineToRelative(3f)
                close()
            }
        }
    }

    val Description: ImageVector by lazy {
        materialIcon(name = "Description") {
            materialPath {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(16f, 18f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(16f, 14f)
                horizontalLineTo(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
            }
        }
    }

    val TableChart: ImageVector by lazy {
        materialIcon(name = "TableChart") {
            materialPath {
                moveTo(10f, 10.02f)
                horizontalLineToRelative(5f)
                verticalLineTo(21f)
                horizontalLineToRelative(-5f)
                verticalLineTo(10.02f)
                close()
                moveTo(17f, 21f)
                horizontalLineToRelative(3f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-9f)
                horizontalLineToRelative(-5f)
                verticalLineTo(21f)
                close()
                moveTo(20f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(19f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(3f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(3f)
                verticalLineTo(10f)
                horizontalLineTo(3f)
                verticalLineTo(19f)
                close()
            }
        }
    }

    val Slideshow: ImageVector by lazy {
        materialIcon(name = "Slideshow") {
            materialPath {
                moveTo(10f, 8f)
                verticalLineToRelative(8f)
                lineToRelative(5f, -4f)
                lineToRelative(-5f, -4f)
                close()
                moveTo(19f, 3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 19f)
                horizontalLineTo(5f)
                verticalLineTo(5f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val Notes: ImageVector by lazy {
        materialIcon(name = "Notes", autoMirror = true) {
            materialPath {
                moveTo(3f, 18f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 13f)
                horizontalLineToRelative(18f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val DarkMode: ImageVector by lazy {
        materialIcon(name = "DarkMode") {
            materialPath {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
                curveToRelative(0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3f, 12f, 3f)
                close()
            }
        }
    }

    val LightMode: ImageVector by lazy {
        materialIcon(name = "LightMode") {
            materialPath {
                moveTo(12f, 7f)
                curveToRelative(-2.76f, 0f, -5f, 2.24f, -5f, 5f)
                reflectiveCurveToRelative(2.24f, 5f, 5f, 5f)
                reflectiveCurveToRelative(5f, -2.24f, 5f, -5f)
                reflectiveCurveToRelative(-2.24f, -5f, -5f, -5f)
                close()
                moveTo(2f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineTo(2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(20f, 13f)
                horizontalLineToRelative(2f)
                curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
                reflectiveCurveToRelative(-0.45f, -1f, -1f, -1f)
                horizontalLineToRelative(-2f)
                curveToRelative(-0.55f, 0f, -1f, 0.45f, -1f, 1f)
                reflectiveCurveToRelative(0.45f, 1f, 1f, 1f)
                close()
                moveTo(11f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineTo(2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(11f, 20f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
                reflectiveCurveToRelative(1f, -0.45f, 1f, -1f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -0.55f, -0.45f, -1f, -1f, -1f)
                reflectiveCurveToRelative(-1f, 0.45f, -1f, 1f)
                close()
                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()
                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                lineToRelative(-1.06f, -1.06f)
                close()
                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0f)
                lineToRelative(1.06f, -1.06f)
                close()
            }
        }
    }

    val BrightnessAuto: ImageVector by lazy {
        materialIcon(name = "BrightnessAuto") {
            materialPath {
                moveTo(10.85f, 12.65f)
                horizontalLineToRelative(2.3f)
                lineTo(12f, 9f)
                lineToRelative(-1.15f, 3.65f)
                close()
                moveTo(20f, 8.69f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4.69f)
                lineTo(12f, 0.69f)
                lineTo(8.69f, 4f)
                horizontalLineTo(4f)
                verticalLineToRelative(4.69f)
                lineTo(0.69f, 12f)
                lineTo(4f, 15.31f)
                verticalLineTo(20f)
                horizontalLineToRelative(4.69f)
                lineTo(12f, 23.31f)
                lineTo(15.31f, 20f)
                horizontalLineTo(20f)
                verticalLineToRelative(-4.69f)
                lineTo(23.31f, 12f)
                lineTo(20f, 8.69f)
                close()
                moveTo(14.3f, 16f)
                lineToRelative(-0.7f, -2f)
                horizontalLineToRelative(-3.2f)
                lineToRelative(-0.7f, 2f)
                horizontalLineTo(7.8f)
                lineTo(11f, 7f)
                horizontalLineToRelative(2f)
                lineToRelative(3.2f, 9f)
                horizontalLineToRelative(-1.9f)
                close()
            }
        }
    }

    val Sort: ImageVector by lazy {
        materialIcon(name = "Sort", autoMirror = true) {
            materialPath {
                moveTo(3f, 18f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 13f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val SelectAll: ImageVector by lazy {
        materialIcon(name = "SelectAll") {
            materialPath {
                moveTo(3f, 5f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                close()
                moveTo(3f, 13f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(13f, 3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                close()
                moveTo(19f, 3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(5f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                close()
                moveTo(3f, 17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(9f, 3f)
                horizontalLineTo(7f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                close()
                moveTo(11f, 21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 13f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 21f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(19f, 17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(15f, 21f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(15f, 5f)
                horizontalLineToRelative(2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
                moveTo(7f, 17f)
                horizontalLineToRelative(10f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                verticalLineToRelative(10f)
                close()
                moveTo(9f, 9f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(6f)
                horizontalLineTo(9f)
                verticalLineTo(9f)
                close()
            }
        }
    }

    val ViewCarousel: ImageVector by lazy {
        materialIcon(name = "ViewCarousel") {
            materialPath {
                moveTo(2f, 7f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(10f)
                horizontalLineTo(2f)
                verticalLineTo(7f)
                close()
                moveTo(7f, 19f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(7f)
                verticalLineToRelative(14f)
                close()
                moveTo(18f, 7f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(10f)
                horizontalLineToRelative(-4f)
                verticalLineTo(7f)
                close()
            }
        }
    }

    val ViewStream: ImageVector by lazy {
        materialIcon(name = "ViewStream") {
            materialPath {
                moveTo(3f, 17f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                close()
                moveTo(3f, 7f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                close()
            }
        }
    }

    val Rename: ImageVector by lazy {
        materialIcon(name = "Rename") {
            materialPath {
                moveTo(18.41f, 5.8f)
                lineTo(17.2f, 4.59f)
                curveToRelative(-0.78f, -0.78f, -2.05f, -0.78f, -2.83f, 0f)
                lineToRelative(-2.68f, 2.68f)
                lineTo(3f, 15.96f)
                verticalLineTo(20f)
                horizontalLineToRelative(4.04f)
                lineToRelative(8.74f, -8.74f)
                lineToRelative(2.63f, -2.63f)
                curveToRelative(0.79f, -0.78f, 0.79f, -2.05f, 0f, -2.83f)
                close()
                moveTo(6.21f, 18f)
                horizontalLineTo(5f)
                verticalLineToRelative(-1.21f)
                lineToRelative(8.66f, -8.66f)
                lineToRelative(1.21f, 1.21f)
                lineTo(6.21f, 18f)
                close()
                moveTo(11f, 20f)
                lineToRelative(4f, -4f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(4f)
                horizontalLineTo(11f)
                close()
            }
        }
    }

    val Print: ImageVector by lazy {
        materialIcon(name = "Print") {
            materialPath {
                moveTo(19f, 8f)
                horizontalLineTo(5f)
                curveToRelative(-1.66f, 0f, -3f, 1.34f, -3f, 3f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-4f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-6f)
                curveToRelative(0f, -1.66f, -1.34f, -3f, -3f, -3f)
                close()
                moveTo(16f, 19f)
                horizontalLineTo(8f)
                verticalLineToRelative(-5f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(5f)
                close()
                moveTo(19f, 12f)
                curveToRelative(-0.55f, 0f, -1f, -0.45f, -1f, -1f)
                reflectiveCurveToRelative(0.45f, -1f, 1f, -1f)
                reflectiveCurveToRelative(1f, 0.45f, 1f, 1f)
                reflectiveCurveToRelative(-0.45f, 1f, -1f, 1f)
                close()
                moveTo(18f, 3f)
                horizontalLineTo(6f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(12f)
                verticalLineTo(3f)
                close()
            }
        }
    }

    val DeleteOutline: ImageVector by lazy {
        materialIcon(name = "DeleteOutline") {
            materialPath {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(8f, 9f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(10f)
                horizontalLineTo(8f)
                verticalLineTo(9f)
                close()
                moveTo(15.5f, 4f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
            }
        }
    }

    val Folder: ImageVector by lazy {
        materialIcon(name = "Folder") {
            materialPath {
                moveTo(10f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-8f)
                lineToRelative(-2f, -2f)
                close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        materialIcon(name = "FolderOpen") {
            materialPath {
                moveTo(20f, 6f)
                horizontalLineToRelative(-8f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(10f)
                close()
            }
        }
    }

    val CreateNewFolder: ImageVector by lazy {
        materialIcon(name = "CreateNewFolder") {
            materialPath {
                moveTo(20f, 6f)
                horizontalLineToRelative(-8f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(4f)
                curveToRelative(-1.11f, 0f, -1.99f, 0.89f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.11f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.11f, 0f, 2f, -0.89f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.11f, -0.89f, -2f, -2f, -2f)
                close()
                moveTo(19f, 14f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(-3f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(3f)
                verticalLineTo(9f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(3f)
                horizontalLineToRelative(3f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val FileOpen: ImageVector by lazy {
        materialIcon(name = "FileOpen") {
            materialPath {
                moveTo(15f, 22f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                horizontalLineToRelative(8f)
                lineToRelative(6f, 6f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                verticalLineTo(9f)
                horizontalLineToRelative(-5f)
                verticalLineTo(4f)
                horizontalLineTo(6f)
                verticalLineToRelative(16f)
                horizontalLineToRelative(9f)
                verticalLineToRelative(2f)
                close()
                moveTo(22.66f, 17f)
                horizontalLineToRelative(-2.24f)
                lineToRelative(2.95f, 2.95f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(19f, 18.41f)
                verticalLineToRelative(2.24f)
                horizontalLineToRelative(-2f)
                verticalLineTo(15f)
                horizontalLineToRelative(5.66f)
                verticalLineToRelative(2f)
                close()
            }
        }
    }

    val Storage: ImageVector by lazy {
        materialIcon(name = "Storage") {
            materialPath {
                moveTo(2f, 20f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 17f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
                moveTo(2f, 4f)
                verticalLineToRelative(4f)
                horizontalLineToRelative(20f)
                verticalLineTo(4f)
                horizontalLineTo(2f)
                close()
                moveTo(6f, 7f)
                horizontalLineTo(4f)
                verticalLineTo(5f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(2f, 14f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(-4f)
                horizontalLineTo(2f)
                verticalLineToRelative(4f)
                close()
                moveTo(4f, 11f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
            }
        }
    }

    val CalendarToday: ImageVector by lazy {
        materialIcon(name = "CalendarToday") {
            materialPath {
                moveTo(20f, 3f)
                horizontalLineToRelative(-1f)
                verticalLineTo(1f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(7f)
                verticalLineTo(1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(16f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(20f, 21f)
                horizontalLineTo(4f)
                verticalLineTo(10f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(11f)
                close()
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        materialIcon(name = "VisibilityOff") {
            materialPath {
                moveTo(12f, 7f)
                curveToRelative(2.76f, 0f, 5f, 2.24f, 5f, 5f)
                curveToRelative(0f, 0.65f, -0.13f, 1.26f, -0.36f, 1.83f)
                lineToRelative(2.92f, 2.92f)
                curveToRelative(1.51f, -1.26f, 2.7f, -2.89f, 3.43f, -4.75f)
                curveToRelative(-1.73f, -4.39f, -6f, -7.5f, -11f, -7.5f)
                curveToRelative(-1.4f, 0f, -2.74f, 0.25f, -3.98f, 0.7f)
                lineToRelative(2.16f, 2.16f)
                curveTo(10.74f, 7.13f, 11.35f, 7f, 12f, 7f)
                close()
                moveTo(2f, 4.27f)
                lineToRelative(2.28f, 2.28f)
                lineToRelative(0.46f, 0.46f)
                curveTo(3.08f, 8.3f, 1.78f, 10.02f, 1f, 12f)
                curveToRelative(1.73f, 4.39f, 6f, 7.5f, 11f, 7.5f)
                curveToRelative(1.55f, 0f, 3.03f, -0.3f, 4.38f, -0.84f)
                lineToRelative(0.42f, 0.42f)
                lineTo(19.73f, 22f)
                lineTo(21f, 20.73f)
                lineTo(3.27f, 3f)
                lineTo(2f, 4.27f)
                close()
                moveTo(7.53f, 9.8f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.05f, 0.21f, -0.08f, 0.43f, -0.08f, 0.65f)
                curveToRelative(0f, 1.66f, 1.34f, 3f, 3f, 3f)
                curveToRelative(0.22f, 0f, 0.44f, -0.03f, 0.65f, -0.08f)
                lineToRelative(1.55f, 1.55f)
                curveToRelative(-0.67f, 0.33f, -1.41f, 0.53f, -2.2f, 0.53f)
                curveToRelative(-2.76f, 0f, -5f, -2.24f, -5f, -5f)
                curveToRelative(0f, -0.79f, 0.2f, -1.53f, 0.53f, -2.2f)
                close()
            }
        }
    }

    val InsertDriveFile: ImageVector by lazy {
        materialIcon(name = "InsertDriveFile", autoMirror = true) {
            materialPath {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(6f, 20f)
                verticalLineTo(4f)
                horizontalLineToRelative(7f)
                verticalLineToRelative(5f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(11f)
                horizontalLineTo(6f)
                close()
            }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        materialIcon(name = "ErrorOutline") {
            materialPath {
                moveTo(11f, 15f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(11f, 7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(-2f)
                close()
                moveTo(11.99f, 2f)
                curveTo(6.47f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.47f, 10f, 9.99f, 10f)
                curveTo(17.52f, 22f, 22f, 17.52f, 22f, 12f)
                reflectiveCurveTo(17.52f, 2f, 11.99f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
            }
        }
    }

    val ExpandLess: ImageVector by lazy {
        materialIcon(name = "ExpandLess") {
            materialPath {
                moveTo(12f, 8f)
                lineToRelative(-6f, 6f)
                lineToRelative(1.41f, 1.41f)
                lineTo(12f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18f, 14f)
                close()
            }
        }
    }

    val ExpandMore: ImageVector by lazy {
        materialIcon(name = "ExpandMore") {
            materialPath {
                moveTo(16.59f, 8.59f)
                lineTo(12f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6f, 10f)
                lineToRelative(6f, 6f)
                lineToRelative(6f, -6f)
                close()
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        materialIcon(name = "CheckCircle") {
            materialPath {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(10f, 17f)
                lineToRelative(-5f, -5f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10f, 14.17f)
                lineToRelative(7.59f, -7.59f)
                lineTo(19f, 8f)
                lineToRelative(-9f, 9f)
                close()
            }
        }
    }

    val RadioButtonUnchecked: ImageVector by lazy {
        materialIcon(name = "RadioButtonUnchecked") {
            materialPath {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(12f, 20f)
                curveToRelative(-4.42f, 0f, -8f, -3.58f, -8f, -8f)
                reflectiveCurveToRelative(3.58f, -8f, 8f, -8f)
                reflectiveCurveToRelative(8f, 3.58f, 8f, 8f)
                reflectiveCurveToRelative(-3.58f, 8f, -8f, 8f)
                close()
            }
        }
    }

    val ScreenRotation: ImageVector by lazy {
        materialIcon(name = "ScreenRotation") {
            materialPath {
                moveTo(16.48f, 2.52f)
                curveToRelative(3.27f, 1.55f, 5.61f, 4.72f, 5.97f, 8.48f)
                horizontalLineToRelative(1.5f)
                curveTo(23.44f, 4.84f, 18.29f, 0f, 12f, 0f)
                lineToRelative(-0.66f, 0.03f)
                lineToRelative(3.81f, 3.81f)
                lineToRelative(1.33f, -1.32f)
                close()
                moveTo(10.23f, 1.75f)
                curveToRelative(-0.59f, -0.59f, -1.54f, -0.59f, -2.12f, 0f)
                lineTo(1.75f, 8.11f)
                curveToRelative(-0.59f, 0.59f, -0.59f, 1.54f, 0f, 2.12f)
                lineToRelative(12.02f, 12.02f)
                curveToRelative(0.59f, 0.59f, 1.54f, 0.59f, 2.12f, 0f)
                lineToRelative(6.36f, -6.36f)
                curveToRelative(0.59f, -0.59f, 0.59f, -1.54f, 0f, -2.12f)
                lineTo(10.23f, 1.75f)
                close()
                moveTo(14.83f, 21.19f)
                lineTo(2.81f, 9.17f)
                lineToRelative(6.36f, -6.36f)
                lineToRelative(12.02f, 12.02f)
                lineToRelative(-6.36f, 6.36f)
                close()
                moveTo(7.52f, 21.48f)
                curveTo(4.25f, 19.94f, 1.91f, 16.76f, 1.55f, 13f)
                horizontalLineTo(0.05f)
                curveTo(0.56f, 19.16f, 5.71f, 24f, 12f, 24f)
                lineToRelative(0.66f, -0.03f)
                lineToRelative(-3.81f, -3.81f)
                lineToRelative(-1.33f, 1.32f)
                close()
            }
        }
    }

    val Palette: ImageVector by lazy {
        materialIcon(name = "Palette") {
            materialPath {
                moveTo(12f, 3f)
                curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
                reflectiveCurveToRelative(4.03f, 9f, 9f, 9f)
                curveToRelative(0.83f, 0f, 1.5f, -0.67f, 1.5f, -1.5f)
                curveToRelative(0f, -0.39f, -0.15f, -0.74f, -0.39f, -1.01f)
                curveToRelative(-0.23f, -0.26f, -0.38f, -0.61f, -0.38f, -0.99f)
                curveToRelative(0f, -0.83f, 0.67f, -1.5f, 1.5f, -1.5f)
                horizontalLineTo(16f)
                curveToRelative(2.76f, 0f, 5f, -2.24f, 5f, -5f)
                curveToRelative(0f, -4.42f, -4.03f, -8f, -9f, -8f)
                close()
                moveTo(6.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                close()
                moveTo(9.5f, 8f)
                curveTo(8.67f, 8f, 8f, 7.33f, 8f, 6.5f)
                reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                close()
                moveTo(14.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                close()
                moveTo(17.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                reflectiveCurveToRelative(1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveToRelative(-0.67f, 1.5f, -1.5f, 1.5f)
                close()
            }
        }
    }

    val CropPortrait: ImageVector by lazy {
        materialIcon(name = "CropPortrait") {
            materialPath {
                moveTo(17f, 3f)
                horizontalLineTo(7f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(14f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(10f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(5f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(17f, 19f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(14f)
                close()
            }
        }
    }

    val CropLandscape: ImageVector by lazy {
        materialIcon(name = "CropLandscape") {
            materialPath {
                moveTo(19f, 5f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(10f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(19f, 17f)
                horizontalLineTo(5f)
                verticalLineTo(7f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(10f)
                close()
            }
        }
    }

    val Check: ImageVector by lazy {
        materialIcon(name = "Check") {
            materialPath {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineToRelative(-1.42f, 1.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
    }

    val ZoomIn: ImageVector by lazy {
        materialIcon(name = "ZoomIn") {
            materialPath {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
                curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(5f, 4.99f)
                lineTo(20.49f, 19f)
                lineToRelative(-4.99f, -5f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
                moveTo(12f, 10f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                horizontalLineTo(9f)
                verticalLineToRelative(-2f)
                horizontalLineTo(7f)
                verticalLineTo(9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(1f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(1f)
                close()
            }
        }
    }

    val ZoomOut: ImageVector by lazy {
        materialIcon(name = "ZoomOut") {
            materialPath {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
                curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(5f, 4.99f)
                lineTo(20.49f, 19f)
                lineToRelative(-4.99f, -5f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
                moveTo(7f, 9f)
                horizontalLineToRelative(5f)
                verticalLineToRelative(1f)
                horizontalLineTo(7f)
                close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        materialIcon(name = "ChevronRight", autoMirror = true) {
            materialPath {
                moveTo(10f, 6f)
                lineTo(8.59f, 7.41f)
                lineTo(13.17f, 12f)
                lineToRelative(-4.58f, 4.59f)
                lineTo(10f, 18f)
                lineToRelative(6f, -6f)
                close()
            }
        }
    }

    @Composable fun search(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Search else ClassicIcons.Search

    @Composable fun settings(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Settings else ClassicIcons.Settings

    @Composable fun close(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Close else ClassicIcons.Close

    @Composable fun arrowBack(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.ArrowBack else ClassicIcons.ArrowBack

    @Composable fun moreVert(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.MoreVert else ClassicIcons.MoreVert

    @Composable fun refresh(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Refresh else ClassicIcons.Refresh

    @Composable fun share(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Share else ClassicIcons.Share

    @Composable fun info(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Info else ClassicIcons.Info

    @Composable fun check(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Check else ClassicIcons.Check

    @Composable fun sort(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Sort else ClassicIcons.Sort

    @Composable fun darkMode(): ImageVector =
        if (LocalMaterialIconsEnabled.current) DarkMode else ClassicIcons.DarkMode

    @Composable fun lightMode(): ImageVector =
        if (LocalMaterialIconsEnabled.current) LightMode else ClassicIcons.LightMode

    @Composable fun brightnessAuto(): ImageVector =
        if (LocalMaterialIconsEnabled.current) BrightnessAuto else ClassicIcons.BrightnessAuto

    @Composable fun selectAll(): ImageVector =
        if (LocalMaterialIconsEnabled.current) SelectAll else ClassicIcons.SelectAll

    @Composable fun viewCarousel(): ImageVector =
        if (LocalMaterialIconsEnabled.current) ViewCarousel else ClassicIcons.ViewCarousel

    @Composable fun viewStream(): ImageVector =
        if (LocalMaterialIconsEnabled.current) ViewStream else ClassicIcons.ViewStream

    @Composable fun screenRotation(): ImageVector =
        if (LocalMaterialIconsEnabled.current) ScreenRotation else ClassicIcons.ScreenRotation

    @Composable fun rename(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Rename else ClassicIcons.Rename

    @Composable fun print(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Print else ClassicIcons.Print

    @Composable fun delete(): ImageVector =
        if (LocalMaterialIconsEnabled.current) DeleteOutline else ClassicIcons.Delete

    @Composable fun folder(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Folder else ClassicIcons.Folder

    @Composable fun folderOpen(): ImageVector =
        if (LocalMaterialIconsEnabled.current) FolderOpen else ClassicIcons.FolderOpen

    @Composable fun createNewFolder(): ImageVector =
        if (LocalMaterialIconsEnabled.current) CreateNewFolder else ClassicIcons.CreateNewFolder

    @Composable fun fileOpen(): ImageVector =
        if (LocalMaterialIconsEnabled.current) FileOpen else ClassicIcons.FileOpen

    @Composable fun storage(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Storage else ClassicIcons.Storage

    @Composable fun palette(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Palette else ClassicIcons.Palette

    @Composable fun calendarToday(): ImageVector =
        if (LocalMaterialIconsEnabled.current) CalendarToday else ClassicIcons.CalendarToday

    @Composable fun visibilityOff(): ImageVector =
        if (LocalMaterialIconsEnabled.current) VisibilityOff else ClassicIcons.VisibilityOff

    @Composable fun insertDriveFile(): ImageVector =
        if (LocalMaterialIconsEnabled.current) InsertDriveFile else ClassicIcons.InsertDriveFile

    @Composable fun errorOutline(): ImageVector =
        if (LocalMaterialIconsEnabled.current) ErrorOutline else ClassicIcons.ErrorOutline

    @Composable fun checkCircle(): ImageVector =
        if (LocalMaterialIconsEnabled.current) CheckCircle else ClassicIcons.CheckCircle

    @Composable fun radioButtonUnchecked(): ImageVector =
        if (LocalMaterialIconsEnabled.current) RadioButtonUnchecked else ClassicIcons.RadioButtonUnchecked

    @Composable fun cropPortrait(): ImageVector =
        if (LocalMaterialIconsEnabled.current) CropPortrait else ClassicIcons.CropPortrait

    @Composable fun cropLandscape(): ImageVector =
        if (LocalMaterialIconsEnabled.current) CropLandscape else ClassicIcons.CropLandscape

    @Composable fun chevronRight(): ImageVector =
        if (LocalMaterialIconsEnabled.current) ChevronRight else ClassicIcons.ChevronRight

    @Composable fun pdf(): ImageVector =
        if (LocalMaterialIconsEnabled.current) PictureAsPdf else ClassicIcons.Pdf

    @Composable fun doc(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Description else ClassicIcons.Doc

    @Composable fun excel(): ImageVector =
        if (LocalMaterialIconsEnabled.current) TableChart else ClassicIcons.Excel

    @Composable fun ppt(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Slideshow else ClassicIcons.Ppt

    @Composable fun textDoc(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Notes else ClassicIcons.Text
}

private inline fun classicIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> Unit
): ImageVector = ImageVector.Builder(
    name = "Classic.$name",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
    autoMirror = autoMirror
).apply(block).build()

private inline fun ImageVector.Builder.strokePath(
    strokeWidth: Float = 1.75f,
    block: PathBuilder.() -> Unit
) = path(
    fill = null,
    stroke = SolidColor(Color.Black),
    strokeLineWidth = strokeWidth,
    strokeLineCap = StrokeCap.Butt,
    strokeLineJoin = StrokeJoin.Miter,
    pathBuilder = block
)

private inline fun ImageVector.Builder.fillBlock(
    block: PathBuilder.() -> Unit
) = path(
    fill = SolidColor(Color.Black),
    pathBuilder = block
)

private fun PathBuilder.circleSubpath(cx: Float, cy: Float, r: Float) {
    moveTo(cx + r, cy)
    arcTo(r, r, 0f, false, true, cx - r, cy)
    arcTo(r, r, 0f, false, true, cx + r, cy)
    close()
}

private object ClassicIcons {
    val Search: ImageVector by lazy {
        classicIcon("Search") {
            strokePath {
                circleSubpath(10.5f, 10.5f, 6.2f)
                moveTo(15.2f, 15.2f)
                lineTo(20.5f, 20.5f)
            }
        }
    }

    val Settings: ImageVector by lazy {
        classicIcon("Settings") {
            strokePath {
                circleSubpath(12f, 12f, 3.2f)
                moveTo(10f, 3.5f); lineTo(14f, 3.5f); lineTo(14.6f, 5.6f)
                lineTo(16.6f, 6.4f); lineTo(18.5f, 5.3f); lineTo(21f, 7.8f)
                lineTo(19.6f, 9.8f); lineTo(20.4f, 11.8f); lineTo(21.5f, 12.4f)
                lineTo(21.5f, 15.6f); lineTo(19.4f, 16.2f); lineTo(18.6f, 18.2f)
                lineTo(19.7f, 20f); lineTo(17.2f, 21.5f); lineTo(15.2f, 20.1f)
                lineTo(13.2f, 20.7f); lineTo(12.6f, 21.5f); lineTo(9.4f, 21.5f)
                lineTo(8.8f, 19.6f); lineTo(6.8f, 18.8f); lineTo(5f, 19.9f)
                lineTo(3.5f, 17.4f); lineTo(4.9f, 15.4f); lineTo(4.1f, 13.4f)
                lineTo(3.5f, 12.8f); lineTo(3.5f, 9.6f); lineTo(5.4f, 9f)
                lineTo(6.2f, 7f); lineTo(5.1f, 5.1f); lineTo(7.6f, 3.6f)
                lineTo(9.4f, 4.9f)
                close()
            }
        }
    }

    val Close: ImageVector by lazy {
        classicIcon("Close") {
            strokePath {
                moveTo(5.5f, 5.5f); lineTo(18.5f, 18.5f)
                moveTo(18.5f, 5.5f); lineTo(5.5f, 18.5f)
            }
        }
    }

    val ArrowBack: ImageVector by lazy {
        classicIcon("ArrowBack", autoMirror = true) {
            strokePath {
                moveTo(20f, 12f); lineTo(5f, 12f)
                moveTo(11f, 6f); lineTo(5f, 12f); lineTo(11f, 18f)
            }
        }
    }

    val MoreVert: ImageVector by lazy {
        classicIcon("MoreVert") {
            fillBlock {
                moveTo(10.75f, 4.75f); lineTo(13.25f, 4.75f); lineTo(13.25f, 7.25f); lineTo(10.75f, 7.25f); close()
                moveTo(10.75f, 10.75f); lineTo(13.25f, 10.75f); lineTo(13.25f, 13.25f); lineTo(10.75f, 13.25f); close()
                moveTo(10.75f, 16.75f); lineTo(13.25f, 16.75f); lineTo(13.25f, 19.25f); lineTo(10.75f, 19.25f); close()
            }
        }
    }

    val Refresh: ImageVector by lazy {
        classicIcon("Refresh") {
            strokePath {
                moveTo(19.5f, 12f)
                arcTo(7.5f, 7.5f, 0f, true, true, 17.5f, 6.9f)
                moveTo(14.5f, 7.5f); lineTo(18.5f, 7.5f); lineTo(18.5f, 3.5f)
            }
        }
    }

    val Share: ImageVector by lazy {
        classicIcon("Share") {
            strokePath {
                circleSubpath(17.5f, 5.5f, 2.5f)
                circleSubpath(6.5f, 12f, 2.5f)
                circleSubpath(17.5f, 18.5f, 2.5f)
                moveTo(8.7f, 10.7f); lineTo(15.3f, 6.8f)
                moveTo(8.7f, 13.3f); lineTo(15.3f, 17.2f)
            }
        }
    }

    val Info: ImageVector by lazy {
        classicIcon("Info") {
            strokePath {
                circleSubpath(12f, 12f, 8.8f)
                moveTo(12f, 11f); lineTo(12f, 16.5f)
                moveTo(12f, 7.2f); lineTo(12f, 9f)
            }
        }
    }

    val Check: ImageVector by lazy {
        classicIcon("Check") {
            strokePath {
                moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f)
            }
        }
    }

    val Sort: ImageVector by lazy {
        classicIcon("Sort", autoMirror = true) {
            strokePath(strokeWidth = 2f) {
                moveTo(3.5f, 6.5f); lineTo(20.5f, 6.5f)
                moveTo(3.5f, 12f); lineTo(15f, 12f)
                moveTo(3.5f, 17.5f); lineTo(9.5f, 17.5f)
            }
        }
    }

    val DarkMode: ImageVector by lazy {
        classicIcon("DarkMode") {
            strokePath {
                moveTo(13.5f, 3.8f)
                arcTo(8.4f, 8.4f, 0f, true, false, 20.2f, 14.5f)
                arcTo(6.2f, 6.2f, 0f, false, true, 13.5f, 3.8f)
                close()
            }
        }
    }

    val LightMode: ImageVector by lazy {
        classicIcon("LightMode") {
            strokePath {
                circleSubpath(12f, 12f, 4.2f)
                moveTo(12f, 2.5f); lineTo(12f, 5.2f)
                moveTo(12f, 18.8f); lineTo(12f, 21.5f)
                moveTo(2.5f, 12f); lineTo(5.2f, 12f)
                moveTo(18.8f, 12f); lineTo(21.5f, 12f)
                moveTo(5.3f, 5.3f); lineTo(7.2f, 7.2f)
                moveTo(16.8f, 16.8f); lineTo(18.7f, 18.7f)
                moveTo(18.7f, 5.3f); lineTo(16.8f, 7.2f)
                moveTo(7.2f, 16.8f); lineTo(5.3f, 18.7f)
            }
        }
    }

    val BrightnessAuto: ImageVector by lazy {
        classicIcon("BrightnessAuto") {
            strokePath {
                moveTo(12f, 3f); lineTo(21f, 12f); lineTo(12f, 21f); lineTo(3f, 12f); close()
                moveTo(9.4f, 15.8f); lineTo(12f, 8.2f); lineTo(14.6f, 15.8f)
                moveTo(10.3f, 13.5f); lineTo(13.7f, 13.5f)
            }
        }
    }

    val SelectAll: ImageVector by lazy {
        classicIcon("SelectAll") {
            strokePath {
                moveTo(4f, 7.5f); lineTo(4f, 4f); lineTo(7.5f, 4f)
                moveTo(16.5f, 4f); lineTo(20f, 4f); lineTo(20f, 7.5f)
                moveTo(20f, 16.5f); lineTo(20f, 20f); lineTo(16.5f, 20f)
                moveTo(7.5f, 20f); lineTo(4f, 20f); lineTo(4f, 16.5f)
                moveTo(8f, 8f); lineTo(16f, 8f); lineTo(16f, 16f); lineTo(8f, 16f); close()
            }
        }
    }

    val ViewCarousel: ImageVector by lazy {
        classicIcon("ViewCarousel") {
            strokePath {
                moveTo(7.5f, 5f); lineTo(16.5f, 5f); lineTo(16.5f, 19f); lineTo(7.5f, 19f); close()
                moveTo(3.5f, 7f); lineTo(3.5f, 17f)
                moveTo(20.5f, 7f); lineTo(20.5f, 17f)
            }
        }
    }

    val ViewStream: ImageVector by lazy {
        classicIcon("ViewStream") {
            strokePath {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 10.5f); lineTo(4f, 10.5f); close()
                moveTo(4f, 13.5f); lineTo(20f, 13.5f); lineTo(20f, 19f); lineTo(4f, 19f); close()
            }
        }
    }

    val ScreenRotation: ImageVector by lazy {
        classicIcon("ScreenRotation") {
            strokePath {
                moveTo(9.5f, 3.2f); lineTo(20.8f, 14.5f); lineTo(14.5f, 20.8f); lineTo(3.2f, 9.5f); close()
                moveTo(15.5f, 3.2f); lineTo(19.5f, 3.2f); lineTo(19.5f, 7.2f)
                moveTo(8.5f, 20.8f); lineTo(4.5f, 20.8f); lineTo(4.5f, 16.8f)
            }
        }
    }

    val Rename: ImageVector by lazy {
        classicIcon("Rename") {
            strokePath {
                moveTo(4.5f, 16f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8f, 19.5f); lineTo(4.5f, 19.5f); close()
                moveTo(13f, 7.5f); lineTo(16.5f, 11f)
                moveTo(12.5f, 20f); lineTo(20.5f, 20f)
            }
        }
    }

    val Print: ImageVector by lazy {
        classicIcon("Print") {
            strokePath {
                moveTo(6.5f, 8.5f); lineTo(6.5f, 4f); lineTo(17.5f, 4f); lineTo(17.5f, 8.5f)
                moveTo(3.5f, 8.5f); lineTo(20.5f, 8.5f); lineTo(20.5f, 16f); lineTo(17.5f, 16f)
                moveTo(6.5f, 16f); lineTo(3.5f, 16f); lineTo(3.5f, 8.5f)
                moveTo(6.5f, 13f); lineTo(17.5f, 13f); lineTo(17.5f, 20f); lineTo(6.5f, 20f); close()
            }
        }
    }

    val Delete: ImageVector by lazy {
        classicIcon("Delete") {
            strokePath {
                moveTo(4.5f, 6.5f); lineTo(19.5f, 6.5f)
                moveTo(9f, 6.5f); lineTo(9f, 4f); lineTo(15f, 4f); lineTo(15f, 6.5f)
                moveTo(6.5f, 6.5f); lineTo(6.5f, 20f); lineTo(17.5f, 20f); lineTo(17.5f, 6.5f)
                moveTo(10f, 10f); lineTo(10f, 17f)
                moveTo(14f, 10f); lineTo(14f, 17f)
            }
        }
    }

    val Folder: ImageVector by lazy {
        classicIcon("Folder") {
            strokePath {
                moveTo(3.5f, 5f); lineTo(9.5f, 5f); lineTo(11.5f, 7.2f); lineTo(20.5f, 7.2f)
                lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
            }
        }
    }

    val FolderOpen: ImageVector by lazy {
        classicIcon("FolderOpen") {
            strokePath {
                moveTo(3.5f, 18.5f); lineTo(3.5f, 5f); lineTo(9.5f, 5f); lineTo(11.5f, 7.2f); lineTo(19f, 7.2f); lineTo(19f, 10.5f)
                moveTo(3.5f, 18.5f); lineTo(6f, 10.5f); lineTo(21f, 10.5f); lineTo(18.5f, 18.5f); close()
            }
        }
    }

    val CreateNewFolder: ImageVector by lazy {
        classicIcon("CreateNewFolder") {
            strokePath {
                moveTo(3.5f, 5f); lineTo(9.5f, 5f); lineTo(11.5f, 7.2f); lineTo(20.5f, 7.2f)
                lineTo(20.5f, 19f); lineTo(3.5f, 19f); close()
                moveTo(12f, 10.5f); lineTo(12f, 16f)
                moveTo(9.2f, 13.25f); lineTo(14.8f, 13.25f)
            }
        }
    }

    val FileOpen: ImageVector by lazy {
        classicIcon("FileOpen") {
            strokePath {
                moveTo(5.5f, 20.5f); lineTo(5.5f, 3.5f); lineTo(13.5f, 3.5f); lineTo(18.5f, 8.5f); lineTo(18.5f, 13.5f)
                moveTo(13.5f, 3.5f); lineTo(13.5f, 8.5f); lineTo(18.5f, 8.5f)
                moveTo(5.5f, 20.5f); lineTo(13.5f, 20.5f)
                moveTo(16f, 16f); lineTo(21f, 21f)
                moveTo(16.5f, 20.5f); lineTo(16f, 16f); lineTo(20.5f, 16.5f)
            }
        }
    }

    val Storage: ImageVector by lazy {
        classicIcon("Storage") {
            strokePath {
                moveTo(3.5f, 4f); lineTo(20.5f, 4f); lineTo(20.5f, 8f); lineTo(3.5f, 8f); close()
                moveTo(3.5f, 10f); lineTo(20.5f, 10f); lineTo(20.5f, 14f); lineTo(3.5f, 14f); close()
                moveTo(3.5f, 16f); lineTo(20.5f, 16f); lineTo(20.5f, 20f); lineTo(3.5f, 20f); close()
                moveTo(6f, 6f); lineTo(8f, 6f)
                moveTo(6f, 12f); lineTo(8f, 12f)
                moveTo(6f, 18f); lineTo(8f, 18f)
            }
        }
    }

    val Palette: ImageVector by lazy {
        classicIcon("Palette") {
            strokePath {
                circleSubpath(12f, 12f, 8.6f)
                circleSubpath(8.5f, 10f, 1f)
                circleSubpath(12f, 7.8f, 1f)
                circleSubpath(15.5f, 10f, 1f)
                circleSubpath(14.5f, 14.5f, 1.4f)
            }
        }
    }

    val CalendarToday: ImageVector by lazy {
        classicIcon("CalendarToday") {
            strokePath {
                moveTo(4f, 5f); lineTo(20f, 5f); lineTo(20f, 20f); lineTo(4f, 20f); close()
                moveTo(4f, 9.5f); lineTo(20f, 9.5f)
                moveTo(8f, 2.8f); lineTo(8f, 6.2f)
                moveTo(16f, 2.8f); lineTo(16f, 6.2f)
            }
        }
    }

    val VisibilityOff: ImageVector by lazy {
        classicIcon("VisibilityOff") {
            strokePath {
                moveTo(3f, 12f); lineTo(8.5f, 6.8f); lineTo(15.5f, 6.8f); lineTo(21f, 12f)
                lineTo(15.5f, 17.2f); lineTo(8.5f, 17.2f); close()
                circleSubpath(12f, 12f, 2.4f)
                moveTo(3.5f, 3.5f); lineTo(20.5f, 20.5f)
            }
        }
    }

    val InsertDriveFile: ImageVector by lazy {
        classicIcon("InsertDriveFile", autoMirror = true) {
            strokePath {
                moveTo(5.5f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
                moveTo(14f, 3.5f); lineTo(14f, 8f); lineTo(18.5f, 8f)
            }
        }
    }

    val ErrorOutline: ImageVector by lazy {
        classicIcon("ErrorOutline") {
            strokePath {
                circleSubpath(12f, 12f, 8.8f)
                moveTo(12f, 7.5f); lineTo(12f, 13.5f)
                moveTo(12f, 15.8f); lineTo(12f, 17.5f)
            }
        }
    }

    val CheckCircle: ImageVector by lazy {
        classicIcon("CheckCircle") {
            strokePath {
                circleSubpath(12f, 12f, 8.8f)
                moveTo(7.8f, 12.2f); lineTo(10.8f, 15.2f); lineTo(16.2f, 9.2f)
            }
        }
    }

    val RadioButtonUnchecked: ImageVector by lazy {
        classicIcon("RadioButtonUnchecked") {
            strokePath {
                circleSubpath(12f, 12f, 8.8f)
            }
        }
    }

    val CropPortrait: ImageVector by lazy {
        classicIcon("CropPortrait") {
            strokePath {
                moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f); lineTo(17.5f, 20.5f); lineTo(6.5f, 20.5f); close()
            }
        }
    }

    val CropLandscape: ImageVector by lazy {
        classicIcon("CropLandscape") {
            strokePath {
                moveTo(3.5f, 6.5f); lineTo(20.5f, 6.5f); lineTo(20.5f, 17.5f); lineTo(3.5f, 17.5f); close()
            }
        }
    }

    val ChevronRight: ImageVector by lazy {
        classicIcon("ChevronRight", autoMirror = true) {
            strokePath {
                moveTo(9f, 6f); lineTo(15f, 12f); lineTo(9f, 18f)
            }
        }
    }

    val Pdf: ImageVector by lazy {
        classicIcon("Pdf") {
            strokePath {
                moveTo(5.5f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
                moveTo(14f, 3.5f); lineTo(14f, 8f); lineTo(18.5f, 8f)
                moveTo(8.5f, 12.5f); lineTo(15.5f, 12.5f); lineTo(15.5f, 17f); lineTo(8.5f, 17f); close()
            }
        }
    }

    val Doc: ImageVector by lazy {
        classicIcon("Doc") {
            strokePath {
                moveTo(5.5f, 3.5f); lineTo(14f, 3.5f); lineTo(18.5f, 8f); lineTo(18.5f, 20.5f); lineTo(5.5f, 20.5f); close()
                moveTo(14f, 3.5f); lineTo(14f, 8f); lineTo(18.5f, 8f)
                moveTo(8.5f, 12f); lineTo(15.5f, 12f)
                moveTo(8.5f, 15f); lineTo(15.5f, 15f)
                moveTo(8.5f, 18f); lineTo(13f, 18f)
            }
        }
    }

    val Excel: ImageVector by lazy {
        classicIcon("Excel") {
            strokePath {
                moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
                moveTo(3.5f, 9.5f); lineTo(20.5f, 9.5f)
                moveTo(3.5f, 14.5f); lineTo(20.5f, 14.5f)
                moveTo(9.5f, 9.5f); lineTo(9.5f, 19.5f)
                moveTo(15f, 9.5f); lineTo(15f, 19.5f)
            }
        }
    }

    val Ppt: ImageVector by lazy {
        classicIcon("Ppt") {
            strokePath {
                moveTo(3.5f, 4.5f); lineTo(20.5f, 4.5f); lineTo(20.5f, 19.5f); lineTo(3.5f, 19.5f); close()
                moveTo(10f, 8.5f); lineTo(15.5f, 12f); lineTo(10f, 15.5f); close()
            }
        }
    }

    val Text: ImageVector by lazy {
        classicIcon("Text", autoMirror = true) {
            strokePath(strokeWidth = 2f) {
                moveTo(4f, 6.5f); lineTo(20f, 6.5f)
                moveTo(4f, 11.5f); lineTo(20f, 11.5f)
                moveTo(4f, 16.5f); lineTo(14f, 16.5f)
            }
        }
    }
}
