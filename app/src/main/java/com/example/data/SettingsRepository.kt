package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.PageViewMode
import com.example.model.SortOption
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docvault_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SCAN_DOT_FOLDERS = booleanPreferencesKey("scan_dot_folders")
        val SHOW_DOT_FOLDER_BADGE = booleanPreferencesKey("show_dot_folder_badge")
        val DEFAULT_PAGE_VIEW_MODE = stringPreferencesKey("default_page_view_mode")
        val SORT_OPTION = stringPreferencesKey("sort_option")
        val XML_SYNTAX_HIGHLIGHT = booleanPreferencesKey("xml_syntax_highlight")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val READER_FONT_SIZE_SP = floatPreferencesKey("reader_font_size_sp")
        val AUTO_CLEAN_RENDER_CACHE = booleanPreferencesKey("auto_clean_render_cache")
        val CUSTOM_FOLDER_URIS = stringSetPreferencesKey("custom_folder_uris")
        val IMPORTED_FILE_URIS = stringSetPreferencesKey("imported_file_uris")
        val HIDDEN_DOC_TYPES = stringSetPreferencesKey("hidden_doc_types")
    }

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val hiddenTypeNames = prefs[Keys.HIDDEN_DOC_TYPES] ?: emptySet()
        AppSettings(
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            scanDotFolders = prefs[Keys.SCAN_DOT_FOLDERS] ?: true,
            showDotFolderBadge = prefs[Keys.SHOW_DOT_FOLDER_BADGE] ?: true,
            defaultPageViewMode = prefs[Keys.DEFAULT_PAGE_VIEW_MODE]?.let {
                runCatching { PageViewMode.valueOf(it) }.getOrNull()
            } ?: PageViewMode.SINGLE_PAGE,
            sortOption = prefs[Keys.SORT_OPTION]?.let {
                runCatching { SortOption.valueOf(it) }.getOrNull()
            } ?: SortOption.DATE_DESC,
            xmlSyntaxHighlight = prefs[Keys.XML_SYNTAX_HIGHLIGHT] ?: true,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            readerFontSizeSp = prefs[Keys.READER_FONT_SIZE_SP] ?: 15f,
            autoCleanRenderCache = prefs[Keys.AUTO_CLEAN_RENDER_CACHE] ?: true,
            visibleDocTypes = DocType.entries.filterNotTo(LinkedHashSet()) { it.name in hiddenTypeNames },
            customFolderUris = prefs[Keys.CUSTOM_FOLDER_URIS] ?: emptySet(),
            importedFileUris = prefs[Keys.IMPORTED_FILE_URIS] ?: emptySet()
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setScanDotFolders(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SCAN_DOT_FOLDERS] = enabled }
    }

    suspend fun setShowDotFolderBadge(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_DOT_FOLDER_BADGE] = enabled }
    }

    suspend fun setDefaultPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.DEFAULT_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setSortOption(option: SortOption) {
        context.dataStore.edit { it[Keys.SORT_OPTION] = option.name }
    }

    suspend fun setXmlSyntaxHighlight(enabled: Boolean) {
        context.dataStore.edit { it[Keys.XML_SYNTAX_HIGHLIGHT] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LINE_NUMBERS] = enabled }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.READER_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 24f) }
    }

    suspend fun setAutoCleanRenderCache(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAN_RENDER_CACHE] = enabled }
    }

    suspend fun setDocTypeVisible(type: DocType, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_DOC_TYPES] ?: emptySet()
            prefs[Keys.HIDDEN_DOC_TYPES] = if (visible) current - type.name else current + type.name
        }
    }

    suspend fun setAllDocTypesVisible(visible: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HIDDEN_DOC_TYPES] = if (visible) {
                emptySet()
            } else {
                DocType.entries.map { it.name }.toSet()
            }
        }
    }

    suspend fun addCustomFolderUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FOLDER_URIS] ?: emptySet()
            prefs[Keys.CUSTOM_FOLDER_URIS] = current + uriString
        }
    }

    suspend fun removeCustomFolderUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.CUSTOM_FOLDER_URIS] ?: emptySet()
            prefs[Keys.CUSTOM_FOLDER_URIS] = current - uriString
        }
    }

    suspend fun addImportedFileUri(uriString: String) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.IMPORTED_FILE_URIS] ?: emptySet()
            prefs[Keys.IMPORTED_FILE_URIS] = current + uriString
        }
    }
}
