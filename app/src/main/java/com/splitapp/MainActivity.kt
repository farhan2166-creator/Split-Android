package com.splitapp

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitapp.data.SplitViewModel
import com.splitapp.model.GroupDetail
import com.splitapp.ui.ExpenseEditor
import com.splitapp.ui.GroupScreen
import com.splitapp.ui.LoadingPage
import com.splitapp.ui.GroupsScreen
import com.splitapp.ui.PaymentEditor
import kotlinx.coroutines.launch

private val palette = lightColorScheme(
    primary = Color(0xFF008577), onPrimary = Color.White,
    secondary = Color(0xFFD97706), surface = Color.White,
    background = Color(0xFFF7F5F1), onBackground = Color(0xFF173D35),
    onSurface = Color(0xFF173D35), surfaceVariant = Color(0xFFF1ECE3)
)

sealed interface Screen {
    data object Home : Screen
    data class Group(val id: String) : Screen
    data class ExpenseForm(val groupId: String, val expenseId: String? = null) : Screen
    data class PaymentForm(val groupId: String, val from: String? = null, val to: String? = null, val amount: Long? = null) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = palette) {
                val vm: SplitViewModel = viewModel()
                val groups by vm.groups.collectAsState()
                val detail by vm.detail.collectAsState()
                val message by vm.message.collectAsState()
                val hostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var pendingCsv by remember { mutableStateOf("") }
                val saveCsv = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("text/csv")
                ) { uri ->
                    if (uri != null) {
                        try {
                            contentResolver.openOutputStream(uri)?.use { stream ->
                                stream.write(pendingCsv.toByteArray(Charsets.UTF_8))
                            } ?: error("Unable to open document")
                            vm.showMessage("CSV saved successfully")
                        } catch (_: Exception) {
                            vm.showMessage("Could not save the CSV file")
                        }
                    }
                }
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val goGroup: (String) -> Unit = { id ->
                    vm.openGroup(id)
                    screen = Screen.Group(id)
                }
                val back: () -> Unit = {
                    screen = when (val current = screen) {
                        is Screen.ExpenseForm -> Screen.Group(current.groupId)
                        is Screen.PaymentForm -> Screen.Group(current.groupId)
                        is Screen.Group -> Screen.Home
                        Screen.Home -> Screen.Home
                    }
                }
                BackHandler(screen != Screen.Home) { back() }
                LaunchedEffect(message) {
                    if (!message.isNullOrBlank()) {
                        hostState.showSnackbar(message!!)
                        vm.clearMessage()
                    }
                }
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                    when (val current = screen) {
                        Screen.Home -> GroupsScreen(groups, vm, goGroup)
                        is Screen.Group -> {
                            val item = detail?.takeIf { it.group.id == current.id }
                            if (item == null) {
                                LoadingPage(back)
                            } else GroupScreen(
                                detail = item,
                                vm = vm,
                                onBack = back,
                                onExpense = { id -> screen = Screen.ExpenseForm(current.id, id) },
                                onPayment = { from, to, amount -> screen = Screen.PaymentForm(current.id, from, to, amount) },
                                onShare = { text, mime ->
                                    if (mime == "text/csv") {
                                        pendingCsv = text
                                        saveCsv.launch("split-transactions.csv")
                                    } else {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = mime
                                            putExtra(Intent.EXTRA_TEXT, text)
                                        }
                                        startActivity(Intent.createChooser(intent, "Share from Split"))
                                    }
                                }
                            )
                        }
                        is Screen.ExpenseForm -> {
                            val item = detail?.takeIf { it.group.id == current.groupId }
                            if (item == null) LoadingPage(back)
                            else ExpenseEditor(item, current.expenseId, vm, back)
                        }
                        is Screen.PaymentForm -> {
                            val item = detail?.takeIf { it.group.id == current.groupId }
                            if (item == null) LoadingPage(back)
                            else PaymentEditor(item, current.from, current.to, current.amount, vm, back)
                        }
                    }
                    SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
