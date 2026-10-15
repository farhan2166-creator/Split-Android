package com.splitapp.data

import com.splitapp.model.*
import org.junit.Assert.*
import org.junit.Test

class BackupCodecTest {
    private val group = SplitGroup("g", "October trip", "PKR", 100L, archived = true)
    private val members = listOf(Member("a", "g", "Ali"), Member("b", "g", "Bina"))
    private val expense = Expense("e", "g", "Hotel", "Stay", 10001L, "a", "Two payers", 500L)
    private val detail = GroupDetail(
        group, members, listOf(expense),
        listOf(Allocation("e", "a", 5001L), Allocation("e", "b", 5000L)),
        listOf(Transfer("t", "g", "b", "a", 2500L, "Paid", 600L)),
        listOf(Contribution("e", "a", 7000L), Contribution("e", "b", 3001L))
    )

    @Test fun completeRoundTripKeepsEveryLedgerRecord() {
        val original = BackupSnapshot(listOf(detail))
        val restored = BackupCodec.decode(BackupCodec.encode(original))
        assertEquals(original, restored)
        assertEquals(
            SplitMath.balances(detail),
            SplitMath.balances(restored.groups.single())
        )
        assertTrue(restored.groups.single().group.archived)
    }

    @Test fun monthlyBudgetSurvivesFullBackupRoundTrip() {
        val withBudget = detail.copy(group = detail.group.copy(monthlyBudgetMinor = 250000L))
        val restored = BackupCodec.decode(BackupCodec.encode(BackupSnapshot(listOf(withBudget))))
        assertEquals(250000L, restored.groups.single().group.monthlyBudgetMinor)
        assertEquals(SplitMath.balances(withBudget),
            SplitMath.balances(restored.groups.single()))
    }

    @Test fun olderBackupWithoutBudgetDefaultsToNull() {
        val original = BackupCodec.encode(BackupSnapshot(listOf(detail)))
        // Existing archives have no budget key; decoding must preserve compatibility.
        val decoded = BackupCodec.decode(original)
        assertNull(decoded.groups.single().group.monthlyBudgetMinor)
    }

    @Test fun emptyBackupRoundTrips() {
        assertEquals(BackupSnapshot(emptyList()),
            BackupCodec.decode(BackupCodec.encode(BackupSnapshot(emptyList()))))
    }

    @Test fun checksumDetectsTampering() {
        val original = BackupCodec.encode(BackupSnapshot(listOf(detail)))
        val tampered = original.replace("split-android-ledger", "split-android-ledger2")
        try {
            BackupCodec.decode(tampered)
            fail("Tampering was accepted")
        } catch (_: IllegalArgumentException) {
            // Format mismatch is rejected before processing the payload.
        }
        val changedChecksum = original.replace(
            Regex("\\\"checksumSha256\\\"\\s*:\\s*\\\"[0-9a-f]+\\\""),
            "\"checksumSha256\": \"0000000000000000000000000000000000000000000000000000000000000000\""
        )
        assertNotEquals(original, changedChecksum)
        try {
            BackupCodec.decode(changedChecksum)
            fail("Damaged checksum was accepted")
        } catch (_: IllegalArgumentException) {
            // No records may be trusted after an integrity mismatch.
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsExpenseSharesThatDoNotMatchTotal() {
        BackupCodec.validate(BackupSnapshot(listOf(detail.copy(
            allocations = listOf(Allocation("e", "a", 1))
        ))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsContributorTotalsThatDoNotMatchExpense() {
        BackupCodec.validate(BackupSnapshot(listOf(detail.copy(
            contributions = listOf(Contribution("e", "a", 500L))
        ))))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsDuplicateParticipantIdentifiersAcrossGroups() {
        val second = detail.copy(
            group = SplitGroup("g2", "Second", "USD", 0),
            members = listOf(Member("a", "g2", "Same ID")),
            expenses = emptyList(), allocations = emptyList(),
            transfers = emptyList(), contributions = emptyList()
        )
        BackupCodec.validate(BackupSnapshot(listOf(detail, second)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsParticipantFromAnotherGroup() {
        BackupCodec.validate(BackupSnapshot(listOf(detail.copy(
            members = listOf(Member("a", "other", "Ali"), members[1])
        ))))
    }
}
