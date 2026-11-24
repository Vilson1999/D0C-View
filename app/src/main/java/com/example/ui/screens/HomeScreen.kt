package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.SortOption
import com.example.ui.components.AppIcons
import com.example.ui.components.CategoryPillsRow
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocViewerAppIcon
import com.example.ui.components.DocumentCard
import com.example.ui.components.DocumentInfoSheet
import com.example.ui.components.EmptyDocumentsIllustration
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.SortOptionsSheet
import com.example.viewmodel.HomeUiState
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isDarkTheme: Boolean,
    onSelectCategory: (DocType) -> Unit,
    onToggleDarkLight: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDocument: (DocumentItem) -> Unit,
    onToggleSelection: (String) -> Unit,
    onStartSelection: (String) -> Unit,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onRenameDocument: (DocumentItem, String) -> Unit,
    onDeleteDocuments: (List<DocumentItem>) -> Unit,
    onRequestStorageAccess: () -> Unit,
    onImportFile: () -> Unit,
    onRescan: () -> Unit,
    onShowAllDocTypes: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val visibleTypes = uiState.visibleDocTypes

    val pagerState = rememberPagerState(
        initialPage = visibleTypes.indexOf(uiState.selectedCategory).coerceAtLeast(0),
        pageCount = { visibleTypes.size.coerceAtLeast(1) }
    )

    // Keep latest state/callback references inside long-lived pager effects
    val currentSelectedCategory by rememberUpdatedState(uiState.selectedCategory)
    val currentVisibleTypes by rememberUpdatedState(visibleTypes)
    val currentOnSelectCategory by rememberUpdatedState(onSelectCategory)
    var programmaticTargetIndex by remember { mutableStateOf<Int?>(null) }

    // Collapse pills row when scrolling up (scrolling down through document list)
    var pillsVisible by remember { mutableStateOf(true) }

    // 1. Sync manual pager swipes -> selected category pill index
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                val types = currentVisibleTypes
                if (types.isEmpty()) return@collect
                val targetIdx = programmaticTargetIndex
                if (targetIdx != null) {
                    if (page == targetIdx) {
                        programmaticTargetIndex = null
                    }
                    return@collect
                }
                val targetType = types.getOrNull(page) ?: return@collect
                if (targetType != currentSelectedCategory) {
                    currentOnSelectCategory(targetType)
                }
            }
    }

    // Reconcile settled page when any manual drag, fling, or interrupted animation stops
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (!scrolling) {
                    programmaticTargetIndex = null
                    val settledType = currentVisibleTypes.getOrNull(pagerState.settledPage)
                    if (settledType != null && settledType != currentSelectedCategory) {
                        currentOnSelectCategory(settledType)
                    }
                }
            }
    }

    // 2. Sync pill clicks / visibility changes / external category updates -> pager
    LaunchedEffect(uiState.selectedCategory, visibleTypes) {
        if (visibleTypes.isEmpty()) return@LaunchedEffect
        pillsVisible = true
        val targetIndex = visibleTypes.indexOf(uiState.selectedCategory)
        if (targetIndex >= 0 && pagerState.currentPage != targetIndex) {
            programmaticTargetIndex = targetIndex
            try {
                pagerState.animateScrollToPage(targetIndex)
            } finally {
                if (programmaticTargetIndex == targetIndex) {
                    programmaticTargetIndex = null
                }
            }
        }
    }

    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -8f) {
                    pillsVisible = false
                } else if (available.y > 8f) {
                    pillsVisible = true
                }
                return Offset.Zero
            }
        }
    }

    // Dialog & sheet state
    var showSortSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }
    var infoTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }

    if (uiState.isSelectionMode) {
        BackHandler { onClearSelection() }
    } else if (uiState.isSearchActive) {
        BackHandler { onToggleSearch() }
    }

    val counts = remember(uiState.documentsByType, visibleTypes) {
        visibleTypes.associateWith { uiState.documentsByType[it]?.size ?: 0 }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                if (uiState.isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = uiState.selectedDocIds.size,
                        onClear = onClearSelection,
                        onSelectAll = onSelectAll
                    )
                } else {
                    HomeTopBar(
                        totalDocs = uiState.allDocumentsCount,
                        isDarkTheme = isDarkTheme,
                        isSearchActive = uiState.isSearchActive,
                        onToggleDarkLight = onToggleDarkLight,
                        onOpenSort = { showSortSheet = true },
                        onToggleSearch = onToggleSearch,
                        onOpenSettings = onOpenSettings
                    )
                }

                // Expandable search bar
                AnimatedVisibility(
                    visible = uiState.isSearchActive && !uiState.isSelectionMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = { Text("Search name, folder, or .dot path…") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        trailingIcon = {
                            if (uiState.searchQuery.isNotEmpty()) {
                                IconButton(onClick = { onSearchQueryChange("") }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "Clear search")
                                }
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                            .testTag("search_input")
                    )
                }

                // Category Pills Row (PDF, DOC, XML, PPT, TEXT) — hides when scrolling up
                CategoryPillsRow(
                    selectedType = uiState.selectedCategory,
                    visibleTypes = visibleTypes,
                    counts = counts,
                    visible = pillsVisible,
                    isDarkTheme = isDarkTheme,
                    onSelectType = onSelectCategory
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = expandVertically(expandFrom = Alignment.Bottom) + fadeIn(),
                exit = shrinkVertically(shrinkTowards = Alignment.Bottom) + fadeOut()
            ) {
                SelectionBottomToolbar(
                    onShare = {
                        DocumentActionsHelper.shareDocuments(context, uiState.selectedDocuments)
                    },
                    onDelete = {
                        val selected = uiState.selectedDocuments
                        if (selected.isNotEmpty()) deleteTargets = selected
                    },
                    onPrint = {
                        val selected = uiState.selectedDocuments
                        if (activity != null && selected.isNotEmpty()) {
                            DocumentActionsHelper.printDocuments(activity, selected)
                        }
                    },
                    onInfo = {
                        val selected = uiState.selectedDocuments
                        if (selected.isNotEmpty()) infoTargets = selected
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .nestedScroll(nestedScrollConnection),
            contentAlignment = Alignment.TopCenter
        ) {
            if (visibleTypes.isEmpty()) {
                AllDocTypesHiddenState(
                    onShowAll = onShowAllDocTypes,
                    onOpenSettings = onOpenSettings
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .testTag("category_pager")
                ) { pageIndex ->
                    val pageType = visibleTypes.getOrNull(pageIndex) ?: return@HorizontalPager
                    val docs = uiState.documentsByType[pageType].orEmpty()
                    val pullToRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = uiState.isScanning,
                    onRefresh = onRescan,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = uiState.isScanning,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            color = pageType.pillColor,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("document_pull_refresh_${pageType.name.lowercase()}")
                ) {
                    if (docs.isEmpty()) {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            item(key = "empty_category_state") {
                                Box(
                                    modifier = Modifier.fillParentMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    EmptyCategoryState(
                                        docType = pageType,
                                        hasFullStorageAccess = uiState.hasFullStorageAccess,
                                        isSearching = uiState.searchQuery.isNotBlank(),
                                        onRequestStorageAccess = onRequestStorageAccess,
                                        onImportFile = onImportFile,
                                        onRescan = onRescan
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = if (uiState.isSelectionMode) 20.dp else 28.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (!uiState.hasFullStorageAccess) {
                                item(key = "storage_access_banner") {
                                    StorageAccessCard(
                                        onGrantAccess = onRequestStorageAccess,
                                        onImportFile = onImportFile
                                    )
                                }
                            }

                            items(docs, key = { it.id }) { doc ->
                                val isSelected = doc.id in uiState.selectedDocIds
                                DocumentCard(
                                    document = doc,
                                    isSelected = isSelected,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isDarkTheme = isDarkTheme,
                                    showDotFolderBadge = uiState.settings.showDotFolderBadge,
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            onToggleSelection(doc.id)
                                        } else {
                                            onOpenDocument(doc)
                                        }
                                    },
                                    onLongClick = {
                                        if (uiState.isSelectionMode) {
                                            onToggleSelection(doc.id)
                                        } else {
                                            onStartSelection(doc.id)
                                        }
                                    },
                                    onRename = { renameTarget = doc },
                                    onShare = {
                                        DocumentActionsHelper.shareDocuments(context, listOf(doc))
                                    },
                                    onPrint = {
                                        if (activity != null) {
                                            DocumentActionsHelper.printDocuments(activity, listOf(doc))
                                        }
                                    },
                                    onInfo = { infoTargets = listOf(doc) },
                                    onDelete = { deleteTargets = listOf(doc) }
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }

    if (showSortSheet) {
        SortOptionsSheet(
            currentOption = uiState.settings.sortOption,
            onSelect = onSelectSort,
            onDismiss = { showSortSheet = false }
        )
    }

    renameTarget?.let { target ->
        RenameDocumentDialog(
            document = target,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                onRenameDocument(target, newName)
                renameTarget = null
            }
        )
    }

    deleteTargets?.let { targets ->
        DeleteConfirmDialog(
            docs = targets,
            onDismiss = { deleteTargets = null },
            onConfirm = {
                onDeleteDocuments(targets)
                deleteTargets = null
            }
        )
    }

    infoTargets?.let { targets ->
        DocumentInfoSheet(
            docs = targets,
            onDismiss = { infoTargets = null }
        )
    }
}

@Composable
private fun HomeTopBar(
    totalDocs: Int,
    isDarkTheme: Boolean,
    isSearchActive: Boolean,
    onToggleDarkLight: () -> Unit,
    onOpenSort: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocViewerAppIcon(
            size = 38.dp,
            modifier = Modifier
                .padding(end = 10.dp)
                .testTag("topbar_app_icon")
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = if (totalDocs == 1) "1 offline document" else "$totalDocs offline documents",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Right side: Dark/Light, Sort, Search, Settings
        IconButton(
            onClick = onToggleDarkLight,
            modifier = Modifier.testTag("topbar_theme_toggle")
        ) {
            Icon(
                imageVector = if (isDarkTheme) AppIcons.LightMode else AppIcons.DarkMode,
                contentDescription = stringResource(R.string.action_toggle_theme),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = onOpenSort,
            modifier = Modifier.testTag("topbar_sort_button")
        ) {
            Icon(
                imageVector = AppIcons.Sort,
                contentDescription = stringResource(R.string.action_sort),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.testTag("topbar_search_button")
        ) {
            Icon(
                imageVector = if (isSearchActive) Icons.Rounded.Close else Icons.Rounded.Search,
                contentDescription = stringResource(R.string.action_search),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        IconButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("topbar_settings_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Settings,
                contentDescription = stringResource(R.string.action_settings),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClear,
                modifier = Modifier.testTag("selection_clear_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_clear_selection)
                )
            }
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp)
            )
            IconButton(
                onClick = onSelectAll,
                modifier = Modifier.testTag("selection_select_all_button")
            ) {
                Icon(
                    imageVector = AppIcons.SelectAll,
                    contentDescription = stringResource(R.string.action_select_all)
                )
            }
        }
    }
}

@Composable
private fun SelectionBottomToolbar(
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomToolbarAction(
                icon = Icons.Outlined.Share,
                label = stringResource(R.string.action_share),
                onClick = onShare,
                modifier = Modifier.testTag("bottom_share_button")
            )
            BottomToolbarAction(
                icon = AppIcons.DeleteOutline,
                label = stringResource(R.string.action_delete),
                tint = MaterialTheme.colorScheme.error,
                onClick = onDelete,
                modifier = Modifier.testTag("bottom_delete_button")
            )
            BottomToolbarAction(
                icon = AppIcons.Print,
                label = stringResource(R.string.action_print),
                onClick = onPrint,
                modifier = Modifier.testTag("bottom_print_button")
            )
            BottomToolbarAction(
                icon = Icons.Outlined.Info,
                label = stringResource(R.string.action_info),
                onClick = onInfo,
                modifier = Modifier.testTag("bottom_info_button")
            )
        }
    }
}

@Composable
private fun BottomToolbarAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun StorageAccessCard(
    onGrantAccess: () -> Unit,
    onImportFile: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = AppIcons.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = stringResource(R.string.grant_storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = stringResource(R.string.grant_storage_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onGrantAccess,
                    modifier = Modifier.testTag("grant_storage_button")
                ) {
                    Text(stringResource(R.string.grant_storage_button))
                }
                OutlinedButton(
                    onClick = onImportFile,
                    modifier = Modifier.testTag("import_file_button")
                ) {
                    Icon(
                        AppIcons.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Import")
                }
            }
        }
    }
}

@Composable
private fun EmptyCategoryState(
    docType: DocType,
    hasFullStorageAccess: Boolean,
    isSearching: Boolean,
    onRequestStorageAccess: () -> Unit,
    onImportFile: () -> Unit,
    onRescan: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            EmptyDocumentsIllustration(
                accentColor = docType.pillColor,
                isHiddenState = false,
                size = 168.dp
            )
            Text(
                text = if (isSearching) {
                    "No matching ${docType.label} files"
                } else {
                    stringResource(R.string.empty_category_title, docType.label)
                },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.empty_category_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (!hasFullStorageAccess) {
                    Button(onClick = onRequestStorageAccess) {
                        Text(stringResource(R.string.grant_storage_button))
                    }
                } else {
                    Button(onClick = onRescan) {
                        Icon(
                            Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Rescan")
                    }
                }
                OutlinedButton(onClick = onImportFile) {
                    Icon(
                        AppIcons.FileOpen,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Open File")
                }
            }
        }
    }
}

@Composable
private fun AllDocTypesHiddenState(
    onShowAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 360.dp)
        ) {
            EmptyDocumentsIllustration(
                accentColor = MaterialTheme.colorScheme.primary,
                isHiddenState = true,
                size = 156.dp
            )
            Text(
                text = "All document types are hidden",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "PDF, DOC, XML, PPT, and TEXT are currently turned off in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onShowAll,
                    modifier = Modifier.testTag("show_all_doc_types_button")
                ) {
                    Text("Show All")
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.testTag("hidden_open_settings_button")
                ) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Settings")
                }
            }
        }
    }
}
