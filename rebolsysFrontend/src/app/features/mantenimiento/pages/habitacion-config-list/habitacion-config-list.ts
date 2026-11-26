import {notificarCambios} from '../../../../core/services/avisos-formulario';
import {CommonModule, DecimalPipe} from '@angular/common';
import {Component, OnDestroy, OnInit, inject, signal} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router} from '@angular/router';
import {Subscription, forkJoin} from 'rxjs';
import {Pagina} from '../../../../core/models/pagina-response';
import {NotificacionService} from '../../../../core/services/notificacion.service';
import {PermisosUi} from '../../../../core/auth/permisos-ui';
import {mensajeError} from '../../../../core/utils/http-error';
import {Icon} from '../../../../shared/components/icono/icono';
import {DialogFocus} from '../../../../shared/directives/dialog-focus';
import {ImagenProtegida} from '../../../../shared/directives/imagen-protegida';
import {
    TipoHabitacion,
    TipoHabitacionRequest,
} from '../../models/tipo-habitacion.model';
import {TipoHabitacionService} from '../../services/tipo-habitacion-service';
import {
    EstadoHabitacion,
    HabitacionConfiguracion,
} from '../../models/habitacion.model';
import {HabitacionService} from '../../services/habitacion-service';

@Component({
    selector: 'app-habitacion-config-list',
    imports: [CommonModule, FormsModule, DecimalPipe, Icon, DialogFocus, ImagenProtegida],
    templateUrl: './habitacion-config-list.html',
    styleUrl: './habitacion-config-list.css',
})
/**
 * CU_21 - Configurar Habitaciones (Listado y gestión):
 * - Consulta paginada con filtros por búsqueda, tipo y estado.
 * - Muestra imagen referencial protegida (con fallback si falla la carga).
 * - Permite consultar detalle, retirar a Mantenimiento (sin borrar historial) y gestionar tipos.
 */
export class HabitacionConfigList implements OnInit, OnDestroy {
    // === 1. INYECCIÓN DE DEPENDENCIAS ===
    private readonly habitacionesService = inject(HabitacionService);
    private readonly tiposService = inject(TipoHabitacionService);
    private readonly notificacionService = inject(NotificacionService);
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    protected readonly acceso = inject(PermisosUi);

    // === 2. ESTADO DEL LISTADO, FILTROS E IMÁGENES ===
    respuesta = signal<Pagina<HabitacionConfiguracion> | null>(null);
    /** IDs de habitaciones cuya imagen falló al cargar para mostrar el placeholder de cama. */
    fallidas = signal<Set<number>>(new Set());

    /** Registra que la imagen protegida de una habitación falló para activar el respaldo visual. */
    marcarImagenFallida(id: number): void {
        this.fallidas.update((set) => {
            const next = new Set(set);
            next.add(id);
            return next;
        });
    }

    tipos = signal<TipoHabitacion[]>([]);

    cargando = signal(true);

    error = signal('');
    private readonly alertaError = notificarCambios(this.error, 'error');

    buscar = signal('');

    filtroTipo = signal<number | null>(null);

    filtroEstado = signal<string>('');

    pagina = signal(0);

    tamanio = signal(12);

    // === 3. ESTADO DE MODALES (DETALLE, RETIRO Y TIPOS DE HABITACIÓN) ===
    detalleVisible = signal(false);

    habitacionDetalle = signal<HabitacionConfiguracion | null>(null);

    detalleCargando = signal(false);

    detalleError = signal('');

    private detallePeticion?: Subscription;

    retirandoVisible = signal(false);

    habitacionARetirar = signal<HabitacionConfiguracion | null>(null);

    retiroOcupado = signal(false);

    retiroError = signal('');

    tiposModalVisible = signal(false);

    tipoEditandoId = signal<number | null>(null);

    tipoNombre = signal('');

    tipoCapacidadMax = signal(1);

    tipoPrecioBase = signal(0);

    tipoFormError = signal('');

    tipoFormOcupado = signal(false);

    tipoPorEliminar = signal<TipoHabitacion | null>(null);

    // === 4. CARGA INICIAL, FILTROS Y PAGINACIÓN ===
    ngOnInit(): void {
        this.cargar();
    }

    cargar(): void {
        this.cargando.set(true);
        this.error.set('');
        forkJoin({
            page: this.habitacionesService.habitaciones({
                buscar: this.buscar().trim(),
                idTipoHabitacion: this.filtroTipo(),
                estado: this.filtroEstado() || null,
                pagina: this.pagina(),
                tamanio: this.tamanio(),
            }),
            tipos: this.tiposService.tiposHabitacion(),
        }).subscribe({
            next: ({page, tipos}) => {
                this.respuesta.set(page);
                this.tipos.set(tipos);
                this.cargando.set(false);
            },
            error: (err) => {
                this.error.set(mensajeError(err));
                this.cargando.set(false);
            },
        });
    }

    filtrar(): void {
        this.pagina.set(0);
        this.cargar();
    }

    cambiarPagina(nuevaPagina: number): void {
        this.pagina.set(nuevaPagina);
        this.cargar();
    }

    // === 5. REGLAS DE RETIRO Y BADGES DE ESTADO ===
    /** Solo se puede retirar a mantenimiento si no está ocupada, en limpieza ni ya retirada. */
    puedeRetirar(room: HabitacionConfiguracion): boolean {
        if (room.estadoHabitacion === 'OCUPADO' || room.estadoHabitacion === 'EN_LIMPIEZA') return false;
        if (room.estadoHabitacion === 'MANTENIMIENTO') return false;
        return true;
    }

    motivoBloqueoRetiro(room: HabitacionConfiguracion): string {
        if (room.estadoHabitacion === 'EN_LIMPIEZA') return 'Finaliza el flujo de limpieza antes de retirar la habitación.';
        if (room.estadoHabitacion === 'OCUPADO') {
            return 'No se puede retirar una habitación que se encuentra ocupada por una estancia activa.';
        }
        if (room.estadoHabitacion === 'MANTENIMIENTO') {
            return 'La habitación ya se encuentra en mantenimiento (retirada de nuevas reservas).';
        }
        return '';
    }

    obtenerClaseBadge(estado: EstadoHabitacion): string {
        switch (estado) {
            case 'DISPONIBLE':
                return 'badge disponible';
            case 'OCUPADO':
                return 'badge en_curso';
            case 'POR_LIMPIAR':
            case 'EN_LIMPIEZA':
                return 'badge limpieza';
            case 'MANTENIMIENTO':
                return 'badge mantenimiento';
            default:
                return 'badge';
        }
    }

    obtenerTextoEstado(estado: EstadoHabitacion): string {
        switch (estado) {
            case 'DISPONIBLE':
                return 'Disponible';
            case 'OCUPADO':
                return 'Ocupada';
            case 'POR_LIMPIAR':
                return 'Por limpiar';
            case 'EN_LIMPIEZA':
                return 'En limpieza';
            case 'MANTENIMIENTO':
                return 'Mantenimiento';
            default:
                return estado;
        }
    }

    // === 6. MODAL DE DETALLE DE HABITACIÓN ===
    abrirDetalle(room: HabitacionConfiguracion): void {
        this.habitacionDetalle.set(room);
        this.detalleVisible.set(true);
        this.cargarDetalle();
    }

    cargarDetalle(): void {
        const room = this.habitacionDetalle();
        if (!room) return;
        this.detallePeticion?.unsubscribe();
        this.detalleCargando.set(true);
        this.detalleError.set('');
        this.detallePeticion = this.habitacionesService
            .habitacion(room.idHabitacion)
            .subscribe({
                next: (h) => {
                    this.habitacionDetalle.set(h);
                    this.detalleCargando.set(false);
                },
                error: (e) => {
                    this.detalleError.set(mensajeError(e));
                    this.detalleCargando.set(false);
                },
            });
    }

    cerrarDetalle(): void {
        this.detallePeticion?.unsubscribe();
        this.detalleVisible.set(false);
        this.habitacionDetalle.set(null);
        this.detalleCargando.set(false);
        this.detalleError.set('');
    }

    ngOnDestroy(): void {
        this.detallePeticion?.unsubscribe();
    }

    // === 7. MODAL DE RETIRO A MANTENIMIENTO (BAJA LÓGICA CON PATCH, NUNCA DELETE) ===
    abrirRetiro(room: HabitacionConfiguracion): void {
        if (!this.puedeRetirar(room)) return;
        this.habitacionARetirar.set(room);
        this.retiroError.set('');
        this.retirandoVisible.set(true);
    }

    cerrarRetiro(): void {
        if (this.retiroOcupado()) return;
        this.retirandoVisible.set(false);
        this.habitacionARetirar.set(null);
    }

    confirmarRetiro(): void {
        const room = this.habitacionARetirar();
        if (!room || this.retiroOcupado()) return;

        this.retiroOcupado.set(true);
        this.retiroError.set('');

        // PATCH a /habitaciones/{id}/estado con MANTENIMIENTO, NUNCA DELETE
        this.habitacionesService
            .cambiarEstadoHabitacion(room.idHabitacion, 'MANTENIMIENTO')
            .subscribe({
                next: () => {
                    this.retiroOcupado.set(false);
                    this.retirandoVisible.set(false);
                    this.habitacionARetirar.set(null);
                    this.notificacionService.mostrar(
                        `Habitación ${room.numeroCuarto} retirada de nuevas reservas (estado: Mantenimiento). Su historial se conserva.`,
                    );
                    this.cargar();
                },
                error: (err) => {
                    this.retiroOcupado.set(false);
                    this.retiroError.set(mensajeError(err));
                },
            });
    }

    // === 8. MODAL DE GESTIÓN DE TIPOS DE HABITACIÓN (CRUD) ===
    abrirTiposModal(): void {
        this.tipoFormError.set('');
        this.cancelarEdicionTipo();
        this.tipoPorEliminar.set(null);
        this.tiposModalVisible.set(true);
        this.cargarTipos();
    }

    cerrarTiposModal(): void {
        if (this.tipoFormOcupado()) return;
        this.tiposModalVisible.set(false);
    }

    cargarTipos(): void {
        this.tiposService.tiposHabitacion().subscribe({
            next: (t) => this.tipos.set(t),
            error: (err) => this.tipoFormError.set(mensajeError(err)),
        });
    }

    iniciarEdicionTipo(t: TipoHabitacion): void {
        this.tipoEditandoId.set(t.idTipoHabitacion);
        this.tipoNombre.set(t.nombreTipo);
        this.tipoCapacidadMax.set(t.capacidadMax);
        this.tipoPrecioBase.set(t.precioBase);
        this.tipoFormError.set('');
    }

    cancelarEdicionTipo(): void {
        this.tipoEditandoId.set(null);
        this.tipoNombre.set('');
        this.tipoCapacidadMax.set(1);
        this.tipoPrecioBase.set(0);
        this.tipoFormError.set('');
    }

    tipoValido(): boolean {
        const nom = this.tipoNombre().trim();
        if (!nom || nom.length > 30) return false;
        const cap = this.tipoCapacidadMax();
        if (cap < 1 || cap > 99) return false;
        const precio = this.tipoPrecioBase();
        if (precio === null || precio === undefined || precio < 0) return false;
        return true;
    }

    guardarTipo(): void {
        if (!this.tipoValido() || this.tipoFormOcupado()) return;
        this.tipoFormOcupado.set(true);
        this.tipoFormError.set('');

        const req: TipoHabitacionRequest = {
            nombreTipo: this.tipoNombre().trim(),
            capacidadMax: Number(this.tipoCapacidadMax()),
            precioBase: Number(this.tipoPrecioBase()),
        };

        const id = this.tipoEditandoId();
        this.tiposService.guardarTipoHabitacion(id, req).subscribe({
            next: () => {
                this.tipoFormOcupado.set(false);
                this.cancelarEdicionTipo();
                this.notificacionService.mostrar(
                    id
                        ? `Tipo ${req.nombreTipo} actualizado.`
                        : `Tipo ${req.nombreTipo} registrado.`,
                );
                this.cargarTipos();
                this.cargar();
            },
            error: (err) => {
                this.tipoFormOcupado.set(false);
                this.tipoFormError.set(mensajeError(err));
            },
        });
    }

    solicitarEliminarTipo(t: TipoHabitacion): void {
        this.tipoFormError.set('');
        this.tipoPorEliminar.set(t);
    }

    confirmarEliminarTipo(): void {
        const t = this.tipoPorEliminar();
        if (!t || this.tipoFormOcupado()) return;

        this.tipoFormOcupado.set(true);
        this.tipoFormError.set('');

        this.tiposService.eliminarTipoHabitacion(t.idTipoHabitacion).subscribe({
            next: () => {
                this.tipoFormOcupado.set(false);
                this.tipoPorEliminar.set(null);
                this.notificacionService.mostrar(`Tipo ${t.nombreTipo} eliminado.`);
                this.cargarTipos();
                this.cargar();
            },
            error: (err) => {
                this.tipoFormOcupado.set(false);
                // Mostrar conflicto 409 o error del servidor sin cerrar el modal
                this.tipoFormError.set(mensajeError(err));
            },
        });
    }

    // === 9. NAVEGACIÓN A FORMULARIOS (CREAR / EDITAR) ===
    abrirCrear(): void {
        void this.router.navigate(['nuevo'], {relativeTo: this.route});
    }

    abrirEditar(room: HabitacionConfiguracion): void {
        void this.router.navigate([room.idHabitacion, 'editar'], {
            relativeTo: this.route,
        });
    }
}
