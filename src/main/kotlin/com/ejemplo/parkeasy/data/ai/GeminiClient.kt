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
    private val http: HttpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(Duration.ofSeconds(10)).build(),
    private val attempts: Int = 3,
    private val pause: (Long) -> Unit = { Thread.sleep(it) },
    /** Modelos alternativos. Si es null se le piden a la API los que tiene disponibles esta clave. */
    private val fallbacks: List<String>? = null,
) : AiClient {

    private companion object {
        val RETRYABLE = setOf(500, 502, 503, 504)
        const val MAX_FALLBACKS = 6
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
        // Los alternativos salen de la lista que Google dice que esta clave puede usar; si no se puede pedir, de una lista fija.
        val tried = mutableListOf<Pair<String, Int>>()
        var models = listOf(model().trim()).filter { it.isNotEmpty() }
        var i = 0
        var expanded = false
        while (i < models.size) {
            val m = models[i]
            val status = callModel(m, key, body.toString(), if (i == 0) attempts else minOf(attempts, 2)) { return it }
            tried += m to status
            i++
            if (i == models.size && !expanded) {
                expanded = true
                val extra = fallbacks ?: (discoverModels(key).ifEmpty { DEFAULT_FALLBACKS })
                models = (models + extra).distinct()
            }
        }
        throw AiException(summary(tried))
    }

    /** Llama a [m] con reintentos. Si responde bien, entrega el texto a [done]; si no, devuelve el código HTTP (404 o 5xx). */
    private inline fun callModel(m: String, key: String, body: String, maxAttempts: Int, done: (String) -> Nothing): Int {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/models/$m:generateContent"))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("x-goog-api-key", key)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build()
        var attempt = 1
        while (true) {
            val response = send(request)
            val status = response.statusCode()
            if (status in RETRYABLE && attempt < maxAttempts) {
                pause(1_000L * attempt)
                attempt++
                continue
            }
            if (status == 404 || status in RETRYABLE) {
                lastDetail = detailOf(response.body())
                return status
            }
            done(parse(status, response.body()))
        }
    }

    private var lastDetail: String? = null

    private fun detailOf(body: String): String? = runCatching {
        Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** Envía la solicitud; ante fallos de conexión reintenta (hasta [attempts] veces) y, si persisten, dice la causa. */
    private fun send(request: HttpRequest): HttpResponse<String> {
        var attempt = 1
        while (true) {
            try {
                return http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            } catch (e: IOException) {
                if (attempt < attempts) {
                    pause(1_000L * attempt)
                    attempt++
                    continue
                }
                val cause = generateSequence<Throwable>(e) { it.cause }.last()
                throw AiException(
                    "No hay conexión con Gemini (${cause.javaClass.simpleName}: ${cause.message ?: "sin detalle"}). " +
                        "Revisa el internet, el antivirus o el proxy e intenta de nuevo.",
                )
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                throw AiException("La consulta se interrumpió")
            }
        }
    }
    private fun summary(tried: List<Pair<String, Int>>): String {
        val head = if (tried.any { it.second in RETRYABLE }) {
            "Gemini está saturado en este momento. Intenta de nuevo en unos minutos."
        } else {
            "Ningún modelo de Gemini respondió. Revisa el nombre en Ajustes."
        }
        val google = lastDetail?.let { " Google dice: $it" }.orEmpty()
        return head + " Resultado por modelo: " + tried.joinToString(", ") { (m, s) -> "$m → $s" } + "." + google
    }

    /** Modelos de texto que esta clave puede usar, los más adecuados primero. Vacío si no se pudo consultar. */
    internal fun discoverModels(key: String): List<String> = try {
        val request = HttpRequest.newBuilder(URI.create("$baseUrl/models?pageSize=200"))
            .timeout(Duration.ofSeconds(15)).header("x-goog-api-key", key).GET().build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
        if (response.statusCode() != 200) emptyList() else rankModels(response.body())
    } catch (e: Exception) {
        if (e is InterruptedException) Thread.currentThread().interrupt()
        emptyList()
    }

    internal fun rankModels(json: String): List<String> {
        val skip = listOf("tts", "image", "embedding", "live", "audio", "robotics", "computer", "aqa", "learnlm", "gemma")
        val names = Json.parseToJsonElement(json).jsonObject["models"]?.jsonArray.orEmpty().mapNotNull { m ->
            val o = m.jsonObject
            val name = o["name"]?.jsonPrimitive?.contentOrNull?.removePrefix("models/") ?: return@mapNotNull null
            val ok = o["supportedGenerationMethods"]?.jsonArray?.any { it.jsonPrimitive.contentOrNull == "generateContent" } == true
            name.takeIf { ok && it.startsWith("gemini-") && skip.none { s -> s in it } }
        }
        fun rank(n: String): Int {
            val unstable = if ("preview" in n || "exp" in n) 10 else 0
            val kind = when {
                "flash-lite" in n -> 1
                "flash" in n -> 0
                "pro" in n -> 2
                else -> 3
            }
            return unstable + kind
        }
        return names.sortedWith(compareBy({ rank(it) }, { it })).take(MAX_FALLBACKS)
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
