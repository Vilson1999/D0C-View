package com.example.ui.screens

import android.app.Activity
import android.content.res.Configuration
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutoutPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.DocumentActionsHelper
import com.example.data.DocumentParser
import com.example.data.OfficePageLayout
import com.example.data.ParsedDocument
import com.example.data.PdfDocumentSession
import com.example.data.PdfRenderQualityPolicy
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
import com.example.ui.components.ReaderZoomMath
import com.example.ui.components.ReaderZoomState
import com.example.ui.components.RenameDocumentDialog
import com.example.ui.components.TopBarActionButton
import com.example.ui.components.ZoomableReaderViewport
import com.example.ui.components.rememberReaderZoomState
import com.example.ui.theme.AppMotion
import com.example.ui.theme.AppMotionPolicy
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAnimationsEnabled
import com.example.ui.theme.LocalMaterialIconsEnabled
import com.example.ui.theme.NavigationBarSideBackdrop
import com.example.ui.theme.SyncSystemBarsAppearance
import com.example.ui.theme.SystemBarContrastPolicy
import kotlin.math.absoluteValue
import kotlinx.coroutines.delay

@Composable
fun DocumentViewerScreen(
    document: DocumentItem,
    settings: AppSettings,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onRenameDocument: (DocumentItem, String, (DocumentItem) -> Unit) -> Unit,
    onDeleteDocument: (DocumentItem, () -> Unit) -> Unit,
    onSavePageViewMode: (DocType, PageViewMode) -> Unit = { _, _ -> },
    onSaveRenderFormat: (DocType, OfficeRenderFormat) -> Unit = { _, _ -> },
    onSaveDocumentOrientation: (DocType, DocumentViewOrientation) -> Unit = { _, _ -> },
    onSaveReaderScreenOrientation: (DocumentViewOrientation) -> Unit = {},
    onSaveOrgZoomScale: (DocType, Float) -> Unit = { _, _ -> },
    syncSystemBars: Boolean = true
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val parser = remember(context) { DocumentParser(context) }

    var currentDoc by remember(document.id) { mutableStateOf(document) }
    var parsedDoc by remember(document.id) { mutableStateOf<ParsedDocument?>(null) }

    val initialViewMode = settings.pageViewModeFor(currentDoc.type)
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

    val defaultFormatForDoc = settings.renderFormatFor(currentDoc.type)

    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showInfoSheet by remember { mutableStateOf(false) }

    var isReaderUiVisible by remember(document.id) { mutableStateOf(true) }
    var uiInteractionNonce by remember(document.id) { mutableIntStateOf(0) }

    var activeRenderFormat by remember(document.id, defaultFormatForDoc) {
        mutableStateOf(defaultFormatForDoc)
    }
    val savedOrientationForDoc = settings.viewOrientationFor(currentDoc.type)
    var documentOrientation by remember(document.id, savedOrientationForDoc) {
        mutableStateOf(savedOrientationForDoc)
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
            val nextFormat = if (activeRenderFormat == OfficeRenderFormat.PDF) {
                OfficeRenderFormat.ORIGINAL
            } else {
                OfficeRenderFormat.PDF
            }
            activeRenderFormat = nextFormat
            onSaveRenderFormat(currentDoc.type, nextFormat)
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

    val readerZoomState = rememberReaderZoomState(
        key = "$parseKey|${pageViewMode.name}",
        doubleTapZoom = settings.pdfDoubleTapZoom,
        initialScale = if (activeRenderFormat == OfficeRenderFormat.ORIGINAL) {
            settings.orgZoomScaleFor(currentDoc.type)
        } else {
            1f
        },
        onScaleCommitted = { scale ->
            if (activeRenderFormat == OfficeRenderFormat.ORIGINAL) {
                onSaveOrgZoomScale(currentDoc.type, scale)
            }
        }
    )

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

    LaunchedEffect(activity, settings.readerScreenOrientation) {
        if (activity != null &&
            !ScreenOrientationPolicy.isSystemAutoRotateEnabled(context.contentResolver) &&
            settings.readerScreenOrientation != DocumentViewOrientation.AUTO
        ) {
            activity.requestedOrientation = ScreenOrientationPolicy.resolveSavedReaderOrientation(
                savedOrientation = settings.readerScreenOrientation,
                isSystemAutoRotateEnabled = false
            )
        }
    }

    DisposableEffect(activity) {
        onDispose {
            activity?.requestedOrientation = ScreenOrientationPolicy.resolveSensorFollowOrientation()
        }
    }

    BackHandler { onBack() }

    val animationsEnabled = LocalAnimationsEnabled.current
    val materialIconsEnabled = LocalMaterialIconsEnabled.current
    val usePillTopBar = settings.usePillTopBar
    val useColorfulTopBar = settings.useColorfulTopBar
    val viewerAccentColor = currentDoc.type.pillColor
    val targetTopBarColor = if (useColorfulTopBar) {
        val base = currentDoc.type.pillColor
        if (isDarkTheme) lerp(base, Color.Black, 0.20f) else lerp(base, Color.White, 0.10f)
    } else {
        if (usePillTopBar) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.background
    }
    val topBarColor by animateColorAsState(
        targetValue = targetTopBarColor,
        animationSpec = AppMotionPolicy.smoothTween(animationsEnabled, 240),
        label = "viewerTopBarColor"
    )
    val topBarContentColor by animateColorAsState(
        targetValue = if (useColorfulTopBar) {
            if (SystemBarContrastPolicy.shouldUseDarkIcons(targetTopBarColor.luminance())) {
                MaterialTheme.colorScheme.onBackground
            } else {
                Color.White
            }
        } else {
            MaterialTheme.colorScheme.onBackground
        },
        animationSpec = AppMotionPolicy.smoothTween(animationsEnabled, 240),
        label = "viewerTopBarContentColor"
    )
    val pagePillColor = if (useColorfulTopBar) {
        topBarColor.copy(alpha = 0.50f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
    }
    val pagePillContentColor = MaterialTheme.colorScheme.onBackground
    val statusBarColor by animateColorAsState(
        targetValue = if (!usePillTopBar && isReaderUiVisible) topBarColor else MaterialTheme.colorScheme.background,
        animationSpec = AppMotionPolicy.smoothTween(animationsEnabled, 240),
        label = "viewerStatusBarColor"
    )
    val bottomBarColor = MaterialTheme.colorScheme.background
    if (syncSystemBars) {
        SyncSystemBarsAppearance(
            statusBarColor = statusBarColor,
            navigationBarColor = bottomBarColor
        )
    }

    val topBarReserve = if (usePillTopBar) 62.6.dp else 43.dp
    val navBarReserve = 0.dp

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = bottomBarColor
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bottomBarColor)
        ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .displayCutoutPadding()
                .clipToBounds()
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
                transitionSpec = { AppMotion.smoothRevealTransform(animationsEnabled) },
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
                                    imageVector = AppIcons.errorOutline(),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(AppIcons.scaledSize(44.dp))
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
                        showPagePill = isReaderUiVisible,
                        pagePillColor = pagePillColor,
                        pagePillContentColor = pagePillContentColor,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        zoomState = readerZoomState,
                        onZoomInteraction = { uiInteractionNonce++ },
                        onToggleUi = toggleReaderUi
                    )
                    is ParsedDocument.Word -> PagedOrContinuousViewer(
                        items = doc.pages,
                        mode = pageViewMode,
                        unitLabel = "Page",
                        showPagePill = isReaderUiVisible,
                        pagePillColor = pagePillColor,
                        pagePillContentColor = pagePillContentColor,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        enableContainerTap = false,
                        onToggleUi = toggleReaderUi,
                        zoomState = readerZoomState,
                        onZoomInteraction = { uiInteractionNonce++ },
                        keySelector = { it.pageNumber },
                        singleItemContent = { page ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .clipToBounds()
                                    .verticalScroll(
                                        rememberScrollState(),
                                        enabled = readerZoomState.canInnerScroll
                                    )
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = topBarReserve + 12.dp,
                                        bottom = navBarReserve + 64.dp
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
                        pagePillColor = pagePillColor,
                        pagePillContentColor = pagePillContentColor,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        enableContainerTap = false,
                        onToggleUi = toggleReaderUi,
                        zoomState = readerZoomState,
                        onZoomInteraction = { uiInteractionNonce++ },
                        keySelector = { it.slideNumber },
                        singleItemContent = { slide ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                                    .clipToBounds()
                                    .verticalScroll(
                                        rememberScrollState(),
                                        enabled = readerZoomState.canInnerScroll
                                    )
                                    .padding(
                                        start = 16.dp,
                                        end = 16.dp,
                                        top = topBarReserve + 12.dp,
                                        bottom = navBarReserve + 64.dp
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
                        zoomState = readerZoomState,
                        onZoomInteraction = { uiInteractionNonce++ },
                        onToggleUi = toggleReaderUi,
                        pillColor = pagePillColor,
                        pillContentColor = pagePillContentColor
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
                        pagePillColor = pagePillColor,
                        pagePillContentColor = pagePillContentColor,
                        topClearance = topBarReserve,
                        bottomClearance = navBarReserve,
                        zoomState = readerZoomState,
                        onZoomInteraction = { uiInteractionNonce++ },
                        onToggleUi = toggleReaderUi
                    )
                }
            }
        }

            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .windowInsetsTopHeight(WindowInsets.statusBars)
                    .background(statusBarColor)
            )

            AnimatedVisibility(
                visible = isReaderUiVisible,
                enter = if (animationsEnabled) {
                    slideInVertically(
                        initialOffsetY = { -it },
                        animationSpec = AppMotionPolicy.smoothSpring(true)
                    ) + fadeIn(tween(200, easing = AppMotionPolicy.SmoothEasing))
                } else {
                    EnterTransition.None
                },
                exit = if (animationsEnabled) {
                    slideOutVertically(
                        targetOffsetY = { -it },
                        animationSpec = tween(180, easing = AppMotionPolicy.SmoothEasing)
                    ) + fadeOut(tween(150))
                } else {
                    ExitTransition.None
                },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .testTag("viewer_topbar")
            ) {
                val topBarRowContent: @Composable (Modifier) -> Unit = { rowModifier ->
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 42.dp) {
                        Row(
                            modifier = rowModifier,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = currentDoc.name,
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = topBarContentColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Start,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("viewer_doc_title")
                            )

                            Spacer(modifier = Modifier.width(4.dp))

                            val rotateRotation by animateFloatAsState(
                                targetValue = if (isLandscape) 90f else 0f,
                                animationSpec = AppMotionPolicy.bouncySpring(
                                    animationsEnabled = animationsEnabled,
                                    dampingRatio = 0.52f,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "viewerRotateIconAngle"
                            )
                            val actionButtonSize = if (materialIconsEnabled) 34.dp else 42.dp
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(if (materialIconsEnabled) 5.dp else 0.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TopBarActionButton(
                                    onClick = {
                                        val nextScreenOrientation = if (isLandscape) {
                                            DocumentViewOrientation.PORTRAIT
                                        } else {
                                            DocumentViewOrientation.LANDSCAPE
                                        }
                                        onSaveReaderScreenOrientation(nextScreenOrientation)
                                        if (activity != null) {
                                            activity.requestedOrientation = ScreenOrientationPolicy.nextManualOrientation(isLandscape)
                                        }
                                        uiInteractionNonce++
                                    },
                                    accentColor = viewerAccentColor,
                                    plainTint = topBarContentColor,
                                    buttonSize = actionButtonSize,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.testTag("viewer_rotate_button")
                                ) { tint ->
                                    Icon(
                                        imageVector = AppIcons.screenRotation(),
                                        contentDescription = stringResource(R.string.action_rotate_screen),
                                        tint = tint,
                                        modifier = Modifier.graphicsLayer { rotationZ = rotateRotation }
                                    )
                                }

                                TopBarActionButton(
                                    onClick = {
                                        val nextMode = if (pageViewMode == PageViewMode.CONTINUOUS) {
                                            PageViewMode.SINGLE_PAGE
                                        } else {
                                            PageViewMode.CONTINUOUS
                                        }
                                        pageViewMode = nextMode
                                        onSavePageViewMode(currentDoc.type, nextMode)
                                        uiInteractionNonce++
                                    },
                                    accentColor = viewerAccentColor,
                                    plainTint = topBarContentColor,
                                    buttonSize = actionButtonSize,
                                    cornerRadius = 10.dp,
                                    modifier = Modifier.testTag("viewer_page_mode_button")
                                ) { tint ->
                                    AnimatedContent(
                                        targetState = pageViewMode,
                                        transitionSpec = { AppMotion.bouncyIconContentTransform(animationsEnabled) },
                                        label = "pageModeIconSwitch"
                                    ) { currentMode ->
                                        Icon(
                                            imageVector = if (currentMode == PageViewMode.SINGLE_PAGE) {
                                                AppIcons.viewCarousel()
                                            } else {
                                                AppIcons.viewStream()
                                            },
                                            contentDescription = stringResource(R.string.action_page_view_mode),
                                            tint = tint
                                        )
                                    }
                                }

                                Box {
                                    val menuIconRotation by animateFloatAsState(
                                        targetValue = if (menuExpanded) 90f else 0f,
                                        animationSpec = AppMotionPolicy.bouncySpring(
                                            animationsEnabled = animationsEnabled,
                                            dampingRatio = 0.52f,
                                            stiffness = Spring.StiffnessMediumLow
                                        ),
                                        label = "viewerMenuButtonRotation"
                                    )
                                    TopBarActionButton(
                                        onClick = {
                                            menuExpanded = true
                                            uiInteractionNonce++
                                        },
                                        accentColor = viewerAccentColor,
                                        plainTint = topBarContentColor,
                                        buttonSize = actionButtonSize,
                                        cornerRadius = 10.dp,
                                        modifier = Modifier.testTag("viewer_more_button")
                                    ) { tint ->
                                        Icon(
                                            imageVector = AppIcons.moreVert(),
                                            contentDescription = stringResource(R.string.action_more_options),
                                            tint = tint,
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
                                    onSaveDocumentOrientation(currentDoc.type, chosenOrientation)
                                    uiInteractionNonce++
                                },
                                tagPrefix = "viewer_menu"
                            )
                            }
                        }
                    }
                }
                }

                if (usePillTopBar) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            .displayCutoutPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = topBarColor,
                            tonalElevation = 0.dp,
                            border = if (!useColorfulTopBar) {
                                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            } else {
                                null
                            },
                            shadowElevation = 6.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pointerInput(Unit) {
                                    detectTapGestures { uiInteractionNonce++ }
                                }
                                .testTag("viewer_topbar_pill")
                        ) {
                            topBarRowContent(
                                Modifier
                                    .fillMaxWidth()
                                    .height(50.6.dp)
                                    .padding(
                                        start = 14.dp,
                                        end = if (materialIconsEnabled) 10.dp else 6.dp
                                    )
                            )
                        }
                    }
                } else {
                    Surface(
                        color = topBarColor,
                        tonalElevation = 0.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Horizontal))
                            .pointerInput(Unit) {
                                detectTapGestures { uiInteractionNonce++ }
                            }
                    ) {
                        topBarRowContent(
                            Modifier
                                .fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .displayCutoutPadding()
                                .height(42.dp)
                                .padding(
                                    start = 8.dp,
                                    end = if (materialIconsEnabled) 6.dp else 2.dp
                                )
                        )
                    }
                }
            }
            NavigationBarSideBackdrop(color = bottomBarColor)
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
    pagePillColor: Color,
    pagePillContentColor: Color,
    topClearance: androidx.compose.ui.unit.Dp,
    bottomClearance: androidx.compose.ui.unit.Dp,
    enableContainerTap: Boolean,
    onToggleUi: () -> Unit,
    zoomState: ReaderZoomState? = null,
    onZoomInteraction: () -> Unit = {},
    keySelector: (T) -> Any,
    singleItemContent: @Composable (T) -> Unit,
    continuousItemContent: @Composable (Int, T) -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val containerModifier = if (enableContainerTap && zoomState == null) {
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
        transitionSpec = { AppMotion.smoothRevealTransform(animationsEnabled) },
        label = "viewerModeSwitch"
    ) { targetMode ->
        if (targetMode == PageViewMode.SINGLE_PAGE) {
            val total = items.size.coerceAtLeast(1)
            val pagerState = rememberPagerState(pageCount = { total })
            val pagerContent: @Composable () -> Unit = {
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = zoomState?.canInnerScroll ?: true,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = if (animationsEnabled) {
                            spring(
                                dampingRatio = AppMotionPolicy.SMOOTH_SPRING_DAMPING,
                                stiffness = Spring.StiffnessMediumLow
                            )
                        } else {
                            snap()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxSize()
                        .clipToBounds()
                ) { idx ->
                    if (idx in items.indices) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .then(
                                    if (animationsEnabled) {
                                        Modifier.graphicsLayer {
                                            val offset = ((pagerState.currentPage - idx) +
                                                pagerState.currentPageOffsetFraction).absoluteValue.coerceIn(0f, 1f)
                                            val pageScale = 1f - (0.045f * offset)
                                            scaleX = pageScale
                                            scaleY = pageScale
                                            alpha = 1f - (0.30f * offset)
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            singleItemContent(items[idx])
                        }
                    }
                }
            }
            Box(modifier = containerModifier) {
                if (zoomState != null) {
                    ZoomableReaderViewport(
                        zoomState = zoomState,
                        onSingleTap = onToggleUi,
                        onUserInteraction = onZoomInteraction
                    ) {
                        pagerContent()
                    }
                } else {
                    pagerContent()
                }
                AnimatedVisibility(
                    visible = showPagePill,
                    enter = if (animationsEnabled) {
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.GENTLE_BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(tween(200, easing = AppMotionPolicy.SmoothEasing))
                    } else {
                        EnterTransition.None
                    },
                    exit = if (animationsEnabled) {
                        slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(180, easing = AppMotionPolicy.SmoothEasing)
                        ) + scaleOut(targetScale = 0.88f, animationSpec = tween(160)) + fadeOut(tween(150))
                    } else {
                        ExitTransition.None
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                ) {
                    PageIndicatorPill(
                        text = "$unitLabel ${pagerState.currentPage + 1} / $total",
                        pillColor = pagePillColor,
                        contentColor = pagePillContentColor,
                        modifier = Modifier.testTag("viewer_page_pill")
                    )
                }
            }
        } else {
            val listState = rememberLazyListState()
            val current by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
            val listContent: @Composable () -> Unit = {
                LazyColumn(
                    state = listState,
                    userScrollEnabled = zoomState?.canInnerScroll ?: true,
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                        .clipToBounds(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = topClearance + 10.dp,
                        bottom = bottomClearance + 64.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(7.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    itemsIndexed(items, key = { _, item -> keySelector(item) }) { idx, item ->
                        continuousItemContent(idx, item)
                    }
                }
            }
            Box(modifier = containerModifier) {
                if (zoomState != null) {
                    ZoomableReaderViewport(
                        zoomState = zoomState,
                        onSingleTap = onToggleUi,
                        onUserInteraction = onZoomInteraction,
                        onVerticalOverflowPan = { overflowY ->
                            val safeScale = zoomState.scale.coerceAtLeast(1f)
                            val consumedChildDelta = listState.dispatchRawDelta(-overflowY / safeScale)
                            -consumedChildDelta * safeScale
                        }
                    ) {
                        listContent()
                    }
                } else {
                    listContent()
                }
                AnimatedVisibility(
                    visible = showPagePill,
                    enter = if (animationsEnabled) {
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.GENTLE_BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(tween(200, easing = AppMotionPolicy.SmoothEasing))
                    } else {
                        EnterTransition.None
                    },
                    exit = if (animationsEnabled) {
                        slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(180, easing = AppMotionPolicy.SmoothEasing)
                        ) + scaleOut(targetScale = 0.88f, animationSpec = tween(160)) + fadeOut(tween(150))
                    } else {
                        ExitTransition.None
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                ) {
                    PageIndicatorPill(
                        text = "$unitLabel $current / ${items.size}",
                        pillColor = pagePillColor,
                        contentColor = pagePillContentColor,
                        modifier = Modifier.testTag("viewer_page_pill")
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
    showPagePill: Boolean,
    pagePillColor: Color,
    pagePillContentColor: Color,
    topClearance: Dp,
    bottomClearance: Dp,
    zoomState: ReaderZoomState,
    onZoomInteraction: () -> Unit,
    onToggleUi: () -> Unit
) {
    val pageIndices = remember(session) { (0 until session.pageCount).toList() }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val targetWidthPx = PdfRenderQualityPolicy.normalizeRequestedWidth(
            with(density) { minOf(maxWidth, maxHeight).toPx().toInt() }
        )

        PagedOrContinuousViewer(
            items = pageIndices,
            mode = mode,
            unitLabel = "Page",
            showPagePill = showPagePill,
            pagePillColor = pagePillColor,
            pagePillContentColor = pagePillContentColor,
            topClearance = topClearance,
            bottomClearance = bottomClearance,
            enableContainerTap = false,
            onToggleUi = onToggleUi,
            zoomState = zoomState,
            onZoomInteraction = onZoomInteraction,
            keySelector = { it },
            singleItemContent = { pageIndex ->
                FittedPdfPageCard(
                    session = session,
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    invertInDark = invertInDark,
                    topClearance = topClearance,
                    bottomClearance = bottomClearance
                )
            },
            continuousItemContent = { _, pageIndex ->
                PdfPageBitmapCard(
                    session = session,
                    pageIndex = pageIndex,
                    targetWidthPx = targetWidthPx,
                    invertInDark = invertInDark,
                    fitToViewportBounds = false
                )
            }
        )
    }
}

@Composable
private fun FittedPdfPageCard(
    session: PdfDocumentSession,
    pageIndex: Int,
    targetWidthPx: Int,
    invertInDark: Boolean,
    topClearance: Dp,
    bottomClearance: Dp,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
            .padding(
                start = 10.dp,
                end = 10.dp,
                top = topClearance + 8.dp,
                bottom = bottomClearance + 14.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        PdfPageBitmapCard(
            session = session,
            pageIndex = pageIndex,
            targetWidthPx = targetWidthPx,
            invertInDark = invertInDark,
            maxContainerWidth = maxWidth,
            maxContainerHeight = maxHeight,
            fitToViewportBounds = true
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
    modifier: Modifier = Modifier,
    maxContainerWidth: Dp = Dp.Unspecified,
    maxContainerHeight: Dp = Dp.Unspecified,
    fitToViewportBounds: Boolean = false
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
    val pageAspect = if (bmp != null && bmp.height > 0) {
        (bmp.width.toFloat() / bmp.height.toFloat()).coerceIn(0.35f, 3.0f)
    } else {
        session.getPageAspectRatio(pageIndex)
    }

    val sizeModifier = if (
        fitToViewportBounds &&
        maxContainerWidth != Dp.Unspecified &&
        maxContainerHeight != Dp.Unspecified
    ) {
        val maxCardWidth = minOf(maxContainerWidth.value, if (pageAspect >= 1f) 1040f else 760f)
        val maxCardHeight = maxContainerHeight.value.coerceAtLeast(1f)
        val (fittedW, fittedH) = ReaderZoomMath.fitPageSize(
            maxWidth = maxCardWidth,
            maxHeight = maxCardHeight,
            aspectRatio = pageAspect
        )
        Modifier.size(width = fittedW.dp, height = fittedH.dp)
    } else {
        val maxCardWidth = if (pageAspect >= 1f) 1040.dp else 720.dp
        Modifier
            .widthIn(max = maxCardWidth)
            .fillMaxWidth()
            .aspectRatio(pageAspect)
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (invertInDark) Color(0xFF0F172A) else Color.White,
        shadowElevation = 3.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier.then(sizeModifier)
    ) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "PDF Page ${pageIndex + 1}",
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                colorFilter = if (invertInDark) PdfInvertColorFilter else null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
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
    pagePillColor: Color,
    pagePillContentColor: Color,
    topClearance: Dp,
    bottomClearance: Dp,
    zoomState: ReaderZoomState,
    onZoomInteraction: () -> Unit,
    onToggleUi: () -> Unit
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    val hScroll = rememberScrollState()
    val wrapModifier = if (wordWrap) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = zoomState.canInnerScroll)
    }
    val padLeft = (layout.marginLeftPt * 0.32f).coerceIn(10f, 28f).dp
    val padRight = (layout.marginRightPt * 0.32f).coerceIn(10f, 28f).dp
    val padTop = (layout.marginTopPt * 0.30f).coerceIn(12f, 26f).dp
    val padBottom = (layout.marginBottomPt * 0.28f).coerceIn(10f, 24f).dp
    val maxWidthDp = if (layout.isLandscape) 820.dp else 700.dp

    AnimatedContent(
        targetState = mode,
        transitionSpec = { AppMotion.smoothRevealTransform(animationsEnabled) },
        label = "plainTextModeSwitch"
    ) { targetMode ->
        if (targetMode == PageViewMode.SINGLE_PAGE) {
            val indexedPages = remember(pages) { pages.mapIndexed { idx, lines -> idx to lines } }
            PagedOrContinuousViewer(
                items = indexedPages,
                mode = PageViewMode.SINGLE_PAGE,
                unitLabel = "Page",
                showPagePill = showPagePill,
                pagePillColor = pagePillColor,
                pagePillContentColor = pagePillContentColor,
                topClearance = topClearance,
                bottomClearance = bottomClearance,
                enableContainerTap = false,
                onToggleUi = onToggleUi,
                zoomState = zoomState,
                onZoomInteraction = onZoomInteraction,
                keySelector = { it.first },
                singleItemContent = { (pageIdx, pageLines) ->
                    val lineOffset = pageIdx * 42
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                            .padding(
                                start = 14.dp,
                                end = 14.dp,
                                top = topClearance + 10.dp,
                                bottom = bottomClearance + 64.dp
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
                                .clip(RoundedCornerShape(14.dp))
                        ) {
                            LazyColumn(
                                userScrollEnabled = zoomState.canInnerScroll,
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
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                    .clipToBounds()
            ) {
                ZoomableReaderViewport(
                    zoomState = zoomState,
                    onSingleTap = onToggleUi,
                    onUserInteraction = onZoomInteraction,
                    onVerticalOverflowPan = { overflowY ->
                        val safeScale = zoomState.scale.coerceAtLeast(1f)
                        val consumedChildDelta = listState.dispatchRawDelta(-overflowY / safeScale)
                        -consumedChildDelta * safeScale
                    },
                    onHorizontalOverflowPan = if (!wordWrap) {
                        { overflowX ->
                            val safeScale = zoomState.scale.coerceAtLeast(1f)
                            val consumedChildDelta = hScroll.dispatchRawDelta(-overflowX / safeScale)
                            -consumedChildDelta * safeScale
                        }
                    } else {
                        null
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        userScrollEnabled = zoomState.canInnerScroll,
                        modifier = wrapModifier
                            .background(MaterialTheme.colorScheme.surface),
                        contentPadding = PaddingValues(
                            start = padLeft,
                            end = padRight,
                            top = topClearance + padTop,
                            bottom = bottomClearance + 68.dp
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
                }
                AnimatedVisibility(
                    visible = showPagePill && pages.size > 1,
                    enter = if (animationsEnabled) {
                        slideInVertically(
                            initialOffsetY = { it / 2 },
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.GENTLE_BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + scaleIn(
                            initialScale = 0.85f,
                            animationSpec = spring(
                                dampingRatio = AppMotionPolicy.BOUNCY_DAMPING_RATIO,
                                stiffness = Spring.StiffnessMedium
                            )
                        ) + fadeIn(tween(200, easing = AppMotionPolicy.SmoothEasing))
                    } else {
                        EnterTransition.None
                    },
                    exit = if (animationsEnabled) {
                        slideOutVertically(
                            targetOffsetY = { it / 2 },
                            animationSpec = tween(180, easing = AppMotionPolicy.SmoothEasing)
                        ) + scaleOut(targetScale = 0.88f, animationSpec = tween(160)) + fadeOut(tween(150))
                    } else {
                        ExitTransition.None
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 14.dp)
                ) {
                    PageIndicatorPill(
                        text = "Page ${currentPage.coerceAtMost(pages.size)} / ${pages.size}",
                        pillColor = pagePillColor,
                        contentColor = pagePillContentColor,
                        modifier = Modifier.testTag("viewer_page_pill")
                    )
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
private fun PageIndicatorPill(
    text: String,
    pillColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    val animationsEnabled = LocalAnimationsEnabled.current
    Surface(
        shape = CircleShape,
        color = pillColor,
        border = BorderStroke(0.8.dp, pillColor.copy(alpha = 0.75f)),
        shadowElevation = 0.dp,
        modifier = modifier
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (!animationsEnabled) {
                    (EnterTransition.None togetherWith ExitTransition.None)
                        .using(SizeTransform(clip = false))
                } else {
                    (fadeIn(tween(160, easing = AppMotionPolicy.SmoothEasing)) +
                        slideInVertically(tween(170, easing = AppMotionPolicy.SmoothEasing)) { it / 3 })
                        .togetherWith(
                            fadeOut(tween(110)) + slideOutVertically(tween(110)) { -it / 3 }
                        )
                        .using(SizeTransform(clip = false))
                }
            },
            label = "pageIndicatorText"
        ) { currentText ->
            Text(
                text = currentText,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = contentColor,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}
