package com.igmjardines.app

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import com.igmjardines.app.data.CompanyProfileEntity
import com.igmjardines.app.data.InvoiceEntity
import com.igmjardines.app.data.InvoiceLineEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun pdfMoney(v: Double) = String.format(Locale("es", "ES"), "%.2f €", v)
private fun pdfDate(v: Long) = SimpleDateFormat("dd/MM/yyyy", Locale("es", "ES")).format(Date(v))

fun writeInvoicePdf(context: Context, uri: Uri, invoice: InvoiceEntity, lines: List<InvoiceLineEntity>, company: CompanyProfileEntity) {
    val document = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val left = 42f
    val right = 553f
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun startPage() {
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.rgb(35, 107, 69)
        paint.textSize = 21f; paint.isFakeBoldText = true
        canvas.drawText(company.tradeName.ifBlank { "IGM JARDINES" }, left, 48f, paint)
        paint.color = android.graphics.Color.DKGRAY; paint.isFakeBoldText = false; paint.textSize = 10f
        canvas.drawText(company.tagline.take(90), left, 65f, paint)
        var headerY = 84f
        val companyLines = listOf(company.holderName, company.taxId, company.address, listOf(company.postalCode, company.city).filter { it.isNotBlank() }.joinToString(" "), company.phone, company.email).filter { it.isNotBlank() }
        companyLines.forEach { canvas.drawText(it.take(95), left, headerY, paint); headerY += 12f }
        if (company.logoUri.isNotBlank()) {
            runCatching {
                context.contentResolver.openInputStream(Uri.parse(company.logoUri))?.use { input ->
                    val bitmap = BitmapFactory.decodeStream(input)
                    if (bitmap != null) {
                        val maxW = 105f; val maxH = 70f
                        val scale = minOf(maxW / bitmap.width, maxH / bitmap.height)
                        val w = bitmap.width * scale; val h = bitmap.height * scale
                        val dst = android.graphics.RectF(right - w, 24f, right, 24f + h)
                        canvas.drawBitmap(bitmap, null, dst, paint); bitmap.recycle()
                    }
                }
            }
        }
        y = maxOf(160f, headerY + 12f)
        paint.color = android.graphics.Color.BLACK; paint.textSize = 18f; paint.isFakeBoldText = true
        val documentTitle = if (invoice.correctionOfInvoiceId != null) "FACTURA RECTIFICATIVA ${invoice.number}" else "FACTURA ${invoice.number}"
        canvas.drawText(documentTitle, left, y, paint); y += 25f
        if (invoice.correctionOfInvoiceId != null) {
            paint.textSize = 10f
            canvas.drawText("Factura rectificada: ${invoice.notes.removePrefix("Rectifica ").take(50)}", left, y, paint); y += 14f
            canvas.drawText("Motivo: ${invoice.correctionReason.take(90)}", left, y, paint); y += 14f
        }
        paint.textSize = 10f; paint.isFakeBoldText = false
        canvas.drawText("Fecha: ${pdfDate(invoice.issueDate)}", left, y, paint)
        invoice.dueDate?.let { canvas.drawText("Vencimiento: ${pdfDate(it)}", 250f, y, paint) }
        y += 18f
        canvas.drawText("Cliente: ${invoice.clientNameSnapshot.take(80)}", left, y, paint); y += 14f
        if (invoice.gardenNameSnapshot.isNotBlank()) { canvas.drawText("Jardín: ${invoice.gardenNameSnapshot.take(80)}", left, y, paint); y += 14f }
        y += 10f
        paint.isFakeBoldText = true
        canvas.drawText("Concepto", left, y, paint); canvas.drawText("Cant.", 330f, y, paint); canvas.drawText("Precio", 390f, y, paint); canvas.drawText("Total", 485f, y, paint)
        paint.isFakeBoldText = false; y += 14f
    }
    fun finishPage() {
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.GRAY; paint.textSize = 9f
        canvas.drawText(company.footer.take(100), left, 810f, paint)
        canvas.drawText("Página $pageNumber", 490f, 810f, paint)
        document.finishPage(page!!); page = null
    }
    startPage()
    lines.forEach { line ->
        if (y > 745f) { finishPage(); startPage() }
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.BLACK; paint.textSize = 9.5f
        val description = line.descriptionSnapshot.take(43)
        canvas.drawText(description, left, y, paint)
        canvas.drawText(String.format(Locale.US, "%.2f", line.quantity), 330f, y, paint)
        canvas.drawText(pdfMoney(line.unitPriceExVat), 390f, y, paint)
        canvas.drawText(pdfMoney(line.lineTotal), 485f, y, paint)
        y += 17f
    }
    if (y > 700f) { finishPage(); startPage() }
    val canvas = page!!.canvas
    y += 18f; paint.textSize = 10f; paint.isFakeBoldText = true
    canvas.drawText("Desglose de IVA", left, y, paint); y += 15f
    paint.isFakeBoldText = false
    lines.groupBy { it.vatRateSnapshot }.toSortedMap().forEach { (rate, group) ->
        canvas.drawText("Base ${rate.toString().replace('.', ',')}%: ${pdfMoney(group.sumOf { it.lineBase })} · Cuota: ${pdfMoney(group.sumOf { it.lineVat })}", left, y, paint)
        y += 14f
    }
    if (y > 745f) { finishPage(); startPage() }
    val summaryCanvas = page!!.canvas
    y += 8f; paint.textSize = 11f; paint.isFakeBoldText = true
    summaryCanvas.drawText("Base imponible: ${pdfMoney(invoice.baseAmount)}", 350f, y, paint); y += 17f
    paint.isFakeBoldText = false
    summaryCanvas.drawText("IVA: ${pdfMoney(invoice.vatAmount)}", 350f, y, paint); y += 19f
    val discountValue = (invoice.baseAmount + invoice.vatAmount - invoice.totalAmount).coerceAtLeast(0.0)
    if (discountValue > 0.009) { summaryCanvas.drawText("Descuento: -${pdfMoney(discountValue)}", 350f, y, paint); y += 17f }
    paint.textSize = 15f; paint.isFakeBoldText = true
    summaryCanvas.drawText("TOTAL: ${pdfMoney(invoice.totalAmount)}", 350f, y, paint); y += 21f
    paint.textSize = 10f; paint.isFakeBoldText = false
    summaryCanvas.drawText("Cobrado: ${pdfMoney(invoice.paidAmount)}", 350f, y, paint); y += 15f
    summaryCanvas.drawText("Pendiente: ${pdfMoney((invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0))}", 350f, y, paint)
    if (company.iban.isNotBlank()) { y += 24f; summaryCanvas.drawText("IBAN: ${company.iban.take(45)}", left, y, paint) }
    if (company.website.isNotBlank()) { y += 14f; summaryCanvas.drawText(company.website.take(90), left, y, paint) }
    if (invoice.notes.isNotBlank()) { y += 25f; paint.textSize = 9f; summaryCanvas.drawText("Notas: ${invoice.notes.take(100)}", left, y, paint) }
    finishPage()
    try { context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) } ?: error("No se pudo abrir el destino del PDF") }
    finally { document.close() }
}

fun writePaymentReceiptPdf(context: Context, uri: Uri, invoice: InvoiceEntity, payment: com.igmjardines.app.data.InvoicePaymentEntity, company: CompanyProfileEntity) {
    val doc = PdfDocument()
    val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
    val p = Paint(Paint.ANTI_ALIAS_FLAG)
    var y = 60f
    p.color = android.graphics.Color.rgb(35, 107, 69); p.textSize = 23f; p.isFakeBoldText = true
    page.canvas.drawText(company.tradeName.ifBlank { "IGM JARDINES" }, 42f, y, p); y += 28f
    p.color = android.graphics.Color.BLACK; p.textSize = 18f
    page.canvas.drawText("RECIBO DE PAGO", 42f, y, p); y += 34f
    p.textSize = 12f; p.isFakeBoldText = false
    val rows = listOf(
        "Recibo asociado a factura: ${invoice.number}",
        "Cliente: ${invoice.clientNameSnapshot}",
        "Fecha de cobro: ${pdfDate(payment.paymentDate)}",
        "Método de pago: ${payment.method}",
        "Importe recibido: ${pdfMoney(payment.amount)}",
        "Total factura: ${pdfMoney(invoice.totalAmount)}",
        "Total cobrado acumulado: ${pdfMoney(invoice.paidAmount)}",
        "Pendiente tras este cobro: ${pdfMoney((invoice.totalAmount - invoice.paidAmount).coerceAtLeast(0.0))}"
    )
    rows.forEach { page.canvas.drawText(it.take(100), 42f, y, p); y += 23f }
    if (payment.note.isNotBlank()) { y += 15f; page.canvas.drawText("Observaciones: ${payment.note.take(90)}", 42f, y, p) }
    y += 50f; page.canvas.drawText(company.footer.take(100), 42f, y, p)
    doc.finishPage(page)
    try { context.contentResolver.openOutputStream(uri)?.use { doc.writeTo(it) } ?: error("No se pudo guardar el recibo") }
    finally { doc.close() }
}
