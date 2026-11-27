package com.example.data

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/**
 * Full offline OOXML Word (.docx) & Word XML package parser preserving:
 * - Paragraph alignments (left, center, right, justify) & spacing
 * - Per-run bold, italic, underline, strikethrough, font sizes, and colors
 * - Explicit page breaks (w:br type="page", w:pageBreakBefore, section breaks)
 * - Embedded images (wp:inline, wp:anchor, a:blip, v:imagedata) with exact EMU dimensions
 * - Embedded charts (c:chart -> word/charts/chart*.xml)
 * - Tables (w:tbl, w:tr, w:tc) with column spans, cell shading, and border/borderless styles
 */
object OoxmlDocumentParser {

    private data class StyleDef(
        val id: String,
        val basedOn: String? = null,
        val name: String? = null,
        val alignment: DocAlignment? = null,
        val headingLevel: Int? = null,
        val isBullet: Boolean? = null,
        val fontSizePt: Float? = null,
        val isBold: Boolean? = null,
        val isItalic: Boolean? = null,
        val colorRgb: Int? = null,
        val spaceBeforePt: Float? = null,
        val spaceAfterPt: Float? = null,
        val hasBottomBorder: Boolean? = null
    )

    private data class ResolvedStyle(
        val alignment: DocAlignment = DocAlignment.LEFT,
        val headingLevel: Int = 0,
        val isBullet: Boolean = false,
        val fontSizePt: Float? = null,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val colorRgb: Int? = null,
        val spaceBeforePt: Float = 0f,
        val spaceAfterPt: Float = 0f,
        val hasBottomBorder: Boolean = false
    )

    private data class MutablePageLayout(
        var widthPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.widthPt,
        var heightPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.heightPt,
        var isLandscape: Boolean? = null,
        var marginLeftPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.marginLeftPt,
        var marginRightPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.marginRightPt,
        var marginTopPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.marginTopPt,
        var marginBottomPt: Float = OfficeLayoutResolver.DEFAULT_WORD_A4.marginBottomPt,
        var hasSize: Boolean = false,
        var hasMargins: Boolean = false
    ) {
        fun toLayout(): OfficePageLayout = OfficeLayoutResolver.buildLayoutFromSizeAndMargins(
            rawWidthPt = widthPt,
            rawHeightPt = heightPt,
            leftPt = marginLeftPt,
            rightPt = marginRightPt,
            topPt = marginTopPt,
            bottomPt = marginBottomPt,
            forceLandscape = isLandscape
        )
    }

    fun parseDocxBytes(bytes: ByteArray): List<WordBlock> =
        parseDocxWithLayout(bytes).blocks

    fun parseDocxWithLayout(bytes: ByteArray): WordDocumentData {
        val parts = LinkedHashMap<String, ByteArray>()
        var totalRead = 0L
        val maxTotal = 28L * 1024L * 1024L

        runCatching {
            ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val data = zis.readBytes()
                        totalRead += data.size
                        if (totalRead <= maxTotal) {
                            parts[entry.name.trimStart('/').lowercase()] = data
                        }
                    }
                    entry = zis.nextEntry
                }
            }
        }

        return parseWordPackageMapWithLayout(parts)
    }

    fun parseWordPackageMap(
        parts: Map<String, ByteArray>,
        inlineBinData: Map<String, ByteArray> = emptyMap()
    ): List<WordBlock> = parseWordPackageMapWithLayout(parts, inlineBinData).blocks

    fun parseWordPackageMapWithLayout(
        parts: Map<String, ByteArray>,
        inlineBinData: Map<String, ByteArray> = emptyMap()
    ): WordDocumentData {
        val docBytes = parts["word/document.xml"]
            ?: parts.entries.firstOrNull { it.key.endsWith("/document.xml") }?.value
            ?: return WordDocumentData(emptyList())

        val rels = parseRelationships(
            parts["word/_rels/document.xml.rels"]
                ?: parts.entries.firstOrNull { it.key.endsWith("document.xml.rels") }?.value
        )
        val styles = parseStylesXml(
            parts["word/styles.xml"]
                ?: parts.entries.firstOrNull { it.key.endsWith("styles.xml") }?.value
        )

        val blocks = mutableListOf<WordBlock>()
        val layoutAcc = MutablePageLayout()
        val parser = OfficeXmlFormatParser.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(docBytes), "UTF-8")

        var skipDepth = -1
        var inBodySectPr = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            val localTag = tag.substringAfter(':')
            if (skipDepth >= 0) {
                if (event == XmlPullParser.END_TAG && parser.depth == skipDepth) {
                    skipDepth = -1
                }
                event = parser.next()
                continue
            }

            when (event) {
                XmlPullParser.START_TAG -> {
                    when (localTag) {
                        "Fallback" -> if (tag == "mc:Fallback" || tag == "Fallback") skipDepth = parser.depth
                        "sectPr" -> inBodySectPr = true
                        "pgSz" -> if (inBodySectPr) applyPgSz(parser, layoutAcc)
                        "pgMar" -> if (inBodySectPr) applyPgMar(parser, layoutAcc)
                        "tbl" -> parseTableElement(parser, parts, rels, styles, inlineBinData)?.let { blocks.add(it) }
                        "p" -> parseParagraphElement(parser, parts, rels, styles, inlineBinData, blocks, layoutAcc)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (localTag == "sectPr") inBodySectPr = false
                }
            }
            event = parser.next()
        }
        return WordDocumentData(blocks = blocks, layout = layoutAcc.toLayout())
    }

    private fun wordAttr(parser: XmlPullParser, localName: String): String? {
        parser.getAttributeValue(null, "w:$localName")?.let { return it }
        parser.getAttributeValue(null, localName)?.let { return it }
        for (i in 0 until parser.attributeCount) {
            val attrName = parser.getAttributeName(i) ?: continue
            if (attrName.substringAfter(':') == localName) {
                return parser.getAttributeValue(i)
            }
        }
        return null
    }

    private fun applyPgSz(parser: XmlPullParser, layout: MutablePageLayout) {
        val wTwips = wordAttr(parser, "w")?.toFloatOrNull()
        val hTwips = wordAttr(parser, "h")?.toFloatOrNull()
        val orient = wordAttr(parser, "orient")
        if (wTwips != null && wTwips > 0f && hTwips != null && hTwips > 0f) {
            layout.widthPt = wTwips / 20f
            layout.heightPt = hTwips / 20f
            layout.isLandscape = when {
                OfficeLayoutResolver.isLandscapeFromDimensions(layout.widthPt, layout.heightPt) -> true
                orient.equals("landscape", ignoreCase = true) -> true
                orient.equals("portrait", ignoreCase = true) -> false
                else -> false
            }
            layout.hasSize = true
        }
    }

    private fun applyPgMar(parser: XmlPullParser, layout: MutablePageLayout) {
        val leftTwips = wordAttr(parser, "left")?.toFloatOrNull()
        val rightTwips = wordAttr(parser, "right")?.toFloatOrNull()
        val topTwips = wordAttr(parser, "top")?.toFloatOrNull()
        val bottomTwips = wordAttr(parser, "bottom")?.toFloatOrNull()
        if (leftTwips != null || rightTwips != null || topTwips != null || bottomTwips != null) {
            leftTwips?.let { if (it >= 0f) layout.marginLeftPt = it / 20f }
            rightTwips?.let { if (it >= 0f) layout.marginRightPt = it / 20f }
            topTwips?.let { if (it >= 0f) layout.marginTopPt = it / 20f }
            bottomTwips?.let { if (it >= 0f) layout.marginBottomPt = it / 20f }
            layout.hasMargins = true
        }
    }

    private fun parseRelationships(relsBytes: ByteArray?): Map<String, String> {
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
                        val normalized = normalizeWordTarget(target)
                        rels[id] = normalized
                    }
                }
                event = parser.next()
            }
        }
        return rels
    }

    private fun normalizeWordTarget(target: String): String {
        val clean = target.trim().replace('\\', '/')
        return when {
            clean.startsWith("/") -> clean.trimStart('/').lowercase()
            clean.startsWith("../") -> clean.removePrefix("../").lowercase()
            clean.startsWith("word/", ignoreCase = true) -> clean.lowercase()
            else -> "word/${clean.lowercase()}"
        }
    }

    private fun parseStylesXml(stylesBytes: ByteArray?): Map<String, ResolvedStyle> {
        if (stylesBytes == null) return emptyMap()
        val rawStyles = HashMap<String, StyleDef>()
        runCatching {
            val parser = OfficeXmlFormatParser.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
            parser.setInput(ByteArrayInputStream(stylesBytes), "UTF-8")

            var styleId: String? = null
            var basedOn: String? = null
            var name: String? = null
            var alignment: DocAlignment? = null
            var headingLevel: Int? = null
            var isBullet: Boolean? = null
            var fontSizePt: Float? = null
            var isBold: Boolean? = null
            var isItalic: Boolean? = null
            var colorRgb: Int? = null
            var spaceBeforePt: Float? = null
            var spaceAfterPt: Float? = null
            var hasBottomBorder: Boolean? = null

            var inPPr = false
            var inRPr = false
            var inPBdr = false

            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                val tag = parser.name ?: ""
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (tag) {
                            "w:style" -> {
                                styleId = parser.getAttributeValue(null, "w:styleId")
                                basedOn = null
                                name = null
                                alignment = null
                                headingLevel = null
                                isBullet = null
                                fontSizePt = null
                                isBold = null
                                isItalic = null
                                colorRgb = null
                                spaceBeforePt = null
                                spaceAfterPt = null
                                hasBottomBorder = null
                            }
                            "w:name" -> if (styleId != null) {
                                name = parser.getAttributeValue(null, "w:val")
                                headingLevel = detectHeadingLevel(name)
                                if (name?.contains("List", true) == true) isBullet = true
                            }
                            "w:basedOn" -> if (styleId != null) {
                                basedOn = parser.getAttributeValue(null, "w:val")
                            }
                            "w:pPr" -> if (styleId != null) inPPr = true
                            "w:rPr" -> if (styleId != null) inRPr = true
                            "w:pBdr" -> if (inPPr) inPBdr = true
                            "w:bottom" -> if (inPBdr) {
                                val bVal = parser.getAttributeValue(null, "w:val") ?: ""
                                if (bVal.isNotEmpty() && !bVal.equals("none", true) && !bVal.equals("nil", true)) {
                                    hasBottomBorder = true
                                }
                            }
                            "w:jc" -> if (inPPr) {
                                alignment = parseAlignment(parser.getAttributeValue(null, "w:val"))
                            }
                            "w:spacing" -> if (inPPr) {
                                parser.getAttributeValue(null, "w:before")?.toFloatOrNull()?.let {
                                    spaceBeforePt = it / 20f
                                }
                                parser.getAttributeValue(null, "w:after")?.toFloatOrNull()?.let {
                                    spaceAfterPt = it / 20f
                                }
                            }
                            "w:b" -> if (inRPr) isBold = parseOnOffAttr(parser)
                            "w:i" -> if (inRPr) isItalic = parseOnOffAttr(parser)
                            "w:sz" -> if (inRPr) {
                                parser.getAttributeValue(null, "w:val")?.toFloatOrNull()?.let {
                                    fontSizePt = (it / 2f).coerceIn(6f, 72f)
                                }
                            }
                            "w:color" -> if (inRPr) {
                                OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "w:val"))?.let {
                                    colorRgb = it
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (tag) {
                            "w:pBdr" -> inPBdr = false
                            "w:pPr" -> inPPr = false
                            "w:rPr" -> inRPr = false
                            "w:style" -> {
                                val id = styleId
                                if (!id.isNullOrBlank()) {
                                    rawStyles[id] = StyleDef(
                                        id = id,
                                        basedOn = basedOn,
                                        name = name,
                                        alignment = alignment,
                                        headingLevel = headingLevel ?: detectHeadingLevel(id),
                                        isBullet = isBullet,
                                        fontSizePt = fontSizePt,
                                        isBold = isBold,
                                        isItalic = isItalic,
                                        colorRgb = colorRgb,
                                        spaceBeforePt = spaceBeforePt,
                                        spaceAfterPt = spaceAfterPt,
                                        hasBottomBorder = hasBottomBorder
                                    )
                                }
                                styleId = null
                            }
                        }
                    }
                }
                event = parser.next()
            }
        }

        val resolved = HashMap<String, ResolvedStyle>()
        fun resolve(id: String, visited: MutableSet<String>): ResolvedStyle {
            resolved[id]?.let { return it }
            val def = rawStyles[id] ?: return ResolvedStyle()
            val parent = if (def.basedOn != null && visited.add(id)) {
                resolve(def.basedOn, visited)
            } else {
                ResolvedStyle()
            }
            val result = ResolvedStyle(
                alignment = def.alignment ?: parent.alignment,
                headingLevel = def.headingLevel ?: parent.headingLevel,
                isBullet = def.isBullet ?: parent.isBullet,
                fontSizePt = def.fontSizePt ?: parent.fontSizePt,
                isBold = def.isBold ?: parent.isBold,
                isItalic = def.isItalic ?: parent.isItalic,
                colorRgb = def.colorRgb ?: parent.colorRgb,
                spaceBeforePt = def.spaceBeforePt ?: parent.spaceBeforePt,
                spaceAfterPt = def.spaceAfterPt ?: parent.spaceAfterPt,
                hasBottomBorder = def.hasBottomBorder ?: parent.hasBottomBorder
            )
            resolved[id] = result
            return result
        }
        rawStyles.keys.forEach { resolve(it, HashSet()) }
        return resolved
    }

    private fun parseParagraphElement(
        parser: XmlPullParser,
        parts: Map<String, ByteArray>,
        rels: Map<String, String>,
        styles: Map<String, ResolvedStyle>,
        inlineBinData: Map<String, ByteArray>,
        outBlocks: MutableList<WordBlock>,
        layoutAcc: MutablePageLayout? = null
    ) {
        val paraDepth = parser.depth
        var styleRef: ResolvedStyle? = null
        var headingLevel = 0
        var isBullet = false
        var explicitAlignment: DocAlignment? = null
        var spaceBeforePt: Float? = null
        var spaceAfterPt: Float? = null
        var leftIndentPt = 0f
        var firstLineIndentPt = 0f
        var lineSpacingMult: Float? = null
        var hasBottomBorder = false
        var pageBreakBefore = false
        var sectionBreakAfter = false
        var inSectPr = false

        var inPPr = false
        var inPBdr = false
        var inRun = false
        var inRPr = false
        var skipDepth = -1

        var runBold: Boolean? = null
        var runItalic: Boolean? = null
        var runUnderline = false
        var runStrike = false
        var runSizePt: Float? = null
        var runColorRgb: Int? = null
        var inHyperlink = false

        val runs = mutableListOf<WordRun>()
        var pendingExtentW: Float? = null
        var pendingExtentH: Float? = null
        var pendingVmlW: Float? = null
        var pendingVmlH: Float? = null

        fun flushRunsToBlock(addBottomBorder: Boolean = false) {
            val baseStyle = styleRef ?: ResolvedStyle()
            val effAlign = explicitAlignment ?: baseStyle.alignment
            val effHeading = if (headingLevel > 0) headingLevel else baseStyle.headingLevel
            val effBullet = isBullet || baseStyle.isBullet
            val effSize = baseStyle.fontSizePt
            val effBefore = spaceBeforePt ?: baseStyle.spaceBeforePt
            val effAfter = spaceAfterPt ?: baseStyle.spaceAfterPt
            val mergedText = runs.joinToString("") { it.text }.trimEnd()

            if (mergedText.isNotBlank()) {
                val normalizedRuns = runs.map { run ->
                    run.copy(
                        isBold = run.isBold || baseStyle.isBold,
                        isItalic = run.isItalic || baseStyle.isItalic,
                        fontSizePt = run.fontSizePt ?: effSize,
                        colorRgb = run.colorRgb ?: baseStyle.colorRgb
                    )
                }
                val anyBold = normalizedRuns.any { it.isBold }
                val anyItalic = normalizedRuns.any { it.isItalic }
                outBlocks.add(
                    WordBlock(
                        text = mergedText,
                        isHeading = effHeading > 0,
                        headingLevel = effHeading,
                        isBold = anyBold,
                        isItalic = anyItalic,
                        isBullet = effBullet,
                        runs = normalizedRuns,
                        alignment = effAlign,
                        fontSizePt = effSize ?: normalizedRuns.firstNotNullOfOrNull { it.fontSizePt },
                        spaceBeforePt = effBefore,
                        spaceAfterPt = effAfter,
                        leftIndentPt = leftIndentPt,
                        firstLineIndentPt = firstLineIndentPt,
                        lineSpacingMult = lineSpacingMult,
                        hasBottomBorder = addBottomBorder && (hasBottomBorder || baseStyle.hasBottomBorder)
                    )
                )
            } else if (addBottomBorder && (hasBottomBorder || baseStyle.hasBottomBorder)) {
                outBlocks.add(
                    WordBlock(
                        text = "",
                        hasBottomBorder = true,
                        spaceBeforePt = effBefore,
                        spaceAfterPt = effAfter
                    )
                )
            }
            runs.clear()
        }

        fun appendRunText(text: String) {
            if (text.isEmpty()) return
            runs.add(
                WordRun(
                    text = text,
                    isBold = runBold ?: false,
                    isItalic = runItalic ?: false,
                    isUnderline = runUnderline || inHyperlink,
                    isStrike = runStrike,
                    fontSizePt = runSizePt,
                    colorRgb = runColorRgb ?: if (inHyperlink) 0x1D4ED8 else null
                )
            )
        }

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            if (skipDepth >= 0) {
                if (event == XmlPullParser.END_TAG && parser.depth == skipDepth) {
                    skipDepth = -1
                }
                event = parser.next()
                continue
            }

            if (event == XmlPullParser.END_TAG && parser.depth == paraDepth && tag == "w:p") {
                break
            }

            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "mc:Fallback" -> skipDepth = parser.depth
                        "w:pPr" -> inPPr = true
                        "w:pBdr" -> if (inPPr) inPBdr = true
                        "w:bottom" -> if (inPBdr) {
                            val bVal = parser.getAttributeValue(null, "w:val") ?: ""
                            if (bVal.isNotEmpty() && !bVal.equals("none", true) && !bVal.equals("nil", true)) {
                                hasBottomBorder = true
                            }
                        }
                        "w:pStyle" -> if (inPPr) {
                            val styleId = parser.getAttributeValue(null, "w:val") ?: ""
                            styleRef = styles[styleId]
                            headingLevel = detectHeadingLevel(styleId) ?: headingLevel
                            if (styleId.contains("List", true)) isBullet = true
                        }
                        "w:jc" -> if (inPPr) {
                            explicitAlignment = parseAlignment(parser.getAttributeValue(null, "w:val"))
                        }
                        "w:spacing" -> if (inPPr) {
                            parser.getAttributeValue(null, "w:before")?.toFloatOrNull()?.let { spaceBeforePt = it / 20f }
                            parser.getAttributeValue(null, "w:after")?.toFloatOrNull()?.let { spaceAfterPt = it / 20f }
                            val lineVal = parser.getAttributeValue(null, "w:line")?.toFloatOrNull()
                            val lineRule = parser.getAttributeValue(null, "w:lineRule") ?: "auto"
                            if (lineVal != null && lineVal > 0f && lineRule.equals("auto", ignoreCase = true)) {
                                lineSpacingMult = (lineVal / 240f).coerceIn(1.0f, 2.2f)
                            }
                        }
                        "w:ind" -> if (inPPr) {
                            parser.getAttributeValue(null, "w:left")?.toFloatOrNull()?.let {
                                leftIndentPt = (it / 20f).coerceIn(0f, 180f)
                            }
                            parser.getAttributeValue(null, "w:firstLine")?.toFloatOrNull()?.let {
                                firstLineIndentPt = (it / 20f).coerceIn(0f, 120f)
                            }
                        }
                        "w:numPr" -> if (inPPr) isBullet = true
                        "w:pageBreakBefore" -> if (inPPr) {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "0" && v != "false") pageBreakBefore = true
                        }
                        "w:sectPr", "sectPr" -> if (inPPr) {
                            sectionBreakAfter = true
                            inSectPr = true
                        }
                        "w:pgSz", "pgSz" -> if (inSectPr && layoutAcc != null && !layoutAcc.hasSize) {
                            applyPgSz(parser, layoutAcc)
                        }
                        "w:pgMar", "pgMar" -> if (inSectPr && layoutAcc != null && !layoutAcc.hasMargins) {
                            applyPgMar(parser, layoutAcc)
                        }
                        "w:hyperlink" -> inHyperlink = true
                        "w:r" -> {
                            inRun = true
                            runBold = null
                            runItalic = null
                            runUnderline = false
                            runStrike = false
                            runSizePt = null
                            runColorRgb = null
                        }
                        "w:rPr" -> if (inRun) inRPr = true
                        "w:b" -> if (inRPr) runBold = parseOnOffAttr(parser)
                        "w:i" -> if (inRPr) runItalic = parseOnOffAttr(parser)
                        "w:u" -> if (inRPr) {
                            val uVal = parser.getAttributeValue(null, "w:val") ?: "single"
                            runUnderline = !uVal.equals("none", true)
                        }
                        "w:strike", "w:dstrike" -> if (inRPr) runStrike = parseOnOffAttr(parser)
                        "w:sz" -> if (inRPr) {
                            parser.getAttributeValue(null, "w:val")?.toFloatOrNull()?.let {
                                runSizePt = (it / 2f).coerceIn(6f, 72f)
                            }
                        }
                        "w:color" -> if (inRPr) {
                            val directRgb = OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "w:val"))
                            val themeRgb = if (directRgb == null) {
                                resolveWordThemeColor(parser.getAttributeValue(null, "w:themeColor"))
                            } else null
                            (directRgb ?: themeRgb)?.let { runColorRgb = it }
                        }
                        "w:t", "w:delText" -> {
                            val txt = parser.nextText()
                            appendRunText(txt)
                        }
                        "w:tab" -> appendRunText("    ")
                        "w:br" -> {
                            val brType = parser.getAttributeValue(null, "w:type")
                            if (brType.equals("page", ignoreCase = true)) {
                                if (pageBreakBefore) {
                                    outBlocks.add(WordBlock(isPageBreak = true))
                                    pageBreakBefore = false
                                }
                                flushRunsToBlock()
                                outBlocks.add(WordBlock(isPageBreak = true))
                            } else {
                                appendRunText("\n")
                            }
                        }
                        "wp:extent" -> {
                            val cx = parser.getAttributeValue(null, "cx")?.toFloatOrNull()
                            val cy = parser.getAttributeValue(null, "cy")?.toFloatOrNull()
                            if (cx != null && cx > 0f) pendingExtentW = cx / 12700f
                            if (cy != null && cy > 0f) pendingExtentH = cy / 12700f
                        }
                        "a:blip" -> {
                            val relId = parser.getAttributeValue(null, "r:embed")
                                ?: parser.getAttributeValue(null, "r:link")
                            val imgBytes = resolveRelationshipBytes(relId, parts, rels)
                            if (imgBytes != null) {
                                if (pageBreakBefore) {
                                    outBlocks.add(WordBlock(isPageBreak = true))
                                    pageBreakBefore = false
                                }
                                flushRunsToBlock()
                                val align = explicitAlignment ?: styleRef?.alignment ?: DocAlignment.CENTER
                                outBlocks.add(
                                    WordBlock(
                                        image = WordImage(
                                            bytes = imgBytes,
                                            widthPt = pendingExtentW,
                                            heightPt = pendingExtentH,
                                            alignment = align
                                        ),
                                        alignment = align,
                                        spaceBeforePt = 4f,
                                        spaceAfterPt = 4f
                                    )
                                )
                            }
                        }
                        "c:chart" -> {
                            val relId = parser.getAttributeValue(null, "r:id")
                            val chartBytes = resolveRelationshipBytes(relId, parts, rels)
                            val chart = chartBytes?.let { OfficeXmlFormatParser.parseChartXml(it) }
                            if (chart != null) {
                                if (pageBreakBefore) {
                                    outBlocks.add(WordBlock(isPageBreak = true))
                                    pageBreakBefore = false
                                }
                                flushRunsToBlock()
                                outBlocks.add(
                                    WordBlock(
                                        chart = chart,
                                        alignment = DocAlignment.CENTER,
                                        spaceBeforePt = 6f,
                                        spaceAfterPt = 6f
                                    )
                                )
                            }
                        }
                        "v:shape" -> {
                            val styleStr = parser.getAttributeValue(null, "style") ?: ""
                            pendingVmlW = parseCssPt(styleStr, "width")
                            pendingVmlH = parseCssPt(styleStr, "height")
                        }
                        "v:imagedata" -> {
                            val relId = parser.getAttributeValue(null, "r:id")
                                ?: parser.getAttributeValue(null, "o:relid")
                            val src = parser.getAttributeValue(null, "src")
                            val imgBytes = resolveRelationshipBytes(relId, parts, rels)
                                ?: src?.let { inlineBinData[it] ?: inlineBinData[it.lowercase()] }
                            if (imgBytes != null) {
                                if (pageBreakBefore) {
                                    outBlocks.add(WordBlock(isPageBreak = true))
                                    pageBreakBefore = false
                                }
                                flushRunsToBlock()
                                val align = explicitAlignment ?: styleRef?.alignment ?: DocAlignment.CENTER
                                outBlocks.add(
                                    WordBlock(
                                        image = WordImage(
                                            bytes = imgBytes,
                                            widthPt = pendingVmlW,
                                            heightPt = pendingVmlH,
                                            alignment = align
                                        ),
                                        alignment = align,
                                        spaceBeforePt = 4f,
                                        spaceAfterPt = 4f
                                    )
                                )
                            }
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "w:pBdr" -> inPBdr = false
                        "w:sectPr" -> inSectPr = false
                        "w:pPr" -> inPPr = false
                        "w:hyperlink" -> inHyperlink = false
                        "w:rPr" -> inRPr = false
                        "w:r" -> inRun = false
                        "w:drawing" -> {
                            pendingExtentW = null
                            pendingExtentH = null
                        }
                        "w:pict" -> {
                            pendingVmlW = null
                            pendingVmlH = null
                        }
                    }
                }
            }
            event = parser.next()
        }

        if (pageBreakBefore) {
            outBlocks.add(WordBlock(isPageBreak = true))
        }
        flushRunsToBlock(addBottomBorder = true)
        if (sectionBreakAfter) {
            outBlocks.add(WordBlock(isPageBreak = true))
        }
    }

    private fun parseTableElement(
        parser: XmlPullParser,
        parts: Map<String, ByteArray>,
        rels: Map<String, String>,
        styles: Map<String, ResolvedStyle>,
        inlineBinData: Map<String, ByteArray>
    ): WordBlock? {
        val tblDepth = parser.depth
        val rows = mutableListOf<WordTableRow>()
        var inTblPr = false
        var inTblBorders = false
        var sawBorderDecl = false
        var anyVisibleBorder = false

        var currentRowCells = mutableListOf<WordTableCell>()
        var isRowHeader = false
        var inRow = false

        var inCell = false
        var cellColSpan = 1
        var cellBgRgb: Int? = null
        var cellParagraphs = mutableListOf<WordBlock>()

        var event = parser.next()
        while (event != XmlPullParser.END_DOCUMENT) {
            val tag = parser.name ?: ""
            if (event == XmlPullParser.END_TAG && parser.depth == tblDepth && tag == "w:tbl") {
                break
            }

            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tag) {
                        "w:tblPr" -> if (!inRow) inTblPr = true
                        "w:tblBorders" -> if (inTblPr) inTblBorders = true
                        "w:top", "w:bottom", "w:left", "w:right", "w:insideH", "w:insideV" -> if (inTblBorders) {
                            sawBorderDecl = true
                            val bVal = parser.getAttributeValue(null, "w:val") ?: ""
                            if (bVal.isNotEmpty() && !bVal.equals("none", true) && !bVal.equals("nil", true)) {
                                anyVisibleBorder = true
                            }
                        }
                        "w:tr" -> {
                            inRow = true
                            isRowHeader = false
                            currentRowCells = mutableListOf()
                        }
                        "w:tblHeader" -> if (inRow && !inCell) isRowHeader = true
                        "w:tc" -> {
                            inCell = true
                            cellColSpan = 1
                            cellBgRgb = null
                            cellParagraphs = mutableListOf()
                        }
                        "w:gridSpan" -> if (inCell) {
                            cellColSpan = parser.getAttributeValue(null, "w:val")?.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        }
                        "w:shd" -> if (inCell) {
                            OfficeXmlFormatParser.parseHexRgb(parser.getAttributeValue(null, "w:fill"))?.let {
                                cellBgRgb = it
                            }
                        }
                        "w:p" -> if (inCell) {
                            parseParagraphElement(parser, parts, rels, styles, inlineBinData, cellParagraphs)
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tag) {
                        "w:tblBorders" -> inTblBorders = false
                        "w:tblPr" -> inTblPr = false
                        "w:tc" -> {
                            if (inCell) {
                                currentRowCells.add(
                                    WordTableCell(
                                        paragraphs = cellParagraphs.toList(),
                                        backgroundRgb = cellBgRgb,
                                        colSpan = cellColSpan
                                    )
                                )
                                inCell = false
                            }
                        }
                        "w:tr" -> {
                            if (inRow && currentRowCells.isNotEmpty()) {
                                rows.add(
                                    WordTableRow(
                                        cells = currentRowCells.toList(),
                                        isHeader = isRowHeader || rows.isEmpty() && anyVisibleBorder
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

        if (rows.isEmpty()) return null
        val hasBorders = if (sawBorderDecl) anyVisibleBorder else true
        return WordBlock(
            tableRows = rows,
            tableHasBorders = hasBorders,
            spaceBeforePt = 4f,
            spaceAfterPt = 6f
        )
    }

    private fun resolveRelationshipBytes(
        relId: String?,
        parts: Map<String, ByteArray>,
        rels: Map<String, String>
    ): ByteArray? {
        if (relId.isNullOrBlank()) return null
        val target = rels[relId] ?: return null
        parts[target]?.let { return it }
        val fileName = target.substringAfterLast('/')
        return parts.entries.firstOrNull { it.key.endsWith("/$fileName") }?.value
    }

    private fun parseAlignment(value: String?): DocAlignment = when (value?.lowercase()) {
        "center" -> DocAlignment.CENTER
        "right", "end" -> DocAlignment.RIGHT
        "both", "distribute" -> DocAlignment.JUSTIFY
        else -> DocAlignment.LEFT
    }

    private fun detectHeadingLevel(styleName: String?): Int? {
        if (styleName.isNullOrBlank()) return null
        return when {
            styleName.contains("Heading1", true) || styleName.contains("Heading 1", true) || styleName.equals("Title", true) -> 1
            styleName.contains("Heading2", true) || styleName.contains("Heading 2", true) || styleName.equals("Subtitle", true) -> 2
            styleName.contains("Heading", true) -> 3
            else -> null
        }
    }

    private fun parseOnOffAttr(parser: XmlPullParser): Boolean {
        val v = parser.getAttributeValue(null, "w:val")
        return v != "0" && !v.equals("false", ignoreCase = true)
    }

    private fun resolveWordThemeColor(themeColor: String?): Int? = when (themeColor?.lowercase()) {
        "accent1" -> 0x4472C4
        "accent2" -> 0xED7D31
        "accent3" -> 0x70AD47
        "accent4" -> 0xFFC000
        "accent5" -> 0x5B9BD5
        "accent6" -> 0x264478
        "hyperlink" -> 0x1D4ED8
        "dark1", "text1" -> 0x0F172A
        "dark2", "text2" -> 0x334155
        else -> null
    }

    private fun parseCssPt(style: String, prop: String): Float? {
        val regex = Regex("""$prop\s*:\s*([0-9.]+)\s*(pt|in|px)?""", RegexOption.IGNORE_CASE)
        val match = regex.find(style) ?: return null
        val value = match.groupValues[1].toFloatOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "in" -> value * 72f
            "px" -> value * 0.75f
            else -> value
        }
    }
}
