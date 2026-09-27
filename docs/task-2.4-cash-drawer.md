# Task 2.4 — Caixa: abertura de turno, sangria, suprimento e fechamento

**Objetivo:** abrir um turno de caixa (`CashDrawerSession`) com fundo de troco,
registrar sangria (`CASH_DROP`) e suprimento (`CASH_SUPPLY`), vincular ao turno
aberto cada pagamento em `CASH`, e fechar o turno com a contagem física,
congelando o valor esperado e a diferença (quebra de caixa).

Decisões: `docs/decisions/task-2.4.md` (G1–G5 e C1–C10, aprovadas pelo Ruan em
2026-09-26). Herdadas da 1.3: #1 (troco é físico: folio `TAB` recusa pagamento
acima do saldo), #2 (estorno de pagamento), #5/#16/#19/#22 (idempotência), #14
(`payment.cash_drawer_session_id` nulo, sem FK, até aqui), #15/#26 (lock e
detach). Gabarito de estilo: o agregado `Folio` (filhos append-only, save com
flush e tradução de constraint) e o `TabRepository` (`FOR KEY SHARE` contra
`FOR UPDATE`, nativos).

Aqui é dinheiro e conferência de valores: teste **denso** em valor esperado,
diferença, transição e na corrida entre pagamento e fechamento.

---

## 1. Escopo

**Dentro**
- Agregado `CashDrawerSession` com `CashMovement` (append-only)
- `CashDrawerSessionStatus` (`OPEN` · `CLOSED`) e `CashMovementType`
  (`CASH_DROP` · `CASH_SUPPLY`), com comportamento
- Abrir turno com `openingFloat`; no máximo um turno aberto por propriedade (C1)
- Sangria e suprimento com valor, motivo e `Idempotency-Key` (C9)
- Fechar com `countedAmount`: calcula e congela `expectedAmount`; a diferença é
  derivada; nunca bloqueia por quebra (C6) e nunca exige nota (decisão #22)
- Vincular pagamento `CASH` ao turno aberto **dentro de
  `FolioService.registerPayment`**, o caminho único de todo pagamento (G2), com
  a configuração `billing.cash-drawer.required` (C2)
- Mapear `payment.cash_drawer_session_id` em `Payment`
- Rotas REST do caixa, com fechamento cego (C4)
- Migration `V8__cash.sql`, com a §12 de `docs/schema-banco-de-dados.md`
  atualizada **antes**
- Seed da configuração no `DevUserSeeder` (G3)
- `http/41-billing-cash-sessions.http` e a linha dele no `http/README.md`

**Fora**
- Entidade `CashDrawer` física, com vários caixas simultâneos (C1; schema §14)
- Relatório de turno com totais de PIX e cartão, histórico e busca de turnos: 5.1
- Recebimento pelo garçom no fechamento da comanda: 3.2. Ela chama
  `FolioFacade.receivePayment` e herda a regra do §4.3 sem reimplementá-la
- Reabrir turno fechado: não existe na v1. Estornar movimento de caixa:
  correção por movimento oposto com motivo
- Tela de caixa: 4.6. Edição da configuração pelo `ADMIN`: 4.7
- `CLAUDE.md` (glossário já atualizado no PR #17) e `docs/MIGRATIONS.md`

---

## 2. Modelo de dados

`V8__cash.sql`, a partir do schema §12 original, com estas mudanças:

| Tabela | Mudança | Por quê |
|---|---|---|
| `cash_drawer_session` | + auditoria (4 colunas) | Toda tabela transacional |
| `cash_drawer_session` | `opened_by`/`closed_by` **sem FK** para `app_user` | Padrão do billing (`payment.received_by`); a FK cruzaria o módulo identity |
| `cash_drawer_session` | − `difference` | Derivado: `counted − expected`, calculado |
| `cash_drawer_session` | `expected_amount` congelado no fechamento, exigido pelo `CHECK` | A conferência é um fato histórico: estorno posterior não reescreve o turno fechado (C8) |
| `cash_drawer_session` | + `closing_note TEXT` | Observação opcional do fechamento (C6, #22) |
| `cash_drawer_session` | `CHECK counted_amount >= 0` | |
| `cash_movement` | só `CASH_DROP` e `CASH_SUPPLY` no `CHECK` | C3: fundo e contagem são colunas do turno |
| `cash_movement` | − `ON DELETE CASCADE`; + `updated_*`; `reason NOT NULL` | Nada é apagado; auditoria; motivo obrigatório |
| `cash_movement` | + `idempotency_key` único | C9 |
| `payment` | FK `fk_payment_cash_session`; `ck_payment_cash_session`; índice parcial | Só `CASH` vai para turno; soma rápida no fechamento |
| `setting` | semeia `billing.cash-drawer.required = false` para as propriedades existentes | C2/G3 |

O SQL exato está na §12 do schema. Mapeamento:
- `CashDrawerSession` e `CashMovement` estendem `AuditedEntity`; ids guardados
  como `@Id UUID` e expostos como `CashDrawerSessionId`/`CashMovementId`
- `movements` é `@OneToMany` EAGER com `@Fetch(SUBSELECT)`, ordenado por
  `createdAt, id`
- `Payment` ganha `cashDrawerSessionId` (coluna já existente na V6), exposto como
  `Optional<CashDrawerSessionId>`
- O autor de um movimento é o `created_by` da auditoria

---

## 3. Invariantes

### Abertura
1. Pertence a uma propriedade. Nasce `OPEN`, com `openedBy`, `openedAt` e
   `openingFloat`
2. `openingFloat` ≥ 0 (`INVALID_OPENING_FLOAT`, também quando nulo); fundo zero
   é válido
3. No máximo um turno `OPEN` por propriedade. Checado no caso de uso para dar um
   código legível, e garantido por `idx_cash_session_open`, que a infra traduz
   em `CASH_DRAWER_SESSION_ALREADY_OPEN` (409), inclusive em duas aberturas
   simultâneas

### Movimento (`drop`, `supply`)
4. Idempotência pelo header `Idempotency-Key`, aparado, 1 a 100
   (`INVALID_IDEMPOTENCY_KEY`). Retry idêntico (mesmo turno, tipo e valor)
   devolve o movimento original **antes de qualquer outra regra**, inclusive
   turno fechado; qualquer diferença de tipo ou valor no mesmo turno, ou a chave
   em outro turno, é `IDEMPOTENCY_KEY_REUSED`. O motivo não entra na comparação.
   A chave é única entre todos os turnos (`uk_cash_movement_idempotency`); uma
   corrida entre turnos vira `IDEMPOTENCY_KEY_REUSED`, nunca 500
5. Só com turno `OPEN` (`CASH_DRAWER_SESSION_CLOSED`)
6. Valor > 0 (`INVALID_CASH_MOVEMENT_AMOUNT`, também quando nulo); motivo
   aparado, de 1 a 500 (`INVALID_CASH_MOVEMENT_REASON`)
7. **Sangria acima do valor esperado é aceita** (C7): a quebra aparece no
   fechamento
8. Append-only. Erro se corrige com movimento oposto e motivo

Ordem: chave válida → replay/chave reusada → turno fechado → valor → motivo.

### Valor esperado e fechamento
9. **`expectedAmount(cashPayments)` = `openingFloat` + Σ `signedAmount` dos
   movimentos (suprimento soma, sangria subtrai, na ordem de registro) +
   `cashPayments`.** Somar com sinal faz um movimento e o seu oposto se anularem
   sem que a conta passe pela soma dos suprimentos sozinha, que pode sair da
   faixa de `Money` (decisão #18). `cashPayments` é a soma dos pagamentos
   `CASH` `CONFIRMED` vinculados ao turno, lida por consulta
   (`CashPaymentTotals`) e passada pelo caso de uso. Pagamento `REFUNDED` não
   entra. Pode ser negativo (sangria acima do esperado, invariante 7). Com o
   turno `CLOSED`, devolve o valor congelado e ignora o argumento
10. `close(countedAmount, cashPayments, note, closedBy, closedByAdmin, closedAt)`
    congela `expectedAmount(cashPayments)` e grava `countedAmount`,
    `closingNote`, `closedAt` e `closedBy`. **Nunca bloqueia por diferença** (C6)
11. `difference()` = `countedAmount − expectedAmount`: positiva é sobra,
    negativa é falta. Calculada, vazia enquanto `OPEN`
12. Contagem ≥ 0 (`INVALID_COUNTED_AMOUNT`, também quando nula)
13. **O fechamento é sempre aceito, e `closingNote` é opcional para todos, com
    diferença zero ou não** (decisão do Ruan de 2026-09-27, #22, que reverte a
    parte da C6 que exigia justificativa na quebra). Exigir nota só quando a
    contagem difere vazaria o esperado do fechamento cego: a recepção testaria
    valores até o sistema aceitar sem nota. A diferença fica congelada e o
    `ADMIN` confere depois. A nota é aparada; em branco vira ausente; acima de
    500 caracteres é `INVALID_CASH_CLOSING_NOTE` (#23)
14. Turno `CLOSED` recusa sangria, suprimento e fechar de novo
    (`CASH_DRAWER_SESSION_CLOSED`)
15. Quem fecha: quem abriu, ou `ADMIN` (`CASH_DRAWER_SESSION_NOT_OWNED`, 409)
16. Fechar é sempre explícito; o turno pode atravessar o dia (C10)

Ordem no fechamento: turno existe → fechado → dono → contagem → tamanho da nota.

### Leitura
17. `frozenExpectedAmount()`: o esperado congelado, vazio enquanto `OPEN`
18. `frozenCashPayments()`: os pagamentos `CASH` contados no fechamento,
    derivados do congelado (`frozenExpectedAmount − openingFloat − Σ
    signedAmount`); vazio enquanto `OPEN`
18a. **Turno sempre legível** (decisão #18): o valor esperado, `totalDrops()` e
    `totalSupplies()` são calculados dentro da transação de toda sangria e todo
    suprimento, antes do commit (padrão da 1.3 #27). O mesmo vale para o
    esperado do turno em que cai um pagamento `CASH`. Um movimento ou pagamento
    que tornaria algum deles irrepresentável é recusado com `MONEY_OUT_OF_RANGE`
    e nada é gravado. Assim o turno nunca fica impossível de fechar, o que
    impediria a propriedade de abrir outro (C1)
19. **Fechamento cego** (C4): `revealsExpectedAmountTo(viewerIsAdmin)` é
    verdadeiro com o turno `CLOSED` ou para o `ADMIN`; com o turno `OPEN` e quem
    não é `ADMIN`, falso

### Vínculo do pagamento (`Folio.receive`)
20. `PaymentMethod.goesToCashDrawer()` é verdadeiro só em `CASH`
21. O vínculo é resolvido por `CashDrawerAssignment` (domínio), montado pelo
    caso de uso com o turno aberto (se houver) e a configuração
    `billing.cash-drawer.required`:
    - método que não vai para o caixa → sem vínculo, com o controle ligado ou não
    - `CASH` com turno aberto → vinculado a ele
    - `CASH` sem turno e controle desligado → sem vínculo
    - `CASH` sem turno e controle ligado → `CASH_DRAWER_SESSION_NOT_OPEN` (409)
22. Em `Folio.receive`, a ordem é a da 1.3 #25 com o caixa por último: chave
    válida → replay/chave reusada → folio fechado → método → valor → saldo →
    caixa. O retry de um `CASH` já registrado devolve o original mesmo que o
    turno tenha fechado depois ou que o controle tenha sido ligado
23. O overload de 5 argumentos de `Folio.receive` equivale a um caixa sem turno
    aberto e com o controle desligado
24. Estorno de pagamento `CASH`: com o turno ainda aberto, o valor esperado cai
    na hora (invariante 9). Com o turno já fechado, o valor congelado não muda;
    a saída física se registra como sangria no turno atual (C8)

### Concorrência
25. Sangria, suprimento e fechamento carregam o turno com `SELECT ... FOR
    UPDATE` **nativo**. O `PESSIMISTIC_WRITE` do Hibernate sai `FOR NO KEY
    UPDATE`, que não conflita com `FOR KEY SHARE`
26. Todo recebimento de pagamento trava o turno aberto com `FOR KEY SHARE` e só
    então o considera aberto. O fechamento (`FOR UPDATE`) espera esses
    pagamentos commitarem e soma depois, em READ COMMITTED; um pagamento que
    chega durante o fechamento espera, relê a linha já `CLOSED` e não a vincula.
    Nunca fica pagamento vinculado a turno fechado sem entrar no valor esperado
27. O estorno de um pagamento vinculado trava o turno dele `FOR KEY SHARE`,
    aberto ou fechado: ou o estorno commita antes da soma do fechamento, ou só
    toma o seu instante depois do commit do fechamento (invariante 24). Portanto
    o pagamento entra no congelado exatamente quando `refundedAt` é posterior a
    `closedAt`
28. Ordem global das travas: **comanda → folio → turno de caixa**. O fechamento
    do caixa não trava folio, então não há ciclo
29. `findByIdForUpdate` tira do contexto a instância já carregada (1.3 #26)

---

## 4. Assinaturas públicas

### 4.1 Domínio (`billing.domain`)

```java
// CashDrawerSession
static CashDrawerSession open(UUID propertyId, Money openingFloat, UUID openedBy, Instant openedAt);
CashMovement drop(Money amount, String reason, String idempotencyKey);
CashMovement supply(Money amount, String reason, String idempotencyKey);
void close(Money countedAmount, Money cashPayments, String note, UUID closedBy,
           boolean closedByAdmin, Instant closedAt);
Money expectedAmount(Money cashPayments);   // CLOSED: o congelado
Optional<Money> frozenExpectedAmount();
Optional<Money> frozenCashPayments();
Optional<Money> difference();
boolean revealsExpectedAmountTo(boolean viewerIsAdmin);
Money totalDrops(); Money totalSupplies();
CashDrawerSessionId id(); UUID propertyId(); CashDrawerSessionStatus status(); boolean isOpen();
Money openingFloat(); UUID openedBy(); Instant openedAt();
Optional<UUID> closedBy(); Optional<Instant> closedAt();
Optional<Money> countedAmount(); Optional<String> closingNote();
List<CashMovement> movements();             // unmodifiable, por createdAt e id
Optional<CashMovement> movementWithKey(String idempotencyKey);   // aparada

// CashMovement (filho, criado só pelo agregado)
CashMovementId id(); CashMovementType type(); Money amount(); Money signedAmount();
String reason(); String idempotencyKey(); boolean matches(CashMovementType type, Money amount);
// + createdAt() e createdBy() da auditoria

// Ids (EntityId): CashDrawerSessionId, CashMovementId — newId(), of(String)

// Enums
CashDrawerSessionStatus  boolean acceptsMovements();         // OPEN
CashMovementType         Money signedAmount(Money amount);    // DROP negativo
PaymentMethod (api)      boolean goesToCashDrawer();          // CASH

// Pagamentos CASH de um turno
record CashPaymentTotals(Money total, long count) { static CashPaymentTotals none(); }

// Vínculo
final class CashDrawerAssignment {
    static CashDrawerAssignment of(Optional<CashDrawerSessionId> openSession, boolean required);
    Optional<CashDrawerSessionId> sessionFor(PaymentMethod method); // CASH_DRAWER_SESSION_NOT_OPEN
}

// Folio: overload novo; o de 5 argumentos equivale a of(Optional.empty(), false)
Payment receive(PaymentMethod method, Money amount, String idempotencyKey, UUID receivedBy,
                Instant paidAt, CashDrawerAssignment cashDrawer);
Optional<CashDrawerSessionId> cashDrawerSessionOf(PaymentId paymentId); // vazio se não houver

// Payment
Optional<CashDrawerSessionId> cashDrawerSessionId();
```

`CashDrawerSessionRepository`: `findById`, `findByIdForUpdate` (`FOR UPDATE`
nativo + detach), `findOpen(propertyId)`, `findOpenForKeyShare(propertyId)`
(devolve o id), `lockForKeyShare(sessionId)`,
`findSessionByMovementKey(key)`, `sumConfirmedCashPayments(sessionId)`, `save`
(persist + flush, traduzindo `idx_cash_session_open` e
`uk_cash_movement_idempotency`).

### 4.2 Caso de uso
`CashDrawerSessionService`: `open(openingFloat)`, `drop(id, amount, reason,
key)`, `supply(id, amount, reason, key)`, `close(id, countedAmount, note,
closedByAdmin)`, `current()`, `find(id)`, todos devolvendo
`CashDrawerSessionWithTotals(session, cashPayments)`. `AuditorAware`, `Clock` e
`CurrentProperty` injetados. Sem `if` de regra.

`FolioService.registerPayment` lê o turno aberto (`FOR KEY SHARE`) e
`billing.cash-drawer.required`, monta o `CashDrawerAssignment` e o entrega a
`Folio.receive`. `refundPayment` trava o turno do pagamento `FOR KEY SHARE`.

### 4.3 Contrato para a 3.2 (sem mudança no `billing/api` nesta task)
Todo recebimento de pagamento, seja pela rota da recepção (1.3) ou por
`FolioFacade.receivePayment`, passa por `FolioService.registerPayment`, que monta
o `CashDrawerAssignment`. A 3.2 **não** reimplementa o vínculo, e
`CASH_DRAWER_SESSION_NOT_OPEN` derruba o fechamento da comanda inteiro, na mesma
transação. A 3.2 respeita a ordem de travas comanda → folio → turno.

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `CASH_DRAWER_SESSION_NOT_FOUND` | 404 | O turno não existe, ou não há turno aberto em `/current` |
| `CASH_DRAWER_SESSION_ALREADY_OPEN` | 409 | Já existe turno aberto na propriedade, inclusive em corrida |
| `CASH_DRAWER_SESSION_CLOSED` | 409 | Sangria, suprimento ou fechamento em turno fechado |
| `CASH_DRAWER_SESSION_NOT_OPEN` | 409 | `CASH` com controle ligado e nenhum turno aberto |
| `CASH_DRAWER_SESSION_NOT_OWNED` | 409 | Fechar turno de outro operador sem ser `ADMIN` |
| `IDEMPOTENCY_KEY_REUSED` | 409 | Reusado da 1.3, para movimento |
| `INVALID_OPENING_FLOAT` | 422 | Fundo < 0 |
| `INVALID_CASH_MOVEMENT_AMOUNT` | 422 | Movimento ≤ 0 |
| `INVALID_CASH_MOVEMENT_REASON` | 422 | Motivo em branco ou acima de 500 |
| `INVALID_COUNTED_AMOUNT` | 422 | Contagem < 0 |
| `INVALID_CASH_CLOSING_NOTE` | 422 | Nota do fechamento acima de 500 caracteres (a nota é opcional, #22) |
| `INVALID_IDEMPOTENCY_KEY` | 422 | Reusado da 1.3 |
| `MONEY_OUT_OF_RANGE` | 422 | Do shared-kernel: sangria, suprimento ou pagamento `CASH` que tornaria o esperado ou um total do turno irrepresentável (18a) |

Valor ausente ou mal formado na rota reusa os códigos de `Money` do
shared-kernel (`INVALID_MONEY`), como na 1.3 #25.

---

## 6. API

```
POST /api/billing/cash-sessions                       ADMIN, FRONT_DESK  {openingFloat}                   201
GET  /api/billing/cash-sessions/current               ADMIN, FRONT_DESK  404 se não houver
GET  /api/billing/cash-sessions/{sessionId}           ADMIN, FRONT_DESK
POST /api/billing/cash-sessions/{sessionId}/drops     ADMIN, FRONT_DESK  Idempotency-Key {amount, reason}  201
POST /api/billing/cash-sessions/{sessionId}/supplies  ADMIN, FRONT_DESK  Idempotency-Key {amount, reason}  201
POST /api/billing/cash-sessions/{sessionId}/close     ADMIN, FRONT_DESK  {countedAmount, note?}            200
```

`WAITER` e `KITCHEN`: 403 (C5).

- **Resposta do turno:** `{id, status, openedAt, openedBy, openingFloat,
  movements: [{id, type, amount, reason, createdAt, createdBy}], totalDrops,
  totalSupplies, cashPaymentsTotal, cashPaymentCount, expectedAmount,
  countedAmount, difference, closedAt, closedBy, closingNote}`
- Turno `OPEN`: `cashPaymentsTotal` e `expectedAmount` vivos; turno `CLOSED`:
  os congelados (invariantes 17 e 18). `cashPaymentCount` é sempre a contagem
  viva dos pagamentos `CASH` `CONFIRMED` do turno
- **Fechamento cego** (C4): com o turno `OPEN`, `expectedAmount` e
  `cashPaymentsTotal` saem `null` para quem não é `ADMIN`
- Movimento devolve 201 com o turno; o replay devolve 201 com o turno
- `Idempotency-Key` é header opcional no binding, e a ausência é recusada pelo
  domínio (1.3 #19). Dinheiro vai em string decimal. Sem bean validation para o
  que o domínio recusa com código

---

## 7. Testes

Alvo até **1,3:1**. O excedente vai para valor esperado, diferença e a corrida
pagamento × fechamento. Enxuto em texto e validação (uma borda por limite).

**Unidade (agente de teste), densa:**
- Valor esperado: só fundo; fundo zero; + `CASH`; + suprimento − sangria;
  centavos (0,01); negativo com sangria acima do esperado
- Diferença: +0,01, −0,01 e 0; congelada depois do fechamento; fecha sem
  nota com diferença ≠ 0; nota acima de 500 recusada
- Transições: cada escrita em turno `CLOSED` recusada; fechar duas vezes
- Idempotência de movimento: replay, chave reusada com outro valor ou tipo,
  replay em turno fechado
- `CashDrawerAssignment`: a matriz de 4 casos + método não-`CASH` com controle
  ligado
- `Folio.receive`: replay antes de `CASH_DRAWER_SESSION_NOT_OPEN`; pagamento
  `CASH` carrega o id do turno; PIX nunca carrega
- Dono: quem abriu fecha; outro `FRONT_DESK` recusado; `ADMIN` fecha
- Fechamento cego: `revealsExpectedAmountTo`

**Integração (DEV), `app/src/test/.../billing/`:**
1. Fatia: abre → suprimento → `CASH` num folio `TAB` vinculado, PIX sem vínculo
   → sangria (com replay) → fecha com falta (nota de 501 recusada) → GET mostra os valores
   congelados; cego para a recepção com o turno aberto. 403 do `WAITER` e do
   `KITCHEN`
2. Controle ligado: `CASH` sem turno dá 409, PIX passa; retry do `CASH` depois
   do fechamento devolve o original
3. Estorno de `CASH` com turno aberto baixa o esperado; depois do fechamento,
   não mexe no congelado
4. **Concorrência:** aberturas simultâneas dão um 201 e o resto 409, nunca 500
5. **Concorrência:** fechamento junto com um `CASH`. Ou o pagamento entra no
   `expectedAmount` congelado, ou fica sem vínculo. Nunca fica vinculado a turno
   fechado sem ter sido contado
6. **Concorrência:** fechamento junto com o estorno de um `CASH` do turno: o
   pagamento é contado exatamente quando o estorno veio depois do fechamento
   (invariante 27)
7. Movimento que tiraria o esperado da faixa de `Money` é recusado com
   `MONEY_OUT_OF_RANGE`, e o turno continua legível e fechável (18a)

---

## 8. `.http`

`http/41-billing-cash-sessions.http`, no padrão do `40`.
- **Preparação:** login do admin, da recepção e do garçom. `GET /current`; se
  houver turno aberto, o admin fecha (aceita 200 ou 404). Abre um folio `TAB`
  pela rota de dev e lança "150.00". Chaves com sufixo aleatório
- **Caminho feliz:** abre com fundo "100.00"; suprimento "50.00"; paga "150.00"
  em `CASH` no folio e confere `cashPaymentsTotal`; sangria "200.00"; repete a
  sangria com a mesma chave (mesmo turno, uma sangria só); fecha contando
  "99.50" com nota; confere `expectedAmount` "100.00" e `difference` "-0.50"
- **Negativos:** um por código alcançável da §5, mais 401 sem token e 403 do
  garçom. `CASH_DRAWER_SESSION_NOT_OPEN` exige o controle ligado, que ainda não
  tem rota: fica no teste de integração 2
- **No fim, o turno fica fechado**, para o arquivo rodar de novo e para o `40`
  não vincular pagamentos a um turno esquecido
