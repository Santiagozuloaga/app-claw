package com.example.clawcode.engine.file

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.InputStream

class OcrProcessor(private val context: Context) {

    fun processImageUri(uri: Uri): String {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                extractTextFromBitmap(bitmap)
            } else {
                "[OCR Engine] No se pudo decodificar el mapa de bits de la imagen."
            }
        } catch (e: Exception) {
            "[OCR Engine Error] Fallo al procesar la imagen: ${e.localizedMessage}"
        }
    }

    private fun extractTextFromBitmap(bitmap: Bitmap): String {
        val width = bitmap.width
        val height = bitmap.height
        
        // Analyze image dimensions & contrast heuristics
        val aspect = width.toFloat() / height.toFloat()
        val totalPixels = width * height

        // Esto no es OCR: solo se midieron dimension y densidad. Antes cerraba con
        // "Texto capturado y digitalizado exitosamente", que es justo lo que no ocurrio.
        return "[SIN OCR]\n" +
                "- Dimensiones de la imagen: ${width}x${height} px\n" +
                "- Relación de aspecto: ${"%.2f".format(aspect)}\n" +
                "- Píxeles totales: ${totalPixels} px\n" +
                "- Texto reconocido: NINGUNO. Esta build no incluye motor OCR; solo se midió la imagen."
    }
}
