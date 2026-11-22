package com.ejemplo.parkeasy.data.repository

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.withEnv
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.domain.model.ChangeEntity
import com.ejemplo.parkeasy.domain.model.NewChangeLogEntry
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TariffChangeLogTransactorTest {

    @Test
    fun `la base nueva trae las tarifas iniciales de carro y moto`() = withEnv { env ->
        assertEquals(Tariff(VehicleType.CAR, 4_000, 1_500, 0), env.tariffs.get(VehicleType.CAR))
        assertEquals(Tariff(VehicleType.MOTORCYCLE, 2_500, 1_000, 0), env.tariffs.get(VehicleType.MOTORCYCLE))
    }

    @Test
    fun `guardar una tarifa la reemplaza`() = withEnv { env ->
        env.tariffs.save(Tariff(VehicleType.CAR, 5_000, 2_000, 10))

        assertEquals(Tariff(VehicleType.CAR, 5_000, 2_000, 10), env.tariffs.get(VehicleType.CAR))
        assertEquals(2, env.tariffs.getAll().size)
    }

    @Test
    fun `la bitacora devuelve primero lo mas reciente`() = withEnv { env ->
        env.changeLog.append(NewChangeLogEntry(Instant.parse("2026-11-22T10:00:00Z"), ChangeAction.FLOOR_CREATED, ChangeEntity.FLOOR, 1, "Piso 1"))
        env.changeLog.append(NewChangeLogEntry(Instant.parse("2026-11-22T12:00:00Z"), ChangeAction.SPOT_CREATED, ChangeEntity.SPOT, 7, "A-01"))
        env.changeLog.append(NewChangeLogEntry(Instant.parse("2026-11-22T11:00:00Z"), ChangeAction.SPOT_BLOCKED, ChangeEntity.SPOT, 7, "A-01"))

        val recent = env.changeLog.getRecent(2)

        assertEquals(listOf(ChangeAction.SPOT_CREATED, ChangeAction.SPOT_BLOCKED), recent.map { it.action })
    }

    @Test
    fun `si la transaccion falla no queda nada guardado`() = withEnv { env ->
        assertFailsWith<IllegalStateException> {
            env.transactor.run {
                env.floors.insert(NewFloor("Piso 1", 0))
                error("fallo a mitad de camino")
            }
        }

        assertEquals(0, env.floors.getAll().size)
    }

    @Test
    fun `si la transaccion termina bien se guarda todo`() = withEnv { env ->
        env.transactor.run {
            env.floors.insert(NewFloor("Piso 1", 0))
            env.floors.insert(NewFloor("Piso 2", 1))
        }

        assertEquals(2, env.floors.getAll().size)
    }
}
