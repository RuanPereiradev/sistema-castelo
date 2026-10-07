import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useOpenFolios } from './useOpenFolios';
import { FolioCard } from './FolioCard';
import { PaymentDialog } from './PaymentDialog';
import '../../styles/cashier.css';

/**
 * Tela de Caixa (FRONT_DESK).
 * Lista folios abertos, recebe pagamentos, mostra saldo total.
 */
export function CashierPage() {
  const [selectedFolioId, setSelectedFolioId] = useState<string | null>(null);
  const [isPaymentOpen, setIsPaymentOpen] = useState(false);
  const queryClient = useQueryClient();

  const { data: folios = [], isLoading } = useOpenFolios();

  const selectedFolio = folios.find((f) => f.id === selectedFolioId);
  const totalBalance = folios.reduce((sum, f) => sum + parseFloat(f.balance), 0);

  return (
    <div className="cashier-container">
      {/* Lista de Folios */}
      <div className="folios-section">
        <h2 className="section-title">Contas em aberto</h2>

        {isLoading && <p className="loading">Carregando contas...</p>}

        {!isLoading && folios.length === 0 && (
          <p className="empty-message">Nenhuma conta aberta</p>
        )}

        {!isLoading && folios.length > 0 && (
          <div className="folios-grid">
            {folios.map((folio) => (
              <FolioCard
                key={folio.id}
                folio={folio}
                isSelected={selectedFolioId === folio.id}
                onSelect={() => setSelectedFolioId(folio.id)}
              />
            ))}
          </div>
        )}
      </div>

      {/* Detalhe e Pagamento */}
      {selectedFolio && (
        <div className="folio-detail-section">
          <div className="folio-detail-header">
            <h3>Folio {selectedFolio.id.slice(0, 8)}</h3>
            <p className="folio-guest">{selectedFolio.guestName}</p>
          </div>

          <div className="folio-charges">
            <h4>Lançamentos</h4>
            <table className="charges-table">
              <thead>
                <tr>
                  <th>Descrição</th>
                  <th>Valor</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {selectedFolio.charges.map((charge) => (
                  <tr key={charge.id}>
                    <td>{charge.description}</td>
                    <td className="amount">{charge.amount}</td>
                    <td>
                      <span className={`charge-status ${charge.type.toLowerCase()}`}>
                        {charge.type}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="folio-balance">
            <div className="balance-row">
              <span>Subtotal</span>
              <span>{selectedFolio.subtotal}</span>
            </div>
            <div className="balance-row total">
              <span>Total</span>
              <span>{selectedFolio.balance}</span>
            </div>
          </div>

          <button
            className="btn btn-primary"
            onClick={() => setIsPaymentOpen(true)}
          >
            Receber pagamento
          </button>
        </div>
      )}

      {/* Resumo Total */}
      <div className="cashier-summary">
        <div className="summary-card">
          <p className="summary-label">Total em aberto</p>
          <p className="summary-value">{totalBalance.toFixed(2)}</p>
        </div>
        <div className="summary-card">
          <p className="summary-label">Contas abertas</p>
          <p className="summary-value">{folios.length}</p>
        </div>
      </div>

      {/* Dialog de Pagamento */}
      {selectedFolio && (
        <PaymentDialog
          isOpen={isPaymentOpen}
          folio={selectedFolio}
          onClose={() => setIsPaymentOpen(false)}
          onSuccess={() => {
            setIsPaymentOpen(false);
            queryClient.invalidateQueries({ queryKey: ['open-folios'] });
          }}
        />
      )}
    </div>
  );
}
