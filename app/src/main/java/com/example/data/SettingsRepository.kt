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
import com.example.model.DocumentViewOrientation
import com.example.model.OfficeRenderFormat
import com.example.model.PageViewMode
import com.example.model.SortOption
import com.example.model.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "docvault_settings")

class SettingsRepository(private val context: Context) {

    private object Keys {
        val SELECTED_CATEGORY = stringPreferencesKey("selected_category")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_MATERIAL_UI = booleanPreferencesKey("use_material_ui")
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
        val READER_SCREEN_ORIENTATION = stringPreferencesKey("reader_screen_orientation")
        val PDF_PAGE_VIEW_MODE = stringPreferencesKey("pdf_page_view_mode")
        val PDF_VIEW_ORIENTATION = stringPreferencesKey("pdf_view_orientation")
        val PDF_INVERT_DARK = booleanPreferencesKey("pdf_invert_dark")
        val PDF_DOUBLE_TAP_ZOOM = floatPreferencesKey("pdf_double_tap_zoom")
        val DOCX_RENDER_FORMAT = stringPreferencesKey("docx_render_format")
        val DOCX_PAGE_VIEW_MODE = stringPreferencesKey("docx_page_view_mode")
        val DOCX_VIEW_ORIENTATION = stringPreferencesKey("docx_view_orientation")
        val DOCX_ORG_ZOOM_SCALE = floatPreferencesKey("docx_org_zoom_scale")
        val DOCX_FONT_SIZE_SP = floatPreferencesKey("docx_font_size_sp")
        val XLSX_RENDER_FORMAT = stringPreferencesKey("xlsx_render_format")
        val XLSX_PAGE_VIEW_MODE = stringPreferencesKey("xlsx_page_view_mode")
        val XLSX_VIEW_ORIENTATION = stringPreferencesKey("xlsx_view_orientation")
        val XLSX_ORG_ZOOM_SCALE = floatPreferencesKey("xlsx_org_zoom_scale")
        val XLSX_FONT_SIZE_SP = floatPreferencesKey("xlsx_font_size_sp")
        val PPT_RENDER_FORMAT = stringPreferencesKey("ppt_render_format")
        val PPT_PAGE_VIEW_MODE = stringPreferencesKey("ppt_page_view_mode")
        val PPT_VIEW_ORIENTATION = stringPreferencesKey("ppt_view_orientation")
        val PPT_ORG_ZOOM_SCALE = floatPreferencesKey("ppt_org_zoom_scale")
        val SHOW_PPT_SPEAKER_NOTES = booleanPreferencesKey("show_ppt_speaker_notes")
        val TEXT_RENDER_FORMAT = stringPreferencesKey("text_render_format")
        val TEXT_PAGE_VIEW_MODE = stringPreferencesKey("text_page_view_mode")
        val TEXT_VIEW_ORIENTATION = stringPreferencesKey("text_view_orientation")
        val TEXT_ORG_ZOOM_SCALE = floatPreferencesKey("text_org_zoom_scale")
        val TEXT_WORD_WRAP = booleanPreferencesKey("text_word_wrap")
        val TEXT_MONOSPACE = booleanPreferencesKey("text_monospace")
        val TEXT_FONT_SIZE_SP = floatPreferencesKey("text_font_size_sp")
        val CUSTOM_FOLDER_URIS = stringSetPreferencesKey("custom_folder_uris")
        val IMPORTED_FILE_URIS = stringSetPreferencesKey("imported_file_uris")
        val HIDDEN_DOC_TYPES = stringSetPreferencesKey("hidden_doc_types")
    }

    private fun parseOrientation(raw: String?): DocumentViewOrientation =
        raw?.let { runCatching { DocumentViewOrientation.valueOf(it) }.getOrNull() } ?: DocumentViewOrientation.AUTO

    private fun parsePageViewMode(raw: String?, fallback: PageViewMode): PageViewMode =
        raw?.let { runCatching { PageViewMode.valueOf(it) }.getOrNull() } ?: fallback

    val settingsFlow: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        val hiddenTypeNames = prefs[Keys.HIDDEN_DOC_TYPES] ?: DEFAULT_HIDDEN_TYPE_NAMES
        val defaultPageMode = parsePageViewMode(prefs[Keys.DEFAULT_PAGE_VIEW_MODE], PageViewMode.SINGLE_PAGE)
        val defaultOfficeFormat = prefs[Keys.OFFICE_RENDER_FORMAT]?.let {
            runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
        } ?: OfficeRenderFormat.PDF
        AppSettings(
            selectedCategory = prefs[Keys.SELECTED_CATEGORY]?.let {
                runCatching { DocType.valueOf(it) }.getOrNull()
            } ?: DocType.PDF,
            themeMode = prefs[Keys.THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            useMaterialUi = prefs[Keys.USE_MATERIAL_UI] ?: true,
            scanDotFolders = prefs[Keys.SCAN_DOT_FOLDERS] ?: true,
            showDotFolderBadge = prefs[Keys.SHOW_DOT_FOLDER_BADGE] ?: true,
            defaultPageViewMode = defaultPageMode,
            sortOption = prefs[Keys.SORT_OPTION]?.let {
                runCatching { SortOption.valueOf(it) }.getOrNull()
            } ?: SortOption.DATE_DESC,
            showExcelGridlines = prefs[Keys.SHOW_EXCEL_GRIDLINES] ?: true,
            showLineNumbers = prefs[Keys.SHOW_LINE_NUMBERS] ?: true,
            readerFontSizeSp = prefs[Keys.READER_FONT_SIZE_SP] ?: 15f,
            autoHideReaderUi = prefs[Keys.AUTO_HIDE_READER_UI] ?: true,
            autoCleanRenderCache = prefs[Keys.AUTO_CLEAN_RENDER_CACHE] ?: true,
            officeRenderFormat = defaultOfficeFormat,
            readerScreenOrientation = parseOrientation(prefs[Keys.READER_SCREEN_ORIENTATION]),
            pdfPageViewMode = parsePageViewMode(prefs[Keys.PDF_PAGE_VIEW_MODE], defaultPageMode),
            pdfViewOrientation = parseOrientation(prefs[Keys.PDF_VIEW_ORIENTATION]),
            pdfInvertInDarkMode = prefs[Keys.PDF_INVERT_DARK] ?: false,
            pdfDoubleTapZoom = (prefs[Keys.PDF_DOUBLE_TAP_ZOOM] ?: 2f).coerceIn(1.5f, 3.5f),
            docxRenderFormat = prefs[Keys.DOCX_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: defaultOfficeFormat,
            docxPageViewMode = parsePageViewMode(prefs[Keys.DOCX_PAGE_VIEW_MODE], defaultPageMode),
            docxViewOrientation = parseOrientation(prefs[Keys.DOCX_VIEW_ORIENTATION]),
            docxOrgZoomScale = (prefs[Keys.DOCX_ORG_ZOOM_SCALE] ?: 1f).coerceIn(1f, 4f),
            docxFontSizeSp = (prefs[Keys.DOCX_FONT_SIZE_SP] ?: prefs[Keys.READER_FONT_SIZE_SP] ?: 15f).coerceIn(11f, 24f),
            xlsxRenderFormat = prefs[Keys.XLSX_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: defaultOfficeFormat,
            xlsxPageViewMode = parsePageViewMode(prefs[Keys.XLSX_PAGE_VIEW_MODE], defaultPageMode),
            xlsxViewOrientation = parseOrientation(prefs[Keys.XLSX_VIEW_ORIENTATION]),
            xlsxOrgZoomScale = (prefs[Keys.XLSX_ORG_ZOOM_SCALE] ?: 1f).coerceIn(1f, 4f),
            xlsxFontSizeSp = (prefs[Keys.XLSX_FONT_SIZE_SP] ?: 14f).coerceIn(11f, 22f),
            pptRenderFormat = prefs[Keys.PPT_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: defaultOfficeFormat,
            pptPageViewMode = parsePageViewMode(prefs[Keys.PPT_PAGE_VIEW_MODE], defaultPageMode),
            pptViewOrientation = parseOrientation(prefs[Keys.PPT_VIEW_ORIENTATION]),
            pptOrgZoomScale = (prefs[Keys.PPT_ORG_ZOOM_SCALE] ?: 1f).coerceIn(1f, 4f),
            showPptSpeakerNotes = prefs[Keys.SHOW_PPT_SPEAKER_NOTES] ?: true,
            textRenderFormat = prefs[Keys.TEXT_RENDER_FORMAT]?.let {
                runCatching { OfficeRenderFormat.valueOf(it) }.getOrNull()
            } ?: OfficeRenderFormat.PDF,
            textPageViewMode = parsePageViewMode(prefs[Keys.TEXT_PAGE_VIEW_MODE], defaultPageMode),
            textViewOrientation = parseOrientation(prefs[Keys.TEXT_VIEW_ORIENTATION]),
            textOrgZoomScale = (prefs[Keys.TEXT_ORG_ZOOM_SCALE] ?: 1f).coerceIn(1f, 4f),
            textWordWrap = prefs[Keys.TEXT_WORD_WRAP] ?: true,
            textMonospace = prefs[Keys.TEXT_MONOSPACE] ?: true,
            textFontSizeSp = (prefs[Keys.TEXT_FONT_SIZE_SP] ?: prefs[Keys.READER_FONT_SIZE_SP] ?: 14f).coerceIn(11f, 24f),
            visibleDocTypes = DocType.entries.filterNotTo(LinkedHashSet()) { it.name in hiddenTypeNames },
            customFolderUris = prefs[Keys.CUSTOM_FOLDER_URIS] ?: emptySet(),
            importedFileUris = prefs[Keys.IMPORTED_FILE_URIS] ?: emptySet()
        )
    }

    suspend fun setSelectedCategory(type: DocType) {
        context.dataStore.edit { it[Keys.SELECTED_CATEGORY] = type.name }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setUseMaterialUi(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_MATERIAL_UI] = enabled }
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

    suspend fun setReaderScreenOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.READER_SCREEN_ORIENTATION] = orientation.name }
    }

    suspend fun setPdfPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.PDF_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setPdfViewOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.PDF_VIEW_ORIENTATION] = orientation.name }
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

    suspend fun setDocxPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.DOCX_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setDocxViewOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.DOCX_VIEW_ORIENTATION] = orientation.name }
    }

    suspend fun setDocxOrgZoomScale(scale: Float) {
        context.dataStore.edit { it[Keys.DOCX_ORG_ZOOM_SCALE] = scale.coerceIn(1f, 4f) }
    }

    suspend fun setDocxFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.DOCX_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 24f) }
    }

    suspend fun setXlsxRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.XLSX_RENDER_FORMAT] = format.name }
    }

    suspend fun setXlsxPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.XLSX_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setXlsxViewOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.XLSX_VIEW_ORIENTATION] = orientation.name }
    }

    suspend fun setXlsxOrgZoomScale(scale: Float) {
        context.dataStore.edit { it[Keys.XLSX_ORG_ZOOM_SCALE] = scale.coerceIn(1f, 4f) }
    }

    suspend fun setXlsxFontSizeSp(sizeSp: Float) {
        context.dataStore.edit { it[Keys.XLSX_FONT_SIZE_SP] = sizeSp.coerceIn(11f, 22f) }
    }

    suspend fun setPptRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.PPT_RENDER_FORMAT] = format.name }
    }

    suspend fun setPptPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.PPT_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setPptViewOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.PPT_VIEW_ORIENTATION] = orientation.name }
    }

    suspend fun setPptOrgZoomScale(scale: Float) {
        context.dataStore.edit { it[Keys.PPT_ORG_ZOOM_SCALE] = scale.coerceIn(1f, 4f) }
    }

    suspend fun setShowPptSpeakerNotes(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_PPT_SPEAKER_NOTES] = enabled }
    }

    suspend fun setTextRenderFormat(format: OfficeRenderFormat) {
        context.dataStore.edit { it[Keys.TEXT_RENDER_FORMAT] = format.name }
    }

    suspend fun setTextPageViewMode(mode: PageViewMode) {
        context.dataStore.edit { it[Keys.TEXT_PAGE_VIEW_MODE] = mode.name }
    }

    suspend fun setTextViewOrientation(orientation: DocumentViewOrientation) {
        context.dataStore.edit { it[Keys.TEXT_VIEW_ORIENTATION] = orientation.name }
    }

    suspend fun setTextOrgZoomScale(scale: Float) {
        context.dataStore.edit { it[Keys.TEXT_ORG_ZOOM_SCALE] = scale.coerceIn(1f, 4f) }
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

    suspend fun setPageViewModeForType(type: DocType, mode: PageViewMode) {
        when (type) {
            DocType.PDF -> setPdfPageViewMode(mode)
            DocType.DOC -> setDocxPageViewMode(mode)
            DocType.EXCEL -> setXlsxPageViewMode(mode)
            DocType.PPT -> setPptPageViewMode(mode)
            DocType.TEXT -> setTextPageViewMode(mode)
        }
    }

    suspend fun setViewOrientationForType(type: DocType, orientation: DocumentViewOrientation) {
        when (type) {
            DocType.PDF -> setPdfViewOrientation(orientation)
            DocType.DOC -> setDocxViewOrientation(orientation)
            DocType.EXCEL -> setXlsxViewOrientation(orientation)
            DocType.PPT -> setPptViewOrientation(orientation)
            DocType.TEXT -> setTextViewOrientation(orientation)
        }
    }

    suspend fun setRenderFormatForType(type: DocType, format: OfficeRenderFormat) {
        when (type) {
            DocType.DOC -> setDocxRenderFormat(format)
            DocType.EXCEL -> setXlsxRenderFormat(format)
            DocType.PPT -> setPptRenderFormat(format)
            DocType.TEXT -> setTextRenderFormat(format)
            DocType.PDF -> Unit
        }
    }

    suspend fun setOrgZoomScaleForType(type: DocType, scale: Float) {
        when (type) {
            DocType.DOC -> setDocxOrgZoomScale(scale)
            DocType.EXCEL -> setXlsxOrgZoomScale(scale)
            DocType.PPT -> setPptOrgZoomScale(scale)
            DocType.TEXT -> setTextOrgZoomScale(scale)
            DocType.PDF -> Unit
        }
    }

    suspend fun setDocTypeVisible(type: DocType, visible: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_DOC_TYPES] ?: DEFAULT_HIDDEN_TYPE_NAMES
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

    companion object {
        private val DEFAULT_HIDDEN_TYPE_NAMES: Set<String> =
            AppSettings.DEFAULT_HIDDEN_DOC_TYPES.map { it.name }.toSet()
    }
}
