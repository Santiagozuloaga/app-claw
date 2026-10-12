package android.net

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * `Uri.parse` basta con conservar el string: lo que se comprueba en la app es la URL que termina en
 * el Intent de WhatsApp (número limpio y texto escapado), no el análisis de un Uri real.
 */
class Uri private constructor(val valor: String) {

    override fun toString(): String = valor

    companion object {
        fun parse(valor: String): Uri = Uri(valor)
    }
}
