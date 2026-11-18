package com.igmjardines.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "invoices", indices = [Index("clientId"), Index("estimateId"), Index("issueDate")])
data class InvoiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val clientId: Long,
    val clientNameSnapshot: String,
    val gardenId: Long?,
    val gardenNameSnapshot: String,
    val estimateId: Long? = null,
    val issueDate: Long = System.currentTimeMillis(),
    val dueDate: Long? = null,
    val status: String = "Pendiente",
    val baseAmount: Double,
    val vatAmount: Double,
    val totalAmount: Double,
    val paidAmount: Double = 0.0,
    val notes: String = "",
    val correctionOfInvoiceId: Long? = null,
    val correctionReason: String = ""
)

@Entity(
    tableName = "invoice_lines",
    foreignKeys = [ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("invoiceId")]
)
data class InvoiceLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val serviceId: Long? = null,
    val descriptionSnapshot: String,
    val unitSnapshot: String,
    val quantity: Double,
    val unitPriceExVat: Double,
    val vatRateSnapshot: Double,
    val lineBase: Double,
    val lineVat: Double,
    val lineTotal: Double
)

@Entity(
    tableName = "invoice_payments",
    foreignKeys = [ForeignKey(entity = InvoiceEntity::class, parentColumns = ["id"], childColumns = ["invoiceId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("invoiceId")]
)
data class InvoicePaymentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val invoiceId: Long,
    val amount: Double,
    val paymentDate: Long = System.currentTimeMillis(),
    val method: String = "Transferencia",
    val note: String = ""
)

@Dao
interface InvoicesDao {
    @Query("SELECT * FROM invoices ORDER BY issueDate DESC, id DESC")
    fun observeInvoices(): Flow<List<InvoiceEntity>>

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :invoiceId ORDER BY id")
    fun observeLines(invoiceId: Long): Flow<List<InvoiceLineEntity>>

    @Query("SELECT * FROM invoice_payments WHERE invoiceId = :invoiceId ORDER BY paymentDate DESC, id DESC")
    fun observePayments(invoiceId: Long): Flow<List<InvoicePaymentEntity>>

    @Query("SELECT * FROM invoice_payments ORDER BY paymentDate DESC, id DESC")
    fun observeAllPayments(): Flow<List<InvoicePaymentEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertInvoice(invoice: InvoiceEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertLines(lines: List<InvoiceLineEntity>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPayment(payment: InvoicePaymentEntity): Long

    @Query("UPDATE invoices SET paidAmount = :paidAmount, status = :status WHERE id = :invoiceId")
    suspend fun updatePaymentSummary(invoiceId: Long, paidAmount: Double, status: String)

    @Query("SELECT COUNT(*) FROM invoices")
    suspend fun countInvoices(): Int

    @Query("SELECT * FROM invoices WHERE id = :id LIMIT 1")
    suspend fun getInvoice(id: Long): InvoiceEntity?

    @Query("SELECT * FROM invoice_lines WHERE invoiceId = :id ORDER BY id")
    suspend fun getLines(id: Long): List<InvoiceLineEntity>

    @Query("SELECT COUNT(*) FROM invoices WHERE estimateId = :estimateId")
    suspend fun countForEstimate(estimateId: Long): Int

    @Query("SELECT COUNT(*) FROM invoices WHERE correctionOfInvoiceId = :invoiceId")
    suspend fun countCorrections(invoiceId: Long): Int

    @Query("SELECT COUNT(*) FROM invoice_payments WHERE invoiceId = :invoiceId")
    suspend fun countPayments(invoiceId: Long): Int

    @Query("UPDATE invoices SET dueDate = :dueDate, notes = :notes WHERE id = :invoiceId")
    suspend fun updateInvoiceDetails(invoiceId: Long, dueDate: Long?, notes: String)

    @Transaction
    suspend fun updateInvoiceDetailsSafely(invoiceId: Long, dueDate: Long?, notes: String) {
        val invoice = getInvoice(invoiceId) ?: error("La factura ya no existe")
        require(invoice.correctionOfInvoiceId == null) { "No se pueden editar los datos de una rectificativa" }
        require(invoice.paidAmount <= 0.009 && countPayments(invoiceId) == 0) {
            "No se puede editar una factura que ya tiene cobros registrados"
        }
        require(countCorrections(invoiceId) == 0) { "La factura ya tiene una rectificativa vinculada" }
        updateInvoiceDetails(invoiceId, dueDate, notes.trim())
    }

    @Transaction
    suspend fun registerPayment(invoiceId: Long, payment: InvoicePaymentEntity) {
        val invoice = getInvoice(invoiceId) ?: error("La factura ya no existe")
        val remaining = (invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0)
        require(payment.amount.isFinite() && payment.amount > 0.0) { "Introduce un importe de cobro válido" }
        require(payment.amount <= remaining + 0.009) { "El pago supera el importe pendiente" }
        require(invoice.correctionOfInvoiceId == null) { "No se pueden registrar cobros directamente sobre una rectificativa" }
        insertPayment(payment.copy(invoiceId = invoiceId))
        val paid = (invoice.paidAmount + payment.amount).coerceAtMost(invoice.totalAmount)
        val status = when {
            paid >= invoice.totalAmount - 0.009 -> "Pagada"
            paid > 0.0 -> "Parcialmente pagada"
            else -> "Pendiente"
        }
        updatePaymentSummary(invoiceId, paid, status)
    }

    @Transaction
    suspend fun createInvoice(invoice: InvoiceEntity, lines: List<InvoiceLineEntity>): Long {
        val id = insertInvoice(invoice)
        insertLines(lines.map { it.copy(invoiceId = id) })
        return id
    }
}
