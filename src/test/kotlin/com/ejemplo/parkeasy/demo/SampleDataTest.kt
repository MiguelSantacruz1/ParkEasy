package com.ejemplo.parkeasy.demo

import com.ejemplo.parkeasy.data.db.AppProfile
import com.ejemplo.parkeasy.domain.model.SpotStatus
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SampleDataTest {
    private fun sample(env: com.ejemplo.parkeasy.TestEnv) = SampleData(
        env.db, env.driver, env.floors, env.spots, env.floorService, env.spotService,
        env.parkingService, env.planPersistence, com.ejemplo.parkeasy.data.repository.SqlSettingRepository(env.db), env.clock,
    )

    @Test
    fun `la demo crea dos pisos con puestos, historial y vehiculos dentro`() = withEnv { env ->
        assertTrue(sample(env).seedIfEmpty(AppProfile.DEMO))
        assertEquals(2, env.floors.getAll().size)
        assertEquals(60, env.spots.getAll().size)
        assertEquals(12, env.records.getAll().count { it.exitTime != null })
        assertEquals(15, env.records.getOpen().size)
        assertTrue(env.records.getAll().all { it.plate.startsWith("TST") && it.notes == SampleData.TEST_DATA })
        assertEquals(2, env.spots.getAll().count { it.blocked })
    }

    @Test
    fun `no carga nada en produccion ni si ya hay datos`() = withEnv { env ->
        val s = sample(env)
        assertFalse(s.seedIfEmpty(AppProfile.PRODUCTION))
        assertTrue(env.floors.getAll().isEmpty())
        s.seedIfEmpty(AppProfile.DEMO)
        assertFalse(s.seedIfEmpty(AppProfile.DEMO))
        assertEquals(2, env.floors.getAll().size)
    }

    @Test
    fun `restablecer deja los mismos datos sin duplicar`() = withEnv { env ->
        val s = sample(env)
        s.seedIfEmpty(AppProfile.DEMO)
        s.reset(AppProfile.DEMO)
        assertEquals(2, env.floors.getAll().size)
        assertEquals(60, env.spots.getAll().size)
        assertEquals(15, env.records.getOpen().size)
    }

    @Test
    fun `restablecer se niega en produccion`() = withEnv { env ->
        val failed = runCatching { sample(env).reset(AppProfile.PRODUCTION) }.isFailure
        assertTrue(failed)
    }
}
