import { formatDistanceToNow } from 'date-fns';
import { pt } from 'date-fns/locale';
import type { KitchenTicket } from './useKitchenTickets';

interface Props {
  readonly ticket: KitchenTicket;
  readonly isSelected: boolean;
  readonly onSelect: () => void;
  readonly priority: 'pending' | 'in-progress' | 'ready';
}

/**
 * Card de um ticket (pedido) da cozinha.
 */
export function TicketCard({ ticket, isSelected, onSelect, priority }: Props) {
  const itemCount = ticket.items.length;
  const readyItems = ticket.items.filter((i) => i.status === 'READY').length;

  return (
    <button
      className={`ticket-card ${isSelected ? 'selected' : ''} priority-${priority}`}
      onClick={onSelect}
    >
      <div className="ticket-header">
        <span className="ticket-table">{ticket.tableLabel}</span>
        <span className="ticket-time">
          {formatDistanceToNow(new Date(ticket.openedAt), { locale: pt })}
        </span>
      </div>

      <div className="ticket-info">
        <span className="guest-count">👥 {ticket.guestCount} pessoas</span>
        <span className="item-count">
          {readyItems}/{itemCount} pronto
        </span>
      </div>

      <div className="ticket-items-preview">
        {ticket.items.slice(0, 2).map((item) => (
          <div key={item.id} className={`preview-item status-${item.status.toLowerCase()}`}>
            {item.quantity}x {item.itemName}
          </div>
        ))}
        {itemCount > 2 && <div className="preview-more">+{itemCount - 2} itens</div>}
      </div>
    </button>
  );
}
