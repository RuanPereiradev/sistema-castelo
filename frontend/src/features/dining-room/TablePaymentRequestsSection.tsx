import { useState } from 'react';
import { formatMoney } from '../../lib/money';
import { DINING_ROOM, describeConfirmPixError, describeError } from './diningRoomMessages';
import { ConfirmDialog } from './ConfirmDialog';
import {
  useConfirmTablePaymentRequest,
  useRejectTablePaymentRequest,
  type TablePaymentRequest,
} from './useTablePaymentRequests';

const TEXT = DINING_ROOM.orderRequests;

function clockOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

function minutesUntil(isoInstant: string, now: number): number {
  return Math.ceil((Date.parse(isoInstant) - now) / 60_000);
}

/**
 * The Pix customers generated at the table, waiting for a waiter to see the
 * bank receipt (decision #27): confirm puts the money on the tab, reject frees
 * the units. Every value shown is the backend's.
 */
export function TablePaymentRequestsSection({
  requests,
  now,
  onNotice,
}: {
  readonly requests: readonly TablePaymentRequest[];
  readonly now: number;
  readonly onNotice: (message: string) => void;
}) {
  const [rejecting, setRejecting] = useState<TablePaymentRequest | null>(null);
  const reject = useRejectTablePaymentRequest();
  const [rejectError, setRejectError] = useState<{ readonly id: string; readonly message: string } | null>(null);

  return (
    <>
      <div className="dr-requests">
        {requests.map((request) => (
          <PixCard
            key={request.id}
            request={request}
            now={now}
            rejectError={rejectError?.id === request.id ? rejectError.message : null}
            onReject={() => {
              setRejectError(null);
              setRejecting(request);
            }}
            onNotice={onNotice}
          />
        ))}
      </div>

      <ConfirmDialog
        isOpen={rejecting !== null}
        title={TEXT.rejectPixTitle}
        message={rejecting ? TEXT.rejectPixMessage(rejecting.diningTableLabel, rejecting.customerName) : ''}
        confirmLabel={TEXT.rejectPixConfirm}
        cancelLabel={TEXT.keep}
        isDanger
        isLoading={reject.isPending}
        onCancel={() => setRejecting(null)}
        onConfirm={() => {
          if (!rejecting || reject.isPending) {
            return;
          }
          const request = rejecting;
          reject.mutate(request.id, {
            onSuccess: () => onNotice(DINING_ROOM.notice.pixRejected(request.diningTableLabel)),
            onError: (error) => setRejectError({ id: request.id, message: describeError(error) }),
            onSettled: () => setRejecting(null),
          });
        }}
      />
    </>
  );
}

function PixCard({
  request,
  now,
  rejectError,
  onReject,
  onNotice,
}: {
  readonly request: TablePaymentRequest;
  readonly now: number;
  readonly rejectError: string | null;
  readonly onReject: () => void;
  readonly onNotice: (message: string) => void;
}) {
  const confirm = useConfirmTablePaymentRequest();
  const minutesLeft = minutesUntil(request.expiresAt, now);
  const error = confirm.error ? describeConfirmPixError(confirm.error) : rejectError;

  return (
    <article className="dr-request dr-request-pix" aria-label={`Pix · ${DINING_ROOM.table} ${request.diningTableLabel}`}>
      <div className="dr-request-top">
        <span className="dr-request-table">
          {DINING_ROOM.table} {request.diningTableLabel}
        </span>
        <span className={minutesLeft <= 5 ? 'dr-request-expiry is-urgent' : 'dr-request-expiry'}>
          {TEXT.expiresIn(minutesLeft)} · {TEXT.expiresAt(clockOf(request.expiresAt))}
        </span>
      </div>
      <div className="dr-request-who">
        <span className="dr-request-customer">{request.customerName}</span>
        <span className="dr-request-time">{TEXT.requestedAt(clockOf(request.requestedAt))}</span>
      </div>

      <div className="dr-pix-amount">
        <span className="dr-pix-amount-label">{TEXT.pixAmount}</span>
        <span className="dr-pix-amount-value">{formatMoney(request.amount)}</span>
        <span className="dr-request-time">
          {request.includeServiceCharge ? TEXT.withService(formatMoney(request.serviceCharge)) : TEXT.withoutService}
        </span>
      </div>

      <ul className="dr-request-lines">
        {request.items.map((item) => (
          <li key={item.tabItemId} className="dr-request-line">
            <span className="dr-sheet-item-qty">{TEXT.units(item.quantity, item.lineQuantity)}</span>
            <span className="dr-sheet-item-body">
              <span className="dr-sheet-item-name">
                {item.itemName ?? '—'}
                {item.variantName && ` · ${item.variantName}`}
              </span>
            </span>
            <span className="dr-sheet-item-price">{formatMoney(item.lineTotal)}</span>
          </li>
        ))}
      </ul>

      <p className="dr-pix-hint">{TEXT.pixHint}</p>

      {error && (
        <p className="dr-error-line" role="alert">
          {error}
        </p>
      )}

      <div className="dr-request-actions">
        <button type="button" className="dr-action-btn dr-action-danger" onClick={onReject} disabled={confirm.isPending}>
          {TEXT.reject}
        </button>
        <button
          type="button"
          className="dr-action-btn dr-action-primary"
          disabled={confirm.isPending}
          onClick={() => {
            if (confirm.isPending) {
              return;
            }
            confirm.mutate(request.id, {
              onSuccess: () => onNotice(DINING_ROOM.notice.pixConfirmed(request.diningTableLabel)),
            });
          }}
        >
          {confirm.isPending ? TEXT.confirming : TEXT.confirmPix}
        </button>
      </div>
    </article>
  );
}
