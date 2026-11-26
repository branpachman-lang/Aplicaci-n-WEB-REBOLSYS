import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { NotificacionService } from '../../../../core/services/notificacion.service';
import { SesionService } from '../../../../core/auth/sesion-service';
import { CajaApi, DatosMovimiento, MovimientoCaja, TurnoCaja } from '../../services/caja-api';
import { CajaPanel } from './caja-panel';

const pagina = { contenido: [], pagina: 0, tamanio: 20, totalElementos: 0, totalPaginas: 0, ultima: true };

function turno(cambios: Partial<TurnoCaja> = {}): TurnoCaja {
  return {
    idTurno: 7, fechaApertura: '2026-11-23T08:00:00', fechaCierre: null, montoBase: 50, abierto: true,
    tipos: [
      { idTipoMovimiento: 1, nombre: 'Ingreso manual', signo: 1 },
      { idTipoMovimiento: 2, nombre: 'Gasto', signo: -1 },
      { idTipoMovimiento: 3, nombre: 'Compra', signo: -1 },
    ],
    ingresosManuales: 0, gastosManuales: 0,
    arqueos: [
      { idFormaPago: 1, formaPago: 'EFECTIVO', montoCalculado: 130.5, montoDeclarado: null, diferencia: null },
      { idFormaPago: 2, formaPago: 'YAPE', montoCalculado: 25, montoDeclarado: null, diferencia: null },
    ],
    revision: 'r'.repeat(64), bloqueoCierre: null, ...cambios,
  };
}

function errorHttp(status: number, message = 'Conflicto') {
  return new HttpErrorResponse({ status, error: status ? { message } : null });
}

describe('Panel de caja', () => {
  function preparar(actual: TurnoCaja | null = turno()) {
    const api = {
      actual: vi.fn(() => of(actual)),
      turno: vi.fn((_id: number) => of(turno())),
      movimientos: vi.fn(() => of(pagina)),
      registrar: vi.fn((_id: number, _datos: DatosMovimiento, _archivo: File | null) => of({} as MovimientoCaja)),
      cerrar: vi.fn((_id: number, _revision: string, _arqueos: unknown) => of(turno({ abierto: false }))),
    };
    const sesion = { actual: () => ({ gerente: true, roles: [] }), permite: vi.fn(() => true), refrescar: vi.fn(() => of(null)) };
    const avisos = { mostrar: vi.fn() };
    TestBed.configureTestingModule({
      imports: [CajaPanel],
      providers: [
        provideHttpClient(), provideHttpClientTesting(),
        { provide: CajaApi, useValue: api },
        { provide: SesionService, useValue: sesion },
        { provide: NotificacionService, useValue: avisos },
      ],
    });
    const componente = TestBed.createComponent(CajaPanel).componentInstance;
    return { componente, api, sesion, avisos };
  }

  function declararTodo(componente: CajaPanel) {
    componente.abrirCierre();
    componente.declarar(1, 129.5);
    componente.declarar(2, 25);
  }

  it('carga el turno actual con su historial y deja el panel vacío sin turno', () => {
    const { componente, api } = preparar();
    expect(componente.turno()?.idTurno).toBe(7);
    expect(componente.historial()).toEqual(pagina);
    expect(api.movimientos).toHaveBeenCalledWith(7, 0);
    expect(componente.puedeCerrar()).toBe(true);

    const vacio = prepararSinTurno();
    expect(vacio.componente.turno()).toBeNull();
    expect(vacio.api.movimientos).not.toHaveBeenCalled();
  });

  function prepararSinTurno() {
    TestBed.resetTestingModule();
    return preparar(null);
  }

  it('informa un error de carga', () => {
    const { componente, api, avisos } = preparar();
    api.actual.mockReturnValue(throwError(() => errorHttp(0)));
    componente.cargar();
    expect(componente.errorCarga()).toBe(true);
    expect(componente.cargando()).toBe(false);
    expect(avisos.mostrar).toHaveBeenCalledWith(expect.stringContaining('No se pudo conectar'), true);
  });

  it('valida el movimiento y lo registra con una clave estable', () => {
    const { componente, api, avisos } = preparar();
    componente.abrirMovimiento(1);
    expect(componente.tipoId).toBe(1);
    componente.guardarMovimiento();
    expect(api.registrar).not.toHaveBeenCalled();
    expect(avisos.mostrar).toHaveBeenCalledWith(expect.stringContaining('Selecciona una categoría'), true);

    componente.concepto = '  Venta de agua  ';
    componente.monto = 12.5;
    componente.guardarMovimiento();
    const datos = api.registrar.mock.calls[0][1];
    expect(datos).toEqual(expect.objectContaining({ idTipoMovimiento: 1, concepto: 'Venta de agua', monto: 12.5 }));
    expect(datos.claveRegistro).toBeTruthy();
    expect(componente.editorMovimiento()).toBeNull();
    expect(avisos.mostrar).toHaveBeenCalledWith('Movimiento registrado en el turno.');
  });

  it('con varias categorías de gasto obliga a elegir una', () => {
    const { componente } = preparar();
    componente.abrirMovimiento(-1);
    expect(componente.tiposMovimiento()).toHaveLength(2);
    expect(componente.tipoId).toBeNull();
  });

  it('un reintento tras perder la conexión conserva la clave y evita envíos simultáneos', () => {
    const { componente, api, avisos } = preparar();
    const respuesta = new Subject<MovimientoCaja>();
    api.registrar.mockReturnValueOnce(throwError(() => errorHttp(0))).mockReturnValueOnce(respuesta);
    componente.abrirMovimiento(1);
    componente.concepto = 'Ingreso';
    componente.monto = 10;

    componente.guardarMovimiento();
    expect(componente.pendienteMovimiento()).toBe(true);
    expect(avisos.mostrar).toHaveBeenCalledWith(expect.stringContaining('Reintenta el mismo registro'), true);
    componente.cerrarDialogo();
    expect(componente.editorMovimiento()).toBe(1);

    componente.guardarMovimiento();
    componente.guardarMovimiento();
    expect(api.registrar).toHaveBeenCalledTimes(2);
    expect(api.registrar.mock.calls[1][1].claveRegistro).toBe(api.registrar.mock.calls[0][1].claveRegistro);
    respuesta.next({} as MovimientoCaja);
    respuesta.complete();
    expect(componente.pendienteMovimiento()).toBe(false);
    expect(componente.ocupado()).toBe(false);
  });

  it('un rechazo de validación no queda pendiente', () => {
    const { componente, api } = preparar();
    api.registrar.mockReturnValue(throwError(() => errorHttp(400, 'Concepto inválido')));
    componente.abrirMovimiento(1);
    componente.concepto = 'Ingreso';
    componente.monto = 10;
    componente.guardarMovimiento();
    expect(componente.pendienteMovimiento()).toBe(false);
  });

  it('rechaza adjuntos que no sean fotos pequeñas', () => {
    const { componente, avisos } = preparar();
    const evento = (archivo: File) => ({ target: { files: { item: () => archivo } } }) as unknown as Event;
    componente.adjuntar(evento(new File(['x'], 'nota.pdf', { type: 'application/pdf' })));
    expect(componente.archivoInvalido).toBe(true);
    expect(avisos.mostrar).toHaveBeenCalledWith(expect.stringContaining('JPG o PNG'), true);
    componente.guardarMovimiento();

    const foto = new File(['x'], 'foto.png', { type: 'image/png' });
    componente.adjuntar(evento(foto));
    expect(componente.archivo).toBe(foto);
    const entrada = { value: 'foto.png' } as HTMLInputElement;
    componente.quitarAdjunto(entrada);
    expect(entrada.value).toBe('');
    expect(componente.archivo).toBeNull();
  });

  it('calcula diferencias y cierra el turno correctamente', () => {
    const { componente, api, sesion, avisos } = preparar();
    componente.abrirCierre();
    expect(componente.declaracionesValidas()).toBe(false);
    expect(componente.diferencia(componente.turno()!.arqueos[0])).toBeNull();
    declararTodo(componente);
    expect(componente.declaracionesValidas()).toBe(true);
    expect(componente.diferencia(componente.turno()!.arqueos[0])).toBe(-1);
    expect(componente.hayDiferencias()).toBe(true);

    componente.confirmarCierre();
    expect(api.cerrar).toHaveBeenCalledWith(7, 'r'.repeat(64), [
      { idFormaPago: 1, montoDeclarado: 129.5 }, { idFormaPago: 2, montoDeclarado: 25 },
    ]);
    expect(componente.cierre()).toBeNull();
    expect(avisos.mostrar).toHaveBeenCalledWith('Turno cerrado y arqueo guardado.');
    expect(api.turno).toHaveBeenCalledWith(7);
    expect(sesion.refrescar).toHaveBeenCalled();
  });

  it('ante un 409 consulta el turno y muestra los importes actualizados', () => {
    const { componente, api, avisos } = preparar();
    api.cerrar.mockReturnValue(throwError(() => errorHttp(409, 'La revisión cambió')));
    api.turno.mockReturnValue(of(turno({ revision: 's'.repeat(64) })));
    declararTodo(componente);
    componente.confirmarCierre();
    expect(componente.cierre()).toBe('conteo');
    expect(componente.cierreIncierto()).toBe(false);
    expect(componente.turno()?.revision).toBe('s'.repeat(64));
    expect(avisos.mostrar).toHaveBeenCalledWith('La revisión cambió', true);
  });

  it('tras perder la conexión confirma si el turno ya quedó cerrado', () => {
    const { componente, api, avisos } = preparar();
    api.cerrar.mockReturnValue(throwError(() => errorHttp(0)));
    api.turno.mockReturnValue(of(turno({ abierto: false, fechaCierre: '2026-11-23T20:00:00' })));
    declararTodo(componente);
    componente.confirmarCierre();
    expect(componente.cierre()).toBeNull();
    expect(avisos.mostrar).toHaveBeenCalledWith('El turno figura cerrado. Revisa el arqueo guardado.');
  });

  it('si no puede confirmar el estado, el siguiente intento consulta en vez de reenviar', () => {
    const { componente, api, avisos } = preparar();
    api.cerrar.mockReturnValue(throwError(() => errorHttp(503, 'No disponible')));
    api.turno.mockReturnValueOnce(throwError(() => errorHttp(0)));
    declararTodo(componente);
    componente.confirmarCierre();
    expect(componente.cierreIncierto()).toBe(true);
    expect(avisos.mostrar).toHaveBeenCalledWith(expect.stringContaining('No se pudo confirmar el estado'), true);

    api.turno.mockReturnValueOnce(of(turno({ bloqueoCierre: 'Hay cobros sin verificar' })));
    componente.confirmarCierre();
    expect(api.cerrar).toHaveBeenCalledTimes(1);
    expect(avisos.mostrar).toHaveBeenCalledWith('Hay cobros sin verificar', true);
  });

  it('otros errores del cierre solo se informan', () => {
    const { componente, api, avisos } = preparar();
    api.cerrar.mockReturnValue(throwError(() => errorHttp(403, 'Sin permiso')));
    declararTodo(componente);
    componente.confirmarCierre();
    expect(api.turno).not.toHaveBeenCalled();
    expect(avisos.mostrar).toHaveBeenCalledWith('Sin permiso', true);
    componente.cerrarDialogo();
    expect(componente.cierre()).toBeNull();
  });

  it('valida importes y formatea fechas', () => {
    const { componente } = preparar();
    expect(componente.importeValido(10.25, 100)).toBe(true);
    expect(componente.importeValido(10.255, 100)).toBe(false);
    expect(componente.importeValido(-1, 100)).toBe(false);
    expect(componente.importeValido(null, 100)).toBe(false);
    expect(componente.fecha('2026-11-23T08:00:00')).toBe('2026-11-23 · 08:00');
    expect(componente.fecha(null)).toBe('—');
  });
});
