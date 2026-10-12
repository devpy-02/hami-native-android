package com.hamitools.ui

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.hamitools.data.AppDatabase
import com.hamitools.data.ProductRepository
import com.hamitools.data.SaleLine
import com.hamitools.data.SalesRepository
import kotlinx.coroutines.launch

class SalesActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val products = ProductRepository(db)
        val sales = SalesRepository(db)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        SalesRoute(products, sales)
                    }
                }
            }
        }
    }
}

@Composable
private fun SalesRoute(products: ProductRepository, sales: SalesRepository) {
    val scope = rememberCoroutineScope()
    val productList by products.activeProducts().collectAsState(initial = emptyList())
    val locations by products.locations().collectAsState(initial = emptyList())
    val invoices by sales.recentInvoices().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    SalesScreen(
        products = productList,
        locations = locations,
        recentInvoices = invoices,
        loadBatches = { sales.batchesFor(it) },
        onSell = { productId, locationId, batchId, qty, unitPrice, customer ->
            val name = productList.firstOrNull { it.id == productId }?.name ?: ""
            scope.launch {
                message = try {
                    val number = sales.sell(
                        SaleLine(productId, name, qty, unitPrice, locationId, batchId),
                        customer,
                    )
                    "فاکتور $number ثبت شد."
                } catch (e: Exception) {
                    "خطا: ${e.message}"
                }
            }
        },
        message = message,
    )
}
