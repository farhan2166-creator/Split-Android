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


    @Test fun multiplePayersPreserveZeroSumLedger() {
        val expense = Expense("e", "g", "Hotel", "Stay", 12000, "a", "", 0)
        val shares = listOf(Allocation("e", "a", 4000), Allocation("e", "b", 4000), Allocation("e", "c", 4000))
        val funding = listOf(Contribution("e", "a", 7000), Contribution("e", "b", 5000))
        val detail = GroupDetail(group, people, listOf(expense), shares, emptyList(), funding)
        assertEquals(mapOf("a" to 3000L, "b" to 1000L, "c" to -4000L), SplitMath.balances(detail))
        val plan = SplitMath.settle(SplitMath.balances(detail))
        assertEquals(4000L, plan.sumOf { it.amountMinor })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidMultiPayerTotalsAreRejected() {
        val expense = Expense("e", "g", "Hotel", "Stay", 12000, "a", "", 0)
        SplitMath.balances(GroupDetail(group, people, listOf(expense),
            listOf(Allocation("e", "a", 12000)), emptyList(),
            listOf(Contribution("e", "a", 5000), Contribution("e", "b", 6000))))
    }

    @Test fun correctingCashMovementRecalculatesBalancesWithoutChangingExpenses() {
        val e = Expense("e", "g", "Taxi", "Transport", 12000L, "a", "", 15L)
        val shares = listOf(
            Allocation("e", "a", 4000L),
            Allocation("e", "b", 4000L),
            Allocation("e", "c", 4000L)
        )
        val original = Transfer("t", "g", "b", "a", 500L,
            "Partial settlement", 18L, TransferKind.REPAYMENT)
        val before = GroupDetail(group, people, listOf(e), shares, listOf(original))
        assertEquals(mapOf("a" to 7500L, "b" to -3500L, "c" to -4000L),
            SplitMath.balances(before))

        val corrected = before.copy(transfers = listOf(original.copy(
            amountMinor = 1500L, note = "Corrected", timestamp = 19L
        )))
        assertEquals(mapOf("a" to 6500L, "b" to -2500L, "c" to -4000L),
            SplitMath.balances(corrected))
        assertEquals(before.expenses.sumOf { it.amountMinor },
            corrected.expenses.sumOf { it.amountMinor })

        val advance = corrected.copy(transfers = listOf(original.copy(
            amountMinor = 1500L, kind = TransferKind.ADVANCE
        )))
        assertEquals(SplitMath.balances(corrected), SplitMath.balances(advance))
    }

    @Test(expected = IllegalArgumentException::class)
    fun ledgerRejectsMismatchedShares() {
        val e = Expense("e", "g", "Dinner", "Food", 100, "a", "", 0)
        SplitMath.balances(GroupDetail(group, people, listOf(e), listOf(Allocation("e", "b", 99)), emptyList()))
    }
}
