# Correcciones Claw — Guía de Integración

## Archivos incluidos

| Archivo | Bugs que corrige | Qué hace |
|---------|-----------------|----------|
| `encoding.py` | Bug #4 (UTF-8 Windows) | `configure_encoding()` al inicio |
| `thinking.py` | Bug #3 (Qwen3), #7 (memoize+env), #9 (parseInt) | Parámetros de thinking seguros |
| `memory.py` | Bug #6 (fire-and-forget), #12 (thinking replay) | Guardado/carga de sesiones |
| `error_utils.py` | Bug #1/#2 (bare except), #5 (Promise anti-patrón), #8 (race condition), #11 (hooks sin catch) | Utilidades de manejo de errores |
| `claw.py` | Todos | Punto de entrada con todo integrado |
| `claw.bat` | Bug #4 (UTF-8 Windows) | Lanzador Windows con `chcp 65001` |

---

## Integración rápida (si ya tienes tu propio claw.py)

### 1. Copiar los módulos de corrección junto a tu claw.py

```
tu_proyecto/
├── claw.py            ← Tu archivo existente
├── claw.bat           ← Nuevo lanzador Windows
├── encoding.py        ← Nuevo
├── thinking.py        ← Nuevo
├── memory.py          ← Nuevo
└── error_utils.py     ← Nuevo
```

### 2. Agregar al principio de tu claw.py (antes de todo lo demás)

```python
# === INICIO SEGURO — agregar ANTES de cualquier import/print ===
import sys, os
if sys.platform == "win32":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8", errors="replace")
    os.environ["PYTHONIOENCODING"] = "utf-8"
# === FIN INICIO SEGURO ===

from encoding import configure_encoding
configure_encoding()  # Aplica chcp 65001 en Windows
```

### 3. Reemplazar el manejo de thinking

```python
# Antes (peligroso con Qwen3):
params = {"thinking": {"type": "adaptive"}}

# Ahora (seguro):
from thinking import construir_params_thinking
params_thinking = construir_params_thinking(modelo, {"type": "adaptive"})
if params_thinking:
    kwargs["thinking"] = params_thinking
```

### 4. Reemplazar guardado de memoria

```python
# Antes (fire-and-forget sin catch - Bug #6):
asyncio.create_task(guardar_memoria())

# Ahora (con manejo de errores):
from error_utils import tarea_segura
tarea_segura(guardar_memoria(), nombre="guardar_memoria")
```

### 5. Limpiar historial antes de enviarlo a la API

```python
# Al cargar historial guardado (Bug #12 - thinking blocks causan error 400):
from memory import limpiar_mensajes_para_replay
mensajes = limpiar_mensajes_para_replay(mensajes_del_disco)
```

### 6. Reemplazar bare except

```python
# Antes (Bug #1/#2 - error silenciado):
try:
    operacion()
except:
    pass

# Ahora (error loggeado):
import logging
logger = logging.getLogger(__name__)
try:
    operacion()
except Exception as e:
    logger.warning(f"Error en operacion: {e}")
```

---

## Checklist de verificación

- [ ] `chcp 65001` en claw.bat
- [ ] `sys.stdout.reconfigure(encoding='utf-8')` al inicio de claw.py
- [ ] Thinking verificado antes de enviar a la API
- [ ] Historial limpiado de thinking blocks al cargar
- [ ] Ningún `except: pass` o `except Exception: pass` sin logging
- [ ] fire-and-forget usando `tarea_segura()` en lugar de `create_task()` directo

---

*Generado por análisis del código fuente de Claude Code — Mayo 2026*
