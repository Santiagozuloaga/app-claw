# INFORME FASE 2 — CLAUDE 3
## Proyecto Claw / OpenClaw — 10 de mayo de 2026

---

## ESTADO AL FINAL DE ESTA SESIÓN

**OpenClaw está corriendo con:**
- Modelo activo: `viernes:latest` (qwen2.5:3b con system prompt en español integrado via Modelfile)
- Gateway: `C:\Users\Admin\.openclaw\gateway.cmd`
- WhatsApp conectado: +573197211133 (selfChatMode)
- 5 hooks activos: boot-md, bootstrap-extra-files, command-logger, compaction-notifier, session-memory

**Archivos de personalidad configurados:**
- `C:\Users\Admin\.openclaw\workspace\IDENTITY.md` — identidad de Viernes
- `C:\Users\Admin\.openclaw\workspace\USER.md` — contexto de Santiago
- `C:\Users\Admin\.openclaw\workspace\SOUL.md` — personalidad completa
- `C:\Users\Admin\.openclaw\workspace\BOOTSTRAP.md` — instrucción crítica de idioma

---

## LO QUE SE HIZO EN ESTA SESIÓN

### 1. Correcciones en clawspring.py y providers.py
- `except:` → `except Exception:` en función de nombres de agentes (Faker)
- `except Exception: pass` documentado en metadata label y readline history
- HTTP 500 retry en stream_ollama: reintenta sin think ni tools
- `think: false` en body Y en options para compatibilidad con versiones antiguas de Ollama

### 2. claw.bat fusionado
- `chcp 65001` para UTF-8
- `cd /d "%~dp0"` para funcionar desde acceso directo del escritorio
- Verifica Python, detecta venv, maneja errores con pause

### 3. Acceso directo en escritorio
- Comando PowerShell para crear `Claw.lnk` con WorkingDirectory correcto
- Sin el WorkingDirectory el .bat se cerraba solo — ese era el bug original

### 4. OpenClaw configurado
- `openclaw.json` reescrito limpio (el original se corrompió varias veces)
- contextWindow: 4096, maxTokens: 512 para qwen2.5
- WhatsApp allowlist con número de Santiago
- `reasoning: false` en todos los modelos de models.json y openclaw.json

### 5. Personalidad Viernes
- IDENTITY.md y USER.md escritos con UTF-8 via PowerShell (no Bloc de notas)
- BOOTSTRAP.md con instrucción en inglés (más efectiva para modelos entrenados en inglés)
- Modelfile de Ollama con system prompt integrado directamente en el modelo

### 6. Evolución de modelos probados
- qwen2.5:1.5b → respondía pero en inglés y con alucinaciones
- qwen2.5:3b → mejor calidad pero más lento
- viernes:latest (qwen2.5:3b + Modelfile) → solución final, español garantizado

---

## PROBLEMA PRINCIPAL NO RESUELTO

**`thinking=medium` hardcodeado en OpenClaw.**
No hay forma de cambiarlo desde ningún archivo de configuración. OpenClaw lo fuerza internamente para todos los modelos. Esto hace que cada respuesta tarde 7-15 minutos en CPU sin GPU.

**Solución definitiva:** RTX 3060 o superior (Fase 4). Con GPU el thinking=medium tarda ~10 segundos.

**Workaround actual:** El modelo `viernes:latest` via Modelfile no tiene thinking propio de Ollama, pero OpenClaw igual activa su thinking layer encima. La única mejora real es hardware.

---

## ESTADO DE WHATSAPP

WhatsApp SÍ funciona — los mensajes llegan al celular aunque tarden 7-10 minutos. El error "Something went wrong" que aparece en la conversación es el timeout de WhatsApp Web, pero la respuesta igual llega porque OpenClaw la reintenta cuando WhatsApp reconecta.

**Comportamiento esperado:**
1. Santiago manda mensaje
2. WhatsApp muestra "Something went wrong" a los 60 segundos
3. 5-10 minutos después llega la respuesta real

---

## PENDIENTE PARA PRÓXIMO CLAUDE

### Prioridad 1 — Verificar que viernes:latest responde en español
```powershell
# Verificar que el modelo existe
ollama list
# Debe mostrar viernes:latest

# Verificar openclaw.json usa viernes
Select-String "viernes" C:\Users\Admin\.openclaw\openclaw.json
```

### Prioridad 2 — Si sigue en inglés
El BOOTSTRAP.md tiene la instrucción. Si el modelo ignora:
- Editar el Modelfile con instrucción más fuerte
- Recrear con: `ollama create viernes -f "C:\Users\Admin\Modelfile"`

### Prioridad 3 — Fase 2 pendiente
- Whisper para transcribir clases (pip install openai-whisper, comando /voice)
- Tails/Cortana como segundo agente (después de estabilizar Viernes)

---

## INSTRUCCIONES PARA EL PRÓXIMO CLAUDE

**LEE ESTO PRIMERO antes de tocar cualquier archivo:**

1. **NO reescribas openclaw.json completo** — cada vez que lo hacemos se rompe algo. Edita solo lo necesario con PowerShell `-replace`.

2. **El thinking=medium NO se puede desactivar** — no pierdas tiempo buscando cómo. Es hardcodeado en OpenClaw. Acepta que las respuestas tardan y enfócate en calidad, no velocidad.

3. **Los archivos de workspace van en UTF-8** — NUNCA uses el Bloc de notas para guardar. Siempre usa:
   ```powershell
   [System.IO.File]::WriteAllText("ruta", $contenido, [System.Text.Encoding]::UTF8)
   ```

4. **Para reiniciar el gateway:**
   ```powershell
   # Ctrl+C en la ventana del gateway, luego:
   Start-Process powershell -ArgumentList "-NoExit", "-Command", "C:\Users\Admin\.openclaw\gateway.cmd"
   ```

5. **Santiago aprende rápido** — explícale qué hace cada comando, no solo se lo des, entiende más de lo que parece.

6. **El hardware es el límite real** — no prometas velocidades que el CPU no puede dar. Sea honesto con los tiempos de respuesta.

---

## RUTAS IMPORTANTES

| Archivo | Ruta |
|---------|------|
| Config principal | `C:\Users\Admin\.openclaw\openclaw.json` |
| Gateway | `C:\Users\Admin\.openclaw\gateway.cmd` |
| Workspace | `C:\Users\Admin\.openclaw\workspace\` |
| Models del agente | `C:\Users\Admin\.openclaw\agents\main\agent\models.json` |
| Logs | `C:\Users\Admin\AppData\Local\Temp\openclaw\` |
| Modelfile de Viernes | `C:\Users\Admin\Modelfile` |
| Claw completo | `C:\Users\Admin\Downloads\claw_completo\` |

---

*Informe generado por Claude 3 — Sesión del 10 de mayo de 2026*
*Próxima sesión: verificar viernes:latest en español, luego Whisper*
