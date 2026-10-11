import { useState, useEffect } from 'react';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { receiveFolioPayment } from './useOpenFolios';
import type { Folio } from './useOpenFolios';
import { CASHIER, describeError } from './cashierMessages';
import '../../styles/payment-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly folio: Folio;
  readonly onClose: () => void;
  readonly onSuccess: () => void;
}

/**
 * Dialog para receber pagamento de um folio.
 * Usa Idempotency-Key para garantir que retentativas não cobrem duas vezes.
 */
export function PaymentDialog({ isOpen, folio, onClose, onSuccess }: Props) {
  const [amount, setAmount] = useState(folio.balance);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [isProcessing, setIsProcessing] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [idempotencyKey, setIdempotencyKey] = useState<string>('');
  const apiCall = useAuthorizedRequest();

  // Gera uma chave de idempotência única quando o dialog abre
  useEffect(() => {
    if (isOpen) {
      setIdempotencyKey(`payment-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`);
      setError(null);
    }
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  async function handlePayment() {
    setIsProcessing(true);
    setError(null);
    try {
      await receiveFolioPayment(apiCall, folio.id, paymentMethod, amount, idempotencyKey);
      onSuccess();
    } catch (err) {
      setError(describeError(err));
    } finally {
      setIsProcessing(false);
    }
  }

  const balance = parseFloat(folio.balance);
  const amountNum = parseFloat(amount || '0');
  const canPay = amountNum > 0 && amountNum <= balance;
  const displayName = folio.reference?.label || folio.guestName || `Folio ${folio.id.slice(0, 8)}`;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog payment-dialog">
        <div className="dialog-header">
          <h3>{CASHIER.receivePayment}</h3>
          <button className="dialog-close" onClick={onClose} disabled={isProcessing}>✕</button>
        </div>

        <div className="dialog-content">
          <div className="payment-field">
            <label>{CASHIER.folioNumber}</label>
            <p className="payment-folio">{displayName}</p>
          </div>

          <div className="payment-field">
            <label>{CASHIER.balance}</label>
            <p className="payment-balance">{folio.balance}</p>
          </div>

          <div className="payment-field">
            <label htmlFor="amount">{CASHIER.amountToReceive}</label>
            <input
              id="amount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              disabled={isProcessing}
              placeholder="0.00"
              className="payment-input"
            />
          </div>

          <div className="payment-field">
            <label htmlFor="method">{CASHIER.paymentMethodLabel}</label>
            <select
              id="method"
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
              disabled={isProcessing}
              className="payment-select"
            >
              <option value="CASH">{CASHIER.paymentMethod.CASH}</option>
              <option value="PIX">{CASHIER.paymentMethod.PIX}</option>
              <option value="CREDIT_CARD">{CASHIER.paymentMethod.CREDIT_CARD}</option>
              <option value="DEBIT_CARD">{CASHIER.paymentMethod.DEBIT_CARD}</option>
            </select>
          </div>

          {error && <p className="error-message">{error}</p>}
        </div>

        <div className="dialog-actions">
          <button
            className="btn btn-secondary"
            onClick={onClose}
            disabled={isProcessing}
          >
            {CASHIER.cancel}
          </button>
          <button
            className="btn btn-primary"
            onClick={handlePayment}
            disabled={isProcessing || !canPay}
          >
            {isProcessing ? CASHIER.processing : CASHIER.receivePayment}
          </button>
        </div>
      </div>
    </>
  );
}
