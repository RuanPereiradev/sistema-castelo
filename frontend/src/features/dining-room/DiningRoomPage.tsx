import { useState } from 'react';
import { useDiningTables } from './useDiningTables';
import { DiningTableCard } from './DiningTableCard';
import { TabDetailsPanel } from './TabDetailsPanel';
import '../../styles/dining-room.css';

/**
 * Página do salão: mapa de mesas e painel de comanda.
 * MVP: abrir comanda em mesa livre. Detalhe da comanda em painel lateral.
 */
export function DiningRoomPage() {
  const { data: tables = [], isLoading, error } = useDiningTables();
  const [selectedTableId, setSelectedTableId] = useState<string | null>(null);

  if (isLoading) {
    return <div className="loading">Carregando mesas...</div>;
  }

  if (error) {
    return (
      <div className="error">
        <p>Erro ao carregar mesas</p>
        <pre>{error instanceof Error ? error.message : String(error)}</pre>
      </div>
    );
  }

  const selectedTable = tables.find((t) => t.id === selectedTableId);

  return (
    <div className="dining-room">
      <div className="dining-room-map">
        <h2>Mapa de Mesas</h2>
        <div className="table-grid">
          {tables.map((table) => (
            <DiningTableCard
              key={table.id}
              table={table}
              isSelected={selectedTableId === table.id}
              onSelect={setSelectedTableId}
            />
          ))}
        </div>
      </div>
      {selectedTable && (
        <aside className="dining-room-panel">
          <TabDetailsPanel table={selectedTable} />
        </aside>
      )}
    </div>
  );
}
