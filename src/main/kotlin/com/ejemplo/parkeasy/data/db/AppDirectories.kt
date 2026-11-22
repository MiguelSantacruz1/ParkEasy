package com.ejemplo.parkeasy.data.db

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/** Resuelve la carpeta de datos del usuario según el sistema operativo. */
object AppDirectories {
    private const val APP_FOLDER = "ParkEasy"

    fun dataDirectory(
        osName: String = System.getProperty("os.name").orEmpty(),
        env: (String) -> String? = System::getenv,
        userHome: String = System.getProperty("user.home").orEmpty(),
    ): Path {
        val os = osName.lowercase()
        val base = when {
            "win" in os -> env("APPDATA")?.takeIf { it.isNotBlank() }
                ?: Paths.get(userHome, "AppData", "Roaming").toString()
            "mac" in os -> Paths.get(userHome, "Library", "Application Support").toString()
            else -> env("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
                ?: Paths.get(userHome, ".local", "share").toString()
        }
        return Paths.get(base, APP_FOLDER)
    }

    /** Con la propiedad parkeasy.dataDir la carpeta de datos se cambia (mediciones y pruebas). */
    fun effectiveDirectory(): Path = System.getProperty("parkeasy.dataDir")?.takeIf { it.isNotBlank() }?.let(Paths::get) ?: dataDirectory()

    /** Ruta del archivo de la base de datos del perfil. Crea la carpeta si no existe. */
    fun databaseFile(directory: Path = effectiveDirectory(), profile: AppProfile = AppProfile.current()): Path {
        Files.createDirectories(directory)
        return directory.resolve(profile.databaseName)
    }
}
