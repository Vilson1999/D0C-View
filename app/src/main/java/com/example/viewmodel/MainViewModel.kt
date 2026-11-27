package com.example.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.DocViewApplication
import com.example.data.DocumentCacheManager
import com.example.data.DocumentParser
import com.example.data.DocumentScanner
import com.example.data.SettingsRepository
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.OfficeRenderFormat
import com.example.model.PageViewMode
import com.example.model.SortOption
import com.example.model.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class HomeUiState(
    val isScanning: Boolean = false,
    val isPullRefreshing: Boolean = false,
    val hasFullStorageAccess: Boolean = false,
    val selectedCategory: DocType = DocType.PDF,
    val visibleDocTypes: List<DocType> = DocType.entries,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedDocIds: Set<String> = emptySet(),
    val documentsByType: Map<DocType, List<DocumentItem>> = DocType.entries.associateWith { emptyList() },
    val rawCountsByType: Map<DocType, Int> = DocType.entries.associateWith { 0 },
    val allDocumentsCount: Int = 0,
    val dotFolderDocCount: Int = 0,
    val cacheSizeBytes: Long = 0L,
    val settings: AppSettings = AppSettings()
) {
    val isSelectionMode: Boolean get() = selectedDocIds.isNotEmpty()
    val visibleDocuments: List<DocumentItem> get() = documentsByType[selectedCategory].orEmpty()
    val selectedDocuments: List<DocumentItem>
        get() = if (selectedDocIds.isEmpty()) emptyList()
        else documentsByType.values.flatten().filter { it.id in selectedDocIds }
}

object DocumentQueryProcessor {
    fun filterAndSort(
        documents: List<DocumentItem>,
        query: String,
        sortOption: SortOption,
        typeFilter: DocType? = null,
        visibleTypes: Set<DocType> = DocType.entries.toSet()
    ): List<DocumentItem> {
        val trimmed = query.trim()
        val matched = documents.asSequence()
            .filter { it.type in visibleTypes }
            .filter { typeFilter == null || it.type == typeFilter }
            .filter {
                trimmed.isEmpty() ||
                    it.name.contains(trimmed, ignoreCase = true) ||
                    it.parentFolderName.contains(trimmed, ignoreCase = true) ||
                    it.path.contains(trimmed, ignoreCase = true)
            }
            .toList()
        return sortDocuments(matched, sortOption)
    }

    fun groupByType(
        documents: List<DocumentItem>,
        query: String,
        sortOption: SortOption,
        visibleTypes: Set<DocType> = DocType.entries.toSet()
    ): Map<DocType, List<DocumentItem>> {
        val sortedFiltered = filterAndSort(documents, query, sortOption, visibleTypes = visibleTypes)
        return DocType.entries.associateWith { type ->
            if (type in visibleTypes) sortedFiltered.filter { it.type == type } else emptyList()
        }
    }

    fun sortDocuments(list: List<DocumentItem>, sortOption: SortOption): List<DocumentItem> =
        when (sortOption) {
            SortOption.DATE_DESC -> list.sortedByDescending { it.lastModified }
            SortOption.DATE_ASC -> list.sortedBy { it.lastModified }
            SortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            SortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            SortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            SortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
        }

    fun resolveHomePillTypes(
        documents: List<DocumentItem>,
        enabledTypes: Set<DocType>
    ): List<DocType> {
        val enabledOrdered = DocType.entries.filter { it in enabledTypes }
        if (enabledOrdered.isEmpty()) return emptyList()
        val populated = enabledOrdered.filter { type -> documents.any { it.type == type } }
        return populated.ifEmpty { enabledOrdered }
    }

    fun nextThemeMode(current: ThemeMode): ThemeMode = when (current) {
        ThemeMode.SYSTEM -> ThemeMode.DARK
        ThemeMode.DARK -> ThemeMode.LIGHT
        ThemeMode.LIGHT -> ThemeMode.SYSTEM
    }
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val settingsRepo = SettingsRepository(application)
    private val scanner = DocumentScanner(application)
    private val cacheManager = DocumentCacheManager(application)
    private val parser = DocumentParser(application)
    private var scanJob: Job? = null
    private var prewarmJob: Job? = null

    private val _rawDocuments = MutableStateFlow<List<DocumentItem>>(emptyList())
    val foundDocuments: StateFlow<List<DocumentItem>> = _rawDocuments.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    private val _isPullRefreshing = MutableStateFlow(false)
    private val _hasFullAccess = MutableStateFlow(scanner.hasFullStorageAccess())
    private val _selectedCategory = MutableStateFlow(DocType.PDF)
    private val _searchQuery = MutableStateFlow("")
    private val _isSearchActive = MutableStateFlow(false)
    private val _selectedDocIds = MutableStateFlow<Set<String>>(emptySet())
    private val _cacheSizeBytes = MutableStateFlow(0L)

    private var lastKnownStorageAccess = _hasFullAccess.value
    private val renamedIdAliases = HashMap<String, String>()
    private var activeViewingExternalDoc: DocumentItem? = null

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages = _messages.asSharedFlow()

    private val _openExternalDoc = MutableStateFlow<DocumentItem?>(null)
    val openExternalDoc: StateFlow<DocumentItem?> = _openExternalDoc.asStateFlow()

    val settingsFlow: StateFlow<AppSettings> = settingsRepo.settingsFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val homeUiState: StateFlow<HomeUiState> = combine(
        _rawDocuments,
        _isScanning,
        _isPullRefreshing,
        _hasFullAccess,
        _selectedCategory,
        _searchQuery,
        _isSearchActive,
        _selectedDocIds,
        _cacheSizeBytes,
        settingsFlow
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val rawDocs = values[0] as List<DocumentItem>
        val scanning = values[1] as Boolean
        val pullRefreshing = values[2] as Boolean
        val fullAccess = values[3] as Boolean
        val category = values[4] as DocType
        val query = values[5] as String
        val searchActive = values[6] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selectedIds = values[7] as Set<String>
        val cacheBytes = values[8] as Long
        val settings = values[9] as AppSettings

        val visibleTypes = DocumentQueryProcessor.resolveHomePillTypes(
            documents = rawDocs,
            enabledTypes = settings.visibleDocTypes
        )
        val rawCounts = DocType.entries.associateWith { type -> rawDocs.count { it.type == type } }
        val grouped = DocumentQueryProcessor.groupByType(
            documents = rawDocs,
            query = query,
            sortOption = settings.sortOption,
            visibleTypes = settings.visibleDocTypes
        )
        val effectiveCategory = when {
            category in visibleTypes -> category
            visibleTypes.isNotEmpty() -> visibleTypes.first()
            else -> category
        }
        val visibleIds = rawDocs
            .asSequence()
            .filter { it.type in settings.visibleDocTypes }
            .map { it.id }
            .toSet()
        val activeSelectedIds = if (selectedIds.isEmpty()) emptySet() else selectedIds.intersect(visibleIds)

        HomeUiState(
            isScanning = scanning,
            isPullRefreshing = pullRefreshing,
            hasFullStorageAccess = fullAccess,
            selectedCategory = effectiveCategory,
            visibleDocTypes = visibleTypes,
            searchQuery = query,
            isSearchActive = searchActive,
            selectedDocIds = activeSelectedIds,
            documentsByType = grouped,
            rawCountsByType = rawCounts,
            allDocumentsCount = visibleTypes.sumOf { rawCounts[it] ?: 0 },
            dotFolderDocCount = rawDocs.count { it.type in settings.visibleDocTypes && it.isFromDotFolder },
            cacheSizeBytes = cacheBytes,
            settings = settings
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeUiState())

    private val backgroundCloseListener: () -> Unit = {
        scanJob?.cancel()
        prewarmJob?.cancel()
    }

    init {
        DocViewApplication.registerBackgroundCloseListener(backgroundCloseListener)
        viewModelScope.launch {
            val initialSettings = settingsRepo.settingsFlow.first()
            val cachedDocs = withContext(Dispatchers.IO) {
                cacheManager.loadDocumentIndex()
                    .filter { initialSettings.scanDotFolders || !it.isFromDotFolder }
                    .filter { !it.isFilePath || File(it.path).exists() }
            }
            if (cachedDocs.isNotEmpty() && _rawDocuments.value.isEmpty()) {
                _rawDocuments.value = cachedDocs
                launchSmartPrewarm(cachedDocs, initialSettings)
            }
            refreshStorageAccessAndScan()
            refreshCacheSize()
        }
    }

    override fun onCleared() {
        prewarmJob?.cancel()
        DocViewApplication.unregisterBackgroundCloseListener(backgroundCloseListener)
        super.onCleared()
    }

    fun pullToRefreshDocuments() {
        refreshStorageAccessAndScan(userInitiated = true, isPullToRefresh = true)
    }

    fun recordDocumentOpened(doc: DocumentItem) {
        viewModelScope.launch(Dispatchers.IO) {
            cacheManager.recordRecentDocument(doc)
        }
    }

    fun refreshStorageAccessAndScan(
        userInitiated: Boolean = false,
        isPullToRefresh: Boolean = false
    ) {
        val currentAccess = scanner.hasFullStorageAccess()
        lastKnownStorageAccess = currentAccess
        _hasFullAccess.value = currentAccess
        scanDocuments(
            forceRestart = true,
            userInitiated = userInitiated,
            isPullToRefresh = isPullToRefresh
        )
    }

    fun onForegroundResume() {
        val currentAccess = scanner.hasFullStorageAccess()
        _hasFullAccess.value = currentAccess
        if (currentAccess != lastKnownStorageAccess) {
            lastKnownStorageAccess = currentAccess
            scanDocuments(forceRestart = true)
        }
    }

    fun scanDocuments(
        forceRestart: Boolean = false,
        userInitiated: Boolean = false,
        isPullToRefresh: Boolean = false
    ) {
        if (scanJob?.isActive == true) {
            if (!forceRestart) return
            scanJob?.cancel()
        }
        _isPullRefreshing.value = isPullToRefresh
        scanJob = viewModelScope.launch {
            val selfJob = currentCoroutineContext()[Job]
            _isScanning.value = true
            DocViewApplication.beginBackgroundWork(getApplication())
            try {
                val access = scanner.hasFullStorageAccess()
                lastKnownStorageAccess = access
                _hasFullAccess.value = access
                val currentSettings = settingsRepo.settingsFlow.first()
                val baselineMap = withContext(Dispatchers.IO) {
                    _rawDocuments.value
                        .filter { currentSettings.scanDotFolders || !it.isFromDotFolder }
                        .filter { !it.isFilePath || File(it.path).exists() }
                        .filter { it.isFilePath || it.uriString in currentSettings.importedFileUris }
                        .associateByTo(LinkedHashMap()) { it.id }
                }

                scanner.streamScanAllDocuments(currentSettings).collect { update ->
                    if (!update.isComplete) {
                        if (baselineMap.isEmpty()) {
                            _rawDocuments.value = update.documents
                        } else {
                            val merged = LinkedHashMap(baselineMap)
                            update.documents.forEach { merged[it.id] = it }
                            val mergedList = merged.values.toList()
                            if (mergedList != _rawDocuments.value) {
                                _rawDocuments.value = mergedList
                            }
                        }
                    } else {
                        _rawDocuments.value = update.documents
                        withContext(Dispatchers.IO) {
                            cacheManager.saveDocumentIndex(update.documents)
                        }
                        _cacheSizeBytes.value = cacheManager.pruneUnnecessaryCache(
                            validDocs = update.documents,
                            aggressive = currentSettings.autoCleanRenderCache
                        )
                        launchSmartPrewarm(update.documents, currentSettings)
                        if (userInitiated) {
                            val visibleCount = update.documents.count { it.type in currentSettings.visibleDocTypes }
                            _messages.tryEmit(
                                if (visibleCount == 1) "Scan complete • 1 document found"
                                else "Scan complete • $visibleCount documents found"
                            )
                        }
                    }
                }
            } finally {
                if (scanJob == null || scanJob === selfJob) {
                    _isScanning.value = false
                    _isPullRefreshing.value = false
                }
                DocViewApplication.endBackgroundWork(getApplication())
            }
        }
    }

    private fun launchSmartPrewarm(docs: List<DocumentItem>, settings: AppSettings) {
        if (docs.isEmpty()) return
        prewarmJob?.cancel()
        prewarmJob = viewModelScope.launch(Dispatchers.IO) {
            val recentIds = cacheManager.loadRecentDocIds(limit = 6)
            val byId = docs.associateBy { it.id }
            val prioritized = LinkedHashSet<DocumentItem>()
            for (recentId in recentIds) {
                byId[recentId]?.takeIf { it.type in settings.visibleDocTypes }?.let { prioritized.add(it) }
            }
            val sortedNewest = DocumentQueryProcessor.sortDocuments(
                docs.filter { it.type in settings.visibleDocTypes },
                settings.sortOption
            )
            for (candidate in sortedNewest) {
                if (prioritized.size >= 5) break
                prioritized.add(candidate)
            }
            if (prioritized.isNotEmpty()) {
                parser.prewarmDocuments(prioritized.toList(), settings, maxPrewarmDocs = 5)
                _cacheSizeBytes.value = cacheManager.getTotalCacheSizeBytes()
            }
        }
    }

    fun selectCategory(type: DocType) {
        _selectedCategory.value = type
    }

    fun toggleSearch() {
        val next = !_isSearchActive.value
        _isSearchActive.value = next
        if (!next) _searchQuery.value = ""
    }

    fun closeSearch() {
        if (_isSearchActive.value || _searchQuery.value.isNotEmpty()) {
            _isSearchActive.value = false
            _searchQuery.value = ""
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun cycleThemeMode(currentMode: ThemeMode) {
        val next = DocumentQueryProcessor.nextThemeMode(currentMode)
        viewModelScope.launch {
            settingsRepo.setThemeMode(next)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepo.setThemeMode(mode) }
    }

    fun setUseMaterialUi(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setUseMaterialUi(enabled) }
    }

    fun setSortOption(option: SortOption) {
        viewModelScope.launch { settingsRepo.setSortOption(option) }
    }

    fun setScanDotFolders(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepo.setScanDotFolders(enabled)
            scanDocuments(forceRestart = true)
        }
    }

    fun setShowDotFolderBadge(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setShowDotFolderBadge(enabled) }
    }

    fun setDefaultPageViewMode(mode: PageViewMode) {
        viewModelScope.launch { settingsRepo.setDefaultPageViewMode(mode) }
    }

    fun setShowExcelGridlines(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setShowExcelGridlines(enabled) }
    }

    fun setShowLineNumbers(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setShowLineNumbers(enabled) }
    }

    fun setReaderFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { settingsRepo.setReaderFontSizeSp(sizeSp) }
    }

    fun setAutoHideReaderUi(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoHideReaderUi(enabled) }
    }

    fun setAutoCleanRenderCache(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setAutoCleanRenderCache(enabled) }
    }

    fun setOfficeRenderFormat(format: OfficeRenderFormat) {
        viewModelScope.launch { settingsRepo.setOfficeRenderFormat(format) }
    }

    fun setPdfPageViewMode(mode: PageViewMode) {
        viewModelScope.launch { settingsRepo.setPdfPageViewMode(mode) }
    }

    fun setPdfInvertInDarkMode(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setPdfInvertInDarkMode(enabled) }
    }

    fun setPdfDoubleTapZoom(zoom: Float) {
        viewModelScope.launch { settingsRepo.setPdfDoubleTapZoom(zoom) }
    }

    fun setDocxRenderFormat(format: OfficeRenderFormat) {
        viewModelScope.launch { settingsRepo.setDocxRenderFormat(format) }
    }

    fun setDocxFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { settingsRepo.setDocxFontSizeSp(sizeSp) }
    }

    fun setXlsxRenderFormat(format: OfficeRenderFormat) {
        viewModelScope.launch { settingsRepo.setXlsxRenderFormat(format) }
    }

    fun setXlsxFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { settingsRepo.setXlsxFontSizeSp(sizeSp) }
    }

    fun setPptRenderFormat(format: OfficeRenderFormat) {
        viewModelScope.launch { settingsRepo.setPptRenderFormat(format) }
    }

    fun setShowPptSpeakerNotes(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setShowPptSpeakerNotes(enabled) }
    }

    fun setTextRenderFormat(format: OfficeRenderFormat) {
        viewModelScope.launch { settingsRepo.setTextRenderFormat(format) }
    }

    fun setTextPageViewMode(mode: PageViewMode) {
        viewModelScope.launch { settingsRepo.setTextPageViewMode(mode) }
    }

    fun setTextWordWrap(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setTextWordWrap(enabled) }
    }

    fun setTextMonospace(enabled: Boolean) {
        viewModelScope.launch { settingsRepo.setTextMonospace(enabled) }
    }

    fun setTextFontSizeSp(sizeSp: Float) {
        viewModelScope.launch { settingsRepo.setTextFontSizeSp(sizeSp) }
    }

    fun setDocTypeVisible(type: DocType, visible: Boolean) {
        viewModelScope.launch { settingsRepo.setDocTypeVisible(type, visible) }
    }

    fun setAllDocTypesVisible(visible: Boolean) {
        viewModelScope.launch { settingsRepo.setAllDocTypesVisible(visible) }
    }

    fun toggleSelection(docId: String) {
        val current = _selectedDocIds.value.toMutableSet()
        if (!current.add(docId)) {
            current.remove(docId)
        }
        _selectedDocIds.value = current
    }

    fun startSelectionWith(docId: String) {
        _selectedDocIds.value = _selectedDocIds.value + docId
    }

    fun selectAllInCurrentCategory() {
        val currentDocs = homeUiState.value.documentsByType[_selectedCategory.value].orEmpty()
        _selectedDocIds.value = currentDocs.map { it.id }.toSet()
    }

    fun clearSelection() {
        _selectedDocIds.value = emptySet()
    }

    fun getSelectedDocuments(): List<DocumentItem> {
        val ids = _selectedDocIds.value
        return _rawDocuments.value.filter { it.id in ids }
    }

    fun findDocumentById(docId: String): DocumentItem? {
        var resolvedId = docId
        val visited = HashSet<String>()
        while (resolvedId in renamedIdAliases && visited.add(resolvedId)) {
            resolvedId = renamedIdAliases[resolvedId] ?: break
        }
        return _rawDocuments.value.firstOrNull { it.id == resolvedId }
            ?: _openExternalDoc.value?.takeIf { it.id == resolvedId }
            ?: activeViewingExternalDoc?.takeIf { it.id == resolvedId }
    }

    fun renameDocument(doc: DocumentItem, newName: String, onRenamed: ((DocumentItem) -> Unit)? = null) {
        viewModelScope.launch {
            scanner.renameDocument(doc, newName)
                .onSuccess { updated ->
                    renamedIdAliases[doc.id] = updated.id
                    val nextList = _rawDocuments.value.map {
                        if (it.id == doc.id) updated else it
                    }
                    _rawDocuments.value = nextList
                    if (_openExternalDoc.value?.id == doc.id) {
                        _openExternalDoc.value = updated
                    }
                    if (activeViewingExternalDoc?.id == doc.id) {
                        activeViewingExternalDoc = updated
                    }
                    _selectedDocIds.value = _selectedDocIds.value.map {
                        if (it == doc.id) updated.id else it
                    }.toSet()
                    withContext(Dispatchers.IO) {
                        cacheManager.evictForDocuments(listOf(doc))
                        cacheManager.saveDocumentIndex(nextList)
                    }
                    refreshCacheSize()
                    _messages.tryEmit("Renamed to ${updated.name}")
                    onRenamed?.invoke(updated)
                }
                .onFailure { err ->
                    _messages.tryEmit(err.localizedMessage ?: "Rename failed")
                }
        }
    }

    fun deleteDocuments(docs: List<DocumentItem>, onComplete: (() -> Unit)? = null) {
        viewModelScope.launch {
            var deletedCount = 0
            val deletedDocs = ArrayList<DocumentItem>()
            val deletedIds = HashSet<String>()
            for (doc in docs) {
                if (scanner.deleteDocument(doc)) {
                    deletedCount++
                    deletedIds.add(doc.id)
                    deletedDocs.add(doc)
                }
            }
            if (deletedIds.isNotEmpty()) {
                val nextList = _rawDocuments.value.filterNot { it.id in deletedIds }
                _rawDocuments.value = nextList
                _selectedDocIds.value = _selectedDocIds.value - deletedIds
                withContext(Dispatchers.IO) {
                    cacheManager.evictForDocuments(deletedDocs)
                    cacheManager.saveDocumentIndex(nextList)
                }
                refreshCacheSize()
            }
            if (deletedCount > 0) {
                _messages.tryEmit(
                    if (deletedCount == 1) "Document deleted" else "$deletedCount documents deleted"
                )
                onComplete?.invoke()
            } else {
                _messages.tryEmit("Unable to delete document (read-only storage)")
            }
        }
    }

    fun onSafFolderPicked(uri: Uri) {
        viewModelScope.launch {
            settingsRepo.addCustomFolderUri(uri.toString())
            scanDocuments(forceRestart = true)
            _messages.tryEmit("Folder added to offline scanner")
        }
    }

    fun removeSafFolder(uriString: String) {
        viewModelScope.launch {
            settingsRepo.removeCustomFolderUri(uriString)
            scanDocuments(forceRestart = true)
        }
    }

    fun onImportedFilePicked(uri: Uri) {
        viewModelScope.launch {
            settingsRepo.addImportedFileUri(uri.toString())
            val resolved = scanner.resolveContentUriToDocument(uri)
            if (resolved != null) {
                _selectedCategory.value = resolved.type
            }
            scanDocuments(forceRestart = true)
        }
    }

    fun handleIncomingViewUri(uri: Uri) {
        viewModelScope.launch {
            val resolved = withContext(Dispatchers.IO) {
                scanner.resolveContentUriToDocument(uri)
            }
            if (resolved != null) {
                activeViewingExternalDoc = resolved
                _openExternalDoc.value = resolved
            }
        }
    }

    fun consumeExternalDocNavigation(doc: DocumentItem) {
        activeViewingExternalDoc = doc
        if (_openExternalDoc.value?.id == doc.id) {
            _openExternalDoc.value = null
        }
    }

    fun clearExternalDoc() {
        _openExternalDoc.value = null
        activeViewingExternalDoc = null
    }

    fun clearCache() {
        viewModelScope.launch {
            DocViewApplication.beginBackgroundWork(getApplication())
            try {
                val deletedBytes = withContext(Dispatchers.IO) {
                    cacheManager.clearAllCache()
                }
                _cacheSizeBytes.value = withContext(Dispatchers.IO) {
                    cacheManager.getTotalCacheSizeBytes()
                }
                _messages.tryEmit(
                    if (deletedBytes > 0) "Cache cleared" else "Cache is already clean"
                )
            } finally {
                DocViewApplication.endBackgroundWork(getApplication())
            }
        }
    }

    private fun refreshCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            _cacheSizeBytes.value = cacheManager.getTotalCacheSizeBytes()
        }
    }
}
