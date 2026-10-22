import { useEffect, useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { ApiError } from '../../lib/problemDetail';
import { publicRequest } from '../../lib/publicHttpClient';

/** Field names follow the `Public*` records and `TableOrderRequestStatusResponse` of the backend. */
export interface PublicTable {
  readonly diningTableLabel: string;
  readonly acceptsOrders: boolean;
  readonly pendingRequestId: string | null;
}

export interface PublicMenuVariant {
  readonly id: string;
  readonly name: string;
  readonly price: string;
  readonly availableNow: boolean;
}

export interface PublicMenuModifier {
  readonly id: string;
  readonly name: string;
  readonly price: string;
  readonly maxQuantity: number;
}

export interface PublicMenuItem {
  readonly id: string;
  readonly name: string;
  readonly description: string | null;
  /** The cheapest active variant when there are variants; the price per kilo when sold by weight. */
  readonly price: string;
  readonly soldByWeight: boolean;
  readonly availableNow: boolean;
  readonly variants: readonly PublicMenuVariant[];
  readonly modifiers: readonly PublicMenuModifier[];
}

export interface PublicMenuCategory {
  readonly name: string;
  readonly items: readonly PublicMenuItem[];
}

export interface PublicMenu {
  readonly categories: readonly PublicMenuCategory[];
}

export type TableOrderRequestStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED';

export interface TableOrderRequestStatusResponse {
  readonly id: string;
  readonly status: TableOrderRequestStatus;
  readonly requestedAt: string;
  readonly expiresAt: string;
}

export interface SubmitTableOrderRequest {
  readonly customerName: string;
  readonly customerDeviceId: string;
  readonly lines: readonly {
    readonly menuItemId: string;
    readonly variantId?: string;
    readonly quantity: number;
    readonly modifiers: readonly { readonly modifierId: string; readonly quantity: number }[];
    readonly specialInstructions?: string;
  }[];
}

export type TabItemStatus = 'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED';

export interface PublicTabItem {
  readonly id: string;
  readonly itemName: string;
  readonly variantName: string | null;
  readonly modifiers: readonly { readonly name: string; readonly quantity: number }[];
  readonly quantity: number;
  readonly weightGrams: number | null;
  readonly lineTotal: string;
  readonly status: TabItemStatus;
  readonly paid: boolean;
  readonly paidQuantity: number;
  readonly reservedQuantity: number;
  readonly customerName: string | null;
  readonly mine: boolean;
}

/** `PublicTableTabResponse`: every money field is the backend's, as a decimal string. */
export interface PublicTableTab {
  readonly diningTableLabel: string;
  readonly status: 'OPEN' | 'CLOSING';
  readonly items: readonly PublicTabItem[];
  readonly subtotal: string;
  readonly unpaidServiceChargeBase: string;
  readonly serviceChargeRate: string;
  readonly serviceCharge: string;
  readonly total: string;
  readonly paidAmount: string;
  readonly amountDue: string;
  readonly acceptsPayment: boolean;
}

export type TablePaymentRequestStatus = 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED';

/** `PublicTablePaymentRequestResponse`. */
export interface PublicTablePaymentRequest {
  readonly id: string;
  readonly status: TablePaymentRequestStatus;
  readonly refunded: boolean;
  readonly subtotal: string;
  readonly serviceCharge: string;
  readonly amount: string;
  readonly includeServiceCharge: boolean;
  readonly pixPayload: string;
  readonly items: readonly {
    readonly tabItemId: string;
    readonly quantity: number;
    readonly lineTotal: string;
    readonly serviceCharge: string;
  }[];
  readonly requestedAt: string;
  readonly expiresAt: string;
}

export interface SubmitTablePaymentRequest {
  readonly customerName: string;
  readonly customerDeviceId: string;
  readonly includeServiceCharge: boolean;
  readonly items: readonly { readonly tabItemId: string; readonly quantity: number }[];
}

/** The customer's phone reads by interval while the screen is visible (decision #7). */
const POLL_INTERVAL_MS = 10_000;

/** A 4xx is an answer, not a hiccup: retrying it only delays the screen. */
function retryOnlyServerFailures(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiError && error.status < 500) {
    return false;
  }
  return failureCount < 2;
}

const tablePath = (orderingToken: string) => `/tables/${encodeURIComponent(orderingToken)}`;

export function usePublicTable(orderingToken: string) {
  return useQuery({
    queryKey: ['public-table', orderingToken],
    queryFn: () => publicRequest<PublicTable>(tablePath(orderingToken)),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: retryOnlyServerFailures,
  });
}

export function usePublicMenu() {
  return useQuery({
    queryKey: ['public-menu'],
    queryFn: () => publicRequest<PublicMenu>('/menu'),
    staleTime: 60_000,
    retry: retryOnlyServerFailures,
  });
}

export function useSubmitOrderRequest(orderingToken: string) {
  return useMutation({
    mutationFn: (request: SubmitTableOrderRequest) =>
      publicRequest<TableOrderRequestStatusResponse>(`${tablePath(orderingToken)}/order-requests`, {
        method: 'POST',
        body: request,
      }),
  });
}

/** Polls only while the request waits for the waiter: a resolved request does not change again. */
export function useOrderRequestStatus(orderingToken: string, requestId: string | null) {
  return useQuery({
    queryKey: ['public-order-request', orderingToken, requestId],
    queryFn: () =>
      publicRequest<TableOrderRequestStatusResponse>(
        `${tablePath(orderingToken)}/order-requests/${encodeURIComponent(requestId ?? '')}`,
      ),
    enabled: requestId !== null,
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? POLL_INTERVAL_MS : false),
    refetchIntervalInBackground: false,
    retry: retryOnlyServerFailures,
  });
}

export const publicTableTabKey = (orderingToken: string, customerDeviceId: string) =>
  ['public-table-tab', orderingToken, customerDeviceId] as const;

/** `404 TAB_NOT_FOUND` means nothing was confirmed yet; the screen shows it as empty. */
export function usePublicTableTab(orderingToken: string, customerDeviceId: string) {
  return useQuery({
    queryKey: publicTableTabKey(orderingToken, customerDeviceId),
    queryFn: () =>
      publicRequest<PublicTableTab>(`${tablePath(orderingToken)}/tab`, {
        headers: { 'X-Customer-Device': customerDeviceId },
      }),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: retryOnlyServerFailures,
  });
}

export const paymentRequestKey = (orderingToken: string, requestId: string | null) =>
  ['public-payment-request', orderingToken, requestId] as const;

export function useSubmitPaymentRequest(orderingToken: string) {
  return useMutation({
    mutationFn: (request: SubmitTablePaymentRequest) =>
      publicRequest<PublicTablePaymentRequest>(`${tablePath(orderingToken)}/payment-requests`, {
        method: 'POST',
        body: request,
      }),
  });
}

/** Polls every 10 s while the waiter has not answered; a resolved request does not change again. */
export function usePaymentRequest(orderingToken: string, requestId: string | null) {
  return useQuery({
    queryKey: paymentRequestKey(orderingToken, requestId),
    queryFn: () =>
      publicRequest<PublicTablePaymentRequest>(
        `${tablePath(orderingToken)}/payment-requests/${encodeURIComponent(requestId ?? '')}`,
      ),
    enabled: requestId !== null,
    refetchInterval: (query) => (query.state.data?.status === 'PENDING' ? POLL_INTERVAL_MS : false),
    refetchIntervalInBackground: false,
    retry: retryOnlyServerFailures,
  });
}

/** What the backend answers for a selection before any Pix exists (decision #30). */
export interface PaymentQuote {
  readonly subtotal: string;
  readonly serviceCharge: string;
  readonly amount: string;
  readonly items: readonly {
    readonly tabItemId: string;
    readonly quantity: number;
    readonly lineTotal: string;
    readonly serviceCharge: string;
  }[];
}

const QUOTE_DEBOUNCE_MS = 350;

/**
 * The value of the units being marked, computed by the backend
 * (`POST /payment-requests/quote`), asked again a moment after the selection
 * stops changing. The screen falls back to the unit count when the route
 * fails or does not exist yet: the browser never adds money.
 */
export function usePaymentQuote(
  orderingToken: string,
  items: readonly { readonly tabItemId: string; readonly quantity: number }[],
  includeServiceCharge: boolean,
) {
  const selectionKey = items
    .map((item) => `${item.tabItemId}x${item.quantity}`)
    .sort()
    .join(',');
  const [settledKey, setSettledKey] = useState(selectionKey);

  useEffect(() => {
    const timer = window.setTimeout(() => setSettledKey(selectionKey), QUOTE_DEBOUNCE_MS);
    return () => window.clearTimeout(timer);
  }, [selectionKey]);

  const settled = settledKey === selectionKey;
  const query = useQuery({
    queryKey: ['public-payment-quote', orderingToken, includeServiceCharge, settledKey],
    queryFn: () =>
      publicRequest<PaymentQuote>(`${tablePath(orderingToken)}/payment-requests/quote`, {
        method: 'POST',
        body: { includeServiceCharge, items },
      }),
    enabled: settled && items.length > 0,
    staleTime: 5_000,
    retry: false,
  });

  return {
    quote: settled && items.length > 0 ? (query.data ?? null) : null,
    isCalculating: items.length > 0 && (!settled || query.isFetching) && !query.isError,
    unavailable: settled && query.isError,
  };
}
