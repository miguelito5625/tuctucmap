/**
 * Sanitización de celdas para exportación CSV (Mitigación OWASP Formula / CSV Injection).
 * 1. Duplica comillas dobles (" -> "")
 * 2. Si el texto comienza con =, +, -, @, \t o \r, antepone un apóstrofe (')
 * 3. Envuelve la celda en comillas dobles.
 */
export function sanitizeCsvCell(value: unknown): string {
  if (value === null || value === undefined) return '""';
  let text = String(value);
  if (text.length === 0) return '""';

  text = text.replace(/"/g, '""');
  if (/^[=+\-@\t\r]/.test(text)) {
    text = `'${text}`;
  }
  return `"${text}"`;
}

