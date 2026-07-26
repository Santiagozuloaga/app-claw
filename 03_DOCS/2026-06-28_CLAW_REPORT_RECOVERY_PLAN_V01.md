# PLAN DE RECUPERACIÓN — CLAW

## Fase A: Limpieza y Sincronización (Prioridad 1)
* **A1:** Unificar los fixes de Windows UTF-8 de `clawspring (1).py` en `2024-06-19_CLAW_CORE_V01.py`.
* **A2:** Eliminar carpetas duplicadas (mcp, memory, etc.) manteniendo únicamente las versiones ISO-SAGE.
* **A3:** Limpiar todos los archivos `__pycache__` y `.pyc`.

## Fase B: Corrección de Estructura e Imports (Prioridad 1)
* **B1:** Corregir `run_claw.py` para usar el nombre de módulo correcto.
* **B2:** Renombrar archivos con formato "Jules" (`CLAW_AAAA_...`) al formato ISO-SAGE estándar (`AAAA-MM-DD_CLAW_...`).
* **B3:** Actualizar todos los imports en los archivos de `01_SRC` para reflejar los nuevos nombres.

## Fase C: Validación Funcional (Prioridad 2)
* **C1:** Verificar la carga de todos los módulos mediante un script de importación masiva.
* **C2:** Ejecutar la suite de tests (`02_TESTS`) y corregir fallos específicos.
* **C3:** Probar el punto de entrada `run_claw.py` en un entorno con dependencias instaladas.

## Fase D: Optimización y Documentación (Prioridad 3)
* **D1:** Eliminar archivos "Shim" si ya no son necesarios.
* **D2:** Actualizar `README.md` con la nueva estructura final consolidada.

---
**Restricción de Ejecución:** Cada paso debe ser verificado individualmente. No ejecutar más de 10 cambios de archivos por tarea.
