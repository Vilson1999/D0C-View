package com.example.data

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Pure-Kotlin offline OOXML PowerPoint (.pptx / .pptm / .potx) parser.
 * Extracts presentation slide size (16:9, 4:3, A4, A3, Letter, Legal, Custom),
 * ordered slides, background colors, styled text runs, images, charts, tables, and notes.
 * Uses [OfficeXmlFormatParser.newPullParser] so it works identically on Android and JVM tests.
 */
object OoxmlPresentationParser {

    private const val MAX_TOTAL_UNZIPPED_BYTES = 32L * 1024L * 1024L

    private data class PositionedBlock(
        val yPt: Float,
        val order: Int,
        val block: WordBlock
    )

    fun parsePptxBytes(bytes: ByteArray): PresentationDeck {
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

        if (parts.isEmpty()) {
            return PresentationDeck(emptyList(), OfficeLayoutResolver.DEFAULT_SLIDE_16_9)
        }

        val presBytes = parts["ppt/presentation.xml"]
            ?: parts.entries.firstOrNull { it.key.endsWith("/presentation.xml") }?.value
        val layout = parsePresentationLayout(presBytes)
        val slidePaths = resolveOrderedSlidePaths(parts)

        val slides = slidePaths.mapIndexed { idx, slidePath ->
            val slideBytes = parts[slidePath]
            if (slideBytes != null) {
                parseSlide(idx + 1, slidePath, slideBytes, parts)
            } else {
                SlidePage(slideNumber = idx + 1, title = "Slide ${idx + 1}", bodyLines = emptyList())
            }
        }

        return PresentationDeck(slides = slides, layout = layout)
    }

    private fun parsePresentationLayout(presBytes: ByteArray?): OfficePageLayout {
        if (presBytes == null) return OfficeLayoutResolver.DEFAULT_SLIDE_16_9
        var wPt = OfficeLayoutResolver.DEFAULT_SLIDE_16_9.widthPt
        var hPt = OfficeLayoutResolver.DEFAULT_SLIDE_16_9.heightPt

        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(presBytes), "UTF-8")
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    val local = (parser.name ?: "").substringAfter(':')
                    if (local == "sldSz") {
                        var cx: Float? = null
                        var cy: Float? = null
                        for (i in 0 until parser.attributeCount) {
                            when (parser.getAttributeName(i)?.substringAfter(':')) {
                                "cx" -> cx = parser.getAttributeValue(i)?.toFloatOrNull()
                                "cy" -> cy = parser.getAttributeValue(i)?.toFloatOrNull()
                            }
                        }
                        if (cx != null && cx > 0f && cy != null && cy > 0f) {
                            wPt = cx / 12700f
                            hPt = cy / 12700f
                            break
                        }
                    }
                }
                event = parser.next()
            }
        }

        val marginH = (wPt * 0.045f).coerceIn(24f, 54f)
        val marginV = (hPt * 0.055f).coerceIn(20f, 42f)
        return OfficeLayoutResolver.buildLayoutFromSizeAndMargins(
            rawWidthPt = wPt,
            rawHeightPt = hPt,
            leftPt = marginH,
            rightPt = marginH,
            topPt = marginV,
            bottomPt = marginV,
            forceLandscape = OfficeLayoutResolver.isLandscapeFromDimensions(wPt, hPt)
        )
    }

    private fun resolveOrderedSlidePaths(parts: Map<String, ByteArray>): List<String> {
        val presBytes = parts["ppt/presentation.xml"]
        val rels = parseRelationships(
            parts["ppt/_rels/presentation.xml.rels"],
            baseDir = "ppt/"
        )
        val ordered = mutableListOf<String>()
        if (presBytes != null) {
            runCatching {
                val parser = OfficeXmlFormatParser.newPullParser()
                parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                parser.setInput(ByteArrayInputStream(presBytes), "UTF-8")
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        val local = (parser.name ?: "").substringAfter(':')
                        if (local == "sldId") {
                            val rId = parser.getAttributeValue(null, "r:id")
                                ?: parser.getAttributeValue(null, "id")
                            val target = rId?.let { rels[it] }
                            if (target != null && parts.containsKey(target)) {
                                ordered.add(target)
                            }
                        }
                    }
                    event = parser.next()
                }
            }
        }
        if (ordered.isNotEmpty()) return ordered

        val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""")
        return parts.keys
            .filter { slideRegex.matches(it) }
            .sortedBy { slideRegex.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
    }

    private fun parseSlide(
        slideNum: Int,
        slidePath: String,
        slideBytes: ByteArray,
        parts: Map<String, ByteArray>
    ): SlidePage {
        val dir = slidePath.substringBeforeLast('/', "")
        val file = slidePath.substringAfterLast('/')
        val relsPath = if (dir.isEmpty()) "_rels/$file.rels" else "$dir/_rels/$file.rels"
        val rels = parseRelationships(parts[relsPath], baseDir = if (dir.isEmpty()) "" else "$dir/")

        var bgRgb: Int? = null
        val positioned = mutableListOf<PositionedBlock>()
        var orderCounter = 0

        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(slideBytes), "UTF-8")

            var inBg = false
            var skipDepth = -1
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                val local = tag.substringAfter(':')
                if (skipDepth >= 0) {
                    if (event == XmlPullParser.END_TAG && parser.depth == skipDepth) {
                        skipDepth = -1
                    }
                    event = parser.next()
                    continue
                }

                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "Fallback" -> skipDepth = parser.depth
                            "bg" -> inBg = true
                            "srgbClr" -> if (inBg && bgRgb == null) {
                                OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "val"))?.let {
                                    bgRgb = it
                                }
                            }
                            "sp" -> {
                                val (yPt, textBlocks) = parseShapeTextBlocks(parser)
                                textBlocks.forEach { b ->
                                    positioned.add(PositionedBlock(yPt, orderCounter++, b))
                                }
                            }
                            "pic" -> {
                                parsePictureBlock(parser, parts, rels)?.let { (yPt, b) ->
                                    positioned.add(PositionedBlock(yPt, orderCounter++, b))
                                }
                            }
                            "graphicFrame" -> {
                                val (yPt, frameBlocks) = parseGraphicFrameBlocks(parser, parts, rels)
                                frameBlocks.forEach { b ->
                                    positioned.add(PositionedBlock(yPt, orderCounter++, b))
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (local == "bg") inBg = false
                    }
                }
                event = parser.next()
            }
        }

        val sortedBlocks = positioned
            .sortedWith(compareBy<PositionedBlock> { it.yPt }.thenBy { it.order })
            .map { it.block }

        val textLines = sortedBlocks.filter {
            it.image == null && it.chart == null && it.tableRows.isEmpty() && it.text.isNotBlank()
        }
        val title = textLines.firstOrNull()?.text ?: "Slide $slideNum"
        val bodyLines = if (textLines.size > 1) textLines.drop(1).map { it.text } else emptyList()

        // Speaker notes
        val notesPath = rels.values.firstOrNull { it.contains("notesSlide", ignoreCase = true) }
            ?: "ppt/notesSlides/notesSlide$slideNum.xml"
        val notes = parts[notesPath]?.let { extractPlainDrawingParagraphs(it).joinToString("\n") } ?: ""

        return SlidePage(
            slideNumber = slideNum,
            title = title,
            bodyLines = bodyLines,
            notes = notes,
            blocks = sortedBlocks,
            backgroundRgb = bgRgb
        )
    }

    private fun parseShapeTextBlocks(parser: XmlPullParser): Pair<Float, List<WordBlock>> {
        val spDepth = parser.depth
        var yPt = 0f
        var placeholderType = ""
        val blocks = mutableListOf<WordBlock>()

        var inParagraph = false
        var paraAlign = DocAlignment.LEFT
        var isBullet = false
        var inRun = false
        var inRPr = false
        var runBold = false
        var runItalic = false
        var runUnderline = false
        var runStrike = false
        var runSizePt: Float? = null
        var runColorRgb: Int? = null
        var inSolidFill = false
        val runs = mutableListOf<WordRun>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val local = (parser.name ?: "").substringAfter(':')
            if (event == XmlPullParser.END_TAG && parser.depth == spDepth && local == "sp") {
                break
            }
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (local) {
                        "ph" -> placeholderType = parser.getAttributeValue(null, "type") ?: placeholderType
                        "off" -> if (!inParagraph) {
                            parser.getAttributeValue(null, "y")?.toFloatOrNull()?.let { yPt = it / 12700f }
                        }
                        "p" -> {
                            inParagraph = true
                            paraAlign = DocAlignment.LEFT
                            isBullet = false
                            runs.clear()
                        }
                        "pPr" -> if (inParagraph) {
                            paraAlign = when (parser.getAttributeValue(null, "algn")?.lowercase()) {
                                "ctr" -> DocAlignment.CENTER
                                "r" -> DocAlignment.RIGHT
                                "just" -> DocAlignment.JUSTIFY
                                else -> DocAlignment.LEFT
                            }
                            val lvl = parser.getAttributeValue(null, "lvl")?.toIntOrNull() ?: 0
                            if (lvl > 0 && !placeholderType.contains("title", ignoreCase = true)) {
                                isBullet = true
                            }
                        }
                        "buChar", "buAutoNum" -> if (inParagraph) isBullet = true
                        "buNone" -> if (inParagraph) isBullet = false
                        "r" -> if (inParagraph) {
                            inRun = true
                            runBold = false
                            runItalic = false
                            runUnderline = false
                            runStrike = false
                            runSizePt = null
                            runColorRgb = null
                        }
                        "rPr" -> if (inRun) {
                            inRPr = true
                            runBold = parser.getAttributeValue(null, "b") == "1"
                            runItalic = parser.getAttributeValue(null, "i") == "1"
                            val u = parser.getAttributeValue(null, "u")
                            runUnderline = !u.isNullOrBlank() && !u.equals("none", true)
                            val strike = parser.getAttributeValue(null, "strike")
                            runStrike = !strike.isNullOrBlank() && !strike.equals("noStrike", true)
                            parser.getAttributeValue(null, "sz")?.toFloatOrNull()?.let {
                                runSizePt = (it / 100f).coerceIn(7f, 64f)
                            }
                        }
                        "solidFill" -> if (inRPr) inSolidFill = true
                        "srgbClr" -> if (inRPr && inSolidFill) {
                            OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "val"))?.let {
                                runColorRgb = it
                            }
                        }
                        "t" -> if (inParagraph) {
                            val txt = parser.nextText()
                            if (txt.isNotEmpty()) {
                                runs.add(
                                    WordRun(
                                        text = txt,
                                        isBold = runBold,
                                        isItalic = runItalic,
                                        isUnderline = runUnderline,
                                        isStrike = runStrike,
                                        fontSizePt = runSizePt,
                                        colorRgb = runColorRgb
                                    )
                                )
                            }
                        }
                        "br" -> if (inParagraph) {
                            runs.add(WordRun(text = "\n"))
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (local) {
                        "solidFill" -> inSolidFill = false
                        "rPr" -> inRPr = false
                        "r" -> inRun = false
                        "p" -> if (inParagraph) {
                            val merged = runs.joinToString("") { it.text }.trim()
                            if (merged.isNotEmpty()) {
                                val isTitle = placeholderType.equals("title", true) ||
                                    placeholderType.equals("ctrTitle", true)
                                blocks.add(
                                    WordBlock(
                                        text = merged,
                                        isHeading = isTitle,
                                        headingLevel = if (isTitle) 1 else 0,
                                        isBold = isTitle || runs.any { it.isBold },
                                        isItalic = runs.any { it.isItalic },
                                        isBullet = isBullet && !isTitle,
                                        runs = runs.toList(),
                                        alignment = paraAlign,
                                        fontSizePt = runs.firstNotNullOfOrNull { it.fontSizePt }
                                            ?: if (isTitle) 22f else 14f,
                                        spaceAfterPt = 4f
                                    )
                                )
                            }
                            inParagraph = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return yPt to blocks
    }

    private fun parsePictureBlock(
        parser: XmlPullParser,
        parts: Map<String, ByteArray>,
        rels: Map<String, String>
    ): Pair<Float, WordBlock>? {
        val picDepth = parser.depth
        var yPt = 0f
        var widthPt: Float? = null
        var heightPt: Float? = null
        var embedId: String? = null

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val local = (parser.name ?: "").substringAfter(':')
            if (event == XmlPullParser.END_TAG && parser.depth == picDepth && local == "pic") {
                break
            }
            if (event == XmlPullParser.START_TAG) {
                when (local) {
                    "off" -> parser.getAttributeValue(null, "y")?.toFloatOrNull()?.let { yPt = it / 12700f }
                    "ext" -> {
                        val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull()
                        val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull()
                        if (cx != null && cx > 0f) widthPt = cx / 12700f
                        if (cy != null && cy > 0f) heightPt = cy / 12700f
                    }
                    "blip" -> {
                        embedId = parser.getAttributeValue(null, "r:embed")
                            ?: parser.getAttributeValue(null, "r:link")
                    }
                }
            }
            event = parser.next()
        }

        val imgPath = embedId?.let { rels[it] } ?: return null
        val imgBytes = resolvePartBytes(imgPath, parts) ?: return null
        return yPt to WordBlock(
            image = WordImage(
                bytes = imgBytes,
                widthPt = widthPt,
                heightPt = heightPt,
                alignment = DocAlignment.CENTER
            ),
            spaceBeforePt = 6f,
            spaceAfterPt = 6f
        )
    }

    private fun parseGraphicFrameBlocks(
        parser: XmlPullParser,
        parts: Map<String, ByteArray>,
        rels: Map<String, String>
    ): Pair<Float, List<WordBlock>> {
        val frameDepth = parser.depth
        var yPt = 0f
        val blocks = mutableListOf<WordBlock>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val local = (parser.name ?: "").substringAfter(':')
            if (event == XmlPullParser.END_TAG && parser.depth == frameDepth && local == "graphicFrame") {
                break
            }
            if (event == XmlPullParser.START_TAG) {
                when (local) {
                    "off" -> parser.getAttributeValue(null, "y")?.toFloatOrNull()?.let { yPt = it / 12700f }
                    "chart" -> {
                        val rId = parser.getAttributeValue(null, "r:id")
                        val chartPath = rId?.let { rels[it] }
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
                    "tbl" -> parseDrawingTable(parser)?.let { blocks.add(it) }
                }
            }
            event = parser.next()
        }
        return yPt to blocks
    }

    private fun parseDrawingTable(parser: XmlPullParser): WordBlock? {
        val tblDepth = parser.depth
        val rows = mutableListOf<WordTableRow>()
        var currentCells = mutableListOf<WordTableCell>()
        var inRow = false
        var inCell = false
        var colSpan = 1
        var cellBgRgb: Int? = null
        var inTcPr = false
        var inParagraph = false
        val paraText = StringBuilder()
        var cellParas = mutableListOf<WordBlock>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val local = (parser.name ?: "").substringAfter(':')
            if (event == XmlPullParser.END_TAG && parser.depth == tblDepth && local == "tbl") {
                break
            }
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (local) {
                        "tr" -> {
                            inRow = true
                            currentCells = mutableListOf()
                        }
                        "tc" -> if (inRow) {
                            inCell = true
                            colSpan = parser.getAttributeValue(null, "gridSpan")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                            cellBgRgb = null
                            cellParas = mutableListOf()
                        }
                        "tcPr" -> if (inCell) inTcPr = true
                        "srgbClr" -> if (inTcPr && cellBgRgb == null) {
                            OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "val"))?.let {
                                cellBgRgb = it
                            }
                        }
                        "p" -> if (inCell) {
                            inParagraph = true
                            paraText.clear()
                        }
                        "t" -> if (inParagraph) paraText.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (local) {
                        "tcPr" -> inTcPr = false
                        "p" -> if (inParagraph) {
                            val txt = paraText.toString().trim()
                            if (txt.isNotEmpty()) {
                                cellParas.add(WordBlock(text = txt))
                            }
                            inParagraph = false
                        }
                        "tc" -> if (inCell) {
                            currentCells.add(
                                WordTableCell(
                                    paragraphs = cellParas.toList().ifEmpty { listOf(WordBlock(text = "")) },
                                    backgroundRgb = cellBgRgb,
                                    colSpan = colSpan
                                )
                            )
                            inCell = false
                        }
                        "tr" -> if (inRow) {
                            if (currentCells.isNotEmpty()) {
                                rows.add(
                                    WordTableRow(
                                        cells = currentCells.toList(),
                                        isHeader = rows.isEmpty()
                                    )
                                )
                            }
                            inRow = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return if (rows.isEmpty()) null else WordBlock(tableRows = rows, tableHasBorders = true, spaceAfterPt = 6f)
    }

    private fun extractPlainDrawingParagraphs(xmlBytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")
            var inParagraph = false
            val currentLine = StringBuilder()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val local = (parser.name ?: "").substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> when (local) {
                        "p" -> {
                            inParagraph = true
                            currentLine.clear()
                        }
                        "t" -> if (inParagraph) currentLine.append(parser.nextText())
                        "br" -> if (inParagraph) currentLine.append("\n")
                    }
                    XmlPullParser.END_TAG -> if (local == "p" && inParagraph) {
                        val line = currentLine.toString().trim()
                        if (line.isNotEmpty()) result.add(line)
                        inParagraph = false
                    }
                }
                event = parser.next()
            }
        }
        return result
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

    private fun resolvePartBytes(path: String, parts: Map<String, ByteArray>): ByteArray? {
        parts[path]?.let { return it }
        val fileName = path.substringAfterLast('/')
        return parts.entries.firstOrNull { it.key.endsWith("/$fileName") }?.value
    }
}
