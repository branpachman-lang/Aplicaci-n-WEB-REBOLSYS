import { numeroComprobante } from './numero-comprobante';

describe('Numeración de comprobantes', () => {
  it('presenta factura y boleta con ocho dígitos', () => {
    expect(numeroComprobante('F002', 3848)).toBe('F002-00003848');
    expect(numeroComprobante('B002', '1')).toBe('B002-00000001');
    expect(numeroComprobante('F002', '00003848')).toBe('F002-00003848');
  });
  it('conserva series y correlativos históricos', () => {
    expect(numeroComprobante('FINT', '12')).toBe('FINT-00000012');
    expect(numeroComprobante('BINT', '123456789')).toBe('BINT-123456789');
  });
});
