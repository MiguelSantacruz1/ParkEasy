package com.ejemplo.parkeasy.domain.model

/** Tarifa de un tipo de vehículo, en pesos colombianos sin decimales. */
data class Tariff(
    val vehicleType: VehicleType,
    val firstHour: Long,
    val extraHour: Long,
    val graceMinutes: Int = 0,
) {
    companion object {
        /** Tarifas iniciales: carro 4.000 + 1.500 por hora adicional; moto 2.500 + 1.000. */
        val defaults: List<Tariff> = listOf(
            Tariff(VehicleType.CAR, firstHour = 4_000, extraHour = 1_500),
            Tariff(VehicleType.MOTORCYCLE, firstHour = 2_500, extraHour = 1_000),
        )
    }
}
