import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

/**
 * Hooks pra operações avançadas na comanda (transferir, juntar, trocar mesa).
 */

export function useTransferItems(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (data: { itemIds: string[]; destinationTabId: string }) => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/transfer`, {
        method: 'POST',
        body: {
          itemIds: data.itemIds,
          destinationTabId: data.destinationTabId,
        },
      });
    },
  });
}

export function useMergeTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (destinationTabId: string) => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/merge`, {
        method: 'POST',
        body: { destinationTabId },
      });
    },
  });
}

export function useMoveTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: async (tableId: string) => {
      return await apiCall<void>(`/restaurant/tabs/${tabId}/move`, {
        method: 'POST',
        body: { tableId },
      });
    },
  });
}
