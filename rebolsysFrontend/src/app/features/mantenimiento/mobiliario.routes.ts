import { Routes } from '@angular/router';
export const MOBILIARIO_ROUTES: Routes = [
  {
    path: '',
    data: { permisos: ['MOBILIARIO:VER'] },
    loadComponent: () =>
      import('./pages/mobiliario-list/mobiliario-list').then(
        (m) => m.MobiliarioList,
      ),
  },
  {
    path: 'nuevo',
    data: { permisos: ['MOBILIARIO:CREAR'] },
    loadComponent: () =>
      import('./pages/mobiliario-form/mobiliario-form').then(
        (m) => m.MobiliarioForm,
      ),
  },
  {
    path: ':id/editar',
    data: { permisos: ['MOBILIARIO:EDITAR'] },
    loadComponent: () =>
      import('./pages/mobiliario-form/mobiliario-form').then(
        (m) => m.MobiliarioForm,
      ),
  },
];
