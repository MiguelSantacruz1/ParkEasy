package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.withEnv
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotRepositoryTest {

    private fun TestEnv.newFloor(name: String = "Piso 1") = floors.insert(NewFloor(name, 0))

    @Test
    fun `un puesto nuevo esta libre y con las medidas por defecto`() = withEnv { env ->
        val floorId = env.newFloor()
        val id = env.spots.insert(NewSpot(floorId, "A-01", SpotType.CAR))

        val spot = env.spots.getById(id)!!
        assertEquals(SpotStatus.FREE, spot.status)
        assertEquals(2.5, spot.width)
        assertEquals(5.0, spot.height)
        assertNull(spot.currentRecordId)
    }

    @Test
    fun `el puesto con registro abierto figura ocupado y al cerrarlo vuelve a libre`() = withEnv { env ->
        val floorId = env.newFloor()
        val spotId = env.spots.insert(NewSpot(floorId, "A-01", SpotType.CAR))
        val recordId = env.records.insert(
            NewParkingRecord(spotId, "A-01", "Piso 1", "ABC123", "Rojo", VehicleType.CAR, Instant.parse("2026-11-22T10:00:00Z")),
        )

        assertEquals(SpotStatus.OCCUPIED, env.spots.getById(spotId)!!.status)
        assertEquals(recordId, env.spots.getById(spotId)!!.currentRecordId)

        env.records.close(
            recordId,
            com.ejemplo.parkeasy.domain.model.RecordClosing(
                Instant.parse("2026-11-22T11:00:00Z"), 4_000, com.ejemplo.parkeasy.domain.model.Tariff.defaults.first(),
            ),
        )
        assertEquals(SpotStatus.FREE, env.spots.getById(spotId)!!.status)
    }

    @Test
    fun `bloquear guarda el motivo y desbloquear lo borra`() = withEnv { env ->
        val spotId = env.spots.insert(NewSpot(env.newFloor(), "A-01", SpotType.CAR))

        env.spots.setBlocked(spotId, true, "Mantenimiento")
        assertEquals(SpotStatus.BLOCKED, env.spots.getById(spotId)!!.status)
        assertEquals("Mantenimiento", env.spots.getById(spotId)!!.blockReason)

        env.spots.setBlocked(spotId, false, "ignorado")
        assertEquals(SpotStatus.FREE, env.spots.getById(spotId)!!.status)
        assertNull(env.spots.getById(spotId)!!.blockReason)
    }

    @Test
    fun `la base rechaza etiquetas repetidas`() = withEnv { env ->
        val floorId = env.newFloor()
        env.spots.insert(NewSpot(floorId, "A-01", SpotType.CAR))

        assertFailsWith<Exception> { env.spots.insert(NewSpot(floorId, "A-01", SpotType.MOTORCYCLE)) }
    }

    @Test
    fun `al eliminar un puesto su etiqueta se puede reutilizar`() = withEnv { env ->
        val floorId = env.newFloor()
        val id = env.spots.insert(NewSpot(floorId, "A-01", SpotType.CAR))

        env.spots.softDelete(id)

        assertNull(env.spots.findByLabel("A-01"))
        assertNotNull(env.spots.insert(NewSpot(floorId, "A-01", SpotType.CAR)))
    }

    @Test
    fun `eliminar un piso oculta todos sus puestos`() = withEnv { env ->
        val piso1 = env.newFloor("Piso 1")
        val piso2 = env.newFloor("Piso 2")
        env.spots.insert(NewSpot(piso1, "A-01", SpotType.CAR))
        env.spots.insert(NewSpot(piso1, "A-02", SpotType.CAR))
        env.spots.insert(NewSpot(piso2, "B-01", SpotType.CAR))

        env.spots.softDeleteByFloor(piso1)

        assertEquals(listOf("B-01"), env.spots.getAll().map { it.label })
        assertTrue(env.spots.getByFloor(piso1).isEmpty())
    }

    @Test
    fun `mover un puesto en el plano conserva etiqueta y tipo`() = withEnv { env ->
        val id = env.spots.insert(NewSpot(env.newFloor(), "A-01", SpotType.DISABLED))

        env.spots.update(env.spots.getById(id)!!.copy(x = 12.5, y = 7.0, rotation = 90.0, covered = true))

        val spot = env.spots.getById(id)!!
        assertEquals(12.5, spot.x)
        assertEquals(90.0, spot.rotation)
        assertTrue(spot.covered)
        assertEquals("A-01", spot.label)
        assertEquals(SpotType.DISABLED, spot.type)
    }
}
