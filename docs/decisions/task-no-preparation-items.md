# Decisões — Task no-preparation-items Item do cardápio que não precisa de preparo

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/no-preparation-items` (worktree `../castel-no-prep`) |
| Rodada atual | 1 — implementação do backend |
| Build | `./mvnw clean verify` passa (rodada 1, 2026-11-11): 2135 testes, 0 falhas, ArchUnit incluído; V15 aplicada no Postgres do Testcontainers. Nenhum `install`, nada aplicado no `castel_dev` |
| Testes | 2149, 0 falhas (rodada 2: −8 testes que não pagavam, decisão #14) |

---

## Origem

Teste do pedido pelo QR com cliente simulado (2026-11-11). O Ruan viu refrigerante passar por
"em preparo": "não tem necessidade de ir em preparo, tipo refrigerante, salgadinho, sorvete".

Hoje todo `MenuItem` tem `PrepStation` obrigatório e todo `TabItem` nasce `PENDING` na fila do
setor. Não existe item que dispense preparo. No cardápio de dev, só "Refrigerante lata" (Bebidas,
`BAR`) está nesse caso; salgadinho e sorvete ainda não estão cadastrados.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Item que não precisa de preparo, ao ser lançado na comanda, **já entra como pronto** (`READY`): pula pendente e em preparo, não aparece na tela da cozinha nem do bar, e o garçom vê como "pronto para levar" e marca entregue quando leva (Ruan, 2026-11-11) | implementado |
| 2 | 0 | Nome no glossário: **"Precisa de preparo"** ↔ `requiresPreparation` (coluna `requires_preparation`, `boolean NOT NULL DEFAULT true`). No admin é uma caixa marcada por padrão; desmarca no refrigerante. Os itens que já existem continuam precisando de preparo (Ruan, 2026-11-11). Linha acrescentada ao glossário do `CLAUDE.md` | implementado |
| 3 | 0 | O item sem preparo **continua com setor obrigatório** (`PrepStation`, como hoje): o refrigerante fica no Bar. O setor não é usado no lançamento enquanto o item não precisa de preparo, mas já está pronto se um dia voltar a precisar (Ruan, 2026-11-11) | implementado (nada mudou na obrigatoriedade) |
| 4 | 1 | DEV: o `tab_item` **congela** `requires_preparation` no lançamento (coluna nova na mesma V15, `NOT NULL DEFAULT true`, não atualizável), como já congela nome, preço e setor. A fila do KDS filtra por ela, e `TabItem.isOnKitchenQueue()` passa a ser "precisa de preparo **e** status na fila". Sem a coluna não há como distinguir o item que nasceu `READY` do que a cozinha marcou `READY` (este continua na fila até a entrega, K5), e desmarcar a caixa no cardápio depois não muda o que já foi lançado | implementado (DEV, aguarda Ruan) |
| 5 | 1 | DEV: o item que nasce `READY` grava `ready_at` = momento do lançamento (o `ck_tab_item_ready` da V10 exige) e não grava `preparation_started_at` | implementado (DEV, aguarda Ruan) |
| 6 | 1 | DEV: na criação pelo admin, `requiresPreparation` ausente vale `true`, o mesmo padrão de `serviceChargeEligible`; o front atual, que não manda o campo, continua criando item que vai para a cozinha | implementado (DEV, aguarda Ruan) |
| 7 | 1 | DEV: não existe rota genérica de atualização de item no admin; cada atributo tem a sua (`PATCH .../price`, `POST .../unavailable`). A marcação segue o `PATCH .../price`: `PATCH /api/restaurant/menu-items/{menuItemId}/requires-preparation` com `{"requiresPreparation": false}` (obrigatório), só `ADMIN` como todo o controller. Método de domínio `MenuItem.changeRequiresPreparationTo(boolean)`; as factories continuam com 5 argumentos e nascem `true`, e o `MenuItemService.create` aplica o comando logo depois, como já faz com `chargeServiceCharge` | implementado (DEV, aguarda Ruan) |
| 8 | 1 | DEV, aviso ao salão: o item que nasce `READY` gera uma mensagem `ORDERED` no tópico `/topic/restaurant/ready-items`, no mesmo formato `KitchenDisplayMessage` que a cozinha já manda (o `item.status` vem `READY`). **Nada** sai em `/topic/kitchen/{SETOR}` sobre item sem preparo, em nenhum evento (lançamento, mudança de status, cancelamento, transferência): os quatro eventos levam `requiresPreparation` e o broadcaster só publica no tópico do setor quando o item precisa de preparo. O tópico dos prontos mantém as regras que já tinha (entrou/saiu de `READY`, todo cancelamento, transferido enquanto `READY`) | implementado (DEV, aguarda Ruan) |
| 9 | 1 | DEV: item vendido por peso não muda: nasce `DELIVERED` qualquer que seja a marcação (decisão #9 da 2.2). Transferir, juntar e trocar de mesa levam a marcação congelada junto com o item | implementado (DEV, aguarda Ruan) |
| 10 | 1 | DEV: a V15 estava reservada em `docs/MIGRATIONS.md` para o hotel (1.1), que ainda não tem nenhum arquivo em nenhuma worktree ou branch. Usei a V15 como o briefing pediu e desci o hotel para V16/V17 no registro, como nas renumerações anteriores | substituída pela #12 (aprovada pelo Ruan) |
| 12 | 1 | Registro de migrations aprovado (Ruan, 2026-11-11): V15 desta task, V16 da `task/folio-adjustment-idempotency`, hotel em V17/V18. Esta task entra na `main` antes daquela. Fecha a pendência de aprovação da #10 | implementado |
| 13 | 1 | DEV (front): no cadastro do item do admin, caixa "Precisa de preparo" marcada por padrão, com a dica de quando desmarcar visível embaixo (não em `title`, que não aparece no toque); na criação vai no `POST`, na edição chama o `PATCH .../requires-preparation` só se mudou. A lista do cardápio ganha a coluna. O salão não muda: já mostra `READY` como "Pronto" com o botão de entregar | implementado (DEV) |
| 11 | 1 | "Desfazer" (`POST /api/kitchen/items/{itemId}/undo`) num item que não precisa de preparo é **recusado** com `INVALID_TAB_ITEM_TRANSITION` (409): ele nasceu pronto e não tem passo de cozinha para desfazer. Iniciar preparo e marcar pronto continuam como estão (Ruan, 2026-11-11, opção "recusar o desfazer"). Responde o P1 | implementado |
| 14 | 2 | DEV (review, rodada 2): saem 8 testes que não pagavam (repetiam outro teste, não podiam falhar ou testavam encanamento: self-service no domínio, item com preparo `PENDING`, os dois de mudança do cardápio em memória, junção, getters do evento, `information_schema`, 403 do `@PreAuthorize`). Ficam os de risco: nasce `READY`, peso, desfazer, entrega, cancelamento, transferência, fila congelada no banco, default, `PATCH` sem campo, WebSocket e QR. `docs/schema-banco-de-dados.md` ganha a coluna e a renumeração V15–V18 | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Marcação no item do cardápio de que ele não precisa de preparo (migration V15, domínio, glossário no `CLAUDE.md`)
- [x] Lançamento de item sem preparo nasce `READY` e fica fora da fila do KDS (#1), em todo caminho que cria `TabItem`: garçom, pedido pelo QR confirmado, self-service (todos passam por `Tab.addItem`). A transferência não cria item, move o existente com a marcação congelada
- [x] Caixa de marcar no cadastro do item, na administração — **backend**: campo no create, rota de alteração e campo na resposta. **Front**: caixa no cadastro e coluna na lista (#13)
- [x] Garçom vê o item como pronto para levar — **backend**: status `READY` na comanda e aviso no tópico dos prontos. **Front**: o salão já trata `READY` como "Pronto" com o botão de entregar (#13)
- [x] `.http` com caminho feliz e cenários negativos (`http/39-restaurant-no-preparation-items.http`; não executado contra servidor, porque a 8080 e o `castel_dev` estão em uso pelo Ruan)
- [x] Testes no orçamento — agente de teste, cobrindo os casos que estavam no P2 (ver "Testes" no Estado)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

**Códigos de erro**

Nenhum código novo. A rota nova responde os códigos que o controller do cardápio já responde:

| Código | HTTP | Quando |
|---|---|---|
| `VALIDATION_FAILED` (o da fundação) | 400 | `PATCH .../requires-preparation` sem `requiresPreparation` |
| `MENU_ITEM_NOT_FOUND` | 404 | item inexistente |
| `ACCESS_DENIED` (o da fundação) | 403 | perfil que não é `ADMIN` alterando ou criando item |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `requiresPreparation` no `POST /api/restaurant/menu-items` | booleano, opcional; ausente = `true` (#6) |
| `PATCH /api/restaurant/menu-items/{menuItemId}/requires-preparation` | corpo `{"requiresPreparation": boolean}`, obrigatório; responde o `MenuItemResponse` inteiro, como o `PATCH .../price` |
| `requiresPreparation` no `MenuItemResponse` (admin) | booleano, sempre presente |
| Item lançado sem preparo, na comanda | `status: "READY"`, `readyAt` = `orderedAt`, `preparationStartedAt` nulo; o garçom marca entregue pela rota que já existe (`POST /api/restaurant/tabs/{tabId}/items/{itemId}/deliver`) |
| `/topic/restaurant/ready-items` | nova mensagem `type: "ORDERED"` (antes só vinha `STATUS_CHANGED`, `CANCELLED` e `TRANSFERRED`) para o item que nasce pronto (#8) |
| `GET /api/kitchen/queue?station=...` e `/topic/kitchen/{SETOR}` | nunca trazem item sem preparo |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| O front do garçom ainda não assina `/topic/restaurant/ready-items` (o tópico existe desde a 3.5, mas nenhuma tela consome). O garçom vê o item `READY` ao abrir a comanda, mas sem aviso em tempo real | O front fica fora desta task, por ordem do briefing | Task do front: assinar o tópico e tratar `ORDERED` além de `STATUS_CHANGED`, `CANCELLED` e `TRANSFERRED` |

---

## Pontos em aberto

Nenhum. O P2 (casos de teste) foi coberto na rodada 1 pelo agente de teste.
