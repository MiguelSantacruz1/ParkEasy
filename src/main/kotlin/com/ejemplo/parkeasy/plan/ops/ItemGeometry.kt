package com.ejemplo.parkeasy.plan.ops

import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.Geometry
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.Rect
import com.ejemplo.parkeasy.plan.model.center
import com.ejemplo.parkeasy.plan.model.distanceToSegment
import com.ejemplo.parkeasy.plan.model.localBounds
import com.ejemplo.parkeasy.plan.model.pathLength
import com.ejemplo.parkeasy.plan.model.pointInPolygon
import com.ejemplo.parkeasy.plan.model.rotatePoint
import com.ejemplo.parkeasy.plan.model.sampleCurve
import kotlin.math.abs
import kotlin.math.round

/** Consultas sobre la forma de un elemento: límites, esquinas, clics y cotas. */
object ItemGeometry {

    fun center(item: PlanItem): Pt = item.geometry.center()

    /** Esquinas (rectángulos y elipses) o puntos (trazos) ya rotados, en coordenadas del plano. */
    fun worldPoints(item: PlanItem): List<Pt> {
        val c = center(item)
        val local = when (val g = item.geometry) {
            is Box -> listOf(Pt(g.x, g.y), Pt(g.x + g.width, g.y), Pt(g.x + g.width, g.y + g.height), Pt(g.x, g.y + g.height))
            is PathGeometry -> g.points
        }
        return local.map { rotatePoint(it, c, item.rotation) }
    }

    /** Rectángulo mínimo, alineado con los ejes, que contiene al elemento ya rotado. */
    fun worldBounds(item: PlanItem): Rect {
        val base = Rect.ofPoints(worldPoints(item))
        val pad = item.strokeWidth / 2
        return if (pad > 0 && item.geometry is PathGeometry) base.inflate(pad) else base
    }

    /** Convierte un punto del plano al sistema local del elemento (sin rotación). */
    fun toLocal(item: PlanItem, p: Pt): Pt = rotatePoint(p, center(item), -item.rotation)

    fun hit(item: PlanItem, p: Pt, tolerance: Double): Boolean {
        val local = toLocal(item, p)
        return when (val g = item.geometry) {
            is Box -> when (g.shape) {
                BoxShape.RECTANGLE -> g.localBounds().inflate(tolerance).contains(local)
                BoxShape.ELLIPSE -> {
                    val rx = g.width / 2 + tolerance
                    val ry = g.height / 2 + tolerance
                    val c = g.center()
                    val dx = (local.x - c.x) / rx
                    val dy = (local.y - c.y) / ry
                    dx * dx + dy * dy <= 1.0
                }
            }
            is PathGeometry -> {
                val reach = maxOf(tolerance, item.strokeWidth / 2)
                val pts = if (g.kind == PathKind.CURVE) sampleCurve(g.points) else g.points
                if (g.kind == PathKind.POLYGON && pointInPolygon(local, pts)) return true
                val closing = g.kind == PathKind.POLYGON && pts.size > 2
                val segments = pts.zipWithNext() + if (closing) listOf(pts.last() to pts.first()) else emptyList()
                segments.any { (a, b) -> distanceToSegment(local, a, b) <= reach }
            }
        }
    }

    /** Verdadero si el centro de [spot] queda dentro de la forma de [zone] (zona techada). */
    fun zoneContainsCenter(zone: PlanItem, spot: PlanItem): Boolean {
        val local = toLocal(zone, center(spot))
        return when (val g = zone.geometry) {
            is Box -> when (g.shape) {
                BoxShape.RECTANGLE -> g.localBounds().contains(local)
                BoxShape.ELLIPSE -> {
                    val c = g.center()
                    val dx = (local.x - c.x) / (g.width / 2)
                    val dy = (local.y - c.y) / (g.height / 2)
                    dx * dx + dy * dy <= 1.0
                }
            }
            is PathGeometry -> {
                val pts = if (g.kind == PathKind.CURVE) sampleCurve(g.points) else g.points
                g.kind == PathKind.POLYGON && pointInPolygon(local, pts)
            }
        }
    }
    /** Texto de las cotas: ancho × alto para formas con caja, largo para trazos. */
    fun dimensionText(item: PlanItem): String = when (val g = item.geometry) {
        is Box -> "%.2f × %.2f m".format(g.width, g.height)
        is PathGeometry -> "%.2f m".format(pathLength(g))
    }

    fun snap(value: Double, step: Double?): Double =
        if (step == null || step <= 0.0) value else round(value / step) * step

    fun nearlyEqual(a: Double, b: Double, eps: Double = 1e-9) = abs(a - b) <= eps

    fun translate(g: Geometry, dx: Double, dy: Double): Geometry = when (g) {
        is Box -> g.copy(x = g.x + dx, y = g.y + dy)
        is PathGeometry -> g.copy(points = g.points.map { Pt(it.x + dx, it.y + dy) })
    }
}
