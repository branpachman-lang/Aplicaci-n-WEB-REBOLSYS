import { HttpInterceptorFn, HttpResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, tap, throwError } from 'rxjs';
import { SesionService } from './sesion-service';
import { esUrlApi, TokenService } from './token-service';

export const proteccionSesionInterceptor: HttpInterceptorFn = (req, next) => {
  if (!esUrlApi(req.url)) return next(req);
  const tokens = inject(TokenService);
  const sesion = inject(SesionService);
  const router = inject(Router);
  const login = new URL(req.url, globalThis.location.origin).pathname.endsWith('/sesion/iniciar');
  const token = login ? null : tokens.token();
  const peticion = token ? req.clone({ setHeaders: { Authorization: 'Bearer ' + token } }) : req;
  return next(peticion).pipe(
    tap(evento => {
      if (evento instanceof HttpResponse && token) {
        const renovado = evento.headers.get('X-Auth-Token');
        if (renovado) tokens.renovar(renovado, token);
      }
    }),
    catchError(error => {
      if (!login && error.status === 401 && (!token || tokens.corresponde(token))) {
        sesion.limpiar();
        void router.navigateByUrl('/login');
      }
      return throwError(() => error);
    }),
  );
};
