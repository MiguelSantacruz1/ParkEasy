package com.ejemplo.parkeasy.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.RecordClosing
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import java.time.Instant

class SqlParkingRecordRepository(
    db: ParkEasyDatabase,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ParkingRecordRepository {
    private val queries = db.parkingRecordQueries

    override fun observeOpen(): Flow<List<ParkingRecord>> =
        queries.selectOpen(::toRecord).asFlow().mapToList(dispatcher)

    override fun getAll(): List<ParkingRecord> = queries.selectAll(::toRecord).executeAsList()

    override fun getById(id: Long): ParkingRecord? =
        queries.selectById(id, ::toRecord).executeAsOneOrNull()

    override fun getOpen(): List<ParkingRecord> = queries.selectOpen(::toRecord).executeAsList()

    override fun findOpenBySpot(spotId: Long): ParkingRecord? =
        queries.selectOpenBySpot(spotId, ::toRecord).executeAsOneOrNull()

    override fun findOpenByPlate(plate: String): ParkingRecord? =
        queries.selectOpenByPlate(plate, ::toRecord).executeAsOneOrNull()

    override fun insert(record: NewParkingRecord): Long {
        queries.insert(
            record.spotId, record.spotLabel, record.floorName, record.plate, record.color,
            record.vehicleType.name, record.entryTime.toEpochMilli(), record.notes,
        )
        return queries.lastInsertId().executeAsOne()
    }

    override fun close(id: Long, closing: RecordClosing) {
        queries.close(
            closing.exitTime.toEpochMilli(), closing.charged,
            closing.tariff.firstHour, closing.tariff.extraHour, closing.tariff.graceMinutes.toLong(),
            id,
        )
    }

    override fun correct(record: ParkingRecord) {
        queries.correct(
            record.plate, record.color, record.notes, record.entryTime.toEpochMilli(),
            record.exitTime?.toEpochMilli(), record.charged, record.id,
        )
    }

    override fun relocate(id: Long, spotId: Long, spotLabel: String, floorName: String) {
        queries.relocate(spotId, spotLabel, floorName, id)
    }

    @Suppress("LongParameterList")
    private fun toRecord(
        id: Long, spotId: Long, spotLabel: String, floorName: String, plate: String, color: String,
        vehicleType: String, entryTime: Long, exitTime: Long?, charged: Long?,
        tariffFirstHour: Long?, tariffExtraHour: Long?, graceMinutes: Long?, notes: String,
    ) = ParkingRecord(
        id = id,
        spotId = spotId,
        spotLabel = spotLabel,
        floorName = floorName,
        plate = plate,
        color = color,
        vehicleType = VehicleType.valueOf(vehicleType),
        entryTime = Instant.ofEpochMilli(entryTime),
        exitTime = exitTime?.let(Instant::ofEpochMilli),
        charged = charged,
        tariffFirstHour = tariffFirstHour,
        tariffExtraHour = tariffExtraHour,
        graceMinutes = graceMinutes?.toInt(),
        notes = notes,
    )
}
