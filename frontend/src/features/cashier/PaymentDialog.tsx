import { useState } from 'react';
import type { Folio } from './useOpenFolios';
import '../../styles/payment-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly folio: Folio;
  readonly onClose: () => void;
  readonly onSuccess: () => void;
}

/**
 * Dialog pra receber pagamento de um folio.
 */
export function PaymentDialog({ isOpen, folio, onClose, onSuccess }: Props) {
  const [amount, setAmount] = useState(folio.balance);
  const [paymentMethod, setPaymentMethod] = useState('CASH');
  const [isProcessing, setIsProcessing] = useState(false);

  if (!isOpen) {
    return null;
  }

  async function handlePayment() {
    setIsProcessing(true);
    try {
      // TODO: Implementar POST /billing/folios/{id}/payments
      console.log('Payment:', { folioId: folio.id, amount, method: paymentMethod });
      // await api.pay(folio.id, amount, paymentMethod);
      onSuccess();
    } finally {
      setIsProcessing(false);
    }
  }

  const balance = parseFloat(folio.balance);
  const canPay = parseFloat(amount) > 0 && parseFloat(amount) <= balance;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog payment-dialog">
        <div className="dialog-header">
          <h3>Receber pagamento</h3>
          <button className="dialog-close" onClick={onClose}>✕</button>
        </div>

        <div className="dialog-content">
          <div className="payment-field">
            <label>Folio</label>
            <p className="payment-folio">{folio.guestName}</p>
          </div>

          <div className="payment-field">
            <label>Saldo devido</label>
            <p className="payment-balance">{folio.balance}</p>
          </div>

          <div className="payment-field">
            <label htmlFor="amount">Valor a receber</label>
            <input
              id="amount"
              type="number"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              disabled={isProcessing}
              step="0.01"
              className="payment-input"
            />
          </div>

          <div className="payment-field">
            <label htmlFor="method">Forma de pagamento</label>
            <select
              id="method"
              value={paymentMethod}
              onChange={(e) => setPaymentMethod(e.target.value)}
              disabled={isProcessing}
              className="payment-select"
            >
              <option value="CASH">Dinheiro</option>
              <option value="PIX">Pix</option>
              <option value="CREDIT_CARD">Cartão de crédito</option>
              <option value="DEBIT_CARD">Cartão de débito</option>
            </select>
          </div>
        </div>

        <div className="dialog-actions">
          <button
            className="btn btn-secondary"
            onClick={onClose}
            disabled={isProcessing}
          >
            Cancelar
          </button>
          <button
            className="btn btn-primary"
            onClick={handlePayment}
            disabled={isProcessing || !canPay}
          >
            {isProcessing ? 'Processando...' : 'Confirmar pagamento'}
          </button>
        </div>
      </div>
    </>
  );
}
