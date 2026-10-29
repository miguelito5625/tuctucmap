# Guía de Configuración, Secretos y Datos Requeridos del Propietario (`TucTuc Map`)

Este documento detalla **todos los datos comerciales, credenciales y secretos** que debes proporcionar o reemplazar antes de lanzar la plataforma en producción, así como la ubicación exacta de los archivos locales protegidos (que **no se suben a GitHub**).

---

## 1. Archivos de Secretos Locales (Excluidos de GitHub en `.gitignore`)

Para proteger tus llaves e infraestructura, todos los secretos fueron extraídos del código fuente y centralizados en archivos locales ignorados por Git:

| Archivo Local (Ignorado por Git) | Plantilla Pública en el Repo | Propósito |
| :--- | :--- | :--- |
| [`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env) | [`.env.example`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env.example) | **Archivo maestro de secretos.** Contiene credenciales de PostgreSQL (`DB_*`), Supabase (`SUPABASE_*`), Firebase (`FIREBASE_RTDB_URL`), teléfono de WhatsApp del administrador y cuentas bancarias. |
| [`tuctucMap/local.properties`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/local.properties) | *(Generado por Android Studio / lee de `../.env`)* | Configura la ruta del SDK de Android (`sdk.dir`) y actúa como respaldo de `BuildConfig` para la app móvil. El archivo [`app/build.gradle.kts`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/build.gradle.kts) lee automáticamente primero desde el archivo raíz [`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env). |
| [`tuctucMapDashboard/src/environments/environment.ts`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMapDashboard/src/environments/environment.ts) | [`environment.example.ts`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMapDashboard/src/environments/environment.example.ts) | Contiene las llaves de conexión de Supabase y Firebase Realtime Database para el panel web en Angular. |
| `tuctucMap/keystore.properties` y `*.jks` | *(Debes crearlo al firmar el APK/AAB)* | Llave criptográfica privada para firmar la app de Android para Google Play Store. |

> **Nota importante:** Si en algún momento el repositorio ya tenía rastreado `tuctucMapDashboard/src/environments/environment.ts` o `tuctucMap/app/google-services.json` en commits anteriores, ejecuta una sola vez:
> ```powershell
> git rm --cached tuctucMapDashboard/src/environments/environment.ts
> ```
> Esto dejará el archivo intacto en tu computadora pero evitará que se suba a GitHub en futuros `git push`.

---

## 2. Datos Comerciales que Debes Reemplazar (Sin Recompilar la App)

Se creó la tabla maestra `public.app_config` en Supabase (definida en [`databaseTuctucApp/db.sql`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db.sql)). Tanto la **App Android** como el **Dashboard Angular** descargan estos valores en tiempo real al iniciar, por lo que puedes actualizarlos directamente en Supabase sin tener que volver a publicar la aplicación en Google Play:

| Campo en `public.app_config` | Valor Actual (Placeholder) | Qué debes colocar |
| :--- | :--- | :--- |
| `admin_whatsapp` | `50200000000` | Tu número real de WhatsApp de cobros y soporte, **incluyendo el código de país sin el signo `+`** (ejemplo para Guatemala: `50255112233`). |
| `bank_1_name` | `Banrural (Monetaria)` | Nombre del primer banco y tipo de cuenta donde recibes los depósitos de suscripción. |
| `bank_1_account` | `000-000000-0` | Número real de tu cuenta bancaria principal. |
| `bank_2_name` | `Banco Industrial (Ahorro)` | Nombre del segundo banco o método alternativo (ej. Tigo Money / Kash / Yape). |
| `bank_2_account` | `000-000000-0` | Número real de tu segunda cuenta bancaria. |
| `account_holder_name` | `Administración TucTuc Map` | Nombre completo del titular de las cuentas bancarias para que el conductor verifique antes de transferir. |
| `currency_symbol` | `Q` | Símbolo de tu moneda local (`Q`, `$`, `S/`, `C$`, etc.). |
| `price_daily` | `10` | Precio del plan diario (1 día). |
| `price_weekly` | `50` | Precio del plan semanal (7 días). |
| `price_monthly` | `150` | Precio del plan mensual (30 días). |
| `search_radius_km` | `15.0` | Radio máximo en kilómetros a la redonda para mostrar toritos cercanos y notificar viajes pendientes (recomendado para pueblos: `8.0` a `15.0` km). |

### ¿Cómo actualizar estos datos en un segundo?

**1. Opción Recomendada: Directamente desde el Dashboard Administrativo Web**
Ingresa a `tuctucMapDashboard` con tu usuario administrador y navega a la nueva sección **Configuración Negocio** (`/config`, disponible en el menú lateral y en el menú de usuario).
- Te permite modificar números de WhatsApp, cuentas bancarias, titular, símbolo de moneda, tarifas de planes y radio en kilómetros.
- Cuenta con un **Simulador Móvil en Vivo** que te muestra en tiempo real exactamente cómo verán los conductores la información antes y después de guardar.
- Incluye un botón para probar tu enlace de WhatsApp con un solo clic.

**2. Opción Alternativa: Desde el Table Editor de Supabase o mediante script**
Puedes actualizar la fila `id = 'default'` en la tabla `app_config` ejecutando este comando desde la carpeta `databaseTuctucApp/`:

```powershell
node run_sql.js "UPDATE public.app_config SET admin_whatsapp = '502TUNUMERO', bank_1_account = 'TU-CUENTA-1', bank_2_account = 'TU-CUENTA-2', account_holder_name = 'TU NOMBRE COMPLETO', updated_at = NOW() WHERE id = 'default';"
```

También te recomendamos actualizar esos mismos valores en tu archivo raíz [`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env) para que la app Android los tenga como respaldo offline predeterminado.

---

## 3. Credenciales Críticas e Infraestructura que Debes Configurar

### A. Rotar la Contraseña de Base de Datos en Supabase (¡Prioridad Alta!)
La contraseña anterior de PostgreSQL estaba escrita directamente en los archivos `.js` de `databaseTuctucApp/`. Aunque ya fue eliminada del código y movida a [`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env), debes cambiarla por seguridad:
1. Ingresa a [Supabase Dashboard](https://supabase.com/dashboard) -> Tu proyecto -> **Project Settings** -> **Database**.
2. En **Database Password**, haz clic en **Reset database password** y genera una contraseña segura.
3. Abre el archivo [`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env) en la raíz del proyecto y reemplaza el valor de `DB_PASSWORD` con tu nueva contraseña.

### B. Publicar las Reglas de Seguridad en Firebase Realtime Database
Las reglas de seguridad corregidas para los nodos reales (`driver_locations` y `trips`, con validación de coordenadas e índices `.indexOn`) están listas en [`tuctucMap/firebase_rules.json`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/firebase_rules.json).
1. Abre [Firebase Console](https://console.firebase.google.com/) -> Tu proyecto (`tuctucmap-ed142`).
2. Ve a **Realtime Database** -> pestaña **Reglas (Rules)**.
3. Copia todo el contenido de [`tuctucMap/firebase_rules.json`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/firebase_rules.json), pégalo en el editor de Firebase y haz clic en **Publicar (Publish)**.

### C. Restringir las API Keys de Google / Firebase (`google-services.json`)
Para evitar que terceros usen tu cuota de Firebase:
1. Obtén la huella SHA-1 y SHA-256 de tu computadora/keystore ejecutando en `tuctucMap/`:
   ```powershell
   .\gradlew.bat signingReport
   ```
2. En **Firebase Console** -> **Project Settings** -> **General** -> Tu app Android (`com.tuctuc.map`), agrega tus huellas **SHA-1** y **SHA-256**.
3. En [Google Cloud Console -> Credentials](https://console.cloud.google.com/apis/credentials), selecciona la API Key de Android y restríngela únicamente al paquete `com.tuctuc.map` con tu huella SHA-1.

### D. Asignar tu Usuario Administrador en Supabase
Para ingresar al panel web (`tuctucMapDashboard`), tu cuenta debe tener el rol `'admin'` en la tabla `profiles`:
1. Crea tu usuario desde la sección **Authentication -> Users** en Supabase (con correo y contraseña).
2. Asigna el rol de administrador ejecutando en `databaseTuctucApp/`:
   ```powershell
   node run_sql.js "UPDATE public.profiles SET role = 'admin', approval_status = 'approved' WHERE id = 'UUID-DE-TU-USUARIO';"
   ```

---

## 4. Resumen deDóndeReemplazar Cada Secreto si Creas un Nuevo Entorno

Si en el futuro cambias de proyecto en Supabase o Firebase, solo debes editar **2 archivos locales** (ambos ignorados por Git):

1. **[`.env`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/.env) (en la raíz del monorepo):**
   - Alimenta los scripts administrativos de Node.js en [`databaseTuctucApp/db_config.js`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/databaseTuctucApp/db_config.js).
   - Alimenta automáticamente la compilación de Android en [`tuctucMap/app/build.gradle.kts`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMap/app/build.gradle.kts) (generando `BuildConfig.SUPABASE_URL`, `BuildConfig.SUPABASE_KEY` y `BuildConfig.FIREBASE_RTDB_URL`).
2. **[`tuctucMapDashboard/src/environments/environment.ts`](file:///c:/Users/exbinario/Documents/desarrollo/antigravity/Android/tuctucmap/tuctucMapDashboard/src/environments/environment.ts):**
   - Alimenta el Dashboard Administrativo en Angular (`supabase.url`, `supabase.key` y `firebase.databaseUrl`).
