import { HttpErrorResponse } from '@angular/common/http';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { Subject, of, throwError } from 'rxjs';
import { SesionService } from '../../core/services/sesion-service';
import { Acceso } from './acceso';

const USUARIO_RECORDADO = 'rebolsys.acceso.usuario';
const CLAVE_NUEVA = 'Una clave nueva y larga';

describe('Acceso', () => {
  function preparar(cambio = false, cargar = vi.fn(() => of({}))) {
    const sesion = {
      actual: signal<{ requiereCambio?: boolean } | null>(null),
      cargar,
      iniciar: vi.fn((_usuario: string, _contrasena: string) => of({ requiereCambio: false })),
      cambiar: vi.fn((_actual: string, _nueva: string) => of({ requiereCambio: false })),
      cerrar: vi.fn(() => of(undefined)),
    };
    const router = { navigateByUrl: vi.fn(() => Promise.resolve(true)) };
    TestBed.configureTestingModule({
      providers: [
        { provide: SesionService, useValue: sesion },
        { provide: Router, useValue: router },
        { provide: ActivatedRoute, useValue: { snapshot: { data: cambio ? { cambio: true } : {} } } },
      ],
    });
    const componente = TestBed.runInInjectionContext(() => new Acceso());
    return { componente, sesion, router };
  }

  beforeEach(() => localStorage.clear());

  it('exige usuario y contraseña antes de enviar', () => {
    const { componente, sesion } = preparar();
    componente.enviar();
    expect(componente.error()).toBe('Ingresa tu usuario y tu contraseña.');
    expect(sesion.iniciar).not.toHaveBeenCalled();
  });

  it('inicia sesión, recuerda el usuario y entra al inicio', () => {
    const { componente, sesion, router } = preparar();
    componente.usuario = '  recepcion ';
    componente.contrasena = 'secreto';
    componente.recordar = true;
    componente.enviar();
    expect(sesion.iniciar).toHaveBeenCalledWith('recepcion', 'secreto');
    expect(localStorage.getItem(USUARIO_RECORDADO)).toBe('recepcion');
    expect(router.navigateByUrl).toHaveBeenCalledWith('/inicio');
    expect(componente.cargando()).toBe(false);
  });

  it('recupera el usuario recordado y lo olvida al desmarcar la opción', () => {
    localStorage.setItem(USUARIO_RECORDADO, 'caja');
    const { componente } = preparar();
    expect(componente.usuario).toBe('caja');
    expect(componente.recordar).toBe(true);
    componente.cambiarRecordatorio(false);
    expect(localStorage.getItem(USUARIO_RECORDADO)).toBeNull();
  });

  it('redirige al cambio de contraseña cuando la credencial es temporal', () => {
    const { componente, sesion, router } = preparar();
    sesion.iniciar.mockReturnValue(of({ requiereCambio: true }));
    componente.usuario = 'nuevo';
    componente.contrasena = 'temporal';
    componente.recordar = false;
    componente.enviar();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/cambiar-contrasena');
    expect(localStorage.getItem(USUARIO_RECORDADO)).toBeNull();
  });

  it('muestra un mensaje genérico ante credenciales rechazadas', () => {
    const { componente, sesion, router } = preparar();
    sesion.iniciar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    componente.usuario = 'recepcion';
    componente.contrasena = 'incorrecta';
    componente.enviar();
    expect(componente.error()).toContain('usuario o la contraseña son incorrectos');
    expect(router.navigateByUrl).not.toHaveBeenCalled();
    expect(componente.cargando()).toBe(false);
  });

  it('informa los errores de conexión y evita envíos simultáneos', () => {
    const { componente, sesion } = preparar();
    const respuesta = new Subject<{ requiereCambio: boolean }>();
    sesion.iniciar.mockReturnValue(respuesta);
    componente.usuario = 'recepcion';
    componente.contrasena = 'secreto';
    componente.enviar();
    componente.enviar();
    expect(sesion.iniciar).toHaveBeenCalledTimes(1);
    respuesta.error(new HttpErrorResponse({ status: 0 }));
    expect(componente.error()).toContain('No se pudo conectar');
  });

  it('valida los requisitos de la nueva contraseña', () => {
    const { componente, sesion } = preparar(true);
    expect(componente.cambio).toBe(true);
    expect(sesion.cargar).toHaveBeenCalled();
    componente.contrasena = 'temporal';
    componente.nueva = 'corta';
    componente.confirmar = 'corta';
    expect(componente.longitudValida).toBe(false);
    componente.enviar();
    expect(componente.error()).toContain('requisitos de la contraseña');

    componente.nueva = 'ñ'.repeat(40);
    componente.confirmar = componente.nueva;
    expect(componente.longitudValida).toBe(true);
    expect(componente.bytesValidos).toBe(false);

    componente.nueva = CLAVE_NUEVA;
    componente.confirmar = 'Otra clave distinta y larga';
    expect(componente.coinciden).toBe(false);
    expect(sesion.cambiar).not.toHaveBeenCalled();
  });

  it('cambia la contraseña requerida y entra al inicio', () => {
    const { componente, sesion, router } = preparar(true);
    sesion.actual.set({ requiereCambio: true });
    expect(componente.temporal()).toBe(true);
    componente.contrasena = 'temporal';
    componente.nueva = CLAVE_NUEVA;
    componente.confirmar = CLAVE_NUEVA;
    componente.enviar();
    expect(sesion.cambiar).toHaveBeenCalledWith('temporal', CLAVE_NUEVA);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/inicio');
  });

  it('indica revisar la contraseña actual si el cambio se rechaza', () => {
    const { componente, sesion } = preparar(true);
    sesion.cambiar.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    componente.contrasena = 'equivocada';
    componente.nueva = CLAVE_NUEVA;
    componente.confirmar = CLAVE_NUEVA;
    componente.enviar();
    expect(componente.error()).toBe('Revisa tu contraseña actual o vuelve a iniciar sesión.');
  });

  it('vuelve al inicio de sesión si no hay sesión al pedir el cambio', () => {
    const { router } = preparar(true, vi.fn(() => throwError(() => new HttpErrorResponse({ status: 401 }))));
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });

  it('la ayuda limpia la contraseña escrita', () => {
    const { componente } = preparar();
    componente.contrasena = 'secreto';
    componente.mostrarActual.set(true);
    componente.cambiarAyuda(true);
    expect(componente.contrasena).toBe('');
    expect(componente.mostrarActual()).toBe(false);
    expect(componente.ayuda()).toBe(true);
    componente.cambiarAyuda(false);
    expect(componente.ayuda()).toBe(false);
  });

  it('cierra la sesión o informa si no fue posible', () => {
    const { componente, sesion, router } = preparar();
    componente.salir();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');

    sesion.cerrar.mockReturnValue(throwError(() => new Error('sin red')));
    componente.salir();
    expect(componente.error()).toBe('No se pudo cerrar la sesión.');
    expect(componente.cargando()).toBe(false);
  });
});
