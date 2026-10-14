package verify

import com.example.clawcode.engine.voice.GatePalabraClave

/**
 * El filtro que decide si una frase dictada va dirigida al agente. Es lo único que separa "el
 * micrófono está abierto y se transcribe todo" de "CLAW actuó porque sí", así que sus casos
 * borde importan: sin separador no debe invocar, y la palabra clave sola no debe lanzar un turno
 * vacío contra el runtime.
 */

private var failures = 0

private fun check(nombre: String, condicion: Boolean, detalle: String) {
    if (condicion) println("  PASS  $nombre -> $detalle")
    else { failures++; println("  FAIL  $nombre -> $detalle") }
}

private fun comando(texto: String, clave: String = "JARVIS"): String? =
    GatePalabraClave.comandoDe(texto, clave)

fun main() {
    println("=== VerifyVoiceGate: qué activa al agente con la voz ===")

    check("VG-1 palabra clave con coma extrae el comando",
        comando("Jarvis, abre gmail") == "abre gmail", "${comando("Jarvis, abre gmail")}")

    check("VG-2 el match de la clave no distingue mayúsculas",
        comando("jarvis abre gmail") == "abre gmail" &&
            comando("JARVIS: abre gmail") == "abre gmail",
        "minus, mayus y dos puntos")

    check("VG-3 sin la clave al principio no se ejecuta nada",
        comando("abre gmail") == null, "null: la frase no iba dirigida al agente")

    check("VG-4 la clave sola no lanza un turno vacío",
        comando("Jarvis") == null && comando("jarvis,") == null,
        "invocar sin comando no manda nada al runtime")

    check("VG-5 sin separador no es la clave (no debe casar 'jarvismodelo')",
        comando("jarvismodelo funciona") == null, "null: exigió separador")

    check("VG-6 espacios y puntuación sueltos no cuentan como comando",
        comando("Jarvis,   ") == null && comando("Jarvis .") == null,
        "null en lugar de un turno en blanco")

    check("VG-7 transcripción o clave vacías devuelven null",
        comando("", "JARVIS") == null && comando("Jarvis, hola", "  ") == null,
        "ninguno de los dos lados puede estar vacío")

    check("VG-8 la clave se puede cambiar por la del modfile",
        comando("claw abre el wifi", "CLAW") == "abre el wifi" &&
            comando("jarvis abre el wifi", "CLAW") == null,
        "sigue la palabra clave configurada, no una constante")

    check("VG-9 la clave debe ir al principio, no en cualquier parte",
        comando("hola jarvis, abre gmail") == null,
        "dictado con saludo previo no invoca: es deliberado, evita falsos positivos")

    check("VG-10 menciona() sí sirve para el modo sin puerta",
        GatePalabraClave.menciona("hola jarvis, qué tiempo hace", "JARVIS") &&
            !GatePalabraClave.menciona("qué tiempo hace", "JARVIS"),
        "para la UI que quiera mostrar 'te estaban llamando'")

    check("VG-11 conserva el resto del texto tal cual, sin reescribirlo",
        comando("Jarvis, pon música de jazz suave") == "pon música de jazz suave",
        "no recorta ni normaliza el comando")

    check("VG-12 dos espacios seguidos tras la clave no rompen el parseo",
        comando("Jarvis,  abre   gmail") == "abre   gmail",
        "espacios interiores intactos: el runtime los ve igual que el usuario los dictó")

    println(if (failures == 0) "RESULTADO VOICE-GATE: 0 FAIL" else "RESULTADO VOICE-GATE: $failures FAIL")
    if (failures != 0) kotlin.system.exitProcess(1)
}
