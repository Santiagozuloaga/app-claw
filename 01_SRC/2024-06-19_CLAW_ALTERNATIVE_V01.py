"""
claw.py — Punto de entrada de Claw, el asistente personal de Santiago.

Versión Jarvis:
  - Personalidad propia (ver claw_personalidad.py)
  - Memoria persistente entre sesiones
  - Arranque estilo Jarvis
  - Todos los bugs del original corregidos

Uso:
  python claw.py                    # modo interactivo
  python claw.py -m ollama/qwen3    # con modelo específico
  python claw.py "haz X"            # modo no interactivo (--print)
  claw.bat                          # doble clic en Windows

Variables de entorno:
  ANTHROPIC_API_KEY   — requerida para modelos Claude
  CLAW_MODEL          — modelo por defecto (ej: claude-sonnet-4-6)
  CLAW_DEBUG          — si es "1", activa logging detallado
"""

# =============================================================================
# ██ INICIO SEGURO — UTF-8 ANTES de cualquier import que use print/logging ██
# =============================================================================
import sys
import os

if sys.platform == "win32":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8", errors="replace")
    os.environ["PYTHONIOENCODING"] = "utf-8"
    os.system("")  # Activar ANSI en Windows CMD/PowerShell
# =============================================================================

import asyncio
import argparse
import logging
import signal
import uuid
from datetime import datetime
from pathlib import Path
from typing import Optional

# ── Módulos de correcciones (del otro Claude) ──────────────────────────────
from encoding   import configure_encoding, safe_print
from thinking   import construir_params_thinking, modelo_soporta_thinking
from memory     import guardar_memoria_auto, cargar_sesion, listar_sesiones
from error_utils import tarea_segura, TareaConCancelacion

# ── Personalidad de Claw ───────────────────────────────────────────────────
from claw_personalidad import (
    NOMBRE, DUEÑO, VERSION,
    saludo_inicio,
    enriquecer_contexto,
    contar_sesiones,
)

# ── Aplicar encoding completo (chcp 65001 en Windows) ─────────────────────
configure_encoding()

# ── Logging ────────────────────────────────────────────────────────────────
_log_dir = Path.home() / ".claw"
_log_dir.mkdir(parents=True, exist_ok=True)

_nivel_log = logging.DEBUG if os.environ.get("CLAW_DEBUG") == "1" else logging.INFO

logging.basicConfig(
    level=_nivel_log,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.FileHandler(_log_dir / "claw.log", encoding="utf-8", errors="replace"),
        # No StreamHandler para no mezclar logs con la UI de Claw
    ],
)
logger = logging.getLogger("claw")


# =============================================================================
# Clase principal — el "Jarvis" de Santiago
# =============================================================================

class Claw:
    """
    Asistente personal de Santiago.
    Un Jarvis/Cortana propio construido sobre Claude.
    """

    def __init__(
        self,
        modelo: str = "claude-sonnet-4-6",
        api_key: Optional[str] = None,
        modo_silencioso: bool = False,
    ):
        self.modelo        = modelo
        self.api_key       = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        self.session_id    = str(uuid.uuid4())[:8]
        self.mensajes: list[dict] = []
        self._activo       = True
        self._modo_silencioso = modo_silencioso

        # Para voz (Fase 2): cancelación de pipeline segura
        self._pipeline_voz = TareaConCancelacion()

        # Verificar API key
        if not self.api_key:
            safe_print(
                f"\n⚠️  ANTHROPIC_API_KEY no configurada.\n"
                f"   Exporta: export ANTHROPIC_API_KEY=sk-ant-...\n"
                f"   O ponla en ~/.claw/.env\n"
            )

        # Mostrar banner de arranque (solo en modo interactivo)
        if not modo_silencioso:
            n_ses = contar_sesiones()
            saludo_inicio(
                modelo=self.modelo,
                session_id=self.session_id,
                n_memorias=n_ses,
            )

        # Avisar si el modelo no soporta thinking (informativo, no error)
        if not modelo_soporta_thinking(self.modelo) and not modo_silencioso:
            safe_print(
                f"  ℹ️  Thinking extendido no disponible para '{self.modelo}'\n"
                f"     (solo modelos Claude claude-sonnet-4 / opus-4)\n"
            )

        logger.info(
            f"Claw iniciado — sesión={self.session_id} modelo={self.modelo}"
        )

    # ── Arranque ──────────────────────────────────────────────────────────

    async def iniciar(self) -> None:
        """Punto de entrada principal. Carga historial y entra al loop."""
        await self._cargar_historial()
        self._registrar_manejador_cierre()
        await self._bucle_conversacion()

    async def _cargar_historial(self) -> None:
        """Intenta cargar la última sesión guardada."""
        # Buscar la sesión más reciente
        mem_dir = Path.home() / ".claw" / "memoria"
        if not mem_dir.exists():
            return

        archivos = sorted(mem_dir.glob("*.json"), reverse=True)
        if not archivos:
            return

        # Cargar la sesión más reciente
        try:
            import json
            datos = json.loads(archivos[0].read_text(encoding="utf-8"))
            session_anterior = datos.get("session_id", "")
            mensajes = await cargar_sesion(session_anterior)
            if mensajes:
                self.mensajes = mensajes
                safe_print(
                    f"  📂 Historial cargado: {len(self.mensajes)} mensajes "
                    f"de sesión {session_anterior}\n"
                )
        except Exception as e:
            logger.warning(f"No se pudo cargar historial: {e}")

    # ── Loop de conversación ──────────────────────────────────────────────

    async def _bucle_conversacion(self) -> None:
        """Loop principal — lee input, consulta API, imprime respuesta."""
        while self._activo:
            try:
                entrada = await asyncio.get_event_loop().run_in_executor(
                    None,
                    lambda: input(f"  {DUEÑO} → ").strip()
                )
            except (EOFError, KeyboardInterrupt):
                await self._despedirse()
                break
            except Exception as e:
                # Corrección Bug #1: loggear, nunca silenciar
                logger.warning(f"Error leyendo input: {e}")
                continue

            if not entrada:
                continue

            # Comandos especiales
            if entrada.lower() in ("/exit", "/quit", "salir", "exit", "quit"):
                await self._despedirse()
                break

            if entrada.lower() in ("/memoria", "/sesiones"):
                self._mostrar_sesiones()
                continue

            if entrada.lower() == "/limpiar":
                self.mensajes.clear()
                safe_print(f"\n  {NOMBRE}: Historial limpiado.\n")
                continue

            if entrada.lower() == "/ayuda":
                self._mostrar_ayuda()
                continue

            # Consulta normal
            respuesta = await self._consultar_api(entrada)
            if respuesta:
                self._imprimir_respuesta(respuesta)

    # ── Consulta a la API ─────────────────────────────────────────────────

    async def _consultar_api(self, mensaje: str) -> Optional[str]:
        """Envía el mensaje a Claude y retorna la respuesta en texto."""
        self.mensajes.append({"role": "user", "content": mensaje})

        # Sistema con personalidad + contexto dinámico (Bug #3 integrado)
        sistema = enriquecer_contexto()

        # Thinking seguro — solo para modelos que lo soportan (Bug #3)
        thinking_config = {"type": "enabled", "budgetTokens": 8000}
        params_thinking = construir_params_thinking(self.modelo, thinking_config)

        try:
            import anthropic
            client = anthropic.Anthropic(api_key=self.api_key)

            kwargs: dict = {
                "model":      self.modelo,
                "max_tokens": 8192,
                "system":     sistema,
                "messages":   self.mensajes,
            }
            if params_thinking:
                kwargs["thinking"] = params_thinking

            safe_print(f"\n  {NOMBRE} → ", end="", flush=True)

            response = client.messages.create(**kwargs)

            # Extraer texto (ignorar thinking blocks para la salida)
            texto = ""
            for bloque in response.content:
                if bloque.type == "text":
                    texto += bloque.text

            # Guardar solo texto en historial (Bug #12: sin thinking blocks)
            self.mensajes.append({
                "role": "assistant",
                "content": [{"type": "text", "text": texto}],
            })

            logger.debug(
                f"Respuesta: {response.usage.input_tokens} in / "
                f"{response.usage.output_tokens} out tokens"
            )

            return texto

        except Exception as e:
            # Bug #1: siempre loggear, mostrar al usuario sin crashear
            logger.error(f"Error de API: {type(e).__name__}: {e}")
            return f"[Error al conectar con la API: {type(e).__name__}. Revisa ~/.claw/claw.log]"

    # ── Formato de respuesta ──────────────────────────────────────────────

    def _imprimir_respuesta(self, texto: str) -> None:
        """Imprime la respuesta de Claw con formato."""
        # Limpiar el "Claw → " del indicador de carga
        # (ya fue impreso por _consultar_api antes de llamar a la API)
        print("\r" + " " * 60 + "\r", end="")  # Limpiar línea de espera

        # Intentar usar rich para markdown si está instalado
        try:
            from rich.console import Console
            from rich.markdown import Markdown
            console = Console()
            print()
            console.print(Markdown(texto))
            print()
        except ImportError:
            # Fallback a texto plano
            safe_print(f"\n{texto}\n")

    # ── Despedida y guardado ──────────────────────────────────────────────

    async def _despedirse(self) -> None:
        """Guarda la sesión y se despide."""
        if self.mensajes:
            safe_print(f"\n  💾 Guardando sesión {self.session_id}...")
            exito = await guardar_memoria_auto(self.session_id, self.mensajes)
            if exito is not None:
                safe_print("  ✅ Sesión guardada.")
        safe_print(f"\n  {NOMBRE}: Hasta luego, {DUEÑO}. 👋\n")

    def _registrar_manejador_cierre(self) -> None:
        """Guarda la sesión ante Ctrl+C o señal de sistema."""
        async def _guardar():
            safe_print(f"\n  💾 Guardando...")
            await guardar_memoria_auto(self.session_id, self.mensajes)
            safe_print(f"  ✅ Listo. Hasta luego, {DUEÑO}.")

        loop = asyncio.get_event_loop()

        def _handler(*_):
            self._activo = False
            # Bug #6: tarea_segura en lugar de create_task directo
            tarea_segura(_guardar(), nombre="guardar_al_salir", nivel_error="error")

        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, _handler)
            except (NotImplementedError, RuntimeError):
                signal.signal(sig, _handler)

    # ── Comandos internos ─────────────────────────────────────────────────

    def _mostrar_sesiones(self) -> None:
        sesiones = listar_sesiones()
        if not sesiones:
            safe_print(f"\n  {NOMBRE}: No hay sesiones guardadas aún.\n")
            return
        safe_print(f"\n  📋 Sesiones guardadas ({len(sesiones)}):\n")
        for s in sesiones[:10]:
            safe_print(
                f"    {s['session_id']}  "
                f"{s['guardado_en'][:16]}  "
                f"{s['num_mensajes']} mensajes"
            )
        print()

    def _mostrar_ayuda(self) -> None:
        safe_print(f"""
  ╭─ Comandos de Claw ───────────────────────────────╮
  │  /ayuda      Esta pantalla                       │
  │  /memoria    Ver sesiones guardadas              │
  │  /limpiar    Borrar historial actual             │
  │  /exit       Salir y guardar sesión              │
  │                                                  │
  │  Simplemente escribe tu consulta y Enter.        │
  │  Claw recuerda el contexto de la conversación.  │
  ╰──────────────────────────────────────────────────╯
""")


# =============================================================================
# Modo no interactivo (--print) — para scripts y pipes
# =============================================================================

async def modo_print(prompt: str, modelo: str) -> None:
    """Ejecuta un único prompt y sale. Para uso en scripts."""
    claw = Claw(modelo=modelo, modo_silencioso=True)
    respuesta = await claw._consultar_api(prompt)
    if respuesta:
        print(respuesta)


# =============================================================================
# Punto de entrada
# =============================================================================

async def main() -> None:
    parser = argparse.ArgumentParser(
        prog="claw",
        description=f"Claw v{VERSION} — Asistente personal de {DUEÑO}",
    )
    parser.add_argument(
        "prompt", nargs="*",
        help="Prompt inicial (modo no interactivo con --print)"
    )
    parser.add_argument(
        "-p", "--print", dest="print_mode", action="store_true",
        help="Modo no interactivo: ejecuta el prompt y sale"
    )
    parser.add_argument(
        "-m", "--model", default=os.environ.get("CLAW_MODEL", "claude-sonnet-4-6"),
        help="Modelo a usar (ej: claude-opus-4-6, ollama/qwen3)"
    )
    parser.add_argument(
        "--version", action="store_true",
        help="Muestra la versión"
    )

    args = parser.parse_args()

    if args.version:
        print(f"Claw v{VERSION}")
        return

    if args.print_mode:
        prompt = " ".join(args.prompt)
        if not prompt:
            print("Error: --print requiere un prompt.", file=sys.stderr)
            sys.exit(1)
        await modo_print(prompt, args.model)
        return

    # Modo interactivo (el normal)
    claw = Claw(modelo=args.model)
    await claw.iniciar()


if __name__ == "__main__":
    asyncio.run(main())
