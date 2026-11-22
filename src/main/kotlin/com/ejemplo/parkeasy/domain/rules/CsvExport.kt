package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.ChangeLogEntry
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Exporta a CSV con punto y coma (el separador que Excel espera con configuración regional
 * de Colombia). Los valores que empiezan con = + - @ se prefijan con una comilla para que
 * una hoja de cálculo no los ejecute como fórmula.
 */
object CsvExport {
    private val stamp = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
    const val SEPARATOR = ';'

    fun escape(value: String): String {
        val safe = if (value.isNotEmpty() && value[0] in "=+-@\t\r") "'$value" else value
        val needsQuotes = safe.any { it == SEPARATOR || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }

    private fun row(vararg cells: String) = cells.joinToString(SEPARATOR.toString()) { escape(it) }

    fun records(records: List<ParkingRecord>, zone: ZoneId): String = buildString {
        appendLine(row("Placa", "Tipo", "Color", "Piso", "Puesto", "Entrada", "Salida", "Cobrado (COP)", "Observaciones"))
        records.forEach { r ->
            appendLine(
                row(
                    r.plate, r.vehicleType.name, r.color, r.floorName, r.spotLabel,
                    stamp.format(r.entryTime.atZone(zone)),
                    r.exitTime?.let { stamp.format(it.atZone(zone)) } ?: "",
                    r.charged?.toString() ?: "",
                    r.notes,
                ),
            )
        }
    }

    fun changeLog(entries: List<ChangeLogEntry>, zone: ZoneId): String = buildString {
        appendLine(row("Fecha", "Acción", "Entidad", "Id", "Detalle"))
        entries.forEach {
            appendLine(row(stamp.format(it.timestamp.atZone(zone)), it.action.name, it.entity.name, it.entityId?.toString() ?: "", it.detail))
        }
    }
}
