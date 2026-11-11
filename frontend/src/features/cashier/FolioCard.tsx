import { formatMoney, moneySign } from '../../lib/money';
import type { Folio } from './useOpenFolios';
import { CASHIER } from './cashierMessages';

interface Props {
  readonly folio: Folio;
  readonly isSelected: boolean;
  readonly onSelect: () => void;
}

/**
 * What the operator reads to recognise a folio: the reference of a stay (the room), or the kind and
 * the short number of a tab's folio, which the API sends with no reference (gap L3).
 */
export function folioDisplayName(folio: Folio): string {
  return folio.reference?.label ?? `${CASHIER.folioType[folio.type]} #${folio.id.slice(0, 8)}`;
}

/** Card de uma conta aberta. O saldo vem calculado da API e só é formatado aqui. */
export function FolioCard({ folio, isSelected, onSelect }: Props) {
  const sign = moneySign(folio.balance);

  return (
    <button
      className={`folio-card ${isSelected ? 'selected' : ''} ${sign === 'positive' ? 'overdue' : ''}`}
      onClick={onSelect}
    >
      <div className="folio-card-header">
        <span className="folio-guest-name">{folioDisplayName(folio)}</span>
        <span className="folio-id">{CASHIER.folioType[folio.type]}</span>
      </div>

      <div className="folio-card-charges">
        <span className="charge-count">{CASHIER.chargeCount(folio.charges.length)}</span>
      </div>

      <div className="folio-card-balance">
        <span className={`balance-value ${sign === 'positive' ? 'pending' : 'paid'}`}>
          {formatMoney(folio.balance)}
          {sign === 'negative' && ` (${CASHIER.balanceCredit})`}
        </span>
      </div>
    </button>
  );
}
