package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import java.time.Clock
import java.time.Instant
import java.time.LocalDate

/**
 * Borrado de registros antiguos. Las placas son datos personales: conservarlas más de lo necesario
 * no tiene justificación, así que el administrador puede borrar los cerrados anteriores a una fecha.
 * Nunca se tocan los vehículos que siguen dentro.
 */
class RetentionService(
    private val records: ParkingRecordRepository,
    private val logger: ChangeLogger,
    private val transactor: Transactor,
    private val clock: Clock,
) {
    private fun cutoff(before: LocalDate): Instant {
        if (!before.isBefore(LocalDate.now(clock))) throw DomainError.InvalidPurgeDate()
        return before.atStartOfDay(clock.zone).toInstant()
    }

    /** Cuántos registros se borrarían con [before]; sirve para avisar antes de confirmar. */
    fun preview(before: LocalDate): Int = records.countClosedBefore(cutoff(before))

    /** Borra los registros cerrados que salieron antes de [before] (el día [before] no se borra). */
    fun purge(before: LocalDate): Int = transactor.run {
        val deleted = records.deleteClosedBefore(cutoff(before))
        logger.log(ChangeAction.RECORDS_PURGED, ChangeEntity.RECORD, null, "$deleted registros cerrados anteriores al $before borrados")
        deleted
    }
}