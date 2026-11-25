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
import com.example.ui.screens.ScannedDocumentsScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.DocVaultTheme
import com.example.viewmodel.MainViewModel

private const val ROUTE_HOME = "home"
private const val ROUTE_VIEWER = "viewer/{docId}"
private const val ROUTE_SETTINGS = "settings"
private const val ROUTE_SCANNED_LIST = "scanned_list"

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
                "text/xml",
                "application/xml",
                "text/plain",
                "*/*"
            )
        )
    }

    DocVaultTheme(darkTheme = isDarkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = ROUTE_HOME
            ) {
                composable(ROUTE_HOME) {
                    HomeScreen(
                        uiState = uiState,
                        isDarkTheme = isDarkTheme,
                        onSelectCategory = viewModel::selectCategory,
                        onToggleDarkLight = { viewModel.toggleDarkLightQuick(isDarkTheme) },
                        onSelectSort = viewModel::setSortOption,
                        onToggleSearch = viewModel::toggleSearch,
                        onSearchQueryChange = viewModel::setSearchQuery,
                        onOpenSettings = {
                            navController.navigate(ROUTE_SETTINGS) { launchSingleTop = true }
                        },
                        onOpenScannedList = {
                            navController.navigate(ROUTE_SCANNED_LIST) { launchSingleTop = true }
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
                        onRescan = viewModel::scanDocuments,
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
                        onSetAllDocTypesVisible = viewModel::setAllDocTypesVisible,
                        onSetScanDotFolders = viewModel::setScanDotFolders,
                        onSetShowDotFolderBadge = viewModel::setShowDotFolderBadge,
                        onSetDefaultPageViewMode = viewModel::setDefaultPageViewMode,
                        onSetXmlSyntaxHighlight = viewModel::setXmlSyntaxHighlight,
                        onSetShowLineNumbers = viewModel::setShowLineNumbers,
                        onSetReaderFontSizeSp = viewModel::setReaderFontSizeSp,
                        onSetAutoCleanRenderCache = viewModel::setAutoCleanRenderCache,
                        onRequestStorageAccess = requestStorageAccess,
                        onPickCustomFolder = { folderPickerLauncher.launch(null) },
                        onRemoveCustomFolder = viewModel::removeSafFolder,
                        onImportFile = openImportFile,
                        onRescanNow = viewModel::scanDocuments,
                        onClearCache = viewModel::clearCache
                    )
                }

                composable(ROUTE_SCANNED_LIST) {
                    ScannedDocumentsScreen(
                        documents = uiState.allVisibleDocuments,
                        visibleTypes = uiState.visibleDocTypes,
                        isScanning = uiState.isScanning,
                        isDarkTheme = isDarkTheme,
                        showDotFolderBadge = uiState.settings.showDotFolderBadge,
                        sortOption = uiState.settings.sortOption,
                        onBack = { navController.popBackStack() },
                        onOpenDocument = { doc ->
                            val encodedId = Uri.encode(doc.id)
                            navController.navigate("viewer/$encodedId") {
                                launchSingleTop = true
                            }
                        },
                        onRescan = viewModel::scanDocuments,
                        onSelectSort = viewModel::setSortOption,
                        onRenameDocument = { doc, name -> viewModel.renameDocument(doc, name) },
                        onDeleteDocuments = { docs -> viewModel.deleteDocuments(docs) }
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
