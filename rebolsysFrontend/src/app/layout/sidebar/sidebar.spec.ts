import { TestBed } from "@angular/core/testing";
import { signal } from '@angular/core';
import { Router, provideRouter } from "@angular/router";
import { Subject, of, throwError } from 'rxjs';
import { SesionService } from "../../core/auth/sesion-service";
import { PermisosUi } from "../../core/auth/permisos-ui";
import { Sidebar } from "./sidebar";

describe("Navegación por módulos", () => {
  function preparar(permisos: string[], gerente = false) {
    TestBed.configureTestingModule({ imports: [Sidebar], providers: [provideRouter([]), {
      provide: SesionService, useFactory: () => {
        const acceso = TestBed.inject(PermisosUi);
        return { actual: acceso.actual, permite: (...p: string[]) => acceso.permite(...p),
          error: signal(''), cerrar: vi.fn(() => of(undefined)), cargar: vi.fn(() => of(acceso.actual())) };
      },
    }] });
    TestBed.inject(PermisosUi).actual.set({ idUsuario: 1, usuario: "prueba", modo: "ACTIVA", idTurno: null,
      idFormaPagoEfectivo: null, gerente, permisos });
    const fixture = TestBed.createComponent(Sidebar);
    fixture.detectChanges();
    return { fixture, raiz: fixture.nativeElement as HTMLElement };
  }

  it("agrupa reservas y ventas sin repetir acciones en el menú del gerente", () => {
    const { raiz } = preparar([], true);
    expect(raiz.textContent).not.toContain("Nueva reserva");
    expect(raiz.textContent).not.toContain("Historial de ventas");
    expect(raiz.querySelectorAll('a[href="/ventas"]')).toHaveLength(1);
    expect(raiz.querySelectorAll('a[href="/reservas"]')).toHaveLength(1);
    expect(raiz.textContent).toContain("Venta de productos");
    expect(raiz.querySelector('a[href="/seguridad"]')).not.toBeNull();
  });

  it("lleva al historial a quien solo tiene permiso de consultar ventas", () => {
    const { raiz } = preparar(["VENTAS:VER"]);
    expect(raiz.querySelector('a[href="/ventas/historial"]')?.textContent).toContain("Venta de productos");
    expect(raiz.querySelector('a[href="/ventas"]')).toBeNull();
  });

  it("conserva el acceso para quien solo puede crear reservas", () => {
    const { raiz } = preparar(["RESERVAS:CREAR"]);
    expect(raiz.querySelector('a[href="/reservas/nueva"]')?.textContent).toContain("Reservas");
    expect(raiz.querySelector('a[href="/seguridad"]')).toBeNull();
  });

  it('cierra la sesión desde el sidebar y evita solicitudes duplicadas', async () => {
    const { raiz, fixture } = preparar([], true);
    const cierre = new Subject<void>();
    const servicio = TestBed.inject(SesionService);
    vi.mocked(servicio.cerrar).mockReturnValue(cierre);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const boton = raiz.querySelector<HTMLButtonElement>('.session-panel .logout-button')!;
    boton.click();
    fixture.detectChanges();
    expect(boton.disabled).toBe(true);
    boton.click();
    expect(servicio.cerrar).toHaveBeenCalledTimes(1);
    cierre.next();
    cierre.complete();
    await fixture.whenStable();
    expect(navegar).toHaveBeenCalledWith('/login');
  });

  it('permite reintentar si el servidor rechaza el cierre de sesión', () => {
    const { raiz, fixture } = preparar([], true);
    const servicio = TestBed.inject(SesionService);
    vi.mocked(servicio.cerrar).mockReturnValue(throwError(() => new Error('Sin conexión')));
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const boton = raiz.querySelector<HTMLButtonElement>('.logout-button')!;
    boton.click();
    fixture.detectChanges();
    expect(raiz.querySelector('[role="alert"]')?.textContent).toContain('No se pudo cerrar');
    expect(boton.disabled).toBe(false);
    expect(servicio.actual()?.usuario).toBe('prueba');
    expect(navegar).not.toHaveBeenCalled();
  });
});
