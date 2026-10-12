#!/usr/bin/env bash
# Ejecuta un arnés de verificación ya compilado en out5 contra el Ollama local.
# usage: bash run.sh verify.VerifyPersistenceKt > persistencia.log
set -euo pipefail

V="$(cd "$(dirname "$0")" && pwd)"
JAVA_HOME="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/jbr"
KOTLIN_LIB="/c/Program Files/JetBrains/IntelliJ IDEA 2026.1.3/plugins/Kotlin/kotlinc/lib"
MAIN="${1:?falta la clase main, p.ej. verify.VerifyPersistenceKt}"

# El stdlib del compilador va PRIMERO: libs/ trae kotlin-stdlib 2.0.21 (el de la app),
# pero kotlinc 2.3.10 emite kotlin.coroutines.jvm.internal.SpillingKt, que sólo existe
# en el stdlib nuevo. Sin esto revienta con NoClassDefFoundError al primer suspend.
CP="$(cygpath -w "$V/out5");$(cygpath -w "$KOTLIN_LIB/kotlin-stdlib.jar")"
for j in "$V"/libs/*.jar; do
  CP="$CP;$(cygpath -w "$j")"
done

# java.exe es nativo: sin esto Git Bash reconvierte el classpath con ';' y lo rompe.
export MSYS2_ARG_CONV_EXCL="*"
"$JAVA_HOME/bin/java.exe" -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -cp "$CP" "$MAIN"
