package com.ejemplo.parkeasy.domain.model

/** Tipo de vehículo de un registro. Cada uno tiene su propia tarifa. */
enum class VehicleType { CAR, MOTORCYCLE }

/** Tipo de vehículo que admite un puesto. */
enum class SpotType {
    CAR, MOTORCYCLE, DISABLED, OTHER;

    /** Un carro cabe en puestos de carro, de discapacitados y otros; una moto, en los de moto y otros. */
    fun accepts(vehicle: VehicleType): Boolean = when (vehicle) {
        VehicleType.CAR -> this == CAR || this == DISABLED || this == OTHER
        VehicleType.MOTORCYCLE -> this == MOTORCYCLE || this == OTHER
    }
}

/** Forma del puesto en el plano. */
enum class SpotShape { RECTANGLE, ELLIPSE }

/** Estado visible de un puesto. Se calcula; nunca se guarda por separado. */
enum class SpotStatus { FREE, OCCUPIED, BLOCKED }

enum class ChangeEntity { FLOOR, SPOT, RECORD, TARIFF }

enum class ChangeAction {
    FLOOR_CREATED, FLOOR_UPDATED, FLOOR_DELETED,
    SPOT_CREATED, SPOT_UPDATED, SPOT_DELETED, SPOT_BLOCKED, SPOT_UNBLOCKED,
    VEHICLE_ENTERED, VEHICLE_EXITED, VEHICLE_MOVED, RECORD_CORRECTED,
    TARIFF_UPDATED, RECORDS_PURGED,
}
