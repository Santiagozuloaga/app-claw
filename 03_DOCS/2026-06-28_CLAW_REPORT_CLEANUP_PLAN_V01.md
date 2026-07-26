# PLAN DE LIMPIEZA — CLAW

Este informe detalla los archivos y directorios identificados para su eliminación o reubicación en la Fase A de la recuperación.

| Ruta | Razón | Riesgo | Recomendación |
| :--- | :--- | :--- | :--- |
| `01_SRC/mcp/` | Duplicado de `2024-06-19_CLAW_MCP_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/memory/` | Duplicado de `2024-06-19_CLAW_MEMORY_PACKAGE_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/multi_agent/` | Duplicado de `2024-06-19_CLAW_MULTI_AGENT_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/plugin/` | Duplicado de `2024-06-19_CLAW_PLUGIN_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/skill/` | Duplicado de `2024-06-19_CLAW_SKILL_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/task/` | Duplicado de `2024-06-19_CLAW_TASK_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/voice/` | Duplicado de `2024-06-19_CLAW_VOICE_V01/` | Bajo | Eliminar tras verificar imports. |
| `01_SRC/clawspring (1).py` | Archivo duplicado con nombre no estándar. | Bajo | Mover fixes a la versión canónica y eliminar. |
| `**/__pycache__` | Archivos de caché generados. | Nulo | Eliminar recursivamente. |
| `**/*.pyc` | Archivos compilados. | Nulo | Eliminar recursivamente. |

## Resumen de Limpieza
* **Total directorios a eliminar:** 7
* **Total archivos identificados:** ~200 (incluyendo .pyc)
* **Estado de seguridad:** ALTO (Todos los archivos tienen respaldo en sus versiones ISO-SAGE o son generados).
