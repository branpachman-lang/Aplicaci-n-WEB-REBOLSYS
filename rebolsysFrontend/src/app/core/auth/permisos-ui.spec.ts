import { PermisosUi } from './permisos-ui';
import type { SesionActual } from './sesion-service';

describe('Permisos del usuario autenticado', () => {
  const sesion: SesionActual = {
    idUsuario: 7, usuario: 'recepcion', modo: 'ACTIVA', idTurno: 8,
    idFormaPagoEfectivo: 1, roles: ['RECEPCIONISTA'], permisos: ['RESERVAS:VER'],
  };
  let acceso: PermisosUi;

  beforeEach(() => { acceso = new PermisosUi(); });

  it('no permite operar sin sesión', () => {
    expect(acceso.permite('RESERVAS:VER')).toBe(false);
  });

  it('exige el permiso de la operación aunque haya una sesión activa', () => {
    acceso.actual.set(sesion);
    expect(acceso.permite('RESERVAS:VER')).toBe(true);
    expect(acceso.permite('RESERVAS:CREAR')).toBe(false);
  });

  it('una etiqueta antigua no concede permisos', () => {
    acceso.actual.set({ ...sesion, modo: 'LEGADO', permisos: [] } as unknown as SesionActual);
    expect(acceso.permite('RESERVAS:CREAR')).toBe(false);
  });

  it('la contraseña temporal bloquea incluso las funciones de gerencia', () => {
    acceso.actual.set({ ...sesion, gerente: true, requiereCambio: true });
    expect(acceso.permite('RESERVAS:VER')).toBe(false);
    expect(acceso.esGerente()).toBe(false);
  });
});
