import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { Component, OnDestroy, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { forkJoin, of, switchMap } from 'rxjs';
import { CatalogoImagenService } from '../../../../core/services/catalogo-imagen.service';
import { NotificacionService } from '../../../../core/services/notificacion.service';
import { SesionService } from '../../../../core/auth/sesion-service';
import { generarClaveOperacion } from '../../../../core/utils/clave-operacion';
import { mensajeError } from '../../../../core/utils/http-error';
import { Icon } from '../../../../shared/components/icono/icono';
import { DialogFocus } from '../../../../shared/directives/dialog-focus';
import { ImagenProtegida } from '../../../../shared/directives/imagen-protegida';
import {
  EstadoHabitacion,
  HabitacionConfiguracion,
} from '../../models/habitacion.model';
import { HabitacionService } from '../../services/habitacion-service';
import { TipoItem } from '../../models/tipo-item.model';
import { TipoItemService } from '../../services/tipo-item-service';
import { EstadoConservacion, Item } from '../../models/mobiliario.model';
import { MobiliarioService } from '../../services/mobiliario-service';
@Component({
  selector: 'app-mobiliario-form',
  imports: [FormsModule, Icon, DialogFocus, ImagenProtegida],
  templateUrl: './mobiliario-form.html',
  styleUrl: './mobiliario-form.css',
})
export class MobiliarioForm implements OnInit, OnDestroy {
  private readonly mobiliarioService = inject(MobiliarioService);
  private readonly habitacionesService = inject(HabitacionService);
  private readonly tiposService = inject(TipoItemService);
  private readonly imagenesService = inject(CatalogoImagenService);
  protected readonly sesion = inject(SesionService);
  private readonly avisos = inject(NotificacionService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  modoEdicion = signal(false);

  itemEnEdicion = signal<Item | null>(null);
  imagenActual = signal<string | null>(null);
  vistaPreviaImagen = signal<string | null>(null);
  archivoImagen = signal<File | null>(null);
  imagenError = signal('');

  creadoIdItem = signal<number | null>(null);

  registroOcupado = signal(false);

  registroError = signal('');

  registroExitoParcial = signal('');

  asignacionClaveOperacion = '';

  lastAsignacionPayload = '';

  formNombreItem = '';

  formIdTipoItem: number | null = null;

  formDestino: 'almacen' | 'habitacion' = 'almacen';

  formIdHabitacion: number | null = null;

  formEstadoActualItem: EstadoConservacion = 'BUENO';

  formObservacion = '';

  modalNuevoTipo = signal(false);

  nuevoTipoNombre = '';

  nuevoTipoError = signal('');

  nuevoTipoOcupado = signal(false);

  abrirRegistrar(): void {
    this.formNombreItem = '';
    this.formIdTipoItem =
      Number(this.route.snapshot.queryParamMap.get('idTipoItem')) ||
      (this.tiposItem()[0]?.idTipoItem ?? null);
    if (
      this.habitacionInicial !== null &&
      this.sesion.permite('MOBILIARIO:ASIGNAR')
    ) {
      this.formDestino = 'habitacion';
      this.formIdHabitacion = this.habitacionInicial;
    } else {
      this.formDestino = 'almacen';
      this.formIdHabitacion = this.habitaciones()[0]?.idHabitacion ?? null;
    }
    this.formEstadoActualItem = 'BUENO';
    this.formObservacion = '';
    this.creadoIdItem.set(null);
    this.registroExitoParcial.set('');
    this.registroError.set('');
    this.asignacionClaveOperacion = generarClaveOperacion();
    this.lastAsignacionPayload = '';
    this.modoEdicion.set(false);
    this.itemEnEdicion.set(null);
    this.imagenActual.set(null);
    this.limpiarVistaPrevia();
    this.archivoImagen.set(null);
    this.imagenError.set('');
  }

  abrirEditar(item: Item): void {
    this.itemEnEdicion.set(item);
    this.formNombreItem = item.nombreItem;
    this.formIdTipoItem = item.idTipoItem;
    this.creadoIdItem.set(null);
    this.registroExitoParcial.set('');
    this.registroError.set('');
    this.modoEdicion.set(true);
    this.imagenActual.set(item.imagenRuta ?? null);
    this.limpiarVistaPrevia();
    this.archivoImagen.set(null);
    this.imagenError.set('');
  }

  vistaImagen(): string | null {
    return this.vistaPreviaImagen() ?? this.imagenActual();
  }

  seleccionarImagen(inputEl: HTMLInputElement): void {
    const archivo = inputEl.files?.[0];
    this.limpiarVistaPrevia();
    if (!archivo) {
      this.archivoImagen.set(null);
      return;
    }
    if (!/^image\/(jpeg|png)$/.test(archivo.type) || archivo.size > 5 * 1024 * 1024) {
      this.imagenError.set('Selecciona una imagen JPEG o PNG de hasta 5 MB.');
      inputEl.value = '';
      this.archivoImagen.set(null);
      return;
    }
    this.imagenError.set('');
    this.archivoImagen.set(archivo);
    this.vistaPreviaImagen.set(URL.createObjectURL(archivo));
  }

  private limpiarVistaPrevia(): void {
    const actual = this.vistaPreviaImagen();
    if (actual?.startsWith('blob:')) URL.revokeObjectURL(actual);
    this.vistaPreviaImagen.set(null);
  }

  ngOnDestroy(): void {
    this.limpiarVistaPrevia();
  }

  guardarRegistro(): void {
    if (this.registroOcupado()) return;

    if (!this.formNombreItem.trim()) {
      this.registroError.set('El nombre del artículo es obligatorio.');
      return;
    }
    if (this.formNombreItem.trim().length > 100) {
      this.registroError.set(
        'El nombre del artículo no puede exceder 100 caracteres.',
      );
      return;
    }
    if (!this.formIdTipoItem) {
      this.registroError.set('Selecciona un tipo de artículo.');
      return;
    }

    if (this.modoEdicion()) {
      const item = this.itemEnEdicion()!;
      const archivo = this.archivoImagen();
      this.registroOcupado.set(true);
      this.registroError.set('');
      this.mobiliarioService
        .guardarItem(item.idItem, {
          nombreItem: this.formNombreItem.trim(),
          idTipoItem: this.formIdTipoItem,
          estadoItem: item.estadoItem,
        })
        .pipe(
          switchMap(() =>
            archivo
              ? this.imagenesService.subir('mobiliario', item.idItem, archivo)
              : of(null),
          ),
        )
        .subscribe({
          next: () => {
            this.registroOcupado.set(false);
            this.cancelar();
            this.avisos.mostrar('Artículo actualizado correctamente.');
          },
          error: (e) => {
            this.registroOcupado.set(false);
            this.registroError.set(mensajeError(e));
          },
        });
      return;
    }

    // Creación nueva
    if (this.formDestino === 'habitacion') {
      if (!this.formIdHabitacion) {
        this.registroError.set(
          'Selecciona la habitación a la que se asignará el artículo.',
        );
        return;
      }
      if (!this.sesion.actual()?.idUsuario) {
        this.registroError.set('Inicia sesión para registrar la asignación.');
        return;
      }
      if (!this.formObservacion.trim()) {
        this.registroError.set(
          'La observación es obligatoria al asignar una habitación.',
        );
        return;
      }
      if (this.formObservacion.trim().length > 180) {
        this.registroError.set(
          'La observación no puede exceder 180 caracteres.',
        );
        return;
      }
    }

    this.registroOcupado.set(true);
    this.registroError.set('');

    // Si ya se creó en un intento anterior y falló la etapa de asignación:
    if (this.creadoIdItem() !== null) {
      this.ejecutarEtapaAsignacionPostAlta(this.creadoIdItem()!);
      return;
    }

    // Etapa 1: Crear unidad en estado Activo
    const archivoNuevo = this.archivoImagen();
    this.mobiliarioService
      .guardarItem(null, {
        nombreItem: this.formNombreItem.trim(),
        idTipoItem: this.formIdTipoItem,
        estadoItem: 'Activo',
      })
      .pipe(
        switchMap((nuevoItem) =>
          archivoNuevo
            ? this.imagenesService
                .subir('mobiliario', nuevoItem.idItem, archivoNuevo)
                .pipe(switchMap(() => of(nuevoItem)))
            : of(nuevoItem),
        ),
      )
      .subscribe({
        next: (nuevoItem) => {
          if (this.formDestino === 'almacen' || !this.formIdHabitacion) {
            this.registroOcupado.set(false);
            this.cancelar();
            this.avisos.mostrar(
              'Artículo registrado correctamente en almacén.',
            );
          } else {
            this.creadoIdItem.set(nuevoItem.idItem);
            this.registroExitoParcial.set(
              `Artículo #${nuevoItem.idItem} registrado con éxito en el sistema. Asignando a la habitación...`,
            );
            this.ejecutarEtapaAsignacionPostAlta(nuevoItem.idItem);
          }
        },
        error: (e) => {
          this.registroOcupado.set(false);
          this.registroError.set(mensajeError(e));
        },
      });
  }

  private ejecutarEtapaAsignacionPostAlta(idItem: number): void {
    const firmaPayload = JSON.stringify({
      idItem,
      h: this.formIdHabitacion,
      u: this.sesion.actual()?.idUsuario,
      c: this.formEstadoActualItem,
      o: this.formObservacion.trim(),
    });

    if (
      this.lastAsignacionPayload &&
      this.lastAsignacionPayload !== firmaPayload
    ) {
      this.asignacionClaveOperacion = generarClaveOperacion();
    }
    this.lastAsignacionPayload = firmaPayload;

    this.mobiliarioService
      .asignarItem(idItem, {
        claveOperacion: this.asignacionClaveOperacion,
        idUsuario: this.sesion.actual()!.idUsuario,
        idHabitacion: this.formIdHabitacion,
        estadoActualItem: this.formEstadoActualItem,
        observacion: this.formObservacion.trim(),
      })
      .subscribe({
        next: () => {
          this.registroOcupado.set(false);
          this.cancelar();
          this.avisos.mostrar(
            'Artículo registrado y asignado a la habitación exitosamente.',
          );
        },
        error: (e) => {
          this.registroOcupado.set(false);
          this.registroExitoParcial.set(
            `El artículo fue creado con éxito en el inventario (ID: #${idItem}), pero ocurrió un error al asignarlo a la habitación.`,
          );
          this.registroError.set(
            `Error en la asignación: ${mensajeError(e)}. Puedes reintentar la asignación sin volver a crear la unidad.`,
          );
        },
      });
  }

  abrirNuevoTipo(): void {
    this.nuevoTipoNombre = '';
    this.nuevoTipoError.set('');
    this.modalNuevoTipo.set(true);
  }

  cerrarModalNuevoTipo(): void {
    if (this.nuevoTipoOcupado()) return;
    this.modalNuevoTipo.set(false);
  }

  guardarNuevoTipo(): void {
    if (this.nuevoTipoOcupado()) return;
    const nombre = this.nuevoTipoNombre.trim();
    if (!nombre) {
      this.nuevoTipoError.set('El nombre del tipo es obligatorio.');
      return;
    }
    if (nombre.length > 30) {
      this.nuevoTipoError.set(
        'El nombre del tipo no puede exceder 30 caracteres.',
      );
      return;
    }

    this.nuevoTipoOcupado.set(true);
    this.nuevoTipoError.set('');
    this.tiposService.crearTipoItem(nombre).subscribe({
      next: (tipoCreado) => {
        this.nuevoTipoOcupado.set(false);
        this.cerrarModalNuevoTipo();
        this.avisos.mostrar(
          `Tipo "${tipoCreado.nombreItem}" creado exitosamente.`,
        );
        this.tiposService.tiposItem().subscribe((tipos) => {
          this.tiposItem.set(tipos || []);
          this.formIdTipoItem = tipoCreado.idTipoItem;
        });
      },
      error: (e) => {
        this.nuevoTipoOcupado.set(false);
        this.nuevoTipoError.set(mensajeError(e));
      },
    });
  }
  readonly id = input<string>();
  habitaciones = signal<HabitacionConfiguracion[]>([]);
  tiposItem = signal<TipoItem[]>([]);

  cargando = signal(true);
  error = signal('');
  private readonly alertaError = notificarCambios(this.error, 'error');
  private habitacionInicial: number | null = null;
  ngOnInit(): void {
    this.cargar();
  }
  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    const raw = this.id();
    const id = raw ? Number(raw) : null;
    if (raw && (!Number.isInteger(id) || id! <= 0)) {
      this.error.set('Identificador de artículo no válido.');
      this.cargando.set(false);
      return;
    }
    const hab = Number(this.route.snapshot.queryParamMap.get('idHabitacion'));
    this.habitacionInicial = Number.isInteger(hab) && hab > 0 ? hab : null;
    forkJoin({
      habitaciones: this.sesion.permite(
        'HABITACIONES:VER',
        'MOBILIARIO:ASIGNAR',
      )
        ? this.habitacionesService.habitacionesTodas()
        : of([]),
      tipos: this.tiposService.tiposItem(),
      item: id === null ? of(null) : this.mobiliarioService.item(id),
    }).subscribe({
      next: (res) => {
        this.habitaciones.set(res.habitaciones);
        this.tiposItem.set(res.tipos);
        if (res.item) this.abrirEditar(res.item);
        else this.abrirRegistrar();
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }
  cancelar(): void {
    if (!this.registroOcupado())
      void this.router.navigate(['/configuraciones/mobiliario']);
  }
  textoCondicion(condicion: EstadoConservacion | null | undefined): string {
    switch (condicion) {
      case 'BUENO':
        return 'Bueno';
      case 'DANADO':
        return 'Dañado';
      case 'FALTANTE':
        return 'Perdido/faltante';
      default:
        return 'No registrada';
    }
  }

  claseEstadoHabitacion(estado: EstadoHabitacion): string {
    switch (estado) {
      case 'DISPONIBLE':
        return 'disponible';
      case 'OCUPADO':
        return 'en_curso';
      case 'POR_LIMPIAR':
      case 'EN_LIMPIEZA':
        return 'limpieza';
      case 'MANTENIMIENTO':
        return 'mantenimiento';
      default:
        return '';
    }
  }
}
