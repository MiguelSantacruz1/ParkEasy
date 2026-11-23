package com.ejemplo.parkeasy.domain.ai

/** Un turno de la conversación con el asistente. */
data class ChatTurn(val fromUser: Boolean, val text: String)

/** Error del asistente con un mensaje listo para mostrar en español. */
class AiException(message: String) : Exception(message)

/** Cliente de un modelo de lenguaje. La implementación real habla con Gemini; las pruebas usan un servidor local. */
interface AiClient {
    /** Responde al último turno de [history] (que debe ser del usuario) con [system] como contexto fijo. */
    fun reply(system: String, history: List<ChatTurn>): String
}
