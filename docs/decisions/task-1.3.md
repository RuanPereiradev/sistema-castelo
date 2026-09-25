# Decisões — Task 1.3 folio: lançamentos, estorno e pagamentos

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Base para a comanda fechar e receber (3.2). Roda em paralelo com a 1.4 e a 2.2.
**A 1.3 entra na `main` antes da 2.2**: a V6 precisa preceder a V7 no Flyway.

---

## Estado

| | |
|---|---|
| Branch | `task/1.3-billing-folio` |
| Rodada atual | 1 — DEV e TEST juntos, review mecânico em andamento |
| Build | `./mvnw clean verify` **verde** — 1229 testes, 0 falhas |
| Testes | 106 de unidade (billing) + 5 de integração, 2 deles de concorrência. Orçamento: 1.737 linhas de teste para 2.642 de produção (0,66:1) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Pagamento acima do saldo: folio `TAB` **recusa** (o operador registra o devido; troco é físico); folio `STAY` **aceita**, e o saldo negativo é crédito do hóspede (sinal e adiantamento) | implementado |
| 2 | 0 | Estorno de pagamento **existe**, só `ADMIN`, com motivo: o pagamento vai de `CONFIRMED` a `REFUNDED` e deixa de abater o saldo. Nome no código: `refund`, que entra no glossário | implementado |
| 3 | 0 | Perfis: ler, receber, estornar lançamento e fechar = `ADMIN` e `FRONT_DESK`; ajuste e estorno de pagamento = só `ADMIN`; `WAITER` e `KITCHEN` = nada nesta task (o garçom recebe pelo fechamento da comanda, 3.2) | implementado |
| 4 | 0 | Fechar exige saldo **exatamente zero**. Diferença se resolve com `AdjustmentCharge` do `ADMIN`, com motivo, antes de fechar | implementado |
| 5 | 0 | Idempotência pelo header `Idempotency-Key` gerado pelo front: retry idêntico devolve o original (antes de qualquer regra, inclusive folio fechado); mesma chave com dados diferentes = `IDEMPOTENCY_KEY_REUSED` | implementado |
| 6 | 0 | `ROOM_ACCOUNT` **recusado** no registro manual de pagamento. A ponte comanda → quarto (3.3) decide o uso | implementado |
| 7 | 0 | Estorno de lançamento: só diária e consumo, uma vez, pelo valor integral. Estorno parcial = ajuste; ajuste errado se corrige com outro ajuste | implementado |
| 8 | 0 | Fechar é sempre explícito. Quem fecha o folio da comanda é o fechamento da comanda (3.2), na mesma transação | implementado |
| 9 | 0 | **Acréscimo ao `billing/api` congelado na 0.6:** `ChargeRequest` ganha `ChargeSource(ChargeSourceType {ROOM_NIGHT, TAB}, UUID id)` — o billing precisa saber se é diária ou consumo e de onde veio. Nenhum módulo consome ainda | implementado |
| 10 | 0 | **Acréscimo ao `billing/api`:** `FolioView.reference` passa a `Optional<FolioReference>`, porque folio `TAB` não tem referência (a #12 da 0.6 proíbe componente nulo) | implementado |
| 11 | 0 | **Acréscimo ao `billing/api`:** `FolioFacade.close(FolioId)`, para a 3.2 e a 3.4 fecharem o folio na mesma transação | implementado |
| 12 | 0 | O `.http` abre folio e lança por uma **rota só do perfil `dev`** (precedente: `DevUserSeeder`), porque nenhuma rota real abre folio nesta task | implementado |
| 13 | 0 | V6 difere do desenho do schema: `folio.owner_id` único com o tipo; `CHECK` de referência em `STAY`; `charge` com auditoria no lugar de `posted_*` (#9 da 0.6), `reversal_of_charge_id` único, `amount <> 0`, motivo no estorno; `payment` com auditoria e `refunded_*`. O schema doc é atualizado antes da migration | implementado (DEV, aguarda Ruan) |
| 14 | 0 | `payment_intent`: só a tabela, sem código até a v1.1. `payment.cash_drawer_session_id`: coluna nula, sem FK nem mapeamento, até a 2.4 | implementado (DEV, aguarda Ruan) |
| 15 | 0 | Lock pessimista (`FOR UPDATE`) em toda escrita no folio, sem `@Version`. Contenção baixa: um folio é uma conta | implementado (DEV, aguarda Ruan) |
| 16 | 0 | Chave de idempotência global (`uk_payment_idempotency`); corrida entre folios diferentes com a mesma chave vira `IDEMPOTENCY_KEY_REUSED`, traduzida no `infra`, não 500. Replay responde 201 com o corpo original | implementado (DEV, aguarda Ruan) |
| 17 | 0 | `idx_folio_open_ref` **único**: dois folios `STAY` abertos com o mesmo código deixariam `findOpenStayFolioByCode` ambíguo (`FOLIO_REFERENCE_ALREADY_IN_USE`). Afeta o hotel (2.1/3.1): a sugestão é abrir com o localizador e trocar pelo quarto no check-in | implementado (DEV, aguarda Ruan) |
| 18 | 0 | Dono do tipo errado e `changeReference` em folio `TAB` = `IllegalArgumentException` (erro entre módulos). Segundo folio para o mesmo dono = 409, não devolve o existente | implementado (DEV, aguarda Ruan) |
| 19 | 0 | `Idempotency-Key` lido como header opcional e recusado pelo domínio, porque header obrigatório ausente dá 500 no handler global | implementado (DEV, aguarda Ruan) |
| 20 | 0 | `PaymentMethod`, `PaymentStatus`, `PaymentId`, `ChargeType` em `billing.domain`. Se a 3.2 precisar do método, ele sobe para o `api/` lá | implementado (DEV, aguarda Ruan) |
| 21 | 0 | `.http` numerado `40-billing-folios.http` (a faixa 30 é do restaurante). Orçamento de teste até 1,3:1, excedente em saldo, estorno, fechamento e concorrência | implementado (DEV, aguarda Ruan) |
| 22 | 1 | Ambiguidades levantadas pelo TEST: o replay idempotente mora **dentro de `Folio.receive`** e devolve a mesma instância antes de qualquer regra (inclusive folio fechado e saldo); chave igual com dados diferentes = `IDEMPOTENCY_KEY_REUSED`, também antes de `FOLIO_CLOSED`; replay de pagamento já `REFUNDED` devolve o próprio pagamento; a chave é aparada antes de comparar e gravar; `totalPayments()` soma só `CONFIRMED` e `balance() == totalCharges() − totalPayments()`; folio `TAB` pode ficar negativo por estorno ou ajuste (só o pagamento acima do saldo é recusado); `isReversible()` é falso depois do estorno; referência nula em `STAY` = `NullPointerException` | implementado (DEV, aguarda Ruan) |
| 23 | 1 | **Revê a #18**: dono do tipo errado e `changeReference` em folio `TAB` lançam `IllegalStateException`, não `IllegalArgumentException` — a regra C5 do ArchUnit proíbe `IllegalArgumentException` no domínio. Continua sendo erro de programação entre módulos, sem código de negócio | implementado (DEV, aguarda Ruan) |
| 24 | 1 | Decisões do DEV: ids guardados como `@Id UUID` e expostos como `FolioId`/`ChargeId`/`PaymentId` (os ids do `api/` não são `@Embeddable`); `save` com `persist`+`flush` para traduzir `uk_folio_owner`, `idx_folio_open_ref`, `uk_payment_idempotency` e `uk_charge_reversal` em código de domínio; consultas de conjunto com `flushMode=COMMIT`; lançamentos ordenados por `createdAt, id`; "já estornado" sem coluna nova (campo `@Transient reversedBy` preenchido pelo agregado); lock sai como `FOR NO KEY UPDATE` | implementado (DEV, aguarda Ruan) |
| 25 | 1 | Ordem das checagens: `reverse` = folio fechado → lançamento existe → já estornado → não estornável → motivo; `refund` = folio fechado → pagamento existe → já estornado → motivo; `receive` = chave válida → replay/chave reusada → folio fechado → método → valor → saldo. `method` ausente = 400; `amount` ausente = `INVALID_MONEY`; `GET /folios` sem `referenceCode` = 404 | implementado (DEV, aguarda Ruan) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Agregado `Folio` com `RoomNightCharge`, `TabCharge`, `AdjustmentCharge` e `Payment`
- [x] `FolioFacade` inteiro, com os acréscimos #9, #10, #11
- [x] Rotas REST da spec e rota de `dev` (#12)
- [x] `V6__billing.sql` e seção 7 do schema doc atualizada
- [x] `http/40-billing-folios.http`
- [x] Testes de unidade densos e os cinco testes de integração da spec

### Fora do escopo

- Caixa (2.4), `PaymentIntent` (v1.1), ligação comanda → folio (3.2), ponte para o quarto (3.3), check-out (3.4)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-1.3-billing-folio.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| `FOLIO_ALREADY_OPENED_FOR_OWNER` não tem cenário no `.http` | A rota de dev gera um dono novo a cada chamada; o código é coberto pelo serviço e pela tradução no infra | Coberto pelo `.http` da 3.2, que abre folio pela comanda |
| Na primeira subida em banco vazio, `SinglePropertyId` lê `property` antes do `DevUserSeeder` criar a linha e responde 500; na segunda subida funciona | Já acontecia antes desta task, fora do escopo | Ordenar o seed antes da leitura (task própria) |
| Busca de folio por id não filtra por propriedade | Propriedade única; mesmo padrão do restaurante | Multipropriedade |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
