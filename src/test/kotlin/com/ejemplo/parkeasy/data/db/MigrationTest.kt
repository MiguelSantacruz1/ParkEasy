package com.ejemplo.parkeasy.data.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ejemplo.parkeasy.domain.model.NewFloor
import com.ejemplo.parkeasy.domain.model.Tariff
import com.ejemplo.parkeasy.domain.model.VehicleType
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

class MigrationTest {

    /** Crea un archivo con el esquema de la versión 1: solo la tabla Setting. */
    private fun createVersion1File(dir: Path): Path {
        val file = dir.resolve("viejo.db")
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        driver.execute(null, "CREATE TABLE Setting (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO Setting(key, value) VALUES ('nombre', 'Parqueadero Central')", 0)
        driver.execute(null, "PRAGMA user_version = 1", 0)
        driver.close()
        return file
    }

    private fun SqlDriver.schemaObjects(): List<String> = executeQuery(
        null,
        "SELECT name FROM sqlite_master WHERE type IN ('table','index') AND name NOT LIKE 'sqlite_%' ORDER BY name",
        { cursor ->
            val names = mutableListOf<String>()
            while (cursor.next().value) names += cursor.getString(0)!!
            QueryResult.Value(names)
        },
        0,
    ).value

    private fun SqlDriver.userVersion(): Long = executeQuery(
        null, "PRAGMA user_version",
        { cursor -> cursor.next(); QueryResult.Value(cursor.getLong(0)!!) },
        0,
    ).value

    @Test
    fun `migrar de la version 1 conserva los ajustes y crea las tablas nuevas`() {
        val dir = Files.createTempDirectory("parkeasy-migracion")
        val file = createVersion1File(dir)

        val driver = DatabaseFactory.createDriver(file)
        val db = DatabaseFactory.create(driver)

        assertEquals("Parqueadero Central", db.settingQueries.selectByKey("nombre").executeAsOneOrNull())
        assertEquals(ParkEasyDatabase.Schema.version, driver.userVersion())
        db.floorQueries.insert("Piso 1", 0, 60.0, 40.0)
        assertEquals(1, db.floorQueries.selectActive().executeAsList().size)
        driver.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `la base migrada tiene el mismo esquema que una base nueva`() {
        val dir = Files.createTempDirectory("parkeasy-migracion")
        val migrated = DatabaseFactory.createDriver(createVersion1File(dir))
        val fresh = DatabaseFactory.createInMemoryDriver()

        assertEquals(fresh.schemaObjects(), migrated.schemaObjects())

        migrated.close()
        fresh.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `la base migrada recibe las tarifas iniciales`() {
        val dir = Files.createTempDirectory("parkeasy-migracion")
        val driver = DatabaseFactory.createDriver(createVersion1File(dir))
        val db = DatabaseFactory.create(driver)

        val tariffs = db.tariffQueries.selectAll { type, first, extra, grace ->
            Tariff(VehicleType.valueOf(type), first, extra, grace.toInt())
        }.executeAsList()

        assertEquals(Tariff.defaults.toSet(), tariffs.toSet())
        driver.close()
        dir.toFile().deleteRecursively()
    }

    @Test
    fun `abrir dos veces una base ya migrada no cambia nada`() {
        val dir = Files.createTempDirectory("parkeasy-migracion")
        val file = AppDirectories.databaseFile(dir)

        val first = DatabaseFactory.createDriver(file)
        val db = DatabaseFactory.create(first)
        db.floorQueries.insert("Piso 1", 0, 60.0, 40.0)
        first.close()

        val second = DatabaseFactory.createDriver(file)
        val reopened = DatabaseFactory.create(second)
        assertEquals(1, reopened.floorQueries.selectActive().executeAsList().size)
        assertEquals(2, reopened.tariffQueries.selectAll().executeAsList().size)
        second.close()
        dir.toFile().deleteRecursively()
    }
}
