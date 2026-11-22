package com.ejemplo.parkeasy.data.db

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.nio.file.Path
import java.util.Properties

/**
 * Abre la base SQLite. Si el archivo es nuevo crea el esquema, y si es de una
 * versión anterior aplica las migraciones (.sqm) según PRAGMA user_version.
 */
object DatabaseFactory {

    fun createDriver(file: Path): SqlDriver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${file.toAbsolutePath()}",
        properties = Properties().apply { setProperty("foreign_keys", "true") },
        schema = ParkEasyDatabase.Schema,
    )

    /** Base en memoria, para pruebas. */
    fun createInMemoryDriver(): SqlDriver = JdbcSqliteDriver(
        url = JdbcSqliteDriver.IN_MEMORY,
        properties = Properties().apply { setProperty("foreign_keys", "true") },
        schema = ParkEasyDatabase.Schema,
    )

    fun create(driver: SqlDriver): ParkEasyDatabase = ParkEasyDatabase(driver)
}
