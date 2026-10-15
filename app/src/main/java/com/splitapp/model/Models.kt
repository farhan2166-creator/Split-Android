package com.splitapp.model

data class SplitGroup(
    val id: String,
    val name: String,
    val currency: String,
    val createdAt: Long,
    val archived: Boolean = false,
    val monthlyBudgetMinor: Long? = null
)

data class Member(val id: String, val groupId: String, val name: String)

data class Expense(
    val id: String,
    val groupId: String,
    val title: String,
    val category: String,
    val amountMinor: Long,
    val paidBy: String,
    val note: String,
    val timestamp: Long
)

data class Allocation(val expenseId: String, val memberId: String, val amountMinor: Long)

/** An expense can be funded by more than one participant. */
data class Contribution(val expenseId: String, val memberId: String, val amountMinor: Long)

/** Advances and repayments both move real money between travelers, not trip spending. */
enum class TransferKind { ADVANCE, REPAYMENT }

data class Transfer(
    val id: String,
    val groupId: String,
    val fromMember: String,
    val toMember: String,
    val amountMinor: Long,
    val note: String,
    val timestamp: Long,
    val kind: TransferKind = TransferKind.REPAYMENT
)

data class GroupDetail(
    val group: SplitGroup,
    val members: List<Member>,
    val expenses: List<Expense>,
    val allocations: List<Allocation>,
    val transfers: List<Transfer>,
    val contributions: List<Contribution> = emptyList()
)

data class Settlement(val fromMember: String, val toMember: String, val amountMinor: Long)
