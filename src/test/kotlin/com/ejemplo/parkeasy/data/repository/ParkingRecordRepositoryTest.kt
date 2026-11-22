package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.withEnv
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.RecordClosing
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParkingRecordRepositoryTest {
    private val t10 = Instant.parse("2026-11-22T10:00:00Z")
    private val t11 = Instant.parse("2026-11-22T11:00:00Z")

    private fun TestEnv.spot(label: String): Long {
        val floorId = floors.getAll().firstOrNull()?.id ?: floors.insert(NewFloor("Piso 1", 0))
        return spots.insert(NewSpot(floorId, label, SpotType.CAR))
    }

    private fun entry(spotId: Long, label: String, plate: String) =
        NewParkingRecord(spotId, label, "Piso 1", plate, "Azul", VehicleType.CAR, t10, "espejo roto")

    @Test
    fun `guarda la entrada con puesto, piso y observaciones`() = withEnv { env ->
        val spotId = env.spot("A-01")
        val id = env.records.insert(entry(spotId, "A-01", "ABC123"))

        val record = env.records.getById(id)!!
        assertEquals("A-01", record.spotLabel)
        assertEquals("Piso 1", record.floorName)
        assertEquals("espejo roto", record.notes)
        assertEquals(t10, record.entryTime)
        assertTrue(record.isOpen)
        assertNull(record.charged)
    }

    @Test
    fun `cerrar un registro guarda hora, valor y la tarifa usada`() = withEnv { env ->
        val id = env.records.insert(entry(env.spot("A-01"), "A-01", "ABC123"))
        val tariff = Tariff(VehicleType.CAR, 4_000, 1_500, 5)

        env.records.close(id, RecordClosing(t11, 4_000, tariff))

        val record = env.records.getById(id)!!
        assertEquals(t11, record.exitTime)
        assertEquals(4_000, record.charged)
        assertEquals(4_000, record.tariffFirstHour)
        assertEquals(1_500, record.tariffExtraHour)
        assertEquals(5, record.graceMinutes)
        assertTrue(!record.isOpen)
    }

    @Test
    fun `busca el registro abierto por puesto y por placa`() = withEnv { env ->
        val spotId = env.spot("A-01")
        val id = env.records.insert(entry(spotId, "A-01", "ABC123"))

        assertEquals(id, env.records.findOpenBySpot(spotId)?.id)
        assertEquals(id, env.records.findOpenByPlate("ABC123")?.id)
        assertNull(env.records.findOpenByPlate("XYZ987"))

        env.records.close(id, RecordClosing(t11, 4_000, Tariff.defaults.first()))
        assertNull(env.records.findOpenBySpot(spotId))
        assertNull(env.records.findOpenByPlate("ABC123"))
    }

    @Test
    fun `la base rechaza dos registros abiertos en el mismo puesto`() = withEnv { env ->
        val spotId = env.spot("A-01")
        env.records.insert(entry(spotId, "A-01", "ABC123"))

        assertFailsWith<Exception> { env.records.insert(entry(spotId, "A-01", "XYZ987")) }
    }

    @Test
    fun `la base rechaza la misma placa abierta en dos puestos`() = withEnv { env ->
        env.records.insert(entry(env.spot("A-01"), "A-01", "ABC123"))

        assertFailsWith<Exception> { env.records.insert(entry(env.spot("A-02"), "A-02", "ABC123")) }
    }

    @Test
    fun `una placa puede volver a entrar despues de salir`() = withEnv { env ->
        val spotId = env.spot("A-01")
        val first = env.records.insert(entry(spotId, "A-01", "ABC123"))
        env.records.close(first, RecordClosing(t11, 4_000, Tariff.defaults.first()))

        assertNotNull(env.records.insert(entry(spotId, "A-01", "ABC123")))
        assertEquals(2, env.records.getAll().size)
        assertEquals(1, env.records.getOpen().size)
    }

    @Test
    fun `el historial conserva puesto y piso aunque se eliminen`() = withEnv { env ->
        val spotId = env.spot("A-01")
        val id = env.records.insert(entry(spotId, "A-01", "ABC123"))
        env.records.close(id, RecordClosing(t11, 4_000, Tariff.defaults.first()))

        env.spots.softDelete(spotId)
        env.floors.softDelete(env.floors.getAll().first().id)

        val record = env.records.getById(id)!!
        assertEquals("A-01", record.spotLabel)
        assertEquals("Piso 1", record.floorName)
    }
}
