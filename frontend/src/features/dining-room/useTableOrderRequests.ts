import { useEffect, useRef } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { DINING_TABLES_KEY } from './useDiningTables';
import { ACTIVE_TABS_KEY } from './useActiveTabs';

/** Field names follow `TableOrderRequestResponse` of the backend, one to one. */
export interface TableOrderRequestModifier {
  readonly modifierId: string;
  readonly modifierName: string;
  readonly quantity: number;
}

export interface TableOrderRequestLine {
  readonly menuItemId: string;
  readonly menuItemName: string;
  readonly variantId: string | null;
  readonly variantName: string | null;
  readonly quantity: number | null;
  readonly modifiers: readonly TableOrderRequestModifier[];
  readonly specialInstructions: string | null;
}

export interface TableOrderRequest {
  readonly id: string;
  readonly status: 'PENDING' | 'CONFIRMED' | 'REJECTED' | 'EXPIRED';
  readonly diningTableId: string;
  readonly diningTableLabel: string;
  readonly requestedAt: string;
  readonly expiresAt: string;
  readonly resolvedAt: string | null;
  readonly tabId: string | null;
  readonly customerName: string | null;
  readonly lines: readonly TableOrderRequestLine[];
}

export const TABLE_ORDER_REQUESTS_KEY = ['table-order-requests'] as const;

/** Stable, so React Query keeps the same array while the answer does not change. */
function pendingOnly(requests: readonly TableOrderRequest[]): readonly TableOrderRequest[] {
  return requests.filter((request) => request.status === 'PENDING');
}

/**
 * `GET /api/restaurant/table-order-requests`: the requests waiting for a waiter,
 * oldest first. Polled every 5 s like the rest of this room (decision #19); a
 * request the backend already reads as expired is dropped here too.
 */
export function useTableOrderRequests() {
  const apiCall = useAuthorizedRequest();

  return useQuery({
    queryKey: TABLE_ORDER_REQUESTS_KEY,
    queryFn: () => apiCall<readonly TableOrderRequest[]>('/restaurant/table-order-requests'),
    select: pendingOnly,
    refetchInterval: 5000,
    staleTime: 2000,
  });
}

/** Re-reads everything a confirmation or a rejection may have changed. */
function useInvalidateAfterResolution() {
  const queryClient = useQueryClient();
  return () => {
    void queryClient.invalidateQueries({ queryKey: TABLE_ORDER_REQUESTS_KEY });
    void queryClient.invalidateQueries({ queryKey: ACTIVE_TABS_KEY });
    void queryClient.invalidateQueries({ queryKey: DINING_TABLES_KEY });
    void queryClient.invalidateQueries({ queryKey: ['tab'] });
  };
}

export function useConfirmTableOrderRequest() {
  const apiCall = useAuthorizedRequest();
  const invalidate = useInvalidateAfterResolution();

  return useMutation({
    mutationFn: (requestId: string) =>
      apiCall<{ readonly request: TableOrderRequest; readonly tabId: string }>(
        `/restaurant/table-order-requests/${requestId}/confirm`,
        { method: 'POST' },
      ),
    // A refused confirmation leaves the request pending (#21 of qr-table-ordering):
    // the list is re-read either way, so the card shows what the server holds.
    onSettled: invalidate,
  });
}

export function useRejectTableOrderRequest() {
  const apiCall = useAuthorizedRequest();
  const invalidate = useInvalidateAfterResolution();

  return useMutation({
    mutationFn: (requestId: string) =>
      apiCall<TableOrderRequest>(`/restaurant/table-order-requests/${requestId}/reject`, {
        method: 'POST',
      }),
    onSettled: invalidate,
  });
}

/** Pending requests per dining table id. */
export function requestsByTable(
  requests: readonly TableOrderRequest[],
): ReadonlyMap<string, readonly TableOrderRequest[]> {
  const byTable = new Map<string, TableOrderRequest[]>();
  for (const request of requests) {
    const list = byTable.get(request.diningTableId) ?? [];
    list.push(request);
    byTable.set(request.diningTableId, list);
  }
  return byTable;
}

/**
 * The browser keeps audio locked until the page gets a gesture, so the context
 * is created on the first tap and reused. Every failure is silent: no sound is
 * better than a broken room.
 */
let audioContext: AudioContext | null = null;

function unlockAudio() {
  try {
    if (!audioContext) {
      audioContext = new AudioContext();
    }
    if (audioContext.state === 'suspended') {
      void audioContext.resume();
    }
  } catch {
    audioContext = null;
  }
}

function chime() {
  try {
    const context = audioContext;
    if (!context || context.state !== 'running') {
      return;
    }
    const start = context.currentTime;
    for (const [offset, frequency] of [
      [0, 880],
      [0.18, 1175],
    ] as const) {
      const oscillator = context.createOscillator();
      const gain = context.createGain();
      oscillator.type = 'sine';
      oscillator.frequency.value = frequency;
      gain.gain.setValueAtTime(0.0001, start + offset);
      gain.gain.exponentialRampToValueAtTime(0.25, start + offset + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.0001, start + offset + 0.16);
      oscillator.connect(gain).connect(context.destination);
      oscillator.start(start + offset);
      oscillator.stop(start + offset + 0.18);
    }
  } catch {
    // Silent on purpose.
  }
}

/** Plays a short chime when an id shows up that the previous read did not have (orders and Pix alike). */
export function useNewRequestChime(ids: readonly string[] | undefined) {
  const known = useRef<ReadonlySet<string> | null>(null);
  const signature = ids === undefined ? null : [...ids].sort().join(',');

  useEffect(() => {
    window.addEventListener('pointerdown', unlockAudio);
    window.addEventListener('keydown', unlockAudio);
    return () => {
      window.removeEventListener('pointerdown', unlockAudio);
      window.removeEventListener('keydown', unlockAudio);
    };
  }, []);

  useEffect(() => {
    if (signature === null) {
      return;
    }
    const current = new Set(signature === '' ? [] : signature.split(','));
    const previous = known.current;
    known.current = current;
    // The first read is what was already waiting, not something that just arrived.
    if (previous && [...current].some((id) => !previous.has(id))) {
      chime();
    }
  }, [signature]);
}
