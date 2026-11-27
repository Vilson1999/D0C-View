package com.example.data

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * Pure-Kotlin offline OOXML Excel (.xlsx / .xlsm / .xltx) workbook parser.
 * Uses [OfficeXmlFormatParser.newPullParser] so it works identically on Android
 * devices and in plain JVM unit tests.
 */
object OoxmlWorkbookParser {

    private const val MAX_TOTAL_UNZIPPED_BYTES = 28L * 1024L * 1024L
    private const val MAX_SHEETS = 24
    private const val MAX_ROWS_PER_SHEET = 1500
    private const val MAX_COLS_PER_SHEET = 42

    private data class SheetRef(
        val name: String,
        val path: String
    )

    private data class FontDef(
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val colorRgb: Int? = null
    )

    private data class CellXf(
        val numFmtId: Int = 0,
        val fontId: Int = 0,
        val fillId: Int = 0,
        val alignment: DocAlignment? = null
    )

    private data class StyleBook(
        val customNumFmts: Map<Int, String> = emptyMap(),
        val fonts: List<FontDef> = emptyList(),
        val fillsRgb: List<Int?> = emptyList(),
        val cellXfs: List<CellXf> = emptyList()
    )

    private data class CellMergeSpan(
        val startRow: Int,
        val endRow: Int,
        val startCol: Int,
        val endCol: Int
    )

    fun parseXlsxBytes(bytes: ByteArray): ExcelWorkbook {
        val parts = LinkedHashMap<String, ByteArray>()
        var totalRead = 0L

        runCatching {
            ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val data = zis.readBytes()
                        totalRead += data.size
                        if (totalRead <= MAX_TOTAL_UNZIPPED_BYTES) {
                            parts[entry.name.trimStart('/').lowercase()] = data
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        if (parts.isEmpty()) return ExcelWorkbook(emptyList())

        val sharedStrings = parseSharedStrings(parts["xl/sharedstrings.xml"])
        val styleBook = parseStyles(parts["xl/styles.xml"])
        val sheetRefs = resolveSheetRefs(parts)

        val sheets = sheetRefs.take(MAX_SHEETS).mapNotNull { ref ->
            val sheetBytes = parts[ref.path] ?: return@mapNotNull null
            parseWorksheet(
                name = ref.name,
                sheetPath = ref.path,
                sheetBytes = sheetBytes,
                sharedStrings = sharedStrings,
                styles = styleBook,
                parts = parts
            )
        }

        val primaryLayout = sheets.firstOrNull()?.layout ?: OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE
        return ExcelWorkbook(sheets = sheets, layout = primaryLayout)
    }

    private fun resolveSheetRefs(parts: Map<String, ByteArray>): List<SheetRef> {
        val workbookBytes = parts["xl/workbook.xml"]
        val wbRels = parseRelationships(
            relsBytes = parts["xl/_rels/workbook.xml.rels"],
            baseDir = "xl/"
        )

        val visibleSheets = mutableListOf<SheetRef>()
        val allSheets = mutableListOf<SheetRef>()

        if (workbookBytes != null) {
            runCatching {
                val parser = OfficeXmlFormatParser.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(workbookBytes), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        val local = (parser.name ?: "").substringAfter(':')
                        if (local == "sheet") {
                            val name = parser.getAttributeValue(null, "name")?.trim()
                                ?.ifBlank { "Sheet ${allSheets.size + 1}" }
                                ?: "Sheet ${allSheets.size + 1}"
                            val rId = parser.getAttributeValue(null, "r:id")
                                ?: parser.getAttributeValue(null, "id")
                            val state = parser.getAttributeValue(null, "state") ?: ""
                            val resolvedPath = rId?.let { wbRels[it] }
                                ?: "xl/worksheets/sheet${allSheets.size + 1}.xml"
                            val normalized = if (parts.containsKey(resolvedPath)) {
                                resolvedPath
                            } else {
                                parts.keys.firstOrNull { it.endsWith("/${resolvedPath.substringAfterLast('/')}") }
                                    ?: resolvedPath
                            }
                            val ref = SheetRef(name = name, path = normalized)
                            allSheets.add(ref)
                            if (!state.equals("hidden", ignoreCase = true) &&
                                !state.equals("veryHidden", ignoreCase = true)
                            ) {
                                visibleSheets.add(ref)
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }

        if (visibleSheets.isNotEmpty()) return visibleSheets
        if (allSheets.isNotEmpty()) return allSheets

        val sheetPattern = Regex("""xl/worksheets/sheet(\d+)\.xml""")
        return parts.keys
            .filter { sheetPattern.matches(it) }
            .sortedBy { sheetPattern.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
            .mapIndexed { idx, path -> SheetRef(name = "Sheet ${idx + 1}", path = path) }
    }

    private fun parseSharedStrings(bytes: ByteArray?): List<String> {
        if (bytes == null) return emptyList()
        val result = mutableListOf<String>()
        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var inSi = false
            var phoneticDepth = -1
            val currentText = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = (parser.name ?: "").substringAfter(':')
                if (phoneticDepth >= 0) {
                    if (event == XmlPullParser.END_TAG && parser.depth == phoneticDepth) {
                        phoneticDepth = -1
                    }
                    event = parser.next()
                    continue
                }
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "si" -> {
                                inSi = true
                                currentText.clear()
                            }
                            "rPh" -> if (inSi) phoneticDepth = parser.depth
                            "t" -> if (inSi) currentText.append(parser.nextText())
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (local == "si" && inSi) {
                            result.add(currentText.toString())
                            inSi = false
                        }
                    }
                }
                event = parser.next()
            }
        }
        return result
    }

    private fun parseStyles(bytes: ByteArray?): StyleBook {
        if (bytes == null) return StyleBook()
        val customNumFmts = HashMap<Int, String>()
        val fonts = mutableListOf<FontDef>()
        val fillsRgb = mutableListOf<Int?>()
        val cellXfs = mutableListOf<CellXf>()

        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

            var section = ""
            var inFont = false
            var fontBold = false
            var fontItalic = false
            var fontColor: Int? = null

            var inFill = false
            var patternType = ""
            var fillFgRgb: Int? = null

            var inXf = false
            var xfNumFmtId = 0
            var xfFontId = 0
            var xfFillId = 0
            var xfAlignment: DocAlignment? = null

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = (parser.name ?: "").substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "numFmts", "fonts", "fills", "cellXfs", "cellStyleXfs" -> section = local
                            "numFmt" -> if (section == "numFmts") {
                                val id = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull()
                                val code = parser.getAttributeValue(null, "formatCode")
                                if (id != null && !code.isNullOrBlank()) {
                                    customNumFmts[id] = code
                                }
                            }
                            "font" -> if (section == "fonts") {
                                inFont = true
                                fontBold = false
                                fontItalic = false
                                fontColor = null
                            }
                            "b" -> if (inFont) {
                                val v = parser.getAttributeValue(null, "val")
                                fontBold = v != "0" && !v.equals("false", ignoreCase = true)
                            }
                            "i" -> if (inFont) {
                                val v = parser.getAttributeValue(null, "val")
                                fontItalic = v != "0" && !v.equals("false", ignoreCase = true)
                            }
                            "color" -> if (inFont) {
                                fontColor = parseColorAttrs(parser)
                            }
                            "fill" -> if (section == "fills") {
                                inFill = true
                                patternType = ""
                                fillFgRgb = null
                            }
                            "patternFill" -> if (inFill) {
                                patternType = parser.getAttributeValue(null, "patternType") ?: ""
                            }
                            "fgColor" -> if (inFill) {
                                fillFgRgb = parseColorAttrs(parser)
                            }
                            "xf" -> if (section == "cellXfs") {
                                inXf = true
                                xfNumFmtId = parser.getAttributeValue(null, "numFmtId")?.toIntOrNull() ?: 0
                                xfFontId = parser.getAttributeValue(null, "fontId")?.toIntOrNull() ?: 0
                                xfFillId = parser.getAttributeValue(null, "fillId")?.toIntOrNull() ?: 0
                                xfAlignment = null
                            }
                            "alignment" -> if (inXf) {
                                xfAlignment = when (parser.getAttributeValue(null, "horizontal")?.lowercase()) {
                                    "center", "centercontinuous" -> DocAlignment.CENTER
                                    "right" -> DocAlignment.RIGHT
                                    "justify" -> DocAlignment.JUSTIFY
                                    "left" -> DocAlignment.LEFT
                                    else -> null
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (local) {
                            "numFmts", "fonts", "fills", "cellXfs", "cellStyleXfs" -> {
                                if (section == local) section = ""
                            }
                            "font" -> if (inFont) {
                                fonts.add(FontDef(fontBold, fontItalic, fontColor))
                                inFont = false
                            }
                            "fill" -> if (inFill) {
                                val usableRgb = if (
                                    patternType.isBlank() ||
                                    patternType.equals("none", true) ||
                                    patternType.equals("gray125", true) ||
                                    patternType.equals("gray0625", true)
                                ) {
                                    null
                                } else {
                                    fillFgRgb
                                }
                                fillsRgb.add(usableRgb)
                                inFill = false
                            }
                            "xf" -> if (inXf) {
                                cellXfs.add(CellXf(xfNumFmtId, xfFontId, xfFillId, xfAlignment))
                                inXf = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        return StyleBook(customNumFmts, fonts, fillsRgb, cellXfs)
    }

    private fun parseWorksheet(
        name: String,
        sheetPath: String,
        sheetBytes: ByteArray,
        sharedStrings: List<String>,
        styles: StyleBook,
        parts: Map<String, ByteArray>
    ): ExcelSheet {
        val colWidths = HashMap<Int, Float>()
        val merges = mutableListOf<CellMergeSpan>()
        val rowMap = sortedMapOf<Int, MutableList<ExcelCell>>()

        var rawPaperW = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.heightPt // 595f short
        var rawPaperH = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.widthPt  // 842f long
        var hasExplicitPaperDimensions = false
        var explicitOrientation: Boolean? = null // true = landscape, false = portrait
        var marginLeftPt = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.marginLeftPt
        var marginRightPt = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.marginRightPt
        var marginTopPt = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.marginTopPt
        var marginBottomPt = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE.marginBottomPt

        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(sheetBytes), "UTF-8")

            var currentRowNum = 0
            var nextAutoCol = 0
            var inCell = false
            var cellCol = 0
            var cellRow = 0
            var cellType = ""
            var cellStyleIdx = 0
            var inInlineStr = false
            val rawValue = StringBuilder()
            val inlineText = StringBuilder()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = (parser.name ?: "").substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "col" -> {
                                val minC = parser.getAttributeValue(null, "min")?.toIntOrNull()?.minus(1)
                                val maxC = parser.getAttributeValue(null, "max")?.toIntOrNull()?.minus(1)
                                val width = parser.getAttributeValue(null, "width")?.toFloatOrNull()
                                if (minC != null && maxC != null && width != null && width > 0f && minC < MAX_COLS_PER_SHEET) {
                                    val clampedMax = maxC.coerceAtMost(MAX_COLS_PER_SHEET - 1)
                                    for (c in minC.coerceAtLeast(0)..clampedMax) {
                                        colWidths[c] = width
                                    }
                                }
                            }
                            "mergeCell" -> {
                                parseMergeRange(parser.getAttributeValue(null, "ref"))?.let {
                                    merges.add(it)
                                }
                            }
                            "row" -> {
                                val rAttr = parser.getAttributeValue(null, "r")?.toIntOrNull()
                                currentRowNum = rAttr ?: (currentRowNum + 1)
                                nextAutoCol = 0
                            }
                            "c" -> {
                                if (rowMap.size < MAX_ROWS_PER_SHEET) {
                                    val ref = parser.getAttributeValue(null, "r")
                                    val parsedRef = parseCellRef(ref)
                                    cellCol = parsedRef?.first ?: nextAutoCol
                                    cellRow = parsedRef?.second ?: currentRowNum
                                    nextAutoCol = cellCol + 1
                                    if (cellCol in 0 until MAX_COLS_PER_SHEET) {
                                        inCell = true
                                        cellType = parser.getAttributeValue(null, "t") ?: "n"
                                        cellStyleIdx = parser.getAttributeValue(null, "s")?.toIntOrNull() ?: 0
                                        inInlineStr = false
                                        rawValue.clear()
                                        inlineText.clear()
                                    }
                                }
                            }
                            "is" -> if (inCell) inInlineStr = true
                            "v" -> if (inCell) rawValue.append(parser.nextText())
                            "t" -> if (inCell && inInlineStr) inlineText.append(parser.nextText())
                            "pageMargins" -> {
                                parser.getAttributeValue(null, "left")?.toFloatOrNull()?.let { marginLeftPt = it * 72f }
                                parser.getAttributeValue(null, "right")?.toFloatOrNull()?.let { marginRightPt = it * 72f }
                                parser.getAttributeValue(null, "top")?.toFloatOrNull()?.let { marginTopPt = it * 72f }
                                parser.getAttributeValue(null, "bottom")?.toFloatOrNull()?.let { marginBottomPt = it * 72f }
                            }
                            "pageSetup" -> {
                                val pwAttr = parseDimensionToPt(parser.getAttributeValue(null, "paperWidth"))
                                val phAttr = parseDimensionToPt(parser.getAttributeValue(null, "paperHeight"))
                                if (pwAttr != null && pwAttr > 0f && phAttr != null && phAttr > 0f) {
                                    rawPaperW = pwAttr
                                    rawPaperH = phAttr
                                    hasExplicitPaperDimensions = abs(pwAttr - phAttr) > 1f
                                } else {
                                    val paperSizeId = parser.getAttributeValue(null, "paperSize")?.toIntOrNull()
                                    if (paperSizeId != null) {
                                        val (pw, ph) = excelPaperDimensions(paperSizeId)
                                        rawPaperW = pw
                                        rawPaperH = ph
                                    }
                                }
                                when (parser.getAttributeValue(null, "orientation")?.lowercase()) {
                                    "landscape" -> explicitOrientation = true
                                    "portrait" -> explicitOrientation = false
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (local) {
                            "is" -> inInlineStr = false
                            "c" -> if (inCell) {
                                val xf = styles.cellXfs.getOrNull(cellStyleIdx)
                                val font = xf?.fontId?.let { styles.fonts.getOrNull(it) }
                                val bgRgb = xf?.fillId?.let { styles.fillsRgb.getOrNull(it) }
                                val numFmtId = xf?.numFmtId ?: 0
                                val numFmtCode = styles.customNumFmts[numFmtId]

                                val raw = rawValue.toString()
                                val (displayText, isNumericOrDate) = resolveCellValue(
                                    cellType = cellType,
                                    raw = raw,
                                    inline = inlineText.toString(),
                                    sharedStrings = sharedStrings,
                                    numFmtId = numFmtId,
                                    numFmtCode = numFmtCode
                                )

                                // Keep non-blank cells or styled header cells within compact column bounds
                                if (displayText.isNotEmpty() || (bgRgb != null && cellCol < 16)) {
                                    val align = xf?.alignment ?: if (isNumericOrDate) DocAlignment.RIGHT else DocAlignment.LEFT
                                    val cell = ExcelCell(
                                        colIndex = cellCol,
                                        text = displayText,
                                        isNumericOrDate = isNumericOrDate,
                                        isBold = font?.isBold == true,
                                        isItalic = font?.isItalic == true,
                                        textColorRgb = font?.colorRgb,
                                        backgroundRgb = bgRgb,
                                        alignment = align
                                    )
                                    rowMap.getOrPut(cellRow) { mutableListOf() }.add(cell)
                                }
                                inCell = false
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        // Apply horizontal merge colSpans & filter swallowed slave cells cleanly
        val swallowed = HashSet<Pair<Int, Int>>()
        val topLeftSpans = HashMap<Pair<Int, Int>, Int>()
        for (m in merges) {
            val spanCols = (m.endCol - m.startCol + 1).coerceAtLeast(1)
            if (spanCols > 1) {
                topLeftSpans[m.startRow to m.startCol] = spanCols
            }
            for (r in m.startRow..m.endRow) {
                for (c in m.startCol..m.endCol) {
                    if (r != m.startRow || c != m.startCol) {
                        swallowed.add(r to c)
                    }
                }
            }
        }

        var maxColCount = 0
        val builtRows = mutableListOf<ExcelRow>()
        for ((rowNum, cells) in rowMap) {
            val filteredCells = cells
                .filter { (rowNum to it.colIndex) !in swallowed }
                .map { cell ->
                    val span = topLeftSpans[rowNum to cell.colIndex] ?: 1
                    val endCol = (cell.colIndex + span).coerceAtMost(MAX_COLS_PER_SHEET)
                    if (endCol > maxColCount) maxColCount = endCol
                    cell.copy(colSpan = span)
                }
                .sortedBy { it.colIndex }

            if (filteredCells.any { it.text.isNotBlank() || it.backgroundRgb != null }) {
                val hasStyledBg = filteredCells.any { it.backgroundRgb != null }
                val boldCount = filteredCells.count { it.isBold }
                val isFirstNonEmpty = builtRows.isEmpty()
                val headerLikely = (isFirstNonEmpty && (hasStyledBg || boldCount > 0)) ||
                    (builtRows.size < 4 && boldCount >= (filteredCells.size + 1) / 2 && filteredCells.size >= 2)
                builtRows.add(
                    ExcelRow(
                        rowNumber = rowNum,
                        cells = filteredCells,
                        isHeaderLikely = headerLikely
                    )
                )
            }
        }

        val embeddedBlocks = extractSheetDrawings(sheetPath, parts)
        val effectiveCols = maxColCount.coerceAtLeast(1)
        val forceLandscape = when {
            explicitOrientation != null -> explicitOrientation
            hasExplicitPaperDimensions -> OfficeLayoutResolver.isLandscapeFromDimensions(rawPaperW, rawPaperH)
            builtRows.isEmpty() && embeddedBlocks.isEmpty() -> false
            else -> {
                val estimatedTableWidthPt = (0 until effectiveCols).sumOf { colIdx ->
                    val explicitChars = colWidths[colIdx]
                    val maxSampleChars = builtRows.asSequence()
                        .take(80)
                        .flatMap { it.cells.asSequence() }
                        .filter { it.colIndex == colIdx && it.colSpan == 1 }
                        .maxOfOrNull { it.text.length } ?: 6
                    val w = if (explicitChars != null && explicitChars > 0f) {
                        explicitChars * 6.0f + 10f
                    } else {
                        maxSampleChars * 5.6f + 14f
                    }
                    w.coerceIn(42f, 200f).toDouble()
                }.toFloat()
                val maxDrawingWidthPt = embeddedBlocks.mapNotNull { block ->
                    block.image?.widthPt ?: if (block.chart != null) 340f else null
                }.maxOrNull() ?: 0f
                val estimatedContentWidthPt = maxOf(estimatedTableWidthPt, maxDrawingWidthPt)

                val estimatedRowsHeightPt = builtRows.sumOf { row ->
                    val maxLines = row.cells.maxOfOrNull { cell ->
                        val text = cell.text
                        if (text.isEmpty()) 1
                        else maxOf(text.count { it == '\n' } + 1, (text.length / 26) + 1)
                    } ?: 1
                    (18f + (maxLines - 1).coerceAtMost(4) * 12f).toDouble()
                }.toFloat()
                val drawingsHeightPt = embeddedBlocks.sumOf { block ->
                    (block.image?.heightPt ?: if (block.chart != null) 210f else 0f).toDouble()
                }.toFloat()
                val estimatedContentHeightPt = (if (builtRows.isNotEmpty()) 36f else 0f) +
                    estimatedRowsHeightPt +
                    drawingsHeightPt

                OfficeLayoutResolver.isLandscapeFromDimensions(
                    lengthPt = estimatedContentWidthPt,
                    heightPt = estimatedContentHeightPt
                )
            }
        }
        val sheetLayout = OfficeLayoutResolver.buildLayoutFromSizeAndMargins(
            rawWidthPt = rawPaperW,
            rawHeightPt = rawPaperH,
            leftPt = marginLeftPt,
            rightPt = marginRightPt,
            topPt = marginTopPt,
            bottomPt = marginBottomPt,
            forceLandscape = forceLandscape
        )

        return ExcelSheet(
            name = name,
            columnWidthsChars = colWidths,
            maxColumns = effectiveCols,
            rows = builtRows,
            embeddedBlocks = embeddedBlocks,
            layout = sheetLayout
        )
    }

    private fun excelPaperDimensions(paperSizeId: Int): Pair<Float, Float> = when (paperSizeId) {
        1 -> 612f to 792f    // Letter
        3 -> 792f to 1224f   // Tabloid
        5 -> 612f to 1008f   // Legal
        8 -> 842f to 1191f   // A3
        9 -> 595f to 842f    // A4
        11 -> 420f to 595f   // A5
        else -> 595f to 842f
    }

    private fun parseDimensionToPt(raw: String?): Float? {
        if (raw.isNullOrBlank()) return null
        val match = Regex("""([0-9.]+)\s*(mm|cm|in|pt)?""", RegexOption.IGNORE_CASE).find(raw.trim())
            ?: return null
        val num = match.groupValues[1].toFloatOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "mm" -> num * 72f / 25.4f
            "cm" -> num * 72f / 2.54f
            "in" -> num * 72f
            else -> num
        }
    }

    private fun extractSheetDrawings(
        sheetPath: String,
        parts: Map<String, ByteArray>
    ): List<WordBlock> {
        val sheetDir = sheetPath.substringBeforeLast('/', "")
        val sheetFile = sheetPath.substringAfterLast('/')
        val relsPath = if (sheetDir.isEmpty()) "_rels/$sheetFile.rels" else "$sheetDir/_rels/$sheetFile.rels"
        val sheetRels = parseRelationships(parts[relsPath], baseDir = if (sheetDir.isEmpty()) "" else "$sheetDir/")

        val blocks = mutableListOf<WordBlock>()
        for (targetPath in sheetRels.values) {
            if (!targetPath.contains("drawing", ignoreCase = true) || !targetPath.endsWith(".xml")) continue
            val drawingBytes = parts[targetPath] ?: continue
            val drawingDir = targetPath.substringBeforeLast('/', "")
            val drawingFile = targetPath.substringAfterLast('/')
            val drawingRelsPath = "$drawingDir/_rels/$drawingFile.rels"
            val drawingRels = parseRelationships(parts[drawingRelsPath], baseDir = "$drawingDir/")

            runCatching {
                val parser = OfficeXmlFormatParser.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(drawingBytes), "UTF-8")

                var pendingW: Float? = null
                var pendingH: Float? = null
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "xdr:ext", "a:ext" -> {
                                val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull()
                                val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull()
                                if (cx != null && cx > 0f) pendingW = cx / 12700f
                                if (cy != null && cy > 0f) pendingH = cy / 12700f
                            }
                            "a:blip" -> {
                                val rId = parser.getAttributeValue(null, "r:embed")
                                    ?: parser.getAttributeValue(null, "r:link")
                                val imgPath = rId?.let { drawingRels[it] }
                                val imgBytes = imgPath?.let { resolvePartBytes(it, parts) }
                                if (imgBytes != null) {
                                    blocks.add(
                                        WordBlock(
                                            image = WordImage(
                                                bytes = imgBytes,
                                                widthPt = pendingW,
                                                heightPt = pendingH,
                                                alignment = DocAlignment.CENTER
                                            ),
                                            spaceBeforePt = 6f,
                                            spaceAfterPt = 6f
                                        )
                                    )
                                }
                            }
                            "c:chart" -> {
                                val rId = parser.getAttributeValue(null, "r:id")
                                val chartPath = rId?.let { drawingRels[it] }
                                val chartBytes = chartPath?.let { resolvePartBytes(it, parts) }
                                val chart = chartBytes?.let { OfficeXmlFormatParser.parseChartXml(it) }
                                if (chart != null) {
                                    blocks.add(
                                        WordBlock(
                                            chart = chart,
                                            alignment = DocAlignment.CENTER,
                                            spaceBeforePt = 6f,
                                            spaceAfterPt = 6f
                                        )
                                    )
                                }
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        return blocks
    }

    private fun resolvePartBytes(path: String, parts: Map<String, ByteArray>): ByteArray? {
        parts[path]?.let { return it }
        val fileName = path.substringAfterLast('/')
        return parts.entries.firstOrNull { it.key.endsWith("/$fileName") }?.value
    }

    private fun parseRelationships(relsBytes: ByteArray?, baseDir: String): Map<String, String> {
        if (relsBytes == null) return emptyMap()
        val rels = HashMap<String, String>()
        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(relsBytes), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && (parser.name ?: "").endsWith("Relationship")) {
                    val id = parser.getAttributeValue(null, "Id")
                    val target = parser.getAttributeValue(null, "Target")
                    if (!id.isNullOrBlank() && !target.isNullOrBlank()) {
                        rels[id] = normalizeRelativePath(baseDir, target)
                    }
                }
                event = parser.next()
            }
        }
        return rels
    }

    private fun normalizeRelativePath(baseDir: String, target: String): String {
        val clean = target.trim().replace('\\', '/')
        if (clean.startsWith("/")) return clean.trimStart('/').lowercase()
        val segments = ArrayDeque<String>()
        baseDir.trim('/').split('/').filter { it.isNotEmpty() }.forEach { segments.addLast(it) }
        clean.split('/').filter { it.isNotEmpty() && it != "." }.forEach { seg ->
            if (seg == "..") {
                if (segments.isNotEmpty()) segments.removeLast()
            } else {
                segments.addLast(seg)
            }
        }
        return segments.joinToString("/").lowercase()
    }

    private fun parseCellRef(ref: String?): Pair<Int, Int>? {
        if (ref.isNullOrBlank()) return null
        var col = 0
        var idx = 0
        while (idx < ref.length && ref[idx].isLetter()) {
            val ch = ref[idx].uppercaseChar()
            if (ch !in 'A'..'Z') break
            col = col * 26 + (ch - 'A' + 1)
            idx++
        }
        if (col == 0 || idx >= ref.length) return null
        val row = ref.substring(idx).toIntOrNull() ?: return null
        return (col - 1) to row
    }

    private fun parseMergeRange(ref: String?): CellMergeSpan? {
        if (ref.isNullOrBlank()) return null
        val parts = ref.split(':')
        if (parts.size != 2) return null
        val start = parseCellRef(parts[0]) ?: return null
        val end = parseCellRef(parts[1]) ?: return null
        return CellMergeSpan(
            startRow = minOf(start.second, end.second),
            endRow = maxOf(start.second, end.second),
            startCol = minOf(start.first, end.first),
            endCol = maxOf(start.first, end.first)
        )
    }

    private fun resolveCellValue(
        cellType: String,
        raw: String,
        inline: String,
        sharedStrings: List<String>,
        numFmtId: Int,
        numFmtCode: String?
    ): Pair<String, Boolean> {
        return when (cellType) {
            "s" -> {
                val idx = raw.trim().toIntOrNull()
                val str = if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else raw
                str.trim() to false
            }
            "inlineStr" -> inline.trim() to false
            "b" -> {
                val boolText = if (raw.trim() == "1" || raw.trim().equals("true", true)) "TRUE" else "FALSE"
                boolText to false
            }
            "str", "e" -> raw.trim() to false
            else -> {
                val trimmed = raw.trim()
                if (trimmed.isEmpty()) return "" to false
                val num = trimmed.toDoubleOrNull() ?: return trimmed to false
                formatExcelNumber(num, numFmtId, numFmtCode) to true
            }
        }
    }

    private fun formatExcelNumber(value: Double, numFmtId: Int, formatCode: String?): String {
        if (isDateFormat(numFmtId, formatCode)) {
            formatExcelDate(value, numFmtId, formatCode)?.let { return it }
        }
        if (numFmtId == 9 || numFmtId == 10 || formatCode?.contains('%') == true) {
            val pct = value * 100.0
            return if (abs(pct - pct.roundToLong()) < 1e-6) {
                "${pct.roundToLong()}%"
            } else {
                String.format(Locale.US, "%.2f%%", pct)
            }
        }
        // Integer check to strip trailing ".0"
        if (abs(value - value.roundToLong()) < 1e-9 && abs(value) < 1e14) {
            val whole = value.roundToLong()
            if (numFmtId in setOf(3, 4, 37, 38, 39, 40) || formatCode?.contains("#,##0") == true) {
                return DecimalFormat("#,##0", DecimalFormatSymbols(Locale.US)).format(whole)
            }
            return whole.toString()
        }
        if (numFmtId in setOf(2, 4, 38, 40) || formatCode?.contains(".00") == true) {
            return DecimalFormat("#,##0.00", DecimalFormatSymbols(Locale.US)).format(value)
        }
        return DecimalFormat("0.######", DecimalFormatSymbols(Locale.US)).format(value)
    }

    private fun isDateFormat(numFmtId: Int, formatCode: String?): Boolean {
        if (numFmtId in 14..22 || numFmtId in 27..36 || numFmtId in 45..47 || numFmtId in 50..58) {
            return true
        }
        val code = formatCode?.lowercase() ?: return false
        val stripped = code
            .replace(Regex(""""[^"]*""""), "")
            .replace(Regex("""\[[^\]]*]"""), "")
        return stripped.contains("yy") ||
            stripped.contains("dd") ||
            (stripped.contains("mm") && (stripped.contains("/") || stripped.contains("-") || stripped.contains(":")))
    }

    private fun formatExcelDate(serial: Double, numFmtId: Int, formatCode: String?): String? {
        if (serial < 0.0 || serial > 2958465.0) return null
        val wholeDays = floor(serial).toLong()
        val frac = (serial - wholeDays).coerceIn(0.0, 0.999999)
        val totalMinutes = (frac * 1440.0).roundToInt()
        val hours = (totalMinutes / 60).coerceIn(0, 23)
        val minutes = (totalMinutes % 60).coerceIn(0, 59)
        val timeText = String.format(Locale.US, "%02d:%02d", hours, minutes)

        if (wholeDays == 0L || numFmtId in setOf(18, 19, 20, 21, 45, 46, 47)) {
            return timeText
        }
        val base = if (wholeDays >= 60L) {
            LocalDate.of(1899, 12, 30)
        } else {
            LocalDate.of(1899, 12, 31)
        }
        val dateText = base.plusDays(wholeDays).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val hasTimePart = numFmtId == 22 ||
            (formatCode?.lowercase()?.contains("h") == true && totalMinutes > 0) ||
            totalMinutes > 0
        return if (hasTimePart) "$dateText $timeText" else dateText
    }

    private fun parseColorAttrs(parser: XmlPullParser): Int? {
        val rgb = parser.getAttributeValue(null, "rgb")?.trim()?.removePrefix("#")
        if (!rgb.isNullOrBlank()) {
            when (rgb.length) {
                8 -> {
                    if (!rgb.equals("00000000", true)) {
                        return rgb.substring(2).toIntOrNull(16)
                    }
                }
                6 -> return rgb.toIntOrNull(16)
            }
        }
        val theme = parser.getAttributeValue(null, "theme")?.toIntOrNull() ?: return null
        val baseRgb = when (theme) {
            0 -> 0xFFFFFF
            1 -> 0x000000
            2 -> 0xE7E6E6
            3 -> 0x44546A
            4 -> 0x4472C4
            5 -> 0xED7D31
            6 -> 0xA5A5A5
            7 -> 0xFFC000
            8 -> 0x5B9BD5
            9 -> 0x70AD47
            else -> return null
        }
        val tint = parser.getAttributeValue(null, "tint")?.toDoubleOrNull() ?: return baseRgb
        return applyTint(baseRgb, tint)
    }

    private fun applyTint(rgb: Int, tint: Double): Int {
        var r = (rgb shr 16) and 0xFF
        var g = (rgb shr 8) and 0xFF
        var b = rgb and 0xFF
        if (tint < 0.0) {
            val f = (1.0 + tint).coerceIn(0.0, 1.0)
            r = (r * f).roundToInt()
            g = (g * f).roundToInt()
            b = (b * f).roundToInt()
        } else if (tint > 0.0) {
            val t = tint.coerceIn(0.0, 1.0)
            r = (r + (255 - r) * t).roundToInt()
            g = (g + (255 - g) * t).roundToInt()
            b = (b + (255 - b) * t).roundToInt()
        }
        return (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)
    }

    /**
     * Converts integer column index (0 -> A, 25 -> Z, 26 -> AA) to standard Excel column label.
     */
    fun columnLetter(index: Int): String {
        var n = index + 1
        val sb = StringBuilder()
        while (n > 0) {
            val rem = (n - 1) % 26
            sb.append(('A'.code + rem).toChar())
            n = (n - 1) / 26
        }
        return sb.reverse().toString()
    }
}
