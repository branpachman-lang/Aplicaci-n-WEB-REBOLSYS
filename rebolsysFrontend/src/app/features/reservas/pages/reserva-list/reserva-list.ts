import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { PermisosUi } from "../../../../core/services/permisos-ui";
import { Component, DestroyRef, OnDestroy, inject, signal } from "@angular/core";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink,Router,ActivatedRoute } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { filter, forkJoin, interval } from 'rxjs';
import { DialogFocus } from "../../../../shared/directives/dialog-focus";
import { NotificacionService } from "../../../../core/services/notificacion.service";
import { CatalogoService } from "../../../../core/services/catalogo-service";
import { Catalogos } from "../../../../core/models/catalogo.model";
import { Pagina } from "../../../../core/models/pagina-response";
import { mensajeError } from "../../../../core/utils/http-error";
import { Icon } from "../../../../shared/components/icono/icono";
import { PagoModal } from "../../../../shared/components/pago-modal/pago-modal";
import { ComprobanteDialog } from '../../components/comprobante-dialog/comprobante-dialog';
import {
  ReservaService,
  ReservaResumen,
  PagoPendiente,
  SalidaPendiente,
} from "../../services/reserva-service";
import {
  ConfirmacionPago,
  CuentaDocumento,
  CuentaReserva,
  DetalleReserva,
  estadoReserva,
} from "../../models/reserva-api.model";

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
    ComprobanteDialog,
  ],
  templateUrl: "./reserva-list.html",
  styleUrl: "./reserva-list.css",
})
export class ReservaList implements OnDestroy {
  readonly acceso = inject(PermisosUi);
  private readonly servicio = inject(ReservaService);
  private readonly catalogoService = inject(CatalogoService);
  private readonly avisos = inject(NotificacionService);
  private readonly destroy = inject(DestroyRef);
  private readonly ruta=inject(ActivatedRoute);
  private readonly router=inject(Router);
  private comprobanteInicial=Number(this.ruta.snapshot.queryParamMap.get('comprobante'));
  respuesta = signal<Pagina<ReservaResumen> | null>(null);
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
  detalle = signal<ReservaResumen | null>(null);
  comprobante = signal<ReservaResumen|null>(null);
  abrirComprobante(r:ReservaResumen){this.detalle.set(null);this.comprobante.set(r);}
  ocupado = signal(false);
  modalError = signal("");
  pagoFijado = signal(false);
  private clavePago = "";
  private solicitudPago: ConfirmacionPago | null = null;
  private searchTimer?: ReturnType<typeof setTimeout>;
  private requestId = 0;
  private detalleId = 0;
  private actualizando = false;
  estadoReserva = estadoReserva;
  pagosPendientes = this.servicio.pagosPendientes;
  registroPendiente = this.servicio.registroPendiente;
  salidasPendientes() {
    return this.servicio.salidasPendientes?.() ?? [];
  }

  constructor() {
    this.leerParametrosIniciales();
    this.cargar(true, false);
    interval(30000)
      .pipe(
        filter(() => !this.actualizando && !this.ocupado() && !this.detalle()
          && !this.pagando() && !this.comprobante()),
        takeUntilDestroyed(this.destroy),
      )
      .subscribe(() => this.cargar(false, false));
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
  cargar(mostrarCarga = true, sincronizar = true) {
    if (sincronizar) this.sincronizarUrl();
    const id = ++this.requestId;
    this.actualizando = true;
    if (mostrarCarga) {
      this.error.set('');
      this.cargando.set(true);
    }
    forkJoin({
      pagina: this.servicio.listar(
        this.pagina,
        this.tamanio,
        this.busqueda,
        this.estado,
      ),
      catalogos: this.catalogoService.listar(),
    })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ pagina, catalogos }) => {
          if (id !== this.requestId) return;
          this.respuesta.set(pagina);
          this.catalogos.set(catalogos);
          this.actualizando = false;
          this.error.set('');
          this.cargando.set(false);
          if(Number.isInteger(this.comprobanteInicial)&&this.comprobanteInicial>0){
            const reservaId=this.comprobanteInicial;this.comprobanteInicial=0;
            void this.router.navigate([],{relativeTo:this.ruta,queryParams:{comprobante:null},queryParamsHandling:'merge',replaceUrl:true});
            forkJoin({reserva:this.servicio.obtener(reservaId),cuenta:this.servicio.cuenta(reservaId)})
              .pipe(takeUntilDestroyed(this.destroy)).subscribe({next:({reserva,cuenta})=>this.abrirComprobante({...reserva,cuenta}),error:e=>this.avisos.mostrar(mensajeError(e),true)});
          }
        },
        error: (e) => {
          if (id === this.requestId) {
            this.actualizando = false;
            if (mostrarCarga) this.error.set(mensajeError(e));
            this.cargando.set(false);
          }
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
  numeroHuespedes(r: ReservaResumen) {
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
  estadoCobro(cuenta: CuentaReserva) {
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
  responsable(habitacion: DetalleReserva, id: number) {
    return (
      habitacion.ocupantes.find((g) => g.idReservaOcupante === id)?.nombre ??
      "Responsable registrado"
    );
  }
  verDetalle(r: ReservaResumen) {
    this.cancelarDetalle.set(null);
    const id = ++this.detalleId;
    forkJoin({ reserva: this.servicio.obtener(r.idReserva), cuenta: this.servicio.cuenta(r.idReserva) })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ reserva, cuenta }) => {
          if (id === this.detalleId) this.detalle.set({ ...reserva, cuenta });
        },
        error: (e) => this.avisos.mostrar(mensajeError(e), true),
      });
  }
  abrirPago(r: ReservaResumen) {
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
    const pendiente = this.salidasPendientes().find(s => s.idDetalleReserva === detalle.idDetalleReserva);
    if (pendiente) {
      this.confirmarSalida(pendiente);
      return;
    }
    if (this.ocupado()) return;
    if (r.cuenta.cuentas.some(c => c.idVenta !== null && c.saldo !== 0)) {
      this.avisos.mostrar('Liquida las cuentas separadas antes de registrar la salida.', true);
      return;
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
    }
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
          forkJoin({ reserva: this.servicio.obtener(salida.idReserva),
            cuenta: this.servicio.cuenta(salida.idReserva) })
            .pipe(takeUntilDestroyed(this.destroy)).subscribe({
              next: ({ reserva, cuenta }) => {
                if (estadoReserva(reserva) === 'FINALIZADA') this.abrirComprobante({ ...reserva, cuenta });
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
