import { Injectable, computed, signal } from '@angular/core';
import { environment } from '../../../environments/environment';

const CLAVE = 'rebolsys.auth.jwt';
type Token = { valor: string; expira: number; id: string };

export function esUrlApi(url: string): boolean {
  try {
    const origen = globalThis.location?.origin || 'http://localhost';
    const base = new URL(environment.apiUrl, origen);
    const destino = new URL(url, origen);
    return destino.origin === base.origin && destino.pathname.startsWith(base.pathname.replace(/\/$/, '') + '/');
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class TokenService {
  private readonly actual = signal<Token | null>(this.leer());
  readonly expiraEn = computed(() => this.actual()?.expira ?? null);

  token(): string | null {
    const token = this.actual();
    if (token && token.expira > Date.now()) return token.valor;
    this.limpiar();
    return null;
  }

  guardar(valor: string): void {
    const token = this.interpretar(valor);
    if (!token || token.expira <= Date.now()) throw new Error('La respuesta de autenticación no es válida');
    this.actual.set(token);
    try { sessionStorage.setItem(CLAVE, valor); } catch { /* Se conserva solo en memoria. */ }
  }

  renovar(valor: string, usado: string): void {
    const actual = this.actual();
    const anterior = this.interpretar(usado);
    const nuevo = this.interpretar(valor);
    // Evita restaurar el acceso tras salir o mezclar respuestas de dos usuarios.
    if (actual && anterior?.id === actual.id && nuevo?.id === actual.id && nuevo.expira > actual.expira) {
      this.guardar(valor);
    }
  }

  corresponde(valor: string | null): boolean {
    return valor === (this.actual()?.valor ?? null);
  }

  limpiar(): void {
    this.actual.set(null);
    try { sessionStorage.removeItem(CLAVE); } catch { /* Almacenamiento no disponible. */ }
  }

  private leer(): Token | null {
    try {
      const valor = sessionStorage.getItem(CLAVE);
      const token = valor ? this.interpretar(valor) : null;
      if (token && token.expira > Date.now()) return token;
      sessionStorage.removeItem(CLAVE);
    } catch { /* Almacenamiento no disponible. */ }
    return null;
  }

  private interpretar(valor: string): Token | null {
    try {
      if (typeof valor !== 'string' || valor.length > 8192) return null;
      const partes = valor.split('.');
      if (partes.length !== 3) return null;
      const payload = partes[1].replace(/-/g, '+').replace(/_/g, '/');
      const datos = JSON.parse(atob(payload.padEnd(Math.ceil(payload.length / 4) * 4, '=')));
      return typeof datos.jti === 'string' && typeof datos.exp === 'number' && Number.isFinite(datos.exp)
        ? { valor, expira: datos.exp * 1000, id: datos.jti } : null;
    } catch {
      return null;
    }
  }
}
