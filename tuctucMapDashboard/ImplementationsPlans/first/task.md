# TucTucMap Dashboard — Tasks

## Core & Config
- [/] `src/app/core/firebase.config.ts` — Firebase init
- [/] `src/app/core/models/driver.model.ts` — TypeScript interface
- [/] `src/app/services/driver.service.ts` — Firebase service

## Layout Shell
- [/] `src/app/shared/layout/shell.component.ts`
- [/] `src/app/shared/layout/shell.component.html`
- [/] `src/app/shared/layout/shell.component.scss`

## Drivers Feature
- [/] `src/app/features/drivers/drivers.component.ts`
- [/] `src/app/features/drivers/drivers.component.html`
- [/] `src/app/features/drivers/drivers.component.scss`

## App Wiring
- [/] `app.config.ts` — provideAnimationsAsync
- [/] `app.routes.ts` — /drivers route
- [/] `app.ts` — import ShellComponent
- [/] `app.html` — only `<app-shell />`

## Styles
- [/] `src/styles.css` — global snackbar tokens
- [/] `src/material-theme.scss` — dark scheme
- [/] `src/index.html` — meta + title

## Verification
- [ ] `ng serve` compiles clean
- [ ] Table reads from Firebase in realtime
- [ ] Approve/Reject updates Firebase
