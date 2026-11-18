package com.igmjardines.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "services",
    indices = [Index("category"), Index("name")]
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val category: String,
    val description: String = "",
    val unit: String,
    val priceExVat: Double,
    val vatRate: Double,
    val active: Boolean = true
)

@Entity(
    tableName = "service_rates",
    indices = [Index("serviceId")],
)
data class ServiceRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serviceId: Long,
    val unit: String,
    val priceExVat: Double
)

@Entity(tableName = "vat_rates")
data class VatRateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val percentage: Double,
    val isDefault: Boolean = false
)

@Dao
interface ServicesDao {
    @Query("SELECT * FROM services WHERE active = 1 ORDER BY category, name")
    fun observeServices(): Flow<List<ServiceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertService(service: ServiceEntity): Long

    @Update
    suspend fun updateService(service: ServiceEntity)

    @Query("SELECT * FROM vat_rates ORDER BY percentage")
    fun observeVatRates(): Flow<List<VatRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVatRate(rate: VatRateEntity): Long

    @Query("SELECT * FROM service_rates WHERE serviceId = :serviceId ORDER BY unit")
    fun observeRates(serviceId: Long): Flow<List<ServiceRateEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRate(rate: ServiceRateEntity): Long
}
