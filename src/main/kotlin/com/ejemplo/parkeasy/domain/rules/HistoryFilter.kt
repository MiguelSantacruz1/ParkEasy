package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.LocalDate
import java.time.ZoneId

enum class RecordState { ALL, INSIDE, CLOSED }

/** Filtros del historial. Las fechas incluyen el día completo, en la zona horaria dada. */
data class HistoryFilter(
    val plate: String = "",
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val floorName: String? = null,
    val vehicleType: VehicleType? = null,
    val state: RecordState = RecordState.ALL,
) {
    fun apply(records: List<ParkingRecord>, zone: ZoneId): List<ParkingRecord> {
        val needle = PlateValidator.normalize(plate)
        val start = from?.atStartOfDay(zone)?.toInstant()
        val end = to?.plusDays(1)?.atStartOfDay(zone)?.toInstant()
        return records.filter { r ->
            (needle.isEmpty() || needle in r.plate) &&
                (start == null || r.entryTime >= start) &&
                (end == null || r.entryTime < end) &&
                (floorName == null || r.floorName == floorName) &&
                (vehicleType == null || r.vehicleType == vehicleType) &&
                when (state) {
                    RecordState.ALL -> true
                    RecordState.INSIDE -> r.isOpen
                    RecordState.CLOSED -> !r.isOpen
                }
        }
    }
}

/** Totales de una lista de registros: cuántos hay y cuánto se cobró en los cerrados. */
data class HistoryTotals(val count: Int, val closed: Int, val charged: Long) {
    companion object {
        fun of(records: List<ParkingRecord>) = HistoryTotals(
            count = records.size,
            closed = records.count { !it.isOpen },
            charged = records.sumOf { it.charged ?: 0L },
        )
    }
}
