# Decisões — Task 3.2 fechamento da comanda: taxa de serviço, divisão de conta, ligação com o folio

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Roda em paralelo com a 2.4 (caixa, V8) e a 3.5 (KDS, V10). **Ordem de merge:**
preparação do `billing.api` (PR #17) → 2.4 → 3.2 → 3.5. Spec:
`docs/task-3.2-tab-closing.md`.

Itens herdados que esta task resolve: 1.2 #8 (adicional segue o item na taxa),
1.3 #3 (garçom recebe pelo fechamento), 1.3 #8/#11 (fechar a comanda fecha o
folio na mesma transação), 1.3 #20 (`PaymentMethod` no `api/`, feito no PR #17),
2.2 #12 (colunas de fechamento na migration da 3.2, agora V9), 2.2 #16
(`serviceCharge()` e `total()`), limitação da 2.2 (teste de concorrência cancelar
comanda × lançar).

Numeração: G e F são as decisões aprovadas pelo Ruan no lote 2; D são decisões
do DEV nesta task, pendentes de revisão.

---

## Estado

| | |
|---|---|
| Branch | `task/3.2-tab-closing` (base `origin/task/billing-api-payment`) |
| Rodada atual | 1 — D20/D21 aplicadas; aguarda testes de unidade (agente de teste) e revisão do Ruan. Conflito esperado com a 3.5 no construtor do `TabService` (resolvido pelo orquestrador no rebase) |
| Build | `./mvnw -Dmaven.repo.local=<isolado> clean install` **verde** — 1405 testes, 0 falhas, ArchUnit incluído. `.http` 34 rodado duas vezes seguidas em banco isolado (63/63) e o 33 sem regressão (81/81) |
| Testes | 7 de integração (3 de fluxo, 4 de concorrência), 441 linhas, para ~1.700 de produção (com migration, javadoc e o `.http` fora da conta). Os de unidade do domínio vêm do agente de teste e completam o orçamento |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| G1 | 0 | Migration da 3.2 é a **`V9__tab_closing.sql`** (ex-V11); V8 = 2.4, V10 = 3.5, hotel em V11/V12. Aprovado pelo Ruan, 2026-09-26 | implementado |
| G2 | 0 | `PaymentMethod`/`PaymentId` no `billing.api` e `FolioFacade.receivePayment(FolioId, PaymentMethod, Money, String) → ReceivedPaymentView`, em PR de preparação (#17). A 3.2 não toca no `billing`. Aprovado pelo Ruan, 2026-09-26 | implementado (PR #17) |
| G3 | 0 | A migration semeia `restaurant.service_charge_percent = 10.00` nas `property` existentes; o `DevUserSeeder` (identity/infra) grava a chave ao criar a propriedade. Aprovado pelo Ruan, 2026-09-26 | implementado |
| G4 | 0 | O caixa do restaurante e do self-service opera com o perfil `WAITER`: fecha e recebe pela comanda. `FRONT_DESK` e `KITCHEN` recebem 403 nas rotas da 3.2. Aprovado pelo Ruan, 2026-09-26 | implementado |
| G5 | 0 | Glossário: `TabDestination` (`DIRECT_PAYMENT` · `ROOM_ACCOUNT`), `guestCount`, `splitGroup`, `serviceChargeWaived`, `reopen`, `TabBill`, `TabBilling`. Aprovado pelo Ruan, 2026-09-26 | implementado |
| F1 | 0 | O percentual congela no `startClosing` (pré-conta) | implementado |
| F2 | 0 | `WAITER` tira a taxa sem motivo, na comanda ou no item; a auditoria registra | implementado |
| F3 | 0 | Só se desliga taxa que nasceu ligada: nada de ligar em item inelegível ou em self-service | implementado |
| F4 | 0 | As primeiras cotas levam o centavo; taxa por grupo pelo maior resto, empate para o menor grupo; taxa calculada uma vez sobre a soma | implementado |
| F5 | 0 | Divisão igual e por item; linha inteira num grupo; grupo pode ser redividido igual; divisão igual sobre o total e sobre o saldo | implementado |
| F6 | 0 | Um único `TabCharge` por comanda; corrigidos o §11.1 e o §13 do schema | implementado |
| F7 | 0 | Sem "grupo já pagou" na v1 | implementado |
| F8 | 0 | `guestCount` opcional, a qualquer momento em `OPEN`/`CLOSING` | implementado |
| F9 | 0 | Fechar é explícito (`POST .../close`), com saldo zero | implementado |
| F10 | 0 | Reabrir `CLOSING → OPEN` com motivo obrigatório; estorna o `TabCharge`; pagamento vira crédito | implementado |
| F11 | 0 | `CLOSED` não reabre na v1 | implementado |
| F12 | 0 | Fecha com item ainda em preparo; o KDS segue avançando item em `CLOSING`/`CLOSED` | implementado (nada bloqueia) |
| F13 | 0 | Cancelar item em `CLOSING` continua recusado; reabrir primeiro | implementado (regra da 2.2) |
| F14 | 0 | Gorjeta além da taxa fora da v1 | implementado (fora) |
| F15 | 0 | Desconto = `AdjustmentCharge` do `ADMIN` na rota da 1.3; a taxa incide antes do desconto | implementado (fora) |
| C2 | 0 | (P16) A 3.2 herda a regra de `CASH` do `FolioService.receivePayment` da 2.4; não decide nada sobre caixa | implementado |
| D1 | 0 | ~~`TabResponse` e a lista trazem `serviceChargeRate`, `serviceCharge` e `total` **congelados**: `null` em `OPEN`. O número vivo em `OPEN` sai na pré-conta (`TabBillResponse`), que as rotas de taxa, grupo e pessoas devolvem. Motivo: o `TabController` (fora do escopo, disputado com a 3.5) monta o `TabResponse` sem o percentual atual, e a leitura da comanda não passa a depender do `Setting`~~ | **revertida pela D20** |
| D2 | 0 | ~~**Cancelar comanda reaberta** (com folio): `Tab.cancel(reason, TabBilling, by, at)` e `TabClosingService.cancel` implementam a invariante 17, mas a rota `POST /tabs/{id}/cancel` é do `TabService`/`TabController`, que esta task não pode editar. Até a ligação, o `cancel(reason, by, at)` da 2.2 **recusa** comanda com folio (`IllegalStateException`, 500) em vez de deixar um folio aberto órfão. A ligação é uma linha no `TabService.cancel`~~ | **revertida pela D21** |
| D3 | 0 | `reopen(reason, billing)` sem autor e instante: a V9 não tem colunas de reabertura, e o rastro é o estorno no folio (motivo, `created_by`, instante). `reopen` zera `tab_charge_id`, o percentual congelado e `closing_started_*` | implementado (DEV, aguarda Ruan) |
| D4 | 0 | "Saldo zero" no `close` e no `cancel` é decidido pelo `billing` pela porta (`closeFolio` recusa com `FOLIO_BALANCE_NOT_ZERO`), sem código duplicado no restaurante. O fake da porta no teste de unidade precisa reproduzir essa recusa | implementado (DEV, aguarda Ruan) |
| D5 | 0 | A porta `TabBilling` ganha `receivePayment` e `paidOn` além dos cinco métodos do rascunho: o pagamento passa pelo agregado (que checa `acceptsPayment`) e a pré-conta mostra o pago | implementado (DEV, aguarda Ruan) |
| D6 | 0 | Descrição do `TabCharge`: `"Tab card <n>"` ou `"Tab table <diningTableId>"`, montada pelo agregado, que não conhece o rótulo da mesa. A 3.3 revê quando o lançamento for para o folio do hóspede | implementado (DEV, aguarda Ruan) |
| D7 | 0 | `serviceChargeBase()` sem parâmetro (não depende do percentual); `serviceCharge`, `total`, `bill` e `evenSplit` recebem o `currentRate` obrigatório, e usam o congelado quando há | implementado (DEV, aguarda Ruan) |
| D8 | 0 | Grupo e `guestCount` em `CLOSED`/`CANCELLED`/`MERGED` respondem `TAB_NOT_OPEN` (o código "a comanda não aceita mais mudança" da 2.2 #16), sem código novo | implementado (DEV, aguarda Ruan) |
| D9 | 0 | `@DynamicUpdate` em `Tab` e `TabItem`: quem grava sob `FOR KEY SHARE` (grupo, taxa do item, pessoas, cancelamento de item, KDS da 3.5) só escreve as colunas que mudou. Sem isso, uma mudança de grupo lida antes de um cancelamento concorrente regravaria o `status` antigo do item | implementado (DEV, aguarda Ruan) |
| D10 | 0 | Pagamento responde `TabPaymentResponse {paymentId, method, amount, paidAt, bill}`: o replay devolve o pagamento original e a pré-conta atual | implementado (DEV, aguarda Ruan) |
| D11 | 0 | Divisão igual na pré-conta: `parts` explícito inválido = 422; sem `parts`, usa `guestCount` só quando a divisão é possível (senão `null`, a leitura não falha); `balanceEvenSplit` só com saldo positivo e divisão possível; `splitGroup` na consulta redivide o total do grupo (F5), e grupo sem item ativo = `INVALID_SPLIT_GROUP` | implementado (DEV, aguarda Ruan) |
| D12 | 0 | Grupos: tudo ou nada — primeiro todos os números, depois todos os itens, depois aplica. Item cancelado pode ser movido. Lista de grupos só com grupo que tem item ativo, em ordem crescente. Rotas sem bean validation para o que o domínio recusa | implementado (DEV, aguarda Ruan) |
| D13 | 0 | Taxa do item: status → item existe → item não elegível (422) → item cancelado (aceito sem mudar nada) → mesmo estado (aceito). `restoreServiceCharge` numa comanda de self-service deixa a taxa desligada | implementado (DEV, aguarda Ruan) |
| D14 | 0 | Testes de integração em classes novas (`TabClosingHttpIntegrationTest`, `TabClosingConcurrencyIntegrationTest`), sem tocar no `TabHttpIntegrationTest` da 2.2 | implementado |
| D15 | 0 | Adaptador da porta em `restaurant.infra`: `FolioFacadeTabBilling` sobre o `FolioFacade`; transação do chamador | implementado (DEV, aguarda Ruan) |
| D16 | 0 | Comanda com total zero (só possível com item por peso de centavo zero) no `startClosing` recebe `INVALID_CHARGE_AMOUNT` do billing: o folio não aceita lançamento zero. Aceito como limitação | implementado (DEV, aguarda Ruan) |

| D17 | 0 | O `DevUserSeeder` grava a chave a cada subida com `ON CONFLICT DO NOTHING`, não só quando cria a propriedade: cobre o banco `dev` em qualquer ordem de subida e nunca sobrescreve um valor mudado à mão | implementado (DEV, aguarda Ruan) |
| D18 | 0 | O grupo da pré-conta é o record aninhado `TabBill.SplitGroup`, e não o `SplitGroupBill` do rascunho: evita um nome novo fora do glossário (`TabBill` + `splitGroup` já aprovados) | implementado (DEV, aguarda Ruan) |
| D19 | 0 | `TabBilling.charge` recebe também o `TabId`, para o lançamento levar `ChargeSource.tab(id)` sem o adaptador ler o dono do folio (que na 3.3 será a reserva) | implementado (DEV, aguarda Ruan) |

| D20 | 1 | **Revê a D1** (orquestrador do lote 2, decisão de fronteira de arquivo): o front precisa do total ao vivo. O `TabController` passa o percentual atual (`TabClosingService.currentServiceChargeRate()`) ao `TabResponse` e à lista: `serviceChargeRate`, `serviceCharge` e `total` vêm ao vivo em `OPEN` e congelados depois. Toda leitura de comanda passa a ler o `Setting` | implementado |
| D21 | 1 | **Revê a D2** (orquestrador do lote 2): o `TabService` recebe `TabBilling` no construtor e o `cancel` chama `tab.cancel(reason, tabBilling, by, at)`. Comanda reaberta cancelada fecha o folio junto, ou recusa com `FOLIO_BALANCE_NOT_ZERO`. O `IllegalStateException` do `cancel` antigo saiu: nenhuma rota o alcança mais, e o método antigo fica para comanda sem folio (usado pelos testes de unidade da 2.2). O `TabClosingService.cancel` saiu, por ser duplicado | implementado |
| D22 | 1 | O suporte de teste (`AbstractIntegrationTest`) grava `restaurant.service_charge_percent` junto com a propriedade única, como a V9 faz com as propriedades existentes e o `DevUserSeeder` no `dev`: com a D20, toda leitura de comanda precisa da chave, inclusive nos testes da 2.2 | implementado |
| D23 | 1 | Maven rodado com repositório local isolado (fora do git), porque o `~/.m2` é compartilhado com as worktrees da 2.4 e da 3.5 | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Spec final e este registro antes do código
- [x] §11.1 e §13 do `docs/schema-banco-de-dados.md` (F6)
- [x] `V9__tab_closing.sql` com seed do `Setting` (G1, G3)
- [x] `DevUserSeeder` grava `restaurant.service_charge_percent` ao criar a propriedade (G3)
- [x] `TabStatus`, `TabDestination`, `TabItem` e `Tab` com o bloco de fechamento
- [x] Taxa de serviço: comanda e item, calculada sobre a soma, congelada no `startClosing` (F1–F3)
- [x] Divisão igual e por item, rateio da taxa, `guestCount` (F4, F5, F8)
- [x] `startClosing`, pagamento, `reopen`, `close` (F9–F12)
- [x] Cancelar comanda com folio (invariante 17), pela rota da 2.2 (D21)
- [x] Porta `TabBilling` e adaptador sobre o `FolioFacade`
- [x] `TabClosingService` e `TabClosingController`, DTOs
- [x] `http/34-restaurant-tab-closing.http` e linha no `http/README.md`
- [x] Integração: fatia, reabertura, 403 e os quatro cenários de concorrência
- [x] `./mvnw clean install` verde, ArchUnit incluído

### Fora do escopo

- `ROOM_ACCOUNT` e ponte para o quarto (3.3); KDS (3.5); transferência e junção (3.6); nota fiscal; `PaymentIntent`; caixa (2.4); desconto pelo garçom; gorjeta; reabrir `CLOSED`
- Testes de unidade do domínio: agente de teste separado, a partir da spec

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Cenário `CASH` no `.http` e na integração | depois do rebase sobre a 2.4 | a regra do turno de caixa entra com a 2.4 | orquestrador do lote 2, na instrução da task |

---

## Contrato com o front

Especificado em `docs/task-3.2-tab-closing.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| `FOLIO_ALREADY_OPENED_FOR_OWNER` continua sem cenário no `.http` (herdada da 1.3) | Com o folio reaproveitado, a rota da comanda nunca produz esse erro | Nenhuma: o código é coberto pelo teste da fachada |
| Reabrir com pagamento e depois cancelar itens pode deixar o folio com crédito maior que o novo total: o saldo fica negativo e a comanda não fecha | Consequência de F10 com 1.3 #1/#22 | `ADMIN` estorna o pagamento (`refund`) na rota da 1.3 |
| Total zero no `startClosing` = `INVALID_CHARGE_AMOUNT` (D16) | Caso só possível com item por peso de centavo zero | Se aparecer, decidir se comanda zero fecha sem lançamento |
| `guestCount` acima de 99 não gera divisão padrão (D11) | O limite de partes é 99 | Front informa `parts` |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
