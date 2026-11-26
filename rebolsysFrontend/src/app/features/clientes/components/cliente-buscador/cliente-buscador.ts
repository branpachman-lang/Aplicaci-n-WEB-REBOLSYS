import { Component, DestroyRef, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { Catalogo } from '../../../../core/models/catalogo.model';
import { mensajeError } from '../../../../core/utils/http-error';
import { Cliente, ClienteDatos, nombreCliente } from '../../models/cliente.model';
import { ClienteService } from '../../services/cliente-service';
import { documentosTitular, tipoDocumento } from '../../utils/persona-validacion';

@Component({
  selector: 'app-cliente-buscador',
  imports: [ReactiveFormsModule],
  templateUrl: './cliente-buscador.html',
  styleUrl: './cliente-buscador.css',
})
export class ClienteBuscador {
  readonly documentos = input.required<Catalogo[]>();
  readonly bloqueado = input(false);
  readonly seleccionar = output<Cliente>();
  readonly registrar = output<Pick<ClienteDatos, 'empresa' | 'idTipoDocumento' | 'numeroDocumento'>>();
  private readonly clientes = inject(ClienteService);
  private readonly destroy = inject(DestroyRef);
  private consulta = 0;
  readonly empresa = signal(false);
  readonly opciones = computed(() => documentosTitular(this.documentos(), this.empresa()));
  readonly resultados = signal<Cliente[]>([]);
  readonly buscando = signal(false);
  readonly mensaje = signal('');
  readonly nombreCliente = nombreCliente;
  readonly form = new FormGroup({
    nombre: new FormControl('', { nonNullable: true }),
    tipo: new FormControl<number | null>(null),
    numero: new FormControl('', { nonNullable: true }),
  });

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(() => {
      this.consulta++;
      this.resultados.set([]);
      this.mensaje.set('');
      this.buscando.set(false);
    });
  }

  cambiarTipo(empresa: boolean) {
    if (empresa === this.empresa()) return;
    this.empresa.set(empresa);
    this.form.reset({ tipo: this.opciones()[0]?.id ?? null });
  }

  buscar() {
    if (this.buscando() || this.bloqueado()) return;
    const { nombre, tipo, numero } = this.form.getRawValue();
    const identidad = nombre.trim().replace(/\s+/g, ' ');
    const documento = numero.trim();
    const opcion = this.opciones().find(({ id }) => id === tipo);
    const formato = tipoDocumento(opcion);
    const error = !identidad && !documento ? 'Ingresa el nombre o el número de documento.'
      : documento && !opcion ? 'Selecciona el tipo de documento.'
      : documento && formato === 'DNI' && !/^\d{8}$/.test(documento) ? 'El DNI debe contener exactamente 8 dígitos.'
      : documento && formato === 'RUC' && !/^\d{11}$/.test(documento) ? 'El RUC debe contener exactamente 11 dígitos.'
      : '';
    if (error) {
      this.mensaje.set(error);
      return;
    }

    const consulta = ++this.consulta;
    this.buscando.set(true);
    this.resultados.set([]);
    this.mensaje.set('');
    this.clientes.buscarTitulares(this.empresa(), identidad, tipo, documento)
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: (clientes) => {
          if (consulta !== this.consulta) return;
          this.buscando.set(false);
          this.resultados.set(clientes.slice(0, 20));
          if (!clientes.length) {
            this.mensaje.set('No encontramos clientes con estos datos. Revisa la búsqueda o registra al cliente.');
          } else if (clientes.length > 20) {
            this.mensaje.set('Se muestran 20 coincidencias. Usa el número de documento para precisar la búsqueda.');
          }
        },
        error: (e) => {
          if (consulta !== this.consulta) return;
          this.buscando.set(false);
          this.mensaje.set(mensajeError(e));
        },
      });
  }

  abrirRegistro() {
    const { tipo, numero } = this.form.getRawValue();
    const existente = numero.trim() && this.resultados().find(({ datos }) =>
      datos.idTipoDocumento === tipo && datos.numeroDocumento.toUpperCase() === numero.trim().toUpperCase(),
    );
    if (existente) {
      this.seleccionar.emit(existente);
      return;
    }
    this.registrar.emit({
      empresa: this.empresa(),
      idTipoDocumento: tipo ?? this.opciones()[0]?.id ?? null,
      numeroDocumento: tipo === null ? '' : numero.trim(),
    });
  }

  etiquetaDocumento(id: number | null) {
    return this.documentos().find((documento) => documento.id === id)?.nombre ?? 'Documento';
  }
}
