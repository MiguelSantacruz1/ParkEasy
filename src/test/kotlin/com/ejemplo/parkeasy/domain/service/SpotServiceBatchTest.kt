package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SpotServiceBatchTest {

    @Test
    fun `agrega varios puestos con numeracion consecutiva`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 2")

        val ids = env.spotService.addMany(floorId, SpotType.CAR, "A-01", 20)

        assertEquals(20, ids.size)
        val labels = env.spots.getByFloor(floorId).map { it.label }
        assertEquals("A-01", labels.first())
        assertEquals("A-20", labels.last())
        assertTrue(env.spots.getByFloor(floorId).all { it.status == SpotStatus.FREE })
    }

    @Test
    fun `los puestos nuevos quedan en una cuadricula sin encimarse`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        env.spotService.addMany(floorId, SpotType.CAR, "A-01", 25)

        val spots = env.spots.getByFloor(floorId)

        assertEquals(25, spots.map { it.x to it.y }.toSet().size)
        assertEquals(3, spots.map { it.y }.toSet().size)
    }

    @Test
    fun `una segunda tanda sigue debajo de la primera`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        env.spotService.addMany(floorId, SpotType.CAR, "A-01", 5)
        val firstBottom = env.spots.getByFloor(floorId).maxOf { it.y + it.height }

        env.spotService.addMany(floorId, SpotType.CAR, "B-01", 5)

        assertTrue(env.spots.findByLabel("B-01")!!.y >= firstBottom)
    }

    @Test
    fun `salta etiquetas que ya existen en cualquier piso`() = withEnv { env ->
        env.floorWithSpot("A-02")
        val floorId = env.floors.getAll().first().id

        env.spotService.addMany(floorId, SpotType.CAR, "A-01", 3)

        assertEquals(listOf("A-01", "A-02", "A-03", "A-04"), env.spots.getAll().map { it.label })
    }

    @Test
    fun `las motos tienen un puesto mas pequeno`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        env.spotService.addMany(floorId, SpotType.MOTORCYCLE, "M-01", 1)

        assertEquals(1.2, env.spots.findByLabel("M-01")!!.width)
    }

    @Test
    fun `la cantidad debe estar entre 1 y 500`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")

        assertFailsWith<DomainError.InvalidCount> { env.spotService.addMany(floorId, SpotType.CAR, "A-01", 0) }
        assertFailsWith<DomainError.InvalidCount> { env.spotService.addMany(floorId, SpotType.CAR, "A-01", 501) }
        env.spotService.addMany(floorId, SpotType.CAR, "A-01", 500)
        assertEquals(500, env.spots.getAll().size)
    }

    @Test
    fun `editar cambia etiqueta, tipo y piso de un puesto libre`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        val piso2 = env.floorService.add("Piso 2")

        env.spotService.update(spotId, "B-07", SpotType.DISABLED, piso2)

        val spot = env.spots.getById(spotId)!!
        assertEquals("B-07", spot.label)
        assertEquals(SpotType.DISABLED, spot.type)
        assertEquals(piso2, spot.floorId)
    }

    @Test
    fun `editar no permite una etiqueta usada por otro puesto`() = withEnv { env ->
        val a = env.floorWithSpot("A-01")
        env.floorWithSpot("A-02")
        val floorId = env.floors.getAll().first().id

        assertFailsWith<DomainError.DuplicateLabel> { env.spotService.update(a, "A-02", SpotType.CAR, floorId) }
        env.spotService.update(a, "A-01", SpotType.OTHER, floorId)
    }

    @Test
    fun `un puesto ocupado puede cambiar de etiqueta pero no de tipo ni de piso`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        env.checkIn(spotId)
        val floorId = env.floors.getAll().first().id
        val piso2 = env.floorService.add("Piso 2")

        env.spotService.update(spotId, "A-99", SpotType.CAR, floorId)
        assertFailsWith<DomainError.SpotOccupied> { env.spotService.update(spotId, "A-99", SpotType.MOTORCYCLE, floorId) }
        assertFailsWith<DomainError.SpotOccupied> { env.spotService.update(spotId, "A-99", SpotType.CAR, piso2) }
    }

    @Test
    fun `se puede deshacer un borrado durante la sesion`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        val deleted = env.spotService.delete(spotId)

        env.spotService.undoDelete(deleted)

        assertEquals("A-01", env.spots.getById(spotId)?.label)
    }

    @Test
    fun `no se deshace el borrado si otra persona ya uso la etiqueta`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        val deleted = env.spotService.delete(spotId)
        env.floorWithSpot("A-01")

        assertFailsWith<DomainError.DuplicateLabel> { env.spotService.undoDelete(deleted) }
    }
}
