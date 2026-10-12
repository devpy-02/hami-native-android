package com.hamitools.data

import androidx.room.withTransaction
import com.hamitools.core.Locations
import org.json.JSONArray
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// خلاصه‌ی گزارش
data class InvoiceSummary(
    val todayCount: Int,
    val todayTotal: Long,
    val stockCostValue: Long,
)

// داده‌ی لازم برای ویرایش یک فاکتور
data class EditableInvoice(
    val id: String,
    val number: String,
    val type: InvType,
    val party: String,
    val lines: List<InvLine>,
    val customerId: String? = null,
    val partyPhone: String = "",
    val partyAddress: String = "",
)

// تاریخچه‌ی فاکتور: حذف، ویرایش، و گزارش ساده
class InvoiceHistory(private val db: AppDatabase) {

    private val invoices = InvoiceRepository(db)

    /** حذف فاکتور و برگرداندن اثر آن (داخل تراکنش) */
    private suspend fun reverseInTransaction(invoiceId: String): InvoiceEntity {
        val inv = db.invoiceDao().byId(invoiceId) ?: error("فاکتور پیدا نشد.")
        val type = InvType.values().firstOrNull { it.code == inv.type }
        val items = JSONArray(inv.itemsJson)
        val now = Instant.now().toString()

        if (type != null) {
            for (i in 0 until items.length()) {
                val o = items.getJSONObject(i)
                if (o.optBoolean("quickSale", false)) continue
                val productId = if (o.isNull("productId")) null else o.getString("productId")
                val qty = o.getLong("quantity")
                val loc = if (o.isNull("locationId")) null else o.getString("locationId")
                val batchId = if (o.isNull("batchId")) null else o.getString("batchId")
                val createdBatch = o.optBoolean("createdBatch", false)

                if (productId != null) {
                    val p = db.productDao().byId(productId)
                    if (p != null && loc != null) {
                        val reverse = -type.sign * qty
                        db.productDao().upsert(
                            p.copy(
                                locationStock = Locations.applyDelta(p.locationStock, loc, reverse),
                                stock = p.stock + reverse,
                                updatedAt = now,
                                version = p.version + 1,
                            )
                        )
                    }
                }

                if (batchId != null) {
                    if (createdBatch) {
                        db.batchDao().delete(batchId)
                    } else {
                        db.batchDao().byId(batchId)?.let { b ->
                            val restored = if (type.sign < 0) {
                                minOf(b.quantity, b.remainingQuantity + qty)
                            } else {
                                maxOf(0L, b.remainingQuantity - qty)
                            }
                            db.batchDao().upsert(b.copy(remainingQuantity = restored))
                        }
                    }
                }
            }
        }
        CustomerAccountRepository(db).reverseInvoice(inv)
        db.invoiceDao().delete(invoiceId)
        return inv
    }

    /** حذف فاکتور و برگرداندن اثر آن */
    suspend fun void(invoiceId: String) = db.withTransaction {
        reverseInTransaction(invoiceId)
    }

    /** خواندن فاکتور برای ویرایش. فروش سریع و فاکتورهای ناقص قابل ویرایش نیستند. */
    suspend fun loadForEdit(invoiceId: String): EditableInvoice {
        val inv = db.invoiceDao().byId(invoiceId) ?: error("فاکتور پیدا نشد.")
        val type = InvType.values().firstOrNull { it.code == inv.type } ?: error("نوع فاکتور ناشناخته است.")
        val items = JSONArray(inv.itemsJson)
        val lines = mutableListOf<InvLine>()
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            if (o.optBoolean("quickSale", false)) error("فروش سریع قابل ویرایش نیست.")
            val createdBatch = o.optBoolean("createdBatch", false)
            lines.add(
                InvLine(
                    productId = o.getString("productId"),
                    productName = o.optString("productName"),
                    quantity = o.getLong("quantity"),
                    unitPrice = o.getLong("unitPrice"),
                    locationId = o.getString("locationId"),
                    // بچ ساخته‌شده‌ی خرید با حذف فاکتور پاک می‌شود؛ دوباره بچ جدید از تاریخ انقضا ساخته می‌شود
                    batchId = if (createdBatch) null else o.optStringOrNull("batchId"),
                    expiryDate = if (createdBatch) o.optStringOrNull("expiryDate") else null,
                    batchNumber = o.optStringOrNull("batchNumber"),
                )
            )
        }
        return EditableInvoice(
            id = inv.id,
            number = inv.invoiceNumber,
            type = type,
            party = inv.customerName ?: "",
            lines = lines,
            customerId = inv.customerId,
            partyPhone = inv.customerPhone ?: "",
            partyAddress = inv.customerAddress ?: "",
        )
    }

    /**
     * ویرایش فاکتور: اثر فاکتور قبلی برگردانده می‌شود، و فاکتور جدید با همان شماره ثبت می‌شود.
     * همه‌ی مراحل داخل یک تراکنش است.
     */
    suspend fun edit(
        invoiceId: String, type: InvType, lines: List<InvLine>, party: String?, customerId: String? = null,
        partyPhone: String? = null, partyAddress: String? = null,
    ) = db.withTransaction {
        val old = reverseInTransaction(invoiceId)
        invoices.createInTransaction(type, lines, party, old.invoiceNumber, customerId, partyPhone, partyAddress)
    }

    suspend fun summary(): InvoiceSummary {
        val today = LocalDate.now()
        val zone = ZoneId.systemDefault()
        val all = db.invoiceDao().all()
        val todaySales = all.filter { inv ->
            (inv.type == InvType.SALES.code || inv.type == InvType.SALES_WHOLESALE.code) &&
                !inv.itemsJson.contains("\"quickSale\":true") &&
                runCatching { Instant.parse(inv.createdAt).atZone(zone).toLocalDate() == today }.getOrDefault(false)
        }
        val stockValue = db.productDao().activeNow().sumOf { p -> p.stock * (p.buyPrice ?: 0L) }
        return InvoiceSummary(
            todayCount = todaySales.size,
            todayTotal = todaySales.sumOf { it.total },
            stockCostValue = stockValue,
        )
    }

    private fun org.json.JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
