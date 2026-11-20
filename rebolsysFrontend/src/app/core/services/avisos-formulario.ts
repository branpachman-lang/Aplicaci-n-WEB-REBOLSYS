import { effect, inject, Signal, untracked } from '@angular/core';
import { NotificacionService, TipoNotificacion } from './notificacion.service';

/** Conserva el detalle del formulario y presenta sus mensajes en la misma bandeja global. */
export function notificarCambios(mensaje: Signal<string>, tipo: TipoNotificacion) {
  const avisos = inject(NotificacionService);
  return effect(() => {
    const texto = mensaje();
    if (texto.trim()) untracked(() => avisos.mostrar(texto, tipo));
  });
}
