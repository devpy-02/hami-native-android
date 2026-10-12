package com.hamitools.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hamitools.data.AppDatabase
import com.hamitools.data.ProductRepository
import kotlinx.coroutines.launch

class ProductsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repo = ProductRepository(AppDatabase.get(this))
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        ProductsRoute(repo)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductsRoute(repo: ProductRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val products by repo.activeProducts().collectAsState(initial = emptyList())
    val locations by repo.locations().collectAsState(initial = emptyList())
    var status by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            status = try {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    ?: error("فایل خوانده نشد")
                val s = repo.importBackup(text)
                "بازیابی شد: ${s.products} کالا، ${s.locations} مکان، ${s.batches} بچ"
            } catch (e: Exception) {
                "خطا در بازیابی: ${e.message}"
            }
        }
    }

    ProductsScreen(
        products = products,
        locations = locations,
        importStatus = status,
        onImport = { picker.launch(arrayOf("application/json", "*/*")) },
        onOpenSales = { context.startActivity(Intent(context, SalesActivity::class.java)) },
        onOpenInvoices = { context.startActivity(Intent(context, InvoiceActivity::class.java)) },
        onOpenQuickSale = { context.startActivity(Intent(context, QuickSaleActivity::class.java)) },
        onOpenHistory = { context.startActivity(Intent(context, HistoryActivity::class.java)) },
    )
}
