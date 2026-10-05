import type { Role } from './roles';
import type { LoginResponse } from './authApi';

/**
 * Tokens live in `sessionStorage`, not `localStorage`: the counter tablet and
 * the kitchen screen are shared devices, and closing the tab has to end the
 * access. Device preferences — the chosen frame, the last destination, the last
 * username — do live in `localStorage`, and none of them is a credential.
 */
const TOKENS_KEY = 'castel.session';
const LAST_USERNAME_KEY = 'castel.lastUsername';
const LAST_DESTINATION_KEY = 'castel.lastDestination';

export interface Session {
  readonly accessToken: string;
  readonly refreshToken: string;
  readonly user: LoginResponse['user'];
}

/** Every accessor swallows its own failure: storage throws in private mode. */
function read(storage: Storage, key: string): string | null {
  try {
    return storage.getItem(key);
  } catch {
    return null;
  }
}

function write(storage: Storage, key: string, value: string): void {
  try {
    storage.setItem(key, value);
  } catch {
    // Nothing to do: the screen works without the preference.
  }
}

export function storeSession(response: LoginResponse): Session {
  const session: Session = {
    accessToken: response.accessToken,
    refreshToken: response.refreshToken,
    user: response.user,
  };
  write(sessionStorage, TOKENS_KEY, JSON.stringify(session));
  write(localStorage, LAST_USERNAME_KEY, response.user.username);
  return session;
}

export function clearSession(): void {
  try {
    sessionStorage.removeItem(TOKENS_KEY);
  } catch {
    // Same as above.
  }
}

export function currentSession(): Session | null {
  const stored = read(sessionStorage, TOKENS_KEY);
  if (!stored) {
    return null;
  }
  try {
    return JSON.parse(stored) as Session;
  } catch {
    return null;
  }
}

export function lastUsername(): string {
  return read(localStorage, LAST_USERNAME_KEY) ?? '';
}

export function lastDestination(): Role | null {
  return read(localStorage, LAST_DESTINATION_KEY) as Role | null;
}

export function rememberDestination(role: Role): void {
  write(localStorage, LAST_DESTINATION_KEY, role);
}
