package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZonedDateTime

class BudgetMathTest {
    private val zone = ZoneId.of("Asia/Karachi")
    private fun stamp(y: Int, m: Int, d: Int): Long =
        ZonedDateTime.of(y, m, d, 12, 0, 0, 0, zone).toInstant().toEpochMilli()

    private val member = Member("a", "g", "Alex")
    private fun detail(limit: Long? = 10000L, amounts: List<Pair<Long, Long>>): GroupDetail {
        val expenses = amounts.mapIndexed { i, pair ->
            Expense("e$i", "g", "Expense $i", "Food", pair.second, "a", "", pair.first)
        }
        return GroupDetail(SplitGroup("g", "Flat", "PKR", 0, monthlyBudgetMinor = limit),
            listOf(member), expenses,
            expenses.map { Allocation(it.id, "a", it.amountMinor) },
            emptyList())
    }

    @Test fun excludesPreviousAndNextMonthsAndRepayments() {
        val d = detail(amounts = listOf(
            stamp(2026, 9, 30) to 9999L,
            stamp(2026, 10, 1) to 1000L,
            stamp(2026, 10, 31) to 3000L,
            stamp(2026, 11, 1) to 9999L
        )).copy(transfers = listOf(Transfer("t", "g", "a", "b", 9999, "", stamp(2026,10,2))))
        val result = BudgetMath.forMonth(d, YearMonth.of(2026, 10), zone)
        assertEquals(4000L, result.spentMinor)
        assertEquals(2, result.expensesCount)
        assertEquals(6000L, result.remainingMinor)
        assertEquals(BudgetHealth.ON_TRACK, result.health)
    }

    @Test fun nearAndOverBudgetStates() {
        val near = BudgetMath.forMonth(detail(amounts = listOf(stamp(2026,10,2) to 8000L)),
            YearMonth.of(2026,10), zone)
        assertEquals(BudgetHealth.NEAR_LIMIT, near.health)
        val over = BudgetMath.forMonth(detail(amounts = listOf(stamp(2026,10,2) to 12000L)),
            YearMonth.of(2026,10), zone)
        assertEquals(BudgetHealth.OVER_LIMIT, over.health)
        assertEquals(-2000L, over.remainingMinor)
        assertEquals(1f, over.progress, 0.001f)
    }

    @Test fun unsetBudgetDoesNotChangeSpending() {
        val result = BudgetMath.forMonth(detail(null, listOf(stamp(2026,10,5) to 100L)),
            YearMonth.of(2026,10), zone)
        assertEquals(100L, result.spentMinor)
        assertNull(result.remainingMinor)
        assertEquals(BudgetHealth.NOT_SET, result.health)
    }
}
