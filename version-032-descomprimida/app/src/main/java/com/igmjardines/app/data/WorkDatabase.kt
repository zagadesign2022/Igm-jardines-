package com.igmjardines.app.data

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "work_orders",
    foreignKeys = [
        ForeignKey(entity = ClientEntity::class, parentColumns = ["id"], childColumns = ["clientId"], onDelete = ForeignKey.RESTRICT),
        ForeignKey(entity = GardenEntity::class, parentColumns = ["id"], childColumns = ["gardenId"], onDelete = ForeignKey.SET_NULL)
    ],
    indices = [Index("clientId"), Index("gardenId"), Index("scheduledAt"), Index("status")]
)
data class WorkOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val clientNameSnapshot: String,
    val gardenId: Long? = null,
    val gardenNameSnapshot: String = "",
    val title: String,
    val description: String = "",
    val scheduledAt: Long = System.currentTimeMillis(),
    val durationMinutes: Int = 60,
    val status: String = "Pendiente",
    val notes: String = "",
    @ColumnInfo(defaultValue = "''") val beforePhotoPath: String = "",
    @ColumnInfo(defaultValue = "''") val afterPhotoPath: String = ""
)

@Dao
interface WorkOrdersDao {
    @Query("SELECT * FROM work_orders ORDER BY scheduledAt ASC, id DESC")
    fun observeAll(): Flow<List<WorkOrderEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: WorkOrderEntity): Long

    @Query("UPDATE work_orders SET status = :status WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String)

    @androidx.room.Update
    suspend fun update(item: WorkOrderEntity)

    @Query("UPDATE business_expenses SET workOrderId = NULL WHERE workOrderId = :id")
    suspend fun detachExpenses(id: Long)

    @Query("UPDATE travel_logs SET workOrderId = NULL WHERE workOrderId = :id")
    suspend fun detachTravel(id: Long)

    @androidx.room.Transaction
    suspend fun deleteById(id: Long) {
        detachExpenses(id)
        detachTravel(id)
        deleteWorkOrder(id)
    }

    @Query("DELETE FROM work_orders WHERE id = :id")
    suspend fun deleteWorkOrder(id: Long)
}
