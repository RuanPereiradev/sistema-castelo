# Decisões — Task 2.2 comanda: abertura, lançamento e cancelamento

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

O centro dos pedidos. Roda em paralelo com a 1.3 e a 1.4. **Entra na `main`
depois da 1.3**: a V6 precisa preceder a V7 no Flyway.

Itens herdados que esta task resolve: 1.5 #2 (cartão livre, uma comanda por
cartão), limitação da 1.5 (desativar mesa com comanda aberta), 1.2 #2
(`requiresVariant`) e #8 (adicional segue o item na taxa).

---

## Estado

| | |
|---|---|
| Branch | `task/2.2-tab` |
| Rodada atual | 1 — review mecânico aplicado; aguarda revisão do Ruan e execução do `.http`. Entra na `main` **depois da 1.3** |
| Build | `./mvnw clean verify` **verde** — 1262 testes, 0 falhas |
| Testes | ~140 de unidade (restaurant) + integração com 3 cenários de concorrência. Orçamento: 1.989 linhas de teste para 2.407 de produção (0,83:1) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **Uma** comanda ativa (`OPEN`/`CLOSING`) por mesa e por cartão. Grupos na mesma mesa se resolvem com divisão e transferência (3.2, 3.6) | implementado |
| 2 | 0 | Cartão do self-service de **1 a 999**, fixo no código e no `CHECK` | implementado |
| 3 | 0 | `WAITER` e `ADMIN` operam a comanda; `FRONT_DESK` e `KITCHEN` não têm acesso. Quem pesa o prato no self-service usa perfil `WAITER` | implementado |
| 4 | 0 | Abertura do self-service é **explícita** (`POST /tabs` com o cartão) e depois o lançamento; o front encadeia | implementado |
| 5 | 0 | Item esgotado, inativo ou fora do horário é **recusado** com 422, com código distinto para "fora do horário" | implementado |
| 6 | 0 | Cancelar item exige **motivo em texto livre**; vale em **qualquer status** menos `CANCELLED`, inclusive entregue | implementado |
| 7 | 0 | Peso: centavo arredondado **meio para cima** (437 g × 59,90 = 26,18); peso de 1 g a 50 kg; quantidade por lançamento de 1 a 999 | implementado |
| 8 | 0 | Desativar mesa com comanda ativa é **recusado** (`DINING_TABLE_HAS_OPEN_TAB`) | implementado |
| 9 | 0 | Item por peso nasce **`DELIVERED`** (o cliente já se serviu, não vai para a tela da cozinha); bebida lançada no cartão nasce `PENDING` e vai para o bar | implementado |
| 10 | 0 | **Cancelar comanda** entra na 2.2: só `OPEN` sem item ativo, com autor, horário e motivo; status `CANCELLED` | implementado |
| 11 | 0 | Nomes novos no glossário: `TabItemOrder` (o pedido de um item) e `ModifierChoice` (adicional escolhido com quantidade) | implementado |
| 12 | 0 | V7 sem `folio_id`, fechamento, destino, taxa de serviço, `guest_count` e `split_group`: vão para a `V11__tab_closing.sql` da 3.2 (reservada em `docs/MIGRATIONS.md`). Colunas do KDS (3.5) e de transferência/junção (3.6) entram já, sem mapeamento. `tab_item` ganha `variant_name` e auditoria; `ck_tab_item_pricing` mais restrito; sem `ON DELETE CASCADE` em `tab_item.tab_id` | implementado (DEV, aguarda Ruan) |
| 13 | 0 | Cancelamento como `POST .../cancel` com `{reason}`, não `DELETE`: nada é apagado. Diverge da seção 8 do plano | implementado (DEV, aguarda Ruan) |
| 14 | 0 | Uma comanda ativa por mesa/cartão garantida **só pelo índice único parcial**, com `saveAndFlush` e tradução do índice em 409 — sem consulta prévia, porque aqui a corrida é real e um caminho só é o caminho testado | implementado (DEV, aguarda Ruan) |
| 15 | 0 | Lançar e cancelar carregam a comanda com `FOR KEY SHARE`: não conflita entre garçons e bloqueia contra o `FOR UPDATE` do fechamento (3.2). Sem `@Version` | implementado (DEV, aguarda Ruan) |
| 16 | 0 | `TabId`, `TabItemId` em `restaurant.domain`; `publicToken` UUID v4, único, fora da API na 2.2; só `subtotal()` (taxa e `total()` na 3.2); um código `TAB_NOT_OPEN` para todo status que não é `OPEN`; sem eventos de domínio (nascem na 3.5) | implementado (DEV, aguarda Ruan) |
| 17 | 1 | Ambiguidades levantadas pelo TEST: `TabItemOrder` e `ModifierChoice` são records top-level do domínio; item **ativo** = tudo menos `CANCELLED` (item entregue impede cancelar a comanda — a venda não some); ordem no `cancelItem` = status → item existe → já cancelado → motivo, e no `cancel` = status → item ativo → motivo; grupo peso/unidade = por peso: variação → adicional → quantidade ≠ 1 (inclusive 0) → peso, por unidade: peso → quantidade (antes da variação obrigatória); `modifiers` nulo = vazio; limites de observação e motivo depois de aparar; variação de outro item = `MENU_ITEM_VARIANT_NOT_FOUND` vindo do agregado; `Tab` ganha `cancelledAt/By/Reason` e `TabItem` ganha `deliveredAt`; `items()` ordena por `orderedAt`, depois id; argumento nulo nas factories = `NullPointerException` | implementado (DEV, aguarda Ruan) |
| 18 | 1 | **Revê a #15 para o cancelamento da comanda**: `cancel` trava a comanda com `FOR UPDATE`, não `FOR KEY SHARE`. Com `FOR KEY SHARE` dos dois lados, cancelar a comanda e lançar um item ao mesmo tempo podiam comitar juntos e deixar comanda `CANCELLED` com item ativo. `FOR UPDATE` espera os lançamentos em curso e eles esperam por ele. Lançar e cancelar item seguem com `FOR KEY SHARE` | implementado |
| 19 | 1 | Adicional repetido é checado na **lista inteira primeiro**; depois, escolha por escolha: oferecido → ativo → quantidade. Corrige a leitura "escolha por escolha" da #17, que deixava um adicional repetido e não oferecido responder `MODIFIER_NOT_OFFERED` | implementado |
| 20 | 1 | Decisões do DEV: `TabOrigin.requireOpeningFields` recusa com `INVALID_TAB_OPENING` e o service escolhe a factory por `switch` na origem; `TabStatus.acceptsCancellation()` (só `OPEN`) para cancelar a comanda; `FOR KEY SHARE` por consulta nativa seguida do `findById`; tradução do índice lendo o nome da constraint da `ConstraintViolationException`; `delivered_at` é a única coluna do KDS já mapeada (#9); `diningTableLabel` preenchido no controller; `AddTabItemCommand.ChosenModifier` só transporta o pedido na camada de aplicação | implementado (DEV, aguarda Ruan) |
| 21 | 1 | Adicional sem `quantity` no corpo vira 0 e é recusado com `INVALID_TAB_ITEM_MODIFIER_QUANTITY` — o front sempre envia a quantidade. Adicional sem `modifierId` = 400. 404 de item do cardápio ou adicional inexistente responde antes de `TAB_NOT_OPEN`, porque o service carrega antes de chamar o agregado | implementado (DEV, aguarda Ruan) |
| 22 | 1 | Review mecânico: **cancelar item trava a linha do item `FOR UPDATE`** (depois do `FOR KEY SHARE` da comanda). Sem isso, dois cancelamentos simultâneos do mesmo item respondiam 200 e o segundo sobrescrevia autor e motivo (invariante 18). Provado com 5 cancelamentos simultâneos: um 200 e quatro `TAB_ITEM_ALREADY_CANCELLED`; sem a trava, os cinco davam 200. Não cria conflito entre garçons lançando itens novos. Também: adicionais do item ordenados por nome; `.http` lista as comandas sem filtro; cartão sorteado no teste de concorrência | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `V7__tab.sql` e seção 11 do schema doc atualizada
- [x] Agregado `Tab` com `TabItem` e `TabItemModifier`, enums com comportamento
- [x] Abrir em mesa e cartão, uma ativa por vez (#1, #2)
- [x] Lançar por unidade (variação, adicionais, observação) e por peso (#7, #9)
- [x] Cancelar item (#6) e cancelar comanda (#10)
- [x] Ler e listar comandas ativas
- [x] `DiningTableService` recusa desativar mesa com comanda ativa (#8)
- [x] `MenuItem.isWithinAvailabilityWindowAt`
- [x] `http/33-restaurant-tabs.http`
- [x] Testes de unidade densos e integração com os três cenários de concorrência

### Fora do escopo

- Fechamento, taxa de serviço, destino, divisão, ligação ao folio (3.2); KDS (3.5); transferência e junção (3.6)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-2.2-tab.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Item do cardápio, adicional e mesa não são conferidos contra a propriedade da comanda | Propriedade única; padrão do módulo | Multipropriedade |
| Corrida entre desativar mesa e abrir comanda nela não é tratada | Ação de `ADMIN`, rara | Lock na mesa se aparecer |
| `?cardNumber=abc` na listagem responde 500: o handler global não mapeia `MethodArgumentTypeMismatchException` | Lacuna já existente em outras rotas, fora do `restaurant` | Task 5.3 (hardening), junto com o 409 de corrida de nome |
| Cancelar comanda × lançar item ao mesmo tempo (#18) não tem teste de concorrência | A garantia é o `FOR UPDATE` do Postgres contra `FOR KEY SHARE`, comportamento documentado do banco | Teste de concorrência na 3.2, junto com o fechamento, que usa o mesmo lock |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
