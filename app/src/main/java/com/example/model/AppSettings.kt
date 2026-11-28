package com.example.model

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

enum class OfficeRenderFormat(val label: String, val shortLabel: String) {
    PDF("PDF View", "PDF"),
    ORIGINAL("Original Format", "Original")
}

enum class DocumentViewOrientation(val label: String) {
    AUTO("Auto"),
    PORTRAIT("Portrait"),
    LANDSCAPE("Landscape")
}

data class AppSettings(
    val selectedCategory: DocType = DocType.PDF,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val useMaterialUi: Boolean = true,
    val scanDotFolders: Boolean = true,
    val showDotFolderBadge: Boolean = true,
    val defaultPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val showExcelGridlines: Boolean = true,
    val showLineNumbers: Boolean = true,
    val readerFontSizeSp: Float = 15f,
    val autoHideReaderUi: Boolean = true,
    val autoCleanRenderCache: Boolean = true,
    val officeRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val readerScreenOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    // PDF folder settings
    val pdfPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val pdfViewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val pdfInvertInDarkMode: Boolean = false,
    val pdfDoubleTapZoom: Float = 2f,
    // DOCX folder settings
    val docxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val docxPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val docxViewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val docxOrgZoomScale: Float = 1f,
    val docxFontSizeSp: Float = 15f,
    // XLSX folder settings
    val xlsxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val xlsxPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val xlsxViewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val xlsxOrgZoomScale: Float = 1f,
    val xlsxFontSizeSp: Float = 14f,
    // PPT folder settings
    val pptRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val pptPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val pptViewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val pptOrgZoomScale: Float = 1f,
    val showPptSpeakerNotes: Boolean = true,
    // TEXT folder settings
    val textRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val textPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val textViewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val textOrgZoomScale: Float = 1f,
    val textWordWrap: Boolean = true,
    val textMonospace: Boolean = true,
    val textFontSizeSp: Float = 14f,
    val visibleDocTypes: Set<DocType> = DEFAULT_VISIBLE_DOC_TYPES,
    val customFolderUris: Set<String> = emptySet(),
    val importedFileUris: Set<String> = emptySet()
) {
    fun pageViewModeFor(type: DocType): PageViewMode = when (type) {
        DocType.PDF -> pdfPageViewMode
        DocType.DOC -> docxPageViewMode
        DocType.EXCEL -> xlsxPageViewMode
        DocType.PPT -> pptPageViewMode
        DocType.TEXT -> textPageViewMode
    }

    fun viewOrientationFor(type: DocType): DocumentViewOrientation = when (type) {
        DocType.PDF -> pdfViewOrientation
        DocType.DOC -> docxViewOrientation
        DocType.EXCEL -> xlsxViewOrientation
        DocType.PPT -> pptViewOrientation
        DocType.TEXT -> textViewOrientation
    }

    fun renderFormatFor(type: DocType): OfficeRenderFormat = when (type) {
        DocType.DOC -> docxRenderFormat
        DocType.EXCEL -> xlsxRenderFormat
        DocType.PPT -> pptRenderFormat
        DocType.TEXT -> textRenderFormat
        DocType.PDF -> OfficeRenderFormat.PDF
    }

    fun orgZoomScaleFor(type: DocType): Float = when (type) {
        DocType.DOC -> docxOrgZoomScale
        DocType.EXCEL -> xlsxOrgZoomScale
        DocType.PPT -> pptOrgZoomScale
        DocType.TEXT -> textOrgZoomScale
        DocType.PDF -> 1f
    }

    companion object {
        val DEFAULT_HIDDEN_DOC_TYPES: Set<DocType> = setOf(DocType.TEXT)
        val DEFAULT_VISIBLE_DOC_TYPES: Set<DocType> =
            DocType.entries.filterNotTo(LinkedHashSet()) { it in DEFAULT_HIDDEN_DOC_TYPES }
    }
}
