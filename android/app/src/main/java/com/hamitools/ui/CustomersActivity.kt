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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.hamitools.data.AppDatabase
import com.hamitools.data.CustomerAccountRepository
import com.hamitools.data.CustomerEntity
import com.hamitools.data.CustomerLedgerEntity
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class CustomersActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.get(this)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                        CustomersRoute(db)
                    }
                }
            }
        }
    }
}

@Composable
private fun CustomersRoute(db: AppDatabase) {
    val scope = rememberCoroutineScope()
    val customers by db.customerDao().observeActive().collectAsState(initial = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var statementCustomerId by remember { mutableStateOf<String?>(null) }
    var paymentCustomer by remember { mutableStateOf<CustomerEntity?>(null) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("مشتریان (${customers.size})", style = MaterialTheme.typography.headlineSmall)
        Text("فروش به مشتری ثبت‌شده، مانده و گردش حساب او را به‌روزرسانی می‌کند.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { showAdd = true }, modifier = Modifier.fillMaxWidth()) { Text("افزودن مشتری") }
        message?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
        if (customers.isEmpty()) Text("هنوز مشتری‌ای ثبت نشده است.", modifier = Modifier.padding(vertical = 12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(customers, key = { it.id }) { customer ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(customer.name, style = MaterialTheme.typography.titleMedium)
                        Text("کد: ${customer.customerCode} · موبایل: ${customer.mobile}")
                        customer.phone?.takeIf { it.isNotBlank() }?.let { Text("تلفن: $it") }
                        customer.address?.takeIf { it.isNotBlank() }?.let { Text("نشانی: $it") }
                        Text("مانده حساب: ${"%,d".format(customer.totalDebt)} تومان")
                        Text("خریدها: ${"%,d".format(customer.totalPurchases)} · دریافتی‌ها: ${"%,d".format(customer.totalPayments)} تومان", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { statementCustomerId = customer.id }) { Text("گردش حساب") }
                            Button(onClick = { paymentCustomer = customer }) { Text("ثبت دریافت") }
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddCustomerDialog(
            onDismiss = { showAdd = false },
            onSave = { name, mobile, phone, address ->
                scope.launch {
                    try {
                        require(name.isNotBlank()) { "نام مشتری را وارد کن." }
                        require(mobile.isNotBlank()) { "شماره موبایل را وارد کن." }
                        require(db.customerDao().byName(name.trim()) == null) { "این نام مشتری قبلاً ثبت شده است؛ نام یکتا انتخاب کن." }
                        require(db.customerDao().byMobile(mobile.trim()) == null) { "این شماره موبایل قبلاً ثبت شده است." }
                        val next = db.customerDao().count() + 1
                        val now = Instant.now().toString()
                        db.customerDao().upsert(
                            CustomerEntity(
                                id = UUID.randomUUID().toString(), name = name.trim(),
                                customerCode = "CUS-${next.toString().padStart(5, '0')}", mobile = mobile.trim(),
                                phone = phone.trim().ifEmpty { null }, address = address.trim().ifEmpty { null },
                                groupId = null, defaultPriceLevel = null, defaultDiscountPercent = 0.0,
                                notes = null, tagsJson = "[]", totalDebt = 0L, totalPurchases = 0L,
                                totalPayments = 0L, lastPurchaseDate = null, lastPaymentDate = null,
                                isActive = true, createdAt = now, updatedAt = now, createdBy = null, deleted = false,
                            )
                        )
                        message = "مشتری ثبت شد. هنگام ثبت فاکتور، او را از فهرست مشتریان انتخاب کن تا فاکتور به حسابش متصل شود."
                        showAdd = false
                    } catch (e: Exception) {
                        message = e.message ?: "ثبت مشتری انجام نشد."
                    }
                }
            },
        )
    }

    val statementCustomer = customers.firstOrNull { it.id == statementCustomerId }
    if (statementCustomer != null) {
        val ledgerFlow = remember(statementCustomer.id) { db.customerDao().observeLedger(statementCustomer.id) }
        val ledger by ledgerFlow.collectAsState(initial = emptyList())
        CustomerStatementDialog(statementCustomer, ledger, onDismiss = { statementCustomerId = null })
    }

    paymentCustomer?.let { customer ->
        PaymentDialog(
            customer = customer,
            onDismiss = { paymentCustomer = null },
            onSave = { amount, note ->
                scope.launch {
                    try {
                        CustomerAccountRepository(db).registerPayment(customer.id, amount, note)
                        message = "دریافت ثبت شد و مانده حساب به‌روزرسانی شد."
                        paymentCustomer = null
                    } catch (e: Exception) {
                        message = e.message ?: "ثبت دریافت انجام نشد."
                    }
                }
            },
        )
    }
}

@Composable
private fun CustomerStatementDialog(
    customer: CustomerEntity,
    ledger: List<CustomerLedgerEntity>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("گردش حساب · ${customer.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مانده فعلی: ${"%,d".format(customer.totalDebt)} تومان")
                if (ledger.isEmpty()) Text("هنوز گردش حسابی ثبت نشده است.")
                LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(ledger, key = { it.id }) { entry ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(entry.description, style = MaterialTheme.typography.bodyMedium)
                                Text("${entry.date} · بدهکار ${"%,d".format(entry.debit)} · بستانکار ${"%,d".format(entry.credit)}", style = MaterialTheme.typography.bodySmall)
                                Text("مانده پس از عملیات: ${"%,d".format(entry.balanceAfter)} تومان", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("بستن") } },
    )
}

@Composable
private fun PaymentDialog(
    customer: CustomerEntity,
    onDismiss: () -> Unit,
    onSave: (amount: Long, note: String) -> Unit,
) {
    var amount by remember(customer.id) { mutableStateOf("") }
    var note by remember(customer.id) { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ثبت دریافت از ${customer.name}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("مانده فعلی: ${"%,d".format(customer.totalDebt)} تومان")
                OutlinedTextField(amount, { amount = it.filter(Char::isDigit) }, label = { Text("مبلغ دریافتی (تومان)") }, singleLine = true)
                OutlinedTextField(note, { note = it }, label = { Text("توضیحات (اختیاری)") })
            }
        },
        confirmButton = { Button(onClick = { amount.toLongOrNull()?.let { onSave(it, note) } }, enabled = (amount.toLongOrNull() ?: 0L) > 0L) { Text("ثبت دریافت") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}

@Composable
private fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, mobile: String, phone: String, address: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مشتری جدید") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("نام مشتری *") }, singleLine = true)
                OutlinedTextField(mobile, { mobile = it }, label = { Text("موبایل *") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("تلفن ثابت") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("نشانی") })
            }
        },
        confirmButton = { Button(onClick = { onSave(name, mobile, phone, address) }) { Text("ذخیره") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("انصراف") } },
    )
}
