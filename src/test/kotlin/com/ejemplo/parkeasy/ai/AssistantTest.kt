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
        GeminiClient({ key }, { "modelo-x" }, "http://127.0.0.1:${s.address.port}/v1beta")

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
