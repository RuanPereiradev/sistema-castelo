import type { Folio } from './useOpenFolios';
import { FolioCard } from './FolioCard';
import { CASHIER } from './cashierMessages';
import '../../styles/cashier.css';

interface Props {
  readonly folios: readonly Folio[];
  readonly isLoading: boolean;
  readonly selectedFolioId: string | null;
  readonly onSelectFolio: (folioId: string) => void;
}

/**
 * Lista de folios (contas) abertas em cards.
 */
export function FolioList({
  folios,
  isLoading,
  selectedFolioId,
  onSelectFolio,
}: Props) {
  return (
    <div className="folios-section">
      <h2 className="section-title">{CASHIER.openFolios}</h2>

      {isLoading && <p className="loading">{CASHIER.loadingFolios}</p>}

      {!isLoading && folios.length === 0 && (
        <p className="empty-message">{CASHIER.noFolios}</p>
      )}

      {!isLoading && folios.length > 0 && (
        <div className="folios-grid">
          {folios.map((folio) => (
            <FolioCard
              key={folio.id}
              folio={folio}
              isSelected={selectedFolioId === folio.id}
              onSelect={() => onSelectFolio(folio.id)}
            />
          ))}
        </div>
      )}
    </div>
  );
}
