package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.model.ThemeMode
import com.example.ui.screens.DocumentViewerScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DocVaultTheme
import com.example.viewmodel.MainViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_VIEWER = "viewer/{docId}"
private const val ROUTE_SETTINGS = "settings"

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIncomingIntent(intent)

        setContent {
            DocVaultApp(viewModel = viewModel)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.let { uri ->
                viewModel.handleIncomingViewUri(uri)
            }
        }
    }
}

@Composable
private fun DocVaultApp(viewModel: MainViewModel) {
    val uiState by viewModel.homeUiState.collectAsStateWithLifecycle()
    val externalDoc by viewModel.openExternalDoc.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val systemDark = isSystemInDarkTheme()
    val isDarkTheme = when (uiState.settings.themeMode) {
        ThemeMode.SYSTEM -> systemDark
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    // Refresh and scan only if storage permission changed after returning from system settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.onForegroundResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { msg ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(msg)
        }
    }

    // Navigate directly if an external VIEW intent opened a file
    LaunchedEffect(externalDoc) {
        val doc = externalDoc ?: return@LaunchedEffect
        val encodedId = Uri.encode(doc.id)
        viewModel.consumeExternalDocNavigation(doc)
        navController.navigate("viewer/$encodedId") {
            launchSingleTop = true
        }
    }

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshStorageAccessAndScan()
    }

    val manageAllFilesLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshStorageAccessAndScan()
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            viewModel.onSafFolderPicked(uri)
        }
    }

    val fileImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.onImportedFilePicked(uri)
        }
    }

    val requestStorageAccess: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            runCatching {
                manageAllFilesLauncher.launch(intent)
            }.onFailure {
                manageAllFilesLauncher.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            }
        } else {
            legacyPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            )
        }
    }

    val openImportFile: () -> Unit = {
        fileImportLauncher.launch(
            arrayOf(
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-excel.sheet.macroenabled.12",
                "text/plain",
                "*/*"
            )
        )
    }

    DocVaultTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME,
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { (it * 0.25f).toInt() },
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(240)) + scaleIn(
                        initialScale = 0.96f,
                        animationSpec = tween(280, easing = FastOutSlowInEasing)
                    )
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { -(it * 0.14f).toInt() },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(220), targetAlpha = 0.7f)
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { -(it * 0.14f).toInt() },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeIn(tween(220), initialAlpha = 0.7f)
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { (it * 0.25f).toInt() },
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    ) + fadeOut(tween(220)) + scaleOut(
                        targetScale = 0.96f,
                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                    )
                }
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        onSelectCategory = viewModel::selectCategory,
                        onCycleThemeMode = { viewModel.cycleThemeMode(uiState.settings.themeMode) },
                        onSelectSort = viewModel::setSortOption,
                        onToggleSearch = viewModel::toggleSearch,
                        onCloseSearch = viewModel::closeSearch,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onOpenSettings = {
                            navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                        },
                        onOpenDocument = { doc ->
                            val encodedId = Uri.encode(doc.id)
                            navController.navigate("viewer/$encodedId") {
                                launchSingleTop = true
                            }
                        },
                        onToggleSelection = viewModel::toggleSelection,
                        onStartSelection = viewModel::startSelectionWith,
                        onSelectAll = viewModel::selectAllInCurrentCategory,
                        onClearSelection = viewModel::clearSelection,
                        onRenameDocument = { doc, name -> viewModel.renameDocument(doc, name) },
                        onDeleteDocuments = { docs -> viewModel.deleteDocuments(docs) },
                        onRequestStorageAccess = requestStorageAccess,
                        onImportFile = openImportFile,
                        onRescan = { viewModel.refreshStorageAccessAndScan(userInitiated = true) },
                        onShowAllDocTypes = { viewModel.setAllDocTypesVisible(true) }
                    )
                }

                composable(
                    route = ROUTE_VIEWER,
                    arguments = listOf(navArgument("docId") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedId = backStackEntry.arguments?.getString("docId").orEmpty()
                    val docId = Uri.decode(encodedId)
                    val document = viewModel.findDocumentById(docId)

                    if (document == null) {
                        LaunchedEffect(docId) {
                            viewModel.clearExternalDoc()
                            navController.popBackStack()
                        }
                    } else {
                        DocumentViewerScreen(
                            document = document,
                            settings = uiState.settings,
                            isDarkTheme = isDarkTheme,
                            onBack = {
                                viewModel.clearExternalDoc()
                                navController.popBackStack()
                            },
                            onRenameDocument = { doc, newName, onRenamed ->
                                viewModel.renameDocument(doc, newName, onRenamed)
                            },
                            onDeleteDocument = { doc, onDeleted ->
                                viewModel.deleteDocuments(listOf(doc), onDeleted)
                            }
                        )
                    }
                }

                composable(ROUTE_SETTINGS) {
                    SettingsScreen(
                        settings = uiState.settings,
                        hasFullStorageAccess = uiState.hasFullStorageAccess,
                        dotFolderDocCount = uiState.dotFolderDocCount,
                        countsByType = uiState.rawCountsByType,
                        cacheSizeBytes = uiState.cacheSizeBytes,
                        onBack = { navController.popBackStack() },
                        onSetThemeMode = viewModel::setThemeMode,
                        onSetDocTypeVisible = viewModel::setDocTypeVisible,
                        onSetScanDotFolders = viewModel::setScanDotFolders,
                        onSetAutoHideReaderUi = viewModel::setAutoHideReaderUi,
                        onSetAutoCleanRenderCache = viewModel::setAutoCleanRenderCache,
                        onSetPdfPageViewMode = viewModel::setPdfPageViewMode,
                        onSetPdfInvertInDarkMode = viewModel::setPdfInvertInDarkMode,
                        onSetPdfDoubleTapZoom = viewModel::setPdfDoubleTapZoom,
                        onSetDocxRenderFormat = viewModel::setDocxRenderFormat,
                        onSetDocxPageSize = viewModel::setDocxPageSize,
                        onSetDocxMargin = viewModel::setDocxMargin,
                        onSetDocxFontSizeSp = viewModel::setDocxFontSizeSp,
                        onSetXlsxRenderFormat = viewModel::setXlsxRenderFormat,
                        onSetXlsxPageSize = viewModel::setXlsxPageSize,
                        onSetXlsxMargin = viewModel::setXlsxMargin,
                        onSetShowExcelGridlines = viewModel::setShowExcelGridlines,
                        onSetXlsxFontSizeSp = viewModel::setXlsxFontSizeSp,
                        onSetPptRenderFormat = viewModel::setPptRenderFormat,
                        onSetPptPageSize = viewModel::setPptPageSize,
                        onSetPptMargin = viewModel::setPptMargin,
                        onSetShowPptSpeakerNotes = viewModel::setShowPptSpeakerNotes,
                        onSetTextPageViewMode = viewModel::setTextPageViewMode,
                        onSetShowLineNumbers = viewModel::setShowLineNumbers,
                        onSetTextWordWrap = viewModel::setTextWordWrap,
                        onSetTextMonospace = viewModel::setTextMonospace,
                        onSetTextFontSizeSp = viewModel::setTextFontSizeSp,
                        onRequestStorageAccess = requestStorageAccess,
                        onPickCustomFolder = { folderPickerLauncher.launch(null) },
                        onRemoveCustomFolder = viewModel::removeSafFolder,
                        onImportFile = openImportFile,
                        onRescanNow = { viewModel.refreshStorageAccessAndScan(userInitiated = true) },
                        onClearCache = viewModel::clearCache
                    )
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = if (uiState.isSelectionMode) 76.dp else 12.dp)
            )
        }
    }
}
