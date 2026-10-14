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

    fun createGroup(name: String, currency: String, firstMember: String, done: (String) -> Unit) = perform({ database.createGroup(name, currency, firstMember) }, done)
    fun addMember(groupId: String, name: String, done: () -> Unit = {}) = perform({ database.addMember(groupId, name) }, { done() })
    fun renameGroup(groupId: String, name: String, done: () -> Unit = {}) = perform({ database.renameGroup(groupId, name) }, { done() })
    fun saveExpense(expense: Expense, shares: List<Allocation>, done: () -> Unit) = perform({ database.saveExpense(expense, shares) }, { done() })
    fun deleteExpense(groupId: String, expenseId: String, done: () -> Unit) = perform({ database.deleteExpense(groupId, expenseId) }, { done() })
    fun addTransfer(transfer: Transfer, done: () -> Unit) = perform({ database.addTransfer(transfer) }, { done() })
    fun deleteTransfer(groupId: String, transferId: String) = perform({ database.deleteTransfer(groupId, transferId) }, {})

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
