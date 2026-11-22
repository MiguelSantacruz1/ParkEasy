package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.Tariff
import java.time.Duration
import java.time.Instant

/** Desglose del cobro, en pesos colombianos sin decimales. */
data class PriceBreakdown(
    val duration: Duration,
    val billedHours: Long,
    val firstHour: Long,
    val extraHours: Long,
    val extraHourValue: Long,
    val total: Long,
)

/**
 * Cobro por hora o fracción, redondeando hacia arriba. Todo vehículo paga al menos
 * la primera hora. Los minutos de gracia se descuentan del tiempo antes de contar horas.
 * Función pura: mismas entradas, mismo resultado. Ver docs/COBRO.md.
 */
object ParkingPriceCalculator {

    fun calculate(entry: Instant, exit: Instant, tariff: Tariff): PriceBreakdown {
        require(!exit.isBefore(entry)) { "La salida no puede ser anterior a la llegada" }
        val duration = Duration.between(entry, exit)
        val chargeableSeconds = maxOf(0L, duration.seconds - tariff.graceMinutes * 60L)
        val hours = maxOf(1L, (chargeableSeconds + SECONDS_PER_HOUR - 1) / SECONDS_PER_HOUR)
        val extraHours = hours - 1
        return PriceBreakdown(
            duration = duration,
            billedHours = hours,
            firstHour = tariff.firstHour,
            extraHours = extraHours,
            extraHourValue = tariff.extraHour,
            total = tariff.firstHour + extraHours * tariff.extraHour,
        )
    }

    private const val SECONDS_PER_HOUR = 3_600L
}
