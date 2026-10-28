/**
 * Every Portuguese string of the self-service screen lives here, never in a component.
 */
export const SELF_SERVICE = {
  brand: 'Hospedaria',
  title: 'Balança',
  cardNumber: 'Número do cartão',
  cardHint: 'De 1 a 999',
  cardInvalid: 'Número de 1 a 999',
  noTabFound: 'Comanda não encontrada',
  weight: 'Peso em gramas',
  weightHint: 'O que a balança marcou',
  weightInvalid: 'Informe o peso em gramas',
  selectItem: 'Escolha o item:',
  noItemsAvailable: 'Nenhum item por peso disponível agora',
  confirmTitle: 'Confirmar e lançar',
  cancel: 'Cancelar',
  add: 'Confirmar e lançar',
  adding: 'Lançando…',
  priceCalculation: (weight: string, pricePerKilo: string, total: string) =>
    `Peso: ${weight} × ${pricePerKilo}/kg = ${total}`,
  total: 'Total',
  itemAdded: (name: string) => `${name} lançado`,
  cardTotal: 'Total do cartão',
  noCards: 'Nenhum cartão em uso',
  newCard: 'Novo cartão',
  resetForm: 'Nova pesagem',
  loading: 'Carregando…',
} as const;

/** Error code → what the user reads. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  TAB_NOT_FOUND: 'Cartão não encontrado',
  INVALID_TAB_OPENING: 'Cartão inválido (1-999)',
  TAB_NOT_OPEN: 'Cartão não está aberto',
  MENU_ITEM_NOT_FOUND: 'Item não existe',
  MENU_ITEM_UNAVAILABLE: 'Item indisponível agora',
  MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW: 'Item não é servido neste horário',
  SOLD_BY_WEIGHT_REQUIRES_WEIGHT: 'Informe o peso em gramas',
  SOLD_BY_WEIGHT_REJECTS_QUANTITY: 'Item por peso não leva quantidade',
  SOLD_BY_WEIGHT_REJECTS_VARIANT: 'Item por peso não tem tamanho',
  SOLD_BY_WEIGHT_REJECTS_MODIFIER: 'Item por peso não leva adicional',
  FORBIDDEN: 'Seu perfil não pode fazer isso',
};

import { ApiError, NetworkError } from '../../lib/problemDetail';

const GENERIC_FAILURE = 'Não deu certo. Tente de novo.';
const OFFLINE = 'Sem conexão com o servidor. Veja a rede e tente de novo.';

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
      console.error(`[self-service] error code without translation: ${error.code}`);
    }
  }
  return GENERIC_FAILURE;
}
