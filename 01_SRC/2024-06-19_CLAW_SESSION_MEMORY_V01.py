"""
memory.py — Correcciones Bug #6 y #12: Memoria automática segura.

Bug #6: copyPlanForResume() era fire-and-forget sin .catch()
Bug #12: Thinking blocks en mensajes guardados causan error 400 en replay
"""
import asyncio
import json
import logging
import os
from datetime import datetime
from pathlib import Path
from typing import Any, Optional

logger = logging.getLogger(__name__)

# Directorio de memoria de Claw (relativo al home del usuario)
MEMORIA_DIR = Path.home() / ".claw" / "memoria"
MAX_HISTORIAL_SESIONES = 50  # Límite para no crecer indefinidamente


# ---------------------------------------------------------------------------
# Corrección Bug #12: Filtrar thinking blocks antes de guardar/cargar
# ---------------------------------------------------------------------------

def limpiar_mensajes_para_persistencia(mensajes: list[dict]) -> list[dict]:
    """
    Elimina bloques de thinking antes de guardar al disco.

    Corrección Bug #12: Los thinking blocks tienen firmas criptográficas
    vinculadas al modelo. Si se reproducen en una sesión nueva o con un
    modelo diferente, la API devuelve error 400 "thinking blocks cannot
    be modified".

    Solo elimina thinking de mensajes del asistente, no toca mensajes del usuario.
    """
    mensajes_limpios = []
    eliminados = 0

    for msg in mensajes:
        if msg.get("role") != "assistant":
            mensajes_limpios.append(msg)
            continue

        contenido = msg.get("content", [])

        # Si content es string (respuesta simple), no hay thinking blocks
        if isinstance(contenido, str):
            mensajes_limpios.append(msg)
            continue

        # Filtrar bloques de thinking
        contenido_limpio = [
            bloque for bloque in contenido
            if isinstance(bloque, dict)
            and bloque.get("type") not in ("thinking", "redacted_thinking")
        ]

        if len(contenido_limpio) < len(contenido):
            eliminados += len(contenido) - len(contenido_limpio)

        # Solo incluir el mensaje si tiene contenido después de filtrar
        if contenido_limpio:
            mensajes_limpios.append({**msg, "content": contenido_limpio})
        # Si quedó vacío, omitir el mensaje del asistente

    if eliminados > 0:
        logger.debug(
            f"Limpieza de pensamiento: {eliminados} bloques de thinking "
            "eliminados para persistencia segura"
        )

    return mensajes_limpios


def limpiar_mensajes_para_replay(mensajes: list[dict]) -> list[dict]:
    """
    Alias de limpiar_mensajes_para_persistencia para mayor claridad semántica.
    Usar al CARGAR una sesión guardada antes de enviarlo a la API.
    """
    return limpiar_mensajes_para_persistencia(mensajes)


# ---------------------------------------------------------------------------
# Corrección Bug #6: Guardado de memoria con manejo de errores
# ---------------------------------------------------------------------------

async def guardar_sesion(
    session_id: str,
    mensajes: list[dict],
    metadata: Optional[dict] = None,
) -> bool:
    """
    Guarda la sesión actual al disco de forma segura.

    Corrección Bug #6: El original usaba fire-and-forget sin .catch().
    Esta versión:
    - Captura y loggea todos los errores (no los silencia)
    - Limpia thinking blocks antes de guardar (Bug #12)
    - Retorna bool indicando éxito/fallo
    """
    try:
        MEMORIA_DIR.mkdir(parents=True, exist_ok=True)
        ruta_sesion = MEMORIA_DIR / f"{session_id}.json"

        # Limpiar thinking blocks antes de guardar
        mensajes_limpios = limpiar_mensajes_para_persistencia(mensajes)

        datos = {
            "session_id": session_id,
            "guardado_en": datetime.now().isoformat(),
            "mensajes": mensajes_limpios,
            "metadata": metadata or {},
        }

        # Escribir a archivo temporal primero (escritura atómica)
        ruta_temp = ruta_sesion.with_suffix(".tmp")
        ruta_temp.write_text(
            json.dumps(datos, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        ruta_temp.replace(ruta_sesion)  # Rename atómico

        logger.debug(
            f"Sesión '{session_id}' guardada: "
            f"{len(mensajes_limpios)} mensajes en {ruta_sesion}"
        )
        return True

    except PermissionError as e:
        logger.error(
            f"Sin permisos para guardar sesión '{session_id}': {e}. "
            f"Verifica que {MEMORIA_DIR} sea escribible."
        )
        return False
    except OSError as e:
        logger.error(f"Error de disco al guardar sesión '{session_id}': {e}")
        return False
    except Exception as e:
        logger.error(
            f"Error inesperado al guardar sesión '{session_id}': "
            f"{type(e).__name__}: {e}",
            exc_info=True,
        )
        return False


async def cargar_sesion(session_id: str) -> Optional[list[dict]]:
    """
    Carga una sesión guardada del disco.

    Corrección Bug #12: Limpia thinking blocks al cargar para evitar
    error 400 "thinking blocks cannot be modified" en la API.
    """
    ruta_sesion = MEMORIA_DIR / f"{session_id}.json"

    try:
        if not ruta_sesion.exists():
            logger.debug(f"No existe sesión guardada para '{session_id}'")
            return None

        datos = json.loads(ruta_sesion.read_text(encoding="utf-8"))
        mensajes = datos.get("mensajes", [])

        # Limpiar thinking blocks al cargar (por si quedaron de versiones anteriores)
        mensajes_limpios = limpiar_mensajes_para_replay(mensajes)

        logger.debug(
            f"Sesión '{session_id}' cargada: {len(mensajes_limpios)} mensajes"
        )
        return mensajes_limpios

    except json.JSONDecodeError as e:
        logger.error(
            f"Archivo de sesión '{session_id}' corrupto: {e}. "
            "Se ignorará y comenzará sesión nueva."
        )
        return None
    except OSError as e:
        logger.warning(f"No se pudo leer sesión '{session_id}': {e}")
        return None
    except Exception as e:
        logger.error(
            f"Error inesperado al cargar sesión '{session_id}': {e}",
            exc_info=True,
        )
        return None


async def guardar_memoria_auto(
    session_id: str,
    mensajes: list[dict],
) -> None:
    """
    Auto-consolidación al salir (Fase 1 del plan).
    Versión fire-and-forget SEGURA: loggea errores en lugar de silenciarlos.

    Uso en el evento de cierre de Claw:
        asyncio.create_task(guardar_memoria_auto(session_id, mensajes))
    """
    exito = await guardar_sesion(session_id, mensajes)
    if not exito:
        logger.warning(
            f"No se pudo auto-guardar la sesión '{session_id}'. "
            "El historial de esta sesión puede perderse."
        )


def listar_sesiones() -> list[dict]:
    """
    Lista todas las sesiones guardadas, ordenadas por fecha (más reciente primero).
    Retorna lista de dicts con {session_id, guardado_en, num_mensajes}.
    """
    if not MEMORIA_DIR.exists():
        return []

    sesiones = []
    for archivo in MEMORIA_DIR.glob("*.json"):
        try:
            datos = json.loads(archivo.read_text(encoding="utf-8"))
            sesiones.append({
                "session_id": datos.get("session_id", archivo.stem),
                "guardado_en": datos.get("guardado_en", ""),
                "num_mensajes": len(datos.get("mensajes", [])),
                "metadata": datos.get("metadata", {}),
            })
        except Exception as e:
            logger.warning(f"No se pudo leer sesión {archivo.name}: {e}")

    # Ordenar por fecha descendente
    sesiones.sort(key=lambda s: s.get("guardado_en", ""), reverse=True)
    return sesiones[:MAX_HISTORIAL_SESIONES]
