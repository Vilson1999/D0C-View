package com.example.data

enum class DocAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

data class WordRun(
    val text: String,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isUnderline: Boolean = false,
    val isStrike: Boolean = false,
    val fontSizePt: Float? = null,
    val colorRgb: Int? = null
)

class WordImage(
    val bytes: ByteArray,
    val widthPt: Float? = null,
    val heightPt: Float? = null,
    val alignment: DocAlignment = DocAlignment.CENTER
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is WordImage) return false
        return bytes.contentEquals(other.bytes) &&
            widthPt == other.widthPt &&
            heightPt == other.heightPt &&
            alignment == other.alignment
    }

    override fun hashCode(): Int {
        var result = bytes.contentHashCode()
        result = 31 * result + (widthPt?.hashCode() ?: 0)
        result = 31 * result + (heightPt?.hashCode() ?: 0)
        result = 31 * result + alignment.hashCode()
        return result
    }
}

enum class OfficeChartType {
    COLUMN,
    BAR,
    LINE,
    PIE,
    DOUGHNUT,
    AREA,
    SCATTER
}

data class ChartSeries(
    val name: String,
    val values: List<Double>,
    val colorRgb: Int? = null
)

data class OfficeChart(
    val title: String,
    val type: OfficeChartType,
    val categories: List<String>,
    val series: List<ChartSeries>
)

data class WordTableCell(
    val paragraphs: List<WordBlock>,
    val backgroundRgb: Int? = null,
    val colSpan: Int = 1
)

data class WordTableRow(
    val cells: List<WordTableCell>,
    val isHeader: Boolean = false
)

data class WordBlock(
    val text: String = "",
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isBullet: Boolean = false,
    val bulletPrefix: String = "•  ",
    val runs: List<WordRun> = emptyList(),
    val alignment: DocAlignment = DocAlignment.LEFT,
    val fontSizePt: Float? = null,
    val spaceBeforePt: Float = 0f,
    val spaceAfterPt: Float = 0f,
    val isPageBreak: Boolean = false,
    val hasBottomBorder: Boolean = false,
    val image: WordImage? = null,
    val chart: OfficeChart? = null,
    val tableRows: List<WordTableRow> = emptyList(),
    val tableHasBorders: Boolean = true
)

data class WordPage(
    val pageNumber: Int,
    val blocks: List<WordBlock>
)

data class SlidePage(
    val slideNumber: Int,
    val title: String,
    val bodyLines: List<String>,
    val notes: String = ""
)
