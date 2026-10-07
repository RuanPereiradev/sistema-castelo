import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface TicketItem {
  readonly id: string;
  readonly itemName: string;
  readonly quantity: number;
  readonly status: string;
  readonly specialInstructions?: string;
}

export interface KitchenTicket {
  readonly id: string;
  readonly tableLabel: string;
  readonly tableNumber: string;
  readonly guestCount: number;
  readonly items: readonly TicketItem[];
  readonly openedAt: string;
}

/**
 * Busca tickets da cozinha (fila de pedidos).
 */
export function useKitchenTickets() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['kitchen-tickets'],
    queryFn: async () => {
      const data = await apiCall<readonly KitchenTicket[]>('/kitchen/queue', {
        method: 'GET',
      });
      return data;
    },
    refetchInterval: 3000, // 3 segundos (mais rápido que o salão)
    staleTime: 1000,
  });
}
