package com.ejemplo.parkeasy.presentation.plan

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.rules.PlateValidator
import com.ejemplo.parkeasy.domain.rules.SpotLabelGenerator
import com.ejemplo.parkeasy.plan.PlanPersistence
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.Geometry
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PathGeometry
import com.ejemplo.parkeasy.plan.model.PathKind
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.Pt
import com.ejemplo.parkeasy.plan.model.Rect
import com.ejemplo.parkeasy.plan.ops.Handle
import com.ejemplo.parkeasy.plan.ops.Handles
import com.ejemplo.parkeasy.plan.ops.History
import com.ejemplo.parkeasy.plan.ops.ItemGeometry
import com.ejemplo.parkeasy.plan.ops.PlanOps
import com.ejemplo.parkeasy.presentation.common.BaseViewModel
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.atan2
import kotlin.math.hypot

enum class Tool { SELECT, RECTANGLE, ELLIPSE, LINE, POLYLINE, POLYGON, CURVE }

enum class PlanMode { EDIT, OPERATION }

enum class SaveStatus { SAVED, PENDING, SAVING, ERROR }

/** Estado de un puesto en vivo, para pintarlo en el plano operativo. */
data class SpotRuntime(val status: SpotStatus, val plate: String?, val recordId: Long?)

/** Vista previa mientras se dibuja o se arrastra. */
sealed interface Preview {
    data class BoxPreview(val rect: Rect, val ellipse: Boolean) : Preview
    data class PathPreview(val points: List<Pt>, val kind: PathKind) : Preview
    data class Marquee(val rect: Rect) : Preview
}

data class Viewport(val scale: Double = 20.0, val offsetX: Double = 40.0, val offsetY: Double = 40.0) {
    fun toPlan(px: Double, py: Double) = Pt((px - offsetX) / scale, (py - offsetY) / scale)
    fun toScreenX(x: Double) = x * scale + offsetX
    fun toScreenY(y: Double) = y * scale + offsetY
}

data class PlanUiState(
    val loading: Boolean = true,
    val floors: List<Floor> = emptyList(),
    val floorId: Long? = null,
    val doc: PlanDocument? = null,
    val selection: Set<String> = emptySet(),
    val tool: Tool = Tool.SELECT,
    val drawType: ItemType = ItemType.SPOT,
    val drawSpotType: SpotType = SpotType.CAR,
    val mode: PlanMode = PlanMode.EDIT,
    val viewport: Viewport = Viewport(),
    val snap: Boolean = true,
    val showGrid: Boolean = true,
    val showDimensions: Boolean = true,
    val preview: Preview? = null,
    val saveStatus: SaveStatus = SaveStatus.SAVED,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val runtime: Map<Long, SpotRuntime> = emptyMap(),
    val query: String = "",
    val highlighted: Set<Long> = emptySet(),
    /** Pisos donde hay un vehículo que coincide con la búsqueda. */
    val matchFloorIds: Set<Long> = emptySet(),
) {
    val selectedItems: List<PlanItem> get() = doc?.items?.filter { it.id in selection }.orEmpty()
}

private sealed interface Gesture {
    class Move(val start: PlanDocument, val origin: Pt, val ids: Set<String>) : Gesture
    class Resize(val start: PlanDocument, val id: String, val handle: Handle, val origin: Pt) : Gesture
    class Rotate(val start: PlanDocument, val id: String) : Gesture
    class MovePoint(val start: PlanDocument, val id: String, val index: Int) : Gesture
    class Marquee(val origin: Pt, val base: Set<String>) : Gesture
    class DrawBox(val origin: Pt) : Gesture
    class DrawLine(val origin: Pt) : Gesture
}

/**
 * Estado y reglas del editor de planos y del plano operativo. Todo lo que dibuja la pantalla
 * sale de [state]; los eventos del puntero llegan ya en metros del plano.
 */
class PlanViewModel(
    private val floorsRepo: FloorRepository,
    spots: SpotRepository,
    records: ParkingRecordRepository,
    private val persistence: PlanPersistence,
    private val messages: MessageCenter,
    private val debounceMs: Long = 400,
) : BaseViewModel(messages) {

    private val lock = Any()
    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    private val history = History<PlanDocument>(limit = 100)
    private var gesture: Gesture? = null
    private var draft: MutableList<Pt> = mutableListOf()
    private var clipboard: List<PlanItem> = emptyList()
    private var allLabels: Map<Long, String> = emptyMap()
    private var spotFloors: Map<Long, Long> = emptyMap()
    private var version = 0L
    private var savedVersion = 0L
    private var saveJob: Job? = null
    private var viewSize: Pair<Double, Double>? = null

    init {
        scope.launch {
            floorsRepo.observeAll().collect { floors -> onFloors(floors) }
        }
        scope.launch {
            combine(spots.observeAll(), records.observeOpen()) { allSpots, open ->
                val plates = open.associate { it.spotId to it.plate }
                allLabels = allSpots.associate { it.id to it.label }
                spotFloors = allSpots.associate { it.id to it.floorId }
                allSpots.associate { it.id to SpotRuntime(it.status, plates[it.id], it.currentRecordId) }
            }.collect { runtime ->
                synchronized(lock) {
                    _state.update { it.copy(runtime = runtime) }
                    refreshSearch()
                }
            }
        }
    }

    /** Exporta el plano del piso actual como imagen o PDF, tal como se ve (editor u operación). */
    fun exportPlan(pdf: Boolean, target: java.nio.file.Path) {
        val s = _state.value
        val floor = s.floors.firstOrNull { it.id == s.floorId }?.name ?: "Plano"
        val operation = s.mode == PlanMode.OPERATION
        launchSafe("Plano exportado a ${target.fileName}") {
            if (pdf) PlanExporter.exportPdf(s, floor, operation, target) else PlanExporter.exportPng(s, floor, operation, target)
        }
    }

    // ---- Pisos ----------------------------------------------------------------------------

    private fun onFloors(floors: List<Floor>) {
        val current = _state.value.floorId
        _state.update { it.copy(floors = floors) }
        val target = if (current != null && floors.any { it.id == current }) current else floors.firstOrNull()?.id
        if (target == null) {
            synchronized(lock) { _state.update { it.copy(floorId = null, doc = null, loading = false, selection = emptySet()) } }
        } else if (target != current) {
            loadFloor(target)
        }
    }

    /** Vuelve a leer el plano de la base, por ejemplo después de restaurar una copia. */
    fun reload() {
        saveJob?.cancel()
        scope.launch { _state.value.floorId?.let { loadFloor(it) } }
    }

    fun selectFloor(floorId: Long) {
        if (floorId == _state.value.floorId) return
        scope.launch {
            persistNow()
            loadFloor(floorId)
        }
    }

    private fun loadFloor(floorId: Long) {
        val doc = persistence.load(floorId)
        synchronized(lock) {
            history.clear()
            gesture = null
            draft.clear()
            version = 0; savedVersion = 0
            _state.update {
                it.copy(
                    loading = false, floorId = floorId, doc = doc, selection = emptySet(), preview = null,
                    saveStatus = SaveStatus.SAVED, canUndo = false, canRedo = false,
                )
            }
            refreshSearch()
        }
        fitToView()
    }

    // ---- Vista ----------------------------------------------------------------------------

    fun setViewSize(width: Double, height: Double) {
        val first = viewSize == null
        viewSize = width to height
        if (first) fitToView()
    }

    fun fitToView() {
        val (w, h) = viewSize ?: return
        synchronized(lock) {
            val doc = _state.value.doc ?: return
            val scale = minOf((w - 80) / doc.width, (h - 80) / doc.height).coerceIn(MIN_SCALE, MAX_SCALE)
            _state.update { it.copy(viewport = Viewport(scale, (w - doc.width * scale) / 2, (h - doc.height * scale) / 2)) }
        }
    }

    /** Acerca o aleja manteniendo bajo el puntero el punto del plano que estaba ahí. */
    fun zoom(factor: Double, anchorX: Double, anchorY: Double): Unit = synchronized(lock) {
        _state.update {
            val v = it.viewport
            val scale = (v.scale * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            val k = scale / v.scale
            it.copy(viewport = Viewport(scale, anchorX - (anchorX - v.offsetX) * k, anchorY - (anchorY - v.offsetY) * k))
        }
    }

    fun pan(dx: Double, dy: Double): Unit = synchronized(lock) {
        _state.update { it.copy(viewport = it.viewport.copy(offsetX = it.viewport.offsetX + dx, offsetY = it.viewport.offsetY + dy)) }
    }

    fun toggleSnap() = _state.update { it.copy(snap = !it.snap) }
    fun toggleGrid() = _state.update { it.copy(showGrid = !it.showGrid) }
    fun toggleDimensions() = _state.update { it.copy(showDimensions = !it.showDimensions) }

    fun setMode(mode: PlanMode) {
        cancel()
        _state.update { it.copy(mode = mode, selection = emptySet()) }
    }

    fun setTool(tool: Tool) {
        cancel()
        _state.update { it.copy(tool = tool) }
    }

    /** Elige qué se dibuja y pasa a la herramienta de forma que mejor le queda. */
    fun setDrawType(type: ItemType) {
        cancel()
        val tool = when (type) {
            ItemType.ROAD, ItemType.WALL, ItemType.RAMP -> Tool.POLYLINE
            ItemType.COVERED_ZONE -> Tool.RECTANGLE
            else -> Tool.RECTANGLE
        }
        _state.update { it.copy(drawType = type, tool = tool) }
    }

    fun setDrawSpotType(type: SpotType) = _state.update { it.copy(drawSpotType = type) }

    // ---- Puntero --------------------------------------------------------------------------

    private val snapStep get() = if (_state.value.snap) SNAP else null
    private fun snapPt(p: Pt) = Pt(ItemGeometry.snap(p.x, snapStep), ItemGeometry.snap(p.y, snapStep))
    private fun tolerance() = 6.0 / _state.value.viewport.scale
    private fun handleRadius() = 8.0 / _state.value.viewport.scale
    private fun rotateOffset() = 26.0 / _state.value.viewport.scale

    fun pointerDown(p: Pt, shift: Boolean = false, clicks: Int = 1): Unit = synchronized(lock) {
        val s = _state.value
        val doc = s.doc ?: return
        if (s.mode != PlanMode.EDIT) return
        when (s.tool) {
            Tool.SELECT -> selectDown(doc, p, shift, clicks)
            Tool.RECTANGLE, Tool.ELLIPSE -> gesture = Gesture.DrawBox(snapPt(p))
            Tool.LINE -> gesture = Gesture.DrawLine(snapPt(p))
            Tool.POLYLINE, Tool.POLYGON, Tool.CURVE -> pathClick(p, clicks)
        }
    }

    private fun selectDown(doc: PlanDocument, p: Pt, shift: Boolean, clicks: Int) {
        val selected = _state.value.selectedItems
        val single = selected.singleOrNull()?.takeIf { !it.locked }
        if (single != null && !shift) {
            if (Handles.hitRotate(single, p, rotateOffset(), handleRadius())) {
                gesture = Gesture.Rotate(doc, single.id); return
            }
            Handles.hitResize(single, p, handleRadius())?.let { gesture = Gesture.Resize(doc, single.id, it, p); return }
            Handles.hitPoint(single, p, handleRadius())?.let { index ->
                if (clicks >= 2) commit(PlanOps.removePoint(doc, single.id, index))
                else gesture = Gesture.MovePoint(doc, single.id, index)
                return
            }
        }
        val hit = PlanOps.hitTest(doc, p, tolerance())
        if (hit == null) {
            val base = if (shift) _state.value.selection else emptySet()
            _state.update { it.copy(selection = base) }
            gesture = Gesture.Marquee(p, base)
            return
        }
        if (clicks >= 2 && hit.geometry is PathGeometry && hit.id in _state.value.selection) {
            val local = ItemGeometry.toLocal(hit, p)
            commit(PlanOps.addPointNear(doc, hit.id, local))
            return
        }
        val newSelection = when {
            shift && hit.id in _state.value.selection -> _state.value.selection - hit.id
            shift -> _state.value.selection + hit.id
            hit.id in _state.value.selection -> _state.value.selection
            else -> setOf(hit.id)
        }
        _state.update { it.copy(selection = newSelection) }
        if (hit.id in newSelection) gesture = Gesture.Move(doc, p, newSelection)
    }

    fun pointerMove(p: Pt): Unit = synchronized(lock) {
        // Sin botón presionado: solo importa para mostrar el próximo tramo de un trazo.
        if (draft.isNotEmpty()) updatePathPreview(snapPt(p))
    }

    fun pointerDrag(p: Pt): Unit = synchronized(lock) {
        val g = gesture ?: return
        val doc = _state.value.doc ?: return
        when (g) {
            is Gesture.Move -> setTransient(PlanOps.move(g.start, g.ids, p.x - g.origin.x, p.y - g.origin.y, snapStep))
            is Gesture.Resize -> setTransient(PlanOps.resize(g.start, g.id, g.handle, p.x - g.origin.x, p.y - g.origin.y, snapStep))
            is Gesture.Rotate -> {
                val item = g.start.item(g.id) ?: return
                val c = ItemGeometry.center(item)
                var deg = Math.toDegrees(atan2(p.y - c.y, p.x - c.x)) + 90.0
                if (_state.value.snap) deg = Math.round(deg / ROTATE_STEP) * ROTATE_STEP
                setTransient(PlanOps.setRotation(g.start, g.id, deg))
            }
            is Gesture.MovePoint -> {
                val item = g.start.item(g.id) ?: return
                setTransient(PlanOps.movePoint(g.start, g.id, g.index, snapPt(ItemGeometry.toLocal(item, p))))
            }
            is Gesture.Marquee -> {
                val rect = Rect.of(g.origin, p)
                val inside = PlanOps.inRect(doc, rect).map { it.id }.toSet()
                _state.update { it.copy(preview = Preview.Marquee(rect), selection = g.base + inside) }
            }
            is Gesture.DrawBox -> {
                val s = _state.value
                _state.update { it.copy(preview = Preview.BoxPreview(Rect.of(g.origin, snapPt(p)), s.tool == Tool.ELLIPSE)) }
            }
            is Gesture.DrawLine -> {
                _state.update { it.copy(preview = Preview.PathPreview(listOf(g.origin, snapPt(p)), PathKind.LINE)) }
            }
        }
    }

    fun pointerUp(p: Pt): Unit = synchronized(lock) {
        val g = gesture ?: return
        gesture = null
        when (g) {
            is Gesture.Move -> endTransient(g.start)
            is Gesture.Resize -> endTransient(g.start)
            is Gesture.Rotate -> endTransient(g.start)
            is Gesture.MovePoint -> endTransient(g.start)
            is Gesture.Marquee -> _state.update { it.copy(preview = null) }
            is Gesture.DrawBox -> {
                val end = snapPt(p)
                val rect = Rect.of(g.origin, end)
                _state.update { it.copy(preview = null) }
                val ellipse = _state.value.tool == Tool.ELLIPSE
                if (rect.width < MIN_DRAW && rect.height < MIN_DRAW) placeDefault(g.origin, ellipse)
                else createItem(Box(rect.left, rect.top, rect.width, rect.height, if (ellipse) BoxShape.ELLIPSE else BoxShape.RECTANGLE))
            }
            is Gesture.DrawLine -> {
                val end = snapPt(p)
                _state.update { it.copy(preview = null) }
                if (hypot(end.x - g.origin.x, end.y - g.origin.y) >= MIN_DRAW) {
                    createItem(PathGeometry(PathKind.LINE, listOf(g.origin, end)))
                }
            }
        }
    }

    /** Cambio provisional mientras se arrastra: no entra al historial ni se guarda todavía. */
    private fun setTransient(doc: PlanDocument) = _state.update { it.copy(doc = doc) }

    private fun endTransient(start: PlanDocument) {
        val current = _state.value.doc ?: return
        if (current == start) return
        val finalDoc = PlanOps.recomputeCovered(current)
        _state.update { it.copy(doc = start) } // para que commit registre el estado de antes
        commit(finalDoc)
    }

    // ---- Dibujo de trazos -----------------------------------------------------------------

    private fun pathClick(p: Pt, clicks: Int) {
        val point = snapPt(p)
        if (clicks >= 2) {
            // El segundo clic de un doble clic cae donde el primero ya puso el punto: no se repite.
            if (draft.isEmpty() || draft.last().let { hypot(it.x - point.x, it.y - point.y) > 1e-6 }) draft += point
            finishDraft()
            return
        }
        draft += point
        updatePathPreview(point)
    }

    private fun updatePathPreview(cursor: Pt) {
        val kind = when (_state.value.tool) {
            Tool.POLYGON -> PathKind.POLYGON
            Tool.CURVE -> PathKind.CURVE
            else -> PathKind.POLYLINE
        }
        _state.update { it.copy(preview = Preview.PathPreview(draft + cursor, kind)) }
    }

    /** Termina el trazo en curso (doble clic o Enter). Un polígono necesita 3 puntos; el resto, 2. */
    fun finishDraft(): Unit = synchronized(lock) {
        val tool = _state.value.tool
        val kind = when (tool) {
            Tool.POLYGON -> PathKind.POLYGON
            Tool.CURVE -> PathKind.CURVE
            else -> PathKind.POLYLINE
        }
        val points = draft.toList()
        draft.clear()
        _state.update { it.copy(preview = null) }
        val needed = if (kind == PathKind.POLYGON) 3 else 2
        if (points.size >= needed) createItem(PathGeometry(kind, points))
    }

    /** Cancela lo que se esté dibujando o arrastrando (Esc). */
    fun cancel(): Unit = synchronized(lock) {
        val g = gesture
        gesture = null
        draft.clear()
        val start = when (g) {
            is Gesture.Move -> g.start
            is Gesture.Resize -> g.start
            is Gesture.Rotate -> g.start
            is Gesture.MovePoint -> g.start
            else -> null
        }
        _state.update { it.copy(preview = null, doc = start ?: it.doc) }
    }

    // ---- Crear elementos ------------------------------------------------------------------

    private fun placeDefault(at: Pt, ellipse: Boolean) {
        val type = _state.value.drawType
        val (w, h) = defaultSize(type)
        createItem(Box(at.x - w / 2, at.y - h / 2, w, h, if (ellipse) BoxShape.ELLIPSE else BoxShape.RECTANGLE).let {
            it.copy(x = ItemGeometry.snap(it.x, snapStep), y = ItemGeometry.snap(it.y, snapStep))
        })
    }

    private fun defaultSize(type: ItemType): Pair<Double, Double> = when (type) {
        ItemType.SPOT -> if (_state.value.drawSpotType == SpotType.MOTORCYCLE) 1.2 to 2.5 else 2.5 to 5.0
        ItemType.COLUMN -> 0.5 to 0.5
        ItemType.STAIRS_ELEVATOR -> 3.0 to 3.0
        ItemType.ENTRANCE, ItemType.EXIT -> 4.0 to 1.0
        ItemType.TEXT -> 4.0 to 1.0
        ItemType.COVERED_ZONE -> 10.0 to 10.0
        ItemType.ROAD, ItemType.WALL, ItemType.RAMP -> 6.0 to 3.0
    }

    private fun createItem(geometry: Geometry) {
        val s = _state.value
        val doc = s.doc ?: return
        val type = s.drawType
        if (type == ItemType.SPOT && geometry !is Box) {
            messages.error("Los puestos son rectángulos o elipses"); return
        }
        val item = PlanItem(
            id = PlanOps.newId(),
            type = type,
            geometry = geometry,
            label = when (type) {
                ItemType.SPOT -> nextLabel(doc)
                ItemType.ENTRANCE -> "Entrada"
                ItemType.EXIT -> "Salida"
                ItemType.TEXT -> "Texto"
                else -> ""
            },
            strokeWidth = when (type) {
                ItemType.ROAD -> 4.0
                ItemType.WALL -> 0.3
                ItemType.RAMP -> 3.0
                else -> 0.0
            },
            round = type == ItemType.COLUMN && (geometry as? Box)?.shape == BoxShape.ELLIPSE,
            spotType = if (type == ItemType.SPOT) s.drawSpotType else null,
        )
        commit(PlanOps.add(doc, listOf(item)), selectIds = setOf(item.id))
    }

    private fun nextLabel(doc: PlanDocument): String {
        val taken = allLabels.values.toSet() + doc.items.filter { it.isSpot }.map { it.label }
        val last = doc.items.lastOrNull { it.isSpot }?.label ?: "P-01"
        return SpotLabelGenerator.generate(last, 1, taken).first()
    }

    /** Hilera de [count] elementos a partir del seleccionado (o de un puesto nuevo si no hay selección). */
    fun createRow(count: Int, spacing: Double, angleDegrees: Double, startLabel: String? = null): Unit = synchronized(lock) {
        val s = _state.value
        val doc = s.doc ?: return
        val template = s.selectedItems.singleOrNull() ?: run {
            val c = s.viewport.toPlan(0.0, 0.0)
            PlanItem(
                PlanOps.newId(), ItemType.SPOT, Box(ItemGeometry.snap(c.x + 2, snapStep), ItemGeometry.snap(c.y + 2, snapStep), 2.5, 5.0),
                label = nextLabel(doc), spotType = s.drawSpotType,
            )
        }
        try {
            val (newDoc, row) = PlanOps.createRow(
                doc, template.copy(label = startLabel?.ifBlank { null } ?: template.label), count, spacing, angleDegrees,
                takenLabels = allLabels.values.toSet(),
            )
            commit(newDoc, selectIds = row.map { it.id }.toSet())
        } catch (e: IllegalArgumentException) {
            messages.error(e.message ?: "No se pudo crear la hilera")
        }
    }

    // ---- Edición de la selección ----------------------------------------------------------

    private fun selectionIds() = _state.value.selection

    fun selectAll(): Unit = synchronized(lock) {
        _state.update { it.copy(selection = it.doc?.items?.map { i -> i.id }?.toSet().orEmpty()) }
    }

    fun clearSelection() = _state.update { it.copy(selection = emptySet()) }

    fun select(ids: Set<String>) = _state.update { it.copy(selection = ids) }

    private fun edit(change: (PlanDocument) -> PlanDocument): Unit = synchronized(lock) {
        val doc = _state.value.doc ?: return
        if (_state.value.mode != PlanMode.EDIT) return
        commit(change(doc))
    }

    fun nudge(dx: Double, dy: Double) = edit { PlanOps.move(it, selectionIds(), dx, dy) }

    fun rotateSelected(degrees: Double) = edit { PlanOps.rotateBy(it, selectionIds(), degrees) }

    fun setRotation(degrees: Double) = edit { doc -> selectionIds().fold(doc) { d, id -> PlanOps.setRotation(d, id, degrees) } }

    fun setSize(width: Double, height: Double) = edit { doc -> selectionIds().fold(doc) { d, id -> PlanOps.setSize(d, id, width, height) } }

    fun setPosition(x: Double, y: Double) = edit { doc -> selectionIds().fold(doc) { d, id -> PlanOps.setPosition(d, id, x, y) } }

    fun setCornerRadius(radius: Double) = edit { doc -> selectionIds().fold(doc) { d, id -> PlanOps.setCornerRadius(d, id, radius) } }

    fun setLocked(locked: Boolean) = edit { PlanOps.setLocked(it, selectionIds(), locked) }

    fun bringToFront() = edit { PlanOps.bringToFront(it, selectionIds()) }

    fun sendToBack() = edit { PlanOps.sendToBack(it, selectionIds()) }

    fun setStrokeWidth(width: Double) = edit { doc ->
        selectionIds().fold(doc) { d, id -> PlanOps.update(d, id) { it.copy(strokeWidth = width.coerceAtLeast(0.0)) } }
    }

    fun setText(text: String) = edit { doc ->
        selectionIds().fold(doc) { d, id -> PlanOps.update(d, id) { if (it.isSpot) it else it.copy(label = text) } }
    }

    fun setRamp(up: Boolean?, connectsToFloorId: Long?) = edit { doc ->
        selectionIds().fold(doc) { d, id ->
            PlanOps.update(d, id) { if (it.type == ItemType.RAMP) it.copy(rampUp = up, connectsToFloorId = connectsToFloorId) else it }
        }
    }

    fun setRound(round: Boolean) = edit { doc ->
        selectionIds().fold(doc) { d, id -> PlanOps.update(d, id) { if (it.type == ItemType.COLUMN) it.copy(round = round) else it } }
    }

    /** Cambia etiqueta y tipo del puesto seleccionado, con las mismas reglas que la tabla de puestos. */
    fun setSpotProperties(label: String, type: SpotType): Unit = synchronized(lock) {
        val s = _state.value
        val doc = s.doc ?: return
        val item = s.selectedItems.singleOrNull()?.takeIf { it.isSpot } ?: return
        val clean = label.trim()
        if (clean.isEmpty()) { messages.error("La etiqueta del puesto no puede estar vacía"); return }
        val duplicate = doc.items.any { it.isSpot && it.id != item.id && it.label == clean } ||
            allLabels.any { (id, l) -> l == clean && id != item.spotId }
        if (duplicate) { messages.error("Ya existe un puesto con la etiqueta $clean"); return }
        if (type != item.spotType && item.spotId?.let { s.runtime[it]?.status } == SpotStatus.OCCUPIED) {
            messages.error("El puesto ${item.label} está ocupado: no se puede cambiar su tipo"); return
        }
        commit(PlanOps.update(doc, item.id) { it.copy(label = clean, spotType = type) })
    }

    fun deleteSelected(): Unit = synchronized(lock) {
        val s = _state.value
        val doc = s.doc ?: return
        if (s.mode != PlanMode.EDIT || s.selection.isEmpty()) return
        val result = PlanOps.delete(doc, s.selection) { item ->
            item.spotId?.let { s.runtime[it]?.status == SpotStatus.OCCUPIED } == true
        }
        if (result.document == doc) {
            messages.error("No se puede borrar: ${result.kept.joinToString { it.label.ifBlank { "elemento" } }} está bloqueado u ocupado")
            return
        }
        commit(result.document)
        if (result.kept.isNotEmpty()) {
            messages.info("No se borraron ${result.kept.size} elemento(s) bloqueados u ocupados")
        }
    }

    fun copy(): Unit = synchronized(lock) { clipboard = _state.value.selectedItems }

    fun paste(): Unit = synchronized(lock) {
        val doc = _state.value.doc ?: return
        if (clipboard.isEmpty() || _state.value.mode != PlanMode.EDIT) return
        val (newDoc, copies) = PlanOps.duplicate(doc, clipboard, takenLabels = allLabels.values.toSet())
        clipboard = copies
        commit(newDoc, selectIds = copies.map { it.id }.toSet())
    }

    fun duplicate(): Unit = synchronized(lock) {
        val doc = _state.value.doc ?: return
        val items = _state.value.selectedItems
        if (items.isEmpty() || _state.value.mode != PlanMode.EDIT) return
        val (newDoc, copies) = PlanOps.duplicate(doc, items, takenLabels = allLabels.values.toSet())
        commit(newDoc, selectIds = copies.map { it.id }.toSet())
    }

    // ---- Historial ------------------------------------------------------------------------

    fun undo(): Unit = synchronized(lock) {
        val current = _state.value.doc ?: return
        val previous = history.undo(current) ?: return
        applyHistory(previous)
    }

    fun redo(): Unit = synchronized(lock) {
        val current = _state.value.doc ?: return
        val next = history.redo(current) ?: return
        applyHistory(next)
    }

    private fun applyHistory(doc: PlanDocument) {
        version++
        _state.update {
            it.copy(
                doc = doc, selection = it.selection.filter { id -> doc.item(id) != null }.toSet(),
                canUndo = history.canUndo, canRedo = history.canRedo, saveStatus = SaveStatus.PENDING,
            )
        }
        scheduleSave()
    }

    /** Aplica un cambio definitivo: lo registra para deshacer y programa el guardado. */
    private fun commit(newDoc: PlanDocument, selectIds: Set<String>? = null) {
        val s = _state.value
        val old = s.doc ?: return
        val d = PlanOps.recomputeCovered(newDoc)
        if (d == old) {
            if (selectIds != null) _state.update { it.copy(selection = selectIds) }
            return
        }
        history.record(old)
        version++
        val selection = (selectIds ?: s.selection).filter { id -> d.item(id) != null }.toSet()
        _state.update {
            it.copy(doc = d, selection = selection, canUndo = true, canRedo = false, saveStatus = SaveStatus.PENDING)
        }
        scheduleSave()
    }

    // ---- Guardado automático --------------------------------------------------------------

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = scope.launch {
            delay(debounceMs)
            persistNow()
        }
    }

    /** Guarda ya, sin esperar el retardo. Las pruebas y el cambio de piso lo usan. */
    suspend fun flush() {
        saveJob?.cancel()
        persistNow()
    }

    private suspend fun persistNow() {
        val snapshot: PlanDocument
        val savingVersion: Long
        synchronized(lock) {
            if (version == savedVersion) return
            snapshot = _state.value.doc ?: return
            savingVersion = version
            _state.update { it.copy(saveStatus = SaveStatus.SAVING) }
        }
        try {
            val result = withContext(Dispatchers.IO) { persistence.save(snapshot) }
            var reload = false
            synchronized(lock) {
                savedVersion = savingVersion
                if (result.kept.isNotEmpty()) {
                    reload = true
                } else {
                    applySpotIds(result.spotIds)
                    _state.update { it.copy(saveStatus = if (version == savingVersion) SaveStatus.SAVED else SaveStatus.PENDING) }
                }
            }
            if (reload) {
                messages.error("Puestos ocupados no se pueden quitar del plano: ${result.kept.joinToString()}")
                _state.value.floorId?.let { loadFloor(it) }
            }
        } catch (e: Exception) {
            synchronized(lock) { _state.update { it.copy(saveStatus = SaveStatus.ERROR) } }
            messages.error(if (e is DomainError) e.message ?: "No se pudo guardar el plano" else "No se pudo guardar el plano: ${e.message}")
        }
    }

    /** Copia a todos los planos (actual e historial) los ids de base que se acaban de asignar. */
    private fun applySpotIds(ids: Map<String, Long>) {
        if (ids.isEmpty()) return
        fun fix(doc: PlanDocument) = doc.copy(items = doc.items.map { item ->
            val id = ids[item.id]
            if (id != null && item.spotId != id) item.copy(spotId = id) else item
        })
        history.mapAll(::fix)
        _state.update { it.copy(doc = it.doc?.let(::fix)) }
    }

    // ---- Búsqueda de placa ----------------------------------------------------------------

    fun setQuery(text: String): Unit = synchronized(lock) {
        _state.update { it.copy(query = text) }
        refreshSearch()
    }

    private fun refreshSearch() {
        val s = _state.value
        val needle = PlateValidator.normalize(s.query)
        if (needle.isEmpty()) {
            _state.update { it.copy(highlighted = emptySet(), matchFloorIds = emptySet()) }
            return
        }
        val ids = s.runtime.filterValues { it.plate?.contains(needle) == true }.keys
        _state.update { it.copy(highlighted = ids, matchFloorIds = ids.mapNotNull { id -> spotFloors[id] }.toSet()) }
    }

    companion object {
        const val SNAP = 0.5
        const val ROTATE_STEP = 15.0
        const val MIN_SCALE = 4.0
        const val MAX_SCALE = 120.0
        private const val MIN_DRAW = 0.3
    }
}
