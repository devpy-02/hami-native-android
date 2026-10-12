package com.hamitools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamitools.data.InvType
import com.hamitools.data.InvoiceEntity
import com.hamitools.data.InvoiceSummary

// تاریخچه‌ی فاکتورها، گزارش ساده و حذف فاکتور
@Composable
fun HistoryScreen(
    invoices: List<InvoiceEntity>,
    summary: InvoiceSummary,
    message: String?,
    onVoid: (String) -> Unit,
    onEdit: (String) -> Unit,
) {
    var toDelete by remember { mutableStateOf<InvoiceEntity?>(null) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Text("تاریخچه و گزارش", style = MaterialTheme.typography.titleLarge)
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("فروش امروز: ${summary.todayCount} فاکتور")
                    Text("جمع فروش امروز: ${"%,d".format(summary.todayTotal)} تومان")
                    Text("ارزش موجودی (بر اساس قیمت خرید): ${"%,d".format(summary.stockCostValue)} تومان")
                }
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text("فاکتورها", style = MaterialTheme.typography.titleMedium)
        }

        items(invoices, key = { it.id }) { inv ->
            val label = InvType.values().firstOrNull { it.code == inv.type }?.label ?: inv.type
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("${inv.invoiceNumber} · $label · ${inv.customerName ?: "بدون نام"}")
                    Text("جمع: ${"%,d".format(inv.total)} تومان · ${inv.createdAt.take(16).replace('T', ' ')}")
                    val isQuick = inv.itemsJson.contains("\"quickSale\":true")
                    if (isQuick) Text("فاکتور سریع · موقت · بدون تغییر موجودی", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { onEdit(inv.id) }) { Text(if (isQuick) "ویرایش فاکتور سریع" else "ویرایش") }
                    OutlinedButton(onClick = { toDelete = inv }) { Text("حذف فاکتور") }
                }
            }
        }
    }

    toDelete?.let { inv ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("فاکتور حذف شود؟") },
            text = { Text(if (inv.itemsJson.contains("\"quickSale\":true")) "فاکتور سریع ${inv.invoiceNumber} حذف می‌شود. این فاکتور موجودی انبار را تغییر نداده است." else "فاکتور ${inv.invoiceNumber} حذف می‌شود و اثر موجودی و بچ‌های آن برگردانده می‌شود.") },
            confirmButton = {
                TextButton(onClick = {
                    onVoid(inv.id)
                    toDelete = null
                }) { Text("بله، حذف شود") }
            },
            dismissButton = {
                OutlinedButton(onClick = { toDelete = null }) { Text("انصراف") }
            },
        )
    }
}
