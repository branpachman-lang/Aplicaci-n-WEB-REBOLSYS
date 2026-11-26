import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../../environments/environment';
import {
  TipoHabitacion,
  TipoHabitacionRequest,
} from '../models/tipo-habitacion.model';
/** Servicio HTTP de Tipos de Habitación (CU_21): listar, crear/actualizar y eliminar tipos. */
@Injectable({ providedIn: 'root' })
export class TipoHabitacionService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;
  tiposHabitacion() {
    return this.http.get<TipoHabitacion[]>(`${this.base}/tipos-habitacion`);
  }

  guardarTipoHabitacion(id: number | null, datos: TipoHabitacionRequest) {
    return id === null
      ? this.http.post<TipoHabitacion>(`${this.base}/tipos-habitacion`, datos)
      : this.http.put<TipoHabitacion>(
          `${this.base}/tipos-habitacion/${id}`,
          datos,
        );
  }

  eliminarTipoHabitacion(id: number) {
    return this.http.delete<void>(`${this.base}/tipos-habitacion/${id}`);
  }
}
