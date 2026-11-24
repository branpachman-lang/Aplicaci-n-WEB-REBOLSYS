import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, Subject, throwError } from 'rxjs';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { ReservaService, ReservaResumen } from '../../services/reserva-service';
import { ReservaList } from './reserva-list';

describe('Carga del listado de reservas', () => {
  const cuenta = { cuentas: [], saldoPendiente: 0, totalCargos: 80, totalPagado: 80 };
  const reserva = { idReserva: 1, titular: 'Cliente', detalles: [
    { idDetalleReserva: 2, estadoDetalle: 'EN_CURSO', ocupantes: [] },
  ], cuenta } as unknown as ReservaResumen;
  const pagina = { contenido: [reserva], pagina: 0, tamanio: 10, ultima: true };
  let servicio: { listar: ReturnType<typeof vi.fn>; obtener: ReturnType<typeof vi.fn>;
    cuenta: ReturnType<typeof vi.fn>; pagosPendientes: () => []; registroPendiente: () => null };
  let listarCatalogos: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    vi.useFakeTimers();
    servicio = {
      listar: vi.fn(() => of(pagina)), obtener: vi.fn(() => of(reserva)),
      cuenta: vi.fn(() => of(cuenta)), pagosPendientes: () => [], registroPendiente: () => null,
    };
    listarCatalogos = vi.fn(() => of({}));
    TestBed.configureTestingModule({
      imports: [ReservaList],
      providers: [provideRouter([]), { provide: ReservaService, useValue: servicio },
        { provide: CatalogoService, useValue: { listar: listarCatalogos } }],
    }).overrideComponent(ReservaList, { set: { template: '', imports: [] } });
  });

  afterEach(() => { TestBed.resetTestingModule(); vi.useRealTimers(); });

  it('recarga bajo demanda conservando filtros y página sin pedir cuentas ni catálogos', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    componente.pagina = 2; componente.estado = 'EN_CURSO'; componente.busqueda = '201';
    vi.advanceTimersByTime(90000);
    expect(servicio.listar).toHaveBeenCalledTimes(1);
    componente.cargar();
    expect(servicio.listar).toHaveBeenLastCalledWith(2, 10, '201', 'EN_CURSO');
    expect(servicio.listar).toHaveBeenCalledTimes(2);
    expect(listarCatalogos).not.toHaveBeenCalled();
    expect(servicio.cuenta).not.toHaveBeenCalled();
    expect(componente.cargando()).toBe(false);
  });

  it('carga catálogos al abrir comprobante y deja la cuenta al diálogo', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    componente.abrirComprobante(reserva);
    expect(componente.comprobante()).toEqual(reserva);
    expect(listarCatalogos).toHaveBeenCalledTimes(1);
    expect(servicio.cuenta).not.toHaveBeenCalled();
  });

  it('permanece sin peticiones nuevas si la red tarda', () => {
    servicio.listar.mockReturnValue(new Subject());
    TestBed.createComponent(ReservaList);
    vi.advanceTimersByTime(90000);
    expect(servicio.listar).toHaveBeenCalledTimes(1);
  });

  it('informa el fallo de carga y recupera el nuevo estado al reintentar', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    servicio.listar.mockReturnValueOnce(throwError(() => new Error('Sin conexión')));
    componente.cargar();
    expect(componente.error()).not.toBe('');
    const finalizada = { ...reserva, detalles: [{ ...reserva.detalles[0], estadoDetalle: 'FINALIZADA' }] };
    servicio.listar.mockReturnValueOnce(of({ ...pagina, contenido: [finalizada] }));
    componente.cargar();
    expect(componente.error()).toBe('');
    expect(componente.respuesta()?.contenido[0].detalles[0].estadoDetalle).toBe('FINALIZADA');
  });

  it('cancela la petición activa al abandonar la página', () => {
    const activa = new Subject(); servicio.listar.mockReturnValueOnce(activa);
    const fixture = TestBed.createComponent(ReservaList);
    expect(activa.observed).toBe(true);
    fixture.destroy();
    expect(activa.observed).toBe(false);
  });

  it('consulta el estado vigente al abrir un detalle que antes estaba en curso', () => {
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    const finalizada = { ...reserva, detalles: [{ ...reserva.detalles[0], estadoDetalle: 'FINALIZADA' }] };
    servicio.obtener.mockReturnValue(of(finalizada));
    componente.verDetalle(reserva);
    expect(servicio.obtener).toHaveBeenCalledWith(1);
    expect(servicio.cuenta).toHaveBeenCalledExactlyOnceWith(1);
    expect(componente.detalle()?.detalles[0].estadoDetalle).toBe('FINALIZADA');
  });

  it('cancela la petición anterior al iniciar otra carga', () => {
    const antigua = new Subject(); servicio.listar.mockReturnValueOnce(antigua);
    const componente = TestBed.createComponent(ReservaList).componentInstance;
    componente.cargar();
    expect(antigua.observed).toBe(false);
    antigua.next({ ...pagina, contenido: [] }); antigua.complete();
    expect(componente.respuesta()?.contenido).toHaveLength(1);
  });
});
