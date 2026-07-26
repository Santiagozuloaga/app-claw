"""
claw.py — Punto de entrada principal de Claw
Versión con todas las correcciones de bugs aplicadas.

INSTRUCCIONES DE INTEGRACIÓN:
Si ya tienes un claw.py existente, copia el bloque de INICIO SEGURO
(sección marcada abajo) al principio de tu archivo, antes de cualquier
otro código que use print(), logging, o la API.
"""

# =============================================================================
# ██ INICIO SEGURO — Copiar estas líneas al principio de claw.py existente ██
# =============================================================================

import sys
import os

# Corrección Bug #4: UTF-8 en Windows ANTES de cualquier print()
# Esto debe ser lo PRIMERO que se ejecuta
if sys.platform == "win32":
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8", errors="replace")
    os.environ["PYTHONIOENCODING"] = "utf-8"

# =============================================================================
# ██ FIN INICIO SEGURO ██
# =============================================================================

import asyncio
import logging
import signal
import uuid
from pathlib import Path
from typing import Optional

# Módulos de correcciones
from encoding import configure_encoding, safe_print
from thinking import construir_params_thinking, modelo_soporta_thinking
from session_memory import guardar_memoria_auto, cargar_sesion
from error_utils import tarea_segura, ejecutar_hook_seguro, TareaConCancelacion

# Aplicar configuración completa de encoding (incluye chcp 65001)
configure_encoding()

# Configurar logging
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    handlers=[
        logging.StreamHandler(sys.stdout),
        logging.FileHandler(
            Path.home() / ".claw" / "claw.log",
            encoding="utf-8",
            errors="replace",
        ),
    ],
)
logger = logging.getLogger("claw")


# =============================================================================
# Personalidad fija de Claw (Fase 1)
# =============================================================================

NOMBRE_CLAW = "Claw"
TONO_CLAW = "directo, técnico y amigable"
CONTEXTO_CLAW = """Eres Claw, un asistente de IA local desarrollado por Santiago.
Eres una versión Python de Claude Code con capacidades expandidas.
Respondes en español por defecto. Eres directo y técnico."""


# =============================================================================
# Clase principal de Claw
# =============================================================================

class Claw:
    """
    Asistente IA principal con todas las correcciones aplicadas.
    """

    def __init__(
        self,
        modelo: str = "claude-sonnet-4-6",
        api_key: Optional[str] = None,
    ):
        self.modelo = modelo
        self.api_key = api_key or os.environ.get("ANTHROPIC_API_KEY", "")
        self.session_id = str(uuid.uuid4())[:8]
        self.mensajes: list[dict] = []
        self._activo = True

        # Para Fase 2 (voz): cancelación segura
        self._pipeline_voz = TareaConCancelacion()

        safe_print(f"\n🤖 {NOMBRE_CLAW} iniciado — Sesión: {self.session_id}")
        safe_print(f"📦 Modelo: {self.modelo}")

        # Advertir si el modelo no soporta thinking
        if not modelo_soporta_thinking(self.modelo):
            safe_print(
                f"ℹ️  Thinking desactivado para '{self.modelo}' "
                "(modelo de tercero o sin soporte confirmado)"
            )

    async def iniciar(self) -> None:
        """Punto de entrada async principal."""
        # Cargar historial anterior si existe
        historial = await cargar_sesion(self.session_id)
        if historial:
            self.mensajes = historial
            safe_print(f"📂 Historial cargado: {len(self.mensajes)} mensajes")

        # Registrar manejador de cierre para auto-guardar
        self._registrar_manejador_cierre()

        # Bucle principal de conversación
        await self._bucle_conversacion()

    async def _bucle_conversacion(self) -> None:
        """Bucle principal de chat."""
        safe_print(f"\n{NOMBRE_CLAW}: ¡Hola! ¿En qué te ayudo? (escribe 'salir' para terminar)\n")

        while self._activo:
            try:
                # Corrección Bug #2: NO usar bare except aquí
                entrada = await asyncio.get_event_loop().run_in_executor(
                    None, lambda: input("Tú: ").strip()
                )
            except (EOFError, KeyboardInterrupt):
                safe_print(f"\n{NOMBRE_CLAW}: ¡Hasta luego!")
                break
            except Exception as e:
                # Corrección Bug #1: Loggear, nunca silenciar
                logger.warning(f"Error al leer entrada: {e}")
                continue

            if not entrada:
                continue
            if entrada.lower() in ("salir", "exit", "quit"):
                safe_print(f"\n{NOMBRE_CLAW}: ¡Hasta luego!")
                break

            # Enviar a la API y obtener respuesta
            respuesta = await self._consultar_api(entrada)
            if respuesta:
                safe_print(f"\n{NOMBRE_CLAW}: {respuesta}\n")

    async def _consultar_api(self, mensaje_usuario: str) -> Optional[str]:
        """
        Envía un mensaje a la API y retorna la respuesta.
        Incluye manejo de thinking según el modelo.
        """
        # Agregar mensaje del usuario
        self.mensajes.append({"role": "user", "content": mensaje_usuario})

        # Construir parámetros de thinking (Corrección Bug #3)
        thinking_config = {"type": "adaptive"}  # O leer de configuración
        params_thinking = construir_params_thinking(self.modelo, thinking_config)

        try:
            import anthropic

            client = anthropic.Anthropic(api_key=self.api_key)

            kwargs = {
                "model": self.modelo,
                "max_tokens": 8192,
                "system": CONTEXTO_CLAW,
                "messages": self.mensajes,
            }

            # Solo agregar thinking si el modelo lo soporta
            if params_thinking:
                kwargs["thinking"] = params_thinking

            response = client.messages.create(**kwargs)

            # Extraer texto de la respuesta
            texto_respuesta = ""
            for bloque in response.content:
                if bloque.type == "text":
                    texto_respuesta += bloque.text

            # Agregar respuesta al historial (sin thinking blocks para persistencia)
            self.mensajes.append({
                "role": "assistant",
                "content": [
                    {"type": "text", "text": texto_respuesta}
                ],
            })

            return texto_respuesta

        except Exception as e:
            # Corrección Bug #1: Loggear el error, no silenciarlo
            logger.error(f"Error de API: {type(e).__name__}: {e}")
            return f"[Error: {type(e).__name__}. Revisa los logs para más detalles.]"

    def _registrar_manejador_cierre(self) -> None:
        """Registra el guardado automático al cerrar."""
        async def _guardar_al_salir():
            safe_print(f"\n💾 Guardando sesión '{self.session_id}'...")
            await guardar_memoria_auto(self.session_id, self.mensajes)
            safe_print("✅ Sesión guardada.")

        # Registrar para Ctrl+C y señales de sistema
        loop = asyncio.get_event_loop()

        def _manejador_senal(*_):
            self._activo = False
            # Corrección Bug #6: tarea_segura en lugar de asyncio.create_task directo
            tarea_segura(
                _guardar_al_salir(),
                nombre="guardar_al_salir",
                nivel_error="error",
            )

        for sig in (signal.SIGINT, signal.SIGTERM):
            try:
                loop.add_signal_handler(sig, _manejador_senal)
            except (NotImplementedError, RuntimeError):
                # Windows no soporta add_signal_handler para todos los signals
                signal.signal(sig, _manejador_senal)


# =============================================================================
# Punto de entrada
# =============================================================================

async def main():
    claw = Claw(
        modelo=os.environ.get("CLAW_MODEL", "claude-sonnet-4-6"),
    )
    await claw.iniciar()


if __name__ == "__main__":
    asyncio.run(main())
