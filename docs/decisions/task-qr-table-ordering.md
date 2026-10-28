# Decisões — Task qr-table-ordering Pedido do cliente pelo QR da mesa

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/qr-table-ordering` (worktree `../castel-qr-ordering`, a partir da `main` em `7cd28a1`) |
| Rodada atual | 2 — achados da review (1–5; o 7 virou limitação) |
| Build | Rodada 2 (2026-10-21): `./mvnw clean install` passa, ArchUnit incluído |
| Testes | 1916 no total, 0 falhas. Os 3 casos `EXPIRED` de `ResolvedIsFinal` saíram da parametrização e viraram `shouldReportExpiredWhenTheJobAlreadyStoredTheExpiry` (#37) |
| `.http` | `http/37-restaurant-table-ordering.http`, 49 requisições, todas verdes contra um Postgres descartável (2026-10-21). Expiração preguiçosa, expiração no envio e a tarefa agendada conferidas à mão no mesmo banco |

Escopo novo: não está no plano técnico nem no `brief-front-end.md`. A v1.1 do plano previa um
QR na **comanda**, para o cliente ver a conta e pagar; este é um QR na **mesa**, para pedir.

---

## Origem

Levantamento de 2026-10-20 no código da `main`:

- `GET /public/menu` já existe sem login (task 1.2) e só o garçom consome
- `Tab.openForTable(propertyId, diningTable, openedBy, openedAt)` e `Tab.addItem(..., orderedBy, ...)`
  exigem um usuário: o cliente não tem um
- `DiningTable` não tem identificador público para o QR; o `publicToken` que existe é da `Tab`,
  e nasce com ela, então não serve para um QR impresso na mesa
- A cozinha já recebe todo item lançado por `/topic/kitchen/{station}` (task 3.5): o pedido do
  cliente chega lá sem trabalho novo, se passar pelo `addItem`
- O salão do front lê mesas e comandas por intervalo de 5s, sem tempo real
- O caixa só vê a comanda quando ela entra em `CLOSING` e lança o total no folio

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | O cliente faz o pedido pelo próprio celular, não só vê o cardápio (Ruan, 2026-10-20) | implementado |
| 2 | 0 | Um QR por mesa. O cliente senta, escaneia, e o cardápio abre já preso àquela mesa (Ruan, 2026-10-20) | implementado |
| 3 | 0 | A comanda da mesa abre no primeiro pedido do cliente, não no escaneamento (Ruan, 2026-10-20) | **revertida pela #8** |
| 4 | 0 | A mesa aberta pelo cliente aparece no mapa do salão como qualquer outra (Ruan, 2026-10-20) | implementado (backend: fila e tópico; o aviso no mapa é do front) |
| 5 | 0 | O pedido do cliente chega ao garçom, à recepção (caixa) e à cozinha (Ruan, 2026-10-20) | implementado |
| 6 | 0 | Todo pedido do cliente chega primeiro ao garçom; só depois que o garçom confirma ele vai para a cozinha e para o caixa (Ruan, 2026-10-20). Detalha a #5 e responde ao risco do pedido sem login: um trote nunca vira prato | implementado |
| 7 | 0 | DEV: quem confirma é o usuário do lançamento — o garçom que confirma vira `orderedBy` dos itens e, no primeiro pedido, `openedBy` da comanda. Consequência da #6: `Tab` e `TabItem` continuam exigindo um usuário | implementado |
| 8 | 0 | A comanda abre quando o garçom confirma o primeiro pedido, não quando o cliente pede. Até a confirmação o pedido fica numa fila de espera separada da comanda, e o mapa do salão mostra a mesa com aviso de "pedido aguardando". Total, pré-conta, folio e fila da cozinha só recebem item confirmado; recusar um trote não deixa nada aberto (Ruan, 2026-10-20). Substitui a #3 | implementado |
| 9 | 0 | O cliente pode pedir numa mesa com comanda já aberta, seja pelo garçom, seja por um pedido dele já confirmado; na confirmação os itens entram nessa comanda. Exceção: comanda em `CLOSING` (pré-conta emitida, total no folio) recusa o pedido no celular, avisando que a conta está sendo fechada (Ruan, 2026-10-20) | implementado |
| 10 | 0 | Aviso em tempo real no salão, pelo STOMP que o KDS já usa (tópico novo para `WAITER`), com som; o aviso no mapa fica até alguém confirmar ou recusar (Ruan aprovou a proposta P3, 2026-10-21) | implementado (backend: tópico e permissão de `WAITER`/`ADMIN`; o som é do front) |
| 11 | 0 | Qualquer garçom confirma ou recusa o pedido inteiro; recusa sem motivo obrigatório; não edita — se precisar mudar, recusa e lança ele mesmo (Ruan aprovou a proposta P4, 2026-10-21) | implementado |
| 12 | 0 | O celular mostra o estado do pedido: aguardando, confirmado, recusado. Não mostra a conta da mesa (fica com o QR da comanda da v1.1) (Ruan aprovou a proposta P5, 2026-10-21) | implementado |
| 13 | 0 | Na confirmação vale a disponibilidade daquele momento: item esgotado recusa o pedido inteiro com o código do item, e o garçom avisa o cliente (Ruan aprovou a proposta P6, 2026-10-21) | implementado |
| 14 | 0 | Pedido aguardando expira em 30 minutos, sem confirmação; o cliente vê "expirado" (Ruan aprovou a proposta P7, 2026-10-21) | implementado |
| 15 | 0 | No máximo 1 pedido aguardando por mesa (o cliente espera a resposta antes de pedir de novo); o admin pode gerar um QR novo para a mesa, invalidando o antigo (Ruan aprovou a proposta P8, 2026-10-21) | **revertida pela #62 da `qr-table-payment`** na parte "1 por mesa" (agora 1 por aparelho, teto 10 por mesa) |
| 16 | 0 | Na administração de mesas: botão para ver e imprimir o QR de cada mesa, e para gerar um novo (Ruan aprovou a proposta P9, 2026-10-21) | implementado (backend: `orderingToken` na resposta e rota de regenerar; botão e impressão são do front) |
| 17 | 0 | A recepção vê a lista das comandas abertas, com itens e total parcial, só para leitura, ao lado dos folios; sem aviso por pedido. Pede uma leitura de comanda liberada também para `FRONT_DESK` (Ruan: "o que for mais prático", 2026-10-21). Detalha a #5 | implementado (backend, pela #26; a tela é do front) |
| 18 | 0 | Glossário: `TableOrderRequest` (tabela `table_order_request`) para o pedido do cliente que espera o garçom; `TableOrderRequestStatus` `PENDING · CONFIRMED · REJECTED · EXPIRED`; `orderingToken` na `DiningTable` para o código do QR, distinto do `publicToken` da comanda (Ruan, 2026-10-21). Entrou no `CLAUDE.md` | implementado |
| 19 | 1 | DEV: o pedido guarda as linhas como o garçom as lançaria (`menuItemId`, `variantId`, `quantity`, adicionais com quantidade, `specialInstructions`), em tabela filha `table_order_request_line`. Na confirmação cada linha vira um `Tab.addItem`, então toda regra do lançamento vale sem cópia | implementado |
| 20 | 1 | DEV: no envio só se checa o barato — item existe, ativo, disponível agora, não vendido por peso (o cliente não tem balança), até 20 linhas. A validação completa é a do `addItem`, na confirmação (#13) | implementado |
| 21 | 1 | DEV: confirmação que falha numa regra do lançamento (item esgotado, comanda em `CLOSING`) responde o código do domínio e o pedido **continua `PENDING`**; o garçom vê o motivo e recusa (#11, #13). Recusar automático apagaria o motivo antes de alguém ler | implementado |
| 22 | 1 | DEV: 1 pendente por mesa (#15) garantido por índice único parcial `WHERE status = 'PENDING'`; o envio expira antes o pendente vencido da mesa. A expiração de 30 min (#14) é preguiçosa na leitura e também por tarefa agendada a cada minuto, que avisa o salão | implementado |
| 23 | 1 | DEV: duas confirmações do mesmo pedido: o pedido é lido `FOR UPDATE`, a segunda recebe `TABLE_ORDER_REQUEST_NOT_PENDING` | implementado |
| 24 | 1 | DEV: código do QR desconhecido, regenerado ou de mesa desativada responde 404 `DINING_TABLE_NOT_FOUND`, sem dizer qual dos três | implementado |
| 25 | 1 | DEV: o salão recebe pelo tópico `/topic/restaurant/table-order-requests` (`WAITER`, `ADMIN`) só o aviso de que algo mudou, e recarrega a lista — mesmo desenho do KDS | implementado |
| 26 | 1 | DEV: a recepção (#17) lê pelas rotas que já existem, `GET /api/restaurant/tabs` e `/{tabId}`, abertas também para `FRONT_DESK`; o resto do recurso continua `ADMIN` e `WAITER` | implementado |
| 27 | 1 | DEV: migration `V13__table_order_request.sql`. O restaurante passa à frente do hotel de novo, que desce para V14/V15 — mesma regra das renumerações anteriores (Ruan aprovou, 2026-10-21) | implementado |
| 28 | 1 | DEV (implementação): a linha é entidade própria (`table_order_request_line`, com `position`) e os adicionais dela vão numa tabela neta `table_order_request_line_modifier` (`position`, `modifier_id`, `quantity`), no mesmo desenho de `tab_item` / `tab_item_modifier`. Sem JSONB: o JPA não aceita coleção dentro de embeddable, e mapear JSON no Hibernate 7 dependeria da versão do Jackson do Boot 4. A chave da neta é a posição, não o adicional: adicional repetido não é checado no envio (#20) e cai no `DUPLICATE_TAB_ITEM_MODIFIER` da confirmação | implementado |
| 29 | 1 | DEV (implementação): na confirmação e na recusa, a expiração preguiçosa **só lê**: o pedido vencido responde `TABLE_ORDER_REQUEST_EXPIRED` sem gravar nada, e a tarefa agendada grava `EXPIRED` e avisa o salão em até um minuto. Gravar e lançar a exceção na mesma transação pediria `noRollbackFor` e deixaria a expiração sem aviso no tópico. O envio, ao contrário, grava a expiração do vencido da mesa antes de inserir o novo, porque o índice único exige | implementado |
| 30 | 1 | DEV (implementação): `INVALID_TABLE_ORDER_REQUEST` responde **422**, não 400 como dizia o contrato. Toda exceção de domínio do projeto responde 422 (`INVALID_TAB_OPENING`, `INVALID_QUANTITY`…); 400 é só `VALIDATION_FAILED` e `MALFORMED_REQUEST` do Bean Validation. Mudança de contrato aprovada (Ruan, 2026-10-21) | implementado |
| 31 | 1 | DEV (implementação): as checagens baratas do envio (#20) respondem os códigos que já existem: `MENU_ITEM_NOT_FOUND` (404), `MENU_ITEM_UNAVAILABLE` e `MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW` (422). Vendido por peso responde `INVALID_TABLE_ORDER_REQUEST`. Quantidade, variação, adicionais e observação são guardados como vieram e só validados na confirmação; as colunas são largas (`INTEGER`, `TEXT`) para nenhum valor derrubar o `INSERT` com 500 | implementado |
| 32 | 1 | DEV (implementação): a confirmação acha a comanda ativa da mesa ou abre uma pelo mesmo `TabService.open`, e lança cada linha pelo mesmo `TabService.addItem` (mesma transação, mesmo lock `FOR KEY SHARE`, mesmo `TabItemOrdered`). Um garçom que abra a comanda da mesa à mão no mesmo instante faz a confirmação responder `TAB_ALREADY_OPEN_FOR_DINING_TABLE`; o pedido continua `PENDING` e a segunda tentativa entra na comanda aberta | implementado |
| 33 | 1 | DEV (implementação): `@EnableScheduling` no `app`, com um `taskScheduler` próprio (o do broker STOMP fica só com o heartbeat). Período em `castel.restaurant.table-order-request-expiry-period`, padrão `PT1M` | implementado |
| 34 | 1 | DEV (implementação): código de QR que nem é UUID (QR rasgado, digitado errado) responde o mesmo 404 `DINING_TABLE_NOT_FOUND` da #24, não `INVALID_ENTITY_ID` | implementado |
| 35 | 1 | DEV (implementação): evento de domínio `TableOrderRequestStatusChanged` (`requestId`, `diningTableId`, `status`, `occurredAt`), um só para as quatro mudanças, no molde de `TabItemStatusChanged`; `PENDING` vira `SUBMITTED` na mensagem do tópico. Também derivados da #19: `TableOrderRequestLine`, `TableOrderRequestLineModifier`. Os três nomes aprovados e no glossário (Ruan, 2026-10-21) | implementado |
| 36 | 1 | DEV (implementação): confirmar lê só o **id** da comanda ativa da mesa, sem carregar a comanda, para o `TabService.addItem` carregá-la já sob o `FOR KEY SHARE`; carregar antes deixaria no contexto uma cópia lida sem lock, que um fechamento concorrente tornaria velha. Pedido de `TabRepository.findActiveIdOnDiningTable` | implementado |
| 37 | 2 | DEV (review, rodada 2): pedido já gravado `EXPIRED` responde `TABLE_ORDER_REQUEST_EXPIRED` ao confirmar ou recusar, igual ao `PENDING` vencido — a resposta não depende de a tarefa já ter rodado. `requireAnswerableAt` checa expiração primeiro (`status.isExpired() \|\| isExpiredAt(at)`), depois `NOT_PENDING`. `expire()` de um pedido já resolvido, `EXPIRED` incluído, continua `NOT_PENDING`. Ajusta a #23 e a #29 | implementado |
| 38 | 2 | DEV (review, rodada 2): o `detail` de `TABLE_ORDER_REQUEST_ALREADY_PENDING` e de `TAB_CLOSING_REFUSES_ORDER_REQUEST` não leva mais o UUID da mesa: chega ao celular, sem login | implementado |
| 39 | 2 | DEV (review, rodada 2): o serviço trunca o relógio em microssegundos, a precisão do `timestamptz`, em toda comparação e gravação de instante (envio, leitura pública, fila, confirmar, recusar, tarefa); Java e Postgres concordam na borda dos 30 min | implementado |
| 40 | 2 | DEV (review, rodada 2): a leitura pública da mesa (`GET /public/tables/{orderingToken}`) lê só o status da comanda ativa (`TabRepository.findActiveStatusOnDiningTable`), sem carregar a comanda e os itens. O envio continua carregando a comanda, pela assinatura de `TableOrderRequest.submit` que os testes usam | implementado |
| 41 | 2 | DEV (review, rodada 2): a tarefa de expiração liga e desliga em `castel.restaurant.table-order-request-expiry-enabled` (padrão ligada); o perfil `test` a desliga, para nenhuma rodada cair no meio de um teste de integração | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Rodada 1 entregou o backend; nenhum item saiu da lista. "Front pendente" marca o que ainda falta na tela.

- [ ] QR por mesa que leva ao cardápio daquela mesa — backend feito (`orderingToken`, `GET /public/tables/{orderingToken}`); front pendente
- [ ] Cardápio público no celular, com pedido — backend feito (`POST .../order-requests`); front pendente
- [x] Primeiro pedido abre a comanda da mesa (na confirmação, #8)
- [ ] Pedido visível para garçom, caixa e cozinha — backend feito (fila, `TabItemOrdered` para o KDS, leitura de comanda para `FRONT_DESK`); front pendente
- [ ] Lista de comandas abertas na recepção, só leitura (#17) — backend feito (#26); front pendente
- [x] Fila de pedidos aguardando: confirmar, recusar, expirar em 30 min, 1 por mesa (#11, #13, #14, #15)
- [ ] Aviso em tempo real com som no salão (#10) — backend feito (tópico); som e aviso no mapa são do front
- [ ] Estado do pedido no celular (#12) — backend feito (`GET .../order-requests/{id}`); front pendente
- [ ] Ver, imprimir e regenerar o QR na administração de mesas (#15, #16) — backend feito (rota de regenerar); front pendente
- [x] `.http` com caminho feliz e cenários negativos (`http/37-restaurant-table-ordering.http`)
- [x] `./mvnw clean install` passando
- [x] Testes de unidade e de integração (agente de testes; `TableOrderRequestTest`, `DiningTableOrderingTokenTest`, `TableOrderRequestHttpIntegrationTest`)

---

## Contrato com o front

**Público, sem login** (`/public/**`)

| Rota | Resposta |
|---|---|
| `GET /public/tables/{orderingToken}` | `200` `{diningTableLabel, acceptsOrders, pendingRequestId}` — `acceptsOrders` falso com a comanda em `CLOSING` |
| `POST /public/tables/{orderingToken}/order-requests` | `201` `TableOrderRequestStatusResponse` |
| `GET /public/tables/{orderingToken}/order-requests/{requestId}` | `200` `TableOrderRequestStatusResponse` `{id, status, requestedAt, expiresAt}` |

Corpo do `POST`: `{lines: [{menuItemId, variantId?, quantity, modifiers: [{modifierId, quantity}], specialInstructions?}]}`

**Interno**

| Rota | Perfis | Resposta |
|---|---|---|
| `GET /api/restaurant/table-order-requests` | `ADMIN`, `WAITER` | pendentes, mais antigo primeiro, com nomes de item, variação e adicional e o rótulo da mesa: `[{id, status, diningTableId, diningTableLabel, requestedAt, expiresAt, resolvedAt, tabId, lines: [{menuItemId, menuItemName, variantId, variantName, quantity, modifiers: [{modifierId, modifierName, quantity}], specialInstructions}]}]` |
| `POST /api/restaurant/table-order-requests/{id}/confirm` | `ADMIN`, `WAITER` | `{request, tabId}` |
| `POST /api/restaurant/table-order-requests/{id}/reject` | `ADMIN`, `WAITER` | o pedido, `REJECTED` |
| `POST /api/restaurant/dining-tables/{id}/ordering-token` | `ADMIN` | a mesa com o código novo (`DiningTableResponse` ganhou `orderingToken` em todas as rotas de mesa) |
| `GET /api/restaurant/tabs`, `/{tabId}` | + `FRONT_DESK` | sem mudança de formato |

STOMP `/topic/restaurant/table-order-requests` (`WAITER`, `ADMIN`): `{type: SUBMITTED·CONFIRMED·REJECTED·EXPIRED, requestId, diningTableId, occurredAt}`.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `DINING_TABLE_NOT_FOUND` | 404 | QR desconhecido, regenerado, de mesa desativada ou que nem é UUID (#34) |
| `TABLE_ORDER_REQUEST_ALREADY_PENDING` | 409 | a mesa já tem um pedido aguardando |
| `TABLE_ORDER_REQUEST_NOT_PENDING` | 409 | confirmar ou recusar pedido já resolvido |
| `TABLE_ORDER_REQUEST_EXPIRED` | 409 | confirmar ou recusar depois dos 30 min |
| `TABLE_ORDER_REQUEST_NOT_FOUND` | 404 | id desconhecido, ou de outra mesa |
| `TAB_CLOSING_REFUSES_ORDER_REQUEST` | 409 | a comanda da mesa está em `CLOSING` |
| `INVALID_TABLE_ORDER_REQUEST` | **422** (era 400; #30) | sem linhas, mais de 20, ou item vendido por peso |
| `MENU_ITEM_NOT_FOUND` | 404 | item de uma linha não existe, no envio (#31) |
| `MENU_ITEM_UNAVAILABLE` · `MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW` | 422 | item esgotado ou fora do horário, no envio (#31) |
| códigos do `addItem` | os mesmos | confirmação recusada pelo lançamento (#21) |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Pedido vencido confirmado ou recusado antes da tarefa passar fica `PENDING` no banco por até um minuto, embora toda leitura o mostre `EXPIRED` (#29) | Gravar e lançar a exceção na mesma transação pediria `noRollbackFor`; a tarefa grava e avisa em seguida | Nenhuma necessária |
| O envio só checa o barato (#20, #31): quantidade fora da faixa, variação inexistente, adicional repetido ou não oferecido entram na fila e só são recusados na confirmação | Decisão #20 | Se virar trote frequente, levar as checagens do `addItem` para o envio |
| Sem limite de envio por QR além do "1 pendente por mesa" | Mesmo raciocínio do orçamento de teste: hotel, não banco | Rate limit em `/public/**` se aparecer abuso |
| `TableOrderRequest.submit` recebe o `MenuItemRepository` (porta) para ler o item de cada linha (achado 7 da review, rodada 2, deixado de fora pelo coordenador) | Precedente no projeto: `Tab` recebe a porta `TabBilling`. Carregar todos os itens no serviço antes do factory duplicaria o laço e leria itens de um pedido que a contagem de linhas ainda vai recusar | Se o agregado precisar de mais portas, trocar por um `Map<MenuItemId, MenuItem>` montado pelo serviço depois da checagem de contagem |
| O `.http` não prova a expiração de 30 min nem a mensagem STOMP | Um não cabe numa execução do arquivo; o outro não é HTTP | Teste de integração com relógio controlado e cliente STOMP (agente de testes) |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|

