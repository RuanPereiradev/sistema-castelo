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
  folioType: {
    STAY: 'Estadia',
    TAB: 'Comanda',
  } as Readonly<Record<'STAY' | 'TAB', string>>,
  chargeCount: (count: number) => (count === 1 ? '1 lançamento' : `${count} lançamentos`),
  charges: 'Lançamentos',
  payments: 'Pagamentos',
  noCharges: 'Nenhum lançamento.',
  noPayments: 'Nenhum pagamento.',
  totalCharges: 'Total lançado',
  totalPayments: 'Total pago',
  balance: 'Saldo',
  balanceCredit: 'crédito do hóspede',
  tabFolioNote: 'Esta conta é de uma comanda: o estorno e o fechamento são feitos pela comanda.',
  chooseFolio: 'Escolha uma conta para ver os lançamentos e os pagamentos.',
  folioColumns: {
    description: 'Descrição',
    amount: 'Valor',
    type: 'Tipo',
    method: 'Forma',
    status: 'Status',
    actions: '',
  },
  reversalOfLine: 'estorno',
  reversedLine: 'estornado',
  refundedReason: (reason: string) => `Motivo do estorno: ${reason}`,

  // Actions
  receivePayment: 'Receber pagamento',
  postAdjustment: 'Lançar ajuste',
  reverseCharge: 'Estornar',
  refundPayment: 'Estornar',
  closeFolio: 'Fechar conta',
  cancel: 'Cancelar',
  amountToReceive: 'Valor a receber',
  paymentMethodLabel: 'Forma de pagamento',
  processing: 'Processando…',
  reasonLabel: 'Motivo',
  reasonPlaceholder: 'Escreva o motivo, até 500 caracteres',

  reverseChargeTitle: 'Estornar o lançamento?',
  reverseChargeMessage: (description: string, amount: string) =>
    `O lançamento "${description}" (${amount}) ganha um estorno do mesmo valor. O original continua na conta e o estorno não se desfaz.`,
  refundPaymentTitle: 'Estornar o pagamento?',
  refundPaymentMessage: (method: string, amount: string) =>
    `O pagamento em ${method} de ${amount} fica estornado e volta para o saldo da conta. Não se desfaz.`,
  closeFolioTitle: 'Fechar a conta?',
  closeFolioMessage: (name: string) =>
    `A conta ${name} será fechada e não recebe mais lançamento nem pagamento. Só fecha com o saldo zerado.`,
  adjustmentTitle: 'Lançar ajuste',
  adjustmentMessage: 'O ajuste entra como um lançamento na conta e não se apaga; para corrigir, lance outro ajuste.',
  adjustmentKind: {
    discount: 'Desconto',
    surcharge: 'Acréscimo',
  },
  adjustmentAmount: 'Valor',
  adjustmentDescription: 'Descrição',
  adjustmentDescriptionPlaceholder: 'O que aparece na conta, até 255 caracteres',

  // Charge types
  chargeType: {
    ROOM_NIGHT: 'Diária',
    TAB: 'Consumo do restaurante',
    ADJUSTMENT: 'Ajuste',
  } as Readonly<Record<'ROOM_NIGHT' | 'TAB' | 'ADJUSTMENT', string>>,

  // Payment methods
  paymentMethod: {
    CASH: 'Dinheiro',
    PIX: 'Pix',
    CREDIT_CARD: 'Cartão de crédito',
    DEBIT_CARD: 'Cartão de débito',
    ROOM_ACCOUNT: 'Conta do hóspede',
  } as Readonly<Record<'CASH' | 'PIX' | 'CREDIT_CARD' | 'DEBIT_CARD' | 'ROOM_ACCOUNT', string>>,

  // Status
  statusLabel: {
    OPEN: 'Aberto',
    CLOSED: 'Fechado',
  } as Readonly<Record<'OPEN' | 'CLOSED', string>>,

  paymentStatus: {
    PENDING: 'Pendente',
    CONFIRMED: 'Confirmado',
    FAILED: 'Falha',
    REFUNDED: 'Estornado',
  } as Readonly<Record<'PENDING' | 'CONFIRMED' | 'FAILED' | 'REFUNDED', string>>,

  folioStatus: {
    OPEN: 'Aberta',
    CLOSED: 'Fechada',
  } as Readonly<Record<'OPEN' | 'CLOSED', string>>,

  // Tabs of the screen
  cashDrawerTabLabel: 'Turno',
  foliosTabLabel: 'Contas',
  restaurantTabsLabel: 'Comandas',

  // Messages
  loadingSessions: 'Carregando turno…',
  loadingFolios: 'Carregando contas…',
  loadingFolio: 'Carregando a conta…',
  sessionFailed: 'Não deu para carregar o turno.',
  foliosFailed: 'Não deu para carregar as contas.',
  folioFailed: 'Não deu para carregar esta conta.',

  // Summary
  openFolioCount: 'Contas abertas',

  // Restaurant tabs, read only (decision #17 of task-qr-table-ordering)
  restaurantTabs: {
    title: 'Comandas abertas',
    readOnlyNote: 'Só leitura: quem lança, transfere e fecha a comanda é o garçom.',
    loading: 'Carregando comandas…',
    failed: 'Não deu para carregar as comandas.',
    loadingTab: 'Carregando a comanda…',
    tabFailed: 'Não deu para carregar esta comanda.',
    empty: 'Nenhuma comanda aberta.',
    choose: 'Escolha uma comanda para ver os itens.',
    noItems: 'Nenhum item lançado.',
    table: (label: string) => `Mesa ${label}`,
    card: (cardNumber: number) => `Cartão ${cardNumber}`,
    unnamed: 'Comanda',
    itemCount: (count: number) => (count === 1 ? '1 item' : `${count} itens`),
    status: {
      OPEN: 'Aberta',
      CLOSING: 'Em fechamento',
      CLOSED: 'Fechada',
      CANCELLED: 'Cancelada',
      MERGED: 'Juntada a outra',
    } as Readonly<Record<'OPEN' | 'CLOSING' | 'CLOSED' | 'CANCELLED' | 'MERGED', string>>,
    itemStatus: {
      PENDING: 'Aguardando',
      IN_PREPARATION: 'Em preparo',
      READY: 'Pronto',
      DELIVERED: 'Servido',
      CANCELLED: 'Cancelado',
    } as Readonly<Record<'PENDING' | 'IN_PREPARATION' | 'READY' | 'DELIVERED' | 'CANCELLED', string>>,
    columns: {
      item: 'Item',
      quantity: 'Qtd.',
      status: 'Situação',
      amount: 'Valor',
    },
    subtotal: 'Subtotal',
    serviceCharge: 'Taxa de serviço',
    total: 'Total parcial',
    guestCount: 'Pessoas',
    openedAt: 'Aberta em',
  },

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
  FOLIO_BALANCE_NOT_ZERO: 'O saldo desta conta não está zerado. A conta só fecha com saldo zero.',
  FOLIO_CLOSED: 'Esta conta acabou de ser fechada. A tela foi atualizada.',
  FOLIO_OWNED_BY_TAB: 'Esta conta é de uma comanda: o estorno e o fechamento são feitos pela comanda.',
  CHARGE_NOT_FOUND: 'Este lançamento não existe mais.',
  CHARGE_ALREADY_REVERSED: 'Este lançamento já foi estornado. A tela foi atualizada.',
  CHARGE_NOT_REVERSIBLE: 'Ajuste e estorno não se estornam. Para corrigir, lance um ajuste.',
  INVALID_CHARGE_AMOUNT: 'Informe um valor diferente de zero.',
  INVALID_CHARGE_DESCRIPTION: 'Escreva a descrição, com até 255 caracteres.',
  INVALID_CHARGE_REASON: 'Escreva o motivo, com até 500 caracteres.',
  PAYMENT_NOT_FOUND: 'Este pagamento não existe mais.',
  PAYMENT_ALREADY_REFUNDED: 'Este pagamento já foi estornado. A tela foi atualizada.',
  INVALID_PAYMENT_REFUND_REASON: 'Escreva o motivo do estorno, com até 500 caracteres.',
  CASH_DRAWER_SESSION_NOT_FOUND: 'Este turno não existe mais.',
  CASH_DRAWER_SESSION_CLOSED: 'Este turno já foi fechado.',
  CASH_DRAWER_SESSION_NOT_OPEN: 'Abra o turno de caixa antes de receber em dinheiro.',
  CASH_DRAWER_SESSION_ALREADY_OPEN: 'Já existe um turno aberto. A tela foi atualizada.',
  CASH_DRAWER_SESSION_NOT_OWNED: 'Só quem abriu o turno, ou o gerente, pode fechá-lo.',
  INVALID_OPENING_FLOAT: 'O fundo de troco não pode ser negativo.',
  INVALID_CASH_MOVEMENT_AMOUNT: 'Informe um valor maior que zero.',
  INVALID_CASH_MOVEMENT_REASON: 'Escreva o motivo, com até 500 caracteres.',
  INVALID_COUNTED_AMOUNT: 'O valor contado não pode ser negativo.',
  INVALID_CASH_CLOSING_NOTE: 'A nota do fechamento tem no máximo 500 caracteres.',
  INVALID_MONEY: 'Valor inválido. Use só números, com vírgula nos centavos: 150,50.',
  MONEY_SCALE_EXCEEDED: 'Use no máximo dois dígitos nos centavos: 150,50.',
  MONEY_OUT_OF_RANGE: 'Valor alto demais. Confira o que foi digitado.',
  MISSING_IDEMPOTENCY_KEY: 'Tente de novo; se continuar, avise o gerente.',
  INVALID_IDEMPOTENCY_KEY: 'Tente de novo; se continuar, avise o gerente.',
  IDEMPOTENCY_KEY_REUSED: 'Esta operação foi recusada. Verifique o valor e tente de novo.',
  INVALID_AMOUNT: 'Informe um valor válido.',
  INVALID_PAYMENT_AMOUNT: 'Informe um valor de pagamento maior que zero.',
  INVALID_REASON: 'Escreva o motivo.',
  PAYMENT_EXCEEDS_BALANCE: 'Esta conta de comanda não recebe mais que o saldo. Confira o valor.',
  PAYMENT_METHOD_NOT_ACCEPTED: 'Esta forma de pagamento não é aceita aqui.',
  TAB_NOT_FOUND: 'Esta comanda não existe mais.',
  INVALID_ENTITY_ID: 'Este registro não existe. Atualize a tela.',
  VALIDATION_FAILED: 'Pedido recusado pelo servidor. Atualize a tela e tente de novo.',
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
