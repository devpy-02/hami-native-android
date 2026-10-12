package com.hamitools.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** پشتیبان کامل نسخهٔ Native؛ تمام جدول‌های اصلی در یک فایل JSON ذخیره می‌شوند. */
object NativeBackupManager {
    private const val APP_ID = "hami-tools-native-backup"
    private const val FORMAT_VERSION = 1

    data class Summary(
        val products: Int,
        val locations: Int,
        val batches: Int,
        val invoices: Int,
        val customers: Int,
        val ledgerEntries: Int,
        val auditEntries: Int,
    )

    suspend fun export(db: AppDatabase): String = withContext(Dispatchers.IO) {
        val data = JSONObject()
        data.put("products", JSONArray().apply { db.productDao().all().forEach { put(it.toJson()) } })
        data.put("locations", JSONArray().apply { db.locationDao().all().forEach { put(it.toJson()) } })
        data.put("batches", JSONArray().apply { db.batchDao().all().forEach { put(it.toJson()) } })
        data.put("invoices", JSONArray().apply { db.invoiceDao().all().forEach { put(it.toJson()) } })
        data.put("customers", JSONArray().apply { db.customerDao().all().forEach { put(it.toJson()) } })
        data.put("customerLedger", JSONArray().apply { db.customerDao().allLedger().forEach { put(it.toJson()) } })
        data.put("auditLogs", JSONArray().apply { db.auditLogDao().all().forEach { put(it.toJson()) } })
        JSONObject()
            .put("app", APP_ID)
            .put("formatVersion", FORMAT_VERSION)
            .put("exportedAt", Instant.now().toString())
            .put("data", data)
            .toString(2)
    }

    suspend fun import(db: AppDatabase, text: String): Summary = withContext(Dispatchers.IO) {
        val root = JSONObject(text)
        require(root.optString("app") == APP_ID) {
            "این فایل، پشتیبان کامل نسخهٔ اندروید حامی نیست. برای بازیابی، فایل پشتیبان Native را انتخاب کن."
        }
        require(root.optInt("formatVersion", -1) == FORMAT_VERSION) { "نسخهٔ فایل پشتیبان پشتیبانی نمی‌شود." }
        val data = root.getJSONObject("data")
        val products = data.array("products").map { it.product() }
        val locations = data.array("locations").map { it.location() }
        val batches = data.array("batches").map { it.batch() }
        val invoices = data.array("invoices").map { it.invoice() }
        val customers = data.array("customers").map { it.customer() }
        val ledger = data.array("customerLedger").map { it.ledger() }
        val audit = data.array("auditLogs").map { it.audit() }

        // ابتدا همهٔ اطلاعات در حافظه اعتبارسنجی می‌شوند؛ سپس جایگزینی همهٔ جدول‌ها اتمیک انجام می‌شود.
        db.withTransaction {
            db.customerDao().clearLedger()
            db.invoiceDao().clear()
            db.batchDao().clear()
            db.auditLogDao().clear()
            db.customerDao().clear()
            db.locationDao().clear()
            db.productDao().clear()
            db.locationDao().upsertAll(locations)
            db.productDao().upsertAll(products)
            db.batchDao().upsertAll(batches)
            db.customerDao().upsertAll(customers)
            db.invoiceDao().upsertAll(invoices)
            db.customerDao().insertLedgerAll(ledger)
            db.auditLogDao().upsertAll(audit)
        }
        Summary(products.size, locations.size, batches.size, invoices.size, customers.size, ledger.size, audit.size)
    }

    private fun ProductEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("code", code).put("unit", unit)
        .put("price", price).putNullable("buyPrice", buyPrice).put("stock", stock)
        .putNullable("supplierCode", supplierCode).put("deleted", deleted)
        .putNullable("profitType", profitType).putNullable("profitValue", profitValue)
        .put("tagIds", JSONArray(tagIds)).put("locationStock", JSONObject(locationStock.mapValues { it.value }))
        .put("updatedAt", updatedAt).put("version", version)

    private fun LocationEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).putNullable("parentId", parentId).put("createdAt", createdAt)

    private fun BatchEntity.toJson() = JSONObject()
        .put("id", id).put("productId", productId).put("productName", productName)
        .putNullable("batchNumber", batchNumber).putNullable("locationId", locationId)
        .put("quantity", quantity).put("remainingQuantity", remainingQuantity).put("expiryDate", expiryDate)
        .putNullable("supplierName", supplierName).putNullable("buyPrice", buyPrice).put("receivedAt", receivedAt)

    private fun InvoiceEntity.toJson() = JSONObject()
        .put("id", id).put("invoiceNumber", invoiceNumber).put("type", type).put("createdAt", createdAt)
        .putNullable("customerName", customerName).put("total", total).putNullable("locationId", locationId)
        .put("itemsJson", itemsJson).putNullable("customerId", customerId)
        .putNullable("customerPhone", customerPhone).putNullable("customerAddress", customerAddress)

    private fun CustomerEntity.toJson() = JSONObject()
        .put("id", id).put("name", name).put("customerCode", customerCode).put("mobile", mobile)
        .putNullable("phone", phone).putNullable("address", address).putNullable("groupId", groupId)
        .putNullable("defaultPriceLevel", defaultPriceLevel).put("defaultDiscountPercent", defaultDiscountPercent)
        .putNullable("notes", notes).put("tagsJson", tagsJson).put("totalDebt", totalDebt)
        .put("totalPurchases", totalPurchases).put("totalPayments", totalPayments)
        .putNullable("lastPurchaseDate", lastPurchaseDate).putNullable("lastPaymentDate", lastPaymentDate)
        .put("isActive", isActive).put("createdAt", createdAt).put("updatedAt", updatedAt)
        .putNullable("createdBy", createdBy).put("deleted", deleted)

    private fun CustomerLedgerEntity.toJson() = JSONObject()
        .put("id", id).put("customerId", customerId).put("customerName", customerName).put("entryType", entryType)
        .putNullable("referenceId", referenceId).putNullable("referenceNumber", referenceNumber)
        .put("debit", debit).put("credit", credit).put("balanceBefore", balanceBefore).put("balanceAfter", balanceAfter)
        .put("date", date).put("time", time).put("timestamp", timestamp).put("description", description)
        .put("performedBy", performedBy).put("performedByName", performedByName).putNullable("notes", notes)
        .put("createdAt", createdAt)

    private fun AuditLogEntity.toJson() = JSONObject()
        .put("id", id).put("productId", productId).put("productName", productName).put("changeType", changeType)
        .put("oldValue", oldValue).put("newValue", newValue).putNullable("diffAmount", diffAmount)
        .put("changedBy", changedBy).put("changedByName", changedByName).put("changedAt", changedAt).put("synced", synced)

    private fun JSONObject.product() = ProductEntity(
        id = getString("id"), name = getString("name"), code = optString("code"), unit = optString("unit"),
        price = optLong("price"), buyPrice = longOrNull("buyPrice"), stock = optLong("stock"),
        supplierCode = stringOrNull("supplierCode"), deleted = optBoolean("deleted"),
        profitType = stringOrNull("profitType"), profitValue = doubleOrNull("profitValue"),
        tagIds = getJSONArray("tagIds").strings(),
        locationStock = getJSONObject("locationStock").let { o -> o.keys().asSequence().associateWith { o.getLong(it) } },
        updatedAt = optString("updatedAt"), version = optLong("version", 1L),
    )

    private fun JSONObject.location() = LocationEntity(getString("id"), optString("name"), stringOrNull("parentId"), optString("createdAt"))

    private fun JSONObject.batch() = BatchEntity(
        getString("id"), getString("productId"), optString("productName"), stringOrNull("batchNumber"),
        stringOrNull("locationId"), optLong("quantity"), optLong("remainingQuantity"), optString("expiryDate"),
        stringOrNull("supplierName"), longOrNull("buyPrice"), optString("receivedAt"),
    )

    private fun JSONObject.invoice() = InvoiceEntity(
        getString("id"), getString("invoiceNumber"), getString("type"), optString("createdAt"),
        stringOrNull("customerName"), optLong("total"), stringOrNull("locationId"), optString("itemsJson"),
        stringOrNull("customerId"), stringOrNull("customerPhone"), stringOrNull("customerAddress"),
    )

    private fun JSONObject.customer() = CustomerEntity(
        id = getString("id"), name = getString("name"), customerCode = getString("customerCode"), mobile = optString("mobile"),
        phone = stringOrNull("phone"), address = stringOrNull("address"), groupId = stringOrNull("groupId"),
        defaultPriceLevel = stringOrNull("defaultPriceLevel"), defaultDiscountPercent = optDouble("defaultDiscountPercent"),
        notes = stringOrNull("notes"), tagsJson = optString("tagsJson", "[]"), totalDebt = optLong("totalDebt"),
        totalPurchases = optLong("totalPurchases"), totalPayments = optLong("totalPayments"),
        lastPurchaseDate = stringOrNull("lastPurchaseDate"), lastPaymentDate = stringOrNull("lastPaymentDate"),
        isActive = optBoolean("isActive", true), createdAt = optString("createdAt"), updatedAt = optString("updatedAt"),
        createdBy = stringOrNull("createdBy"), deleted = optBoolean("deleted"),
    )

    private fun JSONObject.ledger() = CustomerLedgerEntity(
        id = getString("id"), customerId = getString("customerId"), customerName = optString("customerName"),
        entryType = optString("entryType"), referenceId = stringOrNull("referenceId"), referenceNumber = stringOrNull("referenceNumber"),
        debit = optLong("debit"), credit = optLong("credit"), balanceBefore = optLong("balanceBefore"), balanceAfter = optLong("balanceAfter"),
        date = optString("date"), time = optString("time"), timestamp = optString("timestamp"), description = optString("description"),
        performedBy = optString("performedBy"), performedByName = optString("performedByName"), notes = stringOrNull("notes"), createdAt = optString("createdAt"),
    )

    private fun JSONObject.audit() = AuditLogEntity(
        id = getString("id"), productId = optString("productId"), productName = optString("productName"),
        changeType = optString("changeType"), oldValue = optString("oldValue"), newValue = optString("newValue"),
        diffAmount = longOrNull("diffAmount"), changedBy = optString("changedBy"), changedByName = optString("changedByName"),
        changedAt = optString("changedAt"), synced = optBoolean("synced"),
    )

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject = put(key, value ?: JSONObject.NULL)
    private fun JSONObject.stringOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)
    private fun JSONObject.longOrNull(key: String): Long? = if (!has(key) || isNull(key)) null else getLong(key)
    private fun JSONObject.doubleOrNull(key: String): Double? = if (!has(key) || isNull(key)) null else getDouble(key)
    private fun JSONObject.array(key: String): List<JSONObject> {
        val arr = getJSONArray(key)
        return (0 until arr.length()).map { arr.getJSONObject(it) }
    }
    private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
}
