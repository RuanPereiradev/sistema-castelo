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
    requestConfirmed: (label: string) => `Pedido da mesa ${label} confirmado e lançado`,
    requestRejected: (label: string) => `Pedido da mesa ${label} recusado`,
    pixConfirmed: (label: string) => `Pix da mesa ${label} confirmado`,
    pixRejected: (label: string) => `Pix da mesa ${label} recusado`,
    pixRefunded: (customer: string) => `Pix de ${customer} estornado`,
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
  orderRequests: {
    open: 'Pedidos do celular',
    openWithCount: (count: number) => `Pedidos do celular · ${count}`,
    title: 'Pedidos do celular',
    subtitle: 'Aguardando o garçom',
    /** The map's badge: short, it sits on top of a table about 110 px wide. */
    badgeFor: (orders: number, pix: number) =>
      orders > 0 && pix > 0
        ? 'Pedido e Pix'
        : pix > 0
          ? pix === 1
            ? 'Pix aguardando'
            : `${pix} Pix aguardando`
          : orders === 1
            ? 'Pedido aguardando'
            : `${orders} pedidos aguardando`,
    loading: 'Carregando os pedidos do celular…',
    failed: 'Não deu para carregar os pedidos do celular.',
    empty: 'Nenhum pedido do celular aguardando.',
    emptyForTable: 'Esta mesa não tem pedido do celular aguardando.',
    showAll: 'Ver todos',
    inSheet: (orders: number, pix: number) => {
      const parts = [
        orders === 0 ? null : orders === 1 ? 'um pedido aguardando' : `${orders} pedidos aguardando`,
        pix === 0 ? null : pix === 1 ? 'um Pix para conferir' : `${pix} Pix para conferir`,
      ].filter(Boolean);
      return `Do celular desta mesa: ${parts.join(' e ')}.`;
    },
    seeRequests: 'Ver no celular da mesa',
    anonymous: 'Cliente sem nome',
    requestedAt: (time: string) => `Pedido às ${time}`,
    expiresIn: (minutes: number) => (minutes <= 0 ? 'Expira agora' : `Expira em ${minutes} min`),
    expiresAt: (time: string) => `às ${time}`,
    noQuantity: 'sem quantidade',
    confirm: 'Confirmar',
    confirming: 'Confirmando…',
    reject: 'Recusar',
    rejecting: 'Recusando…',
    rejectTitle: 'Recusar o pedido?',
    rejectMessage: (label: string, customer: string) =>
      `O pedido de ${customer} na mesa ${label} sai da fila e nada é lançado. Avise o cliente; não há desfazer.`,
    rejectConfirm: 'Recusar pedido',
    keep: 'Voltar',
    ordersSection: 'Pedidos',
    pixSection: 'Pix para conferir',
    pixAmount: 'Valor do Pix',
    withService: (value: string) => `com serviço ${value}`,
    withoutService: 'sem serviço',
    units: (quantity: number, lineQuantity: number) =>
      quantity === lineQuantity ? `${quantity}×` : `${quantity} de ${lineQuantity}`,
    confirmPix: 'Confirmar Pix',
    pixHint: 'Confira o comprovante no celular do cliente antes de confirmar.',
    rejectPixTitle: 'Recusar o Pix?',
    rejectPixMessage: (label: string, customer: string) =>
      `O Pix de ${customer} na mesa ${label} é recusado e os itens voltam para a conta. Use quando o comprovante não confere.`,
    rejectPixConfirm: 'Recusar Pix',
  },
  paidByPhone: {
    title: 'Pagos pelo celular',
    loading: 'Carregando os Pix confirmados…',
    unavailable: 'Indisponível por enquanto: o servidor ainda não lista os Pix confirmados.',
    failed: 'Não deu para carregar os Pix confirmados.',
    empty: 'Nenhum Pix confirmado nesta comanda.',
    confirmedAt: (time: string) => `Confirmado às ${time}`,
    refunded: 'Estornado',
    refundedAt: (time: string) => `Estornado às ${time}`,
    refundReason: (reason: string) => `Motivo: ${reason}`,
    refund: 'Estornar',
    refunding: 'Estornando…',
    refundTitle: 'Estornar o Pix?',
    refundMessage: (customer: string, amount: string) =>
      `O Pix de ${customer} (${amount}) é estornado e os itens voltam a ficar em aberto na conta. Não há desfazer: devolva o dinheiro ao cliente por fora.`,
    refundReasonLabel: 'Motivo do estorno',
    refundReasonPlaceholder: 'Ex.: cliente pagou duas vezes',
    refundConfirm: 'Estornar Pix',
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
  EXPENSE_NOT_FOUND: 'Esta despesa não existe.',
  EXPENSE_ALREADY_PAID: 'Esta despesa já foi paga.',
  INVALID_EXPENSE_AMOUNT: 'Valor da despesa inválido.',
  INVALID_EXPENSE_DESCRIPTION: 'Descrição da despesa inválida.',
  INVALID_EXPENSE_DATES: 'As datas da despesa são inválidas.',
  INVALID_EXPENSE_EMPLOYEE: 'Funcionário inválido.',
  INVALID_EXPENSE_CANCELLATION: 'Não é possível cancelar esta despesa.',
  TABLE_ORDER_REQUEST_NOT_PENDING: 'Outro garçom já resolveu este pedido. A lista vai se atualizar.',
  TABLE_ORDER_REQUEST_EXPIRED: 'Este pedido passou do prazo e expirou. Fale com o cliente e lance você mesmo, se for o caso.',
  TABLE_ORDER_REQUEST_NOT_FOUND: 'Este pedido não existe mais. A lista vai se atualizar.',
  TABLE_PAYMENT_REQUEST_NOT_PENDING: 'Outro garçom já resolveu este Pix. A lista vai se atualizar.',
  TABLE_PAYMENT_REQUEST_EXPIRED: 'Este Pix passou dos 30 minutos e expirou. Se o cliente pagou, lance o pagamento pelo caixa.',
  TABLE_PAYMENT_REQUEST_NOT_FOUND: 'Este Pix não existe mais. A lista vai se atualizar.',
  TABLE_PAYMENT_REQUEST_NOT_REFUNDABLE: 'Só um Pix confirmado e ainda não estornado pode ser estornado.',
  INVALID_PAYMENT_REFUND_REASON: 'Escreva o motivo do estorno, com até 500 letras.',
  PAYMENT_ALREADY_REFUNDED: 'Este pagamento já foi estornado. A tela vai se atualizar.',
};

/** Confirming a Pix, the tab's own state matters more than in other places. */
const REFUND_PIX_MESSAGES: Readonly<Record<string, string>> = {
  TAB_NOT_OPEN: 'A comanda desta mesa já foi fechada: o estorno agora é pelo caixa.',
};

export function describeRefundPixError(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const specific = REFUND_PIX_MESSAGES[error.code];
    if (specific) {
      return specific;
    }
  }
  return describeError(error);
}

const CONFIRM_PIX_MESSAGES: Readonly<Record<string, string>> = {
  PAYMENT_EXCEEDS_BALANCE: 'A conta já recebeu mais do que falta: este Pix passaria do saldo. Recuse e acerte no caixa.',
  TAB_NOT_OPEN: 'A comanda desta mesa já foi fechada ou cancelada. Recuse o Pix e acerte no caixa.',
};

export function describeConfirmPixError(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const specific = CONFIRM_PIX_MESSAGES[error.code];
    if (specific) {
      return specific;
    }
  }
  return describeError(error);
}

/**
 * A confirmation refused by the posting keeps the request pending (#21 of
 * qr-table-ordering): the waiter reads why and rejects, or fixes and retries.
 */
const CONFIRM_REQUEST_MESSAGES: Readonly<Record<string, string>> = {
  MENU_ITEM_UNAVAILABLE: 'Um item deste pedido está esgotado. Recuse o pedido e avise o cliente.',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Um item deste pedido não é servido neste horário. Recuse o pedido e avise o cliente.',
  MENU_ITEM_VARIANT_UNAVAILABLE: 'Um tamanho escolhido está indisponível. Recuse o pedido e avise o cliente.',
  MENU_ITEM_NOT_FOUND: 'Um item deste pedido saiu do cardápio. Recuse o pedido e avise o cliente.',
  TAB_NOT_OPEN: 'A comanda da mesa não aceita item agora (a conta foi pedida). Recuse o pedido ou reabra a comanda.',
  TAB_ALREADY_OPEN_FOR_DINING_TABLE: 'A mesa ganhou comanda ao mesmo tempo. Tente confirmar de novo.',
  INACTIVE_DINING_TABLE: 'Esta mesa foi desativada. Recuse o pedido.',
};

export function describeConfirmRequestError(error: unknown): string {
  if (error instanceof ApiError && error.code) {
    const specific = CONFIRM_REQUEST_MESSAGES[error.code];
    if (specific) {
      return specific;
    }
  }
  return describeError(error);
}

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
