import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

/** Field names follow `TabResponse` and `TabItemResponse` of the backend, one to one. */
export type TabStatus = 'OPEN' | 'CLOSING' | 'CLOSED' | 'CANCELLED' | 'MERGED';
export type TabItemStatus = 'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED' | 'CANCELLED';

export interface TabItemModifier {
  readonly modifierId: string;
  readonly name: string;
  readonly price: string;
  readonly quantity: number;
}

export interface TabItem {
  readonly id: string;
  readonly menuItemId: string;
  readonly itemName: string;
  readonly variantId: string | null;
  readonly variantName: string | null;
  readonly quantity: number;
  readonly weightGrams: number | null;
  readonly unitPrice: string | null;
  readonly pricePerKilo: string | null;
  readonly modifiers: readonly TabItemModifier[];
  readonly lineTotal: string;
  readonly serviceChargeable: boolean;
  readonly specialInstructions: string | null;
  readonly prepStation: 'KITCHEN' | 'PIZZA' | 'BAR';
  readonly status: TabItemStatus;
  readonly orderedAt: string;
  readonly orderedBy: string;
  readonly preparationStartedAt: string | null;
  readonly readyAt: string | null;
  readonly deliveredAt: string | null;
  readonly cancelledAt: string | null;
  readonly cancelledBy: string | null;
  readonly cancellationReason: string | null;
  readonly serviceChargeWaived: boolean;
  readonly splitGroup: number;
  readonly transferredFromTabId: string | null;
}

export interface Tab {
  readonly id: string;
  readonly origin: 'TABLE_SERVICE' | 'SELF_SERVICE';
  readonly status: TabStatus;
  readonly diningTableId: string | null;
  readonly diningTableLabel: string | null;
  readonly cardNumber: number | null;
  readonly openedAt: string;
  readonly openedBy: string;
  readonly subtotal: string;
  readonly cancelledAt: string | null;
  readonly cancellationReason: string | null;
  readonly items: readonly TabItem[];
  readonly serviceChargeApplied: boolean;
  readonly serviceChargeRate: string;
  readonly serviceCharge: string;
  readonly total: string;
  readonly guestCount: number | null;
  readonly folioId: string | null;
  readonly closingStartedAt: string | null;
  readonly closedAt: string | null;
  readonly mergedIntoTabId: string | null;
}

export const tabKey = (tabId: string) => ['tab', tabId] as const;

/** `GET /api/restaurant/tabs/{tabId}`, polled while the sheet is open. */
export function useTab(tabId: string | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: tabKey(tabId ?? ''),
    queryFn: () => apiCall<Tab>(`/restaurant/tabs/${tabId}`),
    enabled: tabId !== null,
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

/** The body of `POST /api/restaurant/tabs`. The guest count is a separate call. */
export interface OpenTabRequest {
  readonly origin: 'TABLE_SERVICE';
  readonly diningTableId: string;
}

export function useOpenTab() {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (request: OpenTabRequest) =>
      apiCall<Tab>('/restaurant/tabs', { method: 'POST', body: request }),
  });
}

/** Active lines only: a cancelled line stays on the tab for the trail, not for the waiter. */
export function activeItems(tab: Tab): readonly TabItem[] {
  return tab.items.filter((item) => item.status !== 'CANCELLED');
}
