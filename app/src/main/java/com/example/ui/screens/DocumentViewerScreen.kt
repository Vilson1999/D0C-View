package com.example.ui.screens

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.data.DocumentParser
import com.example.data.ParsedDocument
import com.example.data.PdfDocumentSession
import com.example.data.SlidePage
import com.example.data.WordPage
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.PageViewMode
import com.example.ui.components.AppIcons
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentActionDropdownMenu
import com.example.ui.components.DocumentInfoSheet
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.XmlAttrDark
import com.example.ui.theme.XmlAttrLight
import com.example.ui.theme.XmlCommentDark
import com.example.ui.theme.XmlCommentLight
import com.example.ui.theme.XmlTagDark
import com.example.ui.theme.XmlTagLight
import com.example.ui.theme.XmlValueDark
import com.example.ui.theme.XmlValueLight
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun DocumentViewerScreen(
    document: DocumentItem,
    settings: AppSettings,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onRenameDocument: (DocumentItem, String, (DocumentItem) -> Unit) -> Unit,
    onDeleteDocument: (DocumentItem, () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val parser = remember(context) { DocumentParser(context) }

    var currentDoc by remember(document.id) { mutableStateOf(document) }
    var parsedDoc by remember(document.id) { mutableStateOf<ParsedDocument?>(null) }
    var pageViewMode by remember(settings.defaultPageViewMode) {
        mutableStateOf(settings.defaultPageViewMode)
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    var isReaderUiVisible by remember(document.id) { mutableStateOf(true) }
    var uiInteractionNonce by remember(document.id) { mutableIntStateOf(0) }

    val parseKey = when (currentDoc.type) {
        DocType.DOC -> "${currentDoc.id}|${currentDoc.uriString}|${settings.readerFontSizeSp.toInt()}"
        DocType.XML -> "${currentDoc.id}|${currentDoc.uriString}|${settings.readerFontSizeSp.toInt()}|${settings.xmlSyntaxHighlight}|${settings.showLineNumbers}"
        else -> "${currentDoc.id}|${currentDoc.uriString}"
    }

    LaunchedEffect(parseKey) {
        parsedDoc = null
        isReaderUiVisible = true
        parsedDoc = parser.parse(currentDoc, settings)
    }

    LaunchedEffect(settings.autoHideReaderUi) {
        if (!settings.autoHideReaderUi) {
            isReaderUiVisible = true
        }
    }

    val hasOpenOverlay = menuExpanded || showRenameDialog || showDeleteDialog || showInfoSheet
    LaunchedEffect(
        isReaderUiVisible,
        settings.autoHideReaderUi,
        parsedDoc,
        hasOpenOverlay,
        uiInteractionNonce
    ) {
        val loaded = parsedDoc != null && parsedDoc !is ParsedDocument.Error
        if (settings.autoHideReaderUi && isReaderUiVisible && loaded && !hasOpenOverlay) {
            delay(3000L)
            isReaderUiVisible = false
        }
    }

    val toggleReaderUi: () -> Unit = {
        if (!hasOpenOverlay) {
            isReaderUiVisible = !isReaderUiVisible
            uiInteractionNonce++
        }
    }

    DisposableEffect(parsedDoc) {
        val pdfSession = (parsedDoc as? ParsedDocument.Pdf)?.session
        onDispose { pdfSession?.close() }
    }

    BackHandler { onBack() }

    val topBarReserve = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 43.dp
    val navBarReserve = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .testTag("viewer_reader_surface"),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = parsedDoc,
                contentKey = { doc ->
                    when (doc) {
                        null -> "loading"
                        is ParsedDocument.Error -> "error_${doc.message}"
                        is ParsedDocument.Pdf -> "pdf_${currentDoc.id}"
                        is ParsedDocument.Word -> "word_${currentDoc.id}"
                        is ParsedDocument.Slides -> "slides_${currentDoc.id}"
                        is ParsedDocument.CodeXml -> "xml_${currentDoc.id}"
                        is ParsedDocument.PlainText -> "text_${currentDoc.id}"
                    }
                },
                transitionSpec = {
                    (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        scaleIn(initialScale = 0.985f, animationSpec = tween(240)))
                        .togetherWith(fadeOut(tween(150)))
                },
                label = "documentContentReveal"
            ) { doc ->
                when (doc) {
                    null -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                CircularProgressIndicator()
                                Text(
                                    text = "Opening ${currentDoc.name}…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    is ParsedDocument.Error -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = AppIcons.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = doc.message,
                                    style = MaterialTheme.typography.bodyLarge,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                    is ParsedDocument.Pdf -> PdfViewerContent(
                        session = doc.session,
                        mode = pageViewMode,
                        showPagePill = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        onToggleUi = toggleReaderUi
                    )
                    is ParsedDocument.Word -> PagedOrContinuousViewer(
                        items = doc.pages,
                        mode = pageViewMode,
                        unitLabel = "Page",
                        showPagePill = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        enableContainerTap = true,
                        onToggleUi = toggleReaderUi,
                        keySelector = { it.pageNumber },
                        singleItemContent = { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = topBarReserve + 12.dp,
                                        bottom = navBarReserve + 52.dp
                                    ),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                WordPaperCard(page = page, fontSizeSp = settings.readerFontSizeSp)
                            }
                        },
                        continuousItemContent = { _, page ->
                            WordPaperCard(page = page, fontSizeSp = settings.readerFontSizeSp)
                        }
                    )
                    is ParsedDocument.Slides -> PagedOrContinuousViewer(
                        items = doc.slides,
                        mode = pageViewMode,
                        unitLabel = "Slide",
                        showPagePill = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        enableContainerTap = true,
                        onToggleUi = toggleReaderUi,
                        keySelector = { it.slideNumber },
                        singleItemContent = { slide ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = topBarReserve + 12.dp,
                                        bottom = navBarReserve + 52.dp
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                SlideCard(
                                    slide = slide,
                                    totalSlides = doc.slides.size,
                                    accentColor = currentDoc.type.pillColor
                                )
                            }
                        },
                        continuousItemContent = { _, slide ->
                            SlideCard(
                                slide = slide,
                                totalSlides = doc.slides.size,
                                accentColor = currentDoc.type.pillColor
                            )
                        }
                    )
                    is ParsedDocument.CodeXml -> TextAndXmlViewerContent(
                        fullText = doc.formattedXml,
                        pages = doc.pages,
                        mode = pageViewMode,
                        isXml = true,
                        syntaxHighlight = settings.xmlSyntaxHighlight,
                        showLineNumbers = settings.showLineNumbers,
                        fontSizeSp = settings.readerFontSizeSp,
                        isDarkTheme = isDarkTheme,
                        showPagePill = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        onToggleUi = toggleReaderUi
                    )
                    is ParsedDocument.PlainText -> TextAndXmlViewerContent(
                        fullText = doc.fullText,
                        pages = doc.pages,
                        mode = pageViewMode,
                        isXml = false,
                        syntaxHighlight = false,
                        showLineNumbers = settings.showLineNumbers,
                        fontSizeSp = settings.readerFontSizeSp,
                        isDarkTheme = isDarkTheme,
                        showPagePill = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        onToggleUi = toggleReaderUi
                    )
                }
            }

            AnimatedVisibility(
                visible = isReaderUiVisible,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("viewer_topbar")
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 3.dp,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .pointerInput(Unit) {
                            detectTapGestures { uiInteractionNonce++ }
                        }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .height(43.dp)
                            .padding(start = 14.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = currentDoc.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("viewer_doc_title")
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        IconButton(
                            onClick = {
                                pageViewMode = if (pageViewMode == PageViewMode.CONTINUOUS) {
                                    PageViewMode.SINGLE_PAGE
                                } else {
                                    PageViewMode.CONTINUOUS
                                }
                                uiInteractionNonce++
                            },
                            modifier = Modifier.testTag("viewer_page_mode_button")
                        ) {
                            AnimatedContent(
                                targetState = pageViewMode,
                                transitionSpec = {
                                    (scaleIn(
                                        initialScale = 0.7f,
                                        animationSpec = spring(
                                            dampingRatio = 0.7f,
                                            stiffness = Spring.StiffnessMedium
                                        )
                                    ) + fadeIn(tween(180))).togetherWith(
                                        scaleOut(targetScale = 0.7f, animationSpec = tween(120)) +
                                            fadeOut(tween(120))
                                    )
                                },
                                label = "pageModeIconSwitch"
                            ) { currentMode ->
                                Icon(
                                    imageVector = if (currentMode == PageViewMode.SINGLE_PAGE) {
                                        AppIcons.ViewCarousel
                                    } else {
                                        AppIcons.ViewStream
                                    },
                                    contentDescription = stringResource(R.string.action_page_view_mode),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Box {
                            val menuIconRotation by animateFloatAsState(
                                targetValue = if (menuExpanded) 90f else 0f,
                                animationSpec = spring(
                                    dampingRatio = 0.75f,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "viewerMenuButtonRotation"
                            )
                            IconButton(
                                onClick = {
                                    menuExpanded = true
                                    uiInteractionNonce++
                                },
                                modifier = Modifier.testTag("viewer_more_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = stringResource(R.string.action_more_options),
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.graphicsLayer { rotationZ = menuIconRotation }
                                )
                            }

                            DocumentActionDropdownMenu(
                                expanded = menuExpanded,
                                onDismiss = {
                                    menuExpanded = false
                                    uiInteractionNonce++
                                },
                                onRename = { showRenameDialog = true },
                                onShare = { DocumentActionsHelper.shareDocuments(context, listOf(currentDoc)) },
                                onPrint = {
                                    if (activity != null) {
                                        DocumentActionsHelper.printDocuments(activity, listOf(currentDoc))
                                    }
                                },
                                onInfo = { showInfoSheet = true },
                                onDelete = { showDeleteDialog = true },
                                tagPrefix = "viewer_menu"
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRenameDialog) {
        RenameDocumentDialog(
            document = currentDoc,
            onDismiss = {
                showRenameDialog = false
                uiInteractionNonce++
            },
            onConfirm = { newName ->
                showRenameDialog = false
                uiInteractionNonce++
                onRenameDocument(currentDoc, newName) { updated -> currentDoc = updated }
            }
        )
    }

    if (showDeleteDialog) {
        DeleteConfirmDialog(
            docs = listOf(currentDoc),
            onDismiss = {
                showDeleteDialog = false
                uiInteractionNonce++
            },
            onConfirm = {
                showDeleteDialog = false
                onDeleteDocument(currentDoc) { onBack() }
            }
        )
    }

    if (showInfoSheet) {
        DocumentInfoSheet(
            docs = listOf(currentDoc),
            onDismiss = {
                showInfoSheet = false
                uiInteractionNonce++
            }
        )
    }
}

@Composable
private fun <T> PagedOrContinuousViewer(
    items: List<T>,
    mode: PageViewMode,
    unitLabel: String,
    showPagePill: Boolean,
    topClearance: androidx.compose.ui.unit.Dp,
    bottomClearance: androidx.compose.ui.unit.Dp,
    enableContainerTap: Boolean,
    onToggleUi: () -> Unit,
    keySelector: (T) -> Any,
    singleItemContent: @Composable (T) -> Unit,
    continuousItemContent: @Composable (Int, T) -> Unit
) {
    val containerModifier = if (enableContainerTap) {
        Modifier
            .fillMaxSize()
            .pointerInput(onToggleUi) {
                detectTapGestures(onTap = { onToggleUi() })
            }
    } else {
        Modifier.fillMaxSize()
    }

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(150)))
        },
        label = "viewerModeSwitch"
    ) { targetMode ->
        if (targetMode == PageViewMode.SINGLE_PAGE) {
            val total = items.size.coerceAtLeast(1)
            val pagerState = rememberPagerState(pageCount = { total })
            Box(modifier = containerModifier) {
                HorizontalPager(
                    state = pagerState,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ),
                    modifier = Modifier.fillMaxSize()
                ) { idx ->
                    if (idx in items.indices) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    val offset = ((pagerState.currentPage - idx) +
                                        pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                    val pageScale = 1f - (0.045f * offset)
                                    scaleX = pageScale
                                    scaleY = pageScale
                                    alpha = 1f - (0.30f * offset)
                                }
                        ) {
                            singleItemContent(items[idx])
                        }
                    }
                }
                AnimatedVisibility(
                    visible = showPagePill,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                        .testTag("viewer_page_pill")
                ) {
                    PageIndicatorPill(
                        text = "$unitLabel ${pagerState.currentPage + 1} / $total"
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
            Box(modifier = containerModifier) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = topClearance + 10.dp,
                        bottom = bottomClearance + 52.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(items, key = { _, item -> keySelector(item) }) { idx, item ->
                        continuousItemContent(idx, item)
                    }
                }
                AnimatedVisibility(
                    visible = showPagePill,
                    enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                        .testTag("viewer_page_pill")
                ) {
                    PageIndicatorPill(
                        text = "$unitLabel $current / ${items.size}"
                    )
                }
            }
        }
    }
}

@Composable
private fun PdfViewerContent(
    session: PdfDocumentSession,
    mode: PageViewMode,
    showPagePill: Boolean,
    topClearance: androidx.compose.ui.unit.Dp,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onToggleUi: () -> Unit
) {
    val pageIndices = remember(session) { (0 until session.pageCount).toList() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val targetWidthPx = with(density) { maxWidth.toPx().toInt() }.coerceIn(600, 1800)

        PagedOrContinuousViewer(
            items = pageIndices,
            mode = mode,
            unitLabel = "Page",
            showPagePill = showPagePill,
            topClearance = topClearance,
            bottomClearance = bottomClearance,
            enableContainerTap = mode != PageViewMode.SINGLE_PAGE,
            onToggleUi = onToggleUi,
            keySelector = { it },
            singleItemContent = { pageIndex ->
                ZoomablePdfPage(
                    session = session,
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    onSingleTap = onToggleUi,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = 8.dp,
                            end = 8.dp,
                            top = topClearance + 8.dp,
                            bottom = bottomClearance + 12.dp
                        )
                )
            },
            continuousItemContent = { _, pageIndex ->
                PdfPageBitmapCard(
                    session = session,
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx
                )
            }
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ZoomablePdfPage(
    session: PdfDocumentSession,
    pageIndex: Int,
    targetWidthPx: Int,
    onSingleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val scaleAnim = remember(session, pageIndex) { Animatable(1f) }
    val offsetAnim = remember(session, pageIndex) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scaleAnim.value * zoomChange).coerceIn(1f, 4f)
        val newOffset = if (newScale > 1.02f) offsetAnim.value + panChange else Offset.Zero
        scope.launch {
            scaleAnim.snapTo(newScale)
            offsetAnim.snapTo(newOffset)
        }
    }

    Box(
        modifier = modifier
            .pointerInput(session, pageIndex, onSingleTap) {
                detectTapGestures(
                    onTap = { onSingleTap() },
                    onDoubleTap = {
                        scope.launch {
                            val zoomSpring = spring<Float>(
                                dampingRatio = 0.82f,
                                stiffness = Spring.StiffnessMediumLow
                            )
                            if (scaleAnim.value > 1.05f) {
                                launch { scaleAnim.animateTo(1f, zoomSpring) }
                                launch {
                                    offsetAnim.animateTo(
                                        Offset.Zero,
                                        spring(
                                            dampingRatio = 0.82f,
                                            stiffness = Spring.StiffnessMediumLow
                                        )
                                    )
                                }
                            } else {
                                scaleAnim.animateTo(2f, zoomSpring)
                            }
                        }
                    }
                )
            }
            .transformable(state = transformState, canPan = { scaleAnim.value > 1.02f }),
        contentAlignment = Alignment.Center
    ) {
        PdfPageBitmapCard(
            session = session,
            pageIndex = pageIndex,
            targetWidthPx = targetWidthPx,
            modifier = Modifier.graphicsLayer(
                scaleX = scaleAnim.value,
                scaleY = scaleAnim.value,
                translationX = offsetAnim.value.x,
                translationY = offsetAnim.value.y
            )
        )
    }
}

@Composable
private fun PdfPageBitmapCard(
    session: PdfDocumentSession,
    pageIndex: Int,
    targetWidthPx: Int,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(session, pageIndex) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        bitmap = session.renderPage(pageIndex, targetWidthPx)
        session.prefetchAdjacent(pageIndex, targetWidthPx)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp)
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.707f),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp))
            }
        }
    }
}

@Composable
private fun WordPaperCard(
    page: WordPage,
    fontSizeSp: Float
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 700.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            page.blocks.forEach { block ->
                val computedSize = when {
                    block.isHeading && block.headingLevel == 1 -> (fontSizeSp + 6f).sp
                    block.isHeading && block.headingLevel == 2 -> (fontSizeSp + 3f).sp
                    block.isHeading -> (fontSizeSp + 1.5f).sp
                    else -> fontSizeSp.sp
                }
                Text(
                    text = if (block.isBullet) "•  ${block.text}" else block.text,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = computedSize,
                        lineHeight = (computedSize.value * 1.45f).sp,
                        fontWeight = if (block.isHeading || block.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun SlideCard(
    slide: SlidePage,
    totalSlides: Int,
    accentColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 720.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            shadowElevation = 3.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(accentColor)
                )
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "SLIDE ${slide.slideNumber} OF $totalSlides",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = slide.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (slide.bodyLines.isNotEmpty()) {
                        HorizontalDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            slide.bodyLines.forEach { line ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(text = "•", color = accentColor, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (slide.notes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Speaker Notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = slide.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun TextAndXmlViewerContent(
    fullText: String,
    pages: List<List<String>>,
    mode: PageViewMode,
    isXml: Boolean,
    syntaxHighlight: Boolean,
    showLineNumbers: Boolean,
    fontSizeSp: Float,
    isDarkTheme: Boolean,
    showPagePill: Boolean,
    topClearance: androidx.compose.ui.unit.Dp,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onToggleUi: () -> Unit
) {
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(150)))
        },
        label = "textXmlModeSwitch"
    ) { targetMode ->
        if (targetMode == PageViewMode.SINGLE_PAGE) {
            val indexedPages = remember(pages) { pages.mapIndexed { idx, lines -> idx to lines } }
            PagedOrContinuousViewer(
                items = indexedPages,
                mode = PageViewMode.SINGLE_PAGE,
                unitLabel = "Page",
                showPagePill = showPagePill,
                topClearance = topClearance,
                bottomClearance = bottomClearance,
                enableContainerTap = true,
                onToggleUi = onToggleUi,
                keySelector = { it.first },
                singleItemContent = { (pageIdx, pageLines) ->
                    val lineOffset = pageIdx * 42
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = topClearance + 10.dp,
                                bottom = bottomClearance + 52.dp
                            )
                    ) {
                        LazyColumn(
                            contentPadding = PaddingValues(14.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            itemsIndexed(pageLines) { idx, line ->
                                CodeOrTextLine(
                                    lineNumber = lineOffset + idx + 1,
                                    line = line,
                                    isXml = isXml && syntaxHighlight,
                                    showLineNumber = showLineNumbers,
                                    fontSizeSp = fontSizeSp,
                                    isDarkTheme = isDarkTheme
                                )
                            }
                        }
                    }
                },
                continuousItemContent = { _, _ -> }
            )
        } else {
            val allLines = remember(fullText) { fullText.lines() }
            val listState = rememberLazyListState()
            val currentPage by remember { derivedStateOf { (listState.firstVisibleItemIndex / 42) + 1 } }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(onToggleUi) {
                        detectTapGestures(onTap = { onToggleUi() })
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = topClearance + 12.dp,
                        bottom = bottomClearance + 52.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(allLines) { idx, line ->
                        CodeOrTextLine(
                            lineNumber = idx + 1,
                            line = line,
                            isXml = isXml && syntaxHighlight,
                            showLineNumber = showLineNumbers,
                            fontSizeSp = fontSizeSp,
                            isDarkTheme = isDarkTheme
                        )
                    }
                }
                if (pages.size > 1) {
                    AnimatedVisibility(
                        visible = showPagePill,
                        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding()
                            .padding(bottom = 14.dp)
                            .testTag("viewer_page_pill")
                    ) {
                        PageIndicatorPill(
                            text = "Page ${currentPage.coerceAtMost(pages.size)} / ${pages.size}"
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CodeOrTextLine(
    lineNumber: Int,
    line: String,
    isXml: Boolean,
    showLineNumber: Boolean,
    fontSizeSp: Float,
    isDarkTheme: Boolean
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        if (showLineNumber) {
            Text(
                text = lineNumber.toString().padStart(4, ' '),
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = (fontSizeSp - 2.5f).coerceAtLeast(10f).sp
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.padding(end = 10.dp, top = 2.dp)
            )
        }
        if (isXml) {
            Text(
                text = highlightXmlLine(line, isDarkTheme),
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.4f).sp
                ),
                modifier = Modifier.weight(1f)
            )
        } else {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp
                ),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun highlightXmlLine(line: String, isDarkTheme: Boolean): AnnotatedString {
    val tagColor = if (isDarkTheme) XmlTagDark else XmlTagLight
    val attrColor = if (isDarkTheme) XmlAttrDark else XmlAttrLight
    val valColor = if (isDarkTheme) XmlValueDark else XmlValueLight
    val commentColor = if (isDarkTheme) XmlCommentDark else XmlCommentLight

    return buildAnnotatedString {
        if (line.trimStart().startsWith("<!--")) {
            withStyle(SpanStyle(color = commentColor, fontStyle = FontStyle.Italic)) { append(line) }
            return@buildAnnotatedString
        }
        var i = 0
        while (i < line.length) {
            if (line[i] == '<') {
                val closeIdx = line.indexOf('>', i)
                if (closeIdx == -1) {
                    withStyle(SpanStyle(color = tagColor)) { append(line.substring(i)) }
                    break
                }
                appendXmlTag(line.substring(i, closeIdx + 1), tagColor, attrColor, valColor)
                i = closeIdx + 1
            } else {
                val nextTag = line.indexOf('<', i)
                val end = if (nextTag == -1) line.length else nextTag
                append(line.substring(i, end))
                i = end
            }
        }
    }
}

private fun AnnotatedString.Builder.appendXmlTag(
    tagText: String,
    tagColor: Color,
    attrColor: Color,
    valColor: Color
) {
    val spaceIdx = tagText.indexOfFirst { it.isWhitespace() }
    if (spaceIdx == -1) {
        withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium)) { append(tagText) }
        return
    }
    withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium)) {
        append(tagText.substring(0, spaceIdx))
    }
    var i = spaceIdx
    while (i < tagText.length) {
        val ch = tagText[i]
        when {
            ch == '"' || ch == '\'' -> {
                val endQuote = tagText.indexOf(ch, i + 1)
                val end = if (endQuote == -1) tagText.length else endQuote + 1
                withStyle(SpanStyle(color = valColor)) { append(tagText.substring(i, end)) }
                i = end
            }
            ch == '>' || (ch == '/' && i + 1 < tagText.length && tagText[i + 1] == '>') -> {
                withStyle(SpanStyle(color = tagColor, fontWeight = FontWeight.Medium)) {
                    append(tagText.substring(i))
                }
                break
            }
            ch.isLetter() || ch == '_' || ch == ':' || ch == '-' -> {
                var end = i + 1
                while (end < tagText.length && (tagText[end].isLetterOrDigit() || tagText[end] in "_:-")) end++
                withStyle(SpanStyle(color = attrColor)) { append(tagText.substring(i, end)) }
                i = end
            }
            else -> {
                append(ch)
                i++
            }
        }
    }
}

@Composable
private fun PageIndicatorPill(text: String, modifier: Modifier = Modifier) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                (fadeIn(tween(140)) + slideInVertically(tween(140)) { it / 3 })
                    .togetherWith(fadeOut(tween(100)) + slideOutVertically(tween(100)) { -it / 3 })
                    .using(SizeTransform(clip = false))
            },
            label = "pageIndicatorText"
        ) { currentText ->
            Text(
                text = currentText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
