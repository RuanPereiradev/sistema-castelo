import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface CashMovement {
  readonly id: string;
  readonly type: string;
  readonly amount: string;
  readonly reason: string;
  readonly createdAt: string;
  readonly createdBy: string;
}

export interface CashDrawerSession {
  readonly id: string;
  readonly status: string;
  readonly openedAt: string;
  readonly openedBy: string;
  readonly openingFloat: string;
  readonly movements: readonly CashMovement[];
  readonly totalDrops: string;
  readonly totalSupplies: string;
  readonly cashPaymentsTotal: string | null;
  readonly cashPaymentCount: number;
  readonly expectedAmount: string | null;
  readonly countedAmount: string | null;
  readonly difference: string | null;
  readonly closedAt: string | null;
  readonly closedBy: string | null;
  readonly closingNote: string | null;
}

/**
 * Busca o turno de caixa atual (OPEN ou o último CLOSED).
 */
export function useCashSession() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['cash-session-current'],
    queryFn: async () => {
      const data = await apiCall<CashDrawerSession>('/billing/cash-sessions/current', {
        method: 'GET',
      });
      return data;
    },
    refetchInterval: 10000, // 10 segundos
    staleTime: 5000,
    retry: 1,
  });
}

/**
 * Abre um novo turno de caixa.
 */
export async function openCashSession(apiCall: ReturnType<typeof useAuthorizedRequest>, openingFloat: string): Promise<CashDrawerSession> {
  return apiCall<CashDrawerSession>('/billing/cash-sessions', {
    method: 'POST',
    body: JSON.stringify({ openingFloat }),
    headers: { 'Content-Type': 'application/json' },
  });
}

/**
 * Registra uma sangria no turno de caixa.
 */
export async function dropCash(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  sessionId: string,
  amount: string,
  reason: string,
): Promise<CashDrawerSession> {
  const idempotencyKey = crypto.randomUUID();
  return apiCall<CashDrawerSession>(`/billing/cash-sessions/${sessionId}/drops`, {
    method: 'POST',
    body: JSON.stringify({ amount, reason }),
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  });
}

/**
 * Registra um suprimento no turno de caixa.
 */
export async function supplyCash(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  sessionId: string,
  amount: string,
  reason: string,
): Promise<CashDrawerSession> {
  const idempotencyKey = crypto.randomUUID();
  return apiCall<CashDrawerSession>(`/billing/cash-sessions/${sessionId}/supplies`, {
    method: 'POST',
    body: JSON.stringify({ amount, reason }),
    headers: {
      'Content-Type': 'application/json',
      'Idempotency-Key': idempotencyKey,
    },
  });
}

/**
 * Fecha o turno de caixa após a contagem.
 */
export async function closeCashSession(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  sessionId: string,
  countedAmount: string,
  note?: string,
): Promise<CashDrawerSession> {
  return apiCall<CashDrawerSession>(`/billing/cash-sessions/${sessionId}/close`, {
    method: 'POST',
    body: JSON.stringify({ countedAmount, note: note || null }),
    headers: { 'Content-Type': 'application/json' },
  });
}
