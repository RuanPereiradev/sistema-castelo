import { formatDistanceToNow } from 'date-fns';
import { pt } from 'date-fns/locale';
import type { KitchenTicket } from './useKitchenTickets';

interface Props {
  readonly ticket: KitchenTicket;
  readonly isSelected: boolean;
  readonly onSelect: () => void;
  readonly priority: 'pending' | 'in-progress' | 'ready';
  readonly serverTime?: string;
  readonly warningAfterMinutes?: number;
  readonly lateAfterMinutes?: number;
}

/**
 * Card de um ticket (pedido) da cozinha.
 * Mostra indicadores de warning (amarelo) e late (vermelho) baseado no tempo.
 */
export function TicketCard({
  ticket,
  isSelected,
  onSelect,
  priority,
  serverTime,
  warningAfterMinutes = 20,
  lateAfterMinutes = 30,
}: Props) {
  const itemCount = ticket.items.length;
  const readyItems = ticket.items.filter((i) => i.status === 'READY').length;

  // Calcular tempo decorrido
  let timeStatus = '';
  if (serverTime && ticket.openedAt) {
    const now = new Date(serverTime).getTime();
    const opened = new Date(ticket.openedAt).getTime();
    const minutesElapsed = (now - opened) / 1000 / 60;

    if (minutesElapsed >= lateAfterMinutes) {
      timeStatus = 'late';
    } else if (minutesElapsed >= warningAfterMinutes) {
      timeStatus = 'warning';
    }
  }

  return (
    <button
      className={`ticket-card ${isSelected ? 'selected' : ''} priority-${priority} ${timeStatus ? `time-status-${timeStatus}` : ''}`}
      onClick={onSelect}
    >
      <div className="ticket-header">
        <span className="ticket-table">{ticket.tableLabel}</span>
        <span className={`ticket-time ${timeStatus}`}>
          {formatDistanceToNow(new Date(ticket.openedAt), { locale: pt, addSuffix: false })}
          {timeStatus === 'late' && ' ⚠️'}
          {timeStatus === 'warning' && ' ⏰'}
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
