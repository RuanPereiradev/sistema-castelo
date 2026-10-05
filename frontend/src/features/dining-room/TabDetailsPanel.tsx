import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { DiningTable } from './useDiningTables';
import { useTab, useOpenTab } from './useTab';

interface Props {
  readonly table: DiningTable;
}

/**
 * Painel lateral: mostra comanda aberta ou formulário pra abrir.
 * Polling automático atualiza os itens conforme outro garçom mexe.
 */
export function TabDetailsPanel({ table }: Props) {
  const [guestCount, setGuestCount] = useState(1);
  const queryClient = useQueryClient();

  // TODO: Buscar comanda aberta na mesa quando mesa ocupada
  const { data: tab, isLoading: loadingTab } = useTab(null);

  const { mutate: openTab, isPending: opening } = useOpenTab();

  function handleOpenTab(e: React.FormEvent) {
    e.preventDefault();
    if (guestCount < 1) return;

    openTab(
      { diningTableId: table.id, origin: 'TABLE_SERVICE', guestCount },
      {
        onSuccess: (newTab) => {
          queryClient.setQueryData(['tab', newTab.id], newTab);
          queryClient.invalidateQueries({ queryKey: ['dining-tables'] });
        },
      },
    );
  }

  if (table.occupiedTabCount === 0) {
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

  // Mesa ocupada: mostrar comanda (TODO: buscar comanda real)
  if (loadingTab) {
    return <div className="tab-panel">Carregando comanda...</div>;
  }

  if (tab) {
    return (
      <div className="tab-panel">
        <h3>{table.label}</h3>
        <div className="tab-header">
          <span>{tab.guestCount} pessoas</span>
          <span className="tab-status">{tab.status}</span>
        </div>
        <div className="tab-items">
          {tab.items.length === 0 ? (
            <p className="empty">Nenhum item lançado</p>
          ) : (
            <ul>
              {tab.items.map((item) => (
                <li key={item.id} className={`item item-${item.status.toLowerCase()}`}>
                  <span className="item-name">{item.itemName}</span>
                  {item.variantName && <span className="item-variant">{item.variantName}</span>}
                  <span className="item-qty">x{item.quantity}</span>
                  <span className="item-price">{item.price}</span>
                  <span className="item-status">{item.status}</span>
                </li>
              ))}
            </ul>
          )}
        </div>
        <div className="tab-total">
          <strong>Total: {tab.total}</strong>
        </div>
        <div className="tab-actions">
          <button className="btn-secondary">Lançar item</button>
          <button className="btn-secondary">Solicitar conta</button>
          <button className="btn-danger">Cancelar comanda</button>
        </div>
      </div>
    );
  }

  return null;
}
