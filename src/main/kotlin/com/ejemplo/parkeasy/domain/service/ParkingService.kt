package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewParkingRecord
import com.ejemplo.parkeasy.domain.model.ParkingRecord
import com.ejemplo.parkeasy.domain.model.RecordClosing
import com.ejemplo.parkeasy.domain.model.Spot
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.repository.Transactor
import com.ejemplo.parkeasy.domain.rules.ParkingPriceCalculator
import com.ejemplo.parkeasy.domain.rules.PlateValidator
import com.ejemplo.parkeasy.domain.rules.PriceBreakdown
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

/** Datos que se pueden corregir en un registro. La hora de salida solo aplica a registros cerrados. */
data class CorrectionRequest(
    val plate: String,
    val color: String,
    val notes: String,
    val entryTime: Instant,
    val exitTime: Instant?,
)

/** Entrada, salida, corrección y traslado de vehículos, con las reglas de integridad. */
class ParkingService(
    private val spots: SpotRepository,
    private val floors: FloorRepository,
    private val records: ParkingRecordRepository,
    private val tariffs: TariffRepository,
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
        val plate = PlateValidator.normalize(request.plate)
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

    /** Cuánto se cobraría si el vehículo sale en [exitTime], con la tarifa vigente. No guarda nada. */
    fun quote(recordId: Long, exitTime: Instant): PriceBreakdown {
        val record = records.getById(recordId) ?: throw DomainError.RecordNotFound(recordId)
        if (exitTime.isBefore(record.entryTime)) throw DomainError.ExitBeforeEntry(record.entryTime, exitTime)
        return ParkingPriceCalculator.calculate(record.entryTime, exitTime, tariffFor(record.vehicleType))
    }

    /** Registra la salida calculando el cobro con la tarifa vigente. Devuelve el desglose. */
    fun checkOut(recordId: Long, exitTime: Instant): PriceBreakdown = transactor.run {
        val record = records.getById(recordId) ?: throw DomainError.RecordNotFound(recordId)
        val tariff = tariffFor(record.vehicleType)
        if (exitTime.isBefore(record.entryTime)) throw DomainError.ExitBeforeEntry(record.entryTime, exitTime)
        val price = ParkingPriceCalculator.calculate(record.entryTime, exitTime, tariff)
        checkOut(recordId, exitTime, price.total, tariff)
        price
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

    /**
     * Corrige un registro. Si ya está cerrado, el valor se recalcula con la tarifa guardada en
     * el propio registro, no con la vigente. La corrección queda en la bitácora.
     */
    fun correct(recordId: Long, request: CorrectionRequest) = transactor.run {
        val record = records.getById(recordId) ?: throw DomainError.RecordNotFound(recordId)
        if (record.isOpen != (request.exitTime == null)) throw DomainError.RecordStateMismatch(recordId)

        val plate = PlateValidator.normalize(request.plate)
        if (plate.isEmpty()) throw DomainError.BlankPlate()
        if (record.isOpen && plate != record.plate) {
            records.findOpenByPlate(plate)?.let { throw DomainError.PlateAlreadyParked(plate, it.spotLabel) }
        }
        if (request.entryTime.isAfter(clock.instant())) throw DomainError.EntryInFuture(request.entryTime)
        request.exitTime?.let {
            if (it.isBefore(request.entryTime)) throw DomainError.ExitBeforeEntry(request.entryTime, it)
        }

        val charged = if (request.exitTime != null) {
            val saved = Tariff(
                record.vehicleType,
                record.tariffFirstHour ?: tariffFor(record.vehicleType).firstHour,
                record.tariffExtraHour ?: tariffFor(record.vehicleType).extraHour,
                record.graceMinutes ?: 0,
            )
            ParkingPriceCalculator.calculate(request.entryTime, request.exitTime, saved).total
        } else {
            null
        }
        records.correct(
            record.copy(
                plate = plate,
                color = request.color.trim(),
                notes = request.notes.trim(),
                entryTime = request.entryTime,
                exitTime = request.exitTime,
                charged = charged,
            ),
        )
        logger.log(
            ChangeAction.RECORD_CORRECTED, ChangeEntity.RECORD, recordId,
            "Registro de ${record.plate} corregido" + (if (plate != record.plate) " (placa $plate)" else ""),
        )
    }

    /** Cambia el vehículo de puesto sin perder su hora de llegada. */
    fun move(recordId: Long, newSpotId: Long) = transactor.run {
        val record = records.getById(recordId) ?: throw DomainError.RecordNotFound(recordId)
        if (!record.isOpen) throw DomainError.RecordAlreadyClosed(recordId)
        if (record.spotId == newSpotId) throw DomainError.SameSpot(record.spotLabel)

        val target: Spot = spots.getById(newSpotId) ?: throw DomainError.SpotNotFound(newSpotId)
        when (target.status) {
            SpotStatus.BLOCKED -> throw DomainError.SpotBlocked(target.label)
            SpotStatus.OCCUPIED -> throw DomainError.SpotOccupied(target.label)
            SpotStatus.FREE -> Unit
        }
        if (!target.type.accepts(record.vehicleType)) throw DomainError.IncompatibleVehicle(target.label)
        val floor = floors.getById(target.floorId) ?: throw DomainError.FloorNotFound(target.floorId)

        records.relocate(recordId, target.id, target.label, floor.name)
        logger.log(
            ChangeAction.VEHICLE_MOVED, ChangeEntity.RECORD, recordId,
            "${record.plate} pasó de ${record.spotLabel} a ${target.label}",
        )
    }

    /** Vehículos que están dentro ahora y cuya placa contiene el texto buscado. */
    fun findInside(query: String): List<ParkingRecord> {
        val q = PlateValidator.normalize(query)
        if (q.isEmpty()) return emptyList()
        return records.getOpen().filter { q in it.plate }
    }

    private fun tariffFor(type: VehicleType): Tariff =
        tariffs.get(type) ?: Tariff.defaults.first { it.vehicleType == type }
}
