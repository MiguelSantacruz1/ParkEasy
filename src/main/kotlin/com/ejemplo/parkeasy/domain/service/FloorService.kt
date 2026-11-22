package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.Transactor

/** Reglas de los pisos. */
class FloorService(
    private val floors: FloorRepository,
    private val spots: SpotRepository,
    private val logger: ChangeLogger,
    private val transactor: Transactor,
) {
    /** El piso nuevo va al final de la lista. */
    fun add(name: String): Long = transactor.run {
        val nextOrder = (floors.getAll().maxOfOrNull { it.sortOrder } ?: -1) + 1
        val id = floors.insert(NewFloor(name.trim(), nextOrder))
        logger.log(ChangeAction.FLOOR_CREATED, ChangeEntity.FLOOR, id, "Piso ${name.trim()} creado")
        id
    }

    fun rename(floorId: Long, name: String) = transactor.run {
        val floor = floors.getById(floorId) ?: throw DomainError.FloorNotFound(floorId)
        val clean = name.trim()
        if (clean.isEmpty()) throw DomainError.BlankFloorName()
        floors.update(floor.copy(name = clean))
        logger.log(ChangeAction.FLOOR_UPDATED, ChangeEntity.FLOOR, floorId, "Piso ${floor.name} renombrado a $clean")
    }

    /** Sube (-1) o baja (+1) el piso en la lista, intercambiando el orden con su vecino. */
    fun move(floorId: Long, direction: Int) = transactor.run {
        val all = floors.getAll()
        val index = all.indexOfFirst { it.id == floorId }
        if (index < 0) throw DomainError.FloorNotFound(floorId)
        val other = all.getOrNull(index + direction.coerceIn(-1, 1)) ?: return@run
        val floor = all[index]
        floors.update(floor.copy(sortOrder = other.sortOrder))
        floors.update(other.copy(sortOrder = floor.sortOrder))
        logger.log(ChangeAction.FLOOR_UPDATED, ChangeEntity.FLOOR, floorId, "Piso ${floor.name} reordenado")
    }

    /**
     * Elimina el piso y sus puestos (borrado lógico). Se rechaza si alguno está ocupado.
     * Devuelve cuántos puestos se eliminaron con él.
     */
    fun delete(floorId: Long): Int = transactor.run {
        val floor = floors.getById(floorId) ?: throw DomainError.FloorNotFound(floorId)
        val floorSpots = spots.getByFloor(floorId)
        val occupied = floorSpots.count { it.status == SpotStatus.OCCUPIED }
        if (occupied > 0) throw DomainError.FloorHasOccupiedSpots(floor.name, occupied)

        spots.softDeleteByFloor(floorId)
        floors.softDelete(floorId)
        logger.log(
            ChangeAction.FLOOR_DELETED, ChangeEntity.FLOOR, floorId,
            "Piso ${floor.name} eliminado con ${floorSpots.size} puestos",
        )
        floorSpots.size
    }
}
