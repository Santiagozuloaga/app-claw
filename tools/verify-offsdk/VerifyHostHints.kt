package verify

import android.content.Context
import com.example.clawcode.core.storage.InMemoryOllamaConfigStore
import com.example.clawcode.engine.ClawEngine
import com.example.clawcode.engine.MobileActionEngine
import kotlinx.coroutines.runBlocking

/**
 * Cada host que la app autoriza o sugiere se prueba DE VERDAD contra el Ollama real de esta
 * máquina, y se comprueba lo que ve el usuario en el chat cuando el host no responde.
 *
 * Importa porque el bloqueo de Android no es uno solo: el XML autoriza loopback e IPv6, pero
 * Ollama de fábrica escucha únicamente en 127.0.0.1, y una IP LAN exige además arrancar el
 * servidor con OLLAMA_HOST=0.0.0.0. Un aviso equivocado (o ausente) deja el chat en reserva
 * estática sin que nadie sepa por qué.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private const val WEBHOOK = "https://api.openclaw.io/v1/whatsapp/webhook"
private const val MODEL = "qwen2.5:0.5b"
private const val FALLBACK_MARK = "[Runtime local sin respuesta]"

private fun chatWith(host: String, prompt: String): Pair<ClawEngine, String> {
    val engine = ClawEngine(configStore = InMemoryOllamaConfigStore())
    engine.updateOllamaConfig(host, MODEL, WEBHOOK, false)
    runBlocking { engine.sendChatMessage(prompt, null, null, MobileActionEngine(Context())) }
    val reply = engine.chatMessages.value.lastOrNull { it.sender == "JARVIS" }?.text.orEmpty()
    return engine to reply
}

private fun sample(text: String) = text.replace("\n", " | ").take(230)

fun main() {
    println("=== HH-1: los hosts locales que la app autoriza responden de verdad ===")
    val (e4, r4) = chatWith("http://127.0.0.1:11434", "Responde solo con la palabra CUATRO.")
    check("127.0.0.1 hace inferencia real", !r4.contains(FALLBACK_MARK),
        "respuesta=${sample(r4)}")
    check("el host usado queda registrado en la inferencia",
        e4.consoleLogs.value.any { it.message.contains("Inferencia REAL vía Ollama http://127.0.0.1:11434") },
        e4.consoleLogs.value.lastOrNull { it.message.contains("Inferencia REAL") }?.message?.take(150) ?: "ninguno")

    val (el, rl) = chatWith("http://localhost:11434", "Responde solo con la palabra CINCO.")
    check("'localhost' también hace inferencia real (no sólo la IP literal)",
        !rl.contains(FALLBACK_MARK), "respuesta=${sample(rl)}")
    check("localhost no dispara ningún aviso engañoso",
        !rl.contains("ℹ️"), "sin pista appended")
    check("la telemetría refleja la medición real de ese turno",
        el.hardwareTelemetry.value.tokensPerSecond > 0f,
        "tps=${el.hardwareTelemetry.value.tokensPerSecond}")

    println()
    println("=== HH-2: loopback IPv6 -> Ollama de fábrica no escucha ahí ===")
    val (e6, r6) = chatWith("http://[::1]:11434", "Responde solo con la palabra SEIS.")
    val sixFailed = r6.contains(FALLBACK_MARK)
    println("    (informativo) ::1 respondió=${!sixFailed}")
    check("nunca queda mudo: o hay inferencia real, o hay diagnóstico accionable",
        !sixFailed || r6.contains("OLLAMA_HOST"), sample(r6))
    if (sixFailed) {
        check("el diagnóstico del IPv6 explica la causa y el remedio",
            r6.contains("loopback IPv6") && r6.contains("[::]:11434"), sample(r6))
        check("no confunde: el aviso no habla del emulador ni de la LAN",
            !r6.contains("sólo es alcanzable desde el emulador") &&
                    !r6.contains("network_security_config.xml"), sample(r6))
    }

    println()
    println("=== HH-3: host LAN -> los DOS requisitos obligatorios, no uno ===")
    // 192.0.2.0/24 está reservado por RFC 5737: ningún equipo lo enruta, así que falla por
    // timeout sin escanear la red del usuario.
    val (eLan, rLan) = chatWith("http://192.0.2.7:11434", "Responde solo con la palabra SIETE.")
    check("el host LAN no responde (escenario esperado)", rLan.contains(FALLBACK_MARK), sample(rLan))
    check("avisa del bloqueo HTTP en claro de Android y del fichero exacto",
        rLan.contains("network_security_config.xml") && rLan.contains("HTTP en claro"), sample(rLan))
    check("avisa de que Ollama debe arrancar con OLLAMA_HOST=0.0.0.0",
        rLan.contains("OLLAMA_HOST=0.0.0.0"), sample(rLan))
    check("ofrece la alternativa sin recompilar (runtime en el propio teléfono)",
        rLan.contains("127.0.0.1:11434"), sample(rLan))
    check("no mente con la pista del emulador en un host LAN",
        !rLan.contains("sólo es alcanzable desde el emulador"), sample(rLan))
    check("el error real del runtime se conserva junto al aviso",
        Regex("192\\.0\\.2\\.7:11434: [A-Za-z]*(Exception|Error)").containsMatchIn(rLan),
        "...${rLan.takeLast(220).replace("\n", " | ")}")

    println()
    println("=== HH-4: host de fábrica -> el descubrimiento rescata el chat, no la reserva ===")
    // Con 10.0.2.2 el motor sondea 127.0.0.1 y lo adopta, así que aquí NO se espera el aviso del
    // emulador: se espera inferencia real. El aviso sólo aparece en un dispositivo físico donde
    // tampoco haya runtime local, y se comprueba a nivel de fuente en el check siguiente.
    val (eF, rF) = chatWith("http://10.0.2.2:11434", "Responde solo con la palabra OCHO.")
    check("el host de fábrica acaba en inferencia real", !rF.contains(FALLBACK_MARK),
        sample(rF))
    check("y adopta el runtime local alcanzable",
        eF.ollamaConfig.value.hostUrl == "http://127.0.0.1:11434", eF.ollamaConfig.value.hostUrl)
    val engineSrc = java.io.File(
        "C:/Users/Admin/Documents/Qoder/2026-10-11/4f98c400/app-claw-main/app/src/main/java/com/example/clawcode/engine/ClawEngine.kt"
    ).readText()
    check("la rama del emulador sigue existiendo para el dispositivo físico",
        engineSrc.contains("sólo es alcanzable desde el emulador") &&
                engineSrc.contains("fun runtimeConnectionHint"), "rama presente")

    println()
    println("=== HH-5: loopback con el puerto equivocado -> sin ruido, sólo el error ===")
    val (eD, rD) = chatWith("http://127.0.0.1:11499", "Responde solo con la palabra NUEVE.")
    check("el fallo se muestra", rD.contains(FALLBACK_MARK), sample(rD))
    check("no se añade ninguna pista: el host ya está autorizado y es local",
        !rD.contains("ℹ️"), "sin pista")
    check("conectividad marcada como caida, no como sana",
        !eD.ollamaConfig.value.isConnected, "isConnected=${eD.ollamaConfig.value.isConnected}")

    println()
    println("=== HH-6: la pista ofrece el túnel adb como vía sin recompilar ===")
    val (eT, rT) = chatWith("http://192.168.20.30:11434", "Responde solo con la palabra DIEZ.")
    check("el host LAN no local sigue explicando las dos condiciones",
        rT.contains("network_security_config.xml") && rT.contains("OLLAMA_HOST"), sample(rT))
    check("y añade el túnel por USB, que no exige recompilar ni abrir la LAN",
        rT.contains("adb reverse tcp:11434 tcp:11434"), sample(rT))
    val fuenteHint = java.io.File(
        "../app-claw-main/app/src/main/java/com/example/clawcode/engine/ClawEngine.kt"
    ).readLines().joinToString("\n") { it.substringBefore("//") }
    check("la rama del host de fábrica también da las tres opciones, incluida adb reverse",
        Regex("10\\.0\\.2\\.2[^\\n]{0,200}").containsMatchIn(fuenteHint) &&
                fuenteHint.contains("tres opciones") && fuenteHint.contains("adb reverse tcp:11434"),
        "hint de fábrica actualizado")

    println()
    println(if (failures == 0) "RESULTADO HOSTHINTS: 0 FAIL" else "RESULTADO HOSTHINTS: $failures FAIL")
}
