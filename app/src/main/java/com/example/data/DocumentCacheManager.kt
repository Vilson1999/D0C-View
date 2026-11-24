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
    private val pdfPagesDir: File get() = File(cacheRoot, PDF_PAGES_DIR)
    private val parsedDocsDir: File get() = File(cacheRoot, PARSED_DOCS_DIR)

    fun loadDocumentIndex(): List<DocumentItem> {
        return loadIndexFromFile(indexFile)
    }

    fun saveDocumentIndex(documents: List<DocumentItem>) {
        saveIndexToFile(indexFile, documents)
    }

    fun getMemoryParsed(doc: DocumentItem): ParsedDocument? {
        return memoryParsedCache.get(docKey(doc))
    }

    fun putMemoryParsed(doc: DocumentItem, parsed: ParsedDocument) {
        if (parsed !is ParsedDocument.Pdf && parsed !is ParsedDocument.Error) {
            memoryParsedCache.put(docKey(doc), parsed)
        }
    }

    fun loadDiskParsed(doc: DocumentItem): ParsedDocument? {
        val key = docKey(doc)
        val file = File(parsedDocsDir, "${key}.dat")
        if (!file.exists() || !file.isFile) return null
        return runCatching {
            DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
                if (input.readByte().toInt() != CACHE_SCHEMA_VERSION) return null
                val kind = input.readByte().toInt()
                val parsed: ParsedDocument = when (kind) {
                    KIND_WORD -> {
                        val pageCount = input.readInt()
                        val pages = ArrayList<WordPage>(pageCount)
                        repeat(pageCount) {
                            val pageNum = input.readInt()
                            val blockCount = input.readInt()
                            val blocks = ArrayList<WordBlock>(blockCount)
                            repeat(blockCount) {
                                blocks.add(
                                    WordBlock(
                                        text = input.readUTFCompat(),
                                        isHeading = input.readBoolean(),
                                        headingLevel = input.readInt(),
                                        isBold = input.readBoolean(),
                                        isItalic = input.readBoolean(),
                                        isBullet = input.readBoolean()
                                    )
                                )
                            }
                            pages.add(WordPage(pageNum, blocks))
                        }
                        ParsedDocument.Word(pages)
                    }
                    KIND_SLIDES -> {
                        val slideCount = input.readInt()
                        val slides = ArrayList<SlidePage>(slideCount)
                        repeat(slideCount) {
                            val slideNum = input.readInt()
                            val title = input.readUTFCompat()
                            val lineCount = input.readInt()
                            val lines = ArrayList<String>(lineCount)
                            repeat(lineCount) { lines.add(input.readUTFCompat()) }
                            val notes = input.readUTFCompat()
                            slides.add(SlidePage(slideNum, title, lines, notes))
                        }
                        ParsedDocument.Slides(slides)
                    }
                    KIND_XML -> {
                        val formatted = input.readUTFCompat()
                        val pages = readStringPages(input)
                        ParsedDocument.CodeXml(formatted, pages)
                    }
                    KIND_TEXT -> {
                        val fullText = input.readUTFCompat()
                        val pages = readStringPages(input)
                        ParsedDocument.PlainText(fullText, pages)
                    }
                    else -> return null
                }
                file.setLastModified(System.currentTimeMillis())
                memoryParsedCache.put(key, parsed)
                parsed
            }
        }.getOrElse {
            runCatching { file.delete() }
            null
        }
    }

    fun saveDiskParsed(doc: DocumentItem, parsed: ParsedDocument) {
        if (parsed is ParsedDocument.Pdf || parsed is ParsedDocument.Error) return
        val key = docKey(doc)
        memoryParsedCache.put(key, parsed)
        if (!parsedDocsDir.exists()) parsedDocsDir.mkdirs()
        val target = File(parsedDocsDir, "${key}.dat")
        val tmp = File(parsedDocsDir, "${key}.dat.tmp")
        runCatching {
            DataOutputStream(BufferedOutputStream(FileOutputStream(tmp))).use { out ->
                out.writeByte(CACHE_SCHEMA_VERSION)
                when (parsed) {
                    is ParsedDocument.Word -> {
                        out.writeByte(KIND_WORD)
                        out.writeInt(parsed.pages.size)
                        for (page in parsed.pages) {
                            out.writeInt(page.pageNumber)
                            out.writeInt(page.blocks.size)
                            for (block in page.blocks) {
                                out.writeUTFCompat(block.text)
                                out.writeBoolean(block.isHeading)
                                out.writeInt(block.headingLevel)
                                out.writeBoolean(block.isBold)
                                out.writeBoolean(block.isItalic)
                                out.writeBoolean(block.isBullet)
                            }
                        }
                    }
                    is ParsedDocument.Slides -> {
                        out.writeByte(KIND_SLIDES)
                        out.writeInt(parsed.slides.size)
                        for (slide in parsed.slides) {
                            out.writeInt(slide.slideNumber)
                            out.writeUTFCompat(slide.title)
                            out.writeInt(slide.bodyLines.size)
                            for (line in slide.bodyLines) {
                                out.writeUTFCompat(line)
                            }
                            out.writeUTFCompat(slide.notes)
                        }
                    }
                    is ParsedDocument.CodeXml -> {
                        out.writeByte(KIND_XML)
                        out.writeUTFCompat(parsed.formattedXml)
                        writeStringPages(out, parsed.pages)
                    }
                    is ParsedDocument.PlainText -> {
                        out.writeByte(KIND_TEXT)
                        out.writeUTFCompat(parsed.fullText)
                        writeStringPages(out, parsed.pages)
                    }
                    is ParsedDocument.Pdf,
                    is ParsedDocument.Error -> Unit
                }
            }
            if (tmp.length() <= MAX_SINGLE_PARSED_CACHE_BYTES) {
                if (target.exists()) target.delete()
                tmp.renameTo(target)
            } else {
                tmp.delete()
            }
        }.onFailure {
            runCatching { tmp.delete() }
        }
    }

    fun getCachedPdfPageBitmap(docKey: String, pageIndex: Int, widthPx: Int): Bitmap? {
        val memKey = "${docKey}_${pageIndex}_${widthPx}"
        memoryBitmapCache.get(memKey)?.let { bmp ->
            if (!bmp.isRecycled) return bmp
            memoryBitmapCache.remove(memKey)
        }
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

    fun evictForDocuments(docs: List<DocumentItem>) {
        val keys = docs.mapTo(HashSet()) { docKey(it) }
        if (keys.isEmpty()) return
        for (key in keys) {
            memoryParsedCache.remove(key)
        }
        pdfPagesDir.listFiles()?.forEach { file ->
            if (extractDocKeyPrefix(file.name) in keys) {
                file.delete()
            }
        }
        parsedDocsDir.listFiles()?.forEach { file ->
            if (extractDocKeyPrefix(file.name) in keys) {
                file.delete()
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
        memoryBitmapCache.evictAll()
        return cacheRoot.listFiles()?.sumOf { file ->
            val size = dirSize(file)
            runCatching { file.deleteRecursively() }
            size
        } ?: 0L
    }

    fun getTotalCacheSizeBytes(): Long = dirSize(cacheRoot)

    private fun pdfPageFile(docKey: String, pageIndex: Int, widthPx: Int): File {
        return File(pdfPagesDir, "${docKey}_p${pageIndex}_w${widthPx}.webp")
    }

    companion object {
        const val INDEX_FILE_NAME = "doc_index_v1.tsv"
        const val PDF_PAGES_DIR = "pdf_pages"
        const val PARSED_DOCS_DIR = "parsed_docs"
        private const val CACHE_SCHEMA_VERSION = 1
        private const val KIND_WORD = 1
        private const val KIND_SLIDES = 2
        private const val KIND_XML = 3
        private const val KIND_TEXT = 4
        private const val MAX_SINGLE_PARSED_CACHE_BYTES = 4L * 1024L * 1024L
        private const val AUTO_PRUNE_MAX_BYTES = 14L * 1024L * 1024L
        private const val STANDARD_MAX_BYTES = 36L * 1024L * 1024L
        private const val DEFAULT_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

        private val memoryParsedCache = object : LruCache<String, ParsedDocument>(6) {}
        private val memoryBitmapCache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
        }

        fun docKey(doc: DocumentItem): String =
            docKey(doc.id, doc.lastModified, doc.sizeBytes)

        fun docKey(id: String, lastModified: Long, sizeBytes: Long): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$id|$lastModified|$sizeBytes".toByteArray(Charsets.UTF_8))
            return digest.take(12).joinToString("") { "%02x".format(it) }
        }

        internal fun extractDocKeyPrefix(fileName: String): String {
            return fileName.substringBefore('_').substringBefore('.')
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

        internal fun pruneCacheDirectory(
            cacheRoot: File,
            validDocKeys: Set<String>?,
            maxCacheBytes: Long,
            maxAgeMs: Long,
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            if (!cacheRoot.exists()) return 0L
            val minValidTime = nowMs - maxAgeMs

            // 1. Delete stray root temp/unknown files while preserving index and managed cache dirs
            cacheRoot.listFiles()?.forEach { child ->
                when (child.name) {
                    INDEX_FILE_NAME, PDF_PAGES_DIR, PARSED_DOCS_DIR -> Unit
                    else -> runCatching { child.deleteRecursively() }
                }
            }

            // 2. Clean orphaned, temporary, or expired entries in managed cache folders
            val managedFiles = mutableListOf<File>()
            for (dirName in arrayOf(PDF_PAGES_DIR, PARSED_DOCS_DIR)) {
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
            var totalBytes = indexBytes + managedFiles.sumOf { it.length() }
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
