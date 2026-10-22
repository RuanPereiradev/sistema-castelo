import { useEffect, useMemo, useState } from 'react';
import { currentSession } from '../auth/session';
import { DINING_ROOM, describeError } from './diningRoomMessages';
import { useDiningTables, type DiningTable } from './useDiningTables';
import { tabsByTable, useActiveTabs, type TabSummary } from './useActiveTabs';
import {
  TABLE_STATUSES,
  minutesSince,
  statusClass,
  statusLabel,
  statusOf,
  type TableStatus,
} from './tableStatus';
import { TableSheet } from './TableSheet';
import { TableOrderRequestsSheet } from './TableOrderRequestsSheet';
import { requestsByTable, useNewRequestChime, useTableOrderRequests } from './useTableOrderRequests';
import { useTablePaymentRequests } from './useTablePaymentRequests';
import '../../styles/dining-room-portal.css';

type StatusFilter = TableStatus | 'all';

interface Area {
  readonly key: string;
  readonly label: string;
}

/** Tables without an area share one group, listed last. */
function areaOf(table: DiningTable): Area {
  return table.area
    ? { key: table.area, label: table.area }
    : { key: '', label: DINING_ROOM.noArea };
}

function areasOf(tables: readonly DiningTable[]): readonly Area[] {
  const byKey = new Map<string, Area>();
  for (const table of tables) {
    const area = areaOf(table);
    byKey.set(area.key, area);
  }
  return [...byKey.values()].sort((a, b) => {
    if (a.key === '') return 1;
    if (b.key === '') return -1;
    return a.label.localeCompare(b.label, 'pt-BR');
  });
}

function clockOf(now: number): string {
  return new Date(now).toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' });
}

/** Re-renders twice a minute so the clock and the "open for N min" stay honest. */
function useNow(): number {
  const [now, setNow] = useState(() => Date.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 30_000);
    return () => clearInterval(timer);
  }, []);
  return now;
}

function tableMeta(table: DiningTable, tab: TabSummary | undefined, now: number): string {
  if (!tab) {
    return table.seats === null ? '' : `${table.seats} ${DINING_ROOM.seats}`;
  }
  const items = `${tab.activeItemCount} ${tab.activeItemCount === 1 ? 'item' : 'itens'}`;
  return `${items} · ${minutesSince(tab.openedAt, now)} ${DINING_ROOM.minutes}`;
}

/**
 * The map of the room for the waiter: every active table, occupied when it has
 * an active tab, with the tab's consumption on tap. Portal design, phone first.
 */
export function DiningRoomPage() {
  const tablesQuery = useDiningTables();
  const tabsQuery = useActiveTabs();
  const requestsQuery = useTableOrderRequests();
  const paymentsQuery = useTablePaymentRequests();
  useNewRequestChime(requestsQuery.data?.map((request) => request.id));
  useNewRequestChime(paymentsQuery.data?.map((request) => request.id));
  const now = useNow();

  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('all');
  const [areaKey, setAreaKey] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  /** The phone-requests sheet: closed, every table, or one table's id. */
  const [requestsFor, setRequestsFor] = useState<'all' | string | null>(null);

  useEffect(() => {
    if (!notice) return;
    const timer = setTimeout(() => setNotice(null), 2600);
    return () => clearTimeout(timer);
  }, [notice]);

  const tables = useMemo(
    () => (tablesQuery.data ?? []).filter((table) => table.isActive),
    [tablesQuery.data],
  );
  const tabByTable = useMemo(() => tabsByTable(tabsQuery.data ?? []), [tabsQuery.data]);
  const requestByTable = useMemo(() => requestsByTable(requestsQuery.data ?? []), [requestsQuery.data]);
  const pixByTable = useMemo(() => {
    const byTable = new Map<string, number>();
    for (const request of paymentsQuery.data ?? []) {
      byTable.set(request.diningTableId, (byTable.get(request.diningTableId) ?? 0) + 1);
    }
    return byTable;
  }, [paymentsQuery.data]);
  const pendingRequestCount = (requestsQuery.data?.length ?? 0) + (paymentsQuery.data?.length ?? 0);
  const areas = useMemo(() => areasOf(tables), [tables]);
  const currentArea = areas.find((area) => area.key === areaKey) ?? areas[0] ?? null;

  const userInitial = (currentSession()?.user.fullName ?? '?').trim().charAt(0).toUpperCase();

  if (tablesQuery.isPending || tabsQuery.isPending) {
    return (
      <div className="dining-room-portal">
        <p className="dr-state" role="status">{DINING_ROOM.loadingTables}</p>
      </div>
    );
  }

  const failure = tablesQuery.error ?? tabsQuery.error;
  if (failure && (!tablesQuery.data || !tabsQuery.data)) {
    return (
      <div className="dining-room-portal">
        <div className="dr-state" role="alert">
          <p>{DINING_ROOM.tablesFailed}</p>
          <p className="dr-state-detail">{describeError(failure)}</p>
          <button
            type="button"
            className="dr-action-btn dr-action-secondary"
            onClick={() => {
              void tablesQuery.refetch();
              void tabsQuery.refetch();
            }}
          >
            {DINING_ROOM.retry}
          </button>
        </div>
      </div>
    );
  }

  if (tables.length === 0) {
    return (
      <div className="dining-room-portal">
        <p className="dr-state">{DINING_ROOM.noTables}</p>
      </div>
    );
  }

  const counts: Record<StatusFilter, number> = { all: 0, free: 0, occupied: 0, billRequested: 0 };
  for (const table of tables) {
    counts.all += 1;
    counts[statusOf(tabByTable.get(table.id))] += 1;
  }

  const visibleTables = tables.filter((table) => {
    if (currentArea && areaOf(table).key !== currentArea.key) return false;
    if (filter !== 'all' && statusOf(tabByTable.get(table.id)) !== filter) return false;
    return true;
  });

  const selectedTable = tables.find((table) => table.id === selectedTableId) ?? null;
  const sectionTitle =
    (currentArea?.label ?? DINING_ROOM.allTables) +
    (filter === 'all' ? '' : ` · ${statusLabel(filter)}`);

  return (
    <div className="dining-room-portal">
      <header className="dr-header">
        <div className="dr-header-top">
          <div className="dr-brand-mark" aria-hidden="true">{DINING_ROOM.brand.charAt(0)}</div>
          <div className="dr-header-title">
            <div className="dr-brand-info">
              {DINING_ROOM.brand} · {clockOf(now)}
            </div>
            <h1 className="dr-page-title">{DINING_ROOM.title}</h1>
          </div>
          <div className="dr-avatar" aria-hidden="true">{userInitial}</div>
        </div>

        <button
          type="button"
          className={`dr-requests-btn ${pendingRequestCount > 0 ? 'has-pending' : ''}`}
          onClick={() => setRequestsFor('all')}
        >
          {pendingRequestCount > 0
            ? DINING_ROOM.orderRequests.openWithCount(pendingRequestCount)
            : DINING_ROOM.orderRequests.open}
        </button>

        {areas.length > 1 && (
          <div
            className="dr-area-filters"
            style={{ gridTemplateColumns: `repeat(${areas.length}, 1fr)` }}
          >
            {areas.map((area) => (
              <button
                key={area.key}
                type="button"
                className={`dr-area-btn ${currentArea?.key === area.key ? 'active' : ''}`}
                onClick={() => {
                  setAreaKey(area.key);
                  setFilter('all');
                }}
              >
                {area.label} · {tables.filter((t) => areaOf(t).key === area.key).length}
              </button>
            ))}
          </div>
        )}
      </header>

      <div className="dr-chips">
        {(['all', ...TABLE_STATUSES] as const).map((key) => (
          <button
            key={key}
            type="button"
            className={`dr-chip ${filter === key ? 'active' : ''}`}
            onClick={() => setFilter(key)}
          >
            <span className={`dr-chip-indicator ${key === 'all' ? 'all' : statusClass(key)}`} />
            {key === 'all' ? DINING_ROOM.allTables : statusLabel(key)}{' '}
            <span className="dr-chip-count">{counts[key]}</span>
          </button>
        ))}
      </div>

      <div className="dr-content">
        <div className="dr-section-header">
          <div className="dr-section-title">{sectionTitle}</div>
        </div>

        {visibleTables.length === 0 ? (
          <p className="dr-empty">{DINING_ROOM.noTablesInFilter}</p>
        ) : (
          <div className="dr-table-grid">
            {visibleTables.map((table) => {
              const tab = tabByTable.get(table.id);
              const status = statusOf(tab);
              const orders = requestByTable.get(table.id)?.length ?? 0;
              const pix = pixByTable.get(table.id) ?? 0;
              const waiting = orders + pix;
              const badge = DINING_ROOM.orderRequests.badgeFor(orders, pix);
              return (
                <button
                  key={table.id}
                  type="button"
                  className={`dr-table-btn ${statusClass(status)} ${waiting > 0 ? 'has-request' : ''}`}
                  onClick={() => setSelectedTableId(table.id)}
                  aria-label={`${DINING_ROOM.table} ${table.label}, ${statusLabel(status)}${waiting > 0 ? `, ${badge}` : ''}`}
                >
                  {waiting > 0 && (
                    <span className="dr-table-request-badge" aria-hidden="true">
                      {badge}
                    </span>
                  )}
                  <span className="dr-table-number">{table.label}</span>
                  <span className="dr-table-info">
                    <span className="dr-table-label">{statusLabel(status)}</span>
                    <span className="dr-table-meta">{tableMeta(table, tab, now)}</span>
                  </span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {selectedTable && (
        <TableSheet
          key={selectedTable.id}
          table={selectedTable}
          areaLabel={areaOf(selectedTable).label}
          summary={tabByTable.get(selectedTable.id)}
          now={now}
          pendingRequestCount={requestByTable.get(selectedTable.id)?.length ?? 0}
          pendingPixCount={pixByTable.get(selectedTable.id) ?? 0}
          onShowRequests={() => {
            setSelectedTableId(null);
            setRequestsFor(selectedTable.id);
          }}
          onClose={() => setSelectedTableId(null)}
          onNotice={setNotice}
        />
      )}

      {requestsFor !== null && (
        <TableOrderRequestsSheet
          diningTableId={requestsFor === 'all' ? null : requestsFor}
          now={now}
          onShowAll={() => setRequestsFor('all')}
          onClose={() => setRequestsFor(null)}
          onNotice={setNotice}
        />
      )}

      {notice && (
        <div className="dr-notice" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
