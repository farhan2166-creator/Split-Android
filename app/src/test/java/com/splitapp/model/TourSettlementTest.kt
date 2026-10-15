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
