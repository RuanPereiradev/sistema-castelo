# Decisões — Task 2.4 caixa: abertura de turno, sangria, suprimento e fechamento

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Parte do lote 2 (2.4, 3.2, 3.5). Ordem de merge aprovada (G2): preparação do
`billing.api` (PR #17) → **2.4** → 3.2 → 3.5. A V8 precisa entrar na `main` antes
da V9 (3.2) e da V10 (3.5).

---

## Estado

| | |
|---|---|
| Branch | `task/2.4-cash-drawer` (a partir de `task/billing-api-payment`) |
| Rodada atual | 2 — decisão do Ruan sobre a nota do fechamento cego aplicada (#22, #23); aguarda revisão do Ruan. Entra na `main` depois do PR #17 e antes da 3.2 |
| Build | `./mvnw clean install` **verde**, ArchUnit incluído (256 testes no `app`) |
| Testes | 71 de unidade (agente de teste) + 8 de integração no `app` (5 de fatia, 3 de concorrência). Os testes das corridas fechamento × `CASH` e estorno × fechamento foram provados contra a trava fraca ou ausente: falham sem ela. Rodada 0: 517 linhas de integração para 1.649 de produção. O teste da corrida fechamento × `CASH` foi provado contra a trava fraca: com `FOR NO KEY UPDATE` no fechamento, falha. Unidade do domínio: com o agente de teste, que tem até ~1.600 linhas de orçamento (1,3:1) |

---

## Decisões confirmadas

Decisões G e C aprovadas pelo Ruan em 2026-09-26 ("aceito todas"), a partir do
rascunho da spec. Valem sobre o rascunho quando divergem (C5 e C7).

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **G1.** V8 = 2.4 (`V8__cash.sql`); V9 = 3.2; V10 = 3.5; hotel em V11/V12. Com `outOfOrder` desligado, a versão segue a ordem de execução | implementado |
| 2 | 0 | **G2.** `PaymentMethod` e `PaymentId` em `billing.api`; `FolioFacade.receivePayment` delega a `FolioService.registerPayment`, o caminho único de todo pagamento (PR #17, antes desta task). A 2.4 põe o vínculo com o caixa **dentro** desse caminho, sem renomear nada | implementado |
| 3 | 0 | **G3.** Seed de `Setting`: a V8 semeia `billing.cash-drawer.required = false` para as `property` existentes; o `DevUserSeeder` (identity/infra) semeia a chave ao criar a propriedade. Aprovado tocar o `DevUserSeeder` | implementado |
| 4 | 0 | **G4.** O caixa do restaurante e do self-service opera com `WAITER` pela comanda (3.2). Turno de caixa aberto e fechado por `FRONT_DESK` ou `ADMIN` | implementado |
| 5 | 0 | **G5.** Glossário: `CashDrawerSessionStatus` (`OPEN` · `CLOSED`) | implementado |
| 6 | 0 | **C1.** Um turno aberto por propriedade (`idx_cash_session_open`); sem entidade `CashDrawer` física | implementado |
| 7 | 0 | **C2.** `billing.cash-drawer.required` (BOOLEAN), padrão `false`. Desligado: `CASH` se vincula ao turno aberto se houver, senão fica sem vínculo. Ligado: `CASH` sem turno aberto = `CASH_DRAWER_SESSION_NOT_OPEN` | implementado |
| 8 | 0 | **C3.** Fundo de troco e contagem são colunas do turno; `cash_movement` só tem `CASH_DROP` e `CASH_SUPPLY`. `OPENING_FLOAT` e `CLOSING_COUNT` saem do glossário | implementado |
| 9 | 0 | **C4.** Fechamento cego: com o turno `OPEN`, `expectedAmount` e `cashPaymentsTotal` saem `null` para quem não é `ADMIN` | implementado |
| 10 | 0 | **C5.** Abrir, sangria, suprimento e ler: `ADMIN` e `FRONT_DESK`. Fechar: quem abriu ou `ADMIN`. `WAITER` e `KITCHEN`: 403. A sangria **não** fica restrita ao `ADMIN` | implementado |
| 11 | 0 | **C6.** Quebra de caixa não bloqueia o fechamento; a diferença fica congelada; diferença ≠ 0 exige justificativa; sem tolerância | **parte "diferença ≠ 0 exige justificativa" revertida pela #22**; o resto segue implementado |
| 12 | 0 | **C7.** Sangria acima do esperado é **aceita** (não existe `CASH_DROP_EXCEEDS_DRAWER_BALANCE`); a quebra aparece no fechamento, coerente com o fechamento cego | implementado |
| 13 | 0 | **C8.** Estorno de `CASH` com o turno já fechado não muda o turno fechado; a saída física é sangria no turno atual; limitação aceita | implementado |
| 14 | 0 | **C9.** Sangria e suprimento com `Idempotency-Key`, mesmas regras da 1.3 #5 | implementado |
| 15 | 0 | **C10.** Turno nunca fecha sozinho e pode atravessar o dia | implementado |
| 16 | 0 | Decisões do DEV (sem regra de negócio nova): `drop`/`supply` não recebem o autor nem os pagamentos — o autor do movimento é o `created_by` da auditoria, e sem a C7 a sangria não precisa do esperado; `expectedAmount(cashPayments)` com o turno `CLOSED` devolve o valor congelado; `cashPaymentsTotal` do turno fechado é derivado do congelado (esperado − fundo − suprimentos + sangrias), e `cashPaymentCount` é sempre a contagem viva; o turno aberto é travado `FOR KEY SHARE` e lido em **todo** recebimento (não só em `CASH`), para o caso de uso não ter `if` de método; o fechamento trava com `FOR UPDATE` nativo, porque o `PESSIMISTIC_WRITE` do Hibernate sai `FOR NO KEY UPDATE` e não conflita com `KEY SHARE`; o estorno de um pagamento com turno trava esse turno `FOR KEY SHARE`, aberto ou fechado; `ADMIN` é lido na rota por `HttpServletRequest.isUserInRole` | implementado (DEV, aguarda Ruan) |

| 17 | 0 | Decisões do DEV na integração: `AbstractIntegrationTest` semeia `billing.cash-drawer.required = false` junto com a propriedade de teste, porque a V8 roda antes dela e todo pagamento lê a chave; o `.http` roda do zero e de novo (verificado duas vezes numa instância isolada, junto com o `40`); a semeadura da V8 numa base que já tem propriedade foi verificada aplicando V1–V8 à mão | implementado (DEV, aguarda Ruan) |
| 18 | 1 | Review, **bloqueante corrigido**: um suprimento perto do limite de `Money` gravava, e daí o esperado estourava em todo `close` e no GET do `ADMIN`, deixando o turno impossível de fechar e a propriedade sem poder abrir outro (C1). (a) Os movimentos são somados pelo `signedAmount`, então um movimento e o seu oposto se anulam sem passar pelo limite; (b) `drop`/`supply` calculam o esperado dentro da transação, antes do commit (padrão `saveReadable` da 1.3 #27): um movimento que tornaria o esperado irrepresentável é recusado com `MONEY_OUT_OF_RANGE` e nada grava. (c) Mesma classe de falha por outro caminho, fechada pelo DEV: um pagamento `CASH` num folio `STAY` aceita valor acima do saldo (1.3 #1), então um valor digitado errado também estouraria o esperado; `registerPayment` lê o esperado do turno do pagamento antes do commit e recusa com o mesmo código. Spec: invariante 9 (soma com sinal), 18a (turno sempre legível) e `MONEY_OUT_OF_RANGE` na §5 | implementado |
| 19 | 1 | Review: teste de concorrência estorno × fechamento acrescentado; sem o `lockForKeyShare` do estorno, ele falha | implementado |
| 20 | 1 | Review: **recusada** a sugestão de tratar `billing.cash-drawer.required` ausente como `false`. Sem valor padrão escondido, consistente com `Settings`: a chave ausente continua `SETTING_NOT_FOUND` | recusada |
| 21 | 1 | Os testes de unidade do agente de teste (`task/2.4-cash-drawer-tests`, 71 testes) entram na branch. A regra da nota no fechamento cego **não** muda nesta rodada: há pergunta aberta ao Ruan | implementado |
| 22 | 2 | **Decisão do Ruan (2026-09-27): "Fecha sempre, sem exigir nota".** O fechamento é sempre aceito e a nota (`closingNote`) é opcional para todos, com diferença zero ou não. A diferença continua congelada e o `ADMIN` confere depois. Motivo: exigir nota só quando a contagem difere do esperado vaza o esperado no fechamento cego (C4), porque a recepção testaria valores até o sistema aceitar sem nota. Continua o limite de 500 caracteres; nota em branco vira ausente | implementado |
| 24 | — | **Alterada por outra task (F1, 2026-10-03).** O `CashMovementType` ganhou o valor `EXPENSE_PAYMENT`: despesa paga em dinheiro vivo sai da gaveta e entra na conta do `expectedAmount`, senão o fechamento cego acusaria falta por dinheiro que o operador pagou de propósito — e falta no caixa parece roubo. Veio com `CashDrawerSession.payExpense()`, `totalExpensePayments()`, a porta `CashDrawerFacade` em `billing.api`, e a `V12` alterando o `CHECK` de `cash_movement.movement_type`. **Nada da 2.4 mudou de comportamento:** o `expectedAmount` já somava por `signedAmount`, então o valor novo entrou sem caso especial, e os 64 testes do `CashDrawerSessionTest` seguem verdes. Decisão D4 em `docs/decisions/task-F1.md` | implementado |
| 23 | 2 | Decisão do DEV (pedida pelo orquestrador): o código `CASH_CLOSING_NOTE_REQUIRED` passa a `INVALID_CASH_CLOSING_NOTE` (exceção `InvalidCashClosingNoteException`), porque a nota não é mais obrigatória e o único caso que resta é a nota acima de 500 caracteres. Nenhum front consome o código ainda (tela na 4.6) | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

- [x] Spec `docs/task-2.4-cash-drawer.md` e §12 de `docs/schema-banco-de-dados.md` atualizados antes da migration
- [x] `V8__cash.sql`: `cash_drawer_session`, `cash_movement`, FK/`CHECK`/índice em `payment`, seed da configuração
- [x] Agregado `CashDrawerSession` com `CashMovement` append-only; `CashDrawerSessionStatus`, `CashMovementType`
- [x] Abrir turno com `openingFloat`, um aberto por propriedade (inclusive em corrida)
- [x] Sangria e suprimento com valor, motivo e `Idempotency-Key`
- [x] Fechar com contagem: congela `expectedAmount`, `difference()` derivada, justificativa com diferença ≠ 0, dono ou `ADMIN`
- [x] Vínculo do pagamento `CASH` ao turno aberto dentro de `FolioService.registerPayment` (`CashDrawerAssignment`), com `billing.cash-drawer.required`
- [x] `Payment` mapeia `cash_drawer_session_id`; `PaymentMethod.goesToCashDrawer()`
- [x] Concorrência: fechamento × pagamento `CASH` e × estorno (`FOR UPDATE` × `FOR KEY SHARE`)
- [x] Rotas REST do caixa, fechamento cego
- [x] `DevUserSeeder` semeia a configuração (G3)
- [x] `http/41-billing-cash-sessions.http` e a linha no `http/README.md`
- [x] Testes de integração no `app`: fatia, controle ligado, estorno, corrida de abertura, corrida fechamento × `CASH`

### Fora do escopo

- Entidade `CashDrawer` física e vários caixas simultâneos (C1; schema §14)
- Relatório de turno com PIX e cartão, histórico e busca de turnos: 5.1
- Recebimento pelo garçom no fechamento da comanda: 3.2, que herda o vínculo pelo `FolioFacade.receivePayment`
- Reabrir turno fechado; estornar movimento (corrige-se com o oposto)
- Tela de caixa: 4.6. Edição da configuração pelo `ADMIN`: 4.7
- Testes de unidade do domínio: agente de teste separado

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-2.4-cash-drawer.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Estorno de `CASH` de turno já fechado não mexe no turno; a saída se registra como sangria no turno atual | C8 | Movimento próprio de devolução, se o cliente pedir |
| `CASH_DRAWER_SESSION_NOT_OPEN` não tem cenário no `.http`: ligar o controle não tem rota até a 4.7 | Coberto pelo teste de integração | `.http` da 4.7 |
| `cashPaymentCount` de um turno fechado é a contagem viva e pode divergir do total congelado depois de um estorno | O total congelado é o que vale para a conferência; a contagem é informativa | Relatório de turno (5.1) |
| O 403 do `KITCHEN` não está no `.http` (não há usuário de cozinha no `http-client.env.json`) | Coberto pelo teste de integração | Quando o ambiente ganhar a chave |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| | | |
