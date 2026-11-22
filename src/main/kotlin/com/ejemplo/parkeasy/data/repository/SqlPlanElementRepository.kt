package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.repository.PlanElementRepository
import com.ejemplo.parkeasy.domain.repository.PlanElementRow

class SqlPlanElementRepository(private val db: ParkEasyDatabase) : PlanElementRepository {
    private val queries = db.planElementQueries

    override fun getByFloor(floorId: Long): List<String> = queries.selectByFloor(floorId).executeAsList()

    override fun replaceForFloor(floorId: Long, rows: List<PlanElementRow>) {
        db.transaction {
            queries.deleteByFloor(floorId)
            rows.forEach { queries.insert(it.id, floorId, it.type, it.data, it.zOrder.toLong()) }
        }
    }
}
