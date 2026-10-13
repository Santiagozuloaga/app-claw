package com.example.clawcode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.clawcode.MainActivity
import com.example.clawcode.R
import com.example.clawcode.core.storage.PrefsOllamaConfigStore

class ClawAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.claw_widget_layout)

            // Intent to open Claw Live Screen
            val liveIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "claw_live")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val livePendingIntent = PendingIntent.getActivity(
                context,
                101,
                liveIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_live, livePendingIntent)

            // Intent to open Jarvis Chat Screen
            val chatIntent = Intent(context, MainActivity::class.java).apply {
                putExtra("navigate_to", "jarvis_agent")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val chatPendingIntent = PendingIntent.getActivity(
                context,
                102,
                chatIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.btn_widget_chat, chatPendingIntent)

            // El widget vivia de dos literales del layout ("ONLINE" y "Qwen 3.5 GGUF Local") que
            // ningun codigo escribia. Ahora lee la configuracion persistida por la app: si no hay
            // runtime guardado, dice que no lo hay, en lugar de inventarse un modelo.
            val config = PrefsOllamaConfigStore(context).load()
            val host = config?.hostUrl?.trim().orEmpty()
            val modelo = config?.modelName?.trim().orEmpty()
            views.setTextViewText(
                R.id.widget_status,
                if (host.isEmpty()) "sin runtime guardado" else "runtime: $host"
            )
            views.setTextViewText(
                R.id.widget_model_info,
                if (modelo.isEmpty()) "modelo sin configurar"
                else "modelo: $modelo · se ejecuta fuera del teléfono"
            )

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
