package com.ejemplo.parkeasy.plan

import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.ops.PlanOps
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanPersistenceTest {
    private fun newSpot(id: String, label: String, x: Double = 0.0) = PlanItem(
        id = id, type = ItemType.SPOT, geometry = Box(x, 0.0, 2.5, 5.0), label = label, spotType = SpotType.CAR,
    )

    private val road = PlanItem(
        "road-1", ItemType.ROAD, PathGeometry(PathKind.POLYLINE, listOf(Pt(0.0, 8.0), Pt(20.0, 8.0))), strokeWidth = 4.0,
    )

    @Test
    fun `un piso nuevo carga un plano vacío del tamaño del piso`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        val doc = env.planPersistence.load(floorId)!!
        assertTrue(doc.items.isEmpty())
        assertEquals(env.floors.getById(floorId)!!.canvasWidth, doc.width)
        assertNull(env.planPersistence.load(999))
    }

    @Test
    fun `guardar crea puestos y elementos y cargar los devuelve`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        val doc = env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("n1", "A-01"), newSpot("n2", "A-02", 3.0), road))
        val result = env.planPersistence.save(doc)

        assertEquals(setOf("n1", "n2"), result.spotIds.keys)
        assertEquals(2, env.spots.getByFloor(floorId).size)
        val loaded = env.planPersistence.load(floorId)!!
        assertEquals(3, loaded.items.size)
        assertEquals(road, loaded.item("road-1"))
        assertEquals(listOf("A-01", "A-02"), loaded.items.filter { it.isSpot }.map { it.label }.sorted())
    }

    @Test
    fun `mover y renombrar un puesto actualiza la base sin duplicarlo`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        val first = env.planPersistence.save(env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("n1", "A-01"))))
        val spotId = first.spotIds.getValue("n1")

        val doc = env.planPersistence.load(floorId)!!
        val item = doc.items.single()
        val changed = PlanOps.update(PlanOps.move(doc, setOf(item.id), 4.0, 2.0), item.id) { it.copy(label = "B-07", rotation = 90.0) }
        env.planPersistence.save(changed)

        val spot = env.spots.getById(spotId)!!
        assertEquals("B-07", spot.label)
        assertEquals(4.0, spot.x)
        assertEquals(90.0, spot.rotation)
        assertEquals(1, env.spots.getByFloor(floorId).size)
    }

    @Test
    fun `quitar un puesto del plano lo elimina y deshacer lo restaura con el mismo id`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        val withSpot = env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("n1", "A-01")))
        val spotId = env.planPersistence.save(withSpot).spotIds.getValue("n1")
        val saved = env.planPersistence.load(floorId)!!

        env.planPersistence.save(saved.copy(items = emptyList()))
        assertNull(env.spots.getById(spotId))

        env.planPersistence.save(saved)
        assertNotNull(env.spots.getById(spotId))
        assertEquals(1, env.spots.getByFloor(floorId).size)
    }

    @Test
    fun `una etiqueta liberada se puede reutilizar en el mismo guardado`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        env.planPersistence.save(env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("n1", "A-01"))))
        env.planPersistence.save(env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("otro", "A-01"))))
        assertEquals(1, env.spots.getByFloor(floorId).size)
    }

    @Test
    fun `un puesto ocupado no se borra al quitarlo del plano`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        env.checkIn(spotId)
        val floorId = env.floors.getAll().first().id
        val doc = env.planPersistence.load(floorId)!!

        val result = env.planPersistence.save(doc.copy(items = emptyList()))
        assertEquals(listOf("A-01"), result.kept)
        assertNotNull(env.spots.getById(spotId))
    }

    @Test
    fun `un puesto ocupado se puede mover en el plano`() = withEnv { env ->
        val spotId = env.floorWithSpot("A-01")
        env.checkIn(spotId)
        val floorId = env.floors.getAll().first().id
        val doc = env.planPersistence.load(floorId)!!
        val before = env.spots.getById(spotId)!!.x
        env.planPersistence.save(PlanOps.move(doc, setOf(doc.items.single().id), 10.0, 0.0))
        assertEquals(before + 10.0, env.spots.getById(spotId)!!.x)
        assertTrue(env.spots.getById(spotId)!!.currentRecordId != null)
    }

    @Test
    fun `si algo falla no se guarda nada`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        // Dos puestos nuevos con la misma etiqueta: el segundo falla y el primero se deshace.
        val doc = env.planPersistence.load(floorId)!!.copy(items = listOf(newSpot("n1", "A-01"), newSpot("n2", "A-01", 3.0), road))
        runCatching { env.planPersistence.save(doc) }.also { assertTrue(it.isFailure) }
        assertTrue(env.spots.getByFloor(floorId).isEmpty())
        assertTrue(env.planElements.getByFloor(floorId).isEmpty())
    }

    @Test
    fun `los elementos del plano ilegibles se ignoran al cargar`() = withEnv { env ->
        val floorId = env.floorService.add("Piso 1")
        env.planElements.replaceForFloor(
            floorId,
            listOf(
                com.ejemplo.parkeasy.domain.repository.PlanElementRow("x", "ROAD", "basura", 0),
                com.ejemplo.parkeasy.domain.repository.PlanElementRow("road-1", "ROAD", com.ejemplo.parkeasy.plan.model.PlanJson.encode(road), 1),
            ),
        )
        assertEquals(listOf("road-1"), env.planPersistence.load(floorId)!!.items.map { it.id })
    }
}
