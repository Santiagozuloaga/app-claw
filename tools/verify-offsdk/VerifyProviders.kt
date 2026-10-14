package verify

import com.example.clawcode.engine.OllamaChatMessage
import com.example.clawcode.engine.OllamaChatRequest
import com.example.clawcode.engine.OpenAiCompatibleClient
import com.example.clawcode.engine.ProveedorInferencia
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * El segundo proveedor es lo que hace cierta la frase "independiente de Ollama" que el proyecto
 * llevaba escrita desde su primera especificación. Aquí no se prueba contra un SaaS: se prueba
 * contra un servidor /v1/chat/completions falso, con SSE real, para poder exigirle conductas que
 * un servidor de verdad sólo a veces da: error 401, lista vacía, respuesta sin stream, o un prompt
 * con comillas que rompería un payload construido por concatenación.
 */

private var failures = 0

private fun check(nombre: String, condicion: Boolean, detalle: String) {
    if (condicion) println("  PASS  $nombre -> $detalle")
    else { failures++; println("  FAIL  $nombre -> $detalle") }
}

private class ServidorFalso {
    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    var ultimoCuerpo = ""
    var ultimaAuthorization: String? = null
    var codigoChat = 200
    var cuerpoChat = ""
    var codigoModelos = 200
    var cuerpoModelos = ""
    var modo = "sse"

    fun start() {
        server.createContext("/v1/chat/completions") { ex: HttpExchange ->
            ultimoCuerpo = ex.requestBody.readBytes().toString(Charsets.UTF_8)
            ultimaAuthorization = ex.requestHeaders.getFirst("Authorization")
            val bytes = if (codigoChat == 200) {
                when (modo) {
                    "sse" -> trozosSse(listOf("Hol", "a, ", "Santiago"), "gpt-4o-mini")
                    "json-plan" -> """{"model":"gpt-4o-mini","choices":[{"message":{"role":"assistant","content":"respuesta completa"},"finish_reason":"stop"}]}"""
                    "vacio" -> "data: [DONE]"
                    else -> cuerpoChat
                }.toByteArray(Charsets.UTF_8)
            } else cuerpoChat.toByteArray(Charsets.UTF_8)
            ex.responseHeaders.add("Content-Type", "text/event-stream")
            ex.sendResponseHeaders(codigoChat, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes); it.flush() }
        }
        server.createContext("/v1/models") { ex: HttpExchange ->
            ultimaAuthorization = ex.requestHeaders.getFirst("Authorization")
            val bytes = if (codigoModelos == 200) cuerpoModelos.toByteArray()
            else "{\"error\":{\"message\":\"sin acceso\"}}".toByteArray()
            ex.responseHeaders.add("Content-Type", "application/json")
            ex.sendResponseHeaders(codigoModelos, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes); it.flush() }
        }
        server.start()
    }

    fun url() = "http://127.0.0.1:${server.address.port}"

    fun stop() = server.stop(0)

    /** Un chunk por delta, con usage en el último, igual que hace un servidor real. */
    private fun trozosSse(partes: List<String>, modelo: String): String {
        val sb = StringBuilder()
        partes.forEach { p ->
            val escapado = p.replace("\\", "\\\\").replace("\"", "\\\"")
            sb.append("data: {\"model\":\"").append(modelo)
                .append("\",\"choices\":[{\"delta\":{\"content\":\"").append(escapado).append("\"}}]}\n\n")
        }
        sb.append("data: {\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}],")
            .append("\"usage\":{\"prompt_tokens\":11,\"completion_tokens\":7}}\n\n")
        sb.append("data: [DONE]\n\n")
        return sb.toString()
    }
}

private fun peticion(texto: String, modelo: String = "gpt-4o-mini") = OllamaChatRequest(
    hostUrl = "",
    model = modelo,
    systemPrompt = "Eres CLAW.",
    history = listOf(OllamaChatMessage("user", "turno anterior"), OllamaChatMessage("assistant", "ok")),
    userText = texto,
    temperature = 0.7f,
    topP = 0.9f,
    stopTokens = listOf("###"),
    maxTokens = 128
)

fun main() {
    val srv = ServidorFalso()
    srv.start()
    try {
        println("=== VerifyProviders: el puerto de cerebro y su segundo proveedor ===")

        val proveedor = OpenAiCompatibleClient(apiKey = "clave-de-prueba")
        check(
            "OP-1 OpenAiCompatibleClient satisface el puerto",
            proveedor is ProveedorInferencia && proveedor.nombre == "openai-compatible",
            "nombre=${proveedor.nombre}"
        )

        val deltas = java.util.Collections.synchronizedList(mutableListOf<String>())
        val respuesta = runBlocking {
            proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) { t -> deltas.add(t) }
        }.getOrThrow()

        check(
            "OP-2 concatena los deltas SSE en una sola respuesta",
            respuesta.text == "Hola, Santiago",
            "texto='${respuesta.text}'"
        )
        check(
            "OP-3 emite un callback por cada delta, no uno al final",
            deltas.size >= 3,
            "${deltas.size} callbacks: $deltas"
        )
        check(
            "OP-4 el modelo reported es el que devolvió el servidor, no el pedido",
            respuesta.model == "gpt-4o-mini" && respuesta.stopReason == "stop",
            "model=${respuesta.model} finish=${respuesta.stopReason}"
        )
        check(
            "OP-5 los tokens y los t/s salen del usage del servidor",
            respuesta.completionTokens == 7 && respuesta.promptTokens == 11 &&
                respuesta.tokensPerSecond > 0f,
            "completion=${respuesta.completionTokens} tps=%.1f".format(respuesta.tokensPerSecond)
        )
        check(
            "OP-6 la latencia es medida, no estimada",
            respuesta.latencyMs >= 0,
            "${respuesta.latencyMs} ms"
        )

        check(
            "OP-7 envía Authorization: Bearer sólo cuando hay clave",
            srv.ultimaAuthorization == "Bearer clave-de-prueba",
            "${srv.ultimaAuthorization}"
        )
        val sinClave = OpenAiCompatibleClient()
        runBlocking { sinClave.chat(peticion("hola").copy(hostUrl = srv.url())) }
        check(
            "OP-8 sin clave no se manda una cabecera Authorization vacía",
            srv.ultimaAuthorization == null,
            "${srv.ultimaAuthorization}"
        )

        // El payload se construye con el serializador. Si alguien vuelve a la concatenación a
        // mano, esta prueba es la que lo detecta.
        val molesto = "di \"hola\"\ny luego \\ esto: {\"role\":\"system\"}"
        runBlocking { proveedor.chat(peticion(molesto).copy(hostUrl = srv.url())) }
        val payloadReleido = runBlocking {
            kotlinx.serialization.json.Json.parseToJsonElement(srv.ultimoCuerpo)
                .jsonObjectOf()
        }
        check(
            "OP-9 un userText con comillas, saltos y llaves viaja intacto",
            payloadReleido == molesto,
            "recibido=${payloadReleido.take(40)}"
        )
        // El cliente manda stream:false cuando nadie pide deltas (igual que OllamaClient). Para
        // comprobar el payload de streaming hay que pedir streaming de verdad.
        runBlocking { proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) { } }
        check(
            "OP-10 en streaming el payload pide stream, usage y el max_tokens del modfile",
            srv.ultimoCuerpo.contains("\"stream\":true") &&
                srv.ultimoCuerpo.contains("include_usage") &&
                srv.ultimoCuerpo.contains("\"max_tokens\":128"),
            "stream + usage + max_tokens presentes"
        )
        runBlocking { proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) }
        check(
            "OP-10b sin callback el payload no finge ser un stream",
            srv.ultimoCuerpo.contains("\"stream\":false") && !srv.ultimoCuerpo.contains("include_usage"),
            "stream:false y sin stream_options"
        )

        println()
        println("--- errores que no se disfrazan de éxito ---")
        srv.codigoChat = 401
        srv.cuerpoChat = "{\"error\":{\"message\":\"invalid api key\"}}"
        val fallo = runBlocking { proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) }
        check(
            "OP-11 un 401 del proveedor se propaga con el mensaje del servidor",
            fallo.isFailure && fallo.exceptionOrNull()?.message?.contains("invalid api key") == true &&
                fallo.exceptionOrNull()?.message?.contains("401") == true,
            fallo.exceptionOrNull()?.message?.take(110) ?: "sin excepcion"
        )
        srv.codigoChat = 200
        srv.modo = "vacio"
        val vacio = runBlocking { proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) }
        check(
            "OP-12 respuesta sin contenido es failure, no una burbuja vacía",
            vacio.isFailure,
            vacio.exceptionOrNull()?.message?.take(90) ?: "?"
        )
        srv.modo = "json-plan"
        val plano = runBlocking {
            proveedor.chat(peticion("hola").copy(hostUrl = srv.url())) { }.getOrThrow()
        }
        check(
            "OP-13 un servidor que ignora stream y devuelve JSON único se entiende",
            plano.text == "respuesta completa",
            "texto='${plano.text}'"
        )

        println()
        println("--- catálogo y lo que el protocolo NO puede saber ---")
        srv.codigoModelos = 200
        srv.cuerpoModelos = """{"object":"list","data":[{"id":"llama-3.1-8b"},{"id":"gpt-4o-mini","context_length":128000},{"id":null}]}"""
        val modelos = runBlocking { proveedor.listCompletionModels(srv.url()) }.getOrThrow()
        check(
            "OP-14 lista los modelos reales de /v1/models y descarta los sin id",
            modelos.size == 2 && modelos.map { it.name } == listOf("llama-3.1-8b", "gpt-4o-mini"),
            modelos.joinToString { it.name }
        )
        check(
            "OP-15 el tamaño que el protocolo no expone se marca -1, no 0",
            modelos.all { it.sizeBytes == -1L } && modelos[1].contextLength == 128000,
            "sizeBytes=${modelos[0].sizeBytes} contexto2=${modelos[1].contextLength}"
        )
        srv.cuerpoModelos = """{"object":"list","data":[]}"""
        val catalogoVacio = runBlocking { proveedor.listCompletionModels(srv.url()) }
        check(
            "OP-16 catálogo vacío es failure, no 'el usuario no tiene modelos'",
            catalogoVacio.isFailure,
            catalogoVacio.exceptionOrNull()?.message?.take(80) ?: "?"
        )
        val huella = runBlocking { proveedor.loadedModelFootprint(srv.url(), "gpt-4o-mini") }
        check(
            "OP-17 la huella de memoria se declara intratable, no se rellena con ceros",
            huella.isFailure && huella.exceptionOrNull()?.message?.contains("no expone") == true,
            huella.exceptionOrNull()?.message?.take(90) ?: "?"
        )

        println()
        println("--- el proveedor que ya funcionaba sigue siendo el mismo ---")
        val ollama: ProveedorInferencia = com.example.clawcode.engine.OllamaClient()
        check(
            "OP-18 OllamaClient implementa el puerto sin cambiar sus firmas",
            ollama.nombre == "ollama",
            "nombre=${ollama.nombre}"
        )
        val huellaOllama = runBlocking { ollama.loadedModelFootprint("http://127.0.0.1:1", "nada") }
        check(
            "OP-19 Ollama sí intenta la huella (falla por red, no por falta de endpoint)",
            huellaOllama.isFailure && !huellaOllama.exceptionOrNull()!!.message!!.contains("no expone"),
            huellaOllama.exceptionOrNull()?.message?.take(90) ?: "?"
        )
    } finally {
        srv.stop()
    }

    println(if (failures == 0) "RESULTADO PROVEEDORES: 0 FAIL" else "RESULTADO PROVEEDORES: $failures FAIL")
    if (failures != 0) kotlin.system.exitProcess(1)
}

/** Extrae messages[último].content del payload recibido, para comparar contra lo enviado. */
private fun kotlinx.serialization.json.JsonElement.jsonObjectOf(): String {
    val raiz = this.jsonObject
    val mensajes = raiz["messages"]?.jsonArray ?: return ""
    val ultimo = mensajes.lastOrNull()?.jsonObject ?: return ""
    return ultimo["content"]?.jsonPrimitive?.content ?: ""
}
