/**
 * Money arrives as a decimal string ("186.00") and is formatted at the edge of
 * the screen with string operations only: no `Number`, no floating point.
 */
export function formatMoney(value: string): string {
  const negative = value.startsWith('-');
  const unsigned = negative ? value.slice(1) : value;
  const [integerPart = '0', fraction = ''] = unsigned.split('.');
  const grouped = integerPart.replace(/\B(?=(\d{3})+(?!\d))/g, '.');
  const cents = fraction.padEnd(2, '0').slice(0, 2);
  return `${negative ? '−' : ''}R$ ${grouped},${cents}`;
}

/** "350 g" or "1,250 kg" for a line sold by weight. */
export function formatWeight(grams: number): string {
  if (grams < 1000) {
    return `${grams} g`;
  }
  const kilos = Math.floor(grams / 1000);
  const rest = String(grams % 1000).padStart(3, '0');
  return `${kilos},${rest} kg`;
}

/**
 * Whether a decimal string is above, at or below zero, read from its text: a leading `-` and the
 * digits alone. For colour and labels only; comparing two amounts is still the backend's job.
 */
export function moneySign(value: string): 'positive' | 'zero' | 'negative' {
  const unsigned = value.trim().replace(/^[-+]/, '');
  if (!/[1-9]/.test(unsigned)) {
    return 'zero';
  }
  return value.trim().startsWith('-') ? 'negative' : 'positive';
}
