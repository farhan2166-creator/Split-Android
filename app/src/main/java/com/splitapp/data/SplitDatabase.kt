package com.splitapp.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.splitapp.model.*
import java.util.UUID

/** On-device source of truth. Every ledger write that spans tables is transactional. */
class SplitDatabase(context: Context) : SQLiteOpenHelper(context, "split_v1.db", null, 4) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE groups (id TEXT PRIMARY KEY, name TEXT NOT NULL, currency TEXT NOT NULL, created_at INTEGER NOT NULL, archived INTEGER NOT NULL DEFAULT 0, monthly_budget_minor INTEGER CHECK(monthly_budget_minor > 0))""")
        db.execSQL("""CREATE TABLE members (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE, name TEXT NOT NULL COLLATE NOCASE, UNIQUE(group_id, name))""")
        db.execSQL("""CREATE TABLE expenses (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE, title TEXT NOT NULL, category TEXT NOT NULL, amount_minor INTEGER NOT NULL CHECK(amount_minor > 0), paid_by TEXT NOT NULL REFERENCES members(id), note TEXT NOT NULL, timestamp INTEGER NOT NULL)""")
        db.execSQL("""CREATE TABLE allocations (expense_id TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE, member_id TEXT NOT NULL REFERENCES members(id), amount_minor INTEGER NOT NULL CHECK(amount_minor >= 0), PRIMARY KEY (expense_id, member_id))""")
        db.execSQL("""CREATE TABLE transfers (id TEXT PRIMARY KEY, group_id TEXT NOT NULL REFERENCES groups(id) ON DELETE CASCADE, from_member TEXT NOT NULL REFERENCES members(id), to_member TEXT NOT NULL REFERENCES members(id), amount_minor INTEGER NOT NULL CHECK(amount_minor > 0), note TEXT NOT NULL, timestamp INTEGER NOT NULL, CHECK(from_member != to_member))""")
        db.execSQL("CREATE TABLE contributions (expense_id TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE, member_id TEXT NOT NULL REFERENCES members(id), amount_minor INTEGER NOT NULL CHECK(amount_minor > 0), PRIMARY KEY(expense_id,member_id))")
        db.execSQL("CREATE INDEX expenses_by_group ON expenses(group_id, timestamp DESC)")
        db.execSQL("CREATE INDEX transfers_by_group ON transfers(group_id, timestamp DESC)")
        db.execSQL("CREATE INDEX members_by_group ON members(group_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("CREATE TABLE contributions (expense_id TEXT NOT NULL REFERENCES expenses(id) ON DELETE CASCADE, member_id TEXT NOT NULL REFERENCES members(id), amount_minor INTEGER NOT NULL CHECK(amount_minor > 0), PRIMARY KEY(expense_id,member_id))")
            db.execSQL("INSERT INTO contributions(expense_id,member_id,amount_minor) SELECT id,paid_by,amount_minor FROM expenses")
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE groups ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE groups ADD COLUMN monthly_budget_minor INTEGER CHECK(monthly_budget_minor > 0)")
        }
    }

    fun groups(): List<SplitGroup> = readableDatabase.rawQuery(
        "SELECT id, name, currency, created_at, archived, monthly_budget_minor FROM groups ORDER BY created_at DESC", null
    ).use { c -> buildList { while (c.moveToNext()) add(c.asGroup()) } }

    fun group(id: String): GroupDetail? {
        val db = readableDatabase
        val group = db.rawQuery("SELECT id, name, currency, created_at, archived, monthly_budget_minor FROM groups WHERE id=?", arrayOf(id))
            .use { c -> if (c.moveToFirst()) c.asGroup() else null } ?: return null
        val members = db.rawQuery("SELECT id, group_id, name FROM members WHERE group_id=? ORDER BY name COLLATE NOCASE", arrayOf(id))
            .use { c -> buildList { while (c.moveToNext()) add(Member(c.getString(0), c.getString(1), c.getString(2))) } }
        val expenses = db.rawQuery("SELECT id, group_id, title, category, amount_minor, paid_by, note, timestamp FROM expenses WHERE group_id=? ORDER BY timestamp DESC, id", arrayOf(id))
            .use { c -> buildList { while (c.moveToNext()) add(Expense(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5), c.getString(6), c.getLong(7))) } }
        val allocations = db.rawQuery("SELECT a.expense_id, a.member_id, a.amount_minor FROM allocations a JOIN expenses e ON e.id=a.expense_id WHERE e.group_id=?", arrayOf(id))
            .use { c -> buildList { while (c.moveToNext()) add(Allocation(c.getString(0), c.getString(1), c.getLong(2))) } }
        val transfers = db.rawQuery("SELECT id, group_id, from_member, to_member, amount_minor, note, timestamp FROM transfers WHERE group_id=? ORDER BY timestamp DESC, id", arrayOf(id))
            .use { c -> buildList { while (c.moveToNext()) add(Transfer(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getLong(4), c.getString(5), c.getLong(6))) } }
        val contributions = db.rawQuery("SELECT c.expense_id,c.member_id,c.amount_minor FROM contributions c JOIN expenses e ON e.id=c.expense_id WHERE e.group_id=?", arrayOf(id))
            .use { c -> buildList { while(c.moveToNext()) add(Contribution(c.getString(0),c.getString(1),c.getLong(2))) } }
        return GroupDetail(group, members, expenses, allocations, transfers, contributions)
    }

    /** Read all tables under one SQLite snapshot, including archived groups and multi-payer rows. */
    fun backupSnapshot(): BackupSnapshot {
        val db = readableDatabase
        db.beginTransactionNonExclusive()
        return try {
            val result = BackupSnapshot(groups().map { g ->
                group(g.id) ?: error("Missing group during backup")
            })
            BackupCodec.validate(result)
            db.setTransactionSuccessful()
            result
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Restore is all-or-nothing. Every record is validated before the transaction.
     * MERGE_MISSING does not overwrite groups with the same id. Incoming identifiers
     * must not conflict with existing records, even if they occur in another group.
     */
    fun restoreBackup(snapshot: BackupSnapshot, mode: RestoreMode): RestoreResult {
        BackupCodec.validate(snapshot)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val existing = db.rawQuery("SELECT id FROM groups", null).use { c ->
                buildSet { while (c.moveToNext()) add(c.getString(0)) }
            }
            val incoming = when (mode) {
                RestoreMode.MERGE_MISSING -> snapshot.groups.filter { it.group.id !in existing }
                RestoreMode.REPLACE_ALL -> snapshot.groups
            }
            if (mode == RestoreMode.REPLACE_ALL) {
                db.delete("groups", null, null)
            } else {
                // Validate identifier collisions *before* inserting any rows.
                fun existingIds(table: String): Set<String> =
                    db.rawQuery("SELECT id FROM $table", null).use { c ->
                        buildSet { while (c.moveToNext()) add(c.getString(0)) }
                    }
                val members = existingIds("members")
                val expenses = existingIds("expenses")
                val transfers = existingIds("transfers")
                require(incoming.none { g ->
                    g.members.any { it.id in members } ||
                    g.expenses.any { it.id in expenses } ||
                    g.transfers.any { it.id in transfers }
                }) { "Backup record IDs conflict with existing groups; no data imported" }
            }
            incoming.forEach { detail ->
                val g = detail.group
                db.insertOrThrow("groups", null, cv(
                    "id" to g.id, "name" to g.name, "currency" to g.currency,
                    "created_at" to g.createdAt, "archived" to if (g.archived) 1 else 0
                ))
                if (g.monthlyBudgetMinor != null) {
                    db.update("groups", cv("monthly_budget_minor" to g.monthlyBudgetMinor), "id=?", arrayOf(g.id))
                }
                detail.members.forEach { m ->
                    db.insertOrThrow("members", null, cv(
                        "id" to m.id, "group_id" to m.groupId, "name" to m.name
                    ))
                }
                detail.expenses.forEach { e ->
                    db.insertOrThrow("expenses", null, cv(
                        "id" to e.id, "group_id" to e.groupId,
                        "title" to e.title, "category" to e.category,
                        "amount_minor" to e.amountMinor, "paid_by" to e.paidBy,
                        "note" to e.note, "timestamp" to e.timestamp
                    ))
                }
                detail.allocations.forEach { a ->
                    db.insertOrThrow("allocations", null, cv(
                        "expense_id" to a.expenseId, "member_id" to a.memberId,
                        "amount_minor" to a.amountMinor
                    ))
                }
                detail.contributions.forEach { c ->
                    db.insertOrThrow("contributions", null, cv(
                        "expense_id" to c.expenseId, "member_id" to c.memberId,
                        "amount_minor" to c.amountMinor
                    ))
                }
                detail.transfers.forEach { t ->
                    db.insertOrThrow("transfers", null, cv(
                        "id" to t.id, "group_id" to t.groupId,
                        "from_member" to t.fromMember, "to_member" to t.toMember,
                        "amount_minor" to t.amountMinor, "note" to t.note,
                        "timestamp" to t.timestamp
                    ))
                }
            }
            val result = RestoreResult(incoming.size, snapshot.groups.size - incoming.size)
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    fun createGroup(name: String, currency: String, initialMember: String): String {
        require(name.isNotBlank() && initialMember.isNotBlank())
        require(currency.matches(Regex("[A-Z]{3}")))
        val id = UUID.randomUUID().toString()
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.insertOrThrow("groups", null, cv("id" to id, "name" to name.trim(), "currency" to currency, "created_at" to System.currentTimeMillis()))
            db.insertOrThrow("members", null, cv("id" to UUID.randomUUID().toString(), "group_id" to id, "name" to initialMember.trim()))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
        return id
    }

    fun addMember(groupId: String, name: String) {
        require(name.isNotBlank())
        writableDatabase.insertOrThrow("members", null, cv("id" to UUID.randomUUID().toString(), "group_id" to groupId, "name" to name.trim()))
    }

    /** Renaming preserves stable member IDs used by past expenses, shares and repayments. */
    fun renameMember(groupId: String, memberId: String, name: String) {
        require(name.isNotBlank())
        check(writableDatabase.update(
            "members", cv("name" to name.trim()), "id=? AND group_id=?", arrayOf(memberId, groupId)
        ) == 1) { "Participant not found" }
    }

    fun setArchived(groupId: String, archived: Boolean) {
        check(writableDatabase.update("groups", cv("archived" to if (archived) 1 else 0), "id=?", arrayOf(groupId)) == 1)
    }

    fun setMonthlyBudget(groupId: String, amountMinor: Long?) {
        require(amountMinor == null || amountMinor > 0L)
        val db = writableDatabase
        db.execSQL("UPDATE groups SET monthly_budget_minor=? WHERE id=?", arrayOf(amountMinor, groupId))
    }

    fun renameGroup(groupId: String, name: String) {
        require(name.isNotBlank())
        check(writableDatabase.update("groups", cv("name" to name.trim()), "id=?", arrayOf(groupId)) == 1)
    }

    fun saveExpense(expense: Expense, allocations: List<Allocation>, contributions: List<Contribution> = listOf(Contribution(expense.id, expense.paidBy, expense.amountMinor))) {
        require(expense.title.isNotBlank() && expense.amountMinor > 0)
        require(allocations.isNotEmpty() && allocations.all { it.expenseId == expense.id && it.amountMinor >= 0 })
        require(allocations.map { it.memberId }.distinct().size == allocations.size)
        require(allocations.fold(0L) { sum, it -> Math.addExact(sum, it.amountMinor) } == expense.amountMinor)
        require(contributions.isNotEmpty() && contributions.all { it.expenseId == expense.id && it.amountMinor > 0 })
        require(contributions.map { it.memberId }.distinct().size == contributions.size)
        require(contributions.fold(0L) { sum, it -> Math.addExact(sum, it.amountMinor) } == expense.amountMinor)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = db.rawQuery("SELECT id FROM members WHERE group_id=?", arrayOf(expense.groupId))
                .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            require(expense.paidBy in ids && allocations.all { it.memberId in ids } && contributions.all { it.memberId in ids }) { "Unknown group participant" }
            val values = cv(
                "id" to expense.id, "group_id" to expense.groupId, "title" to expense.title.trim(),
                "category" to expense.category, "amount_minor" to expense.amountMinor, "paid_by" to expense.paidBy,
                "note" to expense.note.trim(), "timestamp" to expense.timestamp
            )
            val existing = db.update("expenses", values, "id=? AND group_id=?", arrayOf(expense.id, expense.groupId))
            if (existing == 0) db.insertOrThrow("expenses", null, values)
            db.delete("contributions", "expense_id=?", arrayOf(expense.id))
            contributions.forEach { part -> db.insertOrThrow("contributions", null, cv("expense_id" to part.expenseId, "member_id" to part.memberId, "amount_minor" to part.amountMinor)) }
            db.delete("allocations", "expense_id=?", arrayOf(expense.id))
            allocations.forEach { part -> db.insertOrThrow("allocations", null, cv("expense_id" to part.expenseId, "member_id" to part.memberId, "amount_minor" to part.amountMinor)) }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteExpense(groupId: String, expenseId: String) {
        check(writableDatabase.delete("expenses", "id=? AND group_id=?", arrayOf(expenseId, groupId)) == 1)
    }

    fun addTransfer(transfer: Transfer) {
        require(transfer.amountMinor > 0 && transfer.fromMember != transfer.toMember)
        val db = writableDatabase
        db.beginTransaction()
        try {
            val ids = db.rawQuery("SELECT id FROM members WHERE group_id=?", arrayOf(transfer.groupId))
                .use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
            require(transfer.fromMember in ids && transfer.toMember in ids)
            db.insertOrThrow("transfers", null, cv("id" to transfer.id, "group_id" to transfer.groupId, "from_member" to transfer.fromMember, "to_member" to transfer.toMember, "amount_minor" to transfer.amountMinor, "note" to transfer.note.trim(), "timestamp" to transfer.timestamp))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun deleteTransfer(groupId: String, transferId: String) {
        check(writableDatabase.delete("transfers", "id=? AND group_id=?", arrayOf(transferId, groupId)) == 1)
    }

    private fun Cursor.asGroup() = SplitGroup(getString(0), getString(1), getString(2), getLong(3), getInt(4) != 0, if (isNull(5)) null else getLong(5))

    private fun cv(vararg args: Pair<String, Any>): ContentValues = ContentValues().apply {
        args.forEach { (key, value) -> when (value) {
            is String -> put(key, value)
            is Long -> put(key, value)
            is Int -> put(key, value)
            else -> error("Unsupported value")
        } }
    }
}
