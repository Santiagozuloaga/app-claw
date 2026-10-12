# Verificación fuera del SDK de Android

Arnés que compila y ejecuta de verdad las fuentes Kotlin del módulo `app` en una máquina sin
Android SDK, usando el `kotlinc` y el JBR que trae IntelliJ, `stubs` funcionales de Android y un
servidor Ollama falso (`com.sun.net.httpserver`).

- `compile.sh` lista explícitamente los ficheros del proyecto que se compilan: si algo no
  compila, el arnés falla.
- `run.sh verify.<Clase>Kt` ejecuta una batería de asserts `PASS/FAIL` sobre el código real.
- `stub/` sustituye APIs de Android con comportamiento real (SharedPreferences en memoria
  persistente entre instancias, ActivityManager/BatteryManager inyectables, ficheros temporales).
- `libs/` NO está en el repositorio: hacen falta `kotlin-stdlib`, `kotlinx-coroutines-core-jvm`,
  `kotlinx-serialization-{core,json}-jvm`, `okhttp-4.12.0` y `okio-jvm` (mismas versiones que
  `app/build.gradle.kts`). Para `verify.VerifyProjectTestsKt` añades `junit-4.13.2.jar` y
  `hamcrest-core-1.3.jar`, que es lo que permite ejecutar el JUnit del propio proyecto sin Gradle.

Batería verificada en esta rama: **21 arneses, 548 asserts en verde**, cada hito con prueba de
no-vacuidad (sabotaje del código cubierto -> FAIL -> restauración idéntica -> PASS). Lo que este
arnés **no** puede probar: nada que dependa de Compose (pantallas renderizadas) ni de un dispositivo
— no hay SDK ni `gradle/wrapper` en la máquina de trabajo.
