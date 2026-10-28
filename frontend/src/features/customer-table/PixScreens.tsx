import { useEffect, useRef, useState } from 'react';
import QRCode from 'qrcode';
import { formatMoney, formatWeight } from '../../lib/money';
import type { PublicTablePaymentRequest, PublicTabItem } from './customerApi';
import { CUSTOMER, describeCustomerError } from './customerMessages';
import { StateMessage } from './CustomerChrome';

function clockTime(instant: string): string {
  return new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(new Date(instant));
}

function unitCount(request: PublicTablePaymentRequest): number {
  return request.items.reduce((count, item) => count + item.quantity, 0);
}

/** The QR is drawn here from the backend's payload, the same text as copia e cola (decision #29). */
function PixQrCode({ payload }: { readonly payload: string }) {
  const canvas = useRef<HTMLCanvasElement>(null);
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    if (!canvas.current) {
      return;
    }
    QRCode.toCanvas(canvas.current, payload, {
      width: 216,
      margin: 2,
      errorCorrectionLevel: 'M',
      color: { dark: '#2A0A12', light: '#F4ECE3' },
    }).then(
      () => setFailed(false),
      () => setFailed(true),
    );
  }, [payload]);

  return (
    <div className="ct-qr-arch">
      <div className="ct-qr-arch-inner">
        {failed ? (
          <p className="ct-hint">{CUSTOMER.pix.qrFailed}</p>
        ) : (
          <canvas ref={canvas} className="ct-qr" role="img" aria-label={CUSTOMER.pix.qrLabel} />
        )}
      </div>
    </div>
  );
}

/** Clipboard API where it exists (HTTPS); a hidden textarea and `execCommand` otherwise. */
async function copyText(text: string): Promise<boolean> {
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(text);
      return true;
    }
  } catch {
    // Falls through to the old way.
  }
  try {
    const area = document.createElement('textarea');
    area.value = text;
    area.setAttribute('readonly', '');
    area.style.position = 'fixed';
    area.style.opacity = '0';
    document.body.appendChild(area);
    area.select();
    const copied = document.execCommand('copy');
    document.body.removeChild(area);
    return copied;
  } catch {
    return false;
  }
}

/** The lines of a payment, named from the tab the phone already read. */
function PaymentItems({
  request,
  tabItems,
}: {
  readonly request: PublicTablePaymentRequest;
  readonly tabItems: readonly PublicTabItem[];
}) {
  return (
    <div className="ct-pay-items">
      {request.items.map((part) => {
        const item = tabItems.find((candidate) => candidate.id === part.tabItemId);
        const name = item
          ? [item.weightGrams !== null ? formatWeight(item.weightGrams) : null, item.itemName, item.variantName]
              .filter(Boolean)
              .join(' · ')
          : '—';
        return (
          <div key={part.tabItemId} className="ct-pay-item">
            <span className="ct-card-quantity">{part.quantity}×</span>
            <span className="ct-card-name">{name}</span>
            <span className="ct-pay-item-price">{formatMoney(part.lineTotal)}</span>
          </div>
        );
      })}
      <div className="ct-pay-item ct-pay-item-service">
        <span className="ct-card-quantity" />
        <span className="ct-card-name">{request.includeServiceCharge ? CUSTOMER.pix.service : CUSTOMER.pix.noService}</span>
        {request.includeServiceCharge && <span className="ct-pay-item-price">{formatMoney(request.serviceCharge)}</span>}
      </div>
    </div>
  );
}

export function PixScreen({
  request,
  isLoading,
  error,
  onRetry,
  tabItems,
  onPaid,
  onNotice,
}: {
  readonly request: PublicTablePaymentRequest | undefined;
  readonly isLoading: boolean;
  readonly error: unknown;
  readonly onRetry: () => void;
  readonly tabItems: readonly PublicTabItem[];
  readonly onPaid: () => void;
  readonly onNotice: (message: string) => void;
}) {
  const [copyFailed, setCopyFailed] = useState(false);

  if (!request) {
    return (
      <div className="ct-body">
        {isLoading ? (
          <StateMessage role="status">{CUSTOMER.pix.loading}</StateMessage>
        ) : (
          <StateMessage role="alert" onRetry={onRetry}>
            {CUSTOMER.pix.failed} {describeCustomerError(error)}
          </StateMessage>
        )}
      </div>
    );
  }

  return (
    <>
      <div className="ct-body">
        <div className="ct-pix">
          <div className="ct-pix-amount">
            <span className="ct-eyebrow">{CUSTOMER.pix.toPay(unitCount(request), request.includeServiceCharge)}</span>
            <span className="ct-pix-value">{formatMoney(request.amount)}</span>
            <span className="ct-muted">{CUSTOMER.pix.validUntil(clockTime(request.expiresAt))}</span>
          </div>

          <PixQrCode payload={request.pixPayload} />

          <div className="ct-copy">
            <span className="ct-eyebrow">{CUSTOMER.pix.copyTitle}</span>
            <div className="ct-copy-row">
              <span className="ct-copy-code">{request.pixPayload}</span>
              <button
                type="button"
                className="btn btn-secondary ct-copy-btn"
                onClick={() => {
                  void copyText(request.pixPayload).then((copied) => {
                    setCopyFailed(!copied);
                    if (copied) {
                      onNotice(CUSTOMER.pix.copied);
                    }
                  });
                }}
              >
                {CUSTOMER.pix.copy}
              </button>
            </div>
            {copyFailed && (
              <>
                <p className="ct-hint">{CUSTOMER.pix.copyFailed}</p>
                <textarea className="input ct-copy-fallback" readOnly value={request.pixPayload} rows={3} />
              </>
            )}
          </div>

          <ol className="ct-steps">
            {CUSTOMER.pix.steps.map((step) => (
              <li key={step}>{step}</li>
            ))}
          </ol>

          <PaymentItems request={request} tabItems={tabItems} />
        </div>
      </div>
      <div className="ct-footer ct-footer-stack">
        <button type="button" className="btn btn-primary btn-block ct-cta" onClick={onPaid}>
          {CUSTOMER.pix.paid}
        </button>
      </div>
    </>
  );
}

export function ReceiptScreen({
  request,
  isLoading,
  error,
  onRetry,
  tabItems,
  onSeePix,
  onBackToBill,
}: {
  readonly request: PublicTablePaymentRequest | undefined;
  readonly isLoading: boolean;
  readonly error: unknown;
  readonly onRetry: () => void;
  readonly tabItems: readonly PublicTabItem[];
  readonly onSeePix: () => void;
  readonly onBackToBill: () => void;
}) {
  let body;
  if (!request) {
    body = isLoading ? (
      <StateMessage role="status">{CUSTOMER.pix.loading}</StateMessage>
    ) : (
      <StateMessage role="alert" onRetry={onRetry}>
        {CUSTOMER.pix.failed} {describeCustomerError(error)}
      </StateMessage>
    );
  } else {
    const refunded = request.refunded;
    body = (
      <div className="ct-receipt">
        <div className="ct-arch ct-receipt-arch">
          <div className="ct-arch-inner ct-receipt-inner">
            <div className="ct-receipt-head" role="status">
              <div className={`ct-receipt-mark is-${request.status.toLowerCase()}`} aria-hidden="true">
                {request.status === 'CONFIRMED' && !refunded ? '✓' : request.status === 'PENDING' ? '…' : '✕'}
              </div>
              <span className="ct-eyebrow">{CUSTOMER.receipt.informed(clockTime(request.requestedAt))}</span>
              <span className="ct-pix-value">{formatMoney(request.amount)}</span>
              <span className="ct-receipt-state">
                {refunded ? CUSTOMER.receipt.refunded : CUSTOMER.receipt.states[request.status]}
              </span>
              <span className="ct-muted">
                {refunded ? CUSTOMER.receipt.refundedHint : CUSTOMER.receipt.hints[request.status]}
              </span>
            </div>
            {request.status === 'PENDING' && <div className="ct-receipt-callout">{CUSTOMER.receipt.showReceipt}</div>}
            <PaymentItems request={request} tabItems={tabItems} />
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="ct-body">{body}</div>
      <div className="ct-footer ct-footer-stack">
        {request?.status === 'PENDING' && (
          <button type="button" className="btn btn-ghost ct-more" onClick={onSeePix}>
            {CUSTOMER.receipt.seePix}
          </button>
        )}
        <button type="button" className="btn btn-secondary btn-block ct-cta-secondary" onClick={onBackToBill}>
          {CUSTOMER.receipt.backToBill}
        </button>
      </div>
    </>
  );
}
