/**
 * Every Portuguese string of the cashier lives here, never in a component.
 * The first block is what the screen says; the second translates the stable
 * `code` the API answers on failure into what the cashier should do next.
 */
export const CASHIER = {
  brand: 'Hospedaria',
  title: 'Caixa',

  // Cash drawer session
  currentSession: 'Turno de caixa',
  sessionStatus: 'Status do turno',
  openingFloat: 'Fundo de troco',
  totalDrops: 'Total sangrias',
  totalSupplies: 'Total suprimentos',
  cashPaymentsTotal: 'Total recebido em dinheiro',
  paymentCount: 'Quantidade de pagamentos',
  expectedAmount: 'ESPERADO',
  countedAmount: 'CONTADO',
  difference: 'DIFERENÇA',
  countingBlind: 'Contagem cega: conte o caixa sem ver o total esperado',
  enterCountedAmount: 'Digite o valor contado',
  closeSession: 'Fechar turno',
  closingNote: 'Nota do fechamento',
  closingNoteOptional: '(opcional)',
  sessionClosed: 'Turno fechado',
  sessionOpened: 'Turno aberto',

  // Folios
  openFolios: 'Contas em aberto',
  noFolios: 'Nenhuma conta aberta',
  folioNumber: 'Folio',
  guestName: 'Nome',
  folioStatus: 'Status',
  charges: 'Lançamentos',
  payments: 'Pagamentos',
  totalCharges: 'Total lançado',
  totalPayments: 'Total pago',
  balance: 'Saldo',

  // Actions
  receivePayment: 'Receber pagamento',
  postAdjustment: 'Lançar ajuste',
  reverseCharge: 'Estornar lançamento',
  refundPayment: 'Estornar pagamento',
  closeFolio: 'Fechar conta',

  // Charge types
  chargeType: {
    ROOM_NIGHT_CHARGE: 'Diária',
    TAB_CHARGE: 'Consumo restaurante',
    ADJUSTMENT_CHARGE: 'Ajuste/desconto',
  } as Readonly<Record<string, string>>,

  // Payment methods
  paymentMethod: {
    CASH: 'Dinheiro',
    PIX: 'Pix',
    CREDIT_CARD: 'Cartão de crédito',
    DEBIT_CARD: 'Cartão de débito',
    ROOM_ACCOUNT: 'Conta do hóspede',
  } as Readonly<Record<string, string>>,

  // Status
  statusLabel: {
    OPEN: 'Aberto',
    CLOSED: 'Fechado',
    CLOSING: 'Fechando',
  } as Readonly<Record<string, string>>,

  chargeStatus: {
    CHARGE: 'Lançado',
    REVERSAL: 'Estorno',
  } as Readonly<Record<string, string>>,

  paymentStatus: {
    PENDING: 'Pendente',
    CONFIRMED: 'Confirmado',
    FAILED: 'Falha',
    REFUNDED: 'Estornado',
  } as Readonly<Record<string, string>>,

  folioStatus: {
    OPEN: 'Aberto',
    CLOSED: 'Fechado',
  } as Readonly<Record<string, string>>,

  // Tabs
  cashDrawerTab: 'Turno',
  foliosTab: 'Contas',

  // Messages
  loadingSessions: 'Carregando turno…',
  loadingFolios: 'Carregando contas…',
  sessionFailed: 'Não deu para carregar o turno.',
  foliosFailed: 'Não deu para carregar as contas.',

  // Summary
  totalBalance: 'Total em aberto',
  openFolioCount: 'Contas abertas',

  notice: {
    paymentReceived: 'Pagamento recebido',
    adjustmentPosted: 'Ajuste lançado',
    chargeReversed: 'Lançamento estornado',
    paymentRefunded: 'Pagamento estornado',
    folioClosed: 'Conta fechada',
  },
} as const;

/** Error code → what the cashier reads. Says what to do, not what broke. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  FOLIO_NOT_FOUND: 'Esta conta não existe mais.',
  FOLIO_BALANCE_NOT_ZERO: 'Esta conta ainda tem saldo. Receba o pagamento antes de fechar.',
  FOLIO_CLOSED: 'Esta conta já foi fechada.',
  FOLIO_OWNED_BY_TAB: 'Esta conta é de uma comanda e só pode ser fechada pelo garçom.',
  CHARGE_NOT_FOUND: 'Este lançamento não existe mais.',
  PAYMENT_NOT_FOUND: 'Este pagamento não existe mais.',
  PAYMENT_ALREADY_REFUNDED: 'Este pagamento já foi estornado.',
  CASH_DRAWER_SESSION_NOT_FOUND: 'Este turno não existe mais.',
  CASH_DRAWER_SESSION_CLOSED: 'Este turno já foi fechado.',
  MISSING_IDEMPOTENCY_KEY: 'Tente de novo; se continuar, avise o gerente.',
  INVALID_AMOUNT: 'Informe um valor válido.',
  INVALID_REASON: 'Escreva o motivo.',
  FORBIDDEN: 'Seu perfil não pode fazer isso.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo; se continuar, avise o gerente.';
const OFFLINE = 'Sem conexão com o servidor. Veja a rede e tente de novo.';

import { ApiError, NetworkError } from '../../lib/problemDetail';

/**
 * A code without translation never reaches the cashier raw: it shows the generic
 * line and shouts in the console, so the gap shows up in development.
 */
export function describeError(error: unknown): string {
  if (error instanceof NetworkError) {
    return OFFLINE;
  }
  if (error instanceof ApiError) {
    if (error.status === 403) {
      return ERROR_MESSAGES['FORBIDDEN']!;
    }
    if (error.code) {
      const known = ERROR_MESSAGES[error.code];
      if (known) {
        return known;
      }
      console.error(`[cashier] error code without translation: ${error.code}`);
    }
  }
  return GENERIC_FAILURE;
}
