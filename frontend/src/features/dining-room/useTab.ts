import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface TabItemModifier {
  readonly id: string;
  readonly quantity: number;
  readonly modifierId: string;
  readonly modifierName: string;
  readonly price: string;
}

export interface TabItem {
  readonly id: string;
  readonly menuItemId: string;
  readonly itemName: string;
  readonly quantity: number;
  readonly price: string;
  readonly variantId?: string;
  readonly variantName?: string;
  readonly modifierChoices: readonly TabItemModifier[];
  readonly status: 'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED' | 'CANCELLED';
  readonly createdAt: string;
}

export interface Tab {
  readonly id: string;
  readonly diningTableId: string;
  readonly diningTableLabel: string;
  readonly status: 'OPEN' | 'CLOSING' | 'CLOSED' | 'CANCELLED' | 'MERGED';
  readonly origin: 'TABLE_SERVICE' | 'SELF_SERVICE';
  readonly guestCount: number;
  readonly items: readonly TabItem[];
  readonly total: string;
  readonly createdAt: string;
}

export function useTab(tabId: string | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['tab', tabId],
    queryFn: async () => {
      if (!tabId) return null;
      return await apiCall<Tab>(`/restaurant/tabs/${tabId}`);
    },
    enabled: !!tabId,
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

export interface OpenTabRequest {
  readonly diningTableId: string;
  readonly origin: 'TABLE_SERVICE';
  readonly guestCount: number;
}

export function useOpenTab() {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (request: OpenTabRequest) => {
      return await apiCall<Tab>('/restaurant/tabs', {
        method: 'POST',
        body: request,
      });
    },
  });
}
