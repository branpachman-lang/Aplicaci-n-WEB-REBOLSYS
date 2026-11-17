import { Routes } from '@angular/router';

export const CONFIGURACIONES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('../mantenimiento/mantenimiento').then((m) => m.Mantenimiento),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'habitaciones' },
      {
        path: 'sistema',
        title: 'Configuración del sistema · REBOLSYS',
        data: { soloGerencia: true },
        loadComponent: () => import('./sistema/sistema').then((m) => m.Sistema),
      },
      {
        path: 'habitaciones',
        loadChildren: () =>
          import('../habitaciones/habitaciones.routes').then(
            (m) => m.HABITACIONES_CONFIGURACION_ROUTES,
          ),
      },
      {
        path: 'mobiliario',
        loadChildren: () =>
          import('../inmobiliario/inmobiliario.routes').then(
            (m) => m.INMOBILIARIO_ROUTES,
          ),
      },
      {
        path: 'productos',
        loadChildren: () =>
          import('../productos/productos.routes').then((m) => m.PRODUCTOS_ROUTES),
      },
    ],
  },
];
