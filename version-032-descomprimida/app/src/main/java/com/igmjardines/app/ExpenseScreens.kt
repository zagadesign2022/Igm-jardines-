package com.igmjardines.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igmjardines.app.data.BusinessDatabase
import com.igmjardines.app.data.ExpenseEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar
import java.io.File
import java.util.UUID

private val expenseCategories = listOf("Combustible", "Herramientas", "Productos", "Vehículo", "Seguros", "Materiales", "Impuestos", "Otros")

@Composable
fun ExpensesScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val expenses by db.expensesDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val workOrders by db.workOrdersDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var showAdd by remember { mutableStateOf(false) }
    var editingExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var pendingDelete by remember { mutableStateOf<ExpenseEntity?>(null) }
    var concept by remember { mutableStateOf("") }
    var category by remember { mutableStateOf(expenseCategories.first()) }
    var base by remember { mutableStateOf("") }
    var vat by remember { mutableStateOf("0") }
    var date by remember { mutableStateOf(SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date())) }
    var notes by remember { mutableStateOf("") }
    var receiptUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var linkedWorkId by remember { mutableStateOf<Long?>(null) }
    var workMenu by remember { mutableStateOf(false) }
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> receiptUri = uri }
    val total = expenses.sumOf { it.totalAmount }

    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Gastos", style = MaterialTheme.typography.headlineSmall)
                Text("${expenses.size} gastos registrados")
            }
            Button(onClick = { editingExpense = null; linkedWorkId = null; showAdd = true }) { Text("Añadir") }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Total registrado", style = MaterialTheme.typography.labelLarge)
                Text("${money(total)} €", style = MaterialTheme.typography.headlineMedium)
                Text("Importes introducidos manualmente; no incluye gastos que aún no hayas registrado.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(expenses, key = { it.id }) { item ->
                Card(Modifier.fillMaxWidth()) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.concept, style = MaterialTheme.typography.titleMedium)
                            Text("${item.category} · ${SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(item.expenseDate))}", style = MaterialTheme.typography.bodySmall)
                            Text("Base ${money(item.baseAmount)} € · IVA ${money(item.vatAmount)} €", style = MaterialTheme.typography.bodySmall)
                            if (item.workOrderId != null) Text("Vinculado a trabajo #${item.workOrderId}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                            if (item.receiptPath.isNotBlank()) Text("Justificante adjunto", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall)
                        }
                        Column {
                            Text("${money(item.totalAmount)} €", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = {
                                editingExpense = item
                                concept = item.concept; category = item.category
                                base = item.baseAmount.toString(); vat = item.vatAmount.toString()
                                date = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(item.expenseDate))
                                notes = item.notes; receiptUri = null; linkedWorkId = item.workOrderId; showAdd = true
                            }) { Text("Editar") }
                            TextButton(onClick = { pendingDelete = item }) { Text("Borrar") }
                        }
                    }
                }
            }
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Borrar gasto") },
            text = { Text("¿Quieres borrar el gasto «${item.concept}»? También se eliminará su justificante adjunto si existe. Esta acción no se puede deshacer.") },
            confirmButton = { TextButton(onClick = { scope.launch {
                runCatching {
                    db.expensesDao().delete(item.id)
                    if (item.receiptPath.isNotBlank()) File(item.receiptPath).delete()
                }.onFailure {
                    android.widget.Toast.makeText(context, "No se pudo borrar el gasto: ${it.message}", android.widget.Toast.LENGTH_LONG).show()
                }
                pendingDelete = null
            } }) { Text("Borrar gasto") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } }
        )
    }

    if (showAdd) AlertDialog(
        onDismissRequest = { showAdd = false; receiptUri = null; editingExpense = null; linkedWorkId = null },
        title = { Text(if (editingExpense == null) "Nuevo gasto" else "Editar gasto") },
        text = {
            Column(Modifier.heightIn(max = 480.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                OutlinedTextField(concept, { concept = it }, label = { Text("Concepto *") }, singleLine = true)
                Text("Categoría", style = MaterialTheme.typography.labelLarge)
                expenseCategories.forEach { item ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(category == item, onClick = { category = item })
                        Text(item, Modifier.padding(top = 12.dp))
                    }
                }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (dd/MM/aaaa)") }, singleLine = true)
                OutlinedTextField(base, { base = it }, label = { Text("Base imponible (€)") }, singleLine = true)
                OutlinedTextField(vat, { vat = it }, label = { Text("IVA soportado (€)") }, singleLine = true)
                ExposedDropdownMenuBox(expanded = workMenu, onExpandedChange = { workMenu = it }) {
                    OutlinedTextField(
                        value = workOrders.firstOrNull { it.id == linkedWorkId }?.let { "${it.title} · ${it.clientNameSnapshot}" } ?: "Gasto general (sin trabajo)",
                        onValueChange = {}, readOnly = true, label = { Text("Vincular a trabajo") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(workMenu) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(expanded = workMenu, onDismissRequest = { workMenu = false }) {
                        DropdownMenuItem(text = { Text("Gasto general (sin trabajo)") }, onClick = { linkedWorkId = null; workMenu = false })
                        workOrders.forEach { work -> DropdownMenuItem(text = { Text("${work.title} · ${work.clientNameSnapshot}") }, onClick = { linkedWorkId = work.id; workMenu = false }) }
                    }
                }
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") })
                OutlinedButton(onClick = { receiptPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (receiptUri == null) "Adjuntar foto del justificante" else "Cambiar foto del justificante")
                }
                if (receiptUri != null) Text("Imagen seleccionada. Se guardará junto al gasto y se incluirá en las copias de seguridad.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val b = base.replace(',', '.').toDoubleOrNull()
                val v = vat.replace(',', '.').toDoubleOrNull()
                val d = runCatching { SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).apply { isLenient = false }.parse(date)?.time }.getOrNull()
                if (concept.isNotBlank() && b != null && v != null && b >= 0 && v >= 0 && d != null) {
                    scope.launch {
                        var savedPath = ""
                        val selectedReceipt = receiptUri
                        if (selectedReceipt != null) {
                            val copyResult = runCatching {
                                val folder = File(context.filesDir, "expense_receipts").apply { mkdirs() }
                                val target = File(folder, "expense_${UUID.randomUUID()}.jpg")
                                val input = context.contentResolver.openInputStream(selectedReceipt)
                                    ?: error("Android no ha permitido leer la imagen seleccionada.")
                                input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                                require(target.exists() && target.length() > 0L) { "El justificante está vacío o no se ha podido guardar." }
                                target.absolutePath
                            }
                            if (copyResult.isFailure) {
                                android.widget.Toast.makeText(context, "No se ha guardado el gasto porque no se pudo copiar el justificante: ${copyResult.exceptionOrNull()?.message}", android.widget.Toast.LENGTH_LONG).show()
                                return@launch
                            }
                            savedPath = copyResult.getOrThrow()
                        }
                        val previous = editingExpense
                        try {
                            if (previous == null) {
                                db.expensesDao().insert(ExpenseEntity(concept = concept.trim(), category = category, expenseDate = d, baseAmount = b, vatAmount = v, totalAmount = b + v, notes = notes.trim(), receiptPath = savedPath, workOrderId = linkedWorkId))
                            } else {
                                val finalPath = if (savedPath.isNotBlank()) savedPath else previous.receiptPath
                                db.expensesDao().update(previous.copy(concept = concept.trim(), category = category, expenseDate = d, baseAmount = b, vatAmount = v, totalAmount = b + v, notes = notes.trim(), receiptPath = finalPath, workOrderId = linkedWorkId))
                                if (savedPath.isNotBlank() && previous.receiptPath.isNotBlank() && previous.receiptPath != savedPath) File(previous.receiptPath).delete()
                            }
                        } catch (t: Throwable) {
                            if (savedPath.isNotBlank()) File(savedPath).delete()
                            android.widget.Toast.makeText(context, "No se pudo guardar el gasto: ${t.message}", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        editingExpense = null; concept = ""; base = ""; vat = "0"; notes = ""; receiptUri = null; linkedWorkId = null; showAdd = false
                    }
                } else android.widget.Toast.makeText(context, "Revisa concepto, fecha e importes", android.widget.Toast.LENGTH_SHORT).show()
            }) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = { showAdd = false; receiptUri = null; editingExpense = null; linkedWorkId = null }) { Text("Cancelar") } }
    )
}

@Composable
fun BusinessStatsScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val invoices by db.invoicesDao().observeInvoices().collectAsStateWithLifecycle(initialValue = emptyList())
    val expenses by db.expensesDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val payments by db.invoicesDao().observeAllPayments().collectAsStateWithLifecycle(initialValue = emptyList())
    val workOrders by db.workOrdersDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val trips by db.travelDao().observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val reportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val company = db.companyDao().get() ?: com.igmjardines.app.data.CompanyProfileEntity()
                val reportRows = buildProfitabilityRows(invoices, expenses, trips, workOrders)
                writeProfitabilityReportPdf(context, uri, company, reportRows)
            }.onFailure { android.widget.Toast.makeText(context, "No se pudo crear el informe: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    var period by remember { mutableStateOf("Este mes") }
    val now = remember { System.currentTimeMillis() }
    val start = remember(period, now) {
        val cal = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        when (period) {
            "Este mes" -> cal.set(Calendar.DAY_OF_MONTH, 1)
            "Este trimestre" -> { cal.set(Calendar.MONTH, (cal.get(Calendar.MONTH) / 3) * 3); cal.set(Calendar.DAY_OF_MONTH, 1) }
            "Este año" -> { cal.set(Calendar.MONTH, Calendar.JANUARY); cal.set(Calendar.DAY_OF_MONTH, 1) }
            else -> cal.timeInMillis = 0L
        }
        cal.timeInMillis
    }
    val periodInvoices = invoices.filter { it.issueDate >= start && it.issueDate <= now }
    val periodExpenses = expenses.filter { it.expenseDate >= start && it.expenseDate <= now }
    val billed = periodInvoices.sumOf { it.totalAmount }
    val collected = payments.filter { it.paymentDate >= start && it.paymentDate <= now }.sumOf { it.amount }
    val pending = periodInvoices.sumOf { (it.totalAmount - it.paidAmount).coerceAtLeast(0.0) }
    val outputVat = periodInvoices.sumOf { it.vatAmount }
    val expenseBase = periodExpenses.sumOf { it.baseAmount }
    val expenseVat = periodExpenses.sumOf { it.vatAmount }
    val netVat = outputVat - expenseVat
    val expenseTotal = periodExpenses.sumOf { it.totalAmount }
    val estimated = collected - expenseTotal
    val profitabilityRows = remember(invoices, expenses, trips, workOrders) { buildProfitabilityRows(invoices, expenses, trips, workOrders) }
    val linkedExpenseTotal = expenses.filter { it.workOrderId != null }.sumOf { it.totalAmount }
    val linkedTravelTotal = trips.filter { it.workOrderId != null }.sumOf { it.fuelCost + it.otherCost }
    val monthlyComparison = remember(invoices, expenses, payments, now) {
        val current = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        (0..5).map { offset ->
            val fromCal = (current.clone() as Calendar).apply { add(Calendar.MONTH, -offset) }
            val toCal = (fromCal.clone() as Calendar).apply { add(Calendar.MONTH, 1); add(Calendar.MILLISECOND, -1) }
            val from = fromCal.timeInMillis; val to = toCal.timeInMillis
            val monthInvoices = invoices.filter { it.issueDate in from..to }
            val monthExpenses = expenses.filter { it.expenseDate in from..to }
            val monthCollected = payments.filter { it.paymentDate in from..to }.sumOf { it.amount }
            MonthlyStats(from, monthInvoices.sumOf { it.totalAmount }, monthCollected, monthExpenses.sumOf { it.totalAmount })
        }.reversed()
    }
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Estadísticas", style = MaterialTheme.typography.headlineSmall)
                Text("Resumen según la fecha de emisión de facturas y la fecha del gasto", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { reportLauncher.launch("IGM_JARDINES_informe_rentabilidad.pdf") }) { Text("Exportar PDF") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            listOf("Este mes", "Este trimestre", "Este año", "Todo").forEach { option ->
                FilterChip(selected = period == option, onClick = { period = option }, label = { Text(option) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(if (period == "Todo") "Todos los registros" else "Desde ${SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(start))} hasta hoy", style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        StatCard("Facturación emitida", billed)
        StatCard("Cobros recibidos en el periodo", collected)
        StatCard("Pendiente de las facturas emitidas en el periodo", pending)
        StatCard("Gastos registrados", expenseTotal)
        StatCard("Resultado de caja estimado", estimated)
        Card(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("IVA y gastos", style = MaterialTheme.typography.titleMedium)
                Text("IVA repercutido en facturas emitidas: ${money(outputVat)} €")
                Text("IVA soportado en gastos: ${money(expenseVat)} €")
                Text("Diferencia orientativa de IVA: ${money(netVat)} €")
                Text("Base de gastos: ${money(expenseBase)} €")
                Text("Registros incluidos: ${periodInvoices.size} facturas · ${periodExpenses.size} gastos", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Rentabilidad por cliente y jardín", style = MaterialTheme.typography.titleMedium)
                Text("Se asignan facturas por cliente/jardín y solo se descuentan gastos y desplazamientos vinculados expresamente a un trabajo.", style = MaterialTheme.typography.bodySmall)
                Text("Costes vinculados: ${money(linkedExpenseTotal + linkedTravelTotal)} € · ${profitabilityRows.size} clientes/jardines", style = MaterialTheme.typography.labelLarge)
                profitabilityRows.take(12).forEach { row ->
                    HorizontalDivider()
                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                    Text("Base facturada ${money(row.revenue)} € · Base de gastos ${money(row.expenses)} € · Desplazamientos ${money(row.travel)} €", style = MaterialTheme.typography.bodySmall)
                    Text("Margen estimado: ${money(row.margin)} €", style = MaterialTheme.typography.bodyMedium)
                }
                if (profitabilityRows.isEmpty()) Text("Todavía no hay datos suficientes para calcular rentabilidad por cliente/jardín.", style = MaterialTheme.typography.bodySmall)
                Text("Los gastos generales sin trabajo vinculado no se reparten automáticamente entre clientes.", style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Comparativa de los últimos 6 meses", style = MaterialTheme.typography.titleMedium)
                Text("Facturación por fecha de emisión, cobros por fecha de pago y gastos por fecha del gasto", style = MaterialTheme.typography.bodySmall)
                monthlyComparison.forEach { month ->
                    val monthStart = month.monthStart
                    val monthBilled = month.billed
                    val monthCollected = month.collected
                    val monthExpenses = month.expenses
                    val label = SimpleDateFormat("MMMM yyyy", Locale("es", "ES")).format(Date(monthStart)).replaceFirstChar { it.uppercase(Locale("es", "ES")) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(label, style = MaterialTheme.typography.titleSmall)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Facturado: ${money(monthBilled)} €", style = MaterialTheme.typography.bodySmall)
                            Text("Cobrado: ${money(monthCollected)} €", style = MaterialTheme.typography.bodySmall)
                        }
                        Text("Gastos: ${money(monthExpenses)} € · Diferencia facturado-gastos: ${money(monthBilled - monthExpenses)} €", style = MaterialTheme.typography.bodySmall)
                        Text("Flujo de caja estimado: ${money(monthCollected - monthExpenses)} €", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (monthStart != monthlyComparison.last().monthStart) HorizontalDivider()
                }
            }
        }
        Text("Importante: el IVA neto mostrado es una estimación basada en el IVA de facturas y gastos registrados en el periodo; no sustituye la liquidación fiscal. El flujo de caja resta los gastos registrados a los cobros registrados en sus fechas reales. No equivale al beneficio contable o fiscal.", Modifier.padding(top = 12.dp), style = MaterialTheme.typography.bodySmall)
    }
}

private data class MonthlyStats(val monthStart: Long, val billed: Double, val collected: Double, val expenses: Double)

internal data class ProfitabilityRow(val label: String, val revenue: Double, val expenses: Double, val travel: Double) {
    val margin: Double get() = revenue - expenses - travel
}

private fun buildProfitabilityRows(
    invoices: List<com.igmjardines.app.data.InvoiceEntity>,
    expenses: List<com.igmjardines.app.data.ExpenseEntity>,
    trips: List<com.igmjardines.app.data.TravelLogEntity>,
    workOrders: List<com.igmjardines.app.data.WorkOrderEntity>
): List<ProfitabilityRow> {
    data class Key(val clientId: Long, val gardenId: Long?)
    val keys = linkedSetOf<Key>()
    invoices.forEach { keys += Key(it.clientId, it.gardenId) }
    workOrders.forEach { keys += Key(it.clientId, it.gardenId) }
    return keys.map { key ->
        val matchingInvoices = invoices.filter { it.clientId == key.clientId && it.gardenId == key.gardenId }
        val matchingWorks = workOrders.filter { it.clientId == key.clientId && it.gardenId == key.gardenId }.map { it.id }.toSet()
        val linkedExpenses = expenses.filter { it.workOrderId != null && it.workOrderId in matchingWorks }
        val linkedTrips = trips.filter { it.workOrderId != null && it.workOrderId in matchingWorks }
        val name = matchingInvoices.firstOrNull()?.clientNameSnapshot ?: workOrders.firstOrNull { it.clientId == key.clientId }?.clientNameSnapshot ?: "Cliente"
        val garden = matchingInvoices.firstOrNull()?.gardenNameSnapshot?.takeIf { it.isNotBlank() }
            ?: workOrders.firstOrNull { it.clientId == key.clientId && it.gardenId == key.gardenId }?.gardenNameSnapshot.orEmpty()
        ProfitabilityRow(
            label = if (garden.isBlank()) name else "$name · $garden",
            revenue = matchingInvoices.sumOf { it.baseAmount },
            expenses = linkedExpenses.sumOf { it.baseAmount },
            travel = linkedTrips.sumOf { it.fuelCost + it.otherCost }
        )
    }.sortedByDescending { it.margin }
}

@Composable
private fun StatCard(label: String, amount: Double) {
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Text("${money(amount)} €", style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun money(value: Double): String = String.format(Locale("es", "ES"), "%.2f", value)
