package com.example.clawcode.core.integrity

import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Un verificador que devuelve `true` cuando no puede verificar no es un verificador: es un botón
 * de aceptar. Este objeto antes aprobaba cualquier archivo no vacío si el manifiesto traía el
 * hash vacío, `dummy_sha256` o truncado, y el catálogo de modelos trae justamente hashes de 19,
 * 21 y 31 caracteres que no son SHA-256. Ahora distingue "comprobado y coincide" de "no se puede
 * comprobar", que son cosas distintas y se muestran distinto.
 */
enum class ResultadoIntegridad {
    /** Se calculó el SHA-256 real y coincide byte a byte. */
    VERIFICADO,

    /** Se calculó y no coincide, o el archivo no existe/está vacío. */
    NO_COINCIDE,

    /** Falta el dato de referencia: no se puede afirmar nada sobre la integridad. */
    SIN_REFERENCIA
}

object Sha256Verifier {

    private val SHA256_HEX = Regex("^[0-9a-fA-F]{64}$")

    /** SHA-256 en hexadecimal minúscula, o null si el archivo no se puede leer. */
    fun sha256De(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { entrada ->
                val buffer = ByteArray(8192)
                var leido = entrada.read(buffer)
                while (leido != -1) {
                    digest.update(buffer, 0, leido)
                    leido = entrada.read(buffer)
                }
            }
            digest.digest().joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            null
        }
    }

    fun verifyFileHash(file: File, expectedSha256: String): ResultadoIntegridad {
        if (!expectedSha256.matches(SHA256_HEX)) {
            // Sin una referencia con la forma de un SHA-256 no hay nada que comparar, por mucho
            // que el archivo pese algo.
            return ResultadoIntegridad.SIN_REFERENCIA
        }
        val calculado = sha256De(file) ?: return ResultadoIntegridad.NO_COINCIDE
        return if (calculado.equals(expectedSha256, ignoreCase = true)) {
            ResultadoIntegridad.VERIFICADO
        } else {
            ResultadoIntegridad.NO_COINCIDE
        }
    }

    fun verifySize(file: File, expectedSizeBytes: Long): ResultadoIntegridad {
        if (expectedSizeBytes <= 0L) return ResultadoIntegridad.SIN_REFERENCIA
        if (!file.exists()) return ResultadoIntegridad.NO_COINCIDE
        return if (file.length() == expectedSizeBytes) {
            ResultadoIntegridad.VERIFICADO
        } else {
            ResultadoIntegridad.NO_COINCIDE
        }
    }
}
