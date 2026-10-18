---
name: tuctuc-playstore-release
description: >-
  Runbook especializado para compilar nuevos App Bundles (.aab), incrementar versionCode/versionName en Gradle, extraer commits desde la última modificación del paquete anterior y redactar las notas de versión para Google Play Console.
---

# Skill: Publicación y Generación de Versiones para Google Play Store

Este skill define el protocolo estandarizado y automatizado para generar un nuevo **Android App Bundle (`.aab`)** firmado, incrementar la versión de la app y redactar las **Notas de la versión** analizando los cambios en Git desde la última compilación.

---

## 1. Cuándo Activar este Skill

Este skill **DEBE activarse siempre** que:
1. El usuario indique o solicite subir una nueva versión a Google Play Console (Prueba Cerrada, Prueba Abierta o Producción).
2. El usuario pida actualizar el archivo `.aab`, compilar la versión de lanzamiento o preparar las notas de lanzamiento tras haber realizado cambios en la app.

---

## 2. Flujo de Ejecución Paso a Paso

### Paso 1: Determinar la Marca de Tiempo del Último `.aab`
Verificar la fecha y hora de la última modificación del archivo `app-release.aab` generado anteriormente:

```powershell
$aabPath = "tuctucMap\app\build\outputs\bundle\release\app-release.aab"
$lastAab = Get-Item $aabPath -ErrorAction SilentlyContinue

if ($lastAab) {
    $sinceTimestamp = $lastAab.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")
    Write-Host "Último AAB compilado el: $sinceTimestamp"
} else {
    Write-Host "No se encontró un AAB previo. Se analizarán los commits recientes."
}
```

---

### Paso 2: Extraer los Commits Realizados desde esa Fecha
Consultar en Git todos los commits registrados desde la fecha obtenida en el Paso 1:

```powershell
if ($sinceTimestamp) {
    git log --since="$sinceTimestamp" --pretty=format:"%h - %an, %ad : %s" --date=short
} else {
    # Fallback si no existe AAB previo: últimos 5 commits relevantes de Android
    git log -n 5 --pretty=format:"%h - %an, %ad : %s" --date=short -- tuctucMap/
}
```

*Nota:* Si hay cambios en el árbol de trabajo que aún no se han confirmado (`git status --short`), tenerlos en cuenta para la síntesis de novedades.

---

### Paso 3: Incrementar la Versión en `tuctucMap/app/build.gradle.kts`
Abrir `tuctucMap/app/build.gradle.kts` y localizar el bloque `defaultConfig`:

```kotlin
defaultConfig {
    applicationId = "com.tuctucmap.app"
    minSdk = 24
    targetSdk = 36
    versionCode = 4  // <-- Incrementar en +1 (ej: 5)
    versionName = "1.0.3" // <-- Incrementar parche (ej: "1.0.4") o menor según corresponda
    ...
}
```

**Reglas de versionamiento:**
* `versionCode`: Entero estrictamente secuencial y ascendente (requerido por Google Play para admitir el paquete).
* `versionName`: SemVer (`1.0.X` para correcciones y parches; `1.X.0` para nuevas funciones mayores).

---

### Paso 4: Compilar el Paquete Firmado Oficial (`.aab`)
Ejecutar la compilación del bundle de lanzamiento desde el directorio `tuctucMap/`:

```powershell
.\gradlew.bat bundleRelease
```

**Verificaciones de calidad obligatorias:**
1. La salida de Gradle debe terminar con `BUILD SUCCESSFUL`.
2. Verificar que `tuctucMap/app/build/outputs/bundle/release/app-release.aab` exista y tenga peso óptimo:
   ```powershell
   Get-Item "tuctucMap\app\build\outputs\bundle\release\app-release.aab" | Select-Object Name, Length, LastWriteTime
   ```
   *(Gracias a R8 y reducción de recursos, el peso esperado oscila entre 7 y 16 MB).*

---

### Paso 5: Redactar las Notas de Lanzamiento para Google Play Console
Sintetizar los commits obtenidos en el Paso 2 traduciéndolos a un lenguaje amigable y orientado al usuario final (conductores y pasajeros).

Estructura obligatoria de entrega:

#### A. Nombre de la versión
```text
1.0.X - [Resumen de la característica principal]
```
*(O simplemente la versión `1.0.X`)*

#### B. Notas de la versión (Formato Play Store)
```text
<es-419>
• [Novedad principal 1: ej. nuevo icono, animación o pantalla]
• [Mejora visual o de interacción: ej. optimización de mapa, gestos]
• [Corrección técnica o de rendimiento: ej. prevención de bloqueos ANR, fluidez]
• [Estabilidad general y seguridad del servicio]
</es-419>
```

---

### Paso 6: Sincronización con GitHub (Git Push)
Siguiendo las reglas de [`tuctuc-git-sync`](../tuctuc-git-sync/SKILL.md), registrar el incremento de versión y subirlo a la rama principal:

```powershell
git add tuctucMap/app/build.gradle.kts
git commit -m "chore(release): bump versionCode to [N] (1.0.X) for Play Store release"
git push origin main
```

---

## 3. Entregable al Usuario

Al finalizar la ejecución, el agente debe presentar de forma concisa:
1. Confirmación de compilación exitosa y tamaño del `.aab`.
2. Enlace clicable directo al archivo generado:
   `[tuctucMap/app/build/outputs/bundle/release/app-release.aab](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/build/outputs/bundle/release/app-release.aab)`
3. Los bloques de texto listos para copiar y pegar en Google Play Console:
   - **Nombre de la versión**
   - **Notas de la versión (`<es-419>`)**
