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
  });

  it('sustituye la credencial después del cambio obligatorio', () => {
    const servicio = TestBed.inject(SesionService);
    servicio.cambiar('actual', 'nueva-elegida-de-prueba').subscribe();
    TestBed.inject(HttpTestingController).expectOne('/api/v1/sesion/contrasena')
      .flush({ token: token(), tipo: 'Bearer', expiraEnSegundos: 1800, sesion: { ...sesion, requiereCambio: false } });
    expect(servicio.actual()?.requiereCambio).toBe(false);
    expect(TestBed.inject(TokenService).token()).toBe(token());
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
});
