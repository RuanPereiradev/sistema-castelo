import type { Folio } from './useOpenFolios';

interface Props {
  readonly folio: Folio;
  readonly isSelected: boolean;
  readonly onSelect: () => void;
}

/**
 * Card de um folio (conta) aberta.
 */
export function FolioCard({ folio, isSelected, onSelect }: Props) {
  const balance = parseFloat(folio.balance);
  const isOverdue = balance > 0;

  return (
    <button
      className={`folio-card ${isSelected ? 'selected' : ''} ${isOverdue ? 'overdue' : ''}`}
      onClick={onSelect}
    >
      <div className="folio-card-header">
        <span className="folio-guest-name">{folio.guestName}</span>
        <span className="folio-id">#{folio.id.slice(0, 8)}</span>
      </div>

      <div className="folio-card-charges">
        <span className="charge-count">{folio.charges.length} lançamentos</span>
      </div>

      <div className="folio-card-balance">
        <span className={`balance-value ${balance > 0 ? 'pending' : 'paid'}`}>
          {folio.balance}
        </span>
      </div>
    </button>
  );
}
