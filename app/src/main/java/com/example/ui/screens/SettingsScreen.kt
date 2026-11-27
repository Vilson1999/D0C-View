package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentViewOrientation
import com.example.model.OfficeRenderFormat
import com.example.model.PageViewMode
import com.example.model.ThemeMode
import com.example.model.formatFileSize
import com.example.ui.components.AppIcons
import com.example.ui.components.DocViewerAppIcon
import com.example.ui.components.docTypeIcon
import com.example.ui.theme.DocPillColor
import com.example.ui.theme.ExcelPillColor
import com.example.ui.theme.LocalMaterialUiEnabled
import com.example.ui.theme.PdfPillColor
import com.example.ui.theme.PptPillColor
import com.example.ui.theme.TextPillColor
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasFullStorageAccess: Boolean,
    dotFolderDocCount: Int,
    countsByType: Map<DocType, Int>,
    cacheSizeBytes: Long,
    onBack: () -> Unit,
    onSetUseMaterialUi: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDocTypeVisible: (DocType, Boolean) -> Unit,
    onSetScanDotFolders: (Boolean) -> Unit,
    onSetAutoHideReaderUi: (Boolean) -> Unit,
    onSetAutoCleanRenderCache: (Boolean) -> Unit,
    // PDF folder
    onSetPdfPageViewMode: (PageViewMode) -> Unit,
    onSetPdfViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetPdfInvertInDarkMode: (Boolean) -> Unit,
    onSetPdfDoubleTapZoom: (Float) -> Unit,
    // DOCX folder
    onSetDocxRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetDocxPageViewMode: (PageViewMode) -> Unit,
    onSetDocxViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetDocxFontSizeSp: (Float) -> Unit,
    // XLSX folder
    onSetXlsxRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetXlsxPageViewMode: (PageViewMode) -> Unit,
    onSetXlsxViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowExcelGridlines: (Boolean) -> Unit,
    onSetXlsxFontSizeSp: (Float) -> Unit,
    // PPT folder
    onSetPptRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetPptPageViewMode: (PageViewMode) -> Unit,
    onSetPptViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowPptSpeakerNotes: (Boolean) -> Unit,
    // TEXT folder
    onSetTextRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetTextPageViewMode: (PageViewMode) -> Unit,
    onSetTextViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowLineNumbers: (Boolean) -> Unit,
    onSetTextWordWrap: (Boolean) -> Unit,
    onSetTextMonospace: (Boolean) -> Unit,
    onSetTextFontSizeSp: (Float) -> Unit,
    // Storage & Cache actions
    onRequestStorageAccess: () -> Unit,
    onPickCustomFolder: () -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onImportFile: () -> Unit,
    onRescanNow: () -> Unit,
    onClearCache: () -> Unit
) {
    BackHandler { onBack() }

    var pdfExpanded by remember { mutableStateOf(false) }
    var docxExpanded by remember { mutableStateOf(false) }
    var xlsxExpanded by remember { mutableStateOf(false) }
    var pptExpanded by remember { mutableStateOf(false) }
    var textExpanded by remember { mutableStateOf(false) }
    var visibilityExpanded by remember { mutableStateOf(true) }
    var appearanceExpanded by remember { mutableStateOf(false) }
    var storageExpanded by remember { mutableStateOf(false) }
    var compressionExpanded by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            val materialUi = LocalMaterialUiEnabled.current
            Surface(
                color = if (materialUi) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
                tonalElevation = if (materialUi) 2.dp else 1.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .displayCutoutPadding()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
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
                .displayCutoutPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item(key = "folder_pdf") {
                SettingsFolder(
                    title = "PDF",
                    subtitle = "${settings.pdfPageViewMode.label.removeSuffix(" View")} • ${settings.pdfViewOrientation.label} • Zoom ${settings.pdfDoubleTapZoom.roundToInt()}x" +
                        if (settings.pdfInvertInDarkMode) " • Dark Invert" else "",
                    itemCount = 4,
                    folderColor = PdfPillColor,
                    icon = docTypeIcon(DocType.PDF),
                    expanded = pdfExpanded,
                    onToggle = { pdfExpanded = !pdfExpanded },
                    testTag = "folder_pdf"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsFlowRow(
                            title = "PDF Page Layout",
                            options = PageViewMode.entries,
                            selected = settings.pdfPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetPdfPageViewMode,
                            tagPrefix = "pdf_view_mode"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Document View",
                            options = DocumentViewOrientation.entries,
                            selected = settings.pdfViewOrientation,
                            labelFor = { it.label },
                            onSelect = onSetPdfViewOrientation,
                            tagPrefix = "pdf_orient"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Double-Tap Zoom Level",
                            options = listOf(2f, 3f),
                            selected = if (settings.pdfDoubleTapZoom >= 2.5f) 3f else 2f,
                            labelFor = { "${it.roundToInt()}x Zoom" },
                            onSelect = onSetPdfDoubleTapZoom,
                            tagPrefix = "pdf_zoom"
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Night Filter in Dark Mode",
                            subtitle = "Invert bright white PDF pages for comfortable night reading",
                            checked = settings.pdfInvertInDarkMode,
                            onCheckedChange = onSetPdfInvertInDarkMode,
                            modifier = Modifier.testTag("toggle_pdf_dark_invert")
                        )
                    }
                }
            }

            item(key = "folder_docx") {
                SettingsFolder(
                    title = "DOCX",
                    subtitle = "${settings.docxRenderFormat.shortLabel} • ${settings.docxPageViewMode.label.removeSuffix(" View")} • ${settings.docxViewOrientation.label} • ${settings.docxFontSizeSp.roundToInt()}sp",
                    itemCount = 4,
                    folderColor = DocPillColor,
                    icon = docTypeIcon(DocType.DOC),
                    expanded = docxExpanded,
                    onToggle = { docxExpanded = !docxExpanded },
                    testTag = "folder_docx"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsFlowRow(
                            title = "Show DOCX As",
                            options = OfficeRenderFormat.entries,
                            selected = settings.docxRenderFormat,
                            labelFor = { it.label },
                            onSelect = onSetDocxRenderFormat,
                            tagPrefix = "docx_format"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "DOCX Page Layout",
                            options = PageViewMode.entries,
                            selected = settings.docxPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetDocxPageViewMode,
                            tagPrefix = "docx_view_mode"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Document View",
                            options = DocumentViewOrientation.entries,
                            selected = settings.docxViewOrientation,
                            labelFor = { it.label },
                            onSelect = onSetDocxViewOrientation,
                            tagPrefix = "docx_orient"
                        )
                        HorizontalDivider()
                        FontSizeSliderRow(
                            title = "DOCX Text Size",
                            valueSp = settings.docxFontSizeSp,
                            valueRange = 11f..24f,
                            onValueChange = onSetDocxFontSizeSp
                        )
                    }
                }
            }

            item(key = "folder_xlsx") {
                SettingsFolder(
                    title = "XLSX",
                    subtitle = "${settings.xlsxRenderFormat.shortLabel} • ${settings.xlsxPageViewMode.label.removeSuffix(" View")} • ${settings.xlsxViewOrientation.label}" +
                        if (settings.showExcelGridlines) " • Grid" else "",
                    itemCount = 5,
                    folderColor = ExcelPillColor,
                    icon = docTypeIcon(DocType.EXCEL),
                    expanded = xlsxExpanded,
                    onToggle = { xlsxExpanded = !xlsxExpanded },
                    testTag = "folder_xlsx"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsFlowRow(
                            title = "Show XLSX As",
                            options = OfficeRenderFormat.entries,
                            selected = settings.xlsxRenderFormat,
                            labelFor = { it.label },
                            onSelect = onSetXlsxRenderFormat,
                            tagPrefix = "xlsx_format"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "XLSX Page Layout",
                            options = PageViewMode.entries,
                            selected = settings.xlsxPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetXlsxPageViewMode,
                            tagPrefix = "xlsx_view_mode"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Document View",
                            options = DocumentViewOrientation.entries,
                            selected = settings.xlsxViewOrientation,
                            labelFor = { it.label },
                            onSelect = onSetXlsxViewOrientation,
                            tagPrefix = "xlsx_orient"
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Gridlines & Headers",
                            subtitle = "Show column letters (A, B, C), row numbers, and cell borders",
                            checked = settings.showExcelGridlines,
                            onCheckedChange = onSetShowExcelGridlines,
                            modifier = Modifier.testTag("toggle_xlsx_gridlines")
                        )
                        HorizontalDivider()
                        FontSizeSliderRow(
                            title = "XLSX Cell Text Size",
                            valueSp = settings.xlsxFontSizeSp,
                            valueRange = 11f..22f,
                            onValueChange = onSetXlsxFontSizeSp
                        )
                    }
                }
            }

            item(key = "folder_ppt") {
                SettingsFolder(
                    title = "PPT",
                    subtitle = "${settings.pptRenderFormat.shortLabel} • ${settings.pptPageViewMode.label.removeSuffix(" View")} • ${settings.pptViewOrientation.label}" +
                        if (settings.showPptSpeakerNotes) " • Notes" else "",
                    itemCount = 4,
                    folderColor = PptPillColor,
                    icon = docTypeIcon(DocType.PPT),
                    expanded = pptExpanded,
                    onToggle = { pptExpanded = !pptExpanded },
                    testTag = "folder_ppt"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsFlowRow(
                            title = "Show PPT As",
                            options = OfficeRenderFormat.entries,
                            selected = settings.pptRenderFormat,
                            labelFor = { it.label },
                            onSelect = onSetPptRenderFormat,
                            tagPrefix = "ppt_format"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "PPT Page Layout",
                            options = PageViewMode.entries,
                            selected = settings.pptPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetPptPageViewMode,
                            tagPrefix = "ppt_view_mode"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Document View",
                            options = DocumentViewOrientation.entries,
                            selected = settings.pptViewOrientation,
                            labelFor = { it.label },
                            onSelect = onSetPptViewOrientation,
                            tagPrefix = "ppt_orient"
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Show Speaker Notes",
                            subtitle = "Include slide presenter notes in PDF and Original slide view",
                            checked = settings.showPptSpeakerNotes,
                            onCheckedChange = onSetShowPptSpeakerNotes,
                            modifier = Modifier.testTag("toggle_ppt_notes")
                        )
                    }
                }
            }

            item(key = "folder_text") {
                SettingsFolder(
                    title = "TEXT",
                    subtitle = "${settings.textRenderFormat.shortLabel} • ${settings.textPageViewMode.label.removeSuffix(" View")} • ${settings.textViewOrientation.label} • ${settings.textFontSizeSp.roundToInt()}sp" +
                        if (settings.showLineNumbers) " • Line #" else "",
                    itemCount = 7,
                    folderColor = TextPillColor,
                    icon = docTypeIcon(DocType.TEXT),
                    expanded = textExpanded,
                    onToggle = { textExpanded = !textExpanded },
                    testTag = "folder_text"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OptionChipsFlowRow(
                            title = "Show TEXT As",
                            options = OfficeRenderFormat.entries,
                            selected = settings.textRenderFormat,
                            labelFor = { it.label },
                            onSelect = onSetTextRenderFormat,
                            tagPrefix = "text_format"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "TEXT Page Layout",
                            options = PageViewMode.entries,
                            selected = settings.textPageViewMode,
                            labelFor = { it.label.removeSuffix(" View") },
                            onSelect = onSetTextPageViewMode,
                            tagPrefix = "text_view_mode"
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Document View",
                            options = DocumentViewOrientation.entries,
                            selected = settings.textViewOrientation,
                            labelFor = { it.label },
                            onSelect = onSetTextViewOrientation,
                            tagPrefix = "text_orient"
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Show Line Numbers",
                            subtitle = "Display line numbers in TEXT viewer",
                            checked = settings.showLineNumbers,
                            onCheckedChange = onSetShowLineNumbers,
                            modifier = Modifier.testTag("toggle_text_line_numbers")
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Word Wrap",
                            subtitle = "Wrap long lines automatically instead of horizontal scrolling",
                            checked = settings.textWordWrap,
                            onCheckedChange = onSetTextWordWrap,
                            modifier = Modifier.testTag("toggle_text_word_wrap")
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Monospace Font",
                            subtitle = "Use fixed-width JetBrains Mono typeface for code and logs",
                            checked = settings.textMonospace,
                            onCheckedChange = onSetTextMonospace,
                            modifier = Modifier.testTag("toggle_text_monospace")
                        )
                        HorizontalDivider()
                        FontSizeSliderRow(
                            title = "TEXT Font Size",
                            valueSp = settings.textFontSizeSp,
                            valueRange = 11f..24f,
                            onValueChange = onSetTextFontSizeSp
                        )
                    }
                }
            }

            item(key = "folder_doc_visibility") {
                val visibleCount = settings.visibleDocTypes.size
                SettingsFolder(
                    title = "Home Screen Documents",
                    subtitle = "Show or hide PDF, DOC, EXCEL, PPT, TEXT ($visibleCount/${DocType.entries.size} shown)",
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
                val appearanceSummary = buildString {
                    append(settings.themeMode.label.removeSuffix(" Mode"))
                    append(if (settings.useMaterialUi) " • Material UI" else " • Classic UI")
                    if (settings.autoHideReaderUi) append(" • Auto-hide")
                }
                SettingsFolder(
                    title = "Appearance & Reader UI",
                    subtitle = appearanceSummary,
                    itemCount = 3,
                    folderColor = DocPillColor,
                    icon = AppIcons.Palette,
                    expanded = appearanceExpanded,
                    onToggle = { appearanceExpanded = !appearanceExpanded },
                    testTag = "folder_appearance"
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SettingsSwitchRow(
                            title = "Material UI",
                            subtitle = "Use Material Design 3 dynamic colors and tonal surfaces",
                            checked = settings.useMaterialUi,
                            onCheckedChange = onSetUseMaterialUi,
                            modifier = Modifier.testTag("toggle_material_ui")
                        )
                        HorizontalDivider()
                        OptionChipsFlowRow(
                            title = "Theme",
                            options = ThemeMode.entries,
                            selected = settings.themeMode,
                            labelFor = { it.label.removeSuffix(" Mode") },
                            onSelect = onSetThemeMode,
                            tagPrefix = "theme_mode"
                        )
                        HorizontalDivider()
                        SettingsSwitchRow(
                            title = "Auto-hide reader UI",
                            subtitle = "Hide top bar & page pill after 3s; tap screen to show or hide",
                            checked = settings.autoHideReaderUi,
                            onCheckedChange = onSetAutoHideReaderUi,
                            modifier = Modifier.testTag("toggle_auto_hide_reader_ui")
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
private fun FontSizeSliderRow(
    title: String,
    valueSp: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = "${valueSp.roundToInt()} sp",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = valueSp,
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> OptionChipsFlowRow(
    title: String,
    options: List<T>,
    selected: T,
    labelFor: (T) -> String,
    onSelect: (T) -> Unit,
    tagPrefix: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEachIndexed { idx, option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(labelFor(option)) },
                    modifier = Modifier.testTag("${tagPrefix}_$idx")
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
    icon: ImageVector = AppIcons.Folder,
    content: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (expanded) folderColor.copy(alpha = 0.55f) else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "folderBorder"
    )
    val iconContainerColor by animateColorAsState(
        targetValue = folderColor.copy(alpha = if (expanded) 0.2f else 0.13f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "folderIconBg"
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMedium),
        label = "folderChevron"
    )

    val materialUi = LocalMaterialUiEnabled.current
    val folderBg by animateColorAsState(
        targetValue = when {
            materialUi && expanded -> MaterialTheme.colorScheme.surfaceContainer
            materialUi -> MaterialTheme.colorScheme.surfaceContainerLow
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "folderBg"
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = folderBg,
        tonalElevation = if (materialUi) if (expanded) 3.dp else 1.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = borderColor
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
                    color = iconContainerColor,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
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
                    imageVector = AppIcons.ExpandMore,
                    contentDescription = if (expanded) "Collapse folder" else "Expand folder",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.graphicsLayer {
                        rotationZ = chevronRotation
                    }
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
                ) + fadeIn(tween(200)),
                exit = shrinkVertically(
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow)
                ) + fadeOut(tween(140))
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
    val badgeBg by animateColorAsState(
        targetValue = type.pillColor.copy(alpha = if (checked) 0.16f else 0.08f),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docTypeRowBg"
    )
    val iconTint by animateColorAsState(
        targetValue = if (checked) type.pillColor else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "docTypeRowTint"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (checked) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium),
        label = "docTypeRowScale"
    )

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
            color = badgeBg,
            modifier = Modifier
                .size(36.dp)
                .graphicsLayer {
                    scaleX = iconScale
                    scaleY = iconScale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = docTypeIcon(type),
                    contentDescription = type.label,
                    tint = iconTint,
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
