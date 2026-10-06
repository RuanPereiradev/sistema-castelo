import type { KitchenTicket } from './useKitchenTickets';

interface Props {
  readonly ticket: KitchenTicket;
  readonly onClose: () => void;
}

/**
 * Detalhe de um ticket da cozinha.
 */
export function TicketDetail({ ticket, onClose }: Props) {
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
          {ticket.items.map((item) => (
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
            </div>
          ))}
        </div>
      </div>

      <div className="detail-actions">
        <button
          className="btn btn-secondary"
          onClick={onClose}
        >
          Fechar
        </button>
      </div>
    </div>
  );
}
