import { useState } from 'react';
import { describeError } from './diningRoomMessages';
import type { Tab } from './useTab';
import { useActiveTabs } from './useActiveTabs';
import { useMergeInto } from './useTabOperations';
import '../../styles/confirm-dialog.css';
import '../../styles/tab-operations.css';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
  readonly onDone: () => void;
}

const TEXT = {
  title: 'Juntar comandas',
  message:
    'Todos os itens desta comanda passam para a comanda escolhida, e esta mesa fica livre. Não há desfazer.',
  destination: 'Juntar com a comanda da mesa',
  choose: 'Escolha a mesa…',
  noDestination: 'Nenhuma outra comanda aberta.',
  cancel: 'Voltar',
  confirm: 'Juntar comandas',
  working: 'Juntando…',
} as const;

/**
 * This tab is absorbed into the chosen one, which stays open. Irreversible, so
 * the text says so and the button repeats the verb.
 */
export function MergeTabDialog({ isOpen, tab, onClose, onDone }: Props) {
  const [destinationTabId, setDestinationTabId] = useState('');
  const activeTabs = useActiveTabs();
  const merge = useMergeInto(tab.id);

  if (!isOpen) {
    return null;
  }

  const destinations = (activeTabs.data ?? []).filter(
    (candidate) => candidate.id !== tab.id && candidate.status === 'OPEN' && candidate.diningTableId,
  );
  const canMerge = destinationTabId !== '' && !merge.isPending;

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
            <label htmlFor="merge-destination" className="dialog-label">
              {TEXT.destination}
            </label>
            <select
              id="merge-destination"
              className="dialog-select"
              value={destinationTabId}
              onChange={(event) => setDestinationTabId(event.target.value)}
              disabled={merge.isPending || destinations.length === 0}
            >
              <option value="">{destinations.length === 0 ? TEXT.noDestination : TEXT.choose}</option>
              {destinations.map((candidate) => (
                <option key={candidate.id} value={candidate.id}>
                  {candidate.diningTableLabel}
                </option>
              ))}
            </select>
          </div>

          {merge.error && (
            <p className="dr-error-line" role="alert">
              {describeError(merge.error)}
            </p>
          )}
        </div>

        <div className="dialog-actions">
          <button
            type="button"
            className="btn btn-secondary"
            onClick={() => {
              setDestinationTabId('');
              onClose();
            }}
            disabled={merge.isPending}
          >
            {TEXT.cancel}
          </button>
          <button
            type="button"
            className="btn btn-primary"
            onClick={() => {
              if (!canMerge) return;
              merge.mutate(destinationTabId, { onSuccess: onDone });
            }}
            disabled={!canMerge}
          >
            {merge.isPending ? TEXT.working : TEXT.confirm}
          </button>
        </div>
      </div>
    </>
  );
}
