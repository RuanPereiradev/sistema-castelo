import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { ApiError } from '../../lib/problemDetail';
import { DINING_TABLES_KEY } from './useDiningTables';
import { ACTIVE_TABS_KEY } from './useActiveTabs';

/** Field names follow `TablePaymentRequestResponse` of the backend, one to one. */
export interface TablePaymentRequestItem {
  readonly tabItemId: string;
  readonly itemName: string | null;
  readonly variantName: string | null;
  readonly quantity: number;
  readonly lineQuantity: number;
  readonly lineTotal: string;
  readonly serviceCharge: string;
}

export interface TablePaymentRequest {
  readonly id: string;
  readonly status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED';
  readonly refunded: boolean;
  readonly diningTableId: string;
  readonly diningTableLabel: string;
  readonly tabId: string;
  readonly subtotal: string;
  readonly serviceChargeRate: string;
  readonly serviceCharge: string;
  readonly amount: string;
  readonly requestedAt: string;
  readonly expiresAt: string;
  readonly resolvedAt: string | null;
  readonly resolvedBy: string | null;
  readonly refundedAt: string | null;
  readonly refundReason: string | null;
  readonly customerName: string;
  readonly includeServiceCharge: boolean;
  readonly items: readonly TablePaymentRequestItem[];
}

export const TABLE_PAYMENT_REQUESTS_KEY = ['table-payment-requests'] as const;

function pendingOnly(requests: readonly TablePaymentRequest[]): readonly TablePaymentRequest[] {
  return requests.filter((request) => request.status === 'PENDING');
}

/** `GET /api/restaurant/table-payment-requests`: the Pix waiting for a waiter, polled every 5 s. */
export function useTablePaymentRequests() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: TABLE_PAYMENT_REQUESTS_KEY,
    queryFn: () => apiCall<readonly TablePaymentRequest[]>('/restaurant/table-payment-requests'),
    select: pendingOnly,
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

function useInvalidateAfterPayment() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: TABLE_PAYMENT_REQUESTS_KEY });
    void queryClient.invalidateQueries({ queryKey: ACTIVE_TABS_KEY });
    void queryClient.invalidateQueries({ queryKey: DINING_TABLES_KEY });
    void queryClient.invalidateQueries({ queryKey: ['tab'] });
    // The cashier's folio list carries the same balance.
    void queryClient.invalidateQueries({ queryKey: ['open-folios'] });
  };
}

function usePaymentAction(action: 'confirm' | 'reject') {
  const apiCall = useAuthorizedRequest();
  const invalidate = useInvalidateAfterPayment();

  return useMutation({
    mutationFn: (requestId: string) =>
      apiCall<TablePaymentRequest>(`/restaurant/table-payment-requests/${requestId}/${action}`, { method: 'POST' }),
    onSettled: invalidate,
  });
}

export function useConfirmTablePaymentRequest() {
  return usePaymentAction('confirm');
}

export function useRejectTablePaymentRequest() {
  return usePaymentAction('reject');
}

/**
 * The confirmed Pix of one tab, refunded ones included (decision #38):
 * `GET /api/restaurant/table-payment-requests?status=CONFIRMED&tabId=`.
 */
export function useConfirmedTablePayments(tabId: string | null) {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: [...TABLE_PAYMENT_REQUESTS_KEY, 'confirmed', tabId ?? ''],
    queryFn: () =>
      apiCall<readonly TablePaymentRequest[]>(
        `/restaurant/table-payment-requests?status=CONFIRMED&tabId=${encodeURIComponent(tabId ?? '')}`,
      ),
    enabled: tabId !== null,
    refetchInterval: 5000,
    staleTime: 2000,
    retry: false,
  });
}

/** An older backend without the confirmed list: the screen shows "indisponível", not a failure. */
export function isRouteNotAvailable(error: unknown): boolean {
  return error instanceof ApiError && (error.status === 404 || error.status === 405) && error.code !== 'TAB_NOT_FOUND';
}

export function useRefundTablePaymentRequest() {
  const apiCall = useAuthorizedRequest();
  const invalidate = useInvalidateAfterPayment();

  return useMutation({
    mutationFn: ({ requestId, reason }: { readonly requestId: string; readonly reason: string }) =>
      apiCall<TablePaymentRequest>(`/restaurant/table-payment-requests/${requestId}/refund`, {
        method: 'POST',
        body: { reason },
      }),
    onSettled: invalidate,
  });
}
