import { HttpClient } from '@angular/common/http';
import { Directive, ElementRef, effect, inject, input, output } from '@angular/core';
import { esUrlApi } from '../../core/auth/token-service';

/**
 * Directiva compartida `img[imagenProtegida]` para renderizar imágenes servidas detrás de JWT.
 * - Un `<img [src]="/api/v1/...">` normal NO envía el header `Authorization: Bearer`.
 * - Esta directiva descarga el binario vía `HttpClient` (con el interceptor JWT), crea un `blob:`
 *   temporal para pintarlo en el `<img>` y lo libera con `URL.revokeObjectURL` al destruir la vista.
 */
@Directive({ selector: 'img[imagenProtegida]' })
export class ImagenProtegida {
  // === ENTRADAS / SALIDAS ===
  readonly imagenProtegida = input.required<string>();
  /** Imagen estática opcional a mostrar si la descarga falla o no hay ruta. */
  readonly imagenFallback = input<string | null>(null);
  /** Evento emitido al fallar la carga para activar placeholders en las tarjetas. */
  readonly imagenError = output<void>();
  private readonly http = inject(HttpClient);
  private readonly elemento = inject<ElementRef<HTMLImageElement>>(ElementRef);

  constructor() {
    effect(limpiar => {
      const ruta = this.imagenProtegida();
      const respaldo = this.imagenFallback();
      const imagen = this.elemento.nativeElement;
      const aplicarRespaldo = () => {
        if (respaldo) imagen.src = respaldo;
        else imagen.removeAttribute('src');
      };

      // 1. Sin ruta: aplicar imagen de respaldo o limpiar src
      if (!ruta) {
        aplicarRespaldo();
        return;
      }

      // 2. Rutas locales (blob de vista previa en formulario o assets estáticos del front)
      if (!esUrlApi(ruta)) {
        if (ruta.startsWith('blob:') || ruta.startsWith('/imagenes/') || ruta.startsWith('/images/')) {
          imagen.src = ruta;
        } else {
          aplicarRespaldo();
        }
        return;
      }

      // 3. Rutas protegidas del API (/api/v1/...): descargar como Blob con JWT
      imagen.removeAttribute('src');
      let url: string | null = null;
      const peticion = this.http.get(ruta, { responseType: 'blob' }).subscribe({
        next: archivo => {
          if (!/^image\/(png|jpeg|webp|gif)$/.test(archivo.type)) {
            aplicarRespaldo();
            this.imagenError.emit();
            return;
          }
          url = URL.createObjectURL(archivo);
          imagen.src = url;
        },
        error: () => {
          aplicarRespaldo();
          this.imagenError.emit();
        },
      });
      // Limpieza de memoria al cambiar de ruta o destruir el componente
      limpiar(() => { peticion.unsubscribe(); if (url) URL.revokeObjectURL(url); });
    });
  }
}
