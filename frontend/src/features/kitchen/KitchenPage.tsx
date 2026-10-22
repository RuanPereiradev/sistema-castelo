import { useEffect, useMemo, useState } from 'react';
import { useKitchenTickets, type KitchenItemStatus, type Station } from './useKitchenTickets';
import { useKitchenSocket } from './useKitchenSocket';
import { TicketCard } from './TicketCard';
import { KITCHEN } from './kitchenMessages';
import '../../styles/kitchen.css';

const STATIONS: readonly { station: Station; icon: string }[] = [
  { station: 'KITCHEN', icon: '👨‍🍳' },
  { station: 'PIZZA', icon: '🍕' },
  { station: 'BAR', icon: '🍷' },
];

const COLUMNS: readonly {
  status: KitchenItemStatus;
  title: string;
  icon: string;
  empty: string;
  className: string;
}[] = [
  { status: 'PENDING', title: KITCHEN.pending, icon: '⏳', empty: KITCHEN.noPending, className: 'pending-title' },
  {
    status: 'IN_PREPARATION',
    title: KITCHEN.inPreparation,
    icon: '👨‍🍳',
    empty: KITCHEN.noInPreparation,
    className: 'in-progress-title',
  },
  { status: 'READY', title: KITCHEN.ready, icon: '✓', empty: KITCHEN.noReady, className: 'ready-title' },
];

const REDRAW_EVERY_MS = 30_000;

/**
 * "Agora" pelo relógio do servidor: o `serverTime` da última leitura mais o tempo que
 * passou desde ela. A tela fica ligada por dias e o relógio do aparelho derrapa, então
 * ele só mede intervalo, nunca hora absoluta. Redesenha a cada 30s sem tocar o servidor.
 */
function useServerNow(serverTime: string | undefined): number | null {
  const offset = useMemo(
    () => (serverTime ? new Date(serverTime).getTime() - Date.now() : null),
    [serverTime],
  );
  const [, setTick] = useState(0);
  useEffect(() => {
    const timer = setInterval(() => setTick((tick) => tick + 1), REDRAW_EVERY_MS);
    return () => clearInterval(timer);
  }, []);
  return offset === null ? null : Date.now() + offset;
}

/** Tela da cozinha: um card por item, em colunas pelo status, por setor. */
export function KitchenPage() {
  const [station, setStation] = useState<Station>('KITCHEN');
  const { data, isLoading, isError, refetch } = useKitchenTickets(station);
  const { isConnected, reconnect } = useKitchenSocket({ station, enabled: true });
  const now = useServerNow(data?.serverTime);

  const items = data?.items ?? [];

  return (
    <div className="kitchen-container">
      <div className="kitchen-header">
        <div className="station-tabs">
          {STATIONS.map(({ station: option, icon }) => (
            <button
              key={option}
              className={`station-tab ${station === option ? 'active' : ''}`}
              onClick={() => setStation(option)}
            >
              {icon} {KITCHEN.stations[option]}
            </button>
          ))}
        </div>

        <div className={`connection-status ${isConnected ? 'connected' : 'disconnected'}`}>
          <span className="connection-dot" />
          {isConnected ? KITCHEN.connected : KITCHEN.disconnected}
          {!isConnected && (
            <button className="reconnect-btn" onClick={reconnect}>
              {KITCHEN.reconnect}
            </button>
          )}
        </div>
      </div>

      {isError && (
        <div className="kitchen-error" role="alert">
          <span>{KITCHEN.queueFailed}</span>
          <button className="reconnect-btn" onClick={() => void refetch()}>
            {KITCHEN.retry}
          </button>
        </div>
      )}

      <div className="kitchen-queues">
        {COLUMNS.map((column) => {
          const inColumn = items.filter((item) => item.status === column.status);
          return (
            <div key={column.status} className="queue-section">
              <h3 className={`queue-title ${column.className}`}>
                {column.icon} {column.title} <span className="queue-count">{inColumn.length}</span>
              </h3>
              <div className="queue-tickets">
                {isLoading && <p className="loading">{KITCHEN.loading}</p>}
                {!isLoading && !isError && inColumn.length === 0 && (
                  <p className="empty-queue">{column.empty}</p>
                )}
                {data &&
                  inColumn.map((ticket) => (
                    <TicketCard
                      key={ticket.itemId}
                      ticket={ticket}
                      minutesWaiting={
                        now === null
                          ? 0
                          : Math.max(0, Math.floor((now - new Date(ticket.orderedAt).getTime()) / 60_000))
                      }
                      warningAfterMinutes={data.warningAfterMinutes}
                      lateAfterMinutes={data.lateAfterMinutes}
                    />
                  ))}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}
