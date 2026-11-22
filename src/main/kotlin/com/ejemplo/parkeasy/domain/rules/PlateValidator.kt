package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.VehicleType

/** Valida placas colombianas: carro ABC123 y moto ABC12D. */
object PlateValidator {
    private val carPlate = Regex("^[A-Z]{3}[0-9]{3}$")
    private val motorcyclePlate = Regex("^[A-Z]{3}[0-9]{2}[A-Z]$")

    /** Mayúsculas y sin espacios ni guiones. */
    fun normalize(plate: String): String =
        plate.filterNot { it.isWhitespace() || it == '-' }.uppercase()

    fun isValid(plate: String, type: VehicleType): Boolean {
        val normalized = normalize(plate)
        return when (type) {
            VehicleType.CAR -> carPlate.matches(normalized)
            VehicleType.MOTORCYCLE -> motorcyclePlate.matches(normalized)
        }
    }

    /** Verdadero si la placa tiene formato de carro o de moto. */
    fun isValidForAnyType(plate: String): Boolean = VehicleType.entries.any { isValid(plate, it) }
}
