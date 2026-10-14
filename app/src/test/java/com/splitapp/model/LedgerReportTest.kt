package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

class LedgerReportTest {
    private val group = SplitGroup("g", "Holiday", "PKR", 0)
    private val members = listOf(Member("a", "g", "Ali"), Member("b", "g", "Bina"), Member("c", "g", "Cara"))
    private fun detail() = GroupDetail(
        group, members,
        listOf(
            Expense("e1", "g", "Hotel", "Stay", 12000, "a", "", 1000),
            Expense("e2", "g", "Dinner", "Food", 3000, "b", "", 3000)
        ),
        listOf(
            Allocation("e1", "a", 4000), Allocation("e1", "b", 4000), Allocation("e1", "c", 4000),
            Allocation("e2", "a", 1000), Allocation("e2", "b", 1000), Allocation("e2", "c", 1000)
        ),
        listOf(Transfer("t", "g", "c", "a", 1000, "Cash", 4000)),
        listOf(
            Contribution("e1", "a", 7000), Contribution("e1", "b", 5000),
            Contribution("e2", "b", 3000)
        )
    )

    @Test fun fullReportClosesAtLedgerBalances() {
        val source = detail()
        val report = LedgerReportBuilder.build(source, members.map { it.id }.toSet())
        val expected = SplitMath.balances(source)
        assertEquals(expected, report.sections.associate { it.member.id to it.closingMinor })
        assertEquals(15000L, report.relevantExpensesMinor)
        assertEquals(1000L, report.relevantRepaymentsMinor)
        assertEquals(0L, report.sections.sumOf { it.closingMinor })
    }

    @Test fun onePersonHasCorrectOpeningAndPeriodMovement() {
        val report = LedgerReportBuilder.build(detail(), setOf("a"), LedgerRange(2000, 5000, "Custom"))
        val ali = report.sections.single()
        assertEquals(3000L, ali.openingMinor)
        assertEquals(1000L, ali.paidMinor - ali.shareMinor + ali.sentMinor - ali.receivedMinor + ali.openingMinor)
        assertEquals(1000L, ali.closingMinor)
        assertEquals(2, ali.lines.size)
    }

    @Test fun rangeIsStartInclusiveEndExclusive() {
        val report = LedgerReportBuilder.build(detail(), setOf("b"), LedgerRange(1000, 3000, "Day"))
        val ledger = report.sections.single()
        assertEquals(0L, ledger.openingMinor)
        assertEquals(1000L, ledger.closingMinor)
        assertEquals(2, ledger.lines.size)
        assertEquals(12000L, report.relevantExpensesMinor)
    }

    @Test fun multipleSelectionDoesNotDoubleCountSharedExpense() {
        val report = LedgerReportBuilder.build(detail(), setOf("a", "b"), LedgerRange(0, 2000))
        assertEquals(1, report.activity.size)
        assertEquals(12000L, report.relevantExpensesMinor)
        assertEquals(setOf("a", "b"), report.sections.map { it.member.id }.toSet())
    }

    @Test(expected = IllegalArgumentException::class)
    fun unknownParticipantIsRejected() {
        LedgerReportBuilder.build(detail(), setOf("missing"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedPeriodIsRejected() {
        LedgerRange(6000, 2000)
    }
}
