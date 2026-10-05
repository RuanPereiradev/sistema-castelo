import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface DiningTable {
  readonly id: string;
  readonly label: string;
  readonly seats: number;
  readonly area: string;
  readonly isActive: boolean;
  readonly occupiedTabCount: number;
}

/**
 * Polling automático (5s) das mesas. Em 401, o hook autenticado já redireciona.
 */
export function useDiningTables() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['dining-tables'],
    queryFn: async () => {
      const data = await apiCall<readonly DiningTable[]>('/restaurant/dining-tables');
      return data;
    },
    refetchInterval: 5000,
    staleTime: 2000,
  });
}
