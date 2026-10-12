package com.hamitools.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.hamitools.data.CustomerEntity
import com.hamitools.data.InvLine
import com.hamitools.data.InvType
import com.hamitools.data.InvoiceEntity
import com.hamitools.data.LocationEntity
import com.hamitools.data.ProductEntity
import com.hamitools.data.ProductRepository

// صفحه‌ی فاکتور چندقلمی: نوع فاکتور، چند قلم، و ثبت یکجا
@Composable
fun InvoiceScreen(
    products: List<ProductEntity>,
    locations: List<LocationEntity>,
    recentInvoices: List<InvoiceEntity>,
    customers: List<CustomerEntity>,
    loadBatches: suspend (String) -> List<BatchEntity>,
    onSubmit: (InvType, List<InvLine>, String?, String?, String?, String?) -> Unit,
    message: String?,
    initialType: InvType = InvType.SALES,
    initialParty: String = "",
    initialPartyPhone: String = "",
    initialPartyAddress: String = "",
    initialCustomerId: String? = null,
    initialLines: List<InvLine> = emptyList(),
    editing: Boolean = false,
) {
    var type by remember { mutableStateOf(initialType) }
    var party by remember { mutableStateOf(initialParty) }
    var partyPhone by remember { mutableStateOf(initialPartyPhone) }
    var partyAddress by remember { mutableStateOf(initialPartyAddress) }
    var selectedCustomerId by remember { mutableStateOf(initialCustomerId) }
    val lines = remember { mutableStateOf(initialLines) }
    val customerInvoice = type == InvType.SALES || type == InvType.SALES_WHOLESALE || type == InvType.SALES_RETURN
    val customerMatches = remember(customers, party, selectedCustomerId) {
        val q = party.trim()
        val active = customers.filter { it.isActive && !it.deleted }
        if (selectedCustomerId != null) emptyList()
        else if (q.isBlank()) active.take(8)
        else active.filter { it.name.contains(q, ignoreCase = true) || it.customerCode.contains(q, ignoreCase = true) || it.mobile.contains(q) }.take(8)
    }

    // وضعیت یک قلم در حال ساخت
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<ProductEntity?>(null) }
    var locationId by remember { mutableStateOf<String?>(null) }
    var batchId by remember { mutableStateOf<String?>(null) }
    var batches by remember { mutableStateOf<List<BatchEntity>>(emptyList()) }
    var qtyText by remember { mutableStateOf("") }
    var priceText by remember { mutableStateOf("") }
    var expiryText by remember { mutableStateOf("") }
    var batchNoText by remember { mutableStateOf("") }

    val matches = remember(products, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) products.take(15)
        else products.filter { it.name.lowercase().contains(q) || it.code.lowercase().contains(q) }.take(15)
    }

    LaunchedEffect(selected?.id, type) {
        val p = selected
        if (p == null) {
            batches = emptyList(); locationId = null; batchId = null; priceText = ""
        } else {
            val needsStock = type.sign < 0
            locationId = if (needsStock) p.locationStock.filter { it.value > 0 }.keys.firstOrNull()
                else locations.firstOrNull()?.id
            batchId = null
            priceText = when (type) {
                InvType.SALES, InvType.SALES_WHOLESALE, InvType.SALES_RETURN -> p.price.toString()
                InvType.PURCHASE, InvType.PURCHASE_RETURN -> (p.buyPrice ?: 0L).toString()
            }
            batches = loadBatches(p.id)
        }
    }

    val locationOptions = if (type.sign < 0 && selected != null) {
        selected!!.locationStock.filter { it.value > 0 }.map { it.key to it.value }
    } else {
        locations.map { it.id to -1L }
    }
    val batchOptions = batches.filter { it.locationId == null || it.locationId == locationId }
    val total = lines.value.sumOf { it.quantity * it.unitPrice }

    Column(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("فاکتور", style = MaterialTheme.typography.titleLarge)

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            InvType.values().forEach { t ->
                OutlinedButton(
                    enabled = !editing,
                    onClick = {
                        type = t
                        lines.value = emptyList()
                        selectedCustomerId = null
                        party = ""
                        partyPhone = ""
                        partyAddress = ""
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text((if (type == t) "✓ " else "") + t.label)
                }
            }
        }

        if (customerInvoice) {
            OutlinedTextField(
                value = party,
                onValueChange = {
                    if (selectedCustomerId != null && it != party) {
                        partyPhone = ""
                        partyAddress = ""
                    }
                    party = it
                    selectedCustomerId = null
                },
                label = { Text("جستجوی مشتری یا نام مشتری متفرقه") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (selectedCustomerId != null) {
                val selectedCustomer = customers.firstOrNull { it.id == selectedCustomerId }
                Text(
                    selectedCustomer?.let { "مشتری انتخاب‌شده: ${it.name} · کد ${it.customerCode}" }
                        ?: "مشتری انتخاب‌شده",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { selectedCustomerId = null; party = ""; partyPhone = ""; partyAddress = "" }, modifier = Modifier.fillMaxWidth()) {
                    Text("لغو انتخاب مشتری")
                }
            } else {
                customerMatches.forEach { customer ->
                    OutlinedButton(
                        onClick = {
                            selectedCustomerId = customer.id
                            party = customer.name
                            partyPhone = customer.mobile.ifBlank { customer.phone.orEmpty() }
                            partyAddress = customer.address.orEmpty()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("${customer.name} · ${customer.customerCode} · ${customer.mobile}")
                    }
                }
                if (party.isNotBlank()) {
                    Text("اگر مشتری در فهرست نیست، نام واردشده به‌عنوان طرف حساب متفرقه ثبت می‌شود.", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            OutlinedTextField(
                value = party,
                onValueChange = { party = it },
                label = { Text("تأمین‌کننده") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        OutlinedTextField(
            value = partyPhone,
            onValueChange = { partyPhone = it },
            label = { Text(if (customerInvoice) "شماره تماس مشتری *" else "شماره تماس فروشنده *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = partyAddress,
            onValueChange = { partyAddress = it },
            label = { Text(if (customerInvoice) "آدرس مشتری" else "آدرس فروشنده") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
        )

        LazyColumn(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // ساخت قلم
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (selected == null) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                label = { Text("جستجوی کالا") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            matches.forEach { p ->
                                OutlinedButton(onClick = { selected = p }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${p.name} (موجودی ${p.stock})")
                                }
                            }
                        } else {
                            val p = selected!!
                            Text(p.name, style = MaterialTheme.typography.titleMedium)

                            Text("مکان:", style = MaterialTheme.typography.labelLarge)
                            locationOptions.forEach { (id, qty) ->
                                OutlinedButton(onClick = { locationId = id; batchId = null }, modifier = Modifier.fillMaxWidth()) {
                                    val extra = if (qty >= 0) " ($qty)" else ""
                                    Text((if (locationId == id) "✓ " else "") + ProductRepository.locationPath(locations, id) + extra)
                                }
                            }

                            if (type == InvType.SALES || type == InvType.SALES_WHOLESALE || type == InvType.PURCHASE_RETURN || type == InvType.SALES_RETURN) {
                                Text("بچ (اختیاری):", style = MaterialTheme.typography.labelLarge)
                                OutlinedButton(onClick = { batchId = null }, modifier = Modifier.fillMaxWidth()) {
                                    Text((if (batchId == null) "✓ " else "") + "بدون بچ")
                                }
                                batchOptions.forEach { b ->
                                    OutlinedButton(onClick = { batchId = b.id }, modifier = Modifier.fillMaxWidth()) {
                                        Text((if (batchId == b.id) "✓ " else "") +
                                            "${b.batchNumber ?: "بدون شماره"} · ${b.expiryDate.take(10)} · مانده ${b.remainingQuantity}")
                                    }
                                }
                            }

                            if (type == InvType.PURCHASE) {
                                OutlinedTextField(
                                    value = expiryText,
                                    onValueChange = { expiryText = it },
                                    label = { Text("تاریخ انقضا (YYYY-MM-DD، اختیاری)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = batchNoText,
                                    onValueChange = { batchNoText = it },
                                    label = { Text("شماره بچ (اختیاری)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
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
                                label = { Text("قیمت هر عدد (تومان)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Button(
                                onClick = {
                                    val loc = locationId ?: return@Button
                                    lines.value = lines.value + InvLine(
                                        productId = p.id,
                                        productName = p.name,
                                        quantity = qtyText.toLongOrNull() ?: 0L,
                                        unitPrice = priceText.toLongOrNull() ?: 0L,
                                        locationId = loc,
                                        batchId = batchId,
                                        expiryDate = expiryText.trim().ifEmpty { null }?.let { "${it}T00:00:00Z" },
                                        batchNumber = batchNoText.trim().ifEmpty { null },
                                    )
                                    selected = null
                                    qtyText = ""; expiryText = ""; batchNoText = ""; query = ""
                                },
                                enabled = locationId != null && (qtyText.toLongOrNull() ?: 0L) > 0,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("افزودن قلم به فاکتور") }

                            OutlinedButton(onClick = { selected = null }, modifier = Modifier.fillMaxWidth()) { Text("انصراف از این قلم") }
                        }
                    }
                }
            }

            // اقلام فاکتور در حال ساخت
            items(lines.value.indices.toList()) { i ->
                val l = lines.value[i]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("${l.productName} · ${l.quantity} × ${"%,d".format(l.unitPrice)}")
                        Text(ProductRepository.locationPath(locations, l.locationId), style = MaterialTheme.typography.bodySmall)
                        OutlinedButton(onClick = { lines.value = lines.value.filterIndexed { j, _ -> j != i } }) {
                            Text("حذف قلم")
                        }
                    }
                }
            }

            item {
                Text("جمع فاکتور: ${"%,d".format(total)} تومان", style = MaterialTheme.typography.titleMedium)
                Button(
                    onClick = {
                        onSubmit(type, lines.value, party.trim().ifEmpty { null }, if (customerInvoice) selectedCustomerId else null, partyPhone.trim().ifEmpty { null }, partyAddress.trim().ifEmpty { null })
                    },
                    enabled = lines.value.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (editing) "ذخیره ویرایش فاکتور" else "ثبت فاکتور ${type.label}") }
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
