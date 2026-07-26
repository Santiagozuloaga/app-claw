"""
error_utils.py — Corrección Bugs #1 y #2: Eliminación de bare except / catch vacío.

Patrones seguros de manejo de errores para usar en todo Claw.
Ninguna excepción debe silenciarse sin al menos un log de WARNING.
"""
import asyncio
import functools
import logging
import sys
import traceback
from typing import Any, Callable, Coroutine, Optional, TypeVar

logger = logging.getLogger(__name__)

T = TypeVar("T")


# ---------------------------------------------------------------------------
# Corrección Bug #1/#2: Decoradores que reemplazan catch{} vacíos
# ---------------------------------------------------------------------------

def log_excepciones(nivel: str = "warning", retornar=None):
    """
    Decorador: captura TODAS las excepciones, las loggea y retorna un valor por defecto.
    Reemplaza el patrón 'except: pass' o 'catch {}' vacío.

    Uso:
        @log_excepciones(nivel="warning", retornar=False)
        def operacion_arriesgada():
            ...
    """
    def decorador(func):
        @functools.wraps(func)
        def wrapper(*args, **kwargs):
            try:
                return func(*args, **kwargs)
            except Exception as e:
                _loggear(nivel, func.__name__, e)
                return retornar
        return wrapper
    return decorador


def log_excepciones_async(nivel: str = "warning", retornar=None):
    """
    Versión async del decorador log_excepciones.

    Uso:
        @log_excepciones_async(nivel="error", retornar=None)
        async def guardar_memoria():
            ...
    """
    def decorador(func):
        @functools.wraps(func)
        async def wrapper(*args, **kwargs):
            try:
                return await func(*args, **kwargs)
            except Exception as e:
                _loggear(nivel, func.__name__, e)
                return retornar
        return wrapper
    return decorador


def _loggear(nivel: str, nombre_funcion: str, exc: Exception) -> None:
    """Loggea una excepción al nivel indicado con traceback si es ERROR."""
    mensaje = f"Excepción en {nombre_funcion}: {type(exc).__name__}: {exc}"
    if nivel == "debug":
        logger.debug(mensaje)
    elif nivel == "info":
        logger.info(mensaje)
    elif nivel == "warning":
        logger.warning(mensaje)
    elif nivel == "error":
        logger.error(mensaje, exc_info=True)
    elif nivel == "critical":
        logger.critical(mensaje, exc_info=True)


# ---------------------------------------------------------------------------
# Corrección Bug #5 y #6: Tareas async fire-and-forget seguras
# ---------------------------------------------------------------------------

def tarea_segura(
    coro: Coroutine,
    nombre: str = "tarea",
    nivel_error: str = "warning",
) -> asyncio.Task:
    """
    Reemplaza: asyncio.create_task(coro)  ← errores silenciados
    Con:       tarea_segura(coro, nombre="guardar_memoria")  ← errores loggeados

    Corrección Bug #6: fire-and-forget con manejo de errores.
    """
    async def _envolver():
        try:
            await coro
        except asyncio.CancelledError:
            logger.debug(f"Tarea '{nombre}' cancelada")
        except Exception as e:
            _loggear(nivel_error, nombre, e)

    return asyncio.create_task(_envolver(), name=nombre)


# ---------------------------------------------------------------------------
# Corrección Bug #5: new Promise<void>(async resolve =>) anti-pattern
# ---------------------------------------------------------------------------

async def esperar_con_timeout(
    coro: Coroutine,
    timeout_segundos: float,
    nombre: str = "operación",
) -> Optional[Any]:
    """
    Reemplaza el anti-patrón new Promise<void>(async resolve =>).
    Ejecuta una corutina con timeout, maneja errores correctamente.

    En el original TypeScript, si la async function en el executor lanzaba,
    la Promise quedaba colgada para siempre. Esto no ocurre aquí.
    """
    try:
        return await asyncio.wait_for(coro, timeout=timeout_segundos)
    except asyncio.TimeoutError:
        logger.warning(f"'{nombre}' excedió el timeout de {timeout_segundos}s")
        return None
    except Exception as e:
        logger.error(f"Error en '{nombre}': {e}", exc_info=True)
        return None


# ---------------------------------------------------------------------------
# Corrección Bug #8: Async con flag de cancelación (evita race conditions)
# ---------------------------------------------------------------------------

class TareaConCancelacion:
    """
    Envuelve una corutina con soporte de cancelación explícita.
    Corrige el patrón de race condition en useEffect sin cleanup.

    Uso para el pipeline de voz (Fase 2):
        pipeline = TareaConCancelacion()
        resultado = await pipeline.ejecutar(grabar_y_transcribir())
        # Si hay que cancelar: pipeline.cancelar()
    """

    def __init__(self):
        self._cancelado = False
        self._tarea: Optional[asyncio.Task] = None

    async def ejecutar(self, coro: Coroutine, nombre: str = "tarea") -> Optional[Any]:
        """Ejecuta la corutina con soporte de cancelación."""
        self._cancelado = False
        self._tarea = asyncio.create_task(coro, name=nombre)
        try:
            return await self._tarea
        except asyncio.CancelledError:
            logger.debug(f"TareaConCancelacion '{nombre}' cancelada")
            return None
        except Exception as e:
            logger.error(f"Error en TareaConCancelacion '{nombre}': {e}", exc_info=True)
            return None

    def cancelar(self) -> None:
        """Cancela la tarea en ejecución."""
        self._cancelado = True
        if self._tarea and not self._tarea.done():
            self._tarea.cancel()

    @property
    def fue_cancelado(self) -> bool:
        return self._cancelado


# ---------------------------------------------------------------------------
# Corrección Bug #11: Hooks de directorio sin catch
# ---------------------------------------------------------------------------

async def ejecutar_hook_seguro(
    nombre_hook: str,
    func: Callable,
    *args,
    **kwargs,
) -> bool:
    """
    Ejecuta un hook (directorio, sesión, etc.) de forma segura.
    Si el hook falla, lo loggea pero NO propaga el error.
    Retorna True si el hook tuvo éxito, False si falló.

    Corrección Bug #11: void onCwdChangedForHooks() sin catch.
    """
    try:
        if asyncio.iscoroutinefunction(func):
            await func(*args, **kwargs)
        else:
            func(*args, **kwargs)
        return True
    except Exception as e:
        logger.warning(
            f"Hook '{nombre_hook}' falló (no es crítico): "
            f"{type(e).__name__}: {e}"
        )
        return False
