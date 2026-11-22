package com.ejemplo.parkeasy.plan.ops

import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.rotatePoint
import kotlin.math.hypot

/** Posición de las manijas de un elemento seleccionado, ya rotadas, en coordenadas del plano. */
object Handles {

    fun resizePositions(item: PlanItem): Map<Handle, Pt> {
        val box = item.geometry as? Box ?: return emptyMap()
        val center = ItemGeometry.center(item)
        val l = box.x
        val r = box.x + box.width
        val t = box.y
        val b = box.y + box.height
        val mx = box.x + box.width / 2
        val my = box.y + box.height / 2
        val local = mapOf(
            Handle.NW to Pt(l, t), Handle.N to Pt(mx, t), Handle.NE to Pt(r, t), Handle.E to Pt(r, my),
            Handle.SE to Pt(r, b), Handle.S to Pt(mx, b), Handle.SW to Pt(l, b), Handle.W to Pt(l, my),
        )
        return local.mapValues { rotatePoint(it.value, center, item.rotation) }
    }

    /** Manija de giro: queda [offset] metros por encima del borde superior. */
    fun rotatePosition(item: PlanItem, offset: Double): Pt {
        val bounds = item.geometry.let {
            when (it) {
                is Box -> Pt(it.x + it.width / 2, it.y - offset)
                is PathGeometry -> {
                    val top = it.points.minOf { p -> p.y }
                    val cx = (it.points.minOf { p -> p.x } + it.points.maxOf { p -> p.x }) / 2
                    Pt(cx, top - offset)
                }
            }
        }
        return rotatePoint(bounds, ItemGeometry.center(item), item.rotation)
    }

    /** Puntos de un trazo en el plano, ya rotados. Vacío para rectángulos y elipses. */
    fun pathPoints(item: PlanItem): List<Pt> {
        val path = item.geometry as? PathGeometry ?: return emptyList()
        val center = ItemGeometry.center(item)
        return path.points.map { rotatePoint(it, center, item.rotation) }
    }

    fun hitResize(item: PlanItem, p: Pt, radius: Double): Handle? =
        resizePositions(item).entries.firstOrNull { near(it.value, p, radius) }?.key

    fun hitRotate(item: PlanItem, p: Pt, offset: Double, radius: Double): Boolean =
        near(rotatePosition(item, offset), p, radius)

    fun hitPoint(item: PlanItem, p: Pt, radius: Double): Int? =
        pathPoints(item).indexOfFirst { near(it, p, radius) }.takeIf { it >= 0 }

    private fun near(a: Pt, b: Pt, radius: Double) = hypot(a.x - b.x, a.y - b.y) <= radius
}
