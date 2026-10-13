package verify

import com.example.clawcode.core.integrity.ResultadoIntegridad
import com.example.clawcode.core.integrity.Sha256Verifier
import java.io.File
import java.security.MessageDigest

/**
 * `Sha256Verifier` era un verificador que aprobaba sin mirar: si el hash esperado venía vacío,
 * como `dummy_sha256` o con menos de 10 caracteres — que es justo como vienen los tres hashes del
 * catálogo de modelos — devolvía `true` con sólo que el archivo no estuviera vacío. Sobre eso se
 * apoya el "Verificando Hash SHA256…" que la app enseña al usuario. Aquí se le pone delante
 * contenido real con hash real, y contenido que no coincide.
 */

private var failures = 0

private fun check(nombre: String, condicion: Boolean, detalle: String) {
    if (condicion) println("  PASS  $nombre -> $detalle")
    else { failures++; println("  FAIL  $nombre -> $detalle") }
}

private fun sha256Independiente(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

private fun archivo(temporal: File, nombre: String, bytes: ByteArray): File =
    File(temporal, nombre).apply { writeBytes(bytes) }

fun main() {
    println("=== VerifySha256: el umbral de integridad de los modelos ===")
    val temporal = File(System.getProperty("java.io.tmpdir"), "claw-sha-${System.nanoTime()}")
    temporal.mkdirs()

    try {
        val contenido = "peso de modelo falso para pruebas de integridad".toByteArray()
        val hashBueno = sha256Independiente(contenido)
        val bueno = archivo(temporal, "bueno.bin", contenido)
        val corrupto = archivo(temporal, "corrupto.bin", "otro contenido distinto".toByteArray())
        val vacio = archivo(temporal, "vacio.bin", ByteArray(0))
        val ausente = File(temporal, "no-existe.bin")

        check("SV-1 fixture sano: el hash esperado es el que da",
            hashBueno == Sha256Verifier.sha256De(bueno), "SHA-256 calculado por dos vías")

        check("SV-2 coincidencia real -> VERIFICADO",
            Sha256Verifier.verifyFileHash(bueno, hashBueno) == ResultadoIntegridad.VERIFICADO,
            "único caso en que se afirma integridad")

        check("SV-3 archivo alterado -> NO_COINCIDE",
            Sha256Verifier.verifyFileHash(corrupto, hashBueno) == ResultadoIntegridad.NO_COINCIDE,
            "antes esto dependía sólo del tamaño")

        // Estos tres son la regresión concreta: el código viejo devolvía true para todos.
        check("SV-4 hash vacío ya no aprueba",
            Sha256Verifier.verifyFileHash(bueno, "") == ResultadoIntegridad.SIN_REFERENCIA,
            "SIN_REFERENCIA, no VERIFICADO")
        check("SV-5 'dummy_sha256' ya no aprueba",
            Sha256Verifier.verifyFileHash(bueno, "dummy_sha256") == ResultadoIntegridad.SIN_REFERENCIA,
            "el literal del catálogo de desarrollo")
        val hashCortoDelCatalogo = "3a8d90f23b118b62817812bc85ef109"
        check("SV-6 el hash de 31 caracteres del catálogo no es un SHA-256 y no aprueba",
            hashCortoDelCatalogo.length == 31 &&
                Sha256Verifier.verifyFileHash(bueno, hashCortoDelCatalogo) ==
                ResultadoIntegridad.SIN_REFERENCIA,
            "longitud ${hashCortoDelCatalogo.length}: no tiene forma de SHA-256")

        check("SV-7 sesenta y cuatro caracteres pero no hexadecimales -> SIN_REFERENCIA",
            Sha256Verifier.verifyFileHash(bueno, "z".repeat(64)) == ResultadoIntegridad.SIN_REFERENCIA,
            "se comprueba la forma, no sólo la longitud")

        check("SV-8 archivo inexistente con hash válido -> NO_COINCIDE",
            Sha256Verifier.verifyFileHash(ausente, hashBueno) == ResultadoIntegridad.NO_COINCIDE,
            "no se puede verificar lo que no está")
        check("SV-9 archivo vacío -> NO_COINCIDE",
            Sha256Verifier.verifyFileHash(vacio, hashBueno) == ResultadoIntegridad.NO_COINCIDE,
            "sha256De devuelve null")

        check("SV-10 verifySize sin talla de referencia ya no aprueba",
            Sha256Verifier.verifySize(bueno, 0L) == ResultadoIntegridad.SIN_REFERENCIA,
            "antes devolvía true si el archivo pesaba algo")
        check("SV-11 verifySize con talla que no coincide -> NO_COINCIDE",
            Sha256Verifier.verifySize(bueno, contenido.size + 7L) == ResultadoIntegridad.NO_COINCIDE,
            "comparación exacta")
        check("SV-12 verifySize correcto -> VERIFICADO",
            Sha256Verifier.verifySize(bueno, contenido.size.toLong()) == ResultadoIntegridad.VERIFICADO,
            "mismo umbral que usaba la descarga")

        // El hash de la cadena vacía es la constante que la pantalla de diseño mostraba como
        // "MATCH OK" de un modelo de 1.95 GB. Debe seguir siendo ésa, y no puede coincidir con
        // un archivo que sí tiene contenido.
        val hashVacio = sha256Independiente(ByteArray(0))
        check("SV-13 la constante que la app mostraba como hash verificado es la de cadena vacía",
            hashVacio == "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            "SHA-256 de 0 bytes")
        check("SV-14 ese hash no valida un archivo con contenido",
            Sha256Verifier.verifyFileHash(bueno, hashVacio) == ResultadoIntegridad.NO_COINCIDE,
            "el 'MATCH OK' ya no pasaría")

        val grande = ByteArray(200_000) { (it % 251).toByte() }
        val archivoGrande = archivo(temporal, "grande.bin", grande)
        check("SV-15 archivos mayores que el búfer de 8 KiB se hashean completos",
            Sha256Verifier.sha256De(archivoGrande) == sha256Independiente(grande) &&
                Sha256Verifier.verifyFileHash(archivoGrande, sha256Independiente(grande)) ==
                ResultadoIntegridad.VERIFICADO,
            "${grande.size} bytes en 25 volcados")

        check("SV-16 mayúsculas/minúsculas en el hash esperado no cambian el resultado",
            Sha256Verifier.verifyFileHash(bueno, hashBueno.uppercase()) == ResultadoIntegridad.VERIFICADO,
            "comparación insensible a mayúsculas")
    } finally {
        temporal.deleteRecursively()
    }

    println(if (failures == 0) "RESULTADO SHA256: 0 FAIL" else "RESULTADO SHA256: $failures FAIL")
    if (failures != 0) kotlin.system.exitProcess(1)
}
