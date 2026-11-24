package com.ejemplo.parkeasy.ai

import com.ejemplo.parkeasy.data.ai.GeminiClient
import com.ejemplo.parkeasy.data.repository.SqlSettingRepository
import com.ejemplo.parkeasy.domain.ai.AiException
import com.ejemplo.parkeasy.domain.ai.AiSettings
import com.ejemplo.parkeasy.domain.ai.ChatTurn
import com.ejemplo.parkeasy.domain.ai.ParkingSummary
import com.ejemplo.parkeasy.domain.model.VehicleType
import com.ejemplo.parkeasy.domain.service.CheckInRequest
import com.ejemplo.parkeasy.withEnv
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssistantTest {
    @Test
    fun `el resumen no incluye placas, colores ni observaciones`() = withEnv { env ->
        val spot = env.floorWithSpot("ZZ-77")
        env.parkingService.checkIn(
            CheckInRequest(spot, "QWE987", "Fucsia", VehicleType.CAR, env.clock.now.minusSeconds(3 * 3600), "nota secreta"),
        )
        val text = ParkingSummary.build(
            env.clock, "Mi parqueadero", env.floors.getAll(), env.spots.getAll(), env.records.getAll(), env.tariffs.getAll(),
        )
        assertFalse("QWE987" in text)
        assertFalse("Fucsia" in text)
        assertFalse("secreta" in text)
        assertFalse("ZZ-77" in text)
        assertTrue("1 ocupados" in text, text)
        assertTrue("Vehículos dentro (1)" in text, text)
    }

    @Test
    fun `la clave se guarda y se borra`() = withEnv { env ->
        val ai = AiSettings(SqlSettingRepository(env.db))
        assertFalse(ai.enabled)
        ai.apiKey = "  abc  "
        assertTrue(ai.enabled)
        assertEquals("abc", ai.apiKey)
        assertEquals(AiSettings.DEFAULT_MODEL, ai.model)
        ai.apiKey = ""
        assertFalse(ai.enabled)
    }

    private fun server(status: Int, body: String, seen: MutableList<Triple<String, String?, String>> = mutableListOf()): Pair<HttpServer, List<Triple<String, String?, String>>> {
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            seen += Triple(ex.requestURI.toString(), ex.requestHeaders.getFirst("x-goog-api-key"), ex.requestBody.readBytes().decodeToString())
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(status, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        return s to seen
    }

    private fun client(s: HttpServer, key: String = "k-123") =
        GeminiClient({ key }, { "modelo-x" }, "http://127.0.0.1:${s.address.port}/v1beta", fallbacks = emptyList())

    @Test
    fun `manda la clave en la cabecera y lee la respuesta`() {
        val ok = """{"candidates":[{"content":{"parts":[{"text":"Cobraste "},{"text":"10.000 COP"}]}}]}"""
        val (s, seen) = server(200, ok)
        try {
            val answer = client(s).reply("contexto", listOf(ChatTurn(true, "¿cuánto cobré?")))
            assertEquals("Cobraste 10.000 COP", answer)
            val (uri, key, body) = seen.single()
            assertEquals("/v1beta/models/modelo-x:generateContent", uri)
            assertEquals("k-123", key)
            assertFalse("k-123" in uri)
            assertTrue("contexto" in body && "¿cuánto cobré?" in body && "\"user\"" in body)
        } finally { s.stop(0) }
    }

    @Test
    fun `traduce los errores de la API a mensajes en espanol`() {
        listOf(403 to "clave", 429 to "cupo", 404 to "modelo").forEach { (code, word) ->
            val (s, _) = server(code, """{"error":{"message":"x"}}""")
            try {
                val e = assertFailsWith<AiException> { client(s).reply("c", listOf(ChatTurn(true, "hola"))) }
                assertTrue(word in e.message!!, e.message)
            } finally { s.stop(0) }
        }
    }

    @Test
    fun `reintenta cuando Gemini esta sobrecargado y luego responde`() {
        var calls = 0
        val pauses = mutableListOf<Long>()
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            ex.requestBody.readBytes()
            val ok = ++calls >= 3
            val bytes = (if (ok) """{"candidates":[{"content":{"parts":[{"text":"listo"}]}}]}""" else """{"error":{"message":"overloaded"}}""").toByteArray()
            ex.sendResponseHeaders(if (ok) 200 else 503, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        try {
            val c = GeminiClient({ "k" }, { "m" }, "http://127.0.0.1:${s.address.port}/v1beta", pause = { pauses += it }, fallbacks = emptyList())
            assertEquals("listo", c.reply("c", listOf(ChatTurn(true, "hola"))))
            assertEquals(3, calls)
            assertEquals(listOf(1_000L, 2_000L), pauses)
        } finally { s.stop(0) }
    }

    @Test
    fun `tras agotar los reintentos muestra el detalle de Google`() {
        val (s, seen) = server(503, """{"error":{"message":"The model is overloaded."}}""")
        try {
            val c = GeminiClient({ "k" }, { "m" }, "http://127.0.0.1:${s.address.port}/v1beta", pause = {}, fallbacks = emptyList())
            val e = assertFailsWith<AiException> { c.reply("c", listOf(ChatTurn(true, "hola"))) }
            assertEquals(3, seen.size)
            assertTrue("overloaded" in e.message!! && "503" in e.message!!, e.message)
        } finally { s.stop(0) }
    }

    @Test
    fun `si el modelo no existe o esta saturado prueba el siguiente`() {
        val used = mutableListOf<String>()
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            ex.requestBody.readBytes()
            val model = ex.requestURI.path.substringAfter("/models/").substringBefore(":")
            used += model
            val (code, text) = when (model) {
                "viejo" -> 404 to """{"error":{"message":"not found"}}"""
                "saturado" -> 503 to """{"error":{"message":"overloaded"}}"""
                else -> 200 to """{"candidates":[{"content":{"parts":[{"text":"desde $model"}]}}]}"""
            }
            val bytes = text.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        try {
            val c = GeminiClient({ "k" }, { "viejo" }, "http://127.0.0.1:${s.address.port}/v1beta", pause = {}, fallbacks = listOf("saturado", "bueno", "otro"))
            assertEquals("desde bueno", c.reply("c", listOf(ChatTurn(true, "hola"))))
            assertEquals(listOf("viejo", "saturado", "saturado", "bueno"), used)
        } finally { s.stop(0) }
    }

    @Test
    fun `si todos los modelos fallan dice cuales probo`() {
        val (s, _) = server(503, """{"error":{"message":"overloaded"}}""")
        try {
            val c = GeminiClient({ "k" }, { "a" }, "http://127.0.0.1:${s.address.port}/v1beta", pause = {}, fallbacks = listOf("b"))
            val e = assertFailsWith<AiException> { c.reply("c", listOf(ChatTurn(true, "hola"))) }
            assertTrue("a → 503, b → 503" in e.message!! && "saturado" in e.message!!, e.message)
        } finally { s.stop(0) }
    }

    @Test
    fun `ordena los modelos que ofrece la API y descarta los que no sirven para texto`() {
        val json = """{"models":[
          {"name":"models/gemini-2.5-pro","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-2.5-flash-lite","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-3-flash-preview","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-2.5-flash","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-2.5-flash-image","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemini-embedding-001","supportedGenerationMethods":["embedContent"]},
          {"name":"models/gemini-2.5-flash-tts","supportedGenerationMethods":["generateContent"]},
          {"name":"models/gemma-3-27b","supportedGenerationMethods":["generateContent"]}]}"""
        val c = GeminiClient({ "k" }, { "m" }, "http://x")
        assertEquals(listOf("gemini-2.5-flash", "gemini-2.5-flash-lite", "gemini-2.5-pro", "gemini-3-flash-preview"), c.rankModels(json))
    }

    @Test
    fun `sin lista fija le pregunta a la API que modelos tiene la clave`() {
        val used = mutableListOf<String>()
        val s = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        s.createContext("/") { ex ->
            ex.requestBody.readBytes()
            val path = ex.requestURI.path
            val (code, text) = if (path.endsWith("/models")) {
                200 to """{"models":[{"name":"models/gemini-9-flash","supportedGenerationMethods":["generateContent"]}]}"""
            } else {
                val m = path.substringAfter("/models/").substringBefore(":")
                used += m
                if (m == "gemini-9-flash") 200 to """{"candidates":[{"content":{"parts":[{"text":"hola desde $m"}]}}]}"""
                else 404 to """{"error":{"message":"nope"}}"""
            }
            val bytes = text.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        s.start()
        try {
            val c = GeminiClient({ "k" }, { "no-existe" }, "http://127.0.0.1:${s.address.port}/v1beta", pause = {})
            assertEquals("hola desde gemini-9-flash", c.reply("c", listOf(ChatTurn(true, "hola"))))
            assertEquals(listOf("no-existe", "gemini-9-flash"), used)
        } finally { s.stop(0) }
    }
    @Test
    fun `si no hay conexion reintenta y dice la causa real`() {
        val (s, _) = server(200, "{}")
        val port = s.address.port
        s.stop(0)
        val pauses = mutableListOf<Long>()
        val c = GeminiClient({ "k" }, { "m" }, "http://127.0.0.1:$port/v1beta", pause = { pauses += it }, fallbacks = emptyList())
        val e = assertFailsWith<AiException> { c.reply("c", listOf(ChatTurn(true, "hola"))) }
        assertTrue("No hay conexión" in e.message!! && "Exception" in e.message!!, e.message)
        assertEquals(listOf(1_000L, 2_000L), pauses)
    }
    @Test
    fun `quita el formato Markdown de la respuesta`() {
        val raw = "Cobró **109.500 COP**.\n## Detalle\n* Carros: 99.000\n- Motos: 10.500"
        assertEquals("Cobró 109.500 COP.\nDetalle\n• Carros: 99.000\n• Motos: 10.500", com.ejemplo.parkeasy.domain.ai.AssistantService.plain(raw))
    }
    @Test
    fun `sin clave no sale ninguna consulta`() {
        val (s, seen) = server(200, "{}")
        try {
            assertFailsWith<AiException> { client(s, key = " ").reply("c", listOf(ChatTurn(true, "hola"))) }
            assertTrue(seen.isEmpty())
        } finally { s.stop(0) }
    }

    @Test
    fun `una respuesta vacia o bloqueada es un error claro`() {
        val (s, _) = server(200, """{"promptFeedback":{"blockReason":"SAFETY"}}""")
        try {
            val e = assertFailsWith<AiException> { client(s).reply("c", listOf(ChatTurn(true, "hola"))) }
            assertTrue("no quiso" in e.message!!)
        } finally { s.stop(0) }
    }
}
