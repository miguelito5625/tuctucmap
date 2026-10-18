---
name: tuctuc-git-sync
description: >-
  Guía y runbook especializado para control de versiones, preparación de commits semánticos y push automático a GitHub tras agregar o modificar funciones, pantallas, lógica o esquemas en cualquiera de los proyectos del monorepo (tuctucMap, tuctucMapDashboard, databaseTuctucApp).
---

# Skill: Sincronización y Push a GitHub (TucTuc Map)

Esta guía define el protocolo obligatorio para registrar cambios y sincronizar el repositorio local con GitHub cada vez que el usuario solicite crear, agregar o modificar funciones en cualquiera de los subsistemas del monorepo.

---

## 1. Activación Obligatoria

Este skill **DEBE activarse siempre** que se cumplan las siguientes condiciones:
1. El usuario solicitó agregar una nueva funcionalidad, componente o pantalla.
2. El usuario solicitó corregir, optimizar o modificar funciones existentes en `tuctucMap/`, `tuctucMapDashboard/` o `databaseTuctucApp/`.
3. Se realizaron cambios estructurales en esquemas SQL (`db.sql`), reglas de negocio o configuraciones.

---

## 2. Requisitos Previos al Commit (Garantía de Integridad)

Antes de ejecutar cualquier comando de Git, es **estrictamente obligatorio**:

1. **Compilación y Verificación Exitosa:**
   - Si se modificó la app Android (`tuctucMap/`): ejecutar `.\gradlew.bat assembleDebug` y verificar que finalice con `BUILD SUCCESSFUL`.
   - Si se modificó el dashboard Angular (`tuctucMapDashboard/`): ejecutar `npm run build` y verificar que compile sin errores de TypeScript ni de plantilla.
   - Si se modificó la base de datos (`databaseTuctucApp/`): garantizar que `databaseTuctucApp/db.sql` refleje fielmente el estado actual de tablas, funciones y políticas RLS.
2. **Inspección de Secretos y `.gitignore`:**
   - NUNCA agregar al commit credenciales vivas, archivos `.env`, `local.properties`, certificados `.jks` o claves privadas.
   - Revisar siempre el resultado de `git status -s` antes de hacer `git add`.
3. **Documentación al Día:**
   - Si la función altera la arquitectura, flujos o directrices operativas, actualizar la documentación correspondiente en `.agents/` o en los manuales del proyecto.

---

## 3. Protocolo de Ejecución Paso a Paso

### Paso 1: Inspeccionar el Estado de Git
Ejecutar desde la raíz del monorepo (`tuctucmap/`):
```powershell
git status -s
```
Identificar claramente qué archivos han sido modificados (`M`), agregados (`A` o `??`) o eliminados (`D`).

### Paso 2: Preparar los Archivos (Staging)
Agregar únicamente los archivos relevantes a la tarea realizada:
```powershell
# Opción recomendada: agregar rutas específicas de la funcionalidad
git add ruta/al/archivo1 ruta/al/archivo2

# O si todos los cambios pendientes corresponden a la tarea y están limpios de secretos:
git add .
```

### Paso 3: Crear el Commit Semántico
Redactar un mensaje de commit claro, profesional y descriptivo utilizando la convención semántica:
- `feat(<módulo>): <descripción>` (Nueva funcionalidad)
- `fix(<módulo>): <descripción>` (Corrección de error o bug)
- `refactor(<módulo>): <descripción>` (Reestructuración sin cambio de comportamiento)
- `docs(<módulo>): <descripción>` (Actualización de documentación o runbooks)
- `perf(<módulo>): <descripción>` (Optimización de rendimiento)

**Ejemplos:**
```powershell
git commit -m "feat(driver): agregar historial detallado de renovaciones de suscripcion"
git commit -m "fix(dashboard): resolver desalineacion de kpis en panel de cobranza"
git commit -m "feat(database): rpc y trigger para aprobacion automatica de membresias"
```

### Paso 4: Validar Rama y Sincronizar Remoto
Verificar la rama activa (por defecto `main`):
```powershell
git branch --show-current
```

Si pudieran existir cambios remotos recientes no descargados:
```powershell
git pull --rebase origin main
```

### Paso 5: Enviar Cambios a GitHub (Push)
Hacer el push a la rama correspondiente en GitHub:
```powershell
git push origin main
```

Verificar que la salida confirme el envío exitoso hacia `https://github.com/miguelito5625/tuctucmap`.

---

## 4. Comunicación con el Usuario

Al completar la tarea, el agente debe incluir en su resumen:
1. **Archivos confirmados:** Lista breve de archivos incluidos en el commit.
2. **Mensaje del commit:** Texto exacto del commit realizado.
3. **Confirmación de Push:** Hash del commit o confirmación de que la rama `main` en GitHub fue actualizada exitosamente.
