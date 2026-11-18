package com.igmjardines.app.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val taxId: String = "",
    val phone: String = "",
    val email: String = "",
    val address: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false
)

@Entity(
    tableName = "gardens",
    foreignKeys = [
        ForeignKey(
            entity = ClientEntity::class,
            parentColumns = ["id"],
            childColumns = ["clientId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("clientId")]
)
data class GardenEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val clientId: Long,
    val name: String,
    val address: String = "",
    val notes: String = "",
    val isArchived: Boolean = false
)

@Dao
interface BusinessDao {
    @Query("SELECT * FROM clients WHERE isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeClients(): Flow<List<ClientEntity>>

    @Query("SELECT * FROM clients WHERE isArchived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchivedClients(): Flow<List<ClientEntity>>

    @Query("UPDATE clients SET isArchived = :archived WHERE id = :clientId")
    suspend fun setClientArchived(clientId: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClient(client: ClientEntity): Long

    @Update
    suspend fun updateClient(client: ClientEntity)

    @Query("SELECT * FROM gardens WHERE clientId = :clientId AND isArchived = 0 ORDER BY name COLLATE NOCASE")
    fun observeGardens(clientId: Long): Flow<List<GardenEntity>>

    @Query("SELECT * FROM gardens WHERE clientId = :clientId AND isArchived = 1 ORDER BY name COLLATE NOCASE")
    fun observeArchivedGardens(clientId: Long): Flow<List<GardenEntity>>

    @Query("UPDATE gardens SET isArchived = :archived WHERE id = :gardenId")
    suspend fun setGardenArchived(gardenId: Long, archived: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGarden(garden: GardenEntity): Long

    @Update
    suspend fun updateGarden(garden: GardenEntity)

}

@Database(
    entities = [
        ClientEntity::class, GardenEntity::class,
        ServiceEntity::class, ServiceRateEntity::class, VatRateEntity::class,
        EstimateEntity::class, EstimateLineEntity::class,
        InvoiceEntity::class, InvoiceLineEntity::class, InvoicePaymentEntity::class, CompanyProfileEntity::class, WorkOrderEntity::class, ExpenseEntity::class, MachineEntity::class, MachineMaintenanceEntity::class, TravelLogEntity::class
    ],
    version = 13,
    exportSchema = false
)
abstract class BusinessDatabase : RoomDatabase() {
    abstract fun businessDao(): BusinessDao
    abstract fun servicesDao(): ServicesDao
    abstract fun estimatesDao(): EstimatesDao
    abstract fun invoicesDao(): InvoicesDao
    abstract fun companyDao(): CompanyDao
    abstract fun workOrdersDao(): WorkOrdersDao
    abstract fun expensesDao(): ExpensesDao
    abstract fun assetsDao(): AssetsDao
    abstract fun travelDao(): TravelDao

    companion object {
        // Migraciones explícitas: no se permite borrar automáticamente la base de datos.
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS services (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, category TEXT NOT NULL, description TEXT NOT NULL, unit TEXT NOT NULL, priceExVat REAL NOT NULL, vatRate REAL NOT NULL, active INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_services_category ON services(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_services_name ON services(name)")
                db.execSQL("CREATE TABLE IF NOT EXISTS service_rates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, serviceId INTEGER NOT NULL, unit TEXT NOT NULL, priceExVat REAL NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_service_rates_serviceId ON service_rates(serviceId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS vat_rates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, label TEXT NOT NULL, percentage REAL NOT NULL, isDefault INTEGER NOT NULL)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS estimates (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, number TEXT NOT NULL, clientId INTEGER NOT NULL, clientNameSnapshot TEXT NOT NULL, gardenId INTEGER, gardenNameSnapshot TEXT NOT NULL, issueDate INTEGER NOT NULL, status TEXT NOT NULL, baseAmount REAL NOT NULL, vatAmount REAL NOT NULL, discountAmount REAL NOT NULL, totalAmount REAL NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_estimates_clientId ON estimates(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_estimates_gardenId ON estimates(gardenId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS estimate_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, estimateId INTEGER NOT NULL, serviceId INTEGER, descriptionSnapshot TEXT NOT NULL, unitSnapshot TEXT NOT NULL, quantity REAL NOT NULL, unitPriceExVat REAL NOT NULL, vatRateSnapshot REAL NOT NULL, lineBase REAL NOT NULL, lineVat REAL NOT NULL, lineTotal REAL NOT NULL, FOREIGN KEY(estimateId) REFERENCES estimates(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_estimate_lines_estimateId ON estimate_lines(estimateId)")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS invoices (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, number TEXT NOT NULL, clientId INTEGER NOT NULL, clientNameSnapshot TEXT NOT NULL, gardenId INTEGER, gardenNameSnapshot TEXT NOT NULL, estimateId INTEGER, issueDate INTEGER NOT NULL, dueDate INTEGER, status TEXT NOT NULL, baseAmount REAL NOT NULL, vatAmount REAL NOT NULL, totalAmount REAL NOT NULL, paidAmount REAL NOT NULL, notes TEXT NOT NULL, correctionOfInvoiceId INTEGER, correctionReason TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_clientId ON invoices(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_estimateId ON invoices(estimateId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoices_issueDate ON invoices(issueDate)")
                db.execSQL("CREATE TABLE IF NOT EXISTS invoice_lines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, serviceId INTEGER, descriptionSnapshot TEXT NOT NULL, unitSnapshot TEXT NOT NULL, quantity REAL NOT NULL, unitPriceExVat REAL NOT NULL, vatRateSnapshot REAL NOT NULL, lineBase REAL NOT NULL, lineVat REAL NOT NULL, lineTotal REAL NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_lines_invoiceId ON invoice_lines(invoiceId)")
                db.execSQL("CREATE TABLE IF NOT EXISTS invoice_payments (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, invoiceId INTEGER NOT NULL, amount REAL NOT NULL, paymentDate INTEGER NOT NULL, method TEXT NOT NULL, note TEXT NOT NULL, FOREIGN KEY(invoiceId) REFERENCES invoices(id) ON UPDATE NO ACTION ON DELETE CASCADE)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_invoice_payments_invoiceId ON invoice_payments(invoiceId)")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS company_profile (id INTEGER NOT NULL PRIMARY KEY, tradeName TEXT NOT NULL, tagline TEXT NOT NULL, holderName TEXT NOT NULL, taxId TEXT NOT NULL, phone TEXT NOT NULL, email TEXT NOT NULL, address TEXT NOT NULL, postalCode TEXT NOT NULL, city TEXT NOT NULL, iban TEXT NOT NULL, website TEXT NOT NULL, logoUri TEXT NOT NULL, footer TEXT NOT NULL)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS work_orders (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, clientId INTEGER NOT NULL, clientNameSnapshot TEXT NOT NULL, gardenId INTEGER, gardenNameSnapshot TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, scheduledAt INTEGER NOT NULL, durationMinutes INTEGER NOT NULL, status TEXT NOT NULL, notes TEXT NOT NULL, FOREIGN KEY(clientId) REFERENCES clients(id) ON UPDATE NO ACTION ON DELETE RESTRICT, FOREIGN KEY(gardenId) REFERENCES gardens(id) ON UPDATE NO ACTION ON DELETE SET NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_orders_clientId ON work_orders(clientId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_orders_gardenId ON work_orders(gardenId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_orders_scheduledAt ON work_orders(scheduledAt)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_work_orders_status ON work_orders(status)")
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS business_expenses (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, concept TEXT NOT NULL, category TEXT NOT NULL, expenseDate INTEGER NOT NULL, baseAmount REAL NOT NULL, vatAmount REAL NOT NULL, totalAmount REAL NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_business_expenses_expenseDate ON business_expenses(expenseDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_business_expenses_category ON business_expenses(category)")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS machines (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, name TEXT NOT NULL, brandModel TEXT NOT NULL, machineType TEXT NOT NULL, purchaseDate INTEGER, purchasePrice REAL NOT NULL, operatingHours REAL NOT NULL, nextServiceHours REAL, nextServiceDate INTEGER, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_machines_name ON machines(name)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_machines_nextServiceDate ON machines(nextServiceDate)")
                db.execSQL("CREATE TABLE IF NOT EXISTS machine_maintenance (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, machineId INTEGER NOT NULL, maintenanceDate INTEGER NOT NULL, description TEXT NOT NULL, cost REAL NOT NULL, hoursAtService REAL, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_machine_maintenance_machineId ON machine_maintenance(machineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_machine_maintenance_maintenanceDate ON machine_maintenance(maintenanceDate)")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS travel_logs (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, travelDate INTEGER NOT NULL, clientName TEXT NOT NULL, origin TEXT NOT NULL, destination TEXT NOT NULL, roundTripKm REAL NOT NULL, fuelLitres REAL NOT NULL, fuelCost REAL NOT NULL, otherCost REAL NOT NULL, durationMinutes INTEGER NOT NULL, notes TEXT NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travel_logs_travelDate ON travel_logs(travelDate)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travel_logs_clientName ON travel_logs(clientName)")
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE work_orders ADD COLUMN beforePhotoPath TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE work_orders ADD COLUMN afterPhotoPath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE clients ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE gardens ADD COLUMN isArchived INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_expenses ADD COLUMN receiptPath TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_expenses ADD COLUMN workOrderId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_business_expenses_workOrderId ON business_expenses(workOrderId)")
                db.execSQL("ALTER TABLE travel_logs ADD COLUMN workOrderId INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_travel_logs_workOrderId ON travel_logs(workOrderId)")
            }
        }

        @Volatile private var INSTANCE: BusinessDatabase? = null

        fun resetAfterRestore() { INSTANCE = null }

        fun get(context: Context): BusinessDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    BusinessDatabase::class.java,
                    "igm_jardines.db"
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13).build().also { INSTANCE = it }
            }
    }
}
