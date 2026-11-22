package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.RecordClosing
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import java.time.Clock
import java.time.Instant

data class CheckInRequest(
    val spotId: Long,
    val plate: String,
    val color: String,
    val vehicleType: VehicleType,
    val entryTime: Instant,
    val notes: String = "",
)

/** Entrada y salida de vehículos, con las reglas de integridad de la sección 3. */
class ParkingService(
    private val spots: SpotRepository,
    private val floors: FloorRepository,
    private val records: ParkingRecordRepository,
    private val logger: ChangeLogger,
    private val transactor: Transactor,
    private val clock: Clock,
) {
    /** Registra la entrada. El puesto pasa a ocupado porque queda un registro abierto. */
    fun checkIn(request: CheckInRequest): Long = transactor.run {
        val spot = spots.getById(request.spotId) ?: throw DomainError.SpotNotFound(request.spotId)
        when (spot.status) {
            SpotStatus.BLOCKED -> throw DomainError.SpotBlocked(spot.label)
            SpotStatus.OCCUPIED -> throw DomainError.SpotOccupied(spot.label)
            SpotStatus.FREE -> Unit
        }
        val plate = normalizePlate(request.plate)
        if (plate.isEmpty()) throw DomainError.BlankPlate()
        records.findOpenByPlate(plate)?.let { throw DomainError.PlateAlreadyParked(plate, it.spotLabel) }
        if (!spot.type.accepts(request.vehicleType)) throw DomainError.IncompatibleVehicle(spot.label)
        if (request.entryTime.isAfter(clock.instant())) throw DomainError.EntryInFuture(request.entryTime)

        val floor = floors.getById(spot.floorId) ?: throw DomainError.FloorNotFound(spot.floorId)
        val id = records.insert(
            NewParkingRecord(
                spotId = spot.id,
                spotLabel = spot.label,
                floorName = floor.name,
                plate = plate,
                color = request.color.trim(),
                vehicleType = request.vehicleType,
                entryTime = request.entryTime,
                notes = request.notes.trim(),
            ),
        )
        logger.log(ChangeAction.VEHICLE_ENTERED, ChangeEntity.RECORD, id, "Entrada de $plate en ${spot.label}")
        id
    }

    /** Cierra el registro con la hora de salida, el valor cobrado y la tarifa usada. */
    fun checkOut(recordId: Long, exitTime: Instant, charged: Long, tariff: Tariff) = transactor.run {
        val record = records.getById(recordId) ?: throw DomainError.RecordNotFound(recordId)
        if (!record.isOpen) throw DomainError.RecordAlreadyClosed(recordId)
        if (exitTime.isBefore(record.entryTime)) throw DomainError.ExitBeforeEntry(record.entryTime, exitTime)

        records.close(recordId, RecordClosing(exitTime, charged, tariff))
        logger.log(
            ChangeAction.VEHICLE_EXITED, ChangeEntity.RECORD, recordId,
            "Salida de ${record.plate} de ${record.spotLabel}, cobro $charged COP",
        )
    }

    companion object {
        /** Mayúsculas y sin espacios, para que "abc 123" y "ABC123" sean la misma placa. */
        fun normalizePlate(plate: String): String = plate.filterNot { it.isWhitespace() }.uppercase()
    }
}
