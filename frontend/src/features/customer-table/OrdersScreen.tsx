import { useEffect, useState } from 'react';
import { formatMoney, formatWeight } from '../../lib/money';
import {
  useOrderRequestStatus,
  usePublicTableTab,
  type PublicTabItem,
  type TabItemStatus,
} from './customerApi';
import { CUSTOMER, describeCustomerError, hasErrorCode } from './customerMessages';
import { StateMessage } from './CustomerChrome';

const STEP_OF: Readonly<Record<TabItemStatus, number>> = {
  PENDING: 0,
  IN_PREPARATION: 1,
  READY: 2,
  DELIVERED: 3,
};

/** Clock time of a server instant, for display only. */
function clockTime(instant: string): string {
  return new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(new Date(instant));
}

export function OrdersScreen({
  orderingToken,
  customerDeviceId,
  lastRequestId,
  onForgetRequest,
}: {
  readonly orderingToken: string;
  readonly customerDeviceId: string;
  readonly lastRequestId: string | null;
  readonly onForgetRequest: () => void;
}) {
  const [mineOnly, setMineOnly] = useState(false);
  const tab = usePublicTableTab(orderingToken, customerDeviceId);

  let items;
  if (tab.isPending) {
    items = <StateMessage role="status">{CUSTOMER.orders.loading}</StateMessage>;
  } else if (tab.isError && !tab.data) {
    items = hasErrorCode(tab.error, 'TAB_NOT_FOUND') ? (
      <p className="ct-empty">{mineOnly ? CUSTOMER.orders.emptyMine : CUSTOMER.orders.emptyTable}</p>
    ) : (
      <StateMessage role="alert" onRetry={() => void tab.refetch()}>
        {CUSTOMER.orders.failed} {describeCustomerError(tab.error)}
      </StateMessage>
    );
  } else {
    // A tab that vanished (closed and freed) answers TAB_NOT_FOUND on the next read.
    const gone = hasErrorCode(tab.error, 'TAB_NOT_FOUND');
    const visible = gone ? [] : tab.data.items.filter((item) => !mineOnly || item.mine);
    // Newest first, as the design orders its cards; served items sink to the end.
    const ordered = [...visible]
      .reverse()
      .sort((a, b) => Number(a.status === 'DELIVERED') - Number(b.status === 'DELIVERED'));
    items = (
      <>
        {!gone && tab.data.status === 'CLOSING' && (
          <p className="ct-notice-inline" role="status">
            {CUSTOMER.orders.closing}
          </p>
        )}
        {ordered.length === 0 ? (
          <p className="ct-empty">{mineOnly ? CUSTOMER.orders.emptyMine : CUSTOMER.orders.emptyTable}</p>
        ) : (
          ordered.map((item) => <TabItemCard key={item.id} item={item} />)
        )}
      </>
    );
  }

  return (
    <div className="ct-body">
      <div className="ct-orders">
        <div className="ct-segment" role="group" aria-label={CUSTOMER.orders.filter}>
          {[
            { value: false, label: CUSTOMER.orders.all },
            { value: true, label: CUSTOMER.orders.mine },
          ].map((option) => (
            <button
              key={option.label}
              type="button"
              className={mineOnly === option.value ? 'ct-segment-option is-active' : 'ct-segment-option'}
              aria-pressed={mineOnly === option.value}
              onClick={() => setMineOnly(option.value)}
            >
              {option.label}
            </button>
          ))}
        </div>
        {lastRequestId && (
          <LastRequestCard orderingToken={orderingToken} requestId={lastRequestId} onForget={onForgetRequest} />
        )}
        {items}
      </div>
    </div>
  );
}

function LastRequestCard({
  orderingToken,
  requestId,
  onForget,
}: {
  readonly orderingToken: string;
  readonly requestId: string;
  readonly onForget: () => void;
}) {
  const request = useOrderRequestStatus(orderingToken, requestId);
  const unknown = hasErrorCode(request.error, 'TABLE_ORDER_REQUEST_NOT_FOUND');

  useEffect(() => {
    if (unknown) {
      onForget();
    }
  }, [unknown, onForget]);

  if (unknown) {
    return null;
  }
  if (request.isPending) {
    return <StateMessage role="status">{CUSTOMER.orders.loading}</StateMessage>;
  }
  if (!request.data) {
    return (
      <StateMessage role="alert" onRetry={() => void request.refetch()}>
        {describeCustomerError(request.error)}
      </StateMessage>
    );
  }

  const { status, requestedAt } = request.data;
  return (
    <div className={`ct-card ct-request is-${status.toLowerCase()}`} role="status">
      <span className="ct-card-eyebrow">{CUSTOMER.orders.request.title(clockTime(requestedAt))}</span>
      <span className="ct-card-status">{CUSTOMER.orders.request[status]}</span>
      <p className="ct-card-hint">{CUSTOMER.orders.request.hint[status]}</p>
    </div>
  );
}

function TabItemCard({ item }: { readonly item: PublicTabItem }) {
  const step = STEP_OF[item.status];
  const who = item.mine ? CUSTOMER.orders.you : (item.customerName ?? CUSTOMER.orders.byWaiter);
  const amount = item.weightGrams !== null ? formatWeight(item.weightGrams) : `${item.quantity}×`;

  return (
    <div className={item.status === 'READY' ? 'ct-card is-ready' : 'ct-card'}>
      <div className="ct-card-top">
        <span className="ct-card-eyebrow">{who}</span>
        <span className="ct-card-money">{formatMoney(item.lineTotal)}</span>
      </div>
      <span className="ct-card-status">{CUSTOMER.orders.itemStatus[item.status]}</span>
      <ol className="ct-tracker">
        {CUSTOMER.orders.steps.map((label, index) => (
          <li
            key={label}
            className={index === step ? 'is-current' : index < step ? 'is-done' : undefined}
            aria-current={index === step ? 'step' : undefined}
          >
            <span className="ct-tracker-bar" aria-hidden="true" />
            <span className="ct-tracker-label">{label}</span>
          </li>
        ))}
      </ol>
      <div className="ct-card-lines">
        <div className="ct-card-line">
          <span className="ct-card-quantity">{amount}</span>
          <span className="ct-card-name">
            {item.itemName}
            {item.variantName && ` · ${item.variantName}`}
          </span>
        </div>
        {item.modifiers.map((modifier) => (
          <div key={modifier.name} className="ct-card-line ct-card-modifier">
            <span className="ct-card-quantity" />
            <span className="ct-card-name">
              + {modifier.quantity > 1 ? `${modifier.quantity}× ` : ''}
              {modifier.name}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}
