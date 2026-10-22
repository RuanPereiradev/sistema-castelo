/**
 * What the phone remembers between visits (decision #4): the name typed at the
 * entrance, a random device code generated once, and the last order request
 * sent from this table. Storage can be missing or refuse writes (private tab):
 * every access fails silently, and the screen then asks the name again.
 */
const NAME_KEY = 'castel.customer.name';
const DEVICE_KEY = 'castel.customer.deviceId';
const lastRequestKey = (orderingToken: string) => `castel.customer.lastOrderRequest.${orderingToken}`;

export const MAXIMUM_CUSTOMER_NAME_LENGTH = 40;

/** The backend accepts 8 to 64 of `[A-Za-z0-9_-]`; a stored code outside it is replaced. */
const DEVICE_ID_PATTERN = /^[A-Za-z0-9_-]{8,64}$/;

function read(key: string): string | null {
  try {
    return window.localStorage.getItem(key);
  } catch {
    return null;
  }
}

function write(key: string, value: string | null): void {
  try {
    if (value === null) {
      window.localStorage.removeItem(key);
    } else {
      window.localStorage.setItem(key, value);
    }
  } catch {
    // Storage refused: the value lives only in memory for this visit.
  }
}

export function storedCustomerName(): string | null {
  const name = read(NAME_KEY)?.trim() ?? '';
  return name.length > 0 && name.length <= MAXIMUM_CUSTOMER_NAME_LENGTH ? name : null;
}

export function storeCustomerName(name: string): void {
  write(NAME_KEY, name);
}

/** Reads the device code, or creates and stores a new one. */
export function customerDeviceId(): string {
  const stored = read(DEVICE_KEY);
  if (stored && DEVICE_ID_PATTERN.test(stored)) {
    return stored;
  }
  const created = newDeviceId();
  write(DEVICE_KEY, created);
  return created;
}

/**
 * `crypto.randomUUID` exists only on HTTPS or localhost; a phone opening the dev
 * server by its LAN address has plain HTTP, where `getRandomValues` still works.
 */
function newDeviceId(): string {
  if (typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}

export function storedLastOrderRequestId(orderingToken: string): string | null {
  return read(lastRequestKey(orderingToken));
}

export function storeLastOrderRequestId(orderingToken: string, requestId: string | null): void {
  write(lastRequestKey(orderingToken), requestId);
}
