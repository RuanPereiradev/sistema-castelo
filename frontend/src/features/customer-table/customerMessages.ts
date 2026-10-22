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
    pendingMine: 'Seu pedido anterior ainda aguarda o garçom. Assim que ele confirmar, você pode pedir de novo.',
    pendingOther:
      'Alguém da mesa enviou um pedido que ainda aguarda o garçom. Assim que ele confirmar, você pode enviar o seu.',
    fixName: 'Corrigir o nome',
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
    'Alguém da mesa já tem um pedido aguardando o garçom. Espere ele confirmar e envie de novo.',
  TAB_CLOSING_REFUSES_ORDER_REQUEST: 'A conta da mesa está sendo fechada: não dá para pedir mais nada por aqui.',
  MENU_ITEM_UNAVAILABLE: 'Um dos itens acabou de esgotar. Tire-o do pedido e envie de novo.',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Um dos itens não é servido neste horário. Tire-o do pedido e envie de novo.',
  MENU_ITEM_NOT_FOUND: 'Um dos itens saiu do cardápio. Tire-o do pedido e envie de novo.',
  INVALID_TABLE_ORDER_REQUEST: 'O pedido não pôde ser aceito. Confira os itens: são até 20 diferentes por pedido.',
  INVALID_CUSTOMER_NAME: 'Seu nome precisa ter de 1 a 40 letras. Corrija e envie de novo.',
  INVALID_CUSTOMER_DEVICE_ID: 'Não deu para identificar este celular. Recarregue a página e envie de novo.',
  TABLE_ORDER_REQUEST_NOT_FOUND: 'Não achamos o seu último pedido.',
  TAB_NOT_FOUND: 'A mesa ainda não tem itens confirmados.',
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
