import { HttpClient, HttpResponse } from '@angular/common/http';
import { Directive, DestroyRef, HostListener, inject, input } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NotificacionService } from '../../core/services/notificacion.service';
import { esUrlApi } from '../../core/auth/token-service';
import { mensajeError } from '../../core/utils/http-error';

@Directive({ selector: 'a[archivoProtegido]' })
export class ArchivoProtegido {
  readonly archivoProtegido = input.required<string>();
  readonly descargarArchivo = input(false);
  private readonly http = inject(HttpClient);
  private readonly avisos = inject(NotificacionService);
  private readonly destroy = inject(DestroyRef);
  private ocupado = false;
  private readonly urls = new Set<string>();
  private readonly temporizadores = new Set<ReturnType<typeof setTimeout>>();

  constructor() {
    this.destroy.onDestroy(() => {
      this.urls.forEach(url => URL.revokeObjectURL(url));
      this.temporizadores.forEach(id => clearTimeout(id));
    });
  }

  @HostListener('click', ['$event'])
  @HostListener('auxclick', ['$event'])
  abrir(evento: MouseEvent): void {
    if (evento.button === 2) return;
    evento.preventDefault();
    const ruta = this.archivoProtegido();
    if (this.ocupado || !esUrlApi(ruta)) return;
    this.ocupado = true;
    const ventana = this.descargarArchivo() ? null : window.open('about:blank', '_blank');
    if (ventana) ventana.opener = null;
    this.http.get(ruta, { responseType: 'blob', observe: 'response' })
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: respuesta => {
          this.ocupado = false;
          const archivo = respuesta.body;
          if (!archivo) { ventana?.close(); return; }
          const url = URL.createObjectURL(archivo);
          this.urls.add(url);
          if (ventana && /^(image\/(png|jpeg)|application\/pdf)$/.test(archivo.type)) {
            ventana.location.replace(url);
          } else {
            ventana?.close();
            const enlace = document.createElement('a');
            enlace.href = url;
            enlace.download = this.nombre(respuesta);
            enlace.click();
          }
          const temporizador = setTimeout(() => {
            URL.revokeObjectURL(url); this.urls.delete(url); this.temporizadores.delete(temporizador);
          }, 60_000);
          this.temporizadores.add(temporizador);
        },
        error: error => {
          this.ocupado = false;
          ventana?.close();
          this.avisos.mostrar(mensajeError(error), true);
        },
      });
  }

  private nombre(respuesta: HttpResponse<Blob>): string {
    const cabecera = respuesta.headers.get('Content-Disposition') || '';
    return (cabecera.match(/filename="?([^";]+)"?/)?.[1] || 'evidencia')
      .replace(/[\\/\x00-\x1f]/g, '_').slice(0, 120);
  }
}
