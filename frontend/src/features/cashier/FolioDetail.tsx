import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useCurrentUser } from '../auth/useCurrentUser';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { formatMoney, moneySign } from '../../lib/money';
import {
  closeFolio,
  postFolioAdjustment,
  refundFolioPayment,
  rereadFolio,
  reverseFolioCharge,
  useFolio,
  type ChargeResponse,
  type Folio,
  type PaymentResponse,
} from './useOpenFolios';
import { toDecimalString } from './useCashSession';
import { PaymentDialog } from './PaymentDialog';
import { CashierDialog } from './CashierDialog';
import { folioDisplayName } from './FolioCard';
import { CASHIER, describeError } from './cashierMessages';
import '../../styles/cashier.css';

interface Props {
  readonly folioId: string;
}

type FolioAction =
  | { readonly kind: 'reverse'; readonly charge: ChargeResponse }
  | { readonly kind: 'refund'; readonly payment: PaymentResponse }
  | { readonly kind: 'adjust' }
  | { readonly kind: 'close' };

/**
 * A charge the domain takes back: a room night or a tab charge, not yet reversed. An adjustment and
 * a reversal are not (decision #14). The backend still decides, with its own codes.
 */
function offersReversal(charge: ChargeResponse): boolean {
  return charge.type !== 'ADJUSTMENT' && charge.reversalOf === null && charge.reversedBy === null;
}

/**
 * Detalhe de uma conta: lançamentos, pagamentos, os totais da API e as ações que o perfil alcança
 * (decision #13). After every action the folio is read again from the server (decision #17).
 */
export function FolioDetail({ folioId }: Props) {
  const { data: folio, isLoading, isError, refetch } = useFolio(folioId);

  if (isLoading) {
    return (
      <div className="folio-detail-section">
        <p className="loading">{CASHIER.loadingFolio}</p>
      </div>
    );
  }

  if (!folio) {
    return (
      <div className="folio-detail-section">
        <p className="error-message">{isError ? CASHIER.folioFailed : CASHIER.loadingFolio}</p>
        <button className="btn btn-secondary" onClick={() => void refetch()}>
          {CASHIER.retry}
        </button>
      </div>
    );
  }

  return <FolioView folio={folio} />;
}

function FolioView({ folio }: { readonly folio: Folio }) {
  const { isAdmin, isFrontDesk } = useCurrentUser();
  const [isPaymentOpen, setIsPaymentOpen] = useState(false);
  const [action, setAction] = useState<FolioAction | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const isOpen = folio.status === 'OPEN';
  const isCounterFolio = folio.type === 'STAY';
  const canOperate = isAdmin || isFrontDesk;
  const sign = moneySign(folio.balance);
  const displayName = folioDisplayName(folio);

  function done(message: string) {
    setAction(null);
    setNotice(message);
  }

  function start(next: FolioAction) {
    setNotice(null);
    setAction(next);
  }

  return (
    <div className="folio-detail-section">
      <div className="folio-detail-header">
        <div>
          <h3>{displayName}</h3>
          <p className="folio-reference">
            {CASHIER.folioType[folio.type]}
            {folio.reference && ` · ${folio.reference.code}`}
            {` · ${CASHIER.folioNumber} #${folio.id.slice(0, 8)}`}
          </p>
        </div>
        <span className={`folio-status ${folio.status.toLowerCase()}`}>{CASHIER.folioStatus[folio.status]}</span>
      </div>

      {notice && <p className="notice-message">{notice}</p>}
      {folio.type === 'TAB' && <p className="closing-instruction">{CASHIER.tabFolioNote}</p>}

      <div className="folio-charges">
        <h4>{CASHIER.charges}</h4>
        {folio.charges.length === 0 ? (
          <p className="empty-message">{CASHIER.noCharges}</p>
        ) : (
          <table className="charges-table">
            <thead>
              <tr>
                <th>{CASHIER.folioColumns.description}</th>
                <th>{CASHIER.folioColumns.amount}</th>
                <th>{CASHIER.folioColumns.type}</th>
                <th>{CASHIER.folioColumns.actions}</th>
              </tr>
            </thead>
            <tbody>
              {folio.charges.map((charge) => (
                <tr key={charge.id}>
                  <td>
                    {charge.description}
                    {charge.reason && <div className="folio-reference">{charge.reason}</div>}
                  </td>
                  <td className="amount">{formatMoney(charge.amount)}</td>
                  <td>
                    <span className="charge-type">
                      {CASHIER.chargeType[charge.type]}
                      {charge.reversalOf !== null && ` · ${CASHIER.reversalOfLine}`}
                      {charge.reversedBy !== null && ` · ${CASHIER.reversedLine}`}
                    </span>
                  </td>
                  <td>
                    {isOpen && isCounterFolio && canOperate && offersReversal(charge) && (
                      <button className="btn btn-secondary" onClick={() => start({ kind: 'reverse', charge })}>
                        {CASHIER.reverseCharge}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="folio-payments">
        <h4>{CASHIER.payments}</h4>
        {folio.payments.length === 0 ? (
          <p className="empty-message">{CASHIER.noPayments}</p>
        ) : (
          <table className="payments-table">
            <thead>
              <tr>
                <th>{CASHIER.folioColumns.method}</th>
                <th>{CASHIER.folioColumns.amount}</th>
                <th>{CASHIER.folioColumns.status}</th>
                <th>{CASHIER.folioColumns.actions}</th>
              </tr>
            </thead>
            <tbody>
              {folio.payments.map((payment) => (
                <tr key={payment.id}>
                  <td>{CASHIER.paymentMethod[payment.method]}</td>
                  <td className="amount">{formatMoney(payment.amount)}</td>
                  <td>
                    <span className={`payment-status ${payment.status.toLowerCase()}`}>
                      {CASHIER.paymentStatus[payment.status]}
                    </span>
                    {payment.refundReason && (
                      <div className="folio-reference">{CASHIER.refundedReason(payment.refundReason)}</div>
                    )}
                  </td>
                  <td>
                    {isOpen && isAdmin && payment.status === 'CONFIRMED' && (
                      <button className="btn btn-secondary" onClick={() => start({ kind: 'refund', payment })}>
                        {CASHIER.refundPayment}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      <div className="folio-balance">
        <div className="balance-row">
          <span>{CASHIER.totalCharges}</span>
          <span>{formatMoney(folio.totalCharges)}</span>
        </div>
        <div className="balance-row">
          <span>{CASHIER.totalPayments}</span>
          <span>{formatMoney(folio.totalPayments)}</span>
        </div>
        <div className={`balance-row total ${sign === 'positive' ? 'overdue' : 'paid'}`}>
          <span>{CASHIER.balance}</span>
          <span>
            {formatMoney(folio.balance)}
            {sign === 'negative' && ` (${CASHIER.balanceCredit})`}
          </span>
        </div>
      </div>

      {isOpen && canOperate && (
        <div className="folio-actions">
          <button
            className="btn btn-primary"
            onClick={() => {
              setNotice(null);
              setIsPaymentOpen(true);
            }}
          >
            {CASHIER.receivePayment}
          </button>
          {isAdmin && (
            <button className="btn btn-secondary" onClick={() => start({ kind: 'adjust' })}>
              {CASHIER.postAdjustment}
            </button>
          )}
          {isCounterFolio && (
            <button className="btn btn-secondary" onClick={() => start({ kind: 'close' })}>
              {CASHIER.closeFolio}
            </button>
          )}
        </div>
      )}

      <PaymentDialog
        isOpen={isPaymentOpen}
        folio={folio}
        displayName={displayName}
        onClose={() => setIsPaymentOpen(false)}
        onSuccess={() => {
          setIsPaymentOpen(false);
          setNotice(CASHIER.notice.paymentReceived);
        }}
      />

      {action && (
        <FolioActionDialog
          key={action.kind}
          folio={folio}
          displayName={displayName}
          action={action}
          onCancel={() => setAction(null)}
          onDone={done}
        />
      )}
    </div>
  );
}

function FolioActionDialog({
  folio,
  displayName,
  action,
  onCancel,
  onDone,
}: {
  readonly folio: Folio;
  readonly displayName: string;
  readonly action: FolioAction;
  readonly onCancel: () => void;
  readonly onDone: (notice: string) => void;
}) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [reason, setReason] = useState('');
  const [description, setDescription] = useState('');
  const [amount, setAmount] = useState('');
  const [discount, setDiscount] = useState(true);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function run(write: () => Promise<unknown>, notice: string) {
    setWorking(true);
    setError(null);
    try {
      await write();
      onDone(notice);
    } catch (failure) {
      setError(describeError(failure));
    } finally {
      setWorking(false);
      void rereadFolio(queryClient, folio.id);
    }
  }

  const reasonField = (
    <div className="dialog-field">
      <label className="dialog-label" htmlFor="folioActionReason">
        {CASHIER.reasonLabel}
      </label>
      <textarea
        id="folioActionReason"
        className="dialog-textarea"
        value={reason}
        onChange={(event) => setReason(event.target.value)}
        placeholder={CASHIER.reasonPlaceholder}
        disabled={working}
        rows={3}
      />
    </div>
  );

  switch (action.kind) {
    case 'reverse':
      return (
        <CashierDialog
          title={CASHIER.reverseChargeTitle}
          message={CASHIER.reverseChargeMessage(action.charge.description, formatMoney(action.charge.amount))}
          error={error}
          working={working}
          canConfirm={reason.trim() !== ''}
          confirmLabel={CASHIER.reverseCharge}
          isDanger
          onCancel={onCancel}
          onConfirm={() =>
            void run(
              () => reverseFolioCharge(apiCall, folio.id, action.charge.id, reason.trim()),
              CASHIER.notice.chargeReversed,
            )
          }
        >
          {reasonField}
        </CashierDialog>
      );

    case 'refund':
      return (
        <CashierDialog
          title={CASHIER.refundPaymentTitle}
          message={CASHIER.refundPaymentMessage(
            CASHIER.paymentMethod[action.payment.method],
            formatMoney(action.payment.amount),
          )}
          error={error}
          working={working}
          canConfirm={reason.trim() !== ''}
          confirmLabel={CASHIER.refundPayment}
          isDanger
          onCancel={onCancel}
          onConfirm={() =>
            void run(
              () => refundFolioPayment(apiCall, folio.id, action.payment.id, reason.trim()),
              CASHIER.notice.paymentRefunded,
            )
          }
        >
          {reasonField}
        </CashierDialog>
      );

    case 'close':
      return (
        <CashierDialog
          title={CASHIER.closeFolioTitle}
          message={CASHIER.closeFolioMessage(displayName)}
          error={error}
          working={working}
          canConfirm
          confirmLabel={CASHIER.closeFolio}
          isDanger
          onCancel={onCancel}
          onConfirm={() => void run(() => closeFolio(apiCall, folio.id), CASHIER.notice.folioClosed)}
        />
      );

    case 'adjust': {
      // Negative is a discount in the API (decision #18): a sign in front, by text, no arithmetic.
      const typed = toDecimalString(amount);
      const signedAmount = discount ? `-${typed}` : typed;
      return (
        <CashierDialog
          title={CASHIER.adjustmentTitle}
          message={CASHIER.adjustmentMessage}
          error={error}
          working={working}
          canConfirm={amount.trim() !== '' && description.trim() !== '' && reason.trim() !== ''}
          confirmLabel={CASHIER.postAdjustment}
          onCancel={onCancel}
          onConfirm={() =>
            void run(
              () => postFolioAdjustment(apiCall, folio.id, signedAmount, description.trim(), reason.trim()),
              CASHIER.notice.adjustmentPosted,
            )
          }
        >
          <div className="cashier-tabs">
            {([true, false] as const).map((option) => (
              <button
                key={String(option)}
                className={`tab-button ${discount === option ? 'active' : ''}`}
                onClick={() => setDiscount(option)}
                disabled={working}
              >
                {option ? CASHIER.adjustmentKind.discount : CASHIER.adjustmentKind.surcharge}
              </button>
            ))}
          </div>
          <div className="dialog-field">
            <label className="dialog-label" htmlFor="adjustmentAmount">
              {CASHIER.adjustmentAmount}
            </label>
            <input
              id="adjustmentAmount"
              type="text"
              inputMode="decimal"
              value={amount}
              onChange={(event) => setAmount(event.target.value)}
              disabled={working}
              placeholder="0,00"
              className="form-input"
            />
          </div>
          <div className="dialog-field">
            <label className="dialog-label" htmlFor="adjustmentDescription">
              {CASHIER.adjustmentDescription}
            </label>
            <input
              id="adjustmentDescription"
              type="text"
              value={description}
              onChange={(event) => setDescription(event.target.value)}
              placeholder={CASHIER.adjustmentDescriptionPlaceholder}
              disabled={working}
              className="form-input"
            />
          </div>
          {reasonField}
        </CashierDialog>
      );
    }
  }
}
