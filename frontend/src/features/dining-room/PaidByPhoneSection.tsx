import { useState } from 'react';
import { formatMoney } from '../../lib/money';
import { DINING_ROOM, describeError, describeRefundPixError } from './diningRoomMessages';
import { ConfirmDialog } from './ConfirmDialog';
import {
  isRouteNotAvailable,
  useConfirmedTablePayments,
  useRefundTablePaymentRequest,
  type TablePaymentRequest,
} from './useTablePaymentRequests';

const TEXT = DINING_ROOM.paidByPhone;
const PIX = DINING_ROOM.orderRequests;
const MAXIMUM_REFUND_REASON_LENGTH = 500;

function clockOf(isoInstant: string): string {
  return new Date(isoInstant).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/**
 * The Pix the waiter already confirmed on this tab (decision #38), and the
 * refund of one of them, with a required reason. Values are the backend's.
 */
export function PaidByPhoneSection({
  tabId,
  onNotice,
}: {
  readonly tabId: string;
  readonly onNotice: (message: string) => void;
}) {
  const payments = useConfirmedTablePayments(tabId);
  const refund = useRefundTablePaymentRequest();
  const [refunding, setRefunding] = useState<TablePaymentRequest | null>(null);
  const [reason, setReason] = useState('');
  const [refundError, setRefundError] = useState<{ readonly id: string; readonly message: string } | null>(null);

  let body;
  if (payments.isPending) {
    body = <p className="dr-paid-state">{TEXT.loading}</p>;
  } else if (payments.error && isRouteNotAvailable(payments.error)) {
    body = <p className="dr-paid-state">{TEXT.unavailable}</p>;
  } else if (payments.error && !payments.data) {
    body = (
      <p className="dr-paid-state" role="alert">
        {TEXT.failed} {describeError(payments.error)}
      </p>
    );
  } else if ((payments.data ?? []).length === 0) {
    body = <p className="dr-paid-state">{TEXT.empty}</p>;
  } else {
    body = (payments.data ?? []).map((payment) => (
      <div key={payment.id} className={payment.refunded ? 'dr-paid is-refunded' : 'dr-paid'}>
        <div className="dr-paid-top">
          <span className="dr-request-customer">{payment.customerName}</span>
          <span className="dr-paid-amount">{formatMoney(payment.amount)}</span>
        </div>
        <div className="dr-paid-meta">
          {payment.includeServiceCharge ? PIX.withService(formatMoney(payment.serviceCharge)) : PIX.withoutService}
          {payment.resolvedAt && ` · ${TEXT.confirmedAt(clockOf(payment.resolvedAt))}`}
        </div>
        <ul className="dr-request-lines">
          {payment.items.map((item) => (
            <li key={item.tabItemId} className="dr-request-line">
              <span className="dr-sheet-item-qty">{PIX.units(item.quantity, item.lineQuantity)}</span>
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
        {payment.refunded ? (
          <div className="dr-paid-refunded">
            <span className="dr-paid-tag">
              {payment.refundedAt ? TEXT.refundedAt(clockOf(payment.refundedAt)) : TEXT.refunded}
            </span>
            {payment.refundReason && <span className="dr-paid-meta">{TEXT.refundReason(payment.refundReason)}</span>}
          </div>
        ) : (
          <>
            {refundError?.id === payment.id && (
              <p className="dr-error-line" role="alert">
                {refundError.message}
              </p>
            )}
            <button
              type="button"
              className="dr-action-btn dr-action-danger dr-paid-refund"
              disabled={refund.isPending}
              onClick={() => {
                setRefundError(null);
                setReason('');
                setRefunding(payment);
              }}
            >
              {TEXT.refund}
            </button>
          </>
        )}
      </div>
    ));
  }

  const trimmed = reason.trim();

  return (
    <section className="dr-paid-section" aria-label={TEXT.title}>
      <h3 className="dr-requests-section">❧ {TEXT.title}</h3>
      {body}

      <ConfirmDialog
        isOpen={refunding !== null}
        title={TEXT.refundTitle}
        message={refunding ? TEXT.refundMessage(refunding.customerName, formatMoney(refunding.amount)) : ''}
        requiresReason
        reason={reason}
        onReasonChange={setReason}
        reasonLabel={TEXT.refundReasonLabel}
        reasonPlaceholder={TEXT.refundReasonPlaceholder}
        reasonMaxLength={MAXIMUM_REFUND_REASON_LENGTH}
        confirmLabel={TEXT.refundConfirm}
        cancelLabel={PIX.keep}
        isDanger
        isLoading={refund.isPending}
        onCancel={() => setRefunding(null)}
        onConfirm={() => {
          if (!refunding || refund.isPending || trimmed.length === 0 || trimmed.length > MAXIMUM_REFUND_REASON_LENGTH) {
            return;
          }
          const payment = refunding;
          refund.mutate(
            { requestId: payment.id, reason: trimmed },
            {
              onSuccess: () => onNotice(DINING_ROOM.notice.pixRefunded(payment.customerName)),
              onError: (error) => setRefundError({ id: payment.id, message: describeRefundPixError(error) }),
              onSettled: () => setRefunding(null),
            },
          );
        }}
      />
    </section>
  );
}
