import { useQuery } from '@tanstack/react-query';
import { ApiError, NetworkError } from '../../lib/problemDetail';

/**
 * The menu the waiter orders from is the public one, `GET /public/menu`: active
 * categories and items with their availability right now, no token needed.
 * `GET /api/restaurant/menu-items` is the administration list and refuses the
 * waiter's profile.
 */
export interface MenuItemVariant {
  readonly id: string;
  readonly name: string;
  readonly price: string;
  readonly availableNow: boolean;
}

export interface MenuItemModifier {
  readonly id: string;
  readonly name: string;
  readonly price: string;
  readonly maxQuantity: number;
}

export interface MenuItem {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  /** Unit price, or the price per kilo when `soldByWeight`; null when only variants carry a price. */
  readonly price: string | null;
  readonly soldByWeight: boolean;
  readonly availableNow: boolean;
  readonly variants: readonly MenuItemVariant[];
  readonly modifiers: readonly MenuItemModifier[];
}

export interface MenuCategory {
  readonly name: string;
  readonly items: readonly MenuItem[];
}

interface PublicMenuResponse {
  readonly categories: readonly MenuCategory[];
}

async function fetchPublicMenu(): Promise<readonly MenuCategory[]> {
  let response: Response;
  try {
    response = await fetch('/public/menu', { headers: { Accept: 'application/json' } });
  } catch (cause) {
    throw new NetworkError(cause);
  }
  if (!response.ok) {
    throw new ApiError(response.status, null);
  }
  const body = (await response.json()) as PublicMenuResponse;
  return body.categories;
}

export function useMenuItems() {
  return useQuery({
    queryKey: ['public-menu'],
    queryFn: fetchPublicMenu,
    staleTime: 60_000,
  });
}
