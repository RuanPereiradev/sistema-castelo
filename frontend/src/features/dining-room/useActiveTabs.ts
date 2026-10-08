import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

/**
 * One row of `GET /api/restaurant/tabs`: only `OPEN` and `CLOSING` tabs come,
 * without items. A table with a row here is occupied; `CLOSING` means the bill
 * was asked and the tab is with the cashier.
 */
export interface TabSummary {
  readonly id: string;
  readonly origin: 'TABLE_SERVICE' | 'SELF_SERVICE';
  readonly status: 'OPEN' | 'CLOSING';
  readonly diningTableId: string | null;
  readonly diningTableLabel: string | null;
  readonly cardNumber: number | null;
  readonly openedAt: string;
  readonly subtotal: string;
  readonly activeItemCount: number;
  readonly total: string;
}

export const ACTIVE_TABS_KEY = ['active-tabs'] as const;

/** Polls every 5 s together with the tables, so the map follows the room. */
export function useActiveTabs() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ACTIVE_TABS_KEY,
    queryFn: () => apiCall<readonly TabSummary[]>('/restaurant/tabs'),
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

/** The active tab of each table. A table has at most one: the backend refuses a second. */
export function tabsByTable(tabs: readonly TabSummary[]): ReadonlyMap<string, TabSummary> {
  const byTable = new Map<string, TabSummary>();
  for (const tab of tabs) {
    if (tab.diningTableId) {
      byTable.set(tab.diningTableId, tab);
    }
  }
  return byTable;
}
