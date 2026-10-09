import { useMutation } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import type { Tab } from './useTab';

/**
 * Transfer, merge and move. Bodies follow `TabTransferRequest`, `TabMergeRequest`
 * and `TabMoveRequest` of the backend.
 */

/** Whole lines of this tab go to `toTabId`. */
export function useTransferItems(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (data: { readonly itemIds: readonly string[]; readonly toTabId: string }) =>
      apiCall<unknown>(`/restaurant/tabs/${tabId}/transfer`, {
        method: 'POST',
        body: { toTabId: data.toTabId, itemIds: data.itemIds },
      }),
  });
}

/**
 * This tab is absorbed into `destinationTabId`, which stays open; this one is
 * left `MERGED`. The backend takes the surviving tab on the path and the absorbed
 * one in the body, so the call goes to the destination.
 */
export function useMergeInto(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (destinationTabId: string) =>
      apiCall<Tab>(`/restaurant/tabs/${destinationTabId}/merge`, {
        method: 'POST',
        body: { mergedTabId: tabId },
      }),
  });
}

/** Answers the new tab, with a new id; the old one is left `MERGED` pointing at it. */
export function useMoveTab(tabId: string) {
  const apiCall = useAuthorizedRequest();

  return useMutation({
    mutationFn: (diningTableId: string) =>
      apiCall<Tab>(`/restaurant/tabs/${tabId}/move`, {
        method: 'POST',
        body: { diningTableId },
      }),
  });
}
