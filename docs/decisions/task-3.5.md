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
| Rodada atual | 0 — spec e decisões registradas |
| Build | — |
| Testes | — |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **G1.** Migrations renumeradas: V8 = 2.4, V9 = 3.2, **V10 = 3.5** (`V10__kitchen_queue_ready.sql`), hotel em V11/V12. Com `outOfOrder` desligado, versão maior aplicada antes de uma menor faz o Flyway recusar a menor (Ruan, 2026-09-26) | pendente |
| 2 | 0 | **G3.** Seed de `Setting`: a migration semeia as chaves para as `property` existentes; o `DevUserSeeder` (identity/infra) semeia ao criar a propriedade. Aprovado tocar o `DevUserSeeder` (Ruan, 2026-09-26) | pendente |
| 3 | 0 | **G5.** Glossário: `KitchenTicket`, `KitchenQueue`, eventos `TabItemOrdered`, `TabItemStatusChanged`, `TabItemCancelled` (Ruan, 2026-09-26) | pendente |
| 4 | 0 | **K1.** Cozinha (`KITCHEN`/`ADMIN`) move `PENDING → IN_PREPARATION → READY`; garçom (`WAITER`/`ADMIN`) marca `DELIVERED`. Garçom não assina o tópico da cozinha; cozinha não acessa a comanda (Ruan, 2026-09-26) | pendente |
| 5 | 0 | **K2.** `PENDING → READY` direto é permitido; `preparation_started_at` fica nulo (Ruan, 2026-09-26) | pendente |
| 6 | 0 | **K3.** Desfazer um passo (`READY → IN_PREPARATION`, `IN_PREPARATION → PENDING`), apagando o instante; `DELIVERED` nunca volta (Ruan, 2026-09-26) | pendente |
| 7 | 0 | **K4.** Garçom pode marcar `DELIVERED` a partir de `PENDING`/`IN_PREPARATION`; **sem** "entregar todos os prontos da mesa" por ora (Ruan, 2026-09-26) | pendente |
| 8 | 0 | **K5.** `READY` fica na tela até `DELIVERED` → V10 recria `idx_kds_queue` com `READY` (+ `CHECK`s de instante a critério do DEV) (Ruan, 2026-09-26) | pendente |
| 9 | 0 | **K6.** Não gravar autor por transição (Ruan, 2026-09-26) | pendente |
| 10 | 0 | **K7.** Ticket com mesa/cartão, item, variação, quantidade, adicionais, observação, tempos; sem preço e sem garçom. Agrupamento por mesa fica no front (Ruan, 2026-09-26) | pendente |
| 11 | 0 | **K8.** Um tópico por setor; qualquer `KITCHEN` abre qualquer setor (Ruan, 2026-09-26) | pendente |
| 12 | 0 | **K9.** Cancelado aparece riscado com motivo (front); esgotar item continua só `ADMIN` (Ruan, 2026-09-26) | pendente |
| 13 | 0 | **K10.** Tópico `/topic/restaurant/ready-items` para `WAITER`/`ADMIN` (Ruan, 2026-09-26) | pendente |
| 14 | 0 | **K11.** Limites de atraso em `Setting` por setor (atenção/atrasado), devolvidos na fila: KITCHEN 15/25, PIZZA 20/30, BAR 5/10 min (Ruan, 2026-09-26) | pendente |
| 15 | 0 | **K12.** Item sem preparo vai para o `BAR`; barman marca pronto direto (Ruan, 2026-09-26) | pendente |
| 16 | 0 | **K13.** Reconexão recarrega a fila pelo REST; sem histórico (Ruan, 2026-09-26) | pendente |
| 17 | 0 | **K14.** Rotas por verbo (`/start`, `/ready`, `/undo`, `/deliver`), divergindo do `PATCH /api/kitchen/items/{itemId}/status` do plano §8 (Ruan, 2026-09-26) | pendente |
| 18 | 0 | O KDS avança item também em comanda `CLOSING`/`CLOSED` (casa com F12 da 3.2) (Ruan, 2026-09-26) | pendente |
| 19 | 0 | Aviso operacional: um usuário `KITCHEN` por tela (sessão única, 0.4 #3) (Ruan, 2026-09-26) | registrado |
| 20 | 0 | DEV: chaves dos limites no padrão `billing.cash-drawer.required` (módulo, recurso em kebab-case, atributo): `restaurant.kitchen-display.<setor>.warning-minutes` e `.late-minutes`, `INTEGER` em minutos; campos JSON `warningAfterMinutes`/`lateAfterMinutes` | pendente (DEV, aguarda Ruan) |
| 21 | 0 | DEV: **desfazer volta ao status de antes do último toque.** `READY` que pulou o preparo (K2) volta a `PENDING`, não a `IN_PREPARATION` — senão existiria `IN_PREPARATION` sem `preparation_started_at`, e o "toque errado" do barman não seria desfeito. Ver pontos em aberto | pendente (DEV, aguarda Ruan) |
| 22 | 0 | DEV: `CHECK`s da V10 só para `READY ⇒ ready_at` e `DELIVERED ⇒ delivered_at`; nenhum para `IN_PREPARATION`, para não cimentar a #21 antes da confirmação | pendente |
| 23 | 0 | DEV: `TabItemCancelled` carrega o motivo e **não** o status anterior (obtê-lo exigiria mudar `Tab.cancelItem` ou ler o item antes das checagens da 2.2 #17; nenhum consumidor usa). Desvio do rascunho | pendente (DEV, aguarda Ruan) |
| 24 | 0 | DEV: as transições do agregado **devolvem** o `TabItemStatusChanged`; o service só publica. `TabItemOrdered.of(tabId, item)` e `TabItemCancelled.of(tabId, item)` montam os outros dois | pendente |
| 25 | 0 | DEV: `KitchenDisplayBroadcaster` em `restaurant.web` (adaptador de saída, usa os DTOs da web), não em `infra` como no rascunho. `AFTER_COMMIT` + `REQUIRES_NEW` só leitura; toda falha é engolida e logada, porque exceção em `afterCommit` viraria 500 numa operação já comitada | pendente |
| 26 | 0 | DEV: tópico do garçom recebe `STATUS_CHANGED` quando `from` **ou** `to` é `READY` (a tela tira o aviso ao desfazer/entregar) e todo `CANCELLED` | pendente (DEV, aguarda Ruan) |
| 27 | 0 | DEV: handshake com query string é recusado (400), para o token nunca viajar na URL; `DISCONNECT` é livre no `AuthorizationManager` (sessão não autenticada também desconecta sem ruído) | pendente |
| 28 | 0 | DEV: `station` ausente ou desconhecido = 400 `VALIDATION_FAILED` por validação de parâmetro (`@NotNull @Pattern`), porque o handler global não mapeia `MissingServletRequestParameterException` nem erro de conversão (limitação da 2.2) | pendente |
| 29 | 0 | DEV: `KitchenQueueView` (padrão `XxxView` de leitura, como `ReceivedPaymentView`) para a fila com os limites e o `serverTime` | pendente |
| 30 | 0 | DEV: `http/35-kitchen-display.http` (número dado pelo orquestrador; o rascunho dizia 34) e linha no `http/README.md` | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] `docs/task-3.5-kds.md` e este registro
- [ ] §11 do schema doc atualizada antes da migration
- [ ] `V10__kitchen_queue_ready.sql`: índice com `READY`, `CHECK`s, seed dos limites (K5, K11, G3)
- [ ] `DevUserSeeder` semeia os limites ao criar a propriedade (G3)
- [ ] `TabItemStatus` com `isOnKitchenQueue`, `acceptsPreparationStart`, `acceptsReady`, `acceptsDelivery`, `acceptsUndo`
- [ ] `Tab`/`TabItem`: start, ready (com pulo, K2), deliver (K4), undo (K3); `preparation_started_at` e `ready_at` mapeados
- [ ] Eventos `TabItemOrdered`, `TabItemStatusChanged`, `TabItemCancelled` publicados
- [ ] `TabRepository.findByItemIdForItemChange` e `findByIdForItemChange` com a ordem de lock comanda → item
- [ ] `KitchenQueue` (porta) + `JpaKitchenQueue`; `KitchenDisplayService`
- [ ] `KitchenDisplayController` e `TabItemDeliveryController`; `TabItemResponse` com os três instantes
- [ ] STOMP em `/ws/kitchen`: tópicos por setor e `ready-items`, broadcaster `AFTER_COMMIT`
- [ ] `identity.api.AccessTokenAuthenticator` + implementação; interceptor do CONNECT; autorização manual por mensagem
- [ ] `http/35-kitchen-display.http` e linha no `http/README.md`
- [ ] Integração: fatia REST, concorrência cancelar × pronto, WebSocket real
- [ ] `./mvnw clean install` verde, ArchUnit incluído

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

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Desfazer de um `READY` que pulou o preparo volta a `PENDING` (#21, implementado assim) ou a `IN_PREPARATION` sem instante de início? | 0 |
