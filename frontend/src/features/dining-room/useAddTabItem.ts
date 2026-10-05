import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface ModifierChoice {
  readonly modifierId: string;
  readonly quantity: number;
}

export interface AddTabItemRequest {
  readonly menuItemId: string;
  readonly variantId?: string;
  readonly modifierChoices: readonly ModifierChoice[];
  readonly specialInstructions?: string;
  readonly quantity: number;
}

export interface TabItem {
  readonly id: string;
  readonly itemName: string;
  readonly price: string;
  readonly quantity: number;
  readonly status: string;
}

/**
 * Adiciona um item à comanda.
 */
export function useAddTabItem(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (request: AddTabItemRequest) => {
      return await apiCall<TabItem>(`/restaurant/tabs/${tabId}/items`, {
        method: 'POST',
        body: request,
      });
    },
  });
}
