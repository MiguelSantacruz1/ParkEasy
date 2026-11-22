package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SpotServiceTest {

    @Test
    fun `no permite etiquetas repetidas`() = withEnv { env ->
        env.floorWithSpot("A-01")
        val floorId = env.floors.getAll().first().id

        val error = assertFailsWith<DomainError.DuplicateLabel> {
            env.spotService.add(NewSpot(floorId, " A-01 ", SpotType.CAR))
        }

        assertEquals("A-01", error.label)
        assertEquals(1, env.spots.getAll().size)
    }

    @Test
    fun `la etiqueta no puede estar vacia`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")

        assertFailsWith<DomainError.BlankLabel> { env.spotService.add(NewSpot(floorId, "  ", SpotType.CAR)) }
    }

    @Test
    fun `no agrega puestos a un piso que no existe`() = withEnv { env ->
        assertFailsWith<DomainError.FloorNotFound> { env.spotService.add(NewSpot(99, "A-01", SpotType.CAR)) }
    }

    @Test
    fun `bloquea un puesto libre con motivo`() = withEnv { env ->
        val spotId = env.floorWithSpot()

        env.spotService.block(spotId, " Obstruido ")

        val spot = env.spots.getById(spotId)!!
        assertEquals(SpotStatus.BLOCKED, spot.status)
        assertEquals("Obstruido", spot.blockReason)
    }

    @Test
    fun `no bloquea un puesto ocupado`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        env.checkIn(spotId)

        assertFailsWith<DomainError.SpotOccupied> { env.spotService.block(spotId, null) }
        assertEquals(SpotStatus.OCCUPIED, env.spots.getById(spotId)!!.status)
    }

    @Test
    fun `no bloquea dos veces el mismo puesto`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        env.spotService.block(spotId, null)

        assertFailsWith<DomainError.SpotAlreadyBlocked> { env.spotService.block(spotId, null) }
    }

    @Test
    fun `solo desbloquea un puesto bloqueado`() = withEnv { env ->
        val spotId = env.floorWithSpot()

        assertFailsWith<DomainError.SpotNotBlocked> { env.spotService.unblock(spotId) }

        env.spotService.block(spotId, "Mantenimiento")
        env.spotService.unblock(spotId)
        assertEquals(SpotStatus.FREE, env.spots.getById(spotId)!!.status)
        assertNull(env.spots.getById(spotId)!!.blockReason)
    }

    @Test
    fun `no elimina un puesto ocupado`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        env.checkIn(spotId)

        val error = assertFailsWith<DomainError.CannotDeleteOccupiedSpot> { env.spotService.delete(spotId) }

        assertEquals("A-01", error.spotLabel)
        assertEquals(1, env.spots.getAll().size)
    }

    @Test
    fun `elimina un puesto libre o bloqueado y libera su etiqueta`() = withEnv { env ->
        val libre = env.floorWithSpot("A-01")
        val bloqueado = env.floorWithSpot("A-02")
        env.spotService.block(bloqueado, null)

        env.spotService.delete(libre)
        env.spotService.delete(bloqueado)

        assertEquals(0, env.spots.getAll().size)
        env.floorWithSpot("A-01")
    }

    @Test
    fun `eliminar un puesto no borra su historial`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        val recordId = env.checkIn(spotId)
        env.parkingService.checkOut(recordId, env.clock.now, 4_000, Tariff.defaults.first())

        env.spotService.delete(spotId)

        assertEquals("A-01", env.records.getById(recordId)!!.spotLabel)
    }

    @Test
    fun `altas, bloqueos y bajas quedan en la bitacora`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        env.spotService.block(spotId, null)
        env.spotService.unblock(spotId)
        env.spotService.delete(spotId)

        val actions = env.changeLog.getRecent(20).map { it.action }
        listOf(
            ChangeAction.SPOT_CREATED, ChangeAction.SPOT_BLOCKED,
            ChangeAction.SPOT_UNBLOCKED, ChangeAction.SPOT_DELETED,
        ).forEach { assertEquals(true, it in actions, "falta $it en la bitácora") }
    }
}
