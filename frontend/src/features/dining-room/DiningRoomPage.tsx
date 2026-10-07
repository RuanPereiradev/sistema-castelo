import { useEffect, useState, useRef } from 'react';
import { useDiningTables, type DiningTable } from './useDiningTables';
import { useOpenTab } from './useTab';
import { useDiningTableTab } from './useDiningTableTab';
import '../../styles/dining-room-portal.css';

type StatusFilter = 'todas' | 'livre' | 'ocupada' | 'pronto' | 'conta' | 'reservada';
type TableStatus = 'livre' | 'ocupada' | 'pronto' | 'conta' | 'reservada';

interface TableWithStatus extends DiningTable {
  status: TableStatus;
  waiter: string | null;
  guestCount: number;
  minutesOpen: number;
  isMyTable: boolean;
}

const STATUS_STYLES = {
  livre: { border: '1px dashed rgba(232,149,127,.55)', bg: 'transparent', fg: 'rgba(244,236,227,.82)' },
  ocupada: { border: '1.5px solid #E8957F', bg: '#4A1522', fg: '#F4ECE3' },
  pronto: { border: '1.5px solid #E8957F', bg: '#E8957F', fg: '#3D0F1A' },
  conta: { border: '1.5px solid #F6D9CE', bg: '#F4ECE3', fg: '#4A1220' },
  reservada: { border: '3px double rgba(232,149,127,.65)', bg: 'transparent', fg: '#F6D9CE' },
};

const AREAS = {
  salao: 'Salão',
  varanda: 'Varanda',
};

export function DiningRoomPage() {
  const { data: tables = [], isLoading, error } = useDiningTables();
  const { mutate: openTab, isPending: opening } = useOpenTab();
  const sheetRef = useRef<HTMLDivElement>(null);

  // State
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);
  const [filter, setFilter] = useState<StatusFilter>('todas');
  const [area, setArea] = useState<'salao' | 'varanda'>('salao');
  const [mine, setMine] = useState(false);
  const [notice, setNotice] = useState<string | null>(null);
  const [guestCount, setGuestCount] = useState(1);

  const { data: selectedTab } = useDiningTableTab(
    selectedTableId || '',
    !!selectedTableId,
  );

  // Auto-hide notice
  useEffect(() => {
    if (notice) {
      const timer = setTimeout(() => setNotice(null), 2600);
      return () => clearTimeout(timer);
    }
  }, [notice]);

  // Sheet animation
  useEffect(() => {
    if (selectedTableId && sheetRef.current && selectedTab) {
      const el = sheetRef.current;
      el.style.transition = 'none';
      el.style.transform = 'translateY(24px)';
      el.style.opacity = '0';
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          el.style.transition = 'transform 260ms ease, opacity 260ms ease';
          el.style.transform = 'none';
          el.style.opacity = '1';
        });
      });
    }
  }, [selectedTableId, selectedTab]);

  if (isLoading) {
    return <div className="dining-room-portal loading">Carregando mesas...</div>;
  }

  if (error) {
    return (
      <div className="dining-room-portal error">
        <p>Erro ao carregar mesas</p>
        <p>{error instanceof Error ? error.message : String(error)}</p>
      </div>
    );
  }

  if (!tables || tables.length === 0) {
    return <div className="dining-room-portal empty">Nenhuma mesa encontrada</div>;
  }

  // Map backend data to UI status
  const enrichedTables: TableWithStatus[] = tables.map(t => {
    let status: TableStatus = 'livre';
    if (t.occupiedTabCount > 0) {
      status = 'ocupada';
    }
    return {
      ...t,
      status,
      waiter: null,
      guestCount: t.occupiedTabCount > 0 ? 2 : 0,
      minutesOpen: 0,
      isMyTable: false,
    };
  });

  // Compute counts
  const counts = {
    todas: enrichedTables.length,
    livre: enrichedTables.filter(t => t.status === 'livre').length,
    ocupada: enrichedTables.filter(t => t.status === 'ocupada').length,
    pronto: enrichedTables.filter(t => t.status === 'pronto').length,
    conta: enrichedTables.filter(t => t.status === 'conta').length,
    reservada: enrichedTables.filter(t => t.status === 'reservada').length,
  };

  // Chip configuration
  const chipKeys: StatusFilter[] = ['todas', 'pronto', 'conta', 'ocupada', 'livre', 'reservada'];
  const chips = chipKeys.map(k => {
    const active = filter === k;
    return {
      key: k,
      label: k === 'todas' ? 'Todas' : k.charAt(0).toUpperCase() + k.slice(1),
      count: counts[k],
      active,
    };
  });

  // Area options
  const areaOpts = (['salao', 'varanda'] as const).map(a => ({
    key: a,
    label: AREAS[a],
    count: enrichedTables.filter(t => t.area === a).length,
    active: area === a,
  }));

  // Filtered tables
  const passes = (t: TableWithStatus) => {
    if (t.area !== area) return false;
    if (filter !== 'todas' && t.status !== filter) return false;
    if (mine && !t.isMyTable) return false;
    return true;
  };

  const filteredTables = enrichedTables.filter(passes);
  const sectionTitle = AREAS[area] + (filter === 'todas' ? '' : ` · ${STATUS_STYLES[filter] ? STATUS_STYLES[filter] : ''}`);

  // Selected table details
  const selectedTableData = enrichedTables.find(t => t.id === selectedTableId);

  const handleOpenTab = (tableId: string) => {
    const table = enrichedTables.find(t => t.id === tableId);
    if (!table) return;

    openTab(
      {
        diningTableId: tableId,
        origin: 'TABLE_SERVICE',
        guestCount: Math.max(1, guestCount),
      },
      {
        onSuccess: () => {
          setNotice(`Mesa ${table.label} aberta`);
          setGuestCount(1);
        },
      },
    );
  };

  const handleCloseModal = () => {
    setSelectedTableId(null);
  };

  const handleTableClick = (tableId: string) => {
    setSelectedTableId(tableId);
  };

  return (
    <div className="dining-room-portal">
      {/* Header */}
      <header className="dr-header">
        <div className="dr-header-top">
          <div className="dr-brand-mark">H</div>
          <div className="dr-header-title">
            <div className="dr-brand-info">Hospedaria · 20:14</div>
            <h1 className="dr-page-title">Mesas</h1>
          </div>
          <div className="dr-avatar">G</div>
        </div>

        {/* Area Filters */}
        <div className="dr-area-filters">
          {areaOpts.map(opt => (
            <button
              key={opt.key}
              className={`dr-area-btn ${opt.active ? 'active' : ''}`}
              onClick={() => {
                setArea(opt.key);
                setFilter('todas');
              }}
            >
              {opt.label} · {opt.count}
            </button>
          ))}
        </div>
      </header>

      {/* Chips Filter */}
      <div className="dr-chips">
        {chips.map(chip => (
          <button
            key={chip.key}
            className={`dr-chip ${chip.active ? 'active' : ''}`}
            onClick={() => setFilter(chip.key)}
          >
            <span className="dr-chip-indicator"></span>
            {chip.label}{' '}
            <span className="dr-chip-count">{chip.count}</span>
          </button>
        ))}
      </div>

      {/* Content */}
      <div className="dr-content">
        {/* Section Header */}
        <div className="dr-section-header">
          <div className="dr-section-title">{sectionTitle}</div>
          <button
            className="dr-mine-toggle"
            aria-pressed={mine}
            onClick={() => setMine(!mine)}
          >
            <span className="dr-mine-checkbox">{mine ? '✓' : ''}</span>
            Só as minhas
          </button>
        </div>

        {/* Table Grid */}
        {filteredTables.length > 0 ? (
          <div className="dr-table-grid">
            {filteredTables.map(table => {
              const style = STATUS_STYLES[table.status];
              return (
                <button
                  key={table.id}
                  className={`dr-table-btn ${table.status}`}
                  style={{
                    border: style.border,
                    background: style.bg,
                    color: style.fg,
                  }}
                  onClick={() => handleTableClick(table.id)}
                  aria-label={`Mesa ${table.label}, ${table.seats} lugares`}
                >
                  {table.isMyTable && (
                    <span className="dr-table-mine-mark" title="Sua mesa">
                      ❧
                    </span>
                  )}
                  <div className="dr-table-number">{table.label}</div>
                  <div className="dr-table-info">
                    <div className="dr-table-label">
                      {table.status === 'livre'
                        ? `${table.seats} lugares`
                        : table.status === 'ocupada'
                          ? 'Ocupada'
                          : table.status === 'pronto'
                            ? 'Prato pronto'
                            : table.status === 'conta'
                              ? 'Conta pedida'
                              : 'Reservada'}
                    </div>
                    {table.status !== 'livre' && table.status !== 'reservada' && (
                      <div className="dr-table-meta">
                        {table.guestCount} pessoas · {table.minutesOpen} min
                      </div>
                    )}
                  </div>
                </button>
              );
            })}
          </div>
        ) : (
          <p className="dr-empty">Nenhuma mesa neste filtro.</p>
        )}
      </div>

      {/* Modal Backdrop & Sheet */}
      {selectedTableId && (
        <>
          <div className="dr-modal-backdrop" onClick={handleCloseModal} />
          <div className="dr-sheet" ref={sheetRef}>
            <div className="dr-sheet-content">
              {/* Sheet Header */}
              <div className="dr-sheet-header">
                <div className="dr-sheet-subtitle">
                  {AREAS[selectedTableData?.area as keyof typeof AREAS] || 'Salão'} ·{' '}
                  {selectedTableData?.status || 'livre'}
                </div>
                <h2 className="dr-sheet-title">Mesa {selectedTableData?.label}</h2>
                <button
                  className="dr-sheet-close"
                  onClick={handleCloseModal}
                  aria-label="Fechar"
                >
                  <svg
                    width="20"
                    height="20"
                    viewBox="0 0 24 24"
                    fill="none"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                  >
                    <path d="M18 6 6 18"></path>
                    <path d="m6 6 12 12"></path>
                  </svg>
                </button>
              </div>

              {/* Divider */}
              <div className="dr-divider">
                <span className="dr-divider-line"></span>
                <span className="dr-divider-ornament">❧</span>
                <span className="dr-divider-line"></span>
              </div>

              {/* Facts */}
              <div className="dr-sheet-facts">
                <span>
                  <span className="dr-fact-label">Lugares</span> {selectedTableData?.seats}
                </span>
                {selectedTableData && selectedTableData.guestCount > 0 && (
                  <>
                    <span>
                      <span className="dr-fact-label">Pessoas</span> {selectedTableData.guestCount}
                    </span>
                    <span>
                      <span className="dr-fact-label">Aberta há</span> {selectedTableData.minutesOpen} min
                    </span>
                  </>
                )}
              </div>

              {/* Items (if loaded) */}
              {selectedTab && selectedTab.items && selectedTab.items.length > 0 && (
                <div className="dr-sheet-items">
                  {selectedTab.items.map(item => (
                    <div key={item.id} className="dr-sheet-item">
                      <span className="dr-sheet-item-qty">{item.quantity}×</span>
                      <span className="dr-sheet-item-name">{item.itemName}</span>
                      <span className="dr-sheet-item-price">{item.price}</span>
                    </div>
                  ))}
                  <div className="dr-sheet-total">
                    <span className="dr-sheet-total-label">Total</span>
                    <span className="dr-sheet-total-value">{selectedTab.total}</span>
                  </div>
                </div>
              )}

              {/* Note */}
              {selectedTableData?.status === 'pronto' && (
                <p className="dr-sheet-note">
                  A cozinha avisou que há prato pronto para esta mesa.
                </p>
              )}

              {/* Actions */}
              <div className="dr-sheet-actions">
                {selectedTableData?.status === 'livre' ? (
                  <>
                    <div style={{ display: 'flex', gap: '8px', alignItems: 'center' }}>
                      <label style={{ fontSize: '13.5px', color: 'var(--color-text)' }}>
                        Pessoas:
                      </label>
                      <input
                        type="number"
                        min="1"
                        max="20"
                        value={guestCount}
                        onChange={e => setGuestCount(Math.max(1, parseInt(e.target.value, 10)))}
                        style={{
                          width: '50px',
                          padding: '4px 8px',
                          background: 'var(--color-surface)',
                          border: '1px solid var(--color-divider)',
                          color: 'var(--color-text)',
                          borderRadius: '2px',
                          fontFamily: 'var(--font-body)',
                        }}
                      />
                    </div>
                    <button
                      className="dr-action-btn dr-action-primary"
                      onClick={() => handleOpenTab(selectedTableId)}
                      disabled={opening}
                    >
                      {opening ? 'Abrindo...' : 'Abrir mesa'}
                    </button>
                  </>
                ) : selectedTableData?.status === 'ocupada' ? (
                  <button className="dr-action-btn dr-action-primary">
                    Lançar pedido
                  </button>
                ) : selectedTableData?.status === 'pronto' ? (
                  <button className="dr-action-btn dr-action-primary">
                    Marcar como servido
                  </button>
                ) : null}

                <button className="dr-action-btn dr-action-secondary">
                  {selectedTableData?.status === 'livre' ? 'Cancelar' : 'Voltar'}
                </button>
              </div>
            </div>
          </div>
        </>
      )}

      {/* Notice */}
      {notice && (
        <div className="dr-notice" role="status">
          {notice}
        </div>
      )}
    </div>
  );
}
