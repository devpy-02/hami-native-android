package com.hamitools.data

import kotlinx.coroutines.flow.Flow

// دسترسی یک‌جا به داده‌ها برای صفحه‌ها
class ProductRepository(private val db: AppDatabase) {

    fun activeProducts(): Flow<List<ProductEntity>> = db.productDao().observeActive()

    fun locations(): Flow<List<LocationEntity>> = db.locationDao().observeAll()

    suspend fun importBackup(text: String): BackupImporter.Summary = BackupImporter.import(db, text)

    companion object {
        /** مسیر کامل مکان، مثلاً «منزل ← کمد» */
        fun locationPath(locations: List<LocationEntity>, id: String): String {
            val byId = locations.associateBy { it.id }
            val parts = ArrayDeque<String>()
            var cur = byId[id]
            var guard = 0
            while (cur != null && guard++ < 100) {
                parts.addFirst(cur.name)
                cur = cur.parentId?.let { byId[it] }
            }
            return if (parts.isEmpty()) "—" else parts.joinToString(" ← ")
        }
    }
}
