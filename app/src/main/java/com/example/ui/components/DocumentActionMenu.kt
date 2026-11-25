package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.R

@Composable
fun DocumentActionDropdownMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    tagPrefix: String = "menu"
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss
    ) {
        CompactMenuActionItem(
            icon = AppIcons.Rename,
            label = stringResource(R.string.action_rename),
            onClick = {
                onDismiss()
                onRename()
            },
            testTag = "${tagPrefix}_rename"
        )
        CompactMenuActionItem(
            icon = Icons.Outlined.Share,
            label = stringResource(R.string.action_share),
            onClick = {
                onDismiss()
                onShare()
            },
            testTag = "${tagPrefix}_share"
        )
        CompactMenuActionItem(
            icon = AppIcons.Print,
            label = stringResource(R.string.action_print),
            onClick = {
                onDismiss()
                onPrint()
            },
            testTag = "${tagPrefix}_print"
        )
        CompactMenuActionItem(
            icon = Icons.Outlined.Info,
            label = stringResource(R.string.action_info),
            onClick = {
                onDismiss()
                onInfo()
            },
            testTag = "${tagPrefix}_info"
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
        CompactMenuActionItem(
            icon = AppIcons.DeleteOutline,
            label = stringResource(R.string.action_delete),
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
    onClick: () -> Unit,
    testTag: String,
    tint: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = tint
        )
    }
}
