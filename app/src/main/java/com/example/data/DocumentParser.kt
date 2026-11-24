package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Xml
import com.example.model.DocType
import com.example.model.DocumentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

sealed interface ParsedDocument {
    data class Pdf(val session: PdfDocumentSession) : ParsedDocument
    data class Word(val pages: List<WordPage>) : ParsedDocument
    data class Slides(val slides: List<SlidePage>) : ParsedDocument
    data class CodeXml(val formattedXml: String, val pages: List<List<String>>) : ParsedDocument
    data class PlainText(val fullText: String, val pages: List<List<String>>) : ParsedDocument
    data class Error(val message: String) : ParsedDocument
}

data class WordBlock(
    val text: String,
    val isHeading: Boolean = false,
    val headingLevel: Int = 0,
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val isBullet: Boolean = false
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

    suspend fun parse(doc: DocumentItem): ParsedDocument = withContext(Dispatchers.IO) {
        if (doc.type != DocType.PDF) {
            cacheManager.getMemoryParsed(doc)?.let { return@withContext it }
            cacheManager.loadDiskParsed(doc)?.let { return@withContext it }
        }
        runCatching {
            val parsed = when (doc.type) {
                DocType.PDF -> parsePdf(doc)
                DocType.DOC -> parseWord(doc)
                DocType.PPT -> parseSlides(doc)
                DocType.XML -> parseXml(doc)
                DocType.TEXT -> parsePlainText(doc)
            }
            if (parsed !is ParsedDocument.Pdf && parsed !is ParsedDocument.Error) {
                cacheManager.saveDiskParsed(doc, parsed)
            }
            parsed
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

    private fun parsePdf(doc: DocumentItem): ParsedDocument {
        val pfd = openParcelFileDescriptor(doc)
            ?: return ParsedDocument.Error("Cannot access PDF file offline.")
        return runCatching {
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount == 0) {
                renderer.close()
                pfd.close()
                ParsedDocument.Error("This PDF document has no pages.")
            } else {
                ParsedDocument.Pdf(
                    PdfDocumentSession(
                        docKey = DocumentCacheManager.docKey(doc),
                        pfd = pfd,
                        renderer = renderer,
                        cacheManager = cacheManager
                    )
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

    private fun forEachZipEntry(bytes: ByteArray, onEntry: (name: String, readBytes: () -> ByteArray) -> Boolean) {
        ZipInputStream(BufferedInputStream(ByteArrayInputStream(bytes))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                val shouldContinue = onEntry(entry.name) { zis.readBytes() }
                if (!shouldContinue) break
                entry = zis.nextEntry
            }
        }
    }

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

    private fun parseWord(doc: DocumentItem): ParsedDocument {
        val bytes = readBytesBounded(doc)
            ?: return ParsedDocument.Error("Cannot read document file.")

        if (bytes.isEmpty()) {
            return ParsedDocument.Word(listOf(WordPage(1, listOf(WordBlock("(Empty document)")))))
        }

        val blocks = if (isZipArchive(bytes) || doc.extension == "docx") {
            parseDocxBytes(bytes)
        } else {
            parseLegacyDocBytes(bytes)
        }

        val finalBlocks = blocks.ifEmpty {
            listOf(WordBlock("No readable text content found in ${doc.name}."))
        }

        val pages = finalBlocks.chunked(20).mapIndexed { index, chunk ->
            WordPage(pageNumber = index + 1, blocks = chunk)
        }
        return ParsedDocument.Word(pages)
    }

    private fun parseDocxBytes(bytes: ByteArray): List<WordBlock> {
        var documentXml: ByteArray? = null
        forEachZipEntry(bytes) { name, read ->
            if (name.equals("word/document.xml", ignoreCase = true)) {
                documentXml = read()
                false
            } else true
        }
        val xmlBytes = documentXml ?: return emptyList()
        val blocks = mutableListOf<WordBlock>()

        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inParagraph = false
        var inRunProps = false
        var inParaProps = false
        val paraText = StringBuilder()
        var isBold = false
        var isItalic = false
        var isBullet = false
        var headingLevel = 0
        var anyBoldInPara = false
        var anyItalicInPara = false

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            val tagName = parser.name ?: ""
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (tagName) {
                        "w:p" -> {
                            inParagraph = true
                            paraText.clear()
                            isBold = false
                            isItalic = false
                            isBullet = false
                            headingLevel = 0
                            anyBoldInPara = false
                            anyItalicInPara = false
                        }
                        "w:pPr" -> if (inParagraph) inParaProps = true
                        "w:rPr" -> if (inParagraph) inRunProps = true
                        "w:pStyle" -> if (inParaProps) {
                            val styleVal = parser.getAttributeValue(null, "w:val") ?: ""
                            when {
                                styleVal.contains("Heading1", true) || styleVal.equals("Title", true) -> headingLevel = 1
                                styleVal.contains("Heading2", true) || styleVal.equals("Subtitle", true) -> headingLevel = 2
                                styleVal.contains("Heading", true) -> headingLevel = 3
                                styleVal.contains("List", true) -> isBullet = true
                            }
                        }
                        "w:numPr" -> if (inParaProps) isBullet = true
                        "w:b" -> if (inRunProps) {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "0" && v != "false") isBold = true
                        }
                        "w:i" -> if (inRunProps) {
                            val v = parser.getAttributeValue(null, "w:val")
                            if (v != "0" && v != "false") isItalic = true
                        }
                        "w:t", "w:delText" -> if (inParagraph) {
                            val txt = parser.nextText()
                            if (txt.isNotEmpty()) {
                                paraText.append(txt)
                                if (isBold) anyBoldInPara = true
                                if (isItalic) anyItalicInPara = true
                            }
                        }
                        "w:tab" -> if (inParagraph) paraText.append("    ")
                        "w:br" -> if (inParagraph) paraText.append("\n")
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (tagName) {
                        "w:pPr" -> inParaProps = false
                        "w:rPr" -> {
                            inRunProps = false
                        }
                        "w:r" -> {
                            isBold = false
                            isItalic = false
                        }
                        "w:p" -> {
                            if (inParagraph) {
                                val text = paraText.toString().trimEnd()
                                if (text.isNotBlank()) {
                                    blocks.add(
                                        WordBlock(
                                            text = text,
                                            isHeading = headingLevel > 0,
                                            headingLevel = headingLevel,
                                            isBold = anyBoldInPara,
                                            isItalic = anyItalicInPara,
                                            isBullet = isBullet
                                        )
                                    )
                                }
                            }
                            inParagraph = false
                        }
                    }
                }
            }
            event = parser.next()
        }
        return blocks
    }

    private fun parseLegacyDocBytes(bytes: ByteArray): List<WordBlock> {
        // Extract clean Unicode UTF-16LE and ASCII readable text segments from OLE2 .doc binary
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

    private fun parseSlides(doc: DocumentItem): ParsedDocument {
        val bytes = readBytesBounded(doc)
            ?: return ParsedDocument.Error("Cannot read presentation file.")

        if (bytes.isEmpty()) {
            return ParsedDocument.Slides(listOf(SlidePage(1, doc.name, listOf("Empty presentation"))))
        }

        val slides = if (isZipArchive(bytes) || doc.extension == "pptx") {
            parsePptxBytes(bytes)
        } else {
            parseLegacyPptBytes(bytes)
        }

        val finalSlides = slides.ifEmpty {
            listOf(SlidePage(1, doc.name, listOf("No readable slide content found.")))
        }
        return ParsedDocument.Slides(finalSlides)
    }

    private fun parsePptxBytes(bytes: ByteArray): List<SlidePage> {
        val slideXmlMap = sortedMapOf<Int, ByteArray>()
        val notesXmlMap = sortedMapOf<Int, ByteArray>()
        val slideRegex = Regex("""ppt/slides/slide(\d+)\.xml""", RegexOption.IGNORE_CASE)
        val notesRegex = Regex("""ppt/notesSlides/notesSlide(\d+)\.xml""", RegexOption.IGNORE_CASE)

        forEachZipEntry(bytes) { name, read ->
            slideRegex.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
                slideXmlMap[num] = read()
            }
            notesRegex.matchEntire(name)?.groupValues?.get(1)?.toIntOrNull()?.let { num ->
                notesXmlMap[num] = read()
            }
            true
        }

        return slideXmlMap.entries.map { (slideNum, xmlBytes) ->
            val paragraphs = extractDrawingMlParagraphs(xmlBytes)
            val title = paragraphs.firstOrNull { it.isNotBlank() } ?: "Slide $slideNum"
            val body = if (paragraphs.size > 1) paragraphs.drop(1) else emptyList()
            val notes = notesXmlMap[slideNum]?.let { extractDrawingMlParagraphs(it).joinToString("\n") } ?: ""
            SlidePage(
                slideNumber = slideNum,
                title = title,
                bodyLines = body,
                notes = notes
            )
        }
    }

    private fun extractDrawingMlParagraphs(xmlBytes: ByteArray): List<String> {
        val result = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(ByteArrayInputStream(xmlBytes), "UTF-8")

        var inParagraph = false
        val currentLine = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "a:p" -> {
                            inParagraph = true
                            currentLine.clear()
                        }
                        "a:t" -> if (inParagraph) {
                            currentLine.append(parser.nextText())
                        }
                        "a:br" -> if (inParagraph) {
                            currentLine.append("\n")
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "a:p" && inParagraph) {
                        val line = currentLine.toString().trim()
                        if (line.isNotEmpty()) result.add(line)
                        inParagraph = false
                    }
                }
            }
            event = parser.next()
        }
        return result
    }

    private fun parseLegacyPptBytes(bytes: ByteArray): List<SlidePage> {
        // Parse PPT binary TextCharsAtom (0x0FA0) and TextBytesAtom (0x0FA8) records
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

        // Group consecutive text chunks into slides (~4 blocks per slide when container splits aren't explicit)
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
        // First scan for UTF-16LE runs (common in Microsoft Office OLE2 binaries)
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

        // Fallback ASCII scan if UTF-16LE found very little
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

    private fun parseXml(doc: DocumentItem): ParsedDocument {
        val raw = readTextSmart(doc)
            ?: return ParsedDocument.Error("Cannot read XML file.")
        val formatted = prettyFormatXmlIfNeeded(raw)
        val lines = formatted.lines()
        val pages = if (lines.isEmpty()) listOf(listOf("")) else lines.chunked(42)
        return ParsedDocument.CodeXml(formattedXml = formatted, pages = pages)
    }

    private fun prettyFormatXmlIfNeeded(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        // Preserve already well-indented multi-line XML
        if (trimmed.lines().size > 3) return trimmed

        // Pretty-print single-line/minified XML cleanly
        val sb = StringBuilder()
        var indent = 0
        var i = 0
        while (i < trimmed.length) {
            if (trimmed[i] == '<') {
                val close = trimmed.indexOf('>', i)
                if (close == -1) {
                    sb.append(trimmed.substring(i))
                    break
                }
                val tag = trimmed.substring(i, close + 1)
                val isClosing = tag.startsWith("</")
                val isSelfClosing = tag.endsWith("/>") || tag.startsWith("<?") || tag.startsWith("<!")
                if (isClosing && indent > 0) indent--
                if (sb.isNotEmpty() && !sb.endsWith("\n")) sb.append('\n')
                sb.append("  ".repeat(indent)).append(tag)
                if (!isClosing && !isSelfClosing) indent++
                i = close + 1
            } else {
                val nextTag = trimmed.indexOf('<', i)
                val text = if (nextTag == -1) trimmed.substring(i) else trimmed.substring(i, nextTag)
                val cleaned = text.trim()
                if (cleaned.isNotEmpty()) {
                    sb.append(cleaned)
                }
                i = if (nextTag == -1) trimmed.length else nextTag
            }
        }
        return sb.toString()
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
            // Read up to 4 MB safely for responsive offline rendering
            val maxBytes = 4 * 1024 * 1024
            val buffer = ByteArray(8192)
            val out = java.io.ByteArrayOutputStream()
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
