package com.hamitools.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hamitools.data.AppDatabase
import com.hamitools.data.InvoiceHistory
import com.hamitools.data.InvoiceRepository
import com.hamitools.data.InvoiceSummary
import com.hamitools.data.ProductRepository
import com.hamitools.data.SalesRepository
import kotlinx.coroutines.launch

class HistoryActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val sales = SalesRepository(db)
        val products = ProductRepository(db)
        val history = InvoiceHistory(db)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        HistoryRoute(sales, products, history)
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryRoute(sales: SalesRepository, products: ProductRepository, history: InvoiceHistory) {
    val scope = rememberCoroutineScope()
    val invoices by sales.recentInvoices().collectAsState(initial = emptyList())
    val productList by products.activeProducts().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val summary by produceState(
        initialValue = InvoiceSummary(0, 0L, 0L),
        invoices,
        productList,
    ) {
        value = history.summary()
    }

    HistoryScreen(
        invoices = invoices,
        summary = summary,
        message = message,
        onEdit = { id ->
            scope.launch {
                val inv = runCatching { AppDatabase.get(context).invoiceDao().byId(id) }.getOrNull()
                val isQuick = inv?.itemsJson?.contains("\"quickSale\":true") == true
                val target = if (isQuick) QuickSaleActivity::class.java else InvoiceActivity::class.java
                context.startActivity(Intent(context, target).putExtra("editId", id))
            }
        },
        onVoid = { id ->
            scope.launch {
                message = try {
                    val existing = AppDatabase.get(context).invoiceDao().byId(id)
                    history.void(id)
                    if (existing?.itemsJson?.contains("\"quickSale\":true") == true) "فاکتور سریع حذف شد؛ موجودی تغییری نکرد." else "فاکتور حذف شد و اثر موجودی برگشت."
                } catch (e: Exception) {
                    "خطا در حذف: ${e.message}"
                }
            }
        },
    )
}
