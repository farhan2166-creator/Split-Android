package com.splitapp.model

/** Activity filtering is pure so text searches never alter the underlying ledger. */
enum class ActivityKind(val label: String) {
    All("All"), Expenses("Expenses"), Advances("Advances"), Repayments("Repayments")
}

object LedgerActivity {
    /** Newest-first view of stored expenses and cash movements. */
    fun filter(
        detail: GroupDetail,
        kind: ActivityKind,
        search: String = "",
        category: String = "All"
    ): List<Any> {
        val names = detail.members.associate { it.id to it.name }
        val peopleWhoPaid = detail.contributions.groupBy { it.expenseId }
        val peopleWhoShared = detail.allocations.groupBy { it.expenseId }
        val query = search.trim()

        fun matches(vararg values: String?): Boolean = query.isBlank() ||
            values.any { it?.contains(query, ignoreCase = true) == true }

        val expenses = if (kind == ActivityKind.All || kind == ActivityKind.Expenses) {
            detail.expenses.filter { expense ->
                (category == "All" || expense.category == category) &&
                    (matches(expense.title, expense.category, expense.note, names[expense.paidBy]) ||
                        peopleWhoPaid[expense.id].orEmpty().any { member ->
                            matches(names[member.memberId])
                        } ||
                        peopleWhoShared[expense.id].orEmpty().any { member ->
                            matches(names[member.memberId])
                        })
            }
        } else emptyList()
        val transfers = if (kind != ActivityKind.Expenses) {
            detail.transfers.filter { transfer ->
                (kind == ActivityKind.All ||
                    (kind == ActivityKind.Advances && transfer.kind == TransferKind.ADVANCE) ||
                    (kind == ActivityKind.Repayments && transfer.kind == TransferKind.REPAYMENT)) &&
                    matches(
                        if (transfer.kind == TransferKind.ADVANCE) "Cash advance" else "Repayment",
                        names[transfer.fromMember], names[transfer.toMember], transfer.note
                    )
            }
        } else emptyList()
        return (expenses + transfers).sortedByDescending { entry ->
            when (entry) {
                is Expense -> entry.timestamp
                is Transfer -> entry.timestamp
                else -> Long.MIN_VALUE
            }
        }
    }
}
