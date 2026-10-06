import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Tab } from './useTab';
import { useDiningTables } from './useDiningTables';
import { useTransferItems } from './useTabOperations';
import '../../styles/confirm-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
}

/**
 * Dialog pra transferir itens de uma comanda pra outra.
 */
export function TransferItemsDialog({ isOpen, tab, onClose }: Props) {
  const [selectedItems, setSelectedItems] = useState<Set<string>>(new Set());
  const [destinationTableId, setDestinationTableId] = useState<string>('');
  const queryClient = useQueryClient();

  const { data: tables = [] } = useDiningTables();
  const { mutate: transferItems, isPending } = useTransferItems(tab.id);

  // Filtrar mesas ocupadas (exceto a atual)
  const availableTables = tables.filter((t) => t.occupiedTabCount > 0 && t.id !== tab.diningTableId);

  function toggleItem(itemId: string) {
    const newSelected = new Set(selectedItems);
    if (newSelected.has(itemId)) {
      newSelected.delete(itemId);
    } else {
      newSelected.add(itemId);
    }
    setSelectedItems(newSelected);
  }

  function handleTransfer() {
    if (selectedItems.size === 0 || !destinationTableId) {
      return;
    }

    transferItems(
      {
        itemIds: Array.from(selectedItems),
        destinationTabId: destinationTableId,
      },
      {
        onSuccess: () => {
          setSelectedItems(new Set());
          setDestinationTableId('');
          onClose();
          queryClient.invalidateQueries({ queryKey: ['dining-table-tab'] });
          queryClient.invalidateQueries({ queryKey: ['dining-tables'] });
        },
      },
    );
  }

  if (!isOpen) {
    return null;
  }

  const canTransfer = selectedItems.size > 0 && destinationTableId;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog confirm-dialog">
        <div className="dialog-header">
          <h3 className="dialog-title">Transferir itens</h3>
        </div>

        <div className="dialog-content">
          {/* Seleção de itens */}
          <div className="dialog-field">
            <label className="dialog-label">Itens a transferir</label>
            <div className="items-selection">
              {tab.items.length === 0 ? (
                <p className="empty-text">Nenhum item na comanda</p>
              ) : (
                <div className="items-list-transfer">
                  {tab.items.map((item) => (
                    <label key={item.id} className="item-checkbox">
                      <input
                        type="checkbox"
                        checked={selectedItems.has(item.id)}
                        onChange={() => toggleItem(item.id)}
                        disabled={isPending}
                      />
                      <span className="item-label">
                        {item.itemName} ({item.quantity}x)
                      </span>
                    </label>
                  ))}
                </div>
              )}
            </div>
            {selectedItems.size > 0 && (
              <div className="selection-count">{selectedItems.size} item(ns) selecionado(s)</div>
            )}
          </div>

          {/* Seleção de mesa de destino */}
          <div className="dialog-field">
            <label htmlFor="destination-table" className="dialog-label">
              Mesa de destino
            </label>
            <select
              id="destination-table"
              value={destinationTableId}
              onChange={(e) => setDestinationTableId(e.target.value)}
              disabled={isPending}
              className="dialog-select"
            >
              <option value="">Selecione uma mesa...</option>
              {availableTables.map((table) => (
                <option key={table.id} value={table.id}>
                  {table.label}
                </option>
              ))}
            </select>
          </div>
        </div>

        <div className="dialog-actions">
          <button className="btn btn-secondary" onClick={onClose} disabled={isPending}>
            Cancelar
          </button>
          <button
            className="btn btn-primary"
            onClick={handleTransfer}
            disabled={isPending || !canTransfer}
          >
            {isPending ? 'Transferindo...' : 'Transferir'}
          </button>
        </div>
      </div>
    </>
  );
}
