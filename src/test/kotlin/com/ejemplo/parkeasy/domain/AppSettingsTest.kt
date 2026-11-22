package com.ejemplo.parkeasy.domain

import com.ejemplo.parkeasy.data.repository.SqlSettingRepository
import com.ejemplo.parkeasy.domain.service.AppSettings
import com.ejemplo.parkeasy.domain.service.ThemeMode
import com.ejemplo.parkeasy.withEnv
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class AppSettingsTest {

    @Test
    fun `el tema por defecto sigue al sistema y se recuerda`() = withEnv { env ->
        val repo = SqlSettingRepository(env.db)
        val settings = AppSettings(repo)
        assertEquals(ThemeMode.SYSTEM, settings.theme.value)
        settings.setTheme(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, settings.theme.value)
        assertEquals(ThemeMode.DARK, AppSettings(repo).theme.value)
    }

    @Test
    fun `un tema guardado inválido vuelve al del sistema`() = withEnv { env ->
        val repo = SqlSettingRepository(env.db)
        repo.set("tema", "NEON")
        assertEquals(ThemeMode.SYSTEM, AppSettings(repo).theme.value)
    }

    @Test
    fun `el PIN se valida y no se guarda en claro`() = withEnv { env ->
        val repo = SqlSettingRepository(env.db)
        val settings = AppSettings(repo)
        assertFalse(settings.hasPin)
        assertTrue(settings.verifyPin("cualquiera"), "sin PIN, todo pasa")

        assertFailsWith<IllegalArgumentException> { settings.setPin("12") }
        assertFailsWith<IllegalArgumentException> { settings.setPin("12ab") }
        settings.setPin("2468")
        assertTrue(settings.hasPin)
        assertTrue(settings.verifyPin("2468"))
        assertFalse(settings.verifyPin("1111"))
        assertNotEquals("2468", repo.get("pin_hash"))
        assertFalse(repo.get("pin_hash")!!.contains("2468"))
    }

    @Test
    fun `quitar el PIN exige el actual`() = withEnv { env ->
        val settings = AppSettings(SqlSettingRepository(env.db))
        settings.setPin("2468")
        assertFalse(settings.clearPin("0000"))
        assertTrue(settings.hasPin)
        assertTrue(settings.clearPin("2468"))
        assertFalse(settings.hasPin)
    }

    @Test
    fun `dos PIN iguales no dan el mismo hash gracias a la sal`() = withEnv { env ->
        val repo = SqlSettingRepository(env.db)
        val settings = AppSettings(repo)
        settings.setPin("2468"); val first = repo.get("pin_hash")
        settings.setPin("2468"); val second = repo.get("pin_hash")
        assertNotEquals(first, second)
    }

    @Test
    fun `primer uso y nombre del parqueadero`() = withEnv { env ->
        val settings = AppSettings(SqlSettingRepository(env.db))
        assertFalse(settings.onboardingDone)
        settings.businessName = "  Parqueadero Central "
        settings.markOnboardingDone()
        assertTrue(settings.onboardingDone)
        assertEquals("Parqueadero Central", settings.businessName)
    }
}
