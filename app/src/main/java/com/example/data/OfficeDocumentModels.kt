package com.example.data

import kotlin.math.abs

enum class DocAlignment {
    LEFT,
    CENTER,
    RIGHT,
    JUSTIFY
}

data class OfficePageLayout(
    val widthPt: Float = 595f,
    val heightPt: Float = 842f,
    val marginLeftPt: Float = 54f,
    val marginRightPt: Float = 54f,
    val marginTopPt: Float = 54f,
    val marginBottomPt: Float = 54f,
    val isLandscape: Boolean = widthPt > heightPt,
    val detectedPaperName: String = "A4",
    val detectedMarginName: String = "Normal"
)

data class OfficeContentSummary(
    val detectedPageSize: String,
    val detectedMargin: String,
    val effectivePageSize: String,
    val effectiveMargin: String,
    val isLandscape: Boolean,
    val widthPt: Float,
    val heightPt: Float,
    val textCount: Int,
    val imageCount: Int,
    val chartCount: Int,
    val tableCount: Int,
    val unitCount: Int,
    val unitLabel: String
)

object OfficeLayoutResolver {

    val DEFAULT_WORD_A4 = OfficePageLayout(
        widthPt = 595f,
        heightPt = 842f,
        marginLeftPt = 54f,
        marginRightPt = 54f,
        marginTopPt = 54f,
        marginBottomPt = 54f,
        isLandscape = false,
        detectedPaperName = "A4",
        detectedMarginName = "Normal"
    )

    val DEFAULT_EXCEL_A4_LANDSCAPE = OfficePageLayout(
        widthPt = 842f,
        heightPt = 595f,
        marginLeftPt = 36f,
        marginRightPt = 36f,
        marginTopPt = 36f,
        marginBottomPt = 36f,
        isLandscape = true,
        detectedPaperName = "A4",
        detectedMarginName = "Narrow"
    )

    val DEFAULT_SLIDE_16_9 = OfficePageLayout(
        widthPt = 960f,
        heightPt = 540f,
        marginLeftPt = 44f,
        marginRightPt = 44f,
        marginTopPt = 34f,
        marginBottomPt = 30f,
        isLandscape = true,
        detectedPaperName = "16:9",
        detectedMarginName = "Normal"
    )

    fun classifyPaperName(widthPt: Float, heightPt: Float): String {
        val shortEdge = minOf(widthPt, heightPt)
        val longEdge = maxOf(widthPt, heightPt)
        return when {
            abs(shortEdge - 595f) <= 18f && abs(longEdge - 842f) <= 22f -> "A4"
            abs(shortEdge - 842f) <= 24f && abs(longEdge - 1191f) <= 28f -> "A3"
            abs(shortEdge - 612f) <= 18f && abs(longEdge - 792f) <= 20f -> "Letter"
            abs(shortEdge - 612f) <= 18f && abs(longEdge - 1008f) <= 24f -> "Legal"
            abs(shortEdge - 420f) <= 16f && abs(longEdge - 595f) <= 18f -> "A5"
            abs(shortEdge - 792f) <= 20f && abs(longEdge - 1224f) <= 24f -> "Tabloid"
            abs(longEdge / shortEdge.coerceAtLeast(1f) - (16f / 9f)) <= 0.08f && shortEdge >= 450f -> "16:9"
            abs(longEdge / shortEdge.coerceAtLeast(1f) - (4f / 3f)) <= 0.06f && shortEdge >= 450f -> "4:3"
            else -> "Custom"
        }
    }

    fun classifyMarginName(leftPt: Float, rightPt: Float, topPt: Float, bottomPt: Float): String {
        val avgH = (leftPt + rightPt) / 2f
        val avgV = (topPt + bottomPt) / 2f
        return when {
            avgH <= 42f && avgV <= 60f -> "Narrow"
            avgH >= 88f -> "Wide"
            else -> "Normal"
        }
    }

    fun detectTextLayout(text: String): OfficePageLayout {
        val sampleLines = text.lineSequence().take(1200).filter { it.isNotBlank() }.toList()
        if (sampleLines.isEmpty()) return DEFAULT_WORD_A4

        val expandedLengths = sampleLines
            .map { it.replace("\t", "    ").length }
            .sorted()
        val p90Len = expandedLengths[(expandedLengths.size * 0.9f).toInt().coerceIn(0, expandedLengths.lastIndex)]
        val maxLen = expandedLengths.last()

        val indentedOrTabLines = sampleLines.count { it.startsWith(" ") || it.startsWith("\t") || it.contains('\t') }
        val codeSymbolLines = sampleLines.count { line ->
            line.contains('{') || line.contains('}') || line.contains(';') ||
                line.contains("->") || line.contains("::") || line.count { c -> c == ',' } >= 3
        }
        val isTechnicalOrWide = p90Len > 84 || maxLen > 102 ||
            indentedOrTabLines * 3 >= sampleLines.size ||
            codeSymbolLines * 4 >= sampleLines.size

        val (rawW, rawH, landscape) = when {
            p90Len > 125 || maxLen > 148 -> Triple(1191f, 842f, true)
            p90Len > 84 || maxLen > 102 -> Triple(842f, 595f, true)
            else -> Triple(595f, 842f, false)
        }
        val marginPt = if (isTechnicalOrWide) 36f else 54f
        return buildLayoutFromSizeAndMargins(
            rawWidthPt = rawW,
            rawHeightPt = rawH,
            leftPt = marginPt,
            rightPt = marginPt,
            topPt = marginPt,
            bottomPt = marginPt,
            forceLandscape = landscape
        )
    }

    fun buildLayoutFromSizeAndMargins(
        rawWidthPt: Float,
        rawHeightPt: Float,
        leftPt: Float,
        rightPt: Float,
        topPt: Float,
        bottomPt: Float,
        forceLandscape: Boolean? = null
    ): OfficePageLayout {
        val w = when (forceLandscape) {
            true -> maxOf(rawWidthPt, rawHeightPt)
            false -> minOf(rawWidthPt, rawHeightPt)
            null -> rawWidthPt
        }.coerceIn(280f, 1800f)
        val h = when (forceLandscape) {
            true -> minOf(rawWidthPt, rawHeightPt)
            false -> maxOf(rawWidthPt, rawHeightPt)
            null -> rawHeightPt
        }.coerceIn(280f, 1800f)
        val clampedL = leftPt.coerceIn(14f, w * 0.28f)
        val clampedR = rightPt.coerceIn(14f, w * 0.28f)
        val clampedT = topPt.coerceIn(16f, h * 0.25f)
        val clampedB = bottomPt.coerceIn(16f, h * 0.25f)
        return OfficePageLayout(
            widthPt = w,
            heightPt = h,
            marginLeftPt = clampedL,
            marginRightPt = clampedR,
            marginTopPt = clampedT,
            marginBottomPt = clampedB,
            isLandscape = w > h,
            detectedPaperName = classifyPaperName(w, h),
            detectedMarginName = classifyMarginName(clampedL, clampedR, clampedT, clampedB)
        )
    }

    fun applyOrientation(
        layout: OfficePageLayout,
        orientation: com.example.model.DocumentViewOrientation
    ): OfficePageLayout {
        return when (orientation) {
            com.example.model.DocumentViewOrientation.AUTO -> layout
            com.example.model.DocumentViewOrientation.PORTRAIT -> if (!layout.isLandscape) {
                layout
            } else {
                buildLayoutFromSizeAndMargins(
                    rawWidthPt = layout.widthPt,
                    rawHeightPt = layout.heightPt,
                    leftPt = layout.marginLeftPt,
                    rightPt = layout.marginRightPt,
                    topPt = layout.marginTopPt,
                    bottomPt = layout.marginBottomPt,
                    forceLandscape = false
                )
            }
            com.example.model.DocumentViewOrientation.LANDSCAPE -> if (layout.isLandscape) {
                layout
            } else {
                buildLayoutFromSizeAndMargins(
                    rawWidthPt = layout.widthPt,
                    rawHeightPt = layout.heightPt,
                    leftPt = layout.marginLeftPt,
                    rightPt = layout.marginRightPt,
                    topPt = layout.marginTopPt,
                    bottomPt = layout.marginBottomPt,
                    forceLandscape = true
                )
            }
        }
    }

    /**
     * Auto-detects landscape vs portrait purely from length (width) vs height:
     * - length < height -> Portrait (false)
     * - height < length -> Landscape (true)
     */
    fun isLandscapeFromDimensions(lengthPt: Float, heightPt: Float): Boolean =
        lengthPt > heightPt

    /**
     * Normalizes an Office layout to A4 dimensions (595 x 842 pt Portrait or 842 x 595 pt Landscape)
     * for Original View of DOCX, XLSX, and PPT while respecting the active orientation choice.
     */
    fun withA4Default(
        layout: OfficePageLayout,
        orientation: com.example.model.DocumentViewOrientation
    ): OfficePageLayout {
        val targetLandscape = when (orientation) {
            com.example.model.DocumentViewOrientation.PORTRAIT -> false
            com.example.model.DocumentViewOrientation.LANDSCAPE -> true
            com.example.model.DocumentViewOrientation.AUTO -> layout.isLandscape
        }
        return buildLayoutFromSizeAndMargins(
            rawWidthPt = if (targetLandscape) 842f else 595f,
            rawHeightPt = if (targetLandscape) 595f else 842f,
            leftPt = layout.marginLeftPt,
            rightPt = layout.marginRightPt,
            topPt = layout.marginTopPt,
            bottomPt = layout.marginBottomPt,
            forceLandscape = targetLandscape
        )
    }
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
    val leftIndentPt: Float = 0f,
    val firstLineIndentPt: Float = 0f,
    val lineSpacingMult: Float? = null,
    val isPageBreak: Boolean = false,
    val hasBottomBorder: Boolean = false,
    val image: WordImage? = null,
    val chart: OfficeChart? = null,
    val tableRows: List<WordTableRow> = emptyList(),
    val tableHasBorders: Boolean = true
)

data class WordDocumentData(
    val blocks: List<WordBlock>,
    val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_WORD_A4
)

data class WordPage(
    val pageNumber: Int,
    val blocks: List<WordBlock>
)

data class SlidePage(
    val slideNumber: Int,
    val title: String,
    val bodyLines: List<String>,
    val notes: String = "",
    val blocks: List<WordBlock> = emptyList(),
    val backgroundRgb: Int? = null
)

data class PresentationDeck(
    val slides: List<SlidePage>,
    val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_SLIDE_16_9
)

data class ExcelCell(
    val colIndex: Int,
    val text: String,
    val isNumericOrDate: Boolean = false,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val textColorRgb: Int? = null,
    val backgroundRgb: Int? = null,
    val alignment: DocAlignment = DocAlignment.LEFT,
    val colSpan: Int = 1
)

data class ExcelRow(
    val rowNumber: Int,
    val cells: List<ExcelCell>,
    val isHeaderLikely: Boolean = false
)

data class ExcelSheet(
    val name: String,
    val columnWidthsChars: Map<Int, Float> = emptyMap(),
    val maxColumns: Int = 0,
    val rows: List<ExcelRow> = emptyList(),
    val embeddedBlocks: List<WordBlock> = emptyList(),
    val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE
)

data class ExcelWorkbook(
    val sheets: List<ExcelSheet>,
    val layout: OfficePageLayout = sheets.firstOrNull()?.layout ?: OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE
)
