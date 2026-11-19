import { HttpClient } from '@angular/common/http';
import { Directive, ElementRef, effect, inject, input } from '@angular/core';
import { esUrlApi } from '../../core/services/token-service';

@Directive({ selector: 'img[imagenProtegida]' })
export class ImagenProtegida {
  readonly imagenProtegida = input.required<string>();
  private readonly http = inject(HttpClient);
  private readonly elemento = inject<ElementRef<HTMLImageElement>>(ElementRef);

  constructor() {
    effect(limpiar => {
      const ruta = this.imagenProtegida();
      const imagen = this.elemento.nativeElement;
      if (!esUrlApi(ruta)) {
        if (ruta.startsWith('blob:')) imagen.src = ruta;
        else imagen.removeAttribute('src');
        return;
      }
      imagen.removeAttribute('src');
      let url: string | null = null;
      const peticion = this.http.get(ruta, { responseType: 'blob' }).subscribe({
        next: archivo => {
          if (!/^image\/(png|jpeg)$/.test(archivo.type)) return;
          url = URL.createObjectURL(archivo);
          imagen.src = url;
        },
        error: () => imagen.removeAttribute('src'),
      });
      limpiar(() => { peticion.unsubscribe(); if (url) URL.revokeObjectURL(url); });
    });
  }
}
