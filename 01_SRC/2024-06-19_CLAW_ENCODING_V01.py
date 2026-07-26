"""
encoding.py — Corrección Bug #4: UTF-8 en Windows
Llamar configure_encoding() al inicio de claw.py, ANTES de cualquier print().
"""
import sys
import os
import subprocess
import logging

logger = logging.getLogger(__name__)


def configure_encoding() -> None:
    """
    Garantiza UTF-8 en stdin/stdout/stderr y en subprocesos.
    En Windows: reconfigura los streams y ejecuta chcp 65001.
    En Linux/macOS: verifica que el locale sea UTF-8.
    """
    if sys.platform == "win32":
        _fix_windows_encoding()
    else:
        _check_unix_encoding()


def _fix_windows_encoding() -> None:
    """Aplica correcciones de encoding en Windows."""
    # 1. Reconfigura los streams principales de Python
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stderr, "reconfigure"):
        sys.stderr.reconfigure(encoding="utf-8", errors="replace")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8", errors="replace")

    # 2. Forzar UTF-8 para todos los subprocesos lanzados desde Claw
    os.environ["PYTHONIOENCODING"] = "utf-8"

    # 3. Cambiar la página de código de la consola Windows a UTF-8
    try:
        subprocess.run(
            ["chcp", "65001"],
            shell=True,
            capture_output=True,
            check=False,
        )
    except Exception as e:
        logger.warning(f"No se pudo ejecutar chcp 65001: {e}")

    logger.debug("Encoding Windows configurado a UTF-8")


def _check_unix_encoding() -> None:
    """Verifica encoding en Linux/macOS y avisa si no es UTF-8."""
    encoding = sys.stdout.encoding or ""
    if "utf" not in encoding.lower():
        logger.warning(
            f"El encoding del terminal es '{encoding}', se recomienda UTF-8. "
            "Exporta: PYTHONIOENCODING=utf-8 o LANG=es_ES.UTF-8"
        )


def safe_print(*args, **kwargs) -> None:
    """
    print() seguro: convierte a UTF-8 y reemplaza caracteres no soportados.
    Usar en lugar de print() para salida al usuario.
    """
    try:
        print(*args, **kwargs)
    except (UnicodeEncodeError, UnicodeDecodeError):
        # Fallback: codificar con replace
        text = " ".join(str(a) for a in args)
        safe_text = text.encode("utf-8", errors="replace").decode("utf-8")
        print(safe_text, **{k: v for k, v in kwargs.items() if k != "end"})
