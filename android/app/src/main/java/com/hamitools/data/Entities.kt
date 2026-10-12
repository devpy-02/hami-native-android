package com.hamitools.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val parentId: String?,
    val createdAt: String,
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String,
    val unit: String,
    val price: Long,
    val buyPrice: Long?,
    val stock: Long,
    val supplierCode: String?,
    val deleted: Boolean,
    val profitType: String?,
    val profitValue: Double?,
    val tagIds: List<String>,
    val locationStock: Map<String, Long>,
    val updatedAt: String,
    val version: Long,
)

// بچ انقضا: هر خرید (یا بخش خرید) با تاریخ انقضای خودش
@Entity(tableName = "stock_batches")
data class BatchEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val batchNumber: String?,
    val locationId: String?,
    val quantity: Long,
    val remainingQuantity: Long,
    val expiryDate: String,
    val supplierName: String?,
    val buyPrice: Long?,
    val receivedAt: String,
)

// فاکتور فروش. اقلام به‌صورت JSON در itemsJson ذخیره می‌شوند.
@Entity(tableName = "invoices")
data class InvoiceEntity(
    @PrimaryKey val id: String,
    val invoiceNumber: String,
    val type: String,
    val createdAt: String,
    val customerName: String?,
    val total: Long,
    val locationId: String?,
    val itemsJson: String,
    val customerId: String? = null,
    val customerPhone: String? = null,
    val customerAddress: String? = null,
)


// اطلاعات پایه مشتری؛ فیلدهای مالی برای سازگاری با مدل React نگهداری می‌شوند.
@Entity(tableName = "customers", indices = [Index(value = ["customerCode"], unique = true), Index(value = ["mobile"])])
data class CustomerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val customerCode: String,
    val mobile: String,
    val phone: String?,
    val address: String?,
    val groupId: String?,
    val defaultPriceLevel: String?,
    val defaultDiscountPercent: Double,
    val notes: String?,
    val tagsJson: String,
    val totalDebt: Long,
    val totalPurchases: Long,
    val totalPayments: Long,
    val lastPurchaseDate: String?,
    val lastPaymentDate: String?,
    val isActive: Boolean,
    val createdAt: String,
    val updatedAt: String,
    val createdBy: String?,
    val deleted: Boolean,
)

@Entity(tableName = "customer_ledger", indices = [Index(value = ["customerId"])])
data class CustomerLedgerEntity(
    @PrimaryKey val id: String,
    val customerId: String,
    val customerName: String,
    val entryType: String,
    val referenceId: String?,
    val referenceNumber: String?,
    val debit: Long,
    val credit: Long,
    val balanceBefore: Long,
    val balanceAfter: Long,
    val date: String,
    val time: String,
    val timestamp: String,
    val description: String,
    val performedBy: String,
    val performedByName: String,
    val notes: String?,
    val createdAt: String,
)

@Entity(tableName = "audit_logs", indices = [Index(value = ["changedAt"])])
data class AuditLogEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val productName: String,
    val changeType: String,
    val oldValue: String,
    val newValue: String,
    val diffAmount: Long?,
    val changedBy: String,
    val changedByName: String,
    val changedAt: String,
    val synced: Boolean,
)
