import { useState, useEffect } from 'react';
import { formatMoney } from '../../lib/money';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { DINING_ROOM, describeError } from './diningRoomMessages';
import type { Tab } from './useTab';
import { useQueryClient } from '@tanstack/react-query';
import { tabKey } from './useTab';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly balance: string;
  readonly onClose: () => void;
  readonly onPaymentProcessed: () => void;
  readonly onError: (message: string) => void;
}

interface PaymentResponse {
  paymentId: string;
  method: string;
  amount: string;
  bill: {
    balance: string;
  };
}

/**
 * Modal para receber pagamento de uma comanda em fechamento (CLOSING).
 * A comanda já deve estar em CLOSING quando este modal abre.
 * Após pagamento com saldo zero, a comanda é fechada automaticamente.
 * Usa Idempotency-Key para garantir que retries não duplicam pagamentos.
 */
export function PaymentModal({
  isOpen,
  tab,
  balance,
  onClose,
  onPaymentProcessed,
  onError,
}: Props) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  const [amount, setAmount] = useState(balance);
  const [paymentMethod, setPaymentMethod] = useState<'CASH' | 'PIX' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'ROOM_ACCOUNT'>('CASH');
  const [isProcessing, setIsProcessing] = useState(false);
  const [idempotencyKey] = useState(() => `payment-${Date.now()}-${Math.random().toString(36).slice(2, 9)}`);

  // Reset form when modal opens
  useEffect(() => {
    if (isOpen) {
      setAmount(balance);
      setPaymentMethod('CASH');
    }
  }, [isOpen, balance]);

  if (!isOpen) {
    return null;
  }

  async function handlePayment() {
    setIsProcessing(true);
    try {
      // POST /payments com Idempotency-Key (conforme RFC 7807 e CLAUDE.md)
      const response = await apiCall<PaymentResponse>(`/restaurant/tabs/${tab.id}/payments`, {
        method: 'POST',
        headers: {
          'Idempotency-Key': idempotencyKey,
        } as Record<string, string>,
        body: {
          method: paymentMethod,
          amount,
        },
      });

      // Se o saldo ficou zero, fechar a comanda automaticamente
      const newBalance = response.bill.balance;
      if (newBalance === '0.00' || parseFloat(newBalance) === 0) {
        await apiCall<unknown>(`/restaurant/tabs/${tab.id}/close`, {
          method: 'POST',
        });
      }

      // Recarregar os dados da comanda do servidor (nunca aplicar otimista em dinheiro)
      await queryClient.invalidateQueries({ queryKey: tabKey(tab.id) });

      onPaymentProcessed();
    } catch (error) {
      onError(describeError(error));
    } finally {
      setIsProcessing(false);
    }
  }

  const balanceAmount = parseFloat(balance);
  const paymentAmount = parseFloat(amount);
  const canPay = paymentAmount > 0 && paymentAmount <= balanceAmount;

  return (
    <>
      <div className="dr-modal-backdrop" onClick={onClose} />
      <div className="dr-sheet" role="dialog" aria-modal="true" aria-labelledby="payment-title">
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <h2 id="payment-title" className="dr-sheet-title">
              {DINING_ROOM.payment.title}
            </h2>
            <button
              type="button"
              className="dr-sheet-close"
              onClick={onClose}
              aria-label={DINING_ROOM.close}
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
              >
                <path d="M18 6 6 18" />
                <path d="m6 6 12 12" />
              </svg>
            </button>
          </div>

          <div className="dr-divider" aria-hidden="true">
            <span className="dr-divider-line" />
            <span className="dr-divider-ornament">❧</span>
            <span className="dr-divider-line" />
          </div>

          <div style={{ padding: '16px 0' }}>
            {/* Saldo Devido */}
            <div
              style={{
                padding: '12px',
                marginBottom: '16px',
                background: 'rgba(232, 149, 127, 0.1)',
                borderRadius: '4px',
              }}
            >
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  marginBottom: '4px',
                  fontSize: '0.9em',
                }}
              >
                <span>{DINING_ROOM.payment.balance}</span>
                <strong>{formatMoney(balance)}</strong>
              </div>
            </div>

            {/* Valor a Receber */}
            <div style={{ marginBottom: '16px' }}>
              <label
                htmlFor="payment-amount"
                style={{
                  display: 'block',
                  marginBottom: '8px',
                  fontSize: '13.5px',
                  fontWeight: 600,
                }}
              >
                {DINING_ROOM.payment.amount}
              </label>
              <input
                id="payment-amount"
                type="number"
                step="0.01"
                min="0"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
                disabled={isProcessing}
                style={{
                  width: '100%',
                  padding: '12px',
                  fontSize: '16px',
                  border: '1px solid #E8957F',
                  borderRadius: '4px',
                  background: '#3D0F1A',
                  color: '#F4ECE3',
                  boxSizing: 'border-box',
                }}
              />
            </div>

            {/* Forma de Pagamento */}
            <div style={{ marginBottom: '16px' }}>
              <label
                htmlFor="payment-method"
                style={{
                  display: 'block',
                  marginBottom: '8px',
                  fontSize: '13.5px',
                  fontWeight: 600,
                }}
              >
                {DINING_ROOM.payment.method}
              </label>
              <select
                id="payment-method"
                value={paymentMethod}
                onChange={(e) =>
                  setPaymentMethod(e.target.value as typeof paymentMethod)
                }
                disabled={isProcessing}
                style={{
                  width: '100%',
                  padding: '12px',
                  fontSize: '16px',
                  border: '1px solid #E8957F',
                  borderRadius: '4px',
                  background: '#3D0F1A',
                  color: '#F4ECE3',
                  boxSizing: 'border-box',
                  cursor: 'pointer',
                }}
              >
                <option value="CASH">{DINING_ROOM.payment.cash}</option>
                <option value="PIX">{DINING_ROOM.payment.pix}</option>
                <option value="CREDIT_CARD">{DINING_ROOM.payment.creditCard}</option>
                <option value="DEBIT_CARD">{DINING_ROOM.payment.debitCard}</option>
                <option value="ROOM_ACCOUNT">{DINING_ROOM.payment.roomAccount}</option>
              </select>
            </div>
          </div>

          <div className="dr-sheet-actions">
            <button
              type="button"
              className="dr-action-btn dr-action-primary"
              onClick={() => void handlePayment()}
              disabled={isProcessing || !canPay}
            >
              {isProcessing ? DINING_ROOM.payment.processing : DINING_ROOM.payment.confirmPayment}
            </button>
            <button
              type="button"
              className="dr-action-btn dr-action-ghost"
              onClick={onClose}
              disabled={isProcessing}
            >
              {DINING_ROOM.close}
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
