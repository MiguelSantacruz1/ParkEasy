package com.ejemplo.parkeasy.demo

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.data.db.AppProfile
import com.ejemplo.parkeasy.data.repository.SqlSettingRepository
import com.ejemplo.parkeasy.domain.rules.HistoryFilter
import com.ejemplo.parkeasy.domain.rules.RecordState
import com.ejemplo.parkeasy.withEnv
import java.time.ZoneId
import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Mide el trabajo de datos con el perfil de rendimiento: 500 puestos, 3000 registros cerrados y 150 dentro.
 * Los límites son holgados a propósito (la máquina de pruebas comparte CPU); los tiempos reales se imprimen.
 */
class PerformanceTest {
    private fun sample(env: TestEnv) = SampleData(
        env.db, env.driver, env.floors, env.spots, env.floorService, env.spotService,
        env.parkingService, env.planPersistence, SqlSettingRepository(env.db), env.clock,
    )

    @Test
    fun `500 puestos y 3000 registros se cargan, guardan y filtran con holgura`() = withEnv { env ->
        val seed = measureTimeMillis { sample(env).seedIfEmpty(AppProfile.PERF) }
        assertEquals(SampleData.LARGE_SPOTS, env.spots.getAll().size)
        assertEquals(SampleData.LARGE_HISTORY + 150, env.records.getAll().size)
        assertEquals(150, env.records.getOpen().size)

        val floorId = env.floors.getAll().single().id
        lateinit var doc: com.ejemplo.parkeasy.plan.model.PlanDocument
        val load = measureTimeMillis { doc = env.planPersistence.load(floorId)!! }
        assertEquals(SampleData.LARGE_SPOTS, doc.items.count { it.type == com.ejemplo.parkeasy.plan.model.ItemType.SPOT })
        val save = measureTimeMillis { env.planPersistence.save(doc) }

        val all = env.records.getAll()
        val filter = measureTimeMillis {
            repeat(20) { HistoryFilter(plate = "A", state = RecordState.CLOSED).apply(all, ZoneId.of("America/Bogota")) }
        } / 20

        println("PERF seed=${seed}ms load=${load}ms save=${save}ms filtro(3150 registros)=${filter}ms")
        assertTrue(load < 3_000, "cargar el plano tardó $load ms")
        assertTrue(save < 5_000, "guardar el plano tardó $save ms")
        assertTrue(filter < 500, "filtrar el historial tardó $filter ms")
    }
}
