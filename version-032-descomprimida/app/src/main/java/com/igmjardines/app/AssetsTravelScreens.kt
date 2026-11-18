package com.igmjardines.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igmjardines.app.data.BusinessDatabase
import com.igmjardines.app.data.MachineEntity
import com.igmjardines.app.data.MachineMaintenanceEntity
import com.igmjardines.app.data.TravelLogEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val machineTypes = listOf("Desbrozadora", "Cortasetos", "Motosierra", "Cortacésped", "Multiherramienta", "Soplador", "Otra")
private fun parseDate(value: String): Long? = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).apply { isLenient = false }.parse(value)?.time }.getOrNull()
private fun assetMoney(value: Double): String = String.format(Locale("es", "ES"), "%.2f", value)
private fun dateText(value: Long?): String = value?.let { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(it)) } ?: "Sin fecha"

@Composable
fun MachineryScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val machines by db.assetsDao().observeMachines().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<MachineEntity?>(null) }
    var hoursMachine by remember { mutableStateOf<MachineEntity?>(null) }
    var updatedHours by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(machineTypes.first()) }
    var price by remember { mutableStateOf("0") }
    var hours by remember { mutableStateOf("0") }
    var nextHours by remember { mutableStateOf("") }
    var nextDate by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) { Text("Maquinaria", style = MaterialTheme.typography.headlineSmall); Text("Inventario y mantenimiento") }
            Button(onClick = { showAdd = true }) { Text("Añadir") }
        }
        Spacer(Modifier.height(10.dp))
        if (machines.isEmpty()) Text("Todavía no has registrado maquinaria.", Modifier.padding(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(machines, key = { it.id }) { machine ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(machine.name, style = MaterialTheme.typography.titleMedium)
                        Text(listOf(machine.machineType, machine.brandModel).filter { it.isNotBlank() }.joinToString(" · "))
                        Text("Uso registrado: ${machine.operatingHours} h · Compra: ${assetMoney(machine.purchasePrice)} €")
                        Text("Próxima revisión: ${machine.nextServiceHours?.let { "$it h" } ?: "Sin horas"} · ${dateText(machine.nextServiceDate)}", style = MaterialTheme.typography.bodySmall)
                        Row {
                            TextButton(onClick = { selected = machine }) { Text("Mantenimiento") }
                            TextButton(onClick = { hoursMachine = machine; updatedHours = machine.operatingHours.toString() }) { Text("Actualizar horas") }
                            TextButton(onClick = { scope.launch { db.assetsDao().deleteMachine(machine.id) } }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
    }

    hoursMachine?.let { machine ->
        AlertDialog(onDismissRequest = { hoursMachine = null }, title = { Text("Horas · ${machine.name}") },
            text = { OutlinedTextField(updatedHours, { updatedHours = it }, label = { Text("Horas actuales acumuladas") }) },
            confirmButton = { TextButton(onClick = {
                val value = updatedHours.replace(',', '.').toDoubleOrNull()
                if (value != null && value >= 0) { scope.launch { db.assetsDao().updateOperatingHours(machine.id, value) }; hoursMachine = null }
                else android.widget.Toast.makeText(context, "Introduce unas horas válidas", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Guardar") } },
            dismissButton = { TextButton(onClick = { hoursMachine = null }) { Text("Cancelar") } }
        )
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false }, title = { Text("Nueva máquina") },
        text = { Column(Modifier.heightIn(max = 500.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre *") }, singleLine = true)
            OutlinedTextField(model, { model = it }, label = { Text("Marca y modelo") }, singleLine = true)
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(type, {}, Modifier.menuAnchor().fillMaxWidth(), readOnly = true, label = { Text("Tipo") }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) })
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) { machineTypes.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { type = option; expanded = false }) } }
            }
            OutlinedTextField(price, { price = it }, label = { Text("Precio de compra (€)") })
            OutlinedTextField(hours, { hours = it }, label = { Text("Horas actuales") })
            OutlinedTextField(nextHours, { nextHours = it }, label = { Text("Próxima revisión a las horas (opcional)") })
            OutlinedTextField(nextDate, { nextDate = it }, label = { Text("Próxima revisión dd/MM/aaaa (opcional)") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") })
        } },
        confirmButton = { TextButton(onClick = {
            val p = price.replace(',', '.').toDoubleOrNull(); val h = hours.replace(',', '.').toDoubleOrNull(); val nh = nextHours.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
            val nd = if (nextDate.isBlank()) null else parseDate(nextDate)
            if (name.isNotBlank() && p != null && h != null && p >= 0 && h >= 0 && (nextHours.isBlank() || (nh != null && nh >= 0)) && (nextDate.isBlank() || nd != null)) {
                scope.launch { db.assetsDao().insertMachine(MachineEntity(name = name.trim(), brandModel = model.trim(), machineType = type, purchasePrice = p, operatingHours = h, nextServiceHours = nh, nextServiceDate = nd, notes = notes.trim())) }
                name = ""; model = ""; price = "0"; hours = "0"; nextHours = ""; nextDate = ""; notes = ""; showAdd = false
            } else android.widget.Toast.makeText(context, "Revisa el nombre, importes, horas y fecha", android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
    )

    selected?.let { machine ->
        val logs by db.assetsDao().observeMaintenance(machine.id).collectAsStateWithLifecycle(initialValue = emptyList())
        var description by remember(machine.id) { mutableStateOf("") }
        var cost by remember(machine.id) { mutableStateOf("0") }
        var serviceHours by remember(machine.id) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { selected = null }, title = { Text("Mantenimiento · ${machine.name}") },
            text = { Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(description, { description = it }, label = { Text("Trabajo realizado *") })
                OutlinedTextField(cost, { cost = it }, label = { Text("Coste (€)") })
                OutlinedTextField(serviceHours, { serviceHours = it }, label = { Text("Horas de máquina") })
                Text("Historial", style = MaterialTheme.typography.titleSmall)
                logs.forEach { Text("${dateText(it.maintenanceDate)} · ${it.description} · ${assetMoney(it.cost)} €", style = MaterialTheme.typography.bodySmall) }
            } },
            confirmButton = { TextButton(onClick = {
                val c = cost.replace(',', '.').toDoubleOrNull(); val h = serviceHours.takeIf { it.isNotBlank() }?.replace(',', '.')?.toDoubleOrNull()
                if (description.isNotBlank() && c != null && c >= 0 && (serviceHours.isBlank() || (h != null && h >= 0))) {
                    scope.launch { db.assetsDao().insertMaintenance(MachineMaintenanceEntity(machineId = machine.id, description = description.trim(), cost = c, hoursAtService = h)) }
                    description = ""; cost = "0"; serviceHours = ""
                } else android.widget.Toast.makeText(context, "Revisa los datos", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Registrar revisión") } }, dismissButton = { TextButton(onClick = { selected = null }) { Text("Cerrar") } }
        )
    }
}

@Composable
fun TravelScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val trips by db.travelDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val workOrders by db.workOrdersDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf("") }; var origin by remember { mutableStateOf("") }; var destination by remember { mutableStateOf("") }
    var linkedWorkId by remember { mutableStateOf<Long?>(null) }; var workMenu by remember { mutableStateOf(false) }
    var km by remember { mutableStateOf("") }; var litres by remember { mutableStateOf("0") }; var fuel by remember { mutableStateOf("0") }; var other by remember { mutableStateOf("0") }; var minutes by remember { mutableStateOf("0") }; var notes by remember { mutableStateOf("") }
    val totalCost = trips.sumOf { it.fuelCost + it.otherCost }; val totalKm = trips.sumOf { it.roundTripKm }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column(Modifier.weight(1f)) { Text("Desplazamientos", style = MaterialTheme.typography.headlineSmall); Text("Kilómetros y costes") }; Button(onClick = { showAdd = true }) { Text("Añadir") } }
        Spacer(Modifier.height(10.dp))
        Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text("Resumen acumulado", style = MaterialTheme.typography.titleMedium); Text("${String.format(Locale("es", "ES"), "%.1f", totalKm)} km · ${assetMoney(totalCost)} €") } }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) { items(trips, key = { it.id }) { trip ->
            Card(Modifier.fillMaxWidth()) { Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(trip.clientName.ifBlank { "Desplazamiento" }, style = MaterialTheme.typography.titleMedium); Text("${trip.origin} → ${trip.destination}"); Text("${trip.roundTripKm} km · ${trip.fuelLitres} L · ${dateText(trip.travelDate)}", style = MaterialTheme.typography.bodySmall); if (trip.workOrderId != null) Text("Trabajo #${trip.workOrderId}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                Column { Text("${assetMoney(trip.fuelCost + trip.otherCost)} €"); TextButton(onClick = { scope.launch { db.travelDao().delete(trip.id) } }) { Text("Borrar") } }
            } }
        } }
    }
    if (showAdd) AlertDialog(onDismissRequest = { showAdd = false }, title = { Text("Nuevo desplazamiento") },
        text = { Column(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            OutlinedTextField(client, { client = it }, label = { Text("Cliente (opcional)") })
            ExposedDropdownMenuBox(expanded = workMenu, onExpandedChange = { workMenu = it }) {
                OutlinedTextField(
                    value = workOrders.firstOrNull { it.id == linkedWorkId }?.let { "${it.title} · ${it.clientNameSnapshot}" } ?: "Sin trabajo vinculado",
                    onValueChange = {}, readOnly = true, label = { Text("Vincular a trabajo") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(workMenu) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = workMenu, onDismissRequest = { workMenu = false }) {
                    DropdownMenuItem(text = { Text("Sin trabajo vinculado") }, onClick = { linkedWorkId = null; workMenu = false })
                    workOrders.forEach { work -> DropdownMenuItem(text = { Text("${work.title} · ${work.clientNameSnapshot}") }, onClick = { linkedWorkId = work.id; client = work.clientNameSnapshot; workMenu = false }) }
                }
            }
            OutlinedTextField(origin, { origin = it }, label = { Text("Origen") })
            OutlinedTextField(destination, { destination = it }, label = { Text("Destino") })
            OutlinedTextField(km, { km = it }, label = { Text("Kilómetros ida y vuelta *") })
            OutlinedTextField(litres, { litres = it }, label = { Text("Combustible (litros)") })
            OutlinedTextField(fuel, { fuel = it }, label = { Text("Coste combustible (€)") })
            OutlinedTextField(other, { other = it }, label = { Text("Otros costes (€)") })
            OutlinedTextField(minutes, { minutes = it }, label = { Text("Duración (minutos)") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") })
        } },
        confirmButton = { TextButton(onClick = {
            val k = km.replace(',', '.').toDoubleOrNull(); val l = litres.replace(',', '.').toDoubleOrNull(); val f = fuel.replace(',', '.').toDoubleOrNull(); val o = other.replace(',', '.').toDoubleOrNull(); val m = minutes.toIntOrNull()
            if (k != null && l != null && f != null && o != null && m != null && k >= 0 && l >= 0 && f >= 0 && o >= 0 && m >= 0) {
                scope.launch { db.travelDao().insert(TravelLogEntity(clientName = client.trim(), origin = origin.trim(), destination = destination.trim(), roundTripKm = k, fuelLitres = l, fuelCost = f, otherCost = o, durationMinutes = m, notes = notes.trim(), workOrderId = linkedWorkId)) }
                client = ""; origin = ""; destination = ""; linkedWorkId = null; km = ""; litres = "0"; fuel = "0"; other = "0"; minutes = "0"; notes = ""; showAdd = false
            } else android.widget.Toast.makeText(context, "Revisa los kilómetros, costes y duración", android.widget.Toast.LENGTH_SHORT).show()
        }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { showAdd = false }) { Text("Cancelar") } }
    )
}
