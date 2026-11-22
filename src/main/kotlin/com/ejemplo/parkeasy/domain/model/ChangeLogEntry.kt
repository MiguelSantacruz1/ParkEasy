package com.ejemplo.parkeasy.domain.model

import java.time.Instant

/** Línea de la bitácora: qué se cambió, cuándo y sobre qué. */
data class ChangeLogEntry(
    val id: Long,
    val timestamp: Instant,
    val action: ChangeAction,
    val entity: ChangeEntity,
    val entityId: Long?,
    val detail: String,
)

data class NewChangeLogEntry(
    val timestamp: Instant,
    val action: ChangeAction,
    val entity: ChangeEntity,
    val entityId: Long?,
    val detail: String,
)
