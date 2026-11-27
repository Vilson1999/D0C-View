package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
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
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.displayCutoutPadding
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.data.DocumentParser
import com.example.data.OfficePageLayout
import com.example.data.ParsedDocument
import com.example.data.PdfDocumentSession
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.DocumentViewOrientation
import com.example.model.OfficeRenderFormat
import com.example.model.PageViewMode
import com.example.model.ScreenOrientationPolicy
import com.example.ui.components.AppIcons
import com.example.ui.components.DeleteConfirmDialog
import com.example.ui.components.DocumentActionDropdownMenu
import com.example.ui.components.DocumentInfoSheet
import com.example.ui.components.OfficeOriginalExcelContent
import com.example.ui.components.OfficeOriginalSlideCard
import com.example.ui.components.OfficeOriginalWordPaperCard
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalMaterialUiEnabled
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

    val initialViewMode = when (document.type) {
        DocType.PDF -> settings.pdfPageViewMode
        DocType.TEXT -> settings.textPageViewMode
        else -> settings.defaultPageViewMode
    }
    var pageViewMode by remember(document.id, initialViewMode) {
        mutableStateOf(initialViewMode)
    }

    val supportsFormatSwitch = currentDoc.type == DocType.DOC ||
        currentDoc.type == DocType.EXCEL ||
        currentDoc.type == DocType.PPT ||
        currentDoc.type == DocType.TEXT

    val originalFormatShortLabel = when (currentDoc.type) {
        DocType.DOC -> "DOCX"
        DocType.EXCEL -> "XLSX"
        DocType.PPT -> "PPT"
        DocType.TEXT -> "TEXT"
        else -> ""
    }

    val defaultFormatForDoc = when (currentDoc.type) {
        DocType.DOC -> settings.docxRenderFormat
        DocType.EXCEL -> settings.xlsxRenderFormat
        DocType.PPT -> settings.pptRenderFormat
        DocType.TEXT -> settings.textRenderFormat
        else -> settings.officeRenderFormat
    }

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    var isReaderUiVisible by remember(document.id) { mutableStateOf(true) }
    var uiInteractionNonce by remember(document.id) { mutableIntStateOf(0) }

    var activeRenderFormat by remember(document.id, defaultFormatForDoc) {
        mutableStateOf(defaultFormatForDoc)
    }
    var documentOrientation by remember(document.id) {
        mutableStateOf(DocumentViewOrientation.AUTO)
    }

    val toggleFormatMenuLabel = if (supportsFormatSwitch) {
        if (activeRenderFormat == OfficeRenderFormat.PDF) {
            "Show as $originalFormatShortLabel"
        } else {
            "Show as PDF"
        }
    } else {
        null
    }

    val onToggleRenderFormat: (() -> Unit)? = if (supportsFormatSwitch) {
        {
            activeRenderFormat = if (activeRenderFormat == OfficeRenderFormat.PDF) {
                OfficeRenderFormat.ORIGINAL
            } else {
                OfficeRenderFormat.PDF
            }
            uiInteractionNonce++
        }
    } else {
        null
    }

    val parseKey = when (currentDoc.type) {
        DocType.DOC -> "${currentDoc.id}|${currentDoc.uriString}|${settings.docxFontSizeSp.toInt()}|${activeRenderFormat.name}|${documentOrientation.name}"
        DocType.EXCEL -> "${currentDoc.id}|${currentDoc.uriString}|${settings.xlsxFontSizeSp.toInt()}|${settings.showExcelGridlines}|${activeRenderFormat.name}|${documentOrientation.name}"
        DocType.PPT -> "${currentDoc.id}|${currentDoc.uriString}|${settings.showPptSpeakerNotes}|${activeRenderFormat.name}|${documentOrientation.name}"
        DocType.TEXT -> "${currentDoc.id}|${currentDoc.uriString}|${settings.textFontSizeSp.toInt()}|${settings.showLineNumbers}|${settings.textWordWrap}|${settings.textMonospace}|${activeRenderFormat.name}|${documentOrientation.name}"
        DocType.PDF -> "${currentDoc.id}|${currentDoc.uriString}|${documentOrientation.name}"
    }

    LaunchedEffect(parseKey) {
        parsedDoc = null
        isReaderUiVisible = true
        parsedDoc = parser.parse(
            doc = currentDoc,
            settings = settings,
            renderFormatOverride = activeRenderFormat,
            documentOrientation = documentOrientation
        )
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

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    DisposableEffect(parsedDoc) {
        val pdfSession = (parsedDoc as? ParsedDocument.Pdf)?.session
        onDispose { pdfSession?.close() }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ScreenOrientationPolicy.resolveSensorFollowOrientation()
        }
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
                        is ParsedDocument.Pdf -> "pdf_${currentDoc.id}_${activeRenderFormat.name}_${documentOrientation.name}"
                        is ParsedDocument.Word -> "word_${currentDoc.id}_${documentOrientation.name}"
                        is ParsedDocument.Slides -> "slides_${currentDoc.id}_${documentOrientation.name}"
                        is ParsedDocument.Spreadsheet -> "sheet_${currentDoc.id}_${documentOrientation.name}"
                        is ParsedDocument.PlainText -> "text_${currentDoc.id}_${activeRenderFormat.name}_${documentOrientation.name}"
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
                        invertInDark = settings.pdfInvertInDarkMode && isDarkTheme,
                        doubleTapZoom = settings.pdfDoubleTapZoom,
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
                                OfficeOriginalWordPaperCard(
                                    page = page,
                                    layout = doc.layout,
                                    fontSizeSp = settings.docxFontSizeSp
                                )
                            }
                        },
                        continuousItemContent = { _, page ->
                            OfficeOriginalWordPaperCard(
                                page = page,
                                layout = doc.layout,
                                fontSizeSp = settings.docxFontSizeSp
                            )
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
                                OfficeOriginalSlideCard(
                                    slide = slide,
                                    totalSlides = doc.slides.size,
                                    layout = doc.layout,
                                    accentColor = currentDoc.type.pillColor,
                                    showNotes = settings.showPptSpeakerNotes
                                )
                            }
                        },
                        continuousItemContent = { _, slide ->
                            OfficeOriginalSlideCard(
                                slide = slide,
                                totalSlides = doc.slides.size,
                                layout = doc.layout,
                                accentColor = currentDoc.type.pillColor,
                                showNotes = settings.showPptSpeakerNotes
                            )
                        }
                    )
                    is ParsedDocument.Spreadsheet -> OfficeOriginalExcelContent(
                        workbook = doc.workbook,
                        layout = doc.layout,
                        showGridlines = settings.showExcelGridlines,
                        fontSizeSp = settings.xlsxFontSizeSp,
                        showControls = isReaderUiVisible,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        onToggleUi = toggleReaderUi
                    )
                    is ParsedDocument.PlainText -> PlainTextViewerContent(
                        fullText = doc.fullText,
                        pages = doc.pages,
                        layout = doc.layout,
                        mode = pageViewMode,
                        showLineNumbers = settings.showLineNumbers,
                        wordWrap = settings.textWordWrap,
                        useMonospace = settings.textMonospace,
                        fontSizeSp = settings.textFontSizeSp,
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
                    color = (if (LocalMaterialUiEnabled.current) {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    } else {
                        MaterialTheme.colorScheme.surface
                    }).copy(alpha = 0.96f),
                    tonalElevation = if (LocalMaterialUiEnabled.current) 4.dp else 3.dp,
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
                            .displayCutoutPadding()
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

                        if (supportsFormatSwitch) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = currentDoc.type.pillColor.copy(alpha = 0.14f),
                                border = BorderStroke(1.dp, currentDoc.type.pillColor.copy(alpha = 0.45f)),
                                onClick = { onToggleRenderFormat?.invoke() },
                                modifier = Modifier.testTag("viewer_format_toggle")
                            ) {
                                Text(
                                    text = if (activeRenderFormat == OfficeRenderFormat.PDF) "PDF" else originalFormatShortLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = currentDoc.type.pillColor,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        val rotateRotation by animateFloatAsState(
                            targetValue = if (isLandscape) 90f else 0f,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "viewerRotateIconAngle"
                        )
                        IconButton(
                            onClick = {
                                if (activity != null) {
                                    activity.requestedOrientation = ScreenOrientationPolicy.nextManualOrientation(isLandscape)
                                }
                                uiInteractionNonce++
                            },
                            modifier = Modifier.testTag("viewer_rotate_button")
                        ) {
                            Icon(
                                imageVector = AppIcons.ScreenRotation,
                                contentDescription = stringResource(R.string.action_rotate_screen),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.graphicsLayer { rotationZ = rotateRotation }
                            )
                        }

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

                            val activeDocIsLandscape = when (documentOrientation) {
                                DocumentViewOrientation.PORTRAIT -> false
                                DocumentViewOrientation.LANDSCAPE -> true
                                DocumentViewOrientation.AUTO -> when (val doc = parsedDoc) {
                                    is ParsedDocument.Word -> doc.layout.isLandscape
                                    is ParsedDocument.Slides -> doc.layout.isLandscape
                                    is ParsedDocument.Spreadsheet -> doc.layout.isLandscape
                                    is ParsedDocument.PlainText -> doc.layout.isLandscape
                                    is ParsedDocument.Pdf -> doc.summary?.isLandscape ?: doc.session.isFirstPageNaturallyLandscape
                                    else -> currentDoc.type == DocType.PPT || currentDoc.type == DocType.EXCEL
                                }
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
                                toggleFormatLabel = toggleFormatMenuLabel,
                                onToggleFormat = onToggleRenderFormat,
                                activeDocumentIsLandscape = activeDocIsLandscape,
                                onSelectDocumentOrientation = { chosenOrientation ->
                                    documentOrientation = chosenOrientation
                                    uiInteractionNonce++
                                },
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
                    verticalArrangement = Arrangement.spacedBy(7.dp),
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
    invertInDark: Boolean,
    doubleTapZoom: Float,
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
                    invertInDark = invertInDark,
                    doubleTapZoom = doubleTapZoom,
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
                    targetWidthPx = targetWidthPx,
                    invertInDark = invertInDark
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
    invertInDark: Boolean,
    doubleTapZoom: Float,
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
            .pointerInput(session, pageIndex, onSingleTap, doubleTapZoom) {
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
                                scaleAnim.animateTo(doubleTapZoom.coerceIn(1.5f, 3.5f), zoomSpring)
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
            invertInDark = invertInDark,
            modifier = Modifier.graphicsLayer(
                scaleX = scaleAnim.value,
                scaleY = scaleAnim.value,
                translationX = offsetAnim.value.x,
                translationY = offsetAnim.value.y
            )
        )
    }
}

private val PdfInvertColorFilter: ColorFilter by lazy {
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                -1f, 0f, 0f, 0f, 255f,
                0f, -1f, 0f, 0f, 255f,
                0f, 0f, -1f, 0f, 255f,
                0f, 0f, 0f, 1f, 0f
            )
        )
    )
}

@Composable
private fun PdfPageBitmapCard(
    session: PdfDocumentSession,
    pageIndex: Int,
    targetWidthPx: Int,
    invertInDark: Boolean,
    modifier: Modifier = Modifier
) {
    var bitmap by remember(session, pageIndex, targetWidthPx) {
        mutableStateOf(session.peekCachedPage(pageIndex, targetWidthPx))
    }

    LaunchedEffect(session, pageIndex, targetWidthPx) {
        if (bitmap == null) {
            bitmap = session.renderPage(pageIndex, targetWidthPx)
        }
        session.prefetchAdjacent(pageIndex, targetWidthPx)
    }

    val bmp = bitmap
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (invertInDark) Color(0xFF0F172A) else Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = if (bmp != null && bmp.width > bmp.height) 1040.dp else 720.dp)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.FillWidth,
                colorFilter = if (invertInDark) PdfInvertColorFilter else null,
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
private fun PlainTextViewerContent(
    fullText: String,
    pages: List<List<String>>,
    layout: OfficePageLayout,
    mode: PageViewMode,
    showLineNumbers: Boolean,
    wordWrap: Boolean,
    useMonospace: Boolean,
    fontSizeSp: Float,
    showPagePill: Boolean,
    topClearance: androidx.compose.ui.unit.Dp,
    bottomClearance: androidx.compose.ui.unit.Dp,
    onToggleUi: () -> Unit
) {
    val wrapModifier = if (wordWrap) Modifier.fillMaxSize() else Modifier.fillMaxSize().horizontalScroll(rememberScrollState())
    val padLeft = (layout.marginLeftPt * 0.32f).coerceIn(10f, 28f).dp
    val padRight = (layout.marginRightPt * 0.32f).coerceIn(10f, 28f).dp
    val padTop = (layout.marginTopPt * 0.30f).coerceIn(12f, 26f).dp
    val padBottom = (layout.marginBottomPt * 0.28f).coerceIn(10f, 24f).dp
    val maxWidthDp = if (layout.isLandscape) 820.dp else 700.dp

    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            (fadeIn(tween(220, easing = FastOutSlowInEasing)) +
                scaleIn(initialScale = 0.98f, animationSpec = tween(220)))
                .togetherWith(fadeOut(tween(150)))
        },
        label = "plainTextModeSwitch"
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
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = topClearance + 10.dp,
                                bottom = bottomClearance + 52.dp
                            ),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                            modifier = Modifier
                                .fillMaxSize()
                                .widthIn(max = maxWidthDp)
                        ) {
                            LazyColumn(
                                modifier = wrapModifier,
                                contentPadding = PaddingValues(
                                    start = padLeft,
                                    end = padRight,
                                    top = padTop,
                                    bottom = padBottom
                                ),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                itemsIndexed(pageLines) { idx, line ->
                                    PlainTextLine(
                                        lineNumber = lineOffset + idx + 1,
                                        line = line,
                                        showLineNumber = showLineNumbers,
                                        wordWrap = wordWrap,
                                        useMonospace = useMonospace,
                                        fontSizeSp = fontSizeSp
                                    )
                                }
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
                    modifier = wrapModifier
                        .background(MaterialTheme.colorScheme.surface),
                    contentPadding = PaddingValues(
                        start = padLeft,
                        end = padRight,
                        top = topClearance + padTop,
                        bottom = bottomClearance + 52.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    itemsIndexed(allLines) { idx, line ->
                        PlainTextLine(
                            lineNumber = idx + 1,
                            line = line,
                            showLineNumber = showLineNumbers,
                            wordWrap = wordWrap,
                            useMonospace = useMonospace,
                            fontSizeSp = fontSizeSp
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
private fun PlainTextLine(
    lineNumber: Int,
    line: String,
    showLineNumber: Boolean,
    wordWrap: Boolean,
    useMonospace: Boolean,
    fontSizeSp: Float
) {
    Row(
        modifier = if (wordWrap) Modifier.fillMaxWidth() else Modifier,
        verticalAlignment = Alignment.Top
    ) {
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
        Text(
            text = line,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontFamily = if (useMonospace) JetBrainsMonoFontFamily else FontFamily.SansSerif,
                fontSize = fontSizeSp.sp,
                lineHeight = (fontSizeSp * 1.45f).sp
            ),
            softWrap = wordWrap,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = if (wordWrap) Modifier.weight(1f) else Modifier
        )
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
