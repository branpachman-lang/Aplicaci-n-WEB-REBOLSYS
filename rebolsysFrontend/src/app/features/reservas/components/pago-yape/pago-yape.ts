import { CurrencyPipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import {
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { of, switchMap } from 'rxjs';
import { environment } from '../../../../../environments/environment';
import { mensajeError } from '../../../../core/utils/http-error';

type Modo = 'SIMULADO' | 'MERCADO_PAGO';
type Ambiente = 'PRUEBA' | 'PRODUCCION';
type Estado = 'ENVIANDO' | 'PENDIENTE' | 'APLICADO' | 'RECHAZADO' | 'REVISION';

export interface ConfigYape {
  habilitado: boolean;
  modo: Modo;
  ambiente: Ambiente;
}

export interface IntentoYape {
  claveOperacion: string;
  importe: number;
  telefonoEnmascarado: string;
  modo: Modo;
  ambiente: Ambiente;
  estado: Estado;
  ordenProveedor?: string | null;
  idPago?: number | null;
  mensajeProveedor?: string | null;
}

@Component({
  selector: 'app-pago-yape',
  imports: [CurrencyPipe, FormsModule],
  templateUrl: './pago-yape.html',
  styleUrl: './pago-yape.css',
})
export class PagoYape {
  readonly idReserva = input.required<number>();
  readonly saldo = input.required<number>();
  readonly estadoEstancia = input<string>('');
  readonly revision = input<number>(0);
  readonly aplicado = output<void>();

  private readonly http = inject(HttpClient);
  private readonly destroy = inject(DestroyRef);
  private readonly apiBase = environment.apiUrl;

  readonly config = signal<ConfigYape | null>(null);
  readonly intento = signal<IntentoYape | null>(null);
  readonly error = signal('');
  readonly ocupado = signal(false);

  readonly importe = signal<number | null>(null);
  readonly telefono = signal('');
  readonly otp = signal('');

  private claveActual: string | null = null;

  readonly puedeIniciarNuevo = computed(() => {
    const estado = this.intento()?.estado;
    const estanciaActiva =
      this.estadoEstancia() === 'CONFIRMADA' || this.estadoEstancia() === 'EN_CURSO';
    return (
      !!this.config()?.habilitado &&
      estanciaActiva &&
      this.saldo() > 0 &&
      (!estado || estado === 'RECHAZADO' || estado === 'APLICADO')
    );
  });

  readonly formularioValido = computed(() => {
    const monto = Number(this.importe());
    const centimosExactos =
      Number.isFinite(monto) &&
      Math.abs(monto * 100 - Math.round(monto * 100)) < 0.000001;
    const tel = this.telefono().trim();
    const codigo = this.otp().trim();
    return (
      monto > 0 &&
      monto <= this.saldo() &&
      centimosExactos &&
      /^[0-9]{9}$/.test(tel) &&
      /^[0-9]{6}$/.test(codigo)
    );
  });

  constructor() {
    effect((onCleanup) => {
      const id = this.idReserva();
      this.revision();
      this.claveActual = null;
      this.intento.set(null);
      this.error.set('');
      this.otp.set('');
      this.ocupado.set(true);
      const peticion = this.http
        .get<ConfigYape>(`${this.apiBase}/pagos/yape/config`)
        .pipe(
          switchMap((cfg) => {
            this.config.set(cfg);
            return cfg.habilitado
              ? this.http.get<IntentoYape | null>(`${this.apiBase}/reservas/${id}/yape`)
              : of(null);
          }),
        )
        .subscribe({
          next: (intento) => {
            this.intento.set(intento);
            this.ocupado.set(false);
          },
          error: () => {
            this.config.set(null);
            this.ocupado.set(false);
          },
        });
      onCleanup(() => peticion.unsubscribe());
    });

    effect(() => {
      const saldoActual = this.saldo();
      if (saldoActual > 0) {
        this.importe.set(Number(saldoActual.toFixed(2)));
      } else {
        this.importe.set(null);
      }
    });
  }

  cambiarDatos() {
    this.claveActual = null;
  }

  etiquetaModo(cfg: ConfigYape): string {
    if (cfg.modo === 'SIMULADO') return 'Simulador local';
    return cfg.ambiente === 'PRUEBA'
      ? 'Mercado Pago · Prueba'
      : 'Mercado Pago · En vivo';
  }

  iniciar() {
    if (this.ocupado() || !this.puedeIniciarNuevo() || !this.formularioValido()) return;
    const id = this.idReserva();
    this.claveActual ??= crypto.randomUUID();
    const payload = {
      claveOperacion: this.claveActual,
      importe: Number(Number(this.importe()).toFixed(2)),
      telefono: this.telefono().trim(),
      otp: this.otp().trim(),
    };
    this.ocupado.set(true);
    this.error.set('');
    this.http
      .post<IntentoYape>(`${this.apiBase}/reservas/${id}/yape`, payload)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          if (id !== this.idReserva()) return;
          this.claveActual = null;
          this.otp.set('');
          this.aplicarResultado(r);
        },
        error: (e) => {
          if (id !== this.idReserva()) return;
          if (e.status >= 400 && e.status < 500) {
            this.claveActual = null;
          }
          this.ocupado.set(false);
          this.error.set(mensajeError(e));
        },
      });
  }

  conciliar(i: IntentoYape) {
    if (this.ocupado()) return;
    const id = this.idReserva();
    this.ocupado.set(true);
    this.error.set('');
    this.http
      .post<IntentoYape>(
        `${this.apiBase}/reservas/${id}/yape/${i.claveOperacion}/conciliar`,
        {},
      )
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          if (id === this.idReserva()) this.aplicarResultado(r);
        },
        error: (e) => {
          if (id !== this.idReserva()) return;
          this.ocupado.set(false);
          this.error.set(mensajeError(e));
        },
      });
  }

  private aplicarResultado(i: IntentoYape) {
    this.ocupado.set(false);
    this.intento.set(i);
    if (i.estado === 'APLICADO') {
      this.telefono.set('');
      this.aplicado.emit();
    }
  }
}
