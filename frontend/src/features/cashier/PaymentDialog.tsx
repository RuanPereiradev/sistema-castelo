import { randomId } from '../../lib/randomId';
import { useEffect, useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { formatMoney, moneySign } from '../../lib/money';
import {
  receiveFolioPayment,
  rereadFolio,
  type Folio,
  type PaymentMethod,
} from './useOpenFolios';
import { CASHIER, describeError } from './cashierMessages';
import '../../styles/payment-dialog.css';
import '../../styles/confirm-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly folio: Folio;
  readonly displayName: string;
  readonly onClose: () => void;
  readonly onSuccess: () => void;
}

/** Methods registered by hand at the counter; `ROOM_ACCOUNT` is refused there by the backend. */
const COUNTER_METHODS: readonly PaymentMethod[] = ['CASH', 'PIX', 'CREDIT_CARD', 'DEBIT_CARD'];

function newAttemptKey(): string {
  return randomId();
}

/** The balance as the operator types it, comma for the cents; empty when nothing is owed. */
function suggestedAmount(balance: string): string {
  return moneySign(balance) === 'positive' ? balance.replace('.', ',') : '';
}

/**
 * Receives a payment on a folio. The `Idempotency-Key` belongs to the operator's attempt (decision
 * #16): the retry after a failure reuses it, even after closing and reopening the dialog untouched;
 * changing the amount or the method, or a success, starts a new attempt with a new key.
 *
 * The screen does not compare the amount with the balance: the backend refuses what it does not take
 * (`PAYMENT_EXCEEDS_BALANCE` on a tab's folio) and the code is translated (decision #11).
 */
export function PaymentDialog({ isOpen, folio, displayName, onClose, onSuccess }: Props) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [amount, setAmount] = useState(() => suggestedAmount(folio.balance));
  const [method, setMethod] = useState<PaymentMethod>('CASH');
  const [attemptKey, setAttemptKey] = useState(newAttemptKey);
  const [pendingRetry, setPendingRetry] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!isOpen) {
      return;
    }
    setError(null);
    if (!pendingRetry) {
      // Nothing left hanging: a fresh attempt, starting from the balance the server sent.
      setAmount(suggestedAmount(folio.balance));
      setAttemptKey(newAttemptKey());
    }
    // Only on opening: the balance refreshing in the background must not rewrite what was typed.
  }, [isOpen]);

  if (!isOpen) {
    return null;
  }

  function edit(change: () => void) {
    change();
    // Another amount or method is another attempt, with another key.
    setAttemptKey(newAttemptKey());
    setPendingRetry(false);
  }

  async function receive() {
    setWorking(true);
    setError(null);
    try {
      await receiveFolioPayment(apiCall, folio.id, method, amount, attemptKey);
      setPendingRetry(false);
      setAttemptKey(newAttemptKey());
      onSuccess();
    } catch (failure) {
      setPendingRetry(true);
      setError(describeError(failure));
    } finally {
      setWorking(false);
      void rereadFolio(queryClient, folio.id);
    }
  }

  function close() {
    if (!working) {
      onClose();
    }
  }

  return (
    <>
      <div className="dialog-overlay" onClick={close} />
      <div className="dialog payment-dialog" role="dialog" aria-modal="true" aria-label={CASHIER.receivePayment}>
        <div className="dialog-header">
          <h3 className="dialog-title">{CASHIER.receivePayment}</h3>
        </div>

        <div className="dialog-content">
          <div className="payment-field">
            <label>{CASHIER.folioNumber}</label>
            <p className="payment-folio">{displayName}</p>
          </div>

          <div className="payment-field">
            <label>{CASHIER.balance}</label>
            <p className="payment-balance">{formatMoney(folio.balance)}</p>
          </div>

          <div className="payment-field">
            <label htmlFor="paymentAmount">{CASHIER.amountToReceive}</label>
            <input
              id="paymentAmount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => edit(() => setAmount(event.target.value))}
              disabled={working}
              placeholder="0,00"
              className="form-input"
            />
          </div>

          <div className="payment-field">
            <label htmlFor="paymentMethod">{CASHIER.paymentMethodLabel}</label>
            <select
              id="paymentMethod"
              value={method}
              onChange={(event) => edit(() => setMethod(event.target.value as PaymentMethod))}
              disabled={working}
              className="payment-select"
            >
              {COUNTER_METHODS.map((option) => (
                <option key={option} value={option}>
                  {CASHIER.paymentMethod[option]}
                </option>
              ))}
            </select>
          </div>

          {error && <p className="error-message">{error}</p>}
        </div>

        <div className="dialog-actions">
          <button className="btn btn-secondary" onClick={close} disabled={working}>
            {CASHIER.cancel}
          </button>
          <button
            className="btn btn-primary"
            onClick={() => void receive()}
            disabled={working || amount.trim() === ''}
          >
            {working ? CASHIER.processing : CASHIER.receivePayment}
          </button>
        </div>
      </div>
    </>
  );
}
