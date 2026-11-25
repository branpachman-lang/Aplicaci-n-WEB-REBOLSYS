import { TestBed } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { SesionService } from '../../core/services/sesion-service';
import { Header } from './header';

describe('Header', () => {
  const eventos = new Subject<NavigationEnd>();
  let urlActual = '/inicio';

  beforeEach(() => {
    urlActual = '/inicio';
    TestBed.configureTestingModule({
      imports: [Header],
      providers: [
        { provide: SesionService, useValue: { cargar: () => of({}) } },
        {
          provide: Router,
          useValue: {
            get url() {
              return urlActual;
            },
            events: eventos.asObservable(),
          },
        },
      ],
    });
  });

  it('muestra el título correcto para reportes, sistema y edición de ventas', () => {
    const fixture = TestBed.createComponent(Header);
    const header = fixture.componentInstance as unknown as { titulo: () => string };

    urlActual = '/reportes';
    eventos.next(new NavigationEnd(1, '/reportes', '/reportes'));
    expect(header.titulo()).toBe('REPORTES');

    urlActual = '/configuraciones/sistema';
    eventos.next(new NavigationEnd(2, '/configuraciones/sistema', '/configuraciones/sistema'));
    expect(header.titulo()).toBe('CONFIGURACIÓN DEL SISTEMA');

    urlActual = '/ventas/15/editar';
    eventos.next(new NavigationEnd(3, '/ventas/15/editar', '/ventas/15/editar'));
    expect(header.titulo()).toBe('EDITAR VENTA');
  });
});
