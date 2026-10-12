package com.hamitools.ui

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.hamitools.data.AppDatabase
import com.hamitools.data.NativeBackupManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BackupActivity : ComponentActivity() {
    private lateinit var db: AppDatabase
    private var status by mutableStateOf("از همهٔ اطلاعات نسخهٔ پشتیبان JSON بگیر یا آن را بازیابی کن.")
    private var confirmRestoreUri by mutableStateOf<Uri?>(null)

    private val createBackup = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) exportBackup(uri)
    }
    private val selectBackup = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) confirmRestoreUri = uri
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        db = AppDatabase.get(this)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        BackupScreen(
                            status = status,
                            onExport = {
                                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"))
                                createBackup.launch("hami-backup-$stamp.json")
                            },
                            onImport = { selectBackup.launch(arrayOf("application/json", "text/json", "text/plain", "application/octet-stream")) },
                        )
                        confirmRestoreUri?.let { uri ->
                            RestoreConfirmDialog(
                                onCancel = { confirmRestoreUri = null },
                                onConfirm = {
                                    confirmRestoreUri = null
                                    importBackup(uri)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    private fun exportBackup(uri: Uri) {
        lifecycleScope.launch {
            status = "در حال ساخت نسخهٔ پشتیبان…"
            val result = runCatching {
                val json = NativeBackupManager.export(db)
                withContext(Dispatchers.IO) {
                    contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { it.write(json) }
                    } ?: error("فایل برای نوشتن باز نشد.")
                }
            }
            status = result.fold(
                onSuccess = { "پشتیبان کامل ذخیره شد. کالاها، انبارها، بچ‌ها، فاکتورها، مشتریان، گردش حساب و تاریخچه در فایل قرار دارند." },
                onFailure = { "ساخت پشتیبان ناموفق بود: ${it.message ?: "خطای ناشناخته"}" },
            )
        }
    }

    private fun importBackup(uri: Uri) {
        lifecycleScope.launch {
            status = "در حال بررسی و بازیابی نسخهٔ پشتیبان…"
            val result = runCatching {
                val json = withContext(Dispatchers.IO) {
                    contentResolver.openInputStream(uri)?.use { input -> InputStreamReader(input, Charsets.UTF_8).readText() }
                        ?: error("فایل برای خواندن باز نشد.")
                }
                NativeBackupManager.import(db, json)
            }
            status = result.fold(
                onSuccess = { s -> "بازیابی کامل شد: ${s.products} کالا، ${s.locations} مکان، ${s.batches} بچ، ${s.invoices} فاکتور، ${s.customers} مشتری، ${s.ledgerEntries} گردش حساب و ${s.auditEntries} رویداد تاریخچه." },
                onFailure = { "بازیابی انجام نشد؛ اطلاعات فعلی تغییر نکرد: ${it.message ?: "خطای ناشناخته"}" },
            )
        }
    }
}

@Composable
private fun BackupScreen(status: String, onExport: () -> Unit, onImport: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("پشتیبان‌گیری و بازیابی", style = MaterialTheme.typography.headlineSmall)
        Text("فایل پشتیبان Native شامل کالاها، مکان‌ها و موجودی هر مکان، بچ‌های انقضا، فاکتورهای رسمی و سریع، مشتریان، گردش حساب و تاریخچهٔ تغییرات است.")
        Text("این صفحه فعلاً فایل پشتیبان ساخته‌شده توسط همین نسخهٔ Kotlin را بازیابی می‌کند؛ فایل‌های JSON قدیمی React قالب متفاوتی دارند.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) { Text("ساخت و ذخیرهٔ پشتیبان") }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("بازیابی از فایل پشتیبان") }
        Text("هشدار: بازیابی، اطلاعات فعلی همین جدول‌ها را با اطلاعات داخل فایل جایگزین می‌کند. قبل از بازیابی از اطلاعات فعلی هم پشتیبان بگیر.", color = MaterialTheme.colorScheme.error)
        Text(status, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun RestoreConfirmDialog(onCancel: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("تأیید بازیابی") },
        text = { Text("بازیابی اطلاعات فعلی برنامه را با اطلاعات فایل پشتیبان جایگزین می‌کند. اگر مطمئن هستی ادامه بده.") },
        confirmButton = { TextButton(onClick = onConfirm) { Text("بازیابی") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("انصراف") } },
    )
}
