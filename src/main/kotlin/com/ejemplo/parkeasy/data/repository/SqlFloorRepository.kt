package com.ejemplo.parkeasy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow

class SqlFloorRepository(
    db: ParkEasyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : FloorRepository {
    private val queries = db.floorQueries

    override fun observeAll(): Flow<List<Floor>> =
        queries.selectActive(::toFloor).asFlow().mapToList(dispatcher)

    override fun getAll(): List<Floor> = queries.selectActive(::toFloor).executeAsList()

    override fun getById(id: Long): Floor? = queries.selectById(id, ::toFloor).executeAsOneOrNull()

    override fun insert(floor: NewFloor): Long {
        queries.insert(floor.name, floor.sortOrder.toLong(), floor.canvasWidth, floor.canvasHeight)
        return queries.lastInsertId().executeAsOne()
    }

    override fun update(floor: Floor) {
        queries.update(floor.name, floor.sortOrder.toLong(), floor.canvasWidth, floor.canvasHeight, floor.id)
    }

    override fun softDelete(id: Long) {
        queries.softDelete(id)
    }

    private fun toFloor(id: Long, name: String, sortOrder: Long, width: Double, height: Double) =
        Floor(id, name, sortOrder.toInt(), width, height)
}
