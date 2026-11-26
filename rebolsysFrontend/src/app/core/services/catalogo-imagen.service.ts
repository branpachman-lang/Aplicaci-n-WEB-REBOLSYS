import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';

/**
 * Categorías admitidas para almacenamiento de imágenes en el backend
 * (coinciden con las subcarpetas de `app.catalogo.directorio-imagenes`).
 */
export type CategoriaImagenCatalogo = 'habitaciones' | 'mobiliario' | 'productos';

/**
 * Servicio transversal (core/services) compartido por Habitaciones, Mobiliario y Productos.
 * Gestiona la subida multipart (JPEG/PNG <= 5 MB) y eliminación de imágenes protegidas por JWT.
 */
@Injectable({ providedIn: 'root' })
export class CatalogoImagenService {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/catalogo/imagenes`;

  // === SUBIDA DE IMAGEN INDIVIDUAL (id-{id}.jpg|png) ===
  /** Envía el archivo como multipart/form-data ("archivo") al endpoint protegido del backend. */
  subir(categoria: CategoriaImagenCatalogo, id: number, archivo: File) {
    const form = new FormData();
    form.append('archivo', archivo);
    return this.http.post<{ imagenRuta: string }>(`${this.base}/${categoria}/${id}`, form);
  }

  // === ELIMINACIÓN DE IMAGEN PROPIA ===
  /** Borra la foto individual del registro; el listado volverá a heredar la foto del tipo/categoría. */
  eliminar(categoria: CategoriaImagenCatalogo, id: number) {
    return this.http.delete<void>(`${this.base}/${categoria}/${id}`);
  }
}
