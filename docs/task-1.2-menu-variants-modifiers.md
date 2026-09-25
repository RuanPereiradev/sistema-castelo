# Task 1.2 — Cardápio completo: variações e adicionais

**Objetivo:** o item do cardápio passa a ter variações (P/M/G) com preço próprio
e adicionais oferecidos a partir de um catálogo da propriedade.

Decisões de negócio: `docs/decisions/task-1.2.md` (#1 a #9). Gabarito de estilo:
o módulo `restaurant` como a 0.8 deixou (`MenuItem`, `AvailabilityWindow`,
`MenuItemService`, `MenuAdministrationController`, `PublicMenuController`).

---

## 1. Escopo

**Dentro**
- `MenuItemVariant` dentro do agregado `MenuItem`
- `Modifier` como agregado próprio (catálogo da propriedade)
- `MenuItemModifier`: o vínculo item ↔ adicional, dentro do agregado `MenuItem`
- Migration `V4__menu_variants_and_name_uniqueness.sql`
- Rotas `ADMIN` de cadastro, cardápio público estendido
- `http/31-restaurant-menu-variants-modifiers.http`

**Fora**
- Lançar item com variação e adicional na comanda (`TabItem`, `tab_item_modifier`): 2.2
- Taxa de serviço sobre o adicional (#8): regra aplicada pela `Tab` na 2.2/3.2
- Remover variação: não existe. `tab_item` referencia a variação; ela só é desativada

---

## 2. Modelo de dados

A V3 já criou `menu_item_variant`, `modifier` e `menu_item_modifier`. A única
mudança é a #4:

```sql
-- V4__menu_variants_and_name_uniqueness.sql
ALTER TABLE menu_item_variant
    ADD COLUMN is_available BOOLEAN NOT NULL DEFAULT TRUE;
```

A mesma migration troca os `UNIQUE` de nome das quatro tabelas do cardápio
(`menu_category`, `menu_item`, `menu_item_variant`, `modifier`) por índice único
sobre `lower(name)`: o nome é guardado como digitado e comparado ignorando
maiúsculas (decisão #27).

`menu_item_modifier` não tem colunas de auditoria (é tabela de vínculo, sem
histórico próprio) — mapeie como `@ElementCollection` de um `@Embeddable`, ou
como entidade sem auditoria. `modifier` e `menu_item_variant` têm.

---

## 3. Invariantes

### `MenuItemVariant` (parte do agregado `MenuItem`)

1. Nome obrigatório, sem espaço nas pontas, no máximo 50 caracteres
2. Nome único **dentro do item**, ignorando maiúsculas e minúsculas (`uk_variant_name` é a segunda linha)
3. Preço maior que zero
4. Item vendido por peso **não aceita variação** (#3)
5. O preço da variação **substitui** o preço do item (#1)
6. Variação nasce ativa e disponível. Esgotar (`is_available`) e desativar (`is_active`) são independentes (#4)

### `MenuItem` com variações

7. `requiresVariant()` é verdadeiro quando o item tem ao menos uma variação **ativa** (#2). Desativar todas devolve o item ao preço próprio
8. Item com variações ativas, mas nenhuma disponível, **não está disponível**: `isAvailableAt` responde falso
9. `isVariantAvailableAt(variantId, moment, zone)`: o item está disponível naquele momento **e** a variação está ativa e disponível
10. `startingPrice()`: com variações ativas, o menor preço entre elas; sem, `price()`. É calculado, nunca armazenado

### `Modifier` (agregado próprio)

11. Pertence a uma propriedade. Nome obrigatório, sem espaço nas pontas, no máximo 100 caracteres
12. Nome único na propriedade: checado no caso de uso, garantido por `uk_modifier_name` (mesmo padrão da #8 da 0.8)
13. Preço **maior ou igual a zero** (#6). Negativo é recusado
14. Nasce ativo. Desativar tira o adicional de todos os itens sem desfazer os vínculos

### `MenuItemModifier` (parte do agregado `MenuItem`)

15. `offerModifier(modifier, maxQuantity)`: `maxQuantity` de 1 a 99
16. Item vendido por peso **não aceita adicional** (#3)
17. Adicional inativo não pode ser oferecido
18. Oferecer de novo um adicional já oferecido **atualiza** o `maxQuantity`, não duplica
19. `withdrawModifier(modifierId)` desfaz o vínculo. Retirar um que não está oferecido é recusado
20. O item guarda só o id do adicional, nunca a entidade: são agregados diferentes

---

## 4. Assinaturas públicas

Tipos de id novos (`MenuItemVariantId`, `ModifierId`) seguem o formato de
`MenuItemId`, mas moram em `restaurant.domain`: nenhum outro módulo os usa, e
o `api/` foi congelado na 0.6.

```java
// MenuItem — novos métodos
MenuItemVariant addVariant(String name, Money unitPrice);
void renameVariant(MenuItemVariantId variantId, String newName);
void changeVariantPriceTo(MenuItemVariantId variantId, Money newPrice);
void markVariantUnavailable(MenuItemVariantId variantId);
void markVariantAvailable(MenuItemVariantId variantId);
void deactivateVariant(MenuItemVariantId variantId);
void activateVariant(MenuItemVariantId variantId);
List<MenuItemVariant> variants();                 // unmodifiable, por displayOrder
MenuItemVariant variant(MenuItemVariantId id);    // MenuItemVariantNotFoundException
boolean requiresVariant();
boolean isVariantAvailableAt(MenuItemVariantId id, Instant moment, ZoneId propertyZone);
Money startingPrice();

void offerModifier(Modifier modifier, int maxQuantity);
void withdrawModifier(ModifierId modifierId);
List<MenuItemModifier> modifiers();               // unmodifiable

// MenuItemVariant
MenuItemVariantId id(); String name(); Money unitPrice(); int displayOrder();
boolean isActive(); boolean isAvailable();

// MenuItemModifier
ModifierId modifierId(); int maxQuantity();

// Modifier
static Modifier create(UUID propertyId, String name, Money price);
void rename(String newName);
void changePriceTo(Money newPrice);
void deactivate();
void activate();
ModifierId id(); UUID propertyId(); String name(); Money price(); boolean isActive();
```

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `MENU_ITEM_VARIANT_NOT_FOUND` | 404 | Variação não existe no item |
| `MENU_ITEM_VARIANT_NAME_ALREADY_USED` | 409 | Outra variação do mesmo item já tem esse nome |
| `INVALID_MENU_ITEM_VARIANT_NAME` | 422 | Nome em branco ou acima de 50 caracteres |
| `INVALID_MENU_ITEM_PRICING` | 422 | Preço de variação ausente, zero ou negativo (reusa o código da 0.8) |
| `SOLD_BY_WEIGHT_REJECTS_VARIANT` | 422 | Variação em item vendido por peso |
| `SOLD_BY_WEIGHT_REJECTS_MODIFIER` | 422 | Adicional em item vendido por peso |
| `MODIFIER_NOT_FOUND` | 404 | Adicional não existe, ou não está oferecido no item ao retirar |
| `MODIFIER_NAME_ALREADY_USED` | 409 | Outro adicional da propriedade já tem esse nome |
| `INVALID_MODIFIER_NAME` | 422 | Nome em branco ou acima de 100 caracteres |
| `INVALID_MODIFIER_PRICE` | 422 | Preço ausente ou negativo |
| `INVALID_MODIFIER_MAX_QUANTITY` | 422 | `maxQuantity` fora de 1 a 99 |
| `INACTIVE_MODIFIER` | 422 | Oferecer adicional desativado |

---

## 6. API

Todas `ADMIN`, no `MenuAdministrationController` ou num controller irmão, no
mesmo estilo (variável de caminho nomeada explicitamente). Toda rota de item
devolve o `MenuItemResponse`, agora com `variants` e `modifiers`.

```
POST   /api/restaurant/menu-items/{menuItemId}/variants                         {name, price}  201
PATCH  /api/restaurant/menu-items/{menuItemId}/variants/{variantId}             {name?, price?}
POST   /api/restaurant/menu-items/{menuItemId}/variants/{variantId}/unavailable
POST   /api/restaurant/menu-items/{menuItemId}/variants/{variantId}/available
POST   /api/restaurant/menu-items/{menuItemId}/variants/{variantId}/deactivate
POST   /api/restaurant/menu-items/{menuItemId}/variants/{variantId}/activate

PUT    /api/restaurant/menu-items/{menuItemId}/modifiers/{modifierId}           {maxQuantity}
DELETE /api/restaurant/menu-items/{menuItemId}/modifiers/{modifierId}

POST   /api/restaurant/modifiers              {name, price}  201
GET    /api/restaurant/modifiers
PATCH  /api/restaurant/modifiers/{modifierId} {name?, price?}
POST   /api/restaurant/modifiers/{modifierId}/deactivate
POST   /api/restaurant/modifiers/{modifierId}/activate
```

**Cardápio público** (`GET /public/menu`), cada item ganha:

- `price`: passa a ser o `startingPrice()` (#10 acima)
- `variants`: só as ativas, por `displayOrder` — `{id, name, price, availableNow}`
- `modifiers`: só os adicionais **ativos** oferecidos — `{id, name, price, maxQuantity}`

Preço sempre como string decimal (`"45.00"`). Adicional de preço zero sai `"0.00"`.

---

## 7. Testes

Orçamento do `CLAUDE.md`: alvo de uma linha de teste por linha de produção.

- **Unidade (agente TEST):** as invariantes 1 a 20. Denso em preço
  (`startingPrice`, substituição, zero no adicional) e em disponibilidade
  (invariantes 7 a 9). Enxuto em nome.
- **Integração (agente DEV):** um teste da fatia em `app/src/test/.../menu/`,
  no padrão do `MenuHttpIntegrationTest`: cria item, variação, adicional,
  oferece, esgota uma variação e lê o `/public/menu`.

---

## 8. `.http`

`http/31-restaurant-menu-variants-modifiers.http`, no padrão do
`30-restaurant-menu.http`. Caminho feliz (pizza com P/M/G, borda recheada, ponto
da carne a zero, esgotar a G, ler o cardápio público) e cenários negativos: um
por código de erro da seção 5, e uma rota sem token (401) e com perfil errado (403).
