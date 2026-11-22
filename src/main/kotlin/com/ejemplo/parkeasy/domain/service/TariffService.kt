package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.repository.Transactor

/** Cambiar una tarifa no altera los registros ya cerrados: cada uno guarda su copia. */
class TariffService(
    private val tariffs: TariffRepository,
    private val logger: ChangeLogger,
    private val transactor: Transactor,
) {
    fun save(tariff: Tariff) = transactor.run {
        if (tariff.firstHour < 0 || tariff.extraHour < 0 || tariff.graceMinutes < 0) throw DomainError.InvalidTariff()
        tariffs.save(tariff)
        logger.log(
            ChangeAction.TARIFF_UPDATED, ChangeEntity.TARIFF, null,
            "Tarifa ${tariff.vehicleType}: ${tariff.firstHour} + ${tariff.extraHour} por hora, gracia ${tariff.graceMinutes} min",
        )
    }
}
