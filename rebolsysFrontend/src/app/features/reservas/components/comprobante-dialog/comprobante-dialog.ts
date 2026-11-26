import { map } from 'rxjs';
import { ElementRef } from '@angular/core';
import { ImpresionService } from '../../../../core/services/impresion-service';
import { EntregaComprobante } from '../../../../shared/components/entrega-comprobante/entrega-comprobante';
import { Venta } from '../../../ventas/models/venta.model';
import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import {
  Component,
  DestroyRef,
  inject,
  input,
  output,
  signal,
  computed,
  OnInit,
} from "@angular/core";
import { FormsModule } from "@angular/forms";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { catchError, concatMap, forkJoin, of, throwError } from "rxjs";
import { Catalogos } from "../../../../core/models/catalogo.model";
import { mensajeError } from "../../../../core/utils/http-error";
import { fechaLocal } from "../../../../core/utils/fechas";
import { DialogFocus } from "../../../../shared/directives/dialog-focus";
import { Icon } from "../../../../shared/components/icono/icono";
import { MarcaHospedaje } from '../../../../shared/components/marca-hospedaje/marca-hospedaje';
import { ClienteService } from "../../../clientes/services/cliente-service";
import { ClienteDatos } from "../../../clientes/models/cliente.model";
import { tipoDocumento } from '../../../clientes/utils/persona-validacion';
import { numeroComprobante } from '../../../../core/utils/numero-comprobante';
import { erroresDeValidacion } from '../../../../core/utils/http-error';
import { CuentaDocumento, ReservaApi } from '../../models/reserva-api.model';
import { Emision, PrepararEmision } from "../../models/emision.model";
import { ReservaService } from "../../services/reserva-service";
import { ComprobanteService } from "../../services/comprobante-service";

@Component({
  selector: "app-comprobante-dialog",
  imports: [FormsModule, CurrencyPipe, DatePipe, DialogFocus, Icon, MarcaHospedaje, EntregaComprobante],
  templateUrl: "./comprobante-dialog.html",
  styleUrl: "./comprobante-dialog.css",
})
export class ComprobanteDialog implements OnInit {
  reserva = input<ReservaApi | null>(null);
  venta = input<Venta | null>(null);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly impresion = inject(ImpresionService);
  catalogos = input.required<Catalogos>();
  cerrar = output<void>();
  actualizado = output<void>();
  private servicio = inject(ComprobanteService);
  private reservas = inject(ReservaService);
  private clientes = inject(ClienteService);
  private destroy = inject(DestroyRef);
  cuentas = signal<CuentaDocumento[]>([]);
  seleccion = signal(0);
  documento = signal<CuentaDocumento | null>(null);
  emision = signal<Emision | null>(null);
  ocupado = signal(false);
  cargando = signal(true);
  error = signal("");
  private readonly alertaError = notificarCambios(this.error, 'error');
  aviso = signal("");
  private readonly alertaAviso = notificarCambios(this.aviso, 'exito');
  estadoConsultado = signal(false);
  configuracion = signal<{
    ambiente: string;
    habilitada: boolean;
    configuracionPendiente: string[];
  } | null>(null);
  usuario: number | null = null;
  turno: number | null = null;
  tipo = "BOLETA";
  readonly numeroComprobante = numeroComprobante;
  private camposTocados = signal<Record<string, boolean>>({});
  private intentoReceptor = signal(false);
  private intentoFiscal = signal(false);
  private erroresServidor = signal<Record<string, string>>({});
  idCliente: number | null = null;
  titular = signal<{ idCliente: number; persona: ClienteDatos } | null>(null);
  receptor: ClienteDatos = {
    idTipoDocumento: null,
    numeroDocumento: "",
    empresa: false,
    nombres: "",
    apellidos: "",
    fechaNacimiento: null,
    razonSocial: "",
    direccionFiscal: "",
    correo: null,
    telefono: null,
  };
  readonly tasaIgv = 18;
  direccion = "";
  cuotas: { fechaPago: string; monto: number }[] = [];
  hoy = fechaLocal(new Date());
  private clave = generarClaveOperacion();
  private solicitudFiscal: PrepararEmision | null = null;
  fiscalFijada = signal(false);
  modoFiscal = signal<'local' | 'proveedor' | null>(null);
  emisionFijada = signal(false);
  private solicitudInterna: {
    idUsuario: number;
    idTurno: number;
    tipo: string;
    idClienteReceptor: number;
    idVenta?: number;
  } | null = null;
  cuenta = computed(
    () =>
      this.cuentas().find((c) => (c.idVenta ?? 0) === this.seleccion()) ?? null,
  );
  turnos() {
    return (
      this.catalogos().turnosAbiertos?.filter(
        (t) => t.idUsuario === this.usuario,
      ) ?? []
    );
  }
  cambiarUsuario() {
    this.turno = this.turnos()[0]?.idTurno ?? null;
  }
  estadoPago(doc: CuentaDocumento) {
    return doc.saldo < 0.01 ? 'PAGADO' : 'PENDIENTE DE PAGO';
  }
  pagosVerificados(doc: CuentaDocumento) {
    return doc.pagos.filter(p => p.verificado);
  }
  ngOnInit() {
    this.usuario = this.catalogos().usuarios?.[0]?.id ?? null;
    this.cambiarUsuario();
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.error.set("");
    const reserva = this.reserva();
    const venta = this.venta();
    if (!reserva && !venta) { this.error.set('Selecciona una reserva o venta.'); this.cargando.set(false); return; }
    const idCliente = reserva?.idCliente ?? venta?.idCliente;
    forkJoin({
      cuenta: reserva ? this.reservas.cuenta(reserva.idReserva).pipe(map(c => c.cuentas))
        : this.servicio.cuentaVenta(venta!.idVenta).pipe(map(c => [c])),
      cliente: idCliente ? this.clientes.obtener(idCliente) : of(null),
      config: this.servicio.configuracion(),
    })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ cuenta, cliente, config }) => {
          this.cuentas.set(cuenta);
          this.seleccion.set(venta?.idVenta ?? 0);
          this.configuracion.set(config);
          if (cliente) {
            this.titular.set({ idCliente: cliente.idCliente, persona: { ...cliente.persona } });
            this.receptor = { ...cliente.persona };
            this.idCliente = cliente.idCliente;
            this.direccion = cliente.persona.direccionFiscal ?? '';
            // En la tienda, un comprador con RUC normalmente pide factura.
            if (venta && this.esRuc(cliente.persona.idTipoDocumento)) this.tipo = 'FACTURA';
          }
          this.cargando.set(false);
          this.cambiarCuenta();
        },
        error: (e) => {
          this.cargando.set(false);
          this.error.set(mensajeError(e));
        },
      });
  }
  cambiarCuenta() {
    this.reiniciarValidaciones();
    const cuenta = this.cuenta();
    this.documento.set(cuenta?.estado === "EMITIDO" ? cuenta : null);
    this.emision.set(null);
    this.estadoConsultado.set(false);
    this.error.set("");
    this.solicitudInterna = null;
    this.solicitudFiscal = null;
    this.emisionFijada.set(false);
    this.fiscalFijada.set(false);
    this.modoFiscal.set(null);
    this.clave = generarClaveOperacion();
    if (cuenta?.estado === "EMITIDO" && cuenta.idCuenta)
      this.cargarEstado(cuenta.idCuenta);
  }
  buscar() {
    if (this.ocupado() || this.emisionFijada()) return;
    this.tocarCampo('idTipoDocumento'); this.tocarCampo('numeroDocumento');
    const error = this.validarDocumentoReceptor();
    if (error) {
      this.error.set(error);
      return;
    }
    this.ocupado.set(true);
    this.error.set("");
    const tipo = this.receptor.idTipoDocumento!,
      numero = this.receptor.numeroDocumento.trim();
    this.clientes
      .buscarPorDocumento(tipo, numero)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (c) => {
          this.ocupado.set(false);
          this.receptor = { ...c.datos };
          this.idCliente = c.idCliente;
          this.direccion = c.datos.direccionFiscal ?? "";
          this.aviso.set("Receptor encontrado en el hospedaje.");
        },
        error: (e) => {
          this.ocupado.set(false);
          this.idCliente = null;
          if (e.status === 404) this.aviso.set('El receptor aún no está registrado. Completa sus datos para emitir el comprobante.');
          else this.error.set(mensajeError(e));
        },
      });
  }
  otroReceptor() {
    if (this.ocupado() || this.emisionFijada()) return;
    this.idCliente = null;
    this.reiniciarValidaciones();
    this.receptor = {
      idTipoDocumento: this.tipo === 'FACTURA'
        ? this.catalogos().documentos.find(d => d.codigoSunat === '6')?.id ?? null
        : this.catalogos().documentos.find(d => d.codigoSunat === '1')?.id ?? null,
      numeroDocumento: "",
      empresa: false,
      nombres: "",
      apellidos: "",
      razonSocial: "",
      direccionFiscal: "",
      fechaNacimiento: null,
      correo: null,
      telefono: null,
    };
    this.direccion = "";
    this.error.set('');
    this.aviso.set(
      "Completa y busca el documento; si no existe, se registrará al emitir.",
    );
  }
  documentosReceptor() {
    return this.catalogos().documentos.filter(d => this.tipo !== 'FACTURA' || d.codigoSunat === '6');
  }
  esRuc(id = this.receptor.idTipoDocumento) {
    return tipoDocumento(this.catalogos().documentos.find(d => d.id === id)) === 'RUC';
  }
  esDni() {
    return tipoDocumento(this.catalogos().documentos.find(d => d.id === this.receptor.idTipoDocumento)) === 'DNI';
  }
  limiteDocumento() { return this.esDni() ? 8 : this.esRuc() ? 11 : 50; }
  ajustarEntradaDocumento(evento: Event) {
    const input = evento.target as HTMLInputElement;
    input.value = (this.esDni() || this.esRuc() ? input.value.replace(/[^0-9]/g, '') : input.value).slice(0, this.limiteDocumento());
  }
  tocarCampo(campo: string) {
    this.camposTocados.update(campos => ({ ...campos, [campo]: true }));
  }
  limpiarErrorCampo(campo: string) {
    this.erroresServidor.update(errores => { const copia = { ...errores }; delete copia[campo]; return copia; });
  }
  private reiniciarValidaciones() {
    this.camposTocados.set({}); this.erroresServidor.set({});
    this.intentoReceptor.set(false); this.intentoFiscal.set(false);
  }
  errorCampo(campo: string) {
    const remoto = this.erroresServidor()[campo];
    if (remoto) return remoto;
    if (!this.intentoReceptor() && !this.camposTocados()[campo]) return '';
    return this.validacionesReceptor()[campo] ?? '';
  }
  private validacionesReceptor(): Record<string, string> {
    const errores: Record<string, string> = {};
    if (!this.receptor.idTipoDocumento) errores['idTipoDocumento'] = 'Selecciona el tipo de documento.';
    const documento = this.validarDocumentoReceptor();
    if (documento) errores['numeroDocumento'] = documento;
    if (this.receptor.empresa) {
      if (!this.receptor.razonSocial?.trim()) errores['razonSocial'] = 'Completa la razón social.';
    } else {
      if (!this.receptor.nombres?.trim()) errores['nombres'] = 'Completa los nombres.';
      if (!this.receptor.apellidos?.trim()) errores['apellidos'] = 'Completa los apellidos.';
    }
    if (this.tipo === 'FACTURA' && !this.receptor.direccionFiscal?.trim())
      errores['direccionFiscal'] = 'Completa la dirección fiscal del receptor.';
    if (!this.turnos().some(t => t.idTurno === this.turno)) errores['turno'] = 'Selecciona un turno de caja abierto.';
    return errores;
  }
  errorDireccionFiscal() {
    return (this.intentoFiscal() || this.camposTocados()['direccionReceptor'])
      && this.documento()?.tipo === 'FACTURA' && !this.direccion.trim()
      ? 'Completa la dirección fiscal del receptor.' : this.erroresServidor()['direccionReceptor'] ?? '';
  }
  errorCuota(indice: number, campo: 'fechaPago' | 'monto') {
    const clave = `cuotas[${indice}].${campo}`;
    if (this.erroresServidor()[clave]) return this.erroresServidor()[clave];
    if (!this.intentoFiscal() && !this.camposTocados()[clave]) return '';
    const cuota = this.cuotas[indice];
    if (campo === 'fechaPago') return !cuota.fechaPago ? 'Selecciona la fecha de pago.'
      : cuota.fechaPago < this.hoy ? 'La fecha no puede ser anterior a hoy.' : '';
    if (!Number.isFinite(cuota.monto) || cuota.monto <= 0) return 'Ingresa un monto mayor a cero.';
    if (Math.abs(cuota.monto * 100 - Math.round(cuota.monto * 100)) > 1e-6) return 'Usa como máximo dos decimales.';
    return this.intentoFiscal() && indice === this.cuotas.length - 1
      && Math.round(this.cuotas.reduce((s,c) => s + (Number.isFinite(c.monto) ? c.monto : 0), 0) * 100) !== Math.round((this.documento()?.saldo ?? 0) * 100)
      ? 'Las cuotas deben sumar exactamente el saldo pendiente.' : '';
  }
  nombreTitular() {
    const persona = this.titular()?.persona;
    return persona?.empresa ? persona.razonSocial : `${persona?.nombres ?? ''} ${persona?.apellidos ?? ''}`.trim();
  }
  usarTitular() {
    const titular = this.titular();
    if (!titular || this.ocupado() || this.emisionFijada()) return;
    if (this.tipo === 'FACTURA' && !this.esRuc(titular.persona.idTipoDocumento)) return;
    this.idCliente = titular.idCliente;
    this.receptor = { ...titular.persona };
    this.reiniciarValidaciones();
    this.direccion = titular.persona.direccionFiscal ?? '';
    this.error.set('');
    this.aviso.set('El comprobante se emitirá al titular.');
  }
  cambiarTipo() {
    if (this.ocupado() || this.emisionFijada()) return;
    this.error.set('');
    if (this.tipo === 'FACTURA' && !this.esRuc()) {
      this.otroReceptor();
      this.aviso.set('Ingresa el RUC de quien recibirá la factura. La reserva y sus huéspedes conservan sus documentos.');
    } else if (this.tipo === 'BOLETA' && this.titular()) this.usarTitular();
  }
  cambiarDocumento() {
    if (this.ocupado() || this.emisionFijada() || this.idCliente !== null) return;
    this.cambiarNumero('');
  }
  cambiarNumero(numero: string) {
    if (this.ocupado() || this.emisionFijada() || this.idCliente !== null) return;
    numero = (this.esDni() || this.esRuc() ? numero.replace(/[^0-9]/g, '') : numero).slice(0, this.limiteDocumento());
    this.limpiarErrorCampo('numeroDocumento');
    const documento = this.receptor.idTipoDocumento;
    const empresa = this.esRuc() && numero.startsWith('20');
    this.receptor = { idTipoDocumento: documento, numeroDocumento: numero, empresa,
      nombres: '', apellidos: '', fechaNacimiento: null, razonSocial: '', direccionFiscal: '', correo: null, telefono: null };
    this.direccion = '';
    this.error.set('');
    this.aviso.set('');
    const titular = this.titular()?.persona;
    // Un RUC 10 que contiene el DNI del titular conserva sus nombres como ayuda de entrada.
    if (this.esRuc() && /^10\d{9}$/.test(numero) && titular && !titular.empresa
        && tipoDocumento(this.catalogos().documentos.find(d => d.id === titular.idTipoDocumento)) === 'DNI'
        && numero.slice(2, 10) === titular.numeroDocumento) {
      this.receptor.nombres = titular.nombres;
      this.receptor.apellidos = titular.apellidos;
      this.receptor.fechaNacimiento = titular.fechaNacimiento;
    }
  }
  private validarDocumentoReceptor() {
    const d = this.catalogos().documentos.find(
      (d) => d.id === this.receptor.idTipoDocumento,
    );
    if (!d) return 'Selecciona el tipo de documento del receptor.';
    if (
      this.tipo === "FACTURA" &&
      (d.codigoSunat !== "6" ||
        !/^\d{11}$/.test(this.receptor.numeroDocumento.trim()))
    )
      return "Para factura se requiere un receptor con RUC de 11 dígitos.";
    if (!this.receptor.numeroDocumento.trim()) return 'Ingresa el número de documento.';
    if (this.esDni() && !/^\d{8}$/.test(this.receptor.numeroDocumento.trim())) return 'El DNI debe tener exactamente 8 dígitos.';
    if (this.esRuc() && !/^\d{11}$/.test(this.receptor.numeroDocumento.trim())) return 'El RUC debe tener exactamente 11 dígitos.';
    return '';
  }
  validarReceptor() {
    const error = this.validarDocumentoReceptor();
    if (error) return error;
    const d = this.catalogos().documentos.find(d => d.id === this.receptor.idTipoDocumento)!;
    if (this.receptor.empresa && d.codigoSunat !== "6")
      return "Una empresa debe identificarse con RUC.";
    return this.receptor.empresa
      ? !this.receptor.razonSocial?.trim()
        ? "Completa la razón social."
        : ""
      : !this.receptor.nombres?.trim() || !this.receptor.apellidos?.trim()
        ? "Completa los nombres y apellidos."
        : "";
  }
  emitir() {
    if (this.ocupado()) return;
    this.intentoReceptor.set(true);
    this.erroresServidor.set({});
    const cuenta = this.cuenta();
    const error = this.validarReceptor() || Object.values(this.validacionesReceptor())[0];
    if (error) {
      this.error.set(error);
      return;
    }
    if (
      !cuenta ||
      !this.usuario ||
      !this.turnos().some((t) => t.idTurno === this.turno)
    ) {
      this.error.set("Selecciona un recepcionista y su turno abierto.");
      return;
    }
    this.ocupado.set(true);
    this.error.set("");
    const persona = {
      ...this.receptor,
      numeroDocumento: this.receptor.numeroDocumento.trim(),
      nombres: this.receptor.empresa
        ? null
        : (this.receptor.nombres?.trim() ?? null),
      apellidos: this.receptor.empresa
        ? null
        : (this.receptor.apellidos?.trim() ?? null),
      razonSocial: this.receptor.empresa
        ? (this.receptor.razonSocial?.trim() ?? null)
        : null,
      direccionFiscal: this.receptor.direccionFiscal?.trim() || null,
    };
    const receptor = this.idCliente ? of({ idCliente: this.idCliente })
      : this.clientes.buscarPorDocumento(persona.idTipoDocumento!, persona.numeroDocumento).pipe(
          catchError(e => e.status === 404 ? of(null) : throwError(() => e)),
          concatMap(existente => {
            if (existente?.idCliente) {
              this.receptor = { ...existente.datos };
              this.direccion = persona.direccionFiscal || existente.datos.direccionFiscal || '';
              this.receptor.direccionFiscal = this.direccion;
              return of({ idCliente: existente.idCliente });
            }
            return this.clientes.registrar(persona);
          }),
        );
    receptor
      .pipe(
        concatMap((cliente) => {
          this.idCliente = cliente.idCliente;
          this.solicitudInterna ??= {
            idUsuario: this.usuario!,
            idTurno: this.turno!,
            tipo: this.tipo,
            idClienteReceptor: cliente.idCliente,
            ...(cuenta.idVenta ? { idVenta: cuenta.idVenta } : {}),
          };
          this.emisionFijada.set(true);
          const reserva = this.reserva();
          return reserva
            ? this.servicio.emitir(reserva.idReserva, this.solicitudInterna)
            : this.servicio.emitirVenta(this.venta()!.idVenta, this.solicitudInterna);
        }),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (doc) => {
          this.ocupado.set(false);
          this.documento.set(doc);
          this.cuentas.update((cs) =>
            cs.map((c) => ((c.idVenta ?? 0) === this.seleccion() ? doc : c)),
          );
          this.aviso.set(
            "Comprobante interno generado. Puedes preparar y enviar su versión electrónica beta.",
          );
          this.actualizado.emit();
          this.cargarEstado(doc.idCuenta!);
        },
        error: (e) => {
          this.erroresServidor.set(erroresDeValidacion(e));
          this.ocupado.set(false);
          if (e.status >= 400 && e.status < 500) {
            this.solicitudInterna = null;
            this.emisionFijada.set(false);
          }
          this.error.set(
            mensajeError(e) +
              (this.emisionFijada()
                ? " La confirmación puede haberse perdido; reintenta con el mismo tipo y receptor."
                : ""),
          );
        },
      });
  }
  cargarEstado(id: number) {
    this.estadoConsultado.set(false);
    this.servicio
      .estado(id)
      .pipe(
        catchError((e) => (e.status === 404 ? of(null) : throwError(() => e))),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe({
        next: (e) => {
          this.emision.set(e);
          this.estadoConsultado.set(true);
        },
        error: (e) => this.error.set(mensajeError(e)),
      });
  }
  agregarCuota() {
    this.cuotas.push({ fechaPago: this.hoy, monto: 0 });
  }
  preparar(simulada = false) {
    const doc = this.documento();
    if (
      !doc?.idCuenta ||
      this.ocupado() ||
      !this.usuario ||
      !this.estadoConsultado() ||
      this.emision()
    )
      return;
    if (this.fiscalFijada() && this.modoFiscal() !== (simulada ? 'local' : 'proveedor')) {
      this.error.set('Recupera la preparación pendiente antes de cambiar de modalidad.');
      return;
    }
    if (!this.solicitudFiscal) {
      this.intentoFiscal.set(true);
      this.erroresServidor.set({});
      if (doc.tipo === "FACTURA" && !this.direccion.trim()) {
        this.error.set(
          "Completa la dirección fiscal del receptor para preparar la factura.",
        );
        return;
      }
      if (
        doc.saldo > 0 &&
        (this.cuotas.length === 0 ||
          this.cuotas.some(
            (c) =>
              !c.fechaPago ||
              c.fechaPago < this.hoy ||
              !Number.isFinite(c.monto) ||
              c.monto <= 0 ||
              Math.abs(c.monto * 100 - Math.round(c.monto * 100)) > 1e-6,
          ) ||
          Math.round(this.cuotas.reduce((s, c) => s + c.monto, 0) * 100) !==
            Math.round(doc.saldo * 100))
      ) {
        this.error.set(
          "Las cuotas deben tener fecha válida y sumar exactamente el saldo del comprobante.",
        );
        return;
      }
      this.solicitudFiscal = {
        claveOperacion: this.clave,
        idUsuario: this.usuario,
        tasaIgv: this.tasaIgv,
        direccionReceptor: this.direccion.trim() || null,
        tratamientos: [],
        cuotas: doc.saldo > 0 ? structuredClone(this.cuotas) : [],
      };
    }
    this.ocupado.set(true);
    this.fiscalFijada.set(true);
    this.modoFiscal.set(simulada ? 'local' : 'proveedor');
    this.error.set("");
    (simulada
      ? this.servicio.simular(doc.idCuenta, this.solicitudFiscal)
      : this.servicio.preparar(doc.idCuenta, this.solicitudFiscal))
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (e) => {
          this.emision.set(e);
          this.ocupado.set(false);
          this.actualizado.emit();
        },
        error: (e) => {
          this.ocupado.set(false);
          if (e.status >= 400 && e.status < 500) {
            this.solicitudFiscal = null;
            this.fiscalFijada.set(false);
            this.modoFiscal.set(null);
            this.clave = generarClaveOperacion();
          }
          this.erroresServidor.set(erroresDeValidacion(e));
          this.error.set(mensajeError(e));
          this.cargarEstado(doc.idCuenta!);
        },
      });
  }
  enviar() {
    this.operacion("enviar");
  }
  consultar() {
    this.operacion("consultar");
  }
  private operacion(accion: "enviar" | "consultar") {
    if (this.emision()?.codigoRespuesta === 'SIMULACION_LOCAL') {
      this.error.set('La simulación local no se envía al proveedor ni a SUNAT.');
      return;
    }
    const id = this.documento()?.idCuenta;
    if (!id || !this.usuario || this.ocupado()) return;
    if (!this.estadoConsultado()) return;
    const estado = this.emision()?.estado;
    if (
      accion === "enviar" &&
      estado !== "PREPARADO" &&
      estado !== "ERROR_ENVIO"
    )
      return;
    if (
      accion === "consultar" &&
      estado !== "INCIERTO" &&
      estado !== "ENVIANDO"
    )
      return;
    this.ocupado.set(true);
    this.error.set("");
    const obs =
      accion === "enviar"
        ? this.servicio.enviar(id, this.usuario)
        : this.servicio.consultar(id, this.usuario);
    obs.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (e) => {
        this.emision.set(e);
        this.ocupado.set(false);
        this.actualizado.emit();
      },
      error: (e) => {
        this.ocupado.set(false);
        this.error.set(
          mensajeError(e) +
            " Actualiza el estado antes de intentar otra operación.",
        );
        this.cargarEstado(id);
      },
    });
  }
  descargar(tipo: "xml" | "cdr" | "pdf") {
    const doc = this.documento();
    if (!doc?.idCuenta || this.ocupado()) return;
    this.ocupado.set(true);
    this.error.set("");
    this.servicio
      .archivo(doc.idCuenta, tipo)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (blob) => {
          const e = this.emision();
          const url = URL.createObjectURL(blob);
          const a = document.createElement("a");
          a.href = url;
          a.download = `${numeroComprobante(e?.serie ?? doc.serie, e?.correlativo ?? doc.numero)}.${tipo === "cdr" ? "zip" : tipo}`;
          a.click();
          setTimeout(() => URL.revokeObjectURL(url), 1000);
          this.ocupado.set(false);
        },
        error: (e) => {
          this.ocupado.set(false);
          this.error.set("No se pudo descargar el archivo. " + mensajeError(e));
        },
      });
  }
  imprimir() {
    void this.impresion.imprimir(this.elemento.nativeElement.querySelector('[data-ticket]'))
      .catch(e => this.error.set(e.message));
  }
}
