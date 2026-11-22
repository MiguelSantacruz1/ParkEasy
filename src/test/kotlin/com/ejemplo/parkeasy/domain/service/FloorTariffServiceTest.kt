package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FloorTariffServiceTest {

    @Test
    fun `renombra un piso y rechaza nombres vacios`() = withEnv { env ->
        val id = env.floorService.add("Piso 1")

        env.floorService.rename(id, " Planta baja ")
        assertEquals("Planta baja", env.floors.getById(id)!!.name)
        assertFailsWith<DomainError.BlankFloorName> { env.floorService.rename(id, "  ") }
    }

    @Test
    fun `sube y baja pisos en la lista`() = withEnv { env ->
        val a = env.floorService.add("Sótano")
        val b = env.floorService.add("Piso 1")
        val c = env.floorService.add("Piso 2")

        env.floorService.move(c, -1)
        assertEquals(listOf("Sótano", "Piso 2", "Piso 1"), env.floors.getAll().map { it.name })

        env.floorService.move(a, +1)
        assertEquals(listOf("Piso 2", "Sótano", "Piso 1"), env.floors.getAll().map { it.name })

        env.floorService.move(b, +1)
        assertEquals(listOf("Piso 2", "Sótano", "Piso 1"), env.floors.getAll().map { it.name })
    }

    @Test
    fun `guarda una tarifa nueva y la deja en la bitacora`() = withEnv { env ->
        env.tariffService.save(Tariff(VehicleType.MOTORCYCLE, 3_000, 1_200, 5))

        assertEquals(Tariff(VehicleType.MOTORCYCLE, 3_000, 1_200, 5), env.tariffs.get(VehicleType.MOTORCYCLE))
        assertEquals(1, env.changeLog.getRecent(5).count { it.detail.contains("MOTORCYCLE") })
    }

    @Test
    fun `rechaza valores negativos en la tarifa`() = withEnv { env ->
        assertFailsWith<DomainError.InvalidTariff> { env.tariffService.save(Tariff(VehicleType.CAR, -1, 1_500)) }
        assertFailsWith<DomainError.InvalidTariff> { env.tariffService.save(Tariff(VehicleType.CAR, 4_000, 1_500, -5)) }
        assertEquals(4_000, env.tariffs.get(VehicleType.CAR)!!.firstHour)
    }
}
