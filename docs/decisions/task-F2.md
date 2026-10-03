# Decisões — Task F2 Finanças: resumo financeiro

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/F2-financial-summary` (empilhada na `task/F1-expense`) |
| Rodada atual | 0 (decisões; nenhuma linha de código) |
| Build | não rodado |
| Testes | 0 |

**Depende da F1**, que entrega a saída de dinheiro e a porta `RestaurantActivity`. Esta task
é o resumo: o que entrou, o que saiu, se deu lucro, como está o caixa e se o movimento de
clientes subiu ou caiu — **sem IA**, só agregação e comparação.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **D1 — O dia é congelado.** No fechamento o sistema grava o resultado do dia: receita por fonte, despesa por categoria, pessoas atendidas, formas de pagamento. Estorno lançado depois aparece como **ajuste no dia em que foi feito**, não volta no passado. Sem isso, o resultado de terça muda quando um estorno entra na quinta, e ninguém consegue explicar por que o número que imprimiu virou outro. Recusado o cálculo ao vivo, que seria bem menor de código e deixaria todo número histórico instável. | pendente |
| 2 | 0 | **Herdada da F1 (D7) — receita por fonte.** O restaurante é a fonte real; o hotel entra como **uma linha a mais** quando existir, somando zero até lá. Nada de `TabCharge` e `RoomNightCharge` costurados no meio do cálculo: acrescentar o hotel é acrescentar uma fonte. | pendente |
| 3 | 0 | **Herdada da F1 (D1) — dois regimes, sempre os dois.** O resultado conta despesa por **competência** (a que mês pertence) e o fluxo de caixa conta por **pagamento** (quando o dinheiro saiu). Toda tela do resumo diz qual dos dois está mostrando; um número sem o regime ao lado é um número que mente metade das vezes. | pendente |
| 4 | 0 | **Herdada da F1 (D9) — o número de pessoas nunca aparece sozinho.** `guest_count` é opcional na comanda, então o resumo mostra a cobertura ao lado: "412 pessoas em 180 de 213 comandas". A `RestaurantActivityView` já carrega o dado, então não depende de boa vontade. | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] `DailyClose`: o resultado do dia congelado, com fonte de receita e categoria de despesa
- [ ] Migration do fechamento
- [ ] O gatilho do fechamento — ver ponto em aberto A1
- [ ] `FinancialSummary`: resultado do período, comparação com o anterior, e as frases determinísticas
- [ ] `CashFlow`: entrada, saída e projeção pelo vencimento das contas a pagar
- [ ] `GuestFlow`: movimento de clientes e ticket médio, com a cobertura
- [ ] Rotas do resumo, perfil `ADMIN`
- [ ] Arquivo `.http` com caminho feliz e cenários negativos
- [ ] Linha no `http/README.md`
- [ ] Glossário do `CLAUDE.md`

---

## Pontos em aberto

| # | Pergunta | Quando bloqueia |
|---|---|---|
| A1 | **Quem dispara o fechamento do dia?** Automático por horário (e qual), ou amarrado ao fechamento do turno de caixa, ou botão do `ADMIN`? O restaurante vira a noite, então "meia-noite" pode cortar o movimento no meio | Antes do `DailyClose` |

---

## Limitações conhecidas e aceitas

| Limitação | Por quê |
|---|---|
| Não existe margem por prato nem CMV | `menu_item` não tem custo (herdado da F1) |
| Receita de hotel é zero | O módulo `hotel` não existe; entra como fonte quando existir (D2) |
| O resumo não projeta receita futura | Só a saída tem vencimento conhecido; reserva confirmada seria receita a receber, e o hotel não existe |
