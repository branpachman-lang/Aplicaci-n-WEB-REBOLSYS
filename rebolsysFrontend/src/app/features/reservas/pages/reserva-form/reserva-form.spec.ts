import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter, Router } from "@angular/router";
import { HttpErrorResponse } from '@angular/common/http';
import { of } from "rxjs";
import { CatalogoService } from "../../../../core/services/catalogo-service";
import { NotificacionService } from "../../../../core/services/notificacion.service";
import { HabitacionService } from "../../../habitaciones/services/habitacion-service";
import { ReservaForm } from "./reserva-form";
import { ReservaService } from "../../services/reserva-service";
import { ClienteService } from "../../../clientes/services/cliente-service";
import { DocumentoConsultaService } from "../../../clientes/services/documento-consulta-service";
import { Subject, throwError } from "rxjs";
describe("Formulario de reserva", () => {
  const rooms = [
    {
      idHabitacion: 1,
      numero: "201",
      tipo: "Simple",
      capacidad: 1,
      precio: 80,
      estado: "DISPONIBLE",
      disponible: true,
      reservas: [],
    },
    {
      idHabitacion: 2,
      numero: "202",
      tipo: "Matrimonial",
      capacidad: 2,
      precio: 120,
      estado: "DISPONIBLE",
      disponible: true,
      reservas: [],
    },
  ];
  async function fixture(servicio: Partial<ReservaService> = {}) {
    await TestBed.configureTestingModule({
      imports: [ReservaForm],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        { provide: ReservaService, useValue: { registroPendiente: () => null, ...servicio } },
        {
          provide: CatalogoService,
          useValue: {
            listar: () =>
              of({
                documentos: [
                  { id: 1, nombre: "DNI", codigoSunat: "1" },
                  { id: 2, nombre: "Carnet de extranjería", codigoSunat: "4" },
                  { id: 3, nombre: "RUC", codigoSunat: "6" },
                  { id: 4, nombre: "Pasaporte", codigoSunat: "7" },
                ],
                ocupantes: [{ id: 1, nombre: "Titular" }], usuarios: [{id: 1, nombre: "Recepción"}], turnosAbiertos: [{idTurno: 1, idUsuario: 1, usuario: "Recepción", fechaApertura: "2026-11-03T08:00:00"}],
                pagos: [{ id: 1, nombre: "Efectivo" }],
              }),
          },
        },
        { provide: HabitacionService, useValue: { listar: () => of(rooms) } },
      ],
    }).compileComponents();
    const f = TestBed.createComponent(ReservaForm);

    f.detectChanges();
    return f;
  }
  it("al editar al adulto carga sus menores y permite corregirlos, retirarlos o añadirlos", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Adulto",
      apellidos: "Prueba",
      fechaNacimiento: "1990-01-01",
      idTipoOcupante: 1,
    });
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2020-01-01",
      parentesco: "PADRE",
    });
    c.guardarHuesped();
    c.editarHuesped(0, 0);
    expect(c.anadirMenores()).toBe(true);
    expect(c.menores.length).toBe(1);
    c.grupoMenor(0).patchValue({
      nombres: "Menor corregido",
      parentesco: "TUTOR",
    });
    c.guardarHuesped();
    expect(c.guests(0)[1].persona.nombres).toBe("Menor corregido");
    expect(c.guests(0)[1].responsable?.parentesco).toBe("TUTOR");
    c.editarHuesped(0, 0);
    c.cambiarMenores(false);
    expect(c.menoresQueSeRetiran()).toHaveLength(1);
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(1);
    c.editarHuesped(0, 0);
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "33333333",
      nombres: "Otro menor",
      apellidos: "Prueba",
      fechaNacimiento: "2020-01-01",
      parentesco: "MADRE",
    });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
    expect(c.guests(0)[1].persona.numeroDocumento).toBe("33333333");
    expect(c.lineError(0)).toBe("");
  });
  it('rechaza cruces de horario y permite ingresar exactamente a la hora de salida anterior', async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({idHabitacion: 1, fechaIngreso: '2027-01-02T12:00', fechaSalida: '2027-01-03T12:00'});
    c.rooms.update(lista => lista.map(h => h.idHabitacion !== 1 ? h : {...h, reservas: [{referencia: 'R1', cliente: 'Prueba', ingreso: '2027-01-01T14:00:00', salida: '2027-01-02T12:00:00', estado: 'CONFIRMADA'}]}));
    expect(c.lineError(0)).toBe('');
    c.habitaciones.at(0).patchValue({fechaIngreso: '2027-01-02T11:59'});
    expect(c.lineError(0)).toContain('ya tiene una reserva');
  });
  it('impide registrar una reserva de una habitación cuya tarifa está pendiente', async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({idHabitacion: 1});
    c.rooms.update(lista => lista.map(h => ({...h, precio: 0})));
    expect(c.lineError(0)).toContain('tarifa de esta habitación está pendiente');
    c.revisar();
    expect(c.pagoAbierto()).toBe(false);
  });
  it('habilita habitaciones con tarifa y calcula importes distintos aunque compartan el mismo tipo', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    c.rooms.set([
      {...rooms[0], numero:'402', tipo:'Simple con baño', precio:50},
      {...rooms[1], numero:'506', tipo:'Simple con baño', precio:40},
    ]);
    c.habitaciones.at(0).patchValue({idHabitacion:1,fechaIngreso:'2027-08-21T14:00',fechaSalida:'2027-08-23T12:00'});
    c.agregarHabitacion({idHabitacion:2,fechaIngreso:'2027-08-21T14:00',fechaSalida:'2027-08-23T12:00',huespedes:[]});
    f.detectChanges();
    expect(c.lineError(0)).toBe('');
    expect(c.lineError(1)).toBe('');
    expect(c.costoLinea(0)).toBe(100);
    expect(c.costoLinea(1)).toBe(80);
    expect(c.total()).toBe(180);
    const opciones = [...f.nativeElement.querySelectorAll('select[formcontrolname="idHabitacion"] option')] as HTMLOptionElement[];
    expect(opciones.some(o => o.textContent!.includes('402') && o.textContent!.includes('50') && !o.disabled)).toBe(true);
    expect(opciones.some(o => o.textContent!.includes('506') && o.textContent!.includes('40') && !o.disabled)).toBe(true);
  });
  it('bloquea una habitación ocupada actualmente aunque el estado físico esté desactualizado', async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({idHabitacion: 1});
    c.rooms.update(lista => lista.map(h => ({...h, ocupadaActualmente: true})));
    expect(c.lineError(0)).toContain('no está disponible');
  });
  it('reintenta registro y pago juntos con la misma clave si se pierde la respuesta', async () => {
    const r = {idReserva:30,idCliente:1,idUsuario:1,titular:'Empresa prueba',fechaRegistro:'',claveRegistro:'prueba',detalles:[]};
    const api = {claveRegistro:'prueba',idCliente:1,idUsuario:1,detalles:[]};
    const cuenta = {idReserva:30,numeroReserva:'R30',totalAlojamiento:120,totalCargos:120,totalPagado:120,saldoPendiente:0,admiteConsumosConsolidados:false,cuentas:[]};
    const crear=vi.fn().mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503}))).mockReturnValueOnce(of(r));
    const pagar=vi.fn();
    const c=(await fixture({prepararRegistro:()=>of(api),crear,pagar,cuenta:()=>of(cuenta)})).componentInstance;
    vi.spyOn(TestBed.inject(Router),'navigate').mockResolvedValue(true);
    c.cliente.patchValue({empresa:true,idTipoDocumento:3,numeroDocumento:'20123456789',razonSocial:'Empresa prueba'});
    c.validarCliente();c.habitaciones.at(0).patchValue({idHabitacion:2});c.revisar();
    const cobro={idUsuario:1,idTurno:1,idFormaPago:1,numeroOperacion:null};
    c.confirmar({forma:'EFECTIVO',recibido:50,monto:40,cobro});
    expect(crear).not.toHaveBeenCalled();
    expect(c.reservaRegistrada()).toBe(null);
    const pago={forma:'EFECTIVO' as const,recibido:150,monto:c.total(),cobro};
    c.confirmar(pago);expect(c.reservaRegistrada()).toBe(null);expect(c.pagoFijado()).toBe(true);
    c.confirmar(pago);expect(crear).toHaveBeenCalledTimes(2);
    expect(crear.mock.calls[1]).toEqual(crear.mock.calls[0]);
    expect(crear.mock.calls[0][0].cobro).toEqual({idFormaPago:1,monto:120,numeroOperacion:null});
    expect(pagar).not.toHaveBeenCalled();expect(c.reservaRegistrada()?.idReserva).toBe(30);
  });
  it("suma varias habitaciones y permite quitar una sin perder las otras", async () => {
    const f = await fixture();
    const c = f.componentInstance;
    c.habitaciones.at(0).patchValue({
      idHabitacion: 1,
      fechaIngreso: "2026-10-25T14:00",
      fechaSalida: "2026-10-26T12:00",
    });
    c.agregarHabitacion({
      idHabitacion: 2,
      fechaIngreso: "2026-10-25T14:00",
      fechaSalida: "2026-10-26T12:00",
      huespedes: [],
    });
    expect(c.total()).toBe(200);
    expect(c.yaElegida(1, 1)).toBe(true);
    c.quitarHabitacion(1);
    expect(c.total()).toBe(80);
    expect(c.habitaciones.length).toBe(1);
  });
  it("no abre el pago si faltan datos o las fechas son inválidas", async () => {
    const f = await fixture();
    const c = f.componentInstance;
    c.revisar();
    expect(c.pagoAbierto()).toBe(false);
    expect(TestBed.inject(NotificacionService).actual()?.error).toBe(true);
  });
  it.each([
    [1, '10101010', '10101010'],
    [2, '000123456', '000123456'],
    [4, 'AB01234', ' ab01234 '],
  ])('bloquea el documento de tipo %s entre habitaciones antes de insertar al huésped', async (tipo, primero, repetido) => {
    const c = (await fixture()).componentInstance;
    const persona = { idTipoDocumento: tipo, nombres: 'Ana', apellidos: 'Prueba', fechaNacimiento: '1990-01-01' };
    c.habitaciones.at(0).patchValue({ idHabitacion: 1 });
    c.agregarHabitacion({ idHabitacion: 2, fechaIngreso: '2027-01-02T14:00', fechaSalida: '2027-01-03T12:00', huespedes: [] });
    c.abrirHuesped(0);
    c.guestForm.patchValue({ ...persona, numeroDocumento: primero });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(1);
    c.abrirHuesped(1);
    c.guestForm.patchValue({ ...persona, numeroDocumento: repetido, nombres: 'Otro nombre' });
    c.guardarHuesped();
    expect(c.guests(1)).toHaveLength(0);
    expect(c.huespedEn()).toBe(1);
    expect(TestBed.inject(NotificacionService).actual()?.texto).toContain('documento ya está registrado');
  });

  it('acepta al titular como huésped y bloquea el mismo celular para otra persona durante ocho segundos', async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.cliente.patchValue({ idTipoDocumento: 1, numeroDocumento: '10101010', nombres: 'Ana', apellidos: 'Prueba', fechaNacimiento: '1990-01-01', telefono: '987654321' });
    c.abrirHuesped(0);
    c.usarCliente();
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(1);
    c.abrirHuesped(0);
    c.guestForm.patchValue({ idTipoDocumento: 1, numeroDocumento: '20202020', nombres: 'Otra', apellidos: 'Persona', fechaNacimiento: '1990-01-01', telefono: '+51 987 654 321' });
    vi.useFakeTimers();
    try {
      c.guardarHuesped();
      const avisos = TestBed.inject(NotificacionService);
      expect(c.guests(0)).toHaveLength(1);
      expect(avisos.actual()).toMatchObject({ error: true, texto: expect.stringContaining('celular') });
      vi.advanceTimersByTime(7999);
      expect(avisos.actual()).not.toBeNull();
      vi.advanceTimersByTime(1);
      expect(avisos.actual()).toBeNull();
    } finally {
      vi.useRealTimers();
    }
    c.guestForm.patchValue({ telefono: '923456789' });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
  });

  it("la casilla empieza marcada y conserva la elección al editar al menor", async () => {
    const f = await fixture();
    const c = f.componentInstance;
    c.rooms.update((lista) =>
      lista.map((h) =>
        h.idHabitacion === 1
          ? { ...h, capacidad: 2, plazasCama: 1, maxMenoresSinCama: 1 }
          : h,
      ),
    );
    c.habitaciones.at(0).patchValue({
      idHabitacion: 1,
      fechaIngreso: "2031-01-15T14:00",
      fechaSalida: "2031-01-16T12:00",
    });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Adulto",
      apellidos: "Prueba",
      fechaNacimiento: "1990-01-01",
      idTipoOcupante: 1,
    });
    c.cambiarMenores(true);
    const menor = c.grupoMenor(0);
    menor.patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2025-01-15",
      parentesco: "PADRE",
    });
    expect(menor.get("ocupaPlazaCama")!.enabled).toBe(true);
    expect(menor.get("ocupaPlazaCama")!.value).toBe(true);
    menor.get("ocupaPlazaCama")!.setValue(false);
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
    expect(c.guests(0)[1].ocupaPlazaCama).toBe(false);
    expect(c.resumenOcupacion(0)).toContain("1/1 plazas");
    f.detectChanges();
    await f.whenStable();
    const responsable = f.nativeElement.querySelector(
      ".minor-responsible select",
    ) as HTMLSelectElement;
    expect(responsable.value).toBe("1:11111111");
    c.editarHuesped(0, 1);
    expect(c.guestForm.get("ocupaPlazaCama")!.value).toBe(false);
    c.guestForm.get("ocupaPlazaCama")!.setValue(true);
    c.guardarHuesped();
    expect(TestBed.inject(NotificacionService).actual()?.texto).toContain("plazas de cama");
    c.guestForm.get("ocupaPlazaCama")!.setValue(false);
    c.guardarHuesped();
    expect(c.lineError(0)).toBe("");
    c.habitaciones.at(0).patchValue({
      fechaIngreso: "2032-01-15T14:00",
      fechaSalida: "2032-01-16T12:00",
    });
    expect(c.lineError(0)).toContain("menores de 7");
  });
  it("no habilita excepciones ni desmarca automáticamente para una habitación sin configurar", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones
      .at(0)
      .patchValue({ idHabitacion: 1, fechaIngreso: "2031-01-15T14:00" });
    c.abrirHuesped(0);
    c.cambiarMenores(true);
    const menor = c.grupoMenor(0);
    menor.patchValue({ fechaNacimiento: "2025-01-15" });
    expect(menor.get("ocupaPlazaCama")!.disabled).toBe(true);
    expect(menor.get("ocupaPlazaCama")!.value).toBe(true);
    expect(c.notaCama(menor)).toContain("no admite");
  });
  it('permite desmarcar antes de los siete años y obliga plaza propia desde el cumpleaños',async()=>{
    const c=(await fixture()).componentInstance;
    c.rooms.update(lista=>lista.map(h=>({...h,capacidad:3,plazasCama:2,maxMenoresSinCama:1})));
    c.habitaciones.at(0).patchValue({idHabitacion:1,fechaIngreso:'2031-07-17T14:00'});c.abrirHuesped(0);c.cambiarMenores(true);
    const m=c.grupoMenor(0);m.patchValue({fechaNacimiento:'2025-07-17'});
    expect(m.get('ocupaPlazaCama')!.enabled).toBe(true);m.get('ocupaPlazaCama')!.setValue(false);
    m.patchValue({fechaNacimiento:'2024-07-17'});
    expect(m.get('ocupaPlazaCama')!.disabled).toBe(true);expect(m.get('ocupaPlazaCama')!.value).toBe(true);expect(c.notaCama(m)).toContain('Desde los 7');
    m.patchValue({fechaNacimiento:'2025-07-17'});expect(m.get('ocupaPlazaCama')!.enabled).toBe(true);
  });
  it("permite empresas y solo abre el pago después de revisar datos completos", async () => {
    const f = await fixture();
    const c = f.componentInstance;
    c.cliente.patchValue({
      idTipoDocumento: 3,
      numeroDocumento: "20123456789",
      empresa: true,
      razonSocial: "Empresa de Prueba",
    });
    c.validarCliente();
    c.habitaciones.at(0).patchValue({
      idHabitacion: 2,
      fechaIngreso: "2026-10-25T14:00",
      fechaSalida: "2026-10-26T12:00",
    });
    c.revisar();
    expect(c.pagoAbierto()).toBe(true);
  });

  it("valida DNI y RUC con longitudes específicas", async () => {
    const c = (await fixture()).componentInstance;
    c.cliente.patchValue({ idTipoDocumento: 1, numeroDocumento: "1234567" });
    expect(c.cliente.get("numeroDocumento")!.invalid).toBe(true);
    c.cliente.patchValue({ numeroDocumento: "12345678" });
    expect(c.cliente.get("numeroDocumento")!.valid).toBe(true);
    c.cliente.patchValue({ idTipoDocumento: 3, numeroDocumento: "2012345678" });
    expect(c.cliente.get("numeroDocumento")!.invalid).toBe(true);
    c.cliente.patchValue({ numeroDocumento: "20123456789" });
    expect(c.cliente.get("numeroDocumento")!.valid).toBe(true);
  });
  it("rechaza un titular menor y una fecha de nacimiento futura", async () => {
    const c = (await fixture()).componentInstance;
    c.cliente.patchValue({ fechaNacimiento: "2015-01-01" });
    c.validarCliente();
    expect(c.cliente.get("fechaNacimiento")!.hasError("titularMenor")).toBe(
      true,
    );
    c.cliente.patchValue({ fechaNacimiento: "2999-01-01" });
    expect(c.cliente.get("fechaNacimiento")!.hasError("fechaNacimiento")).toBe(
      true,
    );
  });
  it("agrega adulto y menor en la misma habitación y exige reasignar antes de quitar al adulto", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Adulto",
      apellidos: "Prueba",
      fechaNacimiento: "1990-01-01",
      idTipoOcupante: 1,
    });
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2015-01-01",
      parentesco: "MADRE",
    });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
    expect(c.guests(0)[1].responsable).toEqual({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      parentesco: "MADRE",
    });
    expect(c.lineError(0)).toBe("");
    c.quitarHuesped(0, 0);
    expect(c.guests(0)).toHaveLength(2);
    expect(TestBed.inject(NotificacionService).actual()?.texto).toContain("menores");
  });
  it("no guarda menores solos ni excede la capacidad al añadir una familia", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 1 });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2015-01-01",
      idTipoOcupante: 1,
    });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(0);
    c.guestForm.patchValue({ fechaNacimiento: "1990-01-01" });
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2015-01-01",
      parentesco: "PADRE",
    });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(0);
    expect(TestBed.inject(NotificacionService).actual()?.texto).toContain("capacidad");
  });
  it("consulta la API cuando no existe el cliente local y completa nombres sin inventar la fecha", async () => {
    const c = (await fixture()).componentInstance;
    vi.spyOn(
      TestBed.inject(ClienteService),
      "buscarPorDocumento",
    ).mockReturnValue(throwError(() => ({ status: 404 })));
    const api = vi
      .spyOn(TestBed.inject(DocumentoConsultaService), "consultar")
      .mockReturnValue(
        of({
          numeroDocumento: "11111111",
          nombres: "PERSONA DE PRUEBA",
          apellidos: "LOCAL DEMO",
          razonSocial: null,
          direccionFiscal: null,
        }),
      );
    c.cliente.patchValue({ numeroDocumento: "11111111" });
    c.buscarCliente();
    expect(api).toHaveBeenCalledWith("DNI", "11111111");
    expect(c.cliente.get("nombres")!.value).toBe("PERSONA DE PRUEBA");
    expect(c.cliente.get("fechaNacimiento")!.value).toBe("");
  });
  it("descarta una respuesta si se cambió el documento mientras consultaba", async () => {
    const c = (await fixture()).componentInstance;
    const respuesta = new Subject<any>();
    vi.spyOn(
      TestBed.inject(ClienteService),
      "buscarPorDocumento",
    ).mockReturnValue(respuesta);
    c.cliente.patchValue({ numeroDocumento: "11111111" });
    c.buscarCliente();
    c.cliente.patchValue({ numeroDocumento: "22222222" });
    respuesta.next({ datos: { nombres: "ANTERIOR" } });
    expect(c.cliente.get("nombres")!.value).toBe("");
  });

  it("permite editar un menor sin duplicarlo y conserva su responsable", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Adulto",
      apellidos: "Prueba",
      fechaNacimiento: "1990-01-01",
      idTipoOcupante: 1,
    });
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2015-01-01",
      parentesco: "PADRE",
    });
    c.guardarHuesped();
    c.editarHuesped(0, 1);
    c.guestForm.patchValue({
      nombres: "Menor corregido",
      numeroDocumento: "33333333",
    });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
    expect(c.guests(0)[1].persona.nombres).toBe("Menor corregido");
    expect(c.guests(0)[1].responsable?.numeroDocumento).toBe("11111111");
  });
  it("al corregir el documento del adulto actualiza el vínculo de sus menores", async () => {
    const c = (await fixture()).componentInstance;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.abrirHuesped(0);
    c.guestForm.patchValue({
      idTipoDocumento: 1,
      numeroDocumento: "11111111",
      nombres: "Adulto",
      apellidos: "Prueba",
      fechaNacimiento: "1990-01-01",
      idTipoOcupante: 1,
    });
    c.cambiarMenores(true);
    c.grupoMenor(0).patchValue({
      numeroDocumento: "22222222",
      nombres: "Menor",
      apellidos: "Prueba",
      fechaNacimiento: "2015-01-01",
      parentesco: "PADRE",
    });
    c.guardarHuesped();
    c.editarHuesped(0, 0);
    c.guestForm.patchValue({ numeroDocumento: "33333333" });
    c.guardarHuesped();
    expect(c.guests(0)).toHaveLength(2);
    expect(c.guests(0)[1].responsable?.numeroDocumento).toBe("33333333");
    expect(c.lineError(0)).toBe("");
  });

});
