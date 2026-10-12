# Integración con el ecosistema de agentes — lo que Android puede exponer hoy

Sin inventar contratos. Esto describe las uniones **que ya existen en el código** y las decisiones que
necesitan confirmación antes de construir nada encima.

## Uniones reales (verificadas leyendo el repo)

| Superficie | Dónde | Qué expone hoy | Estado |
|---|---|---|---|
| Enviar un mensaje al motor | `ClawEngine.sendChatMessage(text, attachedFileName, attachedImageUri, mobileActionEngine)` | turno → `runLocalInference()` → `OllamaClient` (HTTP) → burbuja única en `chatMessages` | implementado y verificado por arnés |
| Estado de ejecución | `ClawEngine.chatAwaitingRuntime: StateFlow<String?>` y su espejo en `ClawViewModel` | `null` = reposo; texto = "Consultando '<modelo>' en <host>" | implementado; **en pantalla sin verificar** (tarea #15) |
| Resultados de acciones del SO | `MobileActionEngine.executeActionIntent()` → `Pair<Boolean, String>` | `false` + motivo cuando no hay Intent mapeado | implementado |
| Configuración del runtime | `OllamaConfigStore` (`PrefsOllamaConfigStore`) | host/modelo/webhook persistidos; `isConnected` **siempre** se recalcula, no se restaura | implementado y verificado |
| Historial del chat | `ChatHistoryStore` (`PrefsChatHistoryStore`, últimos 60 mensajes) | conversación sobrevive al reinicio del proceso | nuevo en esta rama, verificado por arnés |
| Registro de errores | `_consoleLogs` / `appendConsoleLog` con prefijos `[MODEL_RESPONSE_GENERATED]`, `[MODEL_INFERENCE_FALLBACK]`, `[INPUT_REJECTED]`, `[GROUNDING_UNAVAILABLE]`, `[HOST_AUTORESOLVED]`, `[TELEMETRY_POLL_FAILED]` | trazas legibles por un agente externo sin parsear UI | implementado |
| Eventos internos | `core/EventBus` (`AppEvent.UserMessageSent`, `ClawMessageGenerated`, `HardwareStatusUpdated`) | difusión interna en el proceso | **no** es un puente entre procesos |
| Tareas del agente | `ClawEngine.tasks: StateFlow<List<AgentTask>>` con `progress` y `logs` | avance real por turno | implementado |

## Lo que **no** existe y no hay que suponer

- Ningún servicio en segundo proceso, `AIDL`, `ContentProvider`, `foreground service` ni socket abierto
  con el puente de WhatsApp. `simulateWhatsAppIncoming()` es **demo local**: no sale tráfico del teléfono.
- Ninguna API de SAGE/AutoCLAW definida en este repo: no hay contratos que implementar, sólo esta tabla.
- Ninguna inferencia dentro del proceso (no hay `.so`, `.gguf`, `.tflite`, ni dependencias LiteRT/MediaPipe).

## Decisiones que requieren confirmación del dueño del proyecto

1. **Cómo quiere exponer Android las capacidades**: (a) intents/`deep links` con un esquema propio,
   (b) un `ContentProvider` de lectura para consola e historial, (c) un servidor local en el dispositivo,
   o (d) seguir sólo por UI. Cada opción tiene un coste de permisos y ciclo de vida distinta; no se elige desde aquí.
2. **Identidad y autenticación** si un agente externo invocará el cliente: hoy los tokens del repo son
   placeholders (`secret_tu_token_aqui`, `ghp_tu_token_aqui`, `TU_API_KEY`) y no hay almacenamiento seguro
   (ni `EncryptedSharedPreferences` ni Keystore).
3. **Ruta de inferencia final**: Ollama por HTTP (funcional hoy, requiere red local permitida) o inferencia
   en proceso (bloqueada por dependencia + modelo con licencia, ver `claw-ondevice-runtime-blocker` en la
   memoria del proyecto). Esta PR no decide: mantiene HTTP y lo declara.
4. **Límite real del historial** (60 mensajes) y qué se conserva al borrar la consola.
