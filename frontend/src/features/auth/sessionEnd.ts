/**
 * Why the last session ended. The API cannot tell the login screen this — the
 * request that failed was the refresh, and by then the user is already gone —
 * so whoever handles that failure writes the code here and the screen reads it
 * once, on mount.
 *
 * `?sessionEnd=CODE` in the address bar does the same thing, which is how the
 * state can be seen before there is an app shell to produce it.
 */
const KEY = 'castel.sessionEnd';

export function recordSessionEnd(code: string): void {
  try {
    sessionStorage.setItem(KEY, code);
  } catch {
    // The message is a courtesy; losing it costs nothing.
  }
}

export function takeSessionEnd(): string | null {
  let stored: string | null = null;
  try {
    stored = sessionStorage.getItem(KEY);
    sessionStorage.removeItem(KEY);
  } catch {
    stored = null;
  }
  const fromUrl = new URLSearchParams(window.location.search).get('sessionEnd');
  return stored ?? fromUrl;
}
