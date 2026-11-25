import { Routes } from '@angular/router';

export const HABITACIONES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('./pages/habitacion-list/habitacion-list').then(
        (m) => m.HabitacionList,
      ),
  },
];

export const HABITACIONES_CONFIGURACION_ROUTES: Routes = [
  {
    path: '',
    data: { permisos: ['HABITACIONES:VER'] },
    loadComponent: () =>
      import('./pages/habitacion-config-list/habitacion-config-list').then(
        (m) => m.HabitacionConfigList,
      ),
  },
  {
    path: 'nuevo',
    data: { permisos: ['HABITACIONES:CREAR'] },
    loadComponent: () =>
      import('./pages/habitacion-form/habitacion-form').then(
        (m) => m.HabitacionForm,
      ),
  },
  {
    path: ':id/editar',
    data: { permisos: ['HABITACIONES:EDITAR'] },
    loadComponent: () =>
      import('./pages/habitacion-form/habitacion-form').then(
        (m) => m.HabitacionForm,
      ),
  },
];
