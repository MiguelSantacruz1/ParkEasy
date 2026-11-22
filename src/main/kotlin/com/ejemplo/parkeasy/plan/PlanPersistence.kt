package com.ejemplo.parkeasy.plan

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotShape
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.PlanElementRepository
import com.ejemplo.parkeasy.domain.repository.PlanElementRow
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import com.ejemplo.parkeasy.domain.service.SpotService
import com.ejemplo.parkeasy.plan.model.Box
import com.ejemplo.parkeasy.plan.model.BoxShape
import com.ejemplo.parkeasy.plan.model.ItemType
import com.ejemplo.parkeasy.plan.model.PlanDocument
import com.ejemplo.parkeasy.plan.model.PlanItem
import com.ejemplo.parkeasy.plan.model.PlanJson

/**
 * Resultado de guardar: [spotIds] relaciona el id de cada elemento puesto del plano con su id
 * en la base (los nuevos lo reciben aquí) y [kept] son los puestos ocupados que no se pudieron
 * borrar y siguen existiendo.
 */
data class SaveResult(val spotIds: Map<String, Long>, val kept: List<String>)

/**
 * Une el plano de un piso con la base. Los puestos viven en la tabla Spot (para que el resto de la
 * app los vea) y el resto de elementos, como JSON en PlanElement. Guardar es atómico.
 */
class PlanPersistence(
    private val floors: FloorRepository,
    private val spots: SpotRepository,
    private val elements: PlanElementRepository,
    private val spotService: SpotService,
    private val transactor: Transactor,
) {
    /** Arma el plano del piso, o null si el piso no existe. */
    fun load(floorId: Long): PlanDocument? {
        val floor = floors.getById(floorId) ?: return null
        val spotItems = spots.getByFloor(floorId).map(::toItem)
        val others = elements.getByFloor(floorId).mapNotNull { runCatching { PlanJson.decode(it) }.getOrNull() }
        return PlanDocument(floorId, floor.canvasWidth, floor.canvasHeight, others + spotItems)
    }

    fun save(doc: PlanDocument): SaveResult = transactor.run {
        floors.getById(doc.floorId) ?: throw DomainError.FloorNotFound(doc.floorId)
        val current = spots.getByFloor(doc.floorId).associateBy { it.id }
        val docSpots = doc.items.filter { it.isSpot }
        val wanted = docSpots.mapNotNull { it.spotId }.toSet()

        // Primero los borrados: así una etiqueta liberada se puede reutilizar en el mismo guardado.
        val kept = mutableListOf<String>()
        current.values.filter { it.id !in wanted }.forEach { spot ->
            if (spot.status == SpotStatus.OCCUPIED) kept += spot.label else spotService.delete(spot.id)
        }

        val ids = LinkedHashMap<String, Long>()
        docSpots.forEach { item -> ids[item.id] = saveSpot(doc.floorId, item, current) }

        val rows = doc.items.filterNot { it.isSpot }.map {
            PlanElementRow(it.id, it.type.name, PlanJson.encode(it), it.zOrder)
        }
        elements.replaceForFloor(doc.floorId, rows)
        SaveResult(ids, kept)
    }

    private fun saveSpot(floorId: Long, item: PlanItem, current: Map<Long, Spot>): Long {
        val box = item.geometry as? Box ?: throw IllegalArgumentException("El puesto ${item.label} debe ser un rectángulo o una elipse")
        val shape = if (box.shape == BoxShape.ELLIPSE) SpotShape.ELLIPSE else SpotShape.RECTANGLE
        val type = item.spotType ?: SpotType.CAR
        val id = item.spotId

        var existing = id?.let { current[it] }
        if (existing == null && id != null) {
            // Estaba eliminado (por ejemplo, al rehacer un borrado): se reactiva.
            spotService.restore(id, item.label)
            existing = spots.getById(id)
        }
        if (existing == null) {
            return spotService.add(
                NewSpot(
                    floorId, item.label, type, shape, box.x, box.y, box.width, box.height,
                    item.rotation, item.covered,
                ),
            )
        }
        if (existing.label != item.label.trim() || existing.type != type) {
            spotService.update(existing.id, item.label, type, floorId)
        }
        val moved = existing.shape != shape || existing.x != box.x || existing.y != box.y ||
            existing.width != box.width || existing.height != box.height ||
            existing.rotation != item.rotation || existing.covered != item.covered
        if (moved) spotService.place(existing.id, shape, box.x, box.y, box.width, box.height, item.rotation, item.covered)
        return existing.id
    }

    private fun toItem(spot: Spot) = PlanItem(
        id = "spot-${spot.id}",
        type = ItemType.SPOT,
        geometry = Box(
            spot.x, spot.y, spot.width, spot.height,
            if (spot.shape == SpotShape.ELLIPSE) BoxShape.ELLIPSE else BoxShape.RECTANGLE,
        ),
        rotation = spot.rotation,
        label = spot.label,
        spotId = spot.id,
        spotType = spot.type,
        covered = spot.covered,
    )
}
