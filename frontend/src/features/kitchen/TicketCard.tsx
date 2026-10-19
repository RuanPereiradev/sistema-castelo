import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { KITCHEN, describeKitchenError } from './kitchenMessages';
import type { KitchenItemStatus, KitchenTicket } from './useKitchenTickets';

type Action = 'start' | 'ready' | 'undo';

/** K2 lets a pending item go straight to ready; K3 undoes one step. */
const ACTIONS: Record<KitchenItemStatus, readonly { action: Action; label: string }[]> = {
  PENDING: [
    { action: 'start', label: KITCHEN.start },
    { action: 'ready', label: KITCHEN.markReady },
  ],
  IN_PREPARATION: [
    { action: 'ready', label: KITCHEN.markReady },
    { action: 'undo', label: KITCHEN.undo },
  ],
  READY: [{ action: 'undo', label: KITCHEN.undo }],
};

interface Props {
  readonly ticket: KitchenTicket;
  /** Minutes since the item was ordered, measured against the server clock. */
  readonly minutesWaiting: number;
  readonly warningAfterMinutes: number;
  readonly lateAfterMinutes: number;
}

/** One item of the queue, with the buttons that move it. */
export function TicketCard({ ticket, minutesWaiting, warningAfterMinutes, lateAfterMinutes }: Props) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const timeStatus =
    ticket.status === 'READY'
      ? ''
      : minutesWaiting >= lateAfterMinutes
        ? 'late'
        : minutesWaiting >= warningAfterMinutes
          ? 'warning'
          : '';

  const where =
    ticket.origin === 'SELF_SERVICE'
      ? `${KITCHEN.card} ${ticket.cardNumber ?? ''}`
      : (ticket.diningTableLabel ?? KITCHEN.counter);

  const run = async (action: Action) => {
    setWorking(true);
    setError(null);
    try {
      await apiCall(`/kitchen/items/${ticket.itemId}/${action}`, { method: 'POST' });
    } catch (failure) {
      setError(describeKitchenError(failure));
    } finally {
      setWorking(false);
      // Success or conflict, the server's queue is the truth.
      void queryClient.invalidateQueries({ queryKey: ['kitchen-tickets', ticket.prepStation] });
    }
  };

  return (
    <article
      className={`ticket-card priority-${ticket.status.toLowerCase().replace('_', '-')} ${
        timeStatus ? `time-status-${timeStatus}` : ''
      }`}
    >
      <div className="ticket-header">
        <span className="ticket-table">{where}</span>
        <span className={`ticket-time ${timeStatus}`}>
          {minutesWaiting} {KITCHEN.minutes}
          {timeStatus === 'late' && ' ⚠️'}
          {timeStatus === 'warning' && ' ⏰'}
        </span>
      </div>

      <div className="ticket-item">
        <span className="item-qty">{ticket.quantity}×</span>{' '}
        <span className="item-name">
          {ticket.itemName}
          {ticket.variantName && ` (${ticket.variantName})`}
        </span>
      </div>

      {ticket.modifiers.length > 0 && (
        <ul className="ticket-modifiers">
          {ticket.modifiers.map((modifier) => (
            <li key={modifier.name}>
              + {modifier.quantity > 1 ? `${modifier.quantity}× ` : ''}
              {modifier.name}
            </li>
          ))}
        </ul>
      )}

      {ticket.specialInstructions && (
        <p className="item-instructions">
          {KITCHEN.notes} {ticket.specialInstructions}
        </p>
      )}

      <div className="item-actions">
        {ACTIONS[ticket.status].map((button) => (
          <button
            key={button.action}
            className={`item-action-btn action-${button.action}`}
            onClick={() => void run(button.action)}
            disabled={working}
          >
            {working ? KITCHEN.working : button.label}
          </button>
        ))}
      </div>

      {error && (
        <p className="ticket-error" role="alert">
          {error}
        </p>
      )}
    </article>
  );
}
