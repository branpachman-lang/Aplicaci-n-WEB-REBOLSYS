import { TestBed } from '@angular/core/testing';
import { ImpresionService } from './impresion-service';

describe('Impresión térmica', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => {
    document.querySelectorAll('iframe').forEach(f => f.remove());
    vi.restoreAllMocks(); vi.clearAllTimers(); vi.useRealTimers();
  });

  it('imprime solo el ticket en un marco de 80 mm y conserva como texto los datos del cliente', async () => {
    const imprimir = vi.fn();
    const principal = vi.spyOn(window, 'print').mockImplementation(() => {});
    const agregar = document.body.appendChild.bind(document.body);
    vi.spyOn(document.body, 'appendChild').mockImplementation(<T extends Node>(n: T): T => {
      const resultado = agregar(n);
      if (n instanceof HTMLIFrameElement && n.contentWindow) {
        n.contentWindow.print = imprimir;
        n.contentWindow.focus = vi.fn();
      }
      return resultado;
    });
    const ticket = document.createElement('article');
    ticket.setAttribute('data-ticket', '');
    ticket.textContent = 'Cliente <script>contenido no ejecutable</script>';
    await TestBed.inject(ImpresionService).imprimir(ticket);
    const marco = document.querySelector('iframe')!;
    expect(marco.contentDocument?.head.textContent).toContain('size:80mm');
    expect(marco.contentDocument?.body.textContent).toContain('<script>contenido no ejecutable</script>');
    expect(marco.contentDocument?.scripts).toHaveLength(0);
    expect(imprimir).toHaveBeenCalledTimes(1);
    expect(principal).not.toHaveBeenCalled();
    marco.contentWindow?.dispatchEvent(new Event('afterprint'));
    expect(document.querySelector('iframe')).toBe(null);
  });

  it('no abre impresión si falta el comprobante', async () => {
    await expect(TestBed.inject(ImpresionService).imprimir(null)).rejects.toThrow('No hay un comprobante');
    expect(document.querySelector('iframe')).toBe(null);
  });
});
