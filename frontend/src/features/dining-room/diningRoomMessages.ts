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
    itemsTransferred: 'Itens transferidos',
    tabsMerged: 'Comandas juntadas',
    tableMoved: (label: string) => `Comanda movida para a mesa ${label}`,
  },
} as const;

/** Error code → what the waiter reads. Says what to do, not what broke. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  TAB_NOT_FOUND: 'Esta comanda não existe mais. A tela vai se atualizar.',
  TAB_NOT_OPEN: 'Esta comanda não está aberta: alguém pediu a conta ou fechou. Veja a mesa de novo.',
  TAB_NOT_CLOSING: 'Esta comanda não está em fechamento. Veja a mesa de novo.',
  TAB_ALREADY_OPEN_FOR_DINING_TABLE: 'Esta mesa já tem comanda aberta. Veja a mesa de novo.',
  TAB_HAS_NO_ACTIVE_ITEMS: 'A comanda está vazia. Lance um item antes de pedir a conta.',
  TAB_HAS_ACTIVE_ITEMS: 'A comanda ainda tem itens. Cancele ou transfira antes.',
  TAB_ITEM_NOT_FOUND: 'Este item não está mais na comanda.',
  TAB_ITEM_ALREADY_CANCELLED: 'Este item já foi cancelado.',
  INVALID_TAB_ITEM_TRANSITION: 'A cozinha já avançou este item. Não dá mais para mudar.',
  INVALID_TAB_TRANSFER: 'Escolha ao menos um item e uma comanda de destino diferente.',
  INVALID_TAB_MERGE: 'Essas comandas não podem ser juntadas. Veja se as duas estão abertas.',
  INVALID_TAB_OPENING: 'Escolha uma mesa para abrir a comanda.',
  INVALID_GUEST_COUNT: 'Informe de 1 a 99 pessoas.',
  INVALID_CANCELLATION_REASON: 'Escreva o motivo do cancelamento.',
  INVALID_REOPENING_REASON: 'Escreva o motivo para reabrir.',
  INVALID_SPECIAL_INSTRUCTIONS: 'A observação está longa demais.',
  INVALID_TAB_ITEM_MODIFIER_QUANTITY: 'Quantidade de adicional acima do permitido.',
  DUPLICATE_TAB_ITEM_MODIFIER: 'O mesmo adicional foi escolhido duas vezes.',
  DINING_TABLE_NOT_FOUND: 'Esta mesa não existe mais. A tela vai se atualizar.',
  DINING_TABLE_HAS_OPEN_TAB: 'A mesa de destino já tem comanda. Escolha uma mesa livre.',
  INACTIVE_DINING_TABLE: 'Esta mesa está desativada.',
  MENU_ITEM_NOT_FOUND: 'Este item saiu do cardápio.',
  MENU_ITEM_UNAVAILABLE: 'Este item está indisponível agora.',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Este item não é servido neste horário.',
  MENU_ITEM_VARIANT_REQUIRED: 'Escolha o tamanho.',
  MENU_ITEM_VARIANT_NOT_FOUND: 'Este tamanho saiu do cardápio.',
  MENU_ITEM_VARIANT_UNAVAILABLE: 'Este tamanho está indisponível agora.',
  MODIFIER_NOT_OFFERED: 'Este adicional não vale para este item.',
  MODIFIER_NOT_FOUND: 'Este adicional saiu do cardápio.',
  INACTIVE_MODIFIER: 'Este adicional está desativado.',
  SOLD_BY_WEIGHT_REQUIRES_WEIGHT: 'Informe o peso em gramas.',
  SOLD_BY_WEIGHT_REJECTS_QUANTITY: 'Item por peso não leva quantidade. Informe o peso.',
  SOLD_BY_WEIGHT_REJECTS_VARIANT: 'Item por peso não tem tamanho.',
  SOLD_BY_WEIGHT_REJECTS_MODIFIER: 'Item por peso não leva adicional.',
  SOLD_BY_UNIT_REJECTS_WEIGHT: 'Este item não é vendido por peso.',
  FORBIDDEN: 'Seu perfil não pode fazer isso.',
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
