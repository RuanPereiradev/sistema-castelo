import { useState } from 'react';
import { describeError } from './diningRoomMessages';
import { activeItems, type Tab } from './useTab';
import { useActiveTabs } from './useActiveTabs';
import { useTransferItems } from './useTabOperations';
import '../../styles/confirm-dialog.css';
import '../../styles/tab-operations.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
  readonly onDone: () => void;
}

const TEXT = {
  title: 'Transferir itens',
  items: 'Itens a transferir',
  noItems: 'Nenhum item ativo na comanda.',
  selected: (count: number) => `${count} ${count === 1 ? 'item selecionado' : 'itens selecionados'}`,
  destination: 'Para a comanda da mesa',
  choose: 'Escolha a mesa…',
  noDestination: 'Nenhuma outra comanda aberta.',
  cancel: 'Voltar',
  confirm: 'Transferir',
  working: 'Transferindo…',
} as const;

/** Whole lines of this tab go to another open tab, chosen by its table. */
export function TransferItemsDialog({ isOpen, tab, onClose, onDone }: Props) {
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [toTabId, setToTabId] = useState('');
  const activeTabs = useActiveTabs();
  const transfer = useTransferItems(tab.id);

  if (!isOpen) {
    return null;
  }

  const destinations = (activeTabs.data ?? []).filter(
    (candidate) => candidate.id !== tab.id && candidate.status === 'OPEN' && candidate.diningTableId,
  );
  const lines = activeItems(tab);
  const canTransfer = selected.size > 0 && toTabId !== '' && !transfer.isPending;

  function toggle(itemId: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(itemId)) {
        next.delete(itemId);
      } else {
        next.add(itemId);
      }
      return next;
    });
  }

  function reset() {
    setSelected(new Set());
    setToTabId('');
  }

  function handleTransfer() {
    if (!canTransfer) return;
    transfer.mutate(
      { itemIds: [...selected], toTabId },
      {
        onSuccess: () => {
          reset();
          onDone();
        },
      },
    );
  }

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog confirm-dialog" role="dialog" aria-modal="true">
        <div className="dialog-header">
          <h3 className="dialog-title">{TEXT.title}</h3>
        </div>

        <div className="dialog-content">
          <div className="dialog-field">
            <span className="dialog-label">{TEXT.items}</span>
            {lines.length === 0 ? (
              <p className="empty-text">{TEXT.noItems}</p>
            ) : (
              <div className="items-list-transfer">
                {lines.map((item) => (
                  <label key={item.id} className="item-checkbox">
                    <input
                      type="checkbox"
                      checked={selected.has(item.id)}
                      onChange={() => toggle(item.id)}
                      disabled={transfer.isPending}
                    />
                    <span className="item-label">
                      {item.quantity}× {item.itemName}
                      {item.variantName && ` (${item.variantName})`}
                    </span>
                  </label>
                ))}
              </div>
            )}
            {selected.size > 0 && <div className="selection-count">{TEXT.selected(selected.size)}</div>}
          </div>

          <div className="dialog-field">
            <label htmlFor="transfer-destination" className="dialog-label">
              {TEXT.destination}
            </label>
            <select
              id="transfer-destination"
              className="dialog-select"
              value={toTabId}
              onChange={(event) => setToTabId(event.target.value)}
              disabled={transfer.isPending || destinations.length === 0}
            >
              <option value="">{destinations.length === 0 ? TEXT.noDestination : TEXT.choose}</option>
              {destinations.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.diningTableLabel}
                </option>
              ))}
            </select>
          </div>

          {transfer.error && (
            <p className="dr-error-line" role="alert">
              {describeError(transfer.error)}
            </p>
          )}
        </div>

        <div className="dialog-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              reset();
              onClose();
            }}
            disabled={transfer.isPending}
          >
            {TEXT.cancel}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleTransfer}
            disabled={!canTransfer}
          >
            {transfer.isPending ? TEXT.working : TEXT.confirm}
          </button>
        </div>
      </div>
    </>
  );
}
