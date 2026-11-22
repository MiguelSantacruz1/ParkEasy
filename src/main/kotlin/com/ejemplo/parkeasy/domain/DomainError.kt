package com.ejemplo.parkeasy.domain

import java.time.Instant

/**
 * Violación de una regla de integridad. Cada caso lleva los datos que la interfaz
 * necesita para explicar el motivo en español.
 */
sealed class DomainError(message: String) : Exception(message) {
    class InvalidBackup(val reason: String) : DomainError("No se puede usar esa copia: $reason")
    class FloorNotFound(val floorId: Long) : DomainError("No existe el piso $floorId")
    class SpotNotFound(val spotId: Long) : DomainError("No existe el puesto $spotId")
    class RecordNotFound(val recordId: Long) : DomainError("No existe el registro $recordId")

    class DuplicateLabel(val label: String) : DomainError("Ya existe un puesto con la etiqueta $label")
    class BlankLabel : DomainError("La etiqueta del puesto no puede estar vacía")
    class BlankPlate : DomainError("La placa no puede estar vacía")

    class SpotOccupied(val spotLabel: String) : DomainError("El puesto $spotLabel está ocupado")
    class SpotBlocked(val spotLabel: String) : DomainError("El puesto $spotLabel está bloqueado")
    class SpotAlreadyBlocked(val spotLabel: String) : DomainError("El puesto $spotLabel ya está bloqueado")
    class SpotNotBlocked(val spotLabel: String) : DomainError("El puesto $spotLabel no está bloqueado")
    class CannotDeleteOccupiedSpot(val spotLabel: String) :
        DomainError("No se puede eliminar el puesto $spotLabel porque está ocupado")
    class FloorHasOccupiedSpots(val floorName: String, val occupied: Int) :
        DomainError("No se puede eliminar $floorName: tiene $occupied puestos ocupados")

    class PlateAlreadyParked(val plate: String, val spotLabel: String) :
        DomainError("La placa $plate ya está en el puesto $spotLabel")
    class IncompatibleVehicle(val spotLabel: String) :
        DomainError("El tipo de vehículo no es compatible con el puesto $spotLabel")
    class EntryInFuture(val entryTime: Instant) : DomainError("La hora de llegada $entryTime está en el futuro")
    class ExitBeforeEntry(val entryTime: Instant, val exitTime: Instant) :
        DomainError("La hora de salida $exitTime es anterior a la llegada $entryTime")
    class SameSpot(val spotLabel: String) : DomainError("El vehículo ya está en el puesto $spotLabel")
    class InvalidTariff : DomainError("Los valores de la tarifa no pueden ser negativos")
    class BlankFloorName : DomainError("El nombre del piso no puede estar vacío")
    class InvalidCount(val count: Int) : DomainError("La cantidad de puestos debe estar entre 1 y 500, no $count")
    class RecordStateMismatch(val recordId: Long) :
        DomainError("La hora de salida de un registro no se cambia desde la corrección: usa registrar salida")
    class RecordAlreadyClosed(val recordId: Long) : DomainError("El registro $recordId ya tiene hora de salida")
}
