import { notificarCambios } from '../../../../core/services/avisos-formulario';
import { CommonModule, DecimalPipe } from '@angular/common';
import {
  Component,
  OnDestroy,
  OnInit,
  computed,
  inject,
  input,
  signal,
} from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { forkJoin, of, switchMap } from 'rxjs';
import { CatalogoImagenService } from '../../../../core/services/catalogo-imagen.service';
import { NotificacionService } from '../../../../core/services/notificacion.service';
import { mensajeError } from '../../../../core/utils/http-error';
import { ImagenProtegida } from '../../../../shared/directives/imagen-protegida';
import { TipoHabitacion } from '../../models/tipo-habitacion.model';
import { TipoHabitacionService } from '../../services/tipo-habitacion-service';
import {
  EstadoHabitacion,
  HabitacionConfiguracion,
  HabitacionConfiguracionRequest,
} from '../../models/habitacion.model';
import { HabitacionService } from '../../services/habitacion-service';
@Component({
  selector: 'app-habitacion-form',
  imports: [CommonModule, FormsModule, DecimalPipe, ImagenProtegida],
  templateUrl: './habitacion-form.html',
  styleUrl: './habitacion-form.css',
})
/**
 * CU_21 - Formulario de Habitación (Crear / Editar):
 * - Controla reglas de capacidad, plazas de cama, menores sin cama y tarifa.
 * - Incluye carga y vista previa de imagen referencial (JPEG/PNG <= 5 MB).
 * - Al guardar, encadena con switchMap: (1) guardar habitación -> (2) subir imagen.
 */
export class HabitacionForm implements OnInit, OnDestroy {
  // === 1. INYECCIÓN DE DEPENDENCIAS Y ESTADO DEL FORMULARIO ===
  private readonly habitacionesService = inject(HabitacionService);
  private readonly tiposService = inject(TipoHabitacionService);
  private readonly notificacionService = inject(NotificacionService);
  private readonly imagenesService = inject(CatalogoImagenService);
  private readonly router = inject(Router);
  formOcupado = signal(false);

  formError = signal('');

  modoEdicion = signal(false);

  idHabitacionEditando = signal<number | null>(null);

  /** Ruta persistida en BD (si ya tiene imagen), URL blob: temporal de vista previa y archivo seleccionado. */
  imagenActual = signal<string | null>(null);
  vistaPreviaImagen = signal<string | null>(null);
  archivoImagen = signal<File | null>(null);
  imagenError = signal('');

  formNumero = signal('');

  formIdTipo = signal<number | null>(null);

  formCapacidad = signal(1);

  formPlazasCama = signal(1);

  formMenoresSinCama = signal(0);

  formEstado = signal<EstadoHabitacion>('DISPONIBLE');

  estadoOriginal = signal<EstadoHabitacion>('DISPONIBLE');

  formPrecioPorNoche = signal<number | null>(null);

  // === 2. SEÑALES COMPUTADAS (TIPO, ESTADO OCUPADA, CAPACIDAD Y TARIFA BASE) ===
  tipoSeleccionado = computed(() => {
    const id = this.formIdTipo();
    return this.tipos().find((t) => t.idTipoHabitacion === id) ?? null;
  });

  esOcupado = computed(() => {
    return this.modoEdicion() && this.estadoOriginal() === 'OCUPADO';
  });

  capacidadMaximaTipo = computed(() => {
    return this.tipoSeleccionado()?.capacidadMax ?? 99;
  });

  precioBaseTipo = computed(() => {
    return this.tipoSeleccionado()?.precioBase ?? 0;
  });

  // === 3. INICIALIZACIÓN EN MODO CREAR O EDITAR ===
  abrirCrear(): void {
    this.modoEdicion.set(false);
    this.idHabitacionEditando.set(null);
    this.formNumero.set('');
    const primerTipo = this.tipos()[0] ?? null;
    this.formIdTipo.set(primerTipo ? primerTipo.idTipoHabitacion : null);
    this.formCapacidad.set(primerTipo ? primerTipo.capacidadMax : 1);
    this.formPlazasCama.set(primerTipo ? primerTipo.capacidadMax : 1);
    this.formMenoresSinCama.set(0);
    this.formEstado.set('DISPONIBLE');
    this.estadoOriginal.set('DISPONIBLE');
    this.formPrecioPorNoche.set(null);
    this.imagenActual.set(null);
    this.limpiarVistaPrevia();
    this.archivoImagen.set(null);
    this.imagenError.set('');
    this.formError.set('');
  }

  abrirEditar(room: HabitacionConfiguracion): void {
    this.modoEdicion.set(true);
    this.idHabitacionEditando.set(room.idHabitacion);
    this.formNumero.set(room.numeroCuarto);
    this.formIdTipo.set(room.idTipoHabitacion);
    this.formCapacidad.set(room.capacidadPersonas);
    this.formPlazasCama.set(room.plazasCama);
    this.formMenoresSinCama.set(room.maxMenoresSinCama);
    this.formEstado.set(room.estadoHabitacion);
    this.estadoOriginal.set(room.estadoHabitacion);
    this.formPrecioPorNoche.set(room.precioPorNoche ?? null);
    this.imagenActual.set(room.imagenRuta ?? null);
    this.limpiarVistaPrevia();
    this.archivoImagen.set(null);
    this.imagenError.set('');
    this.formError.set('');
  }

  // === 4. CARGA Y VISTA PREVIA DE IMAGEN (JPEG/PNG <= 5 MB, URL blob:) ===
  /** Prioriza la vista previa local (blob:) sobre la imagen ya guardada en el servidor. */
  vistaImagen(): string | null {
    return this.vistaPreviaImagen() ?? this.imagenActual();
  }

  /** Valida formato JPEG/PNG y tamaño <= 5 MB en el cliente y crea una URL temporal en memoria. */
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

  /** Libera la URL blob: anterior para evitar fugas de memoria al cambiar archivo o salir. */
  private limpiarVistaPrevia(): void {
    const actual = this.vistaPreviaImagen();
    if (actual?.startsWith('blob:')) URL.revokeObjectURL(actual);
    this.vistaPreviaImagen.set(null);
  }

  ngOnDestroy(): void {
    this.limpiarVistaPrevia();
  }

  // === 5. REGLAS DE CAPACIDAD, PLAZAS DE CAMA Y MENORES SIN CAMA ===
  onTipoChange(nuevoId: number | null): void {
    this.formIdTipo.set(nuevoId);
    const tipo = this.tipos().find((t) => t.idTipoHabitacion === nuevoId);
    if (tipo) {
      if (!this.modoEdicion()) {
        // Al crear, iniciar capacidad y plazas con el máximo del tipo y menores en 0
        this.formCapacidad.set(tipo.capacidadMax);
        this.formPlazasCama.set(tipo.capacidadMax);
        this.formMenoresSinCama.set(0);
      } else {
        if (this.formCapacidad() > tipo.capacidadMax) {
          this.formCapacidad.set(tipo.capacidadMax);
        }
        if (this.formPlazasCama() > this.formCapacidad()) {
          this.formPlazasCama.set(this.formCapacidad());
        }
        const maxMenores = this.maximoMenoresSinCama();
        if (this.formMenoresSinCama() > maxMenores) {
          this.formMenoresSinCama.set(maxMenores);
        }
      }
    }
  }

  onCapacidadChange(valor: number): void {
    const tipo = this.tipoSeleccionado();
    const max = tipo ? tipo.capacidadMax : 99;
    let cap = Number(valor);
    if (cap > max) cap = max;
    if (!Number.isInteger(cap) || cap < 1) cap = 1;
    this.formCapacidad.set(cap);

    if (this.formPlazasCama() > cap) {
      this.formPlazasCama.set(cap);
    }
    const maxMenores = this.maximoMenoresSinCama();
    if (this.formMenoresSinCama() > maxMenores) {
      this.formMenoresSinCama.set(maxMenores);
    }
  }

  onPlazasChange(valor: number): void {
    let plazas = Number(valor);
    const cap = this.formCapacidad();
    if (plazas > cap) plazas = cap;
    if (plazas < 1) plazas = 1;
    this.formPlazasCama.set(plazas);
    this.formMenoresSinCama.update((menores) =>
      Math.min(menores, this.maximoMenoresSinCama()),
    );
  }

  maximoMenoresSinCama(): number {
    return Math.min(
      1,
      Math.max(0, this.formCapacidad() - this.formPlazasCama()),
    );
  }

  // === 6. VALIDACIÓN Y GUARDADO ENCADENADO CON SUBIDA DE IMAGEN (switchMap) ===
  formValido(): boolean {
    const num = this.formNumero().trim();
    if (!num || num.length > 10) return false;
    const idTipo = this.formIdTipo();
    if (!idTipo) return false;
    const tipo = this.tipoSeleccionado();
    if (!tipo) return false;
    const cap = this.formCapacidad();
    if (!Number.isInteger(cap) || cap < 1 || cap > tipo.capacidadMax)
      return false;
    const plazas = this.formPlazasCama();
    if (!Number.isInteger(plazas) || plazas < 1 || plazas > cap) return false;
    const menores = this.formMenoresSinCama();
    if (
      !Number.isInteger(menores) ||
      menores < 0 ||
      menores > this.maximoMenoresSinCama()
    )
      return false;
    const tarifa = this.formPrecioPorNoche();
    if (
      tarifa !== null &&
      (!/^\d+(\.\d{1,2})?$/.test(String(tarifa)) || tarifa > 99999999.99)
    )
      return false;
    if (!this.formEstado()) return false;
    return true;
  }

  guardarHabitacion(): void {
    if (!this.formValido() || this.formOcupado()) return;
    this.formOcupado.set(true);
    this.formError.set('');

    const req: HabitacionConfiguracionRequest = {
      numeroCuarto: this.formNumero().trim(),
      idTipoHabitacion: this.formIdTipo()!,
      estadoHabitacion: this.esOcupado() ? 'OCUPADO' : this.formEstado(),
      capacidadPersonas: this.formCapacidad(),
      plazasCama: this.formPlazasCama(),
      maxMenoresSinCama: this.formMenoresSinCama(),
      precioPorNoche: this.formPrecioPorNoche(),
    };

    const id = this.modoEdicion() ? this.idHabitacionEditando() : null;
    const archivo = this.archivoImagen();
    // Paso 1: guardar/actualizar datos de la habitación para asegurar su ID.
    // Paso 2: si el usuario eligió archivo, subirlo con multipart/form-data (POST /api/v1/imagenes/habitaciones/{id}).
    this.habitacionesService
      .guardarHabitacion(id, req)
      .pipe(
        switchMap((guardada) =>
          archivo
            ? this.imagenesService.subir(
                'habitaciones',
                guardada.idHabitacion,
                archivo,
              )
            : of(null),
        ),
      )
      .subscribe({
      next: () => {
        this.formOcupado.set(false);

        this.notificacionService.mostrar(
          id
            ? `Habitación ${req.numeroCuarto} actualizada con éxito.`
            : `Habitación ${req.numeroCuarto} registrada con éxito.`,
        );
        void this.router.navigate(['/configuraciones/habitaciones']);
      },
      error: (err) => {
        this.formOcupado.set(false);
        this.formError.set(mensajeError(err));
        // Conservar el formulario y los datos ingresados ante un error
      },
    });
  }
  // === 7. CARGA INICIAL DESDE PARÁMETRO DE RUTA Y NAVEGACIÓN ===
  readonly id = input<string>();
  tipos = signal<TipoHabitacion[]>([]);
  cargando = signal(true);
  error = signal('');
  private readonly alertaError = notificarCambios(this.error, 'error');
  ngOnInit(): void {
    this.cargar();
  }
  cargar(): void {
    this.cargando.set(true);
    this.error.set('');
    const raw = this.id();
    const id = raw ? Number(raw) : null;
    if (raw && (!Number.isInteger(id) || id! <= 0)) {
      this.error.set('Identificador de habitación no válido.');
      this.cargando.set(false);
      return;
    }
    forkJoin({
      tipos: this.tiposService.tiposHabitacion(),
      habitacion:
        id === null ? of(null) : this.habitacionesService.habitacion(id),
    }).subscribe({
      next: ({ tipos, habitacion }) => {
        this.tipos.set(tipos);
        if (habitacion) this.abrirEditar(habitacion);
        else this.abrirCrear();
        this.cargando.set(false);
      },
      error: (e) => {
        this.error.set(mensajeError(e));
        this.cargando.set(false);
      },
    });
  }

  cancelar(): void {
    if (!this.formOcupado())
      void this.router.navigate(['/configuraciones/habitaciones']);
  }
}
