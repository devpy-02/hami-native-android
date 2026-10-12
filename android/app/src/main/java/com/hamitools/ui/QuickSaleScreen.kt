package com.hamitools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.OutlinedTextField
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
import com.hamitools.data.ProductEntity

// فروش سریع: تعداد و قیمت واحد الزامی؛ نام کالا اختیاری.
// وقتی نام تایپ می‌شود، کالاهای مطابق نشان داده می‌شوند و فقط با انتخاب خودت به کالا وصل می‌شوند.
// می‌تواند برای ویرایش یک فروش سریع قبلی هم باز شود.
@Composable
fun QuickSaleScreen(
    products: List<ProductEntity>,
    onSubmit: (type: InvType, name: String?, productId: String?, registerProduct: Boolean, qty: Long, unitPrice: Long, customer: String?, customerPhone: String?, customerAddress: String?) -> Unit,
    message: String?,
    initialName: String = "",
    initialProductId: String? = null,
    initialQty: Long = 0L,
    initialPrice: Long = 0L,
    initialCustomer: String = "",
    initialCustomerPhone: String = "",
    initialCustomerAddress: String = "",
    editing: Boolean = false,
    initialType: InvType = InvType.SALES,
) {
    var type by remember { mutableStateOf(initialType) }
    var name by remember { mutableStateOf(initialName) }
    var pickedId by remember { mutableStateOf(initialProductId) }
    var qtyText by remember { mutableStateOf(if (initialQty > 0) initialQty.toString() else "") }
    var priceText by remember { mutableStateOf(if (initialPrice > 0) initialPrice.toString() else "") }
    var customer by remember { mutableStateOf(initialCustomer) }
    var customerPhone by remember { mutableStateOf(initialCustomerPhone) }
    var customerAddress by remember { mutableStateOf(initialCustomerAddress) }
    var askRegister by remember { mutableStateOf(false) }

    // کالای انتخاب‌شده از روی شناسه پیدا می‌شود (وقتی فهرست کالاها آماده شد)
    val picked = products.firstOrNull { it.id == pickedId }

    val qty = qtyText.toLongOrNull() ?: 0L
    val price = priceText.toLongOrNull() ?: 0L
    val valid = qty > 0 && price > 0

    val suggestions = remember(products, name, pickedId) {
        val q = name.trim().lowercase()
        if (pickedId != null || q.isEmpty()) emptyList()
        else products.filter { it.name.lowercase().contains(q) }.take(6)
    }

    fun clear() {
        type = InvType.SALES; name = ""; pickedId = null; qtyText = ""; priceText = ""; customer = ""; customerPhone = ""; customerAddress = ""
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(if (editing) "ویرایش فاکتور سریع" else "فاکتور سریع", style = MaterialTheme.typography.titleLarge)
        Text(
            "ثبت موقت برای مواقع شلوغی؛ بعداً فاکتور رسمی و مرتب ثبت کنید. این فرم موجودی انبار را تغییر نمی‌دهد.",
            style = MaterialTheme.typography.bodySmall,
        )

        Text("نوع فاکتور", style = MaterialTheme.typography.titleMedium)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf(
                InvType.SALES to "فروش خرده",
                InvType.SALES_WHOLESALE to "فروش عمده",
                InvType.PURCHASE to "خرید",
                InvType.PURCHASE_RETURN to "برگشت خرید",
                InvType.SALES_RETURN to "برگشت فروش",
            ).forEach { (invoiceType, label) ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { type = invoiceType }.padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.Start,
                ) {
                    RadioButton(selected = type == invoiceType, onClick = { type = invoiceType })
                    Text(label, modifier = Modifier.padding(top = 12.dp))
                }
            }
        }

        OutlinedTextField(
            value = name,
            onValueChange = { name = it; pickedId = null },
            label = { Text("نام کالا (اختیاری)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        suggestions.forEach { p ->
            OutlinedButton(
                onClick = { pickedId = p.id; name = p.name },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("${p.name} (موجودی ${p.stock})")
            }
        }

        picked?.let { p ->
            Text("به کالای «${p.name}» وصل می‌شود.", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = qtyText,
            onValueChange = { qtyText = it.filter { c -> c.isDigit() } },
            label = { Text("تعداد *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = priceText,
            onValueChange = { priceText = it.filter { c -> c.isDigit() } },
            label = { Text("قیمت واحد (تومان) *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        val partyLabel = if (type == InvType.PURCHASE || type == InvType.PURCHASE_RETURN) "فروشنده / تأمین‌کننده" else "نام مشتری"
        OutlinedTextField(
            value = customer,
            onValueChange = { customer = it },
            label = { Text(partyLabel) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = customerPhone,
            onValueChange = { customerPhone = it },
            label = { Text("شماره تماس") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = customerAddress,
            onValueChange = { customerAddress = it },
            label = { Text("آدرس") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        Button(
            onClick = {
                val n = name.trim()
                val existsByName = products.any { it.name.trim() == n }
                when {
                    picked != null -> {
                        onSubmit(type, picked.name, picked.id, false, qty, price, customer, customerPhone, customerAddress)
                        if (!editing) clear()
                    }
                    n.isEmpty() || existsByName -> {
                        // بدون نام، یا نامی که کالای موجود دارد (بدون وصل شدن): بدون پرسش
                        onSubmit(type, n.ifEmpty { null }, null, false, qty, price, customer, customerPhone, customerAddress)
                        if (!editing) clear()
                    }
                    else -> askRegister = true
                }
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (editing) "ذخیره ویرایش" else "ثبت فاکتور سریع")
        }

        message?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }

    if (askRegister) {
        AlertDialog(
            onDismissRequest = { askRegister = false },
            title = { Text("کالا ثبت شود؟") },
            text = { Text("کالای «${name.trim()}» در انبار نیست. آیا این کالا هم ثبت شود؟") },
            confirmButton = {
                TextButton(onClick = {
                    askRegister = false
                    onSubmit(type, name.trim(), null, true, qty, price, customer, customerPhone, customerAddress)
                    if (!editing) clear()
                }) { Text("بله، کالا ثبت شود") }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    askRegister = false
                    onSubmit(type, name.trim(), null, false, qty, price, customer, customerPhone, customerAddress)
                    if (!editing) clear()
                }) { Text("خیر، فقط فروش") }
            },
        )
    }
}
