package com.ejemplo.parkeasy.domain.service

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.ChangeAction
import com.ejemplo.parkeasy.withEnv
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RetentionServiceTest {
    private fun TestEnv.service() = RetentionService(records, logger, transactor, clock)
    private fun TestEnv.today(): LocalDate = LocalDate.now(clock.withZone(ZoneOffset.UTC))

    private fun TestEnv.closedStay(label: String, plate: String, daysAgo: Long) {
        val spot = floorWithSpot(label)
        val entry = clock.now.minusSeconds(daysAgo * 86_400 + 7_200)
        val id = checkIn(spot, plate = plate, at = entry)
        parkingService.checkOut(id, entry.plusSeconds(3_600))
    }

    @Test
    fun `borra los cerrados anteriores a la fecha y conserva el resto`() = withEnv { env ->
        env.closedStay("A-01", "OLD111", daysAgo = 400)
        env.closedStay("A-02", "OLD222", daysAgo = 300)
        env.closedStay("A-03", "NEW333", daysAgo = 5)
        val cutoff = env.today().minusDays(100)

        assertEquals(2, env.service().preview(cutoff))
        assertEquals(2, env.service().purge(cutoff))
        assertEquals(listOf("NEW333"), env.records.getAll().map { it.plate })
    }

    @Test
    fun `nunca borra a los vehículos que siguen dentro`() = withEnv { env ->
        val spot = env.floorWithSpot("A-01")
        env.checkIn(spot, plate = "INS111", at = env.clock.now.minusSeconds(400L * 86_400))
        assertEquals(0, env.service().purge(env.today().minusDays(1)))
        assertEquals(1, env.records.getOpen().size)
    }

    @Test
    fun `deja constancia en la bitácora`() = withEnv { env ->
        env.closedStay("A-01", "OLD111", daysAgo = 400)
        env.service().purge(env.today().minusDays(100))
        assertTrue(env.changeLog.getRecent(20).any { it.action == ChangeAction.RECORDS_PURGED })
    }

    @Test
    fun `rechaza fechas de hoy o futuras`() = withEnv { env ->
        assertFailsWith<DomainError.InvalidPurgeDate> { env.service().purge(env.today()) }
        assertFailsWith<DomainError.InvalidPurgeDate> { env.service().preview(env.today().plusDays(3)) }
    }
}