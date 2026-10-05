/**
 * Both approved directions are in the code while Ruan decides between them, so
 * the choice is a device preference like any other. It is the only reason this
 * module exists; when one frame wins, this goes away with the other.
 */
export const FRAMES = ['iluminura', 'portal'] as const;

export type Frame = (typeof FRAMES)[number];

export const FRAME_LABELS: Readonly<Record<Frame, string>> = {
  iluminura: 'Iluminura',
  portal: 'Portal',
};

const KEY = 'castel.loginFrame';

function isFrame(value: string | null): value is Frame {
  return value !== null && (FRAMES as readonly string[]).includes(value);
}

export function storedFrame(): Frame {
  try {
    const stored = localStorage.getItem(KEY);
    if (isFrame(stored)) {
      return stored;
    }
  } catch {
    // Falls through to the default.
  }
  return 'iluminura';
}

export function rememberFrame(frame: Frame): void {
  try {
    localStorage.setItem(KEY, frame);
  } catch {
    // The screen still renders; only the preference is lost.
  }
}
