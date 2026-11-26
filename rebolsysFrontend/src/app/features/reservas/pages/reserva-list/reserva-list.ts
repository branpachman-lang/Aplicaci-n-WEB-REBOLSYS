import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { PermisosUi } from "../../../../core/auth/permisos-ui";
import { Component, computed, DestroyRef, OnDestroy, inject, signal } from "@angular/core";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink,Router,ActivatedRoute } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { finalize, forkJoin, Subscription } from 'rxjs';
import { DialogFocus } from "../../../../shared/directives/dialog-focus";
import { Tooltip } from '../../../../shared/directives/tooltip';
import { NotificacionService } from "../../../../core/services/notificacion.service";
import { ClienteService } from "../../../clientes/services/cliente-service";
import { CatalogoService } from "../../../../core/services/catalogo-service";
import { Catalogos } from "../../../../core/models/catalogo.model";
import { Pagina } from "../../../../core/models/pagina-response";
import { mensajeError } from "../../../../core/utils/http-error";
import { Icon } from "../../../../shared/components/icono/icono";
import { PagoModal } from "../../../../shared/components/pago-modal/pago-modal";
import { ComprobanteDialog } from '../../components/comprobante-dialog/comprobante-dialog';
import { PagoYape } from '../../components/pago-yape/pago-yape';
import {
  ReservaService,
  ReservaFila,
  ReservaResumen,
  PagoPendiente,
  SalidaPendiente,
} from "../../services/reserva-service";
import {
  ConfirmacionPago,
  CuentaDocumento,
  CuentaReserva,
  DetalleReserva,
  PagoReserva,
  ReservaApi,
  ResumenCuentaReserva,
  estadoReserva,
} from "../../models/reserva-api.model";

type AccionEstadia = 'ingreso' | 'salida' | 'cancelacion';
type AccionRapida = 'ingreso' | 'salida' | 'historial';

const PERMISOS_ESTADIA: Record<AccionEstadia, string> = {
  ingreso: 'RESERVAS:REGISTRAR_INGRESO',
  salida: 'RESERVAS:REGISTRAR_SALIDA',
  cancelacion: 'RESERVAS:CANCELAR',
};

const MENSAJES_ESTADIA: Record<AccionEstadia, string> = {
  ingreso: 'Se registró el ingreso',
  salida: 'Se registró la salida',
  cancelacion: 'Se canceló la estancia',
};

@Component({
  selector: "app-reservas",
  imports: [
    CurrencyPipe,
    DatePipe,
    FormsModule,
    RouterLink,
    Icon,
    PagoModal,
    DialogFocus,
    Tooltip,
    ComprobanteDialog,
    PagoYape,
  ],
  templateUrl: "./reserva-list.html",
  styleUrl: "./reserva-list.css",
})
export class ReservaList implements OnDestroy {
  readonly acceso = inject(PermisosUi);
  private readonly servicio = inject(ReservaService);
  private readonly clienteService = inject(ClienteService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly avisos = inject(NotificacionService);
  private readonly destroy = inject(DestroyRef);
  private readonly ruta=inject(ActivatedRoute);
  private readonly router=inject(Router);
  private comprobanteInicial=Number(this.ruta.snapshot.queryParamMap.get('comprobante'));
  respuesta = signal<Pagina<ReservaFila> | null>(null);
  catalogos = signal<Catalogos | null>(null);
  cargando = signal(true);
  error = signal("");
  private readonly alertaError = notificarCambios(this.error, 'error');
  pagina = 0;
  tamanio = 10;
  busqueda = "";
  estado = "";
  pagando = signal<ReservaResumen | null>(null);
  salidaParaCobrar = signal<SalidaPendiente | null>(null);
  cancelarDetalle = signal<number | null>(null);
  eliminarPagoId = signal<number | null>(null);
  revisionYape = signal(0);
  detalle = signal<ReservaResumen | null>(null);
  accionEstadia = signal<{ id: number; accion: AccionEstadia } | null>(null);
  procesandoEstadia = signal(false);
  accionRapida = signal<{ reserva: ReservaResumen; accion: AccionRapida } | null>(null);
  cargandoAccionRapida = signal(false);
  habitacionRapidaId = signal<number | null>(null);
  habitacionesRapidas = computed(() => {
    const operacion = this.accionRapida();
    return operacion ? this.habitacionesParaAccion(operacion.reserva, operacion.accion) : [];
  });
  habitacionRapida = computed(() => this.habitacionesRapidas().find(
    h => h.idDetalleReserva === this.habitacionRapidaId(),
  ));
  editandoOcupantes = signal<DetalleReserva | null>(null);
  ocupantesEditados = signal<{ idCliente: number; nombre: string; ocupaPlazaCama: boolean; idClienteResponsable: number | null; parentescoResponsable: 'PADRE' | 'MADRE' | 'TUTOR' | null }[]>([]);
  documentoHuesped = '';
  tipoDocumentoHuesped: number | null = null;
  nuevoHuesped = { nombres: '', apellidos: '', fechaNacimiento: '', telefono: '' };
  registrandoHuesped = signal(false);
  buscandoHuesped = signal(false);
  guardandoOcupantes = signal(false);
  errorOcupantes = signal('');
  ocupantesOcupado = computed(() => this.buscandoHuesped() || this.registrandoHuesped() || this.guardandoOcupantes());
  errorEstadia = signal('');
  comprobante = signal<ReservaApi | null>(null);
  abrirComprobante(r: ReservaApi) {
    if (this.comprobante()) return;
    this.detalle.set(null);
    this.comprobante.set(r);
    if (this.catalogos()) return;
    this.catalogoService.listar().pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (catalogos) => this.catalogos.set(catalogos),
      error: (e) => {
        this.comprobante.set(null);
        this.avisos.mostrar(mensajeError(e), true);
      },
    });
  }
  ocupado = signal(false);
  modalError = signal("");
  pagoFijado = signal(false);
  private clavePago = "";
  private solicitudPago: ConfirmacionPago | null = null;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private carga?: Subscription;
  private detalleId = 0;
  estadoReserva = estadoReserva;
  readonly ingresoPendiente = (detalle: DetalleReserva) => detalle.ingresoVencido === true;
  readonly salidaPendiente = (detalle: DetalleReserva) => detalle.salidaPendiente === true;
  pagosPendientes = this.servicio.pagosPendientes;
  registroPendiente = this.servicio.registroPendiente;
  salidasPendientes() {
    return this.servicio.salidasPendientes?.() ?? [];
  }

  constructor() {
    this.leerParametrosIniciales();
    this.cargar(false);
  }
  ngOnDestroy() {
    clearTimeout(this.searchTimer);
  }
  private leerParametrosIniciales() {
    const q = this.ruta.snapshot.queryParamMap;
    const pag = Number(q.get("pagina"));
    if (Number.isInteger(pag) && pag >= 0) this.pagina = pag;
    const tam = Number(q.get("tamanio"));
    if (tam === 5 || tam === 10 || tam === 20) this.tamanio = tam;
    this.busqueda = q.get("buscar") ?? "";
    this.estado = q.get("estado") ?? "";
  }
  private sincronizarUrl() {
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: {
        pagina: this.pagina > 0 ? this.pagina : null,
        tamanio: this.tamanio !== 10 ? this.tamanio : null,
        buscar: this.busqueda.trim() || null,
        estado: this.estado || null,
      },
      queryParamsHandling: "merge",
      replaceUrl: true,
    });
  }
  cargar(sincronizar = true) {
    if (sincronizar) this.sincronizarUrl();
    this.carga?.unsubscribe();
    this.error.set('');
    this.cargando.set(true);
    this.carga = this.servicio.listar(this.pagina, this.tamanio, this.busqueda, this.estado)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (pagina) => {
          this.respuesta.set(pagina);
          this.error.set('');
          this.cargando.set(false);
          if(Number.isInteger(this.comprobanteInicial)&&this.comprobanteInicial>0){
            const reservaId=this.comprobanteInicial;this.comprobanteInicial=0;
            void this.router.navigate([],{relativeTo:this.ruta,queryParams:{comprobante:null},queryParamsHandling:'merge',replaceUrl:true});
            this.servicio.obtener(reservaId).pipe(takeUntilDestroyed(this.destroy)).subscribe({
              next: (reserva) => this.abrirComprobante(reserva),
              error: (e) => this.avisos.mostrar(mensajeError(e), true),
            });
          }
        },
        error: (e) => {
          this.error.set(mensajeError(e));
          this.cargando.set(false);
        },
      });
  }
  buscar() {
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => {
      this.pagina = 0;
      this.cargar();
    }, 300);
  }
  numeroHuespedes(r: ReservaApi) {
    return r.detalles.reduce((n, h) => n + h.ocupantes.length, 0);
  }
  estadoTexto(s: string) {
    return (
      (
        {
          CONFIRMADA: "Confirmada",
          EN_CURSO: "En curso",
          CANCELADA: "Cancelada",
          FINALIZADA: "Finalizada",
        } as Record<string, string>
      )[s] || s
    );
  }
  estadoCobro(cuenta: ResumenCuentaReserva) {
    return cuenta.saldoPendiente === 0
      ? "PAGADO"
      : cuenta.totalPagado > 0
        ? "PARCIAL"
        : "PENDIENTE";
  }
  saldoPrincipal(cuenta: CuentaReserva) {
    return cuenta.cuentas.find((c) => c.idVenta === null)?.saldo ?? 0;
  }
  tieneDocumentoEmitido(cuenta: CuentaDocumento) {
    return cuenta.estado === 'EMITIDO';
  }
  formaPago(id: number) {
    return (
      this.catalogos()?.pagos.find((p) => p.id === id)?.nombre ??
      "Forma de pago #" + id
    );
  }
  tienePagos(cuenta: CuentaReserva) {
    return cuenta.cuentas.some((c) => c.pagos.length > 0);
  }
  puedeEliminarPago(
    r: ReservaResumen,
    cuenta: CuentaDocumento,
    pago: PagoReserva,
  ) {
    const estado = estadoReserva(r);
    const sinFinalizadas = !r.detalles.some(
      (d) => d.estadoDetalle === "FINALIZADA",
    );
    const turnosAbiertos = this.catalogos()?.turnosAbiertos;
    const turnoAbierto =
      !turnosAbiertos ||
      turnosAbiertos.some((t) => t.idTurno === pago.idTurno);
    return (
      this.acceso.puedeCobrarReserva() &&
      cuenta.estado === "BORRADOR" &&
      sinFinalizadas &&
      (estado === "CONFIRMADA" || estado === "EN_CURSO") &&
      turnoAbierto
    );
  }
  eliminarPago(r: ReservaResumen, pago: PagoReserva) {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.servicio
      .eliminarPago(r.idReserva, pago.idPago)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (cuenta) => {
          this.ocupado.set(false);
          this.eliminarPagoId.set(null);
          this.revisionYape.update((n) => n + 1);
          this.detalle.set({ ...r, cuenta });
          this.avisos.mostrar("Pago eliminado del detalle. Saldo actualizado.");
          this.cargar(false);
        },
        error: (e) => {
          this.ocupado.set(false);
          this.eliminarPagoId.set(null);
          this.avisos.mostrar(mensajeError(e), true);
        },
      });
  }
  responsable(habitacion: DetalleReserva, id: number) {
    return (
      habitacion.ocupantes.find((g) => g.idReservaOcupante === id)?.nombre ??
      "Responsable registrado"
    );
  }
  habitacionesParaAccion(reserva: ReservaApi, accion: AccionRapida) {
    return reserva.detalles.filter(h => accion === 'historial'
      ? h.estadoDetalle === 'FINALIZADA'
      : this.puedeGestionarEstadia(h, accion));
  }

  abrirAccionRapida(r: ReservaApi, accion: AccionRapida) {
    if (this.cargandoAccionRapida() || this.accionRapida() || this.ocupado()) return;
    this.cargandoAccionRapida.set(true);
    this.errorEstadia.set('');
    forkJoin({ reserva: this.servicio.obtener(r.idReserva), cuenta: this.servicio.cuenta(r.idReserva) })
      .pipe(takeUntilDestroyed(this.destroy), finalize(() => this.cargandoAccionRapida.set(false)))
      .subscribe({
        next: ({ reserva, cuenta }) => {
          const candidatas = this.habitacionesParaAccion(reserva, accion);
          if (!candidatas.length) {
            this.avisos.mostrar('Ya no hay habitaciones disponibles para esta acción.');
            this.cargar();
            return;
          }
          this.habitacionRapidaId.set(candidatas.length === 1 ? candidatas[0].idDetalleReserva : null);
          this.accionRapida.set({ reserva: { ...reserva, cuenta }, accion });
        },
        error: e => this.avisos.mostrar(mensajeError(e), true),
      });
  }

  cerrarAccionRapida() {
    if (this.procesandoEstadia()) return;
    this.accionRapida.set(null);
    this.errorEstadia.set('');
  }

  confirmarAccionRapida() {
    const operacion = this.accionRapida();
    const habitacion = this.habitacionRapida();
    if (!operacion || !habitacion || this.procesandoEstadia() || this.ocupado()) return;
    const { reserva, accion } = operacion;
    if (accion === 'salida' && this.puedeGestionarEstadia(habitacion, 'salida')) {
      if (this.abrirSalida(reserva, habitacion)) this.cerrarAccionRapida();
    }
    if (accion !== 'ingreso' || !habitacion.ocupantes.length || !this.puedeGestionarEstadia(habitacion, 'ingreso')) return;
    this.procesandoEstadia.set(true);
    this.errorEstadia.set('');
    this.servicio.ingresar(habitacion.idDetalleReserva).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.procesandoEstadia.set(false);
        this.cerrarAccionRapida();
        this.avisos.mostrar(`Se registró el ingreso de la habitación ${habitacion.numeroCuarto}.`);
        this.cargar();
      },
      error: e => {
        this.procesandoEstadia.set(false);
        this.errorEstadia.set(mensajeError(e));
      },
    });
  }

  cerrarEditorOcupantes() {
    if (!this.ocupantesOcupado()) this.editandoOcupantes.set(null);
  }
  abrirEditorOcupantes(h: DetalleReserva) {
    if (this.editandoOcupantes() || h.estadoDetalle !== 'CONFIRMADA' || !this.acceso.permite('RESERVAS:EDITAR_OCUPANTES')) return;
    if (!this.catalogos()) {
      this.catalogoService.listar().pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: catalogos => {
          this.catalogos.set(catalogos);
          const reserva = this.accionRapida()?.reserva ?? this.detalle();
          const vigente = reserva?.detalles.find(d => d.idDetalleReserva === h.idDetalleReserva);
          if (vigente) this.abrirEditorOcupantes(vigente);
        },
        error: e => this.avisos.mostrar(mensajeError(e), true),
      });
      return;
    }
    const porId = new Map(h.ocupantes.map(g => [g.idReservaOcupante, g.idCliente]));
    this.ocupantesEditados.set(h.ocupantes.map(g => ({
      idCliente: g.idCliente,
      nombre: g.nombre,
      ocupaPlazaCama: g.ocupaPlazaCama,
      idClienteResponsable: g.idOcupanteResponsable ? porId.get(g.idOcupanteResponsable) ?? null : null,
      parentescoResponsable: g.parentescoResponsable,
    })));
    this.documentoHuesped = '';
    this.nuevoHuesped = { nombres: '', apellidos: '', fechaNacimiento: '', telefono: '' };
    this.tipoDocumentoHuesped = this.catalogos()?.documentos[0]?.id ?? null;
    this.errorOcupantes.set('');
    this.editandoOcupantes.set(h);
  }
  buscarHuespedParaAgregar() {
    const tipo = this.tipoDocumentoHuesped;
    const documento = this.documentoHuesped.trim();
    if (!tipo || !documento || this.ocupantesOcupado()) return;
    this.buscandoHuesped.set(true);
    this.errorOcupantes.set('');
    this.clienteService.buscarPorDocumento(tipo, documento).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: cliente => {
        this.buscandoHuesped.set(false);
        if (!cliente.idCliente) {
          this.errorOcupantes.set('Registra primero a esta persona en Clientes y vuelve a buscar su documento.');
          return;
        }
        if (cliente.datos.empresa) {
          this.errorOcupantes.set('Selecciona a una persona natural como huésped.');
          return;
        }
        if (this.ocupantesEditados().some(g => g.idCliente === cliente.idCliente)) {
          this.errorOcupantes.set('Este huésped ya está agregado a la habitación.');
          return;
        }
        this.ocupantesEditados.update(lista => [...lista, {
          idCliente: cliente.idCliente!,
          nombre: [cliente.datos.nombres, cliente.datos.apellidos].filter(Boolean).join(' ') || 'Huésped',
          ocupaPlazaCama: true,
          idClienteResponsable: null,
          parentescoResponsable: null,
        }]);
        this.documentoHuesped = '';
      },
      error: e => {
        this.buscandoHuesped.set(false);
        this.errorOcupantes.set(mensajeError(e));
      },
    });
  }
  registrarHuespedNuevo() {
    const tipo = this.tipoDocumentoHuesped;
    const numeroDocumento = this.documentoHuesped.trim();
    const { nombres, apellidos, fechaNacimiento, telefono } = this.nuevoHuesped;
    if (!tipo || !numeroDocumento || !nombres.trim() || !apellidos.trim() || !fechaNacimiento || this.ocupantesOcupado()) {
      this.errorOcupantes.set('Completa documento, nombres, apellidos y fecha de nacimiento del huésped.');
      return;
    }
    this.registrandoHuesped.set(true);
    this.errorOcupantes.set('');
    this.clienteService.registrar({
      idTipoDocumento: tipo,
      numeroDocumento,
      empresa: false,
      nombres: nombres.trim(),
      apellidos: apellidos.trim(),
      fechaNacimiento,
      razonSocial: null,
      direccionFiscal: null,
      correo: null,
      telefono: telefono.trim() || null,
    }).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: cliente => {
        this.registrandoHuesped.set(false);
        if (this.ocupantesEditados().some(g => g.idCliente === cliente.idCliente)) {
          this.errorOcupantes.set('Este huésped ya está agregado a la habitación.');
          return;
        }
        this.ocupantesEditados.update(lista => [...lista, {
          idCliente: cliente.idCliente,
          nombre: [cliente.persona.nombres, cliente.persona.apellidos].filter(Boolean).join(' '),
          ocupaPlazaCama: true,
          idClienteResponsable: null,
          parentescoResponsable: null,
        }]);
        this.documentoHuesped = '';
        this.nuevoHuesped = { nombres: '', apellidos: '', fechaNacimiento: '', telefono: '' };
      },
      error: e => {
        this.registrandoHuesped.set(false);
        this.errorOcupantes.set(mensajeError(e));
      },
    });
  }
  guardarEdicionOcupantes() {
    const h = this.editandoOcupantes();
    const lista = this.ocupantesEditados();
    if (!h || !lista.length || this.ocupantesOcupado()) {
      this.errorOcupantes.set('La habitación debe tener al menos un huésped antes del check-in.');
      return;
    }
    this.guardandoOcupantes.set(true);
    this.errorOcupantes.set('');
    const ocupantes = lista.map(({ idCliente, ocupaPlazaCama, idClienteResponsable, parentescoResponsable }) => ({
      idCliente, ocupaPlazaCama, idClienteResponsable,
      parentescoResponsable: idClienteResponsable ? parentescoResponsable : null,
    }));
    this.servicio.guardarOcupantes(h.idDetalleReserva, ocupantes).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: actualizada => {
        this.guardandoOcupantes.set(false);
        this.editandoOcupantes.set(null);
        this.detalle.update(r => r ? { ...r, detalles: r.detalles.map(d => d.idDetalleReserva === actualizada.idDetalleReserva ? actualizada : d) } : r);
        this.accionRapida.update(operacion => operacion ? {
          ...operacion,
          reserva: {
            ...operacion.reserva,
            detalles: operacion.reserva.detalles.map(d => d.idDetalleReserva === actualizada.idDetalleReserva ? actualizada : d),
          },
        } : operacion);
        this.avisos.mostrar(`Huéspedes de la habitación ${h.numeroCuarto} actualizados.`);
        this.cargar(false);
      },
      error: e => {
        this.guardandoOcupantes.set(false);
        this.errorOcupantes.set(mensajeError(e));
      },
    });
  }
  verDetalle(r: ReservaApi) {
    this.cancelarDetalle.set(null);
    this.eliminarPagoId.set(null);
    const id = ++this.detalleId;
    this.accionEstadia.set(null);
    this.errorEstadia.set('');
    forkJoin({ reserva: this.servicio.obtener(r.idReserva), cuenta: this.servicio.cuenta(r.idReserva) })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ reserva, cuenta }) => {
          if (id === this.detalleId) this.detalle.set({ ...reserva, cuenta });
        },
        error: (e) => this.avisos.mostrar(mensajeError(e), true),
      });
  }
  puedeGestionarEstadia(h: DetalleReserva, accion: AccionEstadia) {
    if (!this.acceso.permite(PERMISOS_ESTADIA[accion])) return false;
    if (accion === 'salida') return h.estadoDetalle === 'EN_CURSO';
    return h.estadoDetalle === 'CONFIRMADA';
  }
  pedirAccionEstadia(h: DetalleReserva, accion: AccionEstadia) {
    if (this.procesandoEstadia() || !this.puedeGestionarEstadia(h, accion)) return;
    this.errorEstadia.set('');
    this.accionEstadia.set({ id: h.idDetalleReserva, accion });
  }
  gestionarEstadia(r: ReservaResumen, h: DetalleReserva) {
    const confirmacion = this.accionEstadia();
    if (this.procesandoEstadia() || confirmacion?.id !== h.idDetalleReserva
      || !this.puedeGestionarEstadia(h, confirmacion.accion)) return;
    const { accion } = confirmacion;
    const id = h.idDetalleReserva;
    this.procesandoEstadia.set(true);
    this.errorEstadia.set('');
    const operacion = accion === 'ingreso' ? this.servicio.ingresar(id)
      : accion === 'salida' ? this.servicio.registrarSalida(id)
      : this.servicio.cancelarEstadia(id);
    operacion.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: (actualizada) => {
        this.procesandoEstadia.set(false);
        this.accionEstadia.set(null);
        this.avisos.mostrar(`${MENSAJES_ESTADIA[accion]} de la habitación ${h.numeroCuarto}.`);
        const detalle = this.detalle();
        if (detalle?.idReserva === r.idReserva) {
          this.detalle.set({ ...detalle, detalles: detalle.detalles.map(item =>
            item.idDetalleReserva === id ? actualizada : item,
          ) });
          if (accion === 'cancelacion' || accion === 'salida') this.verDetalle(r);
        }
        this.cargar();
      },
      error: (e) => {
        this.procesandoEstadia.set(false);
        this.errorEstadia.set(mensajeError(e));
      },
    });
  }
  abrirPago(r: ReservaApi) {
    this.salidaParaCobrar.set(null);
    const pendiente = this.pagosPendientes().find(
      (p) => p.idReserva === r.idReserva,
    );
    if (pendiente) {
      this.recuperarPago(pendiente);
      return;
    }
    this.detalle.set(null);
    this.modalError.set("");
    // Actualizar catálogos y saldo antes de cobrar: el turno o la cuenta pudieron cambiar.
    forkJoin({
      cuenta: this.servicio.cuenta(r.idReserva),
      catalogos: this.catalogoService.listar(),
    })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ cuenta, catalogos }) => {
          if (this.pagando()) return;
          this.catalogos.set(catalogos);
          if (this.saldoPrincipal(cuenta) <= 0) {
            this.avisos.mostrar("El alojamiento ya está pagado.");
            this.cargar();
            return;
          }
          this.clavePago = generarClaveOperacion();
          this.solicitudPago = null;
          this.pagoFijado.set(false);
          this.pagando.set({ ...r, cuenta });
        },
        error: (e) => this.avisos.mostrar(mensajeError(e), true),
      });
  }
  pagar(pago: ConfirmacionPago) {
    const r = this.pagando();
    if (!r || !pago.cobro || !pago.monto || this.ocupado()) return;
    const salida = this.salidaParaCobrar();
    if (salida) {
      const solicitud = salida.cobro ? salida : { ...salida,
        cobro: { idFormaPago: pago.cobro.idFormaPago, monto: pago.monto,
          numeroOperacion: pago.cobro.numeroOperacion },
      };
      this.salidaParaCobrar.set(solicitud);
      this.confirmarSalida(solicitud);
      return;
    }
    this.solicitudPago ??= structuredClone(pago);
    this.pagoFijado.set(true);
    this.ocupado.set(true);
    this.modalError.set("");
    const solicitud = this.solicitudPago;
    this.servicio
      .pagar(r.idReserva, this.clavePago, solicitud.monto!, solicitud.cobro!)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (cuenta) => {
          this.ocupado.set(false);
          this.pagando.set(null);
          this.pagoFijado.set(false);
          const vuelto = Math.max(
            0,
            (solicitud.recibido ?? solicitud.monto!) - solicitud.monto!,
          );
          this.avisos.mostrar(
            `Pago registrado. Saldo: S/ ${cuenta.saldoPendiente.toFixed(2)}. Vuelto: S/ ${vuelto.toFixed(2)}`,
          );
          this.cargar();
        },
        error: (e) => {
          this.ocupado.set(false);
          if (e.status >= 400 && e.status < 500) {
            this.solicitudPago = null;
            this.clavePago = generarClaveOperacion();
            this.pagoFijado.set(false);
          }
          this.modalError.set(
            mensajeError(e) +
              (this.pagoFijado()
                ? " Reintenta el mismo pago para recuperar la confirmación sin duplicar el abono."
                : ""),
          );
        },
      });
  }
  cerrarPago() {
    if (this.pagoFijado()) {
      this.modalError.set(
        'La operación está pendiente de confirmar. Reinténtala con los mismos datos.',
      );
      return;
    }
    this.pagando.set(null);
    this.salidaParaCobrar.set(null);
  }

  abrirSalida(r: ReservaResumen, detalle: DetalleReserva) {
    if (this.ocupado()) return false;
    const pendiente = this.salidasPendientes().find(s => s.idDetalleReserva === detalle.idDetalleReserva);
    if (pendiente) {
      this.confirmarSalida(pendiente);
      return true;
    }
    if (r.cuenta.cuentas.some(c => c.idVenta !== null && c.saldo !== 0)) {
      this.avisos.mostrar('Liquida las cuentas separadas antes de registrar la salida.', true);
      return false;
    }
    const salida = { idReserva: r.idReserva, idDetalleReserva: detalle.idDetalleReserva,
      claveOperacion: generarClaveOperacion() };
    if (this.saldoPrincipal(r.cuenta) === 0) {
      this.confirmarSalida(salida);
    } else if (this.acceso.puedeCobrarReserva()) {
      this.modalError.set('');
      this.detalle.set(null);
      this.salidaParaCobrar.set(salida);
      this.pagando.set(r);
    } else {
      this.avisos.mostrar('Un recepcionista con permiso de cobro debe liquidar el saldo.', true);
      return false;
    }
    return true;
  }

  confirmarSalida(salida: SalidaPendiente) {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.modalError.set('');
    this.pagoFijado.set(!!this.salidaParaCobrar());
    this.servicio.salir(salida).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.pagoFijado.set(false);
        this.salidaParaCobrar.set(null);
        this.pagando.set(null);
        this.detalle.set(null);
        this.avisos.mostrar('Check-out registrado.');
        this.cargar();
        if (this.acceso.permite('RESERVAS:EMITIR_COMPROBANTE')) {
          this.servicio.obtener(salida.idReserva)
            .pipe(takeUntilDestroyed(this.destroy)).subscribe({
              next: (reserva) => {
                if (estadoReserva(reserva) === 'FINALIZADA') this.abrirComprobante(reserva);
              },
              error: e => this.avisos.mostrar(mensajeError(e), true),
            });
        }
      },
      error: e => {
        this.ocupado.set(false);
        const mensaje = mensajeError(e);
        if (e.status >= 400 && e.status < 500) {
          this.pagoFijado.set(false);
          this.salidaParaCobrar.set(null);
          this.pagando.set(null);
          this.detalle.set(null);
          this.cargar();
        }
        if (this.pagando()) this.modalError.set(mensaje);
        else this.avisos.mostrar(mensaje, true);
      },
    });
  }

  actualizarEstancia(r: ReservaResumen, h: DetalleReserva, accion: 'ingreso' | 'cancelacion') {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    const solicitud = accion === 'ingreso' ? this.servicio.ingresar(h.idDetalleReserva)
      : this.servicio.cancelar(h.idDetalleReserva);
    solicitud.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.cancelarDetalle.set(null);
        this.verDetalle(r);
        this.cargar();
      },
      error: e => {
        this.ocupado.set(false);
        this.avisos.mostrar(mensajeError(e), true);
      },
    });
  }

  recuperarPago(pago: PagoPendiente) {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.servicio
      .pagar(pago.idReserva, pago.claveOperacion, pago.monto, pago.cobro)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.ocupado.set(false);
          this.avisos.mostrar("Confirmación recuperada sin duplicar el pago.");
          this.cargar();
        },
        error: (e) => {
          this.ocupado.set(false);
          this.avisos.mostrar(mensajeError(e), true);
        },
      });
  }
  recuperarRegistro() {
    const solicitud = this.registroPendiente();
    if (!solicitud || this.ocupado()) return;
    this.ocupado.set(true);
    this.servicio
      .crear(solicitud)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          this.ocupado.set(false);
          this.avisos.mostrar(
            "Reserva R" +
              r.idReserva +
              " recuperada. Revisa su saldo antes de cobrar.",
          );
          this.cargar();
        },
        error: (e) => {
          this.ocupado.set(false);
          this.avisos.mostrar(mensajeError(e), true);
        },
      });
  }
}
