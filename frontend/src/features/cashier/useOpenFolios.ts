import { useQuery, type QueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { toDecimalString } from './useCashSession';

type ApiCall = ReturnType<typeof useAuthorizedRequest>;

export type FolioType = 'STAY' | 'TAB';
export type FolioStatus = 'OPEN' | 'CLOSED';
export type ChargeType = 'ROOM_NIGHT' | 'TAB' | 'ADJUSTMENT';
export type PaymentMethod = 'CASH' | 'PIX' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'ROOM_ACCOUNT';
export type PaymentStatus = 'PENDING' | 'CONFIRMED' | 'FAILED' | 'REFUNDED';

/** `FolioResponse.ChargeResponse`. A reversal is a line of its own, negative, naming `reversalOf`. */
export interface ChargeResponse {
  readonly id: string;
  readonly type: ChargeType;
  readonly amount: string;
  readonly description: string;
  readonly reason: string | null;
  readonly reversalOf: string | null;
  readonly reversedBy: string | null;
  readonly createdAt: string;
  readonly createdBy: string | null;
}

/** `FolioResponse.PaymentResponse`. */
export interface PaymentResponse {
  readonly id: string;
  readonly method: PaymentMethod;
  readonly amount: string;
  readonly status: PaymentStatus;
  readonly paidAt: string;
  readonly receivedBy: string | null;
  readonly refundedAt: string | null;
  readonly refundReason: string | null;
}

export interface ReferenceResponse {
  readonly code: string;
  readonly label: string;
}

/** `FolioResponse`: the three totals come calculated; the screen never adds them up. */
export interface Folio {
  readonly id: string;
  readonly type: FolioType;
  readonly status: FolioStatus;
  readonly reference: ReferenceResponse | null;
  readonly openedAt: string;
  readonly closedAt: string | null;
  readonly charges: readonly ChargeResponse[];
  readonly payments: readonly PaymentResponse[];
  readonly totalCharges: string;
  readonly totalPayments: string;
  readonly balance: string;
}

/** `ReceivedPaymentResponse`: the payment and the balance after it. */
export interface ReceivedPayment {
  readonly id: string;
  readonly method: PaymentMethod;
  readonly amount: string;
  readonly status: PaymentStatus;
  readonly paidAt: string;
  readonly balance: string;
}

export const OPEN_FOLIOS_KEY = ['open-folios'] as const;
export const folioKey = (folioId: string) => ['folio', folioId] as const;

/**
 * `GET /api/billing/folios?status=OPEN` (PR #37), the oldest first. The cashier is the restaurant's
 * only (decision #22): the route also answers stay folios, which this screen leaves out.
 */
export function useOpenFolios() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: OPEN_FOLIOS_KEY,
    queryFn: () => apiCall<readonly Folio[]>('/billing/folios?status=OPEN', { method: 'GET' }),
    select: (folios: readonly Folio[]) => folios.filter((folio) => folio.type === 'TAB'),
    refetchInterval: 10000,
    staleTime: 5000,
  });
}

/**
 * One folio by id. The detail reads here, not from the list, so a folio closed a moment ago keeps
 * answering, now `CLOSED`, instead of vanishing from under the operator.
 */
export function useFolio(folioId: string | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: folioKey(folioId ?? ''),
    queryFn: () => apiCall<Folio>(`/billing/folios/${folioId}`, { method: 'GET' }),
    enabled: folioId !== null,
    refetchInterval: 10000,
    staleTime: 5000,
  });
}

/** After any write, the server says what the folio became: never the answer applied by hand. */
export function rereadFolio(queryClient: QueryClient, folioId: string): Promise<unknown> {
  return Promise.all([
    queryClient.invalidateQueries({ queryKey: folioKey(folioId) }),
    queryClient.invalidateQueries({ queryKey: OPEN_FOLIOS_KEY }),
  ]);
}

/** The key belongs to the operator's attempt: a retry sends the same one and is not charged twice. */
export function receiveFolioPayment(
  apiCall: ApiCall,
  folioId: string,
  method: PaymentMethod,
  amount: string,
  idempotencyKey: string,
): Promise<ReceivedPayment> {
  return apiCall<ReceivedPayment>(`/billing/folios/${folioId}/payments`, {
    method: 'POST',
    body: { method, amount: toDecimalString(amount) },
    headers: { 'Idempotency-Key': idempotencyKey },
  });
}

/** `ADMIN` only. A negative amount is a discount. The route takes no idempotency key (gap L2). */
export function postFolioAdjustment(
  apiCall: ApiCall,
  folioId: string,
  amount: string,
  description: string,
  reason: string,
): Promise<Folio> {
  return apiCall<Folio>(`/billing/folios/${folioId}/adjustments`, {
    method: 'POST',
    body: { amount, description, reason },
  });
}

/** `ADMIN` only. */
export function refundFolioPayment(
  apiCall: ApiCall,
  folioId: string,
  paymentId: string,
  reason: string,
): Promise<Folio> {
  return apiCall<Folio>(`/billing/folios/${folioId}/payments/${paymentId}/refund`, {
    method: 'POST',
    body: { reason },
  });
}
