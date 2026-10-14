package com.example.clawcode.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.clawcode.MainActivity
import com.example.clawcode.engine.voice.EscuchaContinuaVoz

/**
 * Entrega los comandos dictados al resto de la app. El servicio no puede alcanzar el ViewModel,
 * así que MainActivity registra el callback al arrancar y lo limpia al destruirse.
 */
object BusVoz {
    @Volatile
    var onComando: ((String) -> Unit)? = null

    @Volatile
    var onEstado: ((String) -> Unit)? = null
}

/**
 * Escucha continua fuera de la app.
 *
 * El transcript de AI Studio prometió esto seis veces y nunca se implementó: la clase sólo publicaba
 * una notificación, sin tocar el micrófono. Ahora [EscuchaContinuaVoz] abre de verdad el motor de
 * dictado del sistema, y el texto que pasa el filtro de palabra clave sale por [BusVoz].
 *
 * Lleva `foregroundServiceType="microphone"` porque efectivamente usa el micro: el indicador del
 * sistema es cierto ahora. Si el motor de dictado no está disponible, el servicio se para y lo dice
 * en la propia notificación en lugar de quedarse anunciado una escucha fantasma.
 */
class ClawLiveBackgroundService : Service() {

    private var escucha: EscuchaContinuaVoz? = null
    private var estadoActual = "iniciando"

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_LIVE) {
            detenerYSalir()
            return START_NOT_STICKY
        }

        val palabraClave = intent?.getStringExtra(EXTRA_WAKE_WORD)?.takeIf { it.isNotBlank() } ?: "JARVIS"

        notificar()

        if (escucha == null) {
            escucha = EscuchaContinuaVoz(
                context = this,
                palabraClave = palabraClave,
                onEstado = { nuevo ->
                    estadoActual = nuevo
                    BusVoz.onEstado?.invoke(nuevo)
                    notificar()
                },
                onComando = { comando -> BusVoz.onComando?.invoke(comando) }
            )
            val arranco = escucha?.iniciar() ?: false
            if (!arranco) {
                estadoActual = "no escucha: sin motor de dictado en el dispositivo"
                notificar()
                detenerYSalir()
                return START_NOT_STICKY
            }
        } else {
            escucha?.setPalabraClave(palabraClave)
        }

        return START_STICKY
    }

    private fun detenerYSalir() {
        escucha?.detener("detenida por el usuario")
        escucha = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        escucha?.detener("servicio destruido")
        escucha = null
        super.onDestroy()
    }

    private fun notificar() {
        val escuchando = estadoActual == "escuchando"
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                putExtra("navigate_to", "claw_live")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(if (escuchando) "CLAW escuchando (micrófono abierto)" else "CLAW en segundo plano")
            .setContentText(estadoActual)
            .setSmallIcon(
                if (escuchando) android.R.drawable.presence_audio_online
                else android.R.drawable.stat_notify_sync
            )
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        runCatching { startForeground(NOTIFICATION_ID, notification) }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Escucha continua de CLAW",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Muestra el estado real del micrófono mientras la app escucha fuera de primer plano"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_ID = "claw_live_channel"
        const val NOTIFICATION_ID = 9001
        const val ACTION_START_LIVE = "com.example.clawcode.action.START_LIVE"
        const val ACTION_STOP_LIVE = "com.example.clawcode.action.STOP_LIVE"
        const val EXTRA_WAKE_WORD = "com.example.clawcode.extra.WAKE_WORD"

        fun startService(context: Context, wakeWord: String) {
            val intent = Intent(context, ClawLiveBackgroundService::class.java).apply {
                action = ACTION_START_LIVE
                putExtra(EXTRA_WAKE_WORD, wakeWord)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClawLiveBackgroundService::class.java).apply {
                action = ACTION_STOP_LIVE
            }
            runCatching { context.startService(intent) }
        }
    }
}
