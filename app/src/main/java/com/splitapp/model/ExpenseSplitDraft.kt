package com.splitapp.model

/** Shared split validation for the expense form, independent of Android widgets. */
enum class ExpenseSplitMode(val title: String) {
    Equal("Equal"), Shares("Shares"), Percent("Percent"), Exact("Exact")
}

object ExpenseSplitDraft {
    /** Does not round assigned money away: exact/percent inputs must match the total. */
    fun allocations(
        totalMinor: Long,
        selected: List<String>,
        mode: ExpenseSplitMode,
        inputs: Map<String, String>
    ): Map<String, Long>? {
        if (totalMinor <= 0L || selected.isEmpty() || selected.distinct().size != selected.size) return null
        return when (mode) {
            ExpenseSplitMode.Equal -> SplitMath.allocate(totalMinor, selected.associateWith { 1L })
            ExpenseSplitMode.Shares -> {
                val weights = selected.associateWith { inputs[it]?.trim()?.toLongOrNull() ?: return null }
                if (weights.values.any { it <= 0L }) null else SplitMath.allocate(totalMinor, weights)
            }
            ExpenseSplitMode.Percent -> {
                val weights = selected.associateWith { Money.parse(inputs[it].orEmpty()) ?: return null }
                val percentTotal = weights.values.fold(0L) { total, value -> Math.addExact(total, value) }
                if (weights.values.any { it <= 0L } || percentTotal != 10000L) null
                else SplitMath.allocate(totalMinor, weights)
            }
            ExpenseSplitMode.Exact -> {
                val amounts = selected.associateWith { Money.parse(inputs[it].orEmpty()) ?: return null }
                val assigned = amounts.values.fold(0L) { total, value -> Math.addExact(total, value) }
                if (assigned != totalMinor) null else amounts
            }
        }
    }

    /** Display the live total while customizing exact amounts or percentages. */
    fun enteredTotal(
        selected: List<String>,
        mode: ExpenseSplitMode,
        inputs: Map<String, String>
    ): Long? {
        if (mode != ExpenseSplitMode.Exact && mode != ExpenseSplitMode.Percent) return null
        var assigned = 0L
        for (id in selected) {
            val raw = inputs[id].orEmpty()
            val amount = if (raw.isBlank()) 0L else Money.parse(raw) ?: return null
            assigned = Math.addExact(assigned, amount)
        }
        return assigned
    }

    /** Adding/removing someone should not erase the other travelers' custom inputs. */
    fun newParticipantInput(mode: ExpenseSplitMode): String = when (mode) {
        ExpenseSplitMode.Shares -> "1"
        ExpenseSplitMode.Percent, ExpenseSplitMode.Exact -> "0"
        ExpenseSplitMode.Equal -> ""
    }
}
