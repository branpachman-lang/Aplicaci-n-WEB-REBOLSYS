import { Injectable } from '@angular/core';

/** Aísla el ticket: no imprime la pantalla, los filtros ni el fondo del modal. */
@Injectable({ providedIn: 'root' })
export class ImpresionService {
  async imprimir(ticket: HTMLElement | null): Promise<void> {
    if (!ticket) throw new Error('No hay un comprobante listo para imprimir.');
    const marco = document.createElement('iframe');
    marco.title = 'Impresión del comprobante en 80 mm';
    marco.setAttribute('aria-hidden', 'true');
    marco.style.cssText = 'position:fixed;left:-10000px;top:0;width:80mm;height:1000px;border:0';
    document.body.appendChild(marco);
    const doc = marco.contentDocument;
    const ventana = marco.contentWindow;
    if (!doc || !ventana) { marco.remove(); throw new Error('No se pudo abrir la impresión.'); }
    doc.open();
    doc.write('<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Comprobante</title></head><body></body></html>');
    doc.close();
    const base = doc.createElement('base');
    base.href = document.baseURI;
    doc.head.appendChild(base);
    document.querySelectorAll('style, link[rel="stylesheet"]').forEach(estilo => doc.head.appendChild(estilo.cloneNode(true)));
    const estilos = doc.createElement('style');
    estilos.textContent = `
      html, body { width:74mm!important; min-width:0!important; min-height:0!important; margin:0!important; padding:0!important; background:#fff!important; color:#000!important; }
      body { font:9pt/1.35 Arial,sans-serif!important; }
      *, *::before, *::after { box-sizing:border-box; box-shadow:none!important; text-shadow:none!important; }
      body * { color:#000!important; }
      .no-print, button, input, select { display:none!important; }
      .ticket, [data-ticket] { display:block!important; width:74mm!important; max-width:74mm!important; border:0!important; margin:0!important; padding:0!important; }
      table { width:100%!important; min-width:0!important; table-layout:fixed; border-collapse:collapse; }
      .table-scroll { overflow:visible!important; }
      th, td { padding:5px 2px!important; font-size:8pt!important; color:#000!important; background:#fff!important; overflow-wrap:anywhere; }
      th:last-child, td:last-child { text-align:right; width:23mm; }
      th:nth-child(2):not(:last-child), td:nth-child(2):not(:last-child) { width:10mm; text-align:center; }
      p, h2, h3 { margin:7px 0!important; color:#000!important; }
      h2, h3, header, .receipt-number { text-align:center; font-size:11pt!important; }
      header img { max-width:34mm!important; max-height:22mm!important; object-fit:contain; }
      header { padding-bottom:8px!important; }
      .payment-total { padding:5px 0!important; font-size:10pt!important; background:#fff!important; }
      .payment-total strong { font-size:12pt!important; }
      .inline-note, footer, .pendiente { font-size:8pt!important; color:#000!important; background:#fff!important; border:0!important; padding:4px 0!important; }
      tr, .totales { break-inside:avoid; }
    `;
    doc.head.appendChild(estilos);
    doc.body.appendChild(doc.importNode(ticket, true));
    let temporizador: ReturnType<typeof setTimeout> | undefined;
    try {
      await Promise.race([
        Promise.all([doc.fonts?.ready ?? Promise.resolve(), ...Array.from(doc.images).map(img => img.complete ? Promise.resolve() :
          new Promise<void>(resolve => { img.onload = () => resolve(); img.onerror = () => resolve(); }))]),
        new Promise<void>(resolve => { temporizador = setTimeout(resolve, 4000); }),
      ]);
      clearTimeout(temporizador);
      const altoMm = Math.max(50, Math.ceil(doc.body.scrollHeight * 25.4 / 96) + 8);
      estilos.textContent += `@page { size:80mm ${Math.min(altoMm, 4000)}mm; margin:3mm; }`;
      ventana.addEventListener('afterprint', () => marco.remove(), { once: true });
      ventana.focus();
      ventana.print();
      setTimeout(() => marco.remove(), 120000);
    } catch (error) {
      clearTimeout(temporizador);
      marco.remove();
      throw error;
    }
  }
}
