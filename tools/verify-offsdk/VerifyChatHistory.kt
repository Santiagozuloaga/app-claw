package verify

import android.app.Application
import android.content.Context
import android.content.FakePrefsDisk
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.core.storage.InMemoryChatHistoryStore
import com.example.clawcode.core.storage.PrefsChatHistoryStore
import com.example.clawcode.data.model.ChatMessage
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking

/**
 * El historial del chat sólo vivía en memoria: al morir el proceso se perdía la conversación y el
 * turno siguiente llegaba sin contexto. Se guarda en SharedPreferences (sin base de datos) y aquí
 * se comprueba que sobrevive al reinicio, que no duplica la semilla y que textos con el separador
 * interno, `|`, saltos de línea o emojis vuelven intactos.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODELO = "modelodepega:1"
private const val RESPUESTA = "RESPUESTA_DEL_MODELO_LOCAL"

private class HistoryOllama {
    val peticiones = mutableListOf<String>()

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/api/chat") { exchange ->
            synchronized(peticiones) { peticiones.add(exchange.requestBody.readBytes().decodeToString()) }
            respond(exchange, """{"model":"$MODELO","message":{"role":"assistant","content":"$RESPUESTA"},""" +
                    """"done":true,"done_reason":"stop","prompt_eval_count":12,"eval_count":4,"eval_duration":80000000}""")
        }
        createContext("/api/tags") { exchange ->
            respond(exchange, """{"models":[{"name":"$MODELO","size":1200000000,"capabilities":["completion"]}]}""")
        }
        createContext("/api/ps") { exchange -> respond(exchange, """{"models":[]}""") }
        executor = java.util.concurrent.Executors.newFixedThreadPool(2) { r ->
            Thread(r).apply { isDaemon = true }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"
    fun stop() = server.stop(0)

    private fun respond(exchange: HttpExchange, body: String) {
        val bytes = body.toByteArray()
        exchange.responseHeaders.add("Content-Type", "application/json")
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }
}

private fun engineCon(historyStore: com.example.clawcode.core.storage.ChatHistoryStore, host: String): ClawEngine {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore(), chatStore = historyStore)
    engine.updateOllamaConfig(host, MODELO, WEBHOOK, false)
    return engine
}

fun main() = runBlocking {
    val fake = HistoryOllama()

    println("=== HC-1: el turno vuelve después de reiniciar la app ===")
    val store = InMemoryChatHistoryStore()
    val motorA = engineCon(store, fake.baseUrl)
    val sinSemilla = motorA.chatMessages.value.filter { it.sender == "USER" }.size
    motorA.sendChatMessage("hola", null, null, MobileActionEngine(Context()))
    delay(120)
    val guardadoA = store.load()
    check("el turno queda persistido al cerrar el turno",
        guardadoA.any { it.sender == "USER" && it.text == "hola" } &&
                guardadoA.any { it.sender == "JARVIS" && it.text == RESPUESTA },
        "guardados=${guardadoA.size}")

    val motorB = engineCon(store, fake.baseUrl)
    check("al reiniciar aparece el mismo historial, sin duplicar la semilla",
        motorB.chatMessages.value.map { it.id to it.text } == guardadoA.map { it.id to it.text },
        "restaurados=${motorB.chatMessages.value.size}")
    check("el mensaje de arranque no se duplica",
        motorB.chatMessages.value.count { it.sender == "JARVIS" && it.text.contains("100% On-Device") } <= 1,
        "semillas=${motorB.chatMessages.value.count { it.sender == "JARVIS" && it.text.contains("100% On-Device") }}")

    println()
    println("=== HC-2: el segundo turno no reaparece dos veces ===")
    motorB.sendChatMessage("segundo", null, null, MobileActionEngine(Context()))
    delay(120)
    val guardadoB = store.load()
    check("el historial crece de a dos mensajes por turno",
        guardadoB.count { it.text == "segundo" } == 1 && guardadoB.count { it.text == "hola" } == 1,
        "total=${guardadoB.size}")
    val motorC = engineCon(store, fake.baseUrl)
    check("otro motor con el mismo store reconstruye el orden exacto",
        motorC.chatMessages.value.map { it.sender to it.text } ==
                guardadoB.map { it.sender to it.text },
        "pares=${motorC.chatMessages.value.size}")
    val historialAlModelo = fake.peticiones.last()
    check("el turno enviado al modelo trae el historial restaurado, no vacío",
        historialAlModelo.contains("hola") && historialAlModelo.contains("segundo"),
        "chars=${historialAlModelo.length}")

    println()
    println("=== HC-3: SharedPreferences reales y textos con separadores/emojis ===")
    FakePrefsDisk.reset()
    val appContext = Application()
    val prefsStore = PrefsChatHistoryStore(appContext)
    val hostiles = listOf(
        ChatMessage(id = "x1", sender = "USER", text = "tubería | separador \u0001 y \u0002", timestamp = "10:00:00"),
        ChatMessage(id = "x2", sender = "JARVIS", text = "línea1\nlínea2 ñoño 😀 %42 + &", timestamp = "10:00:01",
            attachedFile = "foto | 100%.png", isActionExecuted = true, actionExecutedName = "abrir_app"),
        ChatMessage(id = "x3", sender = "USER", text = "", timestamp = "10:00:02")
    )
    prefsStore.save(hostiles)
    val leidoOtroContexto = PrefsChatHistoryStore(Application()).load()
    check("el texto hostil sobrevive el ida y vuelta en SharedPreferences",
        leidoOtroContexto == hostiles, "leídos=${leidoOtroContexto.size}")
    check("un texto vacío no se confunde con el separador de campos",
        leidoOtroContexto.lastOrNull()?.text == "" && leidoOtroContexto.size == 3,
        "ultimo='${leidoOtroContexto.lastOrNull()?.text}'")

    println()
    println("=== HC-4: el archivo no crece sin límite ===")
    val muchos = (1..120).map { ChatMessage(id = "m$it", sender = "USER", text = "mensaje $it", timestamp = "10:00:0$it") }
    prefsStore.save(muchos)
    val recortado = PrefsChatHistoryStore(Application()).load()
    check("sólo se conservan los últimos 60 mensajes", recortado.size == 60, "conservados=${recortado.size}")
    check("se conservan los más recientes y en orden",
        recortado.first().text == "mensaje 61" && recortado.last().text == "mensaje 120",
        "primero=${recortado.first().text} ultimo=${recortado.last().text}")

    FakePrefsDisk.reset()
    fake.stop()
    println()
    if (failures == 0) println("TODAS LAS PRUEBAS PASARON") else println("$failures PRUEBAS FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
