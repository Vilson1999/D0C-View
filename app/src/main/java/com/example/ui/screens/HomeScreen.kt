package com.example.ui.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.SortOption
import com.example.model.ThemeMode
import com.example.ui.components.AppIcons
import com.example.ui.components.CategoryPillsRow
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocViewerAppIcon
import com.example.ui.components.DocumentCard
import com.example.ui.components.DocumentInfoSheet
import com.example.ui.components.EmptyDocumentsIllustration
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.SortOptionsSheet
import com.example.ui.components.StoragePermissionDialog
import com.example.ui.components.TopBarSearchField
import com.example.ui.theme.LocalMaterialUiEnabled
import com.example.ui.theme.NavigationBarSideBackdrop
import com.example.ui.theme.SyncSystemBarsAppearance
import com.example.viewmodel.HomeUiState
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

enum class HomePageDisplayState {
    LOADING,
    EMPTY,
    DOCUMENTS
}

object HomeLoadingPolicy {
    const val EMPTY_WAIT_TIMEOUT_MS: Long = 5_000L

    fun resolvePageDisplayState(
        hasCategoryDocs: Boolean,
        isSearching: Boolean,
        isScanning: Boolean,
        isAccessGrantScanning: Boolean,
        isPullRefreshing: Boolean,
        emptyWaitElapsed: Boolean
    ): HomePageDisplayState {
        if (hasCategoryDocs && !isAccessGrantScanning) {
            return HomePageDisplayState.DOCUMENTS
        }
        if (isSearching && !isScanning && !isAccessGrantScanning) {
            return HomePageDisplayState.EMPTY
        }
        val waitingForDocuments = !isPullRefreshing && (isScanning || !emptyWaitElapsed)
        return if (isAccessGrantScanning || waitingForDocuments) {
            HomePageDisplayState.LOADING
        } else {
            HomePageDisplayState.EMPTY
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    uiState: HomeUiState,
    isDarkTheme: Boolean,
    onSelectCategory: (DocType) -> Unit,
    onCycleThemeMode: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onToggleSearch: () -> Unit,
    onCloseSearch: () -> Unit,
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
    onPullRefresh: () -> Unit,
    onShowAllDocTypes: () -> Unit,
    suppressPermissionDialog: Boolean = false,
    syncSystemBars: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val visibleTypes = uiState.visibleDocTypes
    var searchBarBounds by remember { mutableStateOf<Rect?>(null) }

    val materialUi = LocalMaterialUiEnabled.current
    val topBarColor by animateColorAsState(
        targetValue = if (uiState.isSelectionMode) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(220),
        label = "homeTopBarColor"
    )
    val selectionBottomBarColor = if (materialUi) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surface
    }
    val bottomBarColor by animateColorAsState(
        targetValue = if (uiState.isSelectionMode) {
            selectionBottomBarColor
        } else {
            MaterialTheme.colorScheme.background
        },
        animationSpec = tween(220),
        label = "homeBottomBarColor"
    )
    if (syncSystemBars && !suppressPermissionDialog) {
        SyncSystemBarsAppearance(
            statusBarColor = topBarColor,
            navigationBarColor = bottomBarColor
        )
    }

    LaunchedEffect(uiState.isSearchActive) {
        if (!uiState.isSearchActive) {
            searchBarBounds = null
        }
    }

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
                pagerState.animateScrollToPage(
                    page = targetIndex,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
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
    var permissionDialogDismissed by rememberSaveable { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<DocumentItem?>(null) }
    var deleteTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }
    var infoTargets by remember { mutableStateOf<List<DocumentItem>?>(null) }

    // Wait up to 5 seconds showing big loading circle before showing the "No documents found" logo
    val isHomeVisible = !suppressPermissionDialog
    var emptyWaitElapsed by rememberSaveable { mutableStateOf(false) }
    var wasAccessGrantScanning by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isAccessGrantScanning) {
        if (uiState.isAccessGrantScanning && !wasAccessGrantScanning) {
            emptyWaitElapsed = false
        }
        wasAccessGrantScanning = uiState.isAccessGrantScanning
    }

    LaunchedEffect(isHomeVisible, uiState.allDocumentsCount, emptyWaitElapsed) {
        if (!isHomeVisible) return@LaunchedEffect
        if (uiState.allDocumentsCount > 0) {
            if (emptyWaitElapsed) {
                emptyWaitElapsed = false
            }
            return@LaunchedEffect
        }
        if (!emptyWaitElapsed) {
            delay(HomeLoadingPolicy.EMPTY_WAIT_TIMEOUT_MS)
            emptyWaitElapsed = true
        }
    }

    if (uiState.isSelectionMode) {
        BackHandler { onClearSelection() }
    } else if (uiState.isSearchActive) {
        BackHandler { onToggleSearch() }
    }

    val counts = remember(uiState.documentsByType, visibleTypes) {
        visibleTypes.associateWith { uiState.documentsByType[it]?.size ?: 0 }
    }

    val currentOnCloseSearch by rememberUpdatedState(onCloseSearch)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(uiState.isSearchActive, uiState.isSelectionMode) {
                if (!uiState.isSearchActive || uiState.isSelectionMode) return@pointerInput
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
                                    currentOnCloseSearch()
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                Surface(
                    color = topBarColor,
                    tonalElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            .displayCutoutPadding()
                    ) {
                        AnimatedContent(
                            targetState = uiState.isSelectionMode,
                            transitionSpec = {
                                (slideInVertically(
                                    initialOffsetY = { -it / 3 },
                                    animationSpec = tween(220, easing = FastOutSlowInEasing)
                                ) + fadeIn(tween(200))).togetherWith(
                                    slideOutVertically(
                                        targetOffsetY = { -it / 3 },
                                        animationSpec = tween(160)
                                    ) + fadeOut(tween(150))
                                )
                            },
                            label = "homeTopBarModeSwitch"
                        ) { selectionMode ->
                            if (selectionMode) {
                                SelectionTopBar(
                                    selectedCount = uiState.selectedDocIds.size,
                                    onClear = onClearSelection,
                                    onSelectAll = onSelectAll
                                )
                            } else {
                                HomeTopBar(
                                    themeMode = uiState.settings.themeMode,
                                    isSearchActive = uiState.isSearchActive,
                                    searchQuery = uiState.searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    onCycleThemeMode = onCycleThemeMode,
                                    onOpenSort = { showSortSheet = true },
                                    onOpenSearch = onToggleSearch,
                                    onCloseSearch = onCloseSearch,
                                    onSearchBarBoundsChange = { searchBarBounds = it },
                                    onOpenSettings = onOpenSettings
                                )
                            }
                        }
                    }
                }

                // Category Pills Row (PDF, DOC, EXCEL, PPT, TEXT) — hides when scrolling up
                CategoryPillsRow(
                    selectedType = uiState.selectedCategory,
                    visibleTypes = visibleTypes,
                    counts = counts,
                    visible = pillsVisible,
                    isDarkTheme = isDarkTheme,
                    onSelectType = onSelectCategory,
                    modifier = Modifier
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                        .displayCutoutPadding()
                )
            }
        },
        bottomBar = {
            AnimatedContent(
                targetState = uiState.isSelectionMode,
                transitionSpec = {
                    (slideInVertically(
                        initialOffsetY = { it / 2 },
                        animationSpec = tween(200, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(180))).togetherWith(
                        slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(150)
                        ) + fadeOut(tween(140))
                    )
                },
                label = "homeBottomBarSwitch"
            ) { selectionMode ->
                if (selectionMode) {
                    SelectionBottomToolbar(
                        containerColor = selectionBottomBarColor,
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
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bottomBarColor)
                            .windowInsetsBottomHeight(WindowInsets.navigationBars)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                .displayCutoutPadding()
                .clipToBounds()
                .nestedScroll(nestedScrollConnection),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = !uiState.hasFullStorageAccess && permissionDialogDismissed && !uiState.isSelectionMode,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    StoragePermissionBannerCard(
                        hasReadPermission = uiState.hasDocumentReadPermission,
                        hasWritePermission = uiState.hasDocumentWritePermission,
                        onGrantClick = onRequestStorageAccess,
                        modifier = Modifier
                            .widthIn(max = 720.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }

                if (visibleTypes.isEmpty()) {
                    AllDocTypesHiddenState(
                        onShowAll = onShowAllDocTypes,
                        onOpenSettings = onOpenSettings
                    )
                } else {
                HorizontalPager(
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 720.dp)
                        .testTag("category_pager")
                ) { pageIndex ->
                    val pageType = visibleTypes.getOrNull(pageIndex) ?: return@HorizontalPager
                    val docs = uiState.documentsByType[pageType].orEmpty()
                    val pullToRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = uiState.isPullRefreshing,
                    onRefresh = onPullRefresh,
                    state = pullToRefreshState,
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = uiState.isPullRefreshing,
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            color = pageType.pillColor,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val offset = ((pagerState.currentPage - pageIndex) +
                                pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                            val pageScale = 1f - (0.035f * offset)
                            scaleX = pageScale
                            scaleY = pageScale
                            alpha = 1f - (0.28f * offset)
                        }
                        .testTag("document_pull_refresh_${pageType.name.lowercase()}")
                ) {
                    val displayState = HomeLoadingPolicy.resolvePageDisplayState(
                        hasCategoryDocs = docs.isNotEmpty(),
                        isSearching = uiState.searchQuery.isNotBlank(),
                        isScanning = uiState.isScanning,
                        isAccessGrantScanning = uiState.isAccessGrantScanning,
                        isPullRefreshing = uiState.isPullRefreshing,
                        emptyWaitElapsed = emptyWaitElapsed
                    )

                    AnimatedContent(
                        targetState = displayState,
                        transitionSpec = {
                            (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                                scaleIn(initialScale = 0.96f, animationSpec = tween(240)))
                                .togetherWith(fadeOut(tween(160)))
                        },
                        label = "homePageDisplayState"
                    ) { state ->
                        when (state) {
                            HomePageDisplayState.LOADING -> {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .testTag("home_center_loading_${pageType.name.lowercase()}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(18.dp),
                                        modifier = Modifier.padding(24.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            color = pageType.pillColor,
                                            trackColor = pageType.pillColor.copy(alpha = 0.16f),
                                            strokeWidth = 6.5.dp,
                                            strokeCap = StrokeCap.Round,
                                            modifier = Modifier.size(88.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.loading_documents),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            textAlign = TextAlign.Center
                                        )
                                    }
                                }
                            }
                            HomePageDisplayState.EMPTY -> {
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
                                                isSearching = uiState.searchQuery.isNotBlank()
                                            )
                                        }
                                    }
                                }
                            }
                            HomePageDisplayState.DOCUMENTS -> {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    contentPadding = PaddingValues(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = 0.dp,
                                        bottom = if (uiState.isSelectionMode) 20.dp else 28.dp
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
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
                                    onDelete = { deleteTargets = listOf(doc) },
                                    modifier = Modifier.animateItem(
                                        fadeInSpec = tween(180),
                                        placementSpec = spring(
                                            dampingRatio = 0.85f,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        fadeOutSpec = tween(140)
                                    )
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
        }
    }

    NavigationBarSideBackdrop(color = bottomBarColor)

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

    if (!suppressPermissionDialog && !uiState.hasFullStorageAccess && !permissionDialogDismissed) {
        StoragePermissionDialog(
            onAllow = onRequestStorageAccess,
            onDismiss = { permissionDialogDismissed = true }
        )
    }
    }
}

@Composable
private fun HomeTopBar(
    themeMode: ThemeMode,
    isSearchActive: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onCycleThemeMode: () -> Unit,
    onOpenSort: () -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSearchBarBoundsChange: (Rect) -> Unit,
    onOpenSettings: () -> Unit
) {
    AnimatedContent(
        targetState = isSearchActive,
        transitionSpec = {
            if (targetState) {
                (expandHorizontally(
                    expandFrom = Alignment.End,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) + fadeIn(tween(200))).togetherWith(
                    fadeOut(tween(140)) + scaleOut(targetScale = 0.96f, animationSpec = tween(140))
                )
            } else {
                (fadeIn(tween(200)) + scaleIn(initialScale = 0.96f, animationSpec = tween(200))).togetherWith(
                    shrinkHorizontally(
                        shrinkTowards = Alignment.End,
                        animationSpec = tween(180, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(150))
                )
            }
        },
        label = "homeTopBarSearchSwitch"
    ) { searchOpen ->
        if (searchOpen) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 0.dp)
                    .onGloballyPositioned { onSearchBarBoundsChange(it.boundsInRoot()) }
            ) {
                TopBarSearchField(
                    query = searchQuery,
                    placeholder = "Search documents…",
                    onQueryChange = onSearchQueryChange,
                    onClose = onCloseSearch,
                    inputTestTag = "search_input",
                    closeTestTag = "topbar_search_close_button"
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 2.dp, top = 0.dp, bottom = 0.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DocViewerAppIcon(
                    size = 30.dp,
                    modifier = Modifier
                        .padding(end = 6.dp)
                        .testTag("topbar_app_icon")
                )
                Spacer(modifier = Modifier.weight(1f))

                // Right side: Theme (Auto/Dark/Light), Sort, Search, Settings
                IconButton(
                    onClick = onCycleThemeMode,
                    modifier = Modifier.testTag("topbar_theme_toggle")
                ) {
                    AnimatedContent(
                        targetState = themeMode,
                        transitionSpec = {
                            (scaleIn(
                                initialScale = 0.65f,
                                animationSpec = spring(
                                    dampingRatio = 0.65f,
                                    stiffness = Spring.StiffnessMedium
                                )
                            ) + fadeIn(tween(180))).togetherWith(
                                scaleOut(targetScale = 0.65f, animationSpec = tween(120)) +
                                    fadeOut(tween(120))
                            )
                        },
                        label = "themeIconSwitch"
                    ) { mode ->
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.SYSTEM -> AppIcons.BrightnessAuto
                                ThemeMode.DARK -> AppIcons.DarkMode
                                ThemeMode.LIGHT -> AppIcons.LightMode
                            },
                            contentDescription = stringResource(R.string.action_toggle_theme),
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
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
                    onClick = onOpenSearch,
                    modifier = Modifier.testTag("topbar_search_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Search,
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
    }
}

@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    onClear: () -> Unit,
    onSelectAll: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp, vertical = 0.dp),
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
    containerColor: Color,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onPrint: () -> Unit,
    onInfo: () -> Unit
) {
    Surface(
        color = containerColor,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(
                    WindowInsets.navigationBars.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                    )
                )
                .displayCutoutPadding()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
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
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "bottomToolbarActionScale"
    )
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                onClick = onClick
            )
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint
        )
    }
}

@Composable
private fun EmptyCategoryState(
    docType: DocType,
    isSearching: Boolean
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
            modifier = Modifier
                .widthIn(max = 360.dp)
                .testTag("empty_category_state_${docType.name.lowercase()}")
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
                text = "All document pills are hidden",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Text(
                text = "PDF, DOC, EXCEL, PPT, and TEXT pills are hidden on Home. Files still open from file managers or other apps.",
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

@Composable
private fun StoragePermissionBannerCard(
    hasReadPermission: Boolean,
    hasWritePermission: Boolean,
    onGrantClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_storage_permission_banner")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppIcons.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = stringResource(R.string.grant_storage_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Read: ${if (hasReadPermission) "Granted" else "Needed"} • Write: ${if (hasWritePermission) "Granted" else "Needed"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = onGrantClick,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .testTag("home_grant_storage_button")
            ) {
                Text(stringResource(R.string.grant_storage_button))
            }
        }
    }
}
