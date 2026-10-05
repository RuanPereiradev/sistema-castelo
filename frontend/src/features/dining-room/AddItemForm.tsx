import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { MenuItem } from './useMenuItems';
import { useAddTabItem, type ModifierChoice } from './useAddTabItem';

interface Props {
  readonly tabId: string;
  readonly item: MenuItem;
  readonly onClose: () => void;
}

/**
 * Formulário pra adicionar um item à comanda.
 * Suporta variações, adicionais e observações.
 */
export function AddItemForm({ tabId, item, onClose }: Props) {
  const queryClient = useQueryClient();
  const [quantity, setQuantity] = useState(1);
  const [selectedVariant, setSelectedVariant] = useState<string | null>(
    item.variants.length === 1 ? item.variants[0]!.id : null,
  );
  const [selectedModifiers, setSelectedModifiers] = useState<Record<string, number>>({});
  const [observation, setObservation] = useState('');

  const { mutate: addItem, isPending } = useAddTabItem(tabId);

  function handleAddModifier(modifierId: string, max: number) {
    setSelectedModifiers((prev) => ({
      ...prev,
      [modifierId]: Math.min((prev[modifierId] ?? 0) + 1, max),
    }));
  }

  function handleRemoveModifier(modifierId: string) {
    setSelectedModifiers((prev) => {
      const next = { ...prev };
      delete next[modifierId];
      return next;
    });
  }

  function handleSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!item.id || (item.variants.length > 0 && !selectedVariant)) {
      return;
    }

    const modifierChoices: ModifierChoice[] = Object.entries(selectedModifiers).map(
      ([modifierId, quantity]) => ({
        modifierId,
        quantity,
      }),
    );

    addItem(
      {
        menuItemId: item.id,
        ...(selectedVariant ? { variantId: selectedVariant } : {}),
        modifierChoices,
        ...(observation ? { specialInstructions: observation } : {}),
        quantity,
      },
      {
        onSuccess: () => {
          queryClient.invalidateQueries({ queryKey: ['dining-table-tab'] });
          onClose();
        },
      },
    );
  }

  return (
    <form onSubmit={handleSubmit} className="add-item-form">
      <div className="form-section">
        <h3>{item.name}</h3>
        {item.description && <p className="item-description">{item.description}</p>}
      </div>

      {/* Variações */}
      {item.variants.length > 0 && (
        <div className="form-section">
          <label className="form-label">Tamanho</label>
          <div className="variants-grid">
            {item.variants.map((variant) => (
              <button
                key={variant.id}
                type="button"
                className={`variant-option ${selectedVariant === variant.id ? 'selected' : ''} ${
                  !variant.availableNow ? 'disabled' : ''
                }`}
                onClick={() => variant.availableNow && setSelectedVariant(variant.id)}
                disabled={!variant.availableNow}
              >
                <span className="variant-name">{variant.name}</span>
                <span className="variant-price">{variant.price}</span>
              </button>
            ))}
          </div>
        </div>
      )}

      {/* Adicionais */}
      {item.modifiers.length > 0 && (
        <div className="form-section">
          <label className="form-label">Adicionais</label>
          <div className="modifiers-list">
            {item.modifiers.map((modifier) => {
              const selected = selectedModifiers[modifier.id] ?? 0;
              return (
                <div key={modifier.id} className="modifier-item">
                  <div className="modifier-info">
                    <span className="modifier-name">{modifier.name}</span>
                    <span className="modifier-price">+{modifier.price}</span>
                  </div>
                  <div className="modifier-controls">
                    {selected > 0 ? (
                      <>
                        <button
                          type="button"
                          className="modifier-btn"
                          onClick={() => handleRemoveModifier(modifier.id)}
                        >
                          −
                        </button>
                        <span className="modifier-qty">{selected}</span>
                        <button
                          type="button"
                          className="modifier-btn"
                          onClick={() => handleAddModifier(modifier.id, modifier.maxQuantity)}
                          disabled={selected >= modifier.maxQuantity}
                        >
                          +
                        </button>
                      </>
                    ) : (
                      <button
                        type="button"
                        className="modifier-btn add"
                        onClick={() => handleAddModifier(modifier.id, modifier.maxQuantity)}
                      >
                        Adicionar
                      </button>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        </div>
      )}

      {/* Quantidade */}
      <div className="form-section">
        <label className="form-label">Quantidade</label>
        <div className="quantity-control">
          <button
            type="button"
            onClick={() => setQuantity(Math.max(1, quantity - 1))}
            disabled={quantity <= 1}
          >
            −
          </button>
          <input type="number" value={quantity} readOnly className="quantity-input" />
          <button type="button" onClick={() => setQuantity(quantity + 1)}>
            +
          </button>
        </div>
      </div>

      {/* Observação */}
      <div className="form-section">
        <label htmlFor="observation" className="form-label">
          Observação
        </label>
        <textarea
          id="observation"
          value={observation}
          onChange={(e) => setObservation(e.target.value)}
          placeholder="Ex: sem cebola, sem azeitona..."
          className="form-textarea"
          rows={3}
        />
      </div>

      {/* Botões */}
      <div className="form-actions">
        <button type="button" className="btn btn-secondary" onClick={onClose} disabled={isPending}>
          Cancelar
        </button>
        <button type="submit" className="btn btn-primary" disabled={isPending}>
          {isPending ? 'Adicionando...' : 'Adicionar à comanda'}
        </button>
      </div>
    </form>
  );
}
