package com.splitapp.report

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.splitapp.model.*
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date

/** Always exactly one A4 landscape page; intended for small touring parties. */
object TourSettlementPdf {
    private const val WIDTH = 842
    private const val HEIGHT = 595
    private const val GREEN = -0xe9a99e
    fun write(stream: OutputStream, report: TourSettlement) {
        require(report.members.size <= 12) { "One-page settlement supports up to 12 travelers" }
        val pdf = PdfDocument()
        try {
            val page = pdf.startPage(PdfDocument.PageInfo.Builder(WIDTH, HEIGHT, 1).create())
            val c = page.canvas
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            fun text(value: String, x: Float, y: Float, size: Float = 10f,
                     color: Int = Color.DKGRAY, bold: Boolean = false, right: Boolean = false) {
                p.color = color
                p.textSize = size
                p.typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                p.textAlign = if (right) Paint.Align.RIGHT else Paint.Align.LEFT
                c.drawText(value.take(64), x, y, p)
            }
            fun amount(value: Long): String = Money.format(value)
            fun date(value: Long?): String = value?.let {
                DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it))
            } ?: "—"
            fun line(y: Float) { p.color = 0xFFDDE7E2.toInt(); c.drawRect(36f,y,806f,y + 1f,p) }
            p.color = 0xFF163F48.toInt()
            c.drawRect(0f,0f,WIDTH.toFloat(),82f,p)
            text("SPLIT   /   TOUR SETTLEMENT",36f,30f,12f,Color.WHITE,true)
            text(report.group.name,36f,61f,23f,Color.WHITE,true)
            text("CURRENCY: ${report.group.currency}",806f,31f,10f,Color.WHITE,true,true)
            text("${date(report.firstTransactionAt)}  –  ${date(report.lastTransactionAt)}",806f,59f,10f,Color.WHITE,false,true)
            text("TOUR EXPENSES",36f,109f,10f,0xFF64736F.toInt(),true)
            text("${report.group.currency} ${amount(report.totalExpensesMinor)}",36f,133f,20f,0xFF146C63.toInt(),true)
            text("${report.expenseCount} expense records",36f,149f,9f)
            text("CASH ADVANCES",270f,109f,10f,0xFF64736F.toInt(),true)
            text(amount(report.advancesMinor),270f,133f,19f,0xFF146C63.toInt(),true)
            text("PRIVATE REPAYMENTS",515f,109f,10f,0xFF64736F.toInt(),true)
            text(amount(report.repaymentsMinor),515f,133f,19f,0xFF146C63.toInt(),true)
            val status = if(report.isFullySettled) "FULLY SETTLED" else "BALANCES OPEN"
            text(status,806f,149f,10f,if(report.isFullySettled) 0xFF146C63.toInt() else 0xFFAE6738.toInt(),true,true)
            line(164f)
            val cols = listOf(40f,177f,269f,358f,446f,528f,608f,691f,802f)
            p.color = 0xFFECF4EF.toInt()
            c.drawRect(36f,178f,806f,205f,p)
            val titles = listOf("TRAVELER","BILLS PAID","OWN SHARE","ADV. GIVEN","ADV. GOT","REP. SENT","REP. GOT","NET BALANCE")
            titles.forEachIndexed { i, title -> text(title,cols[i],195f,8f,0xFF295B50.toInt(),true,i>0) }
            val available = 242f
            val rowHeight = minOf(32f, available / maxOf(1,report.members.size))
            report.members.forEachIndexed { i, member ->
                val y = 218f + i * rowHeight
                if (i % 2 == 0) { p.color = 0xFFF5F8F6.toInt(); c.drawRect(36f,y-16f,806f,y+8f,p) }
                val values = listOf(member.member.name,amount(member.paidForExpensesMinor),
                    amount(member.allocatedShareMinor),amount(member.cashAdvancedMinor),
                    amount(member.cashReceivedAsAdvanceMinor),amount(member.repaymentsSentMinor),
                    amount(member.repaymentsReceivedMinor),amount(member.balanceMinor))
                values.forEachIndexed { k, value ->
                    text(if(k==0) value.take(22) else value,cols[k],y,if(k==0)10f else 9f,
                        if(k==7) (if(member.balanceMinor < 0) 0xFFB44E41.toInt() else 0xFF146C63.toInt()) else Color.DKGRAY,
                        k==0 || k==7,k>0)
                }
            }
            val bottom = 228f + report.members.size * rowHeight
            line(bottom)
            text("FINAL SETTLEMENT",36f,bottom+22f,11f,0xFF146C63.toInt(),true)
            if (report.isFullySettled) {
                text("Everyone is settled. No payments remain outstanding.",36f,bottom+43f,10f)
            } else {
                val names=report.members.associate { it.member.id to it.member.name }
                report.suggestedRepayments.take(7).forEachIndexed { i,t ->
                    val x = if (i < 4) 36f else 430f
                    val y = bottom + 43f + (i % 4)*16f
                    text("${names.getValue(t.fromMember)}  →  ${names.getValue(t.toMember)}  :  ${amount(t.amountMinor)}",x,y,9f)
                }
                if (report.suggestedRepayments.size > 7) text("Additional repayments are shown in the detailed ledger.",36f,bottom+111f,8f)
            }
            line(561f)
            text("Net balance = bills paid + advances given + repayments sent - own share - advances received - repayments received.",36f,577f,8f,0xFF526861.toInt())
            text("Positive: receive  /  Negative: pay",806f,577f,8f,0xFF526861.toInt(),false,true)
            pdf.finishPage(page)
            pdf.writeTo(stream)
        } finally { pdf.close() }
    }
}
