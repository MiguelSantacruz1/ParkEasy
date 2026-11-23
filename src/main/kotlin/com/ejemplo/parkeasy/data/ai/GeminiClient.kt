package com.ejemplo.parkeasy.data.ai

import com.ejemplo.parkeasy.domain.ai.AiClient
import com.ejemplo.parkeasy.domain.ai.AiException
import com.ejemplo.parkeasy.domain.ai.ChatTurn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/** Modelos que se prueban, en orden, cuando el elegido no existe o está saturado. */
val DEFAULT_FALLBACKS = listOf("gemini-flash-latest", "gemini-2.5-flash-lite", "gemini-2.0-flash", "gemini-2.5-flash")

/**
 * Cliente de la API REST de Gemini (`generateContent`). La clave va en la cabecera `x-goog-api-key`, nunca
 * en la URL. [baseUrl] se puede cambiar para probar contra un servidor local.
 */
class GeminiClient(
    private val apiKey: () -> String,
    private val model: () -> String,
    private val baseUrl: String = "https://generativelanguage.googleapis.com/v1beta",
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
    private val attempts: Int = 3,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    private val fallbacks: List<String> = DEFAULT_FALLBACKS,
) : AiClient {

    private companion object {
        val RETRYABLE = setOf(500, 502, 503, 504)
    }

    override fun reply(system: String, history: List<ChatTurn>): String {
        val key = apiKey().trim()
        if (key.isEmpty()) throw AiException("Falta la clave de Gemini. Pégala en Ajustes → Asistente con IA.")
        if (history.isEmpty() || !history.last().fromUser) throw AiException("No hay ninguna pregunta que responder")

        val body = buildJsonObject {
            put("systemInstruction", buildJsonObject { put("parts", parts(system)) })
            put(
                "contents",
                buildJsonArray {
                    history.forEach { t ->
                        add(buildJsonObject { put("role", if (t.fromUser) "user" else "model"); put("parts", parts(t.text)) })
                    }
                },
            )
            put("generationConfig", buildJsonObject { put("temperature", 0.3) })
        }
        // Si un modelo no existe (404) o está saturado (500, 502, 503, 504 tras reintentar con pausas), se prueba el siguiente.
        val models = (listOf(model().trim()) + fallbacks).filter { it.isNotEmpty() }.distinct()
        var last: AiException? = null
        for ((i, m) in models.withIndex()) {
            val request = HttpRequest.newBuilder(URI.create("$baseUrl/models/$m:generateContent"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", key)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build()
            val maxAttempts = if (i == 0) attempts else minOf(attempts, 2)
            var attempt = 1
            while (true) {
                val response = try {
                    http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                } catch (e: IOException) {
                    throw AiException("No hay conexión con Gemini. Revisa el internet e intenta de nuevo.")
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw AiException("La consulta se interrumpió")
                }
                val status = response.statusCode()
                if (status in RETRYABLE && attempt < maxAttempts) {
                    pause(1_000L * attempt)
                    attempt++
                    continue
                }
                if (status == 404 || status in RETRYABLE) {
                    last = AiException(errorMessage(status, response.body()))
                    break
                }
                return parse(status, response.body())
            }
        }
        val tried = models.joinToString(", ")
        throw AiException("${last?.message ?: "Gemini no respondió"} Modelos probados: $tried.")
    }
    private fun parts(text: String): JsonArray = buildJsonArray { add(buildJsonObject { put("text", text) }) }

    internal fun parse(status: Int, body: String): String {
        if (status != 200) throw AiException(errorMessage(status, body))
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: throw AiException("Gemini respondió algo que no se entiende")
        val text = root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
            ?.get("content")?.jsonObject?.get("parts")?.jsonArray
            ?.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString("")
        if (text.isNullOrBlank()) {
            val blocked = root["promptFeedback"]?.jsonObject?.get("blockReason")
            throw AiException(if (blocked != null) "Gemini no quiso responder esa pregunta" else "Gemini no devolvió respuesta")
        }
        return text.trim()
    }

    private fun errorMessage(status: Int, body: String): String {
        val detail = runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()
        return when (status) {
            400 -> "Gemini rechazó la consulta (${detail ?: "solicitud no válida"}). Revisa el nombre del modelo en Ajustes."
            401, 403 -> "La clave de Gemini no es válida o no tiene permiso."
            404 -> "No existe ese modelo de Gemini. Revisa el nombre en Ajustes."
            429 -> "Se acabó el cupo gratis de Gemini por ahora. Espera un rato e intenta de nuevo."
            in 500..599 -> "Gemini tiene un problema en este momento (error $status${detail?.let { ": $it" } ?: ""}). " +
                "Se reintentó varias veces; prueba en unos minutos o con otro modelo en Ajustes."
            else -> "Gemini respondió con el error $status"
        }
    }
}
