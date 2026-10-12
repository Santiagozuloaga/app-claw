package android.content

import android.app.ActivityManager
import android.os.BatteryManager

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 *
 * Sustituye android.content.Context y android.content.SharedPreferences. A diferencia de
 * un stub vacío, aquí SharedPreferences es FUNCIONAL (mapa en memoria compartido por
 * nombre de fichero), de modo que la persistencia se puede probar de verdad: lo que escribe
 * un ClawEngine lo lee otro ClawEngine construido después, igual que en un reinicio real.
 */
open class Context {

    open val applicationContext: Context get() = this

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = FakePrefsDisk.open(name)

    // Suficientes para SystemMonitor: el registro de batería devuelve el Intent pegajoso real
    // y getSystemService entrega el ActivityManager del sistema.
    open fun registerReceiver(receiver: Any?, filter: IntentFilter): Intent? = FakeSystemServices.batteryIntent
    open fun getSystemService(name: String): Any? = FakeSystemServices.activityManager

    // Suficiente para AppConnectorManager: esAppInstalada() sólo necesita que getPackageInfo lance
    // NameNotFoundException cuando el paquete no está, y startActivity() registra el Intent enviado.
    open val packageManager: android.content.pm.PackageManager get() = android.content.pm.FakePackages.manager
    open fun startActivity(intent: Intent) { FakeSystemServices.ultimIntent = intent }

    // Directorios reales de cara al código de la app: ModelStorage/ModelDownloadManager escriben
    // y leen ficheros de verdad, así que la descarga se puede verificar con bytes reales.
    val filesDir: java.io.File get() = FakeFileDirs.forContext(this)
    open fun getExternalFilesDir(@Suppress("UNUSED_PARAMETER") type: String?): java.io.File =
        FakeFileDirs.forContext(this)

    companion object {
        const val MODE_PRIVATE = 0
        const val ACTIVITY_SERVICE = "activity"
    }
}

/** Carpeta temporal por instancia de Context; sustituye a /data/data/... en el arnés. */
object FakeFileDirs {

    private val dirs = mutableMapOf<Int, java.io.File>()

    @Synchronized
    fun forContext(context: Context): java.io.File =
        dirs.getOrPut(System.identityHashCode(context)) {
            java.io.File(System.getProperty("java.io.tmpdir"), "claw-harness-${System.nanoTime()}").apply { mkdirs() }
        }

    @Synchronized
    fun clearAll() {
        dirs.values.forEach { it.deleteRecursively() }
        dirs.clear()
    }
}

/** android.content.Intent / IntentFilter mínimos; sólo llevan extras enteros. */
class Intent(private val extras: Map<String, Int> = emptyMap()) {

    constructor(action: String) : this(emptyMap()) {
        this.action = action
    }

    var action: String? = null
    var data: android.net.Uri? = null
    var packageName: String? = null
    var flags: Int = 0

    fun getIntExtra(name: String, defaultValue: Int): Int = extras[name] ?: defaultValue

    fun setPackage(nombre: String) { packageName = nombre }

    fun addFlags(f: Int) { flags = flags or f }

    companion object {
        const val ACTION_BATTERY_CHANGED = "android.intent.action.BATTERY_CHANGED"
        const val ACTION_VIEW = "android.intent.action.VIEW"
        const val FLAG_ACTIVITY_NEW_TASK = 0x10000000
    }
}

class IntentFilter(@Suppress("UNUSED_PARAMETER") action: String)

/** Puntos de inyección del arnés: cada test pone la batería y la RAM que quiere ver. */
object FakeSystemServices {

    var batteryIntent: Intent? = null
    var ultimIntent: Intent? = null
    val activityManager: ActivityManager = ActivityManager()

    fun setBattery(level: Int, scale: Int, temperatureTenths: Int) {
        batteryIntent = Intent(
            mapOf(
                BatteryManager.EXTRA_LEVEL to level,
                BatteryManager.EXTRA_SCALE to scale,
                BatteryManager.EXTRA_TEMPERATURE to temperatureTenths
            )
        )
    }

    fun setMemory(totalBytes: Long, availableBytes: Long) {
        activityManager.totalMemBytes = totalBytes
        activityManager.availMemBytes = availableBytes
    }

    fun reset() {
        batteryIntent = null
        activityManager.totalMemBytes = 0L
        activityManager.availMemBytes = 0L
    }
}

interface SharedPreferences {

    fun contains(key: String): Boolean

    fun getString(key: String, defValue: String?): String?

    fun getBoolean(key: String, defValue: Boolean): Boolean

    fun edit(): SharedPreferences.Editor

    interface Editor {
        fun putString(key: String, value: String?): SharedPreferences.Editor
        fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor
        fun apply()
    }
}

/** Emula el XML de SharedPreferences en disco: sobrevive a nuevas instancias de Context. */
object FakePrefsDisk {

    private val files = mutableMapOf<String, MutableMap<String, Any?>>()

    fun open(name: String): SharedPreferences =
        FakeSharedPreferences(files.getOrPut(name) { mutableMapOf() })

    fun snapshot(name: String): Map<String, Any?> = files[name].orEmpty().toMap()

    fun reset() = files.clear()
}

private class FakeSharedPreferences(
    private val backing: MutableMap<String, Any?>
) : SharedPreferences {

    override fun contains(key: String): Boolean = backing.containsKey(key)

    override fun getString(key: String, defValue: String?): String? =
        backing[key] as? String ?: defValue

    override fun getBoolean(key: String, defValue: Boolean): Boolean =
        backing[key] as? Boolean ?: defValue

    override fun edit(): SharedPreferences.Editor = FakeEditor(backing)

    private class FakeEditor(
        private val backing: MutableMap<String, Any?>
    ) : SharedPreferences.Editor {

        private val pending = mutableMapOf<String, Any?>()

        override fun putString(key: String, value: String?): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor =
            apply { pending[key] = value }

        override fun apply() {
            backing.putAll(pending)
        }
    }
}
