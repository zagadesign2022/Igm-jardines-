package com.igmjardines.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "machines", indices = [Index("name"), Index("nextServiceDate")])
data class MachineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val brandModel: String = "",
    val machineType: String = "Otra",
    val purchaseDate: Long? = null,
    val purchasePrice: Double = 0.0,
    val operatingHours: Double = 0.0,
    val nextServiceHours: Double? = null,
    val nextServiceDate: Long? = null,
    val notes: String = ""
)

@Entity(tableName = "machine_maintenance", indices = [Index("machineId"), Index("maintenanceDate")])
data class MachineMaintenanceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val machineId: Long,
    val maintenanceDate: Long = System.currentTimeMillis(),
    val description: String,
    val cost: Double = 0.0,
    val hoursAtService: Double? = null,
    val notes: String = ""
)

@Dao
interface AssetsDao {
    @Query("SELECT * FROM machines ORDER BY name COLLATE NOCASE")
    fun observeMachines(): Flow<List<MachineEntity>>
    @Query("SELECT * FROM machines ORDER BY name COLLATE NOCASE")
    suspend fun getAllMachines(): List<MachineEntity>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMachine(item: MachineEntity): Long
    @Query("UPDATE machines SET operatingHours = :hours WHERE id = :id")
    suspend fun updateOperatingHours(id: Long, hours: Double)
    @Query("DELETE FROM machines WHERE id = :id")
    suspend fun deleteMachine(id: Long)
    @Query("SELECT * FROM machine_maintenance WHERE machineId = :machineId ORDER BY maintenanceDate DESC")
    fun observeMaintenance(machineId: Long): Flow<List<MachineMaintenanceEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMaintenance(item: MachineMaintenanceEntity): Long
}

@Entity(tableName = "travel_logs", indices = [Index("travelDate"), Index("clientName"), Index("workOrderId")])
data class TravelLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val travelDate: Long = System.currentTimeMillis(),
    val clientName: String = "",
    val origin: String = "",
    val destination: String = "",
    val roundTripKm: Double = 0.0,
    val fuelLitres: Double = 0.0,
    val fuelCost: Double = 0.0,
    val otherCost: Double = 0.0,
    val durationMinutes: Int = 0,
    val notes: String = "",
    val workOrderId: Long? = null
)

@Dao
interface TravelDao {
    @Query("SELECT * FROM travel_logs ORDER BY travelDate DESC, id DESC")
    fun observeAll(): Flow<List<TravelLogEntity>>
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: TravelLogEntity): Long
    @Query("DELETE FROM travel_logs WHERE id = :id")
    suspend fun delete(id: Long)
}
