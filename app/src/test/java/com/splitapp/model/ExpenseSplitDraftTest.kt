package com.splitapp.model

import org.junit.Assert.*
import org.junit.Test

class ExpenseSplitDraftTest {
    private val people = listOf("a", "b", "c")

    @Test fun equalAndWeightedSplitsUseExactMinorUnits() {
        assertEquals(mapOf("a" to 334L, "b" to 333L, "c" to 333L),
            ExpenseSplitDraft.allocations(1000L, people, ExpenseSplitMode.Equal, emptyMap()))
        assertEquals(mapOf("a" to 500L, "b" to 300L, "c" to 200L),
            ExpenseSplitDraft.allocations(1000L, people, ExpenseSplitMode.Shares,
                mapOf("a" to "5", "b" to "3", "c" to "2")))
    }

    @Test fun percentagesMustSumExactlyToOneHundred() {
        val values = mapOf("a" to "33.33", "b" to "33.33", "c" to "33.34")
        val split = ExpenseSplitDraft.allocations(1001L, people, ExpenseSplitMode.Percent, values)
        assertNotNull(split)
        assertEquals(1001L, split!!.values.sum())
        assertEquals(10000L, ExpenseSplitDraft.enteredTotal(people, ExpenseSplitMode.Percent, values))
        assertNull(ExpenseSplitDraft.allocations(1001L, people, ExpenseSplitMode.Percent,
            values + ("c" to "33.33")))
        assertNull(ExpenseSplitDraft.allocations(1001L, people, ExpenseSplitMode.Percent,
            values + ("c" to "0")))
    }

    @Test fun exactSplitsAllowZeroButRequireFullAllocation() {
        val amounts = mapOf("a" to "5.01", "b" to "5.00", "c" to "0")
        assertEquals(mapOf("a" to 501L, "b" to 500L, "c" to 0L),
            ExpenseSplitDraft.allocations(1001L, people, ExpenseSplitMode.Exact, amounts))
        assertNull(ExpenseSplitDraft.allocations(1000L, people, ExpenseSplitMode.Exact, amounts))
        assertEquals(1001L, ExpenseSplitDraft.enteredTotal(people, ExpenseSplitMode.Exact, amounts))
    }

    @Test fun invalidCustomEntriesAndDuplicateParticipantsAreRejected() {
        assertNull(ExpenseSplitDraft.allocations(100L, listOf("a","a"), ExpenseSplitMode.Equal, emptyMap()))
        assertNull(ExpenseSplitDraft.allocations(100L, people, ExpenseSplitMode.Shares,
            mapOf("a" to "1", "b" to "0", "c" to "1")))
        assertNull(ExpenseSplitDraft.allocations(100L, people, ExpenseSplitMode.Exact,
            mapOf("a" to "1.00", "b" to "bad", "c" to "0")))
        assertNull(ExpenseSplitDraft.enteredTotal(people, ExpenseSplitMode.Exact,
            mapOf("a" to "1.00", "b" to "x", "c" to "")))
    }

    @Test fun newSelectionDefaultsDoNotMutateExistingShares() {
        val inputs = mutableMapOf("a" to "2", "b" to "3")
        inputs["c"] = ExpenseSplitDraft.newParticipantInput(ExpenseSplitMode.Shares)
        assertEquals("2", inputs["a"])
        assertEquals("3", inputs["b"])
        assertEquals("1", inputs["c"])
        assertEquals("0", ExpenseSplitDraft.newParticipantInput(ExpenseSplitMode.Percent))
        assertEquals("0", ExpenseSplitDraft.newParticipantInput(ExpenseSplitMode.Exact))
    }

    @Test fun currencyInputAcceptsProperlyGroupedNumbersNotMalformedOnes() {
        assertEquals(123456789L, Money.parse("1,234,567.89"))
        assertEquals(100000L, Money.parse("1,000"))
        assertEquals(123456L, Money.parse("1234.56"))
        assertNull(Money.parse("12,34.56"))
        assertNull(Money.parse("1,23,456"))
        assertNull(Money.parse("1,000.999"))
        assertNull(Money.parse("-1,000"))
    }
}
