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
| Branch | `feat/cancel-deliver-items` |
| Rodada atual | 1 — religar a tela à API real, começando pelo turno |
| Build | `npm run build` passa (2026-10-19) |
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
| 1 | 1 | O caixa chega ao folio pela lista de abertos do billing, `GET /api/billing/folios?status=OPEN` — PR #37 (Ruan, 2026-10-19) | pendente (depende do #37) |
| 2 | 1 | Religar começando pelo turno de caixa: não depende do #37 e é a primeira coisa do dia na recepção (Ruan, 2026-10-19) | implementado |
| 3 | 1 | DEV: sem turno aberto, `/current` responde 404 `CASH_DRAWER_SESSION_NOT_FOUND`; a tela trata como "nenhum turno aberto" e oferece abrir, não como erro | implementado |
| 4 | 1 | DEV: fechamento cego para todos os perfis. A tela nunca calcula nem revela a diferença antes de fechar; o resultado (esperado, contado, diferença) vem da resposta do `close`, que a API já revela com o turno `CLOSED` | implementado |
| 5 | 1 | DEV: o `ADMIN` continua vendo o esperado e o recebido em dinheiro **ao vivo**, porque a API os manda para ele (C4); a recepção vê "—" | implementado |
| 6 | 1 | DEV: fechar turno pede confirmação explícita (ação irreversível, `frontend/CLAUDE.md`) | implementado |
| 7 | 1 | DEV: sangria e suprimento geram a `Idempotency-Key` uma vez por tentativa e a reusam no retry; a chave só renova depois de um sucesso | implementado |
| 8 | 1 | DEV: valor digitado com vírgula vira ponto antes de ir à API (`150,50` → `150.50`), por troca de caractere, sem conversão numérica. Formato inválido a API recusa com `INVALID_MONEY` | implementado |
| 9 | 1 | DEV: o resumo do turno recém-fechado fica na tela até abrir o próximo; não sobrevive a recarregar a página (não há rota de "último turno fechado") | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Turno: abrir, sangria, suprimento, fechamento cego para todos
- [ ] Lista de folios na rota do #37
- [ ] Ajuste, estorno de lançamento, estorno de pagamento, fechar conta
- [ ] Dinheiro sem `parseFloat` em folios e pagamento
- [ ] Testar no navegador contra o backend

---

## Contrato com o front

Consumido, não criado aqui: `docs/task-2.4-cash-drawer.md` §5 e §6,
`http/41-billing-cash-sessions.http`.

---

## Pontos em aberto

| # | Pergunta | Rodada |
|---|---|---|
| 1 | Recarregar a página logo depois de fechar o turno perde o resumo do fechamento (#9). Uma rota do último turno fechado resolveria — vale pedir? | 1 |
