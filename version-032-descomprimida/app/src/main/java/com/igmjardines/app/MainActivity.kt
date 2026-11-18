package com.igmjardines.app

import android.os.Bundle
import android.util.Patterns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.compose.ui.platform.LocalContext
import androidx.room.Room
import com.igmjardines.app.data.BusinessDatabase
import com.igmjardines.app.data.BusinessDao
import com.igmjardines.app.data.ClientEntity
import com.igmjardines.app.data.GardenEntity
import com.igmjardines.app.data.ServicesDao
import com.igmjardines.app.data.ServiceEntity
import com.igmjardines.app.data.VatRateEntity
import com.igmjardines.app.data.EstimatesDao
import com.igmjardines.app.data.EstimateEntity
import com.igmjardines.app.data.EstimateLineEntity
import com.igmjardines.app.data.InvoiceEntity
import com.igmjardines.app.data.InvoicesDao
import com.igmjardines.app.data.WorkOrdersDao
import com.igmjardines.app.data.WorkOrderEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val GardenGreen = Color(0xFF236B45)
private val PaleGreen = Color(0xFFEAF3ED)
private val Ink = Color(0xFF1D2922)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { IGMJardinesApp() }
    }
}

data class MenuEntry(val title: String, val icon: ImageVector, val description: String)

private val entries = listOf(
    MenuEntry("Clientes", Icons.Default.People, "Fichas y jardines"),
    MenuEntry("Servicios y tarifas", Icons.Default.PriceChange, "Catálogo sin IVA"),
    MenuEntry("Presupuestos", Icons.Default.Description, "Crear y gestionar"),
    MenuEntry("Facturas", Icons.Default.ReceiptLong, "Cobros y documentos"),
    MenuEntry("Trabajos y agenda", Icons.Default.CalendarMonth, "Planificación"),
    MenuEntry("Gastos", Icons.Default.Payments, "Control de gastos"),
    MenuEntry("Estadísticas", Icons.Default.BarChart, "Resumen del negocio"),
    MenuEntry("Maquinaria", Icons.Default.Handyman, "Inventario y revisiones"),
    MenuEntry("Desplazamientos", Icons.Default.DirectionsCar, "Kilómetros y costes"),
    MenuEntry("Avisos", Icons.Default.Notifications, "Copias y mantenimiento"),
    MenuEntry("Mi empresa y ajustes", Icons.Default.Settings, "Datos, IVA y copias")
)

class ClientsViewModel(
    private val dao: BusinessDao,
    private val estimatesDao: EstimatesDao,
    private val invoicesDao: InvoicesDao,
    private val workOrdersDao: WorkOrdersDao
) : ViewModel() {
    val clients = dao.observeClients()
    val archivedClients = dao.observeArchivedClients()
    fun setClientArchived(id: Long, archived: Boolean) { viewModelScope.launch { dao.setClientArchived(id, archived) } }
    val allEstimates = estimatesDao.observeEstimates()
    val allInvoices = invoicesDao.observeInvoices()
    val allWorkOrders = workOrdersDao.observeAll()

    fun addClient(name: String, taxId: String, phone: String, email: String, address: String, notes: String) {
        viewModelScope.launch {
            dao.insertClient(ClientEntity(
                name = name.trim(), taxId = taxId.trim(), phone = phone.trim(),
                email = email.trim(), address = address.trim(), notes = notes.trim()
            ))
        }
    }

    fun updateClient(client: ClientEntity) { viewModelScope.launch { dao.updateClient(client) } }

    fun gardens(clientId: Long) = dao.observeGardens(clientId)
    fun archivedGardens(clientId: Long) = dao.observeArchivedGardens(clientId)
    fun setGardenArchived(id: Long, archived: Boolean) { viewModelScope.launch { dao.setGardenArchived(id, archived) } }

    fun updateGarden(garden: GardenEntity) { viewModelScope.launch { dao.updateGarden(garden) } }

    fun addGarden(clientId: Long, name: String, address: String, notes: String) {
        viewModelScope.launch {
            dao.insertGarden(GardenEntity(
                clientId = clientId, name = name.trim(),
                address = address.trim(), notes = notes.trim()
            ))
        }
    }
}

class ClientsViewModelFactory(
    private val dao: BusinessDao,
    private val estimatesDao: EstimatesDao,
    private val invoicesDao: InvoicesDao,
    private val workOrdersDao: WorkOrdersDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ClientsViewModel(dao, estimatesDao, invoicesDao, workOrdersDao) as T
}

class ServicesViewModel(private val dao: ServicesDao) : ViewModel() {
    val services = dao.observeServices()
    val vatRates = dao.observeVatRates()

    fun addService(name: String, category: String, description: String, unit: String, price: Double, vat: Double) {
        viewModelScope.launch {
            dao.insertService(ServiceEntity(
                name = name.trim(), category = category, description = description.trim(),
                unit = unit, priceExVat = price, vatRate = vat
            ))
        }
    }

    fun addVatRate(label: String, percentage: Double) {
        viewModelScope.launch {
            dao.insertVatRate(VatRateEntity(label = label.trim(), percentage = percentage))
        }
    }
}

class ServicesViewModelFactory(private val dao: ServicesDao) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ServicesViewModel(dao) as T
}

data class DraftEstimateLine(
    val serviceId: Long,
    val description: String,
    val unit: String,
    val quantity: Double,
    val price: Double,
    val vatRate: Double
) {
    val base: Double get() = quantity * price
    val vat: Double get() = base * vatRate / 100.0
    val total: Double get() = base + vat
}

class EstimatesViewModel(
    private val estimatesDao: EstimatesDao,
    private val clientsDao: BusinessDao,
    private val servicesDao: ServicesDao
) : ViewModel() {
    val estimates = estimatesDao.observeEstimates()
    val clients = clientsDao.observeClients()
    val services = servicesDao.observeServices()

    fun gardens(clientId: Long) = clientsDao.observeGardens(clientId)

    fun setStatus(id: Long, status: String) { viewModelScope.launch { estimatesDao.updateStatus(id, status) } }

    fun updateCommercialDetails(id: Long, discountText: String, notes: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val estimate = estimatesDao.getEstimate(id)
            if (estimate == null) { onResult("No se encuentra el presupuesto."); return@launch }
            if (estimatesDao.countInvoicesForEstimate(id) > 0) {
                onResult("Este presupuesto ya tiene una factura vinculada y no se puede modificar."); return@launch
            }
            val discount = discountText.replace(',', '.').toDoubleOrNull()
            if (discount == null || !discount.isFinite() || discount < 0.0 || discount > estimate.baseAmount + estimate.vatAmount) {
                onResult("Introduce un descuento válido que no supere el importe del presupuesto."); return@launch
            }
            val total = (estimate.baseAmount + estimate.vatAmount - discount).coerceAtLeast(0.0)
            estimatesDao.updateCommercialDetails(id, discount, total, notes.trim())
            onResult(null)
        }
    }

    fun saveEstimate(
        client: ClientEntity,
        garden: GardenEntity?,
        lines: List<DraftEstimateLine>,
        discount: Double,
        notes: String
    ) {
        viewModelScope.launch {
            val base = lines.sumOf { it.base }
            val vat = lines.sumOf { it.vat }
            val safeDiscount = discount.coerceAtLeast(0.0).coerceAtMost(base + vat)
            val count = estimatesDao.countEstimates() + 1
            val number = "P-%04d".format(count)
            val estimate = EstimateEntity(
                number = number,
                clientId = client.id,
                clientNameSnapshot = client.name,
                gardenId = garden?.id,
                gardenNameSnapshot = garden?.name ?: "",
                baseAmount = base,
                vatAmount = vat,
                discountAmount = safeDiscount,
                totalAmount = (base + vat - safeDiscount).coerceAtLeast(0.0),
                notes = notes.trim()
            )
            estimatesDao.insertEstimateWithLines(estimate, lines.map {
                EstimateLineEntity(
                    estimateId = 0,
                    serviceId = it.serviceId,
                    descriptionSnapshot = it.description,
                    unitSnapshot = it.unit,
                    quantity = it.quantity,
                    unitPriceExVat = it.price,
                    vatRateSnapshot = it.vatRate,
                    lineBase = it.base,
                    lineVat = it.vat,
                    lineTotal = it.total
                )
            })
        }
    }
}

class EstimatesViewModelFactory(
    private val estimatesDao: EstimatesDao,
    private val clientsDao: BusinessDao,
    private val servicesDao: ServicesDao
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        EstimatesViewModel(estimatesDao, clientsDao, servicesDao) as T
}

@Composable
fun IGMJardinesApp() {
    var selected by remember { mutableStateOf("Inicio") }
    val context = LocalContext.current
    val db = remember { BusinessDatabase.get(context) }
    LaunchedEffect(Unit) { ReminderManager.ensureChannel(context); ReminderManager.schedule(context) }
    val vm: ClientsViewModel = viewModel(factory = ClientsViewModelFactory(db.businessDao(), db.estimatesDao(), db.invoicesDao(), db.workOrdersDao()))
    val servicesVm: ServicesViewModel = viewModel(factory = ServicesViewModelFactory(db.servicesDao()))
    val estimatesVm: EstimatesViewModel = viewModel(
        factory = EstimatesViewModelFactory(db.estimatesDao(), db.businessDao(), db.servicesDao())
    )

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = GardenGreen,
            secondary = Color(0xFF6D987B),
            background = Color(0xFFF7F9F7),
            surface = Color.White
        )
    ) {
        Scaffold(
            containerColor = Color(0xFFF7F9F7),
            bottomBar = {
                NavigationBar(containerColor = Color.White) {
                    NavigationBarItem(
                        selected = selected == "Inicio",
                        onClick = { selected = "Inicio" },
                        icon = { Icon(Icons.Default.Home, null) },
                        label = { Text("Inicio") }
                    )
                    NavigationBarItem(
                        selected = selected == "Trabajos y agenda",
                        onClick = { selected = "Trabajos y agenda" },
                        icon = { Icon(Icons.Default.CalendarMonth, null) },
                        label = { Text("Agenda") }
                    )
                    NavigationBarItem(
                        selected = selected == "Mi empresa y ajustes",
                        onClick = { selected = "Mi empresa y ajustes" },
                        icon = { Icon(Icons.Default.Settings, null) },
                        label = { Text("Ajustes") }
                    )
                }
            }
        ) { padding ->
            when (selected) {
                "Inicio" -> Dashboard(Modifier.padding(padding), onOpen = { selected = it })
                "Clientes" -> ClientsScreen(vm, Modifier.padding(padding))
                "Servicios y tarifas" -> ServicesScreen(servicesVm, Modifier.padding(padding))
                "Presupuestos" -> EstimatesScreen(estimatesVm, Modifier.padding(padding))
                "Facturas" -> InvoicesScreen(db, Modifier.padding(padding))
                "Trabajos y agenda" -> WorkOrdersScreen(db, Modifier.padding(padding))
                "Gastos" -> ExpensesScreen(db, Modifier.padding(padding))
                "Estadísticas" -> BusinessStatsScreen(db, Modifier.padding(padding))
                "Maquinaria" -> MachineryScreen(db, Modifier.padding(padding))
                "Desplazamientos" -> TravelScreen(db, Modifier.padding(padding))
                "Avisos" -> ReminderSettingsScreen(Modifier.padding(padding))
                "Mi empresa y ajustes" -> CompanyScreen(db.companyDao(), db, Modifier.padding(padding))
                else -> PlaceholderScreen(selected, Modifier.padding(padding)) { selected = "Inicio" }
            }
        }
    }
}

@Composable
private fun Dashboard(modifier: Modifier = Modifier, onOpen: (String) -> Unit) {
    Column(modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp)) {
        Text("IGM JARDINES", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = GardenGreen)
        Text("Tu jardín en buenas manos", fontSize = 14.sp, color = Color(0xFF617268))
        Spacer(Modifier.height(20.dp))
        Card(
            colors = CardDefaults.cardColors(containerColor = GardenGreen),
            shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("Panel de control", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text("Tu negocio de jardinería, organizado en un solo lugar.", color = Color.White.copy(alpha = .9f))
                Spacer(Modifier.height(14.dp))
                Text("Tus datos se guardan en este dispositivo.", color = Color.White, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("Apartados", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(10.dp))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            gridItems(entries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth().height(112.dp).clickable { onOpen(entry.title) },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.SpaceBetween) {
                        Icon(entry.icon, contentDescription = null, tint = GardenGreen, modifier = Modifier.size(26.dp))
                        Column {
                            Text(entry.title, fontWeight = FontWeight.SemiBold, color = Ink, fontSize = 14.sp)
                            Text(entry.description, color = Color(0xFF718077), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientsScreen(vm: ClientsViewModel, modifier: Modifier = Modifier) {
    val clients by vm.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedClients by vm.archivedClients.collectAsStateWithLifecycle(initialValue = emptyList())
    var showArchived by remember { mutableStateOf(false) }
    var pendingArchiveClient by remember { mutableStateOf<ClientEntity?>(null) }
    var showAddClient by remember { mutableStateOf(false) }
    var editingClient by remember { mutableStateOf<ClientEntity?>(null) }
    var selectedClient by remember { mutableStateOf<ClientEntity?>(null) }

    if (selectedClient != null) {
        GardenListScreen(
            client = selectedClient!!,
            vm = vm,
            modifier = modifier,
            onBack = { selectedClient = null },
            onClientUpdated = { selectedClient = it }
        )
        return
    }

    Column(modifier.fillMaxSize().padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Clientes", fontSize = 25.sp, fontWeight = FontWeight.Bold, color = GardenGreen)
                Text("${clients.size} clientes activos · ${archivedClients.size} archivados", color = Color(0xFF617268), fontSize = 13.sp)
            }
            FilledTonalButton(onClick = { showAddClient = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Nuevo")
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !showArchived, onClick = { showArchived = false }, label = { Text("Activos (${clients.size})") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = showArchived, onClick = { showArchived = true }, label = { Text("Archivados (${archivedClients.size})") })
        }
        Spacer(Modifier.height(12.dp))
        val visibleClients = if (showArchived) archivedClients else clients
        if (visibleClients.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PersonAdd, null, tint = GardenGreen, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(if (showArchived) "No hay clientes archivados" else "Todavía no tienes clientes", fontWeight = FontWeight.SemiBold)
                    Text(if (showArchived) "Los clientes archivados aparecerán aquí." else "Pulsa «Nuevo» para crear la primera ficha.", color = Color(0xFF617268))
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(visibleClients, key = { it.id }) { client ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selectedClient = client },
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Person, null, tint = GardenGreen, modifier = Modifier.size(28.dp))
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(client.name, fontWeight = FontWeight.SemiBold, color = Ink)
                                Text(client.phone.ifBlank { "Sin teléfono" }, color = Color(0xFF718077), fontSize = 12.sp)
                            }
                            if (showArchived) {
                                IconButton(onClick = { vm.setClientArchived(client.id, false) }) { Icon(Icons.Default.Unarchive, contentDescription = "Restaurar cliente", tint = GardenGreen) }
                            } else {
                                IconButton(onClick = { editingClient = client }) { Icon(Icons.Default.Edit, contentDescription = "Editar cliente", tint = GardenGreen) }
                                IconButton(onClick = { pendingArchiveClient = client }) { Icon(Icons.Default.Archive, contentDescription = "Archivar cliente", tint = Color(0xFF8A5A20)) }
                                Icon(Icons.Default.ChevronRight, null, tint = GardenGreen)
                            }
                        }
                    }
                }
            }
        }
    }

    if (pendingArchiveClient != null) {
        AlertDialog(
            onDismissRequest = { pendingArchiveClient = null },
            title = { Text("Archivar cliente") },
            text = { Text("¿Quieres archivar a ${pendingArchiveClient!!.name}? No se eliminarán sus jardines, trabajos, presupuestos ni facturas. Podrás restaurarlo cuando quieras.") },
            confirmButton = { TextButton(onClick = { vm.setClientArchived(pendingArchiveClient!!.id, true); pendingArchiveClient = null }) { Text("Archivar") } },
            dismissButton = { TextButton(onClick = { pendingArchiveClient = null }) { Text("Cancelar") } }
        )
    }

    if (editingClient != null) {
        ClientFormDialog(
            initial = editingClient,
            onDismiss = { editingClient = null },
            onSave = { name, tax, phone, email, address, notes ->
                val old = editingClient!!
                vm.updateClient(old.copy(name = name.trim(), taxId = tax.trim(), phone = phone.trim(), email = email.trim(), address = address.trim(), notes = notes.trim()))
                editingClient = null
            }
        )
    }

    if (showAddClient) {
        ClientFormDialog(
            onDismiss = { showAddClient = false },
            onSave = { name, tax, phone, email, address, notes ->
                vm.addClient(name, tax, phone, email, address, notes)
                showAddClient = false
            }
        )
    }
}

@Composable
private fun GardenListScreen(
    client: ClientEntity,
    vm: ClientsViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit,
    onClientUpdated: (ClientEntity) -> Unit
) {
    val gardens by vm.gardens(client.id).collectAsStateWithLifecycle(initialValue = emptyList())
    val archivedGardens by vm.archivedGardens(client.id).collectAsStateWithLifecycle(initialValue = emptyList())
    var showArchivedGardens by remember { mutableStateOf(false) }
    var pendingArchiveGarden by remember { mutableStateOf<GardenEntity?>(null) }
    val estimates by vm.allEstimates.collectAsStateWithLifecycle(initialValue = emptyList())
    val invoices by vm.allInvoices.collectAsStateWithLifecycle(initialValue = emptyList())
    val workOrders by vm.allWorkOrders.collectAsStateWithLifecycle(initialValue = emptyList())
    val clientEstimates = estimates.filter { it.clientId == client.id }
    val clientInvoices = invoices.filter { it.clientId == client.id }
    val clientWork = workOrders.filter { it.clientId == client.id }
    val totalInvoiced = clientInvoices.sumOf { it.totalAmount }
    val totalPaid = clientInvoices.sumOf { it.paidAmount }
    val totalPending = (totalInvoiced - totalPaid).coerceAtLeast(0.0)
    var showAdd by remember { mutableStateOf(false) }
    var editingGarden by remember { mutableStateOf<GardenEntity?>(null) }
    var editClient by remember { mutableStateOf(false) }

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
        TextButton(onClick = onBack) { Text("← Volver a clientes") }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(client.name, modifier = Modifier.weight(1f), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GardenGreen)
            IconButton(onClick = { editClient = true }) { Icon(Icons.Default.Edit, contentDescription = "Editar ficha", tint = GardenGreen) }
        }
        if (client.taxId.isNotBlank()) Text("DNI / CIF: ${client.taxId}", color = Color(0xFF617268), fontSize = 13.sp)
        if (client.phone.isNotBlank()) Text("Teléfono: ${client.phone}", color = Color(0xFF617268), fontSize = 13.sp)
        if (client.email.isNotBlank()) Text("Email: ${client.email}", color = Color(0xFF617268), fontSize = 13.sp)
        if (client.address.isNotBlank()) Text("Dirección: ${client.address}", color = Color(0xFF617268), fontSize = 13.sp)
        if (client.notes.isNotBlank()) Text("Observaciones: ${client.notes}", color = Color(0xFF617268), fontSize = 13.sp)
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp)) {
                Text("Resumen económico de facturas", fontWeight = FontWeight.SemiBold, color = Ink)
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Facturado", fontSize = 11.sp, color = Color(0xFF617268)); Text(euros(totalInvoiced), fontWeight = FontWeight.Bold) }
                    Column { Text("Cobrado", fontSize = 11.sp, color = Color(0xFF617268)); Text(euros(totalPaid), fontWeight = FontWeight.Bold, color = GardenGreen) }
                    Column { Text("Pendiente", fontSize = 11.sp, color = Color(0xFF617268)); Text(euros(totalPending), fontWeight = FontWeight.Bold) }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("${gardens.size} ubicaciones", fontWeight = FontWeight.SemiBold)
            FilledTonalButton(onClick = { showAdd = true }) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(4.dp))
                Text("Añadir jardín")
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterChip(selected = !showArchivedGardens, onClick = { showArchivedGardens = false }, label = { Text("Activos (${gardens.size})") })
            Spacer(Modifier.width(8.dp))
            FilterChip(selected = showArchivedGardens, onClick = { showArchivedGardens = true }, label = { Text("Archivados (${archivedGardens.size})") })
        }
        val visibleGardens = if (showArchivedGardens) archivedGardens else gardens
        Spacer(Modifier.height(10.dp))
        if (visibleGardens.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
                Text(if (showArchivedGardens) "No hay jardines archivados." else "Este cliente todavía no tiene jardines guardados.", Modifier.padding(18.dp), color = Ink)
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                visibleGardens.forEach { garden ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(15.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Yard, null, tint = GardenGreen)
                                Spacer(Modifier.width(8.dp))
                                Text(garden.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Ink)
                                if (showArchivedGardens) {
                                    IconButton(onClick = { vm.setGardenArchived(garden.id, false) }) { Icon(Icons.Default.Unarchive, contentDescription = "Restaurar jardín", tint = GardenGreen) }
                                } else {
                                    IconButton(onClick = { editingGarden = garden }) { Icon(Icons.Default.Edit, contentDescription = "Editar jardín", tint = GardenGreen) }
                                    IconButton(onClick = { pendingArchiveGarden = garden }) { Icon(Icons.Default.Archive, contentDescription = "Archivar jardín", tint = Color(0xFF8A5A20)) }
                                }
                            }
                            if (garden.address.isNotBlank()) Text(garden.address, color = Color(0xFF617268), fontSize = 13.sp)
                            if (garden.notes.isNotBlank()) Text(garden.notes, color = Color(0xFF617268), fontSize = 12.sp)
                            val gardenJobs = clientWork.count { it.gardenId == garden.id }
                            val gardenEstimates = clientEstimates.count { it.gardenId == garden.id }
                            val gardenInvoices = clientInvoices.count { it.gardenId == garden.id }
                            Text("$gardenJobs trabajos · $gardenEstimates presupuestos · $gardenInvoices facturas", color = Color(0xFF617268), fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Historial del cliente", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = GardenGreen)
        Spacer(Modifier.height(8.dp))
        Text("Trabajos", fontWeight = FontWeight.SemiBold, color = Ink)
        if (clientWork.isEmpty()) Text("Todavía no hay trabajos registrados.", color = Color(0xFF718077), fontSize = 13.sp)
        clientWork.sortedByDescending { it.scheduledAt }.forEach { work ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Text(work.title, fontWeight = FontWeight.SemiBold)
                    Text("${formatDate(work.scheduledAt)} · ${work.status} · ${work.gardenNameSnapshot.ifBlank { "Sin jardín" }}", fontSize = 12.sp, color = Color(0xFF617268))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Presupuestos", fontWeight = FontWeight.SemiBold, color = Ink)
        if (clientEstimates.isEmpty()) Text("Todavía no hay presupuestos.", color = Color(0xFF718077), fontSize = 13.sp)
        clientEstimates.forEach { estimate ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(estimate.number, fontWeight = FontWeight.SemiBold)
                        Text("${formatDate(estimate.issueDate)} · ${estimate.status} · ${estimate.gardenNameSnapshot.ifBlank { "Sin jardín" }}", fontSize = 12.sp, color = Color(0xFF617268))
                    }
                    Text(euros(estimate.totalAmount), fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Facturas y cobros", fontWeight = FontWeight.SemiBold, color = Ink)
        if (clientInvoices.isEmpty()) Text("Todavía no hay facturas.", color = Color(0xFF718077), fontSize = 13.sp)
        clientInvoices.forEach { invoice ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(invoice.number, fontWeight = FontWeight.SemiBold)
                        Text(euros(invoice.totalAmount), fontWeight = FontWeight.SemiBold)
                    }
                    Text("${formatDate(invoice.issueDate)} · ${invoice.status} · ${invoice.gardenNameSnapshot.ifBlank { "Sin jardín" }}", fontSize = 12.sp, color = Color(0xFF617268))
                    Text("Cobrado: ${euros(invoice.paidAmount)}  |  Pendiente: ${euros((invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0))}", fontSize = 12.sp, color = Color(0xFF617268))
                }
            }
        }
    }

    if (pendingArchiveGarden != null) {
        AlertDialog(
            onDismissRequest = { pendingArchiveGarden = null },
            title = { Text("Archivar jardín") },
            text = { Text("¿Quieres archivar ${pendingArchiveGarden!!.name}? Se conservarán sus trabajos, presupuestos y facturas. Podrás restaurarlo después.") },
            confirmButton = { TextButton(onClick = { vm.setGardenArchived(pendingArchiveGarden!!.id, true); pendingArchiveGarden = null }) { Text("Archivar") } },
            dismissButton = { TextButton(onClick = { pendingArchiveGarden = null }) { Text("Cancelar") } }
        )
    }

    if (editClient) {
        ClientFormDialog(
            initial = client,
            onDismiss = { editClient = false },
            onSave = { name, tax, phone, email, address, notes ->
                val updated = client.copy(name = name.trim(), taxId = tax.trim(), phone = phone.trim(), email = email.trim(), address = address.trim(), notes = notes.trim())
                vm.updateClient(updated); onClientUpdated(updated); editClient = false
            }
        )
    }

    if (editingGarden != null) {
        GardenFormDialog(
            initial = editingGarden,
            onDismiss = { editingGarden = null },
            onSave = { name, address, notes ->
                vm.updateGarden(editingGarden!!.copy(name = name.trim(), address = address.trim(), notes = notes.trim()))
                editingGarden = null
            }
        )
    }

    if (showAdd) {
        GardenFormDialog(
            onDismiss = { showAdd = false },
            onSave = { name, address, notes ->
                vm.addGarden(client.id, name, address, notes)
                showAdd = false
            }
        )
    }
}

private fun euros(value: Double): String = String.format(Locale("es", "ES"), "%.2f €", value)

private fun formatDate(value: Long): String = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(value))

@Composable
private fun ClientFormDialog(
    initial: ClientEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var tax by remember(initial?.id) { mutableStateOf(initial?.taxId ?: "") }
    var phone by remember(initial?.id) { mutableStateOf(initial?.phone ?: "") }
    var email by remember(initial?.id) { mutableStateOf(initial?.email ?: "") }
    var address by remember(initial?.id) { mutableStateOf(initial?.address ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    val emailInvalid = email.isNotBlank() && !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuevo cliente" else "Editar cliente") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre o empresa *") }, singleLine = true)
                OutlinedTextField(tax, { tax = it }, label = { Text("DNI / CIF") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true)
                OutlinedTextField(
                    email, { email = it }, label = { Text("Email") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true, isError = emailInvalid,
                    supportingText = { if (emailInvalid) Text("Introduce un email válido o deja el campo vacío") }
                )
                OutlinedTextField(address, { address = it }, label = { Text("Dirección del cliente") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") })
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank() && !emailInvalid) onSave(name, tax, phone, email, address, notes) },
                enabled = name.isNotBlank() && !emailInvalid
            ) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun GardenFormDialog(
    initial: GardenEntity? = null,
    onDismiss: () -> Unit,
    onSave: (String, String, String) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var address by remember(initial?.id) { mutableStateOf(initial?.address ?: "") }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Añadir jardín o parcela" else "Editar jardín o parcela") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre del jardín *") }, singleLine = true)
                OutlinedTextField(address, { address = it }, label = { Text("Dirección o ubicación") })
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") })
            }
        },
        confirmButton = {
            TextButton(onClick = { if (name.isNotBlank()) onSave(name, address, notes) }, enabled = name.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
private fun PlaceholderScreen(title: String, modifier: Modifier = Modifier, onBack: () -> Unit) {
    Column(modifier.fillMaxSize().padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Volver al inicio") }
        Text(title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = GardenGreen)
        Card(colors = CardDefaults.cardColors(containerColor = PaleGreen)) {
            Text(
                "Apartado pendiente de desarrollo. Los datos del módulo Clientes ya se guardan localmente en el dispositivo.",
                modifier = Modifier.padding(18.dp), color = Ink
            )
        }
    }
}
