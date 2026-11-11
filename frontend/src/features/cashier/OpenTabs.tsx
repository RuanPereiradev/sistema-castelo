import { useState } from 'react';
import { formatMoney, formatWeight } from '../../lib/money';
import { useActiveTabs, type TabSummary } from '../dining-room/useActiveTabs';
import { activeItems, useTab, type Tab } from '../dining-room/useTab';
import { CASHIER } from './cashierMessages';
import '../../styles/cashier.css';

const TEXT = CASHIER.restaurantTabs;

function tabName(tab: Pick<TabSummary, 'diningTableLabel' | 'cardNumber'>): string {
  if (tab.diningTableLabel) {
    return TEXT.table(tab.diningTableLabel);
  }
  if (tab.cardNumber !== null) {
    return TEXT.card(tab.cardNumber);
  }
  return TEXT.unnamed;
}

/**
 * As comandas abertas, para a recepção acompanhar o salão (decision #17 of task-qr-table-ordering,
 * #19 of FE4). Read only: no button writes, no notice per order. Every figure comes from the API.
 */
export function OpenTabs() {
  const { data: tabs, isLoading, isError, refetch } = useActiveTabs();
  const [selectedTabId, setSelectedTabId] = useState<string | null>(null);

  return (
    <div className="folios-container">
      <div className="folios-list-section">
        <div className="folios-section">
          <h2 className="section-title">{TEXT.title}</h2>
          <p className="closing-instruction">{TEXT.readOnlyNote}</p>

          {isLoading && <p className="loading">{TEXT.loading}</p>}

          {!isLoading && !tabs && (
            <>
              <p className="error-message">{isError ? TEXT.failed : TEXT.loading}</p>
              <button className="btn btn-secondary" onClick={() => void refetch()}>
                {CASHIER.retry}
              </button>
            </>
          )}

          {tabs && tabs.length === 0 && <p className="empty-message">{TEXT.empty}</p>}

          {tabs && tabs.length > 0 && (
            <div className="folios-grid">
              {tabs.map((tab) => (
                <button
                  key={tab.id}
                  className={`folio-card ${selectedTabId === tab.id ? 'selected' : ''}`}
                  onClick={() => setSelectedTabId(tab.id)}
                >
                  <div className="folio-card-header">
                    <span className="folio-guest-name">{tabName(tab)}</span>
                    <span className="folio-id">{TEXT.status[tab.status]}</span>
                  </div>
                  <div className="folio-card-charges">
                    <span className="charge-count">{TEXT.itemCount(tab.activeItemCount)}</span>
                  </div>
                  <div className="folio-card-balance">
                    <span className="balance-value pending">{formatMoney(tab.total)}</span>
                  </div>
                </button>
              ))}
            </div>
          )}
        </div>
      </div>

      {selectedTabId === null ? (
        <div className="folio-detail-section">
          <p className="empty-message">{TEXT.choose}</p>
        </div>
      ) : (
        <TabDetail key={selectedTabId} tabId={selectedTabId} />
      )}
    </div>
  );
}

function TabDetail({ tabId }: { readonly tabId: string }) {
  const { data: tab, isLoading, isError, refetch } = useTab(tabId);

  if (isLoading) {
    return (
      <div className="folio-detail-section">
        <p className="loading">{TEXT.loadingTab}</p>
      </div>
    );
  }

  if (!tab) {
    return (
      <div className="folio-detail-section">
        <p className="error-message">{isError ? TEXT.tabFailed : TEXT.loadingTab}</p>
        <button className="btn btn-secondary" onClick={() => void refetch()}>
          {CASHIER.retry}
        </button>
      </div>
    );
  }

  return <TabView tab={tab} />;
}

function TabView({ tab }: { readonly tab: Tab }) {
  const items = activeItems(tab);

  return (
    <div className="folio-detail-section">
      <div className="folio-detail-header">
        <div>
          <h3>{tabName(tab)}</h3>
          <p className="folio-reference">
            {TEXT.openedAt} {new Date(tab.openedAt).toLocaleString('pt-BR')}
            {tab.guestCount !== null && ` · ${TEXT.guestCount}: ${tab.guestCount}`}
          </p>
        </div>
        <span className={`folio-status ${tab.status === 'OPEN' ? 'open' : 'closed'}`}>{TEXT.status[tab.status]}</span>
      </div>

      <div className="folio-charges">
        {items.length === 0 ? (
          <p className="empty-message">{TEXT.noItems}</p>
        ) : (
          <table className="charges-table">
            <thead>
              <tr>
                <th>{TEXT.columns.item}</th>
                <th>{TEXT.columns.quantity}</th>
                <th>{TEXT.columns.status}</th>
                <th>{TEXT.columns.amount}</th>
              </tr>
            </thead>
            <tbody>
              {items.map((item) => (
                <tr key={item.id}>
                  <td>
                    {item.itemName}
                    {item.variantName && ` (${item.variantName})`}
                    {item.modifiers.length > 0 && (
                      <div className="folio-reference">
                        {item.modifiers
                          .map((modifier) => (modifier.quantity > 1 ? `${modifier.quantity}× ${modifier.name}` : modifier.name))
                          .join(', ')}
                      </div>
                    )}
                    {item.specialInstructions && <div className="folio-reference">{item.specialInstructions}</div>}
                  </td>
                  <td>{item.weightGrams !== null ? formatWeight(item.weightGrams) : item.quantity}</td>
                  <td>{TEXT.itemStatus[item.status]}</td>
                  <td className="amount">{formatMoney(item.lineTotal)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="folio-balance">
        <div className="balance-row">
          <span>{TEXT.subtotal}</span>
          <span>{formatMoney(tab.subtotal)}</span>
        </div>
        <div className="balance-row">
          <span>{TEXT.serviceCharge}</span>
          <span>{formatMoney(tab.serviceCharge)}</span>
        </div>
        <div className="balance-row total">
          <span>{TEXT.total}</span>
          <span>{formatMoney(tab.total)}</span>
        </div>
      </div>
    </div>
  );
}
