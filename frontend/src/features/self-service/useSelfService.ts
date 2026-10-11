import { useMutation, useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import type { MenuItem } from '../dining-room/useMenuItems';

export interface TabItemResponse {
  readonly id: string;
  readonly menuItemName: string;
  readonly status: string;
  readonly price: string;
}

export interface SelfServiceTab {
  readonly id: string;
  readonly cardNumber: number;
  readonly total: string;
  readonly items: readonly TabItemResponse[];
}

/**
 * Hook to open a self-service tab (or find existing one).
 * Returns a full TabResponse with all items and details.
 */
export function useOpenSelfServiceTab() {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (cardNumber: number) =>
      apiCall<SelfServiceTab>('/restaurant/tabs', {
        method: 'POST',
        body: { origin: 'SELF_SERVICE', cardNumber },
      }),
  });
}

/**
 * Hook to find an existing self-service tab by card number.
 * Uses GET /api/restaurant/tabs?cardNumber=X which returns TabSummaryResponse[].
 */
export function useSelfServiceTab(cardNumber: number | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['self-service-tab', cardNumber],
    queryFn: async () => {
      if (!cardNumber || cardNumber < 1 || cardNumber > 999) return null;
      interface TabSummary {
        readonly id: string;
        readonly cardNumber: number;
        readonly total: string;
        readonly status: string;
      }
      const tabs = await apiCall<readonly TabSummary[]>(
        `/restaurant/tabs?cardNumber=${cardNumber}`,
        {
          method: 'GET',
        },
      );
      // For self-service, we expect at most one tab per card number and it should be OPEN
      const tab = tabs.find((t) => t.cardNumber === cardNumber && t.status === 'OPEN');
      return tab
        ? {
            id: tab.id,
            cardNumber: tab.cardNumber,
            total: tab.total,
            items: [],
          }
        : null;
    },
    enabled: cardNumber !== null && cardNumber >= 1 && cardNumber <= 999,
    staleTime: 5000,
  });
}

/**
 * Hook to get items sold by weight (buffet items).
 */
export function useSelfServiceItems() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['self-service-items'],
    queryFn: async () => {
      const response = await apiCall<{
        readonly categories: readonly {
          readonly name: string;
          readonly items: readonly MenuItem[];
        }[];
      }>('/public/menu', { method: 'GET' });

      const items: MenuItem[] = [];
      for (const category of response.categories) {
        for (const item of category.items) {
          if (item.soldByWeight && item.availableNow) {
            items.push(item);
          }
        }
      }
      return items;
    },
    staleTime: 60_000,
  });
}

export interface AddSelfServiceItemRequest {
  readonly menuItemId: string;
  readonly weightGrams: number;
}

/**
 * Hook to add an item to a self-service tab by weight.
 */
export function useAddSelfServiceItem(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (request: AddSelfServiceItemRequest) =>
      apiCall<SelfServiceTab>(`/restaurant/tabs/${tabId}/items`, {
        method: 'POST',
        body: {
          menuItemId: request.menuItemId,
          weightGrams: request.weightGrams,
        },
      }),
  });
}

/**
 * Calculate the price for a weight-sold item.
 * Price per kilo comes as a string ("45.90"), weight in grams (250).
 * Returns the total price as a string.
 */
export function calculateWeightPrice(pricePerKilo: string, grams: number): string {
  // Convert price per kilo to a fraction: 45.90/1000 per gram
  // 250 grams at 45.90/kg = 45.90 * (250/1000) = 45.90 * 0.25
  const [intPart = '0', fracPart = '00'] = pricePerKilo.split('.');
  const fullPrice = intPart + fracPart.padEnd(2, '0'); // e.g., "4590" for 45.90

  // Multiply by grams and divide by 1000000 (1000 grams * 100 cents)
  const totalCents = Math.round((Number(fullPrice) * grams) / 1000);

  // Convert back to decimal string
  const totalReals = Math.floor(totalCents / 100);
  const remainingCents = totalCents % 100;

  return `${totalReals}.${String(remainingCents).padStart(2, '0')}`;
}
