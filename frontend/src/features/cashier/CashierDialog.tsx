import type { ReactNode } from 'react';
import { CASHIER } from './cashierMessages';
import '../../styles/confirm-dialog.css';

interface Props {
  readonly title: string;
  readonly message?: string;
  readonly children?: ReactNode;
  readonly error: string | null;
  readonly working: boolean;
  readonly canConfirm: boolean;
  readonly confirmLabel: string;
  readonly isDanger?: boolean;
  readonly onConfirm: () => void;
  readonly onCancel: () => void;
}

/**
 * Confirmation of a folio action. Unlike the generic confirm dialog, it stays open on failure with
 * the translated error, so the retry keeps the reason the operator already typed.
 */
export function CashierDialog({
  title,
  message,
  children,
  error,
  working,
  canConfirm,
  confirmLabel,
  isDanger = false,
  onConfirm,
  onCancel,
}: Props) {
  function cancel() {
    if (!working) {
      onCancel();
    }
  }

  return (
    <>
      <div className="dialog-overlay" onClick={cancel} />
      <div className="dialog confirm-dialog" role="dialog" aria-modal="true" aria-label={title}>
        <div className="dialog-header">
          <h3 className="dialog-title">{title}</h3>
        </div>

        <div className="dialog-content">
          {message && <p className="dialog-message">{message}</p>}
          {children}
          {error && <p className="error-message">{error}</p>}
        </div>

        <div className="dialog-actions">
          <button className="btn btn-secondary" onClick={cancel} disabled={working}>
            {CASHIER.cancel}
          </button>
          <button
            className={`btn ${isDanger ? 'btn-danger' : 'btn-primary'}`}
            onClick={onConfirm}
            disabled={working || !canConfirm}
          >
            {working ? CASHIER.processing : confirmLabel}
          </button>
        </div>
      </div>
    </>
  );
}
