package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DocAlignment
import com.example.data.ExcelWorkbook
import com.example.data.OfficeChart
import com.example.data.OfficeChartType
import com.example.data.OfficePageLayout
import com.example.data.SlidePage
import com.example.data.WordBlock
import com.example.data.WordPage
import com.example.data.WordTableRow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val ChartPalette = listOf(
    Color(0xFF2563EB),
    Color(0xFFEA580C),
    Color(0xFF16A34A),
    Color(0xFF9333EA),
    Color(0xFFDC2626),
    Color(0xFF0891B2)
)

private val PaperInkPrimary = Color(0xFF0F172A)
private val PaperInkMuted = Color(0xFF64748B)
private val PaperBorder = Color(0xFFCBD5E1)

private fun rgbIntToColor(rgb: Int): Color = Color(rgb or 0xFF000000.toInt())

private fun isDarkRgb(rgb: Int): Boolean {
    val r = (rgb shr 16) and 0xFF
    val g = (rgb shr 8) and 0xFF
    val b = rgb and 0xFF
    return (0.299f * r + 0.587f * g + 0.114f * b) < 128f
}

private fun decodeScaledBitmap(bytes: ByteArray, maxDim: Int = 4096): Bitmap? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > maxDim || bounds.outHeight / sample > maxDim) {
        sample *= 2
    }
    BitmapFactory.Options().apply {
        inSampleSize = sample
        inScaled = false
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }.let { opts ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }
}.getOrNull()

private fun excelColumnName(colIndex: Int): String {
    var n = colIndex
    val sb = StringBuilder()
    while (n >= 0) {
        sb.append(('A'.code + (n % 26)).toChar())
        n = (n / 26) - 1
    }
    return sb.reverse().toString()
}

@Composable
fun OfficeOriginalWordPaperCard(
    page: WordPage,
    layout: OfficePageLayout,
    fontSizeSp: Float
) {
    val padLeft = (layout.marginLeftPt * 0.36f).coerceIn(12f, 36f).dp
    val padRight = (layout.marginRightPt * 0.36f).coerceIn(12f, 36f).dp
    val padTop = (layout.marginTopPt * 0.34f).coerceIn(14f, 34f).dp
    val padBottom = (layout.marginBottomPt * 0.30f).coerceIn(12f, 30f).dp
    val maxWidthDp = if (layout.isLandscape) 860.dp else 700.dp
    val aspect = (layout.widthPt / layout.heightPt.coerceAtLeast(1f)).coerceIn(0.55f, 1.9f)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxWidthDp),
        contentAlignment = Alignment.TopCenter
    ) {
        val minPageHeight = (minOf(maxWidth, maxWidthDp) / aspect).coerceIn(190.dp, 980.dp)
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = Color.White,
            border = BorderStroke(1.dp, PaperBorder),
            shadowElevation = 3.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .defaultMinSize(minHeight = minPageHeight)
                .testTag("word_original_page_${page.pageNumber}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = padLeft, end = padRight, top = padTop, bottom = padBottom),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Page ${page.pageNumber}",
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperInkMuted
                )
            }
            HorizontalDivider(color = Color(0xFFE2E8F0))

            page.blocks.forEach { block ->
                WordRichBlockContent(
                    block = block,
                    baseFontSizeSp = fontSizeSp,
                    defaultInkColor = PaperInkPrimary,
                    accentColor = Color(0xFF2563EB),
                    borderColor = PaperBorder
                )
            }
        }
        }
    }
}

@Composable
fun OfficeOriginalSlideCard(
    slide: SlidePage,
    totalSlides: Int,
    layout: OfficePageLayout,
    accentColor: Color,
    showNotes: Boolean = true
) {
    val slideBg = slide.backgroundRgb?.let(::rgbIntToColor) ?: Color.White
    val isDarkBg = slide.backgroundRgb?.let(::isDarkRgb) ?: false
    val primaryInk = if (isDarkBg) Color.White else PaperInkPrimary
    val mutedInk = if (isDarkBg) Color(0xFFCBD5E1) else PaperInkMuted
    val aspect = (layout.widthPt / layout.heightPt.coerceAtLeast(1f)).coerceIn(0.65f, 1.95f)
    val padH = (layout.marginLeftPt * 0.34f).coerceIn(14f, 34f).dp
    val padV = (layout.marginTopPt * 0.34f).coerceIn(14f, 30f).dp

    val maxSlideWidthDp = if (layout.isLandscape) 880.dp else 680.dp

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = maxSlideWidthDp)
            .testTag("slide_original_card_${slide.slideNumber}"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val minSlideHeight = (minOf(maxWidth, maxSlideWidthDp) / aspect).coerceIn(190.dp, 880.dp)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = slideBg,
                border = BorderStroke(1.dp, PaperBorder),
                shadowElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .defaultMinSize(minHeight = minSlideHeight)
            ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(accentColor)
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = padH, vertical = padV),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "SLIDE ${slide.slideNumber} OF $totalSlides",
                        style = MaterialTheme.typography.labelSmall,
                        color = accentColor,
                        fontWeight = FontWeight.Bold
                    )

                    if (slide.blocks.isNotEmpty()) {
                        slide.blocks.forEachIndexed { idx, block ->
                            val effectiveBlock = if (idx == 0 && !block.isHeading && block.image == null && block.chart == null && block.tableRows.isEmpty()) {
                                block.copy(isHeading = true, headingLevel = 1)
                            } else {
                                block
                            }
                            WordRichBlockContent(
                                block = effectiveBlock,
                                baseFontSizeSp = 15f,
                                defaultInkColor = primaryInk,
                                accentColor = accentColor,
                                borderColor = if (isDarkBg) Color(0xFF475569) else PaperBorder
                            )
                            if (idx == 0 && slide.blocks.size > 1) {
                                HorizontalDivider(color = if (isDarkBg) Color(0xFF334155) else Color(0xFFE2E8F0))
                            }
                        }
                    } else {
                        Text(
                            text = slide.title,
                            style = MaterialTheme.typography.titleLarge,
                            color = primaryInk
                        )
                        if (slide.bodyLines.isNotEmpty()) {
                            HorizontalDivider(color = if (isDarkBg) Color(0xFF334155) else Color(0xFFE2E8F0))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                slide.bodyLines.forEach { line ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(text = "•", color = accentColor, fontWeight = FontWeight.Bold)
                                        Text(
                                            text = line,
                                            style = MaterialTheme.typography.bodyLarge,
                                            color = primaryInk,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        if (showNotes && slide.notes.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "Speaker Notes",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = slide.notes,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun WordRichBlockContent(
    block: WordBlock,
    baseFontSizeSp: Float,
    defaultInkColor: Color,
    accentColor: Color,
    borderColor: Color
) {
    if (block.image != null) {
        val img = block.image
        val bitmap = remember(img) { decodeScaledBitmap(img.bytes) }
        if (bitmap != null) {
            val align = when (img.alignment) {
                DocAlignment.LEFT, DocAlignment.JUSTIFY -> Alignment.Start
                DocAlignment.RIGHT -> Alignment.End
                DocAlignment.CENTER -> Alignment.CenterHorizontally
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalAlignment = align
            ) {
                val aspect = bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Embedded document image",
                    contentScale = ContentScale.Fit,
                    filterQuality = FilterQuality.High,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .heightIn(max = 280.dp)
                        .aspectRatio(aspect.coerceIn(0.35f, 3f), matchHeightConstraintsFirst = false)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
        }
        return
    }

    if (block.chart != null) {
        OfficeChartCard(
            chart = block.chart,
            inkColor = defaultInkColor,
            borderColor = borderColor
        )
        return
    }

    if (block.tableRows.isNotEmpty()) {
        WordTableContent(
            rows = block.tableRows,
            hasBorders = block.tableHasBorders,
            baseFontSizeSp = baseFontSizeSp,
            defaultInkColor = defaultInkColor,
            borderColor = borderColor
        )
        return
    }

    val effectiveSizeSp = when {
        block.fontSizePt != null -> (block.fontSizePt * (baseFontSizeSp / 15f)).coerceIn(10f, 34f)
        block.isHeading && block.headingLevel == 1 -> (baseFontSizeSp + 6f)
        block.isHeading && block.headingLevel == 2 -> (baseFontSizeSp + 3f)
        block.isHeading -> (baseFontSizeSp + 1.5f)
        else -> baseFontSizeSp
    }
    val textAlign = when (block.alignment) {
        DocAlignment.LEFT -> TextAlign.Start
        DocAlignment.CENTER -> TextAlign.Center
        DocAlignment.RIGHT -> TextAlign.End
        DocAlignment.JUSTIFY -> TextAlign.Justify
    }
    val indentDp = (block.leftIndentPt * 0.35f).coerceIn(0f, 48f).dp
    val lineMult = block.lineSpacingMult?.coerceIn(1.05f, 2.0f) ?: 1.4f

    val annotated = remember(block, effectiveSizeSp, defaultInkColor) {
        buildAnnotatedString {
            if (block.isBullet) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(block.bulletPrefix.ifEmpty { "•  " })
                }
            }
            if (block.runs.isNotEmpty()) {
                block.runs.forEach { run ->
                    val runSize = run.fontSizePt?.let { (it * (baseFontSizeSp / 15f)).coerceIn(10f, 34f) }
                    val runColor = run.colorRgb?.let { rgb ->
                        val raw = rgbIntToColor(rgb)
                        if (isDarkRgb(rgb) && defaultInkColor == Color.White) Color.White else raw
                    } ?: defaultInkColor
                    val decor = when {
                        run.isUnderline && run.isStrike -> TextDecoration.Underline + TextDecoration.LineThrough
                        run.isUnderline -> TextDecoration.Underline
                        run.isStrike -> TextDecoration.LineThrough
                        else -> TextDecoration.None
                    }
                    withStyle(
                        SpanStyle(
                            color = runColor,
                            fontSize = (runSize ?: effectiveSizeSp).sp,
                            fontWeight = if (run.isBold || block.isHeading || block.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (run.isItalic || block.isItalic) FontStyle.Italic else FontStyle.Normal,
                            textDecoration = decor
                        )
                    ) {
                        append(run.text)
                    }
                }
            } else {
                append(block.text)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = indentDp)
    ) {
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyLarge.copy(
                fontSize = effectiveSizeSp.sp,
                lineHeight = (effectiveSizeSp * lineMult).sp,
                fontWeight = if (block.isHeading || block.isBold) FontWeight.Bold else FontWeight.Normal,
                fontStyle = if (block.isItalic) FontStyle.Italic else FontStyle.Normal
            ),
            color = defaultInkColor,
            textAlign = textAlign,
            modifier = Modifier.fillMaxWidth()
        )
        if (block.hasBottomBorder) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = accentColor.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun WordTableContent(
    rows: List<WordTableRow>,
    hasBorders: Boolean,
    baseFontSizeSp: Float,
    defaultInkColor: Color,
    borderColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasBorders) Modifier.border(1.dp, borderColor, RoundedCornerShape(6.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(6.dp))
    ) {
        rows.forEachIndexed { rIdx, row ->
            if (rIdx > 0 && hasBorders) {
                HorizontalDivider(color = borderColor, thickness = 0.75.dp)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (row.isHeader) Modifier.background(Color(0xFFF1F5F9))
                        else Modifier
                    )
            ) {
                row.cells.forEachIndexed { cIdx, cell ->
                    val cellBg = cell.backgroundRgb?.let(::rgbIntToColor)
                    val cellInk = when {
                        cell.backgroundRgb != null -> if (isDarkRgb(cell.backgroundRgb)) Color.White else PaperInkPrimary
                        row.isHeader -> PaperInkPrimary
                        else -> defaultInkColor
                    }
                    Box(
                        modifier = Modifier
                            .weight(cell.colSpan.coerceAtLeast(1).toFloat())
                            .clipToBounds()
                            .then(if (cellBg != null) Modifier.background(cellBg) else Modifier)
                            .then(
                                if (hasBorders && cIdx > 0) Modifier.border(0.5.dp, borderColor)
                                else Modifier
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            cell.paragraphs.forEach { para ->
                                val paraAdjusted = if (row.isHeader && !para.isBold) para.copy(isBold = true) else para
                                WordRichBlockContent(
                                    block = paraAdjusted,
                                    baseFontSizeSp = (baseFontSizeSp - 1.5f).coerceAtLeast(10f),
                                    defaultInkColor = cellInk,
                                    accentColor = Color(0xFF2563EB),
                                    borderColor = borderColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OfficeChartCard(
    chart: OfficeChart,
    inkColor: Color,
    borderColor: Color
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFFF8FAFC),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = chart.title.ifBlank { "Chart" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                color = PaperInkPrimary
            )

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val w = size.width
                val h = size.height
                val seriesList = chart.series
                if (seriesList.isEmpty()) return@Canvas

                if (chart.type == OfficeChartType.PIE || chart.type == OfficeChartType.DOUGHNUT) {
                    val values = seriesList.first().values.map { it.coerceAtLeast(0.0) }
                    val sum = values.sum().takeIf { it > 0.0 } ?: 1.0
                    val diameter = (minOf(w, h) * 0.82f)
                    val topLeft = Offset((w - diameter) / 2f, (h - diameter) / 2f)
                    var startAngle = -90f
                    values.forEachIndexed { i, v ->
                        val sweep = ((v / sum) * 360.0).toFloat()
                        drawArc(
                            color = ChartPalette[i % ChartPalette.size],
                            startAngle = startAngle,
                            sweepAngle = sweep,
                            useCenter = true,
                            topLeft = topLeft,
                            size = Size(diameter, diameter)
                        )
                        startAngle += sweep
                    }
                    if (chart.type == OfficeChartType.DOUGHNUT) {
                        drawCircle(
                            color = Color(0xFFF8FAFC),
                            radius = diameter * 0.26f,
                            center = Offset(w / 2f, h / 2f)
                        )
                    }
                    return@Canvas
                }

                val padL = 22f
                val padB = 18f
                val padT = 10f
                val padR = 12f
                val plotW = (w - padL - padR).coerceAtLeast(40f)
                val plotH = (h - padT - padB).coerceAtLeast(40f)
                val maxVal = seriesList.flatMap { it.values }.maxOrNull()?.coerceAtLeast(1.0) ?: 1.0
                val catCount = maxOf(chart.categories.size, seriesList.maxOfOrNull { it.values.size } ?: 1, 1)

                // Gridlines
                val axisColor = Color(0xFFCBD5E1)
                for (step in 0..3) {
                    val y = padT + (plotH * step / 3f)
                    drawLine(
                        color = axisColor,
                        start = Offset(padL, y),
                        end = Offset(padL + plotW, y),
                        strokeWidth = 1f
                    )
                }

                if (chart.type == OfficeChartType.BAR) {
                    val groupH = plotH / catCount
                    val barH = (groupH / seriesList.size.coerceAtLeast(1) * 0.7f).coerceAtLeast(4f)
                    for (cIdx in 0 until catCount) {
                        seriesList.forEachIndexed { sIdx, ser ->
                            val v = ser.values.getOrNull(cIdx) ?: 0.0
                            val barW = ((v / maxVal) * plotW).toFloat().coerceAtLeast(0f)
                            val y = padT + cIdx * groupH + sIdx * barH + groupH * 0.15f
                            val color = ser.colorRgb?.let(::rgbIntToColor) ?: ChartPalette[sIdx % ChartPalette.size]
                            drawRect(color = color, topLeft = Offset(padL, y), size = Size(barW, barH))
                        }
                    }
                } else if (chart.type == OfficeChartType.LINE || chart.type == OfficeChartType.AREA || chart.type == OfficeChartType.SCATTER) {
                    seriesList.forEachIndexed { sIdx, ser ->
                        val color = ser.colorRgb?.let(::rgbIntToColor) ?: ChartPalette[sIdx % ChartPalette.size]
                        val pts = ser.values.mapIndexed { cIdx, v ->
                            val x = if (catCount <= 1) padL + plotW / 2f else padL + (cIdx.toFloat() / (catCount - 1)) * plotW
                            val y = padT + plotH - ((v / maxVal) * plotH).toFloat()
                            Offset(x, y)
                        }
                        if (chart.type == OfficeChartType.AREA && pts.size >= 2) {
                            val areaPath = Path().apply {
                                moveTo(pts.first().x, padT + plotH)
                                pts.forEach { lineTo(it.x, it.y) }
                                lineTo(pts.last().x, padT + plotH)
                                close()
                            }
                            drawPath(areaPath, color = color.copy(alpha = 0.25f))
                        }
                        if (chart.type != OfficeChartType.SCATTER) {
                            for (i in 0 until pts.lastIndex) {
                                drawLine(color = color, start = pts[i], end = pts[i + 1], strokeWidth = 4f)
                            }
                        }
                        pts.forEach { p ->
                            drawCircle(color = color, radius = 5.5f, center = p)
                        }
                    }
                } else {
                    val groupW = plotW / catCount
                    val barW = (groupW / seriesList.size.coerceAtLeast(1) * 0.68f).coerceAtLeast(4f)
                    for (cIdx in 0 until catCount) {
                        seriesList.forEachIndexed { sIdx, ser ->
                            val v = ser.values.getOrNull(cIdx) ?: 0.0
                            val barH = ((v / maxVal) * plotH).toFloat().coerceAtLeast(0f)
                            val x = padL + cIdx * groupW + sIdx * barW + groupW * 0.16f
                            val y = padT + plotH - barH
                            val color = ser.colorRgb?.let(::rgbIntToColor) ?: ChartPalette[sIdx % ChartPalette.size]
                            drawRect(color = color, topLeft = Offset(x, y), size = Size(barW, barH))
                        }
                    }
                }
            }

            if (chart.categories.isNotEmpty()) {
                Text(
                    text = chart.categories.take(8).joinToString("  •  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = PaperInkMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                chart.series.take(4).forEachIndexed { idx, ser ->
                    val swatch = ser.colorRgb?.let(::rgbIntToColor) ?: ChartPalette[idx % ChartPalette.size]
                    Row(
                        modifier = Modifier.weight(1f, fill = false),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(swatch)
                        )
                        Text(
                            text = ser.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = inkColor.takeIf { it != Color.White } ?: PaperInkPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OfficeOriginalExcelContent(
    workbook: ExcelWorkbook,
    layout: OfficePageLayout,
    showGridlines: Boolean,
    fontSizeSp: Float,
    showControls: Boolean,
    topClearance: Dp,
    bottomClearance: Dp,
    zoomState: ReaderZoomState,
    onZoomInteraction: () -> Unit,
    onToggleUi: () -> Unit
) {
    var selectedSheetIdx by remember(workbook) { mutableIntStateOf(0) }
    val safeIndex = selectedSheetIdx.coerceIn(0, workbook.sheets.lastIndex.coerceAtLeast(0))
    val sheet = workbook.sheets.getOrNull(safeIndex) ?: return

    val hScroll = rememberScrollState()
    val listState = rememberLazyListState()
    val totalCols = sheet.maxColumns.coerceAtLeast(1)
    val rawColWidths = remember(sheet) {
        (0 until totalCols).map { idx ->
            val chars = sheet.columnWidthsChars[idx] ?: 11f
            (chars * 8.0f).coerceIn(48f, 180f)
        }
    }
    val cellFont = (fontSizeSp - 2f).coerceIn(10f, 18f)
    val rowHeaderWidth = 38.dp
    val maxSheetWidthDp = if (layout.isLandscape) 940.dp else 660.dp
    val sheetAspect = (layout.widthPt / layout.heightPt.coerceAtLeast(1f)).coerceIn(0.60f, 1.85f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        ZoomableReaderViewport(
            zoomState = zoomState,
            onSingleTap = onToggleUi,
            onUserInteraction = onZoomInteraction,
            onVerticalOverflowPan = { overflowY ->
                val safeScale = zoomState.scale.coerceAtLeast(1f)
                val consumedChildDelta = listState.dispatchRawDelta(-overflowY / safeScale)
                -consumedChildDelta * safeScale
            },
            onHorizontalOverflowPan = { overflowX ->
                val safeScale = zoomState.scale.coerceAtLeast(1f)
                val consumedChildDelta = hScroll.dispatchRawDelta(-overflowX / safeScale)
                -consumedChildDelta * safeScale
            },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                userScrollEnabled = zoomState.canInnerScroll,
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
                contentPadding = PaddingValues(
                    top = topClearance + 8.dp,
                    bottom = bottomClearance + 68.dp,
                    start = 10.dp,
                    end = 10.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (workbook.sheets.size > 1) {
                    item(key = "sheet_tabs") {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = maxSheetWidthDp)
                        ) {
                            itemsIndexed(workbook.sheets) { idx, item ->
                                FilterChip(
                                    selected = idx == safeIndex,
                                    onClick = {
                                        selectedSheetIdx = idx
                                        onZoomInteraction()
                                    },
                                    label = { Text(item.name) },
                                    modifier = Modifier.testTag("excel_sheet_tab_$idx")
                                )
                            }
                        }
                    }
                }

                item(key = "sheet_meta") {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxSheetWidthDp)
                    ) {
                        Text(
                            text = "${sheet.name} • ${layout.detectedPaperName} • ${sheet.rows.size} rows × $totalCols cols",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                item(key = "excel_grid") {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = maxSheetWidthDp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val cardMaxWidth = minOf(maxWidth, maxSheetWidthDp)
                        val minSheetHeight = (cardMaxWidth / sheetAspect).coerceIn(220.dp, 780.dp)
                        val rowHdrW = if (showGridlines) rowHeaderWidth else 0.dp
                        val availTableW = (cardMaxWidth - rowHdrW - 2.dp).coerceAtLeast(120.dp)
                        val minReadableColDp = 34f
                        val canFitAllColumns = (availTableW.value / totalCols) >= minReadableColDp
                        val fittedColWidths: List<Dp> = remember(rawColWidths, availTableW, canFitAllColumns) {
                            val totalRaw = rawColWidths.sum().coerceAtLeast(1f)
                            if (canFitAllColumns) {
                                val scaled = if (totalRaw <= availTableW.value) {
                                    val expand = (availTableW.value / totalRaw).coerceIn(1f, 1.4f)
                                    rawColWidths.map { it * expand }
                                } else {
                                    val shrink = availTableW.value / totalRaw
                                    rawColWidths.map { (it * shrink).coerceAtLeast(minReadableColDp) }
                                }
                                val sumScaled = scaled.sum().coerceAtLeast(1f)
                                if (sumScaled > availTableW.value) {
                                    val norm = availTableW.value / sumScaled
                                    scaled.map { (it * norm).dp }
                                } else {
                                    scaled.map { it.dp }
                                }
                            } else {
                                rawColWidths.map { it.coerceAtLeast(minReadableColDp).dp }
                            }
                        }
                        val cellHorizontalPad = if (canFitAllColumns && totalCols >= 5) 4.dp else 7.dp

                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = Color.White,
                            border = BorderStroke(1.dp, PaperBorder),
                            shadowElevation = 2.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .defaultMinSize(minHeight = minSheetHeight)
                                .testTag("excel_original_grid")
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (canFitAllColumns) {
                                            Modifier
                                        } else {
                                            Modifier.horizontalScroll(hScroll, enabled = zoomState.canInnerScroll)
                                        }
                                    )
                            ) {
                                if (showGridlines) {
                                    Row(
                                        modifier = Modifier.background(Color(0xFFF1F5F9))
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(rowHeaderWidth)
                                                .height(30.dp)
                                                .border(0.5.dp, PaperBorder),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text("#", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = PaperInkMuted)
                                        }
                                        for (c in 0 until totalCols) {
                                            Box(
                                                modifier = Modifier
                                                    .width(fittedColWidths[c])
                                                    .height(30.dp)
                                                    .border(0.5.dp, PaperBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = excelColumnName(c),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = PaperInkMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Clip
                                                )
                                            }
                                        }
                                    }
                                }

                                sheet.rows.forEachIndexed { rIdx, row ->
                                    val cellsByCol = remember(row) { row.cells.associateBy { it.colIndex } }
                                    Row(
                                        modifier = Modifier.background(
                                            when {
                                                row.isHeaderLikely -> Color(0xFFECFDF5)
                                                rIdx % 2 == 1 -> Color(0xFFF8FAFC)
                                                else -> Color.White
                                            }
                                        )
                                    ) {
                                        if (showGridlines) {
                                            Box(
                                                modifier = Modifier
                                                    .width(rowHeaderWidth)
                                                    .defaultMinSize(minHeight = 32.dp)
                                                    .background(Color(0xFFF8FAFC))
                                                    .border(0.5.dp, PaperBorder)
                                                    .padding(horizontal = 3.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = row.rowNumber.toString(),
                                                    fontSize = 11.sp,
                                                    color = PaperInkMuted,
                                                    maxLines = 1
                                                )
                                            }
                                        }

                                        var col = 0
                                        while (col < totalCols) {
                                            val cell = cellsByCol[col]
                                            val span = (cell?.colSpan ?: 1).coerceIn(1, totalCols - col)
                                            var widthDp = 0.dp
                                            for (s in 0 until span) {
                                                widthDp += fittedColWidths[col + s]
                                            }
                                            val bg = cell?.backgroundRgb?.let(::rgbIntToColor)
                                            val ink = when {
                                                cell?.textColorRgb != null -> rgbIntToColor(cell.textColorRgb)
                                                cell?.backgroundRgb != null && isDarkRgb(cell.backgroundRgb) -> Color.White
                                                else -> PaperInkPrimary
                                            }
                                            val align = when (cell?.alignment) {
                                                DocAlignment.CENTER -> TextAlign.Center
                                                DocAlignment.RIGHT -> TextAlign.End
                                                else -> if (cell?.isNumericOrDate == true) TextAlign.End else TextAlign.Start
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .width(widthDp)
                                                    .defaultMinSize(minHeight = 32.dp)
                                                    .clipToBounds()
                                                    .then(if (bg != null) Modifier.background(bg) else Modifier)
                                                    .then(if (showGridlines) Modifier.border(0.5.dp, PaperBorder) else Modifier)
                                                    .padding(horizontal = cellHorizontalPad, vertical = 6.dp),
                                                contentAlignment = Alignment.CenterStart
                                            ) {
                                                if (cell != null && cell.text.isNotEmpty()) {
                                                    Text(
                                                        text = cell.text,
                                                        fontSize = cellFont.sp,
                                                        fontWeight = if (cell.isBold || row.isHeaderLikely) FontWeight.Bold else FontWeight.Normal,
                                                        fontStyle = if (cell.isItalic) FontStyle.Italic else FontStyle.Normal,
                                                        color = ink,
                                                        textAlign = align,
                                                        softWrap = true,
                                                        maxLines = 4,
                                                        overflow = TextOverflow.Ellipsis,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                            col += span
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (sheet.embeddedBlocks.isNotEmpty()) {
                    item(key = "excel_embedded_header") {
                        Text(
                            text = "Charts & Images (${sheet.embeddedBlocks.size})",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = maxSheetWidthDp)
                                .padding(top = 6.dp)
                        )
                    }
                    itemsIndexed(sheet.embeddedBlocks) { _, block ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .widthIn(max = maxSheetWidthDp)
                        ) {
                            WordRichBlockContent(
                                block = block,
                                baseFontSizeSp = fontSizeSp,
                                defaultInkColor = PaperInkPrimary,
                                accentColor = Color(0xFF16A34A),
                                borderColor = PaperBorder
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it / 2 }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 14.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.88f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.testTag("viewer_page_pill")
                ) {
                    Text(
                        text = "Sheet ${safeIndex + 1} / ${workbook.sheets.size}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
                    )
                }
                ReaderZoomControlsPill(
                    zoomState = zoomState,
                    onUserInteraction = onZoomInteraction
                )
            }
        }
    }
}


