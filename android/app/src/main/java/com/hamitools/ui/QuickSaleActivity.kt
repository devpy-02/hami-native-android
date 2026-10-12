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
import com.hamitools.data.InvType
import com.hamitools.data.ProductRepository
import com.hamitools.data.QuickSaleEdit
import com.hamitools.data.QuickSaleRepository
import kotlinx.coroutines.launch

class QuickSaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        val products = ProductRepository(db)
        val quick = QuickSaleRepository(db)
        val editId = intent.getStringExtra("editId")
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        QuickSaleRoute(products, quick, editId)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickSaleRoute(products: ProductRepository, quick: QuickSaleRepository, editId: String?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val productList by products.activeProducts().collectAsState(initial = emptyList())
    var message by remember { mutableStateOf<String?>(null) }

    // در حالت ویرایش، فروش قبلی بارگذاری می‌شود
    val loaded by produceState<Result<QuickSaleEdit>?>(initialValue = null, editId) {
        value = editId?.let { runCatching { quick.loadForEdit(it) } }
    }

    if (editId != null && loaded == null) {
        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) { Text("در حال بارگذاری...") }
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

    QuickSaleScreen(
        products = productList,
        onSubmit = { type, name, productId, register, qty, price, customer, customerPhone, customerAddress ->
            scope.launch {
                message = try {
                    if (ed != null) {
                        quick.update(ed.id, type, name, productId, qty, price, register, customer, customerPhone, customerAddress)
                        (context as? Activity)?.finish()
                        "فاکتور سریع ${ed.number} ویرایش شد."
                    } else {
                        val number = quick.record(type, name, productId, qty, price, register, customer, customerPhone, customerAddress)
                        "فاکتور سریع $number ثبت شد."
                    }
                } catch (e: Exception) {
                    "خطا: ${e.message}"
                }
            }
        },
        message = message,
        initialName = ed?.name ?: "",
        initialProductId = ed?.productId,
        initialQty = ed?.quantity ?: 0L,
        initialPrice = ed?.unitPrice ?: 0L,
        initialCustomer = ed?.customer ?: "",
        initialCustomerPhone = ed?.customerPhone ?: "",
        initialCustomerAddress = ed?.customerAddress ?: "",
        editing = ed != null,
        initialType = ed?.type ?: InvType.SALES,
    )
}
