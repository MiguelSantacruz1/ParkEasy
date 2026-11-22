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
import kotlin.test.assertNull

class ParkingServiceTest {

    @Test
    fun `la entrada deja el puesto ocupado`() = withEnv { env ->
        val spotId = env.floorWithSpot()

        val recordId = env.checkIn(spotId)

        val spot = env.spots.getById(spotId)!!
        assertEquals(SpotStatus.OCCUPIED, spot.status)
        assertEquals(recordId, spot.currentRecordId)
    }

    @Test
    fun `guarda la placa en mayusculas y sin espacios, con puesto y piso`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot("A-01"), plate = " abc 123 ")

        val record = env.records.getById(recordId)!!
        assertEquals("ABC123", record.plate)
        assertEquals("A-01", record.spotLabel)
        assertEquals("Piso 1", record.floorName)
    }

    @Test
    fun `un puesto bloqueado no admite entradas`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        env.spotService.block(spotId, "Mantenimiento")

        assertFailsWith<DomainError.SpotBlocked> { env.checkIn(spotId) }
        assertNull(env.records.findOpenBySpot(spotId))
    }

    @Test
    fun `un puesto ocupado no admite otra entrada`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        env.checkIn(spotId, plate = "ABC123")

        assertFailsWith<DomainError.SpotOccupied> { env.checkIn(spotId, plate = "XYZ987") }
    }

    @Test
    fun `una placa no puede estar en dos puestos a la vez`() = withEnv { env ->
        val a = env.floorWithSpot("A-01")
        val b = env.floorWithSpot("A-02")
        env.checkIn(a, plate = "ABC123")

        val error = assertFailsWith<DomainError.PlateAlreadyParked> { env.checkIn(b, plate = "abc123") }

        assertEquals("A-01", error.spotLabel)
        assertEquals(SpotStatus.FREE, env.spots.getById(b)!!.status)
    }

    @Test
    fun `la placa no puede estar vacia`() = withEnv { env ->
        assertFailsWith<DomainError.BlankPlate> { env.checkIn(env.floorWithSpot(), plate = "   ") }
    }

    @Test
    fun `la hora de llegada no puede estar en el futuro`() = withEnv { env ->
        val spotId = env.floorWithSpot()

        assertFailsWith<DomainError.EntryInFuture> {
            env.checkIn(spotId, at = env.clock.now.plusSeconds(60))
        }
    }

    @Test
    fun `la llegada justo en este instante es valida`() = withEnv { env ->
        env.checkIn(env.floorWithSpot(), at = env.clock.now)
    }

    @Test
    fun `el tipo de vehiculo debe ser compatible con el puesto`() = withEnv { env ->
        val moto = env.floorWithSpot("M-01", SpotType.MOTORCYCLE)
        val carro = env.floorWithSpot("A-01", SpotType.CAR)

        assertFailsWith<DomainError.IncompatibleVehicle> { env.checkIn(moto, "ABC123", VehicleType.CAR) }
        assertFailsWith<DomainError.IncompatibleVehicle> { env.checkIn(carro, "ABC12D", VehicleType.MOTORCYCLE) }
        env.checkIn(moto, "ABC12D", VehicleType.MOTORCYCLE)
    }

    @Test
    fun `la salida deja el puesto libre y guarda hora, valor y tarifa`() = withEnv { env ->
        val spotId = env.floorWithSpot()
        val recordId = env.checkIn(spotId)
        val exit = env.clock.now
        val tariff = Tariff(VehicleType.CAR, 4_000, 1_500, 0)

        env.parkingService.checkOut(recordId, exit, 4_000, tariff)

        assertEquals(SpotStatus.FREE, env.spots.getById(spotId)!!.status)
        val record = env.records.getById(recordId)!!
        assertEquals(exit, record.exitTime)
        assertEquals(4_000, record.charged)
        assertEquals(4_000, record.tariffFirstHour)
    }

    @Test
    fun `la hora de salida no puede ser anterior a la llegada`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot())
        val entry = env.records.getById(recordId)!!.entryTime

        assertFailsWith<DomainError.ExitBeforeEntry> {
            env.parkingService.checkOut(recordId, entry.minus(Duration.ofMinutes(1)), 0, Tariff.defaults.first())
        }
        assertEquals(true, env.records.getById(recordId)!!.isOpen)
    }

    @Test
    fun `la salida en el mismo instante de la llegada es valida`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot())
        val entry = env.records.getById(recordId)!!.entryTime

        env.parkingService.checkOut(recordId, entry, 4_000, Tariff.defaults.first())
    }

    @Test
    fun `un registro cerrado no se puede cerrar otra vez`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot())
        env.parkingService.checkOut(recordId, env.clock.now, 4_000, Tariff.defaults.first())

        assertFailsWith<DomainError.RecordAlreadyClosed> {
            env.parkingService.checkOut(recordId, env.clock.now, 4_000, Tariff.defaults.first())
        }
    }

    @Test
    fun `tras salir, la misma placa puede entrar en otro puesto`() = withEnv { env ->
        val a = env.floorWithSpot("A-01")
        val b = env.floorWithSpot("A-02")
        val first = env.checkIn(a)
        env.parkingService.checkOut(first, env.clock.now, 4_000, Tariff.defaults.first())

        env.checkIn(b, at = env.clock.now)

        assertEquals(SpotStatus.OCCUPIED, env.spots.getById(b)!!.status)
        assertEquals(SpotStatus.FREE, env.spots.getById(a)!!.status)
    }

    @Test
    fun `entrada y salida quedan en la bitacora`() = withEnv { env ->
        val recordId = env.checkIn(env.floorWithSpot())
        env.parkingService.checkOut(recordId, env.clock.now, 4_000, Tariff.defaults.first())

        val actions = env.changeLog.getRecent(10).map { it.action }
        assertEquals(true, ChangeAction.VEHICLE_ENTERED in actions)
        assertEquals(true, ChangeAction.VEHICLE_EXITED in actions)
    }
}
