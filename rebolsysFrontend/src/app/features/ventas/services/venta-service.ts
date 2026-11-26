import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { environment } from "../../../../environments/environment";
import { numeroComprobante } from '../../../core/utils/numero-comprobante';
import { Pagina } from "../../../core/models/pagina-response";
import { Venta, VentaRequest, ReservaVenta } from "../models/venta.model";
import { VentaActualizar } from "../models/venta.model";
import {
  EMPTY,
  expand,
  map,
  reduce,
  of,
  forkJoin,
  concatMap,
  tap,
  catchError,
  throwError,
} from "rxjs";
import { ReservaApi } from "../../reservas/models/reserva-api.model";
import { CuentaDocumento } from "../../reservas/models/reserva-api.model";
import { SesionService } from "../../../core/services/sesion-service";
import { VentaApi } from "../models/venta-api.model";
import { Producto } from "../../productos/models/producto.model";
import { pendienteOperacion } from "../../../core/services/pendiente-operacion";
import { coincideReserva } from "../utils/buscar-reserva";

export interface VentaPendiente {
  version?: 2;
  datos: VentaRequest;
  idUsuario?: number;
  idVenta?: number;
  pago?: {
    claveOperacion: string;
    idUsuario: number;
    idTurno: number;
    idFormaPago: number;
    monto: number;
    numeroOperacion: string | null;
  };
}
@Injectable({ providedIn: "root" })
export class VentaService {
  private http = inject(HttpClient);
  private base = environment.apiUrl + "/ventas";
  private sesion = inject(SesionService);
  pendiente = pendienteOperacion<VentaPendiente | null>(this.sesion, "venta.pendiente", null);
  private conservar(v: VentaPendiente | null) {
    this.pendiente.set(v ? { ...v } : null);
  }
  registrar(data: VentaRequest) {
    const previo = this.pendiente();
    if (previo && JSON.stringify(previo.datos) !== JSON.stringify(data))
      return throwError(
        () => new Error("Recupera la venta pendiente antes de registrar otra."),
      );
    const trabajo = previo ?? { datos: structuredClone(data), version: 2 as const };
    this.conservar(trabajo);
    return this.sesion.cargar().pipe(
      concatMap((s) => {
        if (trabajo.idUsuario && trabajo.idUsuario !== s.idUsuario)
          throw Error("La venta pendiente corresponde a otro usuario.");
        if (!s.idTurno)
          throw Error(
            "El usuario actual necesita un único turno de caja abierto.",
          );
        const total = data.detalles.reduce((sum, d) =>
          sum + Math.round((d.precioEsperado ?? 0) * 100) * d.cantidad, 0) / 100;
        const cargoReserva = data.modoVenta === 'CARGO_RESERVA' && !!data.idDetalleReserva;
        const forma = data.cobro?.idFormaPago ?? s.idFormaPagoEfectivo;
        if (!cargoReserva && !forma) throw Error('Selecciona una forma de pago.');
        if (!cargoReserva && (!data.cobro || forma === s.idFormaPagoEfectivo) && total > 0 &&
            (data.efectivoRecibido === null || !Number.isFinite(data.efectivoRecibido) ||
             data.efectivoRecibido < total ||
             Math.abs(data.efectivoRecibido * 100 - Math.round(data.efectivoRecibido * 100)) > 1e-6))
          throw Error('El efectivo recibido debe cubrir el total de la venta.');
        trabajo.idUsuario = s.idUsuario;
        this.conservar(trabajo);
        const solicitud = {
          claveRegistro: data.claveRegistro,
          idUsuario: s.idUsuario,
          idClienteComprador: data.idCliente,
          compradorNombreLibre: data.idCliente ? null : data.compradorNombre,
          modalidadCobro: data.idDetalleReserva ? 'RESERVA' : 'SEPARADA',
          idDetalleReserva: data.idDetalleReserva,
          detalles: data.detalles,
          ...(cargoReserva ? {} : { cobro: { idFormaPago: forma, monto: total,
            numeroOperacion: data.cobro?.numeroOperacion ?? null } }),
        };
        return this.http.post<VentaApi>(this.base + '/confirmadas', solicitud).pipe(
          tap(v => { trabajo.idVenta = v.idVenta; this.conservar(trabajo); }),
          concatMap(v => this.presentar(v, data)),
        );
      }),
      tap(() => this.conservar(null)),
      catchError((e) => {
        const rechazoLocal = e instanceof Error && !('status' in e);
        if (!trabajo.idVenta && (rechazoLocal || (e.status >= 400 && e.status < 500)))
          this.conservar(null);
        return throwError(() => e);
      }),
    );
  }
  recuperar() {
    const p = this.pendiente();
    // Una confirmación guardada por la versión anterior ya puede haber movido stock.
    // Consultarla o repetir su pago original evita recrear la venta con una firma diferente.
    if (p && p.version !== 2 && p.idVenta) {
      return this.http.get<VentaApi>(`${this.base}/${p.idVenta}`).pipe(
        concatMap(v => p.pago
          ? this.http.post<CuentaDocumento>(`${this.base}/${p.idVenta}/pagos`, p.pago).pipe(
              concatMap(cuenta => this.presentar(v, p.datos, cuenta)),
            )
          : this.presentar(v, p.datos)),
        tap(() => this.conservar(null)),
      );
    }
    return p
      ? this.registrar(p.datos)
      : throwError(() => new Error("No hay una venta pendiente."));
  }
  obtener(id: number | string) {
    return this.http
      .get<VentaApi>(this.base + "/" + id)
      .pipe(concatMap((v) => this.presentar(v)));
  }
  actualizar(id: number | string, datos: VentaActualizar) {
    return this.sesion.cargar().pipe(
      concatMap((s) =>
        this.http.put<VentaApi>(this.base + "/" + id, {
          idUsuario: s.idUsuario,
          idClienteComprador: null,
          compradorNombreLibre: datos.compradorNombre,
          detalles: datos.detalles,
        }),
      ),
      concatMap((v) => this.presentar(v)),
    );
  }
  listar(pagina = 0, tamanio = 10) {
    return this.http
      .get<Pagina<VentaApi>>(this.base, {
        params: { pagina, tamanio },
      })
      .pipe(
        concatMap((p) =>
          p.contenido.length
            ? forkJoin(p.contenido.map((v) => this.presentar(v))).pipe(
                map((contenido) => ({ ...p, contenido })),
              )
            : of({ ...p, contenido: [] as Venta[] }),
        ),
      );
  }
  private presentar(
    v: VentaApi,
    solicitud?: VentaRequest,
    cuenta?: CuentaDocumento,
  ) {
    return forkJoin({
      productos: this.http.get<Producto[]>(
        environment.apiUrl + "/productos/catalogo",
      ),
      estancias: v.idDetalleReserva
        ? this.reservas("", true)
        : of([] as ReservaVenta[]),
      cuenta: cuenta
        ? of(cuenta)
        : v.estadoVenta === "CONFIRMADA"
          ? this.http.get<CuentaDocumento>(`${this.base}/${v.idVenta}/cuenta`).pipe(
              catchError(e => e.status === 409 && v.modalidadCobro === "RESERVA"
                ? of(null) : throwError(() => e)),
            )
          : of(null),
      cliente: v.idClienteComprador
        ? this.http.get<{
            persona: {
              empresa: boolean;
              razonSocial: string | null;
              nombres: string | null;
              apellidos: string | null;
            };
          }>(environment.apiUrl + "/clientes/" + v.idClienteComprador)
        : of(null),
    }).pipe(
      map(({ productos, estancias, cuenta, cliente }): Venta => {
        const r = estancias.find(
            (r) => r.idDetalleReserva === v.idDetalleReserva,
          ),
          p = cliente?.persona;
        const nombre = p
          ? p.empresa
            ? p.razonSocial
            : [p.nombres, p.apellidos].filter(Boolean).join(" ")
          : null;
        const total = v.total,
          pagado = cuenta?.pagado ?? 0,
          saldo = cuenta?.saldo ?? total;
        return {
          idVenta: v.idVenta,
          idCliente: v.idClienteComprador,
          numero: String(v.idVenta),
          idUsuario: v.idUsuario,
          idReserva: r?.idReserva ?? null,
          fechaVenta: v.fechaRegistro,
          estadoVenta: v.estadoVenta,
          modoVenta:
            v.modalidadCobro === "RESERVA" && !cuenta ? "CARGO_RESERVA" : "SEPARADA",
          compradorNombre:
            nombre ||
            v.compradorNombreLibre ||
            solicitud?.compradorNombre ||
            "Venta al público",
          idDetalleReserva: v.idDetalleReserva,
          referenciaReserva: r?.referencia ?? null,
          habitacion: r?.habitacion ?? null,
          estadoCobro:
            v.estadoVenta === "ANULADA"
              ? "ANULADO"
              : saldo === 0
                ? "PAGADO"
                : "PENDIENTE",
          editable: v.estadoVenta === "BORRADOR",
          subtotal: v.detalles.reduce((s, d) => s + d.subtotal, 0),
          igv: v.detalles.reduce((s, d) => s + d.igv, 0),
          total,
          pagado,
          saldo,
          vuelto:
            solicitud?.efectivoRecibido == null
              ? 0
              : Math.max(
                  0,
                  solicitud.efectivoRecibido -
                    (this.pendiente()?.pago?.monto ?? total),
                ),
          detalles: v.detalles.map((d) => ({
            idConsumo: d.idDetalleVenta,
            idProducto: d.idProducto,
            nombreProducto: d.descripcion,
            cantidad: d.cantidad,
            precioUnitario: d.valorUnitario,
            total: d.total,
            stockRestante:
              productos.find((p) => p.idProducto === d.idProducto)?.stock ?? 0,
            imagenRuta:
              productos.find((p) => p.idProducto === d.idProducto)
                ?.imagenRuta ?? null,
          })),
          comprobante:
            cuenta?.estado === "EMITIDO"
              ? {
                  numero: numeroComprobante(cuenta.serie, cuenta.numero),
                  fecha: cuenta.fechaEmision ?? v.fechaRegistro,
                  receptor:
                    cuenta.receptorNombre ??
                    nombre ??
                    v.compradorNombreLibre ??
                    "Venta al público",
                  documento: cuenta.receptorNumeroDoc ?? "",
                  lineas: (cuenta.lineas ?? []).map((l) => ({
                    descripcion: l.descripcion,
                    cantidad: l.cantidad,
                    valorUnitario: l.cantidad ? l.total / l.cantidad : 0,
                    total: l.total,
                  })),
                  total: cuenta.total,
                  pagado: cuenta.pagado,
                  devuelto: 0,
                  estado: cuenta.estado,
                  pagos: cuenta.pagos.map((p) => ({
                    idPago: p.idPago,
                    fecha: p.fechaPago,
                    monto: p.monto,
                    devuelto: 0,
                  })),
                }
              : null,
        };
      }),
    );
  }
  reservas(buscar = "", incluirFinalizadas = false) {
    const pagina = (numero: number) =>
      this.http.get<Pagina<ReservaApi>>(environment.apiUrl + "/reservas", {
        params: { pagina: numero, tamanio: 100 },
      });
    return pagina(0).pipe(
      expand((p) => (p.ultima ? EMPTY : pagina(p.pagina + 1))),
      reduce((todos, p) => [...todos, ...p.contenido], [] as ReservaApi[]),
      map((reservas) =>
        reservas.flatMap((r) =>
          r.detalles
            .filter(
              (d) =>
                incluirFinalizadas ||
                d.estadoDetalle === "EN_CURSO" ||
                d.estadoDetalle === "CONFIRMADA",
            )
            .map((d): ReservaVenta => ({
              idReserva: r.idReserva,
              idDetalleReserva: d.idDetalleReserva,
              referencia: "R" + r.idReserva,
              numero: String(r.idReserva),
              habitacion: d.numeroCuarto,
              clienteNombre: r.titular,
              idCliente: r.idCliente,
              huespedes: d.ocupantes.map((o) => o.nombre),
              estadoDetalle: d.estadoDetalle,
              fechaIngreso: d.fechaIngreso,
              fechaSalida: d.fechaSalida,
            })),
        ),
      ),
      map((reservas) => reservas.filter((r) => coincideReserva(r, buscar))),
    );
  }
}
