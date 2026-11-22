package com.ejemplo.parkeasy.domain

import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.ChangeLogEntry
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.rules.CsvExport
import com.ejemplo.parkeasy.domain.rules.HistoryFilter
import com.ejemplo.parkeasy.domain.rules.HistoryTotals
import com.ejemplo.parkeasy.domain.rules.RecordState
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals

class HistoryRulesTest {
    private val bogota = ZoneId.of("America/Bogota")

    private fun record(
        id: Long, plate: String, entry: String, exit: String? = null, charged: Long? = null,
        floor: String = "Piso 1", type: VehicleType = VehicleType.CAR, notes: String = "",
    ) = ParkingRecord(
        id, 1, "A-01", floor, plate, "Rojo", type, Instant.parse(entry), exit?.let(Instant::parse),
        charged, null, null, null, notes,
    )

    private val all = listOf(
        record(1, "ABC123", "2026-11-20T15:00:00Z", "2026-11-20T17:00:00Z", 5_500),
        record(2, "XYZ987", "2026-11-21T15:00:00Z", "2026-11-21T16:00:00Z", 4_000, floor = "Piso 2"),
        record(3, "MNO456", "2026-11-22T15:00:00Z", type = VehicleType.MOTORCYCLE),
    )

    @Test
    fun `filtra por placa ignorando guiones y mayúsculas`() {
        assertEquals(listOf(2L), HistoryFilter(plate = "xyz-98").apply(all, bogota).map { it.id })
    }

    @Test
    fun `filtra por estado piso y tipo`() {
        assertEquals(listOf(3L), HistoryFilter(state = RecordState.INSIDE).apply(all, bogota).map { it.id })
        assertEquals(listOf(1L, 2L), HistoryFilter(state = RecordState.CLOSED).apply(all, bogota).map { it.id })
        assertEquals(listOf(2L), HistoryFilter(floorName = "Piso 2").apply(all, bogota).map { it.id })
        assertEquals(listOf(3L), HistoryFilter(vehicleType = VehicleType.MOTORCYCLE).apply(all, bogota).map { it.id })
    }

    @Test
    fun `las fechas incluyen el día completo en hora de Bogotá`() {
        val day = LocalDate.of(2026, 11, 21)
        assertEquals(listOf(2L), HistoryFilter(from = day, to = day).apply(all, bogota).map { it.id })
        // 2026-11-22T03:00Z ya es 22:00 del día 21 en Bogotá
        val late = listOf(record(9, "LAT111", "2026-11-22T03:00:00Z"))
        assertEquals(1, HistoryFilter(from = day, to = day).apply(late, bogota).size)
        assertEquals(0, HistoryFilter(from = day.plusDays(1)).apply(late, bogota).size)
    }

    @Test
    fun `totales suman solo lo cobrado`() {
        assertEquals(HistoryTotals(3, 2, 9_500), HistoryTotals.of(all))
    }

    @Test
    fun `csv escapa separadores comillas y fórmulas`() {
        assertEquals("a", CsvExport.escape("a"))
        assertEquals("\"a;b\"", CsvExport.escape("a;b"))
        assertEquals("\"di \"\"hola\"\"\"", CsvExport.escape("di \"hola\""))
        assertEquals("'=SUMA(A1)", CsvExport.escape("=SUMA(A1)"))
        assertEquals("\"línea1\nlínea2\"", CsvExport.escape("línea1\nlínea2"))
    }

    @Test
    fun `csv de registros trae encabezado y horas de Bogotá`() {
        val lines = CsvExport.records(all, bogota).lines().filter { it.isNotEmpty() }
        assertEquals(4, lines.size)
        assertEquals("Placa;Tipo;Color;Piso;Puesto;Entrada;Salida;Cobrado (COP);Observaciones", lines[0])
        assertEquals("ABC123;CAR;Rojo;Piso 1;A-01;2026-11-20 10:00:00;2026-11-20 12:00:00;5500;", lines[1])
        assertEquals("MNO456;MOTORCYCLE;Rojo;Piso 1;A-01;2026-11-22 10:00:00;;;", lines[3])
    }

    @Test
    fun `csv de bitácora`() {
        val entry = ChangeLogEntry(1, Instant.parse("2026-11-22T15:00:00Z"), ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, 4, "Puesto A-01 creado")
        val lines = CsvExport.changeLog(listOf(entry), bogota).lines()
        assertEquals("2026-11-22 10:00:00;SPOT_CREATED;SPOT;4;Puesto A-01 creado", lines[1])
    }
}
