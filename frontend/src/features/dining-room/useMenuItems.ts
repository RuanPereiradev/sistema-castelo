import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

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
  readonly description?: string;
  readonly price?: string;
  readonly pricePerKilo?: string;
  readonly soldByWeight: boolean;
  readonly availableNow: boolean;
  readonly variants: readonly MenuItemVariant[];
  readonly modifiers: readonly MenuItemModifier[];
}

export interface MenuCategory {
  readonly name: string;
  readonly items: readonly MenuItem[];
}

export interface MenuResponse {
  readonly categories: readonly MenuCategory[];
}

/**
 * Busca cardápio completo com categorias, itens, variações e adicionais.
 */
export function useMenuItems() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['menu-items'],
    queryFn: async () => {
      const data = await apiCall<MenuResponse>('/restaurant/menu-items');
      return data.categories;
    },
    staleTime: 60000, // 1 minuto — cardápio muda pouco
  });
}
