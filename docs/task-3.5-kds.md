# Task 3.5 — KDS: fila por setor, transições do item e tempo real

**Objetivo:** levar cada item lançado à tela do seu setor de preparo
(`PrepStation`), deixar a cozinha mover o item por
`PENDING → IN_PREPARATION → READY` (e desfazer um passo), deixar o garçom marcar
`DELIVERED`, e atualizar as telas em tempo real por WebSocket/STOMP no mesmo
processo.

Decisões: `docs/decisions/task-3.5.md` (G1–G5 e K1–K14 aprovadas pelo Ruan em
2026-09-26). Herdadas: 2.2 #6 (cancelar em qualquer status), #9 (item por peso
nasce `DELIVERED`), #15, #18 e #22 (locks), #16 (eventos nascem aqui); 0.4 #1 e
#3 (token de 15 min, sessão única); 3.2 F12 (fecha com item em preparo).

Risco: médio. Não há dinheiro. Há transição de estado, corrida entre cozinha e
garçom no mesmo item e a confiança do operador (item que some ou não aparece na
tela). Teste denso na máquina de estados e na corrida; enxuto no encanamento
STOMP.

---

## 1. Escopo

**Dentro**
- Transições do `TabItem` pelo agregado `Tab`: iniciar preparo, pronto (inclusive
  direto de `PENDING`, K2), entregue (inclusive de `PENDING`/`IN_PREPARATION`, K4)
  e desfazer um passo (K3)
- `TabItemStatus` com comportamento para cada transição e para "está na fila do KDS"
- Mapear `preparation_started_at` e `ready_at`
- Eventos de domínio `TabItemOrdered`, `TabItemStatusChanged` e `TabItemCancelled`,
  publicados pelos casos de uso e entregues às telas depois do commit
- Fila de leitura por setor, `GET /api/kitchen/queue?station=`, com os limites de
  atraso do setor (K11)
- `V10__kitchen_queue_ready.sql`: `idx_kds_queue` com `READY` (K5), `CHECK`s de
  instante e seed dos limites de atraso
- `DevUserSeeder` semeia os limites ao criar a propriedade (G3)
- Broker STOMP simples em `/ws/kitchen`: um tópico por setor (K8) e
  `/topic/restaurant/ready-items` para o garçom (K10), autenticado no CONNECT
- Porta `identity.api.AccessTokenAuthenticator` para autenticar um access token
  fora do filtro HTTP
- `http/35-kitchen-display.http` (REST) e sua linha no `http/README.md`
- Testes de integração no `app`: fatia REST, concorrência, WebSocket real

**Fora**
- Telas do KDS e do garçom: tasks 4.5 e 4.3
- Impressão na cozinha: backlog v2 do plano
- "Entregar todos os prontos da mesa": fora por ora (K4)
- Autor de cada transição: não é gravado (K6)
- Evento de transferência ou junção que troque a mesa exibida: task 3.6, que deve
  publicar um evento que o broadcaster consuma
- Broker externo e mais de uma instância: não há na v1
- Métricas de tempo de preparo: task 5.1
- `CLAUDE.md` e `docs/MIGRATIONS.md`: quem edita é o orquestrador

---

## 2. Modelo de dados

As colunas de instante já existem na V7 (`preparation_started_at`, `ready_at`,
`delivered_at`). A 3.5 só as mapeia.

`V10__kitchen_queue_ready.sql`:

```sql
DROP INDEX idx_kds_queue;
CREATE INDEX idx_kds_queue
    ON tab_item (prep_station, status, ordered_at)
    WHERE status IN ('PENDING','IN_PREPARATION','READY');

ALTER TABLE tab_item
    ADD CONSTRAINT ck_tab_item_ready     CHECK (status <> 'READY'     OR ready_at     IS NOT NULL),
    ADD CONSTRAINT ck_tab_item_delivered CHECK (status <> 'DELIVERED' OR delivered_at IS NOT NULL);

-- seis chaves INTEGER por propriedade existente, ON CONFLICT DO NOTHING
```

**Limites de atraso (K11)** — `setting`, tipo `INTEGER`, em minutos desde o
lançamento:

| Chave | Padrão |
|---|---|
| `restaurant.kitchen-display.kitchen.warning-minutes` | 15 |
| `restaurant.kitchen-display.kitchen.late-minutes` | 25 |
| `restaurant.kitchen-display.pizza.warning-minutes` | 20 |
| `restaurant.kitchen-display.pizza.late-minutes` | 30 |
| `restaurant.kitchen-display.bar.warning-minutes` | 5 |
| `restaurant.kitchen-display.bar.late-minutes` | 10 |

A migration semeia para toda `property` existente; o `DevUserSeeder` semeia ao
criar a propriedade de dev (G3). O backend só lê e devolve: quem colore é o front.

---

## 3. Invariantes e transições

| De → Para | Método em `Tab` | Quem | Instante |
|---|---|---|---|
| `PENDING → IN_PREPARATION` | `startItemPreparation(itemId, at)` | `KITCHEN`, `ADMIN` | grava `preparation_started_at` |
| `IN_PREPARATION → READY` | `markItemReady(itemId, at)` | `KITCHEN`, `ADMIN` | grava `ready_at` |
| `PENDING → READY` (K2) | `markItemReady(itemId, at)` | `KITCHEN`, `ADMIN` | grava `ready_at`; `preparation_started_at` fica nulo |
| `PENDING`/`IN_PREPARATION`/`READY → DELIVERED` (K1, K4) | `deliverItem(itemId, at)` | `WAITER`, `ADMIN` | grava `delivered_at` |
| `READY → IN_PREPARATION` (K3) | `undoItemStatus(itemId, at)` | `KITCHEN`, `ADMIN` | apaga `ready_at` |
| `READY → PENDING` quando o pronto pulou o preparo | `undoItemStatus(itemId, at)` | `KITCHEN`, `ADMIN` | apaga `ready_at` |
| `IN_PREPARATION → PENDING` (K3) | `undoItemStatus(itemId, at)` | `KITCHEN`, `ADMIN` | apaga `preparation_started_at` |
| tudo menos `CANCELLED` → `CANCELLED` | `cancelItem` (2.2, sem mudança) | `WAITER`, `ADMIN` | já existe (#6) |

1. **Toda transição passa pelo agregado `Tab`.** As regras moram no enum:
   `TabItemStatus.acceptsPreparationStart()`, `acceptsReady()`,
   `acceptsDelivery()`, `acceptsUndo()` e `isOnKitchenQueue()`.
2. **Ordem das checagens** em cada método: item existe
   (`TAB_ITEM_NOT_FOUND`) → item cancelado (`TAB_ITEM_ALREADY_CANCELLED`) →
   status aceita a transição (`INVALID_TAB_ITEM_TRANSITION`).
3. **Desfazer volta ao status de antes do último toque.** `READY` volta a
   `IN_PREPARATION` se o preparo foi iniciado, ou a `PENDING` se o pronto pulou o
   preparo (K2): nunca existe `IN_PREPARATION` sem `preparation_started_at`.
   `PENDING` e `DELIVERED` não desfazem; `DELIVERED` nunca volta (K3), errou, cancela.
4. **O status da comanda não bloqueia as transições do KDS.** A cozinha avança
   item de comanda `OPEN`, `CLOSING` ou `CLOSED` (F12). Comanda `CANCELLED` só tem
   item cancelado (#10); item de comanda `MERGED` já foi transferido (3.6).
5. **Item por peso nunca entra na fila:** nasce `DELIVERED` e
   `isOnKitchenQueue()` é falso.
6. **Instantes vêm do `Clock`.** Sem autor por transição (K6): quem mexeu por
   último fica só em `updated_by`.
7. **Na fila:** `PENDING`, `IN_PREPARATION` e `READY` (K5). Sai ao ser entregue
   ou cancelado.

`isOnKitchenQueue` por status:

| `PENDING` | `IN_PREPARATION` | `READY` | `DELIVERED` | `CANCELLED` |
|---|---|---|---|---|
| sim | sim | sim | não | não |

Matriz status × operação (`✓` = aceita; senão, o código):

| Status | start | ready | deliver | undo |
|---|---|---|---|---|
| `PENDING` | ✓ | ✓ | ✓ | `INVALID_TAB_ITEM_TRANSITION` |
| `IN_PREPARATION` | `INVALID_TAB_ITEM_TRANSITION` | ✓ | ✓ | ✓ (→ `PENDING`) |
| `READY` | `INVALID_TAB_ITEM_TRANSITION` | `INVALID_TAB_ITEM_TRANSITION` | ✓ | ✓ (→ `IN_PREPARATION` ou `PENDING`) |
| `DELIVERED` | `INVALID_TAB_ITEM_TRANSITION` | `INVALID_TAB_ITEM_TRANSITION` | `INVALID_TAB_ITEM_TRANSITION` | `INVALID_TAB_ITEM_TRANSITION` |
| `CANCELLED` | `TAB_ITEM_ALREADY_CANCELLED` | `TAB_ITEM_ALREADY_CANCELLED` | `TAB_ITEM_ALREADY_CANCELLED` | `TAB_ITEM_ALREADY_CANCELLED` |

---

## 4. Assinaturas públicas

```java
// restaurant.domain.TabItemStatus
public boolean isOnKitchenQueue();
public boolean acceptsPreparationStart();
public boolean acceptsReady();
public boolean acceptsDelivery();
public boolean acceptsUndo();
public TabItemStatus undoneTo(boolean preparationStarted); // só quando acceptsUndo(); READY → IN_PREPARATION ou PENDING
public boolean carriesPreparationStart();                  // tudo menos PENDING

// restaurant.domain.Tab — cada uma devolve o evento da transição
public TabItemStatusChanged startItemPreparation(TabItemId itemId, Instant at);
public TabItemStatusChanged markItemReady(TabItemId itemId, Instant at);
public TabItemStatusChanged deliverItem(TabItemId itemId, Instant at);
public TabItemStatusChanged undoItemStatus(TabItemId itemId, Instant at);

// restaurant.domain.TabItem
public Optional<Instant> preparationStartedAt();
public Optional<Instant> readyAt();
public boolean isOnKitchenQueue();

// restaurant.domain — eventos (implementam DomainEvent)
record TabItemOrdered(TabId tabId, TabItemId itemId, PrepStation station, TabItemStatus status, Instant occurredAt)
    static TabItemOrdered of(TabId tabId, TabItem item)          // occurredAt = orderedAt
    boolean reachesKitchenQueue()                                // status.isOnKitchenQueue()
record TabItemStatusChanged(TabId tabId, TabItemId itemId, PrepStation station,
                            TabItemStatus from, TabItemStatus to, Instant occurredAt)
    boolean touchesReady()                                       // from ou to é READY
record TabItemCancelled(TabId tabId, TabItemId itemId, PrepStation station, String reason, Instant occurredAt)
    static TabItemCancelled of(TabId tabId, TabItem cancelledItem) // occurredAt = cancelledAt

// restaurant.domain.InvalidTabItemTransitionException — ConflictException, "INVALID_TAB_ITEM_TRANSITION"

// restaurant.domain.TabRepository — métodos novos
Optional<Tab> findByItemIdForItemChange(TabItemId itemId);
Optional<Tab> findByIdForItemChange(TabId id, TabItemId itemId);

// restaurant.application
public interface KitchenQueue {                      // porta de leitura, em restaurant.infra
    List<KitchenTicket> ticketsOf(UUID propertyId, PrepStation station);
    Optional<KitchenTicket> ticket(TabItemId itemId);
}
record KitchenTicket(...)                            // ver §6
record KitchenQueueView(PrepStation station, Instant serverTime,
                        int warningAfterMinutes, int lateAfterMinutes, List<KitchenTicket> tickets)
class KitchenDisplayService {
    KitchenQueueView queue(PrepStation station);
    KitchenTicket startPreparation(TabItemId itemId);
    KitchenTicket markReady(TabItemId itemId);
    KitchenTicket undo(TabItemId itemId);
    Tab deliver(TabId tabId, TabItemId itemId);
}

// identity.api
public interface AccessTokenAuthenticator {
    Authentication authenticate(String accessToken); // ROLE_* como o filtro HTTP; falha = DomainException com código
}
```

O `TabItemCancelled` não carrega o status anterior: obtê-lo exigiria mudar a
assinatura de `Tab.cancelItem` (território da 3.2) ou ler o item antes das
checagens da #17, e nenhum consumidor usa o dado (a mensagem leva o ticket já
cancelado).

---

## 5. Eventos e entrega em tempo real

- **Publicação:** `TabService.addItem` publica `TabItemOrdered` e
  `TabService.cancelItem` publica `TabItemCancelled`, depois do `save`, pelo
  `ApplicationEventPublisher`. O `KitchenDisplayService` publica o
  `TabItemStatusChanged` que o agregado devolve. Item por peso também gera
  `TabItemOrdered`; o ouvinte o descarta porque não está na fila.
- **Ouvinte:** `KitchenDisplayBroadcaster` (`restaurant.web`, adaptador de saída
  como um controller), `@TransactionalEventListener(phase = BEFORE_COMMIT)`: lê o
  ticket pela porta `KitchenQueue` **dentro** da transação, na conexão que ela já
  tem, e registra um `afterCommit` que só envia pelo `SimpMessageSendingOperations`.
  Nada depois do commit toca o banco: ler ali pediria uma segunda conexão ao pool
  antes de a primeira voltar, e lançamentos simultâneos acima do tamanho do pool
  travariam até o timeout (review, rodada 1). Os `afterCommit` rodam na ordem dos
  commits, o que preserva a ordem das mensagens; nada de `@Async`.
- **Consequências:** lançamento recusado ou transação desfeita não chega ao
  `afterCommit` e não empurra nada. Falha na leitura ou no envio fica em log e não
  vira erro na resposta; a tela se corrige na próxima reconexão (K13).

**Destinos**

| Evento | `/topic/kitchen/{station}` | `/topic/restaurant/ready-items` |
|---|---|---|
| `TabItemOrdered` de item na fila | `ORDERED` | — |
| `TabItemStatusChanged` | `STATUS_CHANGED` | quando `from` ou `to` é `READY` |
| `TabItemCancelled` | `CANCELLED` | `CANCELLED` |

O tópico do garçom recebe também a saída de `READY` (desfazer ou entregar) e o
cancelamento, para a tela dele tirar o aviso; ela ignora o que não conhece.

**Mensagem**

```json
{ "type": "ORDERED | STATUS_CHANGED | CANCELLED",
  "occurredAt": "2026-09-26T19:04:11Z",
  "item": { "...KitchenTicket..." },
  "cancellationReason": "só em CANCELLED" }
```

---

## 6. `KitchenTicket`

O mesmo formato na fila REST, na resposta das transições e na mensagem STOMP
(K7). Sem preço e sem garçom.

```json
{ "itemId", "tabId", "origin", "diningTableLabel", "cardNumber",
  "itemName", "variantName", "quantity",
  "modifiers": [{ "name", "quantity" }],
  "specialInstructions", "prepStation", "status",
  "orderedAt", "preparationStartedAt", "readyAt", "updatedAt" }
```

- Instantes em ISO-8601 UTC; o que não se aplica é `null`.
- `updatedAt` é o `updated_at` da linha: o cliente descarta mensagem mais velha
  que o estado que já tem.
- **Estado inicial e reconexão (K13):** o cliente conecta, assina e só então chama
  `GET /api/kitchen/queue`, guardando as mensagens que chegarem no intervalo;
  funde por `itemId`, vale o `updatedAt` maior. Em cada reconexão repete tudo. Sem
  histórico de mensagens perdidas.

---

## 7. STOMP e autenticação

- **Endpoint:** `/ws/kitchen`, WebSocket puro, sem SockJS. Origens permitidas = as
  do `app.cors.allowed-origins`. Handshake com query string é recusado (400): o
  token nunca viaja na URL.
- **Broker:** simples, em memória, prefixo `/topic`, `preservePublishOrder`,
  heartbeat de 10 s nos dois sentidos. Prefixo de aplicação `/app`, mas nenhum
  `SEND` é aceito: toda ação vai por REST.
- **Handshake HTTP:** `/ws/kitchen` é `permitAll` no `SecurityConfig`; a
  autenticação é no CONNECT.
- **CONNECT:** cabeçalho nativo `Authorization: Bearer <access>`. Um
  `ChannelInterceptor` do `app` chama `AccessTokenAuthenticator` e grava o usuário
  na sessão; o nome do principal é o id do usuário. O comando `STOMP`, sinônimo de
  `CONNECT`, passa pelo mesmo caminho. Falha: frame `ERROR` com `message` igual ao
  código — `AUTHENTICATION_REQUIRED` (sem cabeçalho, ou frame que o protocolo
  recusa numa sessão não conectada, como frame antes do `CONNECT` ou `CONNECT`
  duplicado), `TOKEN_EXPIRED`, `INVALID_TOKEN`, `SESSION_SUPERSEDED`,
  `USER_INACTIVE` — e a sessão fecha.
- **Autorização:** manual, sem `@EnableWebSocketSecurity` (o CSRF obrigatório no
  CONNECT não faz sentido com bearer): interceptor JWT →
  `SecurityContextChannelInterceptor` → `AuthorizationChannelInterceptor`, com:
  - `CONNECT`, `UNSUBSCRIBE`, `HEARTBEAT`: autenticado; `DISCONNECT`: livre
  - `SUBSCRIBE /topic/kitchen/**`: `KITCHEN` ou `ADMIN` (K1, K8)
  - `SUBSCRIBE /topic/restaurant/ready-items`: `WAITER` ou `ADMIN` (K10)
  - todo o resto, `SEND /app/**` incluído: negado → `ERROR` com `ACCESS_DENIED`
- **Token expirado com a conexão aberta:** a autenticação vale para a conexão. A
  sessão só recebe; toda ação passa pelo REST, que revalida o token. Na reconexão o
  cliente usa o token renovado.
- **Sessão única (0.4 #3):** um usuário `KITCHEN` por tela.
- **Log:** não ligar `DEBUG` do `StompSubProtocolHandler` (nem de
  `org.springframework.web.socket`) em produção: o frame `CONNECT` sai no log com o
  cabeçalho `Authorization`, isto é, com o token.

---

## 8. API REST

| Rota | Perfis | Resposta |
|---|---|---|
| `GET /api/kitchen/queue?station=PIZZA` | `KITCHEN`, `ADMIN` | 200 `{station, serverTime, warningAfterMinutes, lateAfterMinutes, items}` — `PENDING`, `IN_PREPARATION` e `READY`, por `orderedAt` e depois id |
| `POST /api/kitchen/items/{itemId}/start` | `KITCHEN`, `ADMIN` | 200 `KitchenTicket` |
| `POST /api/kitchen/items/{itemId}/ready` | `KITCHEN`, `ADMIN` | 200 `KitchenTicket` |
| `POST /api/kitchen/items/{itemId}/undo` | `KITCHEN`, `ADMIN` | 200 `KitchenTicket` |
| `POST /api/restaurant/tabs/{tabId}/items/{itemId}/deliver` | `WAITER`, `ADMIN` | 200 `TabResponse` |

- **Verbos (K14):** comando por verbo, divergindo do `PATCH .../status` do plano
  §8. Cada transição tem seu perfil e seu método no agregado.
- `station` ausente ou fora de `KITCHEN`/`PIZZA`/`BAR`: 400 `VALIDATION_FAILED`.
- `serverTime` deixa o front calcular o tempo decorrido sem depender do relógio
  do monitor.
- `/deliver` fica no `TabItemDeliveryController`, novo, para não tocar no
  `TabController` (3.2). `/api/kitchen/*` fica no `KitchenDisplayController`.
- `TabItemResponse` ganha `preparationStartedAt`, `readyAt` e `deliveredAt`.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `INVALID_TAB_ITEM_TRANSITION` | 409 | O status atual não aceita a transição pedida |
| `TAB_ITEM_ALREADY_CANCELLED` | 409 | Transição em item cancelado (reaproveitado) |
| `TAB_ITEM_NOT_FOUND` | 404 | Item inexistente (reaproveitado) |
| `TAB_NOT_FOUND` | 404 | Entrega em comanda inexistente (reaproveitado) |
| `VALIDATION_FAILED` | 400 | `station` ausente ou desconhecido |
| `SETTING_NOT_FOUND` | 404 | Limite de atraso do setor não configurado |

---

## 9. Concorrência

- **Corrida real: cozinha marca pronto enquanto o garçom cancela o mesmo item.**
  Toda transição trava como o cancelamento de item: comanda `FOR KEY SHARE`,
  depois a linha do item `FOR UPDATE`, na mesma ordem que lançar, cancelar e fechar
  usam. A segunda operação encontra o estado novo: `TAB_ITEM_ALREADY_CANCELLED` ou
  segue sobre o `READY` (cancelar vale em `READY`, #6). Nunca 500, nunca as duas
  sobre o mesmo estado antigo.
- **Rota da cozinha só tem `itemId`:** `findByItemIdForItemChange` lê o `tab_id`
  sem lock, trava a comanda `FOR KEY SHARE`, trava o item `FOR UPDATE` e carrega.
  Nunca trava o item antes da comanda.
- **Fila:** leitura sem lock, por projeção SQL em `restaurant.infra`
  (`JpaKitchenQueue`), sustentada por `idx_kds_queue`; adicionais numa segunda
  consulta. Não carrega agregados.
- **Instância única:** o broker simples só vale com uma instância (v1).

---

## 10. `.http` e verificação do tempo real

`http/35-kitchen-display.http`.

**Preparação:** logins de `admin`, `garcom` e `cozinha`; mesa com sufixo
aleatório; pizza (`PIZZA`), bebida (`BAR`) e buffet por peso (`KITCHEN`); comanda
na mesa com pizza e bebida; comanda no cartão com um prato por peso.

**Caminho feliz:** a fila `PIZZA` tem a pizza e não a bebida, com os limites; a
`BAR` tem a bebida; a `KITCHEN` não tem o prato por peso; `start` e `ready` na
pizza; `undo` volta a `IN_PREPARATION`; `ready` de novo; garçom entrega e a pizza
sai da fila; `ready` direto na bebida.

**Negativos:** `start` de novo (`INVALID_TAB_ITEM_TRANSITION`); `undo` de item
`PENDING`; transição em item cancelado (`TAB_ITEM_ALREADY_CANCELLED`); `itemId`
inexistente (404); `station=GRILL` e sem `station` (400); sem token (401); garçom
na fila (403); cozinha no `/deliver` (403).

**Limpeza:** cancelar os itens e as comandas, para rodar de novo.

**Tempo real:** o HTTP Client não serve de assert confiável para STOMP (o frame
termina em NUL). A prova é o teste de integração. Inspeção manual, no console do
navegador com `@stomp/stompjs`:

```js
const client = new StompJs.Client({
  brokerURL: 'ws://localhost:8080/ws/kitchen',
  connectHeaders: { Authorization: 'Bearer ' + kitchenToken },
  onConnect: () => client.subscribe('/topic/kitchen/PIZZA', m => console.log(JSON.parse(m.body))),
  onStompError: f => console.log('ERROR', f.headers.message),
});
client.activate();
```

---

## 11. Testes

**Unidade (agente de teste), densa:** a matriz da §3 inteira, com o instante
gravado ou apagado em cada transição aceita; `isOnKitchenQueue` por status; item
por peso fora da fila; transição em comanda `CLOSING` e `CLOSED`, fechada pelo
fechamento real da 3.2; o evento devolvido com `from` e `to` corretos.

**Integração (`app`):**
- Fatia REST: lançar, ver na fila, `start`, `ready`, `undo`, entregar, sair da
  fila; 403 dos perfis trocados.
- Comanda em fechamento (F12): `start` com a comanda em `CLOSING`; `ready` e
  entrega depois de `CLOSED`.
- Concorrência: um cancelamento e quatro "pronto" simultâneos no mesmo item:
  nenhum 500, estado final `CANCELLED` com um autor só.
- WebSocket (`WebSocketStompClient` real): CONNECT sem token e com token expirado
  recebem `ERROR` com o código; handshake com query string recebe 400; `WAITER`
  assinando `/topic/kitchen/PIZZA`, `KITCHEN` assinando
  `/topic/restaurant/ready-items` e `SEND` do cliente recebem `ACCESS_DENIED`;
  `ORDERED` só no setor certo; `CANCELLED`; lançamento recusado não gera mensagem.
- Pool pequeno: com `maximum-pool-size=2`, seis lançamentos simultâneos respondem
  201 e as seis mensagens chegam.
