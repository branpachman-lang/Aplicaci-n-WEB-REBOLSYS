import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ReservaService } from './reserva-service';
import { Registro } from '../models/reserva.model';
import { RegistroReservaApi } from '../models/reserva-api.model';
import { CatalogoService } from '../../../core/services/catalogo-service';

describe('Contrato de reservas con el backend actual', () => {
  let servicio: ReservaService;
  let http: HttpTestingController;
  const base = '/api/v1';
  const persona = { idTipoDocumento: 124, numeroDocumento: '90000001', empresa: false,
    nombres: 'Adulto', apellidos: 'Prueba', fechaNacimiento: '1990-01-01',
    razonSocial: null, direccionFiscal: null, correo: null, telefono: null };
  const solicitud: RegistroReservaApi = { claveRegistro: 'reserva-1', idCliente: 1, idUsuario: 130,
    detalles: [{ idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00', costoEstadia: 118, ocupantes: [] }] };
  const cobro = { idUsuario: 130, idTurno: 101, idFormaPago: 1, numeroOperacion: null };
  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    servicio = TestBed.inject(ReservaService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => { http.verify(); sessionStorage.clear(); });

  it('registra el ingreso de una habitación con el usuario de la sesión actual',()=>{
    servicio.ingresar(495).subscribe();
    http.expectOne(base+'/sesion/actual').flush({idUsuario:130,usuario:'recepcion.flujo',modo:'ACTIVA',idTurno:101,idFormaPagoEfectivo:1});
    const ingreso=http.expectOne(base+'/reservas/detalles/495/ingreso');
    expect(ingreso.request.method).toBe('POST');expect(ingreso.request.body).toEqual({idUsuario:130});
    ingreso.flush({idDetalleReserva:495,estadoDetalle:'EN_CURSO'});
  });

  it('adapta documentos, pagos, usuarios y turnos sin inventar identificadores de ocupantes', () => {
    const next = vi.fn();
    TestBed.inject(CatalogoService).listar().subscribe(next);
    http.expectOne(base+'/sesion/actual').flush({idUsuario:130,usuario:'recepcion.flujo',modo:'ACTIVA',idTurno:101,idFormaPagoEfectivo:1});
    http.expectOne(base + '/recepcion/catalogos').flush({ tiposDocumento: [{ id: 124, nombre: 'DNI' }], formasPago: [{ id: 1, nombre: 'EFECTIVO' }], usuarios: [{ id: 130, nombre: 'Recepción' }], turnosAbiertos: [{ idTurno: 101, idUsuario: 130 }] });
    expect(next.mock.calls[0][0]).toMatchObject({ documentos: [{ id: 124 }], pagos: [{ id: 1 }], usuarios: [{ id: 130 }], turnosAbiertos: [{ idTurno: 101 }], ocupantes: [] });
  });

  it('reutiliza al titular como adulto, crea al menor y envía su responsable por idCliente', () => {
    const datos: Registro = { claveRegistro: 'familia', cliente: persona, formaRegistro: 'PENDIENTE', efectivoRecibido: null,
      habitaciones: [{ idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00', costoEstadia: 118,
        huespedes: [{ persona, idTipoOcupante: 0 }, { persona: { ...persona, numeroDocumento: '90000002', nombres: 'Menor', fechaNacimiento: '2024-01-01' }, idTipoOcupante: 0,
          ocupaPlazaCama: false, responsable: { idTipoDocumento: 124, numeroDocumento: '90000001', parentesco: 'MADRE' } }] }] };
    const next = vi.fn();
    servicio.prepararRegistro(datos, 130).subscribe(next);
    http.expectOne((r) => r.url === base + '/recepcion/clientes' && r.params.get('numeroDocumento') === '90000001').flush({ idCliente: 1, datos: persona });
    http.expectOne((r) => r.params.get('numeroDocumento') === '90000002').flush({ message: 'No existe' }, { status: 404, statusText: 'Not Found' });
    const nuevo = http.expectOne(base + '/clientes');
    expect(nuevo.request.body.persona.nombres).toBe('Menor');
    nuevo.flush({ idCliente: 2 });
    expect(next.mock.calls[0][0]).toEqual({ claveRegistro: 'familia', idCliente: 1, idUsuario: 130,
      detalles: [{ idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00', costoEstadia: 118,
        ocupantes: [{ idCliente: 1, idClienteResponsable: null, parentescoResponsable: null, ocupaPlazaCama: true },
          { idCliente: 2, idClienteResponsable: 1, parentescoResponsable: 'MADRE', ocupaPlazaCama: false }] }] });
  });

  it('completa un nacimiento pendiente conservando la ficha y restricciones existentes', () => {
    const datos: Registro = {claveRegistro: 'nacimiento', cliente: persona, habitaciones: [{idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00', costoEstadia: 118, huespedes: []}], formaRegistro: 'PENDIENTE', efectivoRecibido: null};
    const next = vi.fn();
    servicio.prepararRegistro(datos, 130).subscribe(next);
    http.expectOne(r => r.url === base + '/recepcion/clientes').flush({idCliente: 1, datos: {...persona, fechaNacimiento: null}});
    http.expectOne(base + '/clientes/1').flush({idCliente: 1, persona: {...persona, fechaNacimiento: null, telefono: '900000000'}, restriccionesMedicas: 'Dato ficticio que debe conservarse'});
    const actualizar = http.expectOne(base + '/clientes/1');
    expect(actualizar.request.method).toBe('PUT');
    expect(actualizar.request.body).toMatchObject({persona: {fechaNacimiento: '1990-01-01', telefono: '900000000'}, restriccionesMedicas: 'Dato ficticio que debe conservarse'});
    actualizar.flush({idCliente: 1});
    expect(next.mock.calls[0][0].idCliente).toBe(1);
  });

  it('rechaza identidades contradictorias en el borrador antes de crear fichas', () => {
    const datos: Registro = {claveRegistro: 'contradictorio', cliente: persona, habitaciones: [{idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00', costoEstadia: 118, huespedes: [{persona: {...persona, fechaNacimiento: '1980-01-01'}, idTipoOcupante: 0}]}], formaRegistro: 'PENDIENTE', efectivoRecibido: null};
    const error = vi.fn();
    servicio.prepararRegistro(datos,130).subscribe({error});
    expect(error.mock.calls[0][0].message).toContain('mismo documento con datos distintos');
  });

  it('rechaza un RUC repetido antes de consultar o registrar clientes', () => {
    const empresa = { ...persona, idTipoDocumento: 125, numeroDocumento: '20123456789', empresa: true };
    const datos: Registro = {
      claveRegistro: 'duplicado', cliente: empresa, formaRegistro: 'PENDIENTE', efectivoRecibido: null,
      habitaciones: [{ idHabitacion: 1, fechaIngreso: '2027-01-01T14:00', fechaSalida: '2027-01-02T12:00',
        huespedes: [{ persona: { ...empresa, empresa: false, nombres: 'Otro' }, idTipoOcupante: 0 }] }],
    };
    const error = vi.fn();
    servicio.prepararRegistro(datos, 130).subscribe({ error });
    expect(error.mock.calls[0][0].message).toContain('mismo documento con datos distintos');
    http.expectNone(request => request.url.startsWith(base));
  });

  it('conserva la misma reserva al perderse la confirmación y la recupera sin crear otra clave', () => {
    servicio.crear(solicitud).subscribe({ error: () => {} });
    http.expectOne(base + '/reservas').error(new ProgressEvent('error'));
    expect(servicio.registroPendiente()).toEqual(solicitud);
    const otraInstancia = TestBed.runInInjectionContext(() => new ReservaService());
    expect(otraInstancia.registroPendiente()).toEqual(solicitud);
    otraInstancia.crear(otraInstancia.registroPendiente()!).subscribe();
    const retry = http.expectOne(base + '/reservas');
    expect(retry.request.body).toEqual(solicitud);
    retry.flush({ idReserva: 10 });
    expect(otraInstancia.registroPendiente()).toBeNull();
  });

  it('registra solo el abono y conserva un pago incierto para recuperarlo después de recargar', () => {
    servicio.pagar(10, 'pago-1', 50, cobro).subscribe({ error: () => {} });
    const req = http.expectOne(base + '/reservas/10/pagos');
    expect(req.request.body).toEqual({ claveOperacion: 'pago-1', monto: 50, ...cobro });
    req.error(new ProgressEvent('error'));
    const otraInstancia = TestBed.runInInjectionContext(() => new ReservaService());
    const pendiente = otraInstancia.pagosPendientes()[0];
    expect(pendiente).toEqual({ idReserva: 10, claveOperacion: 'pago-1', monto: 50, cobro });
    otraInstancia.pagar(10, pendiente.claveOperacion, pendiente.monto, pendiente.cobro).subscribe();
    const retry = http.expectOne(base + '/reservas/10/pagos');
    expect(retry.request.body).toEqual(req.request.body);
    retry.flush({ idReserva: 10, saldoPendiente: 68 });
    expect(otraInstancia.pagosPendientes()).toEqual([]);
  });

  it('un rechazo de negocio permite corregir el pago y no conserva un cobro inexistente', () => {
    servicio.pagar(10, 'pago-1', 500, cobro).subscribe({ error: () => {} });
    http.expectOne(base + '/reservas/10/pagos').flush({ message: 'Sobrepago' }, { status: 409, statusText: 'Conflict' });
    expect(servicio.pagosPendientes()).toEqual([]);
  });

  it('filtra todas las páginas y consulta únicamente la cuenta de la reserva coincidente', () => {
    const next = vi.fn();
    servicio.listar(0, 10, 'R11').subscribe(next);
    const reserva = (id: number) => ({ idReserva: id, titular: 'Prueba', detalles: [{ numeroCuarto: '101', estadoDetalle: 'CONFIRMADA' }] });
    http.expectOne((r) => r.url === base + '/reservas' && r.params.get('pagina') === '0').flush({ contenido: [reserva(10)], pagina: 0, ultima: false });
    http.expectOne((r) => r.url === base + '/reservas' && r.params.get('pagina') === '1').flush({ contenido: [reserva(11)], pagina: 1, ultima: true });
    http.expectOne(base + '/reservas/11/cuenta').flush({ idReserva: 11, saldoPendiente: 118 });
    expect(next.mock.calls[0][0]).toMatchObject({ totalElementos: 1, contenido: [{ idReserva: 11, cuenta: { saldoPendiente: 118 } }] });
  });
});
