import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { ReservaService, ReservaResumen } from '../../services/reserva-service';
import { ReservaList } from './reserva-list';

describe('Seguimiento y check-out de reservas', () => {
  const cuenta = { cuentas: [], saldoPendiente: 0, totalCargos: 80, totalPagado: 80 };
  const reserva = { idReserva: 1, titular: 'Cliente', detalles: [
    { idDetalleReserva: 2, estadoDetalle: 'EN_CURSO', ocupantes: [] },
  ], cuenta } as unknown as ReservaResumen;
  const pagina = { contenido: [reserva], pagina: 0, tamanio: 10, ultima: true };
  let servicio: { listar: ReturnType<typeof vi.fn>; obtener: ReturnType<typeof vi.fn>;
    cuenta: ReturnType<typeof vi.fn>; salir: ReturnType<typeof vi.fn>;
    pagosPendientes: () => []; registroPendiente: () => null };

  beforeEach(() => {
    vi.useFakeTimers();
    servicio = {
      listar: vi.fn(() => of(pagina)), obtener: vi.fn(() => of(reserva)),
      cuenta: vi.fn(() => of(cuenta)), salir: vi.fn(() => of(reserva.detalles[0])),
      pagosPendientes: () => [], registroPendiente: () => null,
    };
    TestBed.configureTestingModule({
      imports: [ReservaList],
      providers: [provideRouter([]), { provide: ReservaService, useValue: servicio },
        { provide: CatalogoService, useValue: { listar: () => of({}) } }],
    }).overrideComponent(ReservaList, { set: { template: '', imports: [] } });
  });

  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('actualiza automáticamente cada 30 segundos conservando filtros y página', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    componente.pagina = 2; componente.estado = 'EN_CURSO'; componente.busqueda = '201';
    vi.advanceTimersByTime(30000);
    expect(servicio.listar).toHaveBeenLastCalledWith(2, 10, '201', 'EN_CURSO');
    expect(servicio.listar).toHaveBeenCalledTimes(2);
    expect(componente.cargando()).toBe(false);
  });

  it('pausa las consultas mientras hay detalle, pago, comprobante u operación activa', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    for (const modal of [componente.detalle, componente.pagando, componente.comprobante]) {
      modal.set(reserva); vi.advanceTimersByTime(30000); modal.set(null);
    }
    componente.ocupado.set(true); vi.advanceTimersByTime(30000); componente.ocupado.set(false);
    expect(servicio.listar).toHaveBeenCalledTimes(1);
    vi.advanceTimersByTime(30000);
    expect(servicio.listar).toHaveBeenCalledTimes(2);
  });

  it('no solapa peticiones cuando la red tarda más que el intervalo', () => {
    servicio.listar.mockReturnValue(new Subject());
    TestBed.createComponent(ReservaList);
    vi.advanceTimersByTime(90000);
    expect(servicio.listar).toHaveBeenCalledTimes(1);
  });

  it('conserva la tabla ante un fallo de fondo y recupera el nuevo estado en el siguiente ciclo', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    servicio.listar.mockReturnValueOnce(throwError(() => new Error('Sin conexión')));
    vi.advanceTimersByTime(30000);
    expect(componente.respuesta()?.contenido[0]).toEqual(reserva);
    expect(componente.error()).toBe('');
    const finalizada = { ...reserva, detalles: [{ ...reserva.detalles[0], estadoDetalle: 'FINALIZADA' }] };
    servicio.listar.mockReturnValueOnce(of({ ...pagina, contenido: [finalizada] }));
    vi.advanceTimersByTime(30000);
    expect(componente.respuesta()?.contenido[0].detalles[0].estadoDetalle).toBe('FINALIZADA');
  });

  it('cancela la actualización periódica al abandonar la página', () => {
    const fixture = TestBed.createComponent(ReservaList);
    fixture.destroy(); vi.advanceTimersByTime(90000);
    expect(servicio.listar).toHaveBeenCalledTimes(1);
  });

  it('consulta el estado vigente al abrir un detalle que antes estaba en curso', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    const finalizada = { ...reserva, detalles: [{ ...reserva.detalles[0], estadoDetalle: 'FINALIZADA' }] };
    servicio.obtener.mockReturnValue(of(finalizada));
    componente.verDetalle(reserva);
    expect(servicio.obtener).toHaveBeenCalledWith(1);
    expect(componente.detalle()?.detalles[0].estadoDetalle).toBe('FINALIZADA');
  });

  it('reintenta el check-out con el mismo importe y clave tras un fallo de red', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    vi.spyOn(componente.acceso, 'puedeCobrarReserva').mockReturnValue(true);
    const conSaldo = { ...reserva, cuenta: { ...reserva.cuenta, saldoPendiente: 74,
      cuentas: [{ idVenta: null, saldo: 74 }] as ReservaResumen['cuenta']['cuentas'] } };
    servicio.salir.mockReturnValueOnce(throwError(() => ({ status: 503 })));
    componente.abrirSalida(conSaldo, conSaldo.detalles[0]);
    const pago = { forma: 'EFECTIVO' as const, recibido: 80, monto: 74,
      cobro: { idUsuario: 7, idTurno: 13, idFormaPago: 1, numeroOperacion: null } };
    componente.pagar(pago);
    expect(componente.pagoFijado()).toBe(true);
    componente.pagar(pago);
    expect(servicio.salir).toHaveBeenCalledTimes(2);
    expect(servicio.salir.mock.calls[1]).toEqual(servicio.salir.mock.calls[0]);
    expect(servicio.salir.mock.calls[0][0]).toEqual({
      idReserva: 1, idDetalleReserva: 2, claveOperacion: expect.any(String),
      cobro: { idFormaPago: 1, monto: 74, numeroOperacion: null },
    });
    expect(componente.pagando()).toBeNull();
  });

  it('ignora una respuesta antigua si una recarga más reciente ya terminó', () => {
    const antigua = new Subject(); servicio.listar.mockReturnValueOnce(antigua);
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    componente.cargar();
    antigua.next({ ...pagina, contenido: [] }); antigua.complete();
    expect(componente.respuesta()?.contenido).toHaveLength(1);
  });
});
