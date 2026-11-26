export interface ClienteDatos {
  idTipoDocumento: number | null;
  numeroDocumento: string;
  empresa: boolean;
  nombres: string | null;
  apellidos: string | null;
  fechaNacimiento: string | null;
  razonSocial: string | null;
  direccionFiscal: string | null;
  correo: string | null;
  telefono: string | null;
}

export interface Cliente {
  idCliente: number | null;
  datos: ClienteDatos;
}

export function nombreCliente(datos: ClienteDatos): string {
  return datos.empresa ? datos.razonSocial ?? '' : [datos.nombres, datos.apellidos].filter(Boolean).join(' ');
}
