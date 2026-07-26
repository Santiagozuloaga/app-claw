"""
thinking.py — Corrección Bug #3: Thinking para Qwen3 y modelos de terceros.

El código original enviaba thinking a modelos que no lo soportan si la
variable ANTHROPIC_DEFAULT_*_MODEL_SUPPORTED_CAPABILITIES no estaba definida.
Esta versión verifica capacidades de forma segura antes de enviar thinking.
"""
import os
import time
import logging
from typing import Optional

logger = logging.getLogger(__name__)

# Cache for environment variable lookups (TTL 5s)
_ENV_CACHE = {}
_ENV_CACHE_TTL = 5.0

# Modelos de Anthropic que soportan thinking (lista oficial conocida)
_MODELOS_CON_THINKING = {
    "claude-opus-4",
    "claude-opus-4-5",
    "claude-opus-4-6",
    "claude-sonnet-4",
    "claude-sonnet-4-5",
    "claude-sonnet-4-6",
    "claude-3-5-sonnet",
    "claude-3-7-sonnet",
}

# Modelos que soportan adaptive thinking
_MODELOS_CON_ADAPTIVE_THINKING = {
    "claude-opus-4",
    "claude-opus-4-5",
    "claude-opus-4-6",
    "claude-3-7-sonnet",
}

# Modelos de terceros conocidos que NO soportan thinking de Anthropic
_MODELOS_SIN_THINKING = {
    "qwen",        # Prefijo: qwen3, qwen2.5, etc.
    "deepseek",
    "llama",
    "mistral",
    "gemma",
    "phi",
    "grok",
}


def modelo_soporta_thinking(modelo: str) -> bool:
    """
    Verifica si el modelo soporta extended thinking de Anthropic.

    Orden de verificación:
    1. Variable de entorno explícita (mayor prioridad)
    2. Lista de modelos conocidos de terceros sin thinking
    3. Lista de modelos de Anthropic con thinking
    4. Default: False (seguro para modelos desconocidos)
    """
    modelo_lower = modelo.lower()

    # 1. Verificar variable de entorno de capacidades (el mecanismo original)
    override = _leer_capacidad_env(modelo_lower, "thinking")
    if override is not None:
        return override

    # 2. Si el nombre sugiere un modelo de tercero sin thinking, rechazar
    for prefijo in _MODELOS_SIN_THINKING:
        if prefijo in modelo_lower:
            logger.debug(
                f"Modelo '{modelo}' detectado como tercero sin thinking (prefijo: {prefijo})"
            )
            return False

    # 3. Si coincide con modelos Anthropic conocidos, permitir
    for nombre in _MODELOS_CON_THINKING:
        if nombre in modelo_lower:
            return True

    # 4. Default seguro: no enviar thinking a modelos desconocidos
    logger.debug(f"Modelo '{modelo}' desconocido, thinking desactivado por seguridad")
    return False


def modelo_soporta_adaptive_thinking(modelo: str) -> bool:
    """Verifica si el modelo soporta adaptive thinking (sin budget fijo)."""
    modelo_lower = modelo.lower()

    override = _leer_capacidad_env(modelo_lower, "adaptive_thinking")
    if override is not None:
        return override

    for nombre in _MODELOS_CON_ADAPTIVE_THINKING:
        if nombre in modelo_lower:
            return True

    return False


def _leer_capacidad_env(modelo_lower: str, capacidad: str) -> Optional[bool]:
    """
    Lee ANTHROPIC_DEFAULT_*_MODEL_SUPPORTED_CAPABILITIES del entorno (con cache TTL).
    Corrección Bug #7: El cache tiene un TTL muy corto para permitir cambios.
    Retorna None si la variable no está configurada.
    """
    cache_key = f"cap_{modelo_lower}_{capacidad}"
    now = time.time()
    if cache_key in _ENV_CACHE:
        val, ts = _ENV_CACHE[cache_key]
        if now - ts < _ENV_CACHE_TTL:
            return val

    tiers = [
        ("ANTHROPIC_DEFAULT_OPUS_MODEL", "ANTHROPIC_DEFAULT_OPUS_MODEL_SUPPORTED_CAPABILITIES"),
        ("ANTHROPIC_DEFAULT_SONNET_MODEL", "ANTHROPIC_DEFAULT_SONNET_MODEL_SUPPORTED_CAPABILITIES"),
        ("ANTHROPIC_DEFAULT_HAIKU_MODEL", "ANTHROPIC_DEFAULT_HAIKU_MODEL_SUPPORTED_CAPABILITIES"),
    ]

    res = None
    for modelo_env_var, capacidades_env_var in tiers:
        modelo_fijado = os.environ.get(modelo_env_var, "")
        capacidades_raw = os.environ.get(capacidades_env_var)

        if not modelo_fijado or capacidades_raw is None:
            continue

        if modelo_lower != modelo_fijado.lower():
            continue

        capacidades = {c.strip().lower() for c in capacidades_raw.split(",")}
        res = capacidad.lower() in capacidades
        break

    _ENV_CACHE[cache_key] = (res, now)
    return res


def construir_params_thinking(modelo: str, thinking_config: dict) -> dict:
    """
    Construye los parámetros de thinking para la API de Anthropic.

    Corrección Bug #3: Verifica capacidades antes de enviar thinking.
    Si el modelo no soporta thinking, retorna {} sin error.

    Args:
        modelo: Nombre del modelo (ej: "claude-opus-4-6", "qwen3-235b")
        thinking_config: Dict con tipo: {"type": "adaptive"/"enabled"/"disabled",
                                          "budgetTokens": int}

    Returns:
        Dict con parámetros de thinking para la API, o {} si no aplica.
    """
    tipo = thinking_config.get("type", "disabled")

    if tipo == "disabled":
        return {}

    # Verificar capacidad del modelo ANTES de enviar
    if not modelo_soporta_thinking(modelo):
        logger.info(
            f"Thinking solicitado ({tipo}) pero el modelo '{modelo}' "
            "no lo soporta. Omitiendo thinking."
        )
        return {}

    if tipo == "adaptive":
        if modelo_soporta_adaptive_thinking(modelo):
            return {"type": "adaptive"}
        else:
            # Fallback a enabled con budget por defecto
            budget = _calcular_budget_por_defecto(modelo)
            logger.debug(
                f"Modelo '{modelo}' no soporta adaptive thinking, "
                f"usando enabled con budget={budget}"
            )
            return {"type": "enabled", "budget_tokens": budget}

    elif tipo == "enabled":
        budget = thinking_config.get("budgetTokens", _calcular_budget_por_defecto(modelo))
        # Corrección Bug #9: Validar que budget sea entero positivo
        budget = _validar_budget(budget)
        return {"type": "enabled", "budget_tokens": budget}

    return {}


def _calcular_budget_por_defecto(modelo: str) -> int:
    """Budget de thinking por defecto según el modelo."""
    modelo_lower = modelo.lower()
    if "opus" in modelo_lower:
        return 32000
    if "sonnet" in modelo_lower:
        return 16000
    return 8000


def _validar_budget(budget) -> int:
    """
    Corrección Bug #9: Valida que el budget sea un entero positivo.
    Evita el bug de parseInt() sin validar NaN del original.
    """
    try:
        valor = int(budget)
        if valor <= 0:
            logger.warning(f"Budget de thinking inválido ({budget}), usando 8000")
            return 8000
        return min(valor, 100000)  # Cap razonable
    except (ValueError, TypeError):
        logger.warning(f"Budget de thinking no es número ({budget!r}), usando 8000")
        return 8000


def obtener_max_thinking_tokens() -> int:
    """
    Corrección Bug #9: Lee MAX_THINKING_TOKENS con validación correcta.
    El original usaba parseInt() sin verificar NaN.
    """
    raw = os.environ.get("MAX_THINKING_TOKENS", "")
    if not raw:
        return 0  # No configurado = usar default del modelo
    return _validar_budget(raw)
