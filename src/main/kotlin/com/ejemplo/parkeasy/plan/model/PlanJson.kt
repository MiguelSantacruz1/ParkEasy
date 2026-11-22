package com.ejemplo.parkeasy.plan.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Sobre versionado: si el formato cambia, se sube [CURRENT_VERSION] y se migra al leer. */
@Serializable
private data class StoredItem(val version: Int, val item: PlanItem)

object PlanJson {
    const val CURRENT_VERSION = 1

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        classDiscriminator = "tipo"
    }

    fun encode(item: PlanItem): String = json.encodeToString(StoredItem(CURRENT_VERSION, item))

    /** Falla con [IllegalArgumentException] si el texto no es un elemento válido o su versión es más nueva que la de la app. */
    fun decode(text: String): PlanItem {
        val stored = try {
            json.decodeFromString<StoredItem>(text)
        } catch (e: Exception) {
            throw IllegalArgumentException("Elemento del plano ilegible: ${e.message}", e)
        }
        require(stored.version <= CURRENT_VERSION) {
            "El plano fue guardado con una versión más nueva (${stored.version}) que la de esta app"
        }
        return stored.item
    }
}
