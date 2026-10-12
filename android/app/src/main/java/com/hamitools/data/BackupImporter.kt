package com.hamitools.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// وارد کردن فایل پشتیبان JSON نسخه‌ی وب: کالاها، مکان‌ها و بچ‌های انقضا
object BackupImporter {

    private const val APP_ID = "hami-tools-backup"
    private const val PRODUCTS_KEY = "hami_tools_products_v1"
    private const val LOCATIONS_KEY = "hami_tools_locations_v1"
    private const val BATCHES_KEY = "hami_tools_stock_batches_v1"

    data class Parsed(
        val products: List<ProductEntity>,
        val locations: List<LocationEntity>,
        val batches: List<BatchEntity>,
    )

    data class Summary(val products: Int, val locations: Int, val batches: Int)

    fun parse(text: String): Parsed {
        val root = JSONObject(text)
        if (root.optString("app") != APP_ID) {
            throw IllegalArgumentException("این فایل پشتیبان ابزار حامی نیست.")
        }
        val data = root.getJSONObject("data")
        return Parsed(
            products = data.optStringOrNull(PRODUCTS_KEY)?.let(::parseProducts) ?: emptyList(),
            locations = data.optStringOrNull(LOCATIONS_KEY)?.let(::parseLocations) ?: emptyList(),
            batches = data.optStringOrNull(BATCHES_KEY)?.let(::parseBatches) ?: emptyList(),
        )
    }

    suspend fun import(db: AppDatabase, text: String): Summary = withContext(Dispatchers.IO) {
        val parsed = parse(text)
        db.withTransaction {
            db.productDao().clear()
            db.locationDao().clear()
            db.batchDao().clear()
            db.locationDao().upsertAll(parsed.locations)
            db.productDao().upsertAll(parsed.products)
            db.batchDao().upsertAll(parsed.batches)
        }
        Summary(parsed.products.size, parsed.locations.size, parsed.batches.size)
    }

    private fun parseProducts(raw: String): List<ProductEntity> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ls = o.optJSONObject("locationStock")
            val tags = o.optJSONArray("tagIds")
            ProductEntity(
                id = o.getString("id"),
                name = o.optString("name"),
                code = o.optString("code"),
                unit = o.optString("unit"),
                price = o.optLong("price"),
                buyPrice = o.nullableLong("buyPrice"),
                stock = o.optLong("stock"),
                supplierCode = o.nullableString("supplierCode"),
                deleted = o.optBoolean("deleted", false),
                profitType = o.nullableString("profitType"),
                profitValue = o.nullableDouble("profitValue"),
                tagIds = tags?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
                locationStock = ls?.let { lo -> lo.keys().asSequence().associateWith { lo.getLong(it) } } ?: emptyMap(),
                updatedAt = o.optString("updatedAt"),
                version = o.optLong("version", 1L),
            )
        }
    }

    private fun parseLocations(raw: String): List<LocationEntity> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            LocationEntity(
                id = o.getString("id"),
                name = o.optString("name"),
                parentId = o.nullableString("parentId"),
                createdAt = o.optString("createdAt"),
            )
        }
    }

    private fun parseBatches(raw: String): List<BatchEntity> {
        val arr = JSONArray(raw)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            BatchEntity(
                id = o.getString("id"),
                productId = o.getString("productId"),
                productName = o.optString("productName"),
                batchNumber = o.nullableString("batchNumber"),
                locationId = o.nullableString("locationId"),
                quantity = o.optLong("quantity"),
                remainingQuantity = o.optLong("remainingQuantity"),
                expiryDate = o.optString("expiryDate"),
                supplierName = o.nullableString("supplierName"),
                buyPrice = o.nullableLong("buyPrice"),
                receivedAt = o.optString("receivedAt"),
            )
        }
    }

    // در org.json، مقدار null با getString به «null» تبدیل می‌شود؛ این توابع آن را خنثی می‌کنند
    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key).ifEmpty { null }

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else getLong(key)

    private fun JSONObject.nullableDouble(key: String): Double? =
        if (!has(key) || isNull(key)) null else getDouble(key)

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else getString(key)
}
