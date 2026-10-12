# Cómo llevar CLAW Android a un dispositivo, con lo que hay en esta máquina

Guía corta y comprobable. Nada de aquí está afirmado como "ya funciona en Android": **la app no se ha
compilado ni ejecutado en un dispositivo** porque en esta máquina no hay Android SDK ni Gradle
(verificado: sin `ANDROID_HOME`, sin `%LOCALAPPDATA%\Android\Sdk`, sin `~/.gradle`, sin
`gradle/wrapper`, y sólo JRE 1.8 + el JBR 25 de IntelliJ).

## Lo que sí está instalado

| Pieza | Dónde | Versión comprobada |
|---|---|---|
| `adb` / platform-tools | `<ruta_de_plataforma>\adb.exe` | 1.0.41 (37.0.1-15733141) |
| Ollama en el PC | `http://127.0.0.1:11434` | 0.34.4, modelo local `qwen2.5:0.5b` (397 MB), escuchando **sólo** en 127.0.0.1 |
| Kotlin CLI del IDE | `IntelliJ IDEA 2026.1.3/plugins/Kotlin/kotlinc` | 2.3.10 (usada por `tools/verify-offsdk`) |

## Ruta recomendada: teléfono por USB + túnel adb (sin recompilar ajustes ni abrir la LAN)

```bat
adb devices                                  # confirma el teléfono
adb reverse tcp:11434 tcp:11434              # 127.0.0.1 del teléfono -> Ollama del PC
```

Y en **Ajustes → Ollama** de la app: `http://127.0.0.1:11434`. Esta combina dos cosas que ya están
ciertas en el código: `127.0.0.1` está dentro de `RUNTIME_HOST_CANDIDATES` y
`res/xml/network_security_config.xml` ya autoriza el HTTP en claro en loopback, así que no hay que
tocar configuración ni exponer el servicio a la red. Al terminar, `adb reverse --remove-all`.

## Las otras dos rutas y por qué cuestan más

- **Ejecutar Ollama en el propio teléfono** (`pkg install com.ollama.android` o Termux): mismo host
  `http://127.0.0.1:11434`, pero el modelo y su RAM quedan en el dispositivo.
- **IP LAN del PC**: exige (1) añadir esa IP a `network_security_config.xml` y **recompilar**, porque
  Android bloquea HTTP en claro a hosts no listados, y (2) arrancar Ollama con
  `OLLAMA_HOST=0.0.0.0:11434`, porque de fábrica rechaza conexiones de otras máquinas. El chat ya lo
  dice en la pista que imprime cuando falla la conexión.

## Compilar (cuando exista el toolchain)

El repo **no trae wrapper**, así que Gradle no es reproducible tal cual. Con JDK 17 y un Gradle 8.x
del sistema, el comando mínimo para generar el wrapper es `gradle wrapper --gradle-version 8.9` y
luego `gradlew assembleDebug`. Alternativa: abrir la carpeta `app-claw-main` en Android Studio, que
añade el wrapper y baja el SDK. AGP 8.7.3 y Kotlin 2.0.21 ya están fijados en
`gradle/libs.versions.toml`; `compileSdk 35`, `minSdk 24`.

## Qué comprobar al primer arranque (esperado documentado, no afirmado)

1. Chat: el mensaje aparece en una burbuja que **crece mientras el modelo genera** (stream NDJSON).
   Antes de llegar el primer token debe verse `Consultando 'qwen2.5:0.5b' en http://127.0.0.1:11434`.
2. Mensaje vacío desde el micrófono: no publica turno ni petición (consola `[INPUT_REJECTED]`).
3. Dos envíos rápidos: burbujas alternadas `USER, JARVIS, USER, JARVIS`, nunca dos turnos a la vez.
4. `/sys` y `/diag`: sin cifras inventadas; CPU como delta de `/proc/stat`; batería/RAM "-1/sin medir"
   hasta el primer sondeo (~5 s).
5. `/bridge`: debe decir `Estado del socket: NUNCA ABIERTO`; el puente de WhatsApp no existe en esta
   build y la demo local lo dice en vez de afirmar envíos.
6. Matar la app y reabrirla: el historial vuelve (últimos 60 mensajes, `claw_chat_history.xml`).
7. Si Ollama está caído: una sola burbuja con `[Runtime local sin respuesta]` y la pista correcta.

## Verificación disponible hoy

`tools/verify-offsdk` (18 verificaciones, 481 asserts) compila y ejecuta las fuentes reales del módulo
`app` con kotlinc del IDE, stubs funcionales de Android y Ollama real del PC. No sustituye al paso 5 de
la escalera (emulador/dispositivo): lo deja listo y documentado.
