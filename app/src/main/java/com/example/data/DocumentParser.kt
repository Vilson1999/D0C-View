package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.DocViewApplication
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.OfficeMarginOption
import com.example.model.OfficePageSizeOption
import com.example.model.OfficeRenderFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.concurrent.ConcurrentHashMap

sealed interface ParsedDocument {
    data class Pdf(
        val session: PdfDocumentSession,
        val summary: OfficeContentSummary? = null
    ) : ParsedDocument

    data class Word(
        val pages: List<WordPage>,
        val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_WORD_A4,
        val summary: OfficeContentSummary? = null
    ) : ParsedDocument

    data class Slides(
        val slides: List<SlidePage>,
        val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_SLIDE_16_9,
        val summary: OfficeContentSummary? = null
    ) : ParsedDocument

    data class Spreadsheet(
        val workbook: ExcelWorkbook,
        val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_EXCEL_A4_LANDSCAPE,
        val summary: OfficeContentSummary? = null
    ) : ParsedDocument

    data class PlainText(val fullText: String, val pages: List<List<String>>) : ParsedDocument
    data class Error(val message: String) : ParsedDocument
}

class PdfDocumentSession(
    private val docKey: String,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cacheManager: DocumentCacheManager
) {
    private val mutex = Mutex()
    @Volatile
    private var isClosed = false
    val pageCount: Int get() = if (isClosed) 0 else renderer.pageCount

    suspend fun renderPage(index: Int, targetWidthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext null
        val safeWidth = targetWidthPx.coerceIn(480, 2048)
        cacheManager.getCachedPdfPageBitmap(docKey, index, safeWidth)?.let { cached ->
            return@withContext cached
        }
        mutex.withLock {
            if (isClosed || index !in 0 until renderer.pageCount) return@withLock null
            cacheManager.getCachedPdfPageBitmap(docKey, index, safeWidth)?.let { cached ->
                return@withLock cached
            }
            runCatching {
                renderer.openPage(index).use { page ->
                    val aspect = if (page.width > 0) page.height.toFloat() / page.width.toFloat() else 1.414f
                    val targetHeight = (safeWidth * aspect).toInt().coerceIn(320, 3200)
                    val bitmap = Bitmap.createBitmap(safeWidth, targetHeight, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    cacheManager.putCachedPdfPageBitmap(docKey, index, safeWidth, bitmap)
                    bitmap
                }
            }.getOrNull()
        }
    }

    suspend fun prefetchAdjacent(currentIndex: Int, targetWidthPx: Int) {
        val safeWidth = targetWidthPx.coerceIn(480, 2048)
        val neighbors = listOf(currentIndex + 1, currentIndex - 1)
        for (neighbor in neighbors) {
            if (isClosed) return
            if (neighbor in 0 until pageCount &&
                cacheManager.getCachedPdfPageBitmap(docKey, neighbor, safeWidth) == null
            ) {
                renderPage(neighbor, safeWidth)
            }
        }
    }

    fun close() {
        if (isClosed) return
        isClosed = true
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                runCatching { renderer.close() }
                runCatching { pfd.close() }
            }
        }
    }
}

class DocumentParser(private val context: Context) {

    private val cacheManager = DocumentCacheManager(context.applicationContext)

    companion object {
        private val summaryMemoryCache = ConcurrentHashMap<String, OfficeContentSummary>()
    }

    suspend fun parse(
        doc: DocumentItem,
        settings: AppSettings = AppSettings(),
        renderFormatOverride: OfficeRenderFormat? = null,
        pageSizeOverride: OfficePageSizeOption? = null,
        marginOverride: OfficeMarginOption? = null
    ): ParsedDocument = withContext(Dispatchers.IO) {
        val defaultFormat = when (doc.type) {
            DocType.DOC -> settings.docxRenderFormat
            DocType.EXCEL -> settings.xlsxRenderFormat
            DocType.PPT -> settings.pptRenderFormat
            else -> settings.officeRenderFormat
        }
        val defaultPageSize = when (doc.type) {
            DocType.DOC -> settings.docxPageSize
            DocType.EXCEL -> settings.xlsxPageSize
            DocType.PPT -> settings.pptPageSize
            else -> settings.officePageSize
        }
        val defaultMargin = when (doc.type) {
            DocType.DOC -> settings.docxMargin
            DocType.EXCEL -> settings.xlsxMargin
            DocType.PPT -> settings.pptMargin
            else -> settings.officeMargin
        }
        val renderFormat = renderFormatOverride ?: defaultFormat
        val pageSizeOption = pageSizeOverride ?: defaultPageSize
        val marginOption = marginOverride ?: defaultMargin

        if (doc.type == DocType.TEXT) {
            cacheManager.getMemoryParsed(doc)?.let { return@withContext it }
            cacheManager.loadDiskParsed(doc)?.let { return@withContext it }
        }
        DocViewApplication.beginBackgroundWork(context)
        try {
            runCatching {
                val parsed = when (doc.type) {
                    DocType.PDF -> parsePdf(doc)
                    DocType.DOC -> parseWordDocument(doc, settings, renderFormat, pageSizeOption, marginOption)
                    DocType.PPT -> parseSlidesDocument(doc, settings, renderFormat, pageSizeOption, marginOption)
                    DocType.EXCEL -> parseExcelDocument(doc, settings, renderFormat, pageSizeOption, marginOption)
                    DocType.TEXT -> parsePlainText(doc)
                }
                if (parsed is ParsedDocument.PlainText) {
                    cacheManager.saveDiskParsed(doc, parsed)
                }
                parsed
            }.getOrElse { throwable ->
                ParsedDocument.Error(throwable.localizedMessage ?: "Unable to open ${doc.name}")
            }
        } finally {
            DocViewApplication.endBackgroundWork(context)
        }
    }

    private fun openParcelFileDescriptor(doc: DocumentItem): ParcelFileDescriptor? {
        return if (doc.isFilePath) {
            val file = File(doc.path)
            if (file.exists() && file.canRead()) {
                ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            } else null
        } else {
            context.contentResolver.openFileDescriptor(doc.uri, "r")
        }
    }

    private fun openInputStream(doc: DocumentItem): InputStream? {
        return if (doc.isFilePath) {
            val file = File(doc.path)
            if (file.exists() && file.canRead()) file.inputStream() else null
        } else {
            context.contentResolver.openInputStream(doc.uri)
        }
    }

    private fun parsePdf(doc: DocumentItem): ParsedDocument {
        val pfd = openParcelFileDescriptor(doc)
            ?: return ParsedDocument.Error("Cannot access PDF file offline.")
        return openPdfFromPfd(pfd, DocumentCacheManager.docKey(doc), summary = null)
    }

    private fun openConvertedPdf(
        pdfFile: File,
        sessionKey: String,
        summary: OfficeContentSummary?
    ): ParsedDocument {
        val pfd = runCatching {
            ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrElse { err ->
            return ParsedDocument.Error("Cannot open rendered PDF: ${err.localizedMessage ?: "I/O error"}")
        }
        return openPdfFromPfd(pfd, sessionKey, summary)
    }

    private fun openPdfFromPfd(
        pfd: ParcelFileDescriptor,
        sessionKey: String,
        summary: OfficeContentSummary?
    ): ParsedDocument {
        return runCatching {
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                ParsedDocument.Error("This PDF document has no pages.")
            } else {
                ParsedDocument.Pdf(
                    session = PdfDocumentSession(
                        docKey = sessionKey,
                        pfd = pfd,
                        renderer = renderer,
                        cacheManager = cacheManager
                    ),
                    summary = summary?.copy(unitCount = renderer.pageCount)
                )
            }
        }.getOrElse { err ->
            runCatching { pfd.close() }
            ParsedDocument.Error("Could not render PDF: ${err.localizedMessage ?: "Invalid PDF"}")
        }
    }

    private fun isZipArchive(bytes: ByteArray): Boolean = bytes.size >= 4 &&
        bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
        bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun readBytesBounded(doc: DocumentItem, maxBytes: Int = 24 * 1024 * 1024): ByteArray? {
        val stream = openInputStream(doc) ?: return null
        return stream.use { input ->
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                total += read
                if (total > maxBytes) {
                    error("${doc.name} exceeds 24 MB offline parser limit.")
                }
                out.write(buffer, 0, read)
            }
            out.toByteArray()
        }
    }

    private fun summaryCacheKey(doc: DocumentItem, sizeOpt: OfficePageSizeOption, marginOpt: OfficeMarginOption): String =
        "${DocumentCacheManager.docKey(doc)}_${sizeOpt.name}_${marginOpt.name}"

    private fun parseWordDocument(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormat: OfficeRenderFormat,
        pageSizeOption: OfficePageSizeOption,
        marginOption: OfficeMarginOption
    ): ParsedDocument {
        val fontSizeKey = settings.docxFontSizeSp.toInt().coerceIn(11, 24)
        val variantTag = "doc_v3_fs${fontSizeKey}_sz${pageSizeOption.name}_mg${marginOption.name}"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumKey = summaryCacheKey(doc, pageSizeOption, marginOption)

        if (renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = summaryMemoryCache[sumKey]
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        val bytes = readBytesBounded(doc)
            ?: return ParsedDocument.Error("Cannot read document file.")

        val docData = if (bytes.isEmpty()) {
            WordDocumentData(listOf(WordBlock("(Empty document)")))
        } else {
            val parsed = when {
                isZipArchive(bytes) || doc.extension == "docx" -> OoxmlDocumentParser.parseDocxWithLayout(bytes)
                doc.extension == "rtf" || isRtfHeader(bytes) -> parseRtfWithLayout(bytes)
                else -> WordDocumentData(parseLegacyDocBytes(bytes), OfficeLayoutResolver.DEFAULT_WORD_A4)
            }
            if (parsed.blocks.isEmpty()) {
                parsed.copy(blocks = listOf(WordBlock("No readable content found in ${doc.name}.")))
            } else {
                parsed
            }
        }

        val resolvedLayout = OfficeLayoutResolver.resolve(
            detected = docData.layout,
            sizeOverride = pageSizeOption,
            marginOverride = marginOption,
            isSpreadsheet = false
        )
        val pages = paginateWordBlocks(docData.blocks, resolvedLayout, settings.docxFontSizeSp)
        val summary = buildWordSummary(docData.blocks, docData.layout, resolvedLayout, pages.size)
        summaryMemoryCache[sumKey] = summary

        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            return ParsedDocument.Word(pages = pages, layout = resolvedLayout, summary = summary)
        }

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertWordToPdf(
            blocks = docData.blocks,
            baseFontSizeSp = settings.docxFontSizeSp,
            outputFile = cachedPdf,
            layout = resolvedLayout
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            ParsedDocument.Word(pages = pages, layout = resolvedLayout, summary = summary)
        }
    }

    private fun buildWordSummary(
        blocks: List<WordBlock>,
        detectedLayout: OfficePageLayout,
        effectiveLayout: OfficePageLayout,
        pageCount: Int
    ): OfficeContentSummary {
        var texts = 0
        var images = 0
        var charts = 0
        var tables = 0
        for (b in blocks) {
            when {
                b.image != null -> images++
                b.chart != null -> charts++
                b.tableRows.isNotEmpty() -> {
                    tables++
                    b.tableRows.forEach { r ->
                        r.cells.forEach { c ->
                            c.paragraphs.forEach { p ->
                                if (p.image != null) images++
                                else if (p.text.isNotBlank()) texts++
                            }
                        }
                    }
                }
                b.text.isNotBlank() -> texts++
            }
        }
        return OfficeContentSummary(
            detectedPageSize = detectedLayout.detectedPaperName,
            detectedMargin = detectedLayout.detectedMarginName,
            effectivePageSize = effectiveLayout.detectedPaperName,
            effectiveMargin = effectiveLayout.detectedMarginName,
            isLandscape = effectiveLayout.isLandscape,
            widthPt = effectiveLayout.widthPt,
            heightPt = effectiveLayout.heightPt,
            textCount = texts,
            imageCount = images,
            chartCount = charts,
            tableCount = tables,
            unitCount = pageCount.coerceAtLeast(1),
            unitLabel = "Pages"
        )
    }

    private fun paginateWordBlocks(
        blocks: List<WordBlock>,
        layout: OfficePageLayout,
        baseFontSizeSp: Float
    ): List<WordPage> {
        val availHeightPt = (layout.heightPt - layout.marginTopPt - layout.marginBottomPt).coerceAtLeast(240f)
        val availWidthPt = (layout.widthPt - layout.marginLeftPt - layout.marginRightPt).coerceAtLeast(180f)
        val pages = mutableListOf<WordPage>()
        var current = mutableListOf<WordBlock>()
        var usedHeight = 0f

        for (block in blocks) {
            if (block.isPageBreak) {
                if (current.isNotEmpty()) {
                    pages.add(WordPage(pages.size + 1, current.toList()))
                    current = mutableListOf()
                    usedHeight = 0f
                }
                continue
            }
            val estHeight = when {
                block.image != null -> (block.image.heightPt ?: 180f).coerceIn(40f, availHeightPt * 0.7f) + 12f
                block.chart != null -> 240f
                block.tableRows.isNotEmpty() -> (block.tableRows.size * 26f).coerceIn(40f, availHeightPt * 0.85f)
                else -> {
                    val fontSize = (block.fontSizePt ?: baseFontSizeSp * 0.85f).coerceIn(9f, 28f)
                    val charsPerLine = (availWidthPt / (fontSize * 0.52f)).toInt().coerceAtLeast(24)
                    val lines = ((block.text.length / charsPerLine) + 1).coerceAtLeast(1)
                    lines * fontSize * 1.4f + 8f
                }
            }
            if (usedHeight + estHeight > availHeightPt && current.isNotEmpty()) {
                pages.add(WordPage(pages.size + 1, current.toList()))
                current = mutableListOf()
                usedHeight = 0f
            }
            current.add(block)
            usedHeight += estHeight
        }
        if (current.isNotEmpty() || pages.isEmpty()) {
            pages.add(WordPage(pages.size + 1, current.ifEmpty { listOf(WordBlock("(Empty document)")) }))
        }
        return pages
    }

    private fun isRtfHeader(bytes: ByteArray): Boolean {
        if (bytes.size < 5) return false
        val head = String(bytes, 0, minOf(bytes.size, 16), Charsets.US_ASCII)
        return head.trimStart().startsWith("{\\rtf")
    }

    private fun parseRtfWithLayout(bytes: ByteArray): WordDocumentData {
        val raw = String(bytes, Charsets.ISO_8859_1)
        val header = raw.take(4096)
        val paperW = Regex("""\\paperw(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 595f
        val paperH = Regex("""\\paperh(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 842f
        val margL = Regex("""\\margl(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 54f
        val margR = Regex("""\\margr(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 54f
        val margT = Regex("""\\margt(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 54f
        val margB = Regex("""\\margb(\d+)""").find(header)?.groupValues?.get(1)?.toFloatOrNull()?.div(20f) ?: 54f
        val landscape = if (header.contains("\\landscape") || header.contains("\\lndscpsxn")) true else null

        val layout = OfficeLayoutResolver.buildLayoutFromSizeAndMargins(
            rawWidthPt = paperW,
            rawHeightPt = paperH,
            leftPt = margL,
            rightPt = margR,
            topPt = margT,
            bottomPt = margB,
            forceLandscape = landscape
        )

        val stripped = raw
            .replace(Regex("""\\par\b\s*"""), "\n")
            .replace(Regex("""\\line\b\s*"""), "\n")
            .replace(Regex("""\\tab\b\s*"""), "    ")
            .replace(Regex("""\\'[0-9a-fA-F]{2}"""), " ")
            .replace(Regex("""\\[a-zA-Z]+-?\d*\s?"""), "")
            .replace("{", "")
            .replace("}", "")
        val blocks = stripped.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("Times New Roman") && !it.startsWith("Calibri") && !it.startsWith("Arial") }
            .mapIndexed { idx, line -> WordBlock(text = line, isHeading = idx == 0 && line.length < 80) }

        return WordDocumentData(blocks = blocks, layout = layout)
    }

    private fun parseLegacyDocBytes(bytes: ByteArray): List<WordBlock> {
        val blocks = mutableListOf<WordBlock>()
        val extracted = extractReadableRunsFromBinary(bytes)
        extracted.lines()
            .map { it.trim() }
            .filter { it.length >= 2 && it.any { ch -> ch.isLetterOrDigit() } }
            .distinct()
            .forEachIndexed { idx, line ->
                blocks.add(WordBlock(text = line, isHeading = idx == 0 && line.length < 80))
            }
        return blocks
    }

    private fun parseSlidesDocument(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormat: OfficeRenderFormat,
        pageSizeOption: OfficePageSizeOption,
        marginOption: OfficeMarginOption
    ): ParsedDocument {
        val notesFlag = if (settings.showPptSpeakerNotes) 1 else 0
        val variantTag = "ppt_v2_n${notesFlag}_sz${pageSizeOption.name}_mg${marginOption.name}"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumKey = summaryCacheKey(doc, pageSizeOption, marginOption)

        if (renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = summaryMemoryCache[sumKey]
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        val bytes = readBytesBounded(doc)
            ?: return ParsedDocument.Error("Cannot read presentation file.")

        val deck = if (bytes.isEmpty()) {
            PresentationDeck(
                slides = listOf(SlidePage(1, doc.name, listOf("Empty presentation"))),
                layout = OfficeLayoutResolver.DEFAULT_SLIDE_16_9
            )
        } else {
            val parsed = if (isZipArchive(bytes) || doc.extension in setOf("pptx", "pptm", "potx")) {
                OoxmlPresentationParser.parsePptxBytes(bytes)
            } else {
                PresentationDeck(
                    slides = parseLegacyPptBytes(bytes),
                    layout = OfficeLayoutResolver.DEFAULT_SLIDE_16_9
                )
            }
            if (parsed.slides.isEmpty()) {
                parsed.copy(
                    slides = listOf(SlidePage(1, doc.name, listOf("No readable slide content found.")))
                )
            } else {
                parsed
            }
        }

        val effectiveSlides = if (settings.showPptSpeakerNotes) {
            deck.slides
        } else {
            deck.slides.map { it.copy(notes = "") }
        }

        val resolvedLayout = OfficeLayoutResolver.resolve(
            detected = deck.layout,
            sizeOverride = pageSizeOption,
            marginOverride = marginOption,
            isSpreadsheet = false
        )
        val summary = buildSlidesSummary(effectiveSlides, deck.layout, resolvedLayout)
        summaryMemoryCache[sumKey] = summary

        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            return ParsedDocument.Slides(slides = effectiveSlides, layout = resolvedLayout, summary = summary)
        }

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertSlidesToPdf(
            slides = effectiveSlides,
            outputFile = cachedPdf,
            layout = resolvedLayout
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            ParsedDocument.Slides(slides = effectiveSlides, layout = resolvedLayout, summary = summary)
        }
    }

    private fun buildSlidesSummary(
        slides: List<SlidePage>,
        detectedLayout: OfficePageLayout,
        effectiveLayout: OfficePageLayout
    ): OfficeContentSummary {
        var texts = 0
        var images = 0
        var charts = 0
        var tables = 0
        for (slide in slides) {
            if (slide.blocks.isNotEmpty()) {
                slide.blocks.forEach { b ->
                    when {
                        b.image != null -> images++
                        b.chart != null -> charts++
                        b.tableRows.isNotEmpty() -> tables++
                        b.text.isNotBlank() -> texts++
                    }
                }
            } else {
                texts += 1 + slide.bodyLines.size
            }
        }
        return OfficeContentSummary(
            detectedPageSize = detectedLayout.detectedPaperName,
            detectedMargin = detectedLayout.detectedMarginName,
            effectivePageSize = effectiveLayout.detectedPaperName,
            effectiveMargin = effectiveLayout.detectedMarginName,
            isLandscape = effectiveLayout.isLandscape,
            widthPt = effectiveLayout.widthPt,
            heightPt = effectiveLayout.heightPt,
            textCount = texts,
            imageCount = images,
            chartCount = charts,
            tableCount = tables,
            unitCount = slides.size.coerceAtLeast(1),
            unitLabel = "Slides"
        )
    }

    private fun parseLegacyPptBytes(bytes: ByteArray): List<SlidePage> {
        val chunks = mutableListOf<String>()
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        var pos = 0
        while (pos + 8 <= bytes.size) {
            val recType = buffer.getShort(pos + 2).toInt() and 0xFFFF
            val recLen = buffer.getInt(pos + 4)
            if ((recType == 0x0FA0 || recType == 0x0FA8) && recLen in 2..131072 && pos + 8 + recLen <= bytes.size) {
                val slice = bytes.copyOfRange(pos + 8, pos + 8 + recLen)
                val decoded = if (recType == 0x0FA0) {
                    String(slice, Charsets.UTF_16LE)
                } else {
                    String(slice, Charsets.ISO_8859_1)
                }.replace('\r', '\n').trim()
                if (decoded.any { it.isLetterOrDigit() }) {
                    chunks.add(decoded)
                }
                pos += 8 + recLen
            } else {
                pos += 1
            }
        }

        if (chunks.isEmpty()) {
            val fallback = extractReadableRunsFromBinary(bytes)
                .lines()
                .map { it.trim() }
                .filter { it.length >= 3 }
            if (fallback.isEmpty()) return emptyList()
            return fallback.chunked(8).mapIndexed { index, lines ->
                SlidePage(
                    slideNumber = index + 1,
                    title = lines.firstOrNull() ?: "Slide ${index + 1}",
                    bodyLines = lines.drop(1)
                )
            }
        }

        return chunks.chunked(4).mapIndexed { index, group ->
            val lines = group.flatMap { it.lines() }.map { it.trim() }.filter { it.isNotEmpty() }
            SlidePage(
                slideNumber = index + 1,
                title = lines.firstOrNull() ?: "Slide ${index + 1}",
                bodyLines = lines.drop(1)
            )
        }
    }

    private fun extractReadableRunsFromBinary(bytes: ByteArray): String {
        val sb = StringBuilder()
        var i = 0
        val current = StringBuilder()
        while (i + 1 < bytes.size) {
            val lo = bytes[i].toInt() and 0xFF
            val hi = bytes[i + 1].toInt() and 0xFF
            val ch = ((hi shl 8) or lo).toChar()
            if (hi == 0 && (lo in 32..126 || lo == 10 || lo == 13 || lo == 9)) {
                current.append(if (lo == 13) '\n' else ch)
                i += 2
            } else {
                if (current.length >= 6) {
                    sb.append(current.toString().trim()).append('\n')
                }
                current.clear()
                i += 2
            }
        }
        if (current.length >= 6) sb.append(current.toString().trim()).append('\n')

        if (sb.length < 32) {
            val ascii = StringBuilder()
            for (b in bytes) {
                val c = b.toInt() and 0xFF
                if (c in 32..126 || c == 10 || c == 9) {
                    ascii.append(c.toChar())
                } else {
                    if (ascii.length >= 8) {
                        sb.append(ascii.toString().trim()).append('\n')
                    }
                    ascii.clear()
                }
            }
            if (ascii.length >= 8) sb.append(ascii.toString().trim()).append('\n')
        }
        return sb.toString()
    }

    private fun parseExcelDocument(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormat: OfficeRenderFormat,
        pageSizeOption: OfficePageSizeOption,
        marginOption: OfficeMarginOption
    ): ParsedDocument {
        val fontSizeKey = settings.xlsxFontSizeSp.toInt().coerceIn(11, 22)
        val gridFlag = if (settings.showExcelGridlines) 1 else 0
        val variantTag = "xlsx_v2_fs${fontSizeKey}_gl${gridFlag}_sz${pageSizeOption.name}_mg${marginOption.name}"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumKey = summaryCacheKey(doc, pageSizeOption, marginOption)

        if (renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = summaryMemoryCache[sumKey]
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        val bytes = readBytesBounded(doc)
            ?: return ParsedDocument.Error("Cannot read spreadsheet file.")

        val workbook = if (bytes.isEmpty()) {
            ExcelWorkbook(listOf(ExcelSheet(name = "Sheet1")))
        } else if (isZipArchive(bytes) || doc.extension in setOf("xlsx", "xlsm", "xltx")) {
            OoxmlWorkbookParser.parseXlsxBytes(bytes)
        } else {
            parseLegacyXlsBytes(bytes, doc.name)
        }

        val normalizedWorkbook = if (workbook.sheets.isEmpty()) {
            ExcelWorkbook(
                listOf(
                    ExcelSheet(
                        name = "Sheet1",
                        maxColumns = 1,
                        rows = listOf(
                            ExcelRow(
                                rowNumber = 1,
                                cells = listOf(ExcelCell(colIndex = 0, text = "No readable worksheet cells found in ${doc.name}."))
                            )
                        )
                    )
                )
            )
        } else {
            workbook
        }

        val resolvedLayout = OfficeLayoutResolver.resolve(
            detected = normalizedWorkbook.layout,
            sizeOverride = pageSizeOption,
            marginOverride = marginOption,
            isSpreadsheet = true
        )
        val summary = buildExcelSummary(normalizedWorkbook, normalizedWorkbook.layout, resolvedLayout)
        summaryMemoryCache[sumKey] = summary

        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            return ParsedDocument.Spreadsheet(
                workbook = normalizedWorkbook,
                layout = resolvedLayout,
                summary = summary
            )
        }

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertExcelToPdf(
            workbook = normalizedWorkbook,
            showGridlines = settings.showExcelGridlines,
            baseFontSizeSp = settings.xlsxFontSizeSp,
            outputFile = cachedPdf,
            pageSizeOption = pageSizeOption,
            marginOption = marginOption
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            ParsedDocument.Spreadsheet(
                workbook = normalizedWorkbook,
                layout = resolvedLayout,
                summary = summary
            )
        }
    }

    private fun buildExcelSummary(
        workbook: ExcelWorkbook,
        detectedLayout: OfficePageLayout,
        effectiveLayout: OfficePageLayout
    ): OfficeContentSummary {
        var nonEmptyCells = 0
        var images = 0
        var charts = 0
        for (sheet in workbook.sheets) {
            sheet.rows.forEach { r ->
                nonEmptyCells += r.cells.count { it.text.isNotBlank() }
            }
            sheet.embeddedBlocks.forEach { b ->
                if (b.image != null) images++
                if (b.chart != null) charts++
            }
        }
        return OfficeContentSummary(
            detectedPageSize = detectedLayout.detectedPaperName,
            detectedMargin = detectedLayout.detectedMarginName,
            effectivePageSize = effectiveLayout.detectedPaperName,
            effectiveMargin = effectiveLayout.detectedMarginName,
            isLandscape = effectiveLayout.isLandscape,
            widthPt = effectiveLayout.widthPt,
            heightPt = effectiveLayout.heightPt,
            textCount = nonEmptyCells,
            imageCount = images,
            chartCount = charts,
            tableCount = workbook.sheets.size,
            unitCount = workbook.sheets.size.coerceAtLeast(1),
            unitLabel = "Sheets"
        )
    }

    private fun parseLegacyXlsBytes(bytes: ByteArray, fileName: String): ExcelWorkbook {
        val extracted = extractReadableRunsFromBinary(bytes)
        val lines = extracted.lines()
            .map { it.trim() }
            .filter { it.length >= 2 && it.any { ch -> ch.isLetterOrDigit() } }
            .distinct()
            .take(500)

        val rows = if (lines.isEmpty()) {
            listOf(
                ExcelRow(
                    rowNumber = 1,
                    cells = listOf(ExcelCell(colIndex = 0, text = "No readable legacy .xls content found in $fileName."))
                )
            )
        } else {
            lines.mapIndexed { idx, line ->
                val cols = line.split('\t').filter { it.isNotBlank() }.ifEmpty { listOf(line) }
                ExcelRow(
                    rowNumber = idx + 1,
                    cells = cols.take(12).mapIndexed { cIdx, cellText ->
                        ExcelCell(colIndex = cIdx, text = cellText.trim(), isBold = idx == 0)
                    },
                    isHeaderLikely = idx == 0
                )
            }
        }
        val maxCols = rows.maxOfOrNull { it.cells.maxOfOrNull { c -> c.colIndex + 1 } ?: 1 } ?: 1
        return ExcelWorkbook(listOf(ExcelSheet(name = "Sheet1", maxColumns = maxCols, rows = rows)))
    }

    private fun parsePlainText(doc: DocumentItem): ParsedDocument {
        val text = readTextSmart(doc)
            ?: return ParsedDocument.Error("Cannot read text file.")
        val lines = text.lines()
        val pages = if (lines.isEmpty()) listOf(listOf("")) else lines.chunked(42)
        return ParsedDocument.PlainText(fullText = text, pages = pages)
    }

    private fun readTextSmart(doc: DocumentItem): String? {
        val stream = openInputStream(doc) ?: return null
        return stream.use { input ->
            val maxBytes = 4 * 1024 * 1024
            val buffer = ByteArray(8192)
            val out = ByteArrayOutputStream()
            var total = 0
            var truncated = false
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                if (total + read > maxBytes) {
                    val remaining = maxBytes - total
                    if (remaining > 0) out.write(buffer, 0, remaining)
                    truncated = true
                    break
                }
                out.write(buffer, 0, read)
                total += read
            }
            val bytes = out.toByteArray()
            val charset: Charset = when {
                bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            val decoded = String(bytes, charset).removePrefix("\uFEFF")
            if (truncated) "$decoded\n\n… [Preview truncated at 4 MB for smooth scrolling]" else decoded
        }
    }
}
