import { currentSession } from './session';
import type { Role } from './roles';

/**
 * Hook para acessar o usuário e perfil atuais.
 */
export function useCurrentUser() {
  const session = currentSession();
  const user = session?.user;

  return {
    user,
    isAdmin: user?.roles.includes('ADMIN') ?? false,
    isFrontDesk: user?.roles.includes('FRONT_DESK') ?? false,
    isWaiter: user?.roles.includes('WAITER') ?? false,
    isKitchen: user?.roles.includes('KITCHEN') ?? false,
    hasRole: (role: Role) => user?.roles.includes(role) ?? false,
  };
}
