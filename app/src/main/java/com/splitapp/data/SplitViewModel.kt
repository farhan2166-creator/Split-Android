package com.splitapp.data

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.splitapp.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SplitViewModel(application: Application) : AndroidViewModel(application) {
    private val database = SplitDatabase(application)
    private val _groups = MutableStateFlow<List<SplitGroup>>(emptyList())
    val groups = _groups.asStateFlow()
    private val _detail = MutableStateFlow<GroupDetail?>(null)
    val detail = _detail.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private var selectedId: String? = null

    init { refresh() }

    fun showMessage(message: String) { _message.value = message }
    fun clearMessage() { _message.value = null }

    fun openGroup(groupId: String) {
        selectedId = groupId
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            try {
                val currentId = selectedId
                val result = withContext(Dispatchers.IO) {
                    Pair(database.groups(), currentId?.let { database.group(it) })
                }
                _groups.value = result.first
                if (currentId == selectedId) _detail.value = result.second
            } catch (e: Exception) { _message.value = e.message ?: "Unable to read saved expenses" }
        }
    }

    fun createGroup(name: String, currency: String, firstMember: String, done: (String) -> Unit) =
        perform({
            val id = database.createGroup(name, currency, firstMember)
            val creator = database.group(id)?.members?.singleOrNull()?.id
                ?: error("Unable to find creator after creating the group")
            id to creator
        }, { (id, creator) ->
            // This is local device identity, not a Gmail login or cloud membership.
            getApplication<Application>().getSharedPreferences(
                "split_group_identity", Application.MODE_PRIVATE
            ).edit().putString("member_$id", creator).apply()
            done(id)
        })
    fun addMember(groupId: String, name: String, done: () -> Unit = {}) = perform({ database.addMember(groupId, name) }, { done() })
    fun renameMember(groupId: String, memberId: String, name: String, done: () -> Unit = {}) = perform({ database.renameMember(groupId, memberId, name) }, { done() })
    fun setArchived(groupId: String, archived: Boolean, done: () -> Unit = {}) = perform({ database.setArchived(groupId, archived) }, { done() })
    fun setMonthlyBudget(groupId: String, amountMinor: Long?, done: () -> Unit = {}) = perform({ database.setMonthlyBudget(groupId, amountMinor) }, { done() })
    fun renameGroup(groupId: String, name: String, done: () -> Unit = {}) = perform({ database.renameGroup(groupId, name) }, { done() })
    fun saveExpense(expense: Expense, shares: List<Allocation>, done: () -> Unit, contributions: List<Contribution> = listOf(Contribution(expense.id, expense.paidBy, expense.amountMinor))) = perform({ database.saveExpense(expense, shares, contributions) }, { done() })
    fun deleteExpense(groupId: String, expenseId: String, done: () -> Unit) = perform({ database.deleteExpense(groupId, expenseId) }, { done() })
    fun addTransfer(transfer: Transfer, done: () -> Unit) = perform({ database.addTransfer(transfer) }, { done() })
    fun deleteTransfer(groupId: String, transferId: String) = perform({ database.deleteTransfer(groupId, transferId) }, {})

    fun exportBackup(done: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val content = withContext(Dispatchers.IO) {
                    BackupCodec.encode(database.backupSnapshot())
                }
                done(content)
            } catch (e: Exception) {
                _message.value = e.message ?: "Unable to create backup"
            }
        }
    }

    fun previewBackup(content: String, done: (BackupSnapshot) -> Unit) {
        viewModelScope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { BackupCodec.decode(content) }
                done(snapshot)
            } catch (e: Exception) {
                _message.value = "Backup validation failed: " + (e.message ?: "Invalid file")
            }
        }
    }

    fun restoreBackup(snapshot: BackupSnapshot, mode: RestoreMode, done: (RestoreResult) -> Unit) {
        perform({
            database.restoreBackup(snapshot, mode)
        }, { result ->
            selectedId = null
            _detail.value = null
            done(result)
        })
    }

    private fun <T> perform(action: () -> T, done: (T) -> Unit) {
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { action() }
                done(result)
                refresh()
            } catch (e: Exception) {
                _message.value = when {
                    e.message?.contains("UNIQUE constraint", ignoreCase = true) == true -> "This participant already exists"
                    else -> e.message ?: "Unable to save changes"
                }
            }
        }
    }

    override fun onCleared() {
        database.close()
        super.onCleared()
    }
}
