import { Injectable, signal } from "@angular/core";
import type { SesionActual } from "./sesion-service";

@Injectable({ providedIn: "root" })
export class PermisosUi {
  readonly actual = signal<SesionActual | null>(null);

  esGerente() {
    const sesion = this.actual();
    return sesion?.modo === 'ACTIVA' && !sesion.requiereCambio && sesion.gerente === true;
  }

  permite(...permisos: string[]) {
    const s = this.actual();
    return !!s && !s.requiereCambio && (s.gerente ||
      permisos.some(permiso => s.permisos?.includes(permiso)));
  }

  puedeCobrarReserva() {
    const sesion = this.actual();
    return !!sesion && sesion.modo === 'ACTIVA' && !sesion.requiereCambio &&
      (sesion.roles?.includes('RECEPCIONISTA') ?? false) &&
      (sesion.permisos?.includes('RESERVAS:COBRAR') ?? false);
  }
}
