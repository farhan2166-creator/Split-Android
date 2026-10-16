package com.splitapp.model

/** Trip accounting in minor units. Advances and repayments are transfers, not expenses. */
data class TravelerSettlement(
    val member: Member,
    val paidForExpensesMinor: Long,
    val allocatedShareMinor: Long,
    val cashAdvancedMinor: Long,
    val cashReceivedAsAdvanceMinor: Long,
    val repaymentsSentMinor: Long,
    val repaymentsReceivedMinor: Long,
    val balanceMinor: Long
) {
    val totalCashOutMinor: Long get() = Math.addExact(Math.addExact(paidForExpensesMinor, cashAdvancedMinor), repaymentsSentMinor)
    val totalCashInMinor: Long get() = Math.addExact(cashReceivedAsAdvanceMinor, repaymentsReceivedMinor)
}

data class TourSettlement(
    val group: SplitGroup,
    val members: List<TravelerSettlement>,
    val totalExpensesMinor: Long,
    val advancesMinor: Long,
    val repaymentsMinor: Long,
    val suggestedRepayments: List<Settlement>,
    val firstTransactionAt: Long?,
    val lastTransactionAt: Long?,
    val expenseCount: Int
) {
    val isFullySettled: Boolean get() = members.all { it.balanceMinor == 0L }
}

/**
 * One source of truth for the four figures displayed on the Overview tab and
 * printed on the one-page tour statement. Personal figures are absent until the
 * traveler selects "Me"; no member is guessed from group order.
 */
data class TourOverviewFigures(
    val totalExpensesMinor: Long,
    val myExpensesMinor: Long?,
    val cashReceivedMinor: Long?,
    /** Positive means the traveler should collect; negative means they owe. */
    val outstandingMinor: Long?
)

fun TourSettlement.figuresFor(memberId: String?): TourOverviewFigures {
    val person = members.firstOrNull { it.member.id == memberId }
    return TourOverviewFigures(
        totalExpensesMinor = totalExpensesMinor,
        myExpensesMinor = person?.allocatedShareMinor,
        cashReceivedMinor = person?.totalCashInMinor,
        outstandingMinor = person?.balanceMinor
    )
}

object TourSettlementBuilder {
    fun build(detail: GroupDetail): TourSettlement {
        val balances = SplitMath.balances(detail)
        val contributions = detail.contributions.groupBy { it.expenseId }
        val paid = detail.members.associate { it.id to 0L }.toMutableMap()
        val shares = paid.toMutableMap()
        val advanceSent = paid.toMutableMap()
        val advanceReceived = paid.toMutableMap()
        val repaySent = paid.toMutableMap()
        val repayReceived = paid.toMutableMap()
        fun add(map: MutableMap<String, Long>, key: String, amount: Long) {
            map[key] = Math.addExact(map.getValue(key), amount)
        }
        detail.expenses.forEach { expense ->
            (contributions[expense.id] ?: listOf(Contribution(expense.id, expense.paidBy, expense.amountMinor)))
                .forEach { add(paid, it.memberId, it.amountMinor) }
        }
        detail.allocations.forEach { add(shares, it.memberId, it.amountMinor) }
        detail.transfers.forEach { transfer ->
            if (transfer.kind == TransferKind.ADVANCE) {
                add(advanceSent, transfer.fromMember, transfer.amountMinor)
                add(advanceReceived, transfer.toMember, transfer.amountMinor)
            } else {
                add(repaySent, transfer.fromMember, transfer.amountMinor)
                add(repayReceived, transfer.toMember, transfer.amountMinor)
            }
        }
        val rows = detail.members.map { member ->
            val id = member.id
            val net = Math.subtractExact(
                Math.addExact(Math.addExact(paid.getValue(id), advanceSent.getValue(id)), repaySent.getValue(id)),
                Math.addExact(Math.addExact(shares.getValue(id), advanceReceived.getValue(id)), repayReceived.getValue(id))
            )
            check(net == balances.getValue(id)) { "Statement balance mismatch for ${member.name}" }
            TravelerSettlement(member, paid.getValue(id), shares.getValue(id), advanceSent.getValue(id),
                advanceReceived.getValue(id), repaySent.getValue(id), repayReceived.getValue(id), net)
        }
        val total = detail.expenses.fold(0L) { sum, e -> Math.addExact(sum, e.amountMinor) }
        check(rows.fold(0L) { sum, row -> Math.addExact(sum, row.paidForExpensesMinor) } == total)
        check(rows.fold(0L) { sum, row -> Math.addExact(sum, row.allocatedShareMinor) } == total)
        check(rows.fold(0L) { sum, row -> Math.addExact(sum, row.balanceMinor) } == 0L)
        val timestamps = detail.expenses.map { it.timestamp } + detail.transfers.map { it.timestamp }
        return TourSettlement(detail.group, rows, total,
            detail.transfers.filter { it.kind == TransferKind.ADVANCE }.fold(0L) { s, t -> Math.addExact(s, t.amountMinor) },
            detail.transfers.filter { it.kind == TransferKind.REPAYMENT }.fold(0L) { s, t -> Math.addExact(s, t.amountMinor) },
            SplitMath.settle(balances), timestamps.minOrNull(), timestamps.maxOrNull(), detail.expenses.size)
    }
}
