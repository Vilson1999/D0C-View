package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.example.DocViewApplication
import com.example.model.AppSettings
import com.example.model.DocType
import com.example.model.DocumentItem
import com.example.model.DocumentViewOrientation
import com.example.model.OfficeRenderFormat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
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

    data class PlainText(
        val fullText: String,
        val pages: List<List<String>>,
        val layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_WORD_A4,
        val summary: OfficeContentSummary? = null
    ) : ParsedDocument

    data class Error(val message: String) : ParsedDocument
}

object PdfRenderQualityPolicy {
    const val MIN_RENDER_DIM_PX = 480
    const val MAX_SAFE_TEXTURE_PX = 4096

    fun normalizeRequestedWidth(requestedWidthPx: Int): Int =
        requestedWidthPx.coerceIn(MIN_RENDER_DIM_PX, MAX_SAFE_TEXTURE_PX)

    fun resolveRenderSize(
        pageWidth: Int,
        pageHeight: Int,
        requestedDisplayWidthPx: Int,
        needsRotate: Boolean
    ): Pair<Int, Int> {
        val postNativeW = (if (needsRotate) pageHeight else pageWidth).coerceAtLeast(1)
        val postNativeH = (if (needsRotate) pageWidth else pageHeight).coerceAtLeast(1)
        val aspectPost = postNativeH.toFloat() / postNativeW.toFloat()

        // Keep native PDF dimensions when the page is >= display width, and match display pixels
        // for 72dpi vector pages without artificially boosting or shrinking quality.
        val desiredPostW = maxOf(postNativeW, normalizeRequestedWidth(requestedDisplayWidthPx))
            .coerceIn(MIN_RENDER_DIM_PX, MAX_SAFE_TEXTURE_PX)
        val rawScaledH = (desiredPostW * aspectPost).toInt().coerceAtLeast(1)
        val finalPostH = rawScaledH.coerceAtMost(MAX_SAFE_TEXTURE_PX)
        val finalPostW = if (rawScaledH > MAX_SAFE_TEXTURE_PX) {
            (MAX_SAFE_TEXTURE_PX / aspectPost).toInt().coerceAtLeast(1)
        } else {
            desiredPostW
        }

        val rawW = if (needsRotate) finalPostH else finalPostW
        val rawH = if (needsRotate) finalPostW else finalPostH
        return rawW to rawH
    }
}

class PdfDocumentSession(
    private val docKey: String,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    private val cacheManager: DocumentCacheManager,
    val viewOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO,
    val isFirstPageNaturallyLandscape: Boolean = false,
    private val firstPageAspectRatio: Float = 0.707f
) {
    private val effectiveCacheKey: String = if (viewOrientation == DocumentViewOrientation.AUTO) {
        docKey
    } else {
        "${docKey}_orient_${viewOrientation.name.lowercase()}"
    }
    private val mutex = Mutex()
    private val pageAspectRatios = ConcurrentHashMap<Int, Float>().apply {
        put(0, firstPageAspectRatio.coerceIn(0.35f, 3.0f))
    }
    @Volatile
    private var isClosed = false
    val pageCount: Int get() = if (isClosed) 0 else renderer.pageCount

    fun getPageAspectRatio(index: Int): Float =
        pageAspectRatios[index] ?: if (isFirstPageNaturallyLandscape) 1.414f else 0.707f

    fun peekCachedPage(index: Int, targetWidthPx: Int): Bitmap? {
        if (isClosed) return null
        val safeWidth = PdfRenderQualityPolicy.normalizeRequestedWidth(targetWidthPx)
        return cacheManager.getMemoryPdfPageBitmap(effectiveCacheKey, index, safeWidth)
    }

    suspend fun renderPage(
        index: Int,
        targetWidthPx: Int,
        persistSync: Boolean = false
    ): Bitmap? = withContext(Dispatchers.IO) {
        if (isClosed) return@withContext null
        val safeWidth = PdfRenderQualityPolicy.normalizeRequestedWidth(targetWidthPx)
        cacheManager.getCachedPdfPageBitmap(effectiveCacheKey, index, safeWidth)?.let { cached ->
            return@withContext cached
        }
        mutex.withLock {
            if (isClosed || index !in 0 until renderer.pageCount) return@withLock null
            cacheManager.getCachedPdfPageBitmap(effectiveCacheKey, index, safeWidth)?.let { cached ->
                return@withLock cached
            }
            runCatching {
                renderer.openPage(index).use { page ->
                    val needsRotate = when (viewOrientation) {
                        DocumentViewOrientation.AUTO -> false
                        DocumentViewOrientation.PORTRAIT -> page.width > page.height
                        DocumentViewOrientation.LANDSCAPE -> page.width < page.height
                    }
                    val postW = if (needsRotate) page.height else page.width
                    val postH = if (needsRotate) page.width else page.height
                    if (postW > 0 && postH > 0) {
                        pageAspectRatios[index] = (postW.toFloat() / postH.toFloat()).coerceIn(0.35f, 3.0f)
                    }
                    val (rawW, rawH) = PdfRenderQualityPolicy.resolveRenderSize(
                        pageWidth = page.width,
                        pageHeight = page.height,
                        requestedDisplayWidthPx = safeWidth,
                        needsRotate = needsRotate
                    )

                    val rawBitmap = Bitmap.createBitmap(rawW, rawH, Bitmap.Config.ARGB_8888)
                    rawBitmap.eraseColor(Color.WHITE)
                    page.render(rawBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    val finalBitmap = if (needsRotate) {
                        val matrix = Matrix().apply { postRotate(90f) }
                        val rotated = Bitmap.createBitmap(
                            rawBitmap,
                            0,
                            0,
                            rawBitmap.width,
                            rawBitmap.height,
                            matrix,
                            true
                        )
                        if (rotated !== rawBitmap) rawBitmap.recycle()
                        rotated
                    } else {
                        rawBitmap
                    }
                    cacheManager.putCachedPdfPageBitmap(
                        docKey = effectiveCacheKey,
                        pageIndex = index,
                        widthPx = safeWidth,
                        bitmap = finalBitmap,
                        persistSync = persistSync
                    )
                    finalBitmap
                }
            }.getOrNull()
        }
    }

    suspend fun prefetchAdjacent(currentIndex: Int, targetWidthPx: Int) {
        val safeWidth = PdfRenderQualityPolicy.normalizeRequestedWidth(targetWidthPx)
        val neighbors = listOf(currentIndex + 1, currentIndex - 1, currentIndex + 2)
        for (neighbor in neighbors) {
            if (isClosed) return
            if (neighbor in 0 until pageCount &&
                cacheManager.getCachedPdfPageBitmap(effectiveCacheKey, neighbor, safeWidth) == null
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

    private fun isSourceAccessible(doc: DocumentItem): Boolean {
        return !doc.isFilePath || File(doc.path).let { it.exists() && it.canRead() }
    }

    suspend fun parse(
        doc: DocumentItem,
        settings: AppSettings = AppSettings(),
        renderFormatOverride: OfficeRenderFormat? = null,
        documentOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO
    ): ParsedDocument = withContext(Dispatchers.IO) {
        DocViewApplication.beginBackgroundWork(context)
        try {
            parseInternal(
                doc = doc,
                settings = settings,
                renderFormatOverride = renderFormatOverride,
                documentOrientation = documentOrientation
            )
        } finally {
            DocViewApplication.endBackgroundWork(context)
        }
    }

    suspend fun prewarmDocuments(
        docs: List<DocumentItem>,
        settings: AppSettings,
        maxPrewarmDocs: Int = 5
    ) = withContext(Dispatchers.IO) {
        val dm = context.resources.displayMetrics
        val widthPx = PdfRenderQualityPolicy.normalizeRequestedWidth(minOf(dm.widthPixels, dm.heightPixels))
        var warmed = 0
        for (doc in docs) {
            if (!currentCoroutineContext().isActive || warmed >= maxPrewarmDocs) break
            if (doc.sizeBytes > 6L * 1024L * 1024L || !isSourceAccessible(doc)) continue
            warmed++
            runCatching {
                val parsed = parseInternal(
                    doc = doc,
                    settings = settings,
                    documentOrientation = settings.viewOrientationFor(doc.type)
                )
                if (parsed is ParsedDocument.Pdf) {
                    try {
                        parsed.session.renderPage(0, widthPx, persistSync = true)
                    } finally {
                        parsed.session.close()
                    }
                }
            }
        }
    }

    private fun parseInternal(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormatOverride: OfficeRenderFormat? = null,
        documentOrientation: DocumentViewOrientation = DocumentViewOrientation.AUTO
    ): ParsedDocument {
        val defaultFormat = when (doc.type) {
            DocType.DOC -> settings.docxRenderFormat
            DocType.EXCEL -> settings.xlsxRenderFormat
            DocType.PPT -> settings.pptRenderFormat
            DocType.TEXT -> settings.textRenderFormat
            DocType.PDF -> OfficeRenderFormat.PDF
        }
        val renderFormat = renderFormatOverride ?: defaultFormat

        return runCatching {
            when (doc.type) {
                DocType.PDF -> parsePdf(doc, documentOrientation)
                DocType.DOC -> parseWordDocument(doc, settings, renderFormat, documentOrientation)
                DocType.PPT -> parseSlidesDocument(doc, settings, renderFormat, documentOrientation)
                DocType.EXCEL -> parseExcelDocument(doc, settings, renderFormat, documentOrientation)
                DocType.TEXT -> parseTextDocument(doc, settings, renderFormat, documentOrientation)
            }
        }.getOrElse { throwable ->
            ParsedDocument.Error(throwable.localizedMessage ?: "Unable to open ${doc.name}")
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

    private fun parsePdf(
        doc: DocumentItem,
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        val sessionKey = DocumentCacheManager.docKey(doc)
        val pfd = runCatching { openParcelFileDescriptor(doc) }.getOrNull()
        if (pfd != null) {
            val directResult = openPdfFromPfd(
                pfd = pfd,
                sessionKey = sessionKey,
                summary = null,
                documentOrientation = documentOrientation
            )
            if (directResult !is ParsedDocument.Error || doc.isFilePath) {
                return directResult
            }
        } else if (doc.isFilePath) {
            return ParsedDocument.Error("Cannot access PDF file offline.")
        }

        // Fallback for non-seekable or pipe-backed content:// URIs opened from other apps
        val localCopy = File(context.cacheDir, "ext_pdf_${sessionKey}.pdf")
        val copied = runCatching {
            val stream = openInputStream(doc) ?: return@runCatching false
            stream.use { input ->
                localCopy.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            localCopy.exists() && localCopy.length() > 0L
        }.getOrDefault(false)
        if (!copied) {
            return ParsedDocument.Error("Cannot access PDF file offline.")
        }
        val copyPfd = runCatching {
            ParcelFileDescriptor.open(localCopy, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return ParsedDocument.Error("Cannot access PDF file offline.")
        return openPdfFromPfd(
            pfd = copyPfd,
            sessionKey = sessionKey,
            summary = null,
            documentOrientation = documentOrientation
        )
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
        return openPdfFromPfd(pfd, sessionKey, summary, DocumentViewOrientation.AUTO)
    }

    private fun openPdfFromPfd(
        pfd: ParcelFileDescriptor,
        sessionKey: String,
        summary: OfficeContentSummary?,
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        return runCatching {
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                ParsedDocument.Error("This PDF document has no pages.")
            } else {
                val firstPageInfo = runCatching {
                    renderer.openPage(0).use { p ->
                        val naturalLandscape = p.width > p.height
                        val needsRotate = when (documentOrientation) {
                            DocumentViewOrientation.AUTO -> false
                            DocumentViewOrientation.PORTRAIT -> p.width > p.height
                            DocumentViewOrientation.LANDSCAPE -> p.width < p.height
                        }
                        val postW = if (needsRotate) p.height else p.width
                        val postH = if (needsRotate) p.width else p.height
                        val aspect = if (postH > 0) (postW.toFloat() / postH.toFloat()).coerceIn(0.35f, 3.0f) else 0.707f
                        naturalLandscape to aspect
                    }
                }.getOrDefault(false to 0.707f)
                ParsedDocument.Pdf(
                    session = PdfDocumentSession(
                        docKey = sessionKey,
                        pfd = pfd,
                        renderer = renderer,
                        cacheManager = cacheManager,
                        viewOrientation = documentOrientation,
                        isFirstPageNaturallyLandscape = firstPageInfo.first,
                        firstPageAspectRatio = firstPageInfo.second
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

    private fun orientationSuffix(orientation: DocumentViewOrientation): String {
        return if (orientation == DocumentViewOrientation.AUTO) "" else "_${orientation.name.lowercase()}"
    }

    private fun parseWordDocument(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormat: OfficeRenderFormat,
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        val orientSuffix = orientationSuffix(documentOrientation)
        val fontSizeKey = settings.docxFontSizeSp.toInt().coerceIn(11, 24)
        val variantTag = "doc_v5_fs$fontSizeKey$orientSuffix"
        val origVariantTag = "${variantTag}_orig_a4"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumVariant = "doc_v3$orientSuffix"
        val origSumVariant = "${sumVariant}_orig_a4"
        val accessible = isSourceAccessible(doc)

        if (accessible && renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = cacheManager.getMemorySummary(doc, sumVariant)
                ?: cacheManager.loadDiskSummary(doc, sumVariant)
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        if (accessible && renderFormat == OfficeRenderFormat.ORIGINAL) {
            (cacheManager.getMemoryParsed(doc, origVariantTag)
                ?: cacheManager.loadDiskParsed(doc, origVariantTag))?.let { cached ->
                if (cached is ParsedDocument.Word) {
                    cached.summary?.let { cacheManager.putMemorySummary(doc, it, origSumVariant) }
                    return cached
                }
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

        val detectedLayout = docData.layout
        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            val origLayout = OfficeLayoutResolver.withA4Default(detectedLayout, documentOrientation)
            val origPages = paginateWordBlocks(docData.blocks, origLayout, settings.docxFontSizeSp)
            val origSummary = buildWordSummary(docData.blocks, detectedLayout, origLayout, origPages.size)
            cacheManager.saveDiskSummary(doc, origSummary, origSumVariant)
            return ParsedDocument.Word(pages = origPages, layout = origLayout, summary = origSummary).also {
                cacheManager.saveDiskParsed(doc, it, origVariantTag)
            }
        }

        val effectiveLayout = OfficeLayoutResolver.applyOrientation(detectedLayout, documentOrientation)
        val pages = paginateWordBlocks(docData.blocks, effectiveLayout, settings.docxFontSizeSp)
        val summary = buildWordSummary(docData.blocks, detectedLayout, effectiveLayout, pages.size)
        cacheManager.saveDiskSummary(doc, summary, sumVariant)
        val wordParsed = ParsedDocument.Word(pages = pages, layout = effectiveLayout, summary = summary)
        cacheManager.putMemoryParsed(doc, wordParsed, variantTag)

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertWordToPdf(
            blocks = docData.blocks,
            baseFontSizeSp = settings.docxFontSizeSp,
            outputFile = cachedPdf,
            layout = effectiveLayout
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            cacheManager.saveDiskParsed(doc, wordParsed, variantTag)
            wordParsed
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
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        val orientSuffix = orientationSuffix(documentOrientation)
        val notesFlag = if (settings.showPptSpeakerNotes) 1 else 0
        val variantTag = "ppt_v4_n$notesFlag$orientSuffix"
        val origVariantTag = "${variantTag}_orig_a4"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumVariant = "ppt_v3_n$notesFlag$orientSuffix"
        val origSumVariant = "${sumVariant}_orig_a4"
        val accessible = isSourceAccessible(doc)

        if (accessible && renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = cacheManager.getMemorySummary(doc, sumVariant)
                ?: cacheManager.loadDiskSummary(doc, sumVariant)
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        if (accessible && renderFormat == OfficeRenderFormat.ORIGINAL) {
            (cacheManager.getMemoryParsed(doc, origVariantTag)
                ?: cacheManager.loadDiskParsed(doc, origVariantTag))?.let { cached ->
                if (cached is ParsedDocument.Slides) {
                    cached.summary?.let { cacheManager.putMemorySummary(doc, it, origSumVariant) }
                    return cached
                }
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

        val detectedLayout = deck.layout
        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            val origLayout = OfficeLayoutResolver.withA4Default(detectedLayout, documentOrientation)
            val origSummary = buildSlidesSummary(effectiveSlides, detectedLayout, origLayout)
            cacheManager.saveDiskSummary(doc, origSummary, origSumVariant)
            return ParsedDocument.Slides(slides = effectiveSlides, layout = origLayout, summary = origSummary).also {
                cacheManager.saveDiskParsed(doc, it, origVariantTag)
            }
        }

        val effectiveLayout = OfficeLayoutResolver.applyOrientation(detectedLayout, documentOrientation)
        val summary = buildSlidesSummary(effectiveSlides, detectedLayout, effectiveLayout)
        cacheManager.saveDiskSummary(doc, summary, sumVariant)
        val slidesParsed = ParsedDocument.Slides(slides = effectiveSlides, layout = effectiveLayout, summary = summary)
        cacheManager.putMemoryParsed(doc, slidesParsed, variantTag)

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertSlidesToPdf(
            slides = effectiveSlides,
            outputFile = cachedPdf,
            layout = effectiveLayout
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            cacheManager.saveDiskParsed(doc, slidesParsed, variantTag)
            slidesParsed
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
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        val orientSuffix = orientationSuffix(documentOrientation)
        val fontSizeKey = settings.xlsxFontSizeSp.toInt().coerceIn(11, 22)
        val gridFlag = if (settings.showExcelGridlines) 1 else 0
        val variantTag = "xlsx_v4_fs${fontSizeKey}_gl${gridFlag}$orientSuffix"
        val origVariantTag = "${variantTag}_orig_a4"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumVariant = "xlsx_v3$orientSuffix"
        val origSumVariant = "${sumVariant}_orig_a4"
        val accessible = isSourceAccessible(doc)

        if (accessible && renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = cacheManager.getMemorySummary(doc, sumVariant)
                ?: cacheManager.loadDiskSummary(doc, sumVariant)
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        if (accessible && renderFormat == OfficeRenderFormat.ORIGINAL) {
            (cacheManager.getMemoryParsed(doc, origVariantTag)
                ?: cacheManager.loadDiskParsed(doc, origVariantTag))?.let { cached ->
                if (cached is ParsedDocument.Spreadsheet) {
                    cached.summary?.let { cacheManager.putMemorySummary(doc, it, origSumVariant) }
                    return cached
                }
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

        val detectedLayout = normalizedWorkbook.layout
        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            val origLayout = OfficeLayoutResolver.withA4Default(detectedLayout, documentOrientation)
            val origWorkbook = normalizedWorkbook.copy(
                sheets = normalizedWorkbook.sheets.map { sheet ->
                    sheet.copy(layout = OfficeLayoutResolver.withA4Default(sheet.layout, documentOrientation))
                },
                layout = origLayout
            )
            val origSummary = buildExcelSummary(origWorkbook, detectedLayout, origLayout)
            cacheManager.saveDiskSummary(doc, origSummary, origSumVariant)
            return ParsedDocument.Spreadsheet(
                workbook = origWorkbook,
                layout = origLayout,
                summary = origSummary
            ).also {
                cacheManager.saveDiskParsed(doc, it, origVariantTag)
            }
        }

        val effectiveWorkbook = if (documentOrientation == DocumentViewOrientation.AUTO) {
            normalizedWorkbook
        } else {
            normalizedWorkbook.copy(
                sheets = normalizedWorkbook.sheets.map { sheet ->
                    sheet.copy(
                        layout = OfficeLayoutResolver.applyOrientation(sheet.layout, documentOrientation)
                    )
                },
                layout = OfficeLayoutResolver.applyOrientation(detectedLayout, documentOrientation)
            )
        }
        val effectiveLayout = effectiveWorkbook.layout
        val summary = buildExcelSummary(effectiveWorkbook, detectedLayout, effectiveLayout)
        cacheManager.saveDiskSummary(doc, summary, sumVariant)
        val excelParsed = ParsedDocument.Spreadsheet(
            workbook = effectiveWorkbook,
            layout = effectiveLayout,
            summary = summary
        )
        cacheManager.putMemoryParsed(doc, excelParsed, variantTag)

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertExcelToPdf(
            workbook = effectiveWorkbook,
            showGridlines = settings.showExcelGridlines,
            baseFontSizeSp = settings.xlsxFontSizeSp,
            outputFile = cachedPdf
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, summary)
        } else {
            cacheManager.saveDiskParsed(doc, excelParsed, variantTag)
            excelParsed
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

    private fun parseTextDocument(
        doc: DocumentItem,
        settings: AppSettings,
        renderFormat: OfficeRenderFormat,
        documentOrientation: DocumentViewOrientation
    ): ParsedDocument {
        val orientSuffix = orientationSuffix(documentOrientation)
        val fontSizeKey = settings.textFontSizeSp.toInt().coerceIn(11, 24)
        val lnFlag = if (settings.showLineNumbers) 1 else 0
        val wwFlag = if (settings.textWordWrap) 1 else 0
        val msFlag = if (settings.textMonospace) 1 else 0
        val variantTag = "txt_v1_fs${fontSizeKey}_ln${lnFlag}_ww${wwFlag}_ms${msFlag}$orientSuffix"
        val cachedPdf = cacheManager.getConvertedPdfFile(doc, variantTag)
        val sessionKey = "${DocumentCacheManager.docKey(doc)}_$variantTag"
        val sumVariant = "txt_v1$orientSuffix"
        val textCacheKey = "text$orientSuffix"
        val accessible = isSourceAccessible(doc)

        if (accessible && renderFormat == OfficeRenderFormat.PDF && cachedPdf.isFile && cachedPdf.length() > 0L) {
            val cachedSummary = cacheManager.getMemorySummary(doc, sumVariant)
                ?: cacheManager.loadDiskSummary(doc, sumVariant)
            if (cachedSummary != null) {
                cachedPdf.setLastModified(System.currentTimeMillis())
                return openConvertedPdf(cachedPdf, sessionKey, cachedSummary)
            }
        }

        if (accessible && renderFormat == OfficeRenderFormat.ORIGINAL) {
            (cacheManager.getMemoryParsed(doc, textCacheKey)
                ?: cacheManager.loadDiskParsed(doc, textCacheKey))?.let { cached ->
                if (cached is ParsedDocument.PlainText) {
                    cached.summary?.let { cacheManager.putMemorySummary(doc, it, sumVariant) }
                    return cached
                }
            }
        }

        val cachedOriginal = if (accessible) {
            (cacheManager.getMemoryParsed(doc, textCacheKey)
                ?: cacheManager.loadDiskParsed(doc, textCacheKey)) as? ParsedDocument.PlainText
        } else null

        val plainParsed = if (cachedOriginal != null) {
            cachedOriginal
        } else {
            val text = readTextSmart(doc) ?: return ParsedDocument.Error("Cannot read text file.")
            val detectedLayout = OfficeLayoutResolver.detectTextLayout(text)
            val effectiveLayout = OfficeLayoutResolver.applyOrientation(detectedLayout, documentOrientation)
            val lines = text.lines()
            val pages = if (lines.isEmpty()) listOf(listOf("")) else lines.chunked(42)
            val summary = OfficeContentSummary(
                detectedPageSize = detectedLayout.detectedPaperName,
                detectedMargin = detectedLayout.detectedMarginName,
                effectivePageSize = effectiveLayout.detectedPaperName,
                effectiveMargin = effectiveLayout.detectedMarginName,
                isLandscape = effectiveLayout.isLandscape,
                widthPt = effectiveLayout.widthPt,
                heightPt = effectiveLayout.heightPt,
                textCount = lines.count { it.isNotBlank() },
                imageCount = 0,
                chartCount = 0,
                tableCount = 0,
                unitCount = pages.size.coerceAtLeast(1),
                unitLabel = "Pages"
            )
            cacheManager.saveDiskSummary(doc, summary, sumVariant)
            ParsedDocument.PlainText(
                fullText = text,
                pages = pages,
                layout = effectiveLayout,
                summary = summary
            ).also {
                if (renderFormat == OfficeRenderFormat.ORIGINAL) {
                    cacheManager.saveDiskParsed(doc, it, textCacheKey)
                } else {
                    cacheManager.putMemoryParsed(doc, it, textCacheKey)
                }
            }
        }

        if (renderFormat == OfficeRenderFormat.ORIGINAL) {
            return plainParsed
        }

        if (cachedPdf.isFile && cachedPdf.length() > 0L) {
            cachedPdf.setLastModified(System.currentTimeMillis())
            return openConvertedPdf(cachedPdf, sessionKey, plainParsed.summary)
        }

        val ok = OfficeAndXmlToPdfConverter.convertTextToPdf(
            text = plainParsed.fullText,
            baseFontSizeSp = settings.textFontSizeSp,
            showLineNumbers = settings.showLineNumbers,
            wordWrap = settings.textWordWrap,
            useMonospace = settings.textMonospace,
            outputFile = cachedPdf,
            layout = plainParsed.layout
        )
        return if (ok) {
            openConvertedPdf(cachedPdf, sessionKey, plainParsed.summary)
        } else {
            cacheManager.saveDiskParsed(doc, plainParsed, textCacheKey)
            plainParsed
        }
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
