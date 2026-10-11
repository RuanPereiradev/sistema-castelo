import { useState } from 'react';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { useQueryClient } from '@tanstack/react-query';
import type { KitchenTicket } from './useKitchenTickets';

interface Props {
  readonly ticket: KitchenTicket;
  readonly onClose: () => void;
}

/**
 * Detalhe de um ticket da cozinha com ações.
 * Permite iniciar, marcar como pronto, e desfazer transições.
 */
export function TicketDetail({ ticket, onClose }: Props) {
  const [loading, setLoading] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();

  const handleAction = async (itemId: string, action: 'start' | 'ready' | 'undo') => {
    setLoading(itemId);
    setError(null);

    try {
      await apiCall(`/kitchen/items/${itemId}/${action}`, {
        method: 'POST',
      });

      // Atualizar a fila
      queryClient.invalidateQueries({ queryKey: ['kitchen-tickets'] });
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Erro ao executar ação');
    } finally {
      setLoading(null);
    }
  };

  const getActions = (itemStatus: string) => {
    switch (itemStatus) {
      case 'PENDING':
        return [{ label: 'Iniciar', action: 'start' as const }];
      case 'IN_PREPARATION':
        return [
          { label: 'Pronto', action: 'ready' as const },
          { label: 'Desfazer', action: 'undo' as const },
        ];
      case 'READY':
        return [{ label: 'Desfazer', action: 'undo' as const }];
      default:
        return [];
    }
  };

  return (
    <div className="ticket-detail">
      <div className="detail-header">
        <h3>Ticket #{ticket.id.slice(0, 8)}</h3>
        <button className="detail-close" onClick={onClose}>✕</button>
      </div>

      <div className="detail-info">
        <div className="detail-row">
          <span className="label">Mesa</span>
          <span className="value">{ticket.tableLabel}</span>
        </div>
        <div className="detail-row">
          <span className="label">Pessoas</span>
          <span className="value">{ticket.guestCount}</span>
        </div>
      </div>

      <div className="detail-items">
        <h4>Itens</h4>
        <div className="items-list">
          {ticket.items.map((item) => {
            const actions = getActions(item.status);

            return (
              <div key={item.id} className={`item-row status-${item.status.toLowerCase()}`}>
                <div className="item-main">
                  <span className="item-name">{item.itemName}</span>
                  {item.specialInstructions && (
                    <span className="item-instructions">
                      Obs: {item.specialInstructions}
                    </span>
                  )}
                </div>
                <div className="item-meta">
                  <span className="item-qty">×{item.quantity}</span>
                  <span className="item-status">{item.status}</span>
                </div>

                {actions.length > 0 && (
                  <div className="item-actions">
                    {actions.map((btn) => (
                      <button
                        key={btn.action}
                        className={`item-action-btn action-${btn.action}`}
                        onClick={() => handleAction(item.id, btn.action)}
                        disabled={loading === item.id}
                        aria-label={btn.label}
                      >
                        {loading === item.id ? '...' : btn.label}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>

      {error && (
        <div className="detail-error">
          <span>{error}</span>
          <button className="error-close" onClick={() => setError(null)}>✕</button>
        </div>
      )}

      <div className="detail-actions">
        <button className="btn btn-secondary" onClick={onClose}>
          Fechar
        </button>
      </div>
    </div>
  );
}
