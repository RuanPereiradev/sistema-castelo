import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import type { Tab } from './useTab';

/**
 * Hook para buscar a comanda aberta de uma mesa específica.
 * Como não há endpoint direto, faz GET /tabs e filtra pela mesa.
 */
export function useDiningTableTab(tableId: string | null, isOccupied: boolean) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['dining-table-tab', tableId],
    queryFn: async () => {
      if (!tableId || !isOccupied) return null;

      // GET /tabs retorna lista de todas as comandas (sem paginação por enquanto)
      const response = await apiCall<{ readonly tabs: readonly Tab[] }>('/restaurant/tabs');
      // Filtrar pela mesa
      const tab = response.tabs.find((t) => t.diningTableId === tableId);
      return tab ?? null;
    },
    enabled: !!tableId && isOccupied,
    refetchInterval: 5000,
    staleTime: 2000,
  });
}
