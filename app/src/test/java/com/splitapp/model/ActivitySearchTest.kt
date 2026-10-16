package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

class ActivitySearchTest {
    private val group = SplitGroup("g", "Tour", "PKR", 1L)
    private val people = listOf(
        Member("a", "g", "Asim"), Member("b", "g", "Farhan"),
        Member("c", "g", "Tahir")
    )
    private val expenses = listOf(
        Expense("e1", "g", "Hotel", "Stay", 9000L, "a", "Room 11", 100L),
        Expense("e2", "g", "Fuel", "Transport", 3000L, "b", "Petrol", 400L)
    )
    private val transfers = listOf(
        Transfer("t1", "g", "b", "a", 500L, "Cash on arrival", 200L, TransferKind.ADVANCE),
        Transfer("t2", "g", "c", "b", 200L, "Partial return", 300L, TransferKind.REPAYMENT)
    )
    private val detail = GroupDetail(
        group, people, expenses,
        listOf(Allocation("e1", "a", 3000L), Allocation("e1", "b", 3000L),
            Allocation("e1", "c", 3000L), Allocation("e2", "b", 3000L)),
        transfers,
        listOf(Contribution("e1", "a", 6000L), Contribution("e1", "c", 3000L))
    )

    @Test fun allActivityIsNewestFirst() {
        val ids = ActivitySearch.filter(detail, ActivityKind.All).map {
            when(it) { is Expense -> it.id; is Transfer -> it.id; else -> "" }
        }
        assertEquals(listOf("e2", "t2", "t1", "e1"), ids)
    }

    @Test fun filtersSeparateExpensesAdvancesAndRepayments() {
        assertEquals(2, ActivitySearch.filter(detail, ActivityKind.Expenses).size)
        assertEquals(listOf("t1"),
            ActivitySearch.filter(detail, ActivityKind.Advances).filterIsInstance<Transfer>().map { it.id })
        assertEquals(listOf("t2"),
            ActivitySearch.filter(detail, ActivityKind.Repayments).filterIsInstance<Transfer>().map { it.id })
        assertEquals(listOf("e1"),
            ActivitySearch.filter(detail, ActivityKind.All, category = "Stay")
                .filterIsInstance<Expense>().map { it.id })
        assertEquals(1, ActivitySearch.filter(detail, ActivityKind.All, category = "Stay").size)
    }

    @Test fun textMatchesNotesPayersParticipantsAndTransferKinds() {
        assertEquals(1, ActivitySearch.filter(detail, ActivityKind.Expenses, "Room 11").size)
        assertEquals(1, ActivitySearch.filter(detail, ActivityKind.Expenses, "Petrol").size)
        assertEquals(1, ActivitySearch.filter(detail, ActivityKind.Expenses, "Tahir").size)
        assertEquals(listOf("t1"),
            ActivitySearch.filter(detail, ActivityKind.All, "advance")
                .filterIsInstance<Transfer>().map { it.id })
        assertEquals(listOf("t2"),
            ActivitySearch.filter(detail, ActivityKind.All, "partial")
                .filterIsInstance<Transfer>().map { it.id })
        assertTrue(ActivitySearch.filter(detail, ActivityKind.Repayments, "Hotel").isEmpty())
    }

    @Test fun whitespaceQueryDoesNotHideActivity() {
        assertEquals(4, ActivitySearch.filter(detail, ActivityKind.All, "   ").size)
        assertTrue(ActivitySearch.filter(detail, ActivityKind.All, "Not in this trip").isEmpty())
    }
}
