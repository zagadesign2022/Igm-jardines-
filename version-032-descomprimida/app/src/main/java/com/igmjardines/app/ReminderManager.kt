package com.igmjardines.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.igmjardines.app.data.BusinessDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

object ReminderManager {
    const val PREFS = "igm_reminders"
    const val KEY_ENABLED = "enabled"
    const val KEY_BACKUP_DAYS = "backup_days"
    private const val UNIQUE_WORK = "igm_daily_reminders"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("igm_alerts", "Avisos IGM JARDINES", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recordatorios de copias de seguridad y mantenimiento de maquinaria"
            })
        }
    }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences(ReminderManager.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(ReminderManager.KEY_ENABLED, true)) return@withContext Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return@withContext Result.success()
        ReminderManager.ensureChannel(applicationContext)
        val now = System.currentTimeMillis()
        val db = BusinessDatabase.get(applicationContext)
        val alerts = mutableListOf<String>()
        try {
            db.assetsDao().getAllMachines().forEach { machine ->
                val dueByHours = machine.nextServiceHours != null && machine.operatingHours >= machine.nextServiceHours
                val dueByDate = machine.nextServiceDate != null && machine.nextServiceDate <= now + TimeUnit.DAYS.toMillis(7)
                if (dueByHours || dueByDate) {
                    val reason = when { dueByHours && dueByDate -> "por horas y fecha"; dueByHours -> "por horas de uso"; else -> "por fecha" }
                    alerts.add("${machine.name}: revisión pendiente $reason")
                }
            }
        } catch (_: Exception) { /* No bloquear el recordatorio de copias si falla la lectura de maquinaria. */ }
        val interval = prefs.getInt(ReminderManager.KEY_BACKUP_DAYS, 7).coerceIn(7, 30)
        val lastBackup = prefs.getLong("last_backup_at", 0L)
        if (lastBackup == 0L || now - lastBackup >= TimeUnit.DAYS.toMillis(interval.toLong())) {
            alerts.add("Hace falta crear una copia de seguridad de tus datos.")
        }
        if (alerts.isNotEmpty()) {
            val body = alerts.take(5).joinToString("\n") + if (alerts.size > 5) "\nY ${alerts.size - 5} avisos más" else ""
            val notification = NotificationCompat.Builder(applicationContext, "igm_alerts")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("IGM JARDINES · Avisos")
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
                .setContentText(alerts.first())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true).build()
            try { NotificationManagerCompat.from(applicationContext).notify(1101, notification) } catch (_: SecurityException) { }
        }
        Result.success()
    }
}
