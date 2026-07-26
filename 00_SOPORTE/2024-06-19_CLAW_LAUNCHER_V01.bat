@echo off
REM claw.bat — Lanzador de ClawSpring para Windows
REM Corrección Bug #4: UTF-8 en Windows
REM Doble clic para abrir

REM Activar UTF-8 en la consola
chcp 65001 > nul 2>&1

REM Activar UTF-8 para Python y subprocesos
set PYTHONIOENCODING=utf-8

REM Título de la ventana
title Claw — Asistente IA

REM Directorio del script
cd /d "%~dp0"

REM Verificar que Python esté instalado
python --version > nul 2>&1
if errorlevel 1 (
    echo ERROR: Python no encontrado.
    echo Instala Python desde https://python.org
    pause
    exit /b 1
)

REM Verificar que Ollama esté corriendo
curl -s http://localhost:11434/api/tags > nul 2>&1
if errorlevel 1 (
    echo Iniciando Ollama...
    start /B ollama serve
    timeout /t 3 > nul
)

REM Configurar API key para Ollama
set ANTHROPIC_API_KEY=ollama

REM Iniciar ClawSpring
python clawspring.py --model ollama/qwen3.5:latest %*

REM Si cierra con error, mostrar pausa
if errorlevel 1 (
    echo.
    echo Claw termino con error. Revisa los logs.
    pause
)
