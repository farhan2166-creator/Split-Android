package com.splitapp.model

import java.util.Date

/** A snapshot for printing/exporting. Money remains in integer minor units. */
data class LedgerRange(
    val fromInclusive: Long? = null,
    val toExclusive: Long? = null,
    val title: String = "All dates"
) {
    init { require(fromInclusive == null || toExclusive == null || fromInclusive < toExclusive) }
    fun includes(timestamp: Long): Boolean =
        (fromInclusive == null || timestamp >= fromInclusive) &&
            (toExclusive == null || timestamp < toExclusive)
    fun isBefore(timestamp: Long): Boolean = fromInclusive != null && timestamp < fromInclusive
}

enum class LedgerEventKind { EXPENSE_PAID, EXPENSE_SHARE, REPAYMENT_SENT, REPAYMENT_RECEIVED }

data class LedgerLine(
    val timestamp: Long,
    val description: String,
    val kind: LedgerEventKind,
    val inMinor: Long,
    val outMinor: Long,
    val balanceMinor: Long
)

data class MemberLedger(
    val member: Member,
    val openingMinor: Long,
    val closingMinor: Long,
    val paidMinor: Long,
    val shareMinor: Long,
    val sentMinor: Long,
    val receivedMinor: Long,
    val lines: List<LedgerLine>
)

data class LedgerActivity(
    val timestamp: Long,
    val title: String,
    val description: String,
    val amountMinor: Long,
    val isRepayment: Boolean
)

data class CategoryLedgerSummary(
    val category: String,
    val totalMinor: Long,
    val expenseCount: Int
)

data class LedgerReport(
    val group: SplitGroup,
    val range: LedgerRange,
    val sections: List<MemberLedger>,
    val activity: List<LedgerActivity>,
    val relevantExpensesMinor: Long,
    val relevantRepaymentsMinor: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val categoryTotals: List<CategoryLedgerSummary> = emptyList(),
    val settlementSuggestions: List<Settlement> = emptyList(),
    val wholeGroup: Boolean = false
)

/**
 * Uses the same sign convention as SplitMath: contributions and repayments sent
 * increase what the member is owed; shares and repayments received reduce it.
 * A range reports opening + movement = closing, never a misleading period-only balance.
 */
object LedgerReportBuilder {
    private data class Posting(
        val memberId: String,
        val timestamp: Long,
        val transactionId: String,
        val order: Int,
        val description: String,
        val kind: LedgerEventKind,
        val increase: Long,
        val decrease: Long
    )

    fun build(detail: GroupDetail, selectedMemberIds: Set<String>, range: LedgerRange = LedgerRange()): LedgerReport {
        require(selectedMemberIds.isNotEmpty()) { "Choose at least one participant" }
        val members = detail.members.filter { it.id in selectedMemberIds }
        require(members.size == selectedMemberIds.size) { "Unknown participant in ledger selection" }
        SplitMath.balances(detail) // Reject an invalid or incomplete underlying ledger.
        val names = detail.members.associate { it.id to it.name }
        val funding = detail.contributions.groupBy { it.expenseId }
        val allocations = detail.allocations.groupBy { it.expenseId }
        val postings = mutableListOf<Posting>()
        val activity = mutableListOf<LedgerActivity>()
        val categoryAmounts = mutableMapOf<String, Long>()
        val categoryCounts = mutableMapOf<String, Int>()

        detail.expenses.forEach { expense ->
            val paid = funding[expense.id]
                ?: listOf(Contribution(expense.id, expense.paidBy, expense.amountMinor))
            val consumed = allocations[expense.id].orEmpty()
            val relevant = paid.any { it.memberId in selectedMemberIds } ||
                consumed.any { it.memberId in selectedMemberIds }
            if (relevant && range.includes(expense.timestamp)) {
                categoryAmounts[expense.category] = Math.addExact(categoryAmounts[expense.category] ?: 0L, expense.amountMinor)
                categoryCounts[expense.category] = (categoryCounts[expense.category] ?: 0) + 1
                val payerNames = paid.joinToString(", ") { names[it.memberId].orEmpty() }
                activity += LedgerActivity(
                    expense.timestamp, expense.title,
                    "${expense.category} | Paid by $payerNames",
                    expense.amountMinor, false
                )
            }
            paid.forEach { p ->
                postings += Posting(p.memberId, expense.timestamp, expense.id, 0,
                    "${expense.title} | Paid (${expense.category})",
                    LedgerEventKind.EXPENSE_PAID, p.amountMinor, 0L)
            }
            consumed.forEach { p ->
                if (p.amountMinor > 0) postings += Posting(p.memberId, expense.timestamp, expense.id, 1,
                    "${expense.title} | Share (${expense.category})",
                    LedgerEventKind.EXPENSE_SHARE, 0L, p.amountMinor)
            }
        }
        detail.transfers.forEach { transfer ->
            val from = names.getValue(transfer.fromMember)
            val to = names.getValue(transfer.toMember)
            val suffix = if (transfer.note.isBlank()) "" else " | ${transfer.note}"
            postings += Posting(transfer.fromMember, transfer.timestamp, transfer.id, 2,
                "Repayment sent to $to$suffix", LedgerEventKind.REPAYMENT_SENT, transfer.amountMinor, 0)
            postings += Posting(transfer.toMember, transfer.timestamp, transfer.id, 3,
                "Repayment received from $from$suffix", LedgerEventKind.REPAYMENT_RECEIVED, 0, transfer.amountMinor)
            if (range.includes(transfer.timestamp) &&
                (transfer.fromMember in selectedMemberIds || transfer.toMember in selectedMemberIds)) {
                activity += LedgerActivity(transfer.timestamp, "Repayment",
                    "$from to $to$suffix", transfer.amountMinor, true)
            }
        }

        val sorted = postings.sortedWith(compareBy<Posting> { it.timestamp }
            .thenBy { it.transactionId }.thenBy { it.order }.thenBy { it.memberId })
        val sections = members.map { member ->
            var opening = 0L
            for (p in sorted) {
                if (p.memberId == member.id && range.isBefore(p.timestamp)) {
                    opening = Math.addExact(Math.subtractExact(opening, p.decrease), p.increase)
                }
            }
            var balance = opening
            var paid = 0L
            var share = 0L
            var sent = 0L
            var received = 0L
            val lines = buildList {
                sorted.forEach { p ->
                    if (p.memberId == member.id && range.includes(p.timestamp)) {
                        balance = Math.addExact(Math.subtractExact(balance, p.decrease), p.increase)
                        when (p.kind) {
                            LedgerEventKind.EXPENSE_PAID -> paid = Math.addExact(paid, p.increase)
                            LedgerEventKind.EXPENSE_SHARE -> share = Math.addExact(share, p.decrease)
                            LedgerEventKind.REPAYMENT_SENT -> sent = Math.addExact(sent, p.increase)
                            LedgerEventKind.REPAYMENT_RECEIVED -> received = Math.addExact(received, p.decrease)
                        }
                        add(LedgerLine(p.timestamp, p.description, p.kind,
                            p.increase, p.decrease, balance))
                    }
                }
            }
            require(balance == Math.addExact(opening,
                Math.subtractExact(Math.addExact(paid, sent), Math.addExact(share, received))))
            MemberLedger(member, opening, balance, paid, share, sent, received, lines)
        }
        val relevantActivity = activity.sortedWith(compareBy<LedgerActivity> { it.timestamp }
            .thenBy { it.isRepayment }.thenBy { it.title })
        val wholeGroup = selectedMemberIds.size == detail.members.size
        val suggested = if (wholeGroup) {
            SplitMath.settle(sections.associate { it.member.id to it.closingMinor })
        } else emptyList()
        val categories = categoryAmounts.entries.map {
            CategoryLedgerSummary(it.key, it.value, categoryCounts.getValue(it.key))
        }.sortedWith(compareByDescending<CategoryLedgerSummary> { it.totalMinor }.thenBy { it.category })
        return LedgerReport(detail.group, range, sections, relevantActivity,
            relevantActivity.filterNot { it.isRepayment }.fold(0L) { sum, a -> Math.addExact(sum, a.amountMinor) },
            relevantActivity.filter { it.isRepayment }.fold(0L) { sum, a -> Math.addExact(sum, a.amountMinor) },
            categoryTotals = categories,
            settlementSuggestions = suggested,
            wholeGroup = wholeGroup)
    }
}
