import { Injectable, inject } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { environment } from "../../../../environments/environment";
import { Cliente } from "../models/cliente.model";
import { ClienteDatos } from "../models/cliente.model";
import { ClienteDetalle } from "../../reservas/models/reserva-api.model";
import { concatMap } from 'rxjs';

@Injectable({ providedIn: "root" })
export class ClienteService {
  private readonly http = inject(HttpClient);
  private readonly base = environment.apiUrl;

  buscarPorDocumento(tipo: number, documento: string) {
    return this.http.get<Cliente>(`${this.base}/recepcion/clientes`, {
      params: { tipoDocumento: tipo, numeroDocumento: documento },
    });
  }

  buscarTitulares(empresa: boolean, nombre: string, tipo: number | null, numero: string) {
    const params: Record<string, string | number | boolean> = { empresa };
    if (nombre) params['nombre'] = nombre;
    if (numero) params['numeroDocumento'] = numero;
    if (numero && tipo !== null) params['tipoDocumento'] = tipo;
    return this.http.get<Cliente[]>(`${this.base}/recepcion/clientes/titulares`, { params });
  }

  registrarTitular(persona: ClienteDatos) {
    return this.http.post<ClienteDetalle>(`${this.base}/recepcion/clientes/titulares`, persona);
  }

  registrar(persona: ClienteDatos) {
    return this.http.post<ClienteDetalle>(`${this.base}/clientes`, { persona });
  }
  obtener(id:number){return this.http.get<ClienteDetalle>(`${this.base}/clientes/${id}`);}
  completarNacimiento(idCliente: number, fechaNacimiento: string) {
    const url = `${this.base}/clientes/${idCliente}`;
    return this.http.get<ClienteDetalle>(url).pipe(concatMap((actual) =>
      this.http.put<ClienteDetalle>(url, { persona: { ...actual.persona, fechaNacimiento },
        restriccionesMedicas: actual.restriccionesMedicas })));
  }
}
