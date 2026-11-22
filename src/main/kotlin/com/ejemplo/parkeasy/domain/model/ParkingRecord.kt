package com.ejemplo.parkeasy.domain.model

import java.time.Instant

/**
 * Registro de estacionamiento. Guarda el número del puesto y el nombre del piso
 * tal como estaban, para que el historial sobreviva al borrado lógico.
 * Mientras [exitTime] sea nulo, el vehículo sigue dentro.
 */
data class ParkingRecord(
    val id: Long,
    val spotId: Long,
    val spotLabel: String,
    val floorName: String,
    val plate: String,
    val color: String,
    val vehicleType: VehicleType,
    val entryTime: Instant,
    val exitTime: Instant?,
    val charged: Long?,
    val tariffFirstHour: Long?,
    val tariffExtraHour: Long?,
    val graceMinutes: Int?,
    val notes: String,
) {
    val isOpen: Boolean get() = exitTime == null
}

data class NewParkingRecord(
    val spotId: Long,
    val spotLabel: String,
    val floorName: String,
    val plate: String,
    val color: String,
    val vehicleType: VehicleType,
    val entryTime: Instant,
    val notes: String = "",
)

/** Datos que se guardan al cerrar un registro. */
data class RecordClosing(
    val exitTime: Instant,
    val charged: Long,
    val tariff: Tariff,
)
