package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewChangeLogEntry
import com.ejemplo.parkeasy.domain.repository.ChangeLogRepository
import java.time.Clock

/** Escribe en la bitácora con la hora del reloj del sistema. */
class ChangeLogger(
    private val repository: ChangeLogRepository,
    private val clock: Clock,
) {
    fun log(action: ChangeAction, entity: ChangeEntity, entityId: Long?, detail: String) {
        repository.append(NewChangeLogEntry(clock.instant(), action, entity, entityId, detail))
    }
}
