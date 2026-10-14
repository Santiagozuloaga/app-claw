package com.example.clawcode.engine

/**
 * Puerto de cerebro.
 *
 * El propio proyecto lo pedía por escrito (línea 7215 del transcript de AI Studio): "Ninguna
 * decisión debe depender exclusivamente de Ollama", y más abajo "CLAW funciona con cualquier
 * modelo compatible". Hasta hoy eso era falso de manual: `ClawEngine` llamaba a `OllamaClient`
 * en siete puntos y no había por donde poner otro.
 *
 * Las firmas son las que ya tenía `OllamaClient`, a propósito, para que introducir un segundo
 * proveedor no exija reescribir el motor. Por eso los tipos siguen llamándose `Ollama*` pese a
 * ser neutros de formato: `OllamaChatRequest` describe un turno (sistema + historial + usuario +
 * parámetros de muestreo), no un payload de Ollama. Renombrarlos es una tarea aparte y cosmética;
 * lo que importa aquí es que ningún proveedor pueda afirmar una capacidad que no tiene.
 */
interface ProveedorInferencia {

    /** Nombre del backend que responde, para mostrarlo en consola y en la UI. */
    val nombre: String

    /**
     * Emite cada token por [onToken] mientras llega, y devuelve la respuesta completa.
     * `Result.failure` con causa legible cuando el servidor no responde o devuelve error:
     * nunca un texto de relleno.
     */
    suspend fun chat(
        request: OllamaChatRequest,
        onToken: ((String) -> Unit)? = null
    ): Result<OllamaChatReply>

    /**
     * Modelos que este servidor sirve de verdad. Un proveedor sin endpoint de catálogo debe
     * devolver failure, no una lista vacía: una lista vacía se lee como "no tienes modelos" y
     * un catálogo inventado se lee como un modelo que no existe.
     */
    suspend fun listCompletionModels(hostUrl: String): Result<List<OllamaModelInfo>>

    /**
     * RAM/VRAM/contexto del modelo ya cargado. Sólo Ollama expone ese dato (/api/ps); los demás
     * devuelven failure para que la tarjeta "Tokens" siga diciendo "sin medir".
     */
    suspend fun loadedModelFootprint(hostUrl: String, model: String): Result<OllamaModelFootprint>
}
