import { useQuery } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';

export interface ChargeResponse {
  readonly id: string;
  readonly type: string;
  readonly amount: string;
  readonly description: string;
  readonly reason?: string;
  readonly reversalOf?: string;
  readonly reversedBy?: string;
  readonly createdAt: string;
  readonly createdBy?: string;
}

export interface PaymentResponse {
  readonly id: string;
  readonly method: string;
  readonly amount: string;
  readonly status: string;
  readonly paidAt: string;
  readonly receivedBy?: string;
  readonly refundedAt?: string;
  readonly refundReason?: string;
}

export interface ReferenceResponse {
  readonly code: string;
  readonly label: string;
}

export interface Folio {
  readonly id: string;
  readonly type: string;
  readonly status: string;
  readonly reference?: ReferenceResponse;
  readonly openedAt: string;
  readonly closedAt?: string;
  readonly charges: readonly ChargeResponse[];
  readonly payments: readonly PaymentResponse[];
  readonly totalCharges: string;
  readonly totalPayments: string;
  readonly balance: string;
  // For compatibility with old code
  readonly guestName?: string;
  readonly subtotal?: string;
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

/**
 * Busca um folio específico pelo ID.
 */
export function useFolio(folioId: string | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: ['folio', folioId],
    queryFn: async () => {
      if (!folioId) return null;
      return apiCall<Folio>(`/billing/folios/${folioId}`, {
        method: 'GET',
      });
    },
    enabled: !!folioId,
    staleTime: 5000,
  });
}

/**
 * Recebe um pagamento em um folio.
 * Requer Idempotency-Key para evitar cobrança dupla em case de retry.
 */
export async function receiveFolioPayment(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  folioId: string,
  method: string,
  amount: string,
  idempotencyKey?: string,
): Promise<Folio> {
  const options: any = {
    method: 'POST',
    body: { method, amount },
  };
  if (idempotencyKey) {
    options.headers = { 'Idempotency-Key': idempotencyKey };
  }
  return apiCall<Folio>(`/billing/folios/${folioId}/payments`, options);
}

/**
 * Lança um ajuste em um folio (somente ADMIN).
 */
export async function postFolioAdjustment(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
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

/**
 * Estorna um lançamento em um folio.
 */
export async function reverseFolioCharge(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  folioId: string,
  chargeId: string,
  reason: string,
): Promise<Folio> {
  return apiCall<Folio>(`/billing/folios/${folioId}/charges/${chargeId}/reversal`, {
    method: 'POST',
    body: { reason },
  });
}

/**
 * Estorna um pagamento em um folio (somente ADMIN).
 */
export async function refundFolioPayment(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  folioId: string,
  paymentId: string,
  reason: string,
): Promise<Folio> {
  return apiCall<Folio>(`/billing/folios/${folioId}/payments/${paymentId}/refund`, {
    method: 'POST',
    body: { reason },
  });
}

/**
 * Fecha um folio.
 */
export async function closeFolio(
  apiCall: ReturnType<typeof useAuthorizedRequest>,
  folioId: string,
): Promise<Folio> {
  return apiCall<Folio>(`/billing/folios/${folioId}/close`, {
    method: 'POST',
    body: {},
  });
}
