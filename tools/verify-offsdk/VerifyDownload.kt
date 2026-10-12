package verify

import android.app.Application
import com.example.clawcode.core.download.ModelDownloadManager
import com.example.clawcode.core.model.DownloadStatus
import com.example.clawcode.core.model.ModelDescriptor
import com.example.clawcode.core.storage.ModelStorage
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * `ModelDownloadManager` escribía bytes de relleno con `runSimulation()` y una velocidad de
 * `(15000..25000).random()` cuando el descriptor no tenía URL, y la app mostraba eso como
 * "modelo instalado". Aquí se comprueba que sólo se instala lo que llega por HTTP de verdad,
 * que la velocidad es medida y que sin URL real la descarga falla en lugar de fingirse.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

/**
 * Espera con tope a que el manager invoque onComplete. El estado terminal (INSTALLED / ERROR) se
 * escribe en el StateFlow antes de notificar al llamador, así que leer el booleano justo después de
 * ver el status competía con el callback y daba falsos FAIL intermitentes.
 */
private suspend fun awaitFlag(flag: java.util.concurrent.atomic.AtomicBoolean, capMs: Long = 5_000L) {
    val until = System.currentTimeMillis() + capMs
    while (!flag.get() && System.currentTimeMillis() < until) kotlinx.coroutines.delay(25)
}

private const val TOTAL_BYTES = 300_000

/** Bytes deterministas: si el fichero final no coincide byte a byte, nadie descargó nada. */
private fun expectedByte(index: Int): Byte = ((index * 31 + 7) % 251).toByte()

private class FakeModelServer {
    val requests = mutableListOf<String>()

    val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/modelo.gguf") { exchange ->
            requests.add(exchange.requestHeaders.getFirst("Range") ?: "sin Range")
            val from = requests.last().takeIf { it.startsWith("bytes=") }
                ?.removePrefix("bytes=")?.takeWhile { it.isDigit() }?.toLongOrNull() ?: 0L
            val bytes = ByteArray((TOTAL_BYTES - from).toInt()) { i -> expectedByte(from.toInt() + i) }
            exchange.sendResponseHeaders(if (from > 0L) 206 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        executor = java.util.concurrent.Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable).apply { isDaemon = true }
        }
        start()
    }

    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}/modelo.gguf"
    fun stop() = server.stop(0)
}

private fun descriptor(url: String, id: String = "m-real") = ModelDescriptor(
    id = id,
    name = "Modelo de prueba",
    version = "1",
    sizeBytes = TOTAL_BYTES.toLong(),
    sha256 = "dummy_sha256",
    downloadUrl = url
)

private suspend fun awaitStatus(
    manager: ModelDownloadManager,
    modelId: String,
    timeoutMs: Long = 30_000
): ModelDownloadManagerProgressSnapshot {
    var waited = 0
    while (waited < timeoutMs) {
        val progress = manager.downloadProgresses.value[modelId]
        if (progress != null && progress.status != DownloadStatus.DOWNLOADING && progress.status != DownloadStatus.VERIFYING) {
            return ModelDownloadManagerProgressSnapshot(progress.bytesDownloaded, progress.totalBytes,
                progress.downloadSpeedKbps, progress.status, progress.errorMessage.orEmpty())
        }
        delay(100)
        waited += 100
    }
    val last = manager.downloadProgresses.value[modelId]
    return ModelDownloadManagerProgressSnapshot(
        last?.bytesDownloaded ?: -1L, last?.totalBytes ?: -1L, last?.downloadSpeedKbps ?: -1L,
        DownloadStatus.ERROR, "tiempo de espera agotado"
    )
}

data class ModelDownloadManagerProgressSnapshot(
    val bytes: Long,
    val total: Long,
    val speedKbps: Long,
    val status: DownloadStatus,
    val error: String
)

fun main() = runBlocking {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    println("=== DL-1: una descarga real por HTTP instala exactamente los bytes servidos ===")
    val server = FakeModelServer()
    val storage = ModelStorage(Application())
    val manager = ModelDownloadManager(storage)
    val finished = AtomicBoolean(false)
    val model = descriptor(server.baseUrl)

    val job = scope.launch {
        manager.startOrResumeDownload(model, this) { ok -> finished.set(ok) }
    }
    val snapshot = awaitStatus(manager, model.id)
    job.join()

    check("la descarga terminó y informó éxito", finished.get() && snapshot.status == DownloadStatus.INSTALLED,
        "status=${snapshot.status} ok=${finished.get()}")
    check("el tamaño instalado es el que envió el servidor", snapshot.bytes == TOTAL_BYTES.toLong(),
        "bytes=${snapshot.bytes}")
    val installed = File(storage.getModelFolder(model.id), "model.gguf")
    check("el fichero instalado existe en el almacenamiento", installed.isFile, installed.path)
    val onDisk = installed.readBytes()
    check("cada byte del fichero es el que llegó por la red (nada de relleno)",
        onDisk.size == TOTAL_BYTES && onDisk.indices.all { onDisk[it] == expectedByte(it) },
        "len=${onDisk.size} primeros=${onDisk.take(4).joinToString()}")
    check("la velocidad mostrada es medida, no el literal 18500 kbps",
        snapshot.speedKbps > 0 && snapshot.speedKbps != 18500L, "kbps=${snapshot.speedKbps}")
    server.stop()

    println()
    println("=== DL-2: un descriptor sin URL real ya no se 'descarga' a base de relleno ===")
    val storage2 = ModelStorage(Application())
    val manager2 = ModelDownloadManager(storage2)
    val ok2 = AtomicBoolean(true)
    val noUrl = descriptor("huggingface://Qwen/Qwen3.5-3B-GGUF", id = "m-simulado")
    scope.launch { manager2.startOrResumeDownload(noUrl, this) { ok -> ok2.set(ok) } }
    val snap2 = awaitStatus(manager2, noUrl.id)
    check("informa error en lugar de instalarse", snap2.status == DownloadStatus.ERROR && !ok2.get(),
        "status=${snap2.status} ok=${ok2.get()}")
    check("explica que no hay URL de descarga", snap2.error.contains("no trae URL"), snap2.error.take(180))
    check("no aparece ningún .gguf falso",
        !File(storage2.getModelFolder(noUrl.id), "model.gguf").exists(), "sin archivo instalado")

    println()
    println("=== DL-3: si el servidor no responde, no queda ningn modelo instalado ===")
    val storage3 = ModelStorage(Application())
    val manager3 = ModelDownloadManager(storage3)
    val ok3 = AtomicBoolean(true)
    val dead = descriptor("http://127.0.0.1:11503/modelo.gguf", id = "m-muerto")
    scope.launch { manager3.startOrResumeDownload(dead, this) { ok -> ok3.set(ok) } }
    val snap3 = awaitStatus(manager3, dead.id)
    check("la caída del servidor se reporta como error", snap3.status == DownloadStatus.ERROR && !ok3.get(),
        "status=${snap3.status} ok=${ok3.get()}")
    check("el mensaje dice que la descarga real falló", snap3.error.contains("descarga real falló"),
        snap3.error.take(180))
    check("no se instala ningún modelo",
        !File(storage3.getModelFolder(dead.id), "model.gguf").exists(), "sin archivo instalado")

    println()
    println("=== DL-4: con SHA-256 declarado, la integridad se comprueba de verdad ===")
    val esperado = ByteArray(TOTAL_BYTES) { expectedByte(it) }
    val hashCorrecto = java.security.MessageDigest.getInstance("SHA-256").digest(esperado)
        .joinToString("") { "%02x".format(it) }

    val srvOk = FakeModelServer()
    val storOk = ModelStorage(Application())
    val mgrOk = ModelDownloadManager(storOk)
    val modOk = descriptor(srvOk.baseUrl, id = "m-hash-bueno").copy(sha256 = hashCorrecto)
    val okVerdadero = AtomicBoolean(false)
    val callbackVerdadero = AtomicBoolean(false)
    scope.launch { mgrOk.startOrResumeDownload(modOk, this) { ok -> okVerdadero.set(ok); callbackVerdadero.set(true) } }
    val snapOk = awaitStatus(mgrOk, modOk.id)
    // El estado terminal se publica ANTES de que el manager invoque onComplete: sin esperar al
    // callback, la aserción del booleano lee un valor todavía sin escribir.
    awaitFlag(callbackVerdadero)
    check("con el hash correcto la instalación pasa", snapOk.status == DownloadStatus.INSTALLED && okVerdadero.get(),
        "status=${snapOk.status} ok=${okVerdadero.get()}")
    check("el fichero instalado sigue en su sitio", File(storOk.getModelFolder(modOk.id), "model.gguf").isFile,
        "presente")
    srvOk.stop()

    val srvMalo = FakeModelServer()
    val storMalo = ModelStorage(Application())
    val mgrMalo = ModelDownloadManager(storMalo)
    val modMalo = descriptor(srvMalo.baseUrl, id = "m-hash-malo")
        .copy(sha256 = "0".repeat(64))
    val okFalso = AtomicBoolean(true)
    val callbackFalso = AtomicBoolean(false)
    scope.launch { mgrMalo.startOrResumeDownload(modMalo, this) { ok -> okFalso.set(ok); callbackFalso.set(true) } }
    val snapMalo = awaitStatus(mgrMalo, modMalo.id)
    awaitFlag(callbackFalso)
    check("un hash que no corresponde impide dar el modelo por instalado",
        snapMalo.status == DownloadStatus.ERROR && !okFalso.get(),
        "status=${snapMalo.status} ok=${okFalso.get()}")
    check("el mensaje de integridad es cierto: el archivo se descarta",
        snapMalo.error.contains("integridad") &&
                !File(storMalo.getModelFolder(modMalo.id), "model.gguf").exists(),
        snapMalo.error.take(120))
    srvMalo.stop()

    println()
    println("=== DL-5: invariantes de la ruta de descarga y de la configuración de fábrica ===")
    val fuenteDescargas = java.io.File(
        "../app-claw-main/app/src/main/java/com/example/clawcode/core/download/ModelDownloadManager.kt"
    ).readText()
    check("la conexión HTTP se libera siempre (finally + disconnect)",
        Regex("finally \\{[\\s\\S]{0,240}conexionHttp\\?\\.disconnect\\(\\)").containsMatchIn(fuenteDescargas) &&
                fuenteDescargas.contains("conexionHttp = connection"),
        "disconnect en finally")
    // Se comprueba el código, no los comentarios: una nota explica que existía runSimulation().
    val codigoDescargas = fuenteDescargas.lines().joinToString("\n") { it.substringBefore("//") }
    check("el descargador no vuelve a simular pesos", !codigoDescargas.contains("runSimulation"),
        "sin runSimulation en código")
    val configFabrica = com.example.clawcode.data.model.OllamaConfig()
    check("de fábrica la app no afirma ejecutar modelos en el dispositivo",
        !configFabrica.useOnDeviceLiteRTFallback, "useOnDeviceLiteRTFallback=${configFabrica.useOnDeviceLiteRTFallback}")

    scope.cancel()
    println()
    if (failures == 0) println("TODAS LAS PRUEBAS PASARON") else println("$failures PRUEBAS FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
