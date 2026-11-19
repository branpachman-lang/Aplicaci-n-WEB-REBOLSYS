import { Component } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, RouterOutlet, provideRouter } from '@angular/router';
import { sesionGuard } from './sesion-guard';
import { SesionService } from './sesion-service';

@Component({ template: '<router-outlet />', imports: [RouterOutlet] })
class Marco {}

@Component({ template: '' })
class Pagina {}

describe('Sesión compartida entre rutas anidadas', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(), provideHttpClientTesting(), provideRouter([
        { path: '', component: Marco, canActivateChild: [sesionGuard], children: [
          { path: 'configuraciones', children: [
            { path: '', component: Marco, children: [
              { path: 'habitaciones', children: [
                { path: '', component: Pagina, data: { permisos: ['HABITACIONES:VER'] } },
              ] },
            ] },
          ] },
          { path: 'inicio', component: Pagina },
          { path: 'reportes', component: Pagina, data: { soloGerencia: true } },
        ] },
        { path: 'login', component: Pagina },
      ]),
    ] });
  });

  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  it('hace una sola petición para todos los guards y vuelve a consultar en otra navegacion', async () => {
    const router = TestBed.inject(Router), http = TestBed.inject(HttpTestingController);
    let navegacion = router.navigateByUrl('/configuraciones/habitaciones');
    await vi.waitFor(() => expect(http.match('/api/v1/sesion/actual').map(r => {
      r.flush({ idUsuario: 7, usuario: 'recepcion', modo: 'ACTIVA', idTurno: 8,
        idFormaPagoEfectivo: 1, permisos: ['HABITACIONES:VER'] });
      return r;
    })).toHaveLength(1));
    expect(await navegacion).toBe(true);
    http.expectNone('/api/v1/sesion/actual');
    expect(router.url).toBe('/configuraciones/habitaciones');
    navegacion = router.navigateByUrl('/reportes');
    await vi.waitFor(() => {
      http.expectOne('/api/v1/sesion/actual').flush({ idUsuario: 7, usuario: 'recepcion',
        modo: 'ACTIVA', idTurno: 8, idFormaPagoEfectivo: 1, permisos: [] });
    });
    // La redirección a inicio es otra navegacion, con su propia comprobación.
    await vi.waitFor(() => http.expectOne('/api/v1/sesion/actual').flush({ idUsuario: 7,
      usuario: 'recepcion', modo: 'ACTIVA', idTurno: 8, idFormaPagoEfectivo: 1, permisos: [] }));
    await navegacion;
    expect(router.url).toBe('/inicio');
    expect(TestBed.inject(SesionService).permite('HABITACIONES:VER')).toBe(false);
  });
});
