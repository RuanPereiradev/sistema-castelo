# Decisões — Task FE-kds Tela da cozinha (KDS)

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
| Rodada atual | 0 |
| Build | passa (`npm run build`) |
| Testes | nenhum no front (ferramenta ainda não decidida, ver `frontend/CLAUDE.md`) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Biblioteca `@stomp/stompjs` 7, não `stompjs` 2.3.3: o código já usava a API nova e não compilava (`6b95660`) | implementado |
| 2 | 0 | `CONNECT` autentica por `Authorization: Bearer`, o único cabeçalho que `StompConnectAuthenticationInterceptor` lê; token relido a cada tentativa | implementado |
| 3 | 0 | Proxy `/ws` no Vite (`ws: true`), para o WebSocket chegar ao 8080 em dev | implementado |
| 4 | 0 | Reconexão: um cliente só, inscrições refeitas a cada conexão, e **toda conexão e toda mensagem recarregam a fila pelo `GET`**. A mensagem serve de aviso, a fila do servidor é a verdade — resolve perda na queda e ordem trocada sem comparar `updatedAt` no cliente (`2385490`) | implementado |
| 5 | 0 | Sem polling na fila (regra do `frontend/CLAUDE.md`); o `refetchInterval` de 3s saiu | implementado |
| 6 | 0 | **Um card por item**, não uma ficha por comanda: é o que `GET /api/kitchen/queue` devolve, e start/ready/undo agem sobre um item (Ruan, 2026-10-18) | implementado |
| 7 | 0 | O contrato antigo do front (`id`, `tableLabel`, `guestCount`, `items[]`, `openedAt`) era inventado e quebrava a tela com qualquer item na fila; os tipos agora seguem `KitchenQueueResponse`/`KitchenTicketResponse` | implementado |
| 8 | 0 | Ações no próprio card, sem painel de detalhe: `PENDING` → Iniciar e Pronto (K2 permite o pulo), `IN_PREPARATION` → Pronto e Desfazer, `READY` → Desfazer (K3) | implementado |
| 9 | 0 | Tempo de espera conta de `orderedAt` (contrato da 3.5). O "agora" é o `serverTime` da última leitura mais o tempo decorrido desde ela; o relógio do aparelho nunca entra como hora absoluta. O card é redesenhado a cada 30s sem tocar o servidor | implementado |
| 10 | 0 | Coluna pelo status do item: Pendentes, Em preparo, Prontos. `READY` fica até `DELIVERED` (K5) | implementado |
| 11 | 1 | DEV, correção (2026-10-23): a tela da cozinha se inscrevia também em `/topic/restaurant/ready-items`, que o backend reserva a `WAITER`/`ADMIN` (K10). A recusa (`ACCESS_DENIED`) derrubava a conexão inteira e a tela ficava em "Desconectado" para sempre. A cozinha passa a ouvir só o tópico do setor, por onde chega toda mudança de item (K8). Achado no teste às cegas da skill `teste-cliente-garcom`; conferido em Chrome headless: "Conectado" nas três abas e item novo aparecendo sem recarregar | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Build do front passando
- [x] Reconexão do KDS
- [x] Fila no contrato real, um card por item
- [ ] Testar no navegador contra o backend rodando

---

## Contrato com o front

Consumido, não criado aqui: ver `docs/decisions/task-3.5.md` e `http/kitchen-display.http`.

Códigos traduzidos em `frontend/src/features/kitchen/kitchenMessages.ts`:
`INVALID_TAB_ITEM_TRANSITION`, `TAB_ITEM_ALREADY_CANCELLED`, `TAB_ITEM_NOT_FOUND`,
`VALIDATION_FAILED`.

---

## Pontos em aberto

| # | Pergunta | Rodada |
|---|---|---|
| 1 | #21 da 3.5 continua aberta: desfazer de `READY` que pulou o preparo volta a `PENDING`. A tela só mostra o resultado do servidor | 0 |
