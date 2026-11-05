package com.example.tuctucmap.ui.util

/**
 * Utilidades de seguridad para exportación CSV (Mitigación OWASP Formula / CSV Injection).
 * 1. Duplica comillas dobles internas (" -> "")
 * 2. Si el contenido inicia con un caracter ejecutable (=, +, -, @, \t, \r), antepone un apóstrofe (')
 *    para forzar a Excel / Google Sheets a interpretarlo estrictamente como texto plano.
 * 3. Envuelve la celda resultante en comillas dobles.
 */
object CsvSecurityUtils {
    fun escapeCsvCell(raw: Any?): String {
        if (raw == null) return "\"\""
        val text = raw.toString()
        if (text.isEmpty()) return "\"\""

        var escaped = text.replace("\"", "\"\"")
        val firstChar = escaped.first()
        if (firstChar in charArrayOf('=', '+', '-', '@', '\t', '\r')) {
            escaped = "'$escaped"
        }
        return "\"$escaped\""
    }
}

