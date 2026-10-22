import type { ReactNode } from 'react';
import { CUSTOMER } from './customerMessages';
import { BackIcon, BillIcon, MenuIcon, MinusIcon, OrdersIcon, PlusIcon } from './customerIcons';

export type CustomerScreen = 'welcome' | 'menu' | 'cart' | 'orders' | 'bill' | 'pix' | 'receipt';

/** The shield with the brand's initial, the design's mark. */
export function BrandShield({ large = false }: { readonly large?: boolean }) {
  return (
    <div className={large ? 'ct-shield ct-shield-large' : 'ct-shield'} aria-hidden="true">
      {CUSTOMER.brand.charAt(0)}
    </div>
  );
}

/** A thin rule with the hedera ❧ in the middle. */
export function HederaRule() {
  return (
    <div className="ct-hedera-rule" aria-hidden="true">
      <span />
      <span className="ct-hedera">❧</span>
      <span />
    </div>
  );
}

export function CustomerHeader({
  title,
  tableLabel,
  onBack,
}: {
  readonly title: string;
  readonly tableLabel: string;
  readonly onBack?: () => void;
}) {
  return (
    <header className="ct-header">
      {onBack ? (
        <button type="button" className="btn btn-ghost btn-icon ct-back" onClick={onBack} aria-label={CUSTOMER.back}>
          <BackIcon />
        </button>
      ) : (
        <BrandShield />
      )}
      <div className="ct-header-titles">
        <span className="ct-header-brand">{CUSTOMER.brand}</span>
        <h1 className="ct-header-title">{title}</h1>
      </div>
      <div className="ct-table-badge">
        <span className="ct-table-badge-label">{CUSTOMER.table}</span>
        <span className="ct-table-badge-value">{tableLabel}</span>
      </div>
    </header>
  );
}

/** Cardápio, Pedidos and Conta, as in the design. */
export function CustomerNav({
  current,
  onGo,
}: {
  readonly current: CustomerScreen;
  readonly onGo: (screen: CustomerScreen) => void;
}) {
  const entries: readonly { screen: CustomerScreen; label: string; icon: ReactNode }[] = [
    { screen: 'menu', label: CUSTOMER.nav.menu, icon: <MenuIcon /> },
    { screen: 'orders', label: CUSTOMER.nav.orders, icon: <OrdersIcon /> },
    { screen: 'bill', label: CUSTOMER.nav.bill, icon: <BillIcon /> },
  ];
  return (
    <nav className="ct-nav" aria-label={CUSTOMER.nav.label}>
      {entries.map((entry) => {
        const isCurrent = entry.screen === current;
        return (
          <button
            key={entry.label}
            type="button"
            className={isCurrent ? 'ct-nav-item is-current' : 'ct-nav-item'}
            aria-current={isCurrent ? 'page' : undefined}
            onClick={() => onGo(entry.screen)}
          >
            <span className="ct-nav-bar" aria-hidden="true" />
            {entry.icon}
            {entry.label}
          </button>
        );
      })}
    </nav>
  );
}

export function QuantityStepper({
  quantity,
  onDecrease,
  onIncrease,
  decreaseDisabled = false,
  increaseDisabled = false,
}: {
  readonly quantity: number;
  readonly onDecrease: () => void;
  readonly onIncrease: () => void;
  readonly decreaseDisabled?: boolean;
  readonly increaseDisabled?: boolean;
}) {
  return (
    <div className="ct-stepper">
      <button type="button" onClick={onDecrease} disabled={decreaseDisabled} aria-label={CUSTOMER.menu.removeOne}>
        <MinusIcon />
      </button>
      <span className="ct-stepper-value" aria-live="polite">
        {quantity}
      </span>
      <button type="button" onClick={onIncrease} disabled={increaseDisabled} aria-label={CUSTOMER.menu.addOne}>
        <PlusIcon size={18} />
      </button>
    </div>
  );
}

/** Loading, empty and failure all speak in the same quiet line, with a retry when it helps. */
export function StateMessage({
  children,
  onRetry,
  role,
}: {
  readonly children: ReactNode;
  readonly onRetry?: () => void;
  readonly role?: 'status' | 'alert';
}) {
  return (
    <div className="ct-state" role={role}>
      <p>{children}</p>
      {onRetry && (
        <button type="button" className="btn btn-secondary ct-retry" onClick={onRetry}>
          {CUSTOMER.retry}
        </button>
      )}
    </div>
  );
}
