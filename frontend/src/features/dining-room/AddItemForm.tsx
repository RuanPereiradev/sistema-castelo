import { useState } from 'react';
import { formatMoney } from '../../lib/money';
import { describeError } from './diningRoomMessages';
import type { MenuItem } from './useMenuItems';
import { useAddTabItem, type AddTabItemRequest, type ModifierChoice } from './useAddTabItem';

interface Props {
  readonly tabId: string;
  readonly item: MenuItem;
  readonly onBack: () => void;
  readonly onAdded: (itemName: string) => void;
}

const FORM = {
  size: 'Tamanho',
  modifiers: 'Adicionais',
  quantity: 'Quantidade',
  weight: 'Peso em gramas',
  weightHint: 'O que a balança marcou, em gramas.',
  instructions: 'Observação',
  instructionsPlaceholder: 'Ex.: sem cebola, ponto da carne…',
  add: 'Adicionar à comanda',
  adding: 'Lançando…',
  back: 'Voltar ao cardápio',
  chooseSize: 'Escolha o tamanho para continuar.',
  chooseWeight: 'Informe o peso para continuar.',
  unavailable: 'Indisponível agora',
} as const;

const MAX_QUANTITY = 99;
const MAX_WEIGHT_GRAMS = 50_000;

/**
 * One line to add. Sold by unit: size, modifiers, quantity, note. Sold by
 * weight: the grams only, because the backend refuses the rest on it.
 */
export function AddItemForm({ tabId, item, onBack, onAdded }: Props) {
  const [variantId, setVariantId] = useState<string | null>(
    item.variants.length === 1 ? (item.variants[0]?.id ?? null) : null,
  );
  const [modifierQuantities, setModifierQuantities] = useState<Record<string, number>>({});
  const [quantity, setQuantity] = useState(1);
  const [weightGrams, setWeightGrams] = useState('');
  const [instructions, setInstructions] = useState('');

  const addItem = useAddTabItem(tabId);

  const needsVariant = !item.soldByWeight && item.variants.length > 0 && variantId === null;
  const parsedWeight = Number.parseInt(weightGrams, 10);
  const needsWeight =
    item.soldByWeight && (!Number.isInteger(parsedWeight) || parsedWeight <= 0);
  const blocked = needsVariant || needsWeight || addItem.isPending;

  function changeModifier(modifierId: string, delta: number, max: number) {
    setModifierQuantities((current) => {
      const next = Math.min(max, Math.max(0, (current[modifierId] ?? 0) + delta));
      const copy = { ...current };
      if (next === 0) {
        delete copy[modifierId];
      } else {
        copy[modifierId] = next;
      }
      return copy;
    });
  }

  function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (blocked) return;

    const note = instructions.trim();
    let request: AddTabItemRequest;
    if (item.soldByWeight) {
      request = {
        menuItemId: item.id,
        weightGrams: parsedWeight,
        ...(note ? { specialInstructions: note } : {}),
      };
    } else {
      const modifiers: ModifierChoice[] = Object.entries(modifierQuantities).map(
        ([modifierId, chosen]) => ({ modifierId, quantity: chosen }),
      );
      request = {
        menuItemId: item.id,
        quantity,
        ...(variantId ? { variantId } : {}),
        ...(modifiers.length > 0 ? { modifiers } : {}),
        ...(note ? { specialInstructions: note } : {}),
      };
    }

    addItem.mutate(request, { onSuccess: () => onAdded(item.name) });
  }

  return (
    <form className="dr-form" onSubmit={handleSubmit}>
      {item.description && <p className="dr-form-description">{item.description}</p>}

      {!item.soldByWeight && item.variants.length > 0 && (
        <fieldset className="dr-form-section">
          <legend className="dr-form-label">{FORM.size}</legend>
          <div className="dr-variants">
            {item.variants.map((variant) => (
              <button
                key={variant.id}
                type="button"
                className={`dr-variant ${variantId === variant.id ? 'selected' : ''}`}
                onClick={() => setVariantId(variant.id)}
                disabled={!variant.availableNow || addItem.isPending}
                aria-pressed={variantId === variant.id}
              >
                <span className="dr-variant-name">{variant.name}</span>
                <span className="dr-variant-price">
                  {variant.availableNow ? formatMoney(variant.price) : FORM.unavailable}
                </span>
              </button>
            ))}
          </div>
        </fieldset>
      )}

      {!item.soldByWeight && item.modifiers.length > 0 && (
        <fieldset className="dr-form-section">
          <legend className="dr-form-label">{FORM.modifiers}</legend>
          <div className="dr-modifiers">
            {item.modifiers.map((modifier) => {
              const chosen = modifierQuantities[modifier.id] ?? 0;
              return (
                <div key={modifier.id} className="dr-modifier">
                  <span className="dr-modifier-info">
                    <span>{modifier.name}</span>
                    <span className="dr-modifier-price">+ {formatMoney(modifier.price)}</span>
                  </span>
                  <span className="dr-stepper dr-stepper-compact">
                    <button
                      type="button"
                      className="dr-stepper-btn"
                      onClick={() => changeModifier(modifier.id, -1, modifier.maxQuantity)}
                      disabled={chosen === 0 || addItem.isPending}
                      aria-label={`Menos ${modifier.name}`}
                    >
                      −
                    </button>
                    <span className="dr-stepper-value">{chosen}</span>
                    <button
                      type="button"
                      className="dr-stepper-btn"
                      onClick={() => changeModifier(modifier.id, 1, modifier.maxQuantity)}
                      disabled={chosen >= modifier.maxQuantity || addItem.isPending}
                      aria-label={`Mais ${modifier.name}`}
                    >
                      +
                    </button>
                  </span>
                </div>
              );
            })}
          </div>
        </fieldset>
      )}

      {item.soldByWeight ? (
        <div className="dr-form-section">
          <label className="dr-form-label" htmlFor="dr-weight">
            {FORM.weight}
          </label>
          <input
            id="dr-weight"
            className="dr-input"
            type="number"
            inputMode="numeric"
            min={1}
            max={MAX_WEIGHT_GRAMS}
            step={1}
            value={weightGrams}
            onChange={(event) => setWeightGrams(event.target.value)}
            disabled={addItem.isPending}
            autoFocus
          />
          <span className="dr-form-hint">{FORM.weightHint}</span>
        </div>
      ) : (
        <div className="dr-form-section">
          <span className="dr-form-label">{FORM.quantity}</span>
          <div className="dr-stepper">
            <button
              type="button"
              className="dr-stepper-btn"
              onClick={() => setQuantity((n) => Math.max(1, n - 1))}
              disabled={quantity <= 1 || addItem.isPending}
              aria-label="Menos um"
            >
              −
            </button>
            <span className="dr-stepper-value" aria-live="polite">{quantity}</span>
            <button
              type="button"
              className="dr-stepper-btn"
              onClick={() => setQuantity((n) => Math.min(MAX_QUANTITY, n + 1))}
              disabled={quantity >= MAX_QUANTITY || addItem.isPending}
              aria-label="Mais um"
            >
              +
            </button>
          </div>
        </div>
      )}

      <div className="dr-form-section">
        <label className="dr-form-label" htmlFor="dr-instructions">
          {FORM.instructions}
        </label>
        <textarea
          id="dr-instructions"
          className="dr-textarea"
          rows={2}
          maxLength={500}
          placeholder={FORM.instructionsPlaceholder}
          value={instructions}
          onChange={(event) => setInstructions(event.target.value)}
          disabled={addItem.isPending}
        />
      </div>

      {addItem.error && (
        <p className="dr-error-line" role="alert">
          {describeError(addItem.error)}
        </p>
      )}
      {needsVariant && <p className="dr-form-hint">{FORM.chooseSize}</p>}
      {needsWeight && weightGrams !== '' && <p className="dr-form-hint">{FORM.chooseWeight}</p>}

      <div className="dr-sheet-actions">
        <button type="submit" className="dr-action-btn dr-action-primary" disabled={blocked}>
          {addItem.isPending ? FORM.adding : FORM.add}
        </button>
        <button
          type="button"
          className="dr-action-btn dr-action-ghost"
          onClick={onBack}
          disabled={addItem.isPending}
        >
          {FORM.back}
        </button>
      </div>
    </form>
  );
}
