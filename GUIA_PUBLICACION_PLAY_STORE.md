# Guía Maestra de Publicación en Google Play Store - TucTuc Map

Esta guía contiene la hoja de ruta completa, datos técnicos de firma y los textos exactos para copiar y pegar en los formularios de Google Play Console.

---

## 1. Resumen de Ajustes Técnicos Realizados en el Proyecto

| Elemento | Estado Anterior | Estado Actual (Configurado) |
| :--- | :--- | :--- |
| **Application ID** | `com.example.tuctucmap` *(rechazado por Google)* | **`com.tuctucmap.app`** *(listo y válido)* |
| **Almacén de Claves (Keystore)** | Solo firmas temporales `debug` | **`tuctucmap-release.jks`** generado y configurado en `build.gradle.kts` |
| **Seguridad de Claves** | N/A | `keystore.properties` protegido y añadido a `.gitignore` |
| **Firebase (`google-services.json`)** | Solo `com.example.tuctucmap` | Soporte multicliente incluyendo **`com.tuctucmap.app`** |
| **Política de Privacidad** | Inexistente | Creado [`POLITICA_PRIVACIDAD.md`](./POLITICA_PRIVACIDAD.md) con formato legal Play Store |

---

## 2. Datos del Almacén de Claves de Producción (*Keystore*)

> [!CAUTION]
> **NUNCA pierdas el archivo `tuctucmap-release.jks` ni su contraseña.** Si lo pierdes, no podrás publicar actualizaciones de la aplicación en Google Play Store. Guárdalo en un respaldo seguro (Google Drive, disco externo, etc.).

* **Ubicación:** `tuctucMap/tuctucmap-release.jks`
* **Alias de la clave:** `tuctucmap-key`
* **Contraseña del almacén (Storepass):** `TucTuc2026PlayStore!`
* **Contraseña de la clave (Keypass):** `TucTuc2026PlayStore!`
* **Validez:** Hasta el año 2054 (10,000 días)
* **Huella Digital SHA-1:**
  ```text
  93:FC:E2:DB:DB:23:49:25:5B:E0:53:26:8A:52:8D:CC:FB:3D:7B:59
  ```
* **Huella Digital SHA-256:**
  ```text
  CE:E3:A9:CF:DB:C5:17:3C:FF:A9:8D:6A:7C:6B:62:20:BA:A4:E1:4B:8A:E0:8F:9C:B0:65:79:B7:D3:24:4A:9F
  ```

*(Nota: La huella SHA-1 debes registrarla en tu consola de Firebase en la sección "Configuración del proyecto" -> "Tus apps" -> "Agregar huella digital", para asegurar compatibilidad con Google Sign-In).*

---

## 3. Generación del Paquete de Producción (`.aab`)

Google Play Store **no acepta archivos `.apk`** para nuevas apps, exige el formato **`.aab` (Android App Bundle)**.

Para generarlo siempre que hagas una actualización, ejecuta en la terminal dentro de `tuctucMap/`:
```powershell
.\gradlew.bat bundleRelease
```
El archivo firmado y listo para subir a la consola se genera en:
`tuctucMap/app/build/outputs/bundle/release/app-release.aab`

---

## 4. Respuestas para los Formularios de Google Play Console

Al entrar en tu app en [Google Play Console](https://play.google.com/console), ve al menú lateral izquierdo: **"Contenido de la app"** (o "Configuración de la app"). Debes completar los siguientes puntos:

### 4.1 Política de Privacidad
* **URL de la política de privacidad:** Debes ingresar un enlace público accesible donde esté subido el contenido de [`POLITICA_PRIVACIDAD.md`](./POLITICA_PRIVACIDAD.md).
  * *Opciones gratuitas:* Puedes alojarlo en GitHub Pages, un Gist público de GitHub, una página pública de Notion, o el dominio de tu servidor.

### 4.2 Acceso a la Aplicación
* Selecciona: **"Algunas funciones tienen restricciones"** (debido a que requiere inicio de sesión).
* Agrega credenciales de prueba para que los revisores de Google puedan probar la app:
  * **Nombre de credencial 1:** Pasajero Demo
    - Usuario: `demo_pasajero@tuctucmap.com`
    - Contraseña: `TuPassword123!`
  * **Nombre de credencial 2:** Conductor Demo
    - Usuario: `demo_conductor@tuctucmap.com`
    - Contraseña: `TuPassword123!`
  * Instrucciones: *"La app permite iniciar sesión como pasajero o conductor para solicitar y gestionar viajes de toritos/mototaxis locales."*

### 4.3 Anuncios
* Selecciona: **"No, mi app no contiene anuncios"**.

### 4.4 Clasificación de Contenido
* Responde el cuestionario con tu correo.
* Categoría: **"Utilidad, productividad, comunicación u otro"**.
* Responde "No" a violencia, contenido para adultos, lenguaje inapropiado y apuestas.
* Resultado esperado: **Apta para todo público (PEGI 3 / Para todos)**.

### 4.5 Público Objetivo y Contenido
* Grupo de edad: **18 años o más** (o selecciona 13-17 y 18+).
* ¿La app puede atraer a niños involuntariamente?: **No**.

### 4.6 Apps de Noticias y COVID-19
* Noticias: **No**.
* Rastreo de contactos y COVID-19: **Mi app no es una app de rastreo de contactos o estado de salud de COVID-19**.

### 4.7 Declaración de Servicio en Primer Plano (Foreground Service - Ubicación)
Google Play revisa estrictamente por qué la app usa el permiso `FOREGROUND_SERVICE_LOCATION`. Usa este texto exacto en el formulario:

* **Tipo de servicio en primer plano:** Ubicación (`location`).
* **Justificación de uso (copia y pega):**
  > "TucTuc Map es una plataforma de transporte local en toritos / mototaxis. La aplicación utiliza un servicio en primer plano de tipo ubicación (FOREGROUND_SERVICE_LOCATION) exclusivamente para los conductores registrados cuando inician su turno de trabajo o aceptan un viaje. Este servicio garantiza que las coordenadas GPS del vehículo se transmitan en tiempo real a los pasajeros cercanos y a la central de despacho, incluso si el conductor minimiza la app para atender llamadas de emergencia o si la pantalla del dispositivo se apaga durante el trayecto de conducción. El conductor visualiza en todo momento una notificación persistente que indica que el rastreo está activo y cuenta con un botón dentro de la app para detener el servicio cuando finaliza su jornada laboral."
* **Enlace a video demostrativo:** Sube un video de 30-45 segundos a YouTube (en modo Oculto/Unlisted) mostrando a un conductor iniciando sesión, activando el turno y cómo aparece la notificación de rastreo en la barra de estado.

### 4.8 Seguridad de los Datos (Data Safety)
* **¿Tu app recopila o comparte datos del usuario?:** Sí.
* **¿Todos los datos recopilados están cifrados en tránsito?:** Sí (HTTPS/TLS).
* **¿Los usuarios pueden solicitar que se eliminen sus datos?:** Sí.
* **Tipos de datos recopilados:**
  1. **Ubicación:**
     - *Ubicación aproximada:* Sí (Recopilada, no compartida con terceros publicitarios, finalidad: Funcionalidad de la app).
     - *Ubicación precisa:* Sí (Recopilada, finalidad: Funcionalidad de la app y navegación del viaje).
  2. **Información personal:**
     - *Nombre:* Sí (Para perfil de usuario/conductor).
     - *Dirección de correo electrónico:* Sí (Para autenticación y administración de cuenta).
     - *Número de teléfono:* Sí (Para contacto de coordinación entre conductor y pasajero).
  3. **Información y rendimiento de la app:**
     - *Registros de fallos y diagnósticos:* Sí (Para estabilidad y resolución de bugs).

---

## 5. Ficha Principal de Play Store (Marketing y Capturas)

En el menú lateral: **"Presencia en Google Play Store" -> "Ficha de Play Store principal"**:

* **Nombre de la app:** `TucTuc Map` *(o el nombre oficial que elijas)*
* **Descripción breve (máximo 80 caracteres):**
  > Conecta con toritos y mototaxis locales en tiempo real de forma rápida y segura.
* **Descripción completa (extracto sugerido):**
  > TucTuc Map es la solución moderna para moverte en tu comunidad.
  >
  > - Encuentra toritos y mototaxis disponibles cerca de ti en un mapa en tiempo real.
  > - Consulta tarifas referenciales transparentes antes de abordar.
  > - Comparte tu ubicación de viaje y viaja con mayor tranquilidad.
  > - Si eres conductor, administra tus turnos, visualiza pasajeros solicitando servicio y optimiza tus recorridos diarios.
  >
  > ¡Descarga TucTuc Map y transforma tu forma de viajar!
* **Gráficos obligatorios para la ficha:**
  1. **Ícono de la app:** 512 × 512 píxeles (PNG de 32 bits con canal alfa, máx 1 MB).
  2. **Gráfico de funciones (Banner promocional):** 1024 × 500 píxeles (JPG o PNG, máx 1 MB).
  3. **Capturas de pantalla del teléfono:** Mínimo 2 capturas (máximo 8), resolución mínima de 320 px y máxima de 3840 px (proporción 16:9 o 18:9, ej. 1080×2400 px).

---

## 6. Proceso de Publicación y Pruebas

Dependiendo de cuándo creaste tu cuenta de Google Play Console:
1. **Si tu cuenta de desarrollador personal se creó después del 13 de noviembre de 2023:**
   - Google exige una fase de **Pruebas Cerradas (Closed Testing)** con un mínimo de **20 evaluadores (testers)** que hayan optado por participar durante al menos **14 días consecutivos** antes de poder solicitar acceso a Producción.
2. **Si es una cuenta de organización (empresa) o cuenta personal antigua:**
   - Puedes enviar la versión directamente a la pista de **Producción** para revisión y publicación directa.
