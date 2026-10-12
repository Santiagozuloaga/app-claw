
## Añadido en la última ronda (mismo arnés, 18 verificaciones, 478 PASS / 0 FAIL)

- Rechazo de mensajes vacíos en `ClawEngine.sendChatMessage` (único punto común de chat, voz y Dashboard).
- `Mutex` para serializar turnos: dos envíos seguidos ya no entrelazan burbujas ni historial.
- Historial del chat persistido en SharedPreferences (`ChatHistoryStore`, últimos 60), cableado en `ClawViewModel`.
- Instalación de modelos: se comprueba SHA-256 cuando el manifiesto lo trae y se borra el archivo inválido.
- Formateo de hora del motor sin `SimpleDateFormat` compartido entre hilos.
- Documentada la escalera de verificación y las decisiones de integración pendientes:
  `docs/INVENTARIO_FASE0.md`, `docs/INTEGRACION_ECOSISTEMA.md`.

## Último empujón de la misma sesión (Fases 5, 6 y 7 de la descarga)

- `ModelDownloadManager`: la `HttpURLConnection` no se cerraba nunca (socket retenido hasta el colector al
  cancelar o fallar a mitad) -> `disconnect()` en `finally`.
- `OllamaConfig.useOnDeviceLiteRTFallback` arranca en `false`: con el `true` de fábrica la app rotulaba
  "SAFE MODE (Local)" / "LOCAL" sin tener ningún runtime en proceso. El interruptor de Ajustes sigue ahí.
- `VerifyDownload` DL-5 (3 asserts sobre la fuente real + el valor de fábrica). Total: **18 arneses,
  481 PASS / 0 FAIL**. Escalera de la Fase 8 detenida en el paso 3 (no hay Gradle/SDK para el 2 y 5).
