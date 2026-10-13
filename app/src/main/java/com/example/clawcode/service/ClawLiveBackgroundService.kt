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

class ClawLiveBackgroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_LIVE) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("navigate_to", "claw_live")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Este servicio no abre el microfono: no hay AudioRecord, ni SpeechRecognizer, ni TTS
        // en esta clase. Antes se anunciaba como "Live Voice Activo" y el manifest pedía tipo
        // "microphone", con lo que el sistema mostraba un indicador de escucha inexistente.
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CLAW en segundo plano")
            .setContentText("Solo mantiene abierta la app: no está escuchando ni grabando audio.")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Claw Live Voice Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Mantiene activo el motor de voz de JARVIS fuera de la aplicación"
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

        fun startService(context: Context) {
            val intent = Intent(context, ClawLiveBackgroundService::class.java).apply {
                action = ACTION_START_LIVE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, ClawLiveBackgroundService::class.java).apply {
                action = ACTION_STOP_LIVE
            }
            context.startService(intent)
        }
    }
}
