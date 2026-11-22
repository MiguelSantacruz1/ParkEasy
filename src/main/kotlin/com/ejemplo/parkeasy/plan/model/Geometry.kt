package com.ejemplo.parkeasy.plan.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/** Punto del plano, en metros. */
@Serializable
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
}

/** Rectángulo alineado con los ejes, en metros. */
data class Rect(val left: Double, val top: Double, val right: Double, val bottom: Double) {
    val width: Double get() = right - left
    val height: Double get() = bottom - top
    val center: Pt get() = Pt((left + right) / 2, (top + bottom) / 2)

    fun contains(p: Pt) = p.x in left..right && p.y in top..bottom
    fun intersects(o: Rect) = left <= o.right && right >= o.left && top <= o.bottom && bottom >= o.top
    fun inflate(d: Double) = Rect(left - d, top - d, right + d, bottom + d)

    companion object {
        fun of(a: Pt, b: Pt) = Rect(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
        fun ofPoints(points: List<Pt>) = Rect(
            points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y },
        )
    }
}

enum class BoxShape { RECTANGLE, ELLIPSE }

enum class PathKind { LINE, POLYLINE, POLYGON, CURVE }

/**
 * Forma de un elemento del plano. La rotación del elemento gira la forma alrededor de su centro.
 * Se guarda en JSON versionado (ver [PlanJson]).
 */
@Serializable
sealed interface Geometry

/** Rectángulo o elipse. (x, y) es la esquina superior izquierda sin rotar. */
@Serializable
@SerialName("box")
data class Box(
    val x: Double,
    val y: Double,
    val width: Double,
    val height: Double,
    val shape: BoxShape = BoxShape.RECTANGLE,
    val cornerRadius: Double = 0.0,
) : Geometry

/** Línea, polilínea, polígono o curva que pasa por todos sus puntos. */
@Serializable
@SerialName("path")
data class PathGeometry(
    val kind: PathKind,
    val points: List<Pt>,
    val cornerRadius: Double = 0.0,
) : Geometry {
    val closed: Boolean get() = kind == PathKind.POLYGON
}

fun Geometry.localBounds(): Rect = when (this) {
    is Box -> Rect(x, y, x + width, y + height)
    is PathGeometry -> Rect.ofPoints(points)
}

fun Geometry.center(): Pt = localBounds().center

fun rotatePoint(p: Pt, around: Pt, degrees: Double): Pt {
    if (degrees == 0.0) return p
    val r = Math.toRadians(degrees)
    val dx = p.x - around.x
    val dy = p.y - around.y
    return Pt(around.x + dx * cos(r) - dy * sin(r), around.y + dx * sin(r) + dy * cos(r))
}

fun distanceToSegment(p: Pt, a: Pt, b: Pt): Double {
    val abx = b.x - a.x
    val aby = b.y - a.y
    val len2 = abx * abx + aby * aby
    if (len2 == 0.0) return hypot(p.x - a.x, p.y - a.y)
    val t = (((p.x - a.x) * abx + (p.y - a.y) * aby) / len2).coerceIn(0.0, 1.0)
    return hypot(p.x - (a.x + t * abx), p.y - (a.y + t * aby))
}

fun pointInPolygon(p: Pt, polygon: List<Pt>): Boolean {
    var inside = false
    var j = polygon.lastIndex
    for (i in polygon.indices) {
        val a = polygon[i]
        val b = polygon[j]
        if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Puntos de una curva suave (Catmull-Rom) que pasa por [points]. Sirve para dibujar y para detectar clics. */
fun sampleCurve(points: List<Pt>, segmentsPerSpan: Int = 12): List<Pt> {
    if (points.size < 3) return points
    val result = ArrayList<Pt>()
    for (i in 0 until points.lastIndex) {
        val p0 = points[(i - 1).coerceAtLeast(0)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
        for (s in 0 until segmentsPerSpan) {
            val t = s / segmentsPerSpan.toDouble()
            result += Pt(catmull(p0.x, p1.x, p2.x, p3.x, t), catmull(p0.y, p1.y, p2.y, p3.y, t))
        }
    }
    result += points.last()
    return result
}

private fun catmull(p0: Double, p1: Double, p2: Double, p3: Double, t: Double): Double {
    val t2 = t * t
    val t3 = t2 * t
    return 0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3)
}

/** Largo recorrido por una línea, polilínea o curva (y perímetro de un polígono). */
fun pathLength(g: PathGeometry): Double {
    val pts = if (g.kind == PathKind.CURVE) sampleCurve(g.points) else g.points
    var total = 0.0
    for (i in 0 until pts.lastIndex) total += hypot(pts[i + 1].x - pts[i].x, pts[i + 1].y - pts[i].y)
    if (g.closed && pts.size > 2) total += hypot(pts.first().x - pts.last().x, pts.first().y - pts.last().y)
    return total
}
