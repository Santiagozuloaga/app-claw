package verify

import android.content.Context
import android.content.FakeSystemServices
import android.content.Intent
import android.content.pm.FakePackages
import com.example.clawcode.AppConnectorManager
import com.google.ai.client.generativeai.FakeGenerative
import kotlinx.coroutines.runBlocking

/**
 * Ejecuta el `AppConnectorManager` REAL contra los stubs de Android del arnés. Es el paso que la
 * ronda anterior no pudo dar: ahí sólo se pudieron leer invariantes de texto porque el fichero no
 * estaba en la compilación. Añadidos `android.content.pm.PackageManager`, `android.net.Uri` y un
 * sustituto de `com.google.ai.client.generativeai` que CUENTA cada construcción de cliente y cada
 * `generateContent`, la ausencia de salida a la red deja de ser una inferencia sobre el orden del
 * código y pasa a ser un contador en cero.
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

fun main() {
    println("=== CB-1: dispositivos sin nada instalado: el panel no puede inventar ===")
    FakePackages.manager.instalados.clear()
    FakeSystemServices.ultimIntent = null
    val mgr = AppConnectorManager(Context())
    val sinNada = mgr.verificarEstadosApps()

    check("WhatsApp se mide por su paquete, no se asume", sinNada["WhatsApp"] == "No instalada",
        sinNada["WhatsApp"].orEmpty())
    check("Notion con placeholder NO dice 'Configurada'",
        sinNada["Notion"]?.contains("placeholder") == true, sinNada["Notion"].orEmpty())
    check("GitHub con placeholder NO dice 'Configurada'",
        sinNada["GitHub"]?.contains("placeholder") == true, sinNada["GitHub"].orEmpty())
    check("ninguna etiqueta devuelve el contenido del token",
        sinNada.values.none { it.contains("secret_tu_token") || it.contains("ghp_tu_token") },
        "sólo estados, nunca valores")
    check("AI Edge sin AICore se declara no disponible",
        sinNada["AI Edge (Gemini Nano)"]?.startsWith("No disponible") == true,
        sinNada["AI Edge (Gemini Nano)"].orEmpty())
    check("Workspace sin Play Services no promete 'Listo'",
        sinNada["Google Workspace"]?.startsWith("Sin Google Play") == true,
        sinNada["Google Workspace"].orEmpty())

    println()
    println("=== CB-2: con los paquetes presentes, la detección cambia pero no se inventa inferencia ===")
    FakePackages.manager.instalados += setOf("com.whatsapp", "com.google.android.gms", "com.google.android.aicore")
    val conTodo = mgr.verificarEstadosApps()
    check("WhatsApp pasa a verificado", conTodo["WhatsApp"]?.startsWith("Verificada") == true,
        conTodo["WhatsApp"].orEmpty())
    check("Workspace detecta los servicios de Google", conTodo["Google Workspace"]?.startsWith("Servicios de Google presentes") == true,
        conTodo["Google Workspace"].orEmpty())
    check("AI Edge detectado, y sigue declarando que esta build no ejecuta en él",
        conTodo["AI Edge (Gemini Nano)"]?.contains("AICore presente") == true &&
            conTodo["AI Edge (Gemini Nano)"]?.contains("no ejecuta inferencia") == true,
        conTodo["AI Edge (Gemini Nano)"].orEmpty())

    println()
    println("=== CB-3: la ruta 'AI Edge' no saca una petición a la red con credencial de adorno ===")
    FakeGenerative.reset()
    val respuesta = runBlocking { mgr.aiEdgeProcesarLocal("prueba de arnés") }
    check("no se construye el cliente de la Gemini API", FakeGenerative.construidos.get() == 0,
        "construidos=${FakeGenerative.construidos.get()}")
    check("no se llama a generateContent (cero salidas de red)", FakeGenerative.llamadas.get() == 0,
        "llamadas=${FakeGenerative.llamadas.get()}")
    check("el texto dice que no se usó y que no es inferencia en el dispositivo",
        respuesta.contains("[AI EDGE NO USADO]") && respuesta.contains("no ejecuta ningún modelo dentro del"),
        respuesta.take(140))

    println()
    println("=== CB-4: el Intent real de WhatsApp (lo que sí hace la app) ===")
    mgr.whatsappEnviarMensaje("+34 600 111 222", "hola mundo")
    val intent = FakeSystemServices.ultimIntent
    check("se lanza ACTION_VIEW fijando el paquete de WhatsApp",
        intent?.action == Intent.ACTION_VIEW && intent?.packageName == "com.whatsapp",
        "accion=${intent?.action} paquete=${intent?.packageName}")
    val url = intent?.data?.toString().orEmpty()
    check("el teléfono llega sin separadores y el texto escapado",
        url.contains("34600111222") && url.contains("hola+mundo"), url)
    check("y con FLAG_ACTIVITY_NEW_TASK, necesario para arrancar desde un contexto de aplicación",
        (intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_NEW_TASK != 0, "flags=${intent?.flags}")

    println()
    println(if (failures == 0) "RESULTADO CONECTORES-CONDUCTA: 0 FAIL" else "RESULTADO CONECTORES-CONDUCTA: $failures FAIL")
}
