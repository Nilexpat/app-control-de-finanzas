package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.data.model.Movement
import com.example.data.model.MovementType
import com.example.data.model.PaymentMethod
import com.example.data.model.SettlementSummary
import java.io.File
import java.io.FileOutputStream

object ExportHelper {

    fun generateWhatsAppSummary(groupName: String, summary: SettlementSummary): String {
        val sb = StringBuilder()
        sb.appendLine("📊 *REPORTE FINANCIERO - $groupName*")
        sb.appendLine("📅 Mes: ${summary.monthTitle}")
        sb.appendLine("📌 Estado: ${if (summary.isClosed) "Cerrado (Definitivo)" else "Abierto (Provisional)"}")
        sb.appendLine("─────────────────────────")
        sb.appendLine("🟢 *Ventas:* ${CurrencyFormatter.formatCop(summary.totalSalesCop)}")
        sb.appendLine("🔴 *Compras:* ${CurrencyFormatter.formatCop(summary.totalPurchasesCop)}")
        sb.appendLine("📈 *Utilidad:* ${CurrencyFormatter.formatCop(summary.utilityCop)}")
        sb.appendLine("💰 *A Repartir:* ${CurrencyFormatter.formatCop(summary.distributableUtilityCop)}")
        sb.appendLine("─────────────────────────")
        sb.appendLine("👥 *REPARTO DE GANANCIAS:*")
        for (d in summary.distributions) {
            val debtNote = if (d.debtCop > 0) " (Deuda: ${CurrencyFormatter.formatCop(d.debtCop)} | Desc: -${CurrencyFormatter.formatCop(d.discountCop)})" else ""
            sb.appendLine("• *${d.recipientName}* (${d.percentageFormatted}):")
            sb.appendLine("  Parte: ${CurrencyFormatter.formatCop(d.shareAmountCop)}$debtNote")
            sb.appendLine("  👉 *Pago Final: ${CurrencyFormatter.formatCop(d.finalPaymentCop)}*")
            if (d.remainingDebtCop > 0) {
                sb.appendLine("  ⚠️ Deuda pendiente: ${CurrencyFormatter.formatCop(d.remainingDebtCop)}")
            }
        }
        sb.appendLine("─────────────────────────")
        sb.appendLine("🏦 *DÓNDE ESTÁ LA PLATA (NETO):*")
        for (loc in summary.moneyLocations) {
            if (!loc.isSubRow) {
                sb.appendLine("• *${loc.name}:* ${CurrencyFormatter.formatCop(loc.netCop)}")
            }
        }
        sb.appendLine("─────────────────────────")
        sb.appendLine("_Generado con SocioFinanzas_")
        return sb.toString()
    }

    fun generateCsv(groupName: String, summary: SettlementSummary, movements: List<Movement>): String {
        val sb = StringBuilder()
        sb.appendLine("Fecha,Tipo,Detalle,Medio de Pago,Cuenta,Monto COP,Registrado Por,Estado")
        for (m in movements) {
            val dateStr = DateFormatter.formatFullDateTime(m.occurredAt)
            val typeStr = when (m.type) {
                MovementType.VENTA -> "Venta"
                MovementType.COMPRA -> "Compra"
                MovementType.PRESTAMO -> "Préstamo"
                MovementType.ABONO -> "Abono"
            }
            val lines = JsonHelper.deserializeLines(m.linesJson)
            val detail = if (lines.isNotEmpty()) {
                lines.joinToString(" + ") { "${it.productName} (x${it.quantity})" }
            } else if (m.type == MovementType.PRESTAMO || m.type == MovementType.ABONO) {
                "Socio: ${m.debtorMemberName ?: m.registeredByName}"
            } else {
                m.note.ifBlank { "Sin detalle" }
            }
            val payment = if (m.paymentMethod == PaymentMethod.EFECTIVO) "Efectivo" else "Transferencia"
            val account = m.transferAccountName ?: ""
            val status = if (m.isAnnulled) "ANULADO (${m.annulReason ?: ""})" else "Activo"
            sb.appendLine("\"$dateStr\",\"$typeStr\",\"$detail\",\"$payment\",\"$account\",${m.totalCop},\"${m.registeredByName}\",\"$status\"")
        }
        return sb.toString()
    }

    fun createPdfReport(
        context: Context,
        groupName: String,
        summary: SettlementSummary,
        movements: List<Movement>
    ): File? {
        return try {
            val pdfDocument = PdfDocument()
            val pageWidth = 595 // A4 standard width in points
            val pageHeight = 842 // A4 standard height
            val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
            val page = pdfDocument.startPage(pageInfo)
            val canvas: Canvas = page.canvas

            val titlePaint = Paint().apply {
                color = Color.rgb(15, 118, 110)
                textSize = 18f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val subtitlePaint = Paint().apply {
                color = Color.rgb(71, 85, 105)
                textSize = 12f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                isAntiAlias = true
            }

            val headerPaint = Paint().apply {
                color = Color.rgb(30, 41, 59)
                textSize = 13f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val bodyPaint = Paint().apply {
                color = Color.rgb(51, 65, 85)
                textSize = 10f
                isAntiAlias = true
            }

            val boldBodyPaint = Paint().apply {
                color = Color.rgb(15, 23, 42)
                textSize = 10f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            val linePaint = Paint().apply {
                color = Color.rgb(203, 213, 225)
                strokeWidth = 1f
            }

            var y = 40f
            canvas.drawText("SOCIOFINANZAS - REPORTE DE CORTE MENSUAL", 40f, y, titlePaint)
            y += 20f
            canvas.drawText("Grupo: $groupName | Mes: ${summary.monthTitle}", 40f, y, subtitlePaint)
            y += 16f
            val statusStr = if (summary.isClosed) "CERRADO (Definitivo)" else "ABIERTO (Provisional)"
            canvas.drawText("Estado: $statusStr | Movimientos: ${summary.movementsCount}", 40f, y, subtitlePaint)
            y += 18f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Summary boxes
            canvas.drawText("RESUMEN GENERAL", 40f, y, headerPaint)
            y += 18f
            canvas.drawText("Ventas (+): ${CurrencyFormatter.formatCop(summary.totalSalesCop)}", 40f, y, boldBodyPaint)
            canvas.drawText("Compras (-): ${CurrencyFormatter.formatCop(summary.totalPurchasesCop)}", 220f, y, boldBodyPaint)
            canvas.drawText("Utilidad: ${CurrencyFormatter.formatCop(summary.utilityCop)}", 400f, y, boldBodyPaint)
            y += 18f
            canvas.drawText("Préstamos: ${CurrencyFormatter.formatCop(summary.totalLoansCop)}", 40f, y, bodyPaint)
            canvas.drawText("Abonos: ${CurrencyFormatter.formatCop(summary.totalAbonosCop)}", 220f, y, bodyPaint)
            canvas.drawText("Monto a Repartir: ${CurrencyFormatter.formatCop(summary.distributableUtilityCop)}", 400f, y, boldBodyPaint)
            y += 22f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 24f

            // Reparto table
            canvas.drawText("TABLA DE REPARTO DE GANANCIAS", 40f, y, headerPaint)
            y += 16f
            canvas.drawText("Destinatario", 40f, y, boldBodyPaint)
            canvas.drawText("%", 180f, y, boldBodyPaint)
            canvas.drawText("Parte", 230f, y, boldBodyPaint)
            canvas.drawText("Deuda", 300f, y, boldBodyPaint)
            canvas.drawText("Desc.", 370f, y, boldBodyPaint)
            canvas.drawText("PAGO FINAL", 440f, y, boldBodyPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 14f

            for (d in summary.distributions) {
                canvas.drawText(d.recipientName.take(22), 40f, y, bodyPaint)
                canvas.drawText(d.percentageFormatted, 180f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(d.shareAmountCop), 230f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(d.debtCop), 300f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(d.discountCop), 370f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(d.finalPaymentCop), 440f, y, boldBodyPaint)
                y += 14f
            }

            y += 12f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 22f

            // Dónde está la plata table
            canvas.drawText("DÓNDE ESTÁ LA PLATA (POR MEDIO DE PAGO)", 40f, y, headerPaint)
            y += 16f
            canvas.drawText("Medio / Cuenta", 40f, y, boldBodyPaint)
            canvas.drawText("Ventas (+)", 180f, y, boldBodyPaint)
            canvas.drawText("Compras (-)", 270f, y, boldBodyPaint)
            canvas.drawText("Préstamos (-)", 360f, y, boldBodyPaint)
            canvas.drawText("NETO", 460f, y, boldBodyPaint)
            y += 6f
            canvas.drawLine(40f, y, 555f, y, linePaint)
            y += 14f

            for (loc in summary.moneyLocations) {
                val paint = if (loc.isSubRow) bodyPaint else boldBodyPaint
                canvas.drawText(loc.name.take(24), 40f, y, paint)
                canvas.drawText(CurrencyFormatter.formatCop(loc.salesCop), 180f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(loc.purchasesCop), 270f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(loc.loansCop), 360f, y, bodyPaint)
                canvas.drawText(CurrencyFormatter.formatCop(loc.netCop), 460f, y, boldBodyPaint)
                y += 14f
            }

            y += 10f
            val notePaint = Paint().apply {
                color = Color.rgb(100, 116, 139)
                textSize = 8.5f
                isAntiAlias = true
            }
            canvas.drawText("* Movimientos registrados en el período. No incluye pagos del reparto.", 40f, y, notePaint)

            pdfDocument.finishPage(page)

            val dir = File(context.cacheDir, "reports")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "Corte_${summary.monthKey}_$groupName.pdf")
            val outputStream = FileOutputStream(file)
            pdfDocument.writeTo(outputStream)
            outputStream.close()
            pdfDocument.close()
            file
        } catch (_: Exception) {
            null
        }
    }

    fun shareFile(context: Context, file: File, mimeType: String, title: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    fun shareText(context: Context, text: String, title: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_SUBJECT, title)
        }
        val chooser = Intent.createChooser(intent, title).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
