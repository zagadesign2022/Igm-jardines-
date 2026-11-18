package com.igmjardines.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igmjardines.app.data.CompanyDao
import com.igmjardines.app.data.BusinessDatabase
import com.igmjardines.app.data.CompanyProfileEntity
import kotlinx.coroutines.launch

@Composable
fun CompanyScreen(dao: CompanyDao, db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val saved by dao.observe().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var backupMessage by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            runCatching { BackupManager.export(context, db, uri) }
                .onSuccess {
                    android.widget.Toast.makeText(context, "Copia exportada correctamente", android.widget.Toast.LENGTH_LONG).show()
                    (context as? android.app.Activity)?.recreate()
                }
                .onFailure {
                    backupMessage = "No se pudo exportar: ${it.message ?: "error desconocido"}"
                    if (!db.isOpen) (context as? android.app.Activity)?.recreate()
                }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching { BackupManager.restore(context, db, uri) }
                .onSuccess {
                    backupMessage = "Copia restaurada. Reiniciando la aplicación…"
                    (context as? android.app.Activity)?.recreate()
                }
                .onFailure {
                    backupMessage = "No se restauró la copia: ${it.message ?: "error desconocido"}"
                    if (!db.isOpen) (context as? android.app.Activity)?.recreate()
                }
        }
    }
    var form by remember { mutableStateOf(CompanyProfileEntity()) }
    var initialized by remember { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved != null && !initialized) { form = saved!!; initialized = true } }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            form = form.copy(logoUri = uri.toString())
        }
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Mi empresa", style = MaterialTheme.typography.headlineSmall)
        Text("Estos datos se utilizarán en los documentos nuevos. Editarlos no cambiará los documentos ya emitidos.", style = MaterialTheme.typography.bodySmall)
        OutlinedButton(onClick = { logoPicker.launch(arrayOf("image/*")) }) { Text(if (form.logoUri.isBlank()) "Seleccionar logo desde galería" else "Cambiar logo") }
        if (form.logoUri.isNotBlank()) Text("Logo seleccionado", style = MaterialTheme.typography.bodySmall)
        CompanyField(form.tradeName, "Nombre comercial") { form = form.copy(tradeName = it) }
        CompanyField(form.tagline, "Eslogan") { form = form.copy(tagline = it) }
        CompanyField(form.holderName, "Titular / nombre fiscal") { form = form.copy(holderName = it) }
        CompanyField(form.taxId, "NIF / CIF") { form = form.copy(taxId = it) }
        CompanyField(form.phone, "Teléfono") { form = form.copy(phone = it) }
        CompanyField(form.email, "Email") { form = form.copy(email = it) }
        CompanyField(form.address, "Dirección fiscal") { form = form.copy(address = it) }
        CompanyField(form.postalCode, "Código postal") { form = form.copy(postalCode = it) }
        CompanyField(form.city, "Localidad") { form = form.copy(city = it) }
        CompanyField(form.iban, "IBAN (opcional)") { form = form.copy(iban = it) }
        CompanyField(form.website, "Web (opcional)") { form = form.copy(website = it) }
        CompanyField(form.footer, "Pie de documento") { form = form.copy(footer = it) }
        Button(onClick = { scope.launch { dao.save(form); android.widget.Toast.makeText(context, "Datos de empresa guardados", android.widget.Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth()) { Text("Guardar datos de empresa") }
        HorizontalDivider()
        Text("Copias de seguridad", style = MaterialTheme.typography.titleLarge)
        Text("Guarda una copia completa de tus clientes, servicios, presupuestos, facturas, trabajos, gastos, maquinaria y desplazamientos. El archivo se guarda donde tú elijas.", style = MaterialTheme.typography.bodySmall)
        Button(onClick = { exportLauncher.launch("IGM_JARDINES_copia_${System.currentTimeMillis()}.zip") }, modifier = Modifier.fillMaxWidth()) { Text("Exportar copia de seguridad") }
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("*/*", "application/zip", "application/octet-stream", "application/x-sqlite3")) }, modifier = Modifier.fillMaxWidth()) { Text("Restaurar una copia") }
        Text("Al restaurar, los datos actuales se sustituyen por los de la copia. La aplicación conserva una copia interna previa por seguridad.", style = MaterialTheme.typography.bodySmall)
        if (backupMessage.isNotBlank()) Text(backupMessage, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CompanyField(value: String, label: String, onValueChange: (String) -> Unit) {
    OutlinedTextField(value, onValueChange, label = { Text(label) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
}
