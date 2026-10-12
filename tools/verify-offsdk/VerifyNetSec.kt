package verify

import com.example.clawcode.data.model.OllamaConfig
import org.w3c.dom.Element
import java.io.File
import java.net.URI
import javax.xml.parsers.DocumentBuilderFactory

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private val ROOT = File(System.getProperty("claw.repo")
    ?: "C:/Users/Admin/Documents/Qoder/2026-10-11/4f98c400/app-claw-main")
private val SRC = File(ROOT, "app/src/main/java/com/example/clawcode")
private val MANIFEST = File(ROOT, "app/src/main/AndroidManifest.xml")
private val NETSEC = File(ROOT, "app/src/main/res/xml/network_security_config.xml")

private fun parse(f: File): Element =
    DocumentBuilderFactory.newInstance().apply { isNamespaceAware = false }
        .newDocumentBuilder().parse(f).documentElement

private fun cleartextDomains(root: Element): Set<String> {
    val out = mutableSetOf<String>()
    val configs = root.getElementsByTagName("domain-config")
    for (i in 0 until configs.length) {
        val cfg = configs.item(i) as Element
        if (!cfg.getAttribute("cleartextTrafficPermitted").equals("true", ignoreCase = true)) continue
        val domains = cfg.getElementsByTagName("domain")
        for (j in 0 until domains.length) out += domains.item(j).textContent.trim()
    }
    return out
}

private fun baseConfigPermitsCleartext(root: Element): Boolean {
    val bases = root.getElementsByTagName("base-config")
    if (bases.length == 0) return false
    return (bases.item(0) as Element)
        .getAttribute("cleartextTrafficPermitted").equals("true", ignoreCase = true)
}

/** Hosts de runtime local que aparecen como literales en las fuentes de la app. */
private fun literalRuntimeHosts(): Set<String> {
    val rx = Regex("http://([A-Za-z0-9._\\-\\[\\]:]+):11434")
    val found = mutableSetOf<String>()
    SRC.walkTopDown().filter { it.extension == "kt" }.forEach { f ->
        rx.findAll(f.readText()).forEach { m ->
            val raw = m.groupValues[1]
            // "192.168.1.x" es texto de ayuda al usuario, no un destino real: se descarta
            // como host y se trata aparte en NETSEC-4.
            if (!raw.endsWith(".x")) found += try { URI("http://$raw:11434").host ?: raw } catch (_: Exception) { raw }
        }
    }
    return found
}

private val LOCAL_HOSTS = setOf("localhost", "ip6-localhost", "127.0.0.1", "[::1]", "::1", "10.0.2.2")

fun main() {
    println("=== NETSEC-1: Android bloquea el HTTP en claro; la config debe autorizar el runtime local ===")
    check("existe network_security_config.xml", NETSEC.isFile, NETSEC.path)
    check("existe AndroidManifest.xml", MANIFEST.isFile, MANIFEST.path)
    if (!NETSEC.isFile || !MANIFEST.isFile) { println("\nRESULTADO NETSEC: $failures FAIL"); return }

    val app = parse(MANIFEST).getElementsByTagName("application").item(0) as Element
    val ref = app.getAttribute("android:networkSecurityConfig")
    check("<application> referencia la config de red", ref == "@xml/network_security_config", "'$ref'")
    // usesCleartextTraffic="true" global permitiría texto plano a CUALQUIER host, incluidos los que
    // llevan tokens; con networkSecurityConfig presente debe quedar acotado por dominio.
    val blanket = app.getAttribute("android:usesCleartextTraffic")
    check("sin permiso global de texto plano (usesCleartextTraffic)",
        !blanket.equals("true", ignoreCase = true), "'${blanket.ifBlank { "ausente" }}'")

    val netsec = parse(NETSEC)
    check("raíz del documento correcta", netsec.tagName == "network-security-config", netsec.tagName)
    val allowed = cleartextDomains(netsec)
    println("    dominios autorizados en claro: ${allowed.sorted()}")

    println()
    println("=== NETSEC-2: todo host local que la app usa está autorizado ===")
    // Valor real leído de la app compilada: si alguien cambia el host por defecto y olvida la
    // config, esto falla. (El companion de ClawEngine es privado; OllamaConfig es público.)
    val factoryHost = URI(OllamaConfig().hostUrl).host
    println("    hostUrl de fábrica (leído de OllamaConfig): $factoryHost")
    check("hostUrl de fábrica autorizado para HTTP en claro", factoryHost in allowed,
        if (factoryHost in allowed) "cubierto" else "NO cubierto -> IOException en dispositivo")

    val literals = literalRuntimeHosts()
    println("    hosts de runtime literalmente presentes en las fuentes: ${literals.sorted()}")
    check("se encontró el host de descubrimiento 127.0.0.1", "127.0.0.1" in literals, "$literals")
    literals.filter { it in LOCAL_HOSTS }.forEach { h ->
        check("'$h' autorizado para HTTP en claro", h in allowed,
            if (h in allowed) "cubierto" else "NO cubierto -> IOException en dispositivo")
    }

    println()
    println("=== NETSEC-3: los servicios externos con tokens NO pueden degradarse a texto plano ===")
    check("base-config no permite texto plano global", !baseConfigPermitsCleartext(netsec),
        "base-config cleartextTrafficPermitted=false")
    listOf("api.notion.com", "api.github.com", "api.openclaw.io", "api.whatsapp.com",
        "generativelanguage.googleapis.com").forEach { h ->
        check("'$h' fuera de la lista de texto plano", h !in allowed,
            if (h !in allowed) "protegido (solo https)" else "EXPUESTO en claro")
    }
    check("solo hay loopback/alias del emulador autorizados", allowed.all { it in LOCAL_HOSTS },
        "autorizados=${allowed.sorted()}")

    println()
    println("=== NETSEC-4: limitación conocida — un Ollama en la LAN no está cubierto ===")
    // La plataforma no admite comodines ni CIDR, así que un host LAN configurable por el usuario
    // no puede autorizarse de forma genérica. Se verifica que la app lo anuncia y que la config
    // NO lo abre en canal (abrirlo exigiría usar usesCleartextTraffic global, que NETSEC-3 veta).
    val lanHint = File(SRC, "engine/ClawEngine.kt").readText().contains("192.168.1.x")
    check("la app sigue sugiriendo un host LAN al usuario", lanHint, "mencionado en el mensaje de ayuda")
    check("ninguna IP privada concreta queda autorizada por accidente",
        allowed.none { it.startsWith("192.168.") || it.startsWith("10.0.") && it != "10.0.2.2" },
        "autorizados=${allowed.sorted()}")

    println()
    println(if (failures == 0) "RESULTADO NETSEC: 0 FAIL" else "RESULTADO NETSEC: $failures FAIL")
}
