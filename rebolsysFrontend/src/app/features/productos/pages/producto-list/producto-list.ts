import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { CurrencyPipe } from '@angular/common';
import { RouterLink } from '@angular/router';
import { ProductoService } from '../../services/producto-service';
import { CategoriaService } from '../../../categorias/services/categoria-service';
import { AjusteStock, MovimientoInventario, Producto } from '../../models/producto.model';
import { Categoria } from '../../../categorias/models/categoria.model';
import { SesionService } from '../../../../core/services/sesion-service';
import { CategoriaDialog } from '../../../categorias/components/categoria-dialog/categoria-dialog';
import { mensajeError } from '../../../../core/utils/http-error';
import { NotificacionService } from '../../../../core/services/notificacion.service';
import { Icon } from '../../../../shared/components/icono/icono';
import { DialogFocus } from '../../../../shared/directives/dialog-focus';

@Component({
  selector: 'app-producto-list',
  imports: [FormsModule, CurrencyPipe, RouterLink, Icon, DialogFocus, CategoriaDialog],
  templateUrl: './producto-list.html',
  styleUrl: './producto-list.css',
})
export class ProductoList {
  protected readonly sesion = inject(SesionService);
  private productos = inject(ProductoService);
  private categoriasService = inject(CategoriaService);
  private avisos = inject(NotificacionService);

  categoriasAbiertas = signal(false);
  fallidas = signal<Set<number>>(new Set());

  // Filtros y paginación
  pagina = 0;
  tamanio = 12;
  buscar = '';
  estado = '';
  categoria: number | null = null;
  stockBajo: boolean | null = null;

  private readonly consulta = signal(this.parametrosListado());
  private readonly listado = rxResource({
    params: this.consulta,
    stream: ({ params }) =>
      this.productos.listar(
        params.pagina,
        params.tamanio,
        params.buscar,
        params.estado,
        params.categoria,
        params.stockBajo,
      ),
  });
  private readonly revisionCategorias = signal(0);
  private readonly catalogoCategorias = rxResource({
    params: this.revisionCategorias,
    stream: () => this.categoriasService.listar(),
  });

  readonly respuesta = computed(() => (this.listado.hasValue() ? this.listado.value() : null));
  readonly categorias = computed(() =>
    this.catalogoCategorias.hasValue() ? this.catalogoCategorias.value() : [],
  );
  readonly cargando = computed(() => this.listado.isLoading() || this.catalogoCategorias.isLoading());
  readonly error = computed(() => {
    const causa = this.listado.error() ?? this.catalogoCategorias.error();
    return causa ? mensajeError(causa) : '';
  });

  // Modal de baja / retiro
  desactivando = signal<Producto | null>(null);
  ocupado = signal(false);
  modalError = signal('');

  // Modal de detalle y movimientos
  detalleProducto = signal<Producto | null>(null);
  movimientos = signal<MovimientoInventario[]>([]);
  cargandoMovimientos = signal(false);
  errorMovimientos = signal('');

  // Modal de ajuste de existencias
  ajustandoProducto = signal<Producto | null>(null);
  ajusteStockAnterior = 0;
  ajusteStockNuevo: number | null = null;
  ajusteObservacion = '';
  claveOperacion = '';
  ultimoPayloadEnviado: AjusteStock | null = null;
  ocupadoAjuste = signal(false);
  ajusteError = signal('');
  ajusteConflicto = signal('');

  categoriaCreada(categoria: Categoria) {
    if (!this.catalogoCategorias.hasValue()) {
      this.revisionCategorias.update((revision) => revision + 1);
      return;
    }

    this.catalogoCategorias.update((categorias) =>
      [...(categorias ?? []).filter((c) => c.idCategoria !== categoria.idCategoria), categoria].sort(
        (a, b) => a.nombreCategoria.localeCompare(b.nombreCategoria, 'es'),
      ),
    );
  }

  filtrar() {
    this.pagina = 0;
    this.cargar();
  }

  cargar() {
    if (this.catalogoCategorias.error()) this.catalogoCategorias.reload();
    this.consulta.set(this.parametrosListado());
  }

  private parametrosListado() {
    return {
      pagina: this.pagina,
      tamanio: this.tamanio,
      buscar: this.buscar,
      estado: this.estado,
      categoria: this.categoria,
      stockBajo: this.stockBajo,
    };
  }

  imagen(p: Producto): string {
    return p.imagenRuta && !this.fallidas().has(p.idProducto)
      ? p.imagenRuta
      : '/imagenes/productos/sin-imagen.svg';
  }

  marcarImagenFallida(id: number) {
    this.fallidas.update((set) => {
      const next = new Set(set);
      next.add(id);
      return next;
    });
  }

  esStockBajo(p: Producto): boolean {
    return p.stockBajo ?? p.stock <= p.stockMinimo;
  }

  // Operación: Retiro de venta (Baja lógica mediante DELETE /productos/{id})
  iniciarDesactivacion(p: Producto) {
    this.modalError.set('');
    this.desactivando.set(p);
  }

  desactivar() {
    const p = this.desactivando();
    if (!p || this.ocupado()) return;

    this.ocupado.set(true);
    this.modalError.set('');

    this.productos.desactivar(p.idProducto).subscribe({
      next: () => {
        this.ocupado.set(false);
        this.desactivando.set(null);
        this.avisos.mostrar('Producto retirado de la venta. Su historial se conserva intacto.');
        this.cargar();
      },
      error: (e) => {
        // En caso de error, el modal se conserva abierto para informar al usuario
        this.ocupado.set(false);
        this.modalError.set(mensajeError(e));
      },
    });
  }

  // Operación: Consultar detalle completo y movimientos
  abrirDetalle(p: Producto) {
    this.detalleProducto.set(p);
    this.cargandoMovimientos.set(true);
    this.errorMovimientos.set('');
    this.movimientos.set([]);
    if (!this.sesion.permite('PRODUCTOS:VER_MOVIMIENTOS')) {
      this.cargandoMovimientos.set(false);
      return;
    }

    this.productos.movimientos(p.idProducto).subscribe({
      next: (movs) => {
        this.movimientos.set(movs);
        this.cargandoMovimientos.set(false);
      },
      error: (e) => {
        this.errorMovimientos.set(mensajeError(e));
        this.cargandoMovimientos.set(false);
      },
    });
  }

  cerrarDetalle() {
    this.detalleProducto.set(null);
    this.movimientos.set([]);
    this.errorMovimientos.set('');
  }

  // Operación: Ajuste de existencias
  iniciarAjuste(p: Producto) {
    this.ajustandoProducto.set(p);
    this.ajusteStockAnterior = p.stock;
    this.ajusteStockNuevo = null;
    this.ajusteObservacion = '';
    this.ajusteError.set('');
    this.ajusteConflicto.set('');
    this.claveOperacion = generarClaveOperacion();
    this.ultimoPayloadEnviado = null;
  }

  cerrarAjuste() {
    if (this.ocupadoAjuste() || this.ultimoPayloadEnviado) return;
    this.ajustandoProducto.set(null);
    this.ajusteError.set('');
    this.ajusteConflicto.set('');
    this.ultimoPayloadEnviado = null;
  }

  esAjusteValido(): boolean {
    return (
      !!this.sesion.actual()?.idUsuario &&
      this.ajusteStockNuevo !== null &&
      this.ajusteStockNuevo !== undefined &&
      Number.isInteger(this.ajusteStockNuevo) &&
      this.ajusteStockNuevo >= 0 &&
      this.ajusteStockNuevo <= 999999999 &&
      this.ajusteStockNuevo !== this.ajusteStockAnterior &&
      !!this.ajusteObservacion?.trim() &&
      this.ajusteObservacion.trim().length <= 300
    );
  }

  guardarAjuste() {
    const prod = this.ajustandoProducto();
    if (!prod || !this.esAjusteValido() || this.ocupadoAjuste()) return;

    this.ocupadoAjuste.set(true);
    this.ajusteError.set('');
    this.ajusteConflicto.set('');

    const payload: AjusteStock = this.ultimoPayloadEnviado ?? {
      claveOperacion: this.claveOperacion,
      idUsuario: this.sesion.actual()!.idUsuario,
      stockAnterior: this.ajusteStockAnterior,
      stockNuevo: this.ajusteStockNuevo!,
      observacion: this.ajusteObservacion.trim(),
    };

    this.ultimoPayloadEnviado = payload;

    this.productos.ajustarStock(prod.idProducto, payload).subscribe({
      next: (actualizado) => {
        this.ocupadoAjuste.set(false);
        this.ajustandoProducto.set(null);
        this.ultimoPayloadEnviado = null;
        this.avisos.mostrar('Existencias ajustadas exitosamente.');

        // Si el modal de detalle estaba abierto para este producto, refrescarlo
        if (this.detalleProducto()?.idProducto === prod.idProducto) {
          this.abrirDetalle(actualizado);
        }
        this.cargar();
      },
      error: (e) => {
        this.ocupadoAjuste.set(false);
        // Si hay conflicto (por ejemplo stock concurrente modificado)
        const rechazado = e.status >= 400 && e.status < 500;
        const esConflicto =
          rechazado && (e.status === 409 || /stock|existencias/i.test(mensajeError(e)));
        if (rechazado) {
          this.claveOperacion = generarClaveOperacion();
          this.ultimoPayloadEnviado = null;
        }

        if (esConflicto) {
          // Refrescar saldo observado desde el backend
          this.ocupadoAjuste.set(true);
          this.productos.obtener(prod.idProducto).subscribe({
            next: (refrescado) => {
              this.ocupadoAjuste.set(false);
              this.ajusteStockAnterior = refrescado.stock;
              this.claveOperacion = generarClaveOperacion();
              this.ultimoPayloadEnviado = null;
              this.ajusteConflicto.set(
                `Las existencias en almacén cambiaron a ${refrescado.stock} unidades. Revisa el nuevo saldo antes de reenviar el ajuste.`,
              );
            },
            error: () => {
              this.ocupadoAjuste.set(false);
              this.ajusteError.set(mensajeError(e));
            },
          });
        } else {
          const recuperable = !e.status || e.status >= 500;
          this.ajusteError.set(
            mensajeError(e) +
              (recuperable ? ' Reintenta el mismo ajuste para confirmar el resultado.' : ''),
          );
        }
      },
    });
  }
}
