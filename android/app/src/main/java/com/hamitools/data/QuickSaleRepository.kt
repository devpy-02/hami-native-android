package com.hamitools.data

import androidx.room.withTransaction
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

// اطلاعات فروش سریع برای ویرایش
data class QuickSaleEdit(
    val id: String,
    val number: String,
    val name: String,
    val productId: String?,
    val quantity: Long,
    val unitPrice: Long,
    val customer: String,
    val type: InvType,
    val customerPhone: String = "",
    val customerAddress: String = "",
)

// فروش سریع: بدون نیاز به موجودی انبار. تعداد و قیمت واحد الزامی است؛ نام کالا اختیاری است.
// موجودی انبار تغییر نمی‌کند.
class QuickSaleRepository(private val db: AppDatabase) {

    /** ثبت فروش سریع تازه */
    suspend fun record(
        type: InvType,
        name: String?,
        productId: String?,
        quantity: Long,
        unitPrice: Long,
        registerProduct: Boolean,
        customer: String?,
        customerPhone: String? = null,
        customerAddress: String? = null,
    ): String = db.withTransaction {
        require(quantity > 0) { "تعداد باید بزرگ‌تر از صفر باشد." }
        require(unitPrice > 0) { "قیمت واحد باید بزرگ‌تر از صفر باشد." }
        val now = Instant.now().toString()
        val (linkedId, linkedName) = resolveProduct(name, productId, registerProduct, unitPrice, now)
        val number = "Q-${type.prefix}-" + (db.invoiceDao().count() + 1).toString().padStart(5, '0')
        val invoiceId = UUID.randomUUID().toString()
        val total = quantity * unitPrice
        db.invoiceDao().insert(
            InvoiceEntity(
                id = invoiceId,
                invoiceNumber = number,
                type = type.code,
                createdAt = now,
                customerName = customer?.trim()?.ifEmpty { null },
                total = total,
                locationId = null,
                itemsJson = itemJson(linkedId, linkedName, quantity, unitPrice).toString(),
                customerPhone = customerPhone?.trim()?.ifEmpty { null },
                customerAddress = customerAddress?.trim()?.ifEmpty { null },
            )
        )
        // فاکتور سریع یک یادداشت موقت است؛ حساب مشتری و موجودی انبار را تغییر نمی‌دهد.
        number
    }

    /** خواندن فروش سریع برای ویرایش */
    suspend fun loadForEdit(id: String): QuickSaleEdit {
        val inv = db.invoiceDao().byId(id) ?: error("فاکتور پیدا نشد.")
        val arr = JSONArray(inv.itemsJson)
        require(arr.length() == 1 && arr.getJSONObject(0).optBoolean("quickSale", false)) {
            "این فاکتور فروش سریع نیست."
        }
        val o = arr.getJSONObject(0)
        return QuickSaleEdit(
            id = inv.id,
            number = inv.invoiceNumber,
            name = o.optString("productName"),
            productId = if (o.isNull("productId")) null else o.getString("productId"),
            quantity = o.getLong("quantity"),
            unitPrice = o.getLong("unitPrice"),
            customer = inv.customerName ?: "",
            type = InvType.values().firstOrNull { it.code == inv.type } ?: InvType.SALES,
            customerPhone = inv.customerPhone ?: "",
            customerAddress = inv.customerAddress ?: "",
        )
    }

    /**
     * ویرایش فروش سریع. همان شماره و تاریخ فاکتور می‌ماند.
     * موجودی انبار تغییر نمی‌کند.
     */
    suspend fun update(
        id: String,
        type: InvType,
        name: String?,
        productId: String?,
        quantity: Long,
        unitPrice: Long,
        registerProduct: Boolean,
        customer: String?,
        customerPhone: String? = null,
        customerAddress: String? = null,
    ) = db.withTransaction {
        require(quantity > 0) { "تعداد باید بزرگ‌تر از صفر باشد." }
        require(unitPrice > 0) { "قیمت واحد باید بزرگ‌تر از صفر باشد." }
        val inv = db.invoiceDao().byId(id) ?: error("فاکتور پیدا نشد.")
        CustomerAccountRepository(db).reverseInvoice(inv)
        val (linkedId, linkedName) = resolveProduct(name, productId, registerProduct, unitPrice, inv.createdAt)
        db.invoiceDao().delete(id)
        val updated = inv.copy(
                // شناسهٔ جدید مانع می‌شود اثر حسابداری فاکتورهای سریع قدیمی هنگام ویرایش دوباره برگردانده شود.
                id = UUID.randomUUID().toString(),
                type = type.code,
                customerName = customer?.trim()?.ifEmpty { null },
                customerPhone = customerPhone?.trim()?.ifEmpty { null },
                customerAddress = customerAddress?.trim()?.ifEmpty { null },
                total = quantity * unitPrice,
                itemsJson = itemJson(linkedId, linkedName, quantity, unitPrice).toString(),
            )
        db.invoiceDao().insert(updated)
        // فاکتور سریع موقت است و نباید دوباره در دفتر حساب مشتری ثبت شود.
    }

    // کالای وصل‌شده را تعیین می‌کند. فقط انتخاب صریح کاربر یا ثبت کالای جدید باعث وصل شدن می‌شود.
    private suspend fun resolveProduct(
        name: String?,
        productId: String?,
        register: Boolean,
        unitPrice: Long,
        now: String,
    ): Pair<String?, String> {
        val cleanName = name?.trim()?.ifEmpty { null }
        if (productId != null) {
            val p = db.productDao().byId(productId) ?: error("کالای انتخاب‌شده پیدا نشد.")
            return p.id to p.name
        }
        if (cleanName != null && register && db.productDao().findByName(cleanName) == null) {
            val id = UUID.randomUUID().toString()
            db.productDao().upsert(
                ProductEntity(
                    id = id,
                    name = cleanName,
                    code = "",
                    unit = "",
                    price = unitPrice,
                    buyPrice = null,
                    stock = 0L,
                    supplierCode = null,
                    deleted = false,
                    profitType = null,
                    profitValue = null,
                    tagIds = emptyList(),
                    locationStock = emptyMap(),
                    updatedAt = now,
                    version = 1L,
                )
            )
            return id to cleanName
        }
        return null to (cleanName ?: "")
    }

    private fun itemJson(productId: String?, productName: String, quantity: Long, unitPrice: Long): JSONArray {
        val item = JSONObject()
            .put("productId", productId ?: JSONObject.NULL)
            .put("productName", productName)
            .put("quantity", quantity)
            .put("unitPrice", unitPrice)
            .put("quickSale", true)
        return JSONArray().put(item)
    }
}
