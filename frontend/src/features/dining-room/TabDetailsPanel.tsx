import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { DiningTable } from './useDiningTables';
import { useOpenTab } from './useTab';
import { useDiningTableTab } from './useDiningTableTab';
import { AddItemModal } from './AddItemModal';
import { ConfirmDialog } from './ConfirmDialog';
import { TabBillModal } from './TabBillModal';
import { useCancelTab, useGetTabBill } from './useTabActions';
import { formatDistanceToNow } from 'date-fns';
import { pt } from 'date-fns/locale';

interface Props {
  readonly table: DiningTable;
}

const STATUS_LABELS: Record<string, string> = {
  PENDING: 'Pendente',
  IN_PREPARATION: 'Em preparo',
  READY: 'Pronto',
  DELIVERED: 'Servido',
  CANCELLED: 'Cancelado',
};

/**
 * Painel lateral: mostra comanda aberta ou formulário pra abrir.
 * Fase 2: detalhe completo da comanda com polling automático.
 */
export function TabDetailsPanel({ table }: Props) {
  const [guestCount, setGuestCount] = useState(1);
  const [isAddItemOpen, setIsAddItemOpen] = useState(false);
  const [isCancelTabOpen, setIsCancelTabOpen] = useState(false);
  const [cancelTabReason, setCancelTabReason] = useState('');
  const [isBillOpen, setIsBillOpen] = useState(false);
  const queryClient = useQueryClient();

  const isOccupied = table.occupiedTabCount > 0;
  const { data: tab, isLoading: loadingTab, error: errorTab } = useDiningTableTab(
    table.id,
    isOccupied,
  );

  const { mutate: openTab, isPending: opening } = useOpenTab();
  const { mutate: cancelTab, isPending: cancelingTab } = useCancelTab(tab?.id || '');
  const { mutate: getBill, isPending: loadingBill, data: bill } = useGetTabBill(tab?.id || '');

  function handleOpenTab(e: React.FormEvent) {
    e.preventDefault();
    if (guestCount < 1) return;

    openTab(
      { diningTableId: table.id, origin: 'TABLE_SERVICE', guestCount },
      {
        onSuccess: (newTab) => {
          queryClient.setQueryData(['dining-table-tab', table.id], newTab);
          queryClient.invalidateQueries({ queryKey: ['dining-tables'] });
        },
      },
    );
  }

  if (!isOccupied) {
    // Mesa livre: formulário de abertura
    return (
      <div className="tab-panel">
        <h3>Abrir Comanda</h3>
        <form onSubmit={handleOpenTab} className="open-tab-form">
          <label>
            Número de pessoas:
            <input
              type="number"
              min="1"
              max="50"
              value={guestCount}
              onChange={(e) => setGuestCount(Math.max(1, parseInt(e.target.value, 10)))}
              disabled={opening}
              required
            />
          </label>
          <button type="submit" disabled={opening}>
            {opening ? 'Abrindo...' : 'Abrir'}
          </button>
        </form>
      </div>
    );
  }

  // Mesa ocupada: carregando
  if (loadingTab) {
    return <div className="tab-panel loading-state">Carregando comanda...</div>;
  }

  if (errorTab) {
    return (
      <div className="tab-panel error-state">
        <p>Erro ao carregar comanda</p>
        <p className="error-detail">{errorTab instanceof Error ? errorTab.message : String(errorTab)}</p>
      </div>
    );
  }

  if (!tab) {
    return <div className="tab-panel empty-state">Nenhuma comanda encontrada</div>;
  }

  // Fase 2: Detalhe completo da comanda
  const timeOpen = formatDistanceToNow(new Date(tab.createdAt), { locale: pt, addSuffix: true });
  const pendingCount = tab.items.filter((i) => i.status === 'PENDING').length;
  const readyCount = tab.items.filter((i) => i.status === 'READY').length;

  return (
    <div className="tab-panel">
      <div className="tab-panel-header">
        <h3>{table.label}</h3>
        <span className={`tab-status-badge status-${tab.status.toLowerCase()}`}>{tab.status}</span>
      </div>

      <div className="tab-info-grid">
        <div className="tab-info-item">
          <span className="label">Pessoas</span>
          <span className="value">{tab.guestCount}</span>
        </div>
        <div className="tab-info-item">
          <span className="label">Aberta</span>
          <span className="value">{timeOpen}</span>
        </div>
        <div className="tab-info-item">
          <span className="label">Pendente</span>
          <span className={`value ${pendingCount > 0 ? 'alert' : ''}`}>{pendingCount}</span>
        </div>
        <div className="tab-info-item">
          <span className="label">Pronto</span>
          <span className={`value ${readyCount > 0 ? 'success' : ''}`}>{readyCount}</span>
        </div>
      </div>

      <div className="tab-items">
        {tab.items.length === 0 ? (
          <p className="empty-message">Nenhum item lançado</p>
        ) : (
          <div className="items-list">
            {tab.items.map((item) => (
              <div key={item.id} className={`item-row status-${item.status.toLowerCase()}`}>
                <div className="item-main">
                  <div className="item-title">
                    <strong>{item.itemName}</strong>
                    {item.variantName && <span className="variant">({item.variantName})</span>}
                  </div>
                  {item.modifierChoices.length > 0 && (
                    <div className="item-modifiers">
                      {item.modifierChoices.map((m) => (
                        <span key={m.id} className="modifier-tag">
                          {m.modifierName} ×{m.quantity}
                        </span>
                      ))}
                    </div>
                  )}
                </div>
                <div className="item-meta">
                  <span className="quantity">×{item.quantity}</span>
                  <span className="price">{item.price}</span>
                  <span className="status-badge">{STATUS_LABELS[item.status] || item.status}</span>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="tab-total-section">
        <div className="tab-total">
          <span>Total</span>
          <strong>{tab.total}</strong>
        </div>
      </div>

      <div className="tab-actions">
        <button className="btn-primary" onClick={() => setIsAddItemOpen(true)}>
          + Lançar item
        </button>
        <button
          className="btn-secondary"
          onClick={() => {
            setIsBillOpen(true);
            getBill();
          }}
          disabled={loadingBill}
        >
          📋 Pré-conta
        </button>
        <button className="btn-secondary" disabled>
          🔄 Transferir
        </button>
        <button
          className="btn-danger"
          onClick={() => setIsCancelTabOpen(true)}
        >
          ❌ Cancelar
        </button>
      </div>

      {/* Modal de adicionar item */}
      <AddItemModal
        tabId={tab.id}
        isOpen={isAddItemOpen}
        onClose={() => setIsAddItemOpen(false)}
      />

      {/* Diálogo de cancelar item (quando clicar no item) */}
      {/* TODO: Implementar cancel item ao clicar em item específico */}

      {/* Diálogo de cancelar comanda */}
      <ConfirmDialog
        isOpen={isCancelTabOpen}
        title="Cancelar comanda?"
        message="Ao cancelar, a comanda será fechada e nenhum outro garçom poderá adicionar itens."
        requiresReason
        reason={cancelTabReason}
        onReasonChange={setCancelTabReason}
        onConfirm={() => {
          cancelTab(cancelTabReason, {
            onSuccess: () => {
              setIsCancelTabOpen(false);
              setCancelTabReason('');
              queryClient.invalidateQueries({ queryKey: ['dining-table-tab'] });
              queryClient.invalidateQueries({ queryKey: ['dining-tables'] });
            },
          });
        }}
        onCancel={() => {
          setIsCancelTabOpen(false);
          setCancelTabReason('');
        }}
        isLoading={cancelingTab}
        confirmLabel="Cancelar comanda"
        isDanger
      />

      {/* Modal de pré-conta */}
      <TabBillModal
        isOpen={isBillOpen && !!bill}
        bill={bill || null}
        isLoading={loadingBill}
        onClose={() => setIsBillOpen(false)}
      />
    </div>
  );
}
