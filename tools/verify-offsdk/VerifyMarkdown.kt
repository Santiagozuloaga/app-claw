package verify

import com.example.clawcode.ui.util.EstiloMarkdown
import com.example.clawcode.ui.util.fragmentarMarkdown
import com.example.clawcode.ui.util.textoPlanoDeMarkdown

/**
 * El runtime contesta en Markdown. En el celular se vio literal la burbuja
 * "1. **Visualizar contenido**: Utiliza la cámara…" con los asteriscos a la vista, así que el
 * parseador de `ui/util/MarkdownInline.kt` es ahora parte del camino del chat: lo que se pinta y
 * lo que lee el sintetizador salen de ahí. Estas pruebas usan la captura real del teléfono como
 * fixture y, sobre todo, comprueban los falsos positivos (una multiplicación o un snake_case no
 * pueden abrir un énfasis).
 */

private var failures = 0

private fun check(name: String, condition: Boolean, detail: String) {
    if (condition) println("  PASS  $name -> $detail")
    else { failures++; println("  FAIL  $name -> $detail") }
}

private fun plano(cadena: String) = textoPlanoDeMarkdown(cadena)

private fun estiloDe(cadena: String, buscar: String): EstiloMarkdown? =
    fragmentarMarkdown(cadena).firstOrNull { it.texto == buscar }?.estilo

/** Literal de la burbuja tal y como llegó del modelo por el túnel adb (qwen2.5:0.5b). */
private const val RESPUESTA_DEL_TELEFONO =
    "Aquí tienes 3 acciones útiles que puedes probar:\n\n" +
        "1. **Visualizar contenido**: Utiliza la cámara para capturar imágenes y luego muéstralas.\n" +
        "2. **Análisis de imágenes**: Si tienes un conjunto de imágenes, puedes usar el reconocimiento.\n" +
        "3. **Análisis de datos históricos**: Si estás trabajando con datos históricos.\n"

fun main() {
    println("== VerifyMarkdown: parseador de las burbujas del chat ==")

    val fragmentosTelefono = fragmentarMarkdown(RESPUESTA_DEL_TELEFONO)
    val unido = fragmentosTelefono.joinToString("") { it.texto }
    check(
        "MD-1 literal ** desaparece de la burbuja",
        !unido.contains("**") && unido.contains("Visualizar contenido"),
        "sin asteriscos, ${fragmentosTelefono.size} fragmentos"
    )
    check(
        "MD-2 el tramo negrita llega marcado, no borrado",
        fragmentosTelefono.any { it.texto == "Visualizar contenido" && it.estilo == EstiloMarkdown.NEGRITA },
        "estilo NEGRITA preservado"
    )
    val numeraciones = Regex("""(?m)^\d+\.\s""").findAll(unido).count()
    check(
        "MD-3 la numeración de la lista se conserva",
        numeraciones == 3,
        "$numeraciones encabezados numerados en la salida"
    )

    val vinetas = fragmentarMarkdown("- **a** uno\n* dos\n+ tres")
    val textoVinetas = vinetas.joinToString("") { it.texto }
    check(
        "MD-4 viñetas -, *, + se convierten en • sin comerse el texto",
        Regex("""• a uno""").containsMatchIn(textoVinetas) &&
            Regex("""• dos""").containsMatchIn(textoVinetas) &&
            Regex("""• tres""").containsMatchIn(textoVinetas),
        "tres viñetas normalizadas"
    )

    val codigo = fragmentarMarkdown("Mira:\n```kotlin\nval x = 1\n```\nfin")
    val textoCodigo = codigo.joinToString("") { it.texto }
    check(
        "MD-5 valla de código: fuera los backticks, dentro estilo CODIGO",
        !textoCodigo.contains("```") &&
            codigo.any { it.estilo == EstiloMarkdown.CODIGO && it.texto == "val x = 1" } &&
            textoCodigo.contains("fin"),
        "bloque marcado y resto intacto"
    )

    val titulo = fragmentarMarkdown("## Plan de trabajo")
    check(
        "MD-6 encabezado sin almohadillas y con su estilo",
        titulo.singleOrNull { it.texto.contains("Plan") }?.estilo == EstiloMarkdown.TITULO &&
            !titulo.joinToString("") { it.texto }.contains("#"),
        "TITULO en lugar de ## literal"
    )

    check(
        "MD-7 el texto que lee el sintetizador no suena marcas",
        plano(RESPUESTA_DEL_TELEFONO).let { !it.contains('*') && !it.contains('#') && !it.contains('`') },
        "textoPlanoDeMarkdown limpio de marcas"
    )

    val ambiguo = fragmentarMarkdown("el resultado de 2 * 3 * 4 es 12")
    check(
        "MD-8 una multiplicación no abre cursiva",
        ambiguo.none { it.estilo == EstiloMarkdown.CURSIVA },
        "astiscos sueltos se quedan literales"
    )
    val snake = fragmentarMarkdown("usa nombre_de_variable en Kotlin")
    check(
        "MD-9 snake_case no se cursiva",
        snake.none { it.estilo == EstiloMarkdown.CURSIVA } &&
            snake.joinToString("") { it.texto } == "usa nombre_de_variable en Kotlin",
        "guiones bajos intra-palabra ignorados"
    )
    val sinCierre = fragmentarMarkdown("esto abre ** y no cierra nunca")
    check(
        "MD-10 marca sin cierre se pinta literal, no se traga el resto",
        sinCierre.joinToString("") { it.texto } == "esto abre ** y no cierra nunca",
        "round-trip exacto"
    )

    val enfasis = estiloDe("esto es *importante* para ti", "importante")
    check(
        "MD-11 cursiva con un asterisco sí se reconoce",
        enfasis == EstiloMarkdown.CURSIVA,
        "estilo=$enfasis"
    )

    val vacio = fragmentarMarkdown("")
    check(
        "MD-12 cadena vacía no inventa fragmentos",
        vacio.isEmpty() || vacio.all { it.texto.isEmpty() },
        "${vacio.size} fragmentos"
    )

    val mixto = fragmentarMarkdown("Prueba `inline` y **negrita** juntas")
    check(
        "MD-13 estilos mezclados en la misma línea",
        mixto.any { it.estilo == EstiloMarkdown.CODIGO && it.texto == "inline" } &&
            mixto.any { it.estilo == EstiloMarkdown.NEGRITA && it.texto == "negrita" } &&
            !mixto.joinToString("") { it.texto }.contains('`'),
        "código inline y negrita conviven"
    )

    val larga = fragmentarMarkdown(
        "**Fuera de rango**: el valor `999` supera el máximo.\n" +
            "> Revisar con el operador antes de continuar."
    )
    val textoLarga = larga.joinToString("") { it.texto }
    check(
        "MD-14 cita: el '>' se quita y el texto se conserva",
        !textoLarga.contains(">") && textoLarga.contains("Revisar con el operador"),
        "bloque de cita normalizado"
    )

    println(if (failures == 0) "RESULTADO VerifyMarkdown: OK" else "RESULTADO VerifyMarkdown: $failures FALLOS")
    if (failures != 0) kotlin.system.exitProcess(1)
}
