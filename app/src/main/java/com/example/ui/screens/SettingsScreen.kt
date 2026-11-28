package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.example.ui.theme.NavigationBarSideBackdrop
import com.example.ui.theme.PdfPillColor
import com.example.ui.theme.PptPillColor
import com.example.ui.theme.SyncSystemBarsAppearance
import com.example.ui.theme.TextPillColor
import kotlin.math.roundToInt

private enum class SettingsFolderId(
    val title: String,
    val testTag: String,
    val color: Color,
    val itemCount: Int
) {
    PDF("PDF", "folder_pdf", PdfPillColor, 4),
    DOCX("DOCX", "folder_docx", DocPillColor, 4),
    XLSX("XLSX", "folder_xlsx", ExcelPillColor, 5),
    PPT("PPT", "folder_ppt", PptPillColor, 4),
    TEXT("TEXT", "folder_text", TextPillColor, 7),
    DOC_VISIBILITY("Home Screen Documents", "folder_doc_visibility", PdfPillColor, DocType.entries.size),
    APPEARANCE("Appearance & Reader UI", "folder_appearance", DocPillColor, 4),
    STORAGE("Storage & Scanning", "folder_storage", PptPillColor, 5),
    CACHE("Cache & Optimization", "folder_compression", TextPillColor, 2)
}

@Composable
fun SettingsScreen(
    settings: AppSettings,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    hasFullStorageAccess: Boolean,
    dotFolderDocCount: Int,
    countsByType: Map<DocType, Int>,
    cacheSizeBytes: Long,
    onBack: () -> Unit,
    onSetUseMaterialUi: (Boolean) -> Unit,
    onSetUseMaterialIcons: (Boolean) -> Unit,
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
    onClearCache: () -> Unit,
    syncSystemBars: Boolean = true
) {
    var activeFolder by rememberSaveable { mutableStateOf<SettingsFolderId?>(null) }

    val topBarColor = MaterialTheme.colorScheme.background
    val bottomBarColor = MaterialTheme.colorScheme.background
    if (syncSystemBars) {
        SyncSystemBarsAppearance(
            statusBarColor = topBarColor,
            navigationBarColor = bottomBarColor
        )
    }

    val handleBack = {
        if (activeFolder != null) {
            activeFolder = null
        } else {
            onBack()
        }
    }

    BackHandler(onBack = handleBack)

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = topBarColor,
                tonalElevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 29.4.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.statusBars.union(
                                    WindowInsets.displayCutout.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                    )
                                )
                            )
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            .height(29.4.dp)
                            .padding(horizontal = 4.dp, vertical = 0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = handleBack,
                            modifier = Modifier
                                .size(29.4.dp)
                                .testTag("settings_back_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.arrowBack(),
                                contentDescription = "Back",
                                modifier = Modifier.size(AppIcons.scaledSize(22.dp))
                            )
                        }
                        val currentFolder = activeFolder
                        if (currentFolder != null) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = currentFolder.color.copy(alpha = 0.16f),
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(22.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = folderIconFor(currentFolder),
                                        contentDescription = null,
                                        tint = currentFolder.color,
                                        modifier = Modifier.size(AppIcons.scaledSize(13.dp))
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = if (currentFolder != null) 8.dp else 4.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentFolder?.title ?: "Settings",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 26.46.sp,
                                    lineHeight = 26.46.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (currentFolder != null) {
                                    "${currentFolder.itemCount} settings"
                                } else {
                                    "Select a folder"
                                },
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 13.sp,
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                ),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bottomBarColor)
                    .windowInsetsBottomHeight(WindowInsets.navigationBars)
            )
        }
    ) { innerPadding ->
        AnimatedContent(
            targetState = activeFolder,
            transitionSpec = {
                if (targetState != null) {
                    (slideInHorizontally(
                        initialOffsetX = { (it * 0.22f).toInt() },
                        animationSpec = tween(240, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(200))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { -(it * 0.12f).toInt() },
                            animationSpec = tween(200, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(150))
                    )
                } else {
                    (slideInHorizontally(
                        initialOffsetX = { -(it * 0.14f).toInt() },
                        animationSpec = tween(220, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))).togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { (it * 0.22f).toInt() },
                            animationSpec = tween(220, easing = FastOutSlowInEasing)
                        ) + fadeOut(tween(150))
                    )
                }
            },
            label = "settingsFolderNavigation",
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .windowInsetsPadding(WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal))
                .clipToBounds()
        ) { folder ->
            if (folder == null) {
                SettingsFoldersOverview(
                    settings = settings,
                    hasReadPermission = hasReadPermission,
                    hasWritePermission = hasWritePermission,
                    onOpenFolder = { activeFolder = it }
                )
            } else {
                SettingsFolderDetailScreen(
                    folder = folder,
                    settings = settings,
                    hasReadPermission = hasReadPermission,
                    hasWritePermission = hasWritePermission,
                    hasFullStorageAccess = hasFullStorageAccess,
                    dotFolderDocCount = dotFolderDocCount,
                    countsByType = countsByType,
                    cacheSizeBytes = cacheSizeBytes,
                    onSetUseMaterialUi = onSetUseMaterialUi,
                    onSetUseMaterialIcons = onSetUseMaterialIcons,
                    onSetThemeMode = onSetThemeMode,
                    onSetDocTypeVisible = onSetDocTypeVisible,
                    onSetScanDotFolders = onSetScanDotFolders,
                    onSetAutoHideReaderUi = onSetAutoHideReaderUi,
                    onSetAutoCleanRenderCache = onSetAutoCleanRenderCache,
                    onSetPdfPageViewMode = onSetPdfPageViewMode,
                    onSetPdfViewOrientation = onSetPdfViewOrientation,
                    onSetPdfInvertInDarkMode = onSetPdfInvertInDarkMode,
                    onSetPdfDoubleTapZoom = onSetPdfDoubleTapZoom,
                    onSetDocxRenderFormat = onSetDocxRenderFormat,
                    onSetDocxPageViewMode = onSetDocxPageViewMode,
                    onSetDocxViewOrientation = onSetDocxViewOrientation,
                    onSetDocxFontSizeSp = onSetDocxFontSizeSp,
                    onSetXlsxRenderFormat = onSetXlsxRenderFormat,
                    onSetXlsxPageViewMode = onSetXlsxPageViewMode,
                    onSetXlsxViewOrientation = onSetXlsxViewOrientation,
                    onSetShowExcelGridlines = onSetShowExcelGridlines,
                    onSetXlsxFontSizeSp = onSetXlsxFontSizeSp,
                    onSetPptRenderFormat = onSetPptRenderFormat,
                    onSetPptPageViewMode = onSetPptPageViewMode,
                    onSetPptViewOrientation = onSetPptViewOrientation,
                    onSetShowPptSpeakerNotes = onSetShowPptSpeakerNotes,
                    onSetTextRenderFormat = onSetTextRenderFormat,
                    onSetTextPageViewMode = onSetTextPageViewMode,
                    onSetTextViewOrientation = onSetTextViewOrientation,
                    onSetShowLineNumbers = onSetShowLineNumbers,
                    onSetTextWordWrap = onSetTextWordWrap,
                    onSetTextMonospace = onSetTextMonospace,
                    onSetTextFontSizeSp = onSetTextFontSizeSp,
                    onRequestStorageAccess = onRequestStorageAccess,
                    onPickCustomFolder = onPickCustomFolder,
                    onRemoveCustomFolder = onRemoveCustomFolder,
                    onImportFile = onImportFile,
                    onRescanNow = onRescanNow,
                    onClearCache = onClearCache
                )
            }
        }
    }
    NavigationBarSideBackdrop(color = bottomBarColor)
    }
}

@Composable
private fun folderIconFor(folder: SettingsFolderId): ImageVector = when (folder) {
    SettingsFolderId.PDF -> docTypeIcon(DocType.PDF)
    SettingsFolderId.DOCX -> docTypeIcon(DocType.DOC)
    SettingsFolderId.XLSX -> docTypeIcon(DocType.EXCEL)
    SettingsFolderId.PPT -> docTypeIcon(DocType.PPT)
    SettingsFolderId.TEXT -> docTypeIcon(DocType.TEXT)
    SettingsFolderId.DOC_VISIBILITY -> AppIcons.folder()
    SettingsFolderId.APPEARANCE -> AppIcons.palette()
    SettingsFolderId.STORAGE -> AppIcons.storage()
    SettingsFolderId.CACHE -> AppIcons.folder()
}

@Composable
private fun SettingsFoldersOverview(
    settings: AppSettings,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    onOpenFolder: (SettingsFolderId) -> Unit
) {
    val visibleCount = settings.visibleDocTypes.size
    val readStatusLabel = if (hasReadPermission) "Read: OK" else "Read: Off"
    val writeStatusLabel = if (hasWritePermission) "Write: OK" else "Write: Off"
    val appearanceSummary = buildString {
        append(settings.themeMode.label.removeSuffix(" Mode"))
        append(if (settings.useMaterialUi) " • Material UI" else " • Classic UI")
        append(if (settings.useMaterialIcons) " • Material Icons" else " • Classic Icons")
        if (settings.autoHideReaderUi) append(" • Auto-hide")
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = SettingsFolderId.PDF.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.PDF,
                subtitle = "${settings.pdfPageViewMode.label.removeSuffix(" View")} • ${settings.pdfViewOrientation.label} • Zoom ${settings.pdfDoubleTapZoom.roundToInt()}x" +
                    if (settings.pdfInvertInDarkMode) " • Dark Invert" else "",
                onClick = { onOpenFolder(SettingsFolderId.PDF) }
            )
        }

        item(key = SettingsFolderId.DOCX.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.DOCX,
                subtitle = "${settings.docxRenderFormat.shortLabel} • ${settings.docxPageViewMode.label.removeSuffix(" View")} • ${settings.docxViewOrientation.label} • ${settings.docxFontSizeSp.roundToInt()}sp",
                onClick = { onOpenFolder(SettingsFolderId.DOCX) }
            )
        }

        item(key = SettingsFolderId.XLSX.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.XLSX,
                subtitle = "${settings.xlsxRenderFormat.shortLabel} • ${settings.xlsxPageViewMode.label.removeSuffix(" View")} • ${settings.xlsxViewOrientation.label}" +
                    if (settings.showExcelGridlines) " • Grid" else "",
                onClick = { onOpenFolder(SettingsFolderId.XLSX) }
            )
        }

        item(key = SettingsFolderId.PPT.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.PPT,
                subtitle = "${settings.pptRenderFormat.shortLabel} • ${settings.pptPageViewMode.label.removeSuffix(" View")} • ${settings.pptViewOrientation.label}" +
                    if (settings.showPptSpeakerNotes) " • Notes" else "",
                onClick = { onOpenFolder(SettingsFolderId.PPT) }
            )
        }

        item(key = SettingsFolderId.TEXT.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.TEXT,
                subtitle = "${settings.textRenderFormat.shortLabel} • ${settings.textPageViewMode.label.removeSuffix(" View")} • ${settings.textViewOrientation.label} • ${settings.textFontSizeSp.roundToInt()}sp" +
                    if (settings.showLineNumbers) " • Line #" else "",
                onClick = { onOpenFolder(SettingsFolderId.TEXT) }
            )
        }

        item(key = SettingsFolderId.DOC_VISIBILITY.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.DOC_VISIBILITY,
                subtitle = "Show or hide home screen pills ($visibleCount/${DocType.entries.size} shown)",
                onClick = { onOpenFolder(SettingsFolderId.DOC_VISIBILITY) }
            )
        }

        item(key = SettingsFolderId.APPEARANCE.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.APPEARANCE,
                subtitle = appearanceSummary,
                onClick = { onOpenFolder(SettingsFolderId.APPEARANCE) }
            )
        }

        item(key = SettingsFolderId.STORAGE.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.STORAGE,
                subtitle = "$readStatusLabel • $writeStatusLabel • Show hidden folders & custom trees",
                onClick = { onOpenFolder(SettingsFolderId.STORAGE) }
            )
        }

        item(key = SettingsFolderId.CACHE.name) {
            SettingsFolderCard(
                folder = SettingsFolderId.CACHE,
                subtitle = "Low-storage lossless fast cache",
                onClick = { onOpenFolder(SettingsFolderId.CACHE) }
            )
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

@Composable
private fun SettingsFolderCard(
    folder: SettingsFolderId,
    subtitle: String,
    onClick: () -> Unit
) {
    val materialUi = LocalMaterialUiEnabled.current
    val folderBg = if (materialUi) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = folderBg,
        tonalElevation = if (materialUi) 1.dp else 0.dp,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp)
            .testTag(folder.testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = folder.color.copy(alpha = 0.14f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = folderIconFor(folder),
                        contentDescription = null,
                        tint = folder.color,
                        modifier = Modifier.size(AppIcons.scaledSize(22.dp))
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
                        text = folder.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = "${folder.itemCount}",
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
                imageVector = AppIcons.chevronRight(),
                contentDescription = "Open ${folder.title}",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsFolderDetailScreen(
    folder: SettingsFolderId,
    settings: AppSettings,
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    hasFullStorageAccess: Boolean,
    dotFolderDocCount: Int,
    countsByType: Map<DocType, Int>,
    cacheSizeBytes: Long,
    onSetUseMaterialUi: (Boolean) -> Unit,
    onSetUseMaterialIcons: (Boolean) -> Unit,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDocTypeVisible: (DocType, Boolean) -> Unit,
    onSetScanDotFolders: (Boolean) -> Unit,
    onSetAutoHideReaderUi: (Boolean) -> Unit,
    onSetAutoCleanRenderCache: (Boolean) -> Unit,
    onSetPdfPageViewMode: (PageViewMode) -> Unit,
    onSetPdfViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetPdfInvertInDarkMode: (Boolean) -> Unit,
    onSetPdfDoubleTapZoom: (Float) -> Unit,
    onSetDocxRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetDocxPageViewMode: (PageViewMode) -> Unit,
    onSetDocxViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetDocxFontSizeSp: (Float) -> Unit,
    onSetXlsxRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetXlsxPageViewMode: (PageViewMode) -> Unit,
    onSetXlsxViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowExcelGridlines: (Boolean) -> Unit,
    onSetXlsxFontSizeSp: (Float) -> Unit,
    onSetPptRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetPptPageViewMode: (PageViewMode) -> Unit,
    onSetPptViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowPptSpeakerNotes: (Boolean) -> Unit,
    onSetTextRenderFormat: (OfficeRenderFormat) -> Unit,
    onSetTextPageViewMode: (PageViewMode) -> Unit,
    onSetTextViewOrientation: (DocumentViewOrientation) -> Unit,
    onSetShowLineNumbers: (Boolean) -> Unit,
    onSetTextWordWrap: (Boolean) -> Unit,
    onSetTextMonospace: (Boolean) -> Unit,
    onSetTextFontSizeSp: (Float) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onPickCustomFolder: () -> Unit,
    onRemoveCustomFolder: (String) -> Unit,
    onImportFile: () -> Unit,
    onRescanNow: () -> Unit,
    onClearCache: () -> Unit
) {
    val materialUi = LocalMaterialUiEnabled.current
    val cardBg = if (materialUi) {
        MaterialTheme.colorScheme.surfaceContainerLow
    } else {
        MaterialTheme.colorScheme.surface
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("${folder.testTag}_detail"),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "folder_content_${folder.name}") {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = cardBg,
                tonalElevation = if (materialUi) 1.dp else 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 700.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (folder) {
                        SettingsFolderId.PDF -> {
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

                        SettingsFolderId.DOCX -> {
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

                        SettingsFolderId.XLSX -> {
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

                        SettingsFolderId.PPT -> {
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

                        SettingsFolderId.TEXT -> {
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

                        SettingsFolderId.DOC_VISIBILITY -> {
                            Text(
                                text = "Hiding a pill only removes it from the home screen. Opening files from a file manager or other apps and all reader features stay enabled.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider()
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

                        SettingsFolderId.APPEARANCE -> {
                            SettingsSwitchRow(
                                title = "Material UI",
                                subtitle = "Use Material Design 3 dynamic colors and tonal surfaces",
                                checked = settings.useMaterialUi,
                                onCheckedChange = onSetUseMaterialUi,
                                modifier = Modifier.testTag("toggle_material_ui")
                            )
                            HorizontalDivider()
                            SettingsSwitchRow(
                                title = "Material icons",
                                subtitle = "Use rounded Material Design icons across bars, pills, cards, and menus",
                                checked = settings.useMaterialIcons,
                                onCheckedChange = onSetUseMaterialIcons,
                                modifier = Modifier.testTag("toggle_material_icons")
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

                        SettingsFolderId.STORAGE -> {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "Document Read & Write Permissions",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                PermissionStatusRow(
                                    title = "Read Documents",
                                    subtitle = "Scan and open PDF, DOC, EXCEL, PPT, and TEXT files",
                                    granted = hasReadPermission,
                                    modifier = Modifier.testTag("settings_read_permission_status")
                                )
                                PermissionStatusRow(
                                    title = "Write Documents",
                                    subtitle = "Rename, edit, and delete documents in device storage",
                                    granted = hasWritePermission,
                                    modifier = Modifier.testTag("settings_write_permission_status")
                                )
                            }
                            HorizontalDivider()
                            SettingsSwitchRow(
                                title = "Show hidden folder",
                                subtitle = "Automatically include hidden folders ($dotFolderDocCount found)",
                                checked = settings.scanDotFolders,
                                onCheckedChange = onSetScanDotFolders,
                                modifier = Modifier.testTag("toggle_show_hidden_folder")
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
                                            "Granted — read & write enabled across internal, external & hidden folders"
                                        } else {
                                            "Limited — grant read & write access for all folders"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                if (!hasFullStorageAccess) {
                                    Button(
                                        onClick = onRequestStorageAccess,
                                        modifier = Modifier.testTag("settings_grant_storage_button")
                                    ) { Text("Grant") }
                                } else {
                                    OutlinedButton(
                                        onClick = onRescanNow,
                                        modifier = Modifier.testTag("settings_rescan_button")
                                    ) {
                                        Icon(AppIcons.refresh(), contentDescription = null, modifier = Modifier.size(AppIcons.scaledSize(16.dp)))
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
                                    Icon(AppIcons.createNewFolder(), contentDescription = null, modifier = Modifier.size(AppIcons.scaledSize(16.dp)))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Add Folder")
                                }
                                OutlinedButton(onClick = onImportFile, modifier = Modifier.weight(1f)) {
                                    Icon(AppIcons.fileOpen(), contentDescription = null, modifier = Modifier.size(AppIcons.scaledSize(16.dp)))
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
                                                Icon(AppIcons.folder(), contentDescription = null, modifier = Modifier.size(AppIcons.scaledSize(16.dp)))
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
                                                        AppIcons.delete(),
                                                        contentDescription = "Remove folder",
                                                        modifier = Modifier.size(AppIcons.scaledSize(18.dp))
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        SettingsFolderId.CACHE -> {
                            SettingsSwitchRow(
                                title = "Auto-delete unnecessary cache",
                                subtitle = "Use compact lossless fast-open cache and automatically prune orphaned, expired & oversized files",
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
                text = "${type.label} Pill",
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = buildString {
                    append(extPreview)
                    append(" • ")
                    append(countLabel)
                    if (!checked) append(" • Hidden on Home")
                },
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

@Composable
private fun PermissionStatusRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeBg = if (granted) {
        ExcelPillColor.copy(alpha = 0.14f)
    } else {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    }
    val badgeTextColor = if (granted) {
        ExcelPillColor
    } else {
        MaterialTheme.colorScheme.onErrorContainer
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = RoundedCornerShape(50),
                color = badgeBg
            ) {
                Text(
                    text = if (granted) "Granted" else "Required",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeTextColor,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
