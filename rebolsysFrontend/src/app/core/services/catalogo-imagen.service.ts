  import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

export type CategoriaImagenCatalogo = 'habitaciones' | 'mobiliario' | 'productos';

@Injectable({ providedIn: 'root' })
export class CatalogoImagenService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/catalogo/imagenes`;

  subir(categoria: CategoriaImagenCatalogo, id: number, archivo: File) {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<{ imagenRuta: string }>(`${this.base}/${categoria}/${id}`, form);
  }

  eliminar(categoria: CategoriaImagenCatalogo, id: number) {
    return this.http.delete<void>(`${this.base}/${categoria}/${id}`);
  }
}
