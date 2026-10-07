import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Tab } from './useTab';
import { useDiningTables } from './useDiningTables';
import { useMergeTab } from './useTabOperations';
import '../../styles/confirm-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
}

/**
 * Dialog pra juntar uma comanda com outra.
 */
export function MergeTabDialog({ isOpen, tab, onClose }: Props) {
  const [destinationTableId, setDestinationTableId] = useState<string>('');
  const queryClient = useQueryClient();

  const { data: tables = [] } = useDiningTables();
  const { mutate: mergeTab, isPending } = useMergeTab(tab.id);

  // Filtrar mesas ocupadas (exceto a atual)
  const availableTables = tables.filter((t) => t.occupiedTabCount > 0 && t.id !== tab.diningTableId);

  function handleMerge() {
    if (!destinationTableId) {
      return;
    }

    mergeTab(destinationTableId, {
      onSuccess: () => {
        setDestinationTableId('');
        onClose();
        queryClient.invalidateQueries({ queryKey: ['dining-table-tab'] });
        queryClient.invalidateQueries({ queryKey: ['dining-tables'] });
      },
    });
  }

  if (!isOpen) {
    return null;
  }

  const canMerge = !!destinationTableId;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog confirm-dialog">
        <div className="dialog-header">
          <h3 className="dialog-title">Juntar comandas</h3>
        </div>

        <div className="dialog-content">
          <p className="dialog-message">
            Todos os itens desta comanda serão movidos para a comanda de destino.
          </p>

          <div className="dialog-field">
            <label htmlFor="destination-table" className="dialog-label">
              Juntar com comanda em
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
            onClick={handleMerge}
            disabled={isPending || !canMerge}
          >
            {isPending ? 'Juntando...' : 'Juntar comandas'}
          </button>
        </div>
      </div>
    </>
  );
}
