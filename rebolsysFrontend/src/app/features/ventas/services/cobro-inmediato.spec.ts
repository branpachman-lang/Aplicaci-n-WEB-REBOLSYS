import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { of } from 'rxjs';
import { VentaService } from './venta-service';
import { VentaRequest } from '../models/venta.model';
import { ReservaService } from '../../reservas/services/reserva-service';
import { RegistroReservaApi } from '../../reservas/models/reserva-api.model';
import { SesionService, SesionActual } from '../../../core/auth/sesion-service';
import { environment } from '../../../../environments/environment';

describe('Registro con cobro inmediato', () => {
  const base = environment.apiUrl;
  const sesion: SesionActual = { idUsuario: 3, usuario: 'recepcion', modo: 'ACTIVA', idTurno: 8, idFormaPagoEfectivo: 1 };
  let http: HttpTestingController;
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting(), {
      provide: SesionService,
      useValue: { actual: signal(sesion), versionPendientes: signal(0), cargar: () => of(sesion) },
    }] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });

  const venta = (): VentaRequest => ({ claveRegistro: 'venta-estable', modoVenta: 'SEPARADA',
    idDetalleReserva: null, idCliente: null, compradorNombre: 'Lucía Rojas',
    detalles: [{ idProducto: 1, cantidad: 2, precioEsperado: 3.5 }], efectivoRecibido: 10 });

  it('envía productos y cobro en un único POST; después solo consulta la cuenta', () => {
    const servicio = TestBed.inject(VentaService);
    let resultado: any;
    servicio.registrar(venta()).subscribe(v => resultado = v);
    const registro = http.expectOne(base + '/ventas/confirmadas');
    expect(registro.request.method).toBe('POST');
    expect(registro.request.body.cobro).toEqual({ idFormaPago: 1, monto: 7, numeroOperacion: null });
    registro.flush({ idVenta: 10, idUsuario: 3, modalidadCobro: 'SEPARADA', estadoVenta: 'CONFIRMADA',
      total: 7, idDetalleReserva: null, detalles: [] });
    http.expectOne(base + '/productos/catalogo').flush([]);
    http.expectOne(base + '/ventas/10/cuenta').flush({ pagado: 7, saldo: 0, estado: 'BORRADOR', pagos: [] });
    http.expectNone(r => r.method === 'POST' && r.url.endsWith('/pagos'));
    expect(resultado.estadoCobro).toBe('PAGADO');
    expect(resultado.vuelto).toBe(3);
    expect(servicio.pendiente()).toBe(null);
  });

  it('conserva solicitud y clave tras una respuesta perdida; una venta asociada también lleva cobro', () => {
    const servicio = TestBed.inject(VentaService);
    const datos = { ...venta(), idDetalleReserva: 42 };
    servicio.registrar(datos).subscribe({ error: () => {} });
    const primera = http.expectOne(base + '/ventas/confirmadas');
    const cuerpo = primera.request.body;
    expect(cuerpo.modalidadCobro).toBe('RESERVA');
    expect(cuerpo.cobro.monto).toBe(7);
    primera.flush('Respuesta perdida', { status: 503, statusText: 'Service Unavailable' });
    expect(servicio.pendiente()?.datos.claveRegistro).toBe(datos.claveRegistro);
    servicio.recuperar().subscribe({ error: () => {} });
    const segunda = http.expectOne(base + '/ventas/confirmadas');
    expect(segunda.request.body).toEqual(cuerpo);
    segunda.flush({}, { status: 409, statusText: 'Conflict' });
    expect(servicio.pendiente()).toBe(null);
  });

  it('no envía una venta cuando el efectivo no cubre el total', () => {
    let error = '';
    TestBed.inject(VentaService).registrar({ ...venta(), efectivoRecibido: 2 })
      .subscribe({ error: e => error = e.message });
    expect(error).toContain('cubrir el total');
    expect(TestBed.inject(VentaService).pendiente()).toBe(null);
    http.expectNone(base + '/ventas/confirmadas');
  });

  it('recupera el pago de una venta anterior sin volver a registrar ni descontar productos', () => {
    const servicio = TestBed.inject(VentaService);
    servicio.pendiente.set({ datos: venta(), idVenta: 10, pago: { claveOperacion: 'pago-anterior',
      idUsuario: 3, idTurno: 8, idFormaPago: 1, monto: 7, numeroOperacion: null } });
    servicio.recuperar().subscribe();
    http.expectOne(base + '/ventas/10').flush({ idVenta: 10, idUsuario: 3, modalidadCobro: 'SEPARADA',
      estadoVenta: 'CONFIRMADA', total: 7, idDetalleReserva: null, detalles: [] });
    const pago = http.expectOne(base + '/ventas/10/pagos');
    expect(pago.request.body.claveOperacion).toBe('pago-anterior');
    pago.flush({ pagado: 7, saldo: 0, estado: 'BORRADOR', pagos: [] });
    http.expectOne(base + '/productos/catalogo').flush([]);
    http.expectNone(base + '/ventas/confirmadas');
    expect(servicio.pendiente()).toBe(null);
  });

  it('recupera la reserva con el mismo pago en el registro, sin solicitar un abono separado', () => {
    const servicio = TestBed.inject(ReservaService);
    const datos: RegistroReservaApi = { claveRegistro: 'reserva-estable', idCliente: 2, idUsuario: 3,
      detalles: [], cobro: { idFormaPago: 1, monto: 80, numeroOperacion: null } };
    servicio.crear(datos).subscribe({ error: () => {} });
    http.expectOne(base + '/reservas').flush('', { status: 503, statusText: 'Service Unavailable' });
    expect(servicio.registroPendiente()).toEqual(datos);
    servicio.crear(servicio.registroPendiente()!).subscribe();
    const segunda = http.expectOne(base + '/reservas');
    expect(segunda.request.body).toEqual(datos);
    segunda.flush({ idReserva: 20 });
    expect(servicio.registroPendiente()).toBe(null);
    http.expectNone(r => r.method === 'POST' && r.url.endsWith('/pagos'));
  });
});
