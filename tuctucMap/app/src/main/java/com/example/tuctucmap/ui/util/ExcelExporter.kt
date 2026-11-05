package com.example.tuctucmap.ui.util

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.example.tuctucmap.data.model.AdminSubscriptionHistory
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Generador nativo y ultra liviano de hojas de cálculo Excel (.xlsx / OpenXML Spreadsheet)
 * sin requerir dependencias externas pesadas como Apache POI.
 */
object ExcelExporter {

    private fun escapeXml(text: String): String {
        return text.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun colLetter(colIndex: Int): String {
        var index = colIndex
        val sb = StringBuilder()
        while (index >= 0) {
            sb.insert(0, ('A'.code + (index % 26)).toChar())
            index = (index / 26) - 1
        }
        return sb.toString()
    }

    fun exportAndShareSubscriptionsExcel(
        context: Context,
        history: List<AdminSubscriptionHistory>,
        currencySymbol: String,
        periodLabel: String
    ) {
        if (history.isEmpty()) {
            Toast.makeText(context, "No hay registros en el filtro seleccionado para exportar", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val cacheDir = context.cacheDir
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val file = File(cacheDir, "tuctuc_suscripciones_$timestamp.xlsx")

            val headers = listOf(
                "ID Registro",
                "Conductor",
                "Placa / No. Unidad",
                "Plan",
                "Monto ($currencySymbol)",
                "Método de Pago",
                "No. Referencia / Boleta",
                "Fecha de Pago",
                "Fecha de Vencimiento",
                "Estado"
            )

            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
            val rowsData = mutableListOf<List<String>>()

            for (h in history) {
                val dateStr = if (h.createdAtEpoch > 0) sdf.format(Date(h.createdAtEpoch)) else "N/A"
                val endStr = if (h.endDateEpoch > 0) sdf.format(Date(h.endDateEpoch)) else "N/A"
                val status = if (h.isCurrentlyActive) "Activo" else "Expirado"
                val plate = h.driverPlate.ifBlank { "N/D" }
                val driverName = h.driverName.ifBlank { "Conductor" }
                val method = when (h.paymentMethod) {
                    "deposito_bancario" -> "Depósito Bancario"
                    "transferencia" -> "Transferencia"
                    "efectivo" -> "Efectivo"
                    else -> h.paymentMethod.replace("_", " ").replaceFirstChar { it.uppercase() }
                }

                rowsData.add(
                    listOf(
                        h.id,
                        driverName,
                        plate,
                        h.planType.uppercase(),
                        String.format(Locale.US, "%.2f", h.amountPaid),
                        method,
                        h.referenceNumber.ifBlank { "Sin ref." },
                        dateStr,
                        endStr,
                        status
                    )
                )
            }

            writeXlsx(file, headers, rowsData, isAmountCol = { colIdx -> colIdx == 4 })

            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                putExtra(Intent.EXTRA_SUBJECT, "Historial de Suscripciones TucTuc Map ($periodLabel)")
                putExtra(Intent.EXTRA_TEXT, "Adjunto reporte oficial de suscripciones de TucTuc Map correspondiente al filtro: $periodLabel (${history.size} registros).")
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(shareIntent, "Compartir reporte Excel"))
        } catch (e: Exception) {
            android.util.Log.e("ExcelExporter", "Error al generar Excel: ${e.message}", e)
            Toast.makeText(context, "Error al generar Excel: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
        }
    }

    private fun writeXlsx(
        file: File,
        headers: List<String>,
        rows: List<List<String>>,
        isAmountCol: (Int) -> Boolean
    ) {
        ZipOutputStream(FileOutputStream(file).buffered()).use { zip ->
            // 1. [Content_Types].xml
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 2. _rels/.rels
            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 3. xl/_rels/workbook.xml.rels
            zip.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 4. xl/workbook.xml
            zip.putNextEntry(ZipEntry("xl/workbook.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Suscripciones" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 5. xl/styles.xml (Estilo 0 = regular, Estilo 1 = cabecera en negrita con fondo tenue)
            zip.putNextEntry(ZipEntry("xl/styles.xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2">
    <font><name val="Calibri"/><family val="2"/><sz val="11"/></font>
    <font><b/><name val="Calibri"/><family val="2"/><sz val="11"/></font>
  </fonts>
  <fills count="3">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFF1F5F9"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>""".toByteArray(Charsets.UTF_8)
            )
            zip.closeEntry()

            // 6. xl/worksheets/sheet1.xml
            zip.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            val sheetSb = StringBuilder()
            sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <sheetData>
""")

            // Cabeceras (Fila 1)
            sheetSb.append("    <row r=\"1\">\n")
            for ((cIdx, h) in headers.withIndex()) {
                val ref = "${colLetter(cIdx)}1"
                sheetSb.append("      <c r=\"$ref\" t=\"inlineStr\" s=\"1\"><is><t>${escapeXml(h)}</t></is></c>\n")
            }
            sheetSb.append("    </row>\n")

            // Filas de datos (Desde Fila 2)
            var rowNum = 2
            for (row in rows) {
                sheetSb.append("    <row r=\"$rowNum\">\n")
                for ((cIdx, cellVal) in row.withIndex()) {
                    val ref = "${colLetter(cIdx)}$rowNum"
                    if (isAmountCol(cIdx)) {
                        val num = cellVal.toDoubleOrNull()
                        if (num != null) {
                            sheetSb.append("      <c r=\"$ref\" t=\"n\"><v>${String.format(Locale.US, "%.2f", num)}</v></c>\n")
                        } else {
                            sheetSb.append("      <c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(cellVal)}</t></is></c>\n")
                        }
                    } else {
                        sheetSb.append("      <c r=\"$ref\" t=\"inlineStr\"><is><t>${escapeXml(cellVal)}</t></is></c>\n")
                    }
                }
                sheetSb.append("    </row>\n")
                rowNum++
            }

            sheetSb.append("""  </sheetData>
</worksheet>""")

            zip.write(sheetSb.toString().toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
}

