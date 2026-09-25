# Decisões — Task 1.2 cardápio completo (variações e adicionais)

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Primeira task da trilha do restaurante (decisão de 2026-09-24: restaurante
primeiro, hotel depois). Fecha o cardápio que a 0.8 deixou pela metade: as
tabelas `menu_item_variant`, `modifier` e `menu_item_modifier` já existem desde
a `V3__menu.sql` (decisão #4 da 0.8) e ganham agregado aqui.

Vale o teto de três rodadas (decisão #74 da 0.4) e o orçamento de teste do
`CLAUDE.md`: denso no preço (variação × adicional) e nas invariantes, enxuto no
encanamento.

---

## Estado

| | |
|---|---|
| Branch | `task/1.2-menu-variants-modifiers` |
| Rodada atual | 1 — fechada: review mecânico aplicado, decisões do Ruan registradas (#25–#29); falta executar o `.http` no IntelliJ antes do merge |
| Build | `./mvnw clean install` **verde** — 1092 testes, 0 falhas |
| Testes | 93 de unidade + 1 de integração da fatia. Orçamento: ~1.260 linhas de teste para 1.490 de produção (0,83:1) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Preço da variação **substitui** o preço do item (P = 45,00, G = 70,00). Em item com variações, o `unit_price` do item fica como preço de referência da vitrine ("a partir de") — a V3 continua exigindo a coluna | implementado |
| 2 | 0 | Item com variações **exige** a escolha de uma no pedido. Sem variação, vale o preço do item como hoje. A regra é aplicada pela `Tab` na 2.2; o `MenuItem` expõe o que ela precisa para decidir | implementado |
| 3 | 0 | Item vendido por peso **não** aceita variação nem adicional. O agregado recusa | implementado |
| 4 | 0 | Variação pode ser **esgotada sozinha** (acabou a G, a M continua), independente de `is_active`. Pede migration nova com `is_available` em `menu_item_variant`: `V4__menu_variants_and_name_uniqueness.sql` (#5) | implementado |
| 5 | 0 | **Renumeração das migrations reservadas** para seguir a ordem de execução: V4 = 1.2, V5 = 1.5 `dining_table`, V6 = 1.3 billing, V7 = 2.2 tab, V8 = 2.4 caixa, V9 = 1.1 inventário do hotel, V10 = 2.1 reservas. Com o hotel adiado, a numeração antiga faria o Flyway recusar V5/V7 depois de V6/V8 aplicadas. Nenhum arquivo V4+ existia; muda só `docs/MIGRATIONS.md` e a seção 3 de `docs/schema-banco-de-dados.md` | implementado |
| 6 | 0 | `Modifier` é **catálogo da propriedade**, cadastrado uma vez e vinculado a vários itens com `max_quantity` por item (desenho da V3). Preço **zero é permitido**: serve para escolha sem custo ("ponto da carne") | implementado |
| 7 | 0 | O adicional custa **o mesmo em qualquer variação**: o preço mora no `Modifier` | implementado |
| 8 | 0 | O adicional **segue o item na taxa de serviço**: item elegível leva o adicional junto, item inelegível também o exclui | implementado |
| 9 | 0 | O vínculo item ↔ adicional (tabela `menu_item_modifier`) chama-se **`MenuItemModifier`**, com `maxQuantity`. Entra no glossário do `CLAUDE.md` e da seção 5.3 do plano | implementado |
| 10 | 0 | `MenuItemVariant` e `MenuItemModifier` são **parte do agregado `MenuItem`**; `Modifier` é agregado próprio e o item guarda só o seu id. Variação **não é removida**, só desativada: a `tab_item` da 2.2 referencia a variação | implementado |
| 11 | 0 | `requiresVariant()` conta só variações **ativas**: desativar todas devolve o item ao preço próprio. Item com variações ativas e nenhuma disponível fica **indisponível** | implementado |
| 12 | 0 | **Refina a #1.** O "a partir de" da vitrine é **calculado** (`startingPrice()`: menor preço entre as variações ativas), não o `unit_price` guardado no item. Um preço de vitrine digitado à mão divergiria das variações na primeira troca de preço; o `CLAUDE.md` manda calcular valor derivado. O `unit_price` segue na coluna (a V3 exige) e volta a valer se as variações forem desativadas | implementado |
| 13 | 0 | `MenuItemVariantId` e `ModifierId` moram em `restaurant.domain`, não em `api/`: nenhum outro módulo os usa e o `api/` foi congelado na 0.6 | implementado |
| 14 | 0 | `maxQuantity` vai de 1 a 99. Oferecer de novo um adicional já oferecido atualiza o limite em vez de duplicar. Desativar um `Modifier` o tira de todos os itens sem desfazer os vínculos | implementado |
| 15 | 0 | Unicidade do nome da variação é checada **no agregado** (é regra dentro do item, ignorando maiúsculas); a do `Modifier`, no caso de uso, como a #8 da 0.8. O banco garante as duas | implementado |
| 16 | 1 | Ambiguidades levantadas pelo TEST, resolvidas pelo padrão da 0.8: nome de variação e de adicional é **aparado**, e o limite de 50/100 vale depois de aparar · renomear a variação para o próprio nome com outra caixa é aceito · nome de variação **desativada continua ocupado** (`uk_variant_name`; a variação nunca é apagada) · id de variação alheio ao item responde `MENU_ITEM_VARIANT_NOT_FOUND` em toda operação · `displayOrder` da variação segue a ordem de inserção · mudança de estado repetida (esgotar o esgotado, desativar o inativo) é **aceita em silêncio**, como o `markUnavailable()` da 0.8 · adicional inativo não pode ser oferecido de novo nem ter o limite atualizado (`INACTIVE_MODIFIER`) · argumento nulo é `NullPointerException`, não código de negócio | implementado |
| 17 | 1 | `variants` (`@OneToMany`) e `modifiers` (`@ElementCollection` sobre `menu_item_modifier`, sem auditoria) são EAGER com `@Fetch(FetchMode.SUBSELECT)`: três listas no mesmo JOIN é o que o Hibernate recusa (`MultipleBagFetchException`). **Primeira anotação `org.hibernate` fora do shared-kernel** — a regra do ArchUnit só a proíbe lá | implementado |
| 18 | 1 | `MenuItemVariant` e `Modifier` estendem `AuditedEntity` (as tabelas têm as colunas); `MenuItemModifier` não, porque a tabela não tem | implementado |
| 19 | 1 | ~~Na administração, o `price` do `MenuItemResponse` segue sendo o preço **próprio** do item, que é o que se edita; o "a partir de" (#12) só aparece no `/public/menu`. O item lista os adicionais como `{modifierId, maxQuantity}`; nome e preço vêm de `GET /api/restaurant/modifiers` ~~ | **parte revertida pela #26** |
| 20 | 1 | PATCH `{name?, price?}`: campo nulo não muda. Renomear um adicional para o próprio nome não dá 409 (o próprio é excluído da checagem) — diferente do `MenuCategoryService.rename` da 0.8 | implementado |
| 21 | 1 | Corpo de variação e de adicional sem bean validation: nome em branco e preço ausente viram 422 com o código de domínio. `maxQuantity` ausente é 400 `VALIDATION_FAILED`; fora de 1–99 é 422 | implementado |
| 22 | 1 | Ordem das checagens: `addVariant` = peso → nome → preço → unicidade; `offerModifier` = peso → adicional inativo → `maxQuantity`. `DELETE` do vínculo responde 200 com o `MenuItemResponse` | implementado |
| 23 | 1 | Três services novos (`MenuItemVariantService`, `MenuItemModifierService`, `ModifierService`) em vez de inchar o `MenuItemService`; catálogo de adicionais no controller irmão `ModifierAdministrationController` | implementado |
| 24 | 1 | Review mecânico: adicionais do `/public/menu` saem **ordenados por nome** (antes a ordem vinha do banco e mudava a cada re-oferecimento). Entraram três testes de unidade da #16 (nome aparado, renomear para a própria caixa, limite de adicional desativado) e o re-oferecimento no teste de integração | implementado |
| 25 | 1 | Resposta ao ponto I: o "a partir de" **continua contando variação esgotada** (ativa). Com a P esgotada, a vitrine segue mostrando o preço da P | implementado |
| 26 | 1 | **Revê a #19**: na administração, cada adicional do item volta com **nome e preço junto** — `{modifierId, name, price, maxQuantity, isActive}` —, sem segunda chamada. O `price` do item na administração continua sendo o preço próprio | implementado |
| 27 | 1 | Resposta ao ponto H: nome no cardápio é **único ignorando maiúsculas** também no banco ("Grande" = "GRANDE"). O nome é **guardado como digitado** — é o que o cardápio público mostra — e só a comparação ignora a caixa: índice único sobre `lower(name)` em `menu_category`, `menu_item`, `menu_item_variant` e `modifier`, com o nome das constraints da V3. Mesmo padrão do `uk_app_user_username` da 0.4. Entra na V4 desta task, que ainda não foi aplicada fora de branch; o arquivo passa a se chamar `V4__menu_variants_and_name_uniqueness.sql` | implementado |
| 28 | 1 | Ruan aceita as limitações: adicional sem conferência de propriedade e `MenuItem` sem `@Version`. A resposta 500 numa corrida de nome duplicado **vai para a 5.3** (hardening), porque o tratamento vale para o sistema inteiro | implementado |
| 29 | 1 | Ruan aprova as decisões técnicas #10 a #24 como estão (a #19 revista pela #26) | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `MenuItemVariant` (P/M/G) dentro do agregado `MenuItem`
- [x] `Modifier` como catálogo da propriedade, e o vínculo `MenuItemModifier` com `maxQuantity` (#6, #9)
- [x] Casos de uso e rotas `ADMIN` de cadastro de variação e adicional
- [x] Cardápio público (`/public/menu`) mostrando variações e adicionais
- [x] `V4__menu_variants_and_name_uniqueness.sql`: `is_available` em `menu_item_variant` (#4) e nome único ignorando maiúsculas (#27)
- [x] Renumeração das migrations reservadas (#5)
- [x] `http/31-restaurant-menu-variants-modifiers.http` com caminho feliz e cenários negativos (não executado ainda no IntelliJ)
- [x] Testes do agregado e um teste de integração da fatia

### Fora do escopo

- Lançar item na comanda com variação e adicional (`TabItem`, `tab_item_modifier`): task 2.2
- Taxa de serviço sobre o adicional (#8): regra aplicada pela `Tab` na 2.2/3.2
- Observação do pedido (`specialInstructions`): mora no `TabItem`, task 2.2
- Remover variação: não existe, só desativar (#10)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Nome duplicado numa corrida responde 500 em vez de 409 (`DataIntegrityViolationException` sem handler) | 5.3 | Com os índices da #27 o dado nunca fica errado; falta só a mensagem, e o tratamento vale para o sistema inteiro, não só para o cardápio | Ruan, rodada 1 (#28) |

---

## Contrato com o front

Especificado em `docs/task-1.2-menu-variants-modifiers.md`, seções 5 e 6.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `MENU_ITEM_VARIANT_NOT_FOUND` | 404 | Variação não existe no item |
| `MENU_ITEM_VARIANT_NAME_ALREADY_USED` | 409 | Outra variação do mesmo item já tem esse nome |
| `INVALID_MENU_ITEM_VARIANT_NAME` | 422 | Nome em branco ou acima de 50 caracteres |
| `SOLD_BY_WEIGHT_REJECTS_VARIANT` | 422 | Variação em item vendido por peso |
| `SOLD_BY_WEIGHT_REJECTS_MODIFIER` | 422 | Adicional em item vendido por peso |
| `MODIFIER_NOT_FOUND` | 404 | Adicional não existe, ou não está oferecido no item ao retirar |
| `MODIFIER_NAME_ALREADY_USED` | 409 | Outro adicional da propriedade já tem esse nome |
| `INVALID_MODIFIER_NAME` | 422 | Nome em branco ou acima de 100 caracteres |
| `INVALID_MODIFIER_PRICE` | 422 | Preço ausente ou negativo |
| `INVALID_MODIFIER_MAX_QUANTITY` | 422 | `maxQuantity` fora de 1 a 99 |
| `INACTIVE_MODIFIER` | 422 | Oferecer, ou atualizar o limite de, adicional desativado |

Preço de variação inválido reusa `INVALID_MENU_ITEM_PRICING` da 0.8. `maxQuantity`
ausente no corpo é 400 `VALIDATION_FAILED` (#21).

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `price` do item no `/public/menu` | "A partir de" calculado: menor preço entre as variações ativas (#12) |
| `price` do item na administração | Preço próprio do item, o que se edita (#19) |
| `variants` (público) | Só as ativas, por `displayOrder`: `{id, name, price, availableNow}` |
| `modifiers` (público) | Só os adicionais ativos oferecidos: `{id, name, price, maxQuantity}`. Preço zero sai `"0.00"` |
| `modifiers` (administração) | `{modifierId, name, price, maxQuantity, isActive}` (#26) |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Oferecer adicional não confere se ele é da mesma propriedade do item | Instalação de propriedade única; o `findById` da 0.8 também não escopa | Quando existir multipropriedade |
| `MenuItem` sem `@Version`: dois admins editando os adicionais do mesmo item ao mesmo tempo podem perder um vínculo | Só o `ADMIN` edita o cardápio | Lock otimista se a administração tiver mais de um operador |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
