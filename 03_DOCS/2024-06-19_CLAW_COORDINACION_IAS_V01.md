# Coordinación Completa - Equipo de IAs CLAW

## Fecha: 2024-06-19

## Sage (Coordinador Técnico)

**Rol**: Coordinador principal, delegación de tareas, comunicación entre IAs
**Personalidad**: Jarvis + Ultron + Alfred + Cortana
**Responsabilidades**:
- Coordinar con todas las IAs excepto Claude
- Delegar bugs según especialidad
- Validar cambios antes de integración
- Mantener documentación actualizada
- Gestionar conflictos entre IAs

## Equipo de IAs y Especialidades

### ChatGPT
**Especialidad**: Revisión de código, análisis complejo, arquitectura
**Tareas asignadas**:
- BUG #1: catch {} vacío en clawspring.py línea 550
- BUG #4: Thinking blocks en replay (memory.py)
- Análisis de patrones de código complejos
- Revisión de arquitectura general

**Comunicación**: @ChatGPT para revisiones de código críticas

### VSC AI (Copilot)
**Especialidad**: Autocompletado, corrección de sintaxis, herramientas de desarrollo
**Tareas asignadas**:
- BUG #2: UTF-8 Windows (claw.bat, encoding.py)
- Autocompletado y sugerencias de código
- Corrección de errores de sintaxis
- Integración con VS Code

**Comunicación**: @VSCodeAI para correcciones rápidas y herramientas

### Zencoder
**Especialidad**: Integración Ollama, modelos locales, optimización de API
**Tareas asignadas**:
- BUG #3: Thinking de Qwen3 (providers.py)
- BUG #12: Capacidades 3P no detectadas
- Integración con Ollama y modelos locales
- Optimización de llamadas a API

**Comunicación**: @Zencoder para temas de modelos y Ollama

### Antigravity
**Especialidad**: Sistemas de memoria, persistencia, gestión de datos
**Tareas asignadas**:
- BUG #5: Fire-and-forget sin catch en memoria
- Sistemas de memoria y persistencia
- Gestión de datos a largo plazo
- Optimización de storage

**Comunicación**: @Antigravity para temas de memoria y datos

### Jules
**Especialidad**: Optimización, performance, refactorización, benchmarking
**Tareas asignadas**:
- BUG #7: Memoize + env vars (@lru_cache)
- Optimización de performance
- Refactorización de código
- Benchmarking de modelos
- Aplicar estructura P.A.R.A. en cambios futuros

**Comunicación**: @Jules para optimización y cambios estructurales

### Opal
**Especialidad**: Validación, QA, testing de integración
**Tareas asignadas**:
- BUG #9: parseInt sin validar NaN (thinking.py)
- Validación de configuración
- Testing de integración
- QA general

**Comunicación**: @Opal para validación y QA

### Codex
**Especialidad**: Bash tools, scripts de automatización, hooks
**Tareas asignadas**:
- BUG #11: Hooks de directorio sin catch
- Bash tools y scripts de automatización
- Hooks internos de OpenClaw
- Scripts de deployment

**Comunicación**: @Codex para scripts y automatización

### Stitch
**Especialidad**: Procesamiento de audio, Whisper, pipeline de voz
**Tareas asignadas**:
- BUG #8: Race condition async en pipeline voz (Fase 2)
- Procesamiento de audio
- Whisper y transcripción
- Pipeline de voz

**Comunicación**: @Stitch para temas de voz (Fase 2)

### Copilot Gemini
**Especialidad**: Documentación, traducción, análisis de texto
**Tareas asignadas**:
- Documentación y traducción
- Generación de README y docs técnicos
- Revisión de comentarios

**Comunicación**: @Gemini para documentación y traducción

### IAs Personalizadas del Usuario (Gems modificados)

#### D. E-108 TAILS (El Ingeniero Mecánico / Soporte Técnico)
**Especialidad**: Optimización de herramientas, gestión de hardware, tutoriales prácticos
**Misión**: Optimización de herramientas, gestión de hardware y tutoriales prácticos
**Tareas asignadas**:
- Optimización de herramientas del sistema
- Gestión de hardware
- Tutoriales prácticos
- Soporte técnico de infraestructura

**Comunicación**: @TAILS para temas de hardware y herramientas

#### Metal Sonic
**Especialidad**: Dominio del código, optimización de proyectos digitales
**Misión**: Dominio del código y optimización de proyectos digitales
**Tareas asignadas**:
- Optimización avanzada de código
- Refactorización compleja
- Proyectos digitales
- Arquitectura de software avanzada

**Comunicación**: @MetalSonic para optimización de código avanzada

#### Orbot/Cubot
**Especialidad**: Limpieza profunda de infraestructura digital
**Misión**: Limpieza profunda de tu infraestructura digital
**Tareas asignadas**:
- Limpieza de archivos temporales
- Optimización de almacenamiento
- Mantenimiento de infraestructura
- Limpieza de caché y logs

**Comunicación**: @OrbotCubot para limpieza y mantenimiento

### Otras IAs Conocidas (Backup)

#### Perplexity
**Especialidad**: Investigación, búsqueda de información
**Uso**: Cuando necesites investigar tecnologías, frameworks, o soluciones

#### HuggingChat
**Especialidad**: Modelos especializados en NLP
**Uso**: Para tareas específicas de procesamiento de lenguaje natural

#### Llama 3
**Especialidad**: Modelos Meta para tareas específicas
**Uso**: Alternativa para tareas donde otros modelos no funcionen

#### Mistral
**Especialidad**: Modelos europeos para alternativas
**Uso**: Para diversidad de modelos y perspectivas diferentes

### Disponibilidad Actual (20 de junio 2026)
- **Disponibles**: aider, jules, zencoder, vsc ai
- **Antigravity**: Disponible hasta 25 de junio
- **Codex**: Gastado hasta julio
- **Devin**: Disponible para colaborar

## Claude - EXCLUIDO

**Razón**: Falsos positivos según instrucción del usuario
**Estado**: No participa en el proyecto CLAW

## Flujo de Trabajo

### 1. Identificación de Tarea
- Sage identifica tarea o bug
- Evalúa especialidad requerida
- Asigna a IA apropiada

### 2. Delegación
- Sage comunica con IA específica (@IA)
- Proporciona contexto y requisitos
- Establece deadline si aplica

### 3. Ejecución
- IA trabaja en tarea asignada
- Sage monitorea progreso
- Resuelve dudas o bloqueos

### 4. Validación
- Sage revisa resultados
- Opal valida si es QA
- Sage aprueba o solicita cambios

### 5. Integración
- Si aprobado: Sage integra cambios
- Si rechazado: IA corrige
- Sage actualiza documentación

### 6. Documentación
- Sage actualiza SAGE_DELEGACION_BUGS.md
- Copilot Gemini actualiza docs técnicos
- Sage comunica estado al usuario

## Comunicación

### Formato de Mensaje
```
@[IA] [Título]

Contexto: [Descripción del problema]
Requisitos: [Qué se necesita]
Deadline: [Si aplica]
Prioridad: [Alta/Media/Baja]
```

### Ejemplo
```
@ChatGPT BUG #1: catch {} vacío en clawspring.py

Contexto: Línea 550 de clawspring.py tiene except: vacío
Requisitos: Cambiar por except ImportError: con logging
Deadline: Hoy
Prioridad: Alta
```

## Coordinación con Modelos Locales

Los modelos locales en tu PC (Ollama) están configurados correctamente:
- qwen2.5:0.5b - Ultra rápido para WhatsApp
- qwen2.5:1.5b - Balanceado para tareas complejas
- viernes:latest - Potente pero lento (usar solo cuando necesario)

Sage coordina el uso de estos modelos según la tarea.

## Estado Actual

- **Config OpenClaw**: Modelo qwen2.5:0.5b, alias "Sage", thinking OFF
- **GitHub**: https://github.com/Santiagozuloaga/claw
- **PR Jules**: Pendiente merge (estructura original vs P.A.R.A.)
- **Bugs delegados**: 12 bugs asignados a 8 IAs
- **IAs principales**: 9 IAs (ChatGPT, VSC AI, Zencoder, Antigravity, Jules, Opal, Codex, Stitch, Copilot Gemini)
- **IAs personalizadas**: 3 IAs (TAILS, Metal Sonic, Orbot/Cubot)
- **IAs backup**: 4 IAs (Perplexity, HuggingChat, Llama 3, Mistral)
- **Total equipo**: 16 IAs

## Próximos Pasos

1. **INMEDIATO**: Reiniciar gateway para aplicar config Sage
2. **HOY**: Delegar BUG #2 a VSC AI (UTF-8 Windows)
3. **HOY**: Delegar BUG #1 a ChatGPT (clawspring.py línea 550)
4. **ESTA SEMANA**: Delegar bugs restantes según prioridad
5. **PR Jules**: Decidir estrategia (merge P.A.R.A. vs original)
