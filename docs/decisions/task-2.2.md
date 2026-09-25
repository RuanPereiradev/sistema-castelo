# Decisões — Task 2.2 comanda: abertura, lançamento e cancelamento

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

O centro dos pedidos. Roda em paralelo com a 1.3 e a 1.4. **Entra na `main`
depois da 1.3**: a V6 precisa preceder a V7 no Flyway.

Itens herdados que esta task resolve: 1.5 #2 (cartão livre, uma comanda por
cartão), limitação da 1.5 (desativar mesa com comanda aberta), 1.2 #2
(`requiresVariant`) e #8 (adicional segue o item na taxa).

---

## Estado

| | |
|---|---|
| Branch | `task/2.2-tab` |
| Rodada atual | 1 — spec aprovada, DEV e TEST em andamento |
| Build | — |
| Testes | — |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **Uma** comanda ativa (`OPEN`/`CLOSING`) por mesa e por cartão. Grupos na mesma mesa se resolvem com divisão e transferência (3.2, 3.6) | pendente |
| 2 | 0 | Cartão do self-service de **1 a 999**, fixo no código e no `CHECK` | pendente |
| 3 | 0 | `WAITER` e `ADMIN` operam a comanda; `FRONT_DESK` e `KITCHEN` não têm acesso. Quem pesa o prato no self-service usa perfil `WAITER` | pendente |
| 4 | 0 | Abertura do self-service é **explícita** (`POST /tabs` com o cartão) e depois o lançamento; o front encadeia | pendente |
| 5 | 0 | Item esgotado, inativo ou fora do horário é **recusado** com 422, com código distinto para "fora do horário" | pendente |
| 6 | 0 | Cancelar item exige **motivo em texto livre**; vale em **qualquer status** menos `CANCELLED`, inclusive entregue | pendente |
| 7 | 0 | Peso: centavo arredondado **meio para cima** (437 g × 59,90 = 26,18); peso de 1 g a 50 kg; quantidade por lançamento de 1 a 999 | pendente |
| 8 | 0 | Desativar mesa com comanda ativa é **recusado** (`DINING_TABLE_HAS_OPEN_TAB`) | pendente |
| 9 | 0 | Item por peso nasce **`DELIVERED`** (o cliente já se serviu, não vai para a tela da cozinha); bebida lançada no cartão nasce `PENDING` e vai para o bar | pendente |
| 10 | 0 | **Cancelar comanda** entra na 2.2: só `OPEN` sem item ativo, com autor, horário e motivo; status `CANCELLED` | pendente |
| 11 | 0 | Nomes novos no glossário: `TabItemOrder` (o pedido de um item) e `ModifierChoice` (adicional escolhido com quantidade) | pendente |
| 12 | 0 | V7 sem `folio_id`, fechamento, destino, taxa de serviço, `guest_count` e `split_group`: vão para a `V11__tab_closing.sql` da 3.2 (reservada em `docs/MIGRATIONS.md`). Colunas do KDS (3.5) e de transferência/junção (3.6) entram já, sem mapeamento. `tab_item` ganha `variant_name` e auditoria; `ck_tab_item_pricing` mais restrito; sem `ON DELETE CASCADE` em `tab_item.tab_id` | pendente (DEV, aguarda Ruan) |
| 13 | 0 | Cancelamento como `POST .../cancel` com `{reason}`, não `DELETE`: nada é apagado. Diverge da seção 8 do plano | pendente (DEV, aguarda Ruan) |
| 14 | 0 | Uma comanda ativa por mesa/cartão garantida **só pelo índice único parcial**, com `saveAndFlush` e tradução do índice em 409 — sem consulta prévia, porque aqui a corrida é real e um caminho só é o caminho testado | pendente (DEV, aguarda Ruan) |
| 15 | 0 | Lançar e cancelar carregam a comanda com `FOR KEY SHARE`: não conflita entre garçons e bloqueia contra o `FOR UPDATE` do fechamento (3.2). Sem `@Version` | pendente (DEV, aguarda Ruan) |
| 16 | 0 | `TabId`, `TabItemId` em `restaurant.domain`; `publicToken` UUID v4, único, fora da API na 2.2; só `subtotal()` (taxa e `total()` na 3.2); um código `TAB_NOT_OPEN` para todo status que não é `OPEN`; sem eventos de domínio (nascem na 3.5) | pendente (DEV, aguarda Ruan) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] `V7__tab.sql` e seção 11 do schema doc atualizada
- [ ] Agregado `Tab` com `TabItem` e `TabItemModifier`, enums com comportamento
- [ ] Abrir em mesa e cartão, uma ativa por vez (#1, #2)
- [ ] Lançar por unidade (variação, adicionais, observação) e por peso (#7, #9)
- [ ] Cancelar item (#6) e cancelar comanda (#10)
- [ ] Ler e listar comandas ativas
- [ ] `DiningTableService` recusa desativar mesa com comanda ativa (#8)
- [ ] `MenuItem.isWithinAvailabilityWindowAt`
- [ ] `http/33-restaurant-tabs.http`
- [ ] Testes de unidade densos e integração com os três cenários de concorrência

### Fora do escopo

- Fechamento, taxa de serviço, destino, divisão, ligação ao folio (3.2); KDS (3.5); transferência e junção (3.6)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-2.2-tab.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Item do cardápio, adicional e mesa não são conferidos contra a propriedade da comanda | Propriedade única; padrão do módulo | Multipropriedade |
| Corrida entre desativar mesa e abrir comanda nela não é tratada | Ação de `ADMIN`, rara | Lock na mesa se aparecer |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
