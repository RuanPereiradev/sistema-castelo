import { useState } from 'react';
import { formatMoney } from '../../lib/money';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { describeError } from './diningRoomMessages';
import { tabKey } from './useTab';
import type { Tab } from './useTab';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
  readonly onBillRequested: () => void;
}

type SplitMode = 'equal' | 'by-item';

interface ItemGroup {
  itemId: string;
  group: number;
}

export function BillSplitDialog({ isOpen, tab, onClose, onBillRequested }: Props) {
  const queryClient = useQueryClient();
  const apiCall = useAuthorizedRequest();
  const [mode, setMode] = useState<SplitMode>('equal');
  const [parts, setParts] = useState(2);
  const [itemGroups, setItemGroups] = useState<ItemGroup[]>(
    tab.items
      .filter((i) => i.status !== 'CANCELLED')
      .map((i) => ({ itemId: i.id, group: 1 })),
  );
  const [isLoading, setIsLoading] = useState(false);

  if (!isOpen) return null;

  const activeItems = tab.items.filter((i) => i.status !== 'CANCELLED');
  const maxGroup = Math.max(...itemGroups.map((ig) => ig.group), 1);

  async function handleRequestBill() {
    setIsLoading(true);
    try {
      if (mode === 'equal') {
        // Divisão igual: apenas pedir a conta
        await apiCall<unknown>(`/restaurant/tabs/${tab.id}/closing`, {
          method: 'POST',
          body: { parts },
        });
      } else {
        // Divisão por item: atribuir grupos
        await apiCall<unknown>(`/restaurant/tabs/${tab.id}/split-groups`, {
          method: 'PUT',
          body: {
            groups: itemGroups.map((ig) => ({
              itemId: ig.itemId,
              splitGroup: ig.group,
            })),
          },
        });
        // Depois pedir a conta
        await apiCall<unknown>(`/restaurant/tabs/${tab.id}/closing`, {
          method: 'POST',
        });
      }
      await queryClient.invalidateQueries({ queryKey: tabKey(tab.id) });
      onBillRequested();
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
            <h2 className="dr-sheet-title">Divisão da conta</h2>
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

          {/* Modo */}
          <div style={{ padding: '16px 0' }}>
            <label style={{ display: 'block', marginBottom: '12px', fontSize: '13.5px', fontWeight: 600 }}>
              Como dividir:
            </label>
            <div style={{ display: 'flex', gap: '12px' }}>
              {(['equal', 'by-item'] as const).map((m) => (
                <label
                  key={m}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '8px',
                    cursor: 'pointer',
                    padding: '8px',
                    borderRadius: '4px',
                    background: mode === m ? 'rgba(232, 149, 127, 0.15)' : 'transparent',
                  }}
                >
                  <input
                    type="radio"
                    name="mode"
                    value={m}
                    checked={mode === m}
                    onChange={(e) => setMode(e.target.value as SplitMode)}
                    style={{ cursor: 'pointer' }}
                  />
                  <span>{m === 'equal' ? 'Igual (N pessoas)' : 'Por item (grupos)'}</span>
                </label>
              ))}
            </div>
          </div>

          {/* Divisão Igual */}
          {mode === 'equal' && (
            <div style={{ padding: '12px 0' }}>
              <label style={{ display: 'block', marginBottom: '12px', fontSize: '13.5px', fontWeight: 600 }}>
                Número de pessoas:
              </label>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '12px',
                  background: 'rgba(232, 149, 127, 0.1)',
                  padding: '12px',
                  borderRadius: '4px',
                }}
              >
                <button
                  type="button"
                  onClick={() => setParts(Math.max(1, parts - 1))}
                  style={{
                    width: '32px',
                    height: '32px',
                    background: '#E8957F',
                    color: '#3D0F1A',
                    border: 'none',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontSize: '18px',
                    fontWeight: 'bold',
                  }}
                >
                  −
                </button>
                <span style={{ fontSize: '18px', fontWeight: 'bold', minWidth: '40px', textAlign: 'center' }}>
                  {parts}
                </span>
                <button
                  type="button"
                  onClick={() => setParts(Math.min(99, parts + 1))}
                  style={{
                    width: '32px',
                    height: '32px',
                    background: '#E8957F',
                    color: '#3D0F1A',
                    border: 'none',
                    borderRadius: '4px',
                    cursor: 'pointer',
                    fontSize: '18px',
                    fontWeight: 'bold',
                  }}
                >
                  +
                </button>
              </div>
              <div style={{ marginTop: '12px', fontSize: '0.9em', color: '#E8957F' }}>
                Total por pessoa: <strong>{formatMoney((parseFloat(tab.total) / parts).toString())}</strong>
              </div>
            </div>
          )}

          {/* Divisão por Item */}
          {mode === 'by-item' && (
            <div style={{ padding: '12px 0' }}>
              <label style={{ display: 'block', marginBottom: '12px', fontSize: '13.5px', fontWeight: 600 }}>
                Atribua cada item a um grupo:
              </label>
              <div
                style={{
                  display: 'flex',
                  flexDirection: 'column',
                  gap: '8px',
                  maxHeight: '240px',
                  overflowY: 'auto',
                  background: 'rgba(232, 149, 127, 0.05)',
                  padding: '12px',
                  borderRadius: '4px',
                }}
              >
                {activeItems.map((item) => {
                  const itemGroup = itemGroups.find((ig) => ig.itemId === item.id);
                  return (
                    <div
                      key={item.id}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        gap: '12px',
                        padding: '8px',
                        background: 'rgba(232, 149, 127, 0.1)',
                        borderRadius: '3px',
                      }}
                    >
                      <div style={{ flex: 1, fontSize: '0.9em' }}>
                        <div style={{ fontWeight: 600 }}>{item.itemName}</div>
                        <div style={{ fontSize: '0.85em', color: '#E8957F' }}>{formatMoney(item.lineTotal)}</div>
                      </div>
                      <select
                        value={itemGroup?.group ?? 1}
                        onChange={(e) => {
                          const group = parseInt(e.target.value);
                          setItemGroups((prev) => [
                            ...prev.filter((ig) => ig.itemId !== item.id),
                            { itemId: item.id, group },
                          ]);
                        }}
                        style={{
                          padding: '6px 10px',
                          background: '#4A1522',
                          color: '#F4ECE3',
                          border: '1px solid #E8957F',
                          borderRadius: '3px',
                          cursor: 'pointer',
                          minWidth: '60px',
                        }}
                      >
                        {Array.from({ length: Math.max(5, maxGroup) }).map((_, i) => (
                          <option key={i + 1} value={i + 1}>
                            Grupo {i + 1}
                          </option>
                        ))}
                      </select>
                    </div>
                  );
                })}
              </div>
            </div>
          )}

          {/* Taxa de Serviço */}
          {tab.serviceChargeApplied && (
            <div
              style={{
                marginTop: '12px',
                padding: '12px',
                background: 'rgba(232, 149, 127, 0.1)',
                borderRadius: '4px',
                fontSize: '0.9em',
              }}
            >
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span>Subtotal:</span>
                <strong>{formatMoney(tab.subtotal)}</strong>
              </div>
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px' }}>
                <span>Taxa de serviço ({tab.serviceChargeRate}%):</span>
                <strong>{formatMoney(tab.serviceCharge)}</strong>
              </div>
              <div
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  paddingTop: '8px',
                  borderTop: '1px solid rgba(232, 149, 127, 0.3)',
                  fontSize: '1.05em',
                }}
              >
                <strong>Total:</strong>
                <strong>{formatMoney(tab.total)}</strong>
              </div>
            </div>
          )}

          <div className="dr-sheet-actions">
            <button
              type="button"
              className="dr-action-btn dr-action-primary"
              onClick={() => void handleRequestBill()}
              disabled={isLoading}
            >
              {isLoading ? 'Enviando...' : '📋 Pedir a Conta'}
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
