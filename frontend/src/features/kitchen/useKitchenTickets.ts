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

export interface KitchenQueueResponse {
  readonly items: readonly KitchenTicket[];
  readonly warningAfterMinutes: number;
  readonly lateAfterMinutes: number;
  readonly serverTime: string;
}

/**
 * Busca tickets da cozinha (fila de pedidos) com limites de tempo.
 * A API retorna a fila com os limites de warning/late e o serverTime.
 */
export function useKitchenTickets(station: 'KITCHEN' | 'PIZZA' | 'BAR' = 'KITCHEN') {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['kitchen-tickets', station],
    queryFn: async () => {
      const data = await apiCall<KitchenQueueResponse>(`/kitchen/queue?station=${station}`, {
        method: 'GET',
      });
      return data;
    },
    // Sem polling: o useKitchenSocket recarrega a fila a cada mensagem e a cada reconexão.
    staleTime: 1000,
  });
}
