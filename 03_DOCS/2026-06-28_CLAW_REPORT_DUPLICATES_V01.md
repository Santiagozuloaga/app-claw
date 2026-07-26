# REPORTE DE DUPLICADOS — CLAW

## 1. Duplicados por Nombre y Hash
Se han detectado los siguientes archivos con contenido idéntico (Hash MD5 coincidente):

| Archivo A | Archivo B | Estado |
| :--- | :--- | :--- |
| `01_SRC/mcp/types.py` | `01_SRC/2024-06-19_CLAW_MCP_V01/types.py` | IDÉNTICO |
| `01_SRC/task/store.py` | `01_SRC/2024-06-19_CLAW_TASK_V01/store.py` | IDÉNTICO |
| `01_SRC/voice/stt.py` | `01_SRC/2024-06-19_CLAW_VOICE_V01/stt.py` | IDÉNTICO |

## 2. Duplicados Funcionales (Versiones Diferentes)
| Archivo | Versión A | Versión B | Observación |
| :--- | :--- | :--- | :--- |
| Core | `01_SRC/2024-06-19_CLAW_CORE_V01.py` | `01_SRC/clawspring (1).py` | La versión (1) tiene fixes de Windows UTF-8 adicionales. |

## 3. Carpetas Espejo
Las siguientes carpetas contienen exactamente los mismos archivos:
* `01_SRC/mcp/` <-> `01_SRC/2024-06-19_CLAW_MCP_V01/`
* `01_SRC/memory/` <-> `01_SRC/2024-06-19_CLAW_MEMORY_PACKAGE_V01/`
* `01_SRC/multi_agent/` <-> `01_SRC/2024-06-19_CLAW_MULTI_AGENT_V01/`
* `01_SRC/plugin/` <-> `01_SRC/2024-06-19_CLAW_PLUGIN_V01/`
* `01_SRC/skill/` <-> `01_SRC/2024-06-19_CLAW_SKILL_V01/`
* `01_SRC/task/` <-> `01_SRC/2024-06-19_CLAW_TASK_V01/`
* `01_SRC/voice/` <-> `01_SRC/2024-06-19_CLAW_VOICE_V01/`

## 4. Recomendación de Consolidación
La versión **ISO-SAGE** (las que empiezan con fecha) deben ser las canónicas. Las carpetas con nombre simple deben ser eliminadas una vez que se verifique que ningún import externo depende de ellas.

**Nivel de Confianza:** ALTA (Verificado por MD5 y DIFF).
