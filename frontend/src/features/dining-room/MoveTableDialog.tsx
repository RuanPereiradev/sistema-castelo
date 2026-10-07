import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { Tab } from './useTab';
import { useDiningTables } from './useDiningTables';
import { useMoveTab } from './useTabOperations';
import '../../styles/confirm-dialog.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
}

/**
 * Dialog pra trocar comanda de mesa.
 */
export function MoveTableDialog({ isOpen, tab, onClose }: Props) {
  const [destinationTableId, setDestinationTableId] = useState<string>('');
  const queryClient = useQueryClient();

  const { data: tables = [] } = useDiningTables();
  const { mutate: moveTab, isPending } = useMoveTab(tab.id);

  // Filtrar mesas livres (exceto a atual)
  const availableTables = tables.filter((t) => t.occupiedTabCount === 0 && t.id !== tab.diningTableId);

  function handleMove() {
    if (!destinationTableId) {
      return;
    }

    moveTab(destinationTableId, {
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

  const canMove = !!destinationTableId;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog confirm-dialog">
        <div className="dialog-header">
          <h3 className="dialog-title">Trocar de mesa</h3>
        </div>

        <div className="dialog-content">
          <p className="dialog-message">
            A comanda será movida para a mesa selecionada. Todos os itens virão com.
          </p>

          <div className="dialog-field">
            <label htmlFor="destination-table" className="dialog-label">
              Nova mesa
            </label>
            <select
              id="destination-table"
              value={destinationTableId}
              onChange={(e) => setDestinationTableId(e.target.value)}
              disabled={isPending}
              className="dialog-select"
            >
              <option value="">Selecione uma mesa...</option>
              {availableTables.length === 0 ? (
                <option disabled>Nenhuma mesa disponível</option>
              ) : (
                availableTables.map((table) => (
                  <option key={table.id} value={table.id}>
                    {table.label}
                  </option>
                ))
              )}
            </select>
          </div>
        </div>

        <div className="dialog-actions">
          <button className="btn btn-secondary" onClick={onClose} disabled={isPending}>
            Cancelar
          </button>
          <button
            className="btn btn-primary"
            onClick={handleMove}
            disabled={isPending || !canMove}
          >
            {isPending ? 'Movendo...' : 'Trocar de mesa'}
          </button>
        </div>
      </div>
    </>
  );
}
