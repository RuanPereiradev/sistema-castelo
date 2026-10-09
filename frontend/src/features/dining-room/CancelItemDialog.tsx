import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { describeError } from './diningRoomMessages';
import { tabKey } from './useTab';
import type { Tab } from './useTab';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly itemId: string;
  readonly itemName: string;
  readonly onClose: () => void;
  readonly onCancelled: () => void;
}

const REASONS = ['UNAVAILABLE', 'CLIENT_REQUEST', 'KITCHEN_ERROR'] as const;

const REASON_LABELS: Record<(typeof REASONS)[number], string> = {
  UNAVAILABLE: 'Indisponível',
  CLIENT_REQUEST: 'Cliente desistiu',
  KITCHEN_ERROR: 'Erro da cozinha',
};

export function CancelItemDialog({
  isOpen,
  tab,
  itemId,
  itemName,
  onClose,
  onCancelled,
}: Props) {
  const queryClient = useQueryClient();
  const apiCall = useAuthorizedRequest();
  const [selectedReason, setSelectedReason] = useState<(typeof REASONS)[number]>('UNAVAILABLE');
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  async function handleCancel() {
    setIsLoading(true);
    try {
      await apiCall<unknown>(
        `/restaurant/tabs/${tab.id}/items/${itemId}/cancel`,
        {
          method: 'POST',
          body: { reason: selectedReason },
        },
      );
      await queryClient.invalidateQueries({ queryKey: tabKey(tab.id) });
      onCancelled();
    } catch (error) {
      alert(describeError(error));
    } finally {
      setIsLoading(false);
    }
  }

  return (
    <>
      <div className="dr-modal-backdrop" onClick={onClose} />
      <div className="dr-sheet" role="dialog" aria-modal="true">
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <h2 className="dr-sheet-title">Cancelar item</h2>
            <button
              type="button"
              className="dr-sheet-close"
              onClick={onClose}
              aria-label="Fechar"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
                <path d="M18 6 6 18" />
                <path d="m6 6 12 12" />
              </svg>
            </button>
          </div>

          <div className="dr-divider">
            <span className="dr-divider-line" />
            <span className="dr-divider-ornament">❧</span>
            <span className="dr-divider-line" />
          </div>

          <div className="dr-sheet-facts">
            <span>
              <span className="dr-fact-label">Produto</span> {itemName}
            </span>
          </div>

          <div style={{ padding: '12px 0' }}>
            <label style={{ display: 'block', marginBottom: '12px', fontSize: '13.5px', fontWeight: 600 }}>
              Motivo:
            </label>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {REASONS.map((reason) => (
                <label
                  key={reason}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    cursor: 'pointer',
                    padding: '8px',
                    borderRadius: '4px',
                    background: selectedReason === reason ? 'rgba(232, 149, 127, 0.15)' : 'transparent',
                  }}
                >
                  <input
                    type="radio"
                    name="reason"
                    value={reason}
                    checked={selectedReason === reason}
                    onChange={(e) => setSelectedReason(e.target.value as (typeof REASONS)[number])}
                    style={{ cursor: 'pointer' }}
                  />
                  <span>{REASON_LABELS[reason]}</span>
                </label>
              ))}
            </div>
          </div>

          <div className="dr-sheet-actions">
            <button
              type="button"
              className="dr-action-btn dr-action-primary"
              onClick={() => void handleCancel()}
              disabled={isLoading}
              style={{ color: '#f87171' }}
            >
              {isLoading ? 'Cancelando...' : '✕ Cancelar item'}
            </button>
            <button type="button" className="dr-action-btn dr-action-ghost" onClick={onClose}>
              Voltar
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
