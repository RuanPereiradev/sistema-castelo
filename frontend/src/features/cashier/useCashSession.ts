import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { ApiError } from '../../lib/problemDetail';

type ApiCall = ReturnType<typeof useAuthorizedRequest>;

export type CashMovementType = 'CASH_DROP' | 'CASH_SUPPLY' | 'EXPENSE_PAYMENT';

export interface CashMovement {
  readonly id: string;
  readonly type: CashMovementType;
  readonly amount: string;
  readonly reason: string;
  readonly createdAt: string;
  readonly createdBy: string | null;
}

/** `CashDrawerSessionResponse`. With the session open, the expected amount reaches only an ADMIN. */
export interface CashDrawerSession {
  readonly id: string;
  readonly status: 'OPEN' | 'CLOSED';
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

export const CURRENT_SESSION_KEY = ['cash-session-current'] as const;

/**
 * O turno aberto da propriedade, ou `null` quando não há nenhum: o `/current` responde 404
 * `CASH_DRAWER_SESSION_NOT_FOUND`, que aqui é estado normal da recepção, não falha.
 */
export function useCashSession() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: CURRENT_SESSION_KEY,
    queryFn: async (): Promise<CashDrawerSession | null> => {
      try {
        return await apiCall<CashDrawerSession>('/billing/cash-sessions/current', { method: 'GET' });
      } catch (error) {
        if (error instanceof ApiError && error.code === 'CASH_DRAWER_SESSION_NOT_FOUND') {
          return null;
        }
        throw error;
      }
    },
    refetchInterval: 10000,
    staleTime: 5000,
    retry: 1,
  });
}

/** Typed with a comma, sent with a dot: a character swap, never a numeric conversion. */
export function toDecimalString(typed: string): string {
  return typed.trim().replace(',', '.');
}

export function openCashSession(apiCall: ApiCall, openingFloat: string): Promise<CashDrawerSession> {
  return apiCall<CashDrawerSession>('/billing/cash-sessions', {
    method: 'POST',
    body: { openingFloat: toDecimalString(openingFloat) },
  });
}

/** Sangria ou suprimento. A chave é da tentativa do operador: o retry reusa a mesma. */
export function registerCashMovement(
  apiCall: ApiCall,
  sessionId: string,
  kind: 'drops' | 'supplies',
  amount: string,
  reason: string,
  idempotencyKey: string,
): Promise<CashDrawerSession> {
  return apiCall<CashDrawerSession>(`/billing/cash-sessions/${sessionId}/${kind}`, {
    method: 'POST',
    body: { amount: toDecimalString(amount), reason },
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

export function closeCashSession(
  apiCall: ApiCall,
  sessionId: string,
  countedAmount: string,
  note: string,
): Promise<CashDrawerSession> {
  return apiCall<CashDrawerSession>(`/billing/cash-sessions/${sessionId}/close`, {
    method: 'POST',
    body: { countedAmount: toDecimalString(countedAmount), note: note.trim() || null },
  });
}
