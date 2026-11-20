import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EntregaComprobante } from './entrega-comprobante';
import { NotificacionService } from '../../../core/services/notificacion.service';
import { environment } from '../../../../environments/environment';

describe('Envío de comprobantes', () => {
  let http: HttpTestingController;
  const base = environment.apiUrl + '/comprobantes/9';
  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [EntregaComprobante], providers: [provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  function crear(habilitado = true) {
    const f = TestBed.createComponent(EntregaComprobante);
    f.componentRef.setInput('idComprobante', 9); f.detectChanges();
    http.expectOne(base + '/entrega').flush({ correo: 'cliente@example.test', correoHabilitado: habilitado });
    return f.componentInstance;
  }

  it('sugiere el correo del receptor y espera una acción explícita antes de enviarlo', () => {
    const c = crear();
    expect(c.correo).toBe('cliente@example.test');
    http.expectNone(base + '/correo');
    c.correo = 'otro@example.test'; c.enviar(); c.enviar();
    const envio = http.expectOne(base + '/correo');
    expect(envio.request.body).toEqual({ correo: 'otro@example.test' });
    envio.flush({ mensaje: 'Aceptado por el servidor de correo.' });
    expect(c.ocupado()).toBe(false);
    expect(TestBed.inject(NotificacionService).actual()?.tipo).toBe('exito');
    http.expectNone(r => /pagos|emision-electronica/.test(r.url));
  });

  it('valida destinatario y configuración antes de enviar', () => {
    const c = crear(false); c.enviar();
    expect(TestBed.inject(NotificacionService).actual()?.tipo).toBe('advertencia');
    http.expectNone(base + '/correo');
    c.habilitado.set(true); c.correo = 'invalido'; c.enviar();
    http.expectNone(base + '/correo');
  });

  it('no reintenta automáticamente cuando el resultado SMTP es incierto', () => {
    const c = crear(); c.enviar();
    http.expectOne(base + '/correo').flush({ message: 'Revisa el correo recibido antes de volver a enviarlo.' }, { status: 502, statusText: 'Bad Gateway' });
    expect(c.ocupado()).toBe(false);
    expect(TestBed.inject(NotificacionService).actual()?.error).toBe(true);
    expect(TestBed.inject(NotificacionService).actual()?.texto).toContain('Revisa el correo recibido');
    http.expectNone(base + '/correo');
  });
});
