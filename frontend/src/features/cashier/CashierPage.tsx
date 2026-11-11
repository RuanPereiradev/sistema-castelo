import { useState } from 'react';
import { useOpenFolios } from './useOpenFolios';
import { CashDrawerSessionComponent } from './CashDrawerSession';
import { FolioList } from './FolioList';
import { FolioDetail } from './FolioDetail';
import { OpenTabs } from './OpenTabs';
import { CASHIER } from './cashierMessages';
import '../../styles/cashier.css';

type ScreenTab = 'cash' | 'folios';

/**
 * Tela de Caixa (FRONT_DESK e ADMIN): turno de caixa com fechamento cego, contas abertas e, só para
 * leitura, as comandas abertas do salão.
 */
export function CashierPage() {
  const [activeTab, setActiveTab] = useState<ScreenTab>('cash');
  const [selectedFolioId, setSelectedFolioId] = useState<string | null>(null);

  const screenTabs: readonly { readonly id: ScreenTab; readonly label: string }[] = [
    { id: 'cash', label: CASHIER.cashDrawerTabLabel },
    { id: 'folios', label: CASHIER.foliosTabLabel },
  ];

  return (
    <div className="cashier-page">
      <div className="cashier-tabs">
        {screenTabs.map((tab) => (
          <button
            key={tab.id}
            className={`tab-button ${activeTab === tab.id ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.id)}
          >
            {tab.label}
          </button>
        ))}
      </div>

      <div className="cashier-content">
        {activeTab === 'cash' && <CashDrawerSessionComponent />}
        {activeTab === 'folios' && (
          // Contas e comandas na mesma vista (decision #23): the open tabs stay read-only.
          <div className="cashier-side-by-side">
            <div>
              <FoliosView selectedFolioId={selectedFolioId} onSelectFolio={setSelectedFolioId} />
            </div>
            <div>
              <OpenTabs />
            </div>
          </div>
        )}
      </div>
    </div>
  );
}

/**
 * Contas abertas e o detalhe da escolhida. The summary carries only the count: the sum of the
 * balances is not sent by the API and is not added up here (decision #10, gap L1).
 */
function FoliosView({
  selectedFolioId,
  onSelectFolio,
}: {
  readonly selectedFolioId: string | null;
  readonly onSelectFolio: (folioId: string) => void;
}) {
  const { data: folios, isLoading, isError, refetch } = useOpenFolios();

  return (
    <>
      <div className="folios-container">
        <div className="folios-list-section">
          <FolioList
            folios={folios ?? []}
            isLoading={isLoading}
            isError={isError && !folios}
            onRetry={() => void refetch()}
            selectedFolioId={selectedFolioId}
            onSelectFolio={onSelectFolio}
          />
        </div>

        {selectedFolioId === null ? (
          <div className="folio-detail-section">
            <p className="empty-message">{CASHIER.chooseFolio}</p>
          </div>
        ) : (
          <FolioDetail key={selectedFolioId} folioId={selectedFolioId} />
        )}
      </div>

      {folios && (
        <div className="cashier-summary">
          <div className="summary-card">
            <p className="summary-label">{CASHIER.openFolioCount}</p>
            <p className="summary-value">{folios.length}</p>
          </div>
        </div>
      )}
    </>
  );
}
