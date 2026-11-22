package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class FloorServiceTest {

    @Test
    fun `los pisos nuevos van al final`() = withEnv { env ->
        env.floorService.add("Sótano 1")
        env.floorService.add("Piso 1")
        env.floorService.add("Piso 2")

        val floors = env.floors.getAll()
        assertEquals(listOf("Sótano 1", "Piso 1", "Piso 2"), floors.map { it.name })
        assertEquals(listOf(0, 1, 2), floors.map { it.sortOrder })
    }

    @Test
    fun `no elimina un piso con puestos ocupados y no toca nada`() = withEnv { env ->
        val ocupado = env.floorWithSpot("A-01")
        env.floorWithSpot("A-02")
        env.checkIn(ocupado)
        val floorId = env.floors.getAll().first().id

        val error = assertFailsWith<DomainError.FloorHasOccupiedSpots> { env.floorService.delete(floorId) }

        assertEquals(1, error.occupied)
        assertEquals(2, env.spots.getAll().size)
        assertEquals(1, env.floors.getAll().size)
    }

    @Test
    fun `elimina el piso con sus puestos libres y dice cuantos eran`() = withEnv { env ->
        env.floorWithSpot("A-01")
        env.floorWithSpot("A-02")
        env.spotService.block(env.spots.findByLabel("A-02")!!.id, null)
        val floorId = env.floors.getAll().first().id

        val removed = env.floorService.delete(floorId)

        assertEquals(2, removed)
        assertNull(env.floors.getById(floorId))
        assertEquals(0, env.spots.getAll().size)
    }

    @Test
    fun `el historial de un piso eliminado conserva su nombre`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        val recordId = env.checkIn(spotId)
        env.parkingService.checkOut(recordId, env.clock.now, 4_000, Tariff.defaults.first())

        env.floorService.delete(env.floors.getAll().first().id)

        val record = env.records.getById(recordId)!!
        assertEquals("Piso 1", record.floorName)
        assertEquals("A-01", record.spotLabel)
    }

    @Test
    fun `no elimina un piso que no existe`() = withEnv { env ->
        assertFailsWith<DomainError.FloorNotFound> { env.floorService.delete(42) }
    }
}
