import { Injectable, signal } from '@angular/core';

export type TipoNotificacion = 'advertencia' | 'informacion' | 'error' | 'exito';
export interface Notificacion {
  id: number;
  texto: string;
  tipo: TipoNotificacion;
  error: boolean;
}

@Injectable({ providedIn: 'root' })
export class NotificacionService {
  readonly actual = signal<Notificacion | null>(null);
  readonly lista = signal<Notificacion[]>([]);
  private siguiente = 0;
  private readonly temporizadores = new Map<number, ReturnType<typeof setTimeout>>();

  mostrar(texto: string, tipo: TipoNotificacion | boolean = 'exito'): void {
    if (!texto.trim()) return;
    const clase = typeof tipo === 'boolean' ? (tipo ? 'error' : 'exito') : tipo;
    const anterior = this.lista().find(a => a.texto === texto && a.tipo === clase);
    if (anterior) this.cerrar(anterior.id);
    const aviso: Notificacion = { id: ++this.siguiente, texto, tipo: clase, error: clase === 'error' };
    if (this.lista().length >= 5) this.cerrar(this.lista()[0].id);
    this.lista.update(lista => [...lista, aviso]);
    this.actual.set(aviso);
    this.temporizadores.set(aviso.id, setTimeout(() => this.cerrar(aviso.id), 8000));
  }

  cerrar(id: number): void {
    clearTimeout(this.temporizadores.get(id));
    this.temporizadores.delete(id);
    this.lista.update(lista => lista.filter(a => a.id !== id));
    this.actual.set(this.lista().at(-1) ?? null);
  }
}
