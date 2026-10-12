package com.hamitools.ui

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.hamitools.data.AppDatabase
import com.hamitools.data.EditableInvoice
import com.hamitools.data.InvType
import com.hamitools.data.InvoiceHistory
import com.hamitools.data.InvoiceRepository
import com.hamitools.data.ProductRepository
import com.hamitools.data.SalesRepository
import kotlinx.coroutines.launch

class InvoiceActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val products = ProductRepository(db)
        val invoices = InvoiceRepository(db)
        val sales = SalesRepository(db)
        val history = InvoiceHistory(db)
        val editId = intent.getStringExtra("editId")
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        InvoiceRoute(db, products, invoices, sales, history, editId)
                    }
                }
            }
        }
    }
}

@Composable
private fun InvoiceRoute(
    db: AppDatabase,
    products: ProductRepository,
    invoices: InvoiceRepository,
    sales: SalesRepository,
    history: InvoiceHistory,
    editId: String?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val productList by products.activeProducts().collectAsState(initial = emptyList())
    val locations by products.locations().collectAsState(initial = emptyList())
    val recent by sales.recentInvoices().collectAsState(initial = emptyList())
    val customers by db.customerDao().observeActive().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    // در حالت ویرایش، فاکتور قبلی بارگذاری می‌شود
    val loaded by produceState<Result<EditableInvoice>?>(initialValue = null, editId) {
        value = editId?.let { runCatching { history.loadForEdit(it) } }
    }

    if (editId != null && loaded == null) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) { Text("در حال بارگذاری فاکتور...") }
        return
    }
    val failure = loaded?.exceptionOrNull()
    if (failure != null) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
            Text("این فاکتور قابل ویرایش نیست: ${failure.message}")
        }
        return
    }
    val ed = loaded?.getOrNull()

    InvoiceScreen(
        products = productList,
        locations = locations,
        recentInvoices = recent,
        customers = customers,
        loadBatches = { sales.batchesFor(it) },
        onSubmit = { type, lines, party, customerId, partyPhone, partyAddress ->
            scope.launch {
                message = try {
                    if (ed != null) {
                        history.edit(ed.id, type, lines, party, customerId, partyPhone, partyAddress)
                        (context as? Activity)?.finish()
                        "فاکتور ${ed.number} ویرایش شد."
                    } else {
                        val number = invoices.create(type, lines, party, customerId, partyPhone, partyAddress)
                        (context as? Activity)?.finish()
                        "فاکتور ${type.label} $number ثبت شد."
                    }
                } catch (e: Exception) {
                    "خطا: ${e.message}"
                }
            }
        },
        message = message,
        initialType = ed?.type ?: InvType.SALES,
        initialParty = ed?.party ?: "",
        initialPartyPhone = ed?.partyPhone ?: "",
        initialPartyAddress = ed?.partyAddress ?: "",
        initialCustomerId = ed?.customerId,
        initialLines = ed?.lines ?: emptyList(),
        editing = ed != null,
    )
}
