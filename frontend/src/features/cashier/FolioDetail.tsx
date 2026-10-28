import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../auth/useCurrentUser';
import type { Folio } from './useOpenFolios';
import { PaymentDialog } from './PaymentDialog';
import { CASHIER } from './cashierMessages';
import '../../styles/cashier.css';

interface Props {
  readonly folio: Folio;
}

/**
 * Detalhe de um folio com lançamentos, pagamentos e botões de ação.
 * ADMIN pode fazer ajustes, reversais e refunds.
 */
export function FolioDetail({ folio }: Props) {
  const [isPaymentOpen, setIsPaymentOpen] = useState(false);
  const { isAdmin } = useCurrentUser();
  const queryClient = useQueryClient();

  const balance = parseFloat(folio.balance);
  const isOverdue = balance > 0;

  return (
    <div className="folio-detail-section">
      <div className="folio-detail-header">
        <div>
          <h3>{CASHIER.folioNumber} {folio.id.slice(0, 8)}</h3>
          {folio.reference && (
            <p className="folio-reference">{folio.reference.code} - {folio.reference.label}</p>
          )}
        </div>
        <span className={`folio-status ${folio.status.toLowerCase()}`}>
          {CASHIER.folioStatus[folio.status as keyof typeof CASHIER.folioStatus] || folio.status}
        </span>
      </div>

      {/* Lançamentos */}
      {folio.charges.length > 0 && (
        <div className="folio-charges">
          <h4>{CASHIER.charges}</h4>
          <table className="charges-table">
            <thead>
              <tr>
                <th>Descrição</th>
                <th>Valor</th>
                <th>Tipo</th>
              </tr>
            </thead>
            <tbody>
              {folio.charges.map((charge) => (
                <tr key={charge.id}>
                  <td>{charge.description}</td>
                  <td className="amount">{charge.amount}</td>
                  <td>
                    <span className="charge-type">
                      {CASHIER.chargeType[charge.type as keyof typeof CASHIER.chargeType] || charge.type}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Pagamentos */}
      {folio.payments.length > 0 && (
        <div className="folio-payments">
          <h4>{CASHIER.payments}</h4>
          <table className="payments-table">
            <thead>
              <tr>
                <th>Forma</th>
                <th>Valor</th>
                <th>Status</th>
              </tr>
            </thead>
            <tbody>
              {folio.payments.map((payment) => (
                <tr key={payment.id}>
                  <td>
                    {CASHIER.paymentMethod[payment.method as keyof typeof CASHIER.paymentMethod] ||
                      payment.method}
                  </td>
                  <td className="amount">{payment.amount}</td>
                  <td>
                    <span className={`payment-status ${payment.status.toLowerCase()}`}>
                      {CASHIER.paymentStatus[payment.status as keyof typeof CASHIER.paymentStatus] ||
                        payment.status}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Saldo */}
      <div className="folio-balance">
        <div className="balance-row">
          <span>{CASHIER.totalCharges}</span>
          <span>{folio.totalCharges}</span>
        </div>
        <div className="balance-row">
          <span>{CASHIER.totalPayments}</span>
          <span>{folio.totalPayments}</span>
        </div>
        <div className={`balance-row total ${isOverdue ? 'overdue' : 'paid'}`}>
          <span>{CASHIER.balance}</span>
          <span>{folio.balance}</span>
        </div>
      </div>

      {/* Ações */}
      <div className="folio-actions">
        {isOverdue && (
          <button
            className="btn btn-primary"
            onClick={() => setIsPaymentOpen(true)}
          >
            {CASHIER.receivePayment}
          </button>
        )}
        {folio.status === 'OPEN' && !isOverdue && (
          <button
            className="btn btn-secondary"
            disabled={folio.type === 'TAB'}
            title={folio.type === 'TAB' ? CASHIER.foliaTABClosureNote : undefined}
          >
            {CASHIER.closeFolio}
          </button>
        )}
        {isAdmin && folio.status === 'OPEN' && (
          <button className="btn btn-secondary" disabled title="Em desenvolvimento">
            {CASHIER.postAdjustment}
          </button>
        )}
      </div>

      {/* Dialog de Pagamento */}
      <PaymentDialog
        isOpen={isPaymentOpen}
        folio={folio}
        onClose={() => setIsPaymentOpen(false)}
        onSuccess={() => {
          setIsPaymentOpen(false);
          queryClient.invalidateQueries({ queryKey: ['open-folios'] });
        }}
      />
    </div>
  );
}
