import { ApiError, NetworkError } from '../../lib/problemDetail';

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
  noOpenSession: 'Nenhum turno aberto',
  openSessionInstruction: 'Conte o fundo de troco que está na gaveta e abra o turno.',
  openSession: 'Abrir turno',
  openedAt: 'Aberto em',
  movements: 'Movimentos da gaveta',
  movementColumns: {
    type: 'Tipo',
    amount: 'Valor',
    reason: 'Motivo',
    time: 'Hora',
  },
  movementType: {
    CASH_DROP: 'Sangria',
    CASH_SUPPLY: 'Suprimento',
    EXPENSE_PAYMENT: 'Despesa paga',
  } as Readonly<Record<'CASH_DROP' | 'CASH_SUPPLY' | 'EXPENSE_PAYMENT', string>>,
  registerMovement: 'Sangria ou suprimento',
  registerDrop: 'Registrar sangria',
  registerSupply: 'Registrar suprimento',
  closeSessionConfirmTitle: 'Fechar o turno?',
  closeSessionConfirmMessage: (counted: string) =>
    `Você contou ${counted} na gaveta. Depois de fechar, o turno não reabre e a contagem não muda.`,
  retry: 'Tentar de novo',

  // Folios
  openFolios: 'Contas em aberto',
  noFolios: 'Nenhuma conta aberta',
  folioNumber: 'Folio',
  guestName: 'Nome',
  folioStatusLabel: 'Status',
  charges: 'Lançamentos',
  payments: 'Pagamentos',
  totalCharges: 'Total lançado',
  totalPayments: 'Total pago',
  balance: 'Saldo',
  foliaTABClosureNote: 'A comanda só pode ser fechada pelo garçom.',

  // Actions
  receivePayment: 'Receber pagamento',
  postAdjustment: 'Lançar ajuste',
  reverseCharge: 'Estornar lançamento',
  refundPayment: 'Estornar pagamento',
  closeFolio: 'Fechar conta',
  cancel: 'Cancelar',
  amountToReceive: 'Valor a receber',
  paymentMethodLabel: 'Forma de pagamento',
  processing: 'Processando…',

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
  cashDrawerTabLabel: 'Turno',
  foliosTabLabel: 'Contas',

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
    dropRegistered: 'Sangria registrada',
    supplyRegistered: 'Suprimento registrado',
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
  CASH_DRAWER_SESSION_ALREADY_OPEN: 'Já existe um turno aberto. A tela foi atualizada.',
  CASH_DRAWER_SESSION_NOT_OWNED: 'Só quem abriu o turno, ou o gerente, pode fechá-lo.',
  INVALID_OPENING_FLOAT: 'O fundo de troco não pode ser negativo.',
  INVALID_CASH_MOVEMENT_AMOUNT: 'Informe um valor maior que zero.',
  INVALID_CASH_MOVEMENT_REASON: 'Escreva o motivo, com até 500 caracteres.',
  INVALID_COUNTED_AMOUNT: 'O valor contado não pode ser negativo.',
  INVALID_CASH_CLOSING_NOTE: 'A nota do fechamento tem no máximo 500 caracteres.',
  INVALID_MONEY: 'Valor inválido. Use só números, com vírgula nos centavos: 150,50.',
  MONEY_OUT_OF_RANGE: 'Valor alto demais. Confira o que foi digitado.',
  MISSING_IDEMPOTENCY_KEY: 'Tente de novo; se continuar, avise o gerente.',
  INVALID_IDEMPOTENCY_KEY: 'Tente de novo; se continuar, avise o gerente.',
  IDEMPOTENCY_KEY_REUSED: 'Esta operação foi recusada. Verifique o valor e tente de novo.',
  INVALID_AMOUNT: 'Informe um valor válido.',
  INVALID_PAYMENT_AMOUNT: 'Informe um valor de pagamento válido.',
  INVALID_REASON: 'Escreva o motivo.',
  PAYMENT_EXCEEDS_BALANCE: 'O valor não pode ser maior que o saldo.',
  PAYMENT_METHOD_NOT_ACCEPTED: 'Esta forma de pagamento não é aceita.',
  FORBIDDEN: 'Seu perfil não pode fazer isso.',
  ACCESS_DENIED: 'Seu perfil não pode fazer isso.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo; se continuar, avise o gerente.';
const OFFLINE = 'Sem conexão com o servidor. Veja a rede e tente de novo.';

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
