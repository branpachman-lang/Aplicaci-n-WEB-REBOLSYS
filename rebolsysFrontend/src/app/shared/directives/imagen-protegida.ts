import { HttpClient } from '@angular/common/http';
import { Directive, ElementRef, effect, inject, input, output } from '@angular/core';
import { esUrlApi } from '../../core/services/token-service';

@Directive({ selector: 'img[imagenProtegida]' })
export class ImagenProtegida {
  readonly imagenProtegida = input.required<string>();
  readonly imagenFallback = input<string | null>(null);
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

      if (!ruta) {
        aplicarRespaldo();
        return;
      }
      if (!esUrlApi(ruta)) {
        if (ruta.startsWith('blob:') || ruta.startsWith('/imagenes/') || ruta.startsWith('/images/')) {
          imagen.src = ruta;
        } else {
          aplicarRespaldo();
        }
        return;
      }
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
      limpiar(() => { peticion.unsubscribe(); if (url) URL.revokeObjectURL(url); });
    });
  }
}
