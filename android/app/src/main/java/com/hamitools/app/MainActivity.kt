package com.hamitools.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hamitools.ui.HistoryActivity
import com.hamitools.ui.CustomersActivity
import com.hamitools.ui.DebtReportActivity
import com.hamitools.ui.InvoiceActivity
import com.hamitools.ui.ProductsActivity
import com.hamitools.ui.QuickSaleActivity
import com.hamitools.ui.SalesActivity
import com.hamitools.ui.BackupActivity

/** Native Kotlin/Compose entry point. The React app is retained only as a migration reference. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme {
                    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                        HomeScreen(
                            onProducts = { open(ProductsActivity::class.java) },
                            onSales = { open(SalesActivity::class.java) },
                            onQuickSale = { open(QuickSaleActivity::class.java) },
                            onInvoices = { open(InvoiceActivity::class.java) },
                            onHistory = { open(HistoryActivity::class.java) },
                            onCustomers = { open(CustomersActivity::class.java) },
                            onDebtReport = { open(DebtReportActivity::class.java) },
                            onBackup = { open(BackupActivity::class.java) },
                        )
                    }
                }
            }
        }
    }

    private fun open(destination: Class<out android.app.Activity>) {
        startActivity(Intent(this, destination))
    }
}

private data class HomeAction(val title: String, val description: String, val click: () -> Unit)

@Composable
private fun HomeScreen(
    onProducts: () -> Unit,
    onSales: () -> Unit,
    onQuickSale: () -> Unit,
    onInvoices: () -> Unit,
    onHistory: () -> Unit,
    onCustomers: () -> Unit,
    onDebtReport: () -> Unit,
    onBackup: () -> Unit,
) {
    val actions = listOf(
        HomeAction("کالاها و موجودی", "مشاهده و مدیریت کالاها", onProducts),
        HomeAction("ثبت فروش", "ثبت فروش با انتخاب کالا", onSales),
        HomeAction("فروش سریع", "ثبت سریع فروش روزانه", onQuickSale),
        HomeAction("فاکتورها", "مشاهده و ویرایش فاکتورها", onInvoices),
        HomeAction("تاریخچه", "بررسی تغییرات و عملیات", onHistory),
        HomeAction("مشتریان", "ثبت و مشاهده مشتریان", onCustomers),
        HomeAction("گزارش بدهی", "طلب و بستانکاری مشتریان", onDebtReport),
        HomeAction("پشتیبان‌گیری", "ذخیره و بازیابی اطلاعات برنامه", onBackup),
    )

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("ابزار حامی", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "نسخهٔ بومی اندروید — در حال بازسازی از React به Kotlin",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(actions) { action ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(action.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(action.description, style = MaterialTheme.typography.bodySmall)
                        Button(onClick = action.click, modifier = Modifier.fillMaxWidth()) { Text("ورود") }
                    }
                }
            }
        }
    }
}
