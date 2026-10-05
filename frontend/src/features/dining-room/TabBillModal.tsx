import type { TabBill } from './useTabActions';
import '../../styles/tab-bill-modal.css';

interface Props {
  readonly isOpen: boolean;
  readonly bill: TabBill | null;
  readonly isLoading: boolean;
  readonly onClose: () => void;
}

/**
 * Modal mostrando a pré-conta da comanda.
 */
export function TabBillModal({ isOpen, bill, isLoading, onClose }: Props) {
  if (!isOpen) {
    return null;
  }

  return (
    <>
      <div className="modal-overlay" onClick={onClose} />
      <div className="modal bill-modal">
        <div className="modal-header">
          <h2>Pré-Conta</h2>
          <button className="modal-close" onClick={onClose} aria-label="Fechar">
            ✕
          </button>
        </div>

        <div className="modal-content">
          {isLoading && <p className="loading">Carregando pré-conta...</p>}

          {bill && (
            <>
              <div className="bill-items">
                <h3 className="bill-section-title">Itens</h3>
                <table className="bill-table">
                  <thead>
                    <tr>
                      <th>Item</th>
                      <th>Qtd</th>
                      <th>Valor</th>
                      <th>Total</th>
                    </tr>
                  </thead>
                  <tbody>
                    {bill.items.map((item) => (
                      <tr key={item.id}>
                        <td className="item-name">{item.name}</td>
                        <td className="item-qty">{item.quantity}</td>
                        <td className="item-price">{item.price}</td>
                        <td className="item-total">{item.total}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>

              <div className="bill-summary">
                <div className="bill-row">
                  <span className="label">Subtotal</span>
                  <span className="value">{bill.total}</span>
                </div>
                {parseFloat(bill.serviceCharge) > 0 && (
                  <div className="bill-row">
                    <span className="label">Taxa de serviço</span>
                    <span className="value">+{bill.serviceCharge}</span>
                  </div>
                )}
                <div className="bill-row total">
                  <span className="label">Total</span>
                  <span className="value">{bill.balance}</span>
                </div>
              </div>
            </>
          )}
        </div>

        <div className="modal-actions">
          <button className="btn btn-secondary" onClick={onClose}>
            Fechar
          </button>
        </div>
      </div>
    </>
  );
}
