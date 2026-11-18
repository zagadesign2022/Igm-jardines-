package com.igmjardines.app

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.igmjardines.app.data.*
import com.igmjardines.app.data.CompanyProfileEntity
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.text.ParsePosition
import java.util.Date
import java.util.Locale

private fun money(v: Double) = "%.2f €".format(v)
private fun dateText(v: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(v))
private fun parseDateOrNull(value: String): Long? {
    if (value.isBlank()) return null
    val format = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).apply { isLenient = false }
    val position = ParsePosition(0)
    val parsed = format.parse(value.trim(), position)
    return parsed?.time?.takeIf { position.index == value.trim().length }
}

@Composable
fun ServicesScreen(vm: ServicesViewModel, modifier: Modifier = Modifier) {
    val services by vm.services.collectAsStateWithLifecycle(initialValue = emptyList())
    val vats by vm.vatRates.collectAsStateWithLifecycle(initialValue = emptyList())
    var add by remember { mutableStateOf(false) }
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Servicios y tarifas", style = MaterialTheme.typography.headlineSmall); Text("Precios del catálogo sin IVA") }
            Button(onClick = { add = true }) { Text("Añadir") }
        }
        Spacer(Modifier.height(12.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(services, key = { it.id }) { s ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text("${s.category} · ${s.unit} · ${money(s.priceExVat)} + ${s.vatRate}% IVA", fontSize = 13.sp)
                } }
            }
        }
    }
    if (add) {
        var name by remember { mutableStateOf("") }; var category by remember { mutableStateOf("Mantenimiento") }
        var unit by remember { mutableStateOf("hora") }; var price by remember { mutableStateOf("") }
        var vat by remember { mutableStateOf("21") }
        AlertDialog(onDismissRequest = { add = false }, title = { Text("Nuevo servicio") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") })
                OutlinedTextField(category, { category = it }, label = { Text("Categoría") })
                OutlinedTextField(unit, { unit = it }, label = { Text("Unidad") })
                OutlinedTextField(price, { price = it }, label = { Text("Precio sin IVA") })
                OutlinedTextField(vat, { vat = it }, label = { Text("IVA %") })
                if (vats.isNotEmpty()) Text("Tipos configurados: ${vats.joinToString { "${it.label} (${it.percentage}%)" }}", fontSize = 11.sp)
            } }, confirmButton = { TextButton(onClick = {
                val p = price.replace(',', '.').toDoubleOrNull(); val r = vat.replace(',', '.').toDoubleOrNull()
                if (name.isNotBlank() && p != null && p >= 0 && r != null && r >= 0) {
                    vm.addService(name, category, "", unit, p, r); add = false
                }
            }) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { add = false }) { Text("Cancelar") } })
    }
}

@Composable
fun EstimatesScreen(vm: EstimatesViewModel, modifier: Modifier = Modifier) {
    val estimates by vm.estimates.collectAsStateWithLifecycle(initialValue = emptyList())
    val clients by vm.clients.collectAsStateWithLifecycle(initialValue = emptyList())
    val services by vm.services.collectAsStateWithLifecycle(initialValue = emptyList())
    var create by remember { mutableStateOf(false) }
    var editingEstimate by remember { mutableStateOf<EstimateEntity?>(null) }
    var editDiscount by remember { mutableStateOf("0") }
    var editNotes by remember { mutableStateOf("") }
    var editError by remember { mutableStateOf<String?>(null) }
    var chosenClient by remember { mutableStateOf<ClientEntity?>(null) }
    var chosenService by remember { mutableStateOf<ServiceEntity?>(null) }
    var chosenGarden by remember { mutableStateOf<GardenEntity?>(null) }
    val gardens by vm.gardens(chosenClient?.id ?: -1L).collectAsStateWithLifecycle(initialValue = emptyList())
    val draftLines = remember { mutableStateListOf<DraftEstimateLine>() }
    var qty by remember { mutableStateOf("1") }; var discount by remember { mutableStateOf("0") }
    var notes by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column { Text("Presupuestos", style = MaterialTheme.typography.headlineSmall); Text("${estimates.size} documentos") }
            Button(onClick = { create = true }) { Text("Nuevo") }
        }
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(estimates, key = { it.id }) { e ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${e.number} · ${e.clientNameSnapshot}", style = MaterialTheme.typography.titleMedium)
                    Text("${e.status} · ${money(e.totalAmount)} · ${dateText(e.issueDate)}")
                    Row {
                        TextButton(onClick = { editingEstimate = e; editDiscount = e.discountAmount.toString(); editNotes = e.notes; editError = null }) { Text("Editar") }
                        if (e.status != "Aceptado" && e.status != "Rechazado") TextButton(onClick = { scope.launch { vm.setStatus(e.id, "Aceptado") } }) { Text("Marcar aceptado") }
                        if (e.status != "Rechazado") TextButton(onClick = { scope.launch { vm.setStatus(e.id, "Rechazado") } }) { Text("Rechazar") }
                    }
                } }
            }
        }
    }

    if (editingEstimate != null) AlertDialog(
        onDismissRequest = { editingEstimate = null },
        title = { Text("Editar ${editingEstimate!!.number}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Puedes modificar el descuento y las notas. Los importes de las líneas originales se conservan.", fontSize = 12.sp)
            OutlinedTextField(editDiscount, { editDiscount = it }, label = { Text("Descuento (€)") })
            OutlinedTextField(editNotes, { editNotes = it }, label = { Text("Notas") })
            if (editError != null) Text(editError!!, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        } },
        confirmButton = { TextButton(onClick = {
            val target = editingEstimate ?: return@TextButton
            vm.updateCommercialDetails(target.id, editDiscount, editNotes) { error ->
                if (error == null) editingEstimate = null else editError = error
            }
        }) { Text("Guardar cambios") } },
        dismissButton = { TextButton(onClick = { editingEstimate = null }) { Text("Cancelar") } }
    )

    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Nuevo presupuesto") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Cliente")
            clients.forEach { c -> Row(Modifier.fillMaxWidth().clickable { if (chosenClient?.id != c.id) { chosenClient = c; chosenGarden = null } }) { RadioButton(chosenClient?.id == c.id, { if (chosenClient?.id != c.id) { chosenClient = c; chosenGarden = null } }); Text(c.name, Modifier.padding(top = 12.dp)) } }
            if (chosenClient != null) {
                Text("Jardín (opcional)")
                gardens.forEach { g -> Row(Modifier.fillMaxWidth().clickable { chosenGarden = g }) { RadioButton(chosenGarden?.id == g.id, { chosenGarden = g }); Text(g.name, Modifier.padding(top = 12.dp)) } }
            }
            Text("Añadir servicio")
            services.forEach { s -> Row(Modifier.fillMaxWidth().clickable { chosenService = s }) { RadioButton(chosenService?.id == s.id, { chosenService = s }); Text("${s.name} · ${money(s.priceExVat)}", Modifier.padding(top = 12.dp)) } }
            OutlinedTextField(qty, { qty = it }, label = { Text("Cantidad") })
            TextButton(onClick = {
                val s = chosenService; val q = qty.replace(',', '.').toDoubleOrNull()
                if (s != null && q != null && q > 0) { draftLines.add(DraftEstimateLine(s.id, s.name, s.unit, q, s.priceExVat, s.vatRate)); chosenService = null; qty = "1" }
            }, enabled = chosenService != null) { Text("Añadir línea al presupuesto") }
            draftLines.forEachIndexed { index, line ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${line.description} · ${line.quantity} ${line.unit} · ${money(line.total)}", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = { draftLines.removeAt(index) }) { Text("Quitar") }
                }
            }
            OutlinedTextField(discount, { discount = it }, label = { Text("Descuento (€)") })
            OutlinedTextField(notes, { notes = it }, label = { Text("Notas") })
        } }, confirmButton = { TextButton(onClick = {
            val c = chosenClient
            if (c != null && draftLines.isNotEmpty()) {
                vm.saveEstimate(c, chosenGarden, draftLines.toList(), discount.replace(',', '.').toDoubleOrNull() ?: 0.0, notes)
                create = false; chosenClient = null; chosenGarden = null; chosenService = null; draftLines.clear(); qty = "1"; discount = "0"; notes = ""
            }
        }, enabled = chosenClient != null && draftLines.isNotEmpty()) { Text("Guardar") } }, dismissButton = { TextButton(onClick = { create = false }) { Text("Cancelar") } })
}

@Composable
fun InvoicesScreen(db: BusinessDatabase, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val invoices by db.invoicesDao().observeInvoices().collectAsStateWithLifecycle(initialValue = emptyList())
    val estimates by db.estimatesDao().observeEstimates().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var selectedInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var payInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var editInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var editDueDate by remember { mutableStateOf("") }
    var editInvoiceNotes by remember { mutableStateOf("") }
    var receiptInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var receiptPayment by remember { mutableStateOf<InvoicePaymentEntity?>(null) }
    var payAmount by remember { mutableStateOf("") }; var payMethod by remember { mutableStateOf("Transferencia") }
    var pdfInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var correctionInvoice by remember { mutableStateOf<InvoiceEntity?>(null) }
    var correctionReason by remember { mutableStateOf("") }
    val pdfLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val invoice = pdfInvoice
        if (uri != null && invoice != null) scope.launch {
            runCatching {
                val lines = db.invoicesDao().getLines(invoice.id)
                val company = db.companyDao().get() ?: CompanyProfileEntity()
                writeInvoicePdf(context, uri, invoice, lines, company)
            }.onFailure { android.widget.Toast.makeText(context, "Error al crear PDF: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val receiptLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val invoice = receiptInvoice; val payment = receiptPayment
        if (uri != null && invoice != null && payment != null) scope.launch {
            runCatching {
                val company = db.companyDao().get() ?: CompanyProfileEntity()
                writePaymentReceiptPdf(context, uri, invoice, payment, company)
            }.onFailure { android.widget.Toast.makeText(context, "Error al crear recibo: ${it.message}", android.widget.Toast.LENGTH_LONG).show() }
        }
    }
    val paymentRows by db.invoicesDao().observePayments(selectedInvoice?.id ?: -1L).collectAsStateWithLifecycle(initialValue = emptyList())
    Column(modifier.fillMaxSize().padding(16.dp)) {
        Text("Facturas", style = MaterialTheme.typography.headlineSmall)
        Text("Convierte presupuestos aceptados y registra cobros", fontSize = 13.sp)
        Spacer(Modifier.height(10.dp))
        Text("Presupuestos aceptados", style = MaterialTheme.typography.titleMedium)
        estimates.filter { it.status == "Aceptado" }.forEach { e ->
            val already = invoices.any { it.estimateId == e.id }
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) { Row(Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text("${e.number} · ${e.clientNameSnapshot}"); Text(money(e.totalAmount), fontSize = 12.sp) }
                TextButton(enabled = !already, onClick = { scope.launch {
                    if (db.invoicesDao().countForEstimate(e.id) == 0) {
                        val lines = db.estimatesDao().getLines(e.id)
                        val count = db.invoicesDao().countInvoices() + 1
                        val inv = InvoiceEntity(number = "F-%04d".format(count), clientId = e.clientId, clientNameSnapshot = e.clientNameSnapshot,
                            gardenId = e.gardenId, gardenNameSnapshot = e.gardenNameSnapshot, estimateId = e.id,
                            dueDate = System.currentTimeMillis() + 30L * 24L * 60L * 60L * 1000L,
                            baseAmount = e.baseAmount, vatAmount = e.vatAmount, totalAmount = e.totalAmount, notes = e.notes)
                        db.invoicesDao().createInvoice(inv, lines.map { l -> InvoiceLineEntity(invoiceId = 0, serviceId = l.serviceId,
                            descriptionSnapshot = l.descriptionSnapshot, unitSnapshot = l.unitSnapshot, quantity = l.quantity,
                            unitPriceExVat = l.unitPriceExVat, vatRateSnapshot = l.vatRateSnapshot, lineBase = l.lineBase, lineVat = l.lineVat, lineTotal = l.lineTotal) })
                    }
                } }) { Text(if (already) "Facturada" else "Crear factura") }
            } }
        }
        Spacer(Modifier.height(12.dp)); Text("Facturas emitidas", style = MaterialTheme.typography.titleMedium)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(invoices, key = { it.id }) { inv ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text("${inv.number} · ${inv.clientNameSnapshot}", style = MaterialTheme.typography.titleMedium)
                    val shownStatus = if (inv.paidAmount < inv.totalAmount - 0.009 && inv.dueDate != null && inv.dueDate < System.currentTimeMillis()) "Vencida" else inv.status
                    Text("$shownStatus · Total ${money(inv.totalAmount)} · Pendiente ${money((inv.totalAmount-inv.paidAmount).coerceAtLeast(0.0))}")
                    if (inv.dueDate != null) Text("Vencimiento: ${dateText(inv.dueDate)}", fontSize = 12.sp)
                    Row {
                        if (inv.correctionOfInvoiceId == null) {
                            TextButton(onClick = { payInvoice = inv; payAmount = "" }) { Text("Registrar cobro") }
                            TextButton(onClick = { selectedInvoice = inv }) { Text("Cobros") }
                            if (inv.paidAmount <= 0.009 && inv.totalAmount > 0.0) {
                                TextButton(onClick = { editInvoice = inv; editDueDate = inv.dueDate?.let(::dateText) ?: ""; editInvoiceNotes = inv.notes }) { Text("Editar vencimiento") }
                            }
                            if (inv.totalAmount > 0.0) TextButton(onClick = { correctionInvoice = inv; correctionReason = "" }) { Text("Rectificar") }
                        }
                        TextButton(onClick = { pdfInvoice = inv; pdfLauncher.launch("${inv.number}.pdf") }) { Text("PDF") }
                    }
                } }
            }
        }
    }
    if (editInvoice != null) AlertDialog(
        onDismissRequest = { editInvoice = null },
        title = { Text("Editar factura · ${editInvoice!!.number}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Solo puedes modificar estos datos mientras no haya cobros registrados. El importe y las líneas emitidas no se alteran.", fontSize = 12.sp)
                OutlinedTextField(editDueDate, { editDueDate = it }, label = { Text("Vencimiento (dd/MM/aaaa)") }, placeholder = { Text("Dejar vacío si no vence") })
                OutlinedTextField(editInvoiceNotes, { editInvoiceNotes = it }, label = { Text("Observaciones") }, minLines = 2)
                if (editDueDate.isNotBlank() && parseDateOrNull(editDueDate) == null) {
                    Text("Introduce una fecha válida, por ejemplo 15/12/2026.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = { TextButton(onClick = {
            val invoice = editInvoice!!
            val due = parseDateOrNull(editDueDate)
            if (editDueDate.isBlank() || due != null) scope.launch {
                runCatching { db.invoicesDao().updateInvoiceDetailsSafely(invoice.id, due, editInvoiceNotes) }
                    .onFailure { android.widget.Toast.makeText(context, it.message ?: "No se pudo actualizar la factura", android.widget.Toast.LENGTH_LONG).show() }
                editInvoice = null
            }
        }, enabled = editDueDate.isBlank() || parseDateOrNull(editDueDate) != null) { Text("Guardar cambios") } },
        dismissButton = { TextButton(onClick = { editInvoice = null }) { Text("Cancelar") } }
    )

    if (correctionInvoice != null) AlertDialog(
        onDismissRequest = { correctionInvoice = null },
        title = { Text("Crear rectificativa") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Se generará una rectificativa por el importe íntegro de ${correctionInvoice!!.number}.")
                Text("Esta operación no elimina ni modifica la factura original.", fontSize = 12.sp)
                OutlinedTextField(correctionReason, { correctionReason = it }, label = { Text("Motivo de rectificación *") })
            }
        },
        confirmButton = { TextButton(onClick = {
            val original = correctionInvoice!!
            if (correctionReason.isNotBlank()) scope.launch {
                runCatching {
                    if (db.invoicesDao().countCorrections(original.id) == 0) {
                        val originalLines = db.invoicesDao().getLines(original.id)
                        val count = db.invoicesDao().countInvoices() + 1
                        val correction = InvoiceEntity(
                            number = "RC-%04d".format(count), clientId = original.clientId,
                            clientNameSnapshot = original.clientNameSnapshot, gardenId = original.gardenId,
                            gardenNameSnapshot = original.gardenNameSnapshot, issueDate = System.currentTimeMillis(),
                            dueDate = null, status = "Rectificativa", baseAmount = -kotlin.math.abs(original.baseAmount),
                            vatAmount = -kotlin.math.abs(original.vatAmount), totalAmount = -kotlin.math.abs(original.totalAmount),
                            paidAmount = 0.0, notes = "Rectifica ${original.number}", correctionOfInvoiceId = original.id,
                            correctionReason = correctionReason.trim()
                        )
                        db.invoicesDao().createInvoice(correction, originalLines.map { line ->
                            InvoiceLineEntity(invoiceId = 0, serviceId = line.serviceId,
                                descriptionSnapshot = "Rectificación: ${line.descriptionSnapshot}", unitSnapshot = line.unitSnapshot,
                                quantity = -kotlin.math.abs(line.quantity), unitPriceExVat = line.unitPriceExVat,
                                vatRateSnapshot = line.vatRateSnapshot, lineBase = -kotlin.math.abs(line.lineBase),
                                lineVat = -kotlin.math.abs(line.lineVat), lineTotal = -kotlin.math.abs(line.lineTotal))
                        })
                    } else throw IllegalStateException("Esta factura ya tiene una rectificativa completa")
                }.onFailure { android.widget.Toast.makeText(context, it.message ?: "No se pudo crear la rectificativa", android.widget.Toast.LENGTH_LONG).show() }
                correctionInvoice = null
            }
        }, enabled = correctionReason.isNotBlank()) { Text("Crear rectificativa") } },
        dismissButton = { TextButton(onClick = { correctionInvoice = null }) { Text("Cancelar") } }
    )

    if (selectedInvoice != null) AlertDialog(
        onDismissRequest = { selectedInvoice = null },
        title = { Text("Cobros · ${selectedInvoice!!.number}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (paymentRows.isEmpty()) Text("Todavía no hay cobros registrados.")
            paymentRows.forEach { payment ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(money(payment.amount)); Text("${dateText(payment.paymentDate)} · ${payment.method}", fontSize = 12.sp)
                    }
                    TextButton(onClick = {
                        receiptInvoice = selectedInvoice; receiptPayment = payment
                        receiptLauncher.launch("Recibo-${selectedInvoice!!.number}-${payment.id}.pdf")
                    }) { Text("Recibo PDF") }
                }
            }
        } },
        confirmButton = { TextButton(onClick = { selectedInvoice = null }) { Text("Cerrar") } }
    )
    if (payInvoice != null) AlertDialog(onDismissRequest = { payInvoice = null }, title = { Text("Registrar pago · ${payInvoice!!.number}") },
        text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pendiente: ${money((payInvoice!!.totalAmount-payInvoice!!.paidAmount).coerceAtLeast(0.0))}")
            OutlinedTextField(payAmount, { payAmount = it }, label = { Text("Importe recibido (€)") })
            OutlinedTextField(payMethod, { payMethod = it }, label = { Text("Método de pago") })
        } }, confirmButton = { TextButton(onClick = {
            val amount = payAmount.replace(',', '.').toDoubleOrNull(); val invoice = payInvoice!!
            if (amount != null && amount.isFinite() && amount > 0.0 && payMethod.isNotBlank()) scope.launch {
                runCatching { db.invoicesDao().registerPayment(invoice.id, InvoicePaymentEntity(invoiceId = invoice.id, amount = amount, method = payMethod.trim())) }
                    .onFailure { android.widget.Toast.makeText(context, it.message ?: "No se pudo registrar el pago", android.widget.Toast.LENGTH_LONG).show() }
                payInvoice = null
            }
        }, enabled = (payAmount.replace(',', '.').toDoubleOrNull()?.let { it.isFinite() && it > 0.0 } == true) && payMethod.isNotBlank()) { Text("Guardar pago") } }, dismissButton = { TextButton(onClick = { payInvoice = null }) { Text("Cancelar") } })
}
