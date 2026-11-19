import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { proteccionSesionInterceptor } from './proteccion-sesion-interceptor';
import { SesionService } from './sesion-service';
import { TokenService } from './token-service';

const inicio = Math.floor(Date.now() / 1000);
const jwt = (segundos = 1800) => 'eyJhbGciOiJIUzI1NiJ9.' + btoa(JSON.stringify({
  jti: 'acceso-prueba', exp: inicio + segundos,
})) + '.firma';

describe('Protección HTTP con Bearer', () => {
  const limpiar = vi.fn();
  const navegar = vi.fn();
  beforeEach(() => {
    sessionStorage.clear(); limpiar.mockClear(); navegar.mockClear();
    TestBed.configureTestingModule({ providers: [
      provideHttpClient(withInterceptors([proteccionSesionInterceptor])), provideHttpClientTesting(),
      { provide: SesionService, useValue: { limpiar } },
      { provide: Router, useValue: { navigateByUrl: navegar } },
    ] });
    TestBed.inject(TokenService).guardar(jwt());
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  it('envía Bearer en escrituras sin solicitar CSRF', () => {
    TestBed.inject(HttpClient).post('/api/v1/reservas', {}).subscribe();
    const http = TestBed.inject(HttpTestingController);
    http.expectNone('/api/v1/sesion/csrf');
    const solicitud = http.expectOne('/api/v1/reservas');
    expect(solicitud.request.headers.get('Authorization')).toBe('Bearer ' + jwt());
    solicitud.flush({});
  });

  it('excluye servidores externos y el inicio de sesión', () => {
    const cliente = TestBed.inject(HttpClient), http = TestBed.inject(HttpTestingController);
    cliente.get('https://externo.example/api/v1/reservas').subscribe();
    cliente.post('/api/v1/sesion/iniciar', {}).subscribe();
    for (const ruta of ['https://externo.example/api/v1/reservas', '/api/v1/sesion/iniciar']) {
      const r = http.expectOne(ruta);
      expect(r.request.headers.has('Authorization')).toBe(false); r.flush({});
    }
  });

  it('acepta la renovación recibida en cabecera', () => {
    TestBed.inject(HttpClient).get('/api/v1/sesion/actual').subscribe();
    const renovado = jwt(3600);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/actual')
      .flush({}, { headers: { 'X-Auth-Token': renovado } });
    expect(TestBed.inject(TokenService).token()).toBe(renovado);
  });

  it('redirige al acceso ante 401 y conserva el acceso ante 403', () => {
    const cliente = TestBed.inject(HttpClient), http = TestBed.inject(HttpTestingController);
    cliente.get('/api/v1/reservas').subscribe({ error: () => {} });
    http.expectOne('/api/v1/reservas').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(limpiar).not.toHaveBeenCalled();
    cliente.get('/api/v1/reservas').subscribe({ error: () => {} });
    http.expectOne('/api/v1/reservas').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(limpiar).toHaveBeenCalledOnce(); expect(navegar).toHaveBeenCalledWith('/login');
  });
});
