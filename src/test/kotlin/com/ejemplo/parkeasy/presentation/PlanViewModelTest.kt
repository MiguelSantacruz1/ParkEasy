package com.ejemplo.parkeasy.presentation

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import com.ejemplo.parkeasy.presentation.plan.PlanMode
import com.ejemplo.parkeasy.presentation.plan.PlanViewModel
import com.ejemplo.parkeasy.presentation.plan.SaveStatus
import com.ejemplo.parkeasy.presentation.plan.Tool
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlanViewModelTest {

    private fun withVm(block: suspend (TestEnv, PlanViewModel, MessageCenter) -> Unit) {
        TestEnv().use { env ->
            env.floorService.add("Piso 1")
            val messages = MessageCenter()
            val vm = PlanViewModel(env.floors, env.spots, env.records, env.planPersistence, messages, debounceMs = 5)
            runBlocking {
                waitFor { vm.state.value.doc != null }
                vm.toggleSnap() // sin imán: las posiciones salen exactas
                block(env, vm, messages)
            }
        }
    }

    private suspend fun waitFor(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Tiempo agotado esperando la condición" }
            kotlinx.coroutines.delay(10)
        }
    }

    private fun PlanViewModel.click(x: Double, y: Double) {
        pointerDown(Pt(x, y)); pointerUp(Pt(x, y))
    }

    private fun PlanViewModel.drag(x1: Double, y1: Double, x2: Double, y2: Double) {
        pointerDown(Pt(x1, y1)); pointerDrag(Pt((x1 + x2) / 2, (y1 + y2) / 2)); pointerDrag(Pt(x2, y2)); pointerUp(Pt(x2, y2))
    }

    private val PlanViewModel.items get() = state.value.doc!!.items

    @Test
    fun `carga el primer piso con un plano vacío`() = runBlocking {
        withVm { _, vm, _ ->
            assertNotNull(vm.state.value.floorId)
            assertTrue(vm.items.isEmpty())
            assertEquals(SaveStatus.SAVED, vm.state.value.saveStatus)
        }
    }

    @Test
    fun `un clic con la herramienta de puesto crea un puesto con etiqueta y se guarda`() = runBlocking {
        withVm { env, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(10.0, 10.0)
            val spot = vm.items.single()
            assertEquals("P-01", spot.label)
            assertEquals(spot.id, vm.state.value.selection.single())
            assertEquals(Box(8.75, 7.5, 2.5, 5.0), spot.geometry)

            vm.flush()
            assertEquals(1, env.spots.getAll().size)
            assertEquals(SaveStatus.SAVED, vm.state.value.saveStatus)
            assertNotNull(vm.items.single().spotId)
        }
    }

    @Test
    fun `arrastrar con la herramienta de rectángulo dibuja con ese tamaño`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.COVERED_ZONE)
            vm.drag(5.0, 5.0, 15.0, 11.0)
            assertEquals(Box(5.0, 5.0, 10.0, 6.0), vm.items.single().geometry)
        }
    }

    @Test
    fun `la vía se dibuja con clics y doble clic`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.ROAD)
            vm.pointerDown(Pt(2.0, 2.0)); vm.pointerUp(Pt(2.0, 2.0))
            vm.pointerDown(Pt(20.0, 2.0)); vm.pointerUp(Pt(20.0, 2.0))
            vm.pointerDown(Pt(20.0, 12.0), clicks = 1); vm.pointerUp(Pt(20.0, 12.0))
            vm.pointerDown(Pt(20.0, 12.0), clicks = 2); vm.pointerUp(Pt(20.0, 12.0))
            val road = vm.items.single()
            assertEquals(ItemType.ROAD, road.type)
            assertEquals(3, (road.geometry as PathGeometry).points.size)
            assertEquals(4.0, road.strokeWidth)
        }
    }

    @Test
    fun `un polígono con menos de tres puntos se descarta`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.COVERED_ZONE)
            vm.setTool(Tool.POLYGON)
            vm.click(2.0, 2.0); vm.click(10.0, 2.0)
            vm.finishDraft()
            assertTrue(vm.items.isEmpty())
        }
    }

    @Test
    fun `mover con la herramienta de selección actualiza el plano y se puede deshacer`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(10.0, 10.0)
            vm.setTool(Tool.SELECT)
            vm.drag(10.0, 10.0, 14.0, 13.0)
            assertEquals(Box(12.75, 10.5, 2.5, 5.0), vm.items.single().geometry)
            vm.undo()
            assertEquals(Box(8.75, 7.5, 2.5, 5.0), vm.items.single().geometry)
            vm.redo()
            assertEquals(Box(12.75, 10.5, 2.5, 5.0), vm.items.single().geometry)
        }
    }

    @Test
    fun `deshace y rehace más de 50 pasos`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.COLUMN)
            repeat(60) { vm.click(1.0 + it, 1.0) }
            assertEquals(60, vm.items.size)
            repeat(60) { vm.undo() }
            assertTrue(vm.items.isEmpty())
            assertFalse(vm.state.value.canUndo)
            repeat(60) { vm.redo() }
            assertEquals(60, vm.items.size)
        }
    }

    @Test
    fun `esc durante un arrastre devuelve el elemento a su lugar`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(10.0, 10.0)
            val before = vm.items.single().geometry
            vm.setTool(Tool.SELECT)
            vm.pointerDown(Pt(10.0, 10.0)); vm.pointerDrag(Pt(20.0, 20.0))
            vm.cancel()
            assertEquals(before, vm.items.single().geometry)
            assertEquals(1, vm.items.size)
        }
    }

    @Test
    fun `una hilera crea puestos consecutivos y la selecciona`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            vm.createRow(count = 4, spacing = 3.0, angleDegrees = 0.0, startLabel = "A-01")
            // la hilera parte de la plantilla seleccionada y la duplica 4 veces
            assertEquals(5, vm.items.size)
            assertEquals(4, vm.state.value.selection.size)
            assertEquals(listOf("A-01", "A-02", "A-03", "A-04"), vm.state.value.selectedItems.map { it.label })
        }
    }

    @Test
    fun `un puesto dentro de una zona techada queda techado`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.COVERED_ZONE)
            vm.drag(0.0, 0.0, 20.0, 20.0)
            vm.setDrawType(ItemType.SPOT)
            vm.click(10.0, 10.0)
            assertTrue(vm.items.single { it.isSpot }.covered)
            vm.setTool(Tool.SELECT)
            vm.drag(10.0, 10.0, 40.0, 25.0)
            assertFalse(vm.items.single { it.isSpot }.covered)
        }
    }

    @Test
    fun `borrar respeta candados y puestos ocupados`() = runBlocking {
        withVm { env, vm, messages ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            vm.flush()
            val spotId = vm.items.single().spotId!!
            env.checkIn(spotId)
            waitFor { vm.state.value.runtime[spotId]?.plate != null }

            vm.deleteSelected()
            assertEquals(1, vm.items.size)
            assertTrue(messages.current.value!!.isError)

            vm.setDrawType(ItemType.COLUMN)
            vm.click(30.0, 30.0)
            vm.setLocked(true)
            vm.deleteSelected()
            assertEquals(2, vm.items.size)
        }
    }

    @Test
    fun `no deja repetir la etiqueta de otro puesto`() = runBlocking {
        withVm { _, vm, messages ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            val first = vm.items.single().label
            vm.click(15.0, 5.0)
            vm.setSpotProperties(first, SpotType.CAR)
            assertTrue(messages.current.value!!.isError)
            assertTrue(vm.items.map { it.label }.distinct().size == 2)
        }
    }

    @Test
    fun `copiar y pegar crea copias con etiquetas nuevas`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            vm.copy()
            vm.paste()
            vm.paste()
            assertEquals(3, vm.items.size)
            assertEquals(3, vm.items.map { it.label }.distinct().size)
        }
    }

    @Test
    fun `la búsqueda de placa resalta el puesto`() = runBlocking {
        withVm { env, vm, _ ->
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            vm.flush()
            val spotId = vm.items.single().spotId!!
            env.checkIn(spotId, plate = "XYZ987")
            waitFor { vm.state.value.runtime[spotId]?.plate == "XYZ987" }
            vm.setQuery("xyz-98")
            assertEquals(setOf(spotId), vm.state.value.highlighted)
            vm.setQuery("")
            assertTrue(vm.state.value.highlighted.isEmpty())
        }
    }

    @Test
    fun `en modo operación no se edita`() = runBlocking {
        withVm { _, vm, _ ->
            vm.setMode(PlanMode.OPERATION)
            vm.setDrawType(ItemType.SPOT)
            vm.click(5.0, 5.0)
            assertTrue(vm.items.isEmpty())
        }
    }

    @Test
    fun `el zoom conserva el punto bajo el puntero`() = runBlocking {
        withVm { _, vm, _ ->
            val before = vm.state.value.viewport.toPlan(300.0, 200.0)
            vm.zoom(1.5, 300.0, 200.0)
            val after = vm.state.value.viewport.toPlan(300.0, 200.0)
            assertEquals(before.x, after.x, 1e-9)
            assertEquals(before.y, after.y, 1e-9)
        }
    }
}
