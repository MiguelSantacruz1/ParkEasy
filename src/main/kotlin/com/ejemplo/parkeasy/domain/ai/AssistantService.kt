package com.ejemplo.parkeasy.domain.ai

import com.ejemplo.parkeasy.domain.repository.FloorRepository
import com.ejemplo.parkeasy.domain.repository.ParkingRecordRepository
import com.ejemplo.parkeasy.domain.repository.SettingRepository
import com.ejemplo.parkeasy.domain.repository.SpotRepository
import com.ejemplo.parkeasy.domain.repository.TariffRepository
import com.ejemplo.parkeasy.domain.service.AppSettings
import java.time.Clock

/** Ajustes del asistente. Viene apagado: hasta que el administrador pone su clave no sale nada del equipo. */
class AiSettings(private val settings: SettingRepository) {
    var apiKey: String
        get() = settings.get(KEY).orEmpty()
        set(value) = if (value.isBlank()) settings.remove(KEY) else settings.set(KEY, value.trim())

    var model: String
        get() = settings.get(MODEL)?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL
        set(value) = settings.set(MODEL, value.trim())

    val enabled: Boolean get() = apiKey.isNotBlank()

    companion object {
        const val KEY = "ai.gemini.key"
        const val MODEL = "ai.gemini.model"
        const val DEFAULT_MODEL = "gemini-2.5-flash"
    }
}

/**
 * Responde preguntas sobre la operación con un modelo de lenguaje. Cada vez arma un resumen nuevo con
 * [ParkingSummary] (sin placas) y se lo da al modelo como contexto.
 */
class AssistantService(
    private val client: AiClient,
    private val floors: FloorRepository,
    private val spots: SpotRepository,
    private val records: ParkingRecordRepository,
    private val tariffs: TariffRepository,
    private val appSettings: AppSettings,
    private val clock: Clock,
) {
    fun systemPrompt(): String = INSTRUCTIONS + "\n\n" + ParkingSummary.build(
        clock, appSettings.businessName, floors.getAll(), spots.getAll(), records.getAll(), tariffs.getAll(),
    )

    /** [history] termina con la pregunta nueva del usuario. */
    fun ask(history: List<ChatTurn>): String = client.reply(systemPrompt(), history.takeLast(MAX_TURNS))

    companion object {
        const val MAX_TURNS = 10
        const val INSTRUCTIONS = "Eres el asistente de un administrador de parqueadero en Colombia. Responde en español, " +
            "breve y claro, usando solo los datos del resumen de abajo. Si el resumen no alcanza para responder, dilo " +
            "en vez de inventar. Los valores de dinero están en pesos colombianos (COP). No tienes placas ni datos de " +
            "personas y no debes pedirlos. No des asesoría legal ni tributaria."
    }
}
