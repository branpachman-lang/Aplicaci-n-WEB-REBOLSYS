export function generarClaveOperacion(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = new Uint8Array(8);
  crypto.getRandomValues(bytes);
  const aleatorio = Array.from(bytes, (b) => b.toString(16).padStart(2, '0')).join('');
  return `op-${Date.now().toString(36)}-${aleatorio}`;
}
