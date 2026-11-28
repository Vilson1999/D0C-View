package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.model.DocumentViewOrientation
import kotlinx.coroutines.delay

@Composable
fun DocumentActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    toggleFormatLabel: String? = null,
    onToggleFormat: (() -> Unit)? = null,
    activeDocumentIsLandscape: Boolean? = null,
    onSelectDocumentOrientation: ((DocumentViewOrientation) -> Unit)? = null,
    tagPrefix: String = "menu"
) {
    val hasToggleFormat = toggleFormatLabel != null && onToggleFormat != null
    val hasDocumentView = onSelectDocumentOrientation != null && activeDocumentIsLandscape != null
    val baseIndex = (if (hasToggleFormat) 1 else 0) + (if (hasDocumentView) 2 else 0)
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(14.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        if (toggleFormatLabel != null && onToggleFormat != null) {
            CompactMenuActionItem(
                icon = AppIcons.doc(),
                label = toggleFormatLabel,
                menuExpanded = expanded,
                staggerIndex = 0,
                onClick = {
                    onDismiss()
                    onToggleFormat.invoke()
                },
                testTag = "${tagPrefix}_toggle_format"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        }
        if (onSelectDocumentOrientation != null && activeDocumentIsLandscape != null) {
            val docViewBaseIdx = if (hasToggleFormat) 1 else 0
            Text(
                text = stringResource(R.string.action_document_view),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
            CompactMenuActionItem(
                icon = AppIcons.cropPortrait(),
                label = stringResource(R.string.doc_view_portrait),
                menuExpanded = expanded,
                staggerIndex = docViewBaseIdx,
                selected = activeDocumentIsLandscape == false,
                onClick = {
                    onDismiss()
                    onSelectDocumentOrientation.invoke(DocumentViewOrientation.PORTRAIT)
                },
                testTag = "${tagPrefix}_doc_view_portrait"
            )
            CompactMenuActionItem(
                icon = AppIcons.cropLandscape(),
                label = stringResource(R.string.doc_view_landscape),
                menuExpanded = expanded,
                staggerIndex = docViewBaseIdx + 1,
                selected = activeDocumentIsLandscape == true,
                onClick = {
                    onDismiss()
                    onSelectDocumentOrientation.invoke(DocumentViewOrientation.LANDSCAPE)
                },
                testTag = "${tagPrefix}_doc_view_landscape"
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        }
        CompactMenuActionItem(
            icon = AppIcons.rename(),
            label = stringResource(R.string.action_rename),
            menuExpanded = expanded,
            staggerIndex = baseIndex,
            onClick = {
                onDismiss()
                onRename()
            },
            testTag = "${tagPrefix}_rename"
        )
        CompactMenuActionItem(
            icon = AppIcons.share(),
            label = stringResource(R.string.action_share),
            menuExpanded = expanded,
            staggerIndex = baseIndex + 1,
            onClick = {
                onDismiss()
                onShare()
            },
            testTag = "${tagPrefix}_share"
        )
        CompactMenuActionItem(
            icon = AppIcons.print(),
            label = stringResource(R.string.action_print),
            menuExpanded = expanded,
            staggerIndex = baseIndex + 2,
            onClick = {
                onDismiss()
                onPrint()
            },
            testTag = "${tagPrefix}_print"
        )
        CompactMenuActionItem(
            icon = AppIcons.info(),
            label = stringResource(R.string.action_info),
            menuExpanded = expanded,
            staggerIndex = baseIndex + 3,
            onClick = {
                onDismiss()
                onInfo()
            },
            testTag = "${tagPrefix}_info"
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        CompactMenuActionItem(
            icon = AppIcons.delete(),
            label = stringResource(R.string.action_delete),
            menuExpanded = expanded,
            staggerIndex = baseIndex + 4,
            tint = MaterialTheme.colorScheme.error,
            onClick = {
                onDismiss()
                onDelete()
            },
            testTag = "${tagPrefix}_delete"
        )
    }
}

@Composable
private fun CompactMenuActionItem(
    icon: ImageVector,
    label: String,
    menuExpanded: Boolean,
    staggerIndex: Int,
    onClick: () -> Unit,
    testTag: String,
    selected: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(menuExpanded) {
        if (menuExpanded) {
            delay(staggerIndex * 22L)
            visible = true
        } else {
            visible = false
        }
    }

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "menuItemPress"
    )
    val effectiveTint = if (selected) MaterialTheme.colorScheme.primary else tint

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(150)) + slideInVertically(
            initialOffsetY = { -it / 4 },
            animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
        ),
        exit = fadeOut(tween(80))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                )
                .padding(horizontal = 12.dp)
                .testTag(testTag),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = effectiveTint,
                modifier = Modifier.size(AppIcons.scaledSize(18.dp))
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = effectiveTint
            )
            if (selected) {
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = AppIcons.check(),
                    contentDescription = null,
                    tint = effectiveTint,
                    modifier = Modifier.size(AppIcons.scaledSize(16.dp))
                )
            }
        }
    }
}
