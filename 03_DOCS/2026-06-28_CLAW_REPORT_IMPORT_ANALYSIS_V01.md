# ANÁLISIS DE IMPORTACIONES — CLAW

## 1. Clasificación General
* **SAFE:** Imports de librerías estándar (os, sys, json, etc.).
* **BROKEN:**
    * Imports dinámicos en `run_claw.py`.
    * Referencias a `CLAW_2024_06_19_...` en archivos que solo contienen la versión `2024-06-19_CLAW_...`.
* **DYNAMIC:** Uso de `importlib` para cargar el core.
* **CIRCULAR:** Sospecha moderada en el triángulo `CORE -> AGENT -> TOOLS -> CORE`.

## 2. Incidencias Detalladas
| Archivo | Línea | Import Detectado | Estado | Destino Esperado |
| :--- | :--- | :--- | :--- | :--- |
| `run_claw.py` | 12 | `CLAW_2024_06_19_CORE_V01` | BROKEN | `2024-06-19_CLAW_CORE_V01` |
| `01_SRC/mcp/tools.py` | 19 | `CLAW_2024_06_19_TOOL_REGISTRY_V01` | BROKEN | `2024-06-19_CLAW_TOOL_REGISTRY_V01` |
| `01_SRC/compaction.py` | 4 | `import providers` | BROKEN | `2024-06-19_CLAW_PROVIDERS_V01` |

## 3. Análisis de Dependencias
* **HECHO VERIFICADO:** El proyecto depende fuertemente de un `PYTHONPATH` que incluya `01_SRC` y `00_SOPORTE`.
* **HIPÓTESIS:** Los archivos `*_SHIM_V01.py` fueron creados para mitigar la rotura de imports, pero están apuntando a las carpetas con nombres antiguos (ej. `memory/`) en lugar de las ISO-SAGE.

## 4. Recomendación Técnica
Sustituir gradualmente todos los imports que usen el formato "Jules" (`CLAW_...`) por el formato ISO-SAGE correcto (`2024-06-19_CLAW_...`) y eliminar el uso de `importlib` en el punto de entrada para permitir que las herramientas de análisis estático detecten errores antes de la ejecución.

**Nivel de Confianza:** ALTA (Verificado mediante intentos de importación en REPL).
