package com.example.clawcode.core.storage

import android.content.Context
import com.example.clawcode.data.model.ChatMessage
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * El historial visible del chat vivía sólo en memoria: al morir el proceso (o al rotar con
 * `configChanges` sin cubrir) la conversación desaparecía y el siguiente turno perdía el
 * contexto real. Se guarda en SharedPreferences, sin añadir una base de datos.
 *
 * Sólo se guardan los turnos de usuario y del agente; los mensajes de sistema/semilla se
 * regeneran al arrancar, y el límite evita crecer sin fin en un archivo de preferencias.
 */
interface ChatHistoryStore {
    fun load(): List<ChatMessage>
    fun save(messages: List<ChatMessage>)
}

class InMemoryChatHistoryStore : ChatHistoryStore {

    private var guardado: List<ChatMessage> = emptyList()

    override fun load(): List<ChatMessage> = guardado

    override fun save(messages: List<ChatMessage>) {
        guardado = messages
    }
}

class PrefsChatHistoryStore(context: Context) : ChatHistoryStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun load(): List<ChatMessage> {
        val crudo = prefs.getString(KEY_MESSAGES, null) ?: return emptyList()
        if (crudo.isBlank()) return emptyList()
        return crudo.split(CAMPO).mapNotNull(::decodificarLinea)
    }

    override fun save(messages: List<ChatMessage>) {
        val recortado = messages.asReversed().take(MAX_MENSAJES).reversed()
        prefs.edit().putString(KEY_MESSAGES, recortado.joinToString(CAMPO) { codificarLinea(it) }).apply()
    }

    private fun codificarLinea(mensaje: ChatMessage): String = listOf(
        mensaje.id,
        mensaje.sender,
        mensaje.text,
        mensaje.timestamp,
        mensaje.attachedFile.orEmpty(),
        mensaje.attachedImageUri.orEmpty(),
        mensaje.isActionExecuted.toString(),
        mensaje.actionExecutedName.orEmpty()
    ).joinToString(SEPARDOR) { URLEncoder.encode(it, "UTF-8") }

    private fun decodificarLinea(linea: String): ChatMessage? {
        val partes = linea.split(SEPARDOR).map { decodificar(it) }
        if (partes.size < 8) return null
        return ChatMessage(
            id = partes[0],
            sender = partes[1],
            text = partes[2],
            timestamp = partes[3],
            attachedFile = partes[4].ifBlank { null },
            attachedImageUri = partes[5].ifBlank { null },
            isActionExecuted = partes[6].toBoolean(),
            actionExecutedName = partes[7].ifBlank { null }
        )
    }

    private fun decodificar(valor: String): String =
        runCatching { URLDecoder.decode(valor, "UTF-8") }.getOrElse { valor }

    private companion object {
        const val PREFS_NAME = "claw_chat_history"
        const val KEY_MESSAGES = "mensajes"
        const val SEPARDOR = "|"
        const val CAMPO = "\u0001"
        const val MAX_MENSAJES = 60
    }
}
