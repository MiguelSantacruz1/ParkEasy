package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.ChangeLogEntry
import com.ejemplo.parkeasy.domain.model.NewChangeLogEntry
import com.ejemplo.parkeasy.domain.repository.ChangeLogRepository
import java.time.Instant

class SqlChangeLogRepository(db: ParkEasyDatabase) : ChangeLogRepository {
    private val queries = db.changeLogQueries

    override fun append(entry: NewChangeLogEntry): Long {
        queries.insert(
            entry.timestamp.toEpochMilli(), entry.action.name, entry.entity.name,
            entry.entityId, entry.detail,
        )
        return queries.lastInsertId().executeAsOne()
    }

    override fun getRecent(limit: Int): List<ChangeLogEntry> =
        queries.selectRecent(limit.toLong()) { id, timestamp, action, entity, entityId, detail ->
            ChangeLogEntry(
                id = id,
                timestamp = Instant.ofEpochMilli(timestamp),
                action = ChangeAction.valueOf(action),
                entity = ChangeEntity.valueOf(entity),
                entityId = entityId,
                detail = detail,
            )
        }.executeAsList()
}
