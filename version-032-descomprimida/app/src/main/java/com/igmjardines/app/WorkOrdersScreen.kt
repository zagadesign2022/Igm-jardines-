package com.igmjardines.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igmjardines.app.data.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun WorkOrdersScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clients by db.businessDao().observeClients().collectAsStateWithLifecycle(initialValue = emptyList())
    val orders by db.workOrdersDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<WorkOrderEntity?>(null) }
    var deleting by remember { mutableStateOf<WorkOrderEntity?>(null) }
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedClient by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedGarden by remember { mutableStateOf<GardenEntity?>(null) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(java.util.Date())) }
    var duration by remember { mutableStateOf("60") }
    var recurrence by remember { mutableStateOf("Sin repetición") }
    var showAll by remember { mutableStateOf(false) }
    var selectedDay by remember { mutableStateOf<Int?>(null) }
    var showGardenHistory by remember { mutableStateOf(false) }
    var historyClientId by remember { mutableStateOf<Long?>(null) }
    var historyGardenId by remember { mutableStateOf<Long?>(null) }
    val monthStart = remember(monthOffset) { java.util.Calendar.getInstance().apply { set(java.util.Calendar.DAY_OF_MONTH, 1); add(java.util.Calendar.MONTH, monthOffset); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0); set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0) } }
    val monthEnd = remember(monthOffset) { (monthStart.clone() as java.util.Calendar).apply { add(java.util.Calendar.MONTH, 1) } }
    val monthDays = remember(monthOffset) {
        val first = (monthStart.clone() as java.util.Calendar)
        val leading = (first.get(java.util.Calendar.DAY_OF_WEEK) + 5) % 7
        val total = first.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
        (List(leading) { 0 } + (1..total).toList()).chunked(7).map { week -> week + List(7 - week.size) { 0 } }
    }
    val visibleOrders = remember(orders, showAll, monthStart.timeInMillis, monthEnd.timeInMillis, selectedDay) {
        val base = if (showAll) orders else orders.filter { it.scheduledAt >= monthStart.timeInMillis && it.scheduledAt < monthEnd.timeInMillis }
        if (selectedDay == null || showAll) base else base.filter { java.util.Calendar.getInstance().apply { timeInMillis = it.scheduledAt }.get(java.util.Calendar.DAY_OF_MONTH) == selectedDay }
    }
    val gardens by db.businessDao().observeGardens(selectedClient?.id ?: -1L).collectAsStateWithLifecycle(initialValue = emptyList())
    val historyGardens by db.businessDao().observeGardens(historyClientId ?: -1L).collectAsStateWithLifecycle(initialValue = emptyList())
    val gardenHistory = remember(orders, historyGardenId) { orders.filter { historyGardenId != null && it.gardenId == historyGardenId }.sortedByDescending { it.scheduledAt } }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Trabajos y agenda", style = MaterialTheme.typography.headlineSmall)
                Text("${orders.size} trabajos registrados")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { showGardenHistory = true; historyClientId = null; historyGardenId = null }) { Text("Historial") }
                Button(onClick = { showAdd = true }) { Text("Nuevo") }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { monthOffset--; selectedDay = null }) { Text("‹ Anterior") }
            Text(SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(monthStart.time).replaceFirstChar { it.uppercase(Locale("es", "ES")) }, modifier = Modifier.padding(top = 12.dp), fontWeight = FontWeight.SemiBold)
            TextButton(onClick = { monthOffset++; selectedDay = null }) { Text("Siguiente ›") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !showAll, onClick = { showAll = false; selectedDay = null }, label = { Text("Este mes") })
            FilterChip(selected = showAll, onClick = { showAll = true; selectedDay = null }, label = { Text("Todos") })
            if (monthOffset != 0) TextButton(onClick = { monthOffset = 0; selectedDay = null }) { Text("Hoy") }
        }
        if (!showAll) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("L", "M", "X", "J", "V", "S", "D").forEach { day ->
                    Box(Modifier.weight(1f), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(day, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            monthDays.forEach { week ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    week.forEach { day ->
                        val count = if (day == 0) 0 else orders.count { order ->
                            val c = java.util.Calendar.getInstance().apply { timeInMillis = order.scheduledAt }
                            c.get(java.util.Calendar.YEAR) == monthStart.get(java.util.Calendar.YEAR) && c.get(java.util.Calendar.MONTH) == monthStart.get(java.util.Calendar.MONTH) && c.get(java.util.Calendar.DAY_OF_MONTH) == day
                        }
                        Box(Modifier.weight(1f).height(42.dp).then(if (day > 0) Modifier.clickable { selectedDay = if (selectedDay == day) null else day } else Modifier), contentAlignment = androidx.compose.ui.Alignment.Center) {
                            if (day > 0) Surface(shape = CircleShape, color = if (selectedDay == day) MaterialTheme.colorScheme.primary else if (count > 0) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface) {
                                Column(Modifier.size(34.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                                    Text(day.toString(), style = MaterialTheme.typography.bodySmall, color = if (selectedDay == day) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface)
                                    if (count > 0) Text("•", style = MaterialTheme.typography.labelSmall, color = if (selectedDay == day) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { selectedDay = null }) { Text(if (selectedDay == null) "Ver todo el mes" else "Quitar filtro del día") }
            }
        }
        Text("Agenda · ${visibleOrders.size} trabajos", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        if (visibleOrders.isEmpty()) Text("No hay trabajos en este apartado.")
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visibleOrders, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleMedium)
                        Text("${item.clientNameSnapshot}${if (item.gardenNameSnapshot.isNotBlank()) " · ${item.gardenNameSnapshot}" else ""}")
                        Text("${SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(java.util.Date(item.scheduledAt))} · ${item.durationMinutes} min · ${item.status}", style = MaterialTheme.typography.bodySmall)
                        if (item.description.isNotBlank()) Text(item.description, style = MaterialTheme.typography.bodySmall)
                        WorkPhotoActions(db, item)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { editing = item; title = item.title; description = item.description; date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(java.util.Date(item.scheduledAt)); duration = item.durationMinutes.toString(); showAdd = true }) { Text("Editar") }
                            TextButton(onClick = { deleting = item }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
                            TextButton(onClick = { scope.launch { db.workOrdersDao().updateStatus(item.id, "En curso") } }) { Text("En curso") }
                            TextButton(onClick = { scope.launch { db.workOrdersDao().updateStatus(item.id, "Terminado") } }) { Text("Terminado") }
                        }
                    }
                }
            }
        }
    }

    if (showGardenHistory) AlertDialog(
        onDismissRequest = { showGardenHistory = false },
        title = { Text("Historial del jardín") },
        text = {
            Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("1. Selecciona el cliente", style = MaterialTheme.typography.labelLarge)
                clients.forEach { client ->
                    Row(Modifier.fillMaxWidth().clickable { historyClientId = client.id; historyGardenId = null }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = historyClientId == client.id, onClick = { historyClientId = client.id; historyGardenId = null })
                        Text(client.name)
                    }
                }
                if (historyClientId != null) {
                    Text("2. Selecciona el jardín", style = MaterialTheme.typography.labelLarge)
                    if (historyGardens.isEmpty()) Text("Este cliente todavía no tiene jardines registrados.")
                    historyGardens.forEach { garden ->
                        Row(Modifier.fillMaxWidth().clickable { historyGardenId = garden.id }, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            RadioButton(selected = historyGardenId == garden.id, onClick = { historyGardenId = garden.id })
                            Column {
                                Text(garden.name)
                                if (garden.address.isNotBlank()) Text(garden.address, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                if (historyGardenId != null) {
                    HorizontalDivider()
                    Text("Trabajos realizados y programados: ${gardenHistory.size}", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (gardenHistory.isEmpty()) Text("Todavía no hay trabajos asociados a este jardín.")
                    gardenHistory.forEach { work ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(work.title, fontWeight = FontWeight.SemiBold)
                                Text("${SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(java.util.Date(work.scheduledAt))} · ${work.status}", style = MaterialTheme.typography.bodySmall)
                                if (work.description.isNotBlank()) Text(work.description, style = MaterialTheme.typography.bodySmall)
                                val photoCount = listOf(work.beforePhotoPath, work.afterPhotoPath).count { it.isNotBlank() && java.io.File(it).exists() }
                                if (photoCount > 0) Text("Fotografías guardadas: $photoCount", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { showGardenHistory = false }) { Text("Cerrar") } }
    )

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false },
        title = { Text(if (editing == null) "Programar trabajo" else "Editar trabajo") },
        text = {
            Column(Modifier.heightIn(max = 440.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                if (editing != null) Text("Cliente: ${editing?.clientNameSnapshot} · ${editing?.gardenNameSnapshot}")
                if (editing == null && clients.isEmpty()) Text("Primero debes crear un cliente.")
                if (editing == null) clients.forEach { c ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selectedClient?.id == c.id, onClick = { selectedClient = c; selectedGarden = null })
                        Text(c.name, Modifier.padding(top = 12.dp))
                    }
                }
                if (editing == null && gardens.isNotEmpty()) {
                    Text("Jardín (opcional)", style = MaterialTheme.typography.labelLarge)
                    gardens.forEach { g ->
                        Row(Modifier.fillMaxWidth()) {
                            RadioButton(selectedGarden?.id == g.id, onClick = { selectedGarden = g })
                            Text(g.name, Modifier.padding(top = 12.dp))
                        }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Trabajo / tarea *") }, singleLine = true)
                OutlinedTextField(description, { description = it }, label = { Text("Descripción") })
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (dd/MM/aaaa)") }, singleLine = true)
                OutlinedTextField(duration, { duration = it }, label = { Text("Duración en minutos") }, singleLine = true)
                if (editing == null) Text("Repetición", style = MaterialTheme.typography.labelLarge)
                var recurrenceExpanded by remember { mutableStateOf(false) }
                if (editing == null) ExposedDropdownMenuBox(expanded = recurrenceExpanded, onExpandedChange = { recurrenceExpanded = it }) {
                    OutlinedTextField(recurrence, {}, readOnly = true, modifier = Modifier.menuAnchor().fillMaxWidth(), label = { Text("Frecuencia") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = recurrenceExpanded) })
                    ExposedDropdownMenu(expanded = recurrenceExpanded, onDismissRequest = { recurrenceExpanded = false }) {
                        listOf("Sin repetición", "Cada 15 días", "Mensual", "Cada temporada").forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { recurrence = option; recurrenceExpanded = false })
                        }
                    }
                }
                if (editing == null) Text("Los trabajos recurrentes se crearán por adelantado para los próximos 6 meses.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsedDate = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).apply { isLenient = false }.parse(date)?.time }.getOrNull()
                val mins = duration.toIntOrNull()
                val c = selectedClient
                val old = editing
                if ((old != null || c != null) && title.isNotBlank() && parsedDate != null && mins != null && mins > 0) {
                    scope.launch {
                        if (old != null) { db.workOrdersDao().update(old.copy(title = title.trim(), description = description.trim(), scheduledAt = parsedDate, durationMinutes = mins)) } else if (c != null) {
                        val cal = java.util.Calendar.getInstance().apply { timeInMillis = parsedDate }
                        val end = java.util.Calendar.getInstance().apply { timeInMillis = parsedDate; add(java.util.Calendar.MONTH, 6) }
                        var count = 0
                        do {
                            db.workOrdersDao().insert(WorkOrderEntity(clientId = c.id, clientNameSnapshot = c.name,
                                gardenId = selectedGarden?.id, gardenNameSnapshot = selectedGarden?.name ?: "",
                                title = title.trim(), description = description.trim(), scheduledAt = cal.timeInMillis,
                                durationMinutes = mins))
                            count++
                            when (recurrence) {
                                "Cada 15 días" -> cal.add(java.util.Calendar.DAY_OF_YEAR, 15)
                                "Mensual" -> cal.add(java.util.Calendar.MONTH, 1)
                                "Cada temporada" -> cal.add(java.util.Calendar.MONTH, 3)
                                else -> break
                            }
                        } while (recurrence != "Sin repetición" && cal.timeInMillis <= end.timeInMillis && count < 13)
                        }
                    }
                    showAdd = false; editing = null; selectedClient = null; selectedGarden = null; title = ""; description = ""; duration = "60"; recurrence = "Sin repetición"
                } else android.widget.Toast.makeText(context, "Revisa cliente, título, fecha y duración", android.widget.Toast.LENGTH_SHORT).show()
            }, enabled = clients.isNotEmpty() || editing != null) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false; editing = null }) { Text("Cancelar") } }
    )
    deleting?.let { item -> AlertDialog(onDismissRequest = { deleting = null }, title = { Text("Eliminar trabajo") }, text = { Text("¿Eliminar «${item.title}»? Esta acción no se puede deshacer.") }, confirmButton = { TextButton(onClick = { scope.launch { db.workOrdersDao().deleteById(item.id) }; deleting = null }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) } }, dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancelar") } }) }

}


@Composable
private fun WorkPhotoActions(db: BusinessDatabase, item: WorkOrderEntity) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    fun savePhoto(uri: android.net.Uri, before: Boolean) {
        scope.launch {
            runCatching {
                val folder = java.io.File(context.filesDir, "work_photos").apply { mkdirs() }
                val target = java.io.File(folder, "work_${item.id}_${if (before) "before" else "after"}_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { output -> input.copyTo(output) } }
                    ?: error("No se pudo leer la imagen")
                val current = item.copy(
                    beforePhotoPath = if (before) target.absolutePath else item.beforePhotoPath,
                    afterPhotoPath = if (!before) target.absolutePath else item.afterPhotoPath
                )
                db.workOrdersDao().update(current)
                android.widget.Toast.makeText(context, "Fotografía guardada", android.widget.Toast.LENGTH_SHORT).show()
            }.onFailure {
                android.widget.Toast.makeText(context, "No se pudo guardar la fotografía", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }
    val beforePicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri -> uri?.let { savePhoto(it, true) } }
    val afterPicker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri -> uri?.let { savePhoto(it, false) } }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(onClick = { beforePicker.launch("image/*") }) { Text(if (item.beforePhotoPath.isBlank()) "＋ Foto antes" else "Cambiar foto antes") }
            TextButton(onClick = { afterPicker.launch("image/*") }) { Text(if (item.afterPhotoPath.isBlank()) "＋ Foto después" else "Cambiar foto después") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Antes" to item.beforePhotoPath, "Después" to item.afterPhotoPath).forEach { (label, path) ->
                if (path.isNotBlank() && java.io.File(path).exists()) {
                    Column(Modifier.width(92.dp)) {
                        Text(label, style = MaterialTheme.typography.labelSmall)
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { ctx -> android.widget.ImageView(ctx).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP } },
                            update = { view -> view.setImageURI(android.net.Uri.fromFile(java.io.File(path))) },
                            modifier = Modifier.fillMaxWidth().height(72.dp)
                        )
                    }
                }
            }
        }
    }
}
