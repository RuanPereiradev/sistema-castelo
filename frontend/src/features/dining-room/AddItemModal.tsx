import { useState } from 'react';
import type { MenuItem } from './useMenuItems';
import { useMenuItems } from './useMenuItems';
import { AddItemForm } from './AddItemForm';
import '../../styles/add-item-modal.css';

interface Props {
  readonly tabId: string;
  readonly isOpen: boolean;
  readonly onClose: () => void;
}

/**
 * Modal pra lançar item na comanda.
 * Mostra cardápio por categoria e abre formulário de cada item.
 */
export function AddItemModal({ tabId, isOpen, onClose }: Props) {
  const [selectedItem, setSelectedItem] = useState<MenuItem | null>(null);
  const { data: categories = [], isLoading, error } = useMenuItems();

  if (!isOpen) {
    return null;
  }

  if (selectedItem) {
    return (
      <>
        <div className="modal-overlay" onClick={() => setSelectedItem(null)} />
        <div className="modal add-item-modal">
          <div className="modal-header">
            <button
              className="modal-back"
              onClick={() => setSelectedItem(null)}
              aria-label="Voltar"
            >
              ← Voltar
            </button>
          </div>
          <div className="modal-content">
            <AddItemForm
              tabId={tabId}
              item={selectedItem}
              onClose={() => {
                setSelectedItem(null);
                onClose();
              }}
            />
          </div>
        </div>
      </>
    );
  }

  return (
    <>
      {/* Overlay */}
      <div className="modal-overlay" onClick={onClose} />

      {/* Modal */}
      <div className="modal add-item-modal">
        <div className="modal-header">
          <h2>Lançar Item</h2>
          <button className="modal-close" onClick={onClose} aria-label="Fechar">
            ✕
          </button>
        </div>

        <div className="modal-content">
          {isLoading && <p className="loading">Carregando cardápio...</p>}

          {error && (
            <p className="error">
              Erro ao carregar cardápio.{' '}
              {error instanceof Error ? error.message : 'Tente novamente'}
            </p>
          )}

          {!isLoading && !error && categories.length === 0 && (
            <p className="empty">Nenhum item disponível</p>
          )}

          {!isLoading && !error && categories.length > 0 && (
            <div className="menu-categories">
              {categories.map((category) => (
                <div key={category.name} className="menu-category">
                  <h3 className="category-title">{category.name}</h3>
                  <div className="menu-items-list">
                    {category.items.map((item) => (
                      <button
                        key={item.id}
                        type="button"
                        className={`menu-item-btn ${!item.availableNow ? 'unavailable' : ''}`}
                        onClick={() => setSelectedItem(item)}
                        disabled={!item.availableNow}
                      >
                        <div className="item-name">{item.name}</div>
                        {item.description && <div className="item-desc">{item.description}</div>}
                        <div className="item-footer">
                          <span className="item-price">
                            {item.price || item.pricePerKilo || '—'}
                          </span>
                          {!item.availableNow && <span className="unavailable-badge">Indisponível</span>}
                        </div>
                      </button>
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </>
  );
}
