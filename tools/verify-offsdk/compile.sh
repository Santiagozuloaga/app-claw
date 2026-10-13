#!/usr/bin/env bash
# Compila las fuentes REALES de la app (sin Android SDK) contra stubs mínimos de Android
# y los arneses de verificación. Kotlinc se invoca directamente sobre la JVM porque
# kotlinc.bat destroza el classpath separado por ';' bajo Git Bash.
# Todas las rutas se convierten a formato Windows: java.exe no entiende /c/...
set -euo pipefail

V="$(cd "$(dirname "$0")" && pwd)"
# El arnés vive a la vez en el árbol de trabajo (../app-claw-main) y versionado dentro del repo
# (tools/verify-offsdk). Se resuelve por tanteo para que el mismo fichero funcione en los dos sitios.
APP=""
for c in "$V/../../app/src/main/java/com/example/clawcode" "$V/../app-claw-main/app/src/main/java/com/example/clawcode"; do
  if [ -d "$c" ]; then APP="$c"; break; fi
done
[ -n "$APP" ] || { echo "no se localiza el árbol de fuentes del módulo app"; exit 1; }
JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/jbr"
KOTLINC_JAR="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/Kotlin/kotlinc/lib/kotlin-compiler.jar"

CP=""
for j in "$V"/libs/*.jar; do
  CP="$CP$(cygpath -w "$j");"
done
CP="${CP%;}"

OUT="$V/out5"
rm -rf "$OUT"

SRC=(
  "$APP/data/model/Models.kt"
  "$APP/core/AppEvent.kt"
  "$APP/core/EventBus.kt"
  "$APP/core/model/ModelDescriptor.kt"
  "$APP/core/model/QwenModelBridgeService.kt"
  "$APP/core/integrity/Sha256Verifier.kt"
  "$APP/core/download/ModelDownloadManager.kt"
  "$APP/core/storage/ModelStorage.kt"
  "$APP/core/storage/OllamaConfigStore.kt"
  "$APP/core/storage/ChatHistoryStore.kt"
  "$APP/core/telemetry/SystemMonitor.kt"
  "$APP/engine/ClawEngine.kt"
  "$APP/engine/OllamaClient.kt"
  "$APP/ui/util/MarkdownInline.kt"
  "$APP/viewmodel/ClawViewModel.kt"
  "$V/stub/AndroidApp.kt"
  "$V/stub/AndroidContext.kt"
  "$V/stub/AndroidOs.kt"
  "$V/stub/AndroidxLifecycle.kt"
  "$V/stub/HardwareTelemetryUtils.kt"

  "$V/stub/MobileActionEngine.kt"
  "$V/VerifyMain.kt"
  "$V/VerifyE2E.kt"
  "$V/VerifyQwenBridge.kt"
  "$V/VerifyPersistence.kt"
  "$V/VerifyPayload.kt"
  "$V/VerifyQuickActions.kt"
  "$V/VerifyViewModel.kt"
  "$V/VerifyNetSec.kt"
  "$V/VerifyTelemetryLog.kt"
  "$V/VerifyHostHints.kt"
  "$V/VerifyDiagCommands.kt"
  "$V/VerifyChatLoading.kt"
  "$V/VerifyAsyncLoop.kt"
  "$V/VerifyActionResult.kt"
  "$V/VerifyStreaming.kt"
  "$V/VerifySystemMonitor.kt"
  "$V/VerifyDownload.kt"
  "$V/VerifyChatHistory.kt"
  "$V/VerifyGrounding.kt"
  "$V/VerifyUiClaims.kt"
  "$V/VerifyMarkdown.kt"
  "$V/VerifySha256.kt"
  "$V/VerifyConnectors.kt"
  # AppConnectorManager real, con stubs de PackageManager/Uri/cliente Gemini para poder ejecutarlo.
  "$APP/AppConnectorManager.kt"
  "$V/stub/AndroidPackageManager.kt"
  "$V/stub/AndroidUri.kt"
  "$V/stub/GenerativeAi.kt"
  "$V/VerifyConnectorBehavior.kt"
  # Prueba JUnit del propio proyecto: se ejecuta aquí porque no hay gradlew ni SDK.
  "$V/../app-claw-main/app/src/test/java/com/example/clawcode/JarvisVoiceUnitTest.kt"
  "$V/../app-claw-main/app/src/test/java/com/example/clawcode/ChatRealInferenceTest.kt"
  "$V/../app-claw-main/app/src/test/java/com/example/clawcode/RealOllamaIntegrationTest.kt"
  "$V/VerifyProjectTests.kt"
)

SRC_WIN=()
for f in "${SRC[@]}"; do
  [ -f "$f" ] || { echo "FALTA FUENTE: $f"; exit 1; }
  SRC_WIN+=("$(cygpath -w "$f")")
done

"$JAVA_HOME/bin/java.exe" -cp "$(cygpath -w "$KOTLINC_JAR")" \
  org.jetbrains.kotlin.cli.jvm.K2JVMCompiler \
  -no-stdlib -nowarn -jvm-target 11 \
  -cp "$CP" -d "$(cygpath -w "$OUT")" \
  "${SRC_WIN[@]}"

echo "COMPILACIÓN OK"
find "$OUT" -name '*.class' | wc -l
