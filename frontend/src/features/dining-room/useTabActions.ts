import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

/**
 * Hooks pra ações na comanda (cancelar item/comanda, fechar, etc).
 */
export function useCancelTabItem(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (data: { itemId: string; reason: string }) => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/items/${data.itemId}/cancel`, {
        method: 'POST',
        body: { reason: data.reason },
      });
    },
  });
}

export function useCancelTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (reason: string) => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/cancel`, {
        method: 'POST',
        body: { reason },
      });
    },
  });
}

export interface TabBill {
  readonly tabId: string;
  readonly total: string;
  readonly serviceCharge: string;
  readonly balance: string;
  readonly items: readonly {
    readonly id: string;
    readonly name: string;
    readonly price: string;
    readonly quantity: number;
    readonly total: string;
  }[];
}

export function useGetTabBill(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async () => {
      return await apiCall<TabBill>(`/restaurant/tabs/${tabId}/bill`, {
        method: 'GET',
      });
    },
  });
}

export function useCloseTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async () => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/close`, {
        method: 'POST',
      });
    },
  });
}
