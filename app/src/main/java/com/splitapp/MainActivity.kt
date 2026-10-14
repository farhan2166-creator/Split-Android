package com.splitapp

import android.content.Intent
import android.os.Bundle
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.splitapp.data.SplitViewModel
import com.splitapp.model.GroupDetail
import com.splitapp.ui.ExpenseEditor
import com.splitapp.ui.GroupScreen
import com.splitapp.ui.LoadingPage
import com.splitapp.ui.GroupsScreen
import com.splitapp.ui.PaymentEditor
import com.splitapp.ui.LedgerScreen
import com.splitapp.model.LedgerReport
import com.splitapp.report.PdfLedgerWriter
import com.splitapp.report.LedgerPdfOptions
import com.splitapp.data.BackupSnapshot
import com.splitapp.ui.BackupRestoreDialog
import com.splitapp.ui.SplitTheme
import com.splitapp.ui.AppearanceMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    data class LedgerForm(val groupId: String) : Screen
    data class PaymentForm(val groupId: String, val from: String? = null, val to: String? = null, val amount: Long? = null) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appearancePrefs = getSharedPreferences("split_ui", MODE_PRIVATE)
        setContent {
            var appearance by remember {
                mutableStateOf(AppearanceMode.entries.firstOrNull {
                    it.name == appearancePrefs.getString("appearance", "SYSTEM")
                } ?: AppearanceMode.SYSTEM)
            }
            SplitTheme(appearance) {
                val hostView = LocalView.current
                val backgroundColor = MaterialTheme.colorScheme.background.toArgb()
                val isDarkAppearance = when (appearance) {
                    AppearanceMode.DARK -> true
                    AppearanceMode.LIGHT -> false
                    AppearanceMode.SYSTEM ->
                        (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
                            android.content.res.Configuration.UI_MODE_NIGHT_YES
                }
                SideEffect {
                    window.statusBarColor = backgroundColor
                    window.navigationBarColor = backgroundColor
                    WindowCompat.getInsetsController(window, hostView).apply {
                        isAppearanceLightStatusBars = !isDarkAppearance
                        isAppearanceLightNavigationBars = !isDarkAppearance
                    }
                }
                val vm: SplitViewModel = viewModel()
                val groups by vm.groups.collectAsState()
                val detail by vm.detail.collectAsState()
                val message by vm.message.collectAsState()
                val hostState = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                var pendingBackupText by remember { mutableStateOf<String?>(null) }
                var restoreCandidate by remember { mutableStateOf<BackupSnapshot?>(null) }
                val backupSave = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/json")
                ) { uri ->
                    val backupText = pendingBackupText
                    pendingBackupText = null
                    if (uri != null && backupText != null) scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                contentResolver.openOutputStream(uri)?.use { stream ->
                                    stream.write(backupText.toByteArray(Charsets.UTF_8))
                                } ?: error("Unable to create backup file")
                            }
                            vm.showMessage("Full backup saved")
                        } catch (e: Exception) {
                            vm.showMessage("Backup save failed: ${e.message ?: "Unknown error"}")
                        }
                    }
                }
                val backupOpen = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri ->
                    if (uri != null) scope.launch {
                        try {
                            val backupText = withContext(Dispatchers.IO) {
                                val input = contentResolver.openInputStream(uri)
                                    ?: error("Unable to open backup")
                                input.use { stream ->
                                    val output = ByteArrayOutputStream()
                                    val buffer = ByteArray(8192)
                                    while (true) {
                                        val n = stream.read(buffer)
                                        if (n < 0) break
                                        if (n == 0) continue
                                        require(output.size() + n <= 28_000_000) {
                                            "Selected backup exceeds the supported limit"
                                        }
                                        output.write(buffer, 0, n)
                                    }
                                    String(output.toByteArray(), Charsets.UTF_8)
                                }
                            }
                            vm.previewBackup(backupText) { restoreCandidate = it }
                        } catch (e: Exception) {
                            vm.showMessage("Unable to read backup: ${e.message ?: "Unknown error"}")
                        }
                    }
                }
                var pendingCsv by remember { mutableStateOf("") }
                var pendingLedger by remember { mutableStateOf<LedgerReport?>(null) }
                var pendingPdfOptions by remember { mutableStateOf(LedgerPdfOptions()) }
                val savePdf = rememberLauncherForActivityResult(
                    ActivityResultContracts.CreateDocument("application/pdf")
                ) { uri ->
                    val snapshot = pendingLedger
                    pendingLedger = null
                    if (uri != null && snapshot != null) {
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    contentResolver.openOutputStream(uri)?.use { stream ->
                                        PdfLedgerWriter.write(stream, snapshot, pendingPdfOptions)
                                    } ?: error("Unable to open PDF destination")
                                }
                                vm.showMessage("PDF ledger saved")
                            } catch (e: Exception) {
                                vm.showMessage("PDF export failed: ${e.message ?: "Unknown error"}")
                            }
                        }
                    }
                }
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
                        is Screen.LedgerForm -> Screen.Group(current.groupId)
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
                        Screen.Home -> GroupsScreen(
                            groups, vm, goGroup,
                            appearance = appearance,
                            onAppearanceChange = { selected ->
                                appearance = selected
                                appearancePrefs.edit().putString("appearance", selected.name).apply()
                            },
                            onExportBackup = {
                                vm.exportBackup { content ->
                                    pendingBackupText = content
                                    val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.ROOT).format(Date())
                                    backupSave.launch("Split-Backup-$stamp.json")
                                }
                            },
                            onImportBackup = { backupOpen.launch(arrayOf("*/*")) }
                        )
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
                                onLedger = { screen = Screen.LedgerForm(current.id) },
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
                        is Screen.LedgerForm -> {
                            val item = detail?.takeIf { it.group.id == current.groupId }
                            if (item == null) LoadingPage(back)
                            else LedgerScreen(item, back) { report, options ->
                                pendingPdfOptions = options
                                pendingLedger = report
                                savePdf.launch("Split-Ledger.pdf")
                            }
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
                    restoreCandidate?.let { snapshot ->
                        BackupRestoreDialog(
                            backup = snapshot,
                            onDismiss = { restoreCandidate = null },
                            onRestore = { mode ->
                                restoreCandidate = null
                                vm.restoreBackup(snapshot, mode) { result ->
                                    restoreCandidate = null
                                    screen = Screen.Home
                                    vm.showMessage("Imported ${result.imported} group(s), skipped ${result.skipped}")
                                }
                            }
                        )
                    }
                    SnackbarHost(hostState, Modifier.align(Alignment.BottomCenter))
                }
            }
        }
    }
}
