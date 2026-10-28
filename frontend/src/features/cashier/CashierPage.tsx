import { useState } from 'react';
import { useOpenFolios } from './useOpenFolios';
import { CashDrawerSessionComponent } from './CashDrawerSession';
import { FolioList } from './FolioList';
import { FolioDetail } from './FolioDetail';
import { CASHIER } from './cashierMessages';
import '../../styles/cashier.css';

type Tab = 'cash' | 'folios';

/**
 * Tela de Caixa (FRONT_DESK e ADMIN).
 * Turno de caixa com fechamento cego e gestão de folios.
 */
export function CashierPage() {
  const [activeTab, setActiveTab] = useState<Tab>('cash');
  const [selectedFolioId, setSelectedFolioId] = useState<string | null>(null);

  const { data: folios = [], isLoading } = useOpenFolios();
  const selectedFolio = folios.find((f) => f.id === selectedFolioId);
  const totalBalance = folios.reduce((sum, f) => sum + parseFloat(f.balance), 0);

  return (
    <div className="cashier-page">
      {/* Tabs */}
      <div className="cashier-tabs">
        <button
          className={`tab-button ${activeTab === 'cash' ? 'active' : ''}`}
          onClick={() => setActiveTab('cash')}
        >
          {CASHIER.cashDrawerTabLabel}
        </button>
        <button
          className={`tab-button ${activeTab === 'folios' ? 'active' : ''}`}
          onClick={() => setActiveTab('folios')}
        >
          {CASHIER.foliosTabLabel}
        </button>
      </div>

      {/* Conteúdo das abas */}
      <div className="cashier-content">
        {activeTab === 'cash' && (
          <CashDrawerSessionComponent />
        )}

        {activeTab === 'folios' && (
          <div className="folios-container">
            <div className="folios-list-section">
              <FolioList
                folios={folios}
                isLoading={isLoading}
                selectedFolioId={selectedFolioId}
                onSelectFolio={setSelectedFolioId}
              />
            </div>

            {/* Detalhe do folio selecionado */}
            {selectedFolio && (
              <FolioDetail folio={selectedFolio} />
            )}
          </div>
        )}
      </div>

      {/* Resumo Total (somente na aba de folios) */}
      {activeTab === 'folios' && (
        <div className="cashier-summary">
          <div className="summary-card">
            <p className="summary-label">{CASHIER.totalBalance}</p>
            <p className="summary-value">{totalBalance.toFixed(2)}</p>
          </div>
          <div className="summary-card">
            <p className="summary-label">{CASHIER.openFolioCount}</p>
            <p className="summary-value">{folios.length}</p>
          </div>
        </div>
      )}
    </div>
  );
}
