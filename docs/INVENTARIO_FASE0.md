# Inventario Fase 0 — estado real comprobado (2026-10-11)

Todo lo de abajo se leyó del repositorio o se ejecutó en esta máquina. Donde no se pudo comprobar, se dice.

## Sistema de compilación

- Módulo único `app`, `applicationId = com.example.clawcode`, `compileSdk 35`, `minSdk 24`, `targetSdk 35`, `versionName 1.0` (`app/build.gradle.kts`).
- AGP **8.7.3**, Kotlin **2.0.21**, Compose BOM **2024.11.00**, kotlinx-serialization 1.7.3 (`gradle/libs.versions.toml`).
- **No hay `gradle/wrapper` ni `gradlew`**: la versión de Gradle queda en manos del binario del entorno, y sin ese wrapper `assembleDebug` no es reproducible.
- Sin Android SDK, `adb` ni JDK 17 en esta máquina (sólo JRE 1.8 y el JBR 25 de IntelliJ). **Estado de compilación: NO VERIFICADO** — el proyecto no se ha compilado aquí nunca; lo verificado abajo usa kotlinc del IDE sin dependencias Android (ver `tools/verify-offsdk` en el repo hermano / esta rama).
- `app/build.gradle.kts` **no** declara LiteRT, MediaPipe `tasks-genai`, llama.cpp ni ONNX. La única dependencia ML presente en el repo es `com.example.clawcode.core.model.QwenModelBridgeService` hablando por HTTP.

## Estructura y flujos

- Entrada: `MainActivity` (Compose, `setContent`), navegación por estados hacia `ui/screens/*`.
- Pantallas: `JarvisChatScreen`, `DashboardScreen`, `UnifiedConsoleScreen`, `DiagnosticConsole`, `OpenClawOllamaScreen`, `ModelManagerScreen`, `ConfigModfileScreen`, `JarvisAgentDesignerScreen`, `ClawLiveScreen`, `SystemPerformanceReportScreen`.
- Motor: `engine/ClawEngine.kt` (chat, consola, tareas, memoria, telemetría). Punto único del chat: `sendChatMessage()` → `runLocalInference()` → `engine/OllamaClient.kt` (`POST /api/chat`, `stream`, `think:false`, `/api/tags`, `/api/ps`).
- Presentación: `viewmodel/ClawViewModel.kt` con espejos `stateIn(WhileSubscribed)` — **trampa conocida**: un espejo sin suscriptor se queda en su valor inicial.
- Configuración y persistencia: `core/storage/OllamaConfigStore.kt` (SharedPreferences) y `core/storage/ModelStorage.kt` (ficheros). Sin base de datos.
- Permisos declarados: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, CHANGE_WIFI_STATE, RECORD_AUDIO, CAMERA, VIBRATE, ACCESS_FINE/COARSE_LOCATION, READ/WRITE_EXTERNAL_STORAGE (con `maxSdkVersion`) y **MANAGE_EXTERNAL_STORAGE**. No se ha probado en dispositivo cuáles se piden y cuáles usa realmente el código → **sin auditar, no afirmar que son excesivos**.
- Tráfico claro permitido sólo a hosts locales concretos vía `res/xml/network_security_config.xml` (no `usesCleartextTraffic="true"` global).

## Pruebas

- Proyecto: **un** fichero, `app/src/test/java/com/example/clawcode/JarvisVoiceUnitTest.kt` (JUnit4 + `runBlocking`). **No se ha ejecutado nunca aquí** (no hay Gradle). Su aserción de telemetría era incompatible con los valores "sin medir" actuales y se corrigió en esta rama.
- Arnés real de esta rama: `tools/verify-offsdk/` — 17 verificaciones, **451+ asserts**, compilan y ejecutan el código Kotlin real del módulo con stubs funcionales de Android y Ollama falso/real (0.34.4). Cada hito lleva prueba de no-vacuidad (sabotaje → FAIL → restauración idéntica → PASS).

## Recuperación del trabajo anterior (Fase 1)

- El trabajo previo está versionado en `main` (baseline exacto del zip) y `feature/chat-inferencia-local` (PR #1): 45 ficheros, sin eliminaciones.
- Sin métodos huérfanos entre los tocados: `QwenModelBridgeService`, `simulateWhatsAppIncoming`, `runAsyncAgentTaskLoop`, `setSimulatedTemperature`, `injectSimulatedLog` y `runTestInference` siguen llamados desde pantallas o el ViewModel.
- Compatibilidad revisada: `taskPerformanceMetrics` y `groundingHistory` ya no tienen semilla inventada y **ninguna pantalla las consume** (sólo el espejo del ViewModel), así que vaciarlas no rompe la UI. `hardwareTelemetry` sí se consume en 8 pantallas: ahí los centinelas `-1`/`0` significan "sin medir" y el sondeo los rellena en ~5 s; eso queda **pendiente de ver en pantalla**.

## Avance posterior dentro de la misma sesión (Fases 2, 4, 6 y 7)

Verificado con ejecución en `tools/verify-offsdk` (**18 arneses, 478 PASS / 0 FAIL**, cada corrección con prueba de dientes):

- **Fase 2 — validación de entrada:** `ClawEngine.sendChatMessage` no filtraba mensajes vacíos; un dictado en blanco publicaba un turno y lo enviaba al runtime. Ahora se rechaza con `[INPUT_REJECTED]` y sigue pasando un envío con adjunto y texto vacío.
- **Fase 4 — turnos concurrentes:** no había serialización; dos envíos casi simultáneos se entrelazaban (el chat quedaba `USER,USER,JARVIS,JARVIS` y el segundo turno no veía la respuesta del primero). Protegido con `Mutex`; probado contando peticiones simultáneas en el servidor falso (`maxEnVuelo == 1`).
- **Fase 4 — historial y persistencia:** el chat no se guardaba nunca. Añadido `ChatHistoryStore` (`PrefsChatHistoryStore`, últimos 60, codificación con `URLEncoder`) cableado en `ClawViewModel`; verificado a través del sistema de preferencias simulado, con textos que contienen el separador interno, `|`, saltos de línea, eñes y emojis.
- **Fase 6 — integridad:** la instalación de modelos sólo comparaba el tamaño (`verifyFileHash` no se llamaba) y el mensaje "Archivo descartado" era mentira: el `.gguf` inválido se quedaba en la carpeta. Ahora se exige el SHA-256 declarado y se borra el archivo.
- **Fase 7 — estado compartido:** el motor compartía un `SimpleDateFormat` entre la corutina del chat y los callbacks del stream (`Dispatchers.IO`); sustituido por un `ThreadLocal` (minSdk 24 no tiene `java.time`).

## Escalera de verificación (Fase 8) — dónde se paró

1. Inspección estática: hecha. 2. Compilación: **parcial** — kotlinc del IDE sobre las fuentes reales del módulo `app` (sin Compose/Android), no `assembleDebug`. 3. Pruebas: 18 arneses + la única JUnit del proyecto (ésta **sin ejecutar**: no hay Gradle). 4. Integración: el arnés levanta servidores HTTP falsos y reales. 5. **Emulador/dispositivo: no hecho** (sin SDK, adb, JDK 17 ni `gradle/wrapper`). 6. Inferencia real: **hecha contra Ollama 0.34.4 del host**, no dentro del proceso de la app.
- **Fase 5/7 (config y recursos):** el único punto donde quedaba una contradicción de configuración era
  `useOnDeviceLiteRTFallback = true` de fábrica frente a una app sin runtime en proceso; puesto a `false`
  (el ajuste manual sigue existiendo). Y la descarga de modelos no liberaba su `HttpURLConnection`.
