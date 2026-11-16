import {
  createBrowserRouter,
  Navigate,
  Outlet,
  useLocation,
} from 'react-router-dom';
import { LoginPage } from './features/auth/LoginPage';
import { DiningRoomPage } from './features/dining-room/DiningRoomPage';
import { CashierPage } from './features/cashier/CashierPage';
import { KitchenPage } from './features/kitchen/KitchenPage';
import { CustomerTablePage } from './features/customer-table/CustomerTablePage';
import { SelfServicePage } from './features/self-service/SelfServicePage';
import { AdminPage } from './pages/AdminPage';
import { currentSession, type Session } from './features/auth/session';
import { destinationsFor, type Destination } from './features/auth/destinations';
import { UserMenu } from './features/auth/UserMenu';
import './styles/shell.css';

/**
 * Shell da área logada: só a área de trabalho, sem barra de cabeçalho. Cada tela leva o menu do
 * usuário (nome e Sair) no próprio cabeçalho; o salão tem o seu, as demais recebem o canto.
 */
export function Shell() {
  const location = useLocation();
  const hasOwnHeader = location.pathname.startsWith('/salao');

  return (
    <div className="shell">
      {!hasOwnHeader && (
        <div className="user-menu-floating">
          <UserMenu />
        </div>
      )}
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

async function requiredAdminLoader(): Promise<Session> {
  const session = currentSession();
  if (!session) {
    throw new Response('Unauthorized', { status: 401 });
  }
  if (!session.user.roles.includes('ADMIN')) {
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
    // The customer at the table, opened by the table's QR: public, outside the shell, no login.
    path: '/mesa/:orderingToken',
    element: <CustomerTablePage />,
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
        path: 'caixa',
        element: <CashierPage />,
        loader: () => requiredDestinationLoader(['FRONT_DESK']),
      },
      {
        path: 'cozinha',
        element: <KitchenPage />,
        loader: () => requiredDestinationLoader(['KITCHEN']),
      },
      {
        path: 'self-service',
        element: <SelfServicePage />,
        loader: () => requiredDestinationLoader(['WAITER']),
      },
      {
        path: 'admin',
        element: <AdminPage />,
        loader: requiredAdminLoader,
      },
      {
        path: '*',
        element: <Navigate to="/salao" replace />,
      },
    ],
  },
]);
