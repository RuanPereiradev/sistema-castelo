import { useState } from 'react';

interface AddItemModalProps {
  isOpen: boolean;
  tableLabel: string;
  tabId: string;
  onClose: () => void;
  onAddItem: (quantity: number, itemName: string, price: string) => Promise<void>;
}

const MENU_ITEMS = [
  { id: '1', name: '🍺 Chopp Brahma 1L', price: '35.00' },
  { id: '2', name: '🍺 Cerveja Skol Lata 350ml', price: '8.00' },
  { id: '3', name: '🥤 Coca Cola 2L', price: '12.50' },
  { id: '4', name: '💧 Água com Gás 500ml', price: '5.00' },
  { id: '5', name: '🍷 Vinho Tinto', price: '45.00' },
  { id: '6', name: '🥃 Batata Frita Grande', price: '25.00' },
  { id: '7', name: '🍗 Frango Grelhado', price: '35.00' },
  { id: '8', name: '🐟 Peixe do Dia', price: '42.00' },
  { id: '9', name: '🥩 Picanha 300g', price: '55.00' },
  { id: '10', name: '🍝 Macarrão à Carbonara', price: '38.00' },
];

export function AddItemModal({ isOpen, tableLabel, tabId, onClose, onAddItem }: AddItemModalProps) {
  const [quantity, setQuantity] = useState(1);
  const [selectedItem, setSelectedItem] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(false);

  const handleAddItem = async () => {
    if (!selectedItem) return;
    
    const item = MENU_ITEMS.find(i => i.id === selectedItem);
    if (!item) return;

    setIsLoading(true);
    try {
      await onAddItem(quantity, item.name, item.price);
      setSelectedItem(null);
      setQuantity(1);
      onClose();
    } finally {
      setIsLoading(false);
    }
  };

  if (!isOpen) return null;

  return (
    <>
      <div className="dr-modal-backdrop" onClick={onClose} />
      <div className="dr-sheet" style={{ maxHeight: '90vh', overflowY: 'auto' }}>
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <div className="dr-sheet-subtitle">Mesa {tableLabel}</div>
            <h2 className="dr-sheet-title">➕ Lançar item</h2>
            <button
              className="dr-sheet-close"
              onClick={onClose}
              aria-label="Fechar"
            >
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round">
                <path d="M18 6 6 18"></path>
                <path d="m6 6 12 12"></path>
              </svg>
            </button>
          </div>

          <div className="dr-divider">
            <span className="dr-divider-line"></span>
            <span className="dr-divider-ornament">❧</span>
            <span className="dr-divider-line"></span>
          </div>

          <div style={{ padding: '12px 0', maxHeight: '45vh', overflowY: 'auto' }}>
            {MENU_ITEMS.map(item => (
              <button
                key={item.id}
                className={`dr-menu-item ${selectedItem === item.id ? 'active' : ''}`}
                onClick={() => setSelectedItem(item.id)}
                style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  width: '100%',
                  padding: '12px 0',
                  border: 'none',
                  background: selectedItem === item.id ? 'rgba(232, 149, 127, 0.15)' : 'transparent',
                  color: 'inherit',
                  cursor: 'pointer',
                  borderBottom: '1px solid rgba(232, 149, 127, 0.2)',
                  fontSize: '14px',
                  transition: 'background 120ms',
                }}
              >
                <span>{item.name}</span>
                <span style={{ fontWeight: 600, color: '#E8957F' }}>R$ {item.price}</span>
              </button>
            ))}
          </div>

          {selectedItem && (
            <>
              <div className="dr-divider">
                <span className="dr-divider-line"></span>
                <span className="dr-divider-ornament">❧</span>
                <span className="dr-divider-line"></span>
              </div>

              <div style={{ padding: '12px 0', display: 'flex', gap: '8px', alignItems: 'center' }}>
                <label style={{ fontSize: '13.5px', color: 'var(--color-text)' }}>Quantidade:</label>
                <input
                  type="number"
                  min="1"
                  max="20"
                  value={quantity}
                  onChange={e => setQuantity(Math.max(1, parseInt(e.target.value, 10)))}
                  style={{
                    width: '60px',
                    padding: '4px 8px',
                    background: 'var(--color-surface)',
                    border: '1px solid var(--color-divider)',
                    color: 'var(--color-text)',
                    borderRadius: '2px',
                    fontFamily: 'var(--font-body)',
                  }}
                />
              </div>
            </>
          )}

          <div className="dr-sheet-actions">
            <button
              className="dr-action-btn dr-action-primary"
              onClick={handleAddItem}
              disabled={!selectedItem || isLoading}
            >
              {isLoading ? 'Adicionando...' : '➕ Adicionar'}
            </button>
            <button className="dr-action-btn dr-action-secondary" onClick={onClose}>
              Fechar
            </button>
          </div>
        </div>
      </div>
    </>
  );
}
