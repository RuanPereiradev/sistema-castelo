import { useState, type Dispatch } from 'react';
import { formatMoney } from '../../lib/money';
import type { PublicMenu, PublicMenuItem } from './customerApi';
import { CUSTOMER, describeCustomerError } from './customerMessages';
import { cartUnitCount, needsChoice, plainLineKey, unitsOfItem, type CartAction, type CartLine } from './cart';
import { QuantityStepper, StateMessage } from './CustomerChrome';
import { PlusIcon } from './customerIcons';

const ALL = -1;

function priceLabel(item: PublicMenuItem): string {
  const price = formatMoney(item.price);
  if (item.soldByWeight) {
    return CUSTOMER.menu.perKilo(price);
  }
  return item.variants.length > 0 ? CUSTOMER.menu.from(price) : price;
}

export function MenuScreen({
  menu,
  isLoading,
  error,
  onRetry,
  cart,
  dispatch,
  onChoose,
  onSeeCart,
}: {
  readonly menu: PublicMenu | undefined;
  readonly isLoading: boolean;
  readonly error: unknown;
  readonly onRetry: () => void;
  readonly cart: readonly CartLine[];
  readonly dispatch: Dispatch<CartAction>;
  readonly onChoose: (item: PublicMenuItem) => void;
  readonly onSeeCart: () => void;
}) {
  const [category, setCategory] = useState(ALL);
  const units = cartUnitCount(cart);

  let body;
  if (isLoading) {
    body = <StateMessage role="status">{CUSTOMER.menu.loading}</StateMessage>;
  } else if (!menu) {
    body = (
      <StateMessage role="alert" onRetry={onRetry}>
        {CUSTOMER.menu.failed} {describeCustomerError(error)}
      </StateMessage>
    );
  } else if (menu.categories.length === 0) {
    body = <StateMessage>{CUSTOMER.menu.empty}</StateMessage>;
  } else {
    const sections = menu.categories
      .map((section, index) => ({ section, index }))
      .filter(({ index }) => category === ALL || category === index);
    body = (
      <>
        <div className="ct-chips" role="toolbar" aria-label={CUSTOMER.menu.categories}>
          {[{ label: CUSTOMER.menu.all, index: ALL }, ...menu.categories.map((c, index) => ({ label: c.name, index }))].map(
            (chip) => (
              <button
                key={chip.index}
                type="button"
                className={chip.index === category ? 'ct-chip is-active' : 'ct-chip'}
                aria-pressed={chip.index === category}
                onClick={() => setCategory(chip.index)}
              >
                {chip.label}
              </button>
            ),
          )}
        </div>
        <div className="ct-menu">
          {sections.map(({ section, index }) => (
            <section key={index} className="ct-menu-section">
              <h2 className="ct-section-title">
                <span>❧ {section.name}</span>
                <span className="ct-section-rule" aria-hidden="true" />
              </h2>
              {section.items.map((item) => (
                <MenuItemRow key={item.id} item={item} cart={cart} dispatch={dispatch} onChoose={onChoose} />
              ))}
            </section>
          ))}
        </div>
      </>
    );
  }

  return (
    <>
      <div className="ct-body">{body}</div>
      {units > 0 && (
        <div className="ct-footer">
          <button type="button" className="btn btn-primary btn-block ct-cta ct-cart-bar" onClick={onSeeCart}>
            <span>
              {CUSTOMER.menu.seeCart} · {CUSTOMER.menu.itemCount(units)}
            </span>
          </button>
        </div>
      )}
    </>
  );
}

function MenuItemRow({
  item,
  cart,
  dispatch,
  onChoose,
}: {
  readonly item: PublicMenuItem;
  readonly cart: readonly CartLine[];
  readonly dispatch: Dispatch<CartAction>;
  readonly onChoose: (item: PublicMenuItem) => void;
}) {
  const inCart = unitsOfItem(cart, item.id);

  let action;
  if (!item.availableNow) {
    action = <span className="ct-item-tag">{CUSTOMER.menu.unavailable}</span>;
  } else if (item.soldByWeight) {
    action = <span className="ct-item-tag">{CUSTOMER.menu.servedAtBuffet}</span>;
  } else if (needsChoice(item) || inCart === 0) {
    action = (
      <button
        type="button"
        className="btn btn-secondary btn-icon ct-add"
        aria-label={CUSTOMER.menu.add(item.name)}
        onClick={() =>
          needsChoice(item)
            ? onChoose(item)
            : dispatch({ type: 'add', item, variant: null, modifiers: [], quantity: 1 })
        }
      >
        <PlusIcon />
      </button>
    );
  } else {
    const key = plainLineKey(item);
    action = (
      <QuantityStepper
        quantity={inCart}
        onDecrease={() => dispatch({ type: 'change-quantity', key, delta: -1 })}
        onIncrease={() => dispatch({ type: 'change-quantity', key, delta: 1 })}
      />
    );
  }

  return (
    <div className={item.availableNow ? 'ct-item' : 'ct-item is-dimmed'}>
      <div className="ct-item-text">
        <span className="ct-item-name">{item.name}</span>
        {item.description && <span className="ct-item-description">{item.description}</span>}
        <span className="ct-item-price">{priceLabel(item)}</span>
        {needsChoice(item) && inCart > 0 && <span className="ct-item-in-cart">{CUSTOMER.menu.inCart(inCart)}</span>}
      </div>
      {action}
    </div>
  );
}
