package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.withEnv
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ParkingServiceCorrectionTest {

    @Test
    fun `la salida calcula el cobro con la tarifa vigente y lo guarda`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), at = env.clock.now.minus(Duration.ofMinutes(61)))

        val price = env.parkingService.checkOut(recordId, env.clock.now)

        assertEquals(5_500, price.total)
        val record = env.records.getById(recordId)!!
        assertEquals(5_500, record.charged)
        assertEquals(4_000, record.tariffFirstHour)
        assertEquals(1_500, record.tariffExtraHour)
    }

    @Test
    fun `la cotizacion no guarda nada`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), at = env.clock.now.minus(Duration.ofHours(3)))

        assertEquals(7_000, env.parkingService.quote(recordId, env.clock.now).total)
        assertEquals(true, env.records.getById(recordId)!!.isOpen)
    }

    @Test
    fun `una moto se cobra con la tarifa de moto`() = withEnv { env ->
        val spot = env.floorWithSpot("M-01", SpotType.MOTORCYCLE)
        val recordId = env.checkIn(spot, "ABC12D", VehicleType.MOTORCYCLE, env.clock.now.minus(Duration.ofMinutes(61)))

        assertEquals(3_500, env.parkingService.checkOut(recordId, env.clock.now).total)
    }

    @Test
    fun `cambiar la tarifa no altera registros ya cerrados`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), at = env.clock.now.minus(Duration.ofMinutes(61)))
        env.parkingService.checkOut(recordId, env.clock.now)

        env.tariffService.save(Tariff(VehicleType.CAR, 9_000, 9_000))

        assertEquals(5_500, env.records.getById(recordId)!!.charged)
    }

    @Test
    fun `corregir la hora de llegada de un registro cerrado recalcula con la tarifa guardada`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), at = env.clock.now.minus(Duration.ofMinutes(61)))
        env.parkingService.checkOut(recordId, env.clock.now)
        env.tariffService.save(Tariff(VehicleType.CAR, 9_000, 9_000))
        val record = env.records.getById(recordId)!!

        env.parkingService.correct(
            recordId,
            CorrectionRequest(record.plate, record.color, record.notes, env.clock.now.minus(Duration.ofHours(3)), record.exitTime),
        )

        // 3 horas con la tarifa de entonces (4.000 + 2 × 1.500), no con la nueva.
        assertEquals(7_000, env.records.getById(recordId)!!.charged)
        assertEquals(true, ChangeAction.RECORD_CORRECTED in env.changeLog.getRecent(10).map { it.action })
    }

    @Test
    fun `corregir una placa mal escrita en un registro abierto`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), plate = "ABC124")
        val record = env.records.getById(recordId)!!

        env.parkingService.correct(recordId, CorrectionRequest("abc123", "Azul", "rayón", record.entryTime, null))

        val fixed = env.records.getById(recordId)!!
        assertEquals("ABC123", fixed.plate)
        assertEquals("Azul", fixed.color)
        assertEquals("rayón", fixed.notes)
    }

    @Test
    fun `no corrige a una placa que ya esta dentro en otro puesto`() = withEnv { env ->
        env.checkIn(env.floorWithSpot("A-01"), plate = "ABC123")
        val recordId = env.checkIn(env.floorWithSpot("A-02"), plate = "XYZ987")
        val record = env.records.getById(recordId)!!

        assertFailsWith<DomainError.PlateAlreadyParked> {
            env.parkingService.correct(recordId, CorrectionRequest("ABC123", record.color, "", record.entryTime, null))
        }
    }

    @Test
    fun `la correccion respeta las reglas de fechas`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot(), at = env.clock.now.minus(Duration.ofHours(2)))
        env.parkingService.checkOut(recordId, env.clock.now)
        val r = env.records.getById(recordId)!!

        assertFailsWith<DomainError.EntryInFuture> {
            env.parkingService.correct(recordId, CorrectionRequest(r.plate, r.color, "", env.clock.now.plusSeconds(60), r.exitTime))
        }
        assertFailsWith<DomainError.ExitBeforeEntry> {
            env.parkingService.correct(recordId, CorrectionRequest(r.plate, r.color, "", env.clock.now, env.clock.now.minusSeconds(1)))
        }
    }

    @Test
    fun `la correccion no cambia el estado abierto o cerrado`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot())
        val r = env.records.getById(recordId)!!

        assertFailsWith<DomainError.RecordStateMismatch> {
            env.parkingService.correct(recordId, CorrectionRequest(r.plate, r.color, "", r.entryTime, env.clock.now))
        }
    }

    @Test
    fun `mover un vehiculo conserva la hora de llegada`() = withEnv { env ->
        val a = env.floorWithSpot("A-01")
        val b = env.floorWithSpot("A-02")
        val recordId = env.checkIn(a)
        val entry = env.records.getById(recordId)!!.entryTime

        env.parkingService.move(recordId, b)

        val record = env.records.getById(recordId)!!
        assertEquals("A-02", record.spotLabel)
        assertEquals(entry, record.entryTime)
        assertEquals(SpotStatus.FREE, env.spots.getById(a)!!.status)
        assertEquals(SpotStatus.OCCUPIED, env.spots.getById(b)!!.status)
    }

    @Test
    fun `no mueve a un puesto ocupado, bloqueado, incompatible o el mismo`() = withEnv { env ->
        val a = env.floorWithSpot("A-01")
        val ocupado = env.floorWithSpot("A-02")
        val bloqueado = env.floorWithSpot("A-03")
        val moto = env.floorWithSpot("M-01", SpotType.MOTORCYCLE)
        env.checkIn(ocupado, "XYZ987")
        env.spotService.block(bloqueado, null)
        val recordId = env.checkIn(a)

        assertFailsWith<DomainError.SpotOccupied> { env.parkingService.move(recordId, ocupado) }
        assertFailsWith<DomainError.SpotBlocked> { env.parkingService.move(recordId, bloqueado) }
        assertFailsWith<DomainError.IncompatibleVehicle> { env.parkingService.move(recordId, moto) }
        assertFailsWith<DomainError.SameSpot> { env.parkingService.move(recordId, a) }
        assertEquals("A-01", env.records.getById(recordId)!!.spotLabel)
    }

    @Test
    fun `busca por placa solo entre los vehiculos que estan dentro`() = withEnv { env ->
        env.checkIn(env.floorWithSpot("A-01"), plate = "ABC123")
        val salio = env.checkIn(env.floorWithSpot("A-02"), plate = "ABD999")
        env.parkingService.checkOut(salio, env.clock.now)

        assertEquals(listOf("ABC123"), env.parkingService.findInside("bc1").map { it.plate })
        assertEquals(emptyList(), env.parkingService.findInside("ABD"))
        assertEquals(emptyList(), env.parkingService.findInside("  "))
    }
}
