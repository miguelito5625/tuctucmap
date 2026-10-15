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
        path: 'fleet-map',
        loadComponent: () =>
          import('./features/fleet-map/fleet-map.component').then((m) => m.FleetMapComponent),
        title: 'Mapa en Vivo — TucTucMap Admin',
      },
      {
        path: 'passengers',
        loadComponent: () =>
          import('./features/passengers/passengers.component').then((m) => m.PassengersComponent),
        title: 'Pasajeros — TucTucMap Admin',
      },
      {
        path: 'trips',
        loadComponent: () =>
          import('./features/trips/trips.component').then((m) => m.TripsComponent),
        title: 'Viajes — TucTucMap Admin',
      },
    ]
  },
  {
    path: '**',
    redirectTo: 'drivers',
  },
];
