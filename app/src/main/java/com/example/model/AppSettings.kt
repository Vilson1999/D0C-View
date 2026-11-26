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

enum class OfficePageSizeOption(val label: String, val shortLabel: String) {
    AUTO("Auto (File Default)", "Auto"),
    A4("A4 (210 × 297 mm)", "A4"),
    A3("A3 (297 × 420 mm)", "A3"),
    LEGAL("Legal (8.5 × 14 in)", "Legal"),
    LETTER("Letter (8.5 × 11 in)", "Letter")
}

enum class OfficeMarginOption(val label: String, val shortLabel: String) {
    AUTO("Auto (File Default)", "Auto"),
    NARROW("Narrow (0.5 in)", "Narrow"),
    NORMAL("Normal (1.0 in)", "Normal")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
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
    val officePageSize: OfficePageSizeOption = OfficePageSizeOption.AUTO,
    val officeMargin: OfficeMarginOption = OfficeMarginOption.AUTO,
    // PDF folder settings
    val pdfPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val pdfInvertInDarkMode: Boolean = false,
    val pdfDoubleTapZoom: Float = 2f,
    // DOCX folder settings
    val docxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val docxPageSize: OfficePageSizeOption = OfficePageSizeOption.AUTO,
    val docxMargin: OfficeMarginOption = OfficeMarginOption.AUTO,
    val docxFontSizeSp: Float = 15f,
    // XLSX folder settings
    val xlsxRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val xlsxPageSize: OfficePageSizeOption = OfficePageSizeOption.AUTO,
    val xlsxMargin: OfficeMarginOption = OfficeMarginOption.AUTO,
    val xlsxFontSizeSp: Float = 14f,
    // PPT folder settings
    val pptRenderFormat: OfficeRenderFormat = OfficeRenderFormat.PDF,
    val pptPageSize: OfficePageSizeOption = OfficePageSizeOption.AUTO,
    val pptMargin: OfficeMarginOption = OfficeMarginOption.AUTO,
    val showPptSpeakerNotes: Boolean = true,
    // TEXT folder settings
    val textPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val textWordWrap: Boolean = true,
    val textMonospace: Boolean = true,
    val textFontSizeSp: Float = 14f,
    val visibleDocTypes: Set<DocType> = DocType.entries.toSet(),
    val customFolderUris: Set<String> = emptySet(),
    val importedFileUris: Set<String> = emptySet()
)
