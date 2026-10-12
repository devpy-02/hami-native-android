package com.hamitools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamitools.data.BatchEntity
import com.hamitools.data.InvoiceEntity
import com.hamitools.data.LocationEntity
import com.hamitools.data.ProductEntity
import com.hamitools.data.ProductRepository

// صفحه‌ی فروش: انتخاب کالا، مکان، بچ (اختیاری)، تعداد و ثبت فاکتور
@Composable
fun SalesScreen(
    products: List<ProductEntity>,
    locations: List<LocationEntity>,
    recentInvoices: List<InvoiceEntity>,
    loadBatches: suspend (String) -> List<BatchEntity>,
    onSell: (productId: String, locationId: String, batchId: String?, qty: Long, unitPrice: Long, customer: String?) -> Unit,
    message: String?,
) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProductEntity?>(null) }
    var locationId by remember { mutableStateOf<String?>(null) }
    var batchId by remember { mutableStateOf<String?>(null) }
    var batches by remember { mutableStateOf<List<BatchEntity>>(emptyList()) }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var customer by remember { mutableStateOf("") }

    val matches = remember(products, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) products.take(20)
        else products.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }.take(20)
    }

    // با انتخاب کالا، مکان پیش‌فرض، قیمت پیشنهادی و بچ‌ها تنظیم می‌شوند
    LaunchedEffect(selected?.id) {
        val p = selected
        if (p == null) {
            batches = emptyList()
            locationId = null
            batchId = null
            priceText = ""
        } else {
            locationId = p.locationStock.filter { it.value > 0 }.keys.firstOrNull()
            batchId = null
            priceText = p.price.toString()
            batches = loadBatches(p.id)
        }
    }

    val locationOptions = selected?.locationStock?.filter { it.value > 0 }?.toList() ?: emptyList()
    val batchOptions = batches.filter { it.locationId == null || it.locationId == locationId }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("فروش", style = MaterialTheme.typography.titleLarge)

        if (selected == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("جستجوی کالا") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (selected == null) {
                items(matches, key = { it.id }) { p ->
                    OutlinedButton(onClick = { selected = p }, modifier = Modifier.fillMaxWidth()) {
                        Text("${p.name} (موجودی: ${p.stock})")
                    }
                }
            } else {
                item {
                    val p = selected!!
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)

                            Text("مکان:", style = MaterialTheme.typography.labelLarge)
                            if (locationOptions.isEmpty()) Text("این کالا موجودی ندارد.")
                            locationOptions.forEach { (id, qty) ->
                                OutlinedButton(
                                    onClick = { locationId = id; batchId = null },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text((if (locationId == id) "✓ " else "") + "${ProductRepository.locationPath(locations, id)} ($qty)")
                                }
                            }

                            Text("بچ (اختیاری):", style = MaterialTheme.typography.labelLarge)
                            OutlinedButton(onClick = { batchId = null }, modifier = Modifier.fillMaxWidth()) {
                                Text((if (batchId == null) "✓ " else "") + "بدون بچ")
                            }
                            batchOptions.forEach { b ->
                                OutlinedButton(onClick = { batchId = b.id }, modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        (if (batchId == b.id) "✓ " else "") +
                                            "${b.batchNumber ?: "بدون شماره"} · انقضا ${b.expiryDate.take(10)} · مانده ${b.remainingQuantity}"
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = qtyText,
                                onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
                                label = { Text("تعداد") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = priceText,
                                onValueChange = { priceText = it.filter { c -> c.isDigit() } },
                                label = { Text("قیمت فروش هر عدد (تومان)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            OutlinedTextField(
                                value = customer,
                                onValueChange = { customer = it },
                                label = { Text("نام مشتری (اختیاری)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Button(
                                onClick = {
                                    val loc = locationId ?: return@Button
                                    onSell(
                                        p.id,
                                        loc,
                                        batchId,
                                        qtyText.toLongOrNull() ?: 0L,
                                        priceText.toLongOrNull() ?: 0L,
                                        customer,
                                    )
                                    qtyText = ""
                                    selected = null
                                },
                                enabled = locationId != null && (qtyText.toLongOrNull() ?: 0L) > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("ثبت فاکتور فروش") }

                            OutlinedButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) {
                                Text("انصراف")
                            }
                        }
                    }
                }
            }

            item {
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                Text("آخرین فاکتورها", style = MaterialTheme.typography.titleMedium)
            }

            items(recentInvoices, key = { it.id }) { inv ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${inv.invoiceNumber} · ${inv.customerName ?: "بدون نام"}")
                        Text("جمع: ${"%,d".format(inv.total)} تومان · ${inv.createdAt.take(16).replace('T', ' ')}")
                    }
                }
            }
        }
    }
}
