import { useEffect, useId, useState } from 'react';
import { formatMoney } from '../../lib/money';
import type { PublicMenuItem, PublicMenuVariant } from './customerApi';
import type { CartModifier } from './cart';
import { CUSTOMER } from './customerMessages';
import { QuantityStepper } from './CustomerChrome';
import { CloseIcon } from './customerIcons';

/**
 * The choice an item needs before it enters the cart (decision #6): the variant,
 * when there are variants, and each modifier from none up to its `maxQuantity`.
 * It shows unit prices only; the sum is the backend's job.
 */
export function ItemSheet({
  item,
  onAdd,
  onClose,
}: {
  readonly item: PublicMenuItem;
  readonly onAdd: (choice: {
    readonly variant: PublicMenuVariant | null;
    readonly modifiers: readonly CartModifier[];
    readonly quantity: number;
  }) => void;
  readonly onClose: () => void;
}) {
  const titleId = useId();
  const [variantId, setVariantId] = useState<string | null>(null);
  const [modifierQuantities, setModifierQuantities] = useState<Readonly<Record<string, number>>>({});
  const [quantity, setQuantity] = useState(1);

  useEffect(() => {
    function handleKey(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKey);
    return () => window.removeEventListener('keydown', handleKey);
  }, [onClose]);

  const variant = item.variants.find((candidate) => candidate.id === variantId) ?? null;
  const missingVariant = item.variants.length > 0 && variant === null;

  function changeModifier(modifierId: string, delta: number, maxQuantity: number) {
    setModifierQuantities((current) => {
      const next = Math.min(maxQuantity, Math.max(0, (current[modifierId] ?? 0) + delta));
      return { ...current, [modifierId]: next };
    });
  }

  function handleAdd() {
    if (missingVariant) {
      return;
    }
    onAdd({
      variant,
      modifiers: item.modifiers.map((modifier) => ({ modifier, quantity: modifierQuantities[modifier.id] ?? 0 })),
      quantity,
    });
  }

  return (
    <div className="ct-sheet-backdrop" onClick={onClose}>
      <div
        className="ct-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="ct-sheet-head">
          <div className="ct-sheet-titles">
            <h2 id={titleId} className="ct-sheet-title">
              {item.name}
            </h2>
            {item.description && <p className="ct-item-description">{item.description}</p>}
          </div>
          <button type="button" className="btn btn-ghost btn-icon ct-sheet-close" onClick={onClose} aria-label={CUSTOMER.sheet.close}>
            <CloseIcon />
          </button>
        </div>

        <div className="ct-sheet-body">
          {item.variants.length > 0 && (
            <fieldset className="ct-sheet-group">
              <legend className="ct-section-title">
                <span>❧ {CUSTOMER.sheet.variant}</span>
                <span className="ct-section-rule" aria-hidden="true" />
              </legend>
              {item.variants.map((option) => (
                <label key={option.id} className={option.availableNow ? 'ct-option' : 'ct-option is-dimmed'}>
                  <input
                    type="radio"
                    name={`variant-${item.id}`}
                    value={option.id}
                    checked={variantId === option.id}
                    disabled={!option.availableNow}
                    onChange={() => setVariantId(option.id)}
                  />
                  <span className="ct-option-mark" aria-hidden="true" />
                  <span className="ct-option-name">
                    {option.name}
                    {!option.availableNow && <span className="ct-option-note"> · {CUSTOMER.sheet.unavailable}</span>}
                  </span>
                  <span className="ct-option-price">{formatMoney(option.price)}</span>
                </label>
              ))}
            </fieldset>
          )}

          {item.modifiers.length > 0 && (
            <div className="ct-sheet-group">
              <h3 className="ct-section-title">
                <span>❧ {CUSTOMER.sheet.modifiers}</span>
                <span className="ct-section-rule" aria-hidden="true" />
              </h3>
              {item.modifiers.map((modifier) => {
                const chosen = modifierQuantities[modifier.id] ?? 0;
                return (
                  <div key={modifier.id} className="ct-modifier">
                    <div className="ct-modifier-text">
                      <span className="ct-option-name">{modifier.name}</span>
                      <span className="ct-modifier-meta">
                        {CUSTOMER.sheet.modifierPrice(formatMoney(modifier.price))} ·{' '}
                        {CUSTOMER.sheet.modifierLimit(modifier.maxQuantity)}
                      </span>
                    </div>
                    <QuantityStepper
                      quantity={chosen}
                      decreaseDisabled={chosen === 0}
                      increaseDisabled={chosen >= modifier.maxQuantity}
                      onDecrease={() => changeModifier(modifier.id, -1, modifier.maxQuantity)}
                      onIncrease={() => changeModifier(modifier.id, 1, modifier.maxQuantity)}
                    />
                  </div>
                );
              })}
            </div>
          )}

          <div className="ct-modifier">
            <span className="ct-sheet-quantity-label">{CUSTOMER.sheet.quantity}</span>
            <QuantityStepper
              quantity={quantity}
              decreaseDisabled={quantity <= 1}
              onDecrease={() => setQuantity((current) => Math.max(1, current - 1))}
              onIncrease={() => setQuantity((current) => current + 1)}
            />
          </div>
        </div>

        <div className="ct-sheet-foot">
          {missingVariant && <p className="ct-hint">{CUSTOMER.sheet.variantRequired}</p>}
          <button type="button" className="btn btn-primary btn-block ct-cta" disabled={missingVariant} onClick={handleAdd}>
            {CUSTOMER.sheet.addToCart}
          </button>
        </div>
      </div>
    </div>
  );
}
