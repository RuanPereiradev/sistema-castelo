import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { currentSession } from './session';
import { logout } from './authApi';
import { USER_MENU } from './authMessages';
import '../../styles/user-menu.css';

/**
 * The signed-in user as a round initial; opening it shows the name and the way out. It lives in
 * the header of each screen instead of a bar of its own.
 */
export function UserMenu({ avatarClassName }: { readonly avatarClassName?: string }) {
  const navigate = useNavigate();
  const session = currentSession();
  const [open, setOpen] = useState(false);
  const root = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    function closeOutside(event: MouseEvent) {
      if (root.current && !root.current.contains(event.target as Node)) {
        setOpen(false);
      }
    }
    document.addEventListener('mousedown', closeOutside);
    return () => document.removeEventListener('mousedown', closeOutside);
  }, [open]);

  if (!session) {
    return null;
  }
  const fullName = session.user.fullName;
  const initial = fullName.trim().charAt(0).toUpperCase() || '?';

  async function handleLogout() {
    try {
      await logout(session!.accessToken);
    } catch {
      // The session is already dead on the front even when the call fails.
    }
    navigate('/');
  }

  return (
    <div className="user-menu" ref={root}>
      <button
        type="button"
        className={avatarClassName ?? 'user-menu-avatar'}
        aria-label={USER_MENU.open}
        aria-expanded={open}
        onClick={() => setOpen((value) => !value)}
      >
        {initial}
      </button>
      {open && (
        <div className="user-menu-popover" role="menu">
          <span className="user-menu-name">{fullName}</span>
          <button
            type="button"
            className="user-menu-signout"
            role="menuitem"
            title={USER_MENU.signOutHint}
            onClick={() => void handleLogout()}
          >
            {USER_MENU.signOut}
          </button>
        </div>
      )}
    </div>
  );
}
