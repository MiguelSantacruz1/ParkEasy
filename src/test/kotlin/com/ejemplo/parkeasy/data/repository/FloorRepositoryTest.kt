package com.ejemplo.parkeasy.data.repository

import app.cash.turbine.test
import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.withEnv
import com.ejemplo.parkeasy.domain.model.NewFloor
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FloorRepositoryTest {

    @Test
    fun `lista los pisos por orden`() = withEnv { env ->
        env.floors.insert(NewFloor("Piso 2", sortOrder = 2))
        env.floors.insert(NewFloor("Sótano 1", sortOrder = 0))
        env.floors.insert(NewFloor("Piso 1", sortOrder = 1))

        assertEquals(listOf("Sótano 1", "Piso 1", "Piso 2"), env.floors.getAll().map { it.name })
    }

    @Test
    fun `guarda y lee el tamano del lienzo`() = withEnv { env ->
        val id = env.floors.insert(NewFloor("Piso 1", 0, canvasWidth = 80.0, canvasHeight = 50.0))

        val floor = env.floors.getById(id)!!
        assertEquals(80.0, floor.canvasWidth)
        assertEquals(50.0, floor.canvasHeight)
    }

    @Test
    fun `renombrar y reordenar actualiza el piso`() = withEnv { env ->
        val id = env.floors.insert(NewFloor("Piso 1", 0))

        env.floors.update(env.floors.getById(id)!!.copy(name = "Planta baja", sortOrder = 5))

        val floor = env.floors.getById(id)!!
        assertEquals("Planta baja", floor.name)
        assertEquals(5, floor.sortOrder)
    }

    @Test
    fun `el borrado es logico y el piso deja de aparecer`() = withEnv { env ->
        val id = env.floors.insert(NewFloor("Piso 1", 0))

        env.floors.softDelete(id)

        assertNull(env.floors.getById(id))
        assertEquals(emptyList(), env.floors.getAll())
    }

    @Test
    fun `el flujo emite de nuevo cuando se agrega un piso`() = runTest {
        TestEnv().use { env ->
            env.floors.observeAll().test {
                assertEquals(0, awaitItem().size)
                env.floors.insert(NewFloor("Piso 1", 0))
                assertEquals(1, awaitItem().size)
                cancelAndIgnoreRemainingEvents()
            }
        }
    }
}
