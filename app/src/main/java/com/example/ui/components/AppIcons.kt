package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.CropLandscape
import androidx.compose.material.icons.filled.CropPortrait
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Slideshow
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewCarousel
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.CropLandscape
import androidx.compose.material.icons.rounded.CropPortrait
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.Print
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Slideshow
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.ViewStream
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.LocalMaterialIconsEnabled
import com.example.ui.theme.bouncyPressScale

@Immutable
data class StyledIconColors(
    val background: Color,
    val icon: Color
)

/**
 * Icon provider switching between:
 * - ON: Android Material Rounded icon design ([Icons.Rounded])
 * - OFF: Normal Jetpack Compose Material icons ([Icons.Filled])
 */
object AppIcons {

    fun styledColors(accentColor: Color): StyledIconColors {
        val lum = accentColor.luminance()
        val bgRatio = if (lum > 0.70f) 0.70f else 0.84f
        val fgRatio = if (lum > 0.45f) 0.68f else 0.52f
        return StyledIconColors(
            background = lerp(accentColor, Color.White, bgRatio),
            icon = lerp(accentColor, Color.Black, fgRatio)
        )
    }

    @Composable
    fun scaledSize(baseSize: Dp): Dp =
        if (LocalMaterialIconsEnabled.current) baseSize * 1.10f else baseSize

    @Composable fun search(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Search else Icons.Filled.Search

    @Composable fun settings(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Settings else Icons.Filled.Settings

    @Composable fun close(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Close else Icons.Filled.Close

    @Composable fun arrowBack(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.ArrowBack else Icons.AutoMirrored.Filled.ArrowBack

    @Composable fun moreVert(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.MoreVert else Icons.Filled.MoreVert

    @Composable fun refresh(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Refresh else Icons.Filled.Refresh

    @Composable fun share(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Share else Icons.Filled.Share

    @Composable fun info(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Info else Icons.Filled.Info

    @Composable fun check(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Check else Icons.Filled.Check

    @Composable fun sort(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.Sort else Icons.AutoMirrored.Filled.Sort

    @Composable fun darkMode(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.DarkMode else Icons.Filled.DarkMode

    @Composable fun lightMode(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.LightMode else Icons.Filled.LightMode

    @Composable fun brightnessAuto(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.BrightnessAuto else Icons.Filled.BrightnessAuto

    @Composable fun selectAll(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.SelectAll else Icons.Filled.SelectAll

    @Composable fun viewCarousel(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ViewCarousel else Icons.Filled.ViewCarousel

    @Composable fun viewStream(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ViewStream else Icons.Filled.ViewStream

    @Composable fun screenRotation(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ScreenRotation else Icons.Filled.ScreenRotation

    @Composable fun rename(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.DriveFileRenameOutline else Icons.Filled.DriveFileRenameOutline

    @Composable fun print(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Print else Icons.Filled.Print

    @Composable fun delete(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.DeleteOutline else Icons.Filled.Delete

    @Composable fun folder(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Folder else Icons.Filled.Folder

    @Composable fun folderOpen(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.FolderOpen else Icons.Filled.FolderOpen

    @Composable fun createNewFolder(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.CreateNewFolder else Icons.Filled.CreateNewFolder

    @Composable fun fileOpen(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.FileOpen else Icons.Filled.FileOpen

    @Composable fun storage(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Storage else Icons.Filled.Storage

    @Composable fun palette(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Palette else Icons.Filled.Palette

    @Composable fun calendarToday(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.CalendarToday else Icons.Filled.CalendarToday

    @Composable fun visibilityOff(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.VisibilityOff else Icons.Filled.VisibilityOff

    @Composable fun insertDriveFile(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.InsertDriveFile else Icons.AutoMirrored.Filled.InsertDriveFile

    @Composable fun errorOutline(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ErrorOutline else Icons.Filled.ErrorOutline

    @Composable fun checkCircle(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.CheckCircle else Icons.Filled.CheckCircle

    @Composable fun radioButtonUnchecked(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.RadioButtonUnchecked else Icons.Filled.RadioButtonUnchecked

    @Composable fun cropPortrait(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.CropPortrait else Icons.Filled.CropPortrait

    @Composable fun cropLandscape(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.CropLandscape else Icons.Filled.CropLandscape

    @Composable fun chevronRight(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.KeyboardArrowRight else Icons.AutoMirrored.Filled.KeyboardArrowRight

    @Composable fun pdf(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.PictureAsPdf else Icons.Filled.PictureAsPdf

    @Composable fun doc(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Description else Icons.Filled.Description

    @Composable fun excel(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.TableChart else Icons.Filled.TableChart

    @Composable fun ppt(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.Slideshow else Icons.Filled.Slideshow

    @Composable fun textDoc(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.AutoMirrored.Rounded.Notes else Icons.AutoMirrored.Filled.Notes

    @Composable fun zoomIn(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ZoomIn else Icons.Filled.ZoomIn

    @Composable fun zoomOut(): ImageVector =
        if (LocalMaterialIconsEnabled.current) Icons.Rounded.ZoomOut else Icons.Filled.ZoomOut
}

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    accentColor: Color,
    plainTint: Color,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 34.dp,
    cornerRadius: Dp = 10.dp,
    content: @Composable (tint: Color) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val bounceModifier = modifier.bouncyPressScale(
        interactionSource = interactionSource,
        pressedScale = 0.84f
    )
    if (LocalMaterialIconsEnabled.current) {
        val colors = AppIcons.styledColors(accentColor)
        Surface(
            onClick = onClick,
            interactionSource = interactionSource,
            shape = RoundedCornerShape(cornerRadius),
            color = colors.background,
            contentColor = colors.icon,
            border = BorderStroke(0.8.dp, colors.icon.copy(alpha = 0.18f)),
            modifier = bounceModifier.size(buttonSize)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                content(colors.icon)
            }
        }
    } else {
        IconButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = bounceModifier.size(buttonSize)
        ) {
            content(plainTint)
        }
    }
}

