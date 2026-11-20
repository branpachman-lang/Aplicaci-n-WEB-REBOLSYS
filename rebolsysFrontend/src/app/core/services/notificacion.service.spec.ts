import { TestBed } from '@angular/core/testing';
import { NotificacionService, TipoNotificacion } from './notificacion.service';

describe('Alertas del sistema', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => { vi.clearAllTimers(); vi.useRealTimers(); });

  it('muestra los cuatro tipos y los retira a los ocho segundos', () => {
    const servicio = TestBed.inject(NotificacionService);
    for (const tipo of ['advertencia', 'informacion', 'error', 'exito'] as TipoNotificacion[])
      servicio.mostrar('Mensaje ' + tipo, tipo);
    expect(servicio.lista().map(n => n.tipo)).toEqual(['advertencia', 'informacion', 'error', 'exito']);
    vi.advanceTimersByTime(7999);
    expect(servicio.lista()).toHaveLength(4);
    vi.advanceTimersByTime(1);
    expect(servicio.lista()).toHaveLength(0);
    expect(servicio.actual()).toBe(null);
  });

  it('cerrar una alerta no cierra las otras ni cambia sus vencimientos', () => {
    const servicio = TestBed.inject(NotificacionService);
    servicio.mostrar('Primera', true);
    vi.advanceTimersByTime(3000);
    servicio.mostrar('Segunda');
    servicio.cerrar(servicio.lista()[0].id);
    vi.advanceTimersByTime(5000);
    expect(servicio.lista().map(n => n.texto)).toEqual(['Segunda']);
    vi.advanceTimersByTime(3000);
    expect(servicio.lista()).toHaveLength(0);
  });

  it('conserva las llamadas anteriores con booleano y evita mensajes duplicados', () => {
    const servicio = TestBed.inject(NotificacionService);
    servicio.mostrar('No se pudo guardar', true);
    servicio.mostrar('No se pudo guardar', true);
    expect(servicio.lista()).toHaveLength(1);
    expect(servicio.actual()?.error).toBe(true);
    servicio.mostrar('Guardado');
    expect(servicio.actual()?.tipo).toBe('exito');
    for (let i = 0; i < 10; i++) servicio.mostrar('Aviso ' + i, 'informacion');
    expect(servicio.lista()).toHaveLength(5);
  });
});
