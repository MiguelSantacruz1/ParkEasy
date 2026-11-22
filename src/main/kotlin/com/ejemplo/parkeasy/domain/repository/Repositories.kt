package com.ejemplo.parkeasy.domain.repository

import com.ejemplo.parkeasy.domain.model.ChangeLogEntry
import com.ejemplo.parkeasy.domain.model.Floor
import com.ejemplo.parkeasy.domain.model.NewChangeLogEntry
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.RecordClosing
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import kotlinx.coroutines.flow.Flow

/** Ejecuta varias operaciones como una sola transacción: o se guardan todas o ninguna. */
interface Transactor {
    fun <T> run(block: () -> T): T
}

interface FloorRepository {
    fun observeAll(): Flow<List<Floor>>
    fun getAll(): List<Floor>
    fun getById(id: Long): Floor?
    fun insert(floor: NewFloor): Long
    fun update(floor: Floor)
    fun softDelete(id: Long)
}

interface SpotRepository {
    fun observeAll(): Flow<List<Spot>>
    fun getAll(): List<Spot>
    fun getByFloor(floorId: Long): List<Spot>
    fun getById(id: Long): Spot?
    fun findByLabel(label: String): Spot?
    fun insert(spot: NewSpot): Long
    fun update(spot: Spot)
    fun setBlocked(id: Long, blocked: Boolean, reason: String?)
    fun softDelete(id: Long)
    fun restore(id: Long)
    fun softDeleteByFloor(floorId: Long)
}

interface ParkingRecordRepository {
    fun observeOpen(): Flow<List<ParkingRecord>>
    fun getAll(): List<ParkingRecord>
    fun getById(id: Long): ParkingRecord?
    fun getOpen(): List<ParkingRecord>
    fun findOpenBySpot(spotId: Long): ParkingRecord?
    fun findOpenByPlate(plate: String): ParkingRecord?
    fun insert(record: NewParkingRecord): Long
    fun close(id: Long, closing: RecordClosing)

    /** Corrige placa, color, observaciones, horas y valor cobrado de un registro. */
    fun correct(record: ParkingRecord)

    /** Cambia el registro de puesto, conservando la hora de llegada. */
    fun relocate(id: Long, spotId: Long, spotLabel: String, floorName: String)
}

interface SettingRepository {
    fun get(key: String): String?
    fun set(key: String, value: String)
    fun remove(key: String)
}

/** Fila de un elemento del plano que no es un puesto: [data] es su JSON versionado. */
data class PlanElementRow(val id: String, val type: String, val data: String, val zOrder: Int)

interface PlanElementRepository {
    fun getByFloor(floorId: Long): List<String>

    /** Reemplaza todos los elementos del piso por [rows], dentro de una transacción. */
    fun replaceForFloor(floorId: Long, rows: List<PlanElementRow>)
}

interface ChangeLogRepository {
    fun append(entry: NewChangeLogEntry): Long
    fun getRecent(limit: Int): List<ChangeLogEntry>
}

interface TariffRepository {
    fun getAll(): List<Tariff>
    fun get(type: VehicleType): Tariff?
    fun save(tariff: Tariff)
}
