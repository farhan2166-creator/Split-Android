package com.splitapp.model

import java.time.YearMonth
import java.time.ZoneId

/** A budget is a spending guide; it never changes balances or payment records. */
enum class BudgetHealth { NOT_SET, ON_TRACK, NEAR_LIMIT, OVER_LIMIT }

data class MonthlyBudgetSummary(
    val month: YearMonth,
    val spentMinor: Long,
    val limitMinor: Long?,
    val expensesCount: Int,
    val categoryMinor: Map<String, Long>
) {
    init {
        require(spentMinor >= 0 && expensesCount >= 0)
        require(limitMinor == null || limitMinor > 0)
    }

    val remainingMinor: Long? get() = limitMinor?.let { Math.subtractExact(it, spentMinor) }
    val health: BudgetHealth get() {
        val limit = limitMinor ?: return BudgetHealth.NOT_SET
        return when {
            spentMinor > limit -> BudgetHealth.OVER_LIMIT
            // Compare by multiplication-free division to avoid overflow on large values.
            spentMinor >= limit - limit / 5 -> BudgetHealth.NEAR_LIMIT
            else -> BudgetHealth.ON_TRACK
        }
    }
    val progress: Float get() = limitMinor?.let {
        (spentMinor.toDouble() / it.toDouble()).coerceIn(0.0, 1.0).toFloat()
    } ?: 0f
}

object BudgetMath {
    /** Boundaries use the phone's local time zone; repayments never count as spending. */
    fun forMonth(
        detail: GroupDetail,
        month: YearMonth,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): MonthlyBudgetSummary {
        val start = month.atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
        val expenses = detail.expenses.filter { it.timestamp >= start && it.timestamp < end }
        val spent = expenses.fold(0L) { sum, item -> Math.addExact(sum, item.amountMinor) }
        val byCategory = expenses.groupBy { it.category }.mapValues { (_, values) ->
            values.fold(0L) { sum, item -> Math.addExact(sum, item.amountMinor) }
        }
        return MonthlyBudgetSummary(month, spent, detail.group.monthlyBudgetMinor, expenses.size, byCategory)
    }
}
