package com.hamitools.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// نسخه ۲: جدول بچ و فاکتور اضافه شد. داده‌ها از پشتیبان JSON دوباره وارد می‌شوند.
@Database(
    entities = [ProductEntity::class, LocationEntity::class, BatchEntity::class, InvoiceEntity::class, CustomerEntity::class, CustomerLedgerEntity::class, AuditLogEntity::class],
    version = 5,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun locationDao(): LocationDao
    abstract fun batchDao(): BatchDao
    abstract fun invoiceDao(): InvoiceDao
    abstract fun customerDao(): CustomerDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        // Explicit migration keeps existing product/location records instead of dropping the database.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stock_batches` (
                        `id` TEXT NOT NULL,
                        `productId` TEXT NOT NULL,
                        `productName` TEXT NOT NULL,
                        `batchNumber` TEXT,
                        `locationId` TEXT,
                        `quantity` INTEGER NOT NULL,
                        `remainingQuantity` INTEGER NOT NULL,
                        `expiryDate` TEXT NOT NULL,
                        `supplierName` TEXT,
                        `buyPrice` INTEGER,
                        `receivedAt` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `invoices` (
                        `id` TEXT NOT NULL,
                        `invoiceNumber` TEXT NOT NULL,
                        `type` TEXT NOT NULL,
                        `createdAt` TEXT NOT NULL,
                        `customerName` TEXT,
                        `total` INTEGER NOT NULL,
                        `locationId` TEXT,
                        `itemsJson` TEXT NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""CREATE TABLE IF NOT EXISTS `customers` (
                    `id` TEXT NOT NULL, `name` TEXT NOT NULL, `customerCode` TEXT NOT NULL,
                    `mobile` TEXT NOT NULL, `phone` TEXT, `address` TEXT, `groupId` TEXT,
                    `defaultPriceLevel` TEXT, `defaultDiscountPercent` REAL NOT NULL,
                    `notes` TEXT, `tagsJson` TEXT NOT NULL, `totalDebt` INTEGER NOT NULL,
                    `totalPurchases` INTEGER NOT NULL, `totalPayments` INTEGER NOT NULL,
                    `lastPurchaseDate` TEXT, `lastPaymentDate` TEXT, `isActive` INTEGER NOT NULL,
                    `createdAt` TEXT NOT NULL, `updatedAt` TEXT NOT NULL, `createdBy` TEXT,
                    `deleted` INTEGER NOT NULL, PRIMARY KEY(`id`))""".trimIndent())
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_customerCode` ON `customers` (`customerCode`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_mobile` ON `customers` (`mobile`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `customer_ledger` (
                    `id` TEXT NOT NULL, `customerId` TEXT NOT NULL, `customerName` TEXT NOT NULL,
                    `entryType` TEXT NOT NULL, `referenceId` TEXT, `referenceNumber` TEXT,
                    `debit` INTEGER NOT NULL, `credit` INTEGER NOT NULL, `balanceBefore` INTEGER NOT NULL,
                    `balanceAfter` INTEGER NOT NULL, `date` TEXT NOT NULL, `time` TEXT NOT NULL,
                    `timestamp` TEXT NOT NULL, `description` TEXT NOT NULL, `performedBy` TEXT NOT NULL,
                    `performedByName` TEXT NOT NULL, `notes` TEXT, `createdAt` TEXT NOT NULL,
                    PRIMARY KEY(`id`))""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_customer_ledger_customerId` ON `customer_ledger` (`customerId`)")
                db.execSQL("""CREATE TABLE IF NOT EXISTS `audit_logs` (
                    `id` TEXT NOT NULL, `productId` TEXT NOT NULL, `productName` TEXT NOT NULL,
                    `changeType` TEXT NOT NULL, `oldValue` TEXT NOT NULL, `newValue` TEXT NOT NULL,
                    `diffAmount` INTEGER, `changedBy` TEXT NOT NULL, `changedByName` TEXT NOT NULL,
                    `changedAt` TEXT NOT NULL, `synced` INTEGER NOT NULL, PRIMARY KEY(`id`))""".trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_logs_changedAt` ON `audit_logs` (`changedAt`)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerId` TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerPhone` TEXT")
                db.execSQL("ALTER TABLE `invoices` ADD COLUMN `customerAddress` TEXT")
            }
        }

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "hami_tools.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                    .build()
                    .also { instance = it }
            }
    }
}
