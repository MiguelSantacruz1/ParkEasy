package com.ejemplo.parkeasy.data

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ejemplo.parkeasy.TestEnv
import com.ejemplo.parkeasy.data.backup.BackupService
import com.ejemplo.parkeasy.domain.DomainError
import com.ejemplo.parkeasy.domain.model.NewSpot
import com.ejemplo.parkeasy.domain.model.SpotType
import com.ejemplo.parkeasy.withEnv
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BackupServiceTest {

    private fun TestEnv.service(dir: Path) = BackupService(db, driver, dir.resolve("copias"), clock)

    private fun tempDir(): Path = Files.createTempDirectory("parkeasy-backup")

    @Test
    fun `copia y restaura todos los datos`() = withEnv { env ->
        val dir = tempDir()
        val a = env.floorWithSpot("A-01")
        env.checkIn(a, plate = "ABC123")
        val backup = dir.resolve("copia.db")
        env.service(dir).backupTo(backup)
        assertTrue(Files.size(backup) > 0)

        // Después de la copia el dueño cambia cosas
        val floorId = env.floors.getAll().first().id
        env.spotService.add(NewSpot(floorId, "B-09", SpotType.CAR))
        env.parkingService.checkOut(env.records.getOpen().single().id, env.clock.now)
        assertNotNull(env.spots.findByLabel("B-09"))

        val summary = env.service(dir).restoreFrom(backup)

        assertNull(env.spots.findByLabel("B-09"))
        assertNotNull(env.spots.findByLabel("A-01"))
        assertEquals("ABC123", env.records.getOpen().single().plate)
        assertEquals(1, summary.rows.getValue("Spot"))
        assertTrue(Files.exists(summary.safetyCopy), "debe quedar una copia de lo que había antes")
    }

    @Test
    fun `la copia de seguridad previa conserva lo que se iba a perder`() = withEnv { env ->
        val dir = tempDir()
        env.floorWithSpot("A-01")
        val backup = dir.resolve("copia.db")
        env.service(dir).backupTo(backup)
        env.spotService.add(NewSpot(env.floors.getAll().first().id, "B-09", SpotType.CAR))

        val summary = env.service(dir).restoreFrom(backup)

        // Restaurar la copia previa devuelve B-09
        env.service(dir).restoreFrom(summary.safetyCopy)
        assertNotNull(env.spots.findByLabel("B-09"))
    }

    @Test
    fun `rechaza archivos que no son una base de PARKEASY`() = withEnv { env ->
        val dir = tempDir()
        val text = dir.resolve("texto.db").also { Files.writeString(it, "esto no es sqlite") }
        assertFailsWith<DomainError.InvalidBackup> { env.service(dir).restoreFrom(text) }
        assertFailsWith<DomainError.InvalidBackup> { env.service(dir).restoreFrom(dir.resolve("no-existe.db")) }

        val other = dir.resolve("otra.db")
        JdbcSqliteDriver("jdbc:sqlite:$other").also {
            it.execute(null, "CREATE TABLE Cosas (id INTEGER)", 0)
            it.execute(null, "PRAGMA user_version = 3", 0)
            it.close()
        }
        assertFailsWith<DomainError.InvalidBackup> { env.service(dir).restoreFrom(other) }
    }

    @Test
    fun `rechaza copias de una versión más nueva y no toca los datos`() = withEnv { env ->
        val dir = tempDir()
        env.floorWithSpot("A-01")
        val future = dir.resolve("futuro.db")
        JdbcSqliteDriver("jdbc:sqlite:$future").also {
            it.execute(null, "CREATE TABLE Setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
            it.execute(null, "PRAGMA user_version = 99", 0)
            it.close()
        }
        assertFailsWith<DomainError.InvalidBackup> { env.service(dir).restoreFrom(future) }
        assertNotNull(env.spots.findByLabel("A-01"))
    }

    @Test
    fun `una copia de una versión anterior se migra y se restaura`() = withEnv { env ->
        val dir = tempDir()
        env.floorWithSpot("A-01")
        val old = dir.resolve("v1.db")
        JdbcSqliteDriver("jdbc:sqlite:$old").also {
            it.execute(null, "CREATE TABLE Setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
            it.execute(null, "INSERT INTO Setting(key, value) VALUES ('nombre', 'Central')", 0)
            it.execute(null, "PRAGMA user_version = 1", 0)
            it.close()
        }
        val summary = env.service(dir).restoreFrom(old)
        assertEquals(1, summary.rows.getValue("Setting"))
        assertEquals(0, summary.rows.getValue("Spot"))
        assertNull(env.spots.findByLabel("A-01"))
    }

    @Test
    fun `hacer la copia sobre un archivo existente lo reemplaza`() = withEnv { env ->
        val dir = tempDir()
        env.floorWithSpot("A-01")
        val target = dir.resolve("copia.db")
        Files.writeString(target, "viejo")
        env.service(dir).backupTo(target)
        assertTrue(Files.size(target) > 100)
    }
}
