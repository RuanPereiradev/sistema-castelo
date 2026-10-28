import { ApiError, NetworkError } from '../../lib/problemDetail';

/**
 * Every Portuguese string of the dining room lives here, never in a component.
 * The first block is what the screen says; the second translates the stable
 * `code` the API answers on failure into what the waiter should do next.
 */
export const DINING_ROOM = {
  brand: 'Hospedaria',
  title: 'Mesas',
  loadingTables: 'Carregando as mesas…',
  loadingTab: 'Carregando a comanda…',
  noTables: 'Nenhuma mesa cadastrada. Peça ao administrador para cadastrar o salão.',
  noTablesInFilter: 'Nenhuma mesa neste filtro.',
  tablesFailed: 'Não deu para carregar as mesas.',
  tabFailed: 'Não deu para carregar a comanda.',
  tabVanished: 'Esta comanda acabou de ser fechada ou movida por outra pessoa.',
  retry: 'Tentar de novo',
  noArea: 'Sem área',
  allTables: 'Todas',
  table: 'Mesa',
  seats: 'lugares',
  guests: 'Pessoas',
  openFor: 'Aberta há',
  minutes: 'min',
  items: 'Itens',
  noItems: 'Nenhum item lançado ainda.',
  subtotal: 'Subtotal',
  serviceCharge: 'Taxa de serviço',
  total: 'Total',
  close: 'Fechar',
  back: 'Voltar',
  openTable: 'Abrir mesa',
  opening: 'Abrindo…',
  addOrder: 'Lançar pedido',
  requestBill: 'Pedir a conta',
  requestBillTitle: 'Pedir a conta?',
  requestBillMessage:
    'A comanda vai para o caixa com a taxa de serviço congelada. Enquanto estiver lá, não aceita item novo.',
  requestBillConfirm: 'Enviar ao caixa',
  cancelBillRequest: 'Cancelar pedido de conta',
  cancelBillRequestTitle: 'Cancelar o pedido de conta?',
  cancelBillRequestMessage: 'A comanda volta a aceitar itens. Diga o motivo para ficar registrado.',
  cancelBillRequestConfirm: 'Reabrir comanda',
  moreActions: 'Mais ações',
  fewerActions: 'Menos ações',
  transfer: 'Transferir itens',
  merge: 'Juntar comandas',
  move: 'Trocar de mesa',
  cancelTab: 'Cancelar comanda',
  cancelTabTitle: 'Cancelar a comanda?',
  cancelTabMessage: 'Não há desfazer. Diga o motivo para ficar registrado.',
  cancelTabConfirm: 'Cancelar comanda',
  keep: 'Voltar',
  billWithCashier: 'A conta já está com o caixa. Ele fecha e libera a mesa.',
  readyInKitchen: 'A cozinha avisou que há prato pronto para esta mesa.',
  statusLabel: {
    free: 'Livre',
    occupied: 'Ocupada',
    billRequested: 'Conta pedida',
  },
  itemStatus: {
    PENDING: 'Aguardando',
    IN_PREPARATION: 'Em preparo',
    READY: 'Pronto',
    DELIVERED: 'Servido',
    CANCELLED: 'Cancelado',
  } as Readonly<Record<string, string>>,
  notice: {
    tableOpened: (label: string) => `Mesa ${label} aberta`,
    billSent: (label: string) => `Conta da mesa ${label} enviada ao caixa`,
    billCancelled: (label: string) => `Pedido de conta da mesa ${label} cancelado`,
    tabCancelled: (label: string) => `Comanda da mesa ${label} cancelada`,
    itemAdded: (name: string) => `${name} lançado`,
    itemCancelled: 'Item cancelado',
    itemDelivered: 'Item entregue',
    itemsTransferred: 'Itens transferidos',
    tabsMerged: 'Comandas juntadas',
    tableMoved: (label: string) => `Comanda movida para a mesa ${label}`,
    paymentProcessed: 'Pagamento recebido',
    tabClosed: (label: string) => `Comanda da mesa ${label} fechada`,
  },
  payment: {
    title: 'Receber pagamento',
    balance: 'Saldo devido',
    amount: 'Valor a receber',
    method: 'Forma de pagamento',
    confirmPayment: 'Confirmar pagamento',
    processing: 'Processando...',
    cash: 'Dinheiro',
    pix: 'Pix',
    creditCard: 'Cartão de crédito',
    debitCard: 'Cartão de débito',
    roomAccount: 'Conta de hóspede',
  },
} as const;

/** Error code → what the waiter reads. Says what to do, not what broke. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  // Tab-related errors
  TAB_NOT_FOUND: 'Esta comanda não existe mais. A tela vai se atualizar.',
  TAB_NOT_OPEN: 'Esta comanda não está aberta: alguém pediu a conta ou fechou. Veja a mesa de novo.',
  TAB_NOT_CLOSING: 'Esta comanda não está em fechamento. Veja a mesa de novo.',
  TAB_ALREADY_OPEN_FOR_DINING_TABLE: 'Esta mesa já tem comanda aberta. Veja a mesa de novo.',
  TAB_ALREADY_OPEN_FOR_CARD: 'Este cartão já tem comanda aberta. Use outro.',
  TAB_HAS_NO_ACTIVE_ITEMS: 'A comanda está vazia. Lance um item antes de pedir a conta.',
  TAB_HAS_ACTIVE_ITEMS: 'A comanda ainda tem itens. Cancele ou transfira antes.',
  TAB_ALREADY_CLOSED: 'Esta comanda já foi fechada.',

  // Tab item-related errors
  TAB_ITEM_NOT_FOUND: 'Este item não está mais na comanda.',
  TAB_ITEM_ALREADY_CANCELLED: 'Este item já foi cancelado.',
  TAB_ITEM_NOT_SERVICE_CHARGEABLE: 'Este item não é cobrado a taxa de serviço.',
  INVALID_TAB_ITEM_TRANSITION: 'A cozinha já avançou este item. Não dá mais para mudar.',
  INVALID_TAB_ITEM_MODIFIER_QUANTITY: 'Quantidade de adicional acima do permitido.',
  DUPLICATE_TAB_ITEM_MODIFIER: 'O mesmo adicional foi escolhido duas vezes.',

  // Tab operations
  INVALID_TAB_TRANSFER: 'Escolha ao menos um item e uma comanda de destino diferente.',
  INVALID_TAB_MERGE: 'Essas comandas não podem ser juntadas. Veja se as duas estão abertas.',
  INVALID_TAB_OPENING: 'Escolha uma mesa para abrir a comanda.',
  TRANSFERRING_TO_SAME_TAB: 'Escolha uma comanda de destino diferente.',

  // Guest and billing errors
  INVALID_GUEST_COUNT: 'Informe de 1 a 99 pessoas.',
  INVALID_CARD_NUMBER: 'Cartão inválido.',
  INVALID_CANCELLATION_REASON: 'Escreva o motivo do cancelamento.',
  INVALID_REOPENING_REASON: 'Escreva o motivo para reabrir.',
  INVALID_SPLIT_GROUP: 'Escolha a divisão corretamente.',
  INVALID_SPLIT_PARTS: 'A divisão tem partes inválidas.',
  INVALID_SPECIAL_INSTRUCTIONS: 'A observação está longa demais.',
  INVALID_QUANTITY: 'Quantidade inválida.',

  // Dining table errors
  DINING_TABLE_NOT_FOUND: 'Esta mesa não existe mais. A tela vai se atualizar.',
  DINING_TABLE_HAS_OPEN_TAB: 'A mesa de destino já tem comanda. Escolha uma mesa livre.',
  DINING_TABLE_LABEL_ALREADY_USED: 'Este rótulo de mesa já existe.',
  INVALID_DINING_TABLE_LABEL: 'Rótulo da mesa inválido.',
  INVALID_DINING_TABLE_AREA: 'Área da mesa inválida.',
  INVALID_DINING_TABLE_SEATS: 'Número de lugares inválido.',
  INACTIVE_DINING_TABLE: 'Esta mesa está desativada.',

  // Menu item errors
  MENU_ITEM_NOT_FOUND: 'Este item saiu do cardápio.',
  MENU_ITEM_UNAVAILABLE: 'Este item está indisponível agora.',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Este item não é servido neste horário.',
  MENU_ITEM_NAME_ALREADY_USED: 'Este nome de item já existe.',
  INVALID_MENU_ITEM_NAME: 'Nome do item inválido.',
  INVALID_MENU_ITEM_PRICING: 'Preço do item inválido.',

  // Menu variant errors
  MENU_ITEM_VARIANT_REQUIRED: 'Escolha o tamanho.',
  MENU_ITEM_VARIANT_NOT_FOUND: 'Este tamanho saiu do cardápio.',
  MENU_ITEM_VARIANT_UNAVAILABLE: 'Este tamanho está indisponível agora.',
  MENU_ITEM_VARIANT_NAME_ALREADY_USED: 'Este nome de tamanho já existe para este item.',
  INVALID_MENU_ITEM_VARIANT_NAME: 'Nome do tamanho inválido.',

  // Modifier errors
  MODIFIER_NOT_OFFERED: 'Este adicional não vale para este item.',
  MODIFIER_NOT_FOUND: 'Este adicional saiu do cardápio.',
  MODIFIER_NAME_ALREADY_USED: 'Este nome de adicional já existe.',
  INACTIVE_MODIFIER: 'Este adicional está desativado.',
  INVALID_MODIFIER_NAME: 'Nome do adicional inválido.',
  INVALID_MODIFIER_PRICE: 'Preço do adicional inválido.',
  INVALID_MODIFIER_MAX_QUANTITY: 'Quantidade máxima do adicional inválida.',

  // Weight and quantity errors
  SOLD_BY_WEIGHT_REQUIRES_WEIGHT: 'Informe o peso em gramas.',
  SOLD_BY_WEIGHT_REJECTS_QUANTITY: 'Item por peso não leva quantidade. Informe o peso.',
  SOLD_BY_WEIGHT_REJECTS_VARIANT: 'Item por peso não tem tamanho.',
  SOLD_BY_WEIGHT_REJECTS_MODIFIER: 'Item por peso não leva adicional.',
  SOLD_BY_UNIT_REJECTS_WEIGHT: 'Este item não é vendido por peso.',
  INVALID_WEIGHT: 'Peso inválido.',

  // Menu category errors
  MENU_CATEGORY_NOT_FOUND: 'Esta categoria saiu do cardápio.',
  MENU_CATEGORY_NAME_ALREADY_USED: 'Este nome de categoria já existe.',
  INVALID_MENU_CATEGORY_NAME: 'Nome da categoria inválido.',

  // Availability window errors
  INVALID_AVAILABILITY_WINDOW: 'Horário indisponível.',

  // Folio and billing errors (common in payment flow)
  FOLIO_NOT_FOUND: 'Este folio não existe.',
  FOLIO_CLOSED: 'Este folio está fechado.',
  FOLIO_BALANCE_NOT_ZERO: 'O folio ainda tem saldo a pagar.',
  FOLIO_OWNED_BY_TAB: 'Este folio é da comanda. Procure o garçom.',
  PAYMENT_EXCEEDS_BALANCE: 'O valor do pagamento excede o saldo.',
  PAYMENT_NOT_FOUND: 'Este pagamento não existe.',
  PAYMENT_ALREADY_REFUNDED: 'Este pagamento já foi reembolsado.',
  INVALID_PAYMENT_AMOUNT: 'Valor de pagamento inválido.',
  PAYMENT_METHOD_NOT_ACCEPTED: 'Esta forma de pagamento não é aceita.',

  // Idempotency errors
  INVALID_IDEMPOTENCY_KEY: 'Chave de operação inválida. Tente de novo.',
  IDEMPOTENCY_KEY_REUSED: 'Esta operação já foi processada com um valor diferente.',

  // Authentication and authorization
  AUTHENTICATION_REQUIRED: 'Você precisa estar autenticado. Faça login e tente de novo.',
  UNAUTHORIZED: 'Você precisa estar autenticado.',
  ACCESS_DENIED: 'Seu perfil não pode fazer isso.',
  FORBIDDEN: 'Seu perfil não pode fazer isso.',

  // Generic and framework errors
  VALIDATION_FAILED: 'Os dados enviados estão inválidos. Revise e tente de novo.',
  MALFORMED_REQUEST: 'O pedido está mal formatado. Tente de novo.',
  RESOURCE_NOT_FOUND: 'O recurso procurado não existe.',
  METHOD_NOT_ALLOWED: 'Este método não é permitido.',
  REQUEST_BODY_TOO_LARGE: 'O pedido é muito grande. Reduza e tente de novo.',
  INTERNAL_ERROR: 'Erro no servidor. Avise o administrador se continuar.',
  INVALID_CREDENTIALS: 'Usuário ou senha incorretos.',
  INVALID_USERNAME: 'Nome de usuário inválido.',
  PASSWORD_TOO_LONG: 'A senha é muito longa.',
  INVALID_TOKEN: 'Sessão expirada. Faça login de novo.',

  // Cash and financial errors (may appear in payment context)
  CHARGE_NOT_FOUND: 'Este lançamento não existe.',
  CHARGE_ALREADY_REVERSED: 'Este lançamento já foi estornado.',
  CHARGE_NOT_REVERSIBLE: 'Este lançamento não pode ser estornado.',
  INVALID_CHARGE_AMOUNT: 'Valor do lançamento inválido.',
  INVALID_CHARGE_DESCRIPTION: 'Descrição do lançamento inválida.',
  CASH_DRAWER_SESSION_NOT_OPEN: 'Nenhum turno de caixa aberto.',
  INVALID_CASH_MOVEMENT_AMOUNT: 'Valor do movimento inválido.',

  // Expense and financial management
  INVALID_CHARGE_REASON: 'Motivo do lançamento inválido.',
  INVALID_PAYMENT_REFUND_REASON: 'Motivo do reembolso inválido.',
  EXPENSE_NOT_FOUND: 'Esta despesa não existe.',
  EXPENSE_ALREADY_PAID: 'Esta despesa já foi paga.',
  INVALID_EXPENSE_AMOUNT: 'Valor da despesa inválido.',
  INVALID_EXPENSE_DESCRIPTION: 'Descrição da despesa inválida.',
  INVALID_EXPENSE_DATES: 'As datas da despesa são inválidas.',
  INVALID_EXPENSE_EMPLOYEE: 'Funcionário inválido.',
  INVALID_EXPENSE_CANCELLATION: 'Não é possível cancelar esta despesa.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo; se continuar, avise o caixa.';
const OFFLINE = 'Sem conexão com o servidor. Veja a rede e tente de novo.';

/**
 * A code without translation never reaches the waiter raw: it shows the generic
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
      console.error(`[dining-room] error code without translation: ${error.code}`);
    }
  }
  return GENERIC_FAILURE;
}
