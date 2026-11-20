import { Component, effect, inject, input, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../../environments/environment';
import { Icon } from '../icono/icono';
import { NotificacionService } from '../../../core/services/notificacion.service';
import { PermisosUi } from '../../../core/services/permisos-ui';
import { mensajeError } from '../../../core/utils/http-error';

@Component({
  selector: 'app-entrega-comprobante',
  imports: [FormsModule, Icon],
  templateUrl: './entrega-comprobante.html',
  styleUrl: './entrega-comprobante.css',
})
export class EntregaComprobante {
  idComprobante = input.required<number>();
  private readonly http = inject(HttpClient);
  private readonly avisos = inject(NotificacionService);
  readonly acceso = inject(PermisosUi);
  readonly ocupado = signal(false);
  readonly abierto = signal(false);
  readonly habilitado = signal(false);
  correo = '';
  private readonly base = environment.apiUrl + '/comprobantes';

  constructor() {
    effect(onCleanup => {
      const id = this.idComprobante();
      this.habilitado.set(false);
      const consulta = this.http.get<{ correo: string; correoHabilitado: boolean }>(`${this.base}/${id}/entrega`)
        .subscribe({
          next: d => { this.correo = d.correo; this.habilitado.set(d.correoHabilitado); },
          error: e => this.avisos.mostrar(mensajeError(e), 'error'),
        });
      onCleanup(() => consulta.unsubscribe());
    });
  }

  enviar() {
    if (this.ocupado()) return;
    if (!this.habilitado()) { this.avisos.mostrar('El envío por correo aún no está habilitado para este hospedaje.', 'advertencia'); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(this.correo.trim()) || this.correo.length > 254) {
      this.avisos.mostrar('Ingresa un correo válido para recibir el comprobante.', 'advertencia'); return;
    }
    this.ocupado.set(true);
    this.http.post<{ mensaje: string }>(`${this.base}/${this.idComprobante()}/correo`, { correo: this.correo.trim() })
      .subscribe({
        next: r => { this.ocupado.set(false); this.abierto.set(false); this.avisos.mostrar(r.mensaje, 'exito'); },
        error: e => { this.ocupado.set(false); this.avisos.mostrar(mensajeError(e), 'error'); },
      });
  }

  descargar() {
    if (this.ocupado()) return;
    this.ocupado.set(true);
    this.http.get(`${this.base}/${this.idComprobante()}/ticket`, { responseType: 'blob' }).subscribe({
      next: archivo => {
        const url = URL.createObjectURL(archivo);
        const enlace = document.createElement('a');
        enlace.href = url;
        enlace.download = `comprobante-${this.idComprobante()}-80mm.pdf`;
        enlace.click();
        setTimeout(() => URL.revokeObjectURL(url), 30000);
        this.ocupado.set(false);
      },
      error: e => { this.ocupado.set(false); this.avisos.mostrar(mensajeError(e), 'error'); },
    });
  }
}
