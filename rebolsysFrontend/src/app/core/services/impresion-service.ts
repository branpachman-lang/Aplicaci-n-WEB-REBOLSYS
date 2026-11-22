import { Injectable } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class ImpresionService {
  async imprimir(ticket: HTMLElement | null): Promise<void> {
    if (!ticket) throw new Error('No hay un comprobante listo para imprimir.');
    const marco = document.createElement('iframe');
    marco.title = 'Impresión del comprobante en A4';
    marco.setAttribute('aria-hidden', 'true');
    marco.style.cssText = 'position:fixed;left:-10000px;top:0;width:210mm;height:297mm;border:0';
    document.body.appendChild(marco);
    const doc = marco.contentDocument;
    const ventana = marco.contentWindow;
    if (!doc || !ventana) { marco.remove(); throw new Error('No se pudo abrir la impresión.'); }
    doc.open();
    doc.write('<!doctype html><html lang="es"><head><meta charset="utf-8"><title>Comprobante interno A4</title></head><body></body></html>');
    doc.close();
    const base = doc.createElement('base');
    base.href = document.baseURI;
    doc.head.appendChild(base);
    document.querySelectorAll('style, link[rel="stylesheet"]').forEach(estilo => doc.head.appendChild(estilo.cloneNode(true)));
    const estilos = doc.createElement('style');
    estilos.textContent = `
      @page { size:A4 portrait; margin:12mm; }
      html, body { width:186mm!important; min-width:0!important; min-height:0!important; margin:0!important; padding:0!important; background:#fff!important; color:#111!important; }
      body { font:10pt/1.4 Arial,sans-serif!important; }
      *, *::before, *::after { box-sizing:border-box; box-shadow:none!important; text-shadow:none!important; }
      body * { color:#111!important; }
      .no-print, button, input, select { display:none!important; }
      .ticket.ticket-a4, [data-ticket].ticket-a4 { display:flex!important; flex-direction:column!important; width:100%!important; max-width:100%!important; min-height:273mm!important; border:0!important; margin:0!important; padding:0!important; }
      .factura-encabezado { display:flex!important; align-items:center!important; justify-content:space-between!important; gap:12mm!important; border-top:5px solid #282078!important; border-bottom:1px solid #b8bfd0!important; padding:5mm 0!important; }
      .factura-encabezado app-marca-hospedaje { flex:1 1 auto; min-width:0; }
      .factura-encabezado .marca { display:flex!important; align-items:center!important; gap:4mm!important; }
      .factura-encabezado .marca img { width:22mm!important; height:22mm!important; object-fit:contain!important; }
      .factura-encabezado .marca strong { display:block!important; font-size:17pt!important; color:#282078!important; }
      .factura-encabezado .marca span { display:block!important; font-size:9pt!important; }
      .factura-numero { flex:0 0 62mm; border:1px solid #222!important; padding:4mm!important; text-align:center!important; }
      .factura-numero strong, .factura-numero span, .factura-numero small { display:block!important; }
      .factura-numero strong { color:#282078!important; font-size:15pt!important; }
      .factura-numero span { margin-top:2mm!important; font-size:14pt!important; font-weight:700!important; }
      .factura-numero small { margin-top:2mm!important; font-size:8pt!important; }
      .factura-datos { display:grid!important; grid-template-columns:1.2fr 1fr!important; gap:8mm!important; margin:7mm 0!important; padding:4mm!important; border:1px solid #777!important; }
      .factura-datos h3 { margin:0 0 2mm!important; color:#282078!important; font-size:10pt!important; }
      .factura-receptor > * { display:block!important; margin:1mm 0!important; }
      .factura-emision p, .comprobante-totales p { display:flex!important; justify-content:space-between!important; gap:6mm!important; margin:1.5mm 0!important; }
      .factura-emision strong { text-align:right!important; }
      .ticket-seccion { margin:5mm 0!important; }
      .ticket-seccion h3 { margin:0 0 2mm!important; padding-bottom:1.5mm!important; border-bottom:1px solid #aaa!important; color:#282078!important; font-size:10pt!important; }
      .ticket-estadia { display:grid!important; grid-template-columns:1fr 1fr!important; column-gap:8mm!important; }
      .ticket-estadia h3 { grid-column:1/-1!important; }
      .ticket-estadia-item { padding:2mm!important; border:1px solid #ddd!important; }
      .ticket-estadia-item p, .ticket-estado-pago, .ticket-medio-pago { display:flex!important; justify-content:space-between!important; gap:5mm!important; margin:1mm 0!important; }
      .table-scroll { overflow:visible!important; }
      table, table.tabla-comprobante { width:100%!important; min-width:0!important; table-layout:fixed!important; border-collapse:collapse!important; font-size:9pt!important; }
      th, td { padding:3mm 2.5mm!important; border:1px solid #999!important; background:#fff!important; color:#111!important; overflow-wrap:anywhere!important; }
      th { background:#eee!important; font-weight:700!important; }
      .tabla-comprobante th:first-child, .tabla-comprobante td:first-child { width:48%; text-align:left!important; }
      .tabla-comprobante th:nth-child(2), .tabla-comprobante td:nth-child(2) { width:12%; text-align:center!important; }
      .tabla-comprobante th:nth-child(3), .tabla-comprobante td:nth-child(3) { width:20%; text-align:right!important; }
      .tabla-comprobante th:last-child, .tabla-comprobante td:last-child { width:20%; text-align:right!important; }
      .comprobante-totales { display:block!important; align-self:flex-end!important; width:82mm!important; margin:4mm 0!important; padding:3mm!important; border:1px solid #999!important; background:#f7f6fb!important; }
      .comprobante-totales strong { font-size:10pt!important; }
      .comprobante-totales .factura-total-final { padding-top:2mm!important; border-top:1px solid #777!important; font-size:12pt!important; font-weight:700!important; }
      .comprobante-totales .factura-total-final strong { font-size:12pt!important; }
      .ticket-pago { margin-top:2mm!important; padding:3mm!important; border:1px solid #aaa!important; }
      .ticket-pago h3 { border:0!important; }
      .ticket-estado-pago strong { font-weight:700!important; }
      .inline-note { margin:3mm 0!important; padding:2.5mm!important; border:1px solid #b8bfd0!important; background:#f7f6fb!important; font-size:8.5pt!important; }
      .ticket-agradecimiento { margin:4mm 0 0!important; text-align:center!important; }
      .factura-pie { margin-top:auto!important; padding-top:5mm!important; border-top:1px solid #bbb!important; color:#555!important; font-size:8pt!important; }
      p, h2, h3 { color:#111!important; }
      tr, .ticket-estadia-item, .ticket-pago, .comprobante-totales { break-inside:avoid!important; }
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
