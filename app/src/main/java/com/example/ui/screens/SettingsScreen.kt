package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.PageViewMode
import com.example.model.ThemeMode
import com.example.model.formatFileSize
import com.example.ui.components.AppIcons
import com.example.ui.components.DocViewerAppIcon
import com.example.ui.components.docTypeIcon
import com.example.ui.theme.DocPillColor
import com.example.ui.theme.PdfPillColor
import com.example.ui.theme.PptPillColor
import com.example.ui.theme.TextPillColor
import com.example.ui.theme.XmlPillColor
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasFullStorageAccess: Boolean,
    dotFolderDocCount: Int,
    countsByType: Map<DocType, Int>,
    cacheSizeBytes: Long,
    onBack: () -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDocTypeVisible: (DocType, Boolean) -> Unit,
    onSetScanDotFolders: (Boolean) -> Unit,
    onSetDefaultPageViewMode: (PageViewMode) -> Unit,
    onSetXmlSyntaxHighlight: (Boolean) -> Unit,
    onSetShowLineNumbers: (Boolean) -> Unit,
    onSetReaderFontSizeSp: (Float) -> Unit,
    onSetAutoHideReaderUi: (Boolean) -> Unit,
    onSetAutoCleanRenderCache: (Boolean) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onPickCustomFolder: () -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onImportFile: () -> Unit,
    onRescanNow: () -> Unit,
    onClearCache: () -> Unit
) {
    BackHandler { onBack() }

    var visibilityExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var storageExpanded by remember { mutableStateOf(false) }
    var readerExpanded by remember { mutableStateOf(false) }
    var compressionExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                    Column(modifier = Modifier.padding(start = 6.dp)) {
                        Text(text = "Settings", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Organized into folders",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "folder_doc_visibility") {
                val visibleCount = settings.visibleDocTypes.size
                SettingsFolder(
                    title = "Home Screen Documents",
                    subtitle = "Show or hide PDF, DOC, XML, PPT, TEXT ($visibleCount/${DocType.entries.size} shown)",
                    itemCount = DocType.entries.size,
                    folderColor = PdfPillColor,
                    expanded = visibilityExpanded,
                    onToggle = { visibilityExpanded = !visibilityExpanded },
                    testTag = "folder_doc_visibility"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DocType.entries.forEachIndexed { index, type ->
                            if (index > 0) HorizontalDivider()
                            val isShown = type in settings.visibleDocTypes
                            val foundCount = countsByType[type] ?: 0
                            DocTypeVisibilityRow(
                                type = type,
                                foundCount = foundCount,
                                checked = isShown,
                                onCheckedChange = { onSetDocTypeVisible(type, it) }
                            )
                        }
                    }
                }
            }

            item(key = "folder_appearance") {
                SettingsFolder(
                    title = "Appearance & Display",
                    subtitle = "Theme mode (System, Light, Dark)",
                    itemCount = 1,
                    folderColor = DocPillColor,
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    testTag = "folder_appearance"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsRow(
                            title = "Theme",
                            options = ThemeMode.entries,
                            selected = settings.themeMode,
                            labelFor = { it.label.removeSuffix(" Mode") },
                            onSelect = onSetThemeMode
                        )
                    }
                }
            }

            item(key = "folder_storage") {
                SettingsFolder(
                    title = "Storage & Scanning",
                    subtitle = "Dot folders, offline access & custom trees",
                    itemCount = 4,
                    folderColor = PptPillColor,
                    expanded = storageExpanded,
                    onToggle = { storageExpanded = !storageExpanded },
                    testTag = "folder_storage"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSwitchRow(
                            title = "Scan (.) dot folders",
                            subtitle = "Automatically include hidden folders ($dotFolderDocCount found)",
                            checked = settings.scanDotFolders,
                            onCheckedChange = onSetScanDotFolders
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "All Files Offline Access", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (hasFullStorageAccess) {
                                        "Granted — scanning internal, external & dot folders"
                                    } else {
                                        "Limited — grant access to scan all folders"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            if (!hasFullStorageAccess) {
                                Button(onClick = onRequestStorageAccess) { Text("Grant") }
                            } else {
                                OutlinedButton(onClick = onRescanNow) {
                                    Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Rescan")
                                }
                            }
                        }
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(onClick = onPickCustomFolder, modifier = Modifier.weight(1f)) {
                                Icon(AppIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Folder")
                            }
                            OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f)) {
                                Icon(AppIcons.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import File")
                            }
                        }
                        if (settings.customFolderUris.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Linked Folders",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                settings.customFolderUris.forEach { uriStr ->
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = uriStr.substringAfterLast("%3A").ifBlank { uriStr },
                                                style = MaterialTheme.typography.bodySmall,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            IconButton(
                                                onClick = { onRemoveCustomFolder(uriStr) },
                                                modifier = Modifier.size(32.dp)
                                            ) {
                                                Icon(
                                                    AppIcons.DeleteOutline,
                                                    contentDescription = "Remove folder",
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            item(key = "folder_reader") {
                SettingsFolder(
                    title = "Reader & Formats",
                    subtitle = "Page view mode, auto-hide UI, font size, XML syntax",
                    itemCount = 5,
                    folderColor = XmlPillColor,
                    expanded = readerExpanded,
                    onToggle = { readerExpanded = !readerExpanded },
                    testTag = "folder_reader"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsRow(
                            title = "Default Page View Mode",
                            options = PageViewMode.entries,
                            selected = settings.defaultPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetDefaultPageViewMode
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Auto-hide reader UI",
                            subtitle = "Hide top bar & page pill after 2s; tap screen to show or hide",
                            checked = settings.autoHideReaderUi,
                            onCheckedChange = onSetAutoHideReaderUi,
                            modifier = Modifier.testTag("toggle_auto_hide_reader_ui")
                        )
                        HorizontalDivider()
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(text = "Reader Text Size", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = "${settings.readerFontSizeSp.roundToInt()} sp",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Slider(
                                value = settings.readerFontSizeSp,
                                onValueChange = onSetReaderFontSizeSp,
                                valueRange = 11f..24f
                            )
                        }
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "XML Syntax Highlighting",
                            subtitle = "Color-code tags, attributes, and values",
                            checked = settings.xmlSyntaxHighlight,
                            onCheckedChange = onSetXmlSyntaxHighlight
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Show Line Numbers",
                            subtitle = "Display line numbers in XML and TEXT viewer",
                            checked = settings.showLineNumbers,
                            onCheckedChange = onSetShowLineNumbers
                        )
                    }
                }
            }

            item(key = "folder_compression") {
                SettingsFolder(
                    title = "Cache & Optimization",
                    subtitle = "Auto-cleanup & temporary cache",
                    itemCount = 2,
                    folderColor = TextPillColor,
                    expanded = compressionExpanded,
                    onToggle = { compressionExpanded = !compressionExpanded },
                    testTag = "folder_compression"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSwitchRow(
                            title = "Auto-delete unnecessary cache",
                            subtitle = "Automatically remove orphaned, expired & oversized cache while keeping fast-browse cache",
                            checked = settings.autoCleanRenderCache,
                            onCheckedChange = onSetAutoCleanRenderCache
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Temporary Cache", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = formatFileSize(cacheSizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = onClearCache) { Text("Clear") }
                        }
                    }
                }
            }

            item(key = "settings_app_info_footer") {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .fillMaxWidth()
                        .widthIn(max = 700.dp)
                        .padding(top = 4.dp, bottom = 8.dp)
                        .testTag("settings_app_info_footer")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        DocViewerAppIcon(
                            size = 56.dp,
                            modifier = Modifier.testTag("settings_app_icon")
                        )
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .testTag("settings_app_name")
                        )
                        Text(
                            text = "Developer: ${stringResource(R.string.developer_name)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("settings_developer_name")
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "Version ${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .padding(horizontal = 12.dp, vertical = 4.dp)
                                    .testTag("settings_app_version")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun <T> OptionChipsRow(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(labelFor(option)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SettingsFolder(
    title: String,
    subtitle: String,
    itemCount: Int,
    folderColor: Color,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = if (expanded) folderColor.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp)
            .testTag(testTag)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = folderColor.copy(alpha = 0.14f),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppIcons.Folder,
                            contentDescription = null,
                            tint = folderColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = "$itemCount",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = if (expanded) AppIcons.ExpandLess else AppIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse folder" else "Expand folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.padding(bottom = 14.dp))
                    content()
                }
            }
        }
    }
}

@Composable
private fun DocTypeVisibilityRow(
    type: DocType,
    foundCount: Int,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val extPreview = type.extensions.take(3).joinToString(", ") { ".$it" }
    val countLabel = if (foundCount == 1) "1 file" else "$foundCount files"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 4.dp)
            .testTag("toggle_visibility_${type.name.lowercase()}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = type.pillColor.copy(alpha = if (checked) 0.16f else 0.08f),
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = docTypeIcon(type),
                    contentDescription = type.label,
                    tint = if (checked) type.pillColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${type.label} Documents",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = "$extPreview • $countLabel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
