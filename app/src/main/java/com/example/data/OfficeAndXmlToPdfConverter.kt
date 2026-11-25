package com.example.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import java.io.File
import java.io.FileOutputStream

/**
 * Offline renderer that converts parsed DOC/DOCX/RTF, PPT/PPTX, and XML documents
 * into real paginated PDF files using Android's built-in PdfDocument API.
 */
object OfficeAndXmlToPdfConverter {

    private const val A4_WIDTH = 595
    private const val A4_HEIGHT = 842
    private const val SLIDE_WIDTH = 842
    private const val SLIDE_HEIGHT = 474
    private const val MAX_PDF_PAGES = 250

    private val InkPrimary = Color.rgb(15, 23, 42)
    private val InkSecondary = Color.rgb(51, 65, 85)
    private val InkMuted = Color.rgb(100, 116, 139)
    private val LineNumInk = Color.rgb(148, 163, 184)
    private val DividerInk = Color.rgb(226, 232, 240)
    private val SlideAccent = Color.rgb(234, 88, 12)
    private val NotesBg = Color.rgb(248, 250, 252)
    private val NotesBorder = Color.rgb(203, 213, 225)

    // High-contrast XML colors tuned for crisp white PDF pages
    private val PdfXmlTag = Color.rgb(29, 78, 216)
    private val PdfXmlAttr = Color.rgb(124, 58, 237)
    private val PdfXmlValue = Color.rgb(21, 128, 61)
    private val PdfXmlComment = Color.rgb(100, 116, 139)

    fun convertWordToPdf(
        blocks: List<WordBlock>,
        baseFontSizeSp: Float,
        outputFile: File
    ): Boolean {
        val marginH = 44f
        val marginTop = 46f
        val marginBottom = 46f
        val contentWidth = (A4_WIDTH - marginH * 2f).toInt()
        val pageBottom = A4_HEIGHT - marginBottom

        val clampedBase = baseFontSizeSp.coerceIn(10f, 20f) * 0.86f
        val bodyTypeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val boldTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val italicTypeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val boldItalicTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DividerInk
            strokeWidth = 1f
        }

        return writePdfAtomically(outputFile) { pdf ->
            val state = PagedCanvasState(pdf, A4_WIDTH, A4_HEIGHT, marginTop)
            state.startPage()

            for ((index, block) in blocks.withIndex()) {
                if (state.reachedPageLimit) break

                val textSize = when {
                    block.isHeading && block.headingLevel == 1 -> clampedBase + 6.5f
                    block.isHeading && block.headingLevel == 2 -> clampedBase + 3.8f
                    block.isHeading -> clampedBase + 1.8f
                    else -> clampedBase
                }
                val typeface = when {
                    (block.isHeading || block.isBold) && block.isItalic -> boldItalicTypeface
                    block.isHeading || block.isBold -> boldTypeface
                    block.isItalic -> italicTypeface
                    else -> bodyTypeface
                }
                val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (block.isHeading) InkPrimary else InkSecondary
                    this.textSize = textSize
                    this.typeface = typeface
                }

                val topSpace = when {
                    index == 0 -> 0f
                    block.isHeading && block.headingLevel == 1 -> 12f
                    block.isHeading -> 8f
                    else -> 2f
                }
                if (state.cursorY > marginTop && state.cursorY + topSpace < pageBottom) {
                    state.cursorY += topSpace
                }

                val displayText = if (block.isBullet) "•  ${block.text}" else block.text
                drawFlowingText(
                    state = state,
                    text = displayText,
                    paint = paint,
                    x = marginH,
                    width = contentWidth,
                    marginTop = marginTop,
                    pageBottom = pageBottom,
                    lineSpacingMult = 1.38f
                )

                if (block.isHeading && block.headingLevel == 1 && !state.reachedPageLimit) {
                    if (state.cursorY + 6f <= pageBottom) {
                        state.cursorY += 4f
                        state.canvas.drawLine(marginH, state.cursorY, A4_WIDTH - marginH, state.cursorY, rulePaint)
                        state.cursorY += 6f
                    }
                } else {
                    state.cursorY += 6.5f
                }
            }

            state.finishCurrentPage()
        }
    }

    fun convertSlidesToPdf(
        slides: List<SlidePage>,
        outputFile: File
    ): Boolean {
        val marginH = 44f
        val marginTop = 34f
        val marginBottom = 30f
        val contentWidth = (SLIDE_WIDTH - marginH * 2f).toInt()
        val pageBottom = SLIDE_HEIGHT - marginBottom

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SlideAccent }
        val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DividerInk
            strokeWidth = 1.2f
        }
        val notesBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = NotesBg }
        val notesBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = NotesBorder
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }

        val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = SlideAccent
            textSize = 10.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkPrimary
            textSize = 21f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkSecondary
            textSize = 14f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val notesLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkMuted
            textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val notesBodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkMuted
            textSize = 11f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }

        return writePdfAtomically(outputFile) { pdf ->
            val state = PagedCanvasState(pdf, SLIDE_WIDTH, SLIDE_HEIGHT, marginTop)
            val totalSlides = slides.size.coerceAtLeast(1)

            for (slide in slides) {
                if (state.reachedPageLimit) break
                state.startPage(onPageStarted = { canvas ->
                    canvas.drawRect(0f, 0f, SLIDE_WIDTH.toFloat(), 7f, accentPaint)
                })

                val badgeLayout = buildStaticLayout(
                    "SLIDE ${slide.slideNumber} OF $totalSlides",
                    badgePaint,
                    contentWidth,
                    1.1f
                )
                state.canvas.save()
                state.canvas.translate(marginH, state.cursorY)
                badgeLayout.draw(state.canvas)
                state.canvas.restore()
                state.cursorY += badgeLayout.height + 8f

                drawFlowingText(
                    state = state,
                    text = slide.title,
                    paint = titlePaint,
                    x = marginH,
                    width = contentWidth,
                    marginTop = marginTop,
                    pageBottom = pageBottom,
                    lineSpacingMult = 1.25f,
                    onNewOverflowPage = { canvas ->
                        canvas.drawRect(0f, 0f, SLIDE_WIDTH.toFloat(), 7f, accentPaint)
                    }
                )

                state.cursorY += 10f
                if (state.cursorY + 8f < pageBottom) {
                    state.canvas.drawLine(marginH, state.cursorY, SLIDE_WIDTH - marginH, state.cursorY, dividerPaint)
                    state.cursorY += 14f
                }

                for (line in slide.bodyLines) {
                    if (state.reachedPageLimit) break
                    drawFlowingText(
                        state = state,
                        text = "•  $line",
                        paint = bodyPaint,
                        x = marginH,
                        width = contentWidth,
                        marginTop = marginTop,
                        pageBottom = pageBottom,
                        lineSpacingMult = 1.35f,
                        onNewOverflowPage = { canvas ->
                            canvas.drawRect(0f, 0f, SLIDE_WIDTH.toFloat(), 7f, accentPaint)
                        }
                    )
                    state.cursorY += 7f
                }

                if (slide.notes.isNotBlank() && !state.reachedPageLimit) {
                    val notesInnerWidth = (contentWidth - 24).coerceAtLeast(100)
                    val notesLayout = buildStaticLayout(slide.notes, notesBodyPaint, notesInnerWidth, 1.3f)
                    val boxHeight = 28f + notesLayout.height
                    if (state.cursorY + boxHeight + 8f > pageBottom && state.cursorY > marginTop + 40f) {
                        state.finishCurrentPage()
                        if (!state.reachedPageLimit) {
                            state.startPage(onPageStarted = { canvas ->
                                canvas.drawRect(0f, 0f, SLIDE_WIDTH.toFloat(), 7f, accentPaint)
                            })
                        }
                    }
                    if (!state.reachedPageLimit) {
                        val boxTop = state.cursorY.coerceAtMost(pageBottom - boxHeight)
                        val clampedBottom = (boxTop + boxHeight).coerceAtMost(pageBottom)
                        if (clampedBottom - boxTop >= 32f) {
                            val rect = RectF(marginH, boxTop, SLIDE_WIDTH - marginH, clampedBottom)
                            state.canvas.drawRoundRect(rect, 10f, 10f, notesBgPaint)
                            state.canvas.drawRoundRect(rect, 10f, 10f, notesBorderPaint)

                            state.canvas.save()
                            state.canvas.translate(marginH + 12f, boxTop + 8f)
                            val labelLayout = buildStaticLayout("SPEAKER NOTES", notesLabelPaint, notesInnerWidth, 1.1f)
                            labelLayout.draw(state.canvas)
                            state.canvas.translate(0f, labelLayout.height + 4f)
                            state.canvas.clipRect(0f, 0f, notesInnerWidth.toFloat(), (clampedBottom - boxTop - 24f).coerceAtLeast(12f))
                            notesLayout.draw(state.canvas)
                            state.canvas.restore()
                        }
                    }
                }

                state.finishCurrentPage()
            }
        }
    }

    fun convertXmlToPdf(
        formattedXml: String,
        syntaxHighlight: Boolean,
        showLineNumbers: Boolean,
        baseFontSizeSp: Float,
        outputFile: File
    ): Boolean {
        val marginH = 30f
        val marginTop = 34f
        val marginBottom = 34f
        val pageBottom = A4_HEIGHT - marginBottom

        val codeFontSize = (baseFontSizeSp - 3.5f).coerceIn(8.5f, 13.5f)
        val lineNumColWidth = if (showLineNumbers) 36f else 0f
        val codeStartX = marginH + lineNumColWidth
        val codeWidth = (A4_WIDTH - codeStartX - marginH).toInt().coerceAtLeast(160)

        val codePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkPrimary
            textSize = codeFontSize
            typeface = Typeface.MONOSPACE
        }
        val lineNumPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LineNumInk
            textSize = (codeFontSize - 1f).coerceAtLeast(7.5f)
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.RIGHT
        }

        val lines = formattedXml.lines().ifEmpty { listOf("") }

        return writePdfAtomically(outputFile) { pdf ->
            val state = PagedCanvasState(pdf, A4_WIDTH, A4_HEIGHT, marginTop)
            state.startPage()

            for ((idx, line) in lines.withIndex()) {
                if (state.reachedPageLimit) break

                val styledLine: CharSequence = if (syntaxHighlight) {
                    highlightXmlForPdf(line)
                } else {
                    line
                }

                val layout = buildStaticLayout(styledLine, codePaint, codeWidth, 1.32f)
                val firstLineHeight = if (layout.lineCount > 0) {
                    (layout.getLineBottom(0) - layout.getLineTop(0)).toFloat()
                } else {
                    codeFontSize * 1.32f
                }

                if (state.cursorY + firstLineHeight > pageBottom) {
                    state.finishCurrentPage()
                    if (state.reachedPageLimit) break
                    state.startPage()
                }

                if (showLineNumbers) {
                    val baseline = state.cursorY + if (layout.lineCount > 0) layout.getLineBaseline(0).toFloat() else codeFontSize
                    state.canvas.drawText(
                        (idx + 1).toString(),
                        marginH + lineNumColWidth - 8f,
                        baseline,
                        lineNumPaint
                    )
                }

                drawStaticLayoutAcrossPages(
                    state = state,
                    source = styledLine,
                    layout = layout,
                    paint = codePaint,
                    x = codeStartX,
                    width = codeWidth,
                    marginTop = marginTop,
                    pageBottom = pageBottom,
                    lineSpacingMult = 1.32f
                )
                state.cursorY += 1.5f
            }

            if (state.reachedPageLimit) {
                // Already closed at page limit; no trailing draw needed
            } else {
                state.finishCurrentPage()
            }
        }
    }

    private fun drawFlowingText(
        state: PagedCanvasState,
        text: CharSequence,
        paint: TextPaint,
        x: Float,
        width: Int,
        marginTop: Float,
        pageBottom: Float,
        lineSpacingMult: Float,
        onNewOverflowPage: ((Canvas) -> Unit)? = null
    ) {
        val layout = buildStaticLayout(text, paint, width, lineSpacingMult)
        drawStaticLayoutAcrossPages(
            state = state,
            source = text,
            layout = layout,
            paint = paint,
            x = x,
            width = width,
            marginTop = marginTop,
            pageBottom = pageBottom,
            lineSpacingMult = lineSpacingMult,
            onNewOverflowPage = onNewOverflowPage
        )
    }

    private fun drawStaticLayoutAcrossPages(
        state: PagedCanvasState,
        source: CharSequence,
        layout: StaticLayout,
        paint: TextPaint,
        x: Float,
        width: Int,
        marginTop: Float,
        pageBottom: Float,
        lineSpacingMult: Float,
        onNewOverflowPage: ((Canvas) -> Unit)? = null
    ) {
        if (state.reachedPageLimit) return
        if (state.cursorY + layout.height <= pageBottom) {
            state.canvas.save()
            state.canvas.translate(x, state.cursorY)
            layout.draw(state.canvas)
            state.canvas.restore()
            state.cursorY += layout.height
            return
        }

        val totalLines = layout.lineCount
        var lineIdx = 0
        while (lineIdx < totalLines && !state.reachedPageLimit) {
            val firstLineH = (layout.getLineBottom(lineIdx) - layout.getLineTop(lineIdx)).toFloat()
            if (state.cursorY + firstLineH > pageBottom && state.cursorY > marginTop) {
                state.finishCurrentPage()
                if (state.reachedPageLimit) break
                state.startPage(onPageStarted = onNewOverflowPage)
            }

            val chunkStartLine = lineIdx
            val baseTop = layout.getLineTop(chunkStartLine)
            var chunkEndLine = chunkStartLine
            while (chunkEndLine + 1 < totalLines) {
                val nextHeight = (layout.getLineBottom(chunkEndLine + 1) - baseTop).toFloat()
                if (state.cursorY + nextHeight > pageBottom) break
                chunkEndLine++
            }

            val startOffset = layout.getLineStart(chunkStartLine)
            var endOffset = layout.getLineEnd(chunkEndLine)
            while (endOffset > startOffset && source[endOffset - 1] == '\n') {
                endOffset--
            }

            if (endOffset > startOffset) {
                val slice = source.subSequence(startOffset, endOffset)
                val sliceLayout = buildStaticLayout(slice, paint, width, lineSpacingMult)
                state.canvas.save()
                state.canvas.translate(x, state.cursorY)
                sliceLayout.draw(state.canvas)
                state.canvas.restore()
                state.cursorY += sliceLayout.height
            } else {
                state.cursorY += firstLineH
            }

            lineIdx = chunkEndLine + 1
            if (lineIdx < totalLines) {
                state.finishCurrentPage()
                if (!state.reachedPageLimit) {
                    state.startPage(onPageStarted = onNewOverflowPage)
                }
            }
        }
    }

    private fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int,
        lineSpacingMult: Float
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(64))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(false)
            .build()
    }

    private fun highlightXmlForPdf(line: String): CharSequence {
        val sb = SpannableStringBuilder()
        if (line.trimStart().startsWith("<!--")) {
            val start = 0
            sb.append(line)
            sb.setSpan(ForegroundColorSpan(PdfXmlComment), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.ITALIC), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return sb
        }

        var i = 0
        while (i < line.length) {
            if (line[i] == '<') {
                val closeIdx = line.indexOf('>', i)
                if (closeIdx == -1) {
                    val start = sb.length
                    sb.append(line.substring(i))
                    sb.setSpan(ForegroundColorSpan(PdfXmlTag), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    break
                }
                appendXmlTagSpan(sb, line.substring(i, closeIdx + 1))
                i = closeIdx + 1
            } else {
                val nextTag = line.indexOf('<', i)
                val end = if (nextTag == -1) line.length else nextTag
                sb.append(line.substring(i, end))
                i = end
            }
        }
        return sb
    }

    private fun appendXmlTagSpan(sb: SpannableStringBuilder, tagText: String) {
        val spaceIdx = tagText.indexOfFirst { it.isWhitespace() }
        if (spaceIdx == -1) {
            val start = sb.length
            sb.append(tagText)
            sb.setSpan(ForegroundColorSpan(PdfXmlTag), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            return
        }

        val tagStart = sb.length
        sb.append(tagText.substring(0, spaceIdx))
        sb.setSpan(ForegroundColorSpan(PdfXmlTag), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        sb.setSpan(StyleSpan(Typeface.BOLD), tagStart, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        var i = spaceIdx
        while (i < tagText.length) {
            val ch = tagText[i]
            when {
                ch == '"' || ch == '\'' -> {
                    val endQuote = tagText.indexOf(ch, i + 1)
                    val end = if (endQuote == -1) tagText.length else endQuote + 1
                    val start = sb.length
                    sb.append(tagText.substring(i, end))
                    sb.setSpan(ForegroundColorSpan(PdfXmlValue), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end
                }
                ch == '>' || (ch == '/' && i + 1 < tagText.length && tagText[i + 1] == '>') -> {
                    val start = sb.length
                    sb.append(tagText.substring(i))
                    sb.setSpan(ForegroundColorSpan(PdfXmlTag), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    break
                }
                ch.isLetter() || ch == '_' || ch == ':' || ch == '-' -> {
                    var end = i + 1
                    while (end < tagText.length && (tagText[end].isLetterOrDigit() || tagText[end] in "_:-")) end++
                    val start = sb.length
                    sb.append(tagText.substring(i, end))
                    sb.setSpan(ForegroundColorSpan(PdfXmlAttr), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    i = end
                }
                else -> {
                    sb.append(ch)
                    i++
                }
            }
        }
    }

    private fun writePdfAtomically(outputFile: File, block: (PdfDocument) -> Unit): Boolean {
        outputFile.parentFile?.let { if (!it.exists()) it.mkdirs() }
        val tmp = File(outputFile.parentFile, "${outputFile.name}.tmp")
        val pdf = PdfDocument()
        return try {
            block(pdf)
            FileOutputStream(tmp).use { out ->
                pdf.writeTo(out)
                out.flush()
            }
            if (tmp.length() > 0L) {
                if (outputFile.exists()) outputFile.delete()
                tmp.renameTo(outputFile)
            } else {
                tmp.delete()
                false
            }
        } catch (_: Throwable) {
            runCatching { tmp.delete() }
            false
        } finally {
            runCatching { pdf.close() }
        }
    }

    private class PagedCanvasState(
        private val pdf: PdfDocument,
        private val pageWidth: Int,
        private val pageHeight: Int,
        private val marginTop: Float
    ) {
        private var pageNumber = 0
        private var currentPage: PdfDocument.Page? = null
        var cursorY: Float = marginTop
        var reachedPageLimit: Boolean = false
            private set

        val canvas: Canvas
            get() = currentPage!!.canvas

        fun startPage(onPageStarted: ((Canvas) -> Unit)? = null) {
            if (pageNumber >= MAX_PDF_PAGES) {
                reachedPageLimit = true
                return
            }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            onPageStarted?.invoke(page.canvas)
            currentPage = page
            cursorY = marginTop
        }

        fun finishCurrentPage() {
            currentPage?.let { page ->
                pdf.finishPage(page)
                currentPage = null
            }
            if (pageNumber >= MAX_PDF_PAGES) {
                reachedPageLimit = true
            }
        }
    }
}
