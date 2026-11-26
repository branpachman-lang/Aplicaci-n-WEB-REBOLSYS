import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { SesionService } from '../../../core/auth/sesion-service';
import { TokenService } from '../../../core/auth/token-service';
import { AvisoSesion } from './aviso-sesion';

const jwtEn = (segundos: number) =>
  'eyJhbGciOiJIUzI1NiJ9.' +
  btoa(JSON.stringify({ jti: 'aviso-1', exp: Math.floor(Date.now() / 1000) + segundos })) +
  '.firma';

describe('AvisoSesion', () => {
  const refrescar = vi.fn(() => of({}));
  const limpiar = vi.fn();
  const navigateByUrl = vi.fn();

  beforeEach(() => {
    vi.useFakeTimers();
    sessionStorage.clear();
    refrescar.mockClear();
    limpiar.mockClear();
    navigateByUrl.mockClear();
    TestBed.configureTestingModule({
      imports: [AvisoSesion],
      providers: [
        { provide: SesionService, useValue: { refrescar, limpiar } },
        { provide: Router, useValue: { navigateByUrl } },
      ],
    });
  });

  afterEach(() => {
    TestBed.resetTestingModule();
    vi.useRealTimers();
    sessionStorage.clear();
  });

  it('permanece oculto si faltan más de 120 segundos y se muestra dentro del umbral', () => {
    const tokens = TestBed.inject(TokenService);
    tokens.guardar(jwtEn(300));
    const fixture = TestBed.createComponent(AvisoSesion);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.session-banner')).toBeNull();

    tokens.guardar(jwtEn(90));
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.session-banner')?.textContent).toContain(
      'Tu sesión vencerá en',
    );
  });

  it('renueva la sesión al continuar y permite ocultar el aviso', () => {
    TestBed.inject(TokenService).guardar(jwtEn(60));
    const fixture = TestBed.createComponent(AvisoSesion);
    vi.advanceTimersByTime(1000);
    fixture.detectChanges();

    fixture.componentInstance.continuar();
    expect(refrescar).toHaveBeenCalledOnce();

    fixture.componentInstance.ocultar();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.session-banner')).toBeNull();
  });

  it('cierra sesión y redirige a login cuando expira el token', () => {
    TestBed.inject(TokenService).guardar(jwtEn(2));
    TestBed.createComponent(AvisoSesion);
    vi.advanceTimersByTime(3000);
    expect(limpiar).toHaveBeenCalled();
    expect(navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
