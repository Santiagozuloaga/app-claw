# REPORTE DE DEPENDENCIAS — CLAW

## 1. Grafo de Dependencias Internas (Simplificado)
1. `run_claw.py` -> `CORE`
2. `CORE` -> `AGENT`, `PROVIDERS`, `CONFIG`, `TOOLS`, `MEMORY_SHIM`, `SKILL`, `MCP`, `PLUGIN`, `TASK`, `VOICE`
3. `AGENT` -> `TOOL_REGISTRY`, `TOOLS`, `PROVIDERS`, `COMPACTION`
4. `TOOLS` -> `TOOL_REGISTRY`

## 2. Dependencias Externas (Fase 1)
* **ANTHROPIC:** Requerido para modelos Claude.
* **OPENAI:** Requerido para modelos GPT y Whisper.
* **HTTPX:** Cliente HTTP para MCP y proveedores.
* **RICH:** Interfaz de usuario REPL.

## 3. Estado del Entorno
* **HECHO VERIFICADO:** El entorno actual de ejecución carece de las librerías mencionadas en `requirements.txt`.
* **SUPOSICIÓN:** El usuario tiene un entorno virtual o contenedor donde estas dependencias están presentes, pero no están visibles en el shell de auditoría actual.

## 4. Riesgos de Dependencias
* **Incompatibilidad de versiones:** La falta de un archivo de bloqueo (lock file) como `poetry.lock` o `requirements.lock` podría introducir regresiones si se instalan versiones muy recientes de `anthropic`.

**Nivel de Confianza:** MEDIA (Basado en análisis estático de código).
