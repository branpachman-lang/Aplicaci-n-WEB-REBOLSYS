import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { PermisosUi } from "../../../../core/services/permisos-ui";
import { Component, DestroyRef, OnDestroy, inject, signal } from "@angular/core";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { FormsModule } from "@angular/forms";
import { RouterLink,Router,ActivatedRoute } from "@angular/router";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { forkJoin, Subscription } from 'rxjs';
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
  ReservaFila,
  ReservaResumen,
  PagoPendiente,
} from "../../services/reserva-service";
import {
  ConfirmacionPago,
  CuentaReserva,
  DetalleReserva,
  ReservaApi,
  ResumenCuentaReserva,
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
  detalle = signal<ReservaResumen | null>(null);
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
  pagosPendientes = this.servicio.pagosPendientes;
  registroPendiente = this.servicio.registroPendiente;

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
  tienePagos(cuenta: CuentaReserva) {
    return cuenta.cuentas.some((c) => c.pagos.length > 0);
  }
  responsable(habitacion: DetalleReserva, id: number) {
    return (
      habitacion.ocupantes.find((g) => g.idReservaOcupante === id)?.nombre ??
      "Responsable registrado"
    );
  }
  verDetalle(r: ReservaApi) {
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
  abrirPago(r: ReservaApi) {
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
          this.comprobante.set(r);
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
        "El pago está pendiente de confirmar. Reinténtalo con los mismos datos antes de registrar otro abono.",
      );
      return;
    }
    this.pagando.set(null);
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
