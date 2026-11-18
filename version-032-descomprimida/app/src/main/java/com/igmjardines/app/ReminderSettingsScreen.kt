package com.igmjardines.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

@Composable
fun ReminderSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(ReminderManager.PREFS, android.content.Context.MODE_PRIVATE) }
    var enabled by remember { mutableStateOf(prefs.getBoolean(ReminderManager.KEY_ENABLED, true)) }
    var days by remember { mutableIntStateOf(prefs.getInt(ReminderManager.KEY_BACKUP_DAYS, 7)) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(modifier.fillMaxSize().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Avisos y recordatorios", style = MaterialTheme.typography.headlineSmall)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Notificaciones locales", style = MaterialTheme.typography.titleMedium)
                Text("La aplicación revisará una vez al día si hay maquinaria que necesita mantenimiento y si toca hacer una copia de seguridad. No necesita internet.", style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Activar avisos")
                    Switch(checked = enabled, onCheckedChange = {
                        enabled = it; prefs.edit().putBoolean(ReminderManager.KEY_ENABLED, it).apply()
                        ReminderManager.schedule(context)
                    })
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Recordatorio de copias", style = MaterialTheme.typography.titleMedium)
                Text("Avisar si no has exportado una copia en el intervalo elegido.")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = days == 7, onClick = { days = 7; prefs.edit().putInt(ReminderManager.KEY_BACKUP_DAYS, 7).apply() }, label = { Text("Cada semana") })
                    FilterChip(selected = days == 30, onClick = { days = 30; prefs.edit().putInt(ReminderManager.KEY_BACKUP_DAYS, 30).apply() }, label = { Text("Cada mes") })
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Avisos de maquinaria", style = MaterialTheme.typography.titleMedium)
                Text("Te avisará cuando se alcance la hora de revisión indicada o cuando falten siete días o menos para la fecha programada.")
            }
        }
        Button(onClick = {
            ReminderManager.ensureChannel(context)
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            ReminderManager.schedule(context)
        }, modifier = Modifier.fillMaxWidth()) { Text("Activar y programar avisos") }
        Text("Android puede limitar la hora exacta de entrega; son avisos diarios aproximados, no alarmas de precisión.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
