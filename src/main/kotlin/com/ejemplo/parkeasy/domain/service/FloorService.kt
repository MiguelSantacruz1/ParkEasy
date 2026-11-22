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
