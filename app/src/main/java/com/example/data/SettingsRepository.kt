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
import com.example.model.OfficeMarginOption
import com.example.model.OfficePageSizeOption
import com.example.model.OfficeRenderFormat
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
        val SHOW_EXCEL_GRIDLINES = booleanPreferencesKey("show_excel_gridlines")
        val SHOW_LINE_NUMBERS = booleanPreferencesKey("show_line_numbers")
        val READER_FONT_SIZE_SP = floatPreferencesKey("reader_font_size_sp")
        val AUTO_HIDE_READER_UI = booleanPreferencesKey("auto_hide_reader_ui")
        val AUTO_CLEAN_RENDER_CACHE = booleanPreferencesKey("auto_clean_render_cache")
        val OFFICE_RENDER_FORMAT = stringPreferencesKey("office_render_format")
        val OFFICE_PAGE_SIZE = stringPreferencesKey("office_page_size")
        val OFFICE_MARGIN = stringPreferencesKey("office_margin")
        val PDF_PAGE_VIEW_MODE = stringPreferencesKey("pdf_page_view_mode")
        val PDF_INVERT_DARK = booleanPreferencesKey("pdf_invert_dark")
        val PDF_DOUBLE_TAP_ZOOM = floatPreferencesKey("pdf_double_tap_zoom")
        val DOCX_RENDER_FORMAT = stringPreferencesKey("docx_render_format")
        val DOCX_PAGE_SIZE = stringPreferencesKey("docx_page_size")
        val DOCX_MARGIN = stringPreferencesKey("docx_margin")
        val DOCX_FONT_SIZE_SP = floatPreferencesKey("docx_font_size_sp")
        val XLSX_RENDER_FORMAT = stringPreferencesKey("xlsx_render_format")
        val XLSX_PAGE_SIZE = stringPreferencesKey("xlsx_page_size")
        val XLSX_MARGIN = stringPreferencesKey("xlsx_margin")
        val XLSX_FONT_SIZE_SP = floatPreferencesKey("xlsx_font_size_sp")
        val PPT_RENDER_FORMAT = stringPreferencesKey("ppt_render_format")
        val PPT_PAGE_SIZE = stringPreferencesKey("ppt_page_size")
        val PPT_MARGIN = stringPreferencesKey("ppt_margin")
        val SHOW_PPT_SPEAKER_NOTES = booleanPreferencesKey("show_ppt_speaker_notes")
        val TEXT_PAGE_VIEW_MODE = stringPreferencesKey("text_page_view_mode")
        val TEXT_WORD_WRAP = booleanPreferencesKey("text_word_wrap")
        val TEXT_MONOSPACE = booleanPreferencesKey("text_monospace")
        val TEXT_FONT_SIZE_SP = floatPreferencesKey("text_font_size_sp")
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
            showExcelGridlines = prefs[Keys.SHOW_EXCEL_GRIDLINES] ?: true,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            readerFontSizeSp = prefs[Keys.READER_FONT_SIZE_SP] ?: 15f,
            autoHideReaderUi = prefs[Keys.AUTO_HIDE_READER_UI] ?: true,
            autoCleanRenderCache = prefs[Keys.AUTO_CLEAN_RENDER_CACHE] ?: true,
            officeRenderFormat = prefs[Keys.OFFICE_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: OfficeRenderFormat.PDF,
            officePageSize = prefs[Keys.OFFICE_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: OfficePageSizeOption.AUTO,
            officeMargin = prefs[Keys.OFFICE_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: OfficeMarginOption.AUTO,
            pdfPageViewMode = prefs[Keys.PDF_PAGE_VIEW_MODE]?.let {
                runCatching { PageViewMode.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.DEFAULT_PAGE_VIEW_MODE]?.let {
                runCatching { PageViewMode.valueOf(it) }.getOrNull()
            } ?: PageViewMode.SINGLE_PAGE),
            pdfInvertInDarkMode = prefs[Keys.PDF_INVERT_DARK] ?: false,
            pdfDoubleTapZoom = (prefs[Keys.PDF_DOUBLE_TAP_ZOOM] ?: 2f).coerceIn(1.5f, 3.5f),
            docxRenderFormat = prefs[Keys.DOCX_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: OfficeRenderFormat.PDF),
            docxPageSize = prefs[Keys.DOCX_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: OfficePageSizeOption.AUTO),
            docxMargin = prefs[Keys.DOCX_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: OfficeMarginOption.AUTO),
            docxFontSizeSp = (prefs[Keys.DOCX_FONT_SIZE_SP] ?: prefs[Keys.READER_FONT_SIZE_SP] ?: 15f).coerceIn(11f, 24f),
            xlsxRenderFormat = prefs[Keys.XLSX_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: OfficeRenderFormat.PDF),
            xlsxPageSize = prefs[Keys.XLSX_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: OfficePageSizeOption.AUTO),
            xlsxMargin = prefs[Keys.XLSX_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: OfficeMarginOption.AUTO),
            xlsxFontSizeSp = (prefs[Keys.XLSX_FONT_SIZE_SP] ?: 14f).coerceIn(11f, 22f),
            pptRenderFormat = prefs[Keys.PPT_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.OFFICE_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: OfficeRenderFormat.PDF),
            pptPageSize = prefs[Keys.PPT_PAGE_SIZE]?.let {
                runCatching { OfficePageSizeOption.valueOf(it) }.getOrNull()
            } ?: OfficePageSizeOption.AUTO,
            pptMargin = prefs[Keys.PPT_MARGIN]?.let {
                runCatching { OfficeMarginOption.valueOf(it) }.getOrNull()
            } ?: OfficeMarginOption.AUTO,
            showPptSpeakerNotes = prefs[Keys.SHOW_PPT_SPEAKER_NOTES] ?: true,
            textPageViewMode = prefs[Keys.TEXT_PAGE_VIEW_MODE]?.let {
                runCatching { PageViewMode.valueOf(it) }.getOrNull()
            } ?: (prefs[Keys.DEFAULT_PAGE_VIEW_MODE]?.let {
                runCatching { PageViewMode.valueOf(it) }.getOrNull()
            } ?: PageViewMode.SINGLE_PAGE),
            textWordWrap = prefs[Keys.TEXT_WORD_WRAP] ?: true,
            textMonospace = prefs[Keys.TEXT_MONOSPACE] ?: true,
            textFontSizeSp = (prefs[Keys.TEXT_FONT_SIZE_SP] ?: prefs[Keys.READER_FONT_SIZE_SP] ?: 14f).coerceIn(11f, 24f),
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

    suspend fun setShowExcelGridlines(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_EXCEL_GRIDLINES] = enabled }
    }

    suspend fun setShowLineNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LINE_NUMBERS] = enabled }
    }

    suspend fun setReaderFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.READER_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 24f) }
    }

    suspend fun setAutoHideReaderUi(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_HIDE_READER_UI] = enabled }
    }

    suspend fun setAutoCleanRenderCache(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_CLEAN_RENDER_CACHE] = enabled }
    }

    suspend fun setOfficeRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.OFFICE_RENDER_FORMAT] = format.name }
    }

    suspend fun setOfficePageSize(size: OfficePageSizeOption) {
        context.dataStore.edit { it[Keys.OFFICE_PAGE_SIZE] = size.name }
    }

    suspend fun setOfficeMargin(margin: OfficeMarginOption) {
        context.dataStore.edit { it[Keys.OFFICE_MARGIN] = margin.name }
    }

    suspend fun setPdfPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.PDF_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setPdfInvertInDarkMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.PDF_INVERT_DARK] = enabled }
    }

    suspend fun setPdfDoubleTapZoom(zoom: Float) {
        context.dataStore.edit { it[Keys.PDF_DOUBLE_TAP_ZOOM] = zoom.coerceIn(1.5f, 3.5f) }
    }

    suspend fun setDocxRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.DOCX_RENDER_FORMAT] = format.name }
    }

    suspend fun setDocxPageSize(size: OfficePageSizeOption) {
        context.dataStore.edit { it[Keys.DOCX_PAGE_SIZE] = size.name }
    }

    suspend fun setDocxMargin(margin: OfficeMarginOption) {
        context.dataStore.edit { it[Keys.DOCX_MARGIN] = margin.name }
    }

    suspend fun setDocxFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.DOCX_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 24f) }
    }

    suspend fun setXlsxRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.XLSX_RENDER_FORMAT] = format.name }
    }

    suspend fun setXlsxPageSize(size: OfficePageSizeOption) {
        context.dataStore.edit { it[Keys.XLSX_PAGE_SIZE] = size.name }
    }

    suspend fun setXlsxMargin(margin: OfficeMarginOption) {
        context.dataStore.edit { it[Keys.XLSX_MARGIN] = margin.name }
    }

    suspend fun setXlsxFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.XLSX_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 22f) }
    }

    suspend fun setPptRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.PPT_RENDER_FORMAT] = format.name }
    }

    suspend fun setPptPageSize(size: OfficePageSizeOption) {
        context.dataStore.edit { it[Keys.PPT_PAGE_SIZE] = size.name }
    }

    suspend fun setPptMargin(margin: OfficeMarginOption) {
        context.dataStore.edit { it[Keys.PPT_MARGIN] = margin.name }
    }

    suspend fun setShowPptSpeakerNotes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PPT_SPEAKER_NOTES] = enabled }
    }

    suspend fun setTextPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.TEXT_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setTextWordWrap(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TEXT_WORD_WRAP] = enabled }
    }

    suspend fun setTextMonospace(enabled: Boolean) {
        context.dataStore.edit { it[Keys.TEXT_MONOSPACE] = enabled }
    }

    suspend fun setTextFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.TEXT_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 24f) }
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
