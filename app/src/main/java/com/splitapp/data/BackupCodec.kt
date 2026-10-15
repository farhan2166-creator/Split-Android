package com.splitapp.data

import com.splitapp.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale

/** Lossless, versioned, checksummed offline backup. SHA-256 detects corruption, not malicious changes. */
data class BackupSnapshot(val groups: List<GroupDetail>)
enum class RestoreMode { MERGE_MISSING, REPLACE_ALL }
data class RestoreResult(val imported: Int, val skipped: Int)

object BackupCodec {
    private const val FORMAT = "split-android-ledger"
    private const val FORMAT_VERSION = 1
    private const val MAX_BACKUP_CHARS = 28_000_000
    private const val MAX_PAYLOAD_BYTES = 20_000_000
    private const val MAX_GROUPS = 2000
    private const val MAX_RECORDS = 250_000

    fun encode(snapshot: BackupSnapshot): String {
        validate(snapshot)
        val payload = JSONObject().put("groups", JSONArray().apply {
            snapshot.groups.forEach { d ->
                val g = d.group
                put(JSONObject()
                    .put("group", JSONObject().put("id", g.id).put("name", g.name)
                        .put("currency", g.currency).put("createdAt", g.createdAt.toString())
                        .put("archived", g.archived)
                        .put("monthlyBudgetMinor", g.monthlyBudgetMinor?.toString()))
                    .put("members", JSONArray().apply {
                        d.members.forEach { m -> put(JSONObject().put("id", m.id)
                            .put("groupId", m.groupId).put("name", m.name)) }
                    })
                    .put("expenses", JSONArray().apply {
                        d.expenses.forEach { e -> put(JSONObject().put("id", e.id)
                            .put("groupId", e.groupId).put("title", e.title)
                            .put("category", e.category).put("amountMinor", e.amountMinor.toString())
                            .put("paidBy", e.paidBy).put("note", e.note)
                            .put("timestamp", e.timestamp.toString())) }
                    })
                    .put("allocations", JSONArray().apply {
                        d.allocations.forEach { a -> put(JSONObject().put("expenseId", a.expenseId)
                            .put("memberId", a.memberId).put("amountMinor", a.amountMinor.toString())) }
                    })
                    .put("contributions", JSONArray().apply {
                        d.contributions.forEach { a -> put(JSONObject().put("expenseId", a.expenseId)
                            .put("memberId", a.memberId).put("amountMinor", a.amountMinor.toString())) }
                    })
                    .put("transfers", JSONArray().apply {
                        d.transfers.forEach { t -> put(JSONObject().put("id", t.id)
                            .put("groupId", t.groupId).put("fromMember", t.fromMember)
                            .put("toMember", t.toMember).put("amountMinor", t.amountMinor.toString())
                            .put("note", t.note).put("timestamp", t.timestamp.toString())) }
                    }))
            }
        }).toString()
        val bytes = payload.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Backup is too large" }
        return JSONObject()
            .put("format", FORMAT).put("version", FORMAT_VERSION)
            .put("checksumSha256", sha256(bytes))
            .put("payloadBase64", Base64.getEncoder().encodeToString(bytes))
            .toString(2)
    }

    fun decode(text: String): BackupSnapshot {
        require(text.length <= MAX_BACKUP_CHARS) { "Backup exceeds size limit" }
        val envelope = JSONObject(text)
        require(envelope.getString("format") == FORMAT) { "Not a Split Android backup" }
        require(envelope.getInt("version") == FORMAT_VERSION) { "Unsupported backup format version" }
        val base64 = envelope.getString("payloadBase64")
        require(base64.length <= MAX_BACKUP_CHARS) { "Backup payload is too large" }
        val bytes = try {
            Base64.getDecoder().decode(base64)
        } catch (_: IllegalArgumentException) {
            throw IllegalArgumentException("Backup payload is corrupted")
        }
        require(bytes.size <= MAX_PAYLOAD_BYTES) { "Backup exceeds size limit" }
        require(sha256(bytes).equals(envelope.getString("checksumSha256"), ignoreCase = true)) {
            "Backup checksum mismatch: file was damaged or modified"
        }
        val array = JSONObject(String(bytes, Charsets.UTF_8)).getJSONArray("groups")
        require(array.length() <= MAX_GROUPS) { "Too many groups in backup" }
        var count = 0
        fun JSONArray.checked(): JSONArray {
            count += length()
            require(count <= MAX_RECORDS) { "Too many records in backup" }
            return this
        }
        val groups = buildList {
            for (i in 0 until array.length()) {
                val data = array.getJSONObject(i)
                val g = data.getJSONObject("group")
                val group = SplitGroup(g.getString("id"), g.getString("name"),
                    g.getString("currency"), g.longString("createdAt"), g.getBoolean("archived"),
                    if (g.isNull("monthlyBudgetMinor")) null else g.longString("monthlyBudgetMinor"))
                val memberArray = data.getJSONArray("members").checked()
                val members = buildList {
                    for (j in 0 until memberArray.length()) {
                        val m = memberArray.getJSONObject(j)
                        add(Member(m.getString("id"), m.getString("groupId"), m.getString("name")))
                    }
                }
                val expenseArray = data.getJSONArray("expenses").checked()
                val expenses = buildList {
                    for (j in 0 until expenseArray.length()) {
                        val e = expenseArray.getJSONObject(j)
                        add(Expense(e.getString("id"), e.getString("groupId"), e.getString("title"),
                            e.getString("category"), e.longString("amountMinor"),
                            e.getString("paidBy"), e.getString("note"), e.longString("timestamp")))
                    }
                }
                val allocationArray = data.getJSONArray("allocations").checked()
                val allocations = buildList {
                    for (j in 0 until allocationArray.length()) {
                        val a = allocationArray.getJSONObject(j)
                        add(Allocation(a.getString("expenseId"), a.getString("memberId"),
                            a.longString("amountMinor")))
                    }
                }
                val contributionArray = data.getJSONArray("contributions").checked()
                val contributions = buildList {
                    for (j in 0 until contributionArray.length()) {
                        val a = contributionArray.getJSONObject(j)
                        add(Contribution(a.getString("expenseId"), a.getString("memberId"),
                            a.longString("amountMinor")))
                    }
                }
                val transferArray = data.getJSONArray("transfers").checked()
                val transfers = buildList {
                    for (j in 0 until transferArray.length()) {
                        val t = transferArray.getJSONObject(j)
                        add(Transfer(t.getString("id"), t.getString("groupId"),
                            t.getString("fromMember"), t.getString("toMember"),
                            t.longString("amountMinor"), t.getString("note"), t.longString("timestamp")))
                    }
                }
                add(GroupDetail(group, members, expenses, allocations, transfers, contributions))
            }
        }
        return BackupSnapshot(groups).also(::validate)
    }

    /** Validate every relationship, invariant and identifier before writing to SQLite. */
    fun validate(snapshot: BackupSnapshot) {
        require(snapshot.groups.size <= MAX_GROUPS) { "Too many groups in backup" }
        val groupIds = mutableSetOf<String>()
        val memberIdsGlobal = mutableSetOf<String>()
        val expenseIdsGlobal = mutableSetOf<String>()
        val transferIdsGlobal = mutableSetOf<String>()
        var count = 0
        snapshot.groups.forEach { d ->
            val g = d.group
            validId(g.id)
            require(groupIds.add(g.id)) { "Duplicate group identifier" }
            validLabel(g.name)
            require(g.currency.matches(Regex("[A-Z]{3}"))) { "Invalid currency in ${g.name}" }
            require(g.createdAt >= 0) { "Invalid group date" }
            require(g.monthlyBudgetMinor == null || g.monthlyBudgetMinor > 0L) { "Invalid group budget" }
            require(d.members.isNotEmpty()) { "Group ${g.name} has no participants" }
            val memberIds = mutableSetOf<String>()
            val names = mutableSetOf<String>()
            d.members.forEach { m ->
                validId(m.id)
                require(m.groupId == g.id && memberIds.add(m.id) && memberIdsGlobal.add(m.id)) {
                    "Duplicate or mismatched member in ${g.name}"
                }
                validLabel(m.name)
                require(names.add(m.name.trim().lowercase(Locale.ROOT))) {
                    "Duplicate participant name in ${g.name}"
                }
            }
            val expenses = mutableMapOf<String, Expense>()
            d.expenses.forEach { e ->
                validId(e.id)
                require(e.groupId == g.id && expenseIdsGlobal.add(e.id)) {
                    "Duplicate or mismatched expense in ${g.name}"
                }
                validLabel(e.title)
                validText(e.category)
                validText(e.note)
                require(e.amountMinor > 0 && e.timestamp >= 0 && e.paidBy in memberIds) {
                    "Invalid expense in ${g.name}"
                }
                expenses[e.id] = e
            }
            val allocations = mutableMapOf<String, Long>()
            val shareKeys = mutableSetOf<Pair<String, String>>()
            d.allocations.forEach { a ->
                require(a.expenseId in expenses && a.memberId in memberIds &&
                    shareKeys.add(a.expenseId to a.memberId) && a.amountMinor >= 0) {
                    "Invalid expense share in ${g.name}"
                }
                allocations[a.expenseId] =
                    Math.addExact(allocations[a.expenseId] ?: 0L, a.amountMinor)
            }
            val contributions = mutableMapOf<String, Long>()
            val fundingKeys = mutableSetOf<Pair<String, String>>()
            d.contributions.forEach { c ->
                require(c.expenseId in expenses && c.memberId in memberIds &&
                    fundingKeys.add(c.expenseId to c.memberId) && c.amountMinor > 0) {
                    "Invalid contributor in ${g.name}"
                }
                contributions[c.expenseId] =
                    Math.addExact(contributions[c.expenseId] ?: 0L, c.amountMinor)
            }
            expenses.values.forEach { e ->
                require(allocations[e.id] == e.amountMinor) {
                    "Expense shares do not match in ${g.name}"
                }
                // Older single-payer ledgers may have no explicit contribution rows.
                if (contributions.containsKey(e.id)) {
                    require(contributions[e.id] == e.amountMinor) {
                        "Expense contributions do not match in ${g.name}"
                    }
                }
            }
            d.transfers.forEach { t ->
                validId(t.id)
                validText(t.note)
                require(transferIdsGlobal.add(t.id) && t.groupId == g.id &&
                    t.fromMember in memberIds && t.toMember in memberIds &&
                    t.fromMember != t.toMember && t.amountMinor > 0 && t.timestamp >= 0) {
                    "Invalid repayment in ${g.name}"
                }
            }
            SplitMath.balances(d)
            count += d.members.size + d.expenses.size + d.allocations.size +
                d.contributions.size + d.transfers.size
            require(count <= MAX_RECORDS) { "Too many records in backup" }
        }
    }

    private fun validId(id: String) {
        require(id.isNotBlank() && id.length <= 128) { "Invalid record ID" }
    }
    private fun validLabel(text: String) {
        require(text.isNotBlank() && text.length <= 2048) { "Invalid name or description" }
    }
    private fun validText(text: String) {
        require(text.length <= 20000) { "Record text exceeds supported limit" }
    }
    private fun JSONObject.longString(key: String): Long =
        getString(key).toLongOrNull() ?: throw IllegalArgumentException("Invalid number: $key")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") {
            "%02x".format(it.toInt() and 0xff)
        }
}
