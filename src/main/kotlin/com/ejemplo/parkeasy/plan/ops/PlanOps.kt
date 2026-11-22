package com.ejemplo.parkeasy.plan.ops

import com.ejemplo.parkeasy.domain.rules.SpotLabelGenerator
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.Rect
import com.ejemplo.parkeasy.plan.model.distanceToSegment
import com.ejemplo.parkeasy.plan.model.rotatePoint
import java.util.UUID
import kotlin.math.cos
import kotlin.math.sin

/** Manijas de redimensión de un rectángulo o elipse. */
enum class Handle(val dx: Int, val dy: Int) {
    NW(-1, -1), N(0, -1), NE(1, -1), E(1, 0), SE(1, 1), S(0, 1), SW(-1, 1), W(-1, 0),
}

/** Resultado de borrar: el plano nuevo y los elementos que no se pudieron borrar. */
data class DeleteResult(val document: PlanDocument, val kept: List<PlanItem>)

/**
 * Operaciones del editor sobre un [PlanDocument] inmutable. Todas devuelven un plano nuevo,
 * así deshacer y rehacer son solo guardar y recuperar planos. Los elementos con candado
 * ([PlanItem.locked]) no se mueven, giran, redimensionan ni borran.
 */
object PlanOps {
    const val MIN_SIZE = 0.2

    fun newId(): String = UUID.randomUUID().toString()

    // ---- Mover -----------------------------------------------------------------------------

    /**
     * Mueve los elementos [ids] por (dx, dy) metros. Con [snapStep] la esquina superior izquierda
     * del primer elemento queda sobre la cuadrícula y todos se mueven lo mismo.
     */
    fun move(doc: PlanDocument, ids: Set<String>, dx: Double, dy: Double, snapStep: Double? = null): PlanDocument {
        val movable = doc.items.filter { it.id in ids && !it.locked }
        if (movable.isEmpty()) return doc
        var mx = dx
        var my = dy
        if (snapStep != null) {
            val ref = ItemGeometry.worldBounds(movable.first())
            mx = ItemGeometry.snap(ref.left + dx, snapStep) - ref.left
            my = ItemGeometry.snap(ref.top + dy, snapStep) - ref.top
        }
        val movableIds = movable.map { it.id }.toSet()
        return doc.map(movableIds) { it.copy(geometry = ItemGeometry.translate(it.geometry, mx, my)) }
    }

    // ---- Girar -----------------------------------------------------------------------------

    fun rotateBy(doc: PlanDocument, ids: Set<String>, degrees: Double): PlanDocument =
        doc.map(unlocked(doc, ids)) { it.copy(rotation = normalizeAngle(it.rotation + degrees)) }

    fun setRotation(doc: PlanDocument, id: String, degrees: Double): PlanDocument =
        doc.map(unlocked(doc, setOf(id))) { it.copy(rotation = normalizeAngle(degrees)) }

    fun normalizeAngle(deg: Double): Double {
        val r = deg % 360.0
        return if (r < 0) r + 360.0 else r
    }

    // ---- Redimensionar ---------------------------------------------------------------------

    /**
     * Arrastra una manija de un rectángulo o elipse. (dx, dy) está en el plano; se pasa al marco
     * del elemento para respetar su rotación. El lado opuesto a la manija queda fijo.
     */
    fun resize(doc: PlanDocument, id: String, handle: Handle, dx: Double, dy: Double, snapStep: Double? = null): PlanDocument {
        val item = doc.item(id) ?: return doc
        val box = item.geometry as? Box ?: return doc
        if (item.locked) return doc

        val d = rotatePoint(Pt(dx, dy), Pt(0.0, 0.0), -item.rotation)
        var left = box.x
        var top = box.y
        var right = box.x + box.width
        var bottom = box.y + box.height
        if (handle.dx < 0) left += d.x else if (handle.dx > 0) right += d.x
        if (handle.dy < 0) top += d.y else if (handle.dy > 0) bottom += d.y
        if (snapStep != null) {
            if (handle.dx < 0) left = ItemGeometry.snap(left, snapStep) else if (handle.dx > 0) right = ItemGeometry.snap(right, snapStep)
            if (handle.dy < 0) top = ItemGeometry.snap(top, snapStep) else if (handle.dy > 0) bottom = ItemGeometry.snap(bottom, snapStep)
        }
        if (right - left < MIN_SIZE) if (handle.dx < 0) left = right - MIN_SIZE else right = left + MIN_SIZE
        if (bottom - top < MIN_SIZE) if (handle.dy < 0) top = bottom - MIN_SIZE else bottom = top + MIN_SIZE

        val resized = box.copy(x = left, y = top, width = right - left, height = bottom - top)
        return doc.replace(listOf(item.copy(geometry = keepAnchor(item, box, resized, handle))))
    }

    /** Mantiene fijo, en el plano, el punto opuesto a la manija aunque el elemento esté girado. */
    private fun keepAnchor(item: PlanItem, old: Box, new: Box, handle: Handle): Box {
        fun anchor(b: Box): Pt = Pt(
            if (handle.dx < 0) b.x + b.width else if (handle.dx > 0) b.x else b.x + b.width / 2,
            if (handle.dy < 0) b.y + b.height else if (handle.dy > 0) b.y else b.y + b.height / 2,
        )
        val oldCenter = Pt(old.x + old.width / 2, old.y + old.height / 2)
        val newCenter = Pt(new.x + new.width / 2, new.y + new.height / 2)
        val before = rotatePoint(anchor(old), oldCenter, item.rotation)
        val after = rotatePoint(anchor(new), newCenter, item.rotation)
        return new.copy(x = new.x + (before.x - after.x), y = new.y + (before.y - after.y))
    }

    /** Tamaño exacto desde el panel de propiedades; el centro se conserva. */
    fun setSize(doc: PlanDocument, id: String, width: Double, height: Double): PlanDocument {
        val item = doc.item(id) ?: return doc
        val box = item.geometry as? Box ?: return doc
        if (item.locked) return doc
        val w = width.coerceAtLeast(MIN_SIZE)
        val h = height.coerceAtLeast(MIN_SIZE)
        val cx = box.x + box.width / 2
        val cy = box.y + box.height / 2
        return doc.replace(listOf(item.copy(geometry = box.copy(x = cx - w / 2, y = cy - h / 2, width = w, height = h))))
    }

    /** Posición exacta (esquina superior izquierda del rectángulo o elipse). */
    fun setPosition(doc: PlanDocument, id: String, x: Double, y: Double): PlanDocument {
        val item = doc.item(id) ?: return doc
        val box = item.geometry as? Box ?: return doc
        if (item.locked) return doc
        return doc.replace(listOf(item.copy(geometry = box.copy(x = x, y = y))))
    }

    // ---- Puntos de trazos ------------------------------------------------------------------

    fun movePoint(doc: PlanDocument, id: String, index: Int, to: Pt): PlanDocument =
        editPath(doc, id) { g -> if (index in g.points.indices) g.copy(points = g.points.toMutableList().also { it[index] = to }) else g }

    /** Inserta un punto en el segmento más cercano a [near]. */
    fun addPointNear(doc: PlanDocument, id: String, near: Pt): PlanDocument = editPath(doc, id) { g ->
        if (g.kind == PathKind.LINE) return@editPath g
        val pts = g.points
        val segments = pts.indices.mapNotNull { i ->
            val j = i + 1
            when {
                j < pts.size -> i to j
                g.closed && pts.size > 2 -> i to 0
                else -> null
            }
        }
        val best = segments.minByOrNull { (a, b) -> distanceToSegment(near, pts[a], pts[b]) } ?: return@editPath g
        val updated = pts.toMutableList().apply { add(best.first + 1, near) }
        g.copy(points = updated)
    }

    /** Quita un punto. Una línea necesita 2 puntos, una polilínea o curva 2 y un polígono 3. */
    fun removePoint(doc: PlanDocument, id: String, index: Int): PlanDocument = editPath(doc, id) { g ->
        val minimum = if (g.kind == PathKind.POLYGON) 3 else 2
        if (g.kind == PathKind.LINE || g.points.size <= minimum || index !in g.points.indices) g
        else g.copy(points = g.points.filterIndexed { i, _ -> i != index })
    }

    private inline fun editPath(doc: PlanDocument, id: String, change: (PathGeometry) -> PathGeometry): PlanDocument {
        val item = doc.item(id) ?: return doc
        val path = item.geometry as? PathGeometry ?: return doc
        if (item.locked) return doc
        return doc.replace(listOf(item.copy(geometry = change(path))))
    }

    // ---- Propiedades -----------------------------------------------------------------------

    fun update(doc: PlanDocument, id: String, change: (PlanItem) -> PlanItem): PlanDocument {
        val item = doc.item(id) ?: return doc
        return doc.replace(listOf(change(item).copy(id = item.id)))
    }

    fun setLocked(doc: PlanDocument, ids: Set<String>, locked: Boolean): PlanDocument =
        doc.map(ids) { it.copy(locked = locked) }

    /** Esquinas redondeadas de rectángulos y polígonos. */
    fun setCornerRadius(doc: PlanDocument, id: String, radius: Double): PlanDocument {
        val item = doc.item(id) ?: return doc
        val r = radius.coerceAtLeast(0.0)
        val g = when (val geo = item.geometry) {
            is Box -> geo.copy(cornerRadius = r)
            is PathGeometry -> if (geo.kind == PathKind.POLYGON) geo.copy(cornerRadius = r) else geo
        }
        return doc.replace(listOf(item.copy(geometry = g)))
    }

    // ---- Apilado ---------------------------------------------------------------------------

    fun bringToFront(doc: PlanDocument, ids: Set<String>): PlanDocument {
        val top = doc.items.maxOfOrNull { it.zOrder } ?: 0
        return normalizeZ(doc.map(ids) { it.copy(zOrder = top + 1) })
    }

    fun sendToBack(doc: PlanDocument, ids: Set<String>): PlanDocument {
        val bottom = doc.items.minOfOrNull { it.zOrder } ?: 0
        return doc.map(ids) { it.copy(zOrder = bottom - 1) }.let { normalizeZ(it) }
    }

    /** Vuelve a numerar el apilado como 0, 1, 2… conservando el orden relativo (en cada capa). */
    private fun normalizeZ(doc: PlanDocument): PlanDocument {
        val ranked = doc.items.sortedWith(compareBy({ it.zOrder }))
        val z = ranked.withIndex().associate { (i, item) -> item.id to i }
        return doc.copy(items = doc.items.map { it.copy(zOrder = z.getValue(it.id)) })
    }

    // ---- Crear, copiar y borrar --------------------------------------------------------------

    fun add(doc: PlanDocument, items: List<PlanItem>): PlanDocument {
        val top = (doc.items.maxOfOrNull { it.zOrder } ?: -1) + 1
        return doc.copy(items = doc.items + items.mapIndexed { i, it -> it.copy(zOrder = top + i) })
    }

    /**
     * Borra los elementos [ids]. Los bloqueados y los que [protected] rechaza (por ejemplo, puestos
     * ocupados) se conservan y se devuelven en [DeleteResult.kept].
     */
    fun delete(doc: PlanDocument, ids: Set<String>, protected: (PlanItem) -> Boolean = { false }): DeleteResult {
        val (kept, removable) = doc.items.filter { it.id in ids }.partition { it.locked || protected(it) }
        val removeIds = removable.map { it.id }.toSet()
        return DeleteResult(doc.copy(items = doc.items.filterNot { it.id in removeIds }), kept)
    }

    /**
     * Copia los elementos desplazados [offset] metros. Los puestos copiados no tienen `spotId`
     * y reciben etiquetas nuevas que no están en [takenLabels]. Devuelve las copias ya agregadas.
     */
    fun duplicate(
        doc: PlanDocument,
        items: List<PlanItem>,
        offset: Double = 1.0,
        takenLabels: Set<String> = emptySet(),
        idGen: () -> String = ::newId,
    ): Pair<PlanDocument, List<PlanItem>> {
        val taken = (takenLabels + doc.items.filter { it.isSpot }.map { it.label }).toMutableSet()
        val copies = items.map { src ->
            var copy = src.copy(
                id = idGen(), locked = false,
                geometry = ItemGeometry.translate(src.geometry, offset, offset),
            )
            if (src.isSpot) {
                val label = SpotLabelGenerator.generate(src.label.ifBlank { "P-01" }, 1, taken).first()
                taken += label
                copy = copy.copy(label = label, spotId = null)
            }
            copy
        }
        return add(doc, copies) to copies
    }

    /**
     * Crea una hilera de [count] copias de [template] separadas [spacing] metros en la dirección
     * [angleDegrees] (0 = hacia la derecha, 90 = hacia abajo). La primera copia queda donde está
     * la plantilla. Si es un puesto, la numeración sigue desde [startLabel].
     */
    fun createRow(
        doc: PlanDocument,
        template: PlanItem,
        count: Int,
        spacing: Double,
        angleDegrees: Double,
        startLabel: String = template.label,
        takenLabels: Set<String> = emptySet(),
        idGen: () -> String = ::newId,
    ): Pair<PlanDocument, List<PlanItem>> {
        require(count >= 1) { "La hilera necesita al menos un elemento" }
        val taken = takenLabels + doc.items.filter { it.isSpot }.map { it.label }
        val labels = if (template.isSpot) SpotLabelGenerator.generate(startLabel.ifBlank { "P-01" }, count, taken) else emptyList()
        val rad = Math.toRadians(angleDegrees)
        val items = (0 until count).map { i ->
            val base = template.copy(
                id = idGen(), locked = false, spotId = null,
                geometry = ItemGeometry.translate(template.geometry, i * spacing * cos(rad), i * spacing * sin(rad)),
            )
            if (template.isSpot) base.copy(label = labels[i]) else base
        }
        return add(doc, items) to items
    }

    // ---- Selección -------------------------------------------------------------------------

    /** Elemento más alto bajo el punto [p], o null. */
    fun hitTest(doc: PlanDocument, p: Pt, tolerance: Double): PlanItem? =
        doc.renderOrder().asReversed().firstOrNull { ItemGeometry.hit(it, p, tolerance) }

    /** Elementos que tocan el recuadro de selección. */
    fun inRect(doc: PlanDocument, rect: Rect): List<PlanItem> =
        doc.items.filter { ItemGeometry.worldBounds(it).intersects(rect) }

    // ---- Zonas techadas ----------------------------------------------------------------------

    /**
     * Marca como techado todo puesto cuyo centro cae dentro de una zona techada, y desmarca
     * los demás. Devuelve el plano con `covered` actualizado.
     */
    fun recomputeCovered(doc: PlanDocument): PlanDocument {
        val zones = doc.items.filter { it.type == ItemType.COVERED_ZONE }
        return doc.copy(
            items = doc.items.map { item ->
                if (!item.isSpot) item
                else item.copy(covered = zones.any { ItemGeometry.zoneContainsCenter(it, item) })
            },
        )
    }

    private fun unlocked(doc: PlanDocument, ids: Set<String>): Set<String> =
        doc.items.filter { it.id in ids && !it.locked }.map { it.id }.toSet()
}
