import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { mensajeError } from './http-error';

describe('Utilidad de errores HTTP con trazabilidad', () => {
  it('incluye el código de seguimiento desde el cuerpo JSON o la cabecera X-Request-Id', () => {
    const conCuerpo = new HttpErrorResponse({
      status: 409,
      statusText: 'Conflict',
      error: { message: 'Stock insuficiente', requestId: 'a1b2c3d4' },
    });
    expect(mensajeError(conCuerpo)).toBe('Stock insuficiente · Código de seguimiento: a1b2c3d4');

    const conCabecera = new HttpErrorResponse({
      status: 500,
      statusText: 'Internal Server Error',
      headers: new HttpHeaders({ 'X-Request-Id': 'ef567890' }),
      error: { message: 'Ocurrió un error interno' },
    });
    expect(mensajeError(conCabecera)).toBe('Ocurrió un error interno · Código de seguimiento: ef567890');
  });

  it('mantiene el mensaje amigable de conexión cuando status es 0', () => {
    const sinRed = new HttpErrorResponse({ status: 0, statusText: 'Unknown Error' });
    expect(mensajeError(sinRed)).toBe(
      'No se pudo conectar con REBOLSYS. Comprueba que el backend esté iniciado.',
    );
  });
});
