import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { ShellComponent } from './shared/layout/shell.component';

export const routes: Routes = [
  {
    path: '',
    redirectTo: 'drivers',
    pathMatch: 'full',
  },
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.component').then(m => m.LoginComponent),
    title: 'Iniciar Sesión — TucTucMap',
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      {
        path: 'drivers',
        loadComponent: () =>
          import('./features/drivers/drivers.component').then((m) => m.DriversComponent),
        title: 'Conductores — TucTucMap Admin',
      },
      {
        path: 'subscriptions',
        loadComponent: () =>
          import('./features/subscriptions/subscriptions.component').then((m) => m.SubscriptionsComponent),
        title: 'Suscripciones — TucTucMap Admin',
      },
      {
        path: 'trips',
        loadComponent: () =>
          import('./features/trips/trips').then((m) => m.Trips),
        title: 'Viajes — TucTucMap Admin',
      },
    ]
  },
  {
    path: '**',
    redirectTo: 'drivers',
  },
];
