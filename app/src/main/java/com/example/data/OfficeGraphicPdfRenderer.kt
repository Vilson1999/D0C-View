package com.example.data

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Draws MS Office embedded images, charts (Column, Bar, Line, Area, Pie, Doughnut, Scatter),
 * and structured tables onto Android PdfDocument Canvas pages.
 */
object OfficeGraphicPdfRenderer {

    private val OfficePalette = intArrayOf(
        Color.rgb(68, 114, 196),   // MS Office Blue
        Color.rgb(237, 125, 49),   // Orange
        Color.rgb(112, 173, 71),   // Green
        Color.rgb(255, 192, 0),    // Amber
        Color.rgb(91, 155, 213),   // Sky
        Color.rgb(165, 165, 165),  // Slate
        Color.rgb(158, 72, 14),    // Terracotta
        Color.rgb(38, 68, 120)     // Navy
    )

    private val GridLineColor = Color.rgb(226, 232, 240)
    private val AxisColor = Color.rgb(148, 163, 184)
    private val LabelColor = Color.rgb(71, 85, 105)
    private val TitleColor = Color.rgb(15, 23, 42)
    private val TableHeaderBg = Color.rgb(241, 245, 249)
    private val TableBorderColor = Color.rgb(203, 213, 225)

    fun drawImageBlock(
        image: WordImage,
        marginH: Float,
        contentWidth: Float,
        marginTop: Float,
        pageBottom: Float,
        cursorY: Float,
        ensureSpace: (neededHeight: Float) -> Float,
        canvasProvider: () -> Canvas
    ): Float {
        val bitmap = decodeScaledBitmap(image.bytes, maxDimPx = 4096) ?: return cursorY
        return try {
            val aspect = if (bitmap.width > 0) bitmap.height.toFloat() / bitmap.width.toFloat() else 1f
            var drawW = (image.widthPt ?: (contentWidth * 0.68f)).coerceIn(36f, contentWidth)
            var drawH = image.heightPt ?: (drawW * aspect)
            if (image.widthPt != null && image.heightPt == null) {
                drawH = drawW * aspect
            }
            if (drawW > contentWidth) {
                val scale = contentWidth / drawW
                drawW *= scale
                drawH *= scale
            }
            val maxPageContentH = (pageBottom - marginTop - 8f).coerceAtLeast(80f)
            if (drawH > maxPageContentH) {
                val scale = maxPageContentH / drawH
                drawW *= scale
                drawH *= scale
            }

            val y = ensureSpace(drawH + 6f)
            val x = when (image.alignment) {
                DocAlignment.CENTER -> marginH + (contentWidth - drawW) / 2f
                DocAlignment.RIGHT -> marginH + (contentWidth - drawW)
                else -> marginH
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvasProvider().drawBitmap(bitmap, null, RectF(x, y, x + drawW, y + drawH), paint)
            y + drawH + 8f
        } finally {
            bitmap.recycle()
        }
    }

    fun drawChartBlock(
        chart: OfficeChart,
        marginH: Float,
        contentWidth: Float,
        pageBottom: Float,
        marginTop: Float,
        ensureSpace: (neededHeight: Float) -> Float,
        canvasProvider: () -> Canvas
    ): Float {
        val chartHeight = min(230f, (pageBottom - marginTop - 16f).coerceAtLeast(150f))
        val y = ensureSpace(chartHeight + 10f)
        val canvas = canvasProvider()

        val cardRect = RectF(marginH, y, marginH + contentWidth, y + chartHeight)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TableBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 1f
        }
        canvas.drawRoundRect(cardRect, 8f, 8f, bgPaint)
        canvas.drawRoundRect(cardRect, 8f, 8f, borderPaint)

        var innerTop = y + 14f
        if (chart.title.isNotBlank()) {
            val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = TitleColor
                textSize = 11.5f
                typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(chart.title.take(64), cardRect.centerX(), innerTop + 10f, titlePaint)
            innerTop += 22f
        }

        // Draw series legend when multiple series (or pie categories)
        val legendPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabelColor
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        }
        val legendItems = if (chart.type == OfficeChartType.PIE || chart.type == OfficeChartType.DOUGHNUT) {
            chart.categories.take(8).mapIndexed { idx, cat ->
                cat.take(14) to seriesColor(idx, null)
            }
        } else {
            chart.series.take(6).mapIndexed { idx, s ->
                s.name.take(16) to seriesColor(idx, s.colorRgb)
            }
        }

        var legendBottom = y + chartHeight - 10f
        if (legendItems.isNotEmpty()) {
            drawCenteredLegend(canvas, legendItems, cardRect.left + 12f, cardRect.right - 12f, legendBottom, legendPaint)
            legendBottom -= 16f
        }

        val plotRect = RectF(
            cardRect.left + 40f,
            innerTop + 4f,
            cardRect.right - 16f,
            legendBottom - 18f
        )

        if (plotRect.width() > 40f && plotRect.height() > 40f) {
            when (chart.type) {
                OfficeChartType.PIE, OfficeChartType.DOUGHNUT ->
                    drawPieOrDoughnutChart(canvas, chart, plotRect, isDoughnut = chart.type == OfficeChartType.DOUGHNUT)
                OfficeChartType.BAR ->
                    drawHorizontalBarChart(canvas, chart, plotRect)
                OfficeChartType.LINE, OfficeChartType.AREA, OfficeChartType.SCATTER ->
                    drawLineOrAreaChart(canvas, chart, plotRect)
                OfficeChartType.COLUMN ->
                    drawVerticalColumnChart(canvas, chart, plotRect)
            }
        }

        return y + chartHeight + 10f
    }

    fun drawTableBlock(
        rows: List<WordTableRow>,
        hasBorders: Boolean,
        marginH: Float,
        contentWidth: Float,
        baseFontSizePt: Float,
        marginTop: Float,
        pageBottom: Float,
        initialCursorY: Float,
        startNewPage: () -> Float,
        canvasProvider: () -> Canvas
    ): Float {
        if (rows.isEmpty()) return initialCursorY
        val totalCols = rows.maxOf { row -> row.cells.sumOf { it.colSpan.coerceAtLeast(1) } }.coerceAtLeast(1)
        val colWidth = contentWidth / totalCols
        val cellPadH = 6f
        val cellPadV = 5f

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = TableBorderColor
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

        var cursorY = initialCursorY

        for (row in rows) {
            val cellRenderables = ArrayList<Pair<WordTableCell, List<Any>>>(row.cells.size)
            var maxCellHeight = 16f

            for (cell in row.cells) {
                val cellWidthF = (colWidth * cell.colSpan.coerceAtLeast(1) - cellPadH * 2f).coerceAtLeast(24f)
                val cellWidth = cellWidthF.toInt()
                val items = mutableListOf<Any>()
                var cellContentHeight = cellPadV * 2f

                for (para in cell.paragraphs) {
                    if (para.image != null) {
                        val bmp = decodeScaledBitmap(para.image.bytes, maxDimPx = 4096)
                        if (bmp != null) {
                            val aspect = if (bmp.width > 0) bmp.height.toFloat() / bmp.width.toFloat() else 1f
                            var drawW = (para.image.widthPt ?: (cellWidthF * 0.9f)).coerceIn(20f, cellWidthF)
                            var drawH = para.image.heightPt ?: (drawW * aspect)
                            if (drawW > cellWidthF) {
                                val s = cellWidthF / drawW
                                drawW *= s
                                drawH *= s
                            }
                            items.add(CellBitmapItem(bmp, drawW, drawH, para.image.alignment))
                            cellContentHeight += drawH + 4f
                        }
                        continue
                    }

                    if (para.text.isBlank()) continue
                    val fontSize = (para.fontSizePt ?: baseFontSizePt).coerceIn(7.5f, 18f) * 0.88f
                    val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = TitleColor
                        textSize = fontSize
                        typeface = Typeface.create(
                            Typeface.SERIF,
                            when {
                                (para.isBold || row.isHeader) && para.isItalic -> Typeface.BOLD_ITALIC
                                para.isBold || row.isHeader -> Typeface.BOLD
                                para.isItalic -> Typeface.ITALIC
                                else -> Typeface.NORMAL
                            }
                        )
                    }
                    val text = if (para.isBullet) "• ${para.text}" else para.text
                    val layout = StaticLayout.Builder
                        .obtain(text, 0, text.length, paint, cellWidth)
                        .setAlignment(toAndroidAlignment(para.alignment))
                        .setLineSpacing(0f, 1.25f)
                        .setIncludePad(false)
                        .build()
                    items.add(layout)
                    cellContentHeight += layout.height + 2f
                }
                maxCellHeight = max(maxCellHeight, cellContentHeight)
                cellRenderables.add(cell to items)
            }

            if (cursorY + maxCellHeight > pageBottom && cursorY > marginTop + 8f) {
                cursorY = startNewPage()
            }

            val canvas = canvasProvider()
            var xOffset = marginH
            for ((cell, items) in cellRenderables) {
                val spanW = colWidth * cell.colSpan.coerceAtLeast(1)
                val cellRect = RectF(xOffset, cursorY, xOffset + spanW, cursorY + maxCellHeight)

                val bgColor = when {
                    cell.backgroundRgb != null -> Color.rgb(
                        (cell.backgroundRgb shr 16) and 0xFF,
                        (cell.backgroundRgb shr 8) and 0xFF,
                        cell.backgroundRgb and 0xFF
                    )
                    row.isHeader && hasBorders -> TableHeaderBg
                    else -> null
                }
                if (bgColor != null) {
                    fillPaint.color = bgColor
                    canvas.drawRect(cellRect, fillPaint)
                }
                if (hasBorders) {
                    canvas.drawRect(cellRect, borderPaint)
                }

                var itemY = cursorY + cellPadV
                val innerW = (spanW - cellPadH * 2f).coerceAtLeast(12f)
                val imgPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                for (item in items) {
                    when (item) {
                        is StaticLayout -> {
                            canvas.save()
                            canvas.translate(xOffset + cellPadH, itemY)
                            item.draw(canvas)
                            canvas.restore()
                            itemY += item.height + 2f
                        }
                        is CellBitmapItem -> {
                            val imgX = when (item.alignment) {
                                DocAlignment.CENTER -> xOffset + cellPadH + (innerW - item.width) / 2f
                                DocAlignment.RIGHT -> xOffset + cellPadH + (innerW - item.width)
                                else -> xOffset + cellPadH
                            }
                            canvas.drawBitmap(
                                item.bitmap,
                                null,
                                RectF(imgX, itemY, imgX + item.width, itemY + item.height),
                                imgPaint
                            )
                            item.bitmap.recycle()
                            itemY += item.height + 4f
                        }
                    }
                }
                xOffset += spanW
            }
            cursorY += maxCellHeight
        }

        return cursorY + 6f
    }

    private data class CellBitmapItem(
        val bitmap: Bitmap,
        val width: Float,
        val height: Float,
        val alignment: DocAlignment
    )

    private fun drawVerticalColumnChart(canvas: Canvas, chart: OfficeChart, plot: RectF) {
        val allValues = chart.series.flatMap { it.values }
        val maxVal = max(allValues.maxOrNull() ?: 1.0, 1.0)
        drawHorizontalGrid(canvas, plot, maxVal)

        val catCount = chart.categories.size.coerceAtLeast(1)
        val seriesCount = chart.series.size.coerceAtLeast(1)
        val groupWidth = plot.width() / catCount
        val barAreaWidth = groupWidth * 0.72f
        val barWidth = (barAreaWidth / seriesCount).coerceAtLeast(4f)
        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabelColor
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }

        for (c in 0 until catCount) {
            val groupStart = plot.left + c * groupWidth + (groupWidth - barAreaWidth) / 2f
            for (sIdx in 0 until seriesCount) {
                val value = chart.series[sIdx].values.getOrElse(c) { 0.0 }.coerceAtLeast(0.0)
                val barH = ((value / maxVal) * plot.height()).toFloat().coerceAtLeast(1.5f)
                val left = groupStart + sIdx * barWidth + 1f
                val right = (left + barWidth - 2f).coerceAtLeast(left + 2f)
                barPaint.color = seriesColor(sIdx, chart.series[sIdx].colorRgb)
                canvas.drawRoundRect(
                    RectF(left, plot.bottom - barH, right, plot.bottom),
                    2f, 2f, barPaint
                )
            }
            val label = chart.categories[c].take(10)
            canvas.drawText(label, plot.left + (c + 0.5f) * groupWidth, plot.bottom + 11f, catPaint)
        }
    }

    private fun drawHorizontalBarChart(canvas: Canvas, chart: OfficeChart, plot: RectF) {
        val allValues = chart.series.flatMap { it.values }
        val maxVal = max(allValues.maxOrNull() ?: 1.0, 1.0)
        val catCount = chart.categories.size.coerceAtLeast(1)
        val seriesCount = chart.series.size.coerceAtLeast(1)
        val groupHeight = plot.height() / catCount
        val barAreaH = groupHeight * 0.72f
        val barH = (barAreaH / seriesCount).coerceAtLeast(4f)

        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AxisColor
            strokeWidth = 1f
        }
        canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, axisPaint)
        canvas.drawLine(plot.left, plot.bottom, plot.right, plot.bottom, axisPaint)

        val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabelColor
            textSize = 7.8f
            textAlign = Paint.Align.RIGHT
        }

        for (c in 0 until catCount) {
            val groupTop = plot.top + c * groupHeight + (groupHeight - barAreaH) / 2f
            for (sIdx in 0 until seriesCount) {
                val value = chart.series[sIdx].values.getOrElse(c) { 0.0 }.coerceAtLeast(0.0)
                val barW = ((value / maxVal) * plot.width()).toFloat().coerceAtLeast(1.5f)
                val top = groupTop + sIdx * barH + 1f
                val bottom = (top + barH - 2f).coerceAtLeast(top + 2f)
                barPaint.color = seriesColor(sIdx, chart.series[sIdx].colorRgb)
                canvas.drawRoundRect(RectF(plot.left, top, plot.left + barW, bottom), 2f, 2f, barPaint)
            }
            canvas.drawText(
                chart.categories[c].take(8),
                plot.left - 4f,
                plot.top + (c + 0.5f) * groupHeight + 3f,
                labelPaint
            )
        }
    }

    private fun drawLineOrAreaChart(canvas: Canvas, chart: OfficeChart, plot: RectF) {
        val allValues = chart.series.flatMap { it.values }
        val maxVal = max(allValues.maxOrNull() ?: 1.0, 1.0)
        drawHorizontalGrid(canvas, plot, maxVal)

        val catCount = chart.categories.size.coerceAtLeast(1)
        val catPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabelColor
            textSize = 8f
            textAlign = Paint.Align.CENTER
        }
        for (c in 0 until catCount) {
            val x = if (catCount == 1) plot.centerX() else plot.left + c * (plot.width() / (catCount - 1))
            canvas.drawText(chart.categories[c].take(9), x, plot.bottom + 11f, catPaint)
        }

        val isArea = chart.type == OfficeChartType.AREA
        val isScatter = chart.type == OfficeChartType.SCATTER

        chart.series.forEachIndexed { sIdx, series ->
            val color = seriesColor(sIdx, series.colorRgb)
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            }
            val areaPath = Path()
            val linePath = Path()
            val points = series.values.mapIndexed { idx, v ->
                val x = if (catCount <= 1) plot.centerX() else plot.left + idx * (plot.width() / (catCount - 1))
                val y = plot.bottom - ((v.coerceAtLeast(0.0) / maxVal) * plot.height()).toFloat()
                x to y
            }

            points.forEachIndexed { idx, (x, y) ->
                if (idx == 0) {
                    linePath.moveTo(x, y)
                    areaPath.moveTo(x, plot.bottom)
                    areaPath.lineTo(x, y)
                } else {
                    linePath.lineTo(x, y)
                    areaPath.lineTo(x, y)
                }
            }
            if (isArea && points.isNotEmpty()) {
                areaPath.lineTo(points.last().first, plot.bottom)
                areaPath.close()
                val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.argb(64, Color.red(color), Color.green(color), Color.blue(color))
                    style = Paint.Style.FILL
                }
                canvas.drawPath(areaPath, fillPaint)
            }
            if (!isScatter && points.size > 1) {
                canvas.drawPath(linePath, strokePaint)
            }
            points.forEach { (x, y) ->
                canvas.drawCircle(x, y, 3f, dotPaint)
            }
        }
    }

    private fun drawPieOrDoughnutChart(
        canvas: Canvas,
        chart: OfficeChart,
        plot: RectF,
        isDoughnut: Boolean
    ) {
        val values = chart.series.firstOrNull()?.values?.map { it.coerceAtLeast(0.0) }.orEmpty()
        val total = values.sum().takeIf { it > 0.0 } ?: return
        val radius = min(plot.width(), plot.height()) * 0.44f
        val oval = RectF(
            plot.centerX() - radius,
            plot.centerY() - radius,
            plot.centerX() + radius,
            plot.centerY() + radius
        )
        val slicePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val sepPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
        }
        val pctPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 8f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        var startAngle = -90f
        values.forEachIndexed { idx, v ->
            val sweep = ((v / total) * 360.0).toFloat()
            if (sweep > 0.5f) {
                slicePaint.color = seriesColor(idx, null)
                canvas.drawArc(oval, startAngle, sweep, true, slicePaint)
                canvas.drawArc(oval, startAngle, sweep, true, sepPaint)
                if (sweep >= 24f) {
                    val midRad = Math.toRadians((startAngle + sweep / 2f).toDouble())
                    val labelR = radius * (if (isDoughnut) 0.76f else 0.62f)
                    val lx = (plot.centerX() + labelR * cos(midRad)).toFloat()
                    val ly = (plot.centerY() + labelR * sin(midRad)).toFloat() + 3f
                    val pct = ((v / total) * 100.0).toInt()
                    canvas.drawText("$pct%", lx, ly, pctPaint)
                }
                startAngle += sweep
            }
        }

        if (isDoughnut) {
            val holePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
            canvas.drawCircle(plot.centerX(), plot.centerY(), radius * 0.5f, holePaint)
        }
    }

    private fun drawHorizontalGrid(canvas: Canvas, plot: RectF, maxVal: Double) {
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = GridLineColor
            strokeWidth = 0.8f
        }
        val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AxisColor
            strokeWidth = 1f
        }
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = LabelColor
            textSize = 7.5f
            textAlign = Paint.Align.RIGHT
        }

        val steps = 4
        for (i in 0..steps) {
            val frac = i.toFloat() / steps
            val y = plot.bottom - frac * plot.height()
            canvas.drawLine(plot.left, y, plot.right, y, if (i == 0) axisPaint else gridPaint)
            val tickVal = (maxVal * frac)
            val tickLabel = if (maxVal >= 10) tickVal.toInt().toString() else String.format("%.1f", tickVal)
            canvas.drawText(tickLabel, plot.left - 5f, y + 2.5f, tickPaint)
        }
        canvas.drawLine(plot.left, plot.top, plot.left, plot.bottom, axisPaint)
    }

    private fun drawCenteredLegend(
        canvas: Canvas,
        items: List<Pair<String, Int>>,
        left: Float,
        right: Float,
        baselineY: Float,
        textPaint: TextPaint
    ) {
        val swatchPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        val itemWidths = items.map { (name, _) -> 12f + textPaint.measureText(name) + 10f }
        val totalW = min(itemWidths.sum(), right - left)
        var x = left + ((right - left - totalW) / 2f).coerceAtLeast(0f)

        items.forEachIndexed { idx, (name, color) ->
            val w = itemWidths[idx]
            if (x + w > right + 4f) return@forEachIndexed
            swatchPaint.color = color
            canvas.drawRoundRect(RectF(x, baselineY - 7f, x + 7f, baselineY), 1.5f, 1.5f, swatchPaint)
            canvas.drawText(name, x + 10f, baselineY, textPaint)
            x += w
        }
    }

    private fun seriesColor(index: Int, customRgb: Int?): Int {
        if (customRgb != null) {
            return Color.rgb(
                (customRgb shr 16) and 0xFF,
                (customRgb shr 8) and 0xFF,
                customRgb and 0xFF
            )
        }
        return OfficePalette[index % OfficePalette.size]
    }

    private fun toAndroidAlignment(alignment: DocAlignment): Layout.Alignment = when (alignment) {
        DocAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
        DocAlignment.RIGHT -> Layout.Alignment.ALIGN_OPPOSITE
        DocAlignment.LEFT, DocAlignment.JUSTIFY -> Layout.Alignment.ALIGN_NORMAL
    }

    private fun decodeScaledBitmap(bytes: ByteArray, maxDimPx: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val srcW = bounds.outWidth
            val srcH = bounds.outHeight
            if (srcW <= 0 || srcH <= 0) return null

            var sample = 1
            while ((srcW / sample) > maxDimPx || (srcH / sample) > maxDimPx) {
                sample *= 2
            }
            val opts = BitmapFactory.Options().apply {
                inSampleSize = sample
                inScaled = false
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
        }.getOrNull()
    }
}
