package com.ejemplo.parkeasy.data.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.ejemplo.parkeasy.data.db.DatabaseFactory
import com.ejemplo.parkeasy.data.db.ParkEasyDatabase
import com.ejemplo.parkeasy.domain.DomainError
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Cuántas filas quedaron por tabla después de restaurar. */
data class RestoreSummary(val rows: Map<String, Int>, val safetyCopy: Path)

/**
 * Copias de seguridad de toda la base. Una copia es un archivo SQLite completo: se puede abrir
 * con cualquier herramienta y no depende de la versión de la app que la hizo (si es más vieja se migra).
 */
class BackupService(
    private val db: ParkEasyDatabase,
    private val driver: SqlDriver,
    private val safetyDirectory: Path,
    private val clock: Clock,
) {
    /** Crea [target] como copia consistente de la base abierta (VACUUM INTO). Reemplaza si ya existe. */
    fun backupTo(target: Path) {
        target.toAbsolutePath().parent?.let(Files::createDirectories)
        Files.deleteIfExists(target)
        driver.execute(null, "VACUUM INTO ?", 1) { bindString(0, target.toAbsolutePath().toString()) }
    }

    /**
     * Reemplaza todos los datos por los de [source]. Antes guarda una copia de seguridad de lo actual
     * en la carpeta de copias. Si algo falla, la transacción se deshace y los datos quedan como estaban.
     */
    fun restoreFrom(source: Path): RestoreSummary {
        if (!Files.isRegularFile(source)) throw DomainError.InvalidBackup("el archivo no existe")
        checkIsOurDatabase(source)

        val work = Files.createTempFile("parkeasy-restaurar", ".db")
        try {
            Files.copy(source, work, StandardCopyOption.REPLACE_EXISTING)
            // Si la copia es de una versión anterior, esto la lleva al esquema actual.
            DatabaseFactory.createDriver(work).close()

            val safety = safetyDirectory.resolve("antes-de-restaurar-${stamp()}.db")
            backupTo(safety)

            driver.execute(null, "ATTACH DATABASE ? AS copia", 1) { bindString(0, work.toAbsolutePath().toString()) }
            try {
                val counts = linkedMapOf<String, Int>()
                db.transaction {
                    TABLES.asReversed().forEach { driver.execute(null, "DELETE FROM main.$it", 0) }
                    TABLES.forEach { table ->
                        val columns = columnsOf(table).joinToString(", ")
                        driver.execute(null, "INSERT INTO main.$table ($columns) SELECT $columns FROM copia.$table", 0)
                        counts[table] = countRows("main.$table")
                    }
                }
                driver.notifyListeners(*TABLES.toTypedArray())
                return RestoreSummary(counts, safety)
            } finally {
                driver.execute(null, "DETACH DATABASE copia", 0)
            }
        } finally {
            Files.deleteIfExists(work)
        }
    }

    private fun checkIsOurDatabase(file: Path) {
        val raw = JdbcSqliteDriver("jdbc:sqlite:${file.toAbsolutePath()}")
        try {
            val version = raw.query("PRAGMA user_version") { it.getLong(0) ?: 0L }
            val tables = raw.queryAll("SELECT name FROM sqlite_master WHERE type = 'table'") { it.getString(0).orEmpty() }.toSet()
            val current = ParkEasyDatabase.Schema.version
            if (version < 1 || !tables.contains("Setting")) throw DomainError.InvalidBackup("no es una base de PARKEASY")
            if (version > current) throw DomainError.InvalidBackup("fue hecha con una versión más nueva de la app (esquema $version)")
        } catch (e: DomainError) {
            throw e
        } catch (e: Exception) {
            throw DomainError.InvalidBackup("el archivo no es una base de datos válida")
        } finally {
            raw.close()
        }
    }

    private fun columnsOf(table: String): List<String> =
        driver.queryAll("PRAGMA main.table_info($table)") { it.getString(1).orEmpty() }

    private fun countRows(table: String): Int = driver.query("SELECT COUNT(*) FROM $table") { it.getLong(0)?.toInt() ?: 0 }

    private fun stamp() = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(ZonedDateTime.now(clock))

    private fun <T> SqlDriver.query(sql: String, read: (app.cash.sqldelight.db.SqlCursor) -> T): T =
        executeQuery(null, sql, { c -> c.next(); QueryResult.Value(read(c)) }, 0).value

    private fun <T> SqlDriver.queryAll(sql: String, read: (app.cash.sqldelight.db.SqlCursor) -> T): List<T> =
        executeQuery(null, sql, { c ->
            val out = mutableListOf<T>()
            while (c.next().value) out += read(c)
            QueryResult.Value(out)
        }, 0).value

    companion object {
        /** Orden para insertar: primero lo que otras tablas referencian. Para borrar se usa al revés. */
        val TABLES = listOf("Setting", "Tariff", "Floor", "Spot", "ParkingRecord", "PlanElement", "ChangeLog")
    }
}
