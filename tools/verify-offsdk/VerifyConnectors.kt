package verify

/**
 * El panel "APP por APP" de ToolsScreen era la última superficie que afirmaba capacidades sin
 * medirlas: Notion y GitHub se marcaban "Configurada" *porque* el token era el placeholder
 * (`startsWith("secret_")` con valor "secret_tu_token_aqui"), "AI Edge (Gemini Nano)" se declaraba
 * "Disponible en el dispositivo" sin sondear nada, y `aiEdgeProcesarLocal()` prometía AICore en el
 * procesador usando el cliente de RED de Gemini con `modelName = "gemini-nano"` y una clave
 * placeholder, o sea: salía a la red para fallar.
 *
 * Método: invariantes sobre el texto real del fichero. `AppConnectorManager.kt` NO entra en la
 * compilación off-SDK porque depende de `com.google.ai.client.generativeai`, `PackageManager` y
 * `android.net.Uri`, que no están en `libs/`. Así que aquí se comprueba la estructura que sostiene
 * la afirmación (orden del retorno temprano, presencia de la sonda, valores de fábrica) y se dice
 * expresamente que la ejecución conductual de este fichero no está probada.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private fun fuente(): String {
    val candidatos = listOf(
        "../app-claw-main/app/src/main/java/com/example/clawcode/AppConnectorManager.kt",
        "app-claw-main/app/src/main/java/com/example/clawcode/AppConnectorManager.kt"
    )
    val f = candidatos.map { java.io.File(it) }.firstOrNull { it.isFile }
        ?: error("no se encuentra AppConnectorManager.kt en ${candidatos.joinToString(" ")}")
    return f.readText().lines().filterNot { it.trimStart().startsWith("//") }.joinToString("\n")
}

private fun literales(src: String): String =
    Regex("\"([^\"]*)\"").findAll(src).joinToString("\n") { it.groupValues[1] }

fun main() {
    println("=== CN-1: los estados de las apps se miden o se declaran ausentes, no se afirman ===")
    val src = fuente()
    check("ya no existe la afirmación incondicional de AI Edge",
        !src.contains("\"Disponible en el dispositivo\""), "eliminada")
    check("AI Edge se sondea por paquete, como WhatsApp",
        Regex("estados\\[\"AI Edge \\(Gemini Nano\\)\"\\] = if \\(esAppInstalada\\(\"com.google.android.aicore\"\\)")
            .containsMatchIn(src),
        "usa el mismo explorador de paquetes")
    check("Google Workspace deja de prometer 'Listo' sin comprobar nada",
        !literales(src).contains("Listo (Usa la cuenta activa del sistema)"), "ahora sondea Play Services")

    println()
    println("=== CN-2: la lógica invertida de credenciales ===")
    check("Ninguna etiqueta 'Configurada' se deriva de startsWith del placeholder",
        !Regex("startsWith\\(\"(secret_|ghp_)\"\\)").containsMatchIn(src),
        "el patrón que invertía el sentido desapareció")
    val placeholderNotion = Regex("NOTION_TOKEN = \"([^\"]+)\"").find(src)?.groupValues?.get(1).orEmpty()
    val placeholderGithub = Regex("GITHUB_TOKEN = \"([^\"]+)\"").find(src)?.groupValues?.get(1).orEmpty()
    check("los valores de fábrica siguen siendo placeholder (sin credenciales reales publicadas)",
        placeholderNotion.startsWith("secret_tu_token") && placeholderGithub.startsWith("ghp_tu_token") &&
            placeholderNotion != "secret_" && placeholderGithub != "ghp_",
        "secret_tu_token… / ghp_tu_token…")
    check("la comparación usa el prefijo largo del placeholder, no 'secret_' / 'ghp_' a secas",
        src.contains("esCredencialReal(NOTION_TOKEN, \"secret_tu_token\")") &&
            src.contains("esCredencialReal(GITHUB_TOKEN, \"ghp_tu_token\")"),
        "prefijos explícitos")
    check("el helper nunca devuelve el contenido del token",
        Regex("private fun esCredencialReal\\([^)]*\\): Boolean =\\s*valor\\.isNotBlank\\(\\) && !valor\\.startsWith\\(prefijoPlaceholder\\)")
            .containsMatchIn(src), "sólo longitud y prefijo")

    println()
    println("=== CN-3: ninguna salida a la red con credencial de adorno ===")
    check("la clave de Gemini es una constante de fábrica, no un literal suelto en la llamada",
        Regex("AI_EDGE_KEY = \"TU_API_KEY").containsMatchIn(src) &&
            !src.contains("apiKey = \"TU_API_KEY"), "centralizada")
    val posicionGuardia = src.indexOf("if (!esCredencialReal(AI_EDGE_KEY")
    val posicionModelo = src.indexOf("GenerativeModel(")
    check("el retorno temprano existe y está ANTES de construir el cliente de red",
        posicionGuardia in 0 until posicionModelo,
        "guardia@$posicionGuardia < modelo@$posicionModelo")
    check("el mensaje dice que es una llamada remota, no inferencia local",
        literales(src).contains("AI EDGE NO USADO") && src.contains("no ejecuta ningún modelo dentro del"),
        "copy honesto")
    check("el comentario que prometía AICore en el procesador ya no afirma que suceda",
        !src.contains("Se conecta directamente a AICore"), "corregido")
    check("no se usa el nombre de modelo que ese cliente no admite",
        !src.contains("gemini-nano"), "fuera")

    println()
    println("=== CN-4: la pantalla sigue consumiendo el mismo mapa ===")
    val pantalla = runCatching {
        java.io.File("../app-claw-main/app/src/main/java/com/example/clawcode/ui/screens/ToolsScreen.kt")
            .takeIf { it.isFile }?.readText()
            ?: java.io.File("app-claw-main/app/src/main/java/com/example/clawcode/ui/screens/ToolsScreen.kt").readText()
    }.getOrNull().orEmpty()
    check("ToolsScreen sigue leyendo la clave 'AI Edge (Gemini Nano)' (no se rompió la UI)",
        pantalla.contains("estados[\"AI Edge (Gemini Nano)\"]"), "contrato del mapa intacto")
    check("las teclas Notion y GitHub siguen existiendo",
        src.contains("estados[\"Notion\"]") && src.contains("estados[\"GitHub\"]"), "mismas claves")

    println()
    println("  NOTA de método: este fichero no se compila fuera del SDK (GenerativeModel, " +
            "PackageManager, android.net.Uri no están en libs/); lo verificado son invariantes " +
            "estructurales sobre la fuente real, no ejecución conductual.")
    println()
    println(if (failures == 0) "RESULTADO CONECTORES: 0 FAIL" else "RESULTADO CONECTORES: $failures FAIL")
}
