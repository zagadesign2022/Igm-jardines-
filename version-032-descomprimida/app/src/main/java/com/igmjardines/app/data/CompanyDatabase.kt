package com.igmjardines.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "company_profile")
data class CompanyProfileEntity(
    @PrimaryKey val id: Int = 1,
    val tradeName: String = "IGM JARDINES",
    val tagline: String = "Tu jardín en buenas manos",
    val holderName: String = "",
    val taxId: String = "",
    val phone: String = "666249504",
    val email: String = "",
    val address: String = "",
    val postalCode: String = "",
    val city: String = "",
    val iban: String = "",
    val website: String = "",
    val logoUri: String = "",
    val footer: String = "Gracias por confiar en IGM JARDINES"
)

@Dao
interface CompanyDao {
    @Query("SELECT * FROM company_profile WHERE id = 1")
    fun observe(): Flow<CompanyProfileEntity?>

    @Query("SELECT * FROM company_profile WHERE id = 1")
    suspend fun get(): CompanyProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(profile: CompanyProfileEntity)
}
