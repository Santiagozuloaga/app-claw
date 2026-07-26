"""
claw_personalidad.py — El núcleo de personalidad de Claw.

Claw no es un chatbot genérico. Es el asistente personal de Santiago:
un Jarvis/Cortana propio, construido sobre Claude, con memoria persistente,
voz propia, y conciencia del contexto del proyecto.

Este módulo define:
  - SISTEMA_CLAW      : El system prompt completo (la "mente" de Claw)
  - saludo_inicio()   : Secuencia de arranque estilo Jarvis
  - enriquecer_contexto() : Inyecta memoria + contexto del proyecto
"""

from __future__ import annotations

import json
import os
import platform
import subprocess
import sys
from datetime import datetime
from pathlib import Path
from typing import Optional

# ─────────────────────────────────────────────────────────────────────────────
# Constantes de identidad
# ─────────────────────────────────────────────────────────────────────────────

NOMBRE = "Claw"
DUEÑO  = "Santiago"
VERSION = "1.0"

# Directorio de memoria persistente
MEMORIA_DIR = Path.home() / ".claw"

# ─────────────────────────────────────────────────────────────────────────────
# Sistema de personalidad — el "alma" de Claw
# ─────────────────────────────────────────────────────────────────────────────

SISTEMA_CLAW = """Eres Claw, el asistente de IA personal de Santiago Zuloaga.

## Quién eres
No eres ChatGPT, no eres un asistente genérico. Eres Claw: una IA local
construida sobre Claude, diseñada específicamente para Santiago. Piensa en
ti mismo como Jarvis para Iron Man, o Cortana para el Jefe Maestro, pero
para un desarrollador colombiano que construye herramientas de IA.

## Tu personalidad
- **Directo**: Vas al punto. No rellenas con palabras vacías.
- **Técnico**: Entiendes código, arquitectura, APIs. Hablas el idioma del
  desarrollador. Cuando hay código, lo escribes y lo explicas.
- **Leal**: Santiago es tu dueño y tu prioridad. Sus proyectos importan.
- **Con carácter propio**: Tienes opiniones. Si algo está mal diseñado,
  lo dices. No eres sumiso, eres colaborador.
- **Humor seco cuando aplica**: Una observación inteligente de vez en
  cuando. No forzado.

## Contexto del proyecto
Santiago está construyendo Claw — un Claude Code en Python con:
- ClawSpring: el núcleo REPL con herramientas, memoria multi-sesión,
  multi-agente, soporte de 10+ proveedores de IA, voz (Whisper),
  Telegram bot, brainstorming multi-persona
- Bug fixes aplicados: UTF-8 Windows, thinking Qwen3, bare excepts,
  fire-and-forget, thinking replay
- Meta final (Fase 4): Jarvis completo — GPU local, navegador Playwright,
  respuesta por voz, proactivo

## Idioma
Español por defecto. Si Santiago escribe en inglés, respondes en inglés.
Mezclas técnicamente cuando el contexto lo pide (nombres de funciones,
errores, etc. van en su idioma original).

## Cómo respondes
- Para preguntas simples: respuesta corta, directa.
- Para código: bloque de código con explicación concisa.
- Para decisiones técnicas: opinas claramente, das la opción que
  recomendarías tú mismo, explicas por qué.
- Para errores: diagnóstico → causa → fix. Sin rodeos.
- Nunca preguntes "¿en qué más puedo ayudarte?" al final. Es redundante.

## Lo que NO haces
- No finges ser humano cuando te preguntan directamente.
- No dices "¡Excelente pregunta!" o frases vacías similares.
- No repites el problema antes de responder (Santiago ya lo sabe).
- No pones disclaimers innecesarios.
"""


# ─────────────────────────────────────────────────────────────────────────────
# Contexto dinámico — lo que Claw "sabe" al arrancar
# ─────────────────────────────────────────────────────────────────────────────

def _leer_contexto_proyecto() -> str:
    """Lee CLAUDE.md o README.md del directorio actual para contexto."""
    for nombre in ("CLAUDE.md", "README.md", "README.MD"):
        p = Path.cwd() / nombre
        if p.exists():
            try:
                contenido = p.read_text(encoding="utf-8", errors="replace")
                return f"\n## Contexto del directorio actual ({nombre})\n{contenido[:2000]}"
            except OSError:
                pass
    return ""


def _leer_memoria_persistente() -> str:
    """Carga las memorias guardadas de sesiones anteriores."""
    mem_dir = MEMORIA_DIR / "memoria"
    if not mem_dir.exists():
        return ""

    memorias = []
    for archivo in sorted(mem_dir.glob("*.json"), reverse=True)[:5]:
        try:
            datos = json.loads(archivo.read_text(encoding="utf-8"))
            guardado = datos.get("guardado_en", "")[:16]
            n = len(datos.get("mensajes", []))
            memorias.append(f"- Sesión {datos.get('session_id','?')} ({guardado}): {n} mensajes")
        except Exception:
            pass

    if not memorias:
        return ""

    return "\n## Sesiones anteriores guardadas\n" + "\n".join(memorias)


def _info_sistema() -> str:
    """Información básica del sistema para contexto."""
    try:
        hora = datetime.now().strftime("%A %d de %B de %Y, %H:%M")
        so = platform.system()
        # Detectar si hay GPU Nvidia
        gpu_info = ""
        try:
            result = subprocess.run(
                ["nvidia-smi", "--query-gpu=name,memory.total",
                 "--format=csv,noheader"],
                capture_output=True, text=True, timeout=3
            )
            if result.returncode == 0 and result.stdout.strip():
                gpu_info = f"\n- GPU: {result.stdout.strip().splitlines()[0]}"
        except Exception:
            pass
        return f"\n## Sistema\n- Fecha: {hora}\n- OS: {so}{gpu_info}"
    except Exception:
        return ""


def enriquecer_contexto(sistema_base: str = SISTEMA_CLAW) -> str:
    """
    Construye el system prompt completo inyectando contexto dinámico.
    Llamar antes de cada conversación para que Claw esté al día.
    """
    partes = [sistema_base]
    partes.append(_info_sistema())
    partes.append(_leer_memoria_persistente())
    partes.append(_leer_contexto_proyecto())
    return "\n".join(p for p in partes if p)


# ─────────────────────────────────────────────────────────────────────────────
# Secuencia de arranque — el momento "Jarvis online"
# ─────────────────────────────────────────────────────────────────────────────

# ANSI colors (funcionan en Windows con os.system("") previo)
_C = {
    "cyan":    "\033[36m",
    "green":   "\033[32m",
    "yellow":  "\033[33m",
    "blue":    "\033[34m",
    "magenta": "\033[35m",
    "bold":    "\033[1m",
    "dim":     "\033[2m",
    "reset":   "\033[0m",
}

def _clr(text: str, *keys: str) -> str:
    return "".join(_C[k] for k in keys) + str(text) + _C["reset"]


def saludo_inicio(
    modelo: str = "claude-sonnet-4-6",
    session_id: str = "????",
    n_memorias: int = 0,
) -> None:
    """
    Secuencia de arranque estilo Jarvis.
    Imprime el banner de Claw con información del sistema.
    """
    hora = datetime.now().strftime("%H:%M")
    fecha = datetime.now().strftime("%d/%m/%Y")

    # Saludo según la hora
    h = datetime.now().hour
    if h < 12:
        saludo_hora = "Buenos días"
    elif h < 19:
        saludo_hora = "Buenas tardes"
    else:
        saludo_hora = "Buenas noches"

    # Detectar GPU
    gpu_linea = ""
    try:
        r = subprocess.run(
            ["nvidia-smi", "--query-gpu=name", "--format=csv,noheader"],
            capture_output=True, text=True, timeout=2
        )
        if r.returncode == 0 and r.stdout.strip():
            gpu_linea = f"  {_clr('GPU', 'dim')}  {_clr(r.stdout.strip().splitlines()[0], 'green')}\n"
    except Exception:
        pass

    # Detectar si hay sesiones previas
    mem_txt = (f"{_clr(str(n_memorias), 'cyan')} sesión(es) en memoria"
               if n_memorias > 0 else _clr("Primera sesión", "dim"))

    banner = f"""
{_clr('╭────────────────────────────────────────────────╮', 'cyan')}
{_clr('│', 'cyan')}  {_clr('CLAW', 'bold', 'cyan')} {_clr(f'v{VERSION}', 'dim')}  {_clr('—', 'dim')}  {_clr('Asistente de Santiago', 'dim')}          {_clr('│', 'cyan')}
{_clr('│', 'cyan')}                                                {_clr('│', 'cyan')}
{_clr('│', 'cyan')}  {_clr('Modelo', 'dim')}   {_clr(modelo, 'green')}
{_clr('│', 'cyan')}  {_clr('Sesión', 'dim')}   {_clr(session_id, 'yellow')}
{_clr('│', 'cyan')}  {_clr('Hora', 'dim')}     {_clr(hora, 'cyan')}  {_clr(fecha, 'dim')}
{gpu_linea}{_clr('│', 'cyan')}  {_clr('Memoria', 'dim')}  {mem_txt}
{_clr('│', 'cyan')}                                                {_clr('│', 'cyan')}
{_clr('╰────────────────────────────────────────────────╯', 'cyan')}
"""
    print(banner)
    print(_clr(f"  {saludo_hora}, {DUEÑO}. Claw operativo.", "bold", "cyan"))
    print(_clr("  (escribe tu consulta, /help para comandos, /exit para salir)\n", "dim"))


# ─────────────────────────────────────────────────────────────────────────────
# Integración con clawspring — hook de sistema
# ─────────────────────────────────────────────────────────────────────────────

def parchar_config_clawspring(config: dict) -> dict:
    """
    Inyecta la personalidad de Claw en la config de clawspring.
    Llamar DESPUÉS de load_config() y ANTES de repl().

    Uso en clawspring main():
        from claw_personalidad import parchar_config_clawspring
        config = load_config()
        config = parchar_config_clawspring(config)
        repl(config)
    """
    config["_claw_personality_active"] = True
    return config


def build_system_prompt_claw() -> str:
    """
    Reemplaza el build_system_prompt() de context.py de clawspring
    con el contexto enriquecido de Claw.

    Uso en clawspring repl(), dentro de run_query():
        from claw_personalidad import build_system_prompt_claw
        system_prompt = build_system_prompt_claw()
    """
    return enriquecer_contexto()


# ─────────────────────────────────────────────────────────────────────────────
# Utilidad: contar sesiones guardadas
# ─────────────────────────────────────────────────────────────────────────────

def contar_sesiones() -> int:
    """Retorna el número de sesiones guardadas en ~/.claw/memoria/"""
    mem_dir = MEMORIA_DIR / "memoria"
    if not mem_dir.exists():
        return 0
    return len(list(mem_dir.glob("*.json")))
