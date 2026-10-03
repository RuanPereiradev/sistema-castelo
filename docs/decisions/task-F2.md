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

| 5 | 0 | **D5 — O fechamento do turno de caixa é o que congela o resultado.** Escolha do Ruan. O "dia" passa a ser o **turno de operação**, não o dia do calendário: a mesa que pagou à 0h20 cai no mesmo fechamento da que pagou às 23h40, porque é o mesmo turno — que é como o dono pensa. Reaproveita gatilho que já existe (a rota de fechar turno) e o lugar onde já se confere dinheiro. Recusados: agendador por horário, que corta pelo relógio e não pela operação, e botão do `ADMIN`, que deixa dia aberto para sempre se alguém esquecer. **Custo aceito:** turno que ninguém fecha não congela resultado. | pendente |
| 6 | 0 | **D6 — O fechamento cobre uma JANELA DE TEMPO**, do fechamento anterior até este, e não os pagamentos amarrados ao turno. Buraco que isto tapa: PIX e cartão **não** exigem turno aberto — só dinheiro vivo exige. Uma comanda paga em PIX com a gaveta fechada não pertenceria a turno nenhum, e a receita desapareceria do resumo. Com a janela, tudo que aconteceu no intervalo conta, qualquer que seja a forma de pagamento. *Desenho meu, decorrente da D5.* | pendente |
| 7 | 0 | **D7 — O nome é `ShiftResult`, não `DailyClose`.** A D5 fez o conceito virar "resultado de um turno", amarrado um-para-um ao `CashDrawerSession` que o fechou, e **podem existir dois num dia** (almoço e jantar). `DailyClose` carregaria uma mentira: dois `DailyClose` da mesma terça. "Como foi outubro" soma turnos; "como foi terça" soma os turnos que começaram na terça. O `DailyClose` aprovado na D5 da F1 fica **revertido por esta**. | pendente |
| 8 | 0 | **D8 — O turno congela o caixa e a receita; o RESULTADO do período é calculado.** Distinção que apareceu ao montar o modelo: um turno dura horas, e aluguel não pertence a turno — pertence a mês. Então o `ShiftResult` congela o que é verdade operacional do turno (receita faturada, dinheiro que entrou e saiu, pessoas atendidas, quebra da gaveta), e o **lucro ou prejuízo do período** é somado na hora, juntando a receita dos turnos com as despesas **por competência** daquele período. Congelar despesa por turno obrigaria a ratear aluguel por hora, que é número inventado. *Desenho meu.* | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] `ShiftResult`: o resultado do turno congelado, com receita por fonte e o caixa do turno (D7, D8)
- [ ] Migration do `shift_result` + renumerar o hotel de novo
- [ ] Evento em `billing.api` quando o turno fecha, e o ouvinte no `finance` (D5)
- [ ] `FinancialSummary`: resultado do período, comparação com o anterior, e as frases determinísticas
- [ ] `CashFlow`: entrada, saída e projeção pelo vencimento das contas a pagar
- [ ] `GuestFlow`: movimento de clientes e ticket médio, com a cobertura
- [ ] Rotas do resumo, perfil `ADMIN`
- [ ] Arquivo `.http` com caminho feliz e cenários negativos
- [ ] Linha no `http/README.md`
- [ ] Glossário do `CLAUDE.md`

---

## Pontos em aberto

Nenhum. A A1 (quem dispara o fechamento) foi respondida pelo Ruan em 2026-10-03 e virou a D5.

---

## Limitações conhecidas e aceitas

| Limitação | Por quê |
|---|---|
| Não existe margem por prato nem CMV | `menu_item` não tem custo (herdado da F1) |
| Receita de hotel é zero | O módulo `hotel` não existe; entra como fonte quando existir (D2) |
| O resumo não projeta receita futura | Só a saída tem vencimento conhecido; reserva confirmada seria receita a receber, e o hotel não existe |
| Turno que ninguém fecha não congela resultado | D5 — o gatilho é o fechamento do caixa. Mitigação futura: aviso de turno aberto há mais de N horas |
| Dois turnos num dia dão dois registros | D7 — é o preço de o turno ser a unidade; quem pergunta por dia soma os turnos do dia |
