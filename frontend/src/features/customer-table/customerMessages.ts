import { ApiError, NetworkError } from '../../lib/problemDetail';

/**
 * Every Portuguese string of the customer's phone at the table lives here, never
 * in a component. The first block is what the screen says; the second translates
 * the stable `code` the API answers on failure into what the customer should do.
 */
export const CUSTOMER = {
  brand: 'Hospedaria',
  documentTitle: (table: string) => `Hospedaria · Mesa ${table}`,
  table: 'Mesa',
  back: 'Voltar',
  retry: 'Tentar de novo',

  welcome: {
    youAreAt: 'Você está na',
    intro: 'Por aqui você vê o cardápio, faz pedidos e acompanha o preparo de cada item.',
    nameLabel: 'Como podemos te chamar?',
    namePlaceholder: 'ex.: Marina',
    nameRequired: 'Digite seu nome para entrar.',
    nameTooLong: (max: number) => `O nome vai até ${max} letras.`,
    enter: 'Ver o cardápio',
    footnote:
      'Seu nome aparece nos seus pedidos, para a mesa saber de quem é cada item na hora de dividir a conta.',
    loading: 'Procurando a sua mesa…',
    failed: 'Não deu para abrir a mesa agora.',
  },

  invalidTable: {
    title: 'QR inválido',
    message: 'Este QR não abre nenhuma mesa. Chame o garçom, que ele ajuda você a pedir.',
  },

  titles: {
    menu: 'Cardápio',
    cart: 'Seu pedido',
    orders: 'Pedidos da mesa',
    bill: 'Conta da mesa',
    pix: 'Pagar com Pix',
    receipt: 'Comprovante',
  },

  nav: {
    menu: 'Cardápio',
    orders: 'Pedidos',
    bill: 'Conta',
    label: 'Navegação da mesa',
  },

  menu: {
    all: 'Tudo',
    categories: 'Categorias do cardápio',
    loading: 'Carregando o cardápio…',
    failed: 'Não deu para carregar o cardápio.',
    empty: 'O cardápio está vazio agora. Chame o garçom.',
    from: (price: string) => `a partir de ${price}`,
    perKilo: (price: string) => `${price} o quilo`,
    unavailable: 'Indisponível agora',
    servedAtBuffet: 'Servido no buffet',
    add: (name: string) => `Adicionar ${name}`,
    removeOne: 'Tirar um',
    addOne: 'Pôr mais um',
    inCart: (count: number) => (count === 1 ? '1 no pedido' : `${count} no pedido`),
    seeCart: 'Ver pedido',
    itemCount: (count: number) => (count === 1 ? '1 item' : `${count} itens`),
  },

  sheet: {
    close: 'Fechar',
    variant: 'Escolha o tamanho',
    variantRequired: 'Escolha um tamanho para continuar.',
    modifiers: 'Adicionais',
    modifierLimit: (max: number) => (max === 1 ? 'até 1' : `até ${max}`),
    modifierPrice: (price: string) => `+ ${price} cada`,
    quantity: 'Quantidade',
    addToCart: 'Pôr no pedido',
    unavailable: 'indisponível',
  },

  cart: {
    intro: 'Confira antes de enviar. O garçom confirma o pedido, e ele aparece em Pedidos com o seu nome.',
    empty: 'Nada escolhido ainda.',
    each: (price: string) => `${price} cada`,
    modifier: (name: string, quantity: number, price: string) =>
      quantity === 1 ? `+ ${name} · ${price}` : `+ ${quantity}× ${name} · ${price} cada`,
    remove: 'Tirar do pedido',
    note: 'Observação',
    notePlaceholder: 'ex.: sem cebola',
    addNote: '+ Observação',
    chooseMore: '+ Escolher mais',
    send: 'Enviar pedido',
    sending: 'Enviando…',
    sent: 'Pedido enviado. Agora é só aguardar o garçom confirmar.',
    closing: 'A conta da mesa está sendo fechada: não dá para pedir mais nada por aqui.',
    pendingMine: 'Você já tem um pedido aguardando o garçom. Assim que ele confirmar, você pode pedir de novo.',
    limitReached: 'A mesa já tem muitos pedidos esperando o garçom; aguarde um instante.',
    tablePending: (count: number) =>
      count === 1 ? '1 pedido da mesa aguardando o garçom.' : `${count} pedidos da mesa aguardando o garçom.`,
    fixName: 'Corrigir o nome',
  },

  bill: {
    loading: 'Carregando a conta…',
    failed: 'Não deu para carregar a conta.',
    empty: 'A mesa ainda não tem itens confirmados.',
    stats: { total: 'Mesa', paid: 'Já pago', due: 'Falta' },
    instructions: 'Marque o que você vai pagar. Pode ser só o seu ou qualquer item que ainda falte.',
    quick: 'Atalhos de seleção',
    onlyMine: 'Só os meus',
    everythingDue: 'Tudo que falta',
    clear: 'Limpar',
    yourItems: 'Seus itens',
    byWaiter: 'Pelo garçom',
    paid: 'Pago',
    reserved: 'Aguardando garçom',
    units: (count: number) => (count === 1 ? '1 un.' : `${count} un.`),
    lineTotal: (total: string, units: number) => (units === 1 ? total : `${total} · ${units} un.`),
    serviceChargeRate: (rate: string) => `Incluir ${rate.replace('.', ',').replace(/,00$/, '')}% de serviço`,
    optional: '(opcional)',
    nothingSelected: 'Nenhum item marcado',
    selected: (count: number, withService: boolean) =>
      `${count === 1 ? '1 item marcado' : `${count} itens marcados`}${withService ? ' + serviço' : ''}`,
    valueAfter: 'O valor aparece no Pix, calculado pelo restaurante.',
    calculating: 'calculando…',
    quoteBreakdown: (items: string, service: string) => `itens ${items} + serviço ${service}`,
    generatePix: 'Gerar Pix',
    generating: 'Gerando…',
    pixDisabled: 'O restaurante ainda não ativou o Pix pela mesa. Peça a conta ao garçom.',
    closing: 'A conta da mesa está com o caixa. Você ainda pode pagar a sua parte por aqui.',
    pendingPix: 'Você tem um Pix aguardando o garçom.',
    seePendingPix: 'Ver o Pix',
  },

  pix: {
    toPay: (count: number, withService: boolean) =>
      `${count === 1 ? '1 item' : `${count} itens`}${withService ? ' + serviço' : ''}`,
    qrLabel: 'QR Code do Pix',
    qrFailed: 'Não deu para desenhar o QR. Use o código abaixo.',
    copyTitle: 'Pix copia e cola',
    copy: 'Copiar',
    copied: 'Código Pix copiado',
    copyFailed: 'Não deu para copiar. Toque no código, selecione e copie.',
    validUntil: (time: string) => `Válido até ${time}`,
    steps: [
      'Abra o app do seu banco e pague pelo QR Code ou pelo código.',
      'Volte aqui e toque em Já paguei.',
      'Mostre o comprovante do banco ao garçom.',
    ] as readonly string[],
    paid: 'Já paguei',
    backToBill: 'Voltar à conta',
    subtotal: 'Itens',
    service: 'Serviço',
    noService: 'Sem serviço',
    loading: 'Carregando o Pix…',
    failed: 'Não deu para carregar o Pix.',
  },

  receipt: {
    states: {
      PENDING: 'Aguardando garçom',
      CONFIRMED: 'Pagamento confirmado',
      REJECTED: 'Recusado pelo garçom',
      EXPIRED: 'Expirou sem confirmação',
    } as Readonly<Record<string, string>>,
    refunded: 'Pagamento estornado',
    informed: (time: string) => `Pix gerado · ${time}`,
    showReceipt: 'Mostre o comprovante do seu banco ao garçom. Quando ele confirmar, seus itens ficam como pagos.',
    hints: {
      PENDING: 'Assim que o garçom conferir, esta tela muda sozinha.',
      CONFIRMED: 'Seus itens já estão como pagos na conta da mesa.',
      REJECTED: 'O garçom não confirmou este Pix. Fale com ele antes de pagar de novo.',
      EXPIRED: 'Ninguém confirmou a tempo e os itens voltaram para a conta. Se você já pagou, mostre o comprovante ao garçom.',
    } as Readonly<Record<string, string>>,
    refundedHint: 'O restaurante estornou este pagamento; os itens voltaram para a conta.',
    seePix: 'Ver o código Pix',
    backToBill: 'Voltar à conta da mesa',
  },

  orders: {
    all: 'Mesa toda',
    mine: 'Só os meus',
    filter: 'Quais pedidos mostrar',
    loading: 'Carregando os pedidos…',
    failed: 'Não deu para carregar os pedidos.',
    emptyTable: 'Nenhum item confirmado na mesa ainda.',
    emptyMine: 'Você ainda não fez pedidos.',
    closing: 'A conta da mesa está sendo fechada.',
    you: 'Você',
    byWaiter: 'Pelo garçom',
    steps: ['Recebido', 'Em preparo', 'Pronto', 'Servido'] as readonly string[],
    itemStatus: {
      PENDING: 'Recebido pela cozinha',
      IN_PREPARATION: 'Em preparo',
      READY: 'Pronto — já vai sair',
      DELIVERED: 'Servido',
    } as Readonly<Record<string, string>>,
    request: {
      title: (time: string) => `Seu último pedido · ${time}`,
      PENDING: 'Aguardando o garçom',
      CONFIRMED: 'Confirmado pelo garçom',
      REJECTED: 'Recusado pelo garçom',
      EXPIRED: 'Expirou sem confirmação',
      hint: {
        PENDING: 'O garçom vai conferir e confirmar. Depois os itens aparecem abaixo.',
        CONFIRMED: 'Os itens já estão com a cozinha. Acompanhe abaixo.',
        REJECTED: 'Fale com o garçom para saber o motivo.',
        EXPIRED: 'Ninguém confirmou a tempo. Envie de novo ou chame o garçom.',
      } as Readonly<Record<string, string>>,
    } as const,
  },
} as const;

/** Error code → what the customer reads. Says what to do, not what broke. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  DINING_TABLE_NOT_FOUND: 'Este QR não vale mais. Chame o garçom.',
  TABLE_ORDER_REQUEST_ALREADY_PENDING:
    'Você já tem um pedido aguardando o garçom. Espere ele confirmar e envie de novo.',
  TABLE_ORDER_REQUEST_LIMIT_REACHED: 'A mesa já tem muitos pedidos esperando o garçom; aguarde um instante e envie de novo.',
  TAB_CLOSING_REFUSES_ORDER_REQUEST: 'A conta da mesa está sendo fechada: não dá para pedir mais nada por aqui.',
  MENU_ITEM_UNAVAILABLE: 'Um dos itens acabou de esgotar. Tire-o do pedido e envie de novo.',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Um dos itens não é servido neste horário. Tire-o do pedido e envie de novo.',
  MENU_ITEM_NOT_FOUND: 'Um dos itens saiu do cardápio. Tire-o do pedido e envie de novo.',
  INVALID_TABLE_ORDER_REQUEST: 'O pedido não pôde ser aceito. Confira os itens: são até 20 diferentes por pedido.',
  INVALID_CUSTOMER_NAME: 'Seu nome precisa ter de 1 a 40 letras. Corrija e envie de novo.',
  INVALID_CUSTOMER_DEVICE_ID: 'Não deu para identificar este celular. Recarregue a página e envie de novo.',
  TABLE_ORDER_REQUEST_NOT_FOUND: 'Não achamos o seu último pedido.',
  TAB_NOT_FOUND: 'A mesa ainda não tem itens confirmados.',
  TAB_ITEM_ALREADY_PAID: 'Alguém acabou de pagar um desses itens. A conta foi atualizada; marque de novo.',
  TAB_ITEM_RESERVED_FOR_PAYMENT: 'Um desses itens já está num Pix de outra pessoa. A conta foi atualizada; marque de novo.',
  PIX_RECEIVER_NOT_CONFIGURED: 'O restaurante ainda não ativou o Pix pela mesa. Peça a conta ao garçom.',
  INVALID_TABLE_PAYMENT_REQUEST: 'A seleção mudou enquanto você marcava. A conta foi atualizada; marque de novo.',
  TAB_NOT_OPEN: 'A conta desta mesa já foi fechada. Fale com o garçom.',
  TABLE_PAYMENT_REQUEST_EXPIRED: 'Este Pix expirou. Gere outro na conta da mesa.',
  TABLE_PAYMENT_REQUEST_NOT_FOUND: 'Não achamos este Pix. Volte à conta da mesa.',
  VALIDATION_FAILED: 'Faltou uma informação no pedido de pagamento. Tente de novo.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo; se continuar, chame o garçom.';
const OFFLINE = 'Sem conexão. Veja a internet do celular e tente de novo.';

/**
 * A code without translation never reaches the customer raw: it shows the
 * generic line and shouts in the console, so the gap shows up in development.
 */
export function describeCustomerError(error: unknown): string {
  if (error instanceof NetworkError) {
    return OFFLINE;
  }
  if (error instanceof ApiError && error.code) {
    const known = ERROR_MESSAGES[error.code];
    if (known) {
      return known;
    }
    console.error(`[customer-table] error code without translation: ${error.code}`);
  }
  return GENERIC_FAILURE;
}

export function hasErrorCode(error: unknown, code: string): boolean {
  return error instanceof ApiError && error.code === code;
}
