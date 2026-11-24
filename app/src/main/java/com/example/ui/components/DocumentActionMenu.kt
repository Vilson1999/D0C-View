package com.example.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_rename)) },
            leadingIcon = { Icon(AppIcons.Rename, contentDescription = null) },
            onClick = {
                onDismiss()
                onRename()
            },
            modifier = Modifier.testTag("${tagPrefix}_rename")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_share)) },
            leadingIcon = { Icon(Icons.Outlined.Share, contentDescription = null) },
            onClick = {
                onDismiss()
                onShare()
            },
            modifier = Modifier.testTag("${tagPrefix}_share")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_print)) },
            leadingIcon = { Icon(AppIcons.Print, contentDescription = null) },
            onClick = {
                onDismiss()
                onPrint()
            },
            modifier = Modifier.testTag("${tagPrefix}_print")
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_info)) },
            leadingIcon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            onClick = {
                onDismiss()
                onInfo()
            },
            modifier = Modifier.testTag("${tagPrefix}_info")
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.error
                )
            },
            leadingIcon = {
                Icon(
                    AppIcons.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            onClick = {
                onDismiss()
                onDelete()
            },
            modifier = Modifier.testTag("${tagPrefix}_delete")
        )
    }
}
