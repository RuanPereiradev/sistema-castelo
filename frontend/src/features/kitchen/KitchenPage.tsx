import { useState } from 'react';
import { useKitchenTickets } from './useKitchenTickets';
import { useKitchenSocket } from './useKitchenSocket';
import { TicketCard } from './TicketCard';
import { TicketDetail } from './TicketDetail';
import '../../styles/kitchen.css';

type Station = 'KITCHEN' | 'PIZZA' | 'BAR';

/**
 * Tela de Cozinha (KITCHEN) com WebSocket em tempo real.
 * Fila de pedidos por status, atualização via STOMP.
 * Três setores independentes (KITCHEN, PIZZA, BAR).
 */
export function KitchenPage() {
  const [selectedTicketId, setSelectedTicketId] = useState<string | null>(null);
  const [station, setStation] = useState<Station>('KITCHEN');

  // Dados e WebSocket
  const { data: tickets = [], isLoading } = useKitchenTickets();
  const { isConnected, reconnect } = useKitchenSocket({ station, enabled: true });

  const selectedTicket = tickets.find((t) => t.id === selectedTicketId);

  // Agrupar por status
  const pending = tickets.filter((t) => t.items.some((i) => i.status === 'PENDING'));
  const inProgress = tickets.filter((t) => t.items.some((i) => i.status === 'IN_PREPARATION'));
  const ready = tickets.filter((t) => t.items.every((i) => i.status === 'READY'));

  return (
    <div className="kitchen-container">
      {/* Cabeçalho com seleção de setor */}
      <div className="kitchen-header">
        <div className="station-tabs">
          {(['KITCHEN', 'PIZZA', 'BAR'] as const).map((s) => (
            <button
              key={s}
              className={`station-tab ${station === s ? 'active' : ''}`}
              onClick={() => setStation(s)}
            >
              {s === 'KITCHEN' && '👨‍🍳'}
              {s === 'PIZZA' && '🍕'}
              {s === 'BAR' && '🍷'}
              {' '}
              {s === 'KITCHEN' ? 'Cozinha' : s === 'PIZZA' ? 'Pizza' : 'Bar'}
            </button>
          ))}
        </div>

        {/* Status de conexão */}
        <div className={`connection-status ${isConnected ? 'connected' : 'disconnected'}`}>
          <span className="connection-dot" />
          {isConnected ? 'Conectado' : 'Desconectado'}
          {!isConnected && (
            <button className="reconnect-btn" onClick={reconnect}>
              Reconectar
            </button>
          )}
        </div>
      </div>

      {/* Fila por status */}
      <div className="kitchen-queues">
        {/* Pendentes */}
        <div className="queue-section">
          <h3 className="queue-title pending-title">
            ⏳ Pendentes <span className="queue-count">{pending.length}</span>
          </h3>
          <div className="queue-tickets">
            {isLoading && <p className="loading">Carregando...</p>}
            {!isLoading && pending.length === 0 && (
              <p className="empty-queue">Sem pedidos pendentes</p>
            )}
            {pending.map((ticket) => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                isSelected={selectedTicketId === ticket.id}
                onSelect={() => setSelectedTicketId(ticket.id)}
                priority="pending"
              />
            ))}
          </div>
        </div>

        {/* Em preparo */}
        <div className="queue-section">
          <h3 className="queue-title in-progress-title">
            👨‍🍳 Em preparo <span className="queue-count">{inProgress.length}</span>
          </h3>
          <div className="queue-tickets">
            {inProgress.length === 0 && (
              <p className="empty-queue">Nenhum em preparo</p>
            )}
            {inProgress.map((ticket) => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                isSelected={selectedTicketId === ticket.id}
                onSelect={() => setSelectedTicketId(ticket.id)}
                priority="in-progress"
              />
            ))}
          </div>
        </div>

        {/* Prontos */}
        <div className="queue-section">
          <h3 className="queue-title ready-title">
            ✓ Prontos <span className="queue-count">{ready.length}</span>
          </h3>
          <div className="queue-tickets">
            {ready.length === 0 && (
              <p className="empty-queue">Nenhum pronto</p>
            )}
            {ready.map((ticket) => (
              <TicketCard
                key={ticket.id}
                ticket={ticket}
                isSelected={selectedTicketId === ticket.id}
                onSelect={() => setSelectedTicketId(ticket.id)}
                priority="ready"
              />
            ))}
          </div>
        </div>
      </div>

      {/* Detalhe do ticket */}
      {selectedTicket && (
        <TicketDetail
          ticket={selectedTicket}
          onClose={() => setSelectedTicketId(null)}
        />
      )}
    </div>
  );
}
