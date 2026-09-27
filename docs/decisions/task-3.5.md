# Decisões — Task 3.5 KDS: fila por setor, transições do item e tempo real

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Spec: `docs/task-3.5-kds.md`. Parte do lote 2 (2.4, 3.2, 3.5). Ordem de merge
aprovada (G2): preparação do `billing.api` → 2.4 → 3.2 → **3.5**. A V10 precisa
chegar à `main` depois da V9 da 3.2 (G1).

---

## Estado

| | |
|---|---|
| Branch | `task/3.5-kds` (a partir de `task/billing-api-payment`) |
| Rodada atual | 1 — implementado; aguarda testes de unidade (agente de teste) e revisão do Ruan. Entra na `main` depois da 3.2 (V9) |
| Build | `./mvnw -Dmaven.repo.local=$PWD/.m2repo clean install` **verde** — 1403 testes, 0 falhas, ArchUnit incluído |
| Testes | 6 de integração (fatia REST, concorrência, WebSocket real); unidade do domínio a cargo do agente de teste. Orçamento: ~550 linhas de teste de integração para ~1450 de produção (com javadoc e SQL) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **G1.** Migrations renumeradas: V8 = 2.4, V9 = 3.2, **V10 = 3.5** (`V10__kitchen_queue_ready.sql`), hotel em V11/V12. Com `outOfOrder` desligado, versão maior aplicada antes de uma menor faz o Flyway recusar a menor (Ruan, 2026-09-26) | implementado |
| 2 | 0 | **G3.** Seed de `Setting`: a migration semeia as chaves para as `property` existentes; o `DevUserSeeder` (identity/infra) semeia ao criar a propriedade. Aprovado tocar o `DevUserSeeder` (Ruan, 2026-09-26) | implementado |
| 3 | 0 | **G5.** Glossário: `KitchenTicket`, `KitchenQueue`, eventos `TabItemOrdered`, `TabItemStatusChanged`, `TabItemCancelled` (Ruan, 2026-09-26) | implementado |
| 4 | 0 | **K1.** Cozinha (`KITCHEN`/`ADMIN`) move `PENDING → IN_PREPARATION → READY`; garçom (`WAITER`/`ADMIN`) marca `DELIVERED`. Garçom não assina o tópico da cozinha; cozinha não acessa a comanda (Ruan, 2026-09-26) | implementado |
| 5 | 0 | **K2.** `PENDING → READY` direto é permitido; `preparation_started_at` fica nulo (Ruan, 2026-09-26) | implementado |
| 6 | 0 | **K3.** Desfazer um passo (`READY → IN_PREPARATION`, `IN_PREPARATION → PENDING`), apagando o instante; `DELIVERED` nunca volta (Ruan, 2026-09-26) | implementado |
| 7 | 0 | **K4.** Garçom pode marcar `DELIVERED` a partir de `PENDING`/`IN_PREPARATION`; **sem** "entregar todos os prontos da mesa" por ora (Ruan, 2026-09-26) | implementado |
| 8 | 0 | **K5.** `READY` fica na tela até `DELIVERED` → V10 recria `idx_kds_queue` com `READY` (+ `CHECK`s de instante a critério do DEV) (Ruan, 2026-09-26) | implementado |
| 9 | 0 | **K6.** Não gravar autor por transição (Ruan, 2026-09-26) | implementado |
| 10 | 0 | **K7.** Ticket com mesa/cartão, item, variação, quantidade, adicionais, observação, tempos; sem preço e sem garçom. Agrupamento por mesa fica no front (Ruan, 2026-09-26) | implementado |
| 11 | 0 | **K8.** Um tópico por setor; qualquer `KITCHEN` abre qualquer setor (Ruan, 2026-09-26) | implementado |
| 12 | 0 | **K9.** Cancelado aparece riscado com motivo (front); esgotar item continua só `ADMIN` (Ruan, 2026-09-26) | implementado |
| 13 | 0 | **K10.** Tópico `/topic/restaurant/ready-items` para `WAITER`/`ADMIN` (Ruan, 2026-09-26) | implementado |
| 14 | 0 | **K11.** Limites de atraso em `Setting` por setor (atenção/atrasado), devolvidos na fila: KITCHEN 15/25, PIZZA 20/30, BAR 5/10 min (Ruan, 2026-09-26) | implementado |
| 15 | 0 | **K12.** Item sem preparo vai para o `BAR`; barman marca pronto direto (Ruan, 2026-09-26) | implementado |
| 16 | 0 | **K13.** Reconexão recarrega a fila pelo REST; sem histórico (Ruan, 2026-09-26) | implementado |
| 17 | 0 | **K14.** Rotas por verbo (`/start`, `/ready`, `/undo`, `/deliver`), divergindo do `PATCH /api/kitchen/items/{itemId}/status` do plano §8 (Ruan, 2026-09-26) | implementado |
| 18 | 0 | O KDS avança item também em comanda `CLOSING`/`CLOSED` (casa com F12 da 3.2) (Ruan, 2026-09-26) | implementado |
| 19 | 0 | Aviso operacional: um usuário `KITCHEN` por tela (sessão única, 0.4 #3) (Ruan, 2026-09-26) | registrado |
| 20 | 0 | DEV: chaves dos limites no padrão `billing.cash-drawer.required` (módulo, recurso em kebab-case, atributo): `restaurant.kitchen-display.<setor>.warning-minutes` e `.late-minutes`, `INTEGER` em minutos; campos JSON `warningAfterMinutes`/`lateAfterMinutes` | implementado (DEV, aguarda Ruan) |
| 21 | 0 | DEV: **desfazer volta ao status de antes do último toque.** `READY` que pulou o preparo (K2) volta a `PENDING`, não a `IN_PREPARATION` — senão existiria `IN_PREPARATION` sem `preparation_started_at`, e o "toque errado" do barman não seria desfeito. Ver pontos em aberto | implementado (DEV, aguarda Ruan) |
| 22 | 0 | DEV: `CHECK`s da V10 só para `READY ⇒ ready_at` e `DELIVERED ⇒ delivered_at`; nenhum para `IN_PREPARATION`, para não cimentar a #21 antes da confirmação | implementado |
| 23 | 0 | DEV: `TabItemCancelled` carrega o motivo e **não** o status anterior (obtê-lo exigiria mudar `Tab.cancelItem` ou ler o item antes das checagens da 2.2 #17; nenhum consumidor usa). Desvio do rascunho | implementado (DEV, aguarda Ruan) |
| 24 | 0 | DEV: as transições do agregado **devolvem** o `TabItemStatusChanged`; o service só publica. `TabItemOrdered.of(tabId, item)` e `TabItemCancelled.of(tabId, item)` montam os outros dois | implementado |
| 25 | 0 | DEV: `KitchenDisplayBroadcaster` em `restaurant.web` (adaptador de saída, usa os DTOs da web), não em `infra` como no rascunho. `AFTER_COMMIT` + `REQUIRES_NEW` só leitura; toda falha é engolida e logada, porque exceção em `afterCommit` viraria 500 numa operação já comitada | implementado |
| 26 | 0 | DEV: tópico do garçom recebe `STATUS_CHANGED` quando `from` **ou** `to` é `READY` (a tela tira o aviso ao desfazer/entregar) e todo `CANCELLED` | implementado (DEV, aguarda Ruan) |
| 27 | 0 | DEV: handshake com query string é recusado (400), para o token nunca viajar na URL; `DISCONNECT` é livre no `AuthorizationManager` (sessão não autenticada também desconecta sem ruído) | implementado |
| 28 | 0 | DEV: `station` ausente ou desconhecido = 400 `VALIDATION_FAILED` por validação de parâmetro (`@NotNull @Pattern`), porque o handler global não mapeia `MissingServletRequestParameterException` nem erro de conversão (limitação da 2.2) | implementado |
| 29 | 0 | DEV: `KitchenQueueView` (padrão `XxxView` de leitura, como `ReceivedPaymentView`) para a fila com os limites e o `serverTime` | implementado |
| 30 | 0 | DEV: `http/35-kitchen-display.http` (número dado pelo orquestrador; o rascunho dizia 34) e linha no `http/README.md` | implementado |
| 31 | 1 | DEV: a resposta de cada transição da cozinha é o ticket relido pela mesma consulta da fila, na mesma transação; o `JpaKitchenQueue` dá `flush` antes de ler, como o Hibernate faz antes das próprias consultas, para o `updatedAt` já sair gravado | implementado |
| 32 | 1 | DEV: a entrega pelo garçom trava pelo método novo `findByIdForItemChange(tabId, itemId)`, que reaproveita a trava do cancelamento de item (comanda `FOR KEY SHARE` → item `FOR UPDATE`); `findByItemIdForItemChange` lê o `tab_id` sem lock e cai no mesmo caminho | implementado |
| 33 | 1 | DEV: o teste de WebSocket espera a assinatura ficar ativa mandando uma sonda pelo próprio broker (o broker simples não devolve `RECEIPT` para `SUBSCRIBE`); a sonda nunca chega à fila do teste | implementado |
| 34 | 1 | Orquestrador: todo Maven desta task roda com repositório local próprio (`-Dmaven.repo.local=$PWD/.m2repo`), porque o `~/.m2` é compartilhado com as worktrees da 2.4 e da 3.2. `.m2repo/` não é versionado | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `docs/task-3.5-kds.md` e este registro
- [x] §11 do schema doc atualizada antes da migration (nota na §11, §11.2 nova, linha V10 da §3)
- [x] `V10__kitchen_queue_ready.sql`: índice com `READY`, `CHECK`s, seed dos limites (K5, K11, G3)
- [x] `DevUserSeeder` semeia os limites ao criar a propriedade (G3) — conferido num banco zerado
- [x] `TabItemStatus` com `isOnKitchenQueue`, `acceptsPreparationStart`, `acceptsReady`, `acceptsDelivery`, `acceptsUndo`
- [x] `Tab`/`TabItem`: start, ready (com pulo, K2), deliver (K4), undo (K3); `preparation_started_at` e `ready_at` mapeados
- [x] Eventos `TabItemOrdered`, `TabItemStatusChanged`, `TabItemCancelled` publicados
- [x] `TabRepository.findByItemIdForItemChange` e `findByIdForItemChange` com a ordem de lock comanda → item
- [x] `KitchenQueue` (porta) + `JpaKitchenQueue`; `KitchenDisplayService`
- [x] `KitchenDisplayController` e `TabItemDeliveryController`; `TabItemResponse` com os três instantes
- [x] STOMP em `/ws/kitchen`: tópicos por setor e `ready-items`, broadcaster `AFTER_COMMIT`
- [x] `identity.api.AccessTokenAuthenticator` + implementação; interceptor do CONNECT; autorização manual por mensagem
- [x] `http/35-kitchen-display.http` e linha no `http/README.md` — 40 requisições, 0 falhas, duas execuções seguidas
- [x] Integração: fatia REST, concorrência cancelar × pronto, WebSocket real
- [ ] Testes de unidade do domínio (matriz da spec §3): agente de teste separado, a partir da spec
- [x] `./mvnw clean install` verde, ArchUnit incluído

### Fora do escopo

- Telas (4.3, 4.5); impressão (v2); "entregar todos" (K4); autor por transição (K6);
  evento de transferência/junção (3.6); broker externo; métricas (5.1)
- Testes de unidade do domínio: agente de teste separado, a partir da spec

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-3.5-kds.md`, seções 5 a 8.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `INVALID_TAB_ITEM_TRANSITION` | 409 | O status atual não aceita a transição |
| `TAB_ITEM_ALREADY_CANCELLED` | 409 | Transição em item cancelado |
| `TAB_ITEM_NOT_FOUND` | 404 | Item inexistente |
| `TAB_NOT_FOUND` | 404 | Entrega em comanda inexistente |
| `VALIDATION_FAILED` | 400 | `station` ausente ou desconhecido |
| `AUTHENTICATION_REQUIRED` · `TOKEN_EXPIRED` · `INVALID_TOKEN` · `SESSION_SUPERSEDED` · `USER_INACTIVE` | frame `ERROR` | CONNECT recusado |
| `ACCESS_DENIED` | frame `ERROR` | Assinatura ou `SEND` sem permissão |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| instantes do ticket e da fila | ISO-8601 UTC, `null` quando não se aplica |
| `warningAfterMinutes`, `lateAfterMinutes` | inteiro, minutos desde `orderedAt` |
| `type` da mensagem | `ORDERED` · `STATUS_CHANGED` · `CANCELLED` |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| A sessão STOMP continua depois de o access token expirar; usuário desativado segue vendo a fila até reconectar | A sessão só recebe; toda ação passa pelo REST, que revalida o token | Fechar sessões no logout/desativação, se incomodar |
| Broker simples em memória, uma instância | v1 tem uma instância | Broker externo (RabbitMQ) |
| Mensagem perdida (queda de rede, falha no envio) não é reenviada | A fila REST é a verdade; a tela recarrega na reconexão (K13) | — |
| Item de comanda fechada que ninguém avançou fica na fila | F12: o KDS segue mostrando; o front esconde depois de N minutos | Ação de limpeza, se aparecer |
| O teste "lançamento recusado não gera mensagem" não distingue `AFTER_COMMIT` de um ouvinte síncrono: o lançamento recusado falha antes de publicar | Não há, no fluxo real, lançamento que publique e depois desfaça; provar exigiria um gancho só de teste | — |
| Banco zerado no `dev`: o primeiro boot resolve a propriedade antes de o seed criá-la, e as rotas que escrevem respondem 500 até reiniciar | Comportamento anterior à 3.5 (`SinglePropertyId`, decisão #27 da 0.5b), visto ao rodar o `.http` num banco novo | Resolver a propriedade de forma preguiçosa, fora desta task |
| Transição da cozinha lê o `tab_id` do item sem lock; se a 3.6 transferir o item nesse intervalo, a transição responde `TAB_ITEM_NOT_FOUND` | A 3.6 ainda não existe; o operador repete o toque | Revisar na 3.6 |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Desfazer de um `READY` que pulou o preparo volta a `PENDING` (#21, implementado assim) ou a `IN_PREPARATION` sem instante de início? | 0 |
