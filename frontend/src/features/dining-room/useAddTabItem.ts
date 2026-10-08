import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import type { TabItem } from './useTab';

export interface ModifierChoice {
  readonly modifierId: string;
  readonly quantity: number;
}

/**
 * Body of `POST /api/restaurant/tabs/{tabId}/items`. An item sold by unit sends
 * `quantity`; one sold by weight sends `weightGrams` and nothing else, because
 * the backend refuses variant, modifier and quantity on it.
 */
export interface AddTabItemRequest {
  readonly menuItemId: string;
  readonly variantId?: string;
  readonly quantity?: number;
  readonly weightGrams?: number;
  readonly modifiers?: readonly ModifierChoice[];
  readonly specialInstructions?: string;
}

export function useAddTabItem(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (request: AddTabItemRequest) =>
      apiCall<TabItem>(`/restaurant/tabs/${tabId}/items`, { method: 'POST', body: request }),
  });
}
