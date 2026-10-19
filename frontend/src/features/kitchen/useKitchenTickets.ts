import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export type Station = 'KITCHEN' | 'PIZZA' | 'BAR';

export type KitchenItemStatus = 'PENDING' | 'IN_PREPARATION' | 'READY';

/** One item on the queue — `KitchenTicketResponse` of the API. */
export interface KitchenTicket {
  readonly itemId: string;
  readonly tabId: string;
  readonly origin: 'TABLE_SERVICE' | 'SELF_SERVICE';
  readonly diningTableLabel: string | null;
  readonly cardNumber: number | null;
  readonly itemName: string;
  readonly variantName: string | null;
  readonly quantity: number;
  readonly modifiers: readonly { readonly name: string; readonly quantity: number }[];
  readonly specialInstructions: string | null;
  readonly prepStation: Station;
  readonly status: KitchenItemStatus;
  readonly orderedAt: string;
  readonly preparationStartedAt: string | null;
  readonly readyAt: string | null;
  readonly updatedAt: string;
}

export interface KitchenQueueResponse {
  readonly station: Station;
  readonly serverTime: string;
  readonly warningAfterMinutes: number;
  readonly lateAfterMinutes: number;
  readonly items: readonly KitchenTicket[];
}

/**
 * Fila de um setor, com os limites de espera e o `serverTime` da leitura.
 * Sem polling: o useKitchenSocket recarrega a fila a cada mensagem e a cada reconexão.
 */
export function useKitchenTickets(station: Station) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['kitchen-tickets', station],
    queryFn: () =>
      apiCall<KitchenQueueResponse>(`/kitchen/queue?station=${station}`, { method: 'GET' }),
    staleTime: 1000,
  });
}
