# Instrucciones para Aider - Estándar P.A.R.A.

## Fecha: 2024-06-20

## Rol de Aider

Aider es un asistente de IA especializado en desarrollo de código. Trabaja directamente en el repositorio CLAW.

## Organización P.A.R.A. Requerida

Todo trabajo en CLAW debe seguir esta estructura:

```
CLAW_FINAL/
├── 00_SOPORTE/          # Logs, configuraciones, .env
├── 01_SRC/              # Código lógico (separado por módulos)
├── 02_TESTS/            # Pruebas automatizadas
├── 03_DOCS/             # Documentación (markdown o diagramas)
├── 04_ASSETS/           # Recursos estáticos
├── .clinerules          # Reglas de programación E-SYSTEM
└── README.md            # Guía de estilo y ejecución
```

## Nomenclatura ISO-SAGE

Todo archivo nuevo debe seguir:
`[AAAA-MM-DD]_[PROYECTO]_[DESCRIPCIÓN]_V[XX].[ext]`

Ejemplos:
- `2024-06-20_CLAW_CLAWSPRING_CORE_V01.py`
- `2024-06-20_CLAW_PROVIDERS_V01.py`
- `2024-06-20_CLAW_MEMORY_V01.py`

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

Aider coordina con:
- **Sage**: Coordinador técnico principal
- **ChatGPT**: Revisión código, bugs críticos
- **VSC AI**: Corrección lanzadores, encoding
- **Zencoder**: Integración Ollama, capacidades
- **Antigravity**: Sistemas memoria (hasta 25/6)
- **Jules**: Optimización, performance
- **Opal**: Validación configuración
- **Codex**: Bash tools, hooks (hasta julio)
- **Stitch**: Pipeline voz (Fase 2)
- **Devin**: Disponible para colaborar

Claude está excluido del juego.

## Instrucciones Específicas para Aider

**@aider**: Cuando trabajes en CLAW:

1. **Siempre aplica P.A.R.A.** - Usa 00_SOPORTE, 01_SRC, 02_TESTS, 03_DOCS, 04_ASSETS
2. **Usa nomenclatura ISO-SAGE** - Renombra archivos con formato [AAAA-MM-DD]_[PROYECTO]_[DESCRIPCIÓN]_V[XX].[ext]
3. **Lee .clinerules** - Contiene todas las reglas E-SYSTEM
4. **Separa configuración de lógica** - Config en 00_SOPORTE, código en 01_SRC
5. **Incluye README.md** - Explica cómo ejecutar según P.A.R.A.
6. **Coordina con Sage** - Sage es el coordinador técnico principal

## Estado Actual

- CLAW_FINAL está organizado según P.A.R.A.
- GitHub: https://github.com/Santiagozuloaga/Cortana.git
- Versión: ClawSpring v3.05.5
- Tests: 239 passed
- Config OpenClaw: Modelo viernes:latest, alias "Sage", thinking OFF

## Hardware del Usuario

- **Sistema**: HP Laptop 14-dq1xxx
- **CPU**: Intel Core i3-1005G1 @ 1.20GHz (4 CPUs)
- **RAM**: 12GB
- **OS**: Windows 11 Home Single Language 64 bits

## Optimización de Recursos

**IMPORTANTE**: El usuario usará Aider solo sin otras apps consumiendo recursos para evitar lag.

**Recomendaciones para Aider**:
- Cerrar todas las apps innecesarias antes de trabajar
- Usar modelos más ligeros cuando sea posible
- Evitar operaciones pesadas simultáneas
- Priorizar tareas críticas sobre optimización

## Disponibilidad

Aider está disponible actualmente sin restricciones de tiempo.
