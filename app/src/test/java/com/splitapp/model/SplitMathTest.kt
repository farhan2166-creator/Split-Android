package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

class SplitMathTest {
    private val group = SplitGroup("g", "Trip", "PKR", 0)
    private val people = listOf(Member("a", "g", "Ali"), Member("b", "g", "Bina"), Member("c", "g", "Cara"))

    @Test fun equalSplitDistributesRemainderDeterministically() {
        assertEquals(mapOf("a" to 334L, "b" to 333L, "c" to 333L), SplitMath.allocate(1000, mapOf("a" to 1, "b" to 1, "c" to 1)))
    }

    @Test fun weightedSplitPreservesEveryCent() {
        val result = SplitMath.allocate(10001, mapOf("a" to 5, "b" to 3, "c" to 2))
        assertEquals(10001L, result.values.sum())
        assertEquals(mapOf("a" to 5001L, "b" to 3000L, "c" to 2000L), result)
    }

    @Test fun handlesLargeWeightsWithoutMultiplicationOverflow() {
        val result = SplitMath.allocate(999_999_999_999, mapOf("a" to Long.MAX_VALUE, "b" to Long.MAX_VALUE))
        assertEquals(999_999_999_999L, result.values.sum())
    }

    @Test fun settlementsAndRecordedTransfersCancelDebt() {
        val expense = Expense("e", "g", "Dinner", "Food", 10000, "a", "", 0)
        val shares = listOf(Allocation("e", "a", 3334), Allocation("e", "b", 3333), Allocation("e", "c", 3333))
        val detail = GroupDetail(group, people, listOf(expense), shares, emptyList())
        val balances = SplitMath.balances(detail)
        assertEquals(mapOf("a" to 6666L, "b" to -3333L, "c" to -3333L), balances)
        assertEquals(2, SplitMath.settle(balances).size)
        val repayments = listOf(Transfer("t1", "g", "b", "a", 3333, "", 0), Transfer("t2", "g", "c", "a", 3333, "", 0))
        val paid = SplitMath.balances(detail.copy(transfers = repayments))
        assertTrue(paid.values.all { it == 0L })
        assertTrue(SplitMath.settle(paid).isEmpty())
    }

    @Test fun selfPaidExpenseHasZeroBalance() {
        val e = Expense("e", "g", "Coffee", "Food", 155, "a", "", 0)
        val detail = GroupDetail(group, people, listOf(e), listOf(Allocation("e", "a", 155)), emptyList())
        assertTrue(SplitMath.balances(detail).values.all { it == 0L })
    }

    @Test fun exactMoneyParsingRejectsExcessPrecisionAndNegativeAmounts() {
        assertEquals(12345L, Money.parse("123.45"))
        assertEquals(0L, Money.parse("0"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("-1.00"))
        assertNull(Money.parse("NaN"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ledgerRejectsMismatchedShares() {
        val e = Expense("e", "g", "Dinner", "Food", 100, "a", "", 0)
        SplitMath.balances(GroupDetail(group, people, listOf(e), listOf(Allocation("e", "b", 99)), emptyList()))
    }
}
