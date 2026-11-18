package com.igmjardines.app

import android.content.Context
import android.net.Uri
import android.database.sqlite.SQLiteDatabase
import com.igmjardines.app.data.BusinessDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.BufferedInputStream
import java.security.MessageDigest
import java.util.zip.ZipFile
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Copia conjunta y restauración validada de la base de datos y las fotos privadas. */
object BackupManager {
    private const val DB_ENTRY = "database/igm_jardines.db"
    private const val PHOTO_PREFIX = "photos/"
    private const val RECEIPT_PREFIX = "expense_receipts/"
    private const val MANIFEST_ENTRY = "backup/manifest.txt"

    private data class EntryDigest(val size: Long, val sha256: String)

    suspend fun export(context: Context, db: BusinessDatabase, destination: Uri) = withContext(Dispatchers.IO) {
        val archive = File(context.cacheDir, "igm_jardines_backup_tmp.zip")
        try {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
            db.close()
            val source = context.getDatabasePath("igm_jardines.db")
            require(source.exists() && source.length() > 0L) { "No se encuentra la base de datos para copiar." }
            val manifest = mutableListOf<String>()
            var totalSourceBytes = 0L

            fun addFile(zip: ZipOutputStream, entryName: String, file: File) {
                val maxBytes = if (entryName == DB_ENTRY) 200L * 1024L * 1024L else 25L * 1024L * 1024L
                require(file.length() <= maxBytes) { "El archivo ${file.name} supera el tamaño máximo admitido para la copia." }
                require(!entryName.contains('\t') && !entryName.contains('\n')) { "Nombre de archivo no válido para la copia." }
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                zip.putNextEntry(ZipEntry(entryName))
                file.inputStream().buffered().use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        zip.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                        size += count
                    }
                }
                zip.closeEntry()
                totalSourceBytes += size
                require(totalSourceBytes <= 750L * 1024L * 1024L) { "La copia supera el tamaño máximo total permitido." }
                manifest += "$entryName\t$size\t${digest.digest().toHex()}"
            }

            archive.delete()
            ZipOutputStream(archive.outputStream().buffered()).use { zip ->
                addFile(zip, DB_ENTRY, source)
                val photos = File(context.filesDir, "work_photos")
                if (photos.exists()) photos.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(photos).invariantSeparatorsPath }.forEach { photo ->
                    val relative = photo.relativeTo(photos).invariantSeparatorsPath
                    require(relative.isNotBlank() && !relative.startsWith("../")) { "Ruta de fotografía no válida." }
                    addFile(zip, PHOTO_PREFIX + relative, photo)
                }
                val receipts = File(context.filesDir, "expense_receipts")
                if (receipts.exists()) receipts.walkTopDown().filter { it.isFile }.sortedBy { it.relativeTo(receipts).invariantSeparatorsPath }.forEach { receipt ->
                    val relative = receipt.relativeTo(receipts).invariantSeparatorsPath
                    require(relative.isNotBlank() && !relative.startsWith("../")) { "Ruta de justificante no válida." }
                    addFile(zip, RECEIPT_PREFIX + relative, receipt)
                }
                zip.putNextEntry(ZipEntry(MANIFEST_ENTRY))
                zip.write((listOf("IGM_BACKUP_V1") + manifest).joinToString("\n").toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // Verifica el archivo completo antes de copiarlo al destino elegido por el usuario.
            verifyArchive(archive)
            context.contentResolver.openOutputStream(destination, "wt")?.use { output ->
                archive.inputStream().buffered().use { it.copyTo(output) }
            } ?: error("Android no ha permitido crear el archivo de copia.")
            context.getSharedPreferences("igm_reminders", Context.MODE_PRIVATE).edit().putLong("last_backup_at", System.currentTimeMillis()).apply()
        } finally {
            archive.delete()
            BusinessDatabase.resetAfterRestore()
        }
    }

    suspend fun restore(context: Context, db: BusinessDatabase, sourceUri: Uri) = withContext(Dispatchers.IO) {
        val tempDb = File(context.cacheDir, "igm_restore_check.db")
        val tempPhotos = File(context.cacheDir, "igm_restore_photos")
        val tempReceipts = File(context.cacheDir, "igm_restore_receipts")
        val tempManifest = File(context.cacheDir, "igm_restore_manifest.txt")
        val live = context.getDatabasePath("igm_jardines.db")
        val previous = File(context.filesDir, "igm_jardines_pre_restore.db")
        val livePhotos = File(context.filesDir, "work_photos")
        val previousPhotos = File(context.filesDir, "work_photos_pre_restore")
        val liveReceipts = File(context.filesDir, "expense_receipts")
        val previousReceipts = File(context.filesDir, "expense_receipts_pre_restore")
        var replacementStarted = false
        try {
            tempPhotos.deleteRecursively(); tempPhotos.mkdirs()
            tempReceipts.deleteRecursively(); tempReceipts.mkdirs()
            tempManifest.delete()
            var manifestContent: String? = null
            val extractedDigests = linkedMapOf<String, EntryDigest>()
            var foundDatabase = false
            var extractedBytes = 0L
            var entryCount = 0
            val seenEntries = mutableSetOf<String>()
            fun copyEntry(input: java.io.InputStream, target: File, maxEntryBytes: Long): EntryDigest {
                val digest = MessageDigest.getInstance("SHA-256")
                var entryBytes = 0L
                target.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        entryBytes += count
                        extractedBytes += count
                        require(entryBytes <= maxEntryBytes && extractedBytes <= 750L * 1024L * 1024L) {
                            "La copia supera el tamaño máximo permitido. No se ha restaurado."
                        }
                        output.write(buffer, 0, count)
                        digest.update(buffer, 0, count)
                    }
                }
                return EntryDigest(entryBytes, digest.digest().toHex())
            }
            context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                val buffered = BufferedInputStream(stream)
                buffered.mark(4)
                val signature = ByteArray(2)
                val read = buffered.read(signature)
                buffered.reset()
                val isZip = read == 2 && signature[0] == 0x50.toByte() && signature[1] == 0x4b.toByte()
                if (isZip) {
                    ZipInputStream(buffered).use { zip ->
                        while (true) {
                            val entry = zip.nextEntry ?: break
                            if (entry.isDirectory) { zip.closeEntry(); continue }
                            entryCount++
                            require(entryCount <= 10000) { "La copia contiene demasiados archivos." }
                            require(seenEntries.add(entry.name)) { "La copia contiene entradas duplicadas." }
                            when {
                                entry.name == MANIFEST_ENTRY -> {
                                    copyEntry(zip, tempManifest, 2L * 1024L * 1024L)
                                    manifestContent = tempManifest.readText(Charsets.UTF_8)
                                }
                                entry.name == DB_ENTRY -> {
                                    require(!foundDatabase) { "La copia contiene más de una base de datos." }
                                    extractedDigests[entry.name] = copyEntry(zip, tempDb, 200L * 1024L * 1024L)
                                    foundDatabase = true
                                }
                                entry.name.startsWith(PHOTO_PREFIX) -> {
                                    val relative = entry.name.removePrefix(PHOTO_PREFIX)
                                    require(relative.isNotBlank() && !relative.startsWith("/") && relative.split('/').none { it == ".." || it.isBlank() }) { "La copia contiene una ruta de fotografía no válida." }
                                    val target = File(tempPhotos, relative).canonicalFile
                                    require(target.toPath().startsWith(tempPhotos.canonicalFile.toPath())) { "Ruta de fotografía fuera de la carpeta permitida." }
                                    target.parentFile?.mkdirs()
                                    extractedDigests[entry.name] = copyEntry(zip, target, 25L * 1024L * 1024L)
                                }
                                entry.name.startsWith(RECEIPT_PREFIX) -> {
                                    val relative = entry.name.removePrefix(RECEIPT_PREFIX)
                                    require(relative.isNotBlank() && !relative.startsWith("/") && relative.split('/').none { it == ".." || it.isBlank() }) { "La copia contiene una ruta de justificante no válida." }
                                    val target = File(tempReceipts, relative).canonicalFile
                                    require(target.toPath().startsWith(tempReceipts.canonicalFile.toPath())) { "Ruta de justificante fuera de la carpeta permitida." }
                                    target.parentFile?.mkdirs()
                                    extractedDigests[entry.name] = copyEntry(zip, target, 25L * 1024L * 1024L)
                                }
                                else -> error("La copia contiene un archivo no reconocido: ${entry.name}")
                            }
                            zip.closeEntry()
                        }
                    }
                } else {
                    // Compatibilidad con las copias antiguas que contenían únicamente el .db.
                    copyEntry(buffered, tempDb, 200L * 1024L * 1024L)
                    foundDatabase = true
                }
            } ?: error("No se ha podido leer el archivo seleccionado.")
            require(foundDatabase && tempDb.exists() && tempDb.length() > 0L) { "El archivo no contiene una copia IGM JARDINES válida." }
            manifestContent?.let { content ->
                val lines = content.split('\n').map { it.trimEnd('\r') }
                require(lines.firstOrNull() == "IGM_BACKUP_V1") { "El manifiesto de la copia no es válido." }
                val expected = linkedMapOf<String, EntryDigest>()
                lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                    val parts = line.split('\t')
                    require(parts.size == 3 && parts[0].isNotBlank()) { "El manifiesto contiene una entrada incorrecta." }
                    val size = parts[1].toLongOrNull()
                    require(size != null && size >= 0L && parts[2].matches(Regex("[0-9a-f]{64}"))) { "El manifiesto contiene una huella no válida." }
                    require(expected.put(parts[0], EntryDigest(size, parts[2])) == null) { "El manifiesto contiene archivos duplicados." }
                }
                require(expected.keys == extractedDigests.keys) { "La copia está incompleta o contiene archivos no declarados." }
                expected.forEach { (name, digest) ->
                    require(extractedDigests[name] == digest) { "El archivo ${name.substringAfterLast('/')} no supera la comprobación de integridad." }
                }
            }
            val checkDb = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val integrity = checkDb.rawQuery("PRAGMA integrity_check", null).use { c -> if (c.moveToFirst()) c.getString(0) else "" }
                require(integrity == "ok") { "La base de datos está dañada y no se restaurará." }
                val version = checkDb.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); c.getInt(0) }
                require(version in 9..13) { "Esta copia pertenece a una versión no compatible (versión $version)." }
                val requiredTables = listOf("clients", "gardens", "services", "service_rates", "vat_rates", "estimates", "estimate_lines", "invoices", "invoice_lines", "invoice_payments", "company_profile", "work_orders", "business_expenses", "machines", "machine_maintenance", "travel_logs")
                val foundTables = checkDb.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(0)) } }
                require(foundTables.containsAll(requiredTables)) { "La copia no contiene todas las tablas de IGM JARDINES." }
                if (version >= 10) {
                    val columns = checkDb.rawQuery("PRAGMA table_info(work_orders)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    require(columns.containsAll(listOf("beforePhotoPath", "afterPhotoPath"))) { "Faltan campos de fotografías en la base de datos." }
                }
                if (version >= 12) {
                    val expenseColumns = checkDb.rawQuery("PRAGMA table_info(business_expenses)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    require("receiptPath" in expenseColumns) { "Falta el campo de justificantes de gastos." }
                }
                if (version >= 13) {
                    val expenseColumns = checkDb.rawQuery("PRAGMA table_info(business_expenses)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    val travelColumns = checkDb.rawQuery("PRAGMA table_info(travel_logs)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    require("workOrderId" in expenseColumns && "workOrderId" in travelColumns) { "Faltan los vínculos de costes a trabajos." }
                }
                if (version >= 11) {
                    val clientColumns = checkDb.rawQuery("PRAGMA table_info(clients)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    val gardenColumns = checkDb.rawQuery("PRAGMA table_info(gardens)", null).use { c -> buildSet { while (c.moveToNext()) add(c.getString(1)) } }
                    require("isArchived" in clientColumns && "isArchived" in gardenColumns) { "Faltan los campos de archivo de clientes y jardines." }
                }
            } finally { checkDb.close() }
            // Las rutas absolutas pueden cambiar al restaurar en otro dispositivo o perfil Android.
            val writableCheck = SQLiteDatabase.openDatabase(tempDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
            try {
                val version = writableCheck.version
                if (version >= 10) {
                    val prefix = File(context.filesDir, "work_photos").absolutePath + "/"
                    writableCheck.execSQL("UPDATE work_orders SET beforePhotoPath = ? || substr(beforePhotoPath, instr(beforePhotoPath, '/work_photos/') + 13) WHERE beforePhotoPath LIKE '%/work_photos/%'", arrayOf(prefix))
                    writableCheck.execSQL("UPDATE work_orders SET afterPhotoPath = ? || substr(afterPhotoPath, instr(afterPhotoPath, '/work_photos/') + 13) WHERE afterPhotoPath LIKE '%/work_photos/%'", arrayOf(prefix))
                }
                if (version >= 12) {
                    val receiptPrefix = File(context.filesDir, "expense_receipts").absolutePath + "/"
                    writableCheck.execSQL("UPDATE business_expenses SET receiptPath = ? || substr(receiptPath, instr(receiptPath, '/expense_receipts/') + 18) WHERE receiptPath LIKE '%/expense_receipts/%'", arrayOf(receiptPrefix))
                }
            } finally { writableCheck.close() }

            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { }
            db.close()
            live.parentFile?.mkdirs()
            if (previous.exists()) previous.delete()
            if (previousPhotos.exists()) previousPhotos.deleteRecursively()
            if (live.exists()) live.copyTo(previous, overwrite = true)
            if (livePhotos.exists()) livePhotos.copyRecursively(previousPhotos, overwrite = true)
            if (liveReceipts.exists()) liveReceipts.copyRecursively(previousReceipts, overwrite = true)
            replacementStarted = true
            tempDb.copyTo(live, overwrite = true)
            File(live.parentFile, "igm_jardines.db-wal").delete()
            File(live.parentFile, "igm_jardines.db-shm").delete()
            livePhotos.deleteRecursively()
            if (tempPhotos.exists()) tempPhotos.copyRecursively(livePhotos, overwrite = true)
            liveReceipts.deleteRecursively()
            if (tempReceipts.exists()) tempReceipts.copyRecursively(liveReceipts, overwrite = true)
            previous.delete(); previousPhotos.deleteRecursively(); previousReceipts.deleteRecursively()
            BusinessDatabase.resetAfterRestore()
        } catch (t: Throwable) {
            if (replacementStarted) {
                if (previous.exists()) previous.copyTo(live, overwrite = true)
                livePhotos.deleteRecursively()
                if (previousPhotos.exists()) previousPhotos.copyRecursively(livePhotos, overwrite = true)
                liveReceipts.deleteRecursively()
                if (previousReceipts.exists()) previousReceipts.copyRecursively(liveReceipts, overwrite = true)
            }
            if (!db.isOpen) BusinessDatabase.resetAfterRestore()
            throw t
        } finally {
            tempDb.delete(); tempPhotos.deleteRecursively(); tempReceipts.deleteRecursively(); tempManifest.delete()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun verifyArchive(file: File) {
        ZipFile(file).use { zip ->
            val manifestEntry = zip.getEntry(MANIFEST_ENTRY) ?: error("No se ha generado el manifiesto de integridad.")
            val content = zip.getInputStream(manifestEntry).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val lines = content.split('\n').map { it.trimEnd('\r') }
            require(lines.firstOrNull() == "IGM_BACKUP_V1") { "El manifiesto generado no es válido." }
            val expectedNames = mutableSetOf<String>()
            lines.drop(1).filter { it.isNotBlank() }.forEach { line ->
                val parts = line.split('\t')
                require(parts.size == 3 && expectedNames.add(parts[0])) { "El manifiesto generado contiene entradas duplicadas." }
                val expectedSize = parts[1].toLongOrNull() ?: error("Tamaño no válido en el manifiesto.")
                val expectedHash = parts[2]
                val entry = zip.getEntry(parts[0]) ?: error("Falta un archivo en la copia generada.")
                val digest = MessageDigest.getInstance("SHA-256")
                var size = 0L
                zip.getInputStream(entry).use { input ->
                    val buffer = ByteArray(32 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        size += count
                        digest.update(buffer, 0, count)
                    }
                }
                require(size == expectedSize && digest.digest().toHex() == expectedHash) { "La copia generada no ha superado la verificación." }
            }
            val actualNames = mutableSetOf<String>()
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val name = entries.nextElement().name
                if (name != MANIFEST_ENTRY) actualNames += name
            }
            require(actualNames == expectedNames) { "La copia generada contiene archivos no declarados." }
        }
    }

}
