# MAPA — CLAW Android (`app-claw-main`)

Alcance de este índice: **núcleo del motor** (engine + core + viewmodel). No cubre `ui/` (Compose) ni `service/`, `widget/`.
Paquete raíz: `app/src/main/java/com/example/clawcode/`. Ficheros `.kt` totales del repo: 71.
Símbolos verificados con `grep` el 2026-10-11 contra el árbol actual. Ruta y símbolo verificados por separado.

## Capas y dependencias

`viewmodel/ClawViewModel.kt` → `engine/ClawEngine.kt` → `engine/OllamaClient.kt`
`ui/screens/ModelScreen.kt` → `core/model/QwenModelBridgeService.kt` → `engine/OllamaClient.kt`
`engine/ClawEngine.kt` → `engine/MobileActionEngine.kt` (instanciado en el ViewModel, no en el engine)
Toda la capa `core/` → `data/model/Models.kt` (tipos de datos compartidos)

Evidencia: `ClawEngine` solo aparece referenciado desde `ClawViewModel.kt`; `QwenModelBridgeService` solo desde su propio fichero y `ui/screens/ModelScreen*.kt`.

## Ficheros clave

- `engine/ClawEngine.kt` (1393 líneas) — estado global de la app y orquestación. Expone StateFlows:
  `tasks`, `memories`, `tools`, `stats`, `ollamaConfig`, `chatAwaitingRuntime`, `consoleLogs`, `isCompacting`, `whatsAppMessages`, `jarvisModfile`, `fileAttachments`, `chatMessages`, `hardwareTelemetry` (verificado, 2026-10-11)
  - `class ClawEngine(...)` en línea 12, con `private val ollamaClient: OllamaClient = OllamaClient()` (línea 13) y un segundo cliente de sonda, `private val hostProbeClient = OllamaClient(connectTimeoutSeconds = 2, readTimeoutSeconds = 8)` (línea 1226).
- `engine/OllamaClient.kt` (321 líneas) — único camino real de inferencia: HTTP contra Ollama.
  `data class OllamaChatMessage(role, content)`, `data class OllamaChatReply` (text, model, thinking, promptTokens, completionTokens, tokensPerSecond, latencyMs, stopReason), `data class OllamaModelInfo` (name, sizeBytes, isCloudStub), `class OllamaClient` (línea 74). Verificado 2026-10-11.
- `engine/MobileActionEngine.kt` (705 líneas) — acciones de dispositivo y TTS.
  `class MobileActionEngine(context) : TextToSpeech.OnInitListener`, `speak(...)`, `getInstalledApps()`, `launchAppByName(appNameQuery)`, `getActionCatalog()`, `executeActionIntent(...)`, `parseNaturalLanguageToFunctionCall(prompt)`. Verificado 2026-10-11.
- `core/model/QwenModelBridgeService.kt` (198 líneas) — puente de modelo local; **no** ejecuta inferencia propia, delega en `OllamaClient`.
  `sealed class QwenModelBridgeState`, `data class QwenTestInferenceResult`, `class QwenModelBridgeService`, `bridgeState`, `lastTestResult`, `activeModelName` (valor inicial `"sin verificar"`), `suspend loadQwenModelAsync(...)`, `suspend runTestInference(...)`, `suspend unloadModelAsync()`. Verificado 2026-10-11.
- `core/storage/OllamaConfigStore.kt` (65 líneas) — persistencia de la configuración de Ollama.
  `interface OllamaConfigStore` (`load()`, `save(config)`), implementaciones `InMemoryOllamaConfigStore` y `PrefsOllamaConfigStore(context)`. Verificado 2026-10-11.
- `core/EventBus.kt` (23 líneas) — `object EventBus` con `events: SharedFlow<AppEvent>`, `suspend publish(event)`, `tryPublish(event)`. Verificado 2026-10-11.
- `viewmodel/ClawViewModel.kt` (331 líneas) — `class ClawViewModel(application) : AndroidViewModel`, construye `engine` y `mobileActionEngine`, y **espeja** cada StateFlow del engine con `stateIn(...)` (líneas 22-82+). Verificado 2026-10-11.
- `data/model/Models.kt` — vocabulario de datos: `TaskStatus`, `Priority`, `AgentTask`, `MemoryItem`, `McpTool`, `AgentStats`, `ConsoleLog`, `MobileActionCategory`, `InstalledApp`, `ModelTaskMode`, `AgentPersonality`, `ThermalState`, `JarvisModfile`, `ChatMessage`, `HardwareTelemetry`, `OllamaConfig`. Verificado 2026-10-11.

## Puntos de entrada

- `ClawApplication.kt` — `class ClawApplication : Application()`
- `MainActivity.kt` — `class MainActivity : ComponentActivity()`, `enum class NavigationTab(route, title, icon)`
- `service/ClawLiveBackgroundService.kt` — servicio en segundo plano
- Arnés de verificación sin SDK: `../../_verify/compile.sh` y `../../_verify/run.sh` (fuera del repo)

## Cómo validar este mapa

```bash
cd app/src/main/java/com/example/clawcode
grep -c "chatMessages" engine/ClawEngine.kt viewmodel/ClawViewModel.kt
grep -n "class OllamaClient" engine/OllamaClient.kt
```
Si un grep de símbolo citado falla, la sección está obsoleta: regenerarla antes de volver a usarla.

## Límites de este índice

Índice estático producido por búsqueda, no un grafo de llamadas: no sabe quién llama a qué dentro de un fichero, ni tipos resueltos, ni reflexividad. Los ficheros de `ui/` solo aparecen donde un grep los nombró explícitamente.
