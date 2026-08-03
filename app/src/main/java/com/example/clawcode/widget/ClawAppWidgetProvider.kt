package com.example.clawcode.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.example.clawcode.MainActivity
import com.example.clawcode.R

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

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
