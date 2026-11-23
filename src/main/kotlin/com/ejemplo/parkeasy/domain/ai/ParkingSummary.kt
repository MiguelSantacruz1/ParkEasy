package com.ejemplo.parkeasy.domain.ai

import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.Clock
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Resumen en texto de la operación que se le manda al asistente. Es solo agregados: **no incluye placas,
 * colores, observaciones ni etiquetas de puesto**, para no sacar datos personales del equipo.
 */
object ParkingSummary {
    const val DAYS = 30

    fun build(
        clock: Clock,
        businessName: String,
        floors: List<Floor>,
        spots: List<Spot>,
        records: List<ParkingRecord>,
        tariffs: List<Tariff>,
    ): String {
        val now = ZonedDateTime.now(clock)
        val today = now.toLocalDate()
        val zone = clock.zone
        val sb = StringBuilder()
        fun line(s: String = "") = sb.append(s).append('\n')

        line("Parqueadero: ${businessName.ifBlank { "sin nombre" }}")
        line("Fecha y hora actual: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} (${zone})")
        line("Moneda: pesos colombianos (COP).")
        line()

        line("== Ocupación ahora ==")
        floors.forEach { f ->
            val s = spots.filter { it.floorId == f.id }
            line(
                "${f.name}: ${s.size} puestos, ${s.count { it.status == SpotStatus.OCCUPIED }} ocupados, " +
                    "${s.count { it.status == SpotStatus.FREE }} libres, ${s.count { it.status == SpotStatus.BLOCKED }} bloqueados",
            )
        }
        line("Total: ${spots.size} puestos, ${spots.count { it.status == SpotStatus.OCCUPIED }} ocupados.")
        line()

        line("== Tarifas ==")
        tariffs.forEach {
            line("${name(it.vehicleType)}: primera hora ${it.firstHour}, hora adicional ${it.extraHour}, gracia ${it.graceMinutes} min")
        }
        line()

        val open = records.filter { it.isOpen }
        line("== Vehículos dentro (${open.size}) ==")
        if (open.isNotEmpty()) {
            val hours = open.map { Duration.between(it.entryTime, now.toInstant()).toMinutes() / 60.0 }.sorted()
            line("Llevan en promedio ${fmt(hours.average())} h; el que más lleva, ${fmt(hours.last())} h.")
            line("Más de 8 horas: ${hours.count { it > 8 }}. Más de 24 horas: ${hours.count { it > 24 }}.")
        }
        line()

        val from = today.minusDays(DAYS - 1L)
        val closed = records.filter { !it.isOpen && it.exitTime!!.atZone(zone).toLocalDate() >= from }
        line("== Últimos $DAYS días (por fecha de salida) ==")
        line("Estadías cerradas: ${closed.size}. Ingresos: ${closed.sumOf { it.charged ?: 0L }} COP.")
        if (closed.isNotEmpty()) {
            val mins = closed.map { Duration.between(it.entryTime, it.exitTime).toMinutes() }
            line("Duración promedio: ${fmt(mins.average())} min. Más larga: ${mins.max()} min.")
            VehicleType.entries.forEach { t ->
                val g = closed.filter { it.vehicleType == t }
                if (g.isNotEmpty()) line("${name(t)}: ${g.size} estadías, ${g.sumOf { it.charged ?: 0L }} COP")
            }
        }
        line("Ingresos por día (fecha: estadías, COP):")
        (0 until DAYS).map { from.plusDays(it.toLong()) }.forEach { d: LocalDate ->
            val day = closed.filter { it.exitTime!!.atZone(zone).toLocalDate() == d }
            if (day.isNotEmpty()) line("$d: ${day.size}, ${day.sumOf { it.charged ?: 0L }}")
        }
        line()

        val entries = records.filter { it.entryTime.atZone(zone).toLocalDate() >= from }
        if (entries.isNotEmpty()) {
            line("== Entradas por hora del día (últimos $DAYS días) ==")
            val byHour = entries.groupingBy { it.entryTime.atZone(zone).hour }.eachCount()
            line((0..23).filter { it in byHour }.joinToString(", ") { "${"%02d".format(it)}h: ${byHour[it]}" })
        }
        return sb.toString()
    }

    private fun name(t: VehicleType) = if (t == VehicleType.CAR) "Carro" else "Moto"
    private fun fmt(v: Double) = "%.1f".format(java.util.Locale.ROOT, v)
}
