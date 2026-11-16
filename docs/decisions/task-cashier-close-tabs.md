# Decisões — Task cashier-close-tabs · Caixa fecha comanda e cobra

> Registro acumulado das decisões desta task. Atualizado a cada rodada.

---

## Estado

| | |
|---|---|
| Branch | `task/cashier-close-tabs` |
| Rodada atual | 1 |
| Build | implementado |
| Testes | implementado |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | O "caixa" é quem entra com o perfil `ADMIN`. Na tela Caixa, aba "Contas e comandas", ele fecha comandas abertas (mesa e self-service) e cobra, igual ao garçom: pedir a conta (`/closing`), receber pagamento (`/payments`, com `Idempotency-Key`) e fechar (`/close`). Ruan, 2026-11-16 | implementado |
| 2 | 1 | Reverte a parte "só leitura" da #19 do FE4 e a #17 da `task-qr-table-ordering`. Backend **não muda**: `TabClosingController` já aceita `ADMIN` e `WAITER`; `FRONT_DESK` segue recusado (G4) | implementado |
| 3 | 1 | Sem perfil novo e sem mudança em `Role`. Botões de escrita só aparecem para `ADMIN`; `FRONT_DESK` continua só lendo, para não mostrar botão que devolve 403 | implementado |
| 4 | 1 | Reaproveita `PaymentModal` e `useStartClosing` do salão, sem copiar regra. Dinheiro vem da API, nunca somado no front | implementado |
| 5 | 2 | Nome da casa na tela passa de "Hospedaria" para "Castelo Encantado" (Ruan, 2026-11-16) | implementado |
| 6 | 2 | O rótulo da mesa pode já começar com "Mesa" ("Mesa 1"): o front mostra o rótulo sem o "Mesa" repetido (`bareTableLabel`, em `lib/tableLabel.ts`), em vez de mexer nos dados. "V1" e nomes sem "Mesa" ficam como estão (Ruan, 2026-11-16) | implementado |
| 7 | 2 | A barra de cabeçalho do shell (nome da casa, selo de perfil, nome do usuário e Sair) sai. O Sair vai para um menu no círculo com a inicial do usuário (`UserMenu`), no cabeçalho do salão; nas telas sem cabeçalho próprio (caixa, cozinha, self-service, admin) o mesmo círculo fica flutuando no canto. O selo de perfil mostrava `WAITER` para o `ADMIN` e sai junto (Ruan, 2026-11-16) | implementado |
| 8 | 2 | A tela de mesas deixa de agrupar por área ("Salão", "Varanda"): mostra todas as mesas juntas. A ficha da mesa mostra só o status. A coluna `area` continua no banco e no cadastro do admin (Ruan, 2026-11-16) | implementado |
| 9 | 2 | Logo da casa: escudo com uma torre, em vinho e salmão (`lib/BrandCrest.tsx`, `public/favicon.svg`, `public/apple-touch-icon.png`). Substitui a letra "C" no login (as duas molduras), no salão e nas telas do cliente, e vira o ícone da aba. Desenho feito por Claude, pois não havia arquivo de logo no projeto (Ruan, 2026-11-16) | implementado |

---

## Escopo desta task

- [x] Botão "Pedir a conta" na comanda `OPEN` (só ADMIN)
- [x] Botão "Receber pagamento" na comanda `CLOSING` (só ADMIN), fechando ao zerar o saldo
- [x] `.http` com caminho feliz e negativo (FRONT_DESK recusado no fechamento)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|

---

## Contrato com o front

Sem rota nova. Usa `POST /api/restaurant/tabs/{id}/closing`, `/payments`, `/close`.

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Divisão de conta e taxa de serviço continuam só no salão | Não foi pedido; "igual o garçom" no cobrar e fechar | Levar o `BillSplitDialog` ao caixa se pedirem |

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| | | |
