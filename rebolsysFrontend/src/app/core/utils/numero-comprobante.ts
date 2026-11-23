export function numeroComprobante(serie: string | null | undefined, numero: string | number | null | undefined): string {
  const correlativo = String(numero ?? '');
  return `${serie ?? ''}-${/^\d{1,8}$/.test(correlativo) ? correlativo.padStart(8, '0') : correlativo}`;
}
