package com.example.clawcode.ui.util

/**
 * Parser mínimo de Markdown inline. El modelo responde con `**negritas**`, viñetas y bloques de
 * código, y la burbuja del chat los pintaba literales. Se mantiene sin dependencias de Compose
 * para que el arnés de JVM lo pueda ejecutar de verdad.
 */
enum class EstiloMarkdown { NINGUNO, NEGRITA, CURSIVA, CODIGO, TITULO }

data class FragmentoMarkdown(val texto: String, val estilo: EstiloMarkdown)

private val TITULO = Regex("^#{1,6}\\s+(.*)$")
private val VIÑETA = Regex("^\\s*[-*+]\\s+")
private val CITA = Regex("^\\s*>\\s?")
private val LISTA_ORDENADA = Regex("^(\\s*)(\\d+)[.)]\\s+")

fun fragmentarMarkdown(cadena: String): List<FragmentoMarkdown> {
    val salida = mutableListOf<FragmentoMarkdown>()
    var enBloque = false
    cadena.split('\n').forEachIndexed { indice, lineaOriginal ->
        if (lineaOriginal.trimStart().startsWith("```")) {
            enBloque = !enBloque
            return@forEachIndexed
        }
        if (indice > 0) {
            salida.add(FragmentoMarkdown("\n", if (enBloque) EstiloMarkdown.CODIGO else EstiloMarkdown.NINGUNO))
        }
        if (enBloque) {
            salida.add(FragmentoMarkdown(lineaOriginal, EstiloMarkdown.CODIGO))
            return@forEachIndexed
        }
        val recortada = lineaOriginal.trimEnd()
        val titulo = TITULO.find(recortada)
        if (titulo != null) {
            fragmentarInline(titulo.groupValues[1], EstiloMarkdown.TITULO, salida)
            return@forEachIndexed
        }
        val cuerpo = when {
            VIÑETA.containsMatchIn(recortada) -> VIÑETA.replaceFirst(recortada, "• ")
            CITA.containsMatchIn(recortada) -> CITA.replaceFirst(recortada, "")
            else -> LISTA_ORDENADA.replaceFirst(recortada, "$1$2. ")
        }
        fragmentarInline(cuerpo, EstiloMarkdown.NINGUNO, salida)
    }
    return salida
}

/** Lo que debe leer el sintetizador o un `Text` plano: sin marcas y sin vallas de código. */
fun textoPlanoDeMarkdown(cadena: String): String =
    fragmentarMarkdown(cadena).joinToString("") { it.texto }.trim()

private fun fragmentarInline(
    linea: String,
    estilo: EstiloMarkdown,
    salida: MutableList<FragmentoMarkdown>
) {
    val acumulado = StringBuilder()
    fun vaciar() {
        if (acumulado.isNotEmpty()) {
            salida.add(FragmentoMarkdown(acumulado.toString(), estilo))
            acumulado.setLength(0)
        }
    }
    var i = 0
    while (i < linea.length) {
        val c = linea[i]
        val par = if (i + 1 < linea.length) linea.substring(i, i + 2) else ""
        if ((par == "**" || par == "__")) {
            val cierre = buscarCierre(linea, i + 2, par)
            if (cierre > 0) {
                vaciar()
                fragmentarInline(linea.substring(i + 2, cierre), combinar(estilo, EstiloMarkdown.NEGRITA), salida)
                i = cierre + 2
                continue
            }
        } else if (c == '`') {
            val cierre = linea.indexOf('`', i + 1)
            if (cierre > 0) {
                vaciar()
                salida.add(FragmentoMarkdown(linea.substring(i + 1, cierre), EstiloMarkdown.CODIGO))
                i = cierre + 1
                continue
            }
        } else if (c == '*' && par != "**") {
            val cierre = buscarCierre(linea, i + 1, "*")
            if (cierre > 0) {
                vaciar()
                fragmentarInline(linea.substring(i + 1, cierre), combinar(estilo, EstiloMarkdown.CURSIVA), salida)
                i = cierre + 1
                continue
            }
        } else if (c == '_' && par != "__") {
            val cierre = buscarCierre(linea, i + 1, "_")
            if (cierre > 0 && (cierre + 1 >= linea.length || !linea[cierre + 1].isLetterOrDigit())) {
                vaciar()
                fragmentarInline(linea.substring(i + 1, cierre), combinar(estilo, EstiloMarkdown.CURSIVA), salida)
                i = cierre + 1
                continue
            }
        }
        acumulado.append(c)
        i++
    }
    vaciar()
}

private fun combinar(actual: EstiloMarkdown, nuevo: EstiloMarkdown): EstiloMarkdown =
    if (actual == EstiloMarkdown.NINGUNO) nuevo else actual

/**
 * Un cierre sólo cuenta si está pegado por su izquierda: sin esto, "2 * 3 * 4" o una viñeta
 * suelta dentro del texto abren un énfasis que nunca se cierra bien.
 */
private fun buscarCierre(linea: String, desde: Int, marca: String): Int {
    var j = linea.indexOf(marca, desde)
    while (j >= 0) {
        if (j > desde && !linea[j - 1].isWhitespace()) return j
        j = linea.indexOf(marca, j + 1)
    }
    return -1
}
