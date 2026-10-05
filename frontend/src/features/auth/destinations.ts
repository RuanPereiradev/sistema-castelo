import type { Role } from './roles';

/**
 * Which area a profile opens into. This map belongs to the front: the API only
 * hands over `roles`, and says nothing about screens.
 *
 * One reachable area means going straight in — the kitchen user lands in the
 * kitchen. `ADMIN`, or two profiles or more, means asking.
 */
export type Destination = Extract<Role, 'WAITER' | 'FRONT_DESK' | 'KITCHEN'>;

export interface DestinationDescription {
  readonly title: string;
  readonly description: string;
}

export const DESTINATIONS: Readonly<Record<Destination, DestinationDescription>> = {
  WAITER: { title: 'Salão e comanda', description: 'Mesas, pedidos e comandas' },
  FRONT_DESK: { title: 'Caixa e conta', description: 'Fechamento e pagamento' },
  KITCHEN: { title: 'Cozinha', description: 'Monitor de pedidos em preparo' },
};

/** Declaration order is the order the choices appear on screen. */
const ALL: readonly Destination[] = ['WAITER', 'FRONT_DESK', 'KITCHEN'];

export function destinationsFor(roles: readonly Role[]): readonly Destination[] {
  if (roles.includes('ADMIN')) {
    return ALL;
  }
  return ALL.filter((destination) => roles.includes(destination));
}
