# TucTucMap Dashboard — Plan de Implementación

## Objetivo

Construir un dashboard administrativo en Angular 22 + Angular Material 22 para gestionar los conductores registrados en Firebase Realtime Database desde la app Android **tuctucMap**.

---

## Estructura de Archivos a Crear/Modificar

```
src/
├── app/
│   ├── core/
│   │   ├── firebase.config.ts          [NEW] Configuración de Firebase
│   │   └── models/
│   │       └── driver.model.ts         [NEW] Interfaz TypeScript del conductor
│   ├── services/
│   │   └── driver.service.ts           [NEW] Servicio Firebase Realtime DB
│   ├── features/
│   │   └── drivers/
│   │       ├── drivers.component.ts    [NEW] Componente tabla de conductores
│   │       ├── drivers.component.html  [NEW] Template con mat-table + filtros
│   │       └── drivers.component.scss  [NEW] Estilos del componente
│   ├── shared/
│   │   └── layout/
│   │       ├── shell.component.ts      [NEW] Shell con sidenav + toolbar
│   │       ├── shell.component.html    [NEW]
│   │       └── shell.component.scss    [NEW]
│   ├── app.config.ts                   [MODIFY] Agregar provideFirebaseApp, provideDatabase
│   ├── app.routes.ts                   [MODIFY] Agregar ruta /drivers
│   ├── app.ts                          [MODIFY] Limpiar plantilla por defecto
│   └── app.html                        [MODIFY] Solo <router-outlet>
├── styles.css                          [MODIFY] Estilos globales base
└── material-theme.scss                 [MODIFY] Cambiar a dark theme (opcional)
```

---

## Decisiones Técnicas

| Decisión | Elección | Razón |
|---|---|---|
| SDK Firebase | Firebase JS SDK v12 (modular) | Ya instalado con `npm install firebase` |
| Integración Angular | `provideFirebaseApp` + `provideDatabase` de `@angular/fire` | No está instalado; usaremos SDK directamente con RxJS wrappers |
| Estado management | Signals + RxJS `BehaviorSubject` | Sin NgRx para mantenerlo simple |
| Arquitectura componentes | Standalone Components | Proyecto Angular 22 (por defecto standalone) |
| Tema | Dark mode con colores personalizados | Dashboard profesional y moderno |

> **Nota**: El proyecto tiene `firebase` (SDK nativo) instalado pero NO `@angular/fire`. Usaremos el SDK modular de Firebase directamente y crearemos wrappers con Observables de RxJS.

---

## Módulos de Angular Material a Importar

- `MatTableModule` — Tabla de datos
- `MatSortModule` — Ordenamiento de columnas
- `MatPaginatorModule` — Paginación
- `MatTabsModule` — Filtros por pestaña
- `MatChipsModule` — Chips de estado (Pendiente/Aprobado)
- `MatButtonModule` — Botones de acción
- `MatIconModule` — Íconos Material
- `MatToolbarModule` — Barra superior
- `MatSidenavModule` — Navegación lateral
- `MatCardModule` — Cards de estadísticas
- `MatProgressSpinnerModule` — Loading state
- `MatSnackBarModule` — Notificaciones
- `MatBadgeModule` — Badge de conteo
- `MatTooltipModule` — Tooltips en botones
- `MatDialogModule` — Diálogos de confirmación

---

## Flujo de Datos

```
Firebase Realtime DB (nodo: drivers/)
        ↓  onValue() listener
  DriverService (Observable<Driver[]>)
        ↓  async pipe
  DriversComponent (mat-table)
        ↓  acción usuario
  DriverService.approveDriver(uid)
        ↓  update()
  Firebase Realtime DB
```

---

## Verificación

- [ ] `ng serve` compila sin errores
- [ ] La tabla muestra conductores en tiempo real desde Firebase
- [ ] El botón "Aprobar" actualiza el estado en Firebase
- [ ] Los filtros por pestaña funcionan correctamente
- [ ] El diseño es responsive y moderno
