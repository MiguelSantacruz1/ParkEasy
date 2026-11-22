package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.Transactor

/** Reglas de integridad de los puestos: etiquetas únicas, bloqueo y borrado. */
class SpotService(
    private val spots: SpotRepository,
    private val floors: FloorRepository,
    private val logger: ChangeLogger,
    private val transactor: Transactor,
) {
    fun add(spot: NewSpot): Long = transactor.run {
        val label = spot.label.trim()
        if (label.isEmpty()) throw DomainError.BlankLabel()
        floors.getById(spot.floorId) ?: throw DomainError.FloorNotFound(spot.floorId)
        if (spots.findByLabel(label) != null) throw DomainError.DuplicateLabel(label)

        val id = spots.insert(spot.copy(label = label))
        logger.log(ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, id, "Puesto $label creado")
        id
    }

    /** Solo se bloquea un puesto libre. */
    fun block(spotId: Long, reason: String?) = transactor.run {
        val spot = require(spotId)
        when (spot.status) {
            SpotStatus.BLOCKED -> throw DomainError.SpotAlreadyBlocked(spot.label)
            SpotStatus.OCCUPIED -> throw DomainError.SpotOccupied(spot.label)
            SpotStatus.FREE -> Unit
        }
        val cleanReason = reason?.trim()?.ifEmpty { null }
        spots.setBlocked(spotId, true, cleanReason)
        logger.log(
            ChangeAction.SPOT_BLOCKED, ChangeEntity.SPOT, spotId,
            "Puesto ${spot.label} bloqueado" + (cleanReason?.let { ": $it" } ?: ""),
        )
    }

    /** Solo se desbloquea un puesto que está bloqueado. */
    fun unblock(spotId: Long) = transactor.run {
        val spot = require(spotId)
        if (!spot.blocked) throw DomainError.SpotNotBlocked(spot.label)
        spots.setBlocked(spotId, false, null)
        logger.log(ChangeAction.SPOT_UNBLOCKED, ChangeEntity.SPOT, spotId, "Puesto ${spot.label} desbloqueado")
    }

    /** El borrado es lógico y se rechaza si el puesto está ocupado. */
    fun delete(spotId: Long) = transactor.run {
        val spot = require(spotId)
        if (spot.status == SpotStatus.OCCUPIED) throw DomainError.CannotDeleteOccupiedSpot(spot.label)
        spots.softDelete(spotId)
        logger.log(ChangeAction.SPOT_DELETED, ChangeEntity.SPOT, spotId, "Puesto ${spot.label} eliminado")
    }

    private fun require(spotId: Long): Spot = spots.getById(spotId) ?: throw DomainError.SpotNotFound(spotId)
}
