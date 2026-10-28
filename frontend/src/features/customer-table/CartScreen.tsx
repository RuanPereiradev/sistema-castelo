import { useId, useState, type Dispatch } from 'react';
import { formatMoney } from '../../lib/money';
import { MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH, type CartAction, type CartLine } from './cart';
import { CUSTOMER } from './customerMessages';
import { QuantityStepper } from './CustomerChrome';
import { CloseIcon } from './customerIcons';

/** Why the table cannot send now, read from `GET /public/tables/{token}`. */
export type SendBlock = 'closing' | 'pending-mine' | 'limit-reached' | null;

export function CartScreen({
  cart,
  dispatch,
  onChooseMore,
  onSend,
  isSending,
  sendBlock,
  sendError,
  onFixName,
  tablePendingCount,
}: {
  readonly cart: readonly CartLine[];
  readonly dispatch: Dispatch<CartAction>;
  readonly onChooseMore: () => void;
  readonly onSend: () => void;
  readonly isSending: boolean;
  readonly sendBlock: SendBlock;
  readonly sendError: { readonly message: string; readonly fixName: boolean } | null;
  readonly onFixName: () => void;
  /** Requests of the whole table waiting for the waiter, for information only. */
  readonly tablePendingCount: number;
}) {
  const blockMessage =
    sendBlock === 'closing'
      ? CUSTOMER.cart.closing
      : sendBlock === 'pending-mine'
        ? CUSTOMER.cart.pendingMine
        : sendBlock === 'limit-reached'
          ? CUSTOMER.cart.limitReached
          : null;

  return (
    <>
      <div className="ct-body">
        <div className="ct-cart">
          <p className="ct-lead">{CUSTOMER.cart.intro}</p>
          {cart.length > 0 ? (
            <div className="ct-cart-lines">
              {cart.map((line) => (
                <CartLineRow key={line.key} line={line} dispatch={dispatch} />
              ))}
            </div>
          ) : (
            <p className="ct-empty">{CUSTOMER.cart.empty}</p>
          )}
          <button type="button" className="btn btn-ghost ct-more" onClick={onChooseMore}>
            {CUSTOMER.cart.chooseMore}
          </button>
        </div>
      </div>
      <div className="ct-footer ct-footer-stack">
        {sendBlock === null && tablePendingCount > 0 && (
          <p className="ct-hint">{CUSTOMER.cart.tablePending(tablePendingCount)}</p>
        )}
        {blockMessage && (
          <p className="ct-notice-inline" role="status">
            {blockMessage}
          </p>
        )}
        {sendError && (
          <div className="ct-error-inline" role="alert">
            <p>{sendError.message}</p>
            {sendError.fixName && (
              <button type="button" className="btn btn-ghost" onClick={onFixName}>
                {CUSTOMER.cart.fixName}
              </button>
            )}
          </div>
        )}
        <button
          type="button"
          className="btn btn-primary btn-block ct-cta"
          onClick={onSend}
          disabled={cart.length === 0 || isSending || sendBlock !== null}
          aria-busy={isSending}
        >
          {isSending ? CUSTOMER.cart.sending : CUSTOMER.cart.send}
        </button>
      </div>
    </>
  );
}

function CartLineRow({ line, dispatch }: { readonly line: CartLine; readonly dispatch: Dispatch<CartAction> }) {
  const noteId = useId();
  const [noteOpen, setNoteOpen] = useState(line.specialInstructions.length > 0);
  const unitPrice = line.variant ? line.variant.price : line.item.price;

  return (
    <div className="ct-cart-line">
      <div className="ct-cart-line-main">
        <div className="ct-cart-line-text">
          <span className="ct-cart-line-name">
            {line.item.name}
            {line.variant && <span className="ct-cart-line-variant"> · {line.variant.name}</span>}
          </span>
          <span className="ct-cart-line-unit">{CUSTOMER.cart.each(formatMoney(unitPrice))}</span>
          {line.modifiers.map((choice) => (
            <span key={choice.modifier.id} className="ct-cart-line-modifier">
              {CUSTOMER.cart.modifier(choice.modifier.name, choice.quantity, formatMoney(choice.modifier.price))}
            </span>
          ))}
        </div>
        <QuantityStepper
          quantity={line.quantity}
          onDecrease={() => dispatch({ type: 'change-quantity', key: line.key, delta: -1 })}
          onIncrease={() => dispatch({ type: 'change-quantity', key: line.key, delta: 1 })}
        />
        <button
          type="button"
          className="btn btn-ghost btn-icon ct-remove"
          aria-label={`${CUSTOMER.cart.remove}: ${line.item.name}`}
          onClick={() => dispatch({ type: 'remove', key: line.key })}
        >
          <CloseIcon />
        </button>
      </div>
      {noteOpen ? (
        <div className="field ct-note">
          <label htmlFor={noteId} className="ct-note-label">
            {CUSTOMER.cart.note}
          </label>
          <input
            id={noteId}
            className="input ct-note-input"
            type="text"
            value={line.specialInstructions}
            maxLength={MAXIMUM_SPECIAL_INSTRUCTIONS_LENGTH}
            placeholder={CUSTOMER.cart.notePlaceholder}
            onChange={(event) => dispatch({ type: 'note', key: line.key, specialInstructions: event.target.value })}
          />
        </div>
      ) : (
        <button type="button" className="btn btn-ghost ct-add-note" onClick={() => setNoteOpen(true)}>
          {CUSTOMER.cart.addNote}
        </button>
      )}
    </div>
  );
}
