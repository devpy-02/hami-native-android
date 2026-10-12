package com.hamitools.data

import androidx.room.withTransaction
import java.time.Instant
import java.util.UUID

/**
 * حساب مشتری: بدهی مثبت یعنی مشتری به فروشگاه بدهکار است؛ مانده منفی یعنی مشتری بستانکار است.
 * در صورت انتخاب مستقیم مشتری، شناسهٔ او مرجع اصلی است؛ برای فروش‌های قدیمی/متفرقه تطبیق دقیق نام نیز پشتیبانی می‌شود.
 */
class CustomerAccountRepository(private val db: AppDatabase) {

    suspend fun recordInvoice(
        invoiceId: String,
        invoiceNumber: String,
        invoiceType: String,
        customerName: String?,
        amount: Long,
        timestamp: String = Instant.now().toString(),
        customerId: String? = null,
    ) {
        if (amount <= 0L) return
        if (invoiceType != InvType.SALES.code && invoiceType != InvType.SALES_WHOLESALE.code && invoiceType != InvType.SALES_RETURN.code) return
        val cleanName = customerName?.trim()?.takeIf { it.isNotEmpty() }
        val customer = customerId?.let { db.customerDao().byId(it) }
            ?: cleanName?.let { db.customerDao().byName(it) }
            ?: return
        val isReturn = invoiceType == InvType.SALES_RETURN.code
        val debit = if (isReturn) 0L else amount
        val credit = if (isReturn) amount else 0L
        val before = customer.totalDebt
        val after = before + debit - credit
        db.customerDao().upsert(
            customer.copy(
                totalDebt = after,
                totalPurchases = (customer.totalPurchases + debit - credit).coerceAtLeast(0L),
                lastPurchaseDate = if (!isReturn) timestamp else customer.lastPurchaseDate,
                updatedAt = timestamp,
            )
        )
        db.customerDao().insertLedger(
            CustomerLedgerEntity(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                customerName = customer.name,
                entryType = if (isReturn) "invoice_sale_return" else "invoice_sale",
                referenceId = invoiceId,
                referenceNumber = invoiceNumber,
                debit = debit,
                credit = credit,
                balanceBefore = before,
                balanceAfter = after,
                date = timestamp.take(10),
                time = timestamp.substringAfter('T', "").take(8),
                timestamp = timestamp,
                description = if (isReturn) "برگشت از فروش فاکتور $invoiceNumber" else if (invoiceType == InvType.SALES_WHOLESALE.code) "فروش عمده فاکتور $invoiceNumber" else "فروش فاکتور $invoiceNumber",
                performedBy = "local",
                performedByName = "کاربر محلی",
                notes = null,
                createdAt = timestamp,
            )
        )
    }

    /** ثبت دریافت از مشتری؛ amount به‌صورت تومان و باید مثبت باشد. */
    suspend fun registerPayment(customerId: String, amount: Long, note: String? = null) = db.withTransaction {
        require(amount > 0L) { "مبلغ دریافتی باید بیشتر از صفر باشد." }
        val customer = db.customerDao().byId(customerId) ?: error("مشتری پیدا نشد.")
        val now = Instant.now().toString()
        val before = customer.totalDebt
        val after = before - amount
        db.customerDao().upsert(
            customer.copy(
                totalDebt = after,
                totalPayments = customer.totalPayments + amount,
                lastPaymentDate = now.take(10),
                updatedAt = now,
            )
        )
        db.customerDao().insertLedger(
            CustomerLedgerEntity(
                id = UUID.randomUUID().toString(),
                customerId = customer.id,
                customerName = customer.name,
                entryType = "payment_cash",
                referenceId = null,
                referenceNumber = null,
                debit = 0L,
                credit = amount,
                balanceBefore = before,
                balanceAfter = after,
                date = now.take(10),
                time = now.substringAfter('T', "").take(8),
                timestamp = now,
                description = "دریافت نقدی از مشتری",
                performedBy = "local",
                performedByName = "کاربر محلی",
                notes = note?.trim()?.ifEmpty { null },
                createdAt = now,
            )
        )
    }

    /** اثر حسابداری فاکتور را فقط وقتی برمی‌گرداند که قبلاً در دفتر مشتری ثبت شده باشد. */
    suspend fun reverseInvoice(invoice: InvoiceEntity) {
        val ledgerRows = db.customerDao().ledgerForReference(invoice.id)
        if (ledgerRows.isEmpty()) return
        val latest = ledgerRows.maxByOrNull { it.timestamp } ?: return
        val customer = db.customerDao().byId(latest.customerId) ?: return
        val debitToReverse = ledgerRows.sumOf { it.debit }
        val creditToReverse = ledgerRows.sumOf { it.credit }
        val now = Instant.now().toString()
        val before = customer.totalDebt
        val after = before - debitToReverse + creditToReverse
        db.customerDao().upsert(
            customer.copy(
                totalDebt = after,
                totalPurchases = (customer.totalPurchases - debitToReverse + creditToReverse).coerceAtLeast(0L),
                updatedAt = now,
            )
        )
        db.customerDao().insertLedger(
            CustomerLedgerEntity(
                id = UUID.randomUUID().toString(), customerId = customer.id, customerName = customer.name,
                entryType = "invoice_reversal", referenceId = invoice.id, referenceNumber = invoice.invoiceNumber,
                debit = creditToReverse, credit = debitToReverse, balanceBefore = before, balanceAfter = after,
                date = now.take(10), time = now.substringAfter('T', "").take(8), timestamp = now,
                description = "برگشت اثر حسابداری فاکتور ${invoice.invoiceNumber}",
                performedBy = "local", performedByName = "کاربر محلی", notes = null, createdAt = now,
            )
        )
    }
}
