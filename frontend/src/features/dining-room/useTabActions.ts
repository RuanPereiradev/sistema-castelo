import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import type { Tab } from './useTab';

/**
 * The actions of one tab. Each one answers what the backend answers; the caller
 * re-reads the tab and the active list afterwards instead of patching the cache.
 */

export function useCancelTabItem(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (data: { readonly itemId: string; readonly reason: string }) =>
      apiCall<void>(`/restaurant/tabs/${tabId}/items/${data.itemId}/cancel`, {
        method: 'POST',
        body: { reason: data.reason },
      }),
  });
}

export function useCancelTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (reason: string) =>
      apiCall<Tab>(`/restaurant/tabs/${tabId}/cancel`, { method: 'POST', body: { reason } }),
  });
}

/** `TabBillResponse`: everything already computed, the front only displays. */
export interface TabBill {
  readonly tabId: string;
  readonly status: string;
  readonly guestCount: number | null;
  readonly serviceChargeApplied: boolean;
  readonly subtotal: string;
  readonly serviceChargeBase: string;
  readonly serviceChargeRate: string;
  readonly serviceCharge: string;
  readonly total: string;
  readonly paid: string;
  readonly balance: string;
  readonly evenSplitParts: number | null;
  readonly evenSplit: readonly string[];
  readonly balanceEvenSplit: readonly string[];
}

/** 1 to 99; zero or absent answers `INVALID_GUEST_COUNT`. */
export function useRecordGuestCount(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (guestCount: number) =>
      apiCall<TabBill>(`/restaurant/tabs/${tabId}/guest-count`, {
        method: 'PUT',
        body: { guestCount },
      }),
  });
}

/** "Pedir a conta": freezes the service charge and hands the tab to the cashier. */
export function useStartClosing(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: () => apiCall<TabBill>(`/restaurant/tabs/${tabId}/closing`, { method: 'POST' }),
  });
}

/** Takes a `CLOSING` tab back to `OPEN`; the reason stays on the trail. */
export function useReopenTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (reason: string) =>
      apiCall<Tab>(`/restaurant/tabs/${tabId}/reopen`, { method: 'POST', body: { reason } }),
  });
}
