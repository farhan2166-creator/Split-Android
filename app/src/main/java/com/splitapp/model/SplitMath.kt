package com.splitapp.model

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.text.DecimalFormat

/** All persisted monetary values use integer hundredths, never floating point. */
object Money {
    private val amountPattern = Regex("[0-9]{1,12}(\\.[0-9]{1,2})?")

    fun parse(text: String): Long? {
        val value = text.trim()
        if (!amountPattern.matches(value)) return null
        return try {
            BigDecimal(value).movePointRight(2).longValueExact()
        } catch (_: ArithmeticException) {
            null
        }
    }

    fun format(amountMinor: Long): String =
        DecimalFormat("#,##0.00").format(BigDecimal.valueOf(amountMinor, 2))

    fun input(amountMinor: Long): String = BigDecimal.valueOf(amountMinor, 2).toPlainString()
}

object SplitMath {
    /** Allocates all minor units, assigning leftover units by fractional remainder then stable member id. */
    fun allocate(totalMinor: Long, weights: Map<String, Long>): Map<String, Long> {
        require(totalMinor > 0) { "Amount must be positive" }
        require(weights.isNotEmpty() && weights.values.all { it > 0 }) { "Weights must be positive" }
        val sum = weights.values.fold(BigInteger.ZERO) { result, w -> result + BigInteger.valueOf(w) }
        val total = BigInteger.valueOf(totalMinor)
        data class Part(val id: String, val whole: Long, val remainder: BigInteger)
        val parts = weights.map { (id, weight) ->
            val units = total * BigInteger.valueOf(weight)
            val qr = units.divideAndRemainder(sum)
            Part(id, qr[0].longValueExact(), qr[1])
        }
        val remainder = totalMinor - parts.sumOf { it.whole }
        val winners = parts.sortedWith(compareByDescending<Part> { it.remainder }.thenBy { it.id })
            .take(remainder.toInt()).map { it.id }.toSet()
        return parts.associate { it.id to (it.whole + if (it.id in winners) 1 else 0) }
    }

    /** Paid - consumed + repayments sent - repayments received. Positive means owed money. */
    fun balances(detail: GroupDetail): Map<String, Long> {
        val balance = detail.members.associate { it.id to 0L }.toMutableMap()
        val expenses = detail.expenses.associateBy { it.id }
        val contributions = detail.contributions.groupBy { it.expenseId }
        detail.expenses.forEach { expense ->
            require(expense.amountMinor > 0 && balance.containsKey(expense.paidBy))
            val funded = contributions[expense.id] ?: listOf(Contribution(expense.id, expense.paidBy, expense.amountMinor))
            require(funded.isNotEmpty() && funded.map { it.memberId }.distinct().size == funded.size)
            require(funded.all { it.amountMinor > 0 && balance.containsKey(it.memberId) })
            require(funded.fold(0L) { sum, part -> Math.addExact(sum, part.amountMinor) } == expense.amountMinor)
            funded.forEach { part ->
                balance[part.memberId] = Math.addExact(balance.getValue(part.memberId), part.amountMinor)
            }
        }
        val sums = mutableMapOf<String, Long>()
        detail.allocations.forEach { part ->
            require(balance.containsKey(part.memberId) && expenses.containsKey(part.expenseId))
            require(part.amountMinor >= 0)
            balance[part.memberId] = Math.subtractExact(balance.getValue(part.memberId), part.amountMinor)
            sums[part.expenseId] = Math.addExact(sums[part.expenseId] ?: 0L, part.amountMinor)
        }
        detail.expenses.forEach { expense -> require(sums[expense.id] == expense.amountMinor) }
        detail.transfers.forEach { transfer ->
            require(transfer.amountMinor > 0 && transfer.fromMember != transfer.toMember)
            balance[transfer.fromMember] = Math.addExact(balance.getValue(transfer.fromMember), transfer.amountMinor)
            balance[transfer.toMember] = Math.subtractExact(balance.getValue(transfer.toMember), transfer.amountMinor)
        }
        require(balance.values.sum() == 0L) { "Unbalanced ledger" }
        return balance
    }

    /** Greedy settlement plan; each operation reduces at least one outstanding balance to zero. */
    fun settle(balances: Map<String, Long>): List<Settlement> {
        require(balances.values.sum() == 0L)
        val debtors = balances.filterValues { it < 0 }.map { Entry(it.key, -it.value) }
            .sortedWith(compareByDescending<Entry> { it.remaining }.thenBy { it.id }).toMutableList()
        val creditors = balances.filterValues { it > 0 }.map { Entry(it.key, it.value) }
            .sortedWith(compareByDescending<Entry> { it.remaining }.thenBy { it.id }).toMutableList()
        val output = mutableListOf<Settlement>()
        var d = 0
        var c = 0
        while (d < debtors.size && c < creditors.size) {
            val amount = minOf(debtors[d].remaining, creditors[c].remaining)
            output += Settlement(debtors[d].id, creditors[c].id, amount)
            debtors[d].remaining -= amount
            creditors[c].remaining -= amount
            if (debtors[d].remaining == 0L) d++
            if (creditors[c].remaining == 0L) c++
        }
        return output
    }

    private data class Entry(val id: String, var remaining: Long)
}
