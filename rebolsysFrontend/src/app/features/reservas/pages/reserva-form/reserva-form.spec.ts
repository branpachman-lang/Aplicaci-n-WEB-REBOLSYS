import { TestBed } from "@angular/core/testing";
import { provideHttpClient } from "@angular/common/http";
import { provideRouter, Router } from "@angular/router";
import { HttpErrorResponse } from '@angular/common/http';
import { of } from "rxjs";
import { CatalogoService } from "../../../../core/services/catalogo-service";
import { NotificacionService } from "../../../../core/services/notificacion.service";
import { HabitacionService } from "../../../mantenimiento/services/habitacion-service";
import { ReservaForm } from "./reserva-form";
import { ReservaService } from "../../services/reserva-service";
import { ClienteService } from "../../../clientes/services/cliente-service";
import { DocumentoConsultaService } from "../../../clientes/services/documento-consulta-service";
import { fechaLocal } from "../../../../core/utils/fechas";
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
  const empresaRegistrada = {
    idCliente: 1,
    datos: {
      idTipoDocumento: 3, numeroDocumento: '20123456789', empresa: true,
      razonSocial: 'Empresa de prueba', nombres: null, apellidos: null,
      fechaNacimiento: null, direccionFiscal: null, telefono: null, correo: null,
    },
  };
  function abrirRegistro(f: Awaited<ReturnType<typeof fixture>>) {
    const boton = f.nativeElement.querySelector('app-cliente-buscador .acciones .primary') as HTMLButtonElement;
    boton.click();
    f.detectChanges();
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
    const cuenta = {idReserva:30,numeroReserva:'R30',totalAlojamiento:120,totalCargos:120,totalPagado:40,saldoPendiente:80,admiteConsumosConsolidados:false,cuentas:[]};
    const crear=vi.fn().mockReturnValueOnce(throwError(()=>new HttpErrorResponse({status:503}))).mockReturnValueOnce(of(r));
    const pagar=vi.fn();
    const c=(await fixture({prepararRegistro:()=>of(api),crear,pagar,cuenta:()=>of(cuenta)})).componentInstance;
    vi.spyOn(TestBed.inject(Router),'navigate').mockResolvedValue(true);
    c.seleccionarCliente(empresaRegistrada);
    c.habitaciones.at(0).patchValue({idHabitacion:2});c.revisar();
    const cobro={idUsuario:1,idTurno:1,idFormaPago:1,numeroOperacion:null};
    const pago={forma:'EFECTIVO' as const,recibido:50,monto:40,cobro};
    c.confirmar(pago);expect(c.reservaRegistrada()).toBe(null);expect(c.pagoFijado()).toBe(true);
    c.confirmar(pago);expect(crear).toHaveBeenCalledTimes(2);
    expect(crear.mock.calls[1]).toEqual(crear.mock.calls[0]);
    expect(crear.mock.calls[0][0].cobro).toEqual({idFormaPago:1,monto:40,numeroOperacion:null});
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
  it("rechaza un ingreso pasado aunque la salida siga en el futuro", async () => {
    const c = (await fixture()).componentInstance;
    const ayer = new Date();
    ayer.setDate(ayer.getDate() - 1);
    const manana = new Date();
    manana.setDate(manana.getDate() + 1);
    c.seleccionarCliente(empresaRegistrada);
    c.habitaciones.at(0).patchValue({
      idHabitacion: 2,
      fechaIngreso: `${fechaLocal(ayer)}T14:00`,
      fechaSalida: `${fechaLocal(manana)}T12:00`,
    });
    expect(c.lineError(0)).toContain("fecha de ingreso");
    expect(c.lineError(0)).toContain("pasado");
    c.revisar();
    expect(c.pagoAbierto()).toBe(false);
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
    const ingreso = new Date();
    ingreso.setDate(ingreso.getDate() + 1);
    const salida = new Date(ingreso);
    salida.setDate(salida.getDate() + 1);
    c.seleccionarCliente(empresaRegistrada);
    c.habitaciones.at(0).patchValue({
      idHabitacion: 2,
      fechaIngreso: `${fechaLocal(ingreso)}T14:00`,
      fechaSalida: `${fechaLocal(salida)}T12:00`,
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

  it('muestra los errores debajo de cada campo al guardar y los retira al corregir', async () => {
    const f = await fixture();
    const raiz = f.nativeElement as HTMLElement;
    abrirRegistro(f);
    expect(raiz.querySelector('.field-error')).toBeNull();
    raiz.querySelector('[aria-labelledby="cliente-modal-titulo"] form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    f.detectChanges();
    for (const [campo, texto] of [
      ['numeroDocumento', 'El número de documento es obligatorio'], ['nombres', 'Ingresa los nombres'],
      ['apellidos', 'Ingresa los apellidos'], ['fechaNacimiento', 'La fecha de nacimiento del titular es obligatoria'],
    ]) {
      const input = raiz.querySelector(`[aria-labelledby="cliente-modal-titulo"] [formControlName="${campo}"]`)!;
      const mensaje = raiz.querySelector('#' + input.getAttribute('aria-describedby'))!;
      expect(input.getAttribute('aria-invalid')).toBe('true');
      expect(mensaje.textContent).toContain(texto);
      expect(input.closest('.field')!.contains(mensaje)).toBe(true);
    }
    const nombres = raiz.querySelector('[aria-labelledby="cliente-modal-titulo"] [formControlName="nombres"]') as HTMLInputElement;
    nombres.value = 'Lucía Elena';
    nombres.dispatchEvent(new Event('input', { bubbles: true }));
    f.detectChanges();
    expect(raiz.querySelector('#cliente-error-nombres')).toBeNull();
    expect(raiz.querySelector('#cliente-error-apellidos')).not.toBeNull();
    expect(f.componentInstance.pagoAbierto()).toBe(false);
  });

  it('muestra errores de DNI, correo y nacimiento al salir del campo, sin exigir contactos opcionales', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    const raiz = f.nativeElement as HTMLElement;
    abrirRegistro(f);
    const documento = raiz.querySelector('[aria-labelledby="cliente-modal-titulo"] [formControlName="numeroDocumento"]') as HTMLInputElement;
    documento.value = '1234567';
    documento.dispatchEvent(new Event('input', { bubbles: true }));
    documento.dispatchEvent(new Event('blur'));
    c.cliente.patchValue({ correo: 'correo-invalido', fechaNacimiento: '2999-01-01' });
    c.cliente.get('correo')!.markAsTouched();
    c.cliente.get('fechaNacimiento')!.markAsTouched();
    f.detectChanges();
    expect(raiz.querySelector('#cliente-error-documento')?.textContent).toContain('8 dígitos');
    expect(raiz.querySelector('#cliente-error-correo')?.textContent).toContain('correo electrónico válido');
    expect(raiz.querySelector('#cliente-error-nacimiento')?.textContent).toContain('no puede ser futura');
    c.cliente.patchValue({ correo: '', telefono: '', fechaNacimiento: '2015-01-01' });
    c.cliente.get('telefono')!.markAsTouched();
    f.detectChanges();
    expect(raiz.querySelector('#cliente-error-nacimiento')?.textContent).toContain('al menos 18 años');
    expect(raiz.querySelector('#cliente-error-correo')).toBeNull();
    expect(raiz.querySelector('#cliente-error-telefono')).toBeNull();
  });

  it('limita DNI y RUC al escribir o pegar y conserva los documentos alfanuméricos', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    const raiz = f.nativeElement as HTMLElement;
    abrirRegistro(f);
    const documento = raiz.querySelector('[aria-labelledby="cliente-modal-titulo"] [formControlName="numeroDocumento"]') as HTMLInputElement;
    const pegar = (valor: string) => { documento.value = valor; documento.dispatchEvent(new Event('input', { bubbles: true })); f.detectChanges(); };
    expect(documento.maxLength).toBe(8);
    pegar('12a34567890');
    expect(documento.value).toBe('12345678');
    expect(c.cliente.get('numeroDocumento')!.value).toBe('12345678');
    c.cliente.patchValue({ empresa: true });
    c.validarCliente();
    c.cliente.markAllAsTouched();
    f.detectChanges();
    expect(documento.maxLength).toBe(11);
    expect(raiz.querySelector('#cliente-error-documento')?.textContent).toContain('11 dígitos');
    expect(raiz.querySelector('#cliente-error-razon-social')?.textContent).toContain('Ingresa la razón social');
    expect(raiz.querySelector('#cliente-error-nacimiento')).toBeNull();
    pegar('20123456789000');
    expect(c.cliente.get('numeroDocumento')!.value).toBe('20123456789');
    for (const idTipoDocumento of [2, 4]) {
      c.cliente.patchValue({ empresa: false, idTipoDocumento });
      c.validarCliente();
      f.detectChanges();
      pegar('AB1234567890');
      expect(documento.maxLength).toBe(50);
      expect(c.cliente.get('numeroDocumento')!.value).toBe('AB1234567890');
    }
  });

  it('busca por razón social, informa si no existen coincidencias y selecciona un resultado', async () => {
    const f = await fixture();
    const buscar = vi.spyOn(TestBed.inject(ClienteService), 'buscarTitulares')
      .mockReturnValueOnce(of([])).mockReturnValueOnce(of([empresaRegistrada]));
    const panel = f.nativeElement.querySelector('app-cliente-buscador') as HTMLElement;
    const tipo = panel.querySelectorAll<HTMLInputElement>('input[type=radio]')[1];
    tipo.click();
    f.detectChanges();
    expect(Array.from(panel.querySelectorAll('select option')).map((o) => o.textContent?.trim()))
      .toEqual(['Seleccionar documento', 'RUC']);
    const nombre = panel.querySelector<HTMLInputElement>('input[formControlName=nombre]')!;
    nombre.value = 'Empresa de prueba';
    nombre.dispatchEvent(new Event('input', { bubbles: true }));
    const boton = panel.querySelector<HTMLButtonElement>('.acciones button')!;
    boton.click();
    f.detectChanges();
    expect(buscar).toHaveBeenCalledWith(true, 'Empresa de prueba', 3, '');
    expect(panel.querySelector('[role=status]')?.textContent).toContain('No encontramos clientes');
    boton.click();
    f.detectChanges();
    panel.querySelector<HTMLButtonElement>('.resultados button')!.click();
    expect(f.componentInstance.clienteSeleccionado()?.idCliente).toBe(1);
  });

  it('registra y selecciona a quien cumple 18 años hoy y limita el calendario del modal', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    abrirRegistro(f);
    const modal = f.nativeElement.querySelector('[aria-labelledby="cliente-modal-titulo"]') as HTMLElement;
    const tipos = modal.querySelectorAll<HTMLInputElement>('input[type=radio]');
    tipos[1].click();
    f.detectChanges();
    expect(Array.from(modal.querySelectorAll('select option')).map((o) => o.textContent?.trim()))
      .toEqual(['Seleccionar documento', 'RUC']);
    expect(modal.querySelector('input[type=date]')).toBeNull();
    tipos[0].click();
    f.detectChanges();
    expect(Array.from(modal.querySelectorAll('select option')).map((o) => o.textContent?.trim()))
      .toEqual(['Seleccionar documento', 'DNI', 'Carnet de extranjería', 'Pasaporte']);
    const max = modal.querySelector<HTMLInputElement>('input[type=date]')!.max;
    expect(max).toBe(c.fechaMaximaTitular());
    expect(c.fechaMaximaTitular('2026-11-25')).toBe('2008-11-25');
    expect(c.fechaMaximaTitular('2026-02-28')).toBe('2008-02-29');
    const persona = {
      idTipoDocumento: 1, empresa: false, numeroDocumento: '12345678',
      nombres: 'Ana', apellidos: 'Rojas', fechaNacimiento: max,
      razonSocial: null, direccionFiscal: null, telefono: null, correo: null,
    };
    const registrar = vi.spyOn(TestBed.inject(ClienteService), 'registrarTitular')
      .mockReturnValue(of({ idCliente: 14, persona, restriccionesMedicas: null }));
    c.cliente.patchValue(persona);
    modal.querySelector('form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    expect(registrar).toHaveBeenCalledWith(persona);
    expect(c.clienteSeleccionado()?.idCliente).toBe(14);
    expect(c.clienteModal()).toBeNull();
  });

  it('también presenta mensajes bajo los campos del huésped y de sus menores', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    const raiz = f.nativeElement as HTMLElement;
    c.habitaciones.at(0).patchValue({ idHabitacion: 2 });
    c.abrirHuesped(0);
    c.guardarHuesped();
    f.detectChanges();
    expect(raiz.querySelector('#huesped-error-documento')?.textContent).toContain('obligatorio');
    expect(raiz.querySelector('#huesped-error-nombres')?.textContent).toContain('Ingresa los nombres');
    c.guestForm.patchValue({ numeroDocumento: '12345678', nombres: 'Lucía', apellidos: 'Mendoza', fechaNacimiento: '1990-01-01' });
    c.cambiarMenores(true);
    c.guardarHuesped();
    f.detectChanges();
    expect(raiz.querySelector('#huesped-error-nombres')).toBeNull();
    for (const campo of ['documento', 'nombres', 'apellidos', 'nacimiento', 'parentesco']) {
      expect(raiz.querySelector('#menor-error-' + campo + '-0')?.textContent?.trim()).toBeTruthy();
    }
    expect(c.guests(0)).toHaveLength(0);
  });

  it('explica bajo las fechas de la habitación si el ingreso es pasado o la salida es anterior', async () => {
    const f = await fixture();
    const c = f.componentInstance;
    const raiz = f.nativeElement as HTMLElement;
    const habitacion = c.habitaciones.at(0);
    habitacion.patchValue({ fechaIngreso: '2000-01-01T14:00', fechaSalida: '2000-01-01T12:00' });
    habitacion.markAllAsTouched();
    f.detectChanges();
    expect(raiz.querySelector('#habitacion-error-ingreso-0')?.textContent).toContain('no puede estar en el pasado');
    expect(raiz.querySelector('#habitacion-error-salida-0')?.textContent).toContain('posterior al ingreso');
    for (const [campo, valor] of [['fechaIngreso', '2090-01-01T14:00'], ['fechaSalida', '2090-01-02T12:00']]) {
      const input = raiz.querySelector(`[formControlName="${campo}"]`) as HTMLInputElement;
      input.value = valor;
      input.dispatchEvent(new Event('input', { bubbles: true }));
    }
    f.detectChanges();
    expect(raiz.querySelector('#habitacion-error-ingreso-0')).toBeNull();
    expect(raiz.querySelector('#habitacion-error-salida-0')).toBeNull();
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
