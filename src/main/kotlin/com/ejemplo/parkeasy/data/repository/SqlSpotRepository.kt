package com.ejemplo.parkeasy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotShape
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SqlSpotRepository(
    db: ParkEasyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SpotRepository {
    private val queries = db.spotQueries

    override fun observeAll(): Flow<List<Spot>> =
        queries.selectActive(::toSpot).asFlow().mapToList(dispatcher)

    override fun getAll(): List<Spot> = queries.selectActive(::toSpot).executeAsList()

    override fun getByFloor(floorId: Long): List<Spot> =
        queries.selectByFloor(floorId, ::toSpot).executeAsList()

    override fun getById(id: Long): Spot? = queries.selectById(id, ::toSpot).executeAsOneOrNull()

    override fun findByLabel(label: String): Spot? =
        queries.selectByLabel(label, ::toSpot).executeAsOneOrNull()

    override fun insert(spot: NewSpot): Long {
        queries.insert(
            spot.floorId, spot.label, spot.type.name, spot.shape.name,
            spot.x, spot.y, spot.width, spot.height, spot.rotation, spot.covered.toLong(),
        )
        return queries.lastInsertId().executeAsOne()
    }

    override fun update(spot: Spot) {
        queries.update(
            spot.floorId, spot.label, spot.type.name, spot.shape.name,
            spot.x, spot.y, spot.width, spot.height, spot.rotation, spot.covered.toLong(),
            spot.id,
        )
    }

    override fun setBlocked(id: Long, blocked: Boolean, reason: String?) {
        queries.setBlocked(blocked.toLong(), if (blocked) reason else null, id)
    }

    override fun softDelete(id: Long) {
        queries.softDelete(id)
    }

    override fun restore(id: Long) {
        queries.restore(id)
    }

    override fun softDeleteByFloor(floorId: Long) {
        queries.softDeleteByFloor(floorId)
    }

    @Suppress("LongParameterList")
    private fun toSpot(
        id: Long, floorId: Long, label: String, type: String, shape: String,
        x: Double, y: Double, width: Double, height: Double, rotation: Double,
        covered: Long, blocked: Long, blockReason: String?, currentRecordId: Long?,
    ) = Spot(
        id = id,
        floorId = floorId,
        label = label,
        type = SpotType.valueOf(type),
        shape = SpotShape.valueOf(shape),
        x = x, y = y, width = width, height = height, rotation = rotation,
        covered = covered != 0L,
        blocked = blocked != 0L,
        blockReason = blockReason,
        currentRecordId = currentRecordId,
    )

    private fun Boolean.toLong(): Long = if (this) 1L else 0L
}
