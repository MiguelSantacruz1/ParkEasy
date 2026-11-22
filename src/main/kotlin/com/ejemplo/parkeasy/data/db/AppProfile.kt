package com.ejemplo.parkeasy.data.db

/**
 * Perfil de ejecución. En producción la app arranca vacía. Los perfiles `demo` y `perf` usan su propio
 * archivo de base de datos y cargan datos de prueba (TEST_DATA); nunca tocan la base de producción.
 *
 * Se elige con la propiedad `parkeasy.profile` (Gradle: `-Pdemo` o `-Pperf` con `run`).
 */
enum class AppProfile(val databaseName: String, val isTestData: Boolean) {
    PRODUCTION("parkeasy.db", false),
    DEMO("parkeasy-demo.db", true),
    PERF("parkeasy-perf.db", true),
    ;

    companion object {
        const val PROPERTY = "parkeasy.profile"

        fun from(value: String?): AppProfile = entries.firstOrNull { it.name.equals(value?.trim(), ignoreCase = true) } ?: PRODUCTION

        fun current(): AppProfile = from(System.getProperty(PROPERTY))
    }
}