package com.ejemplo.parkeasy.domain.rules

import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParkingPriceCalculatorTest {
    private val entry = Instant.parse("2026-11-22T08:00:00Z")
    private val car = Tariff(VehicleType.CAR, 4_000, 1_500)
    private val moto = Tariff(VehicleType.MOTORCYCLE, 2_500, 1_000)

    private fun total(tariff: Tariff, d: Duration) =
        ParkingPriceCalculator.calculate(entry, entry.plus(d), tariff).total

    @Test
    fun `carro - valores de referencia`() {
        assertEquals(4_000, total(car, Duration.ofMinutes(1)))
        assertEquals(4_000, total(car, Duration.ofMinutes(59)))
        assertEquals(4_000, total(car, Duration.ofMinutes(60)))
        assertEquals(5_500, total(car, Duration.ofMinutes(61)))
        assertEquals(7_000, total(car, Duration.ofHours(3)))
    }

    @Test
    fun `moto - valores de referencia`() {
        assertEquals(2_500, total(moto, Duration.ofMinutes(1)))
        assertEquals(2_500, total(moto, Duration.ofMinutes(59)))
        assertEquals(2_500, total(moto, Duration.ofMinutes(60)))
        assertEquals(3_500, total(moto, Duration.ofMinutes(61)))
        assertEquals(4_500, total(moto, Duration.ofHours(3)))
    }

    @Test
    fun `entrada y salida en el mismo instante cobra la primera hora`() {
        assertEquals(4_000, total(car, Duration.ZERO))
        assertEquals(2_500, total(moto, Duration.ZERO))
    }

    @Test
    fun `una fraccion de segundo mas de la hora cobra la siguiente`() {
        assertEquals(5_500, total(car, Duration.ofHours(1).plusSeconds(1)))
    }

    @Test
    fun `estadias de varios dias`() {
        // 2 días = 48 h → 4.000 + 47 × 1.500
        assertEquals(4_000 + 47 * 1_500L, total(car, Duration.ofDays(2)))
        // 3 días y 5 minutos = 72 h y 5 min → 73 horas
        assertEquals(2_500 + 72 * 1_000L, total(moto, Duration.ofDays(3).plusMinutes(5)))
    }

    @Test
    fun `desglose con horas adicionales`() {
        val r = ParkingPriceCalculator.calculate(entry, entry.plus(Duration.ofMinutes(61)), car)
        assertEquals(2, r.billedHours)
        assertEquals(1, r.extraHours)
        assertEquals(4_000, r.firstHour)
        assertEquals(1_500, r.extraHourValue)
        assertEquals(Duration.ofMinutes(61), r.duration)
    }

    @Test
    fun `los minutos de gracia se descuentan del tiempo`() {
        val conGracia = car.copy(graceMinutes = 10)
        assertEquals(4_000, total(conGracia, Duration.ofMinutes(70)))
        assertEquals(5_500, total(conGracia, Duration.ofMinutes(71)))
        assertEquals(4_000, total(conGracia, Duration.ofMinutes(5)))
    }

    @Test
    fun `con tarifa modificada usa los valores nuevos`() {
        val nueva = Tariff(VehicleType.CAR, firstHour = 5_000, extraHour = 2_000)
        assertEquals(5_000 + 2 * 2_000L, total(nueva, Duration.ofHours(3)))
    }

    @Test
    fun `la salida anterior a la llegada es un error`() {
        assertFailsWith<IllegalArgumentException> {
            ParkingPriceCalculator.calculate(entry, entry.minusSeconds(1), car)
        }
    }
}
