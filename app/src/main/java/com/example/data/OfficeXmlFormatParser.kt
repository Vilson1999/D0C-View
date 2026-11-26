package com.example.data

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * Parses MS Office XML formats (Flat OPC Word XML, Word 2003 XML, Excel SpreadsheetML),
 * OOXML Charts (word/charts/chart*.xml), and standalone embedded base64/SVG media inside XML.
 */
object OfficeXmlFormatParser {

    internal fun newPullParser(): XmlPullParser {
        return runCatching {
            Xml.newPullParser()
        }.getOrElse {
            runCatching {
                XmlPullParserFactory.newInstance().newPullParser()
            }.getOrElse {
                DomXmlPullParser()
            }
        }
    }

    fun isOfficeDocumentXml(raw: String): Boolean {
        val sample = raw.take(4096)
        return sample.contains("<pkg:package", ignoreCase = true) ||
            sample.contains("<w:wordDocument", ignoreCase = true) ||
            sample.contains("<wordDocument", ignoreCase = true) ||
            sample.contains("progid=\"Word.Document\"", ignoreCase = true) ||
            sample.contains("progid=\"Excel.Sheet\"", ignoreCase = true) ||
            (sample.contains("<Workbook", ignoreCase = true) && sample.contains("Worksheet", ignoreCase = true)) ||
            sample.contains("<c:chartSpace", ignoreCase = true) ||
            sample.contains("<chartSpace", ignoreCase = true)
    }

    fun tryParseOfficeOrMediaXml(raw: String): List<WordBlock>? {
        val trimmed = raw.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        if (trimmed.isEmpty()) return null
        val bytes = trimmed.toByteArray(Charsets.UTF_8)

        return when {
            trimmed.contains("<pkg:package", ignoreCase = true) -> {
                val parts = parseFlatOpcParts(bytes)
                if (parts.containsKey("word/document.xml")) {
                    OoxmlDocumentParser.parseWordPackageMap(parts)
                } else {
                    null
                }
            }
            trimmed.contains("<w:wordDocument", ignoreCase = true) ||
                trimmed.contains("<wordDocument", ignoreCase = true) -> {
                parseWord2003Xml(bytes)
            }
            trimmed.contains("<Workbook", ignoreCase = true) &&
                trimmed.contains("Worksheet", ignoreCase = true) -> {
                parseSpreadsheetMl(bytes)
            }
            trimmed.contains("<c:chartSpace", ignoreCase = true) ||
                trimmed.contains("<chartSpace", ignoreCase = true) -> {
                val chart = parseChartXml(bytes) ?: return null
                listOf(WordBlock(chart = chart))
            }
            else -> null
        }
    }

    /**
     * Extracts embedded base64 images (data:image/...;base64,...) or standalone <c:chartSpace>
     * blocks from a general XML file so they can be rendered below or alongside formatted XML.
     */
    fun extractEmbeddedMediaBlocks(raw: String): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        val base64Regex = Regex(
            """data:image/(?:png|jpeg|jpg|webp|gif);base64,([A-Za-z0-9+/=\s]+)""",
            RegexOption.IGNORE_CASE
        )
        base64Regex.findAll(raw).take(12).forEach { match ->
            val cleaned = match.groupValues[1].replace(Regex("""\s+"""), "")
            val decoded = runCatching { Base64.getMimeDecoder().decode(cleaned) }.getOrNull()
            if (decoded != null && decoded.size > 24) {
                blocks.add(
                    WordBlock(
                        image = WordImage(bytes = decoded, alignment = DocAlignment.CENTER),
                        spaceBeforePt = 6f,
                        spaceAfterPt = 6f
                    )
                )
            }
        }
        return blocks
    }

    private fun parseFlatOpcParts(xmlBytes: ByteArray): Map<String, ByteArray> {
        val parts = LinkedHashMap<String, ByteArray>()
        val parser = newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentPartName: String? = null
        var inBinaryData = false
        val binaryText = StringBuilder()
        var inXmlData = false
        var xmlDataDepth = 0
        val xmlBuffer = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when {
                        tag.equals("pkg:part", ignoreCase = true) -> {
                            val rawName = parser.getAttributeValue(null, "pkg:name")
                                ?: parser.getAttributeValue(null, "name")
                                ?: ""
                            currentPartName = rawName.trimStart('/').lowercase()
                        }
                        tag.equals("pkg:binaryData", ignoreCase = true) && currentPartName != null -> {
                            inBinaryData = true
                            binaryText.clear()
                        }
                        tag.equals("pkg:xmlData", ignoreCase = true) && currentPartName != null -> {
                            inXmlData = true
                            xmlDataDepth = parser.depth
                            xmlBuffer.clear()
                        }
                        inXmlData -> {
                            xmlBuffer.append('<').append(tag)
                            for (i in 0 until parser.attributeCount) {
                                xmlBuffer.append(' ')
                                    .append(parser.getAttributeName(i))
                                    .append("=\"")
                                    .append(escapeXmlAttr(parser.getAttributeValue(i) ?: ""))
                                    .append('"')
                            }
                            xmlBuffer.append('>')
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    when {
                        inBinaryData -> binaryText.append(parser.text ?: "")
                        inXmlData -> xmlBuffer.append(escapeXmlText(parser.text ?: ""))
                    }
                }
                XmlPullParser.CDSECT -> {
                    if (inXmlData) xmlBuffer.append(escapeXmlText(parser.text ?: ""))
                }
                XmlPullParser.END_TAG -> {
                    when {
                        tag.equals("pkg:binaryData", ignoreCase = true) && inBinaryData -> {
                            val key = currentPartName
                            if (!key.isNullOrBlank()) {
                                val cleaned = binaryText.toString().replace(Regex("""\s+"""), "")
                                runCatching { Base64.getMimeDecoder().decode(cleaned) }.getOrNull()?.let {
                                    parts[key] = it
                                }
                            }
                            inBinaryData = false
                        }
                        tag.equals("pkg:xmlData", ignoreCase = true) && inXmlData && parser.depth == xmlDataDepth -> {
                            val key = currentPartName
                            if (!key.isNullOrBlank() && xmlBuffer.isNotEmpty()) {
                                parts[key] = xmlBuffer.toString().toByteArray(Charsets.UTF_8)
                            }
                            inXmlData = false
                        }
                        inXmlData -> {
                            xmlBuffer.append("</").append(tag).append('>')
                        }
                        tag.equals("pkg:part", ignoreCase = true) -> {
                            currentPartName = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return parts
    }

    private fun parseWord2003Xml(xmlBytes: ByteArray): List<WordBlock> {
        val binDataMap = HashMap<String, ByteArray>()
        val parser = newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentBinName: String? = null
        val binText = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (tag.equals("w:binData", ignoreCase = true) || tag.equals("binData", ignoreCase = true)) {
                        currentBinName = parser.getAttributeValue(null, "w:name")
                            ?: parser.getAttributeValue(null, "name")
                        binText.clear()
                    }
                }
                XmlPullParser.TEXT -> if (currentBinName != null) binText.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    if ((tag.equals("w:binData", ignoreCase = true) || tag.equals("binData", ignoreCase = true)) && currentBinName != null) {
                        val cleaned = binText.toString().replace(Regex("""\s+"""), "")
                        val binName = currentBinName
                        runCatching { Base64.getMimeDecoder().decode(cleaned) }.getOrNull()?.let { decoded ->
                            binDataMap[binName] = decoded
                            binDataMap[binName.lowercase()] = decoded
                        }
                        currentBinName = null
                    }
                }
            }
            event = parser.next()
        }

        val parts = HashMap<String, ByteArray>()
        parts["word/document.xml"] = xmlBytes
        return OoxmlDocumentParser.parseWordPackageMap(parts, inlineBinData = binDataMap)
    }

    private fun parseSpreadsheetMl(xmlBytes: ByteArray): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        val parser = newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var currentSheetName: String? = null
        var currentRows = mutableListOf<WordTableRow>()
        var currentCells = mutableListOf<WordTableCell>()
        var inCell = false
        var inData = false
        val cellText = StringBuilder()

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = (parser.name ?: "").substringAfter(':')
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "Worksheet" -> {
                            currentSheetName = parser.getAttributeValue(null, "ss:Name")
                                ?: parser.getAttributeValue(null, "Name")
                                ?: "Sheet"
                            currentRows = mutableListOf()
                        }
                        "Row" -> currentCells = mutableListOf()
                        "Cell" -> {
                            inCell = true
                            cellText.clear()
                        }
                        "Data" -> if (inCell) inData = true
                    }
                }
                XmlPullParser.TEXT -> if (inData) cellText.append(parser.text ?: "")
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "Data" -> inData = false
                        "Cell" -> {
                            if (inCell) {
                                val value = cellText.toString().trim()
                                currentCells.add(
                                    WordTableCell(
                                        paragraphs = listOf(WordBlock(text = value))
                                    )
                                )
                                inCell = false
                            }
                        }
                        "Row" -> {
                            if (currentCells.isNotEmpty()) {
                                val isHeader = currentRows.isEmpty()
                                val coloredCells = if (isHeader) {
                                    currentCells.map { cell ->
                                        cell.copy(
                                            backgroundRgb = 0xEFF6FF,
                                            paragraphs = cell.paragraphs.map { it.copy(isBold = true) }
                                        )
                                    }
                                } else {
                                    currentCells
                                }
                                currentRows.add(WordTableRow(cells = coloredCells, isHeader = isHeader))
                            }
                        }
                        "Worksheet" -> {
                            val sheetName = currentSheetName
                            if (!sheetName.isNullOrBlank()) {
                                blocks.add(
                                    WordBlock(
                                        text = sheetName,
                                        isHeading = true,
                                        headingLevel = 2,
                                        spaceBeforePt = 6f,
                                        spaceAfterPt = 4f
                                    )
                                )
                            }
                            if (currentRows.isNotEmpty()) {
                                blocks.add(WordBlock(tableRows = currentRows, tableHasBorders = true, spaceAfterPt = 10f))
                            }
                            currentSheetName = null
                        }
                    }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    fun parseChartXml(chartBytes: ByteArray): OfficeChart? {
        return runCatching {
            val parser = newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(chartBytes), "UTF-8")

            val titleParts = mutableListOf<String>()
            var inTitle = false
            var chartType = OfficeChartType.COLUMN
            val seriesList = mutableListOf<ChartSeries>()
            var globalCategories = listOf<String>()

            var inSeries = false
            var seriesName = ""
            var seriesColor: Int? = null
            var inTx = false
            var inCat = false
            var inVal = false
            var inSpPr = false
            var currentIdx = -1
            val catMap = sortedMapOf<Int, String>()
            val valMap = sortedMapOf<Int, Double>()

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                val local = tag.substringAfter(':')
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (local) {
                            "title" -> inTitle = true
                            "t" -> if (inTitle) {
                                val text = parser.nextText().trim()
                                if (text.isNotEmpty()) titleParts.add(text)
                            }
                            "barChart", "bar3DChart" -> chartType = OfficeChartType.COLUMN
                            "barDir" -> {
                                val v = parser.getAttributeValue(null, "val") ?: ""
                                if (v.equals("bar", ignoreCase = true)) {
                                    chartType = OfficeChartType.BAR
                                } else if (v.equals("col", ignoreCase = true)) {
                                    chartType = OfficeChartType.COLUMN
                                }
                            }
                            "lineChart", "line3DChart" -> chartType = OfficeChartType.LINE
                            "pieChart", "pie3DChart" -> chartType = OfficeChartType.PIE
                            "doughnutChart" -> chartType = OfficeChartType.DOUGHNUT
                            "areaChart", "area3DChart" -> chartType = OfficeChartType.AREA
                            "scatterChart" -> chartType = OfficeChartType.SCATTER
                            "ser" -> {
                                inSeries = true
                                seriesName = ""
                                seriesColor = null
                                catMap.clear()
                                valMap.clear()
                            }
                            "tx" -> if (inSeries) inTx = true
                            "cat", "xVal" -> if (inSeries) inCat = true
                            "val", "yVal" -> if (inSeries) inVal = true
                            "spPr" -> if (inSeries) inSpPr = true
                            "srgbClr" -> if (inSeries && inSpPr && !inCat && !inVal) {
                                val hex = parser.getAttributeValue(null, "val")
                                parseHexRgb(hex)?.let { seriesColor = it }
                            }
                            "pt" -> if (inSeries && (inCat || inVal)) {
                                currentIdx = parser.getAttributeValue(null, "idx")?.toIntOrNull() ?: catMap.size
                            }
                            "v" -> {
                                val text = parser.nextText().trim()
                                when {
                                    inTx && seriesName.isEmpty() -> seriesName = text
                                    inCat -> {
                                        val key = if (currentIdx >= 0) currentIdx else catMap.size
                                        catMap[key] = text
                                        currentIdx = -1
                                    }
                                    inVal -> {
                                        val key = if (currentIdx >= 0) currentIdx else valMap.size
                                        text.toDoubleOrNull()?.let { valMap[key] = it }
                                        currentIdx = -1
                                    }
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (local) {
                            "title" -> inTitle = false
                            "tx" -> inTx = false
                            "cat", "xVal" -> inCat = false
                            "val", "yVal" -> inVal = false
                            "spPr" -> inSpPr = false
                            "ser" -> if (inSeries) {
                                if (catMap.isNotEmpty() && globalCategories.isEmpty()) {
                                    globalCategories = catMap.values.toList()
                                }
                                val vals = if (catMap.isNotEmpty()) {
                                    catMap.keys.map { k -> valMap[k] ?: 0.0 }
                                } else {
                                    valMap.values.toList()
                                }
                                if (vals.isNotEmpty()) {
                                    seriesList.add(
                                        ChartSeries(
                                            name = seriesName.ifBlank { "Series ${seriesList.size + 1}" },
                                            values = vals,
                                            colorRgb = seriesColor
                                        )
                                    )
                                }
                                inSeries = false
                            }
                        }
                    }
                }
                event = parser.next()
            }

            if (seriesList.isEmpty()) return@runCatching null
            val maxLen = seriesList.maxOf { it.values.size }
            val finalCats = if (globalCategories.isNotEmpty()) {
                globalCategories
            } else {
                (1..maxLen).map { "Item $it" }
            }
            OfficeChart(
                title = titleParts.joinToString(" ").trim(),
                type = chartType,
                categories = finalCats,
                series = seriesList
            )
        }.getOrNull()
    }

    internal fun parseHexRgb(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.equals("auto", ignoreCase = true) || cleaned.length != 6) return null
        return cleaned.toIntOrNull(16)
    }

    private fun escapeXmlText(text: String): String = text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

    private fun escapeXmlAttr(text: String): String = escapeXmlText(text)
        .replace("\"", "&quot;")
}
