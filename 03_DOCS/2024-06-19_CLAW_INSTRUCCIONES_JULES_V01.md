# Instrucciones para Jules - Estándar P.A.R.A.

## Fecha: 2024-06-19

## Organización P.A.R.A. Requerida

Todo trabajo en CLAW debe seguir esta estructura:

```
CLAW_FINAL/
├── 00_SOPORTE/          # Logs, configuraciones, .env
├── 01_SRC/              # Código lógico (separado por módulos)
├── 02_TESTS/            # Pruebas automatizadas (imprescindible)
├── 03_DOCS/             # Documentación (markdown o diagramas)
├── 04_ASSETS/           # Recursos estáticos
├── .clinerules          # Reglas de programación E-SYSTEM
└── README.md            # Guía de estilo y ejecución
```

## Nomenclatura ISO-SAGE

Todo archivo nuevo debe seguir:
`[AAAA-MM-DD]_[PROYECTO]_[DESCRIPCIÓN]_V[XX].[ext]`

Ejemplos:
- `2024-06-19_CLAW_CLAWSPRING_CORE_V01.py`
- `2024-06-19_CLAW_PROVIDERS_V01.py`
- `2024-06-19_CLAW_MEMORY_V01.py`

## Reglas de Programación

- **Lenguaje**: Python 3.12+
- **Shell**: Windows PowerShell (todos los comandos deben ser PowerShell)
- **Estilo**: Limpio, comentado bajo ISO-SAGE
- **Jerarquía**: Siempre aplicar P.A.R.A.
- **Independencia**: Nunca mezclar lógica de negocio con configuración de entorno
  - Configuración → 00_SOPORTE/
  - Lógica de negocio → 01_SRC/

## Comandos PowerShell

Todos los comandos deben ser PowerShell:
- Usar `cd` sin comillas para cambiar directorio
- Usar `Get-ChildItem` en lugar de `ls`
- Usar `Remove-Item` en lugar de `rm`
- Usar `Copy-Item` en lugar de `cp`
- Usar `Move-Item` en lugar de `mv`
- Usar backticks `` ` `` para continuar líneas largas

## Seguridad Física

Si el script supera los 50 minutos de ejecución, añade un log de 'alerta de enfriamiento'.

## Coordinación con IAs

Sage coordina con:
- ChatGPT (revisión código, bugs críticos)
- VSC AI (corrección lanzadores, encoding)
- Zencoder (integración Ollama, capacidades)
- Antigravity (sistemas memoria)
- Jules (optimización, performance)
- Opal (validación configuración)
- Codex (bash tools, hooks)
- Stitch (pipeline voz - Fase 2)

Claude está excluido del juego.

## Instrucciones Específicas para Jules

**@jules**: Cuando trabajes en CLAW:

1. **Siempre aplica P.A.R.A.** - Usa 00_SOPORTE, 01_SRC, 02_TESTS, 03_DOCS, 04_ASSETS
2. **Usa nomenclatura ISO-SAGE** - Renombra archivos con formato [AAAA-MM-DD]_[PROYECTO]_[DESCRIPCIÓN]_V[XX].[ext]
3. **Lee .clinerules** - Contiene todas las reglas E-SYSTEM
4. **Separa configuración de lógica** - Config en 00_SOPORTE, código en 01_SRC
5. **Incluye README.md** - Explica cómo ejecutar según P.A.R.A.

## Estado Actual

- CLAW_FINAL está organizado según P.A.R.A.
- GitHub: https://github.com/Santiagozuloaga/claw
- PR Jules usa estructura original (conflicto)
- Config OpenClaw: Modelo qwen2.5:0.5b, alias "Sage", thinking OFF
- System prompt eliminado (no soportado por OpenClaw 2026.5.6)

## Siguiente Paso

@jules: Por favor aplica correcciones Python 3.12 manteniendo estructura P.A.R.A. y nomenclatura ISO-SAGE.
