package android.content.pm

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * Suficiente para `AppConnectorManager.esAppInstalada()`: reproduce el contrato real de lanzar
 * NameNotFoundException cuando el paquete no existe, que es justo lo que la app usa para decidir si
 * una integración está presente. La lista de paquetes instalados es un punto de inyección del arnés.
 */
class PackageManager {

    var instalados: MutableSet<String> = mutableSetOf()

    fun getPackageInfo(packageName: String, flags: Int): Any {
        if (packageName !in instalados) throw NameNotFoundException(packageName)
        return Any()
    }
}

class NameNotFoundException(message: String) : Exception(message)

/** Paquetes del dispositivo para las pruebas: el arnés decide qué hay instalado. */
object FakePackages {
    val manager: PackageManager = PackageManager()
}
