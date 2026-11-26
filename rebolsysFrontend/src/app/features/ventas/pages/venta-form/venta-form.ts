import { ElementRef } from '@angular/core';
import { ImpresionService } from '../../../../core/services/impresion-service';
import { ComprobanteDialog } from '../../../reservas/components/comprobante-dialog/comprobante-dialog';
import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { CatalogoService } from '../../../../core/services/catalogo-service';
import { Catalogos } from '../../../../core/models/catalogo.model';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { VentaCarrito } from "../../components/venta-carrito/venta-carrito";
import { PermisosUi } from "../../../../core/auth/permisos-ui";
import {
  Component,
  DestroyRef,
  OnInit,
  inject,
  signal,
  computed,
  input,
} from "@angular/core";
import { takeUntilDestroyed } from "@angular/core/rxjs-interop";
import { FormsModule } from "@angular/forms";
import { CurrencyPipe, DatePipe } from "@angular/common";
import { RouterLink, Router } from "@angular/router";
import { catchError, forkJoin, of } from "rxjs";
import { ProductoService } from "../../../mantenimiento/services/producto-service";
import { CategoriaService } from "../../../mantenimiento/services/categoria-service";
import { VentaService } from "../../services/venta-service";
import { Producto } from "../../../mantenimiento/models/producto.model";
import { Categoria } from "../../../mantenimiento/models/categoria.model";
import { Venta, VentaRequest, ReservaVenta } from "../../models/venta.model";
import {
  CobroReserva,
} from "../../../reservas/models/reserva-api.model";
import { ReservaService } from "../../../reservas/services/reserva-service";
import { coincideReserva } from "../../utils/buscar-reserva";
import { mensajeError } from "../../../../core/utils/http-error";
import { Icon } from "../../../../shared/components/icono/icono";
import { PagoModal } from "../../../../shared/components/pago-modal/pago-modal";
import { DialogFocus } from "../../../../shared/directives/dialog-focus";
import { ImagenProtegida } from "../../../../shared/directives/imagen-protegida";
import { VentaTicket } from "../../components/venta-ticket/venta-ticket";
import { ClienteService } from "../../../clientes/services/cliente-service";
import { ClienteDatos } from "../../../clientes/models/cliente.model";
import { tipoDocumento } from "../../../clientes/utils/persona-validacion";
import { Observable, concatMap, map, throwError } from "rxjs";
@Component({
  selector: "app-venta-form",
  imports: [
    FormsModule,
    CurrencyPipe,
    DatePipe,
    RouterLink,
    Icon,
    PagoModal,
    DialogFocus,
    ImagenProtegida,
    VentaTicket,
    VentaCarrito,
    ComprobanteDialog,
  ],
  templateUrl: "./venta-form.html",
  styleUrl: "./venta-form.css",
})
export class VentaForm implements OnInit {
  readonly acceso = inject(PermisosUi);
  private readonly elemento = inject(ElementRef<HTMLElement>);
  private readonly impresion = inject(ImpresionService);
  comprobanteAbierto = signal(false);
  private catalogoService = inject(CatalogoService);
  catalogos = signal<Catalogos | null>(null);
  idUsuario = computed(() => this.acceso.actual()?.idUsuario ?? null);
  id = input<string>();
  original = signal<Venta | null>(null);
  private router = inject(Router);
  private productoService = inject(ProductoService);
  private categoriaService = inject(CategoriaService);
  private ventaService = inject(VentaService);
  private reservaService = inject(ReservaService);
  private destroy = inject(DestroyRef);
  productos = signal<Producto[]>([]);
  categorias = signal<Categoria[]>([]);
  reservas = signal<ReservaVenta[]>([]);
  carrito = signal<{ producto: Producto; cantidad: number }[]>([]);
  cargando = signal(true);
  error = signal("");
  private readonly alertaError = notificarCambios(this.error, 'error');
  errorReservas = signal("");
  private readonly alertaErrorReservas = notificarCambios(this.errorReservas, 'error');
  ingresoAbierto = signal<ReservaVenta | null>(null);
  errorIngreso = signal("");
  private readonly alertaErrorIngreso = notificarCambios(this.errorIngreso, 'error');
  errorCatalogo = signal(false);
  guardando = signal(false);
  errorPago = signal("");
  private readonly alertaErrorPago = notificarCambios(this.errorPago, 'error');
  pagoAbierto = signal(false);
  venta = signal<Venta | null>(null);
  fallidas = signal(new Set<number>());
  buscar = signal("");
  categoria = signal<number | null>(null);
  tipoComprador = signal<"EXTERNO" | "HOSPEDAJE">("EXTERNO");
  modoVenta = signal<"SEPARADA" | "CARGO_RESERVA">("SEPARADA");
  admiteCargo = signal(false);
  consultandoCuenta = signal(false);
  private consultaCuenta = 0;
  reservaId = signal<number | null>(null);
  buscarHabitacion = signal("");
  compradorNombre = signal("");
  private clienteService = inject(ClienteService);
  // Datos del comprador externo: se usan para registrarlo y emitir su boleta o factura.
  docTipo = signal<number | null>(null);
  docNumero = signal("");
  nombres = signal("");
  apellidos = signal("");
  razonSocial = signal("");
  direccion = signal("");
  correo = signal("");
  idClienteExterno = signal<number | null>(null);
  buscandoCliente = signal(false);
  avisoCliente = signal("");
  documentosCliente = computed(() => this.catalogos()?.documentos ?? []);
  esRucExterno = computed(
    () =>
      tipoDocumento(
        this.documentosCliente().find((d) => d.id === this.docTipo()),
      ) === "RUC",
  );
  empresaExterna = computed(
    () => this.esRucExterno() && this.docNumero().trim().startsWith("20"),
  );
  private clave = generarClaveOperacion();
  private solicitud: VentaRequest | null = null;
  solicitudFijada = signal(false);
  pendiente = computed(() => this.ventaService.pendiente?.() ?? null);
  seleccionada = computed(
    () =>
      this.reservas().find((r) => r.idDetalleReserva === this.reservaId()) ||
      null,
  );
  visibles = computed(() =>
    this.productos().filter(
      (p) =>
        (this.categoria() === null || p.idCategoria === this.categoria()) &&
        p.nombreProducto
          .toLocaleLowerCase("es")
          .includes(this.buscar().trim().toLocaleLowerCase("es")),
    ),
  );
  reservasVisibles = computed(() =>
    this.reservas().filter((r) => coincideReserva(r, this.buscarHabitacion())),
  );
  total = computed(
    () =>
      this.carrito().reduce(
        (sum, l) =>
          sum + Math.round(l.producto.precioActual * 100) * l.cantidad,
        0,
      ) / 100,
  );
  unidades = computed(() =>
    this.carrito().reduce((sum, l) => sum + l.cantidad, 0),
  );
  ngOnInit() {
    this.cargar();
  }
  cargar() {
    this.cargando.set(true);
    this.error.set("");
    this.errorReservas.set("");
    this.errorCatalogo.set(false);
    forkJoin({
      catalogos: this.catalogoService.listar(),
      productos: this.productoService.catalogo(),
      categorias: this.categoriaService.listar(),
      reservas: this.ventaService.reservas().pipe(
        catchError((e) => {
          this.errorReservas.set(mensajeError(e));
          return of([] as ReservaVenta[]);
        }),
      ),
      original: this.id() ? this.ventaService.obtener(this.id()!) : of(null),
    }).subscribe({
      next: (r) => {
        this.catalogos.set(r.catalogos);
        if (this.docTipo() === null) this.docTipo.set(this.documentoDni());
        this.productos.set(r.productos);
        this.categorias.set(r.categorias);
        this.reservas.set(r.reservas);
        if (r.original) {
          if (!r.original.editable) {
            this.error.set(
              "Esta venta ya tiene cobro o comprobante, o su reserva no está activa. No se puede editar.",
            );
            this.cargando.set(false);
            return;
          }
          this.original.set(r.original);
          this.tipoComprador.set("HOSPEDAJE");
          this.modoVenta.set("SEPARADA");
          this.reservaId.set(r.original.idDetalleReserva);
          this.compradorNombre.set(r.original.compradorNombre);
          const anteriores = new Map(
            r.original.detalles.map((d) => [d.idProducto, d]),
          );
          this.productos.set(
            r.productos.map((p) => ({
              ...p,
              stock: p.stock,
              precioActual:
                anteriores.get(p.idProducto)?.precioUnitario ?? p.precioActual,
            })),
          );
          this.carrito.set(
            r.original.detalles.map((d) => ({
              cantidad: d.cantidad,
              producto: {
                ...(this.productos().find(
                  (p) => p.idProducto === d.idProducto,
                ) || {
                  idProducto: d.idProducto,
                  idCategoria: 0,
                  categoriaNombre: "",
                  stockMinimo: 0,
                  estadoProducto: "INACTIVO" as const,
                  imagenRuta: d.imagenRuta,
                }),
                nombreProducto: d.nombreProducto,
                precioActual: d.precioUnitario,
                stock:
                  this.productos().find((p) => p.idProducto === d.idProducto)
                    ?.stock ?? 0,
              },
            })),
          );
        }
        this.cargando.set(false);
      },
      error: (e) => {
        this.errorCatalogo.set(true);
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }
  cambiarComprador(tipo: "EXTERNO" | "HOSPEDAJE") {
    if (this.guardando() || this.solicitudFijada() || this.pendiente()) return;
    this.tipoComprador.set(tipo);
    this.reservaId.set(null);
    this.compradorNombre.set("");
    this.actualizarModoVenta();
    this.buscarHabitacion.set("");
    this.limpiarClienteExterno();
  }
  private documentoDni() {
    return (
      this.documentosCliente().find((d) => tipoDocumento(d) === "DNI")?.id ??
      null
    );
  }
  limpiarClienteExterno() {
    if (this.guardando() || this.solicitudFijada() || this.buscandoCliente())
      return;
    this.docTipo.set(this.documentoDni());
    this.docNumero.set("");
    this.limpiarDatosCliente();
    this.avisoCliente.set("");
  }
  private limpiarDatosCliente() {
    this.idClienteExterno.set(null);
    this.nombres.set("");
    this.apellidos.set("");
    this.razonSocial.set("");
    this.direccion.set("");
    this.correo.set("");
  }
  cambiarTipoDocumento(id: number | null) {
    if (this.idClienteExterno() !== null) return;
    this.docTipo.set(id);
    this.docNumero.set("");
    this.avisoCliente.set("");
  }
  cambiarNumeroDocumento(numero: string) {
    if (this.idClienteExterno() !== null) return;
    this.docNumero.set(numero.replace(/\s+/g, ""));
    this.avisoCliente.set("");
  }
  private validarDocumentoExterno() {
    const d = this.documentosCliente().find((d) => d.id === this.docTipo());
    const numero = this.docNumero().trim();
    if (!d || !numero) return "Ingresa el tipo y número de documento del cliente.";
    const tipo = tipoDocumento(d);
    if (tipo === "DNI" && !/^\d{8}$/.test(numero))
      return "El DNI debe tener 8 dígitos.";
    if (tipo === "RUC" && !/^\d{11}$/.test(numero))
      return "El RUC debe tener 11 dígitos.";
    if (numero.length > 20) return "El número de documento es demasiado largo.";
    return "";
  }
  private validarClienteExterno() {
    const error = this.validarDocumentoExterno();
    if (error) return error;
    if (this.idClienteExterno() !== null) return "";
    if (this.empresaExterna()) {
      if (!this.razonSocial().trim()) return "Ingresa la razón social del cliente.";
    } else if (!this.nombres().trim() || !this.apellidos().trim())
      return "Ingresa los nombres y apellidos del cliente.";
    if (this.esRucExterno() && !this.direccion().trim())
      return "Ingresa la dirección fiscal del cliente con RUC.";
    const correo = this.correo().trim();
    if (correo) {
      const arroba = correo.indexOf('@');
      const dominio = correo.slice(arroba + 1);
      const punto = dominio.indexOf('.', 1);
      if (
        arroba <= 0 ||
        arroba !== correo.lastIndexOf('@') ||
        /\s/.test(correo) ||
        punto === -1 ||
        punto === dominio.length - 1
      ) {
        return "Ingresa un correo válido o déjalo vacío.";
      }
    }
    return "";
  }
  private llenarCliente(id: number, p: ClienteDatos) {
    this.idClienteExterno.set(id);
    this.docTipo.set(p.idTipoDocumento);
    this.docNumero.set(p.numeroDocumento);
    this.nombres.set(p.nombres ?? "");
    this.apellidos.set(p.apellidos ?? "");
    this.razonSocial.set(p.razonSocial ?? "");
    this.direccion.set(p.direccionFiscal ?? "");
    this.correo.set(p.correo ?? "");
  }
  buscarClienteExterno() {
    if (this.buscandoCliente() || this.guardando() || this.solicitudFijada())
      return;
    const error = this.validarDocumentoExterno();
    if (error) {
      this.error.set(error);
      return;
    }
    this.error.set("");
    this.buscandoCliente.set(true);
    this.clienteService
      .buscarPorDocumento(this.docTipo()!, this.docNumero().trim())
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (c) => {
          this.buscandoCliente.set(false);
          if (c.idCliente) {
            this.llenarCliente(c.idCliente, c.datos);
            this.avisoCliente.set("Cliente encontrado. Sus datos se usarán en la boleta o factura.");
          } else {
            this.limpiarDatosCliente();
            this.avisoCliente.set("Cliente nuevo: completa sus datos; se registrará al confirmar la venta.");
          }
        },
        error: (e) => {
          this.buscandoCliente.set(false);
          this.limpiarDatosCliente();
          if (e.status === 404)
            this.avisoCliente.set("Cliente nuevo: completa sus datos; se registrará al confirmar la venta.");
          else this.error.set(mensajeError(e));
        },
      });
  }
  private nombreClienteExterno() {
    return this.empresaExterna()
      ? this.razonSocial().trim()
      : `${this.nombres().trim()} ${this.apellidos().trim()}`.trim();
  }
  /** Devuelve el id del comprador externo, buscándolo o registrándolo si aún no existe. */
  private resolverClienteExterno(): Observable<number> {
    const id = this.idClienteExterno();
    if (id !== null) return of(id);
    const empresa = this.empresaExterna();
    const persona: ClienteDatos = {
      idTipoDocumento: this.docTipo(),
      numeroDocumento: this.docNumero().trim(),
      empresa,
      nombres: empresa ? null : this.nombres().trim(),
      apellidos: empresa ? null : this.apellidos().trim(),
      razonSocial: empresa ? this.razonSocial().trim() : null,
      direccionFiscal: this.direccion().trim() || null,
      fechaNacimiento: null,
      correo: this.correo().trim() || null,
      telefono: null,
    };
    return this.clienteService
      .buscarPorDocumento(persona.idTipoDocumento!, persona.numeroDocumento)
      .pipe(
        catchError((e) => (e.status === 404 ? of(null) : throwError(() => e))),
        concatMap((existente) => {
          if (existente?.idCliente) {
            this.llenarCliente(existente.idCliente, existente.datos);
            return of(existente.idCliente);
          }
          return this.clienteService.registrar(persona).pipe(
            map((c) => {
              this.llenarCliente(c.idCliente, c.persona);
              return c.idCliente;
            }),
          );
        }),
      );
  }
  elegirReserva(id: number | null) {
    if (this.guardando() || this.solicitudFijada() || this.pendiente()) return;
    this.reservaId.set(id);
    this.compradorNombre.set(this.seleccionada()?.clienteNombre || "");
    this.actualizarModoVenta();
  }
  private actualizarModoVenta() {
    const consulta = ++this.consultaCuenta;
    this.modoVenta.set('SEPARADA');
    this.admiteCargo.set(false);
    const reserva = this.seleccionada();
    this.consultandoCuenta.set(reserva?.estadoDetalle === 'EN_CURSO');
    if (reserva?.estadoDetalle !== 'EN_CURSO') return;
    this.reservaService.cuenta(reserva.idReserva).pipe(
      catchError(() => of(null)),
      takeUntilDestroyed(this.destroy),
    ).subscribe(cuenta => {
      if (consulta !== this.consultaCuenta) return;
      this.consultandoCuenta.set(false);
      const admite = cuenta?.admiteConsumosConsolidados ?? false;
      this.admiteCargo.set(admite);
      if (admite) this.modoVenta.set('CARGO_RESERVA');
    });
  }
  abrirIngreso() {
    const r = this.seleccionada();
    if (
      !r ||
      r.estadoDetalle !== "CONFIRMADA" ||
      this.guardando() ||
      this.solicitudFijada() ||
      this.pendiente()
    )
      return;
    this.errorIngreso.set("");
    this.ingresoAbierto.set(r);
  }
  cerrarIngreso() {
    if (!this.guardando()) this.ingresoAbierto.set(null);
  }
  registrarIngreso() {
    const r = this.ingresoAbierto();
    if (!r || this.guardando()) return;
    this.guardando.set(true);
    this.errorIngreso.set("");
    this.reservaService
      .ingresar(r.idDetalleReserva)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (detalle) => {
          this.reservas.update((lista) =>
            lista.map((actual) =>
              actual.idDetalleReserva === detalle.idDetalleReserva
                ? {
                    ...actual,
                    estadoDetalle: detalle.estadoDetalle,
                    fechaIngreso: detalle.fechaIngreso,
                    fechaSalida: detalle.fechaSalida,
                    huespedes: detalle.ocupantes.map((o) => o.nombre),
                  }
                : actual,
            ),
          );
          this.guardando.set(false);
          this.ingresoAbierto.set(null);
          this.actualizarModoVenta();
          this.error.set("");
              },
        error: (e) => {
          this.guardando.set(false);
          this.errorIngreso.set(mensajeError(e));
        },
      });
  }
  cantidad(id: number) {
    return (
      this.carrito().find((l) => l.producto.idProducto === id)?.cantidad || 0
    );
  }
  agregar(p: Producto) {
    if (
      this.guardando() ||
      this.solicitudFijada() ||
      this.pendiente() ||
      this.cantidad(p.idProducto) >= p.stock
    )
      return;
    const original = this.original()?.detalles.find(
      (d) => d.idProducto === p.idProducto,
    );
    if (
      p.estadoProducto !== "ACTIVO" &&
      this.cantidad(p.idProducto) >= (original?.cantidad ?? 0)
    )
      return;
    this.error.set("");
    const existe = this.carrito().some(
      (l) => l.producto.idProducto === p.idProducto,
    );
    this.carrito.update((items) =>
      existe
        ? items.map((l) =>
            l.producto.idProducto === p.idProducto
              ? { ...l, cantidad: l.cantidad + 1 }
              : l,
          )
        : [...items, { producto: p, cantidad: 1 }],
    );
  }
  restar(id: number) {
    if (this.guardando() || this.solicitudFijada() || this.pendiente()) return;
    this.carrito.update((items) =>
      items
        .map((l) =>
          l.producto.idProducto === id ? { ...l, cantidad: l.cantidad - 1 } : l,
        )
        .filter((l) => l.cantidad > 0),
    );
  }
  quitar(id: number) {
    if (this.guardando() || this.solicitudFijada() || this.pendiente()) return;
    this.carrito.update((items) =>
      items.filter((l) => l.producto.idProducto !== id),
    );
  }
  imagen(p: Producto) {
    return p.imagenRuta && !this.fallidas().has(p.idProducto)
      ? p.imagenRuta
      : "/imagenes/productos/sin-imagen.svg";
  }
  errorImagen(id: number) {
    this.fallidas.update((actual) => new Set([...actual, id]));
  }
  confirmarVenta() {
    if (this.consultandoCuenta()) return;
    if (this.guardando()) return;
    if (this.solicitudFijada() && this.solicitud) {
      this.registrar(this.solicitud.efectivoRecibido, this.solicitud.cobro);
      return;
    }
    if (this.pendiente()) {
      this.error.set("Recupera la venta pendiente antes de registrar otra.");
      return;
    }
    this.error.set("");
    if (!this.carrito().length) {
      this.error.set("Agrega al menos un producto al carrito.");
      return;
    }
    if (
      !this.id() &&
      this.tipoComprador() === "HOSPEDAJE" &&
      !this.seleccionada()
    ) {
      this.error.set("Selecciona la habitación y reserva del comprador.");
      return;
    }
    if (this.compradorNombre().trim().length > 200) {
      this.error.set("El nombre del comprador admite hasta 200 caracteres.");
      return;
    }
    if (!this.id() && this.tipoComprador() === "HOSPEDAJE") {
      if (this.seleccionada()?.estadoDetalle !== "EN_CURSO") {
        this.error.set(
          "Registra primero el ingreso de esta habitación para añadir consumos a la reserva o cobrarle una venta aparte.",
        );
        return;
      }
    }
    if (this.id()) {
      if (!this.original()?.editable || this.guardando()) return;
      this.guardando.set(true);
      this.ventaService
        .actualizar(this.id()!, {
          compradorNombre: this.compradorNombre().trim() || null,
          detalles: this.carrito().map((l) => ({
            idProducto: l.producto.idProducto,
            cantidad: l.cantidad,
            precioEsperado: l.producto.precioActual,
          })),
        })
        .subscribe({
          next: (v) => {
            this.guardando.set(false);
            void this.router.navigate(["/ventas", v.idVenta]);
          },
          error: (e) => {
            this.guardando.set(false);
            this.error.set(mensajeError(e));
          },
        });
      return;
    }
    if (this.tipoComprador() === "EXTERNO") {
      const error = this.validarClienteExterno();
      if (error) {
        this.error.set(error);
        return;
      }
      this.guardando.set(true);
      this.resolverClienteExterno()
        .pipe(takeUntilDestroyed(this.destroy))
        .subscribe({
          next: (idCliente) => {
            this.guardando.set(false);
            this.prepararSolicitud(idCliente, this.nombreClienteExterno());
          },
          error: (e) => {
            this.guardando.set(false);
            this.error.set(
              "No se pudieron registrar los datos del cliente. " + mensajeError(e),
            );
          },
        });
      return;
    }
    const r = this.seleccionada();
    this.prepararSolicitud(
      r && this.compradorNombre().trim() === r.clienteNombre
        ? r.idCliente
        : null,
      this.compradorNombre().trim() || null,
    );
  }
  private prepararSolicitud(idCliente: number | null, nombre: string | null) {
    const r = this.seleccionada();
    this.solicitud = {
      claveRegistro: this.clave,
      modoVenta: r ? this.modoVenta() : 'SEPARADA',
      idDetalleReserva: r?.idDetalleReserva ?? null,
      idCliente,
      compradorNombre: nombre,
      detalles: this.carrito().map((l) => ({
        idProducto: l.producto.idProducto,
        cantidad: l.cantidad,
        precioEsperado: l.producto.precioActual,
      })),
      efectivoRecibido: null,
    };
    if (this.solicitud.modoVenta === 'CARGO_RESERVA') this.registrar(null);
    else if (this.total() === 0) this.registrar(0);
    else {
      this.errorPago.set("");
      this.pagoAbierto.set(true);
    }
  }
  registrar(recibido: number | null, cobro?: CobroReserva) {
    if (!this.solicitud || this.guardando()) return;
    this.guardando.set(true);
    if (!this.solicitudFijada())
      this.solicitud = {
        ...this.solicitud,
        efectivoRecibido: recibido,
        ...(cobro ? { cobro } : {}),
      };
    this.solicitudFijada.set(true);
    this.errorPago.set("");
    this.ventaService.registrar(this.solicitud).subscribe({
      next: (v) => {
        this.guardando.set(false);
        this.pagoAbierto.set(false);
        this.venta.set(v);
        this.carrito.set([]);
        this.clave = generarClaveOperacion();
        this.solicitud = null;
        this.solicitudFijada.set(false);
        this.limpiarClienteExterno();
        this.productoService
          .catalogo()
          .subscribe({ next: (p) => this.productos.set(p) });
      },
      error: (e) => {
        this.guardando.set(false);
        const rechazoLocal = e instanceof Error && !('status' in e);
        if ((rechazoLocal || (e.status >= 400 && e.status < 500)) && !this.pendiente()?.idVenta) {
          this.solicitudFijada.set(false);
        }
        const mensaje =
          e instanceof Error && !("status" in e) ? e.message : mensajeError(e);
        if (this.pagoAbierto()) this.errorPago.set(mensaje);
        else this.error.set(mensaje);
      },
    });
  }
  cerrarPago() {
    if (this.solicitudFijada()) {
      this.errorPago.set(
        "Recupera la confirmación de la misma venta antes de cerrar.",
      );
      return;
    }
    this.pagoAbierto.set(false);
  }
  recuperarVenta() {
    if (this.guardando()) return;
    this.guardando.set(true);
    this.error.set("");
    this.ventaService.recuperar().subscribe({
      next: (v) => {
        this.guardando.set(false);
        this.solicitudFijada.set(false);
        this.solicitud = null;
        this.pagoAbierto.set(false);
        this.venta.set(v);
        this.carrito.set([]);
        this.clave = generarClaveOperacion();
        this.cargar();
      },
      error: (e) => {
        this.guardando.set(false);
        this.error.set(
          e instanceof Error && !("status" in e) ? e.message : mensajeError(e),
        );
      },
    });
  }
  refrescarComprobante() {
    const id = this.venta()?.idVenta;
    if (id) this.ventaService.obtener(id).subscribe({ next: v => this.venta.set(v), error: e => this.error.set(mensajeError(e)) });
  }
  nuevaVenta() {
    this.comprobanteAbierto.set(false);
    this.venta.set(null);
    this.compradorNombre.set("");
    this.reservaId.set(null);
    this.error.set("");
    this.limpiarClienteExterno();
  }
  puedeEmitir(v: Venta) {
    return (
      v.estadoVenta === "CONFIRMADA" &&
      v.modoVenta === "SEPARADA" &&
      this.acceso.permite("VENTAS:EMITIR_COMPROBANTE")
    );
  }
  imprimir() {
    void this.impresion.imprimir(this.elemento.nativeElement.querySelector('.success-ticket [data-ticket]'))
      .catch(e => this.error.set(e.message));
  }
}
