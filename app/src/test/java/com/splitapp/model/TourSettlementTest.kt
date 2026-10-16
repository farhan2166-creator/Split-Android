package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

class TourSettlementTest {
    private val group = SplitGroup("tour", "North Tour", "PKR", 0)
    private val a = Member("a", "tour", "Ali")
    private val b = Member("b", "tour", "Bilal")
    private val c = Member("c", "tour", "Cyrus")
    private val members = listOf(a, b, c)
    private val expense = Expense("hotel", "tour", "Hotel", "Stay", 12000, "b", "", 100L)
    private val allocations = listOf(
        Allocation("hotel", "a", 4000), Allocation("hotel", "b", 4000), Allocation("hotel", "c", 4000)
    )
    private fun detail(transfers: List<Transfer> = emptyList()) =
        GroupDetail(group, members, listOf(expense), allocations, transfers,
            listOf(Contribution("hotel", "b", 9000), Contribution("hotel", "c", 3000)))

    @Test fun advanceIsCashNotExpenseAndPartialSettlementIsExact() {
        val advance = Transfer("advance", "tour", "a", "b", 2000, "Cash before departure", 10L, TransferKind.ADVANCE)
        val partial = Transfer("partial", "tour", "a", "b", 1000, "Partial pay", 110L, TransferKind.REPAYMENT)
        val s = TourSettlementBuilder.build(detail(listOf(advance,partial)))
        assertEquals(12000L, s.totalExpensesMinor)
        assertEquals(2000L, s.advancesMinor)
        assertEquals(1000L, s.repaymentsMinor)
        assertEquals(12000L, s.members.sumOf { it.paidForExpensesMinor })
        assertEquals(12000L, s.members.sumOf { it.allocatedShareMinor })
        assertEquals(SplitMath.balances(detail(listOf(advance,partial))),
            s.members.associate { it.member.id to it.balanceMinor })
        assertEquals(-1000L, s.members.single { it.member.id == "a" }.balanceMinor)
        assertEquals(2000L, s.members.single { it.member.id == "b" }.balanceMinor)
        assertEquals(-1000L, s.members.single { it.member.id == "c" }.balanceMinor)
        assertFalse(s.isFullySettled)
        assertEquals(2000L, s.suggestedRepayments.sumOf { it.amountMinor })
    }

    @Test fun fourOverviewMetricsSeparateExpenseSharesFromCashReceived() {
        val advances = Transfer("cash", "tour", "b", "a", 2200L, "Trip float", 5L, TransferKind.ADVANCE)
        val repayment = Transfer("repayment", "tour", "c", "a", 800L, "Paid back", 6L, TransferKind.REPAYMENT)
        val report = TourSettlementBuilder.build(detail(listOf(advances, repayment)))
        val ali = report.members.first { it.member.id == "a" }
        assertEquals(12000L, report.totalExpensesMinor)
        assertEquals(4000L, ali.allocatedShareMinor)
        assertEquals(3000L, ali.totalCashInMinor)
        assertEquals(0L, ali.paidForExpensesMinor)
        assertEquals(-7000L, ali.balanceMinor)
        assertEquals(SplitMath.balances(detail(listOf(advances, repayment))).getValue("a"),
            ali.balanceMinor)
    }

    @Test fun cashSentDoesNotIncreaseTourSpending() {
        val advanced = Transfer("advance2", "tour", "c", "b", 500L, "", 1L, TransferKind.ADVANCE)
        val report = TourSettlementBuilder.build(detail(listOf(advanced)))
        assertEquals(12000L, report.totalExpensesMinor)
        assertEquals(0L, report.members.first { it.member.id == "c" }.totalCashInMinor)
        assertEquals(500L, report.members.first { it.member.id == "b" }.totalCashInMinor)
    }

    @Test fun overviewFiguresMatchPersonalSettlementForPdfAndScreen() {
        val transfers = listOf(
            Transfer("advance3", "tour", "b", "a", 2200L, "", 3L, TransferKind.ADVANCE),
            Transfer("partial3", "tour", "c", "a", 800L, "", 4L, TransferKind.REPAYMENT)
        )
        val report = TourSettlementBuilder.build(detail(transfers))
        val ali = report.members.single { it.member.id == "a" }
        val figures = report.figuresFor("a")
        assertEquals(12000L, figures.totalExpensesMinor)
        assertEquals(ali.allocatedShareMinor, figures.myExpensesMinor)
        assertEquals(ali.totalCashInMinor, figures.cashReceivedMinor)
        assertEquals(ali.balanceMinor, figures.outstandingMinor)
        assertEquals(-7000L, figures.outstandingMinor)
    }

    @Test fun overviewNeverGuessesMeWhenIdentityIsMissing() {
        val report = TourSettlementBuilder.build(detail())
        listOf(null, "", "missing").forEach { id ->
            val figures = report.figuresFor(id)
            assertEquals(12000L, figures.totalExpensesMinor)
            assertNull(figures.myExpensesMinor)
            assertNull(figures.cashReceivedMinor)
            assertNull(figures.outstandingMinor)
        }
    }

    @Test fun signedOutstandingDirectionRemainsUnchanged() {
        val report = TourSettlementBuilder.build(detail())
        assertEquals(-4000L, report.figuresFor("a").outstandingMinor)
        assertEquals(5000L, report.figuresFor("b").outstandingMinor)
        assertEquals(-1000L, report.figuresFor("c").outstandingMinor)
    }

    @Test fun finalRepaymentsMarkTourSettledWithoutChangingSpending() {
        val transfers = listOf(
            Transfer("a1", "tour", "a", "b", 2000, "", 1, TransferKind.ADVANCE),
            Transfer("a2", "tour", "a", "b", 2000, "", 2, TransferKind.REPAYMENT),
            Transfer("c1", "tour", "c", "b", 1000, "", 3, TransferKind.REPAYMENT)
        )
        val s = TourSettlementBuilder.build(detail(transfers))
        // Ali: -4000 + 4000 = 0, Bilal: +5000 - 5000 = 0, Cyrus: -1000 + 1000 = 0
        assertTrue(s.isFullySettled)
        assertTrue(s.suggestedRepayments.isEmpty())
        assertEquals(12000L, s.totalExpensesMinor)
    }

    @Test fun partialSharingOnlyChargesSelectedTravelers() {
        val lunch = Expense("lunch", "tour", "Lunch", "Food", 3000, "a", "", 3)
        val d = detail().copy(expenses = detail().expenses + lunch,
            allocations = allocations + listOf(Allocation("lunch", "a", 1500), Allocation("lunch", "c", 1500)),
            contributions = detail().contributions + Contribution("lunch","a",3000))
        val s = TourSettlementBuilder.build(d)
        assertEquals(15000L, s.totalExpensesMinor)
        assertEquals(4000L, s.members.single { it.member.id == "b" }.allocatedShareMinor)
        assertEquals(5500L, s.members.single { it.member.id == "a" }.allocatedShareMinor)
    }
}
