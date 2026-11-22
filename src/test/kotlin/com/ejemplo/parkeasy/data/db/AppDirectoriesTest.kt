package com.ejemplo.parkeasy.data.db

import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertEquals

class AppDirectoriesTest {

    @Test
    fun `en Windows usa APPDATA`() {
        val dir = AppDirectories.dataDirectory(
            osName = "Windows 11",
            env = { if (it == "APPDATA") "C:\\Users\\ana\\AppData\\Roaming" else null },
            userHome = "C:\\Users\\ana",
        )
        assertEquals(Paths.get("C:\\Users\\ana\\AppData\\Roaming", "ParkEasy"), dir)
    }

    @Test
    fun `en Windows sin APPDATA cae a la carpeta del usuario`() {
        val dir = AppDirectories.dataDirectory("Windows 10", { null }, "C:\\Users\\ana")
        assertEquals(Paths.get("C:\\Users\\ana", "AppData", "Roaming", "ParkEasy"), dir)
    }

    @Test
    fun `en Linux usa XDG_DATA_HOME o local share`() {
        val conXdg = AppDirectories.dataDirectory("Linux", { if (it == "XDG_DATA_HOME") "/datos" else null }, "/home/ana")
        assertEquals(Paths.get("/datos", "ParkEasy"), conXdg)
        val sinXdg = AppDirectories.dataDirectory("Linux", { null }, "/home/ana")
        assertEquals(Paths.get("/home/ana", ".local", "share", "ParkEasy"), sinXdg)
    }

    @Test
    fun `en macOS usa Application Support`() {
        val dir = AppDirectories.dataDirectory("Mac OS X", { null }, "/Users/ana")
        assertEquals(Paths.get("/Users/ana", "Library", "Application Support", "ParkEasy"), dir)
    }
}
