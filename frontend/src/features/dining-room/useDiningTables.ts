import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

/**
 * `GET /api/restaurant/dining-tables`, as the backend answers it: the table's
 * description only. Whether it is occupied comes from the active tabs, not from
 * here — see `useActiveTabs`.
 */
export interface DiningTable {
  readonly id: string;
  readonly label: string;
  readonly seats: number | null;
  readonly area: string | null;
  readonly isActive: boolean;
}

export const DINING_TABLES_KEY = ['dining-tables'] as const;

/** Polls every 5 s: another waiter may have added or deactivated a table. */
export function useDiningTables() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: DINING_TABLES_KEY,
    queryFn: () => apiCall<readonly DiningTable[]>('/restaurant/dining-tables'),
    refetchInterval: 5000,
    staleTime: 2000,
  });
}
