import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
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
