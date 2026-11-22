package com.ejemplo.parkeasy.presentation

import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.presentation.common.MessageCenter
import com.ejemplo.parkeasy.presentation.history.HistoryTab
import com.ejemplo.parkeasy.presentation.history.HistoryViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HistoryViewModelTest {

    private fun withVm(block: suspend (TestEnv, HistoryViewModel) -> Unit) = TestEnv().use { env ->
        val vm = HistoryViewModel(env.records, env.changeLog, env.clock, MessageCenter())
        try {
            runBlocking { block(env, vm) }
        } finally {
            vm.dispose()
        }
    }

    private suspend fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5_000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Tiempo agotado" }
            delay(10)
        }
    }

    @Test
    fun `lista y filtra los registros`() = withVm { env, vm ->
        val a = env.floorWithSpot("A-01")
        env.checkIn(a, plate = "ABC123")
        vm.reload()
        assertEquals(1, vm.state.value.rows.size)
        vm.setFilter { it.copy(plate = "zzz") }
        assertEquals(0, vm.state.value.rows.size)
        assertEquals(1, vm.state.value.totalRecords)
        vm.clearFilters()
        assertEquals(1, vm.state.value.rows.size)
    }

    @Test
    fun `exporta los registros filtrados con BOM y punto y coma`() = withVm { env, vm ->
        env.checkIn(env.floorWithSpot("A-01"), plate = "ABC123")
        vm.reload()
        val file = Files.createTempFile("parkeasy", ".csv")
        try {
            vm.export(file)
            waitFor { Files.size(file) > 0 }
            val text = Files.readString(file)
            assertTrue(text.startsWith("﻿Placa;Tipo"))
            assertTrue("ABC123" in text)
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test
    fun `exporta la bitácora cuando esa pestaña está activa`() = withVm { env, vm ->
        env.floorWithSpot("A-01")
        vm.reload()
        vm.setTab(HistoryTab.CHANGES)
        val file = Files.createTempFile("parkeasy", ".csv")
        try {
            vm.export(file)
            waitFor { Files.size(file) > 0 }
            assertTrue("SPOT_CREATED" in Files.readString(file))
        } finally {
            Files.deleteIfExists(file)
        }
    }
}
