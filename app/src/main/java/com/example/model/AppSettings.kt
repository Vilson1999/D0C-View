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
    // PDF folder settings
    val pdfPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val pdfInvertInDarkMode: Boolean = false,
    val pdfDoubleTapZoom: Float = 2f,
    // DOCX folder settings
    val docxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val docxFontSizeSp: Float = 15f,
    // XLSX folder settings
    val xlsxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val xlsxFontSizeSp: Float = 14f,
    // PPT folder settings
    val pptRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val showPptSpeakerNotes: Boolean = true,
    // TEXT folder settings
    val textRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val textPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val textWordWrap: Boolean = true,
    val textMonospace: Boolean = true,
    val textFontSizeSp: Float = 14f,
    val visibleDocTypes: Set<DocType> = DocType.entries.toSet(),
    val customFolderUris: Set<String> = emptySet(),
    val importedFileUris: Set<String> = emptySet()
)
