import { useState } from 'react';
import { formatMoney } from '../../lib/money';
import { DINING_ROOM, describeError } from './diningRoomMessages';
import { useMenuItems, type MenuItem } from './useMenuItems';
import type { Tab } from './useTab';
import { AddItemForm } from './AddItemForm';

interface Props {
  readonly isOpen: boolean;
  readonly tab: Tab;
  readonly onClose: () => void;
  /** Called with the item's name once the backend took the line. */
  readonly onAdded: (itemName: string) => void;
}

const MENU = {
  title: 'Lançar pedido',
  loading: 'Carregando o cardápio…',
  failed: 'Não deu para carregar o cardápio.',
  empty: 'O cardápio está vazio. Peça ao administrador para cadastrar os itens.',
  unavailable: 'Indisponível',
  perKilo: '/kg',
  back: 'Voltar ao cardápio',
} as const;

function priceOf(item: MenuItem): string {
  if (item.price) {
    return formatMoney(item.price) + (item.soldByWeight ? MENU.perKilo : '');
  }
  const first = item.variants[0];
  return first ? `a partir de ${formatMoney(first.price)}` : '';
}

/**
 * The public menu, by category, in the same sheet as the table. Tapping an
 * item opens its form; the backend decides what the item accepts.
 */
export function AddItemModal({ isOpen, tab, onClose, onAdded }: Props) {
  const menu = useMenuItems();
  const [selected, setSelected] = useState<MenuItem | null>(null);

  if (!isOpen) {
    return null;
  }

  function close() {
    setSelected(null);
    onClose();
  }

  return (
    <>
      <div className="dr-modal-backdrop" onClick={close} />
      <div className="dr-sheet dr-sheet-tall" role="dialog" aria-modal="true">
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <div className="dr-sheet-subtitle">
              {DINING_ROOM.table} {tab.diningTableLabel}
            </div>
            <h2 className="dr-sheet-title dr-sheet-title-small">
              {selected ? selected.name : MENU.title}
            </h2>
            <button
              type="button"
              className="dr-sheet-close"
              onClick={close}
              aria-label={DINING_ROOM.close}
            >
              <svg
                width="20"
                height="20"
                viewBox="0 0 24 24"
                fill="none"
                stroke="currentColor"
                strokeWidth="1.6"
                strokeLinecap="round"
                aria-hidden="true"
              >
                <path d="M18 6 6 18" />
                <path d="m6 6 12 12" />
              </svg>
            </button>
          </div>

          <div className="dr-divider" aria-hidden="true">
            <span className="dr-divider-line" />
            <span className="dr-divider-ornament">❧</span>
            <span className="dr-divider-line" />
          </div>

          {selected ? (
            <AddItemForm
              tabId={tab.id}
              item={selected}
              onBack={() => setSelected(null)}
              onAdded={(name) => {
                setSelected(null);
                onAdded(name);
              }}
            />
          ) : menu.isPending ? (
            <p className="dr-state" role="status">{MENU.loading}</p>
          ) : menu.error ? (
            <div className="dr-state" role="alert">
              <p>{MENU.failed}</p>
              <p className="dr-state-detail">{describeError(menu.error)}</p>
              <button
                type="button"
                className="dr-action-btn dr-action-secondary"
                onClick={() => void menu.refetch()}
              >
                {DINING_ROOM.retry}
              </button>
            </div>
          ) : !menu.data || menu.data.every((category) => category.items.length === 0) ? (
            <p className="dr-state">{MENU.empty}</p>
          ) : (
            <div className="dr-menu">
              {menu.data
                .filter((category) => category.items.length > 0)
                .map((category) => (
                  <section key={category.name} className="dr-menu-category">
                    <h3 className="dr-menu-category-title">❧ {category.name}</h3>
                    {category.items.map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        className="dr-menu-item"
                        onClick={() => setSelected(item)}
                        disabled={!item.availableNow}
                      >
                        <span className="dr-menu-item-body">
                          <span className="dr-menu-item-name">{item.name}</span>
                          {item.description && (
                            <span className="dr-menu-item-desc">{item.description}</span>
                          )}
                          {!item.availableNow && (
                            <span className="dr-menu-item-unavailable">{MENU.unavailable}</span>
                          )}
                        </span>
                        <span className="dr-menu-item-price">{priceOf(item)}</span>
                      </button>
                    ))}
                  </section>
                ))}
            </div>
          )}

          {!selected && (
            <div className="dr-sheet-actions">
              <button type="button" className="dr-action-btn dr-action-ghost" onClick={close}>
                {DINING_ROOM.back}
              </button>
            </div>
          )}
        </div>
      </div>
    </>
  );
}
