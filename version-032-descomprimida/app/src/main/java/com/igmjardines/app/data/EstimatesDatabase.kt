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

@Entity(tableName = "estimates", indices = [Index("clientId"), Index("gardenId")])
data class EstimateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: String,
    val clientId: Long,
    val clientNameSnapshot: String,
    val gardenId: Long?,
    val gardenNameSnapshot: String,
    val issueDate: Long = System.currentTimeMillis(),
    val status: String = "Pendiente",
    val baseAmount: Double,
    val vatAmount: Double,
    val discountAmount: Double,
    val totalAmount: Double,
    val notes: String = ""
)

@Entity(
    tableName = "estimate_lines",
    foreignKeys = [
        ForeignKey(
            entity = EstimateEntity::class,
            parentColumns = ["id"],
            childColumns = ["estimateId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("estimateId")]
)
data class EstimateLineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val estimateId: Long,
    val serviceId: Long?,
    val descriptionSnapshot: String,
    val unitSnapshot: String,
    val quantity: Double,
    val unitPriceExVat: Double,
    val vatRateSnapshot: Double,
    val lineBase: Double,
    val lineVat: Double,
    val lineTotal: Double
)

@Dao
interface EstimatesDao {
    @Query("SELECT * FROM estimates ORDER BY issueDate DESC, id DESC")
    fun observeEstimates(): Flow<List<EstimateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEstimate(estimate: EstimateEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLines(lines: List<EstimateLineEntity>)

    @Transaction
    suspend fun insertEstimateWithLines(estimate: EstimateEntity, lines: List<EstimateLineEntity>): Long {
        val id = insertEstimate(estimate)
        insertLines(lines.map { it.copy(estimateId = id) })
        return id
    }

    @Query("SELECT * FROM estimate_lines WHERE estimateId = :estimateId ORDER BY id")
    fun observeLines(estimateId: Long): Flow<List<EstimateLineEntity>>

    @Query("SELECT COUNT(*) FROM estimates")
    suspend fun countEstimates(): Int

    @Query("SELECT * FROM estimates WHERE id = :id LIMIT 1")
    suspend fun getEstimate(id: Long): EstimateEntity?

    @Query("SELECT * FROM estimate_lines WHERE estimateId = :id ORDER BY id")
    suspend fun getLines(id: Long): List<EstimateLineEntity>

    @Query("UPDATE estimates SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @Query("SELECT COUNT(*) FROM invoices WHERE estimateId = :estimateId")
    suspend fun countInvoicesForEstimate(estimateId: Long): Int

    @Query("UPDATE estimates SET discountAmount = :discount, totalAmount = :total, notes = :notes WHERE id = :id")
    suspend fun updateCommercialDetails(id: Long, discount: Double, total: Double, notes: String)
}
