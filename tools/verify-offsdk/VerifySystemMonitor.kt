package verify

import android.app.Application
import android.content.FakeSystemServices
import com.example.clawcode.core.telemetry.SystemMonitor
import com.example.clawcode.data.model.HardwareTelemetry
import java.io.File

/**
 * SystemMonitor inventaba la carga de CPU con `(15..35).random()` y el Dashboard la enseñaba como
 * actividad del teléfono. Aquí se comprueba que batería, RAM y carga salen de fuentes que se
 * pueden señalar, que la carga es un delta real de /proc/stat y que sin dos lecturas no se suelta
 * ningún número.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private fun procStat(user: Long, system: Long, idle: Long, iowait: Long): String =
    "cpu  $user 0 $system $idle $iowait 0 0 0 0 0\n" +
            "cpu0 $user 0 $system $idle $iowait 0 0 0 0 0\n" +
            "intr 12345\n"

private fun sampleFile(dir: File, name: String, content: String): File =
    File(dir, name).apply { writeText(content) }

fun main() {
    val dir = File(System.getProperty("java.io.tmpdir"), "claw-procstat-${System.nanoTime()}")
    dir.mkdirs()
    val context = Application()

    println("=== SM-1: batería y RAM salen de las APIs del sistema, no de literales ===")
    FakeSystemServices.reset()
    FakeSystemServices.setBattery(level = 60, scale = 100, temperatureTenths = 295)
    FakeSystemServices.setMemory(totalBytes = 8L * 1024 * 1024 * 1024, availableBytes = 5L * 1024 * 1024 * 1024)
    val missingProc = File(dir, "no-existe-stat")
    val first = SystemMonitor(context, missingProc.path).captureMetrics(HardwareTelemetry())
    check("nivel de batería medido (60 %)", first.batteryLevelPercent == 60, "${first.batteryLevelPercent}")
    check("temperatura en décimas convertida (29.5 °C)", first.batteryTemperatureC == 29.5f,
        "${first.batteryTemperatureC}")
    check("RAM total medida (8 GB)", first.ramTotalGb == 8.0f, "${first.ramTotalGb}")
    check("RAM usada = total - disponible (3 GB)", first.ramUsedGb in 2.99f..3.01f, "${first.ramUsedGb}")
    check("núcleos = disponibles según la JVM, no el literal 8",
        first.cpuCores == Runtime.getRuntime().availableProcessors(), "${first.cpuCores}")

    println()
    println("=== SM-2: sin /proc/stat legible no se inventa ninguna carga de CPU ===")
    check("la carga sigue sin medir (0), no un número al azar", first.cpuUsagePercent == 0,
        "${first.cpuUsagePercent}")
    check("no se registra ninguna instantánea falsa", first.cpuTotalJiffies == 0L,
        "${first.cpuTotalJiffies}")
    val repeats = (1..8).map {
        SystemMonitor(context, missingProc.path).captureMetrics(HardwareTelemetry()).cpuUsagePercent
    }.toSet()
    check("ocho sondeos sin /proc/stat dan siempre el mismo valor", repeats.size == 1 && repeats.first() == 0,
        "valores=${repeats.sorted()}")

    println()
    println("=== SM-3: la carga es el delta real entre dos lecturas de /proc/stat ===")
    val statA = sampleFile(dir, "stat-a", procStat(user = 100, system = 100, idle = 700, iowait = 100))
    val afterFirst = SystemMonitor(context, statA.path).captureMetrics(HardwareTelemetry())
    check("la primera lectura sólo guarda la instantánea",
        afterFirst.cpuTotalJiffies == 1000L && afterFirst.cpuIdleJiffies == 800L &&
                afterFirst.cpuUsagePercent == 0,
        "total=${afterFirst.cpuTotalJiffies} idle=${afterFirst.cpuIdleJiffies} carga=${afterFirst.cpuUsagePercent}")

    // Segunda lectura: total pasa de 1000 a 2000 (+1000) y el tiempo ocioso de 800 a 1400 (+600)
    // -> 400 jiffies de trabajo sobre 1000 = 40 %.
    statA.writeText(procStat(user = 300, system = 300, idle = 1300, iowait = 100))
    val afterSecond = SystemMonitor(context, statA.path).captureMetrics(afterFirst)
    check("el porcentaje sale de los deltas, no de un azar", afterSecond.cpuUsagePercent == 40,
        "carga=${afterSecond.cpuUsagePercent}% (esperado 40 %)")
    check("la instantánea queda actualizada para el siguiente sondeo",
        afterSecond.cpuTotalJiffies == 2000L && afterSecond.cpuIdleJiffies == 1400L,
        "total=${afterSecond.cpuTotalJiffies} idle=${afterSecond.cpuIdleJiffies}")

    // Mismo delta cero: si el sistema no avanza entre lecturas, la carga no se inventa.
    val afterIdle = SystemMonitor(context, statA.path).captureMetrics(afterSecond)
    check("sin delta de tiempo no hay carga nueva (se conserva)", afterIdle.cpuUsagePercent == 40,
        "${afterIdle.cpuUsagePercent}")

    println()
    println("=== SM-4: un /proc/stat malformado no produce números falsos ===")
    val broken = sampleFile(dir, "broken", "cpu  12 34\n")
    val fromBroken = SystemMonitor(context, broken.path).captureMetrics(HardwareTelemetry())
    check("una fila incompleta se descarta en vez de dividirse", fromBroken.cpuTotalJiffies == 0L,
        "${fromBroken.cpuTotalJiffies}")
    check("y la carga se queda sin medir", fromBroken.cpuUsagePercent == 0, "${fromBroken.cpuUsagePercent}")

    FakeSystemServices.reset()
    dir.deleteRecursively()

    println()
    if (failures == 0) println("TODAS LAS PRUEBAS PASARON") else println("$failures PRUEBAS FALLARON")
    if (failures != 0) kotlin.system.exitProcess(1)
}
