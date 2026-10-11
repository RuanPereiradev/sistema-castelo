import { useEffect, useState } from 'react';
import { formatMoney, formatWeight } from '../../lib/money';
import {
  calculateWeightPrice,
  useAddSelfServiceItem,
  useOpenSelfServiceTab,
  useSelfServiceItems,
  useSelfServiceTab,
} from './useSelfService';
import { SELF_SERVICE, describeError } from './selfServiceMessages';
import '../../styles/self-service.css';

const MIN_CARD = 1;
const MAX_CARD = 999;
const MIN_GRAMS = 1;
const MAX_GRAMS = 50_000;

/**
 * Self-Service scale screen for buffet items.
 * Flow:
 * 1. User enters card number (1-999)
 * 2. System finds or creates tab for that card
 * 3. User selects item from available buffet items
 * 4. User enters weight in grams
 * 5. System shows price confirmation
 * 6. User confirms to add item
 */
export function SelfServicePage() {
  const [cardNumber, setCardNumber] = useState<string>('');
  const [selectedItemId, setSelectedItemId] = useState<string | null>(null);
  const [weightGrams, setWeightGrams] = useState<string>('');
  const [notice, setNotice] = useState<string | null>(null);

  const cardNumberInt = cardNumber ? Number.parseInt(cardNumber, 10) : null;
  const isValidCard = cardNumberInt !== null && cardNumberInt >= MIN_CARD && cardNumberInt <= MAX_CARD;

  // Query for items sold by weight
  const itemsQuery = useSelfServiceItems();

  // Query for existing tab
  const tabQuery = useSelfServiceTab(isValidCard ? cardNumberInt : null);

  // Mutation to open/create tab
  const openTabMutation = useOpenSelfServiceTab();

  // Mutation to add item
  const addItemMutation = useAddSelfServiceItem(tabQuery.data?.id || '');

  const tab = tabQuery.data;
  const selectedItem = itemsQuery.data?.find((item) => item.id === selectedItemId);
  const parsedWeight = weightGrams ? Number.parseInt(weightGrams, 10) : null;
  const isValidWeight = parsedWeight !== null && parsedWeight >= MIN_GRAMS && parsedWeight <= MAX_GRAMS;

  const price =
    selectedItem && isValidWeight && selectedItem.price
      ? calculateWeightPrice(selectedItem.price, parsedWeight)
      : null;

  const canAddItem = tab && selectedItem && isValidWeight && !addItemMutation.isPending;

  // Auto-create tab when card is valid and doesn't exist
  useEffect(() => {
    if (isValidCard && !tab && !tabQuery.isLoading && tabQuery.isFetched && !openTabMutation.isPending) {
      openTabMutation.mutate(cardNumberInt);
    }
  }, [isValidCard, tab, tabQuery.isLoading, tabQuery.isFetched, cardNumberInt, openTabMutation]);

  // Show notice and clear it after a delay
  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 2600);
    return () => clearTimeout(timer);
  }, [notice]);

  function handleAddItem() {
    if (!canAddItem || !selectedItem) return;

    addItemMutation.mutate(
      { menuItemId: selectedItem.id, weightGrams: parsedWeight! },
      {
        onSuccess: () => {
          setNotice(SELF_SERVICE.itemAdded(selectedItem.name));
          setSelectedItemId(null);
          setWeightGrams('');
        },
      },
    );
  }

  function handleNewCard() {
    setCardNumber('');
    setSelectedItemId(null);
    setWeightGrams('');
  }

  const isLoading = itemsQuery.isLoading || tabQuery.isLoading || openTabMutation.isPending;
  const hasItems = (itemsQuery.data?.length ?? 0) > 0;

  return (
    <div className="self-service-container">
      <div className="self-service-header">
        <h1>{SELF_SERVICE.title}</h1>
        {tab && (
          <div className="self-service-card-info">
            Cartão #{tab.cardNumber}
          </div>
        )}
      </div>

      <div className="self-service-main">
        {/* Card Number Input */}
        <div className="ss-card-section">
          <label htmlFor="ss-card">{SELF_SERVICE.cardNumber}</label>
          <input
            id="ss-card"
            className="ss-card-input"
            type="number"
            inputMode="numeric"
            min={MIN_CARD}
            max={MAX_CARD}
            placeholder="1-999"
            value={cardNumber}
            onChange={(e) => {
              const val = e.target.value;
              if (val === '' || (Number.parseInt(val, 10) >= MIN_CARD && Number.parseInt(val, 10) <= MAX_CARD)) {
                setCardNumber(val);
              }
            }}
            disabled={isLoading}
            autoFocus
          />
          <span className="ss-card-hint">{SELF_SERVICE.cardHint}</span>
        </div>

        {/* Items Selection */}
        {tab && (
          <>
            <div className="ss-items-section">
              <label className="ss-items-label">{SELF_SERVICE.selectItem}</label>
              {isLoading ? (
                <div className="ss-loading">{SELF_SERVICE.loading}</div>
              ) : !hasItems ? (
                <div className="ss-no-items">{SELF_SERVICE.noItemsAvailable}</div>
              ) : (
                <div className="ss-items-grid">
                  {itemsQuery.data!.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      className={`ss-item-button ${selectedItemId === item.id ? 'selected' : ''}`}
                      onClick={() => setSelectedItemId(item.id)}
                      disabled={!item.availableNow || addItemMutation.isPending}
                    >
                      <span className="ss-item-name">{item.name}</span>
                      {item.price && <span className="ss-item-price">{formatMoney(item.price)}/kg</span>}
                    </button>
                  ))}
                </div>
              )}
            </div>

            {/* Weight Input */}
            {selectedItem && (
              <div className="ss-weight-section">
                <label htmlFor="ss-weight">{SELF_SERVICE.weight}</label>
                <input
                  id="ss-weight"
                  className="ss-weight-input"
                  type="number"
                  inputMode="numeric"
                  min={MIN_GRAMS}
                  max={MAX_GRAMS}
                  step={1}
                  placeholder="0"
                  value={weightGrams}
                  onChange={(e) => setWeightGrams(e.target.value)}
                  disabled={addItemMutation.isPending}
                  autoFocus
                />
                <span className="ss-weight-hint">{SELF_SERVICE.weightHint}</span>
              </div>
            )}

            {/* Price Preview */}
            {selectedItem && isValidWeight && price && (
              <div className="ss-price-preview">
                <div className="ss-price-line">
                  <span className="ss-price-label">{SELF_SERVICE.selectItem}</span>
                  <span className="ss-price-value">{selectedItem.name}</span>
                </div>
                <div className="ss-price-line">
                  {selectedItem.price && (
                    <span className="ss-price-label">
                      {SELF_SERVICE.priceCalculation(
                        formatWeight(parsedWeight!),
                        formatMoney(selectedItem.price),
                        formatMoney(price),
                      )}
                    </span>
                  )}
                </div>
                <div className="ss-price-line ss-price-total">
                  <span className="ss-price-label">{SELF_SERVICE.total}</span>
                  <span className="ss-price-value">{formatMoney(price)}</span>
                </div>
              </div>
            )}

            {/* Tab Total */}
            {tab && (
              <div className="ss-tab-total">
                <span className="ss-tab-total-label">{SELF_SERVICE.cardTotal}</span>
                <span className="ss-tab-total-value">{formatMoney(tab.total)}</span>
              </div>
            )}

            {/* Error Messages */}
            {addItemMutation.error && (
              <div className="ss-error" role="alert">
                {describeError(addItemMutation.error)}
              </div>
            )}

            {/* Notice Messages */}
            {notice && (
              <div className="ss-notice" role="status">
                {notice}
              </div>
            )}

            {/* Actions */}
            <div className="ss-actions">
              <button
                type="button"
                className="ss-action-btn ss-action-primary"
                onClick={handleAddItem}
                disabled={!canAddItem}
              >
                {addItemMutation.isPending ? SELF_SERVICE.adding : SELF_SERVICE.add}
              </button>
              <button
                type="button"
                className="ss-action-btn ss-action-secondary"
                onClick={handleNewCard}
                disabled={addItemMutation.isPending}
              >
                {SELF_SERVICE.newCard}
              </button>
            </div>
          </>
        )}
      </div>
    </div>
  );
}
