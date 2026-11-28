package com.example.ui.components

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ripple
import com.example.R
import com.example.model.DocumentItem
import com.example.model.SortOption
import com.example.ui.theme.JetBrainsMonoFontFamily

@Composable
fun RenameDocumentDialog(
    document: DocumentItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val baseName = remember(document.name) {
        val idx = document.name.lastIndexOf('.')
        if (idx > 0) document.name.substring(0, idx) else document.name
    }
    var text by remember(document.id) { mutableStateOf(baseName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(AppIcons.Rename, contentDescription = null) },
        title = { Text("Rename Document") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("File name") },
                    suffix = {
                        if (document.extension.isNotBlank()) {
                            Text(".${document.extension}")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim()) },
                enabled = text.isNotBlank(),
                modifier = Modifier.testTag("rename_confirm_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun DeleteConfirmDialog(
    docs: List<DocumentItem>,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val count = docs.size
    val message = if (count == 1) {
        "Delete \"${docs.first().name}\" permanently from device storage?"
    } else {
        "Delete $count selected documents permanently from device storage?"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                AppIcons.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text(if (count == 1) "Delete Document" else "Delete $count Documents") },
        text = { Text(message) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                modifier = Modifier.testTag("delete_confirm_button")
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentInfoSheet(
    docs: List<DocumentItem>,
    onDismiss: () -> Unit
) {
    if (docs.isEmpty()) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (docs.size == 1) {
                val doc = docs.first()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = doc.type.pillColor.copy(alpha = 0.14f),
                        modifier = Modifier.size(44.dp)
                    ) {
                        androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = docTypeIcon(doc.type),
                                contentDescription = null,
                                tint = doc.type.pillColor
                            )
                        }
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = doc.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${doc.type.label} Document (.${doc.extension.uppercase()})",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                HorizontalDivider()

                InfoRow(AppIcons.Storage, "Size", "${doc.formattedSize} (${doc.sizeBytes} bytes)")
                InfoRow(AppIcons.CalendarToday, "Modified", doc.formattedDateTime)
                InfoRow(AppIcons.FolderOpen, "Folder", doc.parentFolderName)
                if (doc.isFromDotFolder) {
                    InfoRow(AppIcons.VisibilityOff, "Location Type", "Hidden (.) dot folder")
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            AppIcons.InsertDriveFile,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Offline Path",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = doc.path,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoFontFamily
                            ),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            } else {
                val totalBytes = docs.sumOf { it.sizeBytes }
                val sample = docs.first().copy(sizeBytes = totalBytes)
                Text(
                    text = "${docs.size} Documents Selected",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                HorizontalDivider()
                InfoRow(AppIcons.Storage, "Combined Size", sample.formattedSize)
                InfoRow(
                    AppIcons.InsertDriveFile,
                    "Formats",
                    docs.map { it.type.label }.distinct().joinToString(", ")
                )
                InfoRow(
                    AppIcons.VisibilityOff,
                    "From (.) Dot Folders",
                    "${docs.count { it.isFromDotFolder }} file(s)"
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
        }
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(108.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortOptionsSheet(
    currentOption: SortOption,
    onSelect: (SortOption) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .displayCutoutPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Sort Documents",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            SortOption.entries.forEach { option ->
                val selected = option == currentOption
                val rowBg by animateColorAsState(
                    targetValue = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surface
                    },
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "sortOptionBg"
                )
                val interactionSource = remember { MutableInteractionSource() }
                val pressed by interactionSource.collectIsPressedAsState()
                val scale by animateFloatAsState(
                    targetValue = if (pressed) 0.97f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMedium),
                    label = "sortOptionScale"
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = rowBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(
                            interactionSource = interactionSource,
                            indication = ripple(),
                            onClick = {
                                onSelect(option)
                                onDismiss()
                            }
                        )
                        .testTag("sort_${option.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                        )
                        AnimatedVisibility(
                            visible = selected,
                            enter = scaleIn(initialScale = 0.6f, animationSpec = spring(stiffness = Spring.StiffnessMedium)) + fadeIn(tween(150)),
                            exit = scaleOut(targetScale = 0.6f) + fadeOut(tween(100))
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StoragePermissionDialog(
    onAllow: () -> Unit,
    onDismiss: () -> Unit
) {
    val permissionItems = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            listOf(
                Triple(
                    AppIcons.FolderOpen,
                    "Read Documents",
                    "Scan and view PDF, DOC, EXCEL, PPT, and TEXT files across all folders including hidden (.) folders."
                ),
                Triple(
                    AppIcons.Storage,
                    "Write Documents",
                    "Rename, edit, and delete documents offline directly in device storage."
                )
            )
        } else {
            listOf(
                Triple(
                    AppIcons.FolderOpen,
                    "Read Documents",
                    "Scan and view PDF, DOC, EXCEL, PPT, and TEXT files stored on your device."
                ),
                Triple(
                    AppIcons.Storage,
                    "Write Documents",
                    "Rename, edit, and delete documents offline from device folders."
                )
            )
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = AppIcons.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = stringResource(R.string.permission_dialog_title),
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.permission_dialog_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                permissionItems.forEach { (icon, label, desc) ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(20.dp)
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = desc,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAllow,
                modifier = Modifier.testTag("permission_allow_button")
            ) {
                Text(stringResource(R.string.permission_allow_button))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("permission_dismiss_button")
            ) {
                Text(stringResource(R.string.permission_later_button))
            }
        },
        modifier = Modifier.testTag("storage_permission_dialog")
    )
}
