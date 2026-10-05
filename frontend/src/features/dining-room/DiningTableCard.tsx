import type { DiningTable } from './useDiningTables';

interface Props {
  readonly table: DiningTable;
  readonly isSelected: boolean;
  readonly onSelect: (tableId: string) => void;
}

/**
 * Card de uma mesa no mapa: nome, capacidade, status (livre/ocupada).
 * Clique abre o painel de detalhes ou lança a comanda se livre.
 */
export function DiningTableCard({ table, isSelected, onSelect }: Props) {
  const isOccupied = table.occupiedTabCount > 0;
  const statusLabel = isOccupied ? `Ocupada (${table.occupiedTabCount})` : 'Livre';

  return (
    <div
      className={`table-card ${isSelected ? 'selected' : ''} ${isOccupied ? 'occupied' : ''}`}
      onClick={() => onSelect(table.id)}
      role="button"
      tabIndex={0}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          onSelect(table.id);
        }
      }}
      title={table.isActive ? statusLabel : 'Mesa inativa'}
      aria-pressed={isSelected}
    >
      <div className="table-card-label">{table.label}</div>
      <div className="table-card-seats">👥 {table.seats}</div>
      <div className="table-card-status">{statusLabel}</div>
      {!table.isActive && <div className="table-card-inactive">Inativa</div>}
    </div>
  );
}
