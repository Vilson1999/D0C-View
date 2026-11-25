package com.example.model

enum class ThemeMode(val label: String) {
    SYSTEM("Follow System"),
    LIGHT("Light Mode"),
    DARK("Dark Mode")
}

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val scanDotFolders: Boolean = true,
    val showDotFolderBadge: Boolean = true,
    val defaultPageViewMode: PageViewMode = PageViewMode.SINGLE_PAGE,
    val sortOption: SortOption = SortOption.DATE_DESC,
    val xmlSyntaxHighlight: Boolean = true,
    val showLineNumbers: Boolean = true,
    val readerFontSizeSp: Float = 15f,
    val autoHideReaderUi: Boolean = true,
    val autoCleanRenderCache: Boolean = true,
    val visibleDocTypes: Set<DocType> = DocType.entries.toSet(),
    val customFolderUris: Set<String> = emptySet(),
    val importedFileUris: Set<String> = emptySet()
)
