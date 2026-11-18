package com.igmjardines.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Update
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "business_expenses", indices = [androidx.room.Index("expenseDate"), androidx.room.Index("category"), androidx.room.Index("workOrderId")])
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val concept: String,
    val category: String,
    val expenseDate: Long = System.currentTimeMillis(),
    val baseAmount: Double,
    val vatAmount: Double = 0.0,
    val totalAmount: Double,
    val notes: String = "",
    val receiptPath: String = "",
    val workOrderId: Long? = null
)

@Dao
interface ExpensesDao {
    @Query("SELECT * FROM business_expenses ORDER BY expenseDate DESC, id DESC")
    fun observeAll(): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ExpenseEntity): Long

    @Update
    suspend fun update(item: ExpenseEntity)

    @Query("DELETE FROM business_expenses WHERE id = :id")
    suspend fun delete(id: Long)
}
