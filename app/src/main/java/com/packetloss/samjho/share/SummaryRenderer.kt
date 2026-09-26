package com.packetloss.samjho.share

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Draws a [SummaryDocument] with the phone's own text engine (so Hindi shapes correctly), either as one
 * tall image or as paginated A4 PDF pages. The layout is done once in logical units and scaled, so the
 * image and the PDF look the same.
 */
class SummaryRenderer(private val doc: SummaryDocument) {

    private class Block(val height: Int, val gapAfter: Int, val draw: (Canvas) -> Unit)

    // ---------------------------------------------------------------- outputs

    /** One tall image, [widthPx] wide, as wide as a phone screen is comfortable to read in a chat. */
    fun renderImage(widthPx: Int = 1080): Bitmap {
        val s = widthPx / LOGICAL_WIDTH
        val margin = (MARGIN * s).toInt()
        val blocks = layout(widthPx - 2 * margin, s)
        val total = margin + blocks.sumOf { it.height + it.gapAfter } + margin
        val bitmap = Bitmap.createBitmap(widthPx, total, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        var y = margin
        for (b in blocks) {
            canvas.save()
            canvas.translate(margin.toFloat(), y.toFloat())
            b.draw(canvas)
            canvas.restore()
            y += b.height + b.gapAfter
        }
        return bitmap
    }

    /** A4 pages at 72 dpi. A block that does not fit the rest of a page starts the next one. */
    fun writePdf(file: File) {
        val pageW = 595
        val pageH = 842
        val s = pageW / LOGICAL_WIDTH
        val margin = (MARGIN * s * 1.4f).toInt()
        val blocks = layout(pageW - 2 * margin, s)
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
        var canvas = page.canvas
        var y = margin
        for (b in blocks) {
            if (y + b.height > pageH - margin && y > margin) {
                pdf.finishPage(page)
                pageNumber++
                page = pdf.startPage(PdfDocument.PageInfo.Builder(pageW, pageH, pageNumber).create())
                canvas = page.canvas
                y = margin
            }
            canvas.save()
            canvas.translate(margin.toFloat(), y.toFloat())
            b.draw(canvas)
            canvas.restore()
            y += b.height + b.gapAfter
        }
        pdf.finishPage(page)
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
    }

    // ---------------------------------------------------------------- layout

    private fun layout(width: Int, s: Float): List<Block> = buildList {
        add(titleBlock(width, s))
        for (section in doc.sections) {
            add(sectionHeader(section, width, s))
            val plain = section.entries.all { it.details.isEmpty() && it.note == null }
            if (plain) add(bulletCard(section, width, s))
            else section.entries.forEach { add(entryCard(section.tone, it, width, s)) }
        }
        add(footerBlock(width, s))
    }

    private fun paint(size: Float, s: Float, color: Int, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size * s
        this.color = color
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun textLayout(text: CharSequence, paint: TextPaint, width: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.12f)
            .setIncludePad(false)
            .build()

    private fun titleBlock(width: Int, s: Float): Block {
        val title = textLayout(doc.title, paint(26f, s, PRIMARY, bold = true), width)
        val date = textLayout(doc.dateLine, paint(13f, s, MUTED), width)
        val gap = (4 * s).toInt()
        return Block(title.height + gap + date.height, (18 * s).toInt()) { c ->
            title.draw(c)
            c.save(); c.translate(0f, (title.height + gap).toFloat()); date.draw(c); c.restore()
        }
    }

    private fun sectionHeader(section: Section, width: Int, s: Float): Block {
        val color = accent(section.tone)
        val text = textLayout(section.title.uppercase(), paint(13f, s, color, bold = true), width - (12 * s).toInt())
        val bar = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
        return Block(text.height, (7 * s).toInt()) { c ->
            c.drawRoundRect(RectF(0f, 0f, 4 * s, text.height.toFloat()), 2 * s, 2 * s, bar)
            c.save(); c.translate(12 * s, 0f); text.draw(c); c.restore()
        }
    }

    private fun card(tone: Tone, s: Float): Pair<Paint, Paint> {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = tint(tone) }
        val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = border(tone); style = Paint.Style.STROKE; strokeWidth = 1.2f * s
        }
        return fill to stroke
    }

    private fun bulletCard(section: Section, width: Int, s: Float): Block {
        val pad = (14 * s).toInt()
        val (fill, stroke) = card(section.tone, s)
        val inner = width - 2 * pad
        val layouts = section.entries.map { textLayout("•  ${it.headline}", paint(18f, s, ink(section.tone)), inner) }
        val gap = (8 * s).toInt()
        val height = pad + layouts.sumOf { it.height } + gap * (layouts.size - 1) + pad
        return Block(height, (16 * s).toInt()) { c ->
            val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(r, 14 * s, 14 * s, fill); c.drawRoundRect(r, 14 * s, 14 * s, stroke)
            var y = pad
            for (l in layouts) { c.save(); c.translate(pad.toFloat(), y.toFloat()); l.draw(c); c.restore(); y += l.height + gap }
        }
    }

    private fun entryCard(tone: Tone, e: Entry, width: Int, s: Float): Block {
        val pad = (14 * s).toInt()
        val inner = width - 2 * pad
        val (fill, stroke) = card(tone, s)
        val headline = textLayout(e.headline, paint(20f, s, ink(tone), bold = true), inner)
        val chipPaint = paint(14f, s, PRIMARY, bold = true)
        val chipPadX = 10 * s
        val chipPadY = 5 * s
        val chipH = chipPaint.fontMetrics.let { it.descent - it.ascent } + 2 * chipPadY
        val chipGap = 8 * s

        // Flow the chips left to right, wrapping when a row is full.
        data class Placed(val text: String, val x: Float, val y: Float, val w: Float)
        val placed = mutableListOf<Placed>()
        var x = 0f
        var y = 0f
        for (d in e.details) {
            val w = chipPaint.measureText(d) + 2 * chipPadX
            if (x > 0f && x + w > inner) { x = 0f; y += chipH + chipGap }
            placed += Placed(d, x, y, w)
            x += w + chipGap
        }
        val chipsHeight = if (placed.isEmpty()) 0 else (y + chipH).toInt()
        val note = e.note?.let { textLayout(it, paint(13.5f, s, MUTED), inner) }

        val g = (8 * s).toInt()
        val height = pad + headline.height +
            (if (chipsHeight > 0) g + chipsHeight else 0) +
            (note?.let { g + it.height } ?: 0) + pad
        val chipFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = CHIP }
        return Block(height, (12 * s).toInt()) { c ->
            val r = RectF(0f, 0f, width.toFloat(), height.toFloat())
            c.drawRoundRect(r, 14 * s, 14 * s, fill); c.drawRoundRect(r, 14 * s, 14 * s, stroke)
            var top = pad
            c.save(); c.translate(pad.toFloat(), top.toFloat()); headline.draw(c); c.restore()
            top += headline.height
            if (chipsHeight > 0) {
                top += g
                for (p in placed) {
                    val rect = RectF(pad + p.x, top + p.y, pad + p.x + p.w, top + p.y + chipH)
                    c.drawRoundRect(rect, chipH / 2, chipH / 2, chipFill)
                    c.drawText(p.text, rect.left + chipPadX, rect.top + chipPadY - chipPaint.fontMetrics.ascent, chipPaint)
                }
                top += chipsHeight
            }
            note?.let { top += g; c.save(); c.translate(pad.toFloat(), top.toFloat()); it.draw(c); c.restore() }
        }
    }

    private fun footerBlock(width: Int, s: Float): Block {
        val text = textLayout(doc.footer, paint(12.5f, s, MUTED), width)
        val rule = Paint().apply { color = 0xFFDCE1E6.toInt(); strokeWidth = 1.5f * s }
        val gap = (10 * s).toInt()
        return Block(gap + text.height, 0) { c ->
            c.drawLine(0f, 0f, width.toFloat(), 0f, rule)
            c.save(); c.translate(0f, gap.toFloat()); text.draw(c); c.restore()
        }
    }

    // ---------------------------------------------------------------- colours

    private fun tint(t: Tone) = when (t) {
        Tone.NEUTRAL -> Color.WHITE
        Tone.AVOID, Tone.NOTICE -> 0xFFFFF4E2.toInt()
        Tone.WARNING -> 0xFFFDECEA.toInt()
    }

    private fun border(t: Tone) = when (t) {
        Tone.NEUTRAL -> 0xFFDCE1E6.toInt()
        Tone.AVOID, Tone.NOTICE -> 0xFFEBCB94.toInt()
        Tone.WARNING -> 0xFFF0B8B3.toInt()
    }

    private fun ink(t: Tone) = when (t) {
        Tone.NEUTRAL -> INK
        Tone.AVOID, Tone.NOTICE -> 0xFF8A5200.toInt()
        Tone.WARNING -> 0xFFB3261E.toInt()
    }

    private fun accent(t: Tone) = when (t) {
        Tone.NEUTRAL -> MUTED
        Tone.AVOID, Tone.NOTICE -> 0xFF8A5200.toInt()
        Tone.WARNING -> 0xFFB3261E.toInt()
    }

    private companion object {
        const val LOGICAL_WIDTH = 540f
        const val MARGIN = 28f
        val INK = 0xFF14181B.toInt()
        val MUTED = 0xFF5A6570.toInt()
        val PRIMARY = 0xFF00695C.toInt()
        val CHIP = 0xFFE3F3F0.toInt()
    }
}
