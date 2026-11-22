package com.ejemplo.parkeasy.data.db

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DatabaseFactoryTest {

    @Test
    fun `la base nueva guarda y lee un ajuste`() {
        val driver = DatabaseFactory.createInMemoryDriver()
        val db = DatabaseFactory.create(driver)

        db.settingQueries.upsert("nombre", "Parqueadero Central")

        assertEquals("Parqueadero Central", db.settingQueries.selectByKey("nombre").executeAsOneOrNull())
        assertNull(db.settingQueries.selectByKey("inexistente").executeAsOneOrNull())
        driver.close()
    }

    @Test
    fun `los datos persisten al cerrar y volver a abrir el archivo`() {
        val dir = Files.createTempDirectory("parkeasy-test")
        val file = AppDirectories.databaseFile(dir)

        val primero = DatabaseFactory.createDriver(file)
        DatabaseFactory.create(primero).settingQueries.upsert("tema", "oscuro")
        primero.close()

        val segundo = DatabaseFactory.createDriver(file)
        val valor = DatabaseFactory.create(segundo).settingQueries.selectByKey("tema").executeAsOneOrNull()
        segundo.close()

        assertEquals("oscuro", valor)
        dir.toFile().deleteRecursively()
    }
}
