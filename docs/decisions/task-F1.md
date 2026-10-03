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
| Rodada atual | 0 (decisões de negócio; nenhuma linha de código) |
| Build | não rodado |
| Testes | 0 |

**Código da task:** `F1`. Não cabe na numeração das ondas do `plano-tecnico.md` — é
escopo que o Ruan acrescentou em 2026-10-03, e o plano não previa finanças (a única menção
a relatório é a 5.1, "relatórios operacionais"). `F2` é o resumo financeiro, que depende
desta ter dados.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **D1 — Duas datas na saída.** Cada despesa guarda `accrualDate` (a que mês pertence) e `paidAt` (quando o dinheiro saiu, nulo enquanto é conta a pagar). O Ruan pediu **lucro/prejuízo** e **fluxo de caixa**, que são números diferentes: o aluguel pago dia 5 é despesa do mês inteiro e caixa do dia 5; o almoço lançado no quarto dia 20 e pago no check-out dia 25 é receita do dia 20 e caixa do dia 25. Uma data só deixaria uma das duas perguntas sem resposta para sempre, e consertar depois custa migration mais recálculo de histórico. Habilita também **contas a pagar** de graça: despesa com `paidAt` nulo. | pendente |
| 2 | 0 | **D2 — Módulo próprio `finance`**, dono da saída de dinheiro e do resumo, lendo `billing.api` e uma **porta de leitura nova em `restaurant.api`** para o fluxo de clientes. Alternativas recusadas: dentro do `billing`, que não pode ver o `restaurant` e deixaria `guest_count` inalcançável (sobraria contar folio de comanda, que é número de comandas e não de pessoas); e um `finance` lendo as tabelas alheias por SQL, que acopla à estrutura de tabela em vez da API e o **ArchUnit não enxerga**, porque confere pacote Java e não SQL. Exige: módulo novo no `pom`, o grafo do `CLAUDE.md` e o `ALLOWED_MODULE_GRAPH` do ArchUnit atualizados, e a primeira porta de leitura do `restaurant` — que passa a ser contrato. | pendente |
| 3 | 0 | **D3 — `ExpenseCategory` é enum fixo** de oito valores: `PAYROLL` · `SUPPLIER` · `RENT` · `UTILITIES` · `TAX` · `MAINTENANCE` · `WITHDRAWAL` · `OTHER`. Persistido como string, igual aos doze enums que o sistema já tem. Recusada a tabela `expense_category`: o resumo precisa de conta fixa por categoria ("a folha consumiu 31% da receita"), e com tabela isso exigiria uma coluna marcando qual linha é folha — o modelo ficaria meio enum, meio tabela. Custo aceito: categoria nova depois exige migration alterando o `CHECK`. | pendente |
| 4 | 0 | **D4 — Despesa paga em dinheiro vivo sai da gaveta**, como movimento de caixa do turno. Sem isso o `expectedAmount` (fundo + suprimentos − sangrias + pagamentos) ignoraria a saída e o **fechamento cego acusaria falta** — e falta no caixa parece roubo, não contabilidade. Implica **mudar o `billing`, que já está na `main`**: valor novo em `CashMovementType`, migration alterando o `CHECK` de `cash_movement.movement_type`, método de negócio novo em `CashDrawerSession` e porta em `billing.api` para o `finance` chamar. O log da 2.4 ganha uma linha. Recusadas: sangria manual antes de pagar (dois lançamentos sem ligação entre si, ninguém prova que um virou o outro) e proibir `CASH` em despesa (se na prática se paga o entregador em dinheiro, o número mente). | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] Módulo `finance` no `pom`, no grafo do `CLAUDE.md` e no `ALLOWED_MODULE_GRAPH` do ArchUnit
- [ ] Agregado `Expense` com as duas datas, cancelamento com motivo e quitação
- [ ] `ExpenseCategory` com os oito valores (D3)
- [ ] Migration do `expense`
- [ ] `CashMovementType` com o valor novo + migration alterando o `CHECK` (D4)
- [ ] Método de negócio em `CashDrawerSession` e porta em `billing.api` (D4)
- [ ] Rotas de despesa e de contas a pagar, perfil `ADMIN`
- [ ] Arquivo `.http` com caminho feliz e cenários negativos
- [ ] Linha no `http/README.md`
- [ ] Glossário do `CLAUDE.md` com os termos aprovados
- [ ] Linha no log de decisões da 2.4, registrando a mudança no caixa

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
| A1 | Nome do valor novo de `CashMovementType` e os nomes do glossário em geral | Antes da primeira linha de código |
| A2 | O dia é congelado num `DailyClose` ou recalculado ao vivo sempre? | Só na **F2** |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita |
|---|---|
| Não existe margem por prato | `menu_item` não tem custo; o sistema dirá "lucro do mês", não "a pizza dá 60%" |
| Fluxo de clientes por pessoa é parcial | `guest_count` é **opcional** na comanda; o resumo tem de dizer "412 pessoas em 180 de 213 comandas informadas" em vez de fingir precisão |
| Receita de hotel é zero | O módulo `hotel` não existe |
| Categoria nova exige migration | Consequência aceita da D3 |
