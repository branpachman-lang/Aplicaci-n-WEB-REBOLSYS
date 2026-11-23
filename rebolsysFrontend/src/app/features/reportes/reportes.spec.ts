import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MarcaService } from '../../core/services/marca-service';
import { Reportes } from './reportes';

describe('Integración de reportes e impresión', () => {
  function preparar() {
    TestBed.configureTestingModule({ imports: [Reportes], providers: [
      provideHttpClient(), provideHttpClientTesting(),
      { provide: MarcaService, useValue: { consultar: () => of({ nombre: 'Hospedaje de prueba' }) } },
    ] });
    const fixture = TestBed.createComponent(Reportes);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(req => req.url === '/api/v1/reportes/incidencias').flush({
      filas: [], pagina: 0, haySiguiente: false,
    });
    fixture.detectChanges();
    const raiz = fixture.nativeElement as HTMLElement;
    const boton = (texto: string) => Array.from(raiz.querySelectorAll('button'))
      .find(elemento => elemento.textContent?.trim() === texto)!;
    return { fixture, http, raiz, boton };
  }

  it('mantiene filtros, resultados y paginación al integrar el diseño de ticket', () => {
    const { fixture, http, raiz, boton } = preparar();
    expect(raiz.querySelector('form.filtros')).not.toBeNull();
    expect(raiz.querySelector('.resultado')).not.toBeNull();
    boton('Inventario de productos').click();
    http.expectOne(req => req.url === '/api/v1/reportes/inventario/productos'
      && req.params.get('pagina') === '0').flush({ filas: [], pagina: 0, haySiguiente: true });
    fixture.detectChanges();
    boton('Siguiente').click();
    http.expectOne(req => req.url === '/api/v1/reportes/inventario/productos'
      && req.params.get('pagina') === '1').flush({ filas: [], pagina: 1, haySiguiente: false });
    fixture.detectChanges();
    expect(raiz.textContent).toContain('Página 2');
    http.verify();
  });

  it('adapta el detalle financiero nuevo tanto a la tabla como al ticket', () => {
    const { fixture, http, raiz, boton } = preparar();
    boton('Financiero').click();
    http.expectOne('/api/v1/reportes/financiero').flush({
      generadoPor: 'gerente@example.test', cobrosReservas: 100, cobrosVentas: 0,
      ingresosManuales: 0, egresos: 25, neto: 75,
      filas: [
        { fecha: '2026-11-22T10:00:00', tipo: 'RESERVAS', referencia: 'R-1',
          concepto: 'Cobro de reserva', medioPago: 'YAPE', ingreso: 100, egreso: 0 },
        { fecha: '2026-11-22T11:00:00', tipo: 'EGRESOS', referencia: 'FC-2',
          concepto: 'Compra de limpieza', medioPago: 'EFECTIVO', ingreso: 0, egreso: 25 },
      ],
    });
    fixture.detectChanges();
    expect(raiz.querySelectorAll('.tabla-financiera tbody tr')).toHaveLength(2);
    expect(raiz.querySelector('.resultado')?.textContent).toContain('gerente@example.test');
    expect(raiz.querySelector('.boleta')?.textContent).toContain('Compra de limpieza');
    expect(raiz.querySelector('.boleta')?.textContent).not.toContain('NaN');
    http.verify();
  });

  it('aplica el formato elegido antes de abrir el diálogo de impresión', async () => {
    const { fixture, http, raiz, boton } = preparar();
    const modos: boolean[] = [];
    const imprimir = vi.spyOn(window, 'print').mockImplementation(() => {
      modos.push(raiz.classList.contains('formato-ticket'));
    });
    try {
      boton('Imprimir ticket').click();
      await fixture.whenStable();
      boton('Imprimir A4').click();
      await fixture.whenStable();
      expect(modos).toEqual([true, false]);
      expect(imprimir).toHaveBeenCalledTimes(2);
      http.verify();
    } finally {
      imprimir.mockRestore();
    }
  });
});
