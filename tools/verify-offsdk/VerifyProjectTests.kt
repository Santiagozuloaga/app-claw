package verify

import com.example.clawcode.JarvisVoiceUnitTest
import org.junit.runner.Description
import org.junit.runner.JUnitCore
import org.junit.runner.notification.Failure
import org.junit.runner.notification.RunListener

/**
 * Ejecuta de verdad las pruebas JUnit que trae el proyecto. No es un duplicado de ellas: el repo
 * no tiene `gradle/wrapper` ni SDK en esta máquina, así que `./gradlew test` es imposible y la
 * escalera de verificación se quedaba sin su tercer peldaño. JarvisVoiceUnitTest sólo depende de
 * org.junit.Assert y de corrutinas, así que entra en el arnés off-SDK tal cual está escrita.
 */

fun main() {
    println("=== PT: pruebas unitarias del propio proyecto ejecutadas con JUnitCore ===")

    val core = JUnitCore()
    val started = mutableListOf<String>()
    val failed = mutableListOf<String>()
    val skipped = mutableListOf<String>()
    var lastStarted: String? = null

    core.addListener(object : RunListener() {
        override fun testStarted(description: Description) {
            lastStarted = description.methodName ?: description.displayName
            started += lastStarted!!
        }

        override fun testFailure(failure: Failure) {
            val name = lastStarted ?: "test desconocido"
            val detalle = failure.message ?: failure.trace?.toString() ?: "sin detalle"
            failed += name
            println("  FAIL  $name -> ${detalle.take(240)}")
        }

        // Un assumeTrue() no es un fallo: es la prueba diciendo "este equipo no tiene el runtime".
        // Contarlo como FAIL castigaría a una máquina sin Ollama por algo que no depende del repo.
        override fun testAssumptionFailure(failure: Failure) {
            val name = lastStarted ?: "test desconocido"
            skipped += name
            println("  SKIP  $name -> ${failure.message?.take(180)}")
        }
    })

    val result = core.run(
        JarvisVoiceUnitTest::class.java,
        com.example.clawcode.ChatRealInferenceTest::class.java,
        com.example.clawcode.RealOllamaIntegrationTest::class.java
    )

    for (name in started) if (name !in failed && name !in skipped) println("  PASS  $name -> asserts del proyecto satisfechos")

    println()
    println("  tests ejecutados=${result.runCount}  fallos=${result.failureCount}  saltadas=${skipped.size}  tiempo=${result.runTime}ms")
    println()
    println(if (result.wasSuccessful()) "RESULTADO PRUEBAS-PROYECTO: 0 FAIL" else "RESULTADO PRUEBAS-PROYECTO: ${result.failureCount} FAIL")
}
