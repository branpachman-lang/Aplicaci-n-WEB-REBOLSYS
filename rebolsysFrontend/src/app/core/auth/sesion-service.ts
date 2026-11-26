import { Injectable, inject, signal } from "@angular/core";
import { HttpClient } from "@angular/common/http";
import { EMPTY, Observable, Subject, catchError, defer, finalize, map, of, shareReplay, takeUntil, tap, throwError } from "rxjs";
import { environment } from "../../../environments/environment";
import { PermisosUi } from "./permisos-ui";
import { TokenService } from './token-service';
export interface Autenticacion {
  token: string;
  tipo: 'Bearer';
  expiraEnSegundos: number;
  sesion: SesionActual;
}
export interface SesionActual {
  idUsuario: number;
  usuario: string;
  modo: "ACTIVA";
  idTurno: number | null;
  idFormaPagoEfectivo: number | null;
  requiereCambio?: boolean;
  gerente?: boolean;
  permisos?: string[];
  roles?: string[];
}
@Injectable({ providedIn: "root" })
export class SesionService {
  private http = inject(HttpClient);
  private acceso = inject(PermisosUi);
  private readonly tokens = inject(TokenService);
  private readonly cambioDeAcceso = new Subject<void>();
  private peticion?: Observable<SesionActual>;
  private pendiente?: Observable<SesionActual>;
  private versionAcceso = 0;
  private navegacion?: number;
  private recienAutenticado = false;
  readonly actual = this.acceso.actual;
  readonly versionPendientes = signal(0);
  error = signal("");
  cargar(forzar = false) {
    if (this.pendiente) return this.pendiente;
    if (!forzar && this.peticion) return this.peticion;
    const version = this.versionAcceso;
    const consulta: Observable<SesionActual> = defer(() => version === this.versionAcceso
      ? this.http.get<SesionActual>(environment.apiUrl + '/sesion/actual')
      : EMPTY)
      .pipe(
        takeUntil(this.cambioDeAcceso),
        tap((s) => {
          this.recordar(s);
        }),
        catchError((e) => {
          if (version === this.versionAcceso) {
            this.peticion = undefined;
            this.actual.set(null);
            this.error.set("No se pudo obtener la sesión de operación.");
          }
          return throwError(() => e);
        }),
        finalize(() => {
          if (this.pendiente === consulta) this.pendiente = undefined;
        }),
        shareReplay({ bufferSize: 1, refCount: false }),
      );
    this.peticion = this.pendiente = consulta;
    return consulta;
  }

  cargarEnNavegacion(id: number) {
    if (this.navegacion === id) return this.cargar();
    this.navegacion = id;
    const forzar = !this.recienAutenticado;
    this.recienAutenticado = false;
    return this.cargar(forzar);
  }

  refrescar() {
    this.invalidarConsulta();
    return this.cargar(true);
  }

  iniciar(usuario: string, contrasena: string) {
    return this.http.post<Autenticacion>(environment.apiUrl + "/sesion/iniciar", { usuario, contrasena })
      .pipe(map(respuesta => this.autenticar(respuesta)),
        tap(s => { this.limpiarPendientes(); this.recordar(s); }));
  }

  cambiar(actual: string, nueva: string) {
    return this.http.post<Autenticacion>(environment.apiUrl + "/sesion/contrasena", { actual, nueva })
      .pipe(map(respuesta => this.autenticar(respuesta)),
        tap(s => this.recordar(s)));
  }

  cerrar() {
    return this.http.post<void>(environment.apiUrl + "/sesion/cerrar", {}).pipe(tap(() => this.limpiar()));
  }

  permite(...permisos: string[]) {
    return this.acceso.permite(...permisos);
  }

  limpiar() {
    this.invalidarConsulta();
    this.tokens.limpiar();
    this.limpiarPendientes();
    this.navegacion = undefined;
    this.recienAutenticado = false;
    this.actual.set(null);
    this.error.set("");
  }

  private limpiarPendientes() {
    try {
      for (let i = sessionStorage.length - 1; i >= 0; i--) {
        const clave = sessionStorage.key(i);
        if (clave && /^(rebolsys\.cuenta\.|rebolsys\.reservas\.|rebolsys\.venta\.pendiente$)/.test(clave)) {
          sessionStorage.removeItem(clave);
        }
      }
    } catch {
      // El navegador puede bloquear el almacenamiento de la pestaña.
    } finally {
      this.versionPendientes.update(version => version + 1);
    }
  }

  private recordar(s: SesionActual) {
    this.actual.set(s);
    this.error.set("");
  }

  private autenticar(respuesta: Autenticacion): SesionActual {
    if (respuesta.tipo !== 'Bearer' || !respuesta.sesion) throw new Error('La respuesta de autenticación no es válida');
    this.tokens.guardar(respuesta.token);
    this.invalidarConsulta();
    this.peticion = of(respuesta.sesion);
    this.navegacion = undefined;
    this.recienAutenticado = true;
    return respuesta.sesion;
  }

  private invalidarConsulta() {
    this.versionAcceso++;
    this.cambioDeAcceso.next();
    this.peticion = this.pendiente = undefined;
  }
}
