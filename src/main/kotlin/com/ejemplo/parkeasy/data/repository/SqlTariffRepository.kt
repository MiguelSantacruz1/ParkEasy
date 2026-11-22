package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.repository.TariffRepository

class SqlTariffRepository(db: ParkEasyDatabase) : TariffRepository {
    private val queries = db.tariffQueries

    override fun getAll(): List<Tariff> = queries.selectAll(::toTariff).executeAsList()

    override fun get(type: VehicleType): Tariff? =
        queries.selectByType(type.name, ::toTariff).executeAsOneOrNull()

    override fun save(tariff: Tariff) {
        queries.upsert(tariff.vehicleType.name, tariff.firstHour, tariff.extraHour, tariff.graceMinutes.toLong())
    }

    private fun toTariff(vehicleType: String, firstHour: Long, extraHour: Long, graceMinutes: Long) =
        Tariff(VehicleType.valueOf(vehicleType), firstHour, extraHour, graceMinutes.toInt())
}
