package com.example.clawcode

import android.app.Application
import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClawApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        setupGlobalExceptionHandler()
    }

    private fun setupGlobalExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val timestamp = dateFormat.format(Date())
                val stackTrace = throwable.stackTraceToString()

                val crashReport = """
                    ========== CLAW SYSTEM UNCAUGHT EXCEPTION ==========
                    Timestamp: $timestamp
                    Thread: ${thread.name} (ID: ${thread.id})
                    Exception: ${throwable.javaClass.name}: ${throwable.message}
                    
                    Stack Trace:
                    $stackTrace
                    ====================================================
                    
                """.trimIndent()

                Log.e("ClawApplication", "Uncaught Exception Intercepted: ${throwable.message}", throwable)

                // Save to local file
                val crashFile = File(filesDir, CRASH_LOG_FILENAME)
                crashFile.appendText(crashReport)
            } catch (e: Exception) {
                Log.e("ClawApplication", "Failed to write crash log to file", e)
            } finally {
                defaultHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    companion object {
        const val CRASH_LOG_FILENAME = "claw_crash_log.txt"

        fun getCrashLogs(context: Context): String {
            return try {
                val file = File(context.filesDir, CRASH_LOG_FILENAME)
                if (file.exists()) {
                    val content = file.readText()
                    if (content.isNotBlank()) content else "No hay cierres inesperados ni excepciones registradas."
                } else {
                    "No hay cierres inesperados ni excepciones registradas."
                }
            } catch (e: Exception) {
                "Error al leer el registro de fallos: ${e.localizedMessage}"
            }
        }

        fun clearCrashLogs(context: Context): Boolean {
            return try {
                val file = File(context.filesDir, CRASH_LOG_FILENAME)
                if (file.exists()) {
                    file.delete()
                } else true
            } catch (e: Exception) {
                false
            }
        }
    }
}
