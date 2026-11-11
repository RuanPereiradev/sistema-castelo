/**
 * What the QR printed on a dining table encodes (decision #42 of task qr-table-ordering).
 *
 * The base comes from `VITE_PUBLIC_BASE_URL` when the build sets it, because the admin may
 * open this page at an address the customer's phone cannot reach; otherwise it is the
 * origin the page was opened from.
 */
export function publicBaseUrl(): string {
  const configured = import.meta.env.VITE_PUBLIC_BASE_URL?.trim();
  const base = configured ? configured : window.location.origin;
  return base.replace(/\/+$/, '');
}

/** The customer route of the router, `/mesa/:orderingToken`. */
export function tableOrderingUrl(orderingToken: string): string {
  return `${publicBaseUrl()}/mesa/${encodeURIComponent(orderingToken)}`;
}

const LOOPBACK_HOSTS: ReadonlySet<string> = new Set(['localhost', '0.0.0.0', '[::1]', '::1']);

/**
 * Why a phone would not open this address: a loopback host only opens on the machine that
 * printed it, and a base without `http(s)://` is not a link at all for the camera app.
 */
export type OrderingUrlProblem = 'THIS_MACHINE_ONLY' | 'NOT_A_WEB_ADDRESS';

export function orderingUrlProblem(url: string): OrderingUrlProblem | null {
  let parsed: URL;
  try {
    parsed = new URL(url);
  } catch {
    return 'NOT_A_WEB_ADDRESS';
  }
  if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
    return 'NOT_A_WEB_ADDRESS';
  }
  const hostname = parsed.hostname.toLowerCase();
  const loopback =
    LOOPBACK_HOSTS.has(hostname) || hostname.endsWith('.localhost') || hostname.startsWith('127.');
  return loopback ? 'THIS_MACHINE_ONLY' : null;
}
