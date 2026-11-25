import { HttpErrorResponse } from '@angular/common/http';
import { ErrorResponse } from '../models/error-response';

export function erroresDeValidacion(
  err: HttpErrorResponse,
): Record<string, string> {
  const respuesta = err.error as Partial<ErrorResponse> | null;
  return respuesta?.validationErrors ?? {};
}

export function mensajeError(error: HttpErrorResponse|Error): string {
  if(error instanceof Error&&!('status' in error))return error.message;
  const err=error as HttpErrorResponse;
  const mensajeServidor = typeof err.error?.message === 'string' ? err.error.message : '';
  if (err.status === 0 || ((err.status === 502 || err.status === 504) && !mensajeServidor)) {
    return 'No se pudo conectar con REBOLSYS. Comprueba que el backend esté iniciado.';
  }
  const respuesta = err.error as Partial<ErrorResponse> | null;
  const requestId =
    (typeof respuesta?.requestId === 'string' ? respuesta.requestId.trim() : '') ||
    (err.headers?.get('X-Request-Id')?.trim() ?? '');
  const sufijo = requestId ? ` · Código de seguimiento: ${requestId}` : '';
  const validaciones = Object.values(erroresDeValidacion(err));
  if (validaciones.length) {
    return [...new Set(validaciones)].join('. ') + sufijo;
  }
  return (respuesta?.message || `Error ${err.status}: ${err.statusText}`) + sufijo;
}
