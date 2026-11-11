# Decisões — Task FE4 Tela do caixa

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/fe4-cashier-folios` (worktree `../castel-cashier`, a partir da `main` em `0203695`); a rodada 1 foi na `feat/cancel-deliver-items` |
| Rodada atual | 2 — folios na rota do #37, ações do folio, dinheiro sem `parseFloat` e a lista de comandas da recepção (#17 da `task-qr-table-ordering`) |
| Build | `npm run build` passa (rodada 2, 2026-11-11) |
| Testes | nenhum no front (ferramenta ainda não decidida, ver `frontend/CLAUDE.md`) |

O log nasceu na rodada 1. A rodada 0 é o commit `aa22ad1`, feito sem log; a revisão de
2026-10-19 contra a API achou os problemas listados em "Origem".

---

## Origem

Revisão do caixa contra `billing/web` (2026-10-19):

1. A lista de folios chamava `GET /billing/folios/open`, rota inexistente
2. O fechamento só aparecia com `expectedAmount` preenchido, que a API esconde de quem não é
   `ADMIN` (C4 da 2.4): a recepção não fechava o turno
3. A diferença era calculada no navegador com `parseFloat`, antes do fechamento
4. Abrir turno, sangria e suprimento não tinham tela; as funções não mandavam `Idempotency-Key`
5. Ajuste, estorno de lançamento e de pagamento desabilitados; "Fechar conta" sem ação
6. Dinheiro em `parseFloat` em `CashierPage`, `FolioCard`, `FolioDetail`, `PaymentDialog`

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | O caixa chega ao folio pela lista de abertos do billing, `GET /api/billing/folios?status=OPEN` — PR #37 (Ruan, 2026-10-19) | implementado (rodada 2) |
| 2 | 1 | Religar começando pelo turno de caixa: não depende do #37 e é a primeira coisa do dia na recepção (Ruan, 2026-10-19) | implementado |
| 3 | 1 | DEV: sem turno aberto, `/current` responde 404 `CASH_DRAWER_SESSION_NOT_FOUND`; a tela trata como "nenhum turno aberto" e oferece abrir, não como erro | implementado |
| 4 | 1 | DEV: fechamento cego para todos os perfis. A tela nunca calcula nem revela a diferença antes de fechar; o resultado (esperado, contado, diferença) vem da resposta do `close`, que a API já revela com o turno `CLOSED` | implementado |
| 5 | 1 | DEV: o `ADMIN` continua vendo o esperado e o recebido em dinheiro **ao vivo**, porque a API os manda para ele (C4); a recepção vê "—" | implementado |
| 6 | 1 | DEV: fechar turno pede confirmação explícita (ação irreversível, `frontend/CLAUDE.md`) | implementado |
| 7 | 1 | DEV: sangria e suprimento geram a `Idempotency-Key` uma vez por tentativa e a reusam no retry; a chave só renova depois de um sucesso | implementado |
| 8 | 1 | DEV: valor digitado com vírgula vira ponto antes de ir à API (`150,50` → `150.50`), por troca de caractere, sem conversão numérica. Formato inválido a API recusa com `INVALID_MONEY` | implementado |
| 9 | 1 | DEV: o resumo do turno recém-fechado fica na tela até abrir o próximo; não sobrevive a recarregar a página (não há rota de "último turno fechado") | implementado |
| 10 | 2 | DEV: o "Total em aberto" (soma dos saldos) sai da tela. A API não devolve esse número e somar no navegador é cálculo que o front não faz; fica a contagem de contas abertas, que não é dinheiro. Lacuna L1 | implementado |
| 11 | 2 | DEV: o pagamento não compara valor com saldo no front. Quem recusa é o backend, com `PAYMENT_EXCEEDS_BALANCE` (só folio `TAB`: o `STAY` aceita ficar com crédito, cenário 12 do `40-billing-folios.http`). O valor vem preenchido com o saldo, como a API mandou, quando o saldo é positivo | implementado |
| 12 | 2 | DEV: o sinal do saldo (a receber, zerado, crédito) é lido no texto da string decimal (`-` na frente; só zeros), sem conversão numérica. Serve só para a cor e para preencher o valor do pagamento | implementado |
| 13 | 2 | DEV: as ações seguem o `@PreAuthorize` do `FolioController`. Ajuste e estorno de pagamento aparecem só para `ADMIN`; receber, estornar lançamento e fechar conta para `ADMIN` e `FRONT_DESK`. No folio `TAB` estorno de lançamento e fechar conta não aparecem (a API recusa com `FOLIO_OWNED_BY_TAB` para todo perfil); a tela diz que quem fecha é a comanda | implementado |
| 14 | 2 | DEV: "Estornar" aparece só no lançamento que o domínio aceita estornar (não é ajuste, não é estorno, não foi estornado) e no pagamento `CONFIRMED`. A regra continua no backend: `CHARGE_NOT_REVERSIBLE`, `CHARGE_ALREADY_REVERSED` e `PAYMENT_ALREADY_REFUNDED` têm tradução | implementado |
| 15 | 2 | DEV: ajuste, estornos e fechar conta passam por diálogo de confirmação; os estornos e o ajuste pedem o motivo no próprio diálogo. Na falha o diálogo fica aberto com a frase do erro, para o retry não perder o que foi digitado | implementado |
| 16 | 2 | DEV: `Idempotency-Key` só no pagamento, a única rota do folio que lê o cabeçalho. Padrão do #7: gerada uma vez por tentativa, reusada no retry (também ao fechar e reabrir o diálogo sem mudar nada), renovada ao mudar valor ou forma e depois de um sucesso. O ajuste não tem chave na API: lacuna L2 | implementado |
| 17 | 2 | DEV: depois de toda ação a tela relê o folio (`GET /folios/{id}`) e a lista de abertos; a resposta da ação não é aplicada na tela. O detalhe lê pelo id, então a conta fechada aqui ou por outra pessoa continua visível como "Fechada" até o operador escolher outra | implementado |
| 18 | 2 | DEV: o ajuste tem seletor "Desconto" / "Acréscimo"; o operador digita o valor sem sinal e o desconto ganha um `-` na frente, por troca de texto (na API, negativo é desconto) | implementado |
| 19 | 2 | DEV: as comandas abertas (#17 da `task-qr-table-ordering`) ficam numa aba "Comandas" ao lado de "Contas", só leitura: lista em `GET /api/restaurant/tabs` (`OPEN` e `CLOSING`) e detalhe em `GET /api/restaurant/tabs/{tabId}`, relidos a cada 5 s pelos hooks do salão. Sem aviso por pedido e sem botão de escrita | revertida pela #23 (no layout) |
| 20 | 2 | DEV: o "total parcial" da comanda é o campo `total` da API (subtotal mais a taxa de serviço, viva enquanto `OPEN` e congelada em `CLOSING`); o detalhe mostra também `subtotal` e `serviceCharge`, como vieram. Itens cancelados não aparecem na lista de itens | implementado |
| 21 | 2 | Folio `STAY` com crédito (hóspede pagou a mais) só fecha com o gerente: a recepção vê a recusa `FOLIO_BALANCE_NOT_ZERO` e chama o `ADMIN`, que estorna o pagamento ou lança ajuste. Nada muda no backend (Ruan, 2026-11-11). Responde o ponto em aberto #2 | revertida pela #22 |
| 22 | 2 | O caixa é só do restaurante: lista e detalhe mostram apenas folios `TAB`; os `STAY` (hóspede) ficam fora da tela, sem rótulo de tipo. Saem "Estornar lançamento" e "Fechar conta", que a API só aceita em `STAY` (`FOLIO_OWNED_BY_TAB`); a comanda continua fechando pelo salão. Ficam receber pagamento e, para `ADMIN`, ajuste e estorno de pagamento. O código de `STAY` sai do front e volta do histórico quando o hotel chegar (Ruan, 2026-11-11: "não quero nada de hóspede aqui, apenas parte de restaurante") | implementado — revê as partes de `STAY` da #13 e da #14 |
| 23 | 2 | Contas e comandas abertas na **mesma vista**, aba "Contas e comandas": contas numa coluna, comandas (só leitura) na outra, cada uma com a lista acima do detalhe; em tela estreita uma fica abaixo da outra. Só layout: as regras da #19 continuam (Ruan, 2026-11-11) | implementado |
| 24 | 2 | DEV: o ajuste manda `Idempotency-Key`, que a `task/folio-adjustment-idempotency` (V16) torna obrigatória. Uma chave por tentativa: reusada no retry com o diálogo aberto, renovada quando valor, sinal, descrição ou motivo mudam, e descartada no sucesso (o diálogo fecha). Antes daquela task entrar, o backend ignora o cabeçalho. Fecha a lacuna L2 do lado do front | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Turno: abrir, sangria, suprimento, fechamento cego para todos
- [x] Lista de folios na rota do #37
- [x] Ajuste, estorno de lançamento, estorno de pagamento, fechar conta
- [x] Dinheiro sem `parseFloat` em folios e pagamento
- [x] Comandas abertas na recepção, só leitura (#17 e #26 da `task-qr-table-ordering`; aqui #19 e #20)
- [x] Testar no navegador contra o backend — teste rápido do Ruan em 2026-11-11 ("aparentemente tá em ordem"), já com as decisões #22 e #23

---

## Contrato com o front

Consumido, não criado aqui: `docs/task-2.4-cash-drawer.md` §5 e §6,
`http/41-billing-cash-sessions.http`.

---

## Pontos em aberto

| # | Pergunta | Rodada |
|---|---|---|
| 1 | Recarregar a página logo depois de fechar o turno perde o resumo do fechamento (#9). Uma rota do último turno fechado resolveria — vale pedir? | 1 |
| 2 | Folio `STAY` com crédito (pagou a mais): a recepção não estorna pagamento nem lança ajuste (só `ADMIN`), e fechar exige saldo zero. Hoje a recepção não fecha essa conta sem o gerente. É isso mesmo? — **respondido pela #21** | 2 |

## Lacunas de API

Achadas na rodada 2. Não contornadas no front.

| # | Lacuna | Efeito na tela |
|---|---|---|
| L1 | Nenhuma rota devolve o total em aberto dos folios (soma dos saldos) | O resumo mostra só a contagem de contas abertas (#10) |
| L2 | `POST /folios/{id}/adjustments` não lê `Idempotency-Key` | Um retry depois de falha de rede pode lançar o ajuste duas vezes; o front só trava o botão durante o envio |
| L3 | `FolioResponse` não diz de quem é o folio `TAB` (sem `reference`, sem dono) | Na lista o folio de comanda aparece só como "Comanda" e o número curto; não dá para saber a mesa |
