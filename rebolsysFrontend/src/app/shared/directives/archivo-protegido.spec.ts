import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { proteccionSesionInterceptor } from '../../core/services/proteccion-sesion-interceptor';
import { SesionService } from '../../core/services/sesion-service';
import { TokenService } from '../../core/services/token-service';
import { ArchivoProtegido } from './archivo-protegido';

@Component({ imports: [ArchivoProtegido], template: '<a [archivoProtegido]="ruta" [href]="ruta">Ver foto</a>' })
class Ejemplo {
  ruta = '/api/v1/limpieza/evidencias/foto.png';
}

describe('Evidencias protegidas por JWT', () => {
  const cambiar = vi.fn();
  beforeEach(() => {
    sessionStorage.clear(); cambiar.mockClear();
    const Original = URL;
    vi.stubGlobal('URL', class extends Original {
      static override createObjectURL = vi.fn(() => 'blob:evidencia-local');
      static override revokeObjectURL = vi.fn();
    });
    vi.spyOn(window, 'open').mockReturnValue({ opener: window, location: { replace: cambiar }, close: vi.fn() } as unknown as Window);
    TestBed.configureTestingModule({ imports: [Ejemplo], providers: [
      provideHttpClient(withInterceptors([proteccionSesionInterceptor])), provideHttpClientTesting(),
      { provide: SesionService, useValue: { limpiar: vi.fn() } },
      { provide: Router, useValue: { navigateByUrl: vi.fn() } },
    ] });
    const jwt = 'eyJhbGciOiJIUzI1NiJ9.' + btoa(JSON.stringify({ jti: 'evidencia', exp: Math.floor(Date.now() / 1000) + 1800 })) + '.firma';
    TestBed.inject(TokenService).guardar(jwt);
  });
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    vi.restoreAllMocks(); vi.unstubAllGlobals(); sessionStorage.clear();
  });

  it('obtiene la foto con Bearer y abre un Blob sin poner el token en el enlace', () => {
    const fixture = TestBed.createComponent(Ejemplo); fixture.detectChanges();
    fixture.nativeElement.querySelector('a').click();
    const req = TestBed.inject(HttpTestingController).expectOne('/api/v1/limpieza/evidencias/foto.png');
    expect(req.request.headers.get('Authorization')).toBe('Bearer ' + TestBed.inject(TokenService).token());
    expect(req.request.url).not.toContain('token');
    req.flush(new Blob(['imagen'], { type: 'image/png' }));
    expect(cambiar).toHaveBeenCalledWith('blob:evidencia-local');
    fixture.destroy();
  });

  it('rechaza destinos externos antes de adjuntar credenciales', () => {
    const fixture = TestBed.createComponent(Ejemplo);
    fixture.componentInstance.ruta = 'https://externo.example/foto.png'; fixture.detectChanges();
    fixture.nativeElement.querySelector('a').click();
    TestBed.inject(HttpTestingController).expectNone('https://externo.example/foto.png');
    expect(window.open).not.toHaveBeenCalled(); fixture.destroy();
  });
});
