package com.hamitools.data

import androidx.room.withTransaction
import com.hamitools.core.Locations
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

// یک قلم فروش
data class SaleLine(
    val productId: String,
    val productName: String,
    val quantity: Long,
    val unitPrice: Long,
    val locationId: String,
    val batchId: String?,
)

// ثبت فروش: موجودی مکان، مانده‌ی بچ و فاکتور همگی داخل یک تراکنش انجام می‌شوند.
class SalesRepository(private val db: AppDatabase) {

    fun recentInvoices(): Flow<List<InvoiceEntity>> = db.invoiceDao().observeRecent()

    suspend fun batchesFor(productId: String): List<BatchEntity> = db.batchDao().activeFor(productId)

    /** ثبت فاکتور فروش. اگر موجودی کافی نباشد، خطا پرتاب می‌شود و هیچ چیزی تغییر نمی‌کند. */
    suspend fun sell(line: SaleLine, customerName: String?): String = db.withTransaction {
        require(line.quantity > 0) { "تعداد باید بزرگ‌تر از صفر باشد." }

        val product = db.productDao().byId(line.productId) ?: error("کالا پیدا نشد.")
        val available = product.locationStock[line.locationId] ?: 0L
        check(available >= line.quantity) { "موجودی این مکان کافی نیست ($available عدد)." }

        val now = Instant.now().toString()
        db.productDao().upsert(
            product.copy(
                locationStock = Locations.applyDelta(product.locationStock, line.locationId, -line.quantity),
                stock = product.stock - line.quantity,
                updatedAt = now,
                version = product.version + 1,
            )
        )

        line.batchId?.let { batchId ->
            val batch = db.batchDao().byId(batchId) ?: error("بچ پیدا نشد.")
            check(batch.remainingQuantity >= line.quantity) {
                "مانده‌ی بچ ${batch.batchNumber ?: ""} کافی نیست (${batch.remainingQuantity} عدد)."
            }
            db.batchDao().upsert(batch.copy(remainingQuantity = batch.remainingQuantity - line.quantity))
        }

        val number = "F-" + (db.invoiceDao().count() + 1).toString().padStart(5, '0')
        val invoiceId = UUID.randomUUID().toString()
        val total = line.quantity * line.unitPrice
        db.invoiceDao().insert(
            InvoiceEntity(
                id = invoiceId,
                invoiceNumber = number,
                type = "sales",
                createdAt = now,
                customerName = customerName?.trim()?.ifEmpty { null },
                total = total,
                locationId = line.locationId,
                itemsJson = itemsToJson(listOf(line)),
            )
        )
        CustomerAccountRepository(db).recordInvoice(invoiceId, number, InvType.SALES.code, customerName, total, now)
        number
    }

    private fun itemsToJson(lines: List<SaleLine>): String {
        val arr = JSONArray()
        lines.forEach { l ->
            arr.put(
                JSONObject()
                    .put("productId", l.productId)
                    .put("productName", l.productName)
                    .put("quantity", l.quantity)
                    .put("unitPrice", l.unitPrice)
                    .put("locationId", l.locationId)
                    .put("batchId", l.batchId ?: JSONObject.NULL)
            )
        }
        return arr.toString()
    }
}
