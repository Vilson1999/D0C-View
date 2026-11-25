package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.SortOption
import com.example.ui.components.AppIcons
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentCard
import com.example.ui.components.DocumentInfoSheet
import com.example.ui.components.EmptyDocumentsIllustration
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.SortOptionsSheet
import com.example.ui.components.TopBarSearchField
import com.example.viewmodel.DocumentQueryProcessor

/**
 * Displays the complete scanned list of documents in a single LazyColumn,
 * with distinct color-coded icons representing each document file type.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannedDocumentsScreen(
    documents: List<DocumentItem>,
    visibleTypes: List<DocType>,
    isScanning: Boolean,
    isDarkTheme: Boolean,
    showDotFolderBadge: Boolean,
    sortOption: SortOption,
    onBack: () -> Unit,
    onOpenDocument: (DocumentItem) -> Unit,
    onRescan: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onRenameDocument: (DocumentItem, String) -> Unit,
    onDeleteDocuments: (List<DocumentItem>) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }
    var showSortSheet by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }
    var infoTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }

    LaunchedEffect(searchActive) {
        if (!searchActive) searchBarBounds = null
    }

    BackHandler {
        if (searchActive) {
            searchActive = false
            searchQuery = ""
        } else {
            onBack()
        }
    }

    val filteredDocuments = remember(documents, searchQuery, sortOption, visibleTypes) {
        DocumentQueryProcessor.filterAndSort(
            documents = documents,
            query = searchQuery,
            sortOption = sortOption,
            visibleTypes = visibleTypes.toSet()
        )
    }

    val pullToRefreshState = rememberPullToRefreshState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(searchActive) {
                if (!searchActive) return@pointerInput
                awaitPointerEventScope {
                    var downPos: Offset? = null
                    var isTapCandidate = false
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Final)
                        val change = event.changes.firstOrNull() ?: continue
                        when (event.type) {
                            PointerEventType.Press -> {
                                val bounds = searchBarBounds
                                if (bounds == null || !bounds.contains(change.position)) {
                                    downPos = change.position
                                    isTapCandidate = true
                                } else {
                                    downPos = null
                                    isTapCandidate = false
                                }
                            }
                            PointerEventType.Move -> {
                                val start = downPos
                                if (isTapCandidate && start != null) {
                                    if ((change.position - start).getDistance() > viewConfiguration.touchSlop) {
                                        isTapCandidate = false
                                    }
                                }
                            }
                            PointerEventType.Release -> {
                                if (isTapCandidate && event.changes.none { it.pressed }) {
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    searchActive = false
                                    searchQuery = ""
                                }
                                downPos = null
                                isTapCandidate = false
                            }
                            else -> Unit
                        }
                    }
                }
            }
    ) {
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                tonalElevation = 1.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    if (searchActive) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .onGloballyPositioned { searchBarBounds = it.boundsInRoot() }
                        ) {
                            TopBarSearchField(
                                query = searchQuery,
                                placeholder = "Search scanned files…",
                                onQueryChange = { searchQuery = it },
                                onClose = {
                                    searchActive = false
                                    searchQuery = ""
                                },
                                inputTestTag = "scanned_search_input",
                                closeTestTag = "scanned_search_close_button"
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("scanned_back_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.scanned_documents_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${filteredDocuments.size} of ${documents.size} scanned files",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            IconButton(
                                onClick = { showSortSheet = true },
                                modifier = Modifier.testTag("scanned_sort_button")
                            ) {
                                Icon(
                                    imageVector = AppIcons.Sort,
                                    contentDescription = stringResource(R.string.action_sort)
                                )
                            }

                            IconButton(
                                onClick = { searchActive = true },
                                modifier = Modifier.testTag("scanned_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = stringResource(R.string.action_search)
                                )
                            }

                            IconButton(
                                onClick = onRescan,
                                modifier = Modifier.testTag("scanned_refresh_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Rescan documents"
                                )
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            PullToRefreshBox(
                isRefreshing = isScanning,
                onRefresh = onRescan,
                state = pullToRefreshState,
                indicator = {
                    PullToRefreshDefaults.Indicator(
                        state = pullToRefreshState,
                        isRefreshing = isScanning,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .testTag("scanned_pull_refresh")
            ) {
                if (filteredDocuments.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(24.dp)
                    ) {
                        item(key = "scanned_empty_state") {
                            Box(
                                modifier = Modifier.fillParentMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.widthIn(max = 360.dp)
                                ) {
                                    EmptyDocumentsIllustration(
                                        accentColor = MaterialTheme.colorScheme.primary,
                                        isHiddenState = false,
                                        size = 156.dp
                                    )
                                    Text(
                                        text = if (searchQuery.isNotBlank()) {
                                            "No matching scanned documents"
                                        } else {
                                            "No scanned documents yet"
                                        },
                                        style = MaterialTheme.typography.titleMedium,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Pull down or tap Rescan to refresh your offline document list.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            if (searchQuery.isNotBlank()) {
                                                searchQuery = ""
                                            } else {
                                                onRescan()
                                            }
                                        },
                                        modifier = Modifier.testTag("scanned_empty_action_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Refresh,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            if (searchQuery.isNotBlank()) "Clear Search" else "Rescan"
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("scanned_documents_lazy_column"),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 10.dp,
                            bottom = 28.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filteredDocuments, key = { it.id }) { doc ->
                            DocumentCard(
                                document = doc,
                                isSelected = false,
                                isSelectionMode = false,
                                isDarkTheme = isDarkTheme,
                                showDotFolderBadge = showDotFolderBadge,
                                onClick = { onOpenDocument(doc) },
                                onLongClick = { infoTargets = listOf(doc) },
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

    if (showSortSheet) {
        SortOptionsSheet(
            currentOption = sortOption,
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
}
