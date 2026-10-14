package com.splitapp.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.splitapp.model.*
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/** Android-native A4 PDF ledger. No internet, photo, or storage permission required. */
object PdfLedgerWriter {
    private const val WIDTH = 595
    private const val HEIGHT = 842
    private const val LEFT = 38f
    private const val RIGHT = 557f
    private const val TOP = 64f
    private const val BOTTOM = 786f
    private const val GREEN = 0xFF08745D.toInt()
    private const val MUTED = 0xFF58726A.toInt()
    private const val ROW_HEIGHT = 24f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun date(time: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(time))
    private fun money(amount: Long): String =
        Money.format(amount)
    private fun safe(value: String): String = value.replace("\n", " ").replace("\r", " ")

    fun write(output: OutputStream, report: LedgerReport) {
        val pdf = PdfDocument()
        try {
            val printer = Printer(pdf, report)
            printer.start("Ledger summary")
            printer.heading(report.group.name)
            printer.text("Currency: ${report.group.currency}", 11f, bold = true)
            printer.text("Period: ${report.range.title}")
            printer.text("Included participants: ${report.sections.joinToString(", ") { it.member.name }}")
            printer.text("Generated: ${date(report.createdAt)}")
            printer.space(12f)
            printer.heading("Period activity")
            printer.keyValue("Relevant expense total", report.relevantExpensesMinor)
            printer.keyValue("Relevant repayments", report.relevantRepaymentsMinor)
            printer.text("Transactions are counted once when any selected member participates.", 9f)
            printer.space(9f)
            printer.heading("Balances for selected participants")
            report.sections.forEach { section ->
                printer.text(section.member.name, 11f, bold = true)
                printer.keyValue("Opening", section.openingMinor)
                printer.keyValue("Closing", section.closingMinor)
                printer.space(6f)
            }
            printer.text("Positive balance: money to receive. Negative balance: money owed.", 9f)
            printer.text("Closing = opening + paid + repayments sent - shares - repayments received.", 9f)
            report.sections.forEach { section ->
                printer.start("Member ledger")
                printer.heading(section.member.name)
                printer.text("${report.group.name} | ${report.range.title}")
                printer.keyValue("Opening balance", section.openingMinor)
                printer.keyValue("Paid for expenses", section.paidMinor)
                printer.keyValue("Allocated expense shares", section.shareMinor)
                printer.keyValue("Repayments sent", section.sentMinor)
                printer.keyValue("Repayments received", section.receivedMinor)
                printer.keyValue("Closing balance", section.closingMinor)
                printer.space(12f)
                printer.tableHeader()
                if (section.lines.isEmpty()) {
                    printer.text("No ledger movements in the selected date range.", 10f)
                }
                section.lines.forEach { line ->
                    printer.tableRow(date(line.timestamp), line.description,
                        line.inMinor, line.outMinor, line.balanceMinor)
                }
            }
            printer.start("Group activity")
            printer.heading("Transaction activity")
            printer.text("Transactions involving selected participants | ${report.range.title}")
            printer.space(6f)
            if (report.activity.isEmpty()) {
                printer.text("No transactions for the current selection.", 11f)
            } else {
                report.activity.forEach { item ->
                    printer.activityRow(date(item.timestamp), item.title,
                        item.description, item.amountMinor, item.isRepayment)
                }
            }
            printer.finish()
            pdf.writeTo(output)
        } finally {
            pdf.close()
        }
    }

    private class Printer(private val pdf: PdfDocument, private val report: LedgerReport) {
        private var page: PdfDocument.Page? = null
        private var canvas: Canvas? = null
        private var y = TOP
        private var pageNo = 0
        private var title = ""

        fun start(section: String) {
            close()
            title = section
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, pageNo).create())
            canvas = page!!.canvas
            drawHeader()
            y = TOP + 26f
        }

        private fun drawHeader() {
            val c = canvas ?: return
            paint.style = Paint.Style.FILL
            paint.color = GREEN
            c.drawRect(0f, 0f, WIDTH.toFloat(), 10f, paint)
            label("SPLIT", LEFT, 35f, 17f, GREEN, true)
            label(safe(title), RIGHT, 34f, 10f, MUTED, false, true)
            paint.color = 0xFFDBE5E0.toInt()
            c.drawRect(LEFT, 46f, RIGHT, 47f, paint)
        }

        private fun ensure(space: Float) {
            if (y + space > BOTTOM) start(title)
        }

        fun heading(s: String) {
            ensure(34f)
            label(safe(s), LEFT, y + 16f, 15f, GREEN, true)
            y += 34f
        }

        fun text(s: String, size: Float = 10f, bold: Boolean = false) {
            val lines = wrap(safe(s), 495f, size, bold)
            ensure(lines.size * (size + 5f) + 5f)
            lines.forEach {
                label(it, LEFT, y + size, size, if (bold) Color.DKGRAY else MUTED, bold)
                y += size + 5f
            }
            y += 4f
        }

        fun space(height: Float) { y += height }

        fun keyValue(key: String, value: Long) {
            ensure(23f)
            label(key, LEFT, y + 14f, 10f, Color.DKGRAY)
            label("${report.group.currency} ${money(value)}", RIGHT, y + 14f, 10f,
                if (value < 0) 0xFFB34F3D.toInt() else GREEN, true, true)
            y += 23f
        }

        fun tableHeader() {
            ensure(27f)
            val c = canvas ?: return
            paint.color = GREEN
            c.drawRect(LEFT, y, RIGHT, y + ROW_HEIGHT, paint)
            label("Date", LEFT + 5f, y + 16f, 9f, Color.WHITE, true)
            label("Description", 120f, y + 16f, 9f, Color.WHITE, true)
            label("In", 397f, y + 16f, 9f, Color.WHITE, true, true)
            label("Out", 466f, y + 16f, 9f, Color.WHITE, true, true)
            label("Balance", RIGHT - 5f, y + 16f, 9f, Color.WHITE, true, true)
            y += ROW_HEIGHT
        }

        fun tableRow(date: String, description: String, inc: Long, out: Long, balance: Long) {
            ensure(ROW_HEIGHT + 2f)
            if (y == TOP + 26f) tableHeader()
            val c = canvas ?: return
            paint.color = if ((y / ROW_HEIGHT).toInt() % 2 == 0) 0xFFF1F6F3.toInt() else Color.WHITE
            c.drawRect(LEFT, y, RIGHT, y + ROW_HEIGHT, paint)
            label(clip(date, 78f, 8f), LEFT + 5f, y + 16f, 8f, Color.DKGRAY)
            label(clip(description, 189f, 8f), 120f, y + 16f, 8f, Color.DKGRAY)
            label(if (inc == 0L) "-" else money(inc), 397f, y + 16f, 8f, GREEN, false, true)
            label(if (out == 0L) "-" else money(out), 466f, y + 16f, 8f, Color.DKGRAY, false, true)
            label(money(balance), RIGHT - 5f, y + 16f, 8f,
                if (balance < 0) 0xFFB34F3D.toInt() else GREEN, true, true)
            y += ROW_HEIGHT
        }

        fun activityRow(date: String, title: String, description: String, amount: Long, repayment: Boolean) {
            ensure(49f)
            label(clip("$date  ${if (repayment) "REPAYMENT" else "EXPENSE"}  $title", 385f, 10f),
                LEFT, y + 13f, 10f, Color.DKGRAY, true)
            label("${report.group.currency} ${money(amount)}", RIGHT, y + 13f, 10f, GREEN, true, true)
            label(clip(safe(description), 490f, 9f), LEFT, y + 30f, 9f, MUTED)
            paint.color = 0xFFDFE9E4.toInt()
            canvas?.drawRect(LEFT, y + 38f, RIGHT, y + 39f, paint)
            y += 49f
        }

        private fun label(text: String, x: Float, baseline: Float, size: Float, color: Int,
            bold: Boolean = false, right: Boolean = false) {
            val c = canvas ?: return
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) Typeface.create(Typeface.DEFAULT, Typeface.BOLD) else Typeface.DEFAULT
            paint.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(text, x, baseline, paint)
        }

        private fun width(text: String, size: Float, bold: Boolean): Float {
            paint.textSize = size
            paint.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            return paint.measureText(text)
        }

        private fun clip(text: String, max: Float, size: Float): String {
            if (width(text, size, false) <= max) return text
            var end = text.length
            while (end > 0 && width(text.substring(0, end) + "...", size, false) > max) end--
            return text.take(end) + "..."
        }

        private fun wrap(text: String, max: Float, size: Float, bold: Boolean): List<String> {
            val parts = text.split(' ')
            val lines = mutableListOf<String>()
            var line = ""
            for (word in parts) {
                val next = if (line.isEmpty()) word else "$line $word"
                if (width(next, size, bold) > max && line.isNotEmpty()) {
                    lines += clip(line, max, size)
                    line = word
                } else line = next
            }
            lines += clip(line, max, size)
            return lines
        }

        private fun close() {
            val p = page ?: return
            paint.color = 0xFFDFE9E4.toInt()
            canvas?.drawRect(LEFT, 802f, RIGHT, 803f, paint)
            label("Split | ${report.group.name}", LEFT, 819f, 8f, MUTED)
            label("Page $pageNo", RIGHT, 819f, 8f, MUTED, false, true)
            pdf.finishPage(p)
            page = null
            canvas = null
        }
        fun finish() = close()
    }
}
