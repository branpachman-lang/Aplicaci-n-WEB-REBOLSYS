import { Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { interval } from 'rxjs';
import { SesionService } from '../../../core/services/sesion-service';
import { TokenService } from '../../../core/services/token-service';

const UMBRAL_SEGUNDOS = 120;

@Component({
  selector: 'app-aviso-sesion',
  templateUrl: './aviso-sesion.html',
  styleUrl: './aviso-sesion.css',
})
export class AvisoSesion {
  private readonly tokens = inject(TokenService);
  private readonly sesion = inject(SesionService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);

  private readonly ahora = signal(Date.now());
  private readonly ocultoPara = signal<number | null>(null);
  protected readonly renovando = signal(false);

  protected readonly segundosRestantes = computed(() => {
    const expira = this.tokens.expiraEn();
    if (expira === null) return null;
    return Math.max(0, Math.ceil((expira - this.ahora()) / 1000));
  });

  protected readonly visible = computed(() => {
    const expira = this.tokens.expiraEn();
    const segundos = this.segundosRestantes();
    return (
      expira !== null &&
      segundos !== null &&
      segundos > 0 &&
      segundos <= UMBRAL_SEGUNDOS &&
      this.ocultoPara() !== expira
    );
  });

  constructor() {
    interval(1000)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe(() => {
        this.ahora.set(Date.now());
        const expira = this.tokens.expiraEn();
        if (expira !== null && expira <= this.ahora()) {
          this.sesion.limpiar();
          void this.router.navigateByUrl('/login');
        }
      });
  }

  continuar(): void {
    if (this.renovando()) return;
    this.renovando.set(true);
    this.sesion
      .refrescar()
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.renovando.set(false);
          this.ahora.set(Date.now());
        },
        error: () => {
          this.renovando.set(false);
        },
      });
  }

  ocultar(): void {
    this.ocultoPara.set(this.tokens.expiraEn());
  }
}
