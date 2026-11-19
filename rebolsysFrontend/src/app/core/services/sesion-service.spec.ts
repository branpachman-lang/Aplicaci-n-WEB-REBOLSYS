import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { SesionService } from './sesion-service';
import { TokenService } from './token-service';

const expira = Math.floor(Date.now() / 1000) + 1800;
const token = () => 'eyJhbGciOiJIUzI1NiJ9.' + btoa(JSON.stringify({ jti: 'sesion', exp: expira })) + '.firma';
const sesion = { idUsuario: 7, usuario: 'gerencia', modo: 'ACTIVA', idTurno: 8, idFormaPagoEfectivo: 1 };

describe('Inicio y cierre con JWT', () => {
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
  });
  afterEach(() => { TestBed.inject(HttpTestingController).verify(); sessionStorage.clear(); });

  it('recuerda el Bearer y conserva el turno recibido al iniciar', () => {
    const servicio = TestBed.inject(SesionService);
    const respuesta = vi.fn();
    servicio.iniciar('gerencia', 'clave-de-prueba').subscribe(respuesta);
    TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/iniciar')
      .flush({ token: token(), tipo: 'Bearer', expiraEnSegundos: 1800, sesion });
    expect(TestBed.inject(TokenService).token()).toBe(token());
    expect(respuesta).toHaveBeenCalledWith(sesion);
    expect(servicio.actual()?.idTurno).toBe(8);
    expect(sessionStorage.getItem('rebolsys.auth.jwt')).not.toContain('clave-de-prueba');
    servicio.cargar().subscribe();
    servicio.cargarEnNavegacion(1).subscribe();
    TestBed.inject(HttpTestingController).expectNone('/api/v1/sesion/actual');
  });

  it('sustituye la credencial después del cambio obligatorio', () => {
    const servicio = TestBed.inject(SesionService);
    servicio.cambiar('actual', 'nueva-elegida-de-prueba').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/contrasena')
      .flush({ token: token(), tipo: 'Bearer', expiraEnSegundos: 1800, sesion: { ...sesion, requiereCambio: false } });
    expect(servicio.actual()?.requiereCambio).toBe(false);
    expect(TestBed.inject(TokenService).token()).toBe(token());
    servicio.cargarEnNavegacion(1).subscribe();
    TestBed.inject(HttpTestingController).expectNone('/api/v1/sesion/actual');
  });

  it('elimina token y operaciones pendientes al cerrar', () => {
    const servicio = TestBed.inject(SesionService);
    TestBed.inject(TokenService).guardar(token());
    sessionStorage.setItem('rebolsys.venta.pendiente', '{}');
    servicio.cerrar().subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/cerrar').flush(null);
    expect(TestBed.inject(TokenService).token()).toBeNull();
    expect(sessionStorage.getItem('rebolsys.venta.pendiente')).toBeNull();
    expect(servicio.actual()).toBeNull();
  });

  it('cancela una consulta pendiente para que no restaure datos después de salir', () => {
    const servicio = TestBed.inject(SesionService);
    servicio.cargar().subscribe();
    const pendiente = TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/actual');
    servicio.limpiar();
    expect(pendiente.cancelled).toBe(true);
    expect(servicio.actual()).toBeNull();
  });

  it('comparte la consulta pendiente incluso cuando varios consumidores fuerzan la carga', () => {
    const servicio = TestBed.inject(SesionService), http = TestBed.inject(HttpTestingController);
    const recibir = vi.fn();
    servicio.cargar(true).subscribe(recibir);
    servicio.cargar(true).subscribe(recibir);
    servicio.cargar().subscribe(recibir);
    http.expectOne('/api/v1/sesion/actual').flush(sesion);
    expect(recibir).toHaveBeenCalledTimes(3);
    servicio.cargar().subscribe();
    http.expectNone('/api/v1/sesion/actual');
  });

  it('consulta una vez por navegación y actualiza permisos en la siguiente', () => {
    const servicio = TestBed.inject(SesionService), http = TestBed.inject(HttpTestingController);
    servicio.cargarEnNavegacion(10).subscribe();
    servicio.cargarEnNavegacion(10).subscribe();
    http.expectOne('/api/v1/sesion/actual').flush({ ...sesion, permisos: ['VENTAS:VER'] });
    servicio.cargarEnNavegacion(10).subscribe();
    http.expectNone('/api/v1/sesion/actual');
    servicio.cargarEnNavegacion(11).subscribe();
    http.expectOne('/api/v1/sesion/actual').flush({ ...sesion, permisos: [] });
    expect(servicio.permite('VENTAS:VER')).toBe(false);
  });

  it('descarta una consulta anterior al refrescar tras cerrar caja', () => {
    const servicio = TestBed.inject(SesionService), http = TestBed.inject(HttpTestingController);
    servicio.cargar().subscribe();
    const anterior = http.expectOne('/api/v1/sesion/actual');
    servicio.refrescar().subscribe();
    expect(anterior.cancelled).toBe(true);
    http.expectOne('/api/v1/sesion/actual').flush({ ...sesion, idTurno: null });
    servicio.cargar().subscribe();
    http.expectNone('/api/v1/sesion/actual');
    expect(servicio.actual()?.idTurno).toBeNull();
  });

  it('permite reintentar después de fallar la consulta', () => {
    const servicio = TestBed.inject(SesionService), http = TestBed.inject(HttpTestingController);
    servicio.cargar().subscribe({ error: () => {} });
    http.expectOne('/api/v1/sesion/actual').flush({}, { status: 503, statusText: 'Unavailable' });
    servicio.cargar().subscribe();
    http.expectOne('/api/v1/sesion/actual').flush(sesion);
    expect(servicio.actual()).toEqual(sesion);
    expect(servicio.error()).toBe('');
  });

  it('no inicia una consulta guardada antes de salir ni mezcla cuentas al ingresar otra vez', () => {
    const servicio = TestBed.inject(SesionService), http = TestBed.inject(HttpTestingController);
    const anterior = servicio.cargar();
    servicio.limpiar();
    anterior.subscribe();
    http.expectNone('/api/v1/sesion/actual');
    servicio.iniciar('recepcion', 'clave').subscribe();
    http.expectOne('/api/v1/sesion/iniciar').flush({ token: token(), tipo: 'Bearer',
      expiraEnSegundos: 1800, sesion: { ...sesion, idUsuario: 9, usuario: 'recepcion' } });
    servicio.cargarEnNavegacion(12).subscribe();
    expect(servicio.actual()?.idUsuario).toBe(9);
    http.expectNone('/api/v1/sesion/actual');
  });
});
