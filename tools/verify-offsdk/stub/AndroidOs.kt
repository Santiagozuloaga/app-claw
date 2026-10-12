package android.os

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 * En Android estos extras son constantes estáticas de android.os.BatteryManager.
 */
object BatteryManager {
    const val EXTRA_LEVEL = "level"
    const val EXTRA_SCALE = "scale"
    const val EXTRA_TEMPERATURE = "temperature"
}

/** StatFs medido sobre el sistema de ficheros real del arnés (no un valor fijo). */
class StatFs(private val path: String) {
    val blockSizeLong: Long get() = 4096L
    val availableBlocksLong: Long get() = java.io.File(path).usableSpace / blockSizeLong
}
