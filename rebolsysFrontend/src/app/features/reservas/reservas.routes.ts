import { Routes } from '@angular/router';

export const RESERVAS_ROUTES: Routes = [
  {
    path: '',
    data: { permisos: ['RESERVAS:VER'] },
    loadComponent: () =>
      import('./pages/reserva-list/reserva-list').then((m) => m.ReservaList),
  },
  {
    path: 'nueva',
    data: { permisos: ['RESERVAS:CREAR'] },
    loadComponent: () =>
      import('./pages/reserva-form/reserva-form').then((m) => m.ReservaForm),
  },
];
