import { ApiError, NetworkError } from '../../lib/problemDetail';

/**
 * Every Portuguese string of the kitchen display lives here, never in a component.
 * The second block translates the stable `code` the API answers on failure.
 */
export const KITCHEN = {
  stations: {
    KITCHEN: 'Cozinha',
    PIZZA: 'Pizza',
    BAR: 'Bar',
  },
  connected: 'Conectado',
  disconnected: 'Desconectado',
  reconnect: 'Reconectar',
  loading: 'Carregando a fila…',
  queueFailed: 'Não deu para carregar a fila.',
  retry: 'Tentar de novo',
  pending: 'Pendentes',
  inPreparation: 'Em preparo',
  ready: 'Prontos',
  noPending: 'Nenhum pedido pendente',
  noInPreparation: 'Nenhum em preparo',
  noReady: 'Nenhum pronto',
  card: 'Cartão',
  counter: 'Balcão',
  minutes: 'min',
  notes: 'Obs.',
  start: 'Iniciar',
  markReady: 'Pronto',
  undo: 'Desfazer',
  working: '…',
} as const;

const ERROR_MESSAGES: Record<string, string> = {
  INVALID_TAB_ITEM_TRANSITION: 'Este item já mudou de etapa em outra tela. A fila foi atualizada.',
  TAB_ITEM_ALREADY_CANCELLED: 'O garçom cancelou este item.',
  TAB_ITEM_NOT_FOUND: 'Este item não está mais na fila.',
  VALIDATION_FAILED: 'Setor desconhecido.',
  FORBIDDEN: 'Seu perfil não pode mexer na fila da cozinha.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo.';
const OFFLINE = 'Sem conexão com o servidor. Veja a rede e tente de novo.';

/** A code without translation shows the generic line and shouts in the console. */
export function describeKitchenError(error: unknown): string {
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
      console.error(`[kitchen] error code without translation: ${error.code}`);
    }
  }
  return GENERIC_FAILURE;
}
