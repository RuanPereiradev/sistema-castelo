import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { useCashSession, closeCashSession } from './useCashSession';
import { CASHIER, describeError } from './cashierMessages';
import '../../styles/cashier.css';

/**
 * Tela de turno de caixa com fechamento cego.
 * O operador conta o dinheiro sem ver o total esperado.
 */
export function CashDrawerSessionComponent() {
  const [countedAmount, setCountedAmount] = useState('');
  const [closingNote, setClosingNote] = useState('');
  const [isClosing, setIsClosing] = useState(false);
  const [showDifference, setShowDifference] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  const { data: session, isLoading, error: loadError } = useCashSession();

  async function handleCloseSession() {
    if (!session || !countedAmount) return;

    setIsClosing(true);
    setError(null);
    try {
      await closeCashSession(apiCall, session.id, countedAmount, closingNote);
      queryClient.invalidateQueries({ queryKey: ['cash-session-current'] });
      setCountedAmount('');
      setClosingNote('');
      setShowDifference(false);
    } catch (err) {
      setError(describeError(err));
    } finally {
      setIsClosing(false);
    }
  }

  if (isLoading) {
    return (
      <div className="cash-session-container">
        <p className="loading">{CASHIER.loadingSessions}</p>
      </div>
    );
  }

  if (loadError || !session) {
    return (
      <div className="cash-session-container">
        <p className="error">{CASHIER.sessionFailed}</p>
      </div>
    );
  }

  const isClosed = session.status === 'CLOSED';
  const parsed = parseFloat(countedAmount || '0');
  const expected = session.expectedAmount ? parseFloat(session.expectedAmount) : 0;
  const difference = showDifference ? parsed - expected : 0;

  return (
    <div className="cash-session-container">
      <div className="session-header">
        <h2>{CASHIER.currentSession}</h2>
        <span className={`session-status ${session.status.toLowerCase()}`}>
          {CASHIER.statusLabel[session.status as keyof typeof CASHIER.statusLabel] || session.status}
        </span>
      </div>

      {/* Informações do turno */}
      <div className="session-info">
        <div className="info-row">
          <span className="info-label">{CASHIER.openingFloat}</span>
          <span className="info-value">{session.openingFloat}</span>
        </div>
        <div className="info-row">
          <span className="info-label">{CASHIER.totalDrops}</span>
          <span className="info-value">{session.totalDrops}</span>
        </div>
        <div className="info-row">
          <span className="info-label">{CASHIER.totalSupplies}</span>
          <span className="info-value">{session.totalSupplies}</span>
        </div>
        <div className="info-row">
          <span className="info-label">{CASHIER.cashPaymentsTotal}</span>
          <span className="info-value">{session.cashPaymentsTotal || '—'}</span>
        </div>
        <div className="info-row">
          <span className="info-label">{CASHIER.paymentCount}</span>
          <span className="info-value">{session.cashPaymentCount}</span>
        </div>
      </div>

      {/* Movimentações */}
      {session.movements.length > 0 && (
        <div className="movements-section">
          <h3>{CASHIER.title}</h3>
          <table className="movements-table">
            <thead>
              <tr>
                <th>Tipo</th>
                <th>Valor</th>
                <th>Motivo</th>
                <th>Data</th>
              </tr>
            </thead>
            <tbody>
              {session.movements.map((movement) => (
                <tr key={movement.id}>
                  <td>{movement.type}</td>
                  <td className="amount">{movement.amount}</td>
                  <td>{movement.reason}</td>
                  <td>{new Date(movement.createdAt).toLocaleTimeString('pt-BR')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {/* Fechamento do turno (apenas se aberto) */}
      {!isClosed && session.expectedAmount !== null && (
        <div className="closing-section">
          <p className="closing-instruction">{CASHIER.countingBlind}</p>

          {error && <p className="error-message">{error}</p>}

          <div className="closing-form">
            <div className="form-group">
              <label htmlFor="countedAmount">{CASHIER.enterCountedAmount}</label>
              <input
                id="countedAmount"
                type="text"
                inputMode="decimal"
                value={countedAmount}
                onChange={(e) => {
                  setCountedAmount(e.target.value);
                  setShowDifference(false);
                }}
                disabled={isClosing}
                placeholder="0.00"
                className="form-input"
              />
            </div>

            <div className="form-group">
              <label htmlFor="closingNote">{CASHIER.closingNote} {CASHIER.closingNoteOptional}</label>
              <textarea
                id="closingNote"
                value={closingNote}
                onChange={(e) => setClosingNote(e.target.value)}
                disabled={isClosing}
                placeholder="Ex: diferença de 5 reais encontrada..."
                className="form-textarea"
                rows={2}
              />
            </div>

            {/* Botão para revelar diferença */}
            {countedAmount && !showDifference && (
              <button
                className="btn btn-secondary"
                onClick={() => setShowDifference(true)}
                disabled={isClosing}
              >
                Revelar diferença
              </button>
            )}

            {/* Resumo da contagem */}
            {showDifference && (
              <div className="closing-summary">
                <div className="summary-row">
                  <span>{CASHIER.expectedAmount}</span>
                  <span className="value">{session.expectedAmount}</span>
                </div>
                <div className="summary-row">
                  <span>{CASHIER.countedAmount}</span>
                  <span className="value">{countedAmount}</span>
                </div>
                <div className={`summary-row total ${difference === 0 ? 'balanced' : 'unbalanced'}`}>
                  <span>{CASHIER.difference}</span>
                  <span className="value">{difference >= 0 ? '+' : ''}{difference.toFixed(2)}</span>
                </div>
              </div>
            )}

            {/* Botão para fechar turno */}
            {showDifference && (
              <button
                className="btn btn-primary"
                onClick={handleCloseSession}
                disabled={isClosing || !countedAmount}
              >
                {isClosing ? 'Fechando…' : CASHIER.closeSession}
              </button>
            )}
          </div>
        </div>
      )}

      {/* Turno já fechado */}
      {isClosed && (
        <div className="closed-session-info">
          <h3>{CASHIER.sessionClosed}</h3>
          <div className="info-row">
            <span className="info-label">{CASHIER.expectedAmount}</span>
            <span className="info-value">{session.expectedAmount}</span>
          </div>
          <div className="info-row">
            <span className="info-label">{CASHIER.countedAmount}</span>
            <span className="info-value">{session.countedAmount}</span>
          </div>
          <div className="info-row">
            <span className="info-label">{CASHIER.difference}</span>
            <span className={`info-value ${session.difference === '0.00' ? 'balanced' : 'unbalanced'}`}>
              {session.difference}
            </span>
          </div>
          {session.closingNote && (
            <div className="info-row">
              <span className="info-label">Nota</span>
              <span className="info-value">{session.closingNote}</span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}
