import { useState } from 'react';
import { DINING_ROOM, describeConfirmRequestError, describeError } from './diningRoomMessages';
import { ConfirmDialog } from './ConfirmDialog';
import {
  useConfirmTableOrderRequest,
  useRejectTableOrderRequest,
  useTableOrderRequests,
  type TableOrderRequest,
  type TableOrderRequestLine,
} from './useTableOrderRequests';
import { useTablePaymentRequests } from './useTablePaymentRequests';
import { TablePaymentRequestsSection } from './TablePaymentRequestsSection';

const TEXT = DINING_ROOM.orderRequests;

function clockOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** Whole minutes until the server's `expiresAt`, by the device clock, as the map's "open for". */
function minutesUntil(isoInstant: string, now: number): number {
  return Math.ceil((Date.parse(isoInstant) - now) / 60_000);
}

function CloseIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" aria-hidden="true">
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </svg>
  );
}

/**
 * The requests customers sent from the table's QR, waiting for a waiter: read,
 * then confirm (the lines go on the table's tab) or reject. Opened from the map,
 * for every table, or from one table's sheet, for that table only.
 */
export function TableOrderRequestsSheet({
  diningTableId,
  now,
  onShowAll,
  onClose,
  onNotice,
}: {
  /** Only this table's requests, when opened from its sheet. */
  readonly diningTableId: string | null;
  readonly now: number;
  readonly onShowAll: () => void;
  readonly onClose: () => void;
  readonly onNotice: (message: string) => void;
}) {
  const requestsQuery = useTableOrderRequests();
  const paymentsQuery = useTablePaymentRequests();
  const [rejecting, setRejecting] = useState<TableOrderRequest | null>(null);
  const reject = useRejectTableOrderRequest();
  const [rejectError, setRejectError] = useState<{ readonly id: string; readonly message: string } | null>(null);

  const ofThisView = <T extends { readonly diningTableId: string }>(list: readonly T[] | undefined) =>
    (list ?? []).filter((request) => diningTableId === null || request.diningTableId === diningTableId);
  const requests = ofThisView(requestsQuery.data);
  const payments = ofThisView(paymentsQuery.data);
  const failure = (requestsQuery.error && !requestsQuery.data) || (paymentsQuery.error && !paymentsQuery.data);

  let body;
  if (requestsQuery.isPending || paymentsQuery.isPending) {
    body = <p className="dr-state" role="status">{TEXT.loading}</p>;
  } else if (failure) {
    body = (
      <div className="dr-state" role="alert">
        <p>{TEXT.failed}</p>
        <p className="dr-state-detail">{describeError(requestsQuery.error ?? paymentsQuery.error)}</p>
        <button
          type="button"
          className="dr-action-btn dr-action-secondary"
          onClick={() => {
            void requestsQuery.refetch();
            void paymentsQuery.refetch();
          }}
        >
          {DINING_ROOM.retry}
        </button>
      </div>
    );
  } else if (requests.length === 0 && payments.length === 0) {
    body = <p className="dr-empty">{diningTableId === null ? TEXT.empty : TEXT.emptyForTable}</p>;
  } else {
    body = (
      <>
        {payments.length > 0 && (
          <>
            <h3 className="dr-requests-section">❧ {TEXT.pixSection}</h3>
            <TablePaymentRequestsSection requests={payments} now={now} onNotice={onNotice} />
          </>
        )}
        {requests.length > 0 && payments.length > 0 && <h3 className="dr-requests-section">❧ {TEXT.ordersSection}</h3>}
      <div className="dr-requests">
        {requests.map((request) => (
          <RequestCard
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
      </>
    );
  }

  return (
    <>
      <div className="dr-modal-backdrop" onClick={onClose} />
      <div className="dr-sheet dr-sheet-tall" role="dialog" aria-modal="true" aria-labelledby="dr-requests-title">
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <div className="dr-sheet-subtitle">{TEXT.subtitle}</div>
            <h2 id="dr-requests-title" className="dr-sheet-title">
              {TEXT.title}
            </h2>
            <button type="button" className="dr-sheet-close" onClick={onClose} aria-label={DINING_ROOM.close}>
              <CloseIcon />
            </button>
          </div>

          {body}

          <div className="dr-sheet-actions">
            {diningTableId !== null && (
              <button type="button" className="dr-action-btn dr-action-secondary" onClick={onShowAll}>
                {TEXT.showAll}
              </button>
            )}
            <button type="button" className="dr-action-btn dr-action-ghost" onClick={onClose}>
              {DINING_ROOM.close}
            </button>
          </div>
        </div>
      </div>

      <ConfirmDialog
        isOpen={rejecting !== null}
        title={TEXT.rejectTitle}
        message={
          rejecting
            ? TEXT.rejectMessage(rejecting.diningTableLabel, rejecting.customerName ?? TEXT.anonymous)
            : ''
        }
        confirmLabel={TEXT.rejectConfirm}
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
            onSuccess: () => onNotice(DINING_ROOM.notice.requestRejected(request.diningTableLabel)),
            onError: (error) => setRejectError({ id: request.id, message: describeError(error) }),
            onSettled: () => setRejecting(null),
          });
        }}
      />
    </>
  );
}

function RequestCard({
  request,
  now,
  rejectError,
  onReject,
  onNotice,
}: {
  readonly request: TableOrderRequest;
  readonly now: number;
  readonly rejectError: string | null;
  readonly onReject: () => void;
  readonly onNotice: (message: string) => void;
}) {
  const confirm = useConfirmTableOrderRequest();
  const minutesLeft = minutesUntil(request.expiresAt, now);
  const error = confirm.error ? describeConfirmRequestError(confirm.error) : rejectError;

  return (
    <article className="dr-request" aria-label={`${DINING_ROOM.table} ${request.diningTableLabel}`}>
      <div className="dr-request-top">
        <span className="dr-request-table">
          {DINING_ROOM.table} {request.diningTableLabel}
        </span>
        <span className={minutesLeft <= 5 ? 'dr-request-expiry is-urgent' : 'dr-request-expiry'}>
          {TEXT.expiresIn(minutesLeft)} · {TEXT.expiresAt(clockOf(request.expiresAt))}
        </span>
      </div>
      <div className="dr-request-who">
        <span className="dr-request-customer">{request.customerName ?? TEXT.anonymous}</span>
        <span className="dr-request-time">{TEXT.requestedAt(clockOf(request.requestedAt))}</span>
      </div>

      <ul className="dr-request-lines">
        {request.lines.map((line, index) => (
          <RequestLine key={`${line.menuItemId}-${index}`} line={line} />
        ))}
      </ul>

      {error && (
        <p className="dr-error-line" role="alert">
          {error}
        </p>
      )}

      <div className="dr-request-actions">
        <button
          type="button"
          className="dr-action-btn dr-action-danger"
          onClick={onReject}
          disabled={confirm.isPending}
        >
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
              onSuccess: () => onNotice(DINING_ROOM.notice.requestConfirmed(request.diningTableLabel)),
            });
          }}
        >
          {confirm.isPending ? TEXT.confirming : TEXT.confirm}
        </button>
      </div>
    </article>
  );
}

function RequestLine({ line }: { readonly line: TableOrderRequestLine }) {
  return (
    <li className="dr-request-line">
      <span className="dr-sheet-item-qty">{line.quantity === null ? TEXT.noQuantity : `${line.quantity}×`}</span>
      <span className="dr-sheet-item-body">
        <span className="dr-sheet-item-name">
          {line.menuItemName}
          {line.variantName && ` · ${line.variantName}`}
        </span>
        {line.modifiers.map((modifier) => (
          <span key={modifier.modifierId} className="dr-sheet-item-detail">
            + {modifier.quantity > 1 ? `${modifier.quantity}× ` : ''}
            {modifier.modifierName}
          </span>
        ))}
        {line.specialInstructions && (
          <span className="dr-sheet-item-detail dr-sheet-item-note">“{line.specialInstructions}”</span>
        )}
      </span>
    </li>
  );
}
