package com.example.data

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.Build
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import com.example.model.OfficeMarginOption
import com.example.model.OfficePageSizeOption
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

/**
 * Offline renderer that converts parsed DOC/DOCX/RTF, PPT/PPTX, and EXCEL (.xlsx/.xls) documents
 * into real paginated PDF files using Android's built-in PdfDocument API, honoring detected or
 * user-selected page sizes (A4, A3, Legal, Letter, 16:9, 4:3) and margins (Narrow, Normal).
 */
object OfficeAndXmlToPdfConverter {

    private const val MAX_PDF_PAGES = 250

    private val InkPrimary = Color.rgb(15, 23, 42)
    private val InkSecondary = Color.rgb(30, 41, 59)
    private val InkMuted = Color.rgb(100, 116, 139)
    private val DividerInk = Color.rgb(203, 213, 225)
    private val SlideAccent = Color.rgb(234, 88, 12)
    private val ExcelAccent = Color.rgb(22, 163, 74)
    private val ExcelHeaderBg = Color.rgb(241, 245, 249)
    private val ExcelZebraBg = Color.rgb(248, 250, 252)
    private val NotesBg = Color.rgb(248, 250, 252)
    private val NotesBorder = Color.rgb(203, 213, 225)

    fun convertWordToPdf(
        blocks: List<WordBlock>,
        baseFontSizeSp: Float,
        outputFile: File,
        layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_WORD_A4
    ): Boolean {
        val pageW = layout.widthPt.roundToInt().coerceIn(280, 1800)
        val pageH = layout.heightPt.roundToInt().coerceIn(280, 1800)
        val marginLeft = layout.marginLeftPt.coerceIn(16f, pageW * 0.28f)
        val marginRight = layout.marginRightPt.coerceIn(16f, pageW * 0.28f)
        val marginTop = layout.marginTopPt.coerceIn(18f, pageH * 0.25f)
        val marginBottom = layout.marginBottomPt.coerceIn(18f, pageH * 0.25f)
        val contentWidth = (pageW - marginLeft - marginRight).toInt().coerceAtLeast(160)
        val pageBottom = pageH - marginBottom

        val userScale = (baseFontSizeSp.coerceIn(10f, 22f) / 15f).coerceIn(0.85f, 1.35f)
        val clampedBase = baseFontSizeSp.coerceIn(10f, 20f) * 0.84f
        val bodyTypeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        val boldTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
        val italicTypeface = Typeface.create(Typeface.SERIF, Typeface.ITALIC)
        val boldItalicTypeface = Typeface.create(Typeface.SERIF, Typeface.BOLD_ITALIC)

        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DividerInk
            strokeWidth = 0.9f
        }

        return writePdfAtomically(outputFile) { pdf ->
            val state = PagedCanvasState(pdf, pageW, pageH, marginTop)
            state.startPage()

            val ensureBlockSpace: (Float) -> Float = { needed ->
                if (!state.reachedPageLimit && state.cursorY + needed > pageBottom && state.cursorY > marginTop + 6f) {
                    state.finishCurrentPage()
                    if (!state.reachedPageLimit) state.startPage()
                }
                state.cursorY
            }

            for ((index, block) in blocks.withIndex()) {
                if (state.reachedPageLimit) break

                if (block.isPageBreak) {
                    if (state.cursorY > marginTop + 4f) {
                        state.finishCurrentPage()
                        if (!state.reachedPageLimit) state.startPage()
                    }
                    continue
                }

                if (block.image != null) {
                    val before = block.spaceBeforePt.coerceIn(0f, 12f)
                    if (state.cursorY > marginTop + 2f && state.cursorY + before < pageBottom) {
                        state.cursorY += before
                    }
                    state.cursorY = OfficeGraphicPdfRenderer.drawImageBlock(
                        image = block.image,
                        marginH = marginLeft,
                        contentWidth = contentWidth.toFloat(),
                        marginTop = marginTop,
                        pageBottom = pageBottom,
                        cursorY = state.cursorY,
                        ensureSpace = ensureBlockSpace,
                        canvasProvider = { state.canvas }
                    ) + block.spaceAfterPt.coerceIn(0f, 10f)
                    continue
                }

                if (block.chart != null) {
                    val before = block.spaceBeforePt.coerceIn(0f, 12f)
                    if (state.cursorY > marginTop + 2f && state.cursorY + before < pageBottom) {
                        state.cursorY += before
                    }
                    state.cursorY = OfficeGraphicPdfRenderer.drawChartBlock(
                        chart = block.chart,
                        marginH = marginLeft,
                        contentWidth = contentWidth.toFloat(),
                        pageBottom = pageBottom,
                        marginTop = marginTop,
                        ensureSpace = ensureBlockSpace,
                        canvasProvider = { state.canvas }
                    ) + block.spaceAfterPt.coerceIn(0f, 10f)
                    continue
                }

                if (block.tableRows.isNotEmpty()) {
                    val before = block.spaceBeforePt.coerceIn(0f, 12f)
                    if (state.cursorY > marginTop + 2f && state.cursorY + before < pageBottom) {
                        state.cursorY += before
                    }
                    state.cursorY = OfficeGraphicPdfRenderer.drawTableBlock(
                        rows = block.tableRows,
                        hasBorders = block.tableHasBorders,
                        marginH = marginLeft,
                        contentWidth = contentWidth.toFloat(),
                        baseFontSizePt = clampedBase,
                        marginTop = marginTop,
                        pageBottom = pageBottom,
                        initialCursorY = state.cursorY,
                        startNewPage = {
                            state.finishCurrentPage()
                            if (!state.reachedPageLimit) state.startPage()
                            state.cursorY
                        },
                        canvasProvider = { state.canvas }
                    ) + block.spaceAfterPt.coerceIn(0f, 10f)
                    continue
                }

                if (block.hasBottomBorder && block.text.isBlank()) {
                    if (state.cursorY + 8f <= pageBottom) {
                        state.cursorY += 4f
                        state.canvas.drawLine(marginLeft, state.cursorY, pageW - marginRight, state.cursorY, rulePaint)
                        state.cursorY += 6f
                    }
                    continue
                }

                val textSize = when {
                    block.fontSizePt != null -> (block.fontSizePt * userScale * 0.92f).coerceIn(8f, 32f)
                    block.isHeading && block.headingLevel == 1 -> clampedBase + 6.0f
                    block.isHeading && block.headingLevel == 2 -> clampedBase + 3.5f
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
                    index == 0 || state.cursorY <= marginTop + 2f -> 0f
                    block.spaceBeforePt > 0f -> (block.spaceBeforePt * 0.78f).coerceIn(1f, 18f)
                    block.isHeading && block.headingLevel == 1 -> 11f
                    block.isHeading -> 7f
                    else -> 1.5f
                }
                if (state.cursorY > marginTop && state.cursorY + topSpace < pageBottom) {
                    state.cursorY += topSpace
                }

                val displayCharSeq = buildWordParagraphText(
                    block = block,
                    userScale = userScale
                )
                val androidAlign = toAndroidAlignment(block.alignment)
                val justify = block.alignment == DocAlignment.JUSTIFY
                val indentLeft = block.leftIndentPt.coerceIn(0f, contentWidth * 0.4f)
                val effectiveW = (contentWidth - indentLeft).toInt().coerceAtLeast(120)
                val lineSpacing = block.lineSpacingMult ?: if (block.isHeading) 1.24f else 1.32f

                drawFlowingText(
                    state = state,
                    text = displayCharSeq,
                    paint = paint,
                    x = marginLeft + indentLeft,
                    width = effectiveW,
                    marginTop = marginTop,
                    pageBottom = pageBottom,
                    lineSpacingMult = lineSpacing,
                    alignment = androidAlign,
                    justify = justify
                )

                val drawUnderlineRule = block.hasBottomBorder ||
                    (block.isHeading && block.headingLevel == 1 && block.alignment == DocAlignment.LEFT)
                if (drawUnderlineRule && !state.reachedPageLimit) {
                    if (state.cursorY + 6f <= pageBottom) {
                        state.cursorY += 3f
                        state.canvas.drawLine(marginLeft, state.cursorY, pageW - marginRight, state.cursorY, rulePaint)
                        state.cursorY += 5f
                    }
                } else {
                    val afterSpace = if (block.spaceAfterPt > 0f) {
                        (block.spaceAfterPt * 0.72f).coerceIn(2f, 14f)
                    } else {
                        5f
                    }
                    state.cursorY += afterSpace
                }
            }

            state.finishCurrentPage()
        }
    }

    private fun buildWordParagraphText(
        block: WordBlock,
        userScale: Float
    ): CharSequence {
        if (block.runs.isEmpty()) {
            return if (block.isBullet) "${block.bulletPrefix}${block.text}" else block.text
        }
        val sb = SpannableStringBuilder()
        if (block.isBullet) {
            sb.append(block.bulletPrefix)
        }
        for (run in block.runs) {
            if (run.text.isEmpty()) continue
            val start = sb.length
            sb.append(run.text)
            val end = sb.length

            val style = when {
                run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                run.isBold -> Typeface.BOLD
                run.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            if (style != Typeface.NORMAL) {
                sb.setSpan(StyleSpan(style), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.isUnderline) {
                sb.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.isStrike) {
                sb.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.colorRgb != null) {
                val c = Color.rgb(
                    (run.colorRgb shr 16) and 0xFF,
                    (run.colorRgb shr 8) and 0xFF,
                    run.colorRgb and 0xFF
                )
                sb.setSpan(ForegroundColorSpan(c), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            if (run.fontSizePt != null && block.fontSizePt != run.fontSizePt) {
                val scaledPx = (run.fontSizePt * userScale * 0.92f).toInt().coerceIn(7, 34)
                sb.setSpan(AbsoluteSizeSpan(scaledPx, false), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }
        return sb.ifEmpty { if (block.isBullet) "${block.bulletPrefix}${block.text}" else block.text }
    }

    private fun toAndroidAlignment(alignment: DocAlignment): Layout.Alignment = when (alignment) {
        DocAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        DocAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        DocAlignment.LEFT, DocAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    }

    fun convertSlidesToPdf(
        slides: List<SlidePage>,
        outputFile: File,
        layout: OfficePageLayout = OfficeLayoutResolver.DEFAULT_SLIDE_16_9
    ): Boolean {
        val slideW = layout.widthPt.roundToInt().coerceIn(320, 1800)
        val slideH = layout.heightPt.roundToInt().coerceIn(280, 1800)
        val marginLeft = layout.marginLeftPt.coerceIn(16f, slideW * 0.25f)
        val marginRight = layout.marginRightPt.coerceIn(16f, slideW * 0.25f)
        val marginTop = layout.marginTopPt.coerceIn(18f, slideH * 0.22f)
        val marginBottom = layout.marginBottomPt.coerceIn(18f, slideH * 0.22f)
        val contentWidth = (slideW - marginLeft - marginRight).toInt().coerceAtLeast(180)
        val pageBottom = slideH - marginBottom

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
            val state = PagedCanvasState(pdf, slideW, slideH, marginTop)
            val totalSlides = slides.size.coerceAtLeast(1)

            for (slide in slides) {
                if (state.reachedPageLimit) break

                val slideBgRgb = slide.backgroundRgb
                val isDarkSlideBg = if (slideBgRgb != null) {
                    val r = (slideBgRgb shr 16) and 0xFF
                    val g = (slideBgRgb shr 8) and 0xFF
                    val b = slideBgRgb and 0xFF
                    (0.299f * r + 0.587f * g + 0.114f * b) < 120f
                } else false

                val paintSlideHeader: (Canvas) -> Unit = { canvas ->
                    if (slideBgRgb != null) {
                        canvas.drawColor(
                            Color.rgb(
                                (slideBgRgb shr 16) and 0xFF,
                                (slideBgRgb shr 8) and 0xFF,
                                slideBgRgb and 0xFF
                            )
                        )
                    }
                    canvas.drawRect(0f, 0f, slideW.toFloat(), 7f, accentPaint)
                }

                state.startPage(onPageStarted = paintSlideHeader)

                val badgeLayout = buildStaticLayout(
                    "SLIDE ${slide.slideNumber} OF $totalSlides",
                    badgePaint,
                    contentWidth,
                    1.1f
                )
                state.canvas.save()
                state.canvas.translate(marginLeft, state.cursorY)
                badgeLayout.draw(state.canvas)
                state.canvas.restore()
                state.cursorY += badgeLayout.height + 8f

                if (slide.blocks.isNotEmpty()) {
                    val ensureSlideSpace: (Float) -> Float = { needed ->
                        if (!state.reachedPageLimit && state.cursorY + needed > pageBottom && state.cursorY > marginTop + 8f) {
                            state.finishCurrentPage()
                            if (!state.reachedPageLimit) state.startPage(onPageStarted = paintSlideHeader)
                        }
                        state.cursorY
                    }

                    for ((blockIdx, block) in slide.blocks.withIndex()) {
                        if (state.reachedPageLimit) break
                        if (block.image != null) {
                            state.cursorY = OfficeGraphicPdfRenderer.drawImageBlock(
                                image = block.image,
                                marginH = marginLeft,
                                contentWidth = contentWidth.toFloat(),
                                marginTop = marginTop,
                                pageBottom = pageBottom,
                                cursorY = state.cursorY,
                                ensureSpace = ensureSlideSpace,
                                canvasProvider = { state.canvas }
                            )
                            continue
                        }
                        if (block.chart != null) {
                            state.cursorY = OfficeGraphicPdfRenderer.drawChartBlock(
                                chart = block.chart,
                                marginH = marginLeft,
                                contentWidth = contentWidth.toFloat(),
                                pageBottom = pageBottom,
                                marginTop = marginTop,
                                ensureSpace = ensureSlideSpace,
                                canvasProvider = { state.canvas }
                            )
                            continue
                        }
                        if (block.tableRows.isNotEmpty()) {
                            state.cursorY = OfficeGraphicPdfRenderer.drawTableBlock(
                                rows = block.tableRows,
                                hasBorders = block.tableHasBorders,
                                marginH = marginLeft,
                                contentWidth = contentWidth.toFloat(),
                                baseFontSizePt = 12f,
                                marginTop = marginTop,
                                pageBottom = pageBottom,
                                initialCursorY = state.cursorY,
                                startNewPage = {
                                    state.finishCurrentPage()
                                    if (!state.reachedPageLimit) state.startPage(onPageStarted = paintSlideHeader)
                                    state.cursorY
                                },
                                canvasProvider = { state.canvas }
                            )
                            continue
                        }

                        val effectiveTitle = block.isHeading || blockIdx == 0
                        val txtColor = if (isDarkSlideBg) Color.WHITE else if (effectiveTitle) InkPrimary else InkSecondary
                        val sz = (block.fontSizePt ?: if (effectiveTitle) 20f else 13.5f).coerceIn(9f, 34f)
                        val slideTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = txtColor
                            textSize = sz
                            typeface = Typeface.create(
                                Typeface.SANS_SERIF,
                                when {
                                    (effectiveTitle || block.isBold) && block.isItalic -> Typeface.BOLD_ITALIC
                                    effectiveTitle || block.isBold -> Typeface.BOLD
                                    block.isItalic -> Typeface.ITALIC
                                    else -> Typeface.NORMAL
                                }
                            )
                        }
                        val seq = buildWordParagraphText(block, userScale = 1f)
                        drawFlowingText(
                            state = state,
                            text = seq,
                            paint = slideTextPaint,
                            x = marginLeft,
                            width = contentWidth,
                            marginTop = marginTop,
                            pageBottom = pageBottom,
                            lineSpacingMult = 1.28f,
                            alignment = toAndroidAlignment(block.alignment),
                            onNewOverflowPage = paintSlideHeader
                        )
                        if (effectiveTitle && blockIdx == 0 && state.cursorY + 12f < pageBottom) {
                            state.cursorY += 6f
                            state.canvas.drawLine(marginLeft, state.cursorY, slideW - marginRight, state.cursorY, dividerPaint)
                            state.cursorY += 10f
                        } else {
                            state.cursorY += 6f
                        }
                    }
                } else {
                    titlePaint.color = if (isDarkSlideBg) Color.WHITE else InkPrimary
                    bodyPaint.color = if (isDarkSlideBg) Color.rgb(226, 232, 240) else InkSecondary

                    drawFlowingText(
                        state = state,
                        text = slide.title,
                        paint = titlePaint,
                        x = marginLeft,
                        width = contentWidth,
                        marginTop = marginTop,
                        pageBottom = pageBottom,
                        lineSpacingMult = 1.25f,
                        onNewOverflowPage = paintSlideHeader
                    )

                    state.cursorY += 10f
                    if (state.cursorY + 8f < pageBottom) {
                        state.canvas.drawLine(marginLeft, state.cursorY, slideW - marginRight, state.cursorY, dividerPaint)
                        state.cursorY += 14f
                    }

                    for (line in slide.bodyLines) {
                        if (state.reachedPageLimit) break
                        drawFlowingText(
                            state = state,
                            text = "•  $line",
                            paint = bodyPaint,
                            x = marginLeft,
                            width = contentWidth,
                            marginTop = marginTop,
                            pageBottom = pageBottom,
                            lineSpacingMult = 1.35f,
                            onNewOverflowPage = paintSlideHeader
                        )
                        state.cursorY += 7f
                    }
                }

                if (slide.notes.isNotBlank() && !state.reachedPageLimit) {
                    val notesInnerWidth = (contentWidth - 24).coerceAtLeast(100)
                    val notesLayout = buildStaticLayout(slide.notes, notesBodyPaint, notesInnerWidth, 1.3f)
                    val boxHeight = 28f + notesLayout.height
                    if (state.cursorY + boxHeight + 8f > pageBottom && state.cursorY > marginTop + 40f) {
                        state.finishCurrentPage()
                        if (!state.reachedPageLimit) {
                            state.startPage(onPageStarted = paintSlideHeader)
                        }
                    }
                    if (!state.reachedPageLimit) {
                        val boxTop = state.cursorY.coerceAtMost(pageBottom - boxHeight)
                        val clampedBottom = (boxTop + boxHeight).coerceAtMost(pageBottom)
                        if (clampedBottom - boxTop >= 32f) {
                            val rect = RectF(marginLeft, boxTop, slideW - marginRight, clampedBottom)
                            state.canvas.drawRoundRect(rect, 10f, 10f, notesBgPaint)
                            state.canvas.drawRoundRect(rect, 10f, 10f, notesBorderPaint)

                            state.canvas.save()
                            state.canvas.translate(marginLeft + 12f, boxTop + 8f)
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

    fun convertExcelToPdf(
        workbook: ExcelWorkbook,
        showGridlines: Boolean,
        baseFontSizeSp: Float,
        outputFile: File,
        pageSizeOption: OfficePageSizeOption = OfficePageSizeOption.AUTO,
        marginOption: OfficeMarginOption = OfficeMarginOption.AUTO
    ): Boolean {
        val cellTextSize = (baseFontSizeSp.coerceIn(11f, 22f) * 0.64f).coerceIn(7.5f, 13.5f)
        val headerTextSize = (cellTextSize * 0.94f).coerceIn(7f, 12.5f)
        val sansNormal = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        val sansBold = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        val sansItalic = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
        val sansBoldItalic = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD_ITALIC)

        val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ExcelAccent }
        val bannerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ExcelAccent
            textSize = 9.5f
            typeface = sansBold
        }
        val sheetTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkPrimary
            textSize = 14f
            typeface = sansBold
        }
        val colHeaderTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkSecondary
            textSize = headerTextSize
            typeface = sansBold
            textAlign = Paint.Align.CENTER
        }
        val rowNumTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = InkMuted
            textSize = headerTextSize
            typeface = sansNormal
            textAlign = Paint.Align.CENTER
        }
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DividerInk
            style = Paint.Style.STROKE
            strokeWidth = 0.7f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        val sheets = workbook.sheets.ifEmpty {
            listOf(ExcelSheet(name = "Sheet1", maxColumns = 1, rows = emptyList()))
        }

        return writePdfAtomically(outputFile) { pdf ->
            val defaultLayout = OfficeLayoutResolver.resolve(
                workbook.layout,
                pageSizeOption,
                marginOption,
                isSpreadsheet = true
            )
            val state = PagedCanvasState(
                pdf = pdf,
                pageWidth = defaultLayout.widthPt.roundToInt(),
                pageHeight = defaultLayout.heightPt.roundToInt(),
                marginTop = defaultLayout.marginTopPt
            )

            for ((sheetIdx, sheet) in sheets.withIndex()) {
                if (state.reachedPageLimit) break

                val sheetLayout = OfficeLayoutResolver.resolve(
                    sheet.layout,
                    pageSizeOption,
                    marginOption,
                    isSpreadsheet = true
                )
                val sheetW = sheetLayout.widthPt.roundToInt().coerceIn(280, 1800)
                val sheetH = sheetLayout.heightPt.roundToInt().coerceIn(280, 1800)
                val marginLeft = sheetLayout.marginLeftPt.coerceIn(14f, sheetW * 0.25f)
                val marginRight = sheetLayout.marginRightPt.coerceIn(14f, sheetW * 0.25f)
                val marginTop = sheetLayout.marginTopPt.coerceIn(16f, sheetH * 0.22f)
                val marginBottom = sheetLayout.marginBottomPt.coerceIn(16f, sheetH * 0.22f)
                val contentWidth = (sheetW - marginLeft - marginRight).coerceAtLeast(180f)
                val pageBottom = sheetH - marginBottom

                val totalCols = sheet.maxColumns.coerceAtLeast(1)
                val rowNumColW = if (showGridlines) 28f else 0f
                val availTableW = (contentWidth - rowNumColW).coerceAtLeast(160f)

                val colWidthsPt = FloatArray(totalCols) { colIdx ->
                    val explicitChars = sheet.columnWidthsChars[colIdx]
                    val maxSampleChars = sheet.rows
                        .asSequence()
                        .take(80)
                        .flatMap { it.cells.asSequence() }
                        .filter { it.colIndex == colIdx && it.colSpan == 1 }
                        .maxOfOrNull { it.text.length } ?: 6
                    val approxW = if (explicitChars != null && explicitChars > 0f) {
                        explicitChars * (cellTextSize * 0.62f) + 12f
                    } else {
                        maxSampleChars * (cellTextSize * 0.56f) + 16f
                    }
                    approxW.coerceIn(46f, minOf(210f, availTableW))
                }

                // Partition columns into horizontal bands so wide spreadsheets never crush columns
                val colBands = mutableListOf<IntRange>()
                var bandStart = 0
                while (bandStart < totalCols) {
                    var widthAcc = 0f
                    var bandEnd = bandStart
                    while (bandEnd < totalCols) {
                        val w = colWidthsPt[bandEnd]
                        if (bandEnd > bandStart && widthAcc + w > availTableW) break
                        widthAcc += w
                        bandEnd++
                    }
                    colBands.add(bandStart until bandEnd)
                    bandStart = bandEnd
                }

                fun scaledBandWidths(band: IntRange): FloatArray {
                    val raw = FloatArray(band.count()) { i -> colWidthsPt[band.first + i] }
                    val sum = raw.sum().coerceAtLeast(1f)
                    if (sum < availTableW) {
                        val factor = (availTableW / sum).coerceAtMost(1.8f)
                        for (i in raw.indices) raw[i] *= factor
                    } else if (sum > availTableW) {
                        val factor = availTableW / sum
                        for (i in raw.indices) raw[i] *= factor
                    }
                    return raw
                }

                fun drawSheetHeaderBanner(band: IntRange) {
                    val canvas = state.canvas
                    canvas.drawRect(0f, 0f, sheetW.toFloat(), 5f, accentPaint)
                    val colSpanLabel = if (colBands.size > 1) {
                        "  •  Cols ${OoxmlWorkbookParser.columnLetter(band.first)}–${OoxmlWorkbookParser.columnLetter(band.last)}"
                    } else {
                        ""
                    }
                    val badge = "SHEET ${sheetIdx + 1} OF ${sheets.size}  •  ${sheetLayout.detectedPaperName} ${sheetLayout.detectedMarginName}$colSpanLabel"
                    canvas.drawText(badge, marginLeft, state.cursorY + 8f, bannerPaint)
                    canvas.drawText(sheet.name.take(58), marginLeft, state.cursorY + 23f, sheetTitlePaint)
                    state.cursorY += 30f
                }

                fun drawColumnLetterBar(band: IntRange, widths: FloatArray) {
                    if (!showGridlines) return
                    val barH = 18f
                    val canvas = state.canvas
                    fillPaint.color = ExcelHeaderBg
                    val totalBarW = rowNumColW + widths.sum()
                    canvas.drawRect(marginLeft, state.cursorY, marginLeft + totalBarW, state.cursorY + barH, fillPaint)
                    canvas.drawRect(marginLeft, state.cursorY, marginLeft + totalBarW, state.cursorY + barH, gridPaint)

                    if (rowNumColW > 0f) {
                        canvas.drawLine(marginLeft + rowNumColW, state.cursorY, marginLeft + rowNumColW, state.cursorY + barH, gridPaint)
                    }
                    var x = marginLeft + rowNumColW
                    band.forEachIndexed { idx, colIdx ->
                        val cw = widths[idx]
                        canvas.drawText(
                            OoxmlWorkbookParser.columnLetter(colIdx),
                            x + cw / 2f,
                            state.cursorY + 12.5f,
                            colHeaderTextPaint
                        )
                        canvas.drawLine(x + cw, state.cursorY, x + cw, state.cursorY + barH, gridPaint)
                        x += cw
                    }
                    state.cursorY += barH
                }

                val repeatHeaderRow = sheet.rows.firstOrNull()?.takeIf { it.isHeaderLikely }

                fun drawExcelRow(
                    row: ExcelRow,
                    band: IntRange,
                    widths: FloatArray,
                    zebraShade: Boolean
                ): Boolean {
                    val cellMap = row.cells.associateBy { it.colIndex }
                    val padH = 5f
                    val padV = 4f
                    val layouts = mutableListOf<Triple<Int, Float, StaticLayout?>>()
                    val colStyles = mutableListOf<ExcelCell?>()
                    var maxH = (cellTextSize + padV * 2f + 3f).coerceAtLeast(17f)

                    var ci = band.first
                    while (ci <= band.last) {
                        val cell = cellMap[ci]
                        val spanCols = (cell?.colSpan ?: 1).coerceIn(1, band.last - ci + 1)
                        var spanWidth = 0f
                        for (k in 0 until spanCols) {
                            spanWidth += widths[(ci - band.first) + k]
                        }
                        val innerW = (spanWidth - padH * 2f).coerceAtLeast(16f).toInt()
                        val text = cell?.text.orEmpty()
                        val layout = if (text.isNotEmpty()) {
                            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                                color = cell?.textColorRgb?.let {
                                    Color.rgb((it shr 16) and 0xFF, (it shr 8) and 0xFF, it and 0xFF)
                                } ?: InkPrimary
                                textSize = cellTextSize
                                typeface = when {
                                    cell?.isBold == true && cell.isItalic -> sansBoldItalic
                                    cell?.isBold == true || row.isHeaderLikely -> sansBold
                                    cell?.isItalic == true -> sansItalic
                                    else -> sansNormal
                                }
                            }
                            buildStaticLayout(
                                text = text,
                                paint = paint,
                                width = innerW,
                                lineSpacingMult = 1.18f,
                                alignment = toAndroidAlignment(cell?.alignment ?: DocAlignment.LEFT)
                            )
                        } else null
                        if (layout != null) {
                            maxH = maxOf(maxH, layout.height + padV * 2f)
                        }
                        layouts.add(Triple(ci - band.first, spanWidth, layout))
                        colStyles.add(cell)
                        ci += spanCols
                    }

                    val rowH = maxH.coerceAtMost((pageBottom - marginTop - 60f).coerceAtLeast(36f))
                    if (state.cursorY + rowH > pageBottom && state.cursorY > marginTop + 54f) {
                        return false
                    }

                    val canvas = state.canvas
                    if (showGridlines && rowNumColW > 0f) {
                        fillPaint.color = ExcelHeaderBg
                        canvas.drawRect(marginLeft, state.cursorY, marginLeft + rowNumColW, state.cursorY + rowH, fillPaint)
                        canvas.drawRect(marginLeft, state.cursorY, marginLeft + rowNumColW, state.cursorY + rowH, gridPaint)
                        canvas.drawText(
                            row.rowNumber.toString(),
                            marginLeft + rowNumColW / 2f,
                            state.cursorY + rowH / 2f + headerTextSize * 0.35f,
                            rowNumTextPaint
                        )
                    }

                    var x = marginLeft + rowNumColW
                    layouts.forEachIndexed { idx, (_, spanW, layout) ->
                        val cell = colStyles[idx]
                        val bgRgb = cell?.backgroundRgb
                        val cellBgColor = when {
                            bgRgb != null -> Color.rgb((bgRgb shr 16) and 0xFF, (bgRgb shr 8) and 0xFF, bgRgb and 0xFF)
                            row.isHeaderLikely -> Color.rgb(236, 253, 245)
                            zebraShade -> ExcelZebraBg
                            else -> null
                        }
                        if (cellBgColor != null) {
                            fillPaint.color = cellBgColor
                            canvas.drawRect(x, state.cursorY, x + spanW, state.cursorY + rowH, fillPaint)
                        }
                        if (showGridlines) {
                            canvas.drawRect(x, state.cursorY, x + spanW, state.cursorY + rowH, gridPaint)
                        }
                        if (layout != null) {
                            canvas.save()
                            canvas.clipRect(x + 2f, state.cursorY + 1f, x + spanW - 2f, state.cursorY + rowH - 1f)
                            val yOffset = state.cursorY + ((rowH - layout.height) / 2f).coerceAtLeast(padV)
                            canvas.translate(x + padH, yOffset)
                            layout.draw(canvas)
                            canvas.restore()
                        }
                        x += spanW
                    }

                    state.cursorY += rowH
                    return true
                }

                for ((bandIndex, band) in colBands.withIndex()) {
                    if (state.reachedPageLimit) break
                    val widths = scaledBandWidths(band)
                    state.startPage(overrideWidth = sheetW, overrideHeight = sheetH, overrideMarginTop = marginTop)
                    drawSheetHeaderBanner(band)
                    drawColumnLetterBar(band, widths)

                    if (sheet.rows.isEmpty()) {
                        val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = InkMuted
                            textSize = cellTextSize + 1f
                            typeface = sansItalic
                        }
                        state.canvas.drawText("(Empty sheet)", marginLeft, state.cursorY + 20f, emptyPaint)
                        state.cursorY += 28f
                    } else {
                        for ((rowIdx, row) in sheet.rows.withIndex()) {
                            if (state.reachedPageLimit) break
                            val zebra = rowIdx % 2 == 1
                            val fits = drawExcelRow(row, band, widths, zebra)
                            if (!fits) {
                                state.finishCurrentPage()
                                if (state.reachedPageLimit) break
                                state.startPage(overrideWidth = sheetW, overrideHeight = sheetH, overrideMarginTop = marginTop)
                                drawSheetHeaderBanner(band)
                                drawColumnLetterBar(band, widths)
                                if (repeatHeaderRow != null && rowIdx > 0) {
                                    drawExcelRow(repeatHeaderRow, band, widths, zebraShade = false)
                                }
                                drawExcelRow(row, band, widths, zebra)
                            }
                        }
                    }

                    if (bandIndex == 0 && sheet.embeddedBlocks.isNotEmpty() && !state.reachedPageLimit) {
                        val ensureSpace: (Float) -> Float = { needed ->
                            if (!state.reachedPageLimit && state.cursorY + needed > pageBottom && state.cursorY > marginTop + 40f) {
                                state.finishCurrentPage()
                                if (!state.reachedPageLimit) {
                                    state.startPage(overrideWidth = sheetW, overrideHeight = sheetH, overrideMarginTop = marginTop)
                                    drawSheetHeaderBanner(band)
                                }
                            }
                            state.cursorY
                        }
                        state.cursorY = ensureSpace(28f)
                        if (!state.reachedPageLimit) {
                            state.cursorY += 10f
                            state.canvas.drawText("CHARTS & IMAGES", marginLeft, state.cursorY + 10f, bannerPaint)
                            state.cursorY += 18f
                        }
                        for (mediaBlock in sheet.embeddedBlocks) {
                            if (state.reachedPageLimit) break
                            mediaBlock.image?.let { img ->
                                state.cursorY = OfficeGraphicPdfRenderer.drawImageBlock(
                                    image = img,
                                    marginH = marginLeft,
                                    contentWidth = contentWidth,
                                    marginTop = marginTop,
                                    pageBottom = pageBottom,
                                    cursorY = state.cursorY,
                                    ensureSpace = ensureSpace,
                                    canvasProvider = { state.canvas }
                                )
                            }
                            mediaBlock.chart?.let { ch ->
                                state.cursorY = OfficeGraphicPdfRenderer.drawChartBlock(
                                    chart = ch,
                                    marginH = marginLeft,
                                    contentWidth = contentWidth,
                                    pageBottom = pageBottom,
                                    marginTop = marginTop,
                                    ensureSpace = ensureSpace,
                                    canvasProvider = { state.canvas }
                                )
                            }
                        }
                    }

                    state.finishCurrentPage()
                }
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
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        justify: Boolean = false,
        onNewOverflowPage: ((Canvas) -> Unit)? = null
    ) {
        val layout = buildStaticLayout(text, paint, width, lineSpacingMult, alignment, justify)
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
            alignment = alignment,
            justify = justify,
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
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        justify: Boolean = false,
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
                val sliceLayout = buildStaticLayout(slice, paint, width, lineSpacingMult, alignment, justify)
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
        lineSpacingMult: Float,
        alignment: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        justify: Boolean = false
    ): StaticLayout {
        val builder = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, width.coerceAtLeast(64))
            .setAlignment(alignment)
            .setLineSpacing(0f, lineSpacingMult)
            .setIncludePad(false)
        if (justify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
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

        fun startPage(
            onPageStarted: ((Canvas) -> Unit)? = null,
            overrideWidth: Int = pageWidth,
            overrideHeight: Int = pageHeight,
            overrideMarginTop: Float = marginTop
        ) {
            if (pageNumber >= MAX_PDF_PAGES) {
                reachedPageLimit = true
                return
            }
            pageNumber++
            val pageInfo = PdfDocument.PageInfo.Builder(overrideWidth, overrideHeight, pageNumber).create()
            val page = pdf.startPage(pageInfo)
            page.canvas.drawColor(Color.WHITE)
            onPageStarted?.invoke(page.canvas)
            currentPage = page
            cursorY = overrideMarginTop
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
