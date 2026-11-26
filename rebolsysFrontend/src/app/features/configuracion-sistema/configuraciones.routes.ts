import { inject } from '@angular/core';
import { Routes } from '@angular/router';
import { PermisosUi } from '../../core/auth/permisos-ui';

function destinoInicialConfiguracion(): string {
  const acceso = inject(PermisosUi);
  if (acceso.permite('HABITACIONES:VER')) return 'habitaciones';
  if (acceso.permite('PRODUCTOS:VER')) return 'productos';
  if (acceso.permite('MOBILIARIO:VER')) return 'mobiliario';
  if (acceso.esGerente()) return 'sistema';
  return 'habitaciones';
}

export const CONFIGURACIONES_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () =>
      import('../mantenimiento/mantenimiento').then((m) => m.Mantenimiento),
    children: [
      { path: '', pathMatch: 'full', redirectTo: destinoInicialConfiguracion },
      {
        path: 'sistema',
        title: 'Configuración del sistema · REBOLSYS',
        data: { soloGerencia: true },
        loadComponent: () => import('./pages/sistema/sistema').then((m) => m.Sistema),
      },
      {
        path: 'habitaciones',
        loadChildren: () =>
          import('../mantenimiento/habitaciones.routes').then(
            (m) => m.HABITACIONES_CONFIGURACION_ROUTES,
          ),
      },
      {
        path: 'mobiliario',
        loadChildren: () =>
          import('../mantenimiento/mobiliario.routes').then(
            (m) => m.MOBILIARIO_ROUTES,
          ),
      },
      {
        path: 'productos',
        loadChildren: () =>
          import('../mantenimiento/productos.routes').then((m) => m.PRODUCTOS_ROUTES),
      },
    ],
  },
];
