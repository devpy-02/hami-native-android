package com.hamitools.data

import androidx.room.withTransaction
import com.hamitools.core.Locations
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

// نوع فاکتور: جهت تأثیر روی موجودی (sign) و پیشوند شماره
enum class InvType(val code: String, val label: String, val sign: Int, val prefix: String) {
    SALES("sales", "فروش خرده", -1, "F"),
    SALES_WHOLESALE("sales_wholesale", "فروش عمده", -1, "FW"),
    PURCHASE("purchase", "خرید", +1, "P"),
    SALES_RETURN("sales_return", "برگشت فروش", +1, "RS"),
    PURCHASE_RETURN("purchase_return", "برگشت خرید", -1, "RP"),
}

// یک قلم فاکتور
data class InvLine(
    val productId: String,
    val productName: String,
    val quantity: Long,
    val unitPrice: Long,
    val locationId: String,
    val batchId: String?,
    val expiryDate: String?,
    val batchNumber: String?,
)

// ثبت فاکتور چندقلمی: موجودی مکان‌ها، بچ‌ها، قیمت خرید و فاکتور همگی در یک تراکنش
class InvoiceRepository(private val db: AppDatabase) {

    suspend fun create(
        type: InvType, lines: List<InvLine>, party: String?, customerId: String? = null,
        partyPhone: String? = null, partyAddress: String? = null,
    ): String = db.withTransaction {
        createInTransaction(type, lines, party, null, customerId, partyPhone, partyAddress)
    }

    /** ثبت فاکتور داخل تراکنش فعلی. اگر forcedNumber داده شود (ویرایش)، همان شماره استفاده می‌شود. */
    suspend fun createInTransaction(type: InvType, lines: List<InvLine>, party: String?, forcedNumber: String?, customerId: String? = null, partyPhone: String? = null, partyAddress: String? = null): String {
        require(lines.isNotEmpty()) { "فاکتور خالی است." }
        val now = Instant.now().toString()
        // شناسه‌ی بچی که برای هر قلم ساخته شده (برای حذف فاکتور لازم است)
        val createdBatchIds = arrayOfNulls<String>(lines.size)

        lines.forEachIndexed { index, l ->
            require(l.quantity > 0) { "تعداد «${l.productName}» باید بزرگ‌تر از صفر باشد." }
            val p = db.productDao().byId(l.productId) ?: error("کالا پیدا نشد: ${l.productName}")
            val delta = type.sign * l.quantity

            if (type.sign < 0) {
                val have = p.locationStock[l.locationId] ?: 0L
                check(have >= l.quantity) { "موجودی «${l.productName}» در این مکان کافی نیست ($have عدد)." }
            }

            // خرید: قیمت خرید کالا به‌روز می‌شود
            val newBuy = if (type == InvType.PURCHASE && l.unitPrice > 0) l.unitPrice else p.buyPrice
            db.productDao().upsert(
                p.copy(
                    locationStock = Locations.applyDelta(p.locationStock, l.locationId, delta),
                    stock = p.stock + delta,
                    buyPrice = newBuy,
                    updatedAt = now,
                    version = p.version + 1,
                )
            )

            if (l.batchId != null) {
                val b = db.batchDao().byId(l.batchId) ?: error("بچ پیدا نشد.")
                val newRemaining = if (type.sign < 0) {
                    check(b.remainingQuantity >= l.quantity) {
                        "مانده‌ی بچ ${b.batchNumber ?: ""} کافی نیست (${b.remainingQuantity} عدد)."
                    }
                    b.remainingQuantity - l.quantity
                } else {
                    minOf(b.quantity, b.remainingQuantity + l.quantity)
                }
                db.batchDao().upsert(b.copy(remainingQuantity = newRemaining))
            } else if (type == InvType.PURCHASE && !l.expiryDate.isNullOrBlank()) {
                val batchId = UUID.randomUUID().toString()
                db.batchDao().upsert(
                    BatchEntity(
                        id = batchId,
                        productId = p.id,
                        productName = p.name,
                        batchNumber = l.batchNumber?.trim()?.ifEmpty { null },
                        locationId = l.locationId,
                        quantity = l.quantity,
                        remainingQuantity = l.quantity,
                        expiryDate = l.expiryDate,
                        supplierName = party?.trim()?.ifEmpty { null },
                        buyPrice = l.unitPrice,
                        receivedAt = now,
                    )
                )
                createdBatchIds[index] = batchId
            }
        }

        val number = forcedNumber ?: (type.prefix + "-" + (db.invoiceDao().count() + 1).toString().padStart(5, '0'))
        val invoiceId = UUID.randomUUID().toString()
        val total = lines.sumOf { it.quantity * it.unitPrice }
        db.invoiceDao().insert(
            InvoiceEntity(
                id = invoiceId,
                invoiceNumber = number,
                type = type.code,
                createdAt = now,
                customerName = party?.trim()?.ifEmpty { null },
                total = total,
                locationId = null,
                itemsJson = linesToJson(lines, createdBatchIds.toList()),
                customerId = customerId,
                customerPhone = partyPhone?.trim()?.ifEmpty { null },
                customerAddress = partyAddress?.trim()?.ifEmpty { null },
            )
        )
        CustomerAccountRepository(db).recordInvoice(invoiceId, number, type.code, party, total, now, customerId)
        return number
    }

    private fun linesToJson(lines: List<InvLine>, createdBatchIds: List<String?>): String {
        val arr = JSONArray()
        lines.forEachIndexed { i, l ->
            arr.put(
                JSONObject()
                    .put("productId", l.productId)
                    .put("productName", l.productName)
                    .put("quantity", l.quantity)
                    .put("unitPrice", l.unitPrice)
                    .put("locationId", l.locationId)
                    .put("batchId", l.batchId ?: createdBatchIds[i] ?: JSONObject.NULL)
                    .put("createdBatch", createdBatchIds[i] != null)
                    .put("expiryDate", l.expiryDate ?: JSONObject.NULL)
                    .put("batchNumber", l.batchNumber ?: JSONObject.NULL)
            )
        }
        return arr.toString()
    }
}
