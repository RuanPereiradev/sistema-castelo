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

/** Only the fields this round reads; the bill fields belong to the Conta screen. */
export interface PublicTableTab {
  readonly diningTableLabel: string;
  readonly status: 'OPEN' | 'CLOSING';
  readonly items: readonly PublicTabItem[];
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

/** `404 TAB_NOT_FOUND` means nothing was confirmed yet; the screen shows it as empty. */
export function usePublicTableTab(orderingToken: string, customerDeviceId: string) {
  return useQuery({
    queryKey: ['public-table-tab', orderingToken, customerDeviceId],
    queryFn: () =>
      publicRequest<PublicTableTab>(`${tablePath(orderingToken)}/tab`, {
        headers: { 'X-Customer-Device': customerDeviceId },
      }),
    refetchInterval: POLL_INTERVAL_MS,
    refetchIntervalInBackground: false,
    retry: retryOnlyServerFailures,
  });
}
