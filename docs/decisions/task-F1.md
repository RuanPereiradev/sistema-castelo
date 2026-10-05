# Decisões — Task F1 Finanças: saída de dinheiro

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/F1-expense` |
| Rodada atual | 1 (implementação) |
| Build | `./mvnw clean install` passa: **BUILD SUCCESS, 6min07, zero falhas** |
| Testes | 28 de unidade no `finance` + 10 de integração no `app`; os 64 do `CashDrawerSessionTest` da 2.4 seguem verdes |

**Código da task:** `F1`. Não cabe na numeração das ondas do `plano-tecnico.md` — é
escopo que o Ruan acrescentou em 2026-10-03, e o plano não previa finanças (a única menção
a relatório é a 5.1, "relatórios operacionais"). `F2` é o resumo financeiro, que depende
desta ter dados.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **D1 — Duas datas na saída.** Cada despesa guarda `accrualDate` (a que mês pertence) e `paidAt` (quando o dinheiro saiu, nulo enquanto é conta a pagar). O Ruan pediu **lucro/prejuízo** e **fluxo de caixa**, que são números diferentes: o aluguel pago dia 5 é despesa do mês inteiro e caixa do dia 5; o almoço lançado no quarto dia 20 e pago no check-out dia 25 é receita do dia 20 e caixa do dia 25. Uma data só deixaria uma das duas perguntas sem resposta para sempre, e consertar depois custa migration mais recálculo de histórico. Habilita também **contas a pagar** de graça: despesa com `paidAt` nulo. | implementado |
| 2 | 0 | **D2 — Módulo próprio `finance`**, dono da saída de dinheiro e do resumo, lendo `billing.api` e uma **porta de leitura nova em `restaurant.api`** para o fluxo de clientes. Alternativas recusadas: dentro do `billing`, que não pode ver o `restaurant` e deixaria `guest_count` inalcançável (sobraria contar folio de comanda, que é número de comandas e não de pessoas); e um `finance` lendo as tabelas alheias por SQL, que acopla à estrutura de tabela em vez da API e o **ArchUnit não enxerga**, porque confere pacote Java e não SQL. Exige: módulo novo no `pom`, o grafo do `CLAUDE.md` e o `ALLOWED_MODULE_GRAPH` do ArchUnit atualizados, e a primeira porta de leitura do `restaurant` — que passa a ser contrato. | implementado |
| 3 | 0 | **D3 — `ExpenseCategory` é enum fixo** de oito valores: `PAYROLL` · `SUPPLIER` · `RENT` · `UTILITIES` · `TAX` · `MAINTENANCE` · `WITHDRAWAL` · `OTHER`. Persistido como string, igual aos doze enums que o sistema já tem. Recusada a tabela `expense_category`: o resumo precisa de conta fixa por categoria ("a folha consumiu 31% da receita"), e com tabela isso exigiria uma coluna marcando qual linha é folha — o modelo ficaria meio enum, meio tabela. Custo aceito: categoria nova depois exige migration alterando o `CHECK`. | implementado |
| 4 | 0 | **D4 — Despesa paga em dinheiro vivo sai da gaveta**, como movimento de caixa do turno. Sem isso o `expectedAmount` (fundo + suprimentos − sangrias + pagamentos) ignoraria a saída e o **fechamento cego acusaria falta** — e falta no caixa parece roubo, não contabilidade. Implica **mudar o `billing`, que já está na `main`**: valor novo em `CashMovementType`, migration alterando o `CHECK` de `cash_movement.movement_type`, método de negócio novo em `CashDrawerSession` e porta em `billing.api` para o `finance` chamar. O log da 2.4 ganha uma linha. Recusadas: sangria manual antes de pagar (dois lançamentos sem ligação entre si, ninguém prova que um virou o outro) e proibir `CASH` em despesa (se na prática se paga o entregador em dinheiro, o número mente). | implementado |

| 5 | 0 | **D5 — Nomes aprovados** para o glossário: `Expense` (tabela `expense`), `ExpenseCategory`, `accrualDate`, `dueDate`, `payables`, `supplierName`, `CashMovementType.EXPENSE_PAYMENT` e `RestaurantActivity` (a porta nova em `restaurant.api`). **O `DailyClose` desta lista foi revertido pela D7 da F2**: virou `ShiftResult`, porque o gatilho passou a ser o fechamento do turno e podem existir dois num dia. Técnicos, fora do glossário: `ExpenseService`, `ExpenseController`, `ExpenseRepository` e as exceções. O `EXPENSE_PAYMENT` é o sensível: entra num enum que já está na `main`, então renomear depois é migration nova. | implementado |
| 6 | 0 | **D6 — A F1 toma a `V12`**, que estava reservada ao hotel, e o hotel desce para **V13 (1.1) e V14 (2.1)**. Mesma razão da T11b da 3.6: com o `outOfOrder` desligado, a versão segue a ordem de execução, e o Ruan quer finanças na primeira entrega enquanto o hotel segue adiado. Uma migration só para a task, criando `expense` e alterando o `CHECK` de `cash_movement.movement_type`. *Decisão minha, decorrente da D4 — revisável sem ônus enquanto o hotel não abrir.* | implementado |

| 7 | 1 | **D7 — Finanças é do restaurante; o hotel entra como uma fonte a mais.** Diretriz do Ruan em 2026-10-03: o restaurante é o produto, e o hotel vai ser "só uma entrada de dinheiro e despesa a mais". Consequência de desenho, não só de prioridade: a receita é modelada **por fonte**, numa lista, e não com `TabCharge` e `RoomNightCharge` costurados no meio do cálculo. Acrescentar o hotel na F2 passa a ser acrescentar uma fonte, não mexer no resumo. A F2 **não espera** o hotel: ela nasce respondendo pelo restaurante, com a fonte do hotel somando zero até existir. | implementado |
| 8 | 1 | **D8 — `RestaurantActivity`, a primeira porta de leitura do restaurante.** Responde **atividade**, não dinheiro: comandas abertas, quantas informaram o número de pessoas, a soma dessas pessoas, e a divisão entre mesa e cartão. O dinheiro continua sendo do `billing`, e quem junta os dois é o `finance`. Estreita de propósito: um módulo de relatório que pudesse perguntar qualquer coisa ao restaurante acabaria acoplado à forma da comanda. Comanda `CANCELLED` e `MERGED` ficam fora — a aberta por engano e a absorvida por outra não serviram ninguém, e contá-las infla o movimento de clientes. | implementado |
| 9 | 1 | **D9 — O número de pessoas é parcial, e o tipo diz isso.** `guest_count` é opcional na comanda, então `RestaurantActivityView` carrega também `tabsWithGuestCount`, e expõe `guestCountCoverage()` e `guestCountIsComplete()`. Quem mostra o número de pessoas **tem de** mostrar a cobertura ao lado: "412 pessoas em 180 de 213 comandas" é honesto, "412 pessoas" não é. A honestidade fica no tipo, não na boa vontade de quem for consumir. | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Módulo `finance` no `pom`, no grafo do `CLAUDE.md` e no `ALLOWED_MODULE_GRAPH` do ArchUnit
- [x] Agregado `Expense` com as duas datas, cancelamento com motivo e quitação
- [x] `ExpenseCategory` com os oito valores (D3)
- [x] `V12__finance_expense.sql`: cria `expense` e altera o `CHECK` do `cash_movement` (D6)
- [x] Renumerar o hotel no `docs/MIGRATIONS.md`: 1.1 → V13, 2.1 → V14 (D6)
- [x] `CashMovementType` com o valor novo + migration alterando o `CHECK` (D4)
- [x] Método de negócio em `CashDrawerSession` e porta em `billing.api` (D4)
- [x] Rotas de despesa e de contas a pagar, perfil `ADMIN`
- [x] Arquivo `.http` com caminho feliz e cenários negativos
- [x] Linha no `http/README.md`
- [x] Glossário do `CLAUDE.md` com os termos aprovados
- [x] Linha no log de decisões da 2.4, registrando a mudança no caixa
- [x] `RestaurantActivity` e `RestaurantActivityView` em `restaurant.api`, com a consulta de atividade (D8, D9)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Resumo financeiro, fechamento do dia, projeção de caixa | F2 | Depende da F1 ter dados; junto passaria de mil linhas e não caberia em três rodadas de review | eu propus, o Ruan aceitou a divisão |
| Custo de produto (CMV) e margem por prato | task própria | `menu_item` não tem campo de custo; pôr custo muda o cardápio | — |
| Folha de pagamento com encargo, férias, 13º | fora do v1 | Outro produto; aqui funcionário é categoria de despesa | — |
| Cadastro de fornecedor, conciliação bancária, nota de entrada, centro de custo | fora do v1 | — | — |

---

## Pontos em aberto

| # | Pergunta | Quando bloqueia |
|---|---|---|
| — | Nenhum. A A2 (dia congelado ou ao vivo) foi respondida pelo Ruan em 2026-10-03 e virou a **D1 da F2**: congela o dia | — |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita |
|---|---|
| Não existe margem por prato | `menu_item` não tem custo; o sistema dirá "lucro do mês", não "a pizza dá 60%" |
| Fluxo de clientes por pessoa é parcial | `guest_count` é **opcional** na comanda. Mitigado na D9: o tipo carrega a cobertura, então o consumidor não consegue mostrar o número sozinho por descuido |
| Receita de hotel é zero | O módulo `hotel` não existe |
| Retirada dos sócios não conta como custo | `WITHDRAWAL` move caixa e não pesa no resultado: contar como despesa reportaria prejuízo num mês lucrativo em que os sócios só tiraram o próprio dinheiro. *Decisão minha, não pedida pelo Ruan — uma linha para reverter* |
| Categoria nova exige migration | Consequência aceita da D3 |
