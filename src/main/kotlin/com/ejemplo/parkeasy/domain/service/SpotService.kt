package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotShape
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import com.ejemplo.parkeasy.domain.rules.SpotLabelGenerator

/** Reglas de integridad de los puestos: etiquetas únicas, edición, bloqueo y borrado. */
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

    /**
     * Agrega [count] puestos con numeración consecutiva desde [startLabel]. Quedan libres en el
     * plano, en una cuadrícula de [COLUMNS] columnas, listos para acomodarlos en el editor.
     * Si el piso ya tiene puestos, la cuadrícula empieza debajo de ellos.
     */
    fun addMany(floorId: Long, type: SpotType, startLabel: String, count: Int, covered: Boolean = false): List<Long> =
        transactor.run {
            if (count !in 1..MAX_BATCH) throw DomainError.InvalidCount(count)
            if (startLabel.isBlank()) throw DomainError.BlankLabel()
            floors.getById(floorId) ?: throw DomainError.FloorNotFound(floorId)

            val taken = spots.getAll().map { it.label }.toSet()
            val labels = SpotLabelGenerator.generate(startLabel, count, taken)
            val (width, height) = sizeFor(type)
            val startY = spots.getByFloor(floorId).maxOfOrNull { it.y + it.height + GAP }?.coerceAtLeast(GAP) ?: GAP

            val ids = labels.mapIndexed { index, label ->
                spots.insert(
                    NewSpot(
                        floorId = floorId, label = label, type = type,
                        x = GAP + (index % COLUMNS) * (width + GAP),
                        y = startY + (index / COLUMNS) * (height + GAP),
                        width = width, height = height, covered = covered,
                    ),
                )
            }
            logger.log(
                ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, ids.first(),
                "${labels.size} puestos creados: ${labels.first()} a ${labels.last()}",
            )
            ids
        }

    /** Cambia etiqueta, tipo y piso. Un puesto ocupado conserva su tipo y su piso. */
    fun update(spotId: Long, label: String, type: SpotType, floorId: Long) = transactor.run {
        val spot = require(spotId)
        val newLabel = label.trim()
        if (newLabel.isEmpty()) throw DomainError.BlankLabel()
        spots.findByLabel(newLabel)?.let { if (it.id != spotId) throw DomainError.DuplicateLabel(newLabel) }
        if (floorId != spot.floorId) floors.getById(floorId) ?: throw DomainError.FloorNotFound(floorId)
        if (spot.status == SpotStatus.OCCUPIED && (type != spot.type || floorId != spot.floorId)) {
            throw DomainError.SpotOccupied(spot.label)
        }
        spots.update(spot.copy(label = newLabel, type = type, floorId = floorId))
        logger.log(ChangeAction.SPOT_UPDATED, ChangeEntity.SPOT, spotId, "Puesto ${spot.label} editado como $newLabel")
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

    /** El borrado es lógico y se rechaza si el puesto está ocupado. Devuelve el puesto para poder deshacer. */
    fun delete(spotId: Long): Spot = transactor.run {
        val spot = require(spotId)
        if (spot.status == SpotStatus.OCCUPIED) throw DomainError.CannotDeleteOccupiedSpot(spot.label)
        spots.softDelete(spotId)
        logger.log(ChangeAction.SPOT_DELETED, ChangeEntity.SPOT, spotId, "Puesto ${spot.label} eliminado")
        spot
    }

    /** Deshace un borrado. Falla si mientras tanto otro puesto tomó la misma etiqueta. */
    fun undoDelete(spot: Spot) = transactor.run {
        if (spots.findByLabel(spot.label) != null) throw DomainError.DuplicateLabel(spot.label)
        spots.restore(spot.id)
        logger.log(ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, spot.id, "Puesto ${spot.label} restaurado")
    }

    /**
     * Reactiva un puesto eliminado con su etiqueta (la de la copia del plano que lo vuelve a pedir).
     * Falla si otro puesto activo ya usa esa etiqueta.
     */
    fun restore(spotId: Long, label: String) = transactor.run {
        val clean = label.trim()
        if (clean.isEmpty()) throw DomainError.BlankLabel()
        spots.findByLabel(clean)?.let { if (it.id != spotId) throw DomainError.DuplicateLabel(clean) }
        spots.restore(spotId)
        logger.log(ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, spotId, "Puesto $clean restaurado")
    }

    /**
     * Guarda dónde y cómo está dibujado un puesto en el plano. No toca etiqueta ni tipo y
     * funciona también con puestos ocupados; no deja rastro en la bitácora porque mover un
     * puesto no cambia datos del negocio.
     */
    fun place(spotId: Long, shape: SpotShape, x: Double, y: Double, width: Double, height: Double, rotation: Double, covered: Boolean) =
        transactor.run {
            val spot = require(spotId)
            spots.update(spot.copy(shape = shape, x = x, y = y, width = width, height = height, rotation = rotation, covered = covered))
        }

    private fun require(spotId: Long): Spot = spots.getById(spotId) ?: throw DomainError.SpotNotFound(spotId)

    private fun sizeFor(type: SpotType): Pair<Double, Double> =
        if (type == SpotType.MOTORCYCLE) 1.2 to 2.5 else NewSpot.DEFAULT_WIDTH to NewSpot.DEFAULT_HEIGHT

    companion object {
        const val MAX_BATCH = 500
        private const val COLUMNS = 10
        private const val GAP = 0.5
    }
}
