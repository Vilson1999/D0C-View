package com.example.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.print.pdf.PrintedPdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.core.content.FileProvider
import com.example.model.DocType
import com.example.model.DocumentItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object DocumentActionsHelper {

    fun getShareableUri(context: Context, doc: DocumentItem): Uri {
        return if (doc.isFilePath) {
            val file = File(doc.path)
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }.getOrElse { doc.uri }
        } else {
            doc.uri
        }
    }

    fun shareDocuments(context: Context, docs: List<DocumentItem>) {
        if (docs.isEmpty()) return
        val uris = ArrayList(docs.map { getShareableUri(context, it) })
        val intent = if (uris.size == 1) {
            val doc = docs.first()
            Intent(Intent.ACTION_SEND).apply {
                type = doc.type.mimeTypes.firstOrNull() ?: "*/*"
                putExtra(Intent.EXTRA_STREAM, uris.first())
                putExtra(Intent.EXTRA_SUBJECT, doc.name)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        val chooser = Intent.createChooser(intent, "Share Document${if (docs.size > 1) "s" else ""}")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun printDocuments(activity: Activity, docs: List<DocumentItem>) {
        if (docs.isEmpty()) return
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return

        if (docs.size == 1 && docs.first().type == DocType.PDF) {
            val doc = docs.first()
            printManager.print(
                doc.name,
                PdfStreamPrintAdapter(activity, doc),
                PrintAttributes.Builder().build()
            )
            return
        }

        // Parse text/structured documents and print via PrintedPdfDocument
        val parser = DocumentParser(activity.applicationContext)
        CoroutineScope(Dispatchers.Main).launch {
            val combinedText = buildString {
                docs.forEachIndexed { idx, doc ->
                    if (idx > 0) append("\n\n==============================\n\n")
                    append(doc.name).append('\n')
                    append("------------------------------\n\n")
                    when (val parsed = parser.parse(doc)) {
                        is ParsedDocument.PlainText -> append(parsed.fullText)
                        is ParsedDocument.Word -> parsed.pages.forEach { p ->
                            p.blocks.forEach { b ->
                                if (b.isBullet) append("• ")
                                append(b.text).append("\n\n")
                            }
                        }
                        is ParsedDocument.Slides -> parsed.slides.forEach { s ->
                            append("Slide ${s.slideNumber}: ${s.title}\n")
                            s.bodyLines.forEach { append("  • $it\n") }
                            if (s.notes.isNotBlank()) append("  [Notes] ${s.notes}\n")
                            append('\n')
                        }
                        is ParsedDocument.Spreadsheet -> parsed.workbook.sheets.forEach { sheet ->
                            append("[Sheet: ${sheet.name}]\n")
                            sheet.rows.forEach { row ->
                                append(row.cells.joinToString(" | ") { it.text }).append('\n')
                            }
                            append('\n')
                        }
                        is ParsedDocument.Pdf -> append("[PDF Document: ${doc.name} (${parsed.session.pageCount} pages)]").also {
                            parsed.session.close()
                        }
                        is ParsedDocument.Error -> append(parsed.message)
                    }
                }
            }

            if (activity.isFinishing || activity.isDestroyed) return@launch
            val safePrintText = if (combinedText.length > 250_000) {
                combinedText.substring(0, 250_000) + "\n\n[Print preview truncated at 250,000 characters]"
            } else {
                combinedText
            }
            val jobName = if (docs.size == 1) docs.first().name else "${docs.size} Documents"
            printManager.print(
                jobName,
                TextPrintAdapter(activity, jobName, safePrintText),
                PrintAttributes.Builder().build()
            )
        }
    }

    private class PdfStreamPrintAdapter(
        private val context: Context,
        private val doc: DocumentItem
    ) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(doc.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            runCatching {
                val input = if (doc.isFilePath) {
                    FileInputStream(File(doc.path))
                } else {
                    context.contentResolver.openInputStream(doc.uri)
                }
                input?.use { src ->
                    FileOutputStream(destination.fileDescriptor).use { dst ->
                        src.copyTo(dst)
                    }
                } ?: error("Cannot read PDF stream")
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure {
                callback.onWriteFailed(it.localizedMessage)
            }
        }
    }

    private class TextPrintAdapter(
        private val context: Context,
        private val jobName: String,
        private val text: String
    ) : PrintDocumentAdapter() {
        private var pdfDocument: PrintedPdfDocument? = null
        private var pageChunks: List<StaticLayout> = emptyList()

        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: Bundle?
        ) {
            pdfDocument = PrintedPdfDocument(context, newAttributes)
            if (cancellationSignal?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }

            val pageWidth = ((newAttributes.mediaSize?.widthMils ?: 8500) * 72 / 1000).coerceAtLeast(300)
            val pageHeight = ((newAttributes.mediaSize?.heightMils ?: 11000) * 72 / 1000).coerceAtLeast(400)
            val contentWidth = (pageWidth - 96).coerceAtLeast(200)
            val contentHeight = (pageHeight - 96).coerceAtLeast(250)

            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = 11f
            }

            val fullLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(2f, 1.15f)
                .build()

            val layouts = mutableListOf<StaticLayout>()
            var lineStart = 0
            while (lineStart < fullLayout.lineCount) {
                var lineEnd = lineStart
                val startTop = fullLayout.getLineTop(lineStart)
                while (lineEnd < fullLayout.lineCount &&
                    fullLayout.getLineBottom(lineEnd) - startTop <= contentHeight
                ) {
                    lineEnd++
                }
                if (lineEnd == lineStart) lineEnd++
                val charStart = fullLayout.getLineStart(lineStart)
                val charEnd = fullLayout.getLineEnd(lineEnd - 1)
                val slice = text.substring(charStart, charEnd)
                layouts.add(
                    StaticLayout.Builder.obtain(slice, 0, slice.length, paint, contentWidth)
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                        .setLineSpacing(2f, 1.15f)
                        .build()
                )
                lineStart = lineEnd
            }
            pageChunks = layouts.ifEmpty { listOf(fullLayout) }

            val info = PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageChunks.size)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(
            pages: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback
        ) {
            val doc = pdfDocument ?: return
            runCatching {
                pageChunks.forEachIndexed { index, layout ->
                    if (cancellationSignal?.isCanceled == true) {
                        callback.onWriteCancelled()
                        return
                    }
                    val page: PdfDocument.Page = doc.startPage(index)
                    val canvas = page.canvas
                    canvas.save()
                    canvas.translate(48f, 48f)
                    layout.draw(canvas)
                    canvas.restore()
                    doc.finishPage(page)
                }
                FileOutputStream(destination.fileDescriptor).use { out ->
                    doc.writeTo(out)
                }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure {
                callback.onWriteFailed(it.localizedMessage)
            }.also {
                doc.close()
                pdfDocument = null
            }
        }
    }
}
