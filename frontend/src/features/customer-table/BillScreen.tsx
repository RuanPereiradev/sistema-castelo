import { useState } from 'react';
import { formatMoney, formatWeight } from '../../lib/money';
import { usePaymentQuote, usePublicTableTab, type PublicTabItem, type PublicTableTab } from './customerApi';
import { CUSTOMER, describeCustomerError, hasErrorCode } from './customerMessages';
import { StateMessage } from './CustomerChrome';

/** One unit of a tab line, as the design lists them: free to pick, paid, or reserved in someone's Pix. */
interface Unit {
  readonly key: string;
  readonly item: PublicTabItem;
  readonly index: number;
  readonly state: 'free' | 'paid' | 'reserved';
}

function freeUnitsOf(item: PublicTabItem): number {
  return Math.max(0, item.quantity - item.paidQuantity - item.reservedQuantity);
}

function unitsOf(item: PublicTabItem): readonly Unit[] {
  const free = freeUnitsOf(item);
  return Array.from({ length: item.quantity }, (_, index) => ({
    key: `${item.id}:${index}`,
    item,
    index,
    state: index < free ? 'free' : index < free + item.reservedQuantity ? 'reserved' : 'paid',
  }));
}

/** Mine first, then each name, then what the waiter posted. */
function groupsOf(items: readonly PublicTabItem[]): readonly { label: string; items: readonly PublicTabItem[] }[] {
  const mine = items.filter((item) => item.mine);
  const byName = new Map<string, PublicTabItem[]>();
  const byWaiter: PublicTabItem[] = [];
  for (const item of items) {
    if (item.mine) continue;
    if (item.customerName === null) {
      byWaiter.push(item);
    } else {
      byName.set(item.customerName, [...(byName.get(item.customerName) ?? []), item]);
    }
  }
  return [
    ...(mine.length > 0 ? [{ label: CUSTOMER.bill.yourItems, items: mine }] : []),
    ...[...byName.entries()].map(([label, list]) => ({ label, items: list })),
    ...(byWaiter.length > 0 ? [{ label: CUSTOMER.bill.byWaiter, items: byWaiter }] : []),
  ];
}

/** Unit keys (`itemId:index`) → how many units of each line. */
function quantitiesOf(keys: readonly string[]): readonly { tabItemId: string; quantity: number }[] {
  const quantities = new Map<string, number>();
  for (const key of keys) {
    const itemId = key.slice(0, key.lastIndexOf(':'));
    quantities.set(itemId, (quantities.get(itemId) ?? 0) + 1);
  }
  return [...quantities.entries()].map(([tabItemId, quantity]) => ({ tabItemId, quantity }));
}

function rateLabel(rate: string): string {
  return CUSTOMER.bill.serviceChargeRate(rate);
}

export interface PaymentChoice {
  readonly includeServiceCharge: boolean;
  readonly items: readonly { readonly tabItemId: string; readonly quantity: number }[];
}

/**
 * The table's bill, unit by unit (decision #26). The value of what is marked is
 * asked to the backend (#30); without that answer the screen shows only how many.
 */
export function BillScreen({
  orderingToken,
  customerDeviceId,
  onGeneratePix,
  isGenerating,
  generateError,
  hasPendingPix,
  onSeePendingPix,
}: {
  readonly orderingToken: string;
  readonly customerDeviceId: string;
  readonly onGeneratePix: (choice: PaymentChoice) => void;
  readonly isGenerating: boolean;
  readonly generateError: string | null;
  readonly hasPendingPix: boolean;
  readonly onSeePendingPix: () => void;
}) {
  const tab = usePublicTableTab(orderingToken, customerDeviceId);
  const [selected, setSelected] = useState<ReadonlySet<string>>(new Set());
  const [includeServiceCharge, setIncludeServiceCharge] = useState(true);

  let body;
  let data: PublicTableTab | null = null;
  if (tab.isPending) {
    body = <StateMessage role="status">{CUSTOMER.bill.loading}</StateMessage>;
  } else if (!tab.data || hasErrorCode(tab.error, 'TAB_NOT_FOUND')) {
    body = hasErrorCode(tab.error, 'TAB_NOT_FOUND') ? (
      <p className="ct-empty">{CUSTOMER.bill.empty}</p>
    ) : (
      <StateMessage role="alert" onRetry={() => void tab.refetch()}>
        {CUSTOMER.bill.failed} {describeCustomerError(tab.error)}
      </StateMessage>
    );
  } else {
    data = tab.data;
  }

  // A unit picked before the last read may have been paid or reserved since: it drops out.
  const freeKeys = new Set(
    (data?.items ?? []).flatMap((item) => unitsOf(item).filter((unit) => unit.state === 'free').map((u) => u.key)),
  );
  const chosen = [...selected].filter((key) => freeKeys.has(key));
  const chosenCount = chosen.length;
  const choiceItems = quantitiesOf(chosen);
  const { quote, isCalculating, unavailable } = usePaymentQuote(orderingToken, choiceItems, includeServiceCharge);

  function selectWhere(predicate: (item: PublicTabItem) => boolean) {
    setSelected(
      new Set(
        (data?.items ?? [])
          .filter(predicate)
          .flatMap((item) => unitsOf(item).filter((unit) => unit.state === 'free').map((unit) => unit.key)),
      ),
    );
  }

  function toggle(key: string) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(key)) {
        next.delete(key);
      } else {
        next.add(key);
      }
      return next;
    });
  }

  function handleGenerate() {
    if (isGenerating || chosenCount === 0) {
      return;
    }
    onGeneratePix({ includeServiceCharge, items: choiceItems });
  }

  if (data) {
    const groups = groupsOf(data.items);
    body = (
      <div className="ct-bill">
        <div className="ct-bill-stats">
          {[
            { label: CUSTOMER.bill.stats.total, value: data.total },
            { label: CUSTOMER.bill.stats.paid, value: data.paidAmount },
            { label: CUSTOMER.bill.stats.due, value: data.amountDue },
          ].map((stat) => (
            <div key={stat.label} className="ct-bill-stat">
              <span className="ct-bill-stat-label">{stat.label}</span>
              <span className="ct-bill-stat-value">{formatMoney(stat.value)}</span>
            </div>
          ))}
        </div>

        {data.status === 'CLOSING' && <p className="ct-notice-inline">{CUSTOMER.bill.closing}</p>}
        {hasPendingPix && (
          <div className="ct-notice-inline ct-notice-action">
            <span>{CUSTOMER.bill.pendingPix}</span>
            <button type="button" className="btn btn-ghost" onClick={onSeePendingPix}>
              {CUSTOMER.bill.seePendingPix}
            </button>
          </div>
        )}

        {data.items.length === 0 ? (
          <p className="ct-empty">{CUSTOMER.bill.empty}</p>
        ) : (
          <>
            <div className="ct-bill-intro">
              <p className="ct-lead">{CUSTOMER.bill.instructions}</p>
              <div className="ct-quick" role="group" aria-label={CUSTOMER.bill.quick}>
                <button type="button" className="ct-quick-btn" onClick={() => selectWhere((item) => item.mine)}>
                  {CUSTOMER.bill.onlyMine}
                </button>
                <button type="button" className="ct-quick-btn" onClick={() => selectWhere(() => true)}>
                  {CUSTOMER.bill.everythingDue}
                </button>
                <button type="button" className="ct-quick-btn" onClick={() => setSelected(new Set())}>
                  {CUSTOMER.bill.clear}
                </button>
              </div>
            </div>

            {groups.map((group) => (
              <section key={group.label} className="ct-bill-group">
                <h2 className="ct-section-title">
                  <span>❧ {group.label}</span>
                  <span className="ct-section-rule" aria-hidden="true" />
                </h2>
                {group.items.flatMap((item) =>
                  unitsOf(item).map((unit) => (
                    <UnitRow key={unit.key} unit={unit} isOn={chosen.includes(unit.key)} onToggle={() => toggle(unit.key)} />
                  )),
                )}
              </section>
            ))}

            <button
              type="button"
              className={includeServiceCharge ? 'ct-service is-on' : 'ct-service'}
              aria-pressed={includeServiceCharge}
              onClick={() => setIncludeServiceCharge((current) => !current)}
            >
              <span className="ct-check" aria-hidden="true">
                {includeServiceCharge && <CheckIcon />}
              </span>
              <span className="ct-service-label">
                {rateLabel(data.serviceChargeRate)} <span className="ct-muted">{CUSTOMER.bill.optional}</span>
              </span>
            </button>
          </>
        )}
      </div>
    );
  }

  const canPay = data !== null && data.acceptsPayment && data.items.length > 0;

  return (
    <>
      <div className="ct-body">{body}</div>
      {data && data.items.length > 0 && (
        <div className="ct-footer ct-footer-stack">
          {!data.acceptsPayment && (
            <p className="ct-notice-inline" role="status">
              {CUSTOMER.bill.pixDisabled}
            </p>
          )}
          {generateError && (
            <p className="ct-error-inline" role="alert">
              {generateError}
            </p>
          )}
          <div className="ct-pay-summary">
            <div className="ct-pay-summary-row">
              <span>
                {chosenCount === 0
                  ? CUSTOMER.bill.nothingSelected
                  : CUSTOMER.bill.selected(chosenCount, includeServiceCharge)}
              </span>
              {chosenCount > 0 && quote && !isCalculating && (
                <span className="ct-pay-amount">{formatMoney(quote.amount)}</span>
              )}
              {chosenCount > 0 && isCalculating && <span className="ct-muted">{CUSTOMER.bill.calculating}</span>}
            </div>
            {chosenCount > 0 && quote && !isCalculating && includeServiceCharge && (
              <span className="ct-muted">
                {CUSTOMER.bill.quoteBreakdown(formatMoney(quote.subtotal), formatMoney(quote.serviceCharge))}
              </span>
            )}
            {chosenCount > 0 && unavailable && <span className="ct-muted">{CUSTOMER.bill.valueAfter}</span>}
          </div>
          <button
            type="button"
            className="btn btn-primary btn-block ct-cta"
            onClick={handleGenerate}
            disabled={!canPay || chosenCount === 0 || isGenerating}
            aria-busy={isGenerating}
          >
            {isGenerating ? CUSTOMER.bill.generating : CUSTOMER.bill.generatePix}
          </button>
        </div>
      )}
    </>
  );
}

function CheckIcon() {
  return (
    <svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M20 6 9 17l-5-5" />
    </svg>
  );
}

function UnitRow({ unit, isOn, onToggle }: { readonly unit: Unit; readonly isOn: boolean; readonly onToggle: () => void }) {
  const { item } = unit;
  const locked = unit.state !== 'free';
  const name = [
    item.weightGrams !== null ? formatWeight(item.weightGrams) : null,
    item.itemName,
    item.variantName,
  ]
    .filter(Boolean)
    .join(' · ');

  return (
    <button
      type="button"
      className={`ct-unit ${locked ? 'is-locked' : ''} ${isOn ? 'is-on' : ''}`}
      onClick={onToggle}
      disabled={locked}
      aria-pressed={isOn}
    >
      <span className="ct-check" aria-hidden="true">
        {isOn && <CheckIcon />}
      </span>
      <span className="ct-unit-name">
        {name}
        {item.modifiers.length > 0 && (
          <span className="ct-unit-detail">
            {item.modifiers.map((modifier) => `+ ${modifier.quantity > 1 ? `${modifier.quantity}× ` : ''}${modifier.name}`).join(' ')}
          </span>
        )}
      </span>
      {locked && (
        <span className={unit.state === 'reserved' ? 'ct-unit-tag is-reserved' : 'ct-unit-tag'}>
          {unit.state === 'reserved' ? CUSTOMER.bill.reserved : CUSTOMER.bill.paid}
        </span>
      )}
      {/* The line's total from the backend, once per line: the front never divides money. */}
      {unit.index === 0 && <span className="ct-unit-price">{CUSTOMER.bill.lineTotal(formatMoney(item.lineTotal), item.quantity)}</span>}
    </button>
  );
}
