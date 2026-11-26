import { Component, DestroyRef, OnInit, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Observable, finalize } from 'rxjs';
import { PermisosUi } from '../../../../core/auth/permisos-ui';
import { Catalogo } from '../../../../core/models/catalogo.model';
import { mensajeError } from '../../../../core/utils/http-error';
import { Icon } from '../../../../shared/components/icono/icono';
import { ClienteDatos, nombreCliente } from '../../../clientes/models/cliente.model';
import { ClienteService } from '../../../clientes/services/cliente-service';
import { DetalleReserva, OcupanteRegistro } from '../../models/reserva-api.model';
import { ReservaService } from '../../services/reserva-service';

interface HuespedEditable extends OcupanteRegistro {
  nombre: string;
}

@Component({
  selector: 'app-huespedes-estadia',
  imports: [FormsModule, Icon],
  templateUrl: './huespedes-estadia.html',
})
export class HuespedesEstadia implements OnInit {
  readonly estancia = input.required<DetalleReserva>();
  readonly documentos = input.required<Catalogo[]>();
  readonly textoVolver = input('Volver');
  readonly guardados = output<DetalleReserva>();
  readonly cerrar = output<void>();

  private readonly clientes = inject(ClienteService);
  private readonly reservas = inject(ReservaService);
  private readonly destroy = inject(DestroyRef);
  readonly acceso = inject(PermisosUi);

  huespedes: HuespedEditable[] = [];
  tipoDocumento: number | null = null;
  numeroDocumento = '';
  nombres = '';
  apellidos = '';
  nacimiento = '';
  telefono = '';
  idClienteSinNacimiento: number | null = null;
  readonly nuevo = signal(false);
  readonly ocupado = signal(false);
  readonly error = signal('');

  ngOnInit() {
    this.tipoDocumento = this.documentos().find(d => d.codigoSunat !== '6')?.id ?? null;
    const ocupantes = this.estancia().ocupantes;
    const idsPorOcupacion = new Map(ocupantes.map(o => [o.idReservaOcupante, o.idCliente]));
    this.huespedes = ocupantes.map(o => ({
      idCliente: o.idCliente,
      nombre: o.nombre,
      ocupaPlazaCama: o.ocupaPlazaCama,
      idClienteResponsable: o.idOcupanteResponsable === null
        ? null : idsPorOcupacion.get(o.idOcupanteResponsable) ?? null,
      parentescoResponsable: o.parentescoResponsable,
    }));
  }

  cambiarDocumento() {
    this.nuevo.set(false);
    this.idClienteSinNacimiento = null;
    this.nombres = '';
    this.apellidos = '';
    this.nacimiento = '';
    this.telefono = '';
    this.error.set('');
  }

  buscar() {
    const documento = this.numeroDocumento.trim();
    if (this.ocupado() || this.tipoDocumento === null || !documento) return;
    this.cambiarDocumento();
    this.ejecutar(this.clientes.buscarPorDocumento(this.tipoDocumento, documento), ({ idCliente, datos }) => {
      if (!idCliente || datos.empresa) {
        this.error.set('Selecciona el documento personal de un huésped.');
      } else if (!datos.fechaNacimiento) {
        if (this.acceso.permite('CLIENTES:EDITAR')) {
          this.idClienteSinNacimiento = idCliente;
          this.nombres = datos.nombres ?? '';
          this.apellidos = datos.apellidos ?? '';
          this.nuevo.set(true);
        } else {
          this.error.set('Completa la fecha de nacimiento en Clientes antes de agregar a este huésped.');
        }
      } else {
        this.agregar(idCliente, datos);
      }
    }, () => {
      if (this.acceso.permite('CLIENTES:CREAR')) this.nuevo.set(true);
      else this.error.set('El documento no está registrado. Solicita el registro del huésped.');
    });
  }

  registrar() {
    if (this.ocupado() || !this.nuevo() || this.tipoDocumento === null ||
        !this.nombres.trim() || !this.apellidos.trim() || !this.nacimiento) return;
    const persona: ClienteDatos = {
      idTipoDocumento: this.tipoDocumento,
      numeroDocumento: this.numeroDocumento.trim(),
      empresa: false,
      nombres: this.nombres.trim(),
      apellidos: this.apellidos.trim(),
      fechaNacimiento: this.nacimiento,
      razonSocial: null,
      direccionFiscal: null,
      correo: null,
      telefono: this.telefono.trim() || null,
    };
    const request = this.idClienteSinNacimiento === null
      ? this.clientes.registrar(persona)
      : this.clientes.completarNacimiento(this.idClienteSinNacimiento, this.nacimiento);
    this.ejecutar(request, cliente => this.agregar(cliente.idCliente, cliente.persona));
  }

  private agregar(id: number, persona: ClienteDatos) {
    if (this.huespedes.some(h => h.idCliente === id)) {
      this.error.set('El huésped ya está en esta habitación.');
      return;
    }
    this.huespedes.push({
      idCliente: id, nombre: nombreCliente(persona), ocupaPlazaCama: true,
      idClienteResponsable: null, parentescoResponsable: null,
    });
    this.numeroDocumento = '';
    this.cambiarDocumento();
  }

  quitar(id: number) {
    this.huespedes = this.huespedes.filter(h => h.idCliente !== id);
    for (const huesped of this.huespedes) {
      if (huesped.idClienteResponsable !== id) continue;
      huesped.idClienteResponsable = null;
      huesped.parentescoResponsable = null;
    }
  }

  guardar() {
    if (this.ocupado() || this.nuevo()) return;
    const ocupantes = this.huespedes.map(({ idCliente, ocupaPlazaCama, idClienteResponsable, parentescoResponsable }) =>
      ({ idCliente, ocupaPlazaCama, idClienteResponsable,
        parentescoResponsable: idClienteResponsable === null ? null : parentescoResponsable }));
    this.ejecutar(this.reservas.guardarOcupantes(this.estancia().idDetalleReserva, ocupantes),
      estancia => this.guardados.emit(estancia));
  }

  private ejecutar<T>(request: Observable<T>, exito: (respuesta: T) => void, sinRegistro?: () => void) {
    this.ocupado.set(true);
    this.error.set('');
    request.pipe(takeUntilDestroyed(this.destroy), finalize(() => this.ocupado.set(false))).subscribe({
      next: exito,
      error: e => {
        if (e.status === 404 && sinRegistro) sinRegistro();
        else this.error.set(mensajeError(e));
      },
    });
  }
}
