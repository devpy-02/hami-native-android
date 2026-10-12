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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hamitools.data.LocationEntity
import com.hamitools.data.ProductEntity
import com.hamitools.data.ProductRepository

// صفحه‌ی لیست کالاها
@Composable
fun ProductsScreen(
    products: List<ProductEntity>,
    locations: List<LocationEntity>,
    importStatus: String?,
    onImport: () -> Unit,
    onOpenSales: () -> Unit,
    onOpenInvoices: () -> Unit,
    onOpenQuickSale: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(products, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) products
        else products.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("کالاها (${filtered.size})", style = MaterialTheme.typography.titleLarge)

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("جستجوی نام یا کد") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(onClick = onOpenQuickSale, modifier = Modifier.fillMaxWidth()) {
            Text("فروش سریع")
        }
        OutlinedButton(onClick = onOpenInvoices, modifier = Modifier.fillMaxWidth()) {
            Text("فاکتور (فروش، خرید، برگشت)")
        }
        OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) {
            Text("تاریخچه و گزارش")
        }
        OutlinedButton(onClick = onOpenSales, modifier = Modifier.fillMaxWidth()) {
            Text("ثبت فروش ساده")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
            Text("وارد کردن فایل پشتیبان JSON")
        }

        importStatus?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(filtered, key = { it.id }) { product ->
                ProductCard(product, locations)
            }
        }
    }
}

@Composable
private fun ProductCard(product: ProductEntity, locations: List<LocationEntity>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(product.name, style = MaterialTheme.typography.titleMedium)
            Text("کد ${product.code} · واحد ${product.unit}", style = MaterialTheme.typography.bodySmall)
            Text(
                "فروش: ${"%,d".format(product.price)} تومان" +
                    (product.buyPrice?.let { " · خرید: ${"%,d".format(it)}" } ?: ""),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("موجودی کل: ${product.stock}", style = MaterialTheme.typography.bodyMedium)
            product.locationStock
                .filter { it.value > 0 }
                .forEach { (id, qty) ->
                    Text(
                        "  ${ProductRepository.locationPath(locations, id)}: $qty",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
        }
    }
}
