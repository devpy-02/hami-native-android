package com.hamitools.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE deleted = 0 ORDER BY name")
    fun observeActive(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM products WHERE deleted = 0")
    suspend fun activeNow(): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun byId(id: String): ProductEntity?

    @Query("SELECT * FROM products WHERE name = :name AND deleted = 0 LIMIT 1")
    suspend fun findByName(name: String): ProductEntity?

    @Upsert
    suspend fun upsertAll(items: List<ProductEntity>)

    @Upsert
    suspend fun upsert(item: ProductEntity)

    @Query("SELECT * FROM products")
    suspend fun all(): List<ProductEntity>

    @Query("DELETE FROM products")
    suspend fun clear()
}

@Dao
interface LocationDao {
    @Query("SELECT * FROM locations ORDER BY createdAt")
    fun observeAll(): Flow<List<LocationEntity>>

    @Upsert
    suspend fun upsertAll(items: List<LocationEntity>)

    @Query("SELECT * FROM locations")
    suspend fun all(): List<LocationEntity>

    @Query("DELETE FROM locations")
    suspend fun clear()
}

@Dao
interface BatchDao {
    @Query("SELECT * FROM stock_batches WHERE productId = :productId AND remainingQuantity > 0 ORDER BY expiryDate")
    suspend fun activeFor(productId: String): List<BatchEntity>

    @Query("SELECT * FROM stock_batches WHERE id = :id")
    suspend fun byId(id: String): BatchEntity?

    @Upsert
    suspend fun upsertAll(items: List<BatchEntity>)

    @Upsert
    suspend fun upsert(item: BatchEntity)

    @Query("SELECT * FROM stock_batches")
    suspend fun all(): List<BatchEntity>

    @Query("DELETE FROM stock_batches WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM stock_batches")
    suspend fun clear()
}

@Dao
interface InvoiceDao {
    @Query("SELECT * FROM invoices ORDER BY createdAt DESC LIMIT 200")
    fun observeRecent(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoices ORDER BY createdAt DESC")
    suspend fun all(): List<InvoiceEntity>

    @Query("SELECT * FROM invoices WHERE id = :id")
    suspend fun byId(id: String): InvoiceEntity?

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun count(): Int

    @Insert
    suspend fun insert(item: InvoiceEntity)

    @Upsert
    suspend fun upsertAll(items: List<InvoiceEntity>)

    @Query("DELETE FROM invoices")
    suspend fun clear()

    @Query("DELETE FROM invoices WHERE id = :id")
    suspend fun delete(id: String)
}


@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers WHERE deleted = 0 ORDER BY name COLLATE NOCASE")
    fun observeActive(): Flow<List<CustomerEntity>>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun byId(id: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE customerCode = :code LIMIT 1")
    suspend fun byCode(code: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE mobile = :mobile AND deleted = 0 LIMIT 1")
    suspend fun byMobile(mobile: String): CustomerEntity?

    @Query("SELECT * FROM customers WHERE name = :name COLLATE NOCASE AND deleted = 0 LIMIT 1")
    suspend fun byName(name: String): CustomerEntity?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Query("SELECT * FROM customers")
    suspend fun all(): List<CustomerEntity>

    @Query("DELETE FROM customers")
    suspend fun clear()

    @Upsert
    suspend fun upsert(item: CustomerEntity)

    @Upsert
    suspend fun upsertAll(items: List<CustomerEntity>)

    @Query("SELECT * FROM customer_ledger WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun observeLedger(customerId: String): Flow<List<CustomerLedgerEntity>>

    @Query("SELECT * FROM customer_ledger WHERE referenceId = :referenceId AND entryType IN ('invoice_sale', 'invoice_sale_return')")
    suspend fun ledgerForReference(referenceId: String): List<CustomerLedgerEntity>

    @Query("SELECT * FROM customer_ledger")
    suspend fun allLedger(): List<CustomerLedgerEntity>

    @Query("DELETE FROM customer_ledger")
    suspend fun clearLedger()

    @Upsert
    suspend fun insertLedger(item: CustomerLedgerEntity)

    @Upsert
    suspend fun insertLedgerAll(items: List<CustomerLedgerEntity>)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY changedAt DESC LIMIT 500")
    fun observeRecent(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs")
    suspend fun all(): List<AuditLogEntity>

    @Query("DELETE FROM audit_logs")
    suspend fun clear()

    @Upsert
    suspend fun upsert(item: AuditLogEntity)

    @Upsert
    suspend fun upsertAll(items: List<AuditLogEntity>)
}
