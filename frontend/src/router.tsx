import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useLoaderData,
  useNavigate,
} from 'react-router-dom';
import { LoginPage } from './features/auth/LoginPage';
import { DiningRoomPage } from './features/dining-room/DiningRoomPage';
import { currentSession, type Session } from './features/auth/session';
import { destinationsFor, type Destination } from './features/auth/destinations';
import { logout } from './features/auth/authApi';
import './styles/shell.css';

/** Shell da área logada: cabeçalho e navegação. */
export function Shell() {
  const navigate = useNavigate();
  const session = useLoaderData() as Session;

  async function handleLogout() {
    try {
      await logout(session.accessToken);
    } catch {
      // Ignore: logout falhou mas a sessão já morreu no front.
    }
    navigate('/');
  }

  const destinations = destinationsFor(session.user.roles);
  const isMultiArea = destinations.length > 1 || session.user.roles.includes('ADMIN');

  return (
    <div className="shell">
      <header className="shell-header">
        <div className="shell-header-content">
          <h1>Castel</h1>
          <div className="shell-header-right">
            {isMultiArea && (
              <span className="shell-role-badge" title={session.user.roles.join(', ')}>
                {destinations[0]?.toUpperCase() || 'Admin'}
              </span>
            )}
            <span className="shell-username">{session.user.fullName}</span>
            <button
              className="shell-logout-btn"
              onClick={handleLogout}
              aria-label="Sair"
              title="Sair de todos os dispositivos"
            >
              Sair
            </button>
          </div>
        </div>
      </header>
      <main className="shell-main">
        <Outlet />
      </main>
    </div>
  );
}

/** Proteção de rota: verifica sessão e papel. */
async function sessionLoader(): Promise<Session> {
  const session = currentSession();
  if (!session) {
    throw new Response('Unauthorized', { status: 401 });
  }
  return session;
}

async function requiredDestinationLoader(roles: readonly Destination[]): Promise<Session> {
  const session = currentSession();
  if (!session) {
    throw new Response('Unauthorized', { status: 401 });
  }
  const userDestinations = destinationsFor(session.user.roles);
  if (!roles.some((r) => userDestinations.includes(r))) {
    throw new Response('Forbidden', { status: 403 });
  }
  return session;
}

export const router = createBrowserRouter([
  {
    path: '/',
    element: <LoginPage />,
  },
  {
    path: '/',
    element: <Shell />,
    loader: sessionLoader,
    errorElement: <Navigate to="/" replace />,
    children: [
      {
        path: 'salao',
        element: <DiningRoomPage />,
        loader: () => requiredDestinationLoader(['WAITER']),
      },
      {
        path: '*',
        element: <Navigate to="/salao" replace />,
      },
    ],
  },
]);
