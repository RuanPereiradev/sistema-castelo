/**
 * Every Portuguese string of the admin section lives here, never in a component.
 */
export const ADMIN = {
  title: 'Administração',
  menu: 'Cardápio',
  tables: 'Mesas',

  // Menu section
  loadingMenu: 'Carregando cardápio…',
  noMenuItems: 'Nenhum item no cardápio. Crie o primeiro clicando em "Novo item".',
  menuFailed: 'Não deu para carregar o cardápio.',
  newItem: 'Novo item',
  editItem: 'Editar',
  deleteItem: 'Deletar',
  createItemTitle: 'Novo item do cardápio',
  editItemTitle: 'Editar item',
  itemName: 'Nome',
  itemDescription: 'Descrição (opcional)',
  itemCategory: 'Categoria',
  itemPrice: 'Preço',
  itemPricePerKilo: 'Preço por quilo (g)',
  itemSoldByWeight: 'Vendido por peso',
  itemPrepStation: 'Setor de preparo',
  itemActive: 'Ativo',
  itemAvailable: 'Disponível',
  itemVariants: 'Variações',
  itemModifiers: 'Adicionais oferecidos',
  itemAvailabilityWindows: 'Janelas de horário',
  itemServiceChargeEligible: 'Elegível para taxa de serviço',

  variantName: 'Nome',
  variantPrice: 'Preço',
  addVariant: 'Adicionar variação',
  editVariant: 'Editar',
  deleteVariant: 'Deletar',

  modifierName: 'Nome do adicional',
  modifierMaxQuantity: 'Quantidade máxima',
  addModifier: 'Adicionar adicional',
  removeModifier: 'Remover',

  windowDay: 'Dia da semana',
  windowStart: 'Horário inicial',
  windowEnd: 'Horário final',
  addWindow: 'Adicionar janela',
  removeWindow: 'Remover',

  prepStationKitchen: 'Cozinha',
  prepStationPizza: 'Pizzaria',
  prepStationBar: 'Bar',

  save: 'Salvar',
  cancel: 'Cancelar',
  delete: 'Deletar',
  confirm: 'Confirmar',
  close: 'Fechar',

  deleteConfirmTitle: 'Deletar este item?',
  deleteConfirmMessage: 'Não há desfazer. Você tem certeza?',

  itemCreated: 'Item criado com sucesso',
  itemUpdated: 'Item atualizado com sucesso',
  itemDeleted: 'Item deletado',

  // Tables section
  loadingTables: 'Carregando mesas…',
  noTables: 'Nenhuma mesa cadastrada. Crie a primeira clicando em "Nova mesa".',
  tablesFailed: 'Não deu para carregar as mesas.',
  newTable: 'Nova mesa',
  editTable: 'Editar',
  deleteTable: 'Deletar',
  createTableTitle: 'Nova mesa',
  editTableTitle: 'Editar mesa',
  tableLabel: 'Rótulo (ex: "1", "A1", "Terraço 3")',
  tableSeats: 'Lugares (opcional)',
  tableArea: 'Área (opcional)',
  tableActive: 'Ativa',
  tableHasOpenTab: 'Esta mesa tem comanda aberta e não pode ser desativada.',

  tableCreated: 'Mesa criada com sucesso',
  tableUpdated: 'Mesa atualizada com sucesso',
  tableDeleted: 'Mesa deletada',

  // Categories
  loadingCategories: 'Carregando categorias…',
  noCategories: 'Nenhuma categoria. Crie uma para adicionar itens.',
  categoriesFailed: 'Não deu para carregar as categorias.',
  newCategory: 'Nova categoria',
  categoryName: 'Nome',
  categoryCreated: 'Categoria criada com sucesso',

  // Generic errors
  retry: 'Tentar de novo',
  loadingError: 'Erro ao carregar. Tente de novo.',
  saveError: 'Não deu para salvar. Tente de novo.',
  deleteError: 'Não deu para deletar. Tente de novo.',
  validationError: 'Verifique os campos e tente de novo.',

  // Validation messages
  fieldRequired: 'Este campo é obrigatório',
  invalidPrice: 'Preço inválido',
  invalidQuantity: 'Quantidade inválida',
  invalidTime: 'Horário inválido',
  priceFormat: 'Use formato decimal (ex: 10.50)',
} as const;

/** Error code → what the admin reads. */
const ERROR_MESSAGES: Readonly<Record<string, string>> = {
  MENU_ITEM_NOT_FOUND: 'Este item não existe mais. A tela vai se atualizar.',
  MENU_CATEGORY_NOT_FOUND: 'Esta categoria não existe mais. A tela vai se atualizar.',
  DINING_TABLE_NOT_FOUND: 'Esta mesa não existe mais. A tela vai se atualizar.',
  DINING_TABLE_HAS_OPEN_TAB: 'Esta mesa tem comanda aberta e não pode ser desativada.',
  INVALID_MENU_ITEM_NAME: 'Nome do item inválido.',
  INVALID_MENU_ITEM_PRICE: 'Preço do item inválido.',
  INVALID_MENU_ITEM_DESCRIPTION: 'Descrição do item muito longa.',
  INVALID_DINING_TABLE_LABEL: 'Rótulo da mesa inválido.',
  INVALID_DINING_TABLE_SEATS: 'Quantidade de lugares inválida.',
  FORBIDDEN: 'Seu perfil não pode fazer isso.',
};

const GENERIC_FAILURE = 'Não deu certo. Tente de novo; se continuar, avise o administrador.';

/**
 * A code without translation never reaches the admin raw: it shows the generic
 * line and logs to console, so the gap shows up in development.
 */
export function describeError(error: unknown): string {
  if (error instanceof Error) {
    if ('status' in error && error.status === 401) {
      return 'Sessão expirada. Faça login novamente.';
    }
    if ('code' in error) {
      const code = error.code as string;
      const known = ERROR_MESSAGES[code];
      if (known) {
        return known;
      }
      console.error(`[admin] error code without translation: ${code}`);
    }
  }
  return GENERIC_FAILURE;
}
