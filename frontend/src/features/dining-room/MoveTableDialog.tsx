import { useState } from 'react';
import { describeError } from './diningRoomMessages';
import type { Tab } from './useTab';
import { useDiningTables } from './useDiningTables';
import { tabsByTable, useActiveTabs } from './useActiveTabs';
import { useMoveTab } from './useTabOperations';
import '../../styles/confirm-dialog.css';
import '../../styles/tab-operations.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
  /** Called with the label of the table the tab now sits on. */
  readonly onDone: (destinationLabel: string) => void;
}

const TEXT = {
  title: 'Trocar de mesa',
  message: 'A comanda inteira vai para a mesa escolhida, com todos os itens.',
  destination: 'Nova mesa',
  choose: 'Escolha a mesa…',
  noDestination: 'Nenhuma mesa livre.',
  cancel: 'Voltar',
  confirm: 'Trocar de mesa',
  working: 'Movendo…',
} as const;

/** Destination is any active table without an active tab. */
export function MoveTableDialog({ isOpen, tab, onClose, onDone }: Props) {
  const [diningTableId, setDiningTableId] = useState('');
  const tables = useDiningTables();
  const activeTabs = useActiveTabs();
  const move = useMoveTab(tab.id);

  if (!isOpen) {
    return null;
  }

  const occupied = tabsByTable(activeTabs.data ?? []);
  const freeTables = (tables.data ?? []).filter(
    (table) => table.isActive && table.id !== tab.diningTableId && !occupied.has(table.id),
  );
  const canMove = diningTableId !== '' && !move.isPending;

  return (
    <>
      <div className="dialog-overlay" onClick={onClose} />
      <div className="dialog confirm-dialog" role="dialog" aria-modal="true">
        <div className="dialog-header">
          <h3 className="dialog-title">{TEXT.title}</h3>
        </div>

        <div className="dialog-content">
          <p className="dialog-message">{TEXT.message}</p>

          <div className="dialog-field">
            <label htmlFor="move-destination" className="dialog-label">
              {TEXT.destination}
            </label>
            <select
              id="move-destination"
              className="dialog-select"
              value={diningTableId}
              onChange={(event) => setDiningTableId(event.target.value)}
              disabled={move.isPending || freeTables.length === 0}
            >
              <option value="">{freeTables.length === 0 ? TEXT.noDestination : TEXT.choose}</option>
              {freeTables.map((table) => (
                <option key={table.id} value={table.id}>
                  {table.label}
                </option>
              ))}
            </select>
          </div>

          {move.error && (
            <p className="dr-error-line" role="alert">
              {describeError(move.error)}
            </p>
          )}
        </div>

        <div className="dialog-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              setDiningTableId('');
              onClose();
            }}
            disabled={move.isPending}
          >
            {TEXT.cancel}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              if (!canMove) return;
              const destination = freeTables.find((table) => table.id === diningTableId);
              move.mutate(diningTableId, {
                onSuccess: () => onDone(destination?.label ?? ''),
              });
            }}
            disabled={!canMove}
          >
            {move.isPending ? TEXT.working : TEXT.confirm}
          </button>
        </div>
      </div>
    </>
  );
}
