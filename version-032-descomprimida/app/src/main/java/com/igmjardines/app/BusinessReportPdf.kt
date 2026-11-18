package com.igmjardines.app

import android.content.Context
import android.net.Uri
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.igmjardines.app.data.CompanyProfileEntity
import java.util.Locale

internal fun writeProfitabilityReportPdf(
    context: Context,
    uri: Uri,
    company: CompanyProfileEntity,
    rows: List<ProfitabilityRow>
) {
    val document = PdfDocument()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var pageNumber = 0
    var page: PdfDocument.Page? = null
    var y = 0f
    fun startPage() {
        pageNumber++
        page = document.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.rgb(35, 107, 69)
        paint.textSize = 21f
        paint.isFakeBoldText = true
        canvas.drawText(company.tradeName.ifBlank { "IGM JARDINES" }.take(45), 40f, 48f, paint)
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 16f
        canvas.drawText("INFORME DE RENTABILIDAD", 40f, 82f, paint)
        paint.textSize = 9f
        paint.isFakeBoldText = false
        canvas.drawText("Por cliente y jardín · importes en euros", 40f, 101f, paint)
        y = 132f
    }
    fun finishPage() {
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.GRAY
        paint.textSize = 8f
        canvas.drawText(company.footer.take(90), 40f, 810f, paint)
        canvas.drawText("Página $pageNumber", 500f, 810f, paint)
        document.finishPage(page!!)
        page = null
    }
    fun money(value: Double) = String.format(Locale("es", "ES"), "%.2f €", value)
    startPage()
    if (rows.isEmpty()) {
        page!!.canvas.drawText("No hay datos suficientes para generar el desglose.", 40f, y, paint)
    } else rows.forEach { row ->
        if (y > 745f) { finishPage(); startPage() }
        val canvas = page!!.canvas
        paint.color = android.graphics.Color.rgb(35, 107, 69)
        paint.textSize = 11f
        paint.isFakeBoldText = true
        canvas.drawText(row.label.take(75), 40f, y, paint)
        y += 18f
        paint.color = android.graphics.Color.BLACK
        paint.textSize = 9.5f
        paint.isFakeBoldText = false
        canvas.drawText("Base facturada: ${money(row.revenue)}", 48f, y, paint); y += 14f
        canvas.drawText("Base de gastos vinculados: ${money(row.expenses)}", 48f, y, paint); y += 14f
        canvas.drawText("Desplazamientos vinculados: ${money(row.travel)}", 48f, y, paint); y += 14f
        paint.isFakeBoldText = true
        canvas.drawText("Margen estimado: ${money(row.margin)}", 48f, y, paint)
        paint.isFakeBoldText = false
        y += 22f
    }
    if (y > 745f) { finishPage(); startPage() }
    y += 12f
    paint.color = android.graphics.Color.DKGRAY
    paint.textSize = 8f
    page!!.canvas.drawText("Cálculo orientativo: facturas emitidas menos gastos y desplazamientos vinculados a trabajos. No incluye reparto automático de gastos generales ni equivale al beneficio fiscal.", 40f, y, paint)
    finishPage()
    try {
        context.contentResolver.openOutputStream(uri)?.use { document.writeTo(it) }
            ?: error("No se pudo abrir el destino del informe")
    } finally { document.close() }
}
