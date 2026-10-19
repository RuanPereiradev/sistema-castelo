import { useState } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { useAuthorizedRequest } from '../../lib/useAuthorizedRequest';
import { formatMoney } from '../../lib/money';
import { ConfirmDialog } from '../dining-room/ConfirmDialog';
import {
  CURRENT_SESSION_KEY,
  closeCashSession,
  openCashSession,
  registerCashMovement,
  useCashSession,
  type CashDrawerSession,
} from './useCashSession';
import { CASHIER, describeError } from './cashierMessages';
import '../../styles/cashier.css';

/**
 * Turno de caixa: abrir com fundo de troco, sangria, suprimento e fechamento cego.
 * Ninguém vê a diferença antes de fechar; o resultado vem da resposta do fechamento.
 */
export function CashDrawerSessionComponent() {
  const { data: session, isLoading, isError, refetch } = useCashSession();
  const [justClosed, setJustClosed] = useState<CashDrawerSession | null>(null);

  if (isLoading) {
    return (
      <div className="cash-session-container">
        <p className="loading">{CASHIER.loadingSessions}</p>
      </div>
    );
  }

  if (isError) {
    return (
      <div className="cash-session-container">
        <p className="error">{CASHIER.sessionFailed}</p>
        <button className="btn btn-secondary" onClick={() => void refetch()}>
          {CASHIER.retry}
        </button>
      </div>
    );
  }

  if (!session) {
    return (
      <div className="cash-session-container">
        {justClosed && <ClosedSessionSummary session={justClosed} />}
        <OpenSessionForm onOpened={() => setJustClosed(null)} />
      </div>
    );
  }

  return (
    <div className="cash-session-container">
      <OpenSessionView session={session} />
      <CashMovementForm sessionId={session.id} />
      <CloseSessionForm sessionId={session.id} onClosed={setJustClosed} />
    </div>
  );
}

function OpenSessionForm({ onOpened }: { readonly onOpened: () => void }) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [openingFloat, setOpeningFloat] = useState('');
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function open() {
    setWorking(true);
    setError(null);
    try {
      await openCashSession(apiCall, openingFloat);
      onOpened();
    } catch (failure) {
      setError(describeError(failure));
    } finally {
      setWorking(false);
      // Aberto aqui ou por outra pessoa no mesmo instante: o servidor diz qual é o turno.
      void queryClient.invalidateQueries({ queryKey: CURRENT_SESSION_KEY });
    }
  }

  return (
    <div className="closing-section">
      <h2>{CASHIER.noOpenSession}</h2>
      <p className="closing-instruction">{CASHIER.openSessionInstruction}</p>
      {error && <p className="error-message">{error}</p>}
      <div className="closing-form">
        <div className="form-group">
          <label htmlFor="openingFloat">{CASHIER.openingFloat}</label>
          <input
            id="openingFloat"
            type="text"
            inputMode="decimal"
            value={openingFloat}
            onChange={(event) => setOpeningFloat(event.target.value)}
            disabled={working}
            placeholder="0,00"
            className="form-input"
          />
        </div>
        <button
          className="btn btn-primary"
          onClick={() => void open()}
          disabled={working || openingFloat.trim() === ''}
        >
          {working ? CASHIER.processing : CASHIER.openSession}
        </button>
      </div>
    </div>
  );
}

function OpenSessionView({ session }: { readonly session: CashDrawerSession }) {
  const hidden = '—';
  return (
    <>
      <div className="session-header">
        <h2>{CASHIER.currentSession}</h2>
        <span className="session-status open">{CASHIER.statusLabel['OPEN']}</span>
      </div>

      <div className="session-info">
        <InfoRow label={CASHIER.openedAt} value={new Date(session.openedAt).toLocaleString('pt-BR')} />
        <InfoRow label={CASHIER.openingFloat} value={formatMoney(session.openingFloat)} />
        <InfoRow label={CASHIER.totalDrops} value={formatMoney(session.totalDrops)} />
        <InfoRow label={CASHIER.totalSupplies} value={formatMoney(session.totalSupplies)} />
        <InfoRow label={CASHIER.paymentCount} value={String(session.cashPaymentCount)} />
        {/* Só o ADMIN recebe estes dois com o turno aberto (fechamento cego). */}
        <InfoRow
          label={CASHIER.cashPaymentsTotal}
          value={session.cashPaymentsTotal === null ? hidden : formatMoney(session.cashPaymentsTotal)}
        />
        <InfoRow
          label={CASHIER.expectedAmount}
          value={session.expectedAmount === null ? hidden : formatMoney(session.expectedAmount)}
        />
      </div>

      {session.movements.length > 0 && (
        <div className="movements-section">
          <h3>{CASHIER.movements}</h3>
          <table className="movements-table">
            <thead>
              <tr>
                <th>{CASHIER.movementColumns.type}</th>
                <th>{CASHIER.movementColumns.amount}</th>
                <th>{CASHIER.movementColumns.reason}</th>
                <th>{CASHIER.movementColumns.time}</th>
              </tr>
            </thead>
            <tbody>
              {session.movements.map((movement) => (
                <tr key={movement.id}>
                  <td>{CASHIER.movementType[movement.type]}</td>
                  <td className="amount">{formatMoney(movement.amount)}</td>
                  <td>{movement.reason}</td>
                  <td>{new Date(movement.createdAt).toLocaleTimeString('pt-BR')}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

function newAttemptKey(): string {
  return crypto.randomUUID();
}

function CashMovementForm({ sessionId }: { readonly sessionId: string }) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [kind, setKind] = useState<'drops' | 'supplies'>('drops');
  const [amount, setAmount] = useState('');
  const [reason, setReason] = useState('');
  // Uma chave por tentativa: o retry depois de uma falha reusa a mesma e não lança duas vezes.
  const [attemptKey, setAttemptKey] = useState(newAttemptKey);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  function edit(change: () => void) {
    change();
    setNotice(null);
    // Mudou o que se lança: é outra tentativa, com outra chave.
    setAttemptKey(newAttemptKey());
  }

  async function register() {
    setWorking(true);
    setError(null);
    try {
      await registerCashMovement(apiCall, sessionId, kind, amount, reason.trim(), attemptKey);
      setNotice(kind === 'drops' ? CASHIER.notice.dropRegistered : CASHIER.notice.supplyRegistered);
      setAmount('');
      setReason('');
      setAttemptKey(newAttemptKey());
    } catch (failure) {
      setError(describeError(failure));
    } finally {
      setWorking(false);
      void queryClient.invalidateQueries({ queryKey: CURRENT_SESSION_KEY });
    }
  }

  return (
    <div className="closing-section">
      <h3>{CASHIER.registerMovement}</h3>
      <div className="cashier-tabs">
        {(['drops', 'supplies'] as const).map((option) => (
          <button
            key={option}
            className={`tab-button ${kind === option ? 'active' : ''}`}
            onClick={() => edit(() => setKind(option))}
            disabled={working}
          >
            {option === 'drops' ? CASHIER.movementType.CASH_DROP : CASHIER.movementType.CASH_SUPPLY}
          </button>
        ))}
      </div>
      {error && <p className="error-message">{error}</p>}
      {notice && <p className="notice-message">{notice}</p>}
      <div className="closing-form">
        <div className="form-group">
          <label htmlFor="movementAmount">{CASHIER.movementColumns.amount}</label>
          <input
            id="movementAmount"
            type="text"
            inputMode="decimal"
            value={amount}
            onChange={(event) => edit(() => setAmount(event.target.value))}
            disabled={working}
            placeholder="0,00"
            className="form-input"
          />
        </div>
        <div className="form-group">
          <label htmlFor="movementReason">{CASHIER.movementColumns.reason}</label>
          <input
            id="movementReason"
            type="text"
            value={reason}
            onChange={(event) => edit(() => setReason(event.target.value))}
            disabled={working}
            className="form-input"
          />
        </div>
        <button
          className="btn btn-primary"
          onClick={() => void register()}
          disabled={working || amount.trim() === '' || reason.trim() === ''}
        >
          {working
            ? CASHIER.processing
            : kind === 'drops'
              ? CASHIER.registerDrop
              : CASHIER.registerSupply}
        </button>
      </div>
    </div>
  );
}

function CloseSessionForm({
  sessionId,
  onClosed,
}: {
  readonly sessionId: string;
  readonly onClosed: (closed: CashDrawerSession) => void;
}) {
  const apiCall = useAuthorizedRequest();
  const queryClient = useQueryClient();
  const [countedAmount, setCountedAmount] = useState('');
  const [note, setNote] = useState('');
  const [confirming, setConfirming] = useState(false);
  const [working, setWorking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function close() {
    setWorking(true);
    setError(null);
    try {
      onClosed(await closeCashSession(apiCall, sessionId, countedAmount, note));
    } catch (failure) {
      setError(describeError(failure));
    } finally {
      setWorking(false);
      setConfirming(false);
      void queryClient.invalidateQueries({ queryKey: CURRENT_SESSION_KEY });
    }
  }

  return (
    <div className="closing-section">
      <h3>{CASHIER.closeSession}</h3>
      <p className="closing-instruction">{CASHIER.countingBlind}</p>
      {error && <p className="error-message">{error}</p>}
      <div className="closing-form">
        <div className="form-group">
          <label htmlFor="countedAmount">{CASHIER.enterCountedAmount}</label>
          <input
            id="countedAmount"
            type="text"
            inputMode="decimal"
            value={countedAmount}
            onChange={(event) => setCountedAmount(event.target.value)}
            disabled={working}
            placeholder="0,00"
            className="form-input"
          />
        </div>
        <div className="form-group">
          <label htmlFor="closingNote">
            {CASHIER.closingNote} {CASHIER.closingNoteOptional}
          </label>
          <textarea
            id="closingNote"
            value={note}
            onChange={(event) => setNote(event.target.value)}
            disabled={working}
            className="form-textarea"
            rows={2}
          />
        </div>
        <button
          className="btn btn-primary"
          onClick={() => setConfirming(true)}
          disabled={working || countedAmount.trim() === ''}
        >
          {CASHIER.closeSession}
        </button>
      </div>

      <ConfirmDialog
        isOpen={confirming}
        title={CASHIER.closeSessionConfirmTitle}
        message={CASHIER.closeSessionConfirmMessage(countedAmount.trim())}
        onConfirm={() => void close()}
        onCancel={() => setConfirming(false)}
        isLoading={working}
        confirmLabel={CASHIER.closeSession}
        cancelLabel={CASHIER.cancel}
        isDanger
      />
    </div>
  );
}

function ClosedSessionSummary({ session }: { readonly session: CashDrawerSession }) {
  const balanced = session.difference === '0.00';
  return (
    <div className="closed-session-info">
      <h3>{CASHIER.sessionClosed}</h3>
      {session.expectedAmount && <InfoRow label={CASHIER.expectedAmount} value={formatMoney(session.expectedAmount)} />}
      {session.countedAmount && <InfoRow label={CASHIER.countedAmount} value={formatMoney(session.countedAmount)} />}
      {session.difference && (
        <div className="info-row">
          <span className="info-label">{CASHIER.difference}</span>
          <span className={`info-value ${balanced ? 'balanced' : 'unbalanced'}`}>
            {formatMoney(session.difference)}
          </span>
        </div>
      )}
      {session.closingNote && <InfoRow label={CASHIER.closingNote} value={session.closingNote} />}
    </div>
  );
}

function InfoRow({ label, value }: { readonly label: string; readonly value: string }) {
  return (
    <div className="info-row">
      <span className="info-label">{label}</span>
      <span className="info-value">{value}</span>
    </div>
  );
}
