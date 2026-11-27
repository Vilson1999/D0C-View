package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import com.example.model.DocType
import com.example.model.DocumentItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class DocumentCacheManager(private val context: Context) {

    private val cacheRoot: File get() = context.cacheDir
    private val indexFile: File get() = File(cacheRoot, INDEX_FILE_NAME)
    private val recentsFile: File get() = File(cacheRoot, RECENTS_FILE_NAME)
    private val pdfPagesDir: File get() = File(cacheRoot, PDF_PAGES_DIR)
    private val parsedDocsDir: File get() = File(cacheRoot, PARSED_DOCS_DIR)
    private val convertedPdfsDir: File get() = File(cacheRoot, CONVERTED_PDFS_DIR)
    private val summariesDir: File get() = File(cacheRoot, SUMMARIES_DIR)

    fun loadDocumentIndex(): List<DocumentItem> = loadIndexFromFile(indexFile)

    fun saveDocumentIndex(documents: List<DocumentItem>) {
        saveIndexToFile(indexFile, documents)
    }

    fun recordRecentDocument(doc: DocumentItem) {
        synchronized(recentsLock) {
            recordRecentIdToFile(recentsFile, doc.id)
        }
    }

    fun loadRecentDocIds(limit: Int = 8): List<String> {
        synchronized(recentsLock) {
            return loadRecentIdsFromFile(recentsFile, limit)
        }
    }

    fun getMemoryParsed(doc: DocumentItem, variantTag: String = DEFAULT_VARIANT): ParsedDocument? {
        return memoryParsedCache.get(cacheEntryKey(doc, variantTag))
    }

    fun putMemoryParsed(doc: DocumentItem, parsed: ParsedDocument, variantTag: String = DEFAULT_VARIANT) {
        if (parsed !is ParsedDocument.Pdf && parsed !is ParsedDocument.Error) {
            memoryParsedCache.put(cacheEntryKey(doc, variantTag), parsed)
        }
    }

    fun loadDiskParsed(doc: DocumentItem, variantTag: String = DEFAULT_VARIANT): ParsedDocument? {
        val memKey = cacheEntryKey(doc, variantTag)
        memoryParsedCache.get(memKey)?.let { return it }
        val file = File(parsedDocsDir, "${memKey}.dat")
        val loaded = loadParsedFromFile(file) ?: return null
        memoryParsedCache.put(memKey, loaded)
        return loaded
    }

    fun saveDiskParsed(doc: DocumentItem, parsed: ParsedDocument, variantTag: String = DEFAULT_VARIANT) {
        if (parsed is ParsedDocument.Pdf || parsed is ParsedDocument.Error) return
        val memKey = cacheEntryKey(doc, variantTag)
        memoryParsedCache.put(memKey, parsed)
        if (!parsedDocsDir.exists()) parsedDocsDir.mkdirs()
        saveParsedToFile(File(parsedDocsDir, "${memKey}.dat"), parsed)
    }

    fun getMemorySummary(doc: DocumentItem, variantTag: String): OfficeContentSummary? {
        return memorySummaryCache.get(cacheEntryKey(doc, variantTag))
    }

    fun putMemorySummary(doc: DocumentItem, summary: OfficeContentSummary, variantTag: String) {
        memorySummaryCache.put(cacheEntryKey(doc, variantTag), summary)
    }

    fun loadDiskSummary(doc: DocumentItem, variantTag: String): OfficeContentSummary? {
        val memKey = cacheEntryKey(doc, variantTag)
        memorySummaryCache.get(memKey)?.let { return it }
        val file = File(summariesDir, "${memKey}.sum")
        val loaded = loadSummaryFromFile(file) ?: return null
        memorySummaryCache.put(memKey, loaded)
        return loaded
    }

    fun saveDiskSummary(doc: DocumentItem, summary: OfficeContentSummary, variantTag: String) {
        val memKey = cacheEntryKey(doc, variantTag)
        memorySummaryCache.put(memKey, summary)
        if (!summariesDir.exists()) summariesDir.mkdirs()
        saveSummaryToFile(File(summariesDir, "${memKey}.sum"), summary)
    }

    fun getMemoryPdfPageBitmap(docKey: String, pageIndex: Int, widthPx: Int): Bitmap? {
        val memKey = "${docKey}_${pageIndex}_${widthPx}"
        val bmp = memoryBitmapCache.get(memKey) ?: return null
        if (!bmp.isRecycled) return bmp
        memoryBitmapCache.remove(memKey)
        return null
    }

    fun getCachedPdfPageBitmap(docKey: String, pageIndex: Int, widthPx: Int): Bitmap? {
        getMemoryPdfPageBitmap(docKey, pageIndex, widthPx)?.let { return it }
        val memKey = "${docKey}_${pageIndex}_${widthPx}"
        val file = pdfPageFile(docKey, pageIndex, widthPx)
        if (!file.exists() || !file.isFile) return null
        val decoded = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
        if (decoded != null) {
            file.setLastModified(System.currentTimeMillis())
            memoryBitmapCache.put(memKey, decoded)
        } else {
            runCatching { file.delete() }
        }
        return decoded
    }

    fun putCachedPdfPageBitmap(docKey: String, pageIndex: Int, widthPx: Int, bitmap: Bitmap) {
        val memKey = "${docKey}_${pageIndex}_${widthPx}"
        memoryBitmapCache.put(memKey, bitmap)
        if (!pdfPagesDir.exists()) pdfPagesDir.mkdirs()
        val target = pdfPageFile(docKey, pageIndex, widthPx)
        val tmp = File(pdfPagesDir, "${target.name}.tmp")
        runCatching {
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            FileOutputStream(tmp).use { out ->
                bitmap.compress(format, 85, out)
            }
            if (target.exists()) target.delete()
            tmp.renameTo(target)
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    fun getConvertedPdfFile(doc: DocumentItem, variantTag: String): File {
        if (!convertedPdfsDir.exists()) convertedPdfsDir.mkdirs()
        return File(convertedPdfsDir, "${docKey(doc)}_${variantTag}.pdf")
    }

    fun evictForDocuments(docs: List<DocumentItem>) {
        val keys = docs.mapTo(HashSet()) { docKey(it) }
        if (keys.isEmpty()) return

        memoryParsedCache.snapshot().keys.forEach { cacheKey ->
            if (extractDocKeyPrefix(cacheKey) in keys) memoryParsedCache.remove(cacheKey)
        }
        memorySummaryCache.snapshot().keys.forEach { cacheKey ->
            if (extractDocKeyPrefix(cacheKey) in keys) memorySummaryCache.remove(cacheKey)
        }
        memoryBitmapCache.snapshot().keys.forEach { cacheKey ->
            if (extractDocKeyPrefix(cacheKey) in keys) memoryBitmapCache.remove(cacheKey)
        }

        for (dir in arrayOf(pdfPagesDir, parsedDocsDir, convertedPdfsDir, summariesDir)) {
            dir.listFiles()?.forEach { file ->
                if (extractDocKeyPrefix(file.name) in keys) {
                    file.delete()
                }
            }
        }
    }

    suspend fun pruneUnnecessaryCache(
        validDocs: List<DocumentItem>?,
        aggressive: Boolean
    ): Long = withContext(Dispatchers.IO) {
        val validKeys = validDocs?.mapTo(HashSet()) { docKey(it) }
        val maxBytes = if (aggressive) AUTO_PRUNE_MAX_BYTES else STANDARD_MAX_BYTES
        pruneCacheDirectory(
            cacheRoot = cacheRoot,
            validDocKeys = validKeys,
            maxCacheBytes = maxBytes,
            maxAgeMs = DEFAULT_MAX_AGE_MS
        )
    }

    fun clearAllCache(): Long {
        memoryParsedCache.evictAll()
        memorySummaryCache.evictAll()
        memoryBitmapCache.evictAll()
        return cacheRoot.listFiles()?.sumOf { file ->
            val size = dirSize(file)
            runCatching { file.deleteRecursively() }
            size
        } ?: 0L
    }

    fun getTotalCacheSizeBytes(): Long = dirSize(cacheRoot)

    private fun cacheEntryKey(doc: DocumentItem, variantTag: String): String {
        val safeVariant = variantTag.replace(Regex("[^A-Za-z0-9._-]"), "_").ifEmpty { DEFAULT_VARIANT }
        return "${docKey(doc)}_$safeVariant"
    }

    private fun pdfPageFile(docKey: String, pageIndex: Int, widthPx: Int): File {
        return File(pdfPagesDir, "${docKey}_p${pageIndex}_w${widthPx}.webp")
    }

    companion object {
        const val INDEX_FILE_NAME = "doc_index_v1.tsv"
        const val RECENTS_FILE_NAME = "recent_docs_v1.tsv"
        const val PDF_PAGES_DIR = "pdf_pages"
        const val PARSED_DOCS_DIR = "parsed_docs"
        const val CONVERTED_PDFS_DIR = "converted_pdfs"
        const val SUMMARIES_DIR = "doc_summaries"
        private const val DEFAULT_VARIANT = "default"
        private const val CACHE_SCHEMA_VERSION = 3
        private const val SUMMARY_SCHEMA_VERSION = 1
        private const val KIND_WORD = 1
        private const val KIND_SLIDES = 2
        private const val KIND_SPREADSHEET = 3
        private const val KIND_TEXT = 4
        private const val MAX_SINGLE_PARSED_CACHE_BYTES = 6L * 1024L * 1024L
        private const val MAX_RECENT_DOCS = 12
        private const val AUTO_PRUNE_MAX_BYTES = 24L * 1024L * 1024L
        private const val STANDARD_MAX_BYTES = 48L * 1024L * 1024L
        private const val DEFAULT_MAX_AGE_MS = 10L * 24L * 60L * 60L * 1000L

        private val recentsLock = Any()
        private val memoryParsedCache = object : LruCache<String, ParsedDocument>(10) {}
        private val memorySummaryCache = object : LruCache<String, OfficeContentSummary>(32) {}
        private val memoryBitmapCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }

        fun docKey(doc: DocumentItem): String = docKey(doc.id, doc.lastModified, doc.sizeBytes)

        fun docKey(id: String, lastModified: Long, sizeBytes: Long): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$id|$lastModified|$sizeBytes".toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }

        internal fun extractDocKeyPrefix(fileName: String): String {
            return fileName.substringBefore('_').substringBefore('.')
        }

        internal fun recordRecentIdToFile(file: File, docId: String, maxEntries: Int = MAX_RECENT_DOCS) {
            if (docId.isBlank()) return
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val current = loadRecentIdsFromFile(file, maxEntries * 2).filterNot { it == docId }
            val updated = (listOf(docId) + current).take(maxEntries)
            val tmp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                    for (id in updated) {
                        writer.append(escapeTsv(id)).append('\n')
                    }
                }
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }.onFailure {
                runCatching { tmp.delete() }
            }
        }

        internal fun loadRecentIdsFromFile(file: File, limit: Int = MAX_RECENT_DOCS): List<String> {
            if (!file.exists() || !file.isFile) return emptyList()
            return runCatching {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.filter { it.isNotBlank() }
                        .map { unescapeTsv(it) }
                        .distinct()
                        .take(limit)
                        .toList()
                }
            }.getOrDefault(emptyList())
        }

        internal fun loadIndexFromFile(file: File): List<DocumentItem> {
            if (!file.exists() || !file.isFile) return emptyList()
            return runCatching {
                file.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.mapNotNull { line ->
                        if (line.isBlank()) return@mapNotNull null
                        val parts = line.split('\t')
                        if (parts.size != 11) return@mapNotNull null
                        val type = runCatching { DocType.valueOf(parts[4]) }.getOrNull() ?: return@mapNotNull null
                        DocumentItem(
                            id = unescapeTsv(parts[0]),
                            name = unescapeTsv(parts[1]),
                            path = unescapeTsv(parts[2]),
                            uriString = unescapeTsv(parts[3]),
                            type = type,
                            extension = unescapeTsv(parts[5]),
                            sizeBytes = parts[6].toLongOrNull() ?: 0L,
                            lastModified = parts[7].toLongOrNull() ?: 0L,
                            parentFolderName = unescapeTsv(parts[8]),
                            isFromDotFolder = parts[9] == "1",
                            isFilePath = parts[10] == "1"
                        )
                    }.toList()
                }
            }.getOrDefault(emptyList())
        }

        internal fun saveIndexToFile(file: File, documents: List<DocumentItem>) {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                tmp.bufferedWriter(Charsets.UTF_8).use { writer ->
                    for (doc in documents) {
                        writer.append(escapeTsv(doc.id)).append('\t')
                        writer.append(escapeTsv(doc.name)).append('\t')
                        writer.append(escapeTsv(doc.path)).append('\t')
                        writer.append(escapeTsv(doc.uriString)).append('\t')
                        writer.append(doc.type.name).append('\t')
                        writer.append(escapeTsv(doc.extension)).append('\t')
                        writer.append(doc.sizeBytes.toString()).append('\t')
                        writer.append(doc.lastModified.toString()).append('\t')
                        writer.append(escapeTsv(doc.parentFolderName)).append('\t')
                        writer.append(if (doc.isFromDotFolder) '1' else '0').append('\t')
                        writer.append(if (doc.isFilePath) '1' else '0').append('\n')
                    }
                }
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }.onFailure {
                runCatching { tmp.delete() }
            }
        }

        internal fun saveSummaryToFile(file: File, summary: OfficeContentSummary) {
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            runCatching {
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                    out.writeByte(SUMMARY_SCHEMA_VERSION)
                    writeSummaryBody(out, summary)
                }
                if (file.exists()) file.delete()
                tmp.renameTo(file)
            }.onFailure {
                runCatching { tmp.delete() }
            }
        }

        internal fun loadSummaryFromFile(file: File): OfficeContentSummary? {
            if (!file.exists() || !file.isFile) return null
            return runCatching {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                    if (input.readByte().toInt() != SUMMARY_SCHEMA_VERSION) return null
                    readSummaryBody(input).also {
                        file.setLastModified(System.currentTimeMillis())
                    }
                }
            }.getOrElse {
                runCatching { file.delete() }
                null
            }
        }

        internal fun saveParsedToFile(
            file: File,
            parsed: ParsedDocument,
            maxSingleFileBytes: Long = MAX_SINGLE_PARSED_CACHE_BYTES
        ): Boolean {
            if (parsed is ParsedDocument.Pdf || parsed is ParsedDocument.Error) return false
            file.parentFile?.let { if (!it.exists()) it.mkdirs() }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            return runCatching {
                DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                    out.writeByte(CACHE_SCHEMA_VERSION)
                    when (parsed) {
                        is ParsedDocument.Word -> {
                            out.writeByte(KIND_WORD)
                            writeLayout(out, parsed.layout)
                            writeNullableSummary(out, parsed.summary)
                            out.writeInt(parsed.pages.size)
                            for (page in parsed.pages) {
                                out.writeInt(page.pageNumber)
                                writeWordBlocks(out, page.blocks)
                            }
                        }
                        is ParsedDocument.Slides -> {
                            out.writeByte(KIND_SLIDES)
                            writeLayout(out, parsed.layout)
                            writeNullableSummary(out, parsed.summary)
                            out.writeInt(parsed.slides.size)
                            for (slide in parsed.slides) {
                                out.writeInt(slide.slideNumber)
                                out.writeUTFCompat(slide.title)
                                writeStringList(out, slide.bodyLines)
                                out.writeUTFCompat(slide.notes)
                                writeWordBlocks(out, slide.blocks)
                                writeNullableInt(out, slide.backgroundRgb)
                            }
                        }
                        is ParsedDocument.Spreadsheet -> {
                            out.writeByte(KIND_SPREADSHEET)
                            writeLayout(out, parsed.layout)
                            writeNullableSummary(out, parsed.summary)
                            writeWorkbook(out, parsed.workbook)
                        }
                        is ParsedDocument.PlainText -> {
                            out.writeByte(KIND_TEXT)
                            writeLayout(out, parsed.layout)
                            writeNullableSummary(out, parsed.summary)
                            out.writeUTFCompat(parsed.fullText)
                            writeStringPages(out, parsed.pages)
                        }
                        is ParsedDocument.Pdf,
                        is ParsedDocument.Error -> Unit
                    }
                }
                if (tmp.length() <= maxSingleFileBytes) {
                    if (file.exists()) file.delete()
                    tmp.renameTo(file)
                } else {
                    tmp.delete()
                    false
                }
            }.getOrElse {
                runCatching { tmp.delete() }
                false
            }
        }

        internal fun loadParsedFromFile(file: File): ParsedDocument? {
            if (!file.exists() || !file.isFile) return null
            return runCatching {
                DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                    if (input.readByte().toInt() != CACHE_SCHEMA_VERSION) return null
                    val parsed: ParsedDocument = when (input.readByte().toInt()) {
                        KIND_WORD -> {
                            val layout = readLayout(input)
                            val summary = readNullableSummary(input)
                            val pageCount = input.readInt()
                            val pages = ArrayList<WordPage>(pageCount)
                            repeat(pageCount) {
                                val pageNum = input.readInt()
                                val blocks = readWordBlocks(input)
                                pages.add(WordPage(pageNum, blocks))
                            }
                            ParsedDocument.Word(pages = pages, layout = layout, summary = summary)
                        }
                        KIND_SLIDES -> {
                            val layout = readLayout(input)
                            val summary = readNullableSummary(input)
                            val slideCount = input.readInt()
                            val slides = ArrayList<SlidePage>(slideCount)
                            repeat(slideCount) {
                                val slideNum = input.readInt()
                                val title = input.readUTFCompat()
                                val bodyLines = readStringList(input)
                                val notes = input.readUTFCompat()
                                val blocks = readWordBlocks(input)
                                val bgRgb = readNullableInt(input)
                                slides.add(
                                    SlidePage(
                                        slideNumber = slideNum,
                                        title = title,
                                        bodyLines = bodyLines,
                                        notes = notes,
                                        blocks = blocks,
                                        backgroundRgb = bgRgb
                                    )
                                )
                            }
                            ParsedDocument.Slides(slides = slides, layout = layout, summary = summary)
                        }
                        KIND_SPREADSHEET -> {
                            val layout = readLayout(input)
                            val summary = readNullableSummary(input)
                            val workbook = readWorkbook(input)
                            ParsedDocument.Spreadsheet(workbook = workbook, layout = layout, summary = summary)
                        }
                        KIND_TEXT -> {
                            val layout = readLayout(input)
                            val summary = readNullableSummary(input)
                            val fullText = input.readUTFCompat()
                            val pages = readStringPages(input)
                            ParsedDocument.PlainText(
                                fullText = fullText,
                                pages = pages,
                                layout = layout,
                                summary = summary
                            )
                        }
                        else -> return null
                    }
                    file.setLastModified(System.currentTimeMillis())
                    parsed
                }
            }.getOrElse {
                runCatching { file.delete() }
                null
            }
        }

        internal fun pruneCacheDirectory(
            cacheRoot: File,
            validDocKeys: Set<String>?,
            maxCacheBytes: Long,
            maxAgeMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            if (!cacheRoot.exists()) return 0L
            val minValidTime = nowMs - maxAgeMs

            // 1. Delete stray root temp/unknown files while preserving index, recents, and managed dirs
            cacheRoot.listFiles()?.forEach { child ->
                when (child.name) {
                    INDEX_FILE_NAME,
                    RECENTS_FILE_NAME,
                    PDF_PAGES_DIR,
                    PARSED_DOCS_DIR,
                    CONVERTED_PDFS_DIR,
                    SUMMARIES_DIR -> Unit
                    else -> runCatching { child.deleteRecursively() }
                }
            }

            // 2. Clean orphaned, temporary, or expired entries in managed cache folders
            val managedFiles = mutableListOf<File>()
            for (dirName in arrayOf(PDF_PAGES_DIR, PARSED_DOCS_DIR, CONVERTED_PDFS_DIR, SUMMARIES_DIR)) {
                val dir = File(cacheRoot, dirName)
                if (!dir.exists() || !dir.isDirectory) continue
                dir.listFiles()?.forEach { entry ->
                    if (!entry.isFile || entry.name.endsWith(".tmp")) {
                        runCatching { entry.deleteRecursively() }
                        return@forEach
                    }
                    val keyPrefix = extractDocKeyPrefix(entry.name)
                    val isOrphaned = validDocKeys != null && keyPrefix !in validDocKeys
                    val isExpired = entry.lastModified() in 1 until minValidTime
                    if (isOrphaned || isExpired) {
                        runCatching { entry.delete() }
                    } else {
                        managedFiles.add(entry)
                    }
                }
            }

            // 3. Enforce bounded LRU cache budget (delete oldest-used entries first)
            val indexBytes = File(cacheRoot, INDEX_FILE_NAME).takeIf { it.isFile }?.length() ?: 0L
            val recentsBytes = File(cacheRoot, RECENTS_FILE_NAME).takeIf { it.isFile }?.length() ?: 0L
            var totalBytes = indexBytes + recentsBytes + managedFiles.sumOf { it.length() }
            if (totalBytes > maxCacheBytes) {
                val targetBytes = (maxCacheBytes * 85L) / 100L
                val lruSorted = managedFiles.sortedBy { it.lastModified() }
                for (candidate in lruSorted) {
                    if (totalBytes <= targetBytes) break
                    val size = candidate.length()
                    if (runCatching { candidate.delete() }.getOrDefault(false)) {
                        totalBytes -= size
                    }
                }
            }

            return dirSize(cacheRoot)
        }

        private fun dirSize(file: File): Long {
            if (!file.exists()) return 0L
            if (file.isFile) return file.length()
            return file.listFiles()?.sumOf { dirSize(it) } ?: 0L
        }

        private fun writeNullableInt(out: DataOutputStream, value: Int?) {
            out.writeBoolean(value != null)
            if (value != null) out.writeInt(value)
        }

        private fun readNullableInt(input: DataInputStream): Int? {
            return if (input.readBoolean()) input.readInt() else null
        }

        private fun writeNullableFloat(out: DataOutputStream, value: Float?) {
            out.writeBoolean(value != null)
            if (value != null) out.writeFloat(value)
        }

        private fun readNullableFloat(input: DataInputStream): Float? {
            return if (input.readBoolean()) input.readFloat() else null
        }

        private fun writeLayout(out: DataOutputStream, layout: OfficePageLayout) {
            out.writeFloat(layout.widthPt)
            out.writeFloat(layout.heightPt)
            out.writeFloat(layout.marginLeftPt)
            out.writeFloat(layout.marginRightPt)
            out.writeFloat(layout.marginTopPt)
            out.writeFloat(layout.marginBottomPt)
            out.writeBoolean(layout.isLandscape)
            out.writeUTFCompat(layout.detectedPaperName)
            out.writeUTFCompat(layout.detectedMarginName)
        }

        private fun readLayout(input: DataInputStream): OfficePageLayout = OfficePageLayout(
            widthPt = input.readFloat(),
            heightPt = input.readFloat(),
            marginLeftPt = input.readFloat(),
            marginRightPt = input.readFloat(),
            marginTopPt = input.readFloat(),
            marginBottomPt = input.readFloat(),
            isLandscape = input.readBoolean(),
            detectedPaperName = input.readUTFCompat(),
            detectedMarginName = input.readUTFCompat()
        )

        private fun writeNullableSummary(out: DataOutputStream, summary: OfficeContentSummary?) {
            out.writeBoolean(summary != null)
            if (summary != null) writeSummaryBody(out, summary)
        }

        private fun readNullableSummary(input: DataInputStream): OfficeContentSummary? {
            return if (input.readBoolean()) readSummaryBody(input) else null
        }

        private fun writeSummaryBody(out: DataOutputStream, s: OfficeContentSummary) {
            out.writeUTFCompat(s.detectedPageSize)
            out.writeUTFCompat(s.detectedMargin)
            out.writeUTFCompat(s.effectivePageSize)
            out.writeUTFCompat(s.effectiveMargin)
            out.writeBoolean(s.isLandscape)
            out.writeFloat(s.widthPt)
            out.writeFloat(s.heightPt)
            out.writeInt(s.textCount)
            out.writeInt(s.imageCount)
            out.writeInt(s.chartCount)
            out.writeInt(s.tableCount)
            out.writeInt(s.unitCount)
            out.writeUTFCompat(s.unitLabel)
        }

        private fun readSummaryBody(input: DataInputStream): OfficeContentSummary = OfficeContentSummary(
            detectedPageSize = input.readUTFCompat(),
            detectedMargin = input.readUTFCompat(),
            effectivePageSize = input.readUTFCompat(),
            effectiveMargin = input.readUTFCompat(),
            isLandscape = input.readBoolean(),
            widthPt = input.readFloat(),
            heightPt = input.readFloat(),
            textCount = input.readInt(),
            imageCount = input.readInt(),
            chartCount = input.readInt(),
            tableCount = input.readInt(),
            unitCount = input.readInt(),
            unitLabel = input.readUTFCompat()
        )

        private fun writeWordBlocks(out: DataOutputStream, blocks: List<WordBlock>) {
            out.writeInt(blocks.size)
            for (block in blocks) writeWordBlock(out, block)
        }

        private fun readWordBlocks(input: DataInputStream): List<WordBlock> {
            val count = input.readInt()
            val list = ArrayList<WordBlock>(count)
            repeat(count) { list.add(readWordBlock(input)) }
            return list
        }

        private fun writeWordBlock(out: DataOutputStream, b: WordBlock) {
            out.writeUTFCompat(b.text)
            out.writeBoolean(b.isHeading)
            out.writeInt(b.headingLevel)
            out.writeBoolean(b.isBold)
            out.writeBoolean(b.isItalic)
            out.writeBoolean(b.isBullet)
            out.writeUTFCompat(b.bulletPrefix)
            out.writeInt(b.runs.size)
            for (r in b.runs) {
                out.writeUTFCompat(r.text)
                out.writeBoolean(r.isBold)
                out.writeBoolean(r.isItalic)
                out.writeBoolean(r.isUnderline)
                out.writeBoolean(r.isStrike)
                writeNullableFloat(out, r.fontSizePt)
                writeNullableInt(out, r.colorRgb)
            }
            out.writeByte(b.alignment.ordinal)
            writeNullableFloat(out, b.fontSizePt)
            out.writeFloat(b.spaceBeforePt)
            out.writeFloat(b.spaceAfterPt)
            out.writeFloat(b.leftIndentPt)
            out.writeFloat(b.firstLineIndentPt)
            writeNullableFloat(out, b.lineSpacingMult)
            out.writeBoolean(b.isPageBreak)
            out.writeBoolean(b.hasBottomBorder)

            out.writeBoolean(b.image != null)
            b.image?.let { img ->
                out.writeInt(img.bytes.size)
                out.write(img.bytes)
                writeNullableFloat(out, img.widthPt)
                writeNullableFloat(out, img.heightPt)
                out.writeByte(img.alignment.ordinal)
            }

            out.writeBoolean(b.chart != null)
            b.chart?.let { ch ->
                out.writeUTFCompat(ch.title)
                out.writeByte(ch.type.ordinal)
                writeStringList(out, ch.categories)
                out.writeInt(ch.series.size)
                for (s in ch.series) {
                    out.writeUTFCompat(s.name)
                    out.writeInt(s.values.size)
                    for (v in s.values) out.writeDouble(v)
                    writeNullableInt(out, s.colorRgb)
                }
            }

            out.writeInt(b.tableRows.size)
            for (row in b.tableRows) {
                out.writeBoolean(row.isHeader)
                out.writeInt(row.cells.size)
                for (cell in row.cells) {
                    writeWordBlocks(out, cell.paragraphs)
                    writeNullableInt(out, cell.backgroundRgb)
                    out.writeInt(cell.colSpan)
                }
            }
            out.writeBoolean(b.tableHasBorders)
        }

        private fun readWordBlock(input: DataInputStream): WordBlock {
            val text = input.readUTFCompat()
            val isHeading = input.readBoolean()
            val headingLevel = input.readInt()
            val isBold = input.readBoolean()
            val isItalic = input.readBoolean()
            val isBullet = input.readBoolean()
            val bulletPrefix = input.readUTFCompat()
            val runCount = input.readInt()
            val runs = ArrayList<WordRun>(runCount)
            repeat(runCount) {
                runs.add(
                    WordRun(
                        text = input.readUTFCompat(),
                        isBold = input.readBoolean(),
                        isItalic = input.readBoolean(),
                        isUnderline = input.readBoolean(),
                        isStrike = input.readBoolean(),
                        fontSizePt = readNullableFloat(input),
                        colorRgb = readNullableInt(input)
                    )
                )
            }
            val alignOrd = input.readByte().toInt().coerceIn(0, DocAlignment.entries.lastIndex)
            val fontSizePt = readNullableFloat(input)
            val spaceBeforePt = input.readFloat()
            val spaceAfterPt = input.readFloat()
            val leftIndentPt = input.readFloat()
            val firstLineIndentPt = input.readFloat()
            val lineSpacingMult = readNullableFloat(input)
            val isPageBreak = input.readBoolean()
            val hasBottomBorder = input.readBoolean()

            val image = if (input.readBoolean()) {
                val len = input.readInt().coerceIn(0, 16 * 1024 * 1024)
                val bytes = ByteArray(len)
                input.readFully(bytes)
                val wPt = readNullableFloat(input)
                val hPt = readNullableFloat(input)
                val imgAlignOrd = input.readByte().toInt().coerceIn(0, DocAlignment.entries.lastIndex)
                WordImage(bytes, wPt, hPt, DocAlignment.entries[imgAlignOrd])
            } else null

            val chart = if (input.readBoolean()) {
                val title = input.readUTFCompat()
                val typeOrd = input.readByte().toInt().coerceIn(0, OfficeChartType.entries.lastIndex)
                val cats = readStringList(input)
                val serCount = input.readInt()
                val seriesList = ArrayList<ChartSeries>(serCount)
                repeat(serCount) {
                    val sName = input.readUTFCompat()
                    val vCount = input.readInt()
                    val values = ArrayList<Double>(vCount)
                    repeat(vCount) { values.add(input.readDouble()) }
                    val cRgb = readNullableInt(input)
                    seriesList.add(ChartSeries(sName, values, cRgb))
                }
                OfficeChart(title, OfficeChartType.entries[typeOrd], cats, seriesList)
            } else null

            val rowCount = input.readInt()
            val tableRows = ArrayList<WordTableRow>(rowCount)
            repeat(rowCount) {
                val isHeader = input.readBoolean()
                val cellCount = input.readInt()
                val cells = ArrayList<WordTableCell>(cellCount)
                repeat(cellCount) {
                    val paragraphs = readWordBlocks(input)
                    val bgRgb = readNullableInt(input)
                    val colSpan = input.readInt()
                    cells.add(WordTableCell(paragraphs, bgRgb, colSpan))
                }
                tableRows.add(WordTableRow(cells, isHeader))
            }
            val tableHasBorders = input.readBoolean()

            return WordBlock(
                text = text,
                isHeading = isHeading,
                headingLevel = headingLevel,
                isBold = isBold,
                isItalic = isItalic,
                isBullet = isBullet,
                bulletPrefix = bulletPrefix,
                runs = runs,
                alignment = DocAlignment.entries[alignOrd],
                fontSizePt = fontSizePt,
                spaceBeforePt = spaceBeforePt,
                spaceAfterPt = spaceAfterPt,
                leftIndentPt = leftIndentPt,
                firstLineIndentPt = firstLineIndentPt,
                lineSpacingMult = lineSpacingMult,
                isPageBreak = isPageBreak,
                hasBottomBorder = hasBottomBorder,
                image = image,
                chart = chart,
                tableRows = tableRows,
                tableHasBorders = tableHasBorders
            )
        }

        private fun writeWorkbook(out: DataOutputStream, wb: ExcelWorkbook) {
            writeLayout(out, wb.layout)
            out.writeInt(wb.sheets.size)
            for (sheet in wb.sheets) {
                out.writeUTFCompat(sheet.name)
                out.writeInt(sheet.columnWidthsChars.size)
                for ((colIdx, widthVal) in sheet.columnWidthsChars) {
                    out.writeInt(colIdx)
                    out.writeFloat(widthVal)
                }
                out.writeInt(sheet.maxColumns)
                out.writeInt(sheet.rows.size)
                for (row in sheet.rows) {
                    out.writeInt(row.rowNumber)
                    out.writeBoolean(row.isHeaderLikely)
                    out.writeInt(row.cells.size)
                    for (cell in row.cells) {
                        out.writeInt(cell.colIndex)
                        out.writeUTFCompat(cell.text)
                        out.writeBoolean(cell.isNumericOrDate)
                        out.writeBoolean(cell.isBold)
                        out.writeBoolean(cell.isItalic)
                        writeNullableInt(out, cell.textColorRgb)
                        writeNullableInt(out, cell.backgroundRgb)
                        out.writeByte(cell.alignment.ordinal)
                        out.writeInt(cell.colSpan)
                    }
                }
                writeWordBlocks(out, sheet.embeddedBlocks)
                writeLayout(out, sheet.layout)
            }
        }

        private fun readWorkbook(input: DataInputStream): ExcelWorkbook {
            val wbLayout = readLayout(input)
            val sheetCount = input.readInt()
            val sheets = ArrayList<ExcelSheet>(sheetCount)
            repeat(sheetCount) {
                val name = input.readUTFCompat()
                val widthCount = input.readInt()
                val widths = LinkedHashMap<Int, Float>(widthCount)
                repeat(widthCount) { widths[input.readInt()] = input.readFloat() }
                val maxColumns = input.readInt()
                val rowCount = input.readInt()
                val rows = ArrayList<ExcelRow>(rowCount)
                repeat(rowCount) {
                    val rowNum = input.readInt()
                    val headerLikely = input.readBoolean()
                    val cellCount = input.readInt()
                    val cells = ArrayList<ExcelCell>(cellCount)
                    repeat(cellCount) {
                        val colIdx = input.readInt()
                        val text = input.readUTFCompat()
                        val isNum = input.readBoolean()
                        val isBold = input.readBoolean()
                        val isItalic = input.readBoolean()
                        val textRgb = readNullableInt(input)
                        val bgRgb = readNullableInt(input)
                        val alignOrd = input.readByte().toInt().coerceIn(0, DocAlignment.entries.lastIndex)
                        val colSpan = input.readInt()
                        cells.add(
                            ExcelCell(
                                colIndex = colIdx,
                                text = text,
                                isNumericOrDate = isNum,
                                isBold = isBold,
                                isItalic = isItalic,
                                textColorRgb = textRgb,
                                backgroundRgb = bgRgb,
                                alignment = DocAlignment.entries[alignOrd],
                                colSpan = colSpan
                            )
                        )
                    }
                    rows.add(ExcelRow(rowNum, cells, headerLikely))
                }
                val embeddedBlocks = readWordBlocks(input)
                val sheetLayout = readLayout(input)
                sheets.add(ExcelSheet(name, widths, maxColumns, rows, embeddedBlocks, sheetLayout))
            }
            return ExcelWorkbook(sheets = sheets, layout = wbLayout)
        }

        private fun writeStringList(out: DataOutputStream, list: List<String>) {
            out.writeInt(list.size)
            for (s in list) out.writeUTFCompat(s)
        }

        private fun readStringList(input: DataInputStream): List<String> {
            val count = input.readInt()
            val list = ArrayList<String>(count)
            repeat(count) { list.add(input.readUTFCompat()) }
            return list
        }

        private fun escapeTsv(value: String): String = buildString(value.length) {
            for (ch in value) {
                when (ch) {
                    '\\' -> append("\\\\")
                    '\t' -> append("\\t")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(ch)
                }
            }
        }

        private fun unescapeTsv(value: String): String = buildString(value.length) {
            var i = 0
            while (i < value.length) {
                val ch = value[i]
                if (ch == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        '\\' -> { append('\\'); i += 2; continue }
                        't' -> { append('\t'); i += 2; continue }
                        'n' -> { append('\n'); i += 2; continue }
                        'r' -> { append('\r'); i += 2; continue }
                    }
                }
                append(ch)
                i++
            }
        }

        private fun DataOutputStream.writeUTFCompat(text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            writeInt(bytes.size)
            write(bytes)
        }

        private fun DataInputStream.readUTFCompat(): String {
            val len = readInt().coerceIn(0, 16 * 1024 * 1024)
            val bytes = ByteArray(len)
            readFully(bytes)
            return String(bytes, Charsets.UTF_8)
        }

        private fun writeStringPages(out: DataOutputStream, pages: List<List<String>>) {
            out.writeInt(pages.size)
            for (page in pages) {
                out.writeInt(page.size)
                for (line in page) {
                    out.writeUTFCompat(line)
                }
            }
        }

        private fun readStringPages(input: DataInputStream): List<List<String>> {
            val pageCount = input.readInt()
            val pages = ArrayList<List<String>>(pageCount)
            repeat(pageCount) {
                val lineCount = input.readInt()
                val lines = ArrayList<String>(lineCount)
                repeat(lineCount) { lines.add(input.readUTFCompat()) }
                pages.add(lines)
            }
            return pages
        }
    }
}
