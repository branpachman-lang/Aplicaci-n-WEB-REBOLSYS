import { AbstractControl, ValidationErrors } from '@angular/forms';
import { Catalogo } from '../../../core/models/catalogo.model';

const formatoFechaLima = new Intl.DateTimeFormat('en-CA', { timeZone: 'America/Lima' });

export function documentosTitular(documentos: Catalogo[], empresa: boolean) {
  return documentos.filter(({ codigoSunat }) =>
    empresa ? codigoSunat === '6' : ['1', '4', '7'].some((codigo) => codigoSunat === codigo),
  );
}

export function fechaMaximaTitular(referencia = formatoFechaLima.format(new Date())) {
  const [anio, mes, dia] = referencia.split('-').map(Number);
  const ultimoDiaActual = new Date(Date.UTC(anio, mes, 0)).getUTCDate();
  const ultimoDiaNacimiento = new Date(Date.UTC(anio - 18, mes, 0)).getUTCDate();
  const diaLimite = dia === ultimoDiaActual ? ultimoDiaNacimiento : Math.min(dia, ultimoDiaNacimiento);
  return `${anio - 18}-${String(mes).padStart(2, '0')}-${String(diaLimite).padStart(2, '0')}`;
}

export function tipoDocumento(
  documento: Catalogo | undefined,
): 'DNI' | 'RUC' | 'OTRO' {
  if (!documento) return 'OTRO';
  if (
    documento.codigoSunat === '1' ||
    documento.nombre.trim().toUpperCase() === 'DNI'
  )
    return 'DNI';
  if (
    documento.codigoSunat === '6' ||
    documento.nombre.trim().toUpperCase() === 'RUC'
  )
    return 'RUC';
  return 'OTRO';
}

export function fechaNacimientoValida(
  control: AbstractControl,
): ValidationErrors | null {
  const fecha = control.value as string;
  if (!fecha) return null;
  const hoy = new Date();
  const local = `${hoy.getFullYear()}-${String(hoy.getMonth() + 1).padStart(2, '0')}-${String(hoy.getDate()).padStart(2, '0')}`;
  const parsed = new Date(fecha + 'T12:00:00Z');
  return /^\d{4}-\d{2}-\d{2}$/.test(fecha) &&
    !Number.isNaN(parsed.getTime()) &&
    parsed.toISOString().slice(0, 10) === fecha &&
    fecha <= local
    ? null
    : { fechaNacimiento: true };
}

export function esAdulto(
  fechaNacimiento: string | null,
  referencia: string,
): boolean {
  if (!fechaNacimiento || !/^\d{4}-\d{2}-\d{2}$/.test(fechaNacimiento))
    return false;
  const [anio, mes, dia] = fechaNacimiento.split('-').map(Number);
  const ultimoDia = new Date(Date.UTC(anio + 18, mes, 0)).getUTCDate();
  const cumple = `${anio + 18}-${String(mes).padStart(2, '0')}-${String(Math.min(dia, ultimoDia)).padStart(2, '0')}`;
  return cumple <= referencia.slice(0, 10);
}

export function titularAdulto(
  control: AbstractControl,
): ValidationErrors | null {
  if (!control.value) return null;
  return esAdulto(control.value, formatoFechaLima.format(new Date())) ? null : { titularMenor: true };
}
