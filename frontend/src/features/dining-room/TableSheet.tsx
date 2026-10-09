import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { formatMoney, formatWeight } from '../../lib/money';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { DINING_ROOM, describeError } from './diningRoomMessages';
import { DINING_TABLES_KEY, type DiningTable } from './useDiningTables';
import { ACTIVE_TABS_KEY, type TabSummary } from './useActiveTabs';
import { activeItems, tabKey, useOpenTab, useTab, type Tab, type TabItem } from './useTab';
import { useCancelTab, useReopenTab, useStartClosing } from './useTabActions';
import { minutesSince, statusLabel, statusOf } from './tableStatus';
import { AddItemModal } from './AddItemModal';
import { CancelItemDialog } from './CancelItemDialog';
import { ConfirmDialog } from './ConfirmDialog';
import { TransferItemsDialog } from './TransferItemsDialog';
import { MergeTabDialog } from './MergeTabDialog';
import { MoveTableDialog } from './MoveTableDialog';

interface Props {
  readonly table: DiningTable;
  readonly areaLabel: string;
  /** The active tab of the table, when it has one. Absent means the table is free. */
  readonly summary: TabSummary | undefined;
  readonly now: number;
  readonly onClose: () => void;
  readonly onNotice: (message: string) => void;
}

const MIN_GUESTS = 1;
const MAX_GUESTS = 99;

function CloseIcon() {
  return (
    <svg
      width="20"
      height="20"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M18 6 6 18" />
      <path d="m6 6 12 12" />
    </svg>
  );
}

function Ornament() {
  return (
    <div className="dr-divider" aria-hidden="true">
      <span className="dr-divider-line" />
      <span className="dr-divider-ornament">❧</span>
      <span className="dr-divider-line" />
    </div>
  );
}

function quantityOf(item: TabItem): string {
  return item.weightGrams !== null ? formatWeight(item.weightGrams) : `${item.quantity}×`;
}

/**
 * The bottom sheet of one table: facts, the lines of the tab with their totals,
 * and the actions its status allows. Every action re-reads the server afterwards.
 */
export function TableSheet({ table, areaLabel, summary, now, onClose, onNotice }: Props) {
  const queryClient = useQueryClient();
  const status = statusOf(summary);
  const tabId = summary?.id ?? null;
  const tabQuery = useTab(tabId);

  const [guestCount, setGuestCount] = useState(() =>
    Math.min(Math.max(table.seats ?? 2, MIN_GUESTS), MAX_GUESTS),
  );
  const [moreActions, setMoreActions] = useState(false);
  const [dialog, setDialog] = useState<
    'none' | 'add-item' | 'request-bill' | 'cancel-bill' | 'cancel-tab' | 'transfer' | 'merge' | 'move' | 'cancel-item'
  >('none');
  const [reason, setReason] = useState('');
  const [cancellingItemId, setCancellingItemId] = useState<string | null>(null);

  const apiCall = useAuthorizedRequest();
  const openTab = useOpenTab();
  const startClosing = useStartClosing(tabId ?? '');
  const reopenTab = useReopenTab(tabId ?? '');
  const cancelTab = useCancelTab(tabId ?? '');

  async function refreshAll(): Promise<void> {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ACTIVE_TABS_KEY }),
      queryClient.invalidateQueries({ queryKey: DINING_TABLES_KEY }),
      tabId ? queryClient.invalidateQueries({ queryKey: tabKey(tabId) }) : Promise.resolve(),
    ]);
  }

  function closeDialog() {
    setDialog('none');
    setReason('');
  }

  /**
   * Opening takes two calls: the open itself, then the guest count, which the open
   * body does not carry. A failed count does not undo the open — the table is
   * taken either way — so it only shows its own message.
   */
  async function handleOpenTable() {
    let opened: Tab;
    try {
      opened = await openTab.mutateAsync({ origin: 'TABLE_SERVICE', diningTableId: table.id });
    } catch (error) {
      onNotice(describeError(error));
      await refreshAll();
      return;
    }
    queryClient.setQueryData(tabKey(opened.id), opened);
    try {
      await apiCall<unknown>(`/restaurant/tabs/${opened.id}/guest-count`, {
        method: 'PUT',
        body: { guestCount },
      });
      onNotice(DINING_ROOM.notice.tableOpened(table.label));
    } catch (error) {
      onNotice(describeError(error));
    } finally {
      await refreshAll();
    }
  }

  async function run(action: () => Promise<unknown>, successMessage: string) {
    try {
      await action();
      closeDialog();
      onNotice(successMessage);
    } catch (error) {
      onNotice(describeError(error));
    } finally {
      await refreshAll();
    }
  }

  const tab: Tab | undefined = tabQuery.data;
  const lines = tab ? activeItems(tab) : [];
  const anyReady = lines.some((item) => item.status === 'READY');
  const busy =
    openTab.isPending || startClosing.isPending || reopenTab.isPending || cancelTab.isPending;

  return (
    <>
      <div className="dr-modal-backdrop" onClick={onClose} />
      <div className="dr-sheet" role="dialog" aria-modal="true" aria-labelledby="dr-sheet-title">
        <div className="dr-sheet-content">
          <div className="dr-sheet-header">
            <div className="dr-sheet-subtitle">
              {areaLabel} · {statusLabel(status)}
            </div>
            <h2 id="dr-sheet-title" className="dr-sheet-title">
              {DINING_ROOM.table} {table.label}
            </h2>
            <button
              type="button"
              className="dr-sheet-close"
              onClick={onClose}
              aria-label={DINING_ROOM.close}
            >
              <CloseIcon />
            </button>
          </div>

          <Ornament />

          {status === 'free' ? (
            <>
              <div className="dr-sheet-facts">
                {table.seats !== null && (
                  <span>
                    <span className="dr-fact-label">{DINING_ROOM.seats}</span> {table.seats}
                  </span>
                )}
              </div>

              <div className="dr-stepper" role="group" aria-label={DINING_ROOM.guests}>
                <span className="dr-stepper-label">{DINING_ROOM.guests}</span>
                <button
                  type="button"
                  className="dr-stepper-btn"
                  onClick={() => setGuestCount((n) => Math.max(MIN_GUESTS, n - 1))}
                  disabled={guestCount <= MIN_GUESTS || busy}
                  aria-label="Menos uma pessoa"
                >
                  −
                </button>
                <span className="dr-stepper-value" aria-live="polite">{guestCount}</span>
                <button
                  type="button"
                  className="dr-stepper-btn"
                  onClick={() => setGuestCount((n) => Math.min(MAX_GUESTS, n + 1))}
                  disabled={guestCount >= MAX_GUESTS || busy}
                  aria-label="Mais uma pessoa"
                >
                  +
                </button>
              </div>

              <div className="dr-sheet-actions">
                <button
                  type="button"
                  className="dr-action-btn dr-action-primary"
                  onClick={() => void handleOpenTable()}
                  disabled={busy}
                >
                  {openTab.isPending ? DINING_ROOM.opening : DINING_ROOM.openTable}
                </button>
                <button type="button" className="dr-action-btn dr-action-ghost" onClick={onClose}>
                  {DINING_ROOM.close}
                </button>
              </div>
            </>
          ) : tabQuery.isPending ? (
            <p className="dr-state" role="status">{DINING_ROOM.loadingTab}</p>
          ) : tabQuery.error || !tab ? (
            <div className="dr-state" role="alert">
              <p>{DINING_ROOM.tabFailed}</p>
              <p className="dr-state-detail">{describeError(tabQuery.error)}</p>
              <button
                type="button"
                className="dr-action-btn dr-action-secondary"
                onClick={() => void tabQuery.refetch()}
              >
                {DINING_ROOM.retry}
              </button>
            </div>
          ) : (
            <>
              <div className="dr-sheet-facts">
                <span>
                  <span className="dr-fact-label">{DINING_ROOM.guests}</span>{' '}
                  {tab.guestCount ?? '—'}
                </span>
                <span>
                  <span className="dr-fact-label">{DINING_ROOM.openFor}</span>{' '}
                  {minutesSince(tab.openedAt, now)} {DINING_ROOM.minutes}
                </span>
                <span>
                  <span className="dr-fact-label">{DINING_ROOM.items}</span> {lines.length}
                </span>
              </div>

              {lines.length === 0 ? (
                <p className="dr-empty">{DINING_ROOM.noItems}</p>
              ) : (
                <div className="dr-sheet-items">
                  {lines.map((item) => (
                    <div key={item.id}>
                      <div className={`dr-sheet-item status-${item.status.toLowerCase()}`}>
                        <span className="dr-sheet-item-qty">{quantityOf(item)}</span>
                        <span className="dr-sheet-item-body">
                          <span className="dr-sheet-item-name">
                            {item.itemName}
                            {item.variantName && ` (${item.variantName})`}
                          </span>
                          {item.modifiers.length > 0 && (
                            <span className="dr-sheet-item-detail">
                              {item.modifiers
                                .map((m) => `+ ${m.quantity > 1 ? `${m.quantity}× ` : ''}${m.name}`)
                                .join(' · ')}
                            </span>
                          )}
                          {item.specialInstructions && (
                            <span className="dr-sheet-item-detail dr-sheet-item-note">
                              {item.specialInstructions}
                            </span>
                          )}
                          {item.status !== 'DELIVERED' && (
                            <span className={`dr-sheet-item-status status-${item.status.toLowerCase()}`}>
                              {DINING_ROOM.itemStatus[item.status] ?? item.status}
                            </span>
                          )}
                        </span>
                        <span className="dr-sheet-item-price">{formatMoney(item.lineTotal)}</span>
                      </div>
                      {item.status !== 'DELIVERED' && item.status !== 'CANCELLED' && (
                        <div style={{ display: 'flex', gap: '8px', padding: '8px 0', justifyContent: 'flex-end' }}>
                          {item.status === 'READY' && (
                            <button
                              type="button"
                              className="dr-action-btn dr-action-small"
                              onClick={() => {
                                setCancellingItemId(item.id);
                                setDialog('cancel-item');
                              }}
                            >
                              ✓ Entregar
                            </button>
                          )}
                          <button
                            type="button"
                            className="dr-action-btn dr-action-small"
                            onClick={() => {
                              setCancellingItemId(item.id);
                              setDialog('cancel-item');
                            }}
                          >
                            ✕ Cancelar
                          </button>
                        </div>
                      )}
                    </div>
                  ))}
                  <div className="dr-sheet-summary">
                    <span>{DINING_ROOM.subtotal}</span>
                    <span>{formatMoney(tab.subtotal)}</span>
                  </div>
                  {tab.serviceChargeApplied && (
                    <div className="dr-sheet-summary">
                      <span>
                        {DINING_ROOM.serviceCharge} ({tab.serviceChargeRate}%)
                      </span>
                      <span>{formatMoney(tab.serviceCharge)}</span>
                    </div>
                  )}
                  <div className="dr-sheet-total">
                    <span className="dr-sheet-total-label">{DINING_ROOM.total}</span>
                    <span className="dr-sheet-total-value">{formatMoney(tab.total)}</span>
                  </div>
                </div>
              )}

              {status === 'billRequested' && (
                <p className="dr-sheet-note">{DINING_ROOM.billWithCashier}</p>
              )}
              {anyReady && <p className="dr-sheet-note">{DINING_ROOM.readyInKitchen}</p>}

              <div className="dr-sheet-actions">
                {status === 'occupied' ? (
                  <>
                    <button
                      type="button"
                      className="dr-action-btn dr-action-primary"
                      onClick={() => setDialog('add-item')}
                      disabled={busy}
                    >
                      {DINING_ROOM.addOrder}
                    </button>
                    <button
                      type="button"
                      className="dr-action-btn dr-action-secondary"
                      onClick={() => setDialog('request-bill')}
                      disabled={busy}
                    >
                      {DINING_ROOM.requestBill}
                    </button>
                    <button
                      type="button"
                      className="dr-action-btn dr-action-ghost"
                      onClick={() => setMoreActions((open) => !open)}
                      aria-expanded={moreActions}
                    >
                      {moreActions ? DINING_ROOM.fewerActions : DINING_ROOM.moreActions}
                    </button>
                    {moreActions && (
                      <div className="dr-more-actions">
                        <button
                          type="button"
                          className="dr-action-btn dr-action-secondary"
                          onClick={() => setDialog('transfer')}
                          disabled={busy || lines.length === 0}
                        >
                          {DINING_ROOM.transfer}
                        </button>
                        <button
                          type="button"
                          className="dr-action-btn dr-action-secondary"
                          onClick={() => setDialog('merge')}
                          disabled={busy}
                        >
                          {DINING_ROOM.merge}
                        </button>
                        <button
                          type="button"
                          className="dr-action-btn dr-action-secondary"
                          onClick={() => setDialog('move')}
                          disabled={busy}
                        >
                          {DINING_ROOM.move}
                        </button>
                        <button
                          type="button"
                          className="dr-action-btn dr-action-danger"
                          onClick={() => setDialog('cancel-tab')}
                          disabled={busy}
                        >
                          {DINING_ROOM.cancelTab}
                        </button>
                      </div>
                    )}
                  </>
                ) : (
                  <button
                    type="button"
                    className="dr-action-btn dr-action-secondary"
                    onClick={() => setDialog('cancel-bill')}
                    disabled={busy}
                  >
                    {DINING_ROOM.cancelBillRequest}
                  </button>
                )}
                <button type="button" className="dr-action-btn dr-action-ghost" onClick={onClose}>
                  {DINING_ROOM.back}
                </button>
              </div>
            </>
          )}
        </div>
      </div>

      {tab && (
        <>
          <AddItemModal
            isOpen={dialog === 'add-item'}
            tab={tab}
            onClose={closeDialog}
            onAdded={(name) => {
              closeDialog();
              onNotice(DINING_ROOM.notice.itemAdded(name));
              void refreshAll();
            }}
          />

          <ConfirmDialog
            isOpen={dialog === 'request-bill'}
            title={DINING_ROOM.requestBillTitle}
            message={DINING_ROOM.requestBillMessage}
            confirmLabel={DINING_ROOM.requestBillConfirm}
            cancelLabel={DINING_ROOM.keep}
            isLoading={startClosing.isPending}
            onConfirm={() =>
              void run(() => startClosing.mutateAsync(), DINING_ROOM.notice.billSent(table.label))
            }
            onCancel={closeDialog}
          />

          <ConfirmDialog
            isOpen={dialog === 'cancel-bill'}
            title={DINING_ROOM.cancelBillRequestTitle}
            message={DINING_ROOM.cancelBillRequestMessage}
            requiresReason
            reason={reason}
            onReasonChange={setReason}
            confirmLabel={DINING_ROOM.cancelBillRequestConfirm}
            cancelLabel={DINING_ROOM.keep}
            isLoading={reopenTab.isPending}
            onConfirm={() =>
              void run(
                () => reopenTab.mutateAsync(reason),
                DINING_ROOM.notice.billCancelled(table.label),
              )
            }
            onCancel={closeDialog}
          />

          <ConfirmDialog
            isOpen={dialog === 'cancel-tab'}
            title={DINING_ROOM.cancelTabTitle}
            message={DINING_ROOM.cancelTabMessage}
            requiresReason
            reason={reason}
            onReasonChange={setReason}
            confirmLabel={DINING_ROOM.cancelTabConfirm}
            cancelLabel={DINING_ROOM.keep}
            isDanger
            isLoading={cancelTab.isPending}
            onConfirm={() =>
              void run(
                () => cancelTab.mutateAsync(reason),
                DINING_ROOM.notice.tabCancelled(table.label),
              )
            }
            onCancel={closeDialog}
          />

          {cancellingItemId && (
            <CancelItemDialog
              isOpen={dialog === 'cancel-item'}
              tab={tab}
              itemId={cancellingItemId}
              itemName={tab.items.find((i) => i.id === cancellingItemId)?.itemName ?? ''}
              onClose={() => {
                closeDialog();
                setCancellingItemId(null);
              }}
              onCancelled={() => {
                closeDialog();
                setCancellingItemId(null);
                onNotice(DINING_ROOM.notice.itemCancelled);
                void refreshAll();
              }}
            />
          )}

          <TransferItemsDialog
            isOpen={dialog === 'transfer'}
            tab={tab}
            onClose={closeDialog}
            onDone={() => {
              closeDialog();
              onNotice(DINING_ROOM.notice.itemsTransferred);
              void refreshAll();
            }}
          />
          <MergeTabDialog
            isOpen={dialog === 'merge'}
            tab={tab}
            onClose={closeDialog}
            onDone={() => {
              onNotice(DINING_ROOM.notice.tabsMerged);
              void refreshAll();
              onClose();
            }}
          />
          <MoveTableDialog
            isOpen={dialog === 'move'}
            tab={tab}
            onClose={closeDialog}
            onDone={(destinationLabel) => {
              onNotice(DINING_ROOM.notice.tableMoved(destinationLabel));
              void refreshAll();
              onClose();
            }}
          />
        </>
      )}
    </>
  );
}
