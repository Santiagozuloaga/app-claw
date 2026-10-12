package android.app

/**
 * STUB SOLO PARA VERIFICACIÓN FUERA DE ANDROID. NO forma parte de la app.
 * En Android real Application extiende ContextWrapper -> Context; aquí basta con que sea un
 * Context para que ClawViewModel pueda pasárselo a PrefsOllamaConfigStore y a MobileActionEngine.
 */

import android.content.Context

open class Application : Context()

/** ActivityManager funcional lo bastante para que SystemMonitor se pueda medir de verdad. */
class ActivityManager {

    class MemoryInfo {
        var totalMem: Long = 0L
        var availMem: Long = 0L
    }

    var totalMemBytes: Long = 0L
    var availMemBytes: Long = 0L

    fun getMemoryInfo(out: MemoryInfo) {
        out.totalMem = totalMemBytes
        out.availMem = availMemBytes
    }
}
