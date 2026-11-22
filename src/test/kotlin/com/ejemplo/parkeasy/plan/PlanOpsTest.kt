package com.ejemplo.parkeasy.plan

import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.PlanJson
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.Rect
import com.ejemplo.parkeasy.plan.ops.Handle
import com.ejemplo.parkeasy.plan.ops.History
import com.ejemplo.parkeasy.plan.ops.ItemGeometry
import com.ejemplo.parkeasy.plan.ops.PlanOps
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlanOpsTest {
    private fun spot(id: String, x: Double, y: Double, label: String = id, rotation: Double = 0.0) = PlanItem(
        id = id, type = ItemType.SPOT, geometry = Box(x, y, 2.5, 5.0), rotation = rotation,
        label = label, spotType = SpotType.CAR,
    )

    private fun doc(vararg items: PlanItem) = PlanDocument(1, 50.0, 30.0, items.toList())

    private fun near(a: Double, b: Double) = assertTrue(kotlin.math.abs(a - b) < 1e-6, "$a != $b")

    @Test
    fun `mover desplaza elementos y respeta el candado`() {
        val d = doc(spot("a", 0.0, 0.0), spot("b", 5.0, 5.0).copy(locked = true))
        val moved = PlanOps.move(d, setOf("a", "b"), 2.0, 3.0)
        assertEquals(Box(2.0, 3.0, 2.5, 5.0), moved.item("a")!!.geometry)
        assertEquals(d.item("b"), moved.item("b"))
    }

    @Test
    fun `mover con imán deja la esquina en la cuadrícula`() {
        val d = doc(spot("a", 0.0, 0.0))
        val moved = PlanOps.move(d, setOf("a"), 1.13, 2.4, snapStep = 0.5)
        val box = moved.item("a")!!.geometry as Box
        near(1.0, box.x)
        near(2.5, box.y)
    }

    @Test
    fun `girar normaliza el ángulo`() {
        val d = doc(spot("a", 0.0, 0.0))
        assertEquals(350.0, PlanOps.rotateBy(d, setOf("a"), -10.0).item("a")!!.rotation)
        assertEquals(10.0, PlanOps.setRotation(d, "a", 370.0).item("a")!!.rotation)
    }

    @Test
    fun `redimensionar deja fijo el lado opuesto`() {
        val d = doc(spot("a", 10.0, 10.0))
        val box = PlanOps.resize(d, "a", Handle.E, 1.0, 0.0).item("a")!!.geometry as Box
        near(10.0, box.x)
        near(3.5, box.width)
        near(5.0, box.height)
        val nw = PlanOps.resize(d, "a", Handle.NW, -1.0, -2.0).item("a")!!.geometry as Box
        near(9.0, nw.x)
        near(8.0, nw.y)
        near(12.5, nw.x + nw.width)
        near(15.0, nw.y + nw.height)
    }

    @Test
    fun `redimensionar un elemento girado conserva el ancla en el plano`() {
        val item = spot("a", 10.0, 10.0, rotation = 90.0)
        val d = doc(item)
        val anchorBefore = ItemGeometry.worldPoints(item)[0] // esquina NW local
        val resized = PlanOps.resize(d, "a", Handle.SE, 0.0, 0.0).item("a")!!
        // sin movimiento no cambia nada
        assertEquals(item.geometry, resized.geometry)
        val grown = PlanOps.resize(d, "a", Handle.SE, 0.0, 2.0).item("a")!!
        val anchorAfter = ItemGeometry.worldPoints(grown)[0]
        near(anchorBefore.x, anchorAfter.x)
        near(anchorBefore.y, anchorAfter.y)
    }

    @Test
    fun `redimensionar no baja del tamaño mínimo`() {
        val d = doc(spot("a", 0.0, 0.0))
        val box = PlanOps.resize(d, "a", Handle.E, -100.0, 0.0).item("a")!!.geometry as Box
        near(PlanOps.MIN_SIZE, box.width)
    }

    @Test
    fun `tamaño exacto conserva el centro`() {
        val d = doc(spot("a", 0.0, 0.0))
        val box = PlanOps.setSize(d, "a", 4.0, 6.0).item("a")!!.geometry as Box
        near(1.25, box.x + box.width / 2)
        near(2.5, box.y + box.height / 2)
    }

    @Test
    fun `puntos de un trazo se mueven agregan y quitan`() {
        val line = PlanItem("p", ItemType.ROAD, PathGeometry(PathKind.POLYLINE, listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0))), strokeWidth = 4.0)
        val d = doc(line)
        val moved = PlanOps.movePoint(d, "p", 1, Pt(12.0, 1.0)).item("p")!!.geometry as PathGeometry
        assertEquals(Pt(12.0, 1.0), moved.points[1])
        val added = PlanOps.addPointNear(d, "p", Pt(5.0, 0.5)).item("p")!!.geometry as PathGeometry
        assertEquals(4, added.points.size)
        assertEquals(Pt(5.0, 0.5), added.points[1])
        val removed = PlanOps.removePoint(d, "p", 1).item("p")!!.geometry as PathGeometry
        assertEquals(2, removed.points.size)
        val tooFew = PlanOps.removePoint(PlanOps.removePoint(d, "p", 1), "p", 0).item("p")!!.geometry as PathGeometry
        assertEquals(2, tooFew.points.size)
    }

    @Test
    fun `un polígono conserva al menos tres puntos`() {
        val poly = PlanItem("g", ItemType.COVERED_ZONE, PathGeometry(PathKind.POLYGON, listOf(Pt(0.0, 0.0), Pt(5.0, 0.0), Pt(5.0, 5.0))))
        val g = PlanOps.removePoint(doc(poly), "g", 0).item("g")!!.geometry as PathGeometry
        assertEquals(3, g.points.size)
    }

    @Test
    fun `traer al frente y enviar al fondo cambian el orden`() {
        val d = doc(spot("a", 0.0, 0.0).copy(zOrder = 0), spot("b", 1.0, 0.0).copy(zOrder = 1), spot("c", 2.0, 0.0).copy(zOrder = 2))
        val front = PlanOps.bringToFront(d, setOf("a"))
        assertEquals(listOf("b", "c", "a"), front.renderOrder().map { it.id })
        val back = PlanOps.sendToBack(d, setOf("c"))
        assertEquals(listOf("c", "a", "b"), back.renderOrder().map { it.id })
    }

    @Test
    fun `borrar respeta candado y elementos protegidos`() {
        val d = doc(spot("a", 0.0, 0.0), spot("b", 5.0, 0.0).copy(locked = true), spot("c", 10.0, 0.0))
        val result = PlanOps.delete(d, setOf("a", "b", "c"), protected = { it.id == "c" })
        assertEquals(listOf("b", "c"), result.document.items.map { it.id })
        assertEquals(setOf("b", "c"), result.kept.map { it.id }.toSet())
    }

    @Test
    fun `duplicar da etiquetas nuevas y quita spotId`() {
        val original = spot("a", 0.0, 0.0, label = "A-01").copy(spotId = 7)
        val d = doc(original)
        var n = 0
        val (nd, copies) = PlanOps.duplicate(d, listOf(original), idGen = { "n${++n}" })
        assertEquals(2, nd.items.size)
        val copy = copies.single()
        assertNull(copy.spotId)
        assertEquals("A-02", copy.label)
        assertEquals(Box(1.0, 1.0, 2.5, 5.0), copy.geometry)
    }

    @Test
    fun `crear hilera numera los puestos y los separa`() {
        val template = spot("t", 0.0, 0.0, label = "A-01")
        var n = 0
        val (nd, row) = PlanOps.createRow(
            PlanDocument(1, 50.0, 30.0), template, count = 5, spacing = 3.0, angleDegrees = 0.0,
            idGen = { "r${++n}" },
        )
        assertEquals(5, nd.items.size)
        assertEquals(listOf("A-01", "A-02", "A-03", "A-04", "A-05"), row.map { it.label })
        assertEquals(listOf(0.0, 3.0, 6.0, 9.0, 12.0), row.map { (it.geometry as Box).x })
        assertTrue(row.all { it.spotId == null })
    }

    @Test
    fun `hilera en vertical y sin repetir etiquetas existentes`() {
        val template = spot("t", 0.0, 0.0, label = "A-01")
        var n = 0
        val (_, row) = PlanOps.createRow(
            doc(spot("x", 40.0, 0.0, label = "A-02")), template, count = 2, spacing = 6.0, angleDegrees = 90.0,
            idGen = { "r${++n}" },
        )
        assertEquals(listOf("A-01", "A-03"), row.map { it.label })
        near(6.0, (row[1].geometry as Box).y)
    }

    @Test
    fun `hilera sin elementos falla`() {
        assertFailsWith<IllegalArgumentException> {
            PlanOps.createRow(doc(), spot("t", 0.0, 0.0), 0, 3.0, 0.0)
        }
    }

    @Test
    fun `selección por clic toma el elemento de arriba`() {
        val d = doc(spot("a", 0.0, 0.0), spot("b", 1.0, 1.0))
        assertEquals("b", PlanOps.hitTest(d, Pt(2.0, 2.0), 0.0)!!.id)
        assertEquals("a", PlanOps.hitTest(d, Pt(0.5, 0.5), 0.0)!!.id)
        assertNull(PlanOps.hitTest(d, Pt(40.0, 20.0), 0.1))
    }

    @Test
    fun `clic respeta la rotación`() {
        // Barra de 10 x 1 girada 90 grados: ocupa un alto de 10 centrado en su centro.
        val bar = PlanItem("w", ItemType.WALL, Box(0.0, 0.0, 10.0, 1.0), rotation = 90.0)
        val d = doc(bar)
        assertNotNull(PlanOps.hitTest(d, Pt(5.0, 4.0), 0.0))
        assertNull(PlanOps.hitTest(d, Pt(5.0, 8.0), 0.0))
        assertNull(PlanOps.hitTest(d, Pt(9.0, 0.5), 0.0))
    }

    @Test
    fun `clic sobre una elipse y sobre un trazo`() {
        val ellipse = PlanItem("e", ItemType.COVERED_ZONE, Box(0.0, 0.0, 10.0, 10.0, BoxShape.ELLIPSE))
        val road = PlanItem("r", ItemType.ROAD, PathGeometry(PathKind.LINE, listOf(Pt(20.0, 0.0), Pt(30.0, 0.0))), strokeWidth = 4.0)
        val d = doc(ellipse, road)
        assertNull(PlanOps.hitTest(d, Pt(0.2, 0.2), 0.0)) // esquina fuera de la elipse
        assertNotNull(PlanOps.hitTest(d, Pt(5.0, 5.0), 0.0))
        assertNotNull(PlanOps.hitTest(d, Pt(25.0, 1.5), 0.0))
        assertNull(PlanOps.hitTest(d, Pt(25.0, 3.0), 0.0))
    }

    @Test
    fun `selección por recuadro`() {
        val d = doc(spot("a", 0.0, 0.0), spot("b", 20.0, 20.0))
        assertEquals(listOf("a"), PlanOps.inRect(d, Rect(-1.0, -1.0, 4.0, 6.0)).map { it.id })
    }

    @Test
    fun `zona techada marca y desmarca puestos por su centro`() {
        val zone = PlanItem("z", ItemType.COVERED_ZONE, Box(0.0, 0.0, 10.0, 10.0))
        val inside = spot("a", 1.0, 1.0)
        val outside = spot("b", 20.0, 20.0).copy(covered = true)
        val marked = PlanOps.recomputeCovered(doc(zone, inside, outside))
        assertTrue(marked.item("a")!!.covered)
        assertFalse(marked.item("b")!!.covered)
        val moved = PlanOps.recomputeCovered(PlanOps.move(marked, setOf("z"), 30.0, 0.0))
        assertFalse(moved.item("a")!!.covered)
    }

    @Test
    fun `zona techada poligonal`() {
        val zone = PlanItem("z", ItemType.COVERED_ZONE, PathGeometry(PathKind.POLYGON, listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(0.0, 10.0))))
        val a = spot("a", 0.5, 0.5) // centro (1.75, 3.0) dentro
        val b = spot("b", 6.0, 6.0) // centro fuera de la hipotenusa
        val r = PlanOps.recomputeCovered(doc(zone, a, b))
        assertTrue(r.item("a")!!.covered)
        assertFalse(r.item("b")!!.covered)
    }

    @Test
    fun `cotas muestran medidas`() {
        assertEquals("2,50 × 5,00 m".replace(',', '.'), ItemGeometry.dimensionText(spot("a", 0.0, 0.0)).replace(',', '.'))
    }

    @Test
    fun `historial deshace y rehace al menos 50 pasos`() {
        val history = History<Int>()
        var state = 0
        repeat(60) { history.record(state); state++ }
        assertEquals(60, state)
        var undone = 0
        while (true) {
            val prev = history.undo(state) ?: break
            state = prev
            undone++
        }
        assertEquals(60, undone)
        assertEquals(0, state)
        repeat(60) { state = history.redo(state)!! }
        assertEquals(60, state)
        assertFalse(history.canRedo)
    }

    @Test
    fun `un cambio nuevo borra lo que se podía rehacer y el límite se respeta`() {
        val history = History<Int>(limit = 3)
        (0..5).forEach { history.record(it) }
        assertEquals(3, history.undoSteps)
        history.undo(99)
        assertTrue(history.canRedo)
        history.record(7)
        assertFalse(history.canRedo)
    }

    @Test
    fun `json guarda y recupera cada tipo de geometría`() {
        val items = listOf(
            spot("a", 1.0, 2.0).copy(spotId = 5, covered = true),
            PlanItem("r", ItemType.ROAD, PathGeometry(PathKind.CURVE, listOf(Pt(0.0, 0.0), Pt(5.0, 5.0), Pt(10.0, 0.0))), strokeWidth = 4.0),
            PlanItem("c", ItemType.COLUMN, Box(0.0, 0.0, 0.5, 0.5, BoxShape.ELLIPSE), round = true),
            PlanItem("t", ItemType.TEXT, Box(0.0, 0.0, 3.0, 1.0), label = "Entrada norte"),
            PlanItem("m", ItemType.RAMP, PathGeometry(PathKind.LINE, listOf(Pt(0.0, 0.0), Pt(8.0, 0.0))), rampUp = true, connectsToFloorId = 2),
        )
        items.forEach { assertEquals(it, PlanJson.decode(PlanJson.encode(it))) }
    }

    @Test
    fun `json rechaza texto dañado y versiones más nuevas`() {
        assertFailsWith<IllegalArgumentException> { PlanJson.decode("no es json") }
        val future = PlanJson.encode(spot("a", 0.0, 0.0)).replace("\"version\":1", "\"version\":99")
        assertFailsWith<IllegalArgumentException> { PlanJson.decode(future) }
    }
}
