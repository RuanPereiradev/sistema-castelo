import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface Charge {
  readonly id: string;
  readonly description: string;
  readonly amount: string;
  readonly type: string;
}

export interface Folio {
  readonly id: string;
  readonly guestName: string;
  readonly subtotal: string;
  readonly balance: string;
  readonly charges: readonly Charge[];
}

/**
 * Busca folios (contas) abertas.
 */
export function useOpenFolios() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['open-folios'],
    queryFn: async () => {
      const data = await apiCall<readonly Folio[]>('/billing/folios/open', {
        method: 'GET',
      });
      return data;
    },
    refetchInterval: 10000, // 10 segundos
    staleTime: 5000,
  });
}
