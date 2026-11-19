import { TestBed } from '@angular/core/testing';
import { esUrlApi, TokenService } from './token-service';

function jwt(id = 'acceso-1', segundos = 1800): string {
  return 'eyJhbGciOiJIUzI1NiJ9.' + btoa(JSON.stringify({ jti: id, exp: Math.floor(Date.now() / 1000) + segundos })) + '.firma';
}

describe('Almacenamiento JWT por pestaña', () => {
  beforeEach(() => { sessionStorage.clear(); TestBed.configureTestingModule({}); });
  afterEach(() => sessionStorage.clear());

  it('conserva el token al recargar y no guarda datos personales adicionales', () => {
    const token = jwt();
    TestBed.inject(TokenService).guardar(token);
    expect(sessionStorage.getItem('rebolsys.auth.jwt')).toBe(token);
    TestBed.resetTestingModule();
    expect(TestBed.inject(TokenService).token()).toBe(token);
  });

  it('descarta tokens vencidos o malformados', () => {
    sessionStorage.setItem('rebolsys.auth.jwt', jwt('vencido', -1));
    expect(TestBed.inject(TokenService).token()).toBeNull();
    expect(() => TestBed.inject(TokenService).guardar('invalido')).toThrow();
    expect(sessionStorage.getItem('rebolsys.auth.jwt')).toBeNull();
  });

  it('una respuesta pendiente no restaura un acceso cerrado', () => {
    const tokens = TestBed.inject(TokenService);
    const inicial = jwt();
    tokens.guardar(inicial);
    tokens.limpiar();
    tokens.renovar(jwt('acceso-1', 3600), inicial);
    expect(tokens.token()).toBeNull();
  });

  it('no mezcla renovaciones de distintos usuarios ni retrocede el vencimiento', () => {
    const tokens = TestBed.inject(TokenService);
    const inicial = jwt();
    const reciente = jwt('acceso-1', 3600);
    tokens.guardar(inicial);
    tokens.renovar(reciente, inicial);
    tokens.renovar(inicial, inicial);
    expect(tokens.token()).toBe(reciente);
    const otro = jwt('otro-acceso');
    tokens.guardar(otro);
    tokens.renovar(reciente, inicial);
    expect(tokens.token()).toBe(otro);
  });

  it('limita las credenciales al origen y prefijo de la API', () => {
    expect(esUrlApi('/api/v1/reservas')).toBe(true);
    expect(esUrlApi('https://externo.example/api/v1/reservas')).toBe(false);
    expect(esUrlApi('/api/v10/reservas')).toBe(false);
    expect(esUrlApi('//externo.example/api/v1/reservas')).toBe(false);
  });
});
