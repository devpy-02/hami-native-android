package com.hamitools.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hamitools.data.AppDatabase
import com.hamitools.data.CustomerEntity

private enum class DebtFilter { ALL, OWES, CREDIT }

class DebtReportActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        DebtReportScreen(db)
                    }
                }
            }
        }
    }
}

@Composable
private fun DebtReportScreen(db: AppDatabase) {
    val customers by db.customerDao().observeActive().collectAsState(initial = emptyList())
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DebtFilter.ALL) }

    val totalReceivable = customers.sumOf { it.totalDebt.coerceAtLeast(0L) }
    val totalCustomerCredit = customers.sumOf { (-it.totalDebt).coerceAtLeast(0L) }
    val debtorsCount = customers.count { it.totalDebt > 0L }
    val creditsCount = customers.count { it.totalDebt < 0L }
    val visible = customers
        .filter { it.name.contains(query.trim(), ignoreCase = true) || it.customerCode.contains(query.trim(), ignoreCase = true) || it.mobile.contains(query.trim()) }
        .filter {
            when (filter) {
                DebtFilter.ALL -> true
                DebtFilter.OWES -> it.totalDebt > 0L
                DebtFilter.CREDIT -> it.totalDebt < 0L
            }
        }
        .sortedWith(compareByDescending<CustomerEntity> { it.totalDebt }.thenBy { it.name.lowercase() })

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("گزارش بدهی مشتریان", style = MaterialTheme.typography.headlineSmall)
        Text("مبالغ بر اساس ماندهٔ ثبت‌شده در حساب هر مشتری محاسبه می‌شوند.", style = MaterialTheme.typography.bodySmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("مجموع طلب از مشتریان", style = MaterialTheme.typography.titleMedium)
                Text("${"%,d".format(totalReceivable)} تومان", style = MaterialTheme.typography.headlineSmall)
                Text("${debtorsCount} مشتری بدهکار", style = MaterialTheme.typography.bodyMedium)
                Text("ماندهٔ بستانکاری مشتریان: ${"%,d".format(totalCustomerCredit)} تومان · ${creditsCount} مشتری", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("جست‌وجو با نام، کد یا موبایل") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = filter == DebtFilter.ALL, onClick = { filter = DebtFilter.ALL }, label = { Text("همه") })
            FilterChip(selected = filter == DebtFilter.OWES, onClick = { filter = DebtFilter.OWES }, label = { Text("بدهکار") })
            FilterChip(selected = filter == DebtFilter.CREDIT, onClick = { filter = DebtFilter.CREDIT }, label = { Text("بستانکار") })
        }

        if (visible.isEmpty()) {
            Text("مشتری‌ای مطابق این فیلتر پیدا نشد.", modifier = Modifier.padding(vertical = 12.dp))
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(visible, key = { it.id }) { customer ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(customer.name, style = MaterialTheme.typography.titleMedium)
                            Text("کد مشتری: ${customer.customerCode} · موبایل: ${customer.mobile}", style = MaterialTheme.typography.bodySmall)
                            val balanceLabel = when {
                                customer.totalDebt > 0L -> "بدهکار به فروشگاه"
                                customer.totalDebt < 0L -> "بستانکار نزد فروشگاه"
                                else -> "تسویه"
                            }
                            Text(balanceLabel, style = MaterialTheme.typography.labelMedium)
                            Text("${"%,d".format(kotlin.math.abs(customer.totalDebt))} تومان", style = MaterialTheme.typography.titleMedium)
                            Text("خالص خرید: ${"%,d".format(customer.totalPurchases)} · مجموع دریافت: ${"%,d".format(customer.totalPayments)} تومان", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
