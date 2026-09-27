# Task 3.6 — RASCUNHO, aguardando respostas do Ruan

> **Status (2026-09-27):** rascunho de planejamento, ainda **não aprovado**. Nenhuma linha de
> código foi escrita. Para retomar:
> 1. o Ruan responde às perguntas T1–T14 (seção "Perguntas de negócio" abaixo, com a
>    recomendação de cada uma) e aprova os nomes do glossário;
> 2. este arquivo vira a spec final (`docs/task-3.6-tab-transfer-merge.md`, sem este
>    cabeçalho), e `docs/decisions/task-3.6.md` é criado a partir de
>    `docs/TEMPLATE-decisoes.md` com as respostas, **antes** da primeira linha de código;
> 3. fluxo de sempre: DEV (worktree, Maven com `-Dmaven.repo.local` isolado) → testes de
>    unidade só a partir da spec → review (até 3 rodadas) → PR **com base na `main`**.
>
> Contexto: o lote 2 (2.4 caixa V8, 3.2 fechamento V9, 3.5 KDS V10) está na `main` (PR #22).
> A 3.6 não precisa de migration no caminho recomendado; V11/V12 continuam reservadas ao
> hotel. Pendência herdada para incluir: cenário `FOLIO_OWNED_BY_TAB` numa comanda real
> no `http/34` (sugestão da rodada 2 da 3.2).
>
> Numeração das perguntas: T1–T14 abaixo correspondem, na ordem, às perguntas 1–14.

---

# Preparação da task 3.6: `transferItemsTo()` e `mergeWith()`

Nada foi escrito em disco. Li o CLAUDE.md, o plano-tecnico (§2.2, §2.4, glossário, §8, onda 3), o schema §11/§11.1/§13, o MIGRATIONS.md, as specs e decisões de 2.2, 3.2 e 3.5, e o código de `restaurant` (Tab, TabItem, os dois enums de status, TabRepository/JpaTabRepository/SpringDataTabRepository, TabService, TabClosingService, KitchenDisplayService, KitchenDisplayBroadcaster, JpaKitchenQueue, TabController, TabBilling e FolioFacade).

**Três achados do código que definem o desenho:**
- **Mapeamento de `Tab.items`.** A coluna de ligação está como `@JoinColumn(name = "tab_id", nullable = false, updatable = false)`. Tirar o item de uma lista e pôr em outra **não grava nada** em `tab_item.tab_id`. A 3.6 precisa de um jeito explícito de mover a linha (§7.3 da spec).
- **Trava do item filtra a comanda.** `lockItemForUpdate` usa `where id = :itemId and tab_id = :tabId`. Se o item for transferido enquanto a cozinha espera a trava, o Postgres reavalia o `WHERE` depois da espera e não devolve nada. É daí que vem o `TAB_ITEM_NOT_FOUND` da limitação da 3.5, e a 3.6 corrige com uma nova tentativa.
- **Eventos da cozinha já prontos para isso.** `TabStatus.MERGED` já responde "não" a tudo e `holdsItsPlace = false`. O `KitchenTicket` lê a mesa pelo `JOIN` com `tab`, então basta um evento novo para a tela receber o ticket com a mesa nova.

---

## 1. Rascunho: `docs/task-3.6-tab-transfer-merge.md`

```markdown
# Task 3.6 — Comanda: transferência de itens, junção e troca de mesa

**Objetivo:** mover linhas inteiras de uma comanda `OPEN` para outra `OPEN`
(`transferItemsTo`), juntar uma comanda `OPEN` noutra (`mergeWith`, a absorvida
vai a `MERGED`) e trocar a comanda de mesa (`moveToTable`, abrir na mesa nova +
juntar, numa transação), gravando de onde cada item veio e avisando o KDS da
mesa nova.

Decisões: `docs/decisions/task-3.6.md` (T1–Tn a aprovar). Herdadas:
- 2.2 #1: uma comanda ativa por mesa/cartão; grupos na mesma mesa = divisão (3.2) e transferência.
- 2.2 #8: mesa com comanda ativa não desativa (a troca de mesa passa a ser o caminho para liberar uma mesa).
- 2.2 #12: `transferred_*` e `merged_*` já existem na V7, sem mapeamento.
- 2.2 #15, #18, #22: `FOR KEY SHARE` para quem acrescenta; `FOR UPDATE` para mudança de status; item `FOR UPDATE` depois da comanda.
- 3.2 F13: nada muda o dinheiro de uma comanda em `CLOSING`; reabrir primeiro.
- 3.2 invariante 17 / D4: comanda com folio só sai de cena com `billing.closeFolio` (saldo zero).
- 3.2 D27: `@DynamicUpdate` + trava da linha do item.
- 3.5 §1 "Fora": a 3.6 publica o evento que o broadcaster consome para trocar a mesa exibida.
- 3.5, limitação: transição da cozinha × transferência → `TAB_ITEM_NOT_FOUND` (resolvida aqui, §7.4).

Gabarito: `TabClosingService`/`TabClosingController` (classe própria para não
disputar `TabService`/`TabController`) e `KitchenDisplayBroadcaster`.

Risco alto: dinheiro (total das duas comandas, taxa do item que muda de
origem), transição de estado (`MERGED` final) e concorrência com **duas**
comandas travadas. Teste denso nesses três pontos.

---

## 1. Escopo

**Dentro**
- `Tab.transferItemsTo(destination, itemIds, by, at)`: linhas inteiras, tudo ou nada.
- `Tab.mergeWith(mergedTab, billing, by, at)`: todos os itens ativos da absorvida vão para esta; a absorvida vai a `MERGED` com `merged_into_tab_id`, `merged_at`, `merged_by`.
- `moveToTable` (caso de uso): abre comanda na mesa de destino e junta a atual nela, numa transação (T-move).
- Mapear `tab_item.transferred_from_tab_id`, `transferred_at`, `transferred_by` e `tab.merged_into_tab_id`, `merged_at`, `merged_by`.
- `TabStatus.acceptsItemTransfer()` e `acceptsMerge()`; `TabItemStatus.acceptsTransfer()`.
- Evento `TabItemTransferred`, um por item movido (transferência, junção, troca de mesa); o broadcaster empurra `TRANSFERRED`.
- `findByItemIdForItemChange` repete a leitura do `tab_id` quando o item mudou de comanda no meio (limitação da 3.5).
- `TabTransferService`, `TabTransferController`, DTOs; `TabResponse`/`TabItemResponse` com os campos novos.
- §11 e §13 do schema atualizados (rastro da última origem; ciclo impossível pelo status).
- `http/36-restaurant-tab-transfer.http` e a linha no `http/README.md` (orquestrador).
- Testes de unidade (agente de teste) e integração (DEV) com concorrência.

**Fora**
- Transferir **parte** da quantidade de uma linha (T4).
- Desfazer junção; reabrir `MERGED` (T10).
- Transferir de/para comanda `CLOSING`, `CLOSED`, `CANCELLED`, `MERGED` (T1).
- Mover crédito de folio entre comandas (T2).
- Histórico completo de saltos (A→B→C); fica só a última origem (T11).
- Relatório "itens que saíram desta comanda" (5.1).
- Telas (4.3, 4.5). `CLAUDE.md`, `docs/MIGRATIONS.md`: orquestrador.

---

## 2. Modelo de dados

**Sem migration** (se T3/T11 forem aprovadas como recomendado). Todas as colunas
já estão na V7:

- `tab.merged_into_tab_id`, `merged_at`, `merged_by`, `ck_tab_merged`, `ck_tab_no_self_merge`, `idx_tab_merged_into`;
- `tab_item.transferred_from_tab_id`, `transferred_at`, `transferred_by`, `ck_tab_item_transferred`.

Mapeamento:
- `Tab`: `mergedIntoTabId` (UUID), `mergedAt`, `mergedBy`.
- `TabItem`: `transferredFromTabId`, `transferredAt`, `transferredBy` e **`tabId` como coluna
  básica** `@Column(name = "tab_id", insertable = false)`, gravada só na transferência
  (§7.3). A lista `Tab.items` continua `updatable = false`, sem o `UPDATE` redundante por
  item lançado.

**Se a Ruan exigir motivo (T3) ou histórico de saltos (T11):** `V11__tab_transfer.sql`
(`tab_item.transfer_reason`, `tab.merge_reason`, ou a tabela `tab_item_transfer`), e o
hotel desce para V12/V13. Não pode ser V13: com o `outOfOrder` desligado, uma V13
aplicada antes faria o Flyway recusar as V11/V12 do hotel depois.

---

## 3. Invariantes

**Transferência — `source.transferItemsTo(destination, itemIds, by, at)`**, nesta ordem:
1. `destination` ≠ `this` → `INVALID_TAB_TRANSFER` (422).
2. `itemIds` não vazio (duplicados colapsam) → `INVALID_TAB_TRANSFER`.
3. Origem `acceptsItemTransfer()` (só `OPEN`) → `TAB_NOT_OPEN`.
4. Destino `acceptsItemTransfer()` → `TAB_NOT_OPEN` (o detalhe diz qual comanda).
5. **Todos** os itens existem na origem → `TAB_ITEM_NOT_FOUND`.
6. **Todos** ativos (`TabItemStatus.acceptsTransfer()` = `isActive()`) → `TAB_ITEM_ALREADY_CANCELLED`.
   Item cancelado fica na comanda onde foi cancelado.
7. Aplica, item a item, sem tocar em nada congelado:
   - `lineTotal`, preço, adicionais, `serviceChargeable`, `prepStation`, `status`
     e os instantes do KDS viajam como estão. Item por peso (nasce `DELIVERED`) pode ir.
   - `transferredFromTabId = source`, `transferredAt`, `transferredBy` (sobrescreve a origem anterior, T11).
   - `splitGroup` volta a `1` (T9).
   - `serviceChargeWaived`: mantém; e, se a origem está com a taxa desligada
     (`serviceChargeApplied = false`) e o item é `serviceChargeable`, passa a `true`,
     para o valor efetivo do item não mudar em silêncio (T7).
8. Devolve um `TabItemTransferred` por item.
- Σ `subtotal()` das duas comandas antes = Σ depois.
- `source.subtotal()` cai exatamente o Σ `lineTotal` movido.
- A origem que fica sem item ativo **continua `OPEN`**, ocupando a mesa. O garçom cancela
  (`cancel`, 2.2 #10) ou junta (T6).

**Junção — `this.mergeWith(mergedTab, billing, by, at)`**, nesta ordem:
1. `mergedTab` ≠ `this` → `INVALID_TAB_MERGE` (422).
2. `this.acceptsMerge()` (só `OPEN`) → `TAB_NOT_OPEN`.
3. `mergedTab.acceptsMerge()` → `TAB_NOT_OPEN`.
4. Se `mergedTab` tem folio (foi reaberta), `billing.closeFolio(folio)`; com saldo ≠ 0,
   `FOLIO_BALANCE_NOT_ZERO` (T2, mesma regra do `cancel` com folio).
5. Todo item **ativo** da absorvida é transferido pela regra 7. Os cancelados ficam nela.
   Comanda absorvida sem item ativo é aceita.
6. `guestCount`: soma quando as duas têm; senão fica o desta (T8).
7. `mergedTab` → `MERGED`, `merged_into_tab_id = this`, `merged_at`, `merged_by`.
8. `serviceChargeApplied` desta não muda; a absorvida com a taxa desligada leva os
   itens com `serviceChargeWaived` (regra 7, T7).
- **`MERGED` é final:** recusa lançar, cancelar item, cancelar, taxa, grupos,
  pessoas, fechar, pagar, transferir, juntar (os predicados já existentes respondem
  `false`). Libera mesa e cartão (`holdsItsPlace = false`, fora dos índices parciais).
  O KDS segue avançando item que **já estava** nela? Não há: todo ativo saiu.
- **Sem ciclo:** o destino é sempre `OPEN` e a absorvida vira `MERGED`, que nunca é
  destino. A → B → A é impossível pelo status (fecha a linha da §13).

**Troca de mesa — `TabTransferService.moveToTable(tabId, diningTableId)`** (T5)
- `Tab.openForTable(destinationTable)` (`INACTIVE_DINING_TABLE`, e o índice parcial
  responde `TAB_ALREADY_OPEN_FOR_DINING_TABLE`) + `newTab.mergeWith(current)`, numa
  transação.
- Cartão → mesa usa o mesmo caminho.
- O rastro: a antiga fica `MERGED` apontando para a nova, e os itens com `transferred_from`.

**Taxa de serviço e origem**
- `serviceChargeable` é o retrato do lançamento (2.2 #13) e nunca muda.
  - Mesa → cartão: a comanda de self-service não cobra (`serviceChargeApplied = false`), e o item perde a taxa.
  - Cartão → mesa: o item nasceu sem taxa e continua sem. Religar dá `TAB_ITEM_NOT_SERVICE_CHARGEABLE` (F3).
- Taxa calculada uma vez sobre a soma no destino (3.2 #5): transferir não soma taxas por item.

---

## 4. Assinaturas públicas

// Tab
List<TabItemTransferred> transferItemsTo(Tab destination, Set<TabItemId> itemIds, UUID by, Instant at);
List<TabItemTransferred> mergeWith(Tab mergedTab, TabBilling billing, UUID by, Instant at);
Optional<TabId> mergedIntoTabId(); Optional<Instant> mergedAt(); Optional<UUID> mergedBy();

// TabItem (pacote)
void transferTo(TabId destination, TabId source, boolean waiveServiceCharge, UUID by, Instant at);
// público
Optional<TabId> transferredFromTabId(); Optional<Instant> transferredAt(); Optional<UUID> transferredBy();

// TabStatus
acceptsItemTransfer() (OPEN) · acceptsMerge() (OPEN)
// TabItemStatus
acceptsTransfer() (tudo menos CANCELLED)

// Evento (restaurant.domain, DomainEvent)
record TabItemTransferred(TabId fromTabId, TabId toTabId, TabItemId itemId, PrepStation station,
                          TabItemStatus status, Instant occurredAt)
    boolean reachesKitchenQueue();   // status.isOnKitchenQueue()
    boolean isReady();               // status == READY

// TabRepository — novos
TabsForTransfer findForTransfer(TabId source, TabId destination, Set<TabItemId> itemIds);
TabsForTransfer findForMerge(TabId receiving, TabId merged);
// record TabsForTransfer(Tab source, Tab destination) em restaurant.domain (nome técnico)

// Exceções: InvalidTabTransferException ("INVALID_TAB_TRANSFER", 422),
//           InvalidTabMergeException ("INVALID_TAB_MERGE", 422)

// restaurant.application
class TabTransferService {
    TabTransferView transfer(TabId source, TabId destination, Set<TabItemId> itemIds);
    Tab merge(TabId receiving, TabId merged);
    Tab moveToTable(TabId tabId, DiningTableId diningTableId);
}

---

## 5. Códigos de erro

| Código | HTTP | Quando |
|---|---|---|
| `INVALID_TAB_TRANSFER` | 422 | Destino = origem; lista de itens vazia ou ausente |
| `INVALID_TAB_MERGE` | 422 | Juntar a comanda com ela mesma |

Reaproveitados: `TAB_NOT_FOUND` (qualquer das duas), `TAB_NOT_OPEN` (origem,
destino ou absorvida fora de `OPEN`, `MERGED` inclusive), `TAB_ITEM_NOT_FOUND`,
`TAB_ITEM_ALREADY_CANCELLED`, `FOLIO_BALANCE_NOT_ZERO`, `DINING_TABLE_NOT_FOUND`,
`INACTIVE_DINING_TABLE`, `TAB_ALREADY_OPEN_FOR_DINING_TABLE`, `SETTING_NOT_FOUND`.

---

## 6. API

`ADMIN` e `WAITER`; `KITCHEN` e `FRONT_DESK` → 403 (T12).

POST /api/restaurant/tabs/{tabId}/transfer  {toTabId, itemIds:[...]}  200 TabTransferResponse
POST /api/restaurant/tabs/{tabId}/merge     {mergedTabId}             200 TabResponse (a que fica)
POST /api/restaurant/tabs/{tabId}/move      {diningTableId}           201 TabResponse (a nova)

- `TabTransferResponse {source: TabResponse, destination: TabResponse}`: o front redesenha as duas.
- `TabResponse` ganha `mergedIntoTabId, mergedAt, mergedBy`. Um `GET` numa `MERGED`
  responde 200 com o ponteiro, e o front segue.
- Item ganha `transferredFromTabId, transferredAt, transferredBy`.
- Totais ao vivo (D20 da 3.2): o controller lê o percentual **antes** da escrita (D25).
- `toTabId`/`mergedTabId`/`diningTableId` ausentes → 400; `itemIds` ausente → `INVALID_TAB_TRANSFER`.
- Controller novo `TabTransferController`, sem tocar no `TabController`.

---

## 7. Concorrência

1. **Ordem determinística.** As duas comandas são travadas em ordem crescente de `id`
   (UUID), qualquer que seja o papel. Cada uma é carregada depois da sua trava.
   Depois vêm os itens `FOR UPDATE`, em ordem de `id`. Nunca item antes de comanda.
2. **Modo por papel:**
   - Transferência: origem e destino `FOR KEY SHARE` (é "tirar um item e pôr outro";
     garçons seguem lançando nas duas). Itens `where id in (:ids) and tab_id = :source
     order by id for update`.
   - Junção: a absorvida `FOR UPDATE` (muda de status; espera todo lançamento em curso,
     para nenhum item ficar preso numa `MERGED`, como a 2.2 #18); a que fica `FOR KEY SHARE`.
   - Troca de mesa: a comanda atual `FOR UPDATE`; a nova é inserida e o índice parcial decide.
3. **Gravação do `tab_id`.** O `TabItem.transferTo` grava `tabId`, `transferred_*` (e
   `service_charge_waived`/`split_group`). Com o `@DynamicUpdate` sai um `UPDATE` só com
   essas colunas, sem regravar `status`. A verificação da FK toma `FOR KEY SHARE` no destino,
   que é compatível com a trava já tomada.
4. **KDS (limitação da 3.5).** `findByItemIdForItemChange` passa a repetir: lê o
   `tab_id`, trava a comanda, trava o item com `tab_id` = o lido. Se não vier linha,
   lê o `tab_id` de novo e repete (até 3 vezes); só então `TAB_ITEM_NOT_FOUND`.
   Depois de travado o item, ele não muda de comanda: a transferência precisa do
   `FOR UPDATE` do item e a junção do `FOR UPDATE` da comanda.
5. **Compatibilidade:**
   - `startClosing`/`reopen`/`close`/`cancel` (`FOR UPDATE`) e transferência/junção se esperam.
     Quem chega depois vê o status novo (`TAB_NOT_OPEN`).
   - Pagamento (`FOR KEY SHARE`) numa `OPEN` é `TAB_NOT_CLOSING` de qualquer jeito.
   - A rota do billing trava só o folio, e a junção trava comanda → folio: sem ciclo.

---

## 8. Eventos e KDS

- `TabTransferService` publica os `TabItemTransferred` que o agregado devolve.
- `KitchenDisplayBroadcaster.onItemTransferred` (`BEFORE_COMMIT`, mesma mecânica
  da #35/#47 da 3.5): lê o ticket (já com a mesa/cartão novos) e, no `afterCommit`:
  - `reachesKitchenQueue()` → `/topic/kitchen/{station}` com `type: "TRANSFERRED"`;
  - `isReady()` → também `/topic/restaurant/ready-items`.
  - Item `DELIVERED` (e o prato por peso) não gera mensagem.
- `KitchenDisplayMessage.transferred(occurredAt, ticket)`. O cliente funde por
  `itemId` e fica com o `updatedAt` maior (K13). O `updatedAt` muda porque
  `transferred_*` sujam a entidade.

---

## 9. `.http` — `http/36-restaurant-tab-transfer.http`

**Preparação:** logins (`admin`, `garcom`, `cozinha`); mesas A, B, C com sufixo aleatório;
itens: pizza (`PIZZA`), bebida (`BAR`), um sem taxa, buffet por peso; comanda na mesa A
com pizza, bebida ×2 e prato; comanda na mesa B com um item; cartão aleatório.

**Caminho feliz:**
1. Transfere a pizza A → B: subtotais conferidos (A cai o `lineTotal`, B sobe), `transferredFromTabId = A`, `splitGroup = 1`.
2. `start` na pizza pela cozinha continua funcionando; a fila `PIZZA` mostra a mesa B.
3. Tira a taxa da comanda A, transfere a bebida A → B: o item chega com `serviceChargeWaived = true`.
4. `guestCount` 2 em A e 3 em B; junta A em B: A `MERGED` com `mergedIntoTabId = B`, B com `guestCount = 5`; `GET /tabs?diningTableId=A` vazio; abrir comanda nova na mesa A → 201.
5. Troca B para a mesa C (`/move`): nova comanda em C com os itens; B `MERGED`; mesa B livre.
6. Transfere mesa → cartão: taxa zero no cartão.

**Negativos:**
- `INVALID_TAB_TRANSFER` (mesma comanda; lista vazia)
- `INVALID_TAB_MERGE`
- `TAB_NOT_OPEN`: origem em `CLOSING`; destino em `CLOSING`; lançar em comanda `MERGED`; juntar numa `MERGED`
- `TAB_ITEM_NOT_FOUND` (item de outra comanda)
- `TAB_ITEM_ALREADY_CANCELLED`
- `FOLIO_BALANCE_NOT_ZERO` (juntar comanda reaberta com pagamento)
- `TAB_ALREADY_OPEN_FOR_DINING_TABLE` e `INACTIVE_DINING_TABLE` no `/move`
- `TAB_NOT_FOUND`; 400 sem `toTabId`; 401; 403 da cozinha

**Limpeza:** fecha ou cancela o que ficou aberto.

---

## 10. Testes

**Unidade (agente de teste), densa, com `FakeTabBilling`:**
- *Matriz de status:* origem × destino em {OPEN, CLOSING, CLOSED, CANCELLED, MERGED} para transferir e juntar (só OPEN × OPEN passa); `MERGED` × toda operação existente (lançar, cancelar item, cancelar, taxa, grupo, pessoas, startClosing, pagar, reabrir, fechar, transições do KDS em item que ficou).
- *Item:* status × transferir (5 casos; cancelado recusa; por peso aceita); tudo ou nada (um item inexistente não move nenhum); ordem das checagens.
- *Dinheiro:* conservação de Σ subtotal; `lineTotal` e adicionais intactos; base da taxa no destino (mesa→mesa, mesa→cartão = 0, cartão→mesa sem taxa); origem com a taxa desligada → item chega dispensado; taxa sobre a soma no destino (3 × 3,35 → 1,01).
- *Junção:* `guestCount` (3+2, null+2, 3+null); folio da absorvida fechado com saldo zero e recusado com saldo ≠ 0 (nada mudou); cancelados ficam; absorvida sem item ativo; `merged_*` gravados; ciclo impossível.
- *Eventos:* um por item movido, `from`/`to` certos, `reachesKitchenQueue`/`isReady`.

**Integração (DEV, `app`):**
- Fatia: transferir, juntar, trocar de mesa pela rota real; linha de `tab_item.tab_id` conferida no banco; 403 da cozinha.
- **Concorrência (Testcontainers):**
  (a) transferir × `startClosing` da origem: o `TabCharge` = total dos itens que ficaram, ou a transferência recebe `TAB_NOT_OPEN`;
  (b) 5 lançamentos na absorvida × junção: nenhum item ativo em comanda `MERGED`;
  (c) duas transferências do mesmo item para destinos diferentes: um 200 e um `TAB_ITEM_NOT_FOUND`;
  (d) juntar A em B × juntar B em A ao mesmo tempo: um 200, um `TAB_NOT_OPEN`, sem deadlock;
  (e) `ready` da cozinha × transferência do mesmo item: nenhum 500, a cozinha avança depois de repetir a leitura (§7.4).
- WebSocket: uma mensagem `TRANSFERRED` no setor certo, com o rótulo da mesa nova.

Orçamento: produção estimada ~700 linhas (agregado ~160, repositório ~70, serviço ~100,
web/DTOs ~150, broadcaster/evento ~60, exceções ~40); testes ~700.
```

---

## 2. Perguntas de negócio para o Ruan

1. **Transferir de ou para comanda em `CLOSING`.** Recomendo **recusar** (`TAB_NOT_OPEN`) e reabrir primeiro. O `TabCharge` já lançado congelou o total. Mexer nele pediria estornar e relançar dentro da transferência, justamente o que a F13 recusou para o cancelamento de item. Com `reopen` + transferir + `startClosing`, o rastro fica no folio.
2. **Juntar comanda que tem folio ou pagamento** (reaberta, com crédito). Recomendo **fechar o folio da absorvida se o saldo for zero**, e com saldo ≠ 0 recusar com `FOLIO_BALANCE_NOT_ZERO` (o `ADMIN` estorna o pagamento antes). É a regra do `cancel` com folio (invariante 17, D4). Levar o crédito para o folio da outra comanda exigiria transferência de pagamento no billing, que não existe. A que fica pode ter folio sem problema: o próximo `startClosing` reaproveita.
3. **Mesa ↔ cartão entre si** (transferir e juntar entre `TABLE_SERVICE` e `SELF_SERVICE`). Recomendo **permitir**, com o `serviceChargeable` do item congelado. Mesa → cartão perde a taxa (cartão não cobra); cartão → mesa fica sem taxa. O cliente do buffet que senta para ser servido é caso real, e o congelamento segue a regra de preço (1.4 do schema).
4. **Transferir parte da quantidade de uma linha** (2 de 3 cervejas). Recomendo **não, na v1: só linha inteira**. `line_total`, `quantity` e os adicionais são imutáveis (2.2 #11). Partir a linha cria item novo e reduz o antigo, o que quebra o append-only e o rateio de adicionais. Quem divide a conta usa `splitGroup` (3.2), e o garçom lança unidades separadas quando sabe que vão se separar.
5. **"Trocar de mesa" (mover a comanda inteira).** Recomendo uma **operação própria na API (`POST /tabs/{id}/move`)**, feita por dentro como "abrir na mesa nova + juntar", numa transação. O plano dá "transferir mesa" ao garçom. Assim tem rastro completo sem migration (a antiga fica `MERGED` → nova, e os itens com origem). A alternativa, trocar `dining_table_id` no lugar, não tem coluna de rastro e exigiria migration. O custo: a comanda muda de `id`, e o folio segue a regra da pergunta 2.
6. **Comanda de origem que fica vazia depois de transferir tudo.** Recomendo **ficar `OPEN`**, ocupando a mesa, e o garçom cancela (sem motivo extra, já que ficou sem item ativo) ou usa a junção. Cancelar sozinha é mudança de status disfarçada, que pediria `FOR UPDATE` na origem e um motivo. O front sugere "juntar" quando todos os itens estão marcados.
7. **Taxa de serviço quando a origem está com a taxa desligada** (`serviceChargeApplied = false`) e o destino com ela ligada. Recomendo que o **item chegue com `serviceChargeWaived = true`**, para o valor efetivo não mudar sem ninguém ver. O garçom religa no destino, sem motivo (F2).
8. **`guestCount` na junção.** Recomendo **somar quando as duas têm; se só uma tem, fica a da comanda que fica**. O valor só serve de padrão para a divisão igual (F8), e o garçom corrige a qualquer momento.
9. **`splitGroup` do item transferido ou juntado.** Recomendo **voltar ao grupo 1**. O número do grupo só tem sentido dentro da comanda de origem: o grupo 2 da mesa 4 não é o grupo 2 da mesa 5. A outra opção é manter o número.
10. **Desfazer junção.** Recomendo **não: `MERGED` é final**, como `CLOSED` (F11). Para "desjuntar", o garçom abre comanda nova e transfere os itens de volta; o rastro mostra os dois movimentos.
11. **Rastro de auditoria com vários saltos** (A → B → C). A V7 guarda **só a última origem** (`transferred_from_tab_id`). Recomendo **aceitar na v1** (sem migration, o hotel fica em V11/V12). A pergunta da §11, "por que a mesa 4 fechou com menos", segue respondida no primeiro salto. Um histórico completo pede a tabela `tab_item_transfer` numa V11, com o hotel renumerado.
12. **Quem pode.** Recomendo **`WAITER` e `ADMIN`**, como toda rota de comanda (2.2 #3, G4, e o plano dá "transferir mesa" ao garçom). `KITCHEN` e `FRONT_DESK` recebem 403.
13. **Motivo obrigatório.** Recomendo **não** em transferência, junção e troca de mesa. É operação de salão frequente e não muda o valor da venda; autor e instante já ficam nas colunas `transferred_*`/`merged_*`. Com motivo, entra migration (V11, com o hotel indo para V12/V13).
14. **Qual comanda fica na junção.** Recomendo **o garçom escolher** (a comanda da rota é a que fica), com o front sugerindo a mais antiga.

---

## 3. Nomes novos para o glossário do CLAUDE.md (pedir aprovação)

| Português | Código proposto |
|---|---|
| Transferir itens | `transferItemsTo()` (já no glossário do plano, falta no CLAUDE.md) |
| Juntar comandas | `mergeWith()` (idem) |
| Comanda absorvida na junção | `mergedTab` (parâmetro) / `mergedIntoTabId` (ponteiro) |
| Origem do item transferido | `transferredFromTabId` (`transferredAt`, `transferredBy`) |
| Trocar de mesa | `moveToTable` (rota `/move`) |
| Evento do item transferido | `TabItemTransferred` (mensagem STOMP `TRANSFERRED`) |

Nomes técnicos, que não precisam de glossário mas convém registrar: `TabTransferService`, `TabTransferController`, `TabTransferResponse`, `TabTransferView`, `TabsForTransfer`, e os códigos `INVALID_TAB_TRANSFER` e `INVALID_TAB_MERGE`. Os enums não ganham valor novo, só os predicados `acceptsItemTransfer`, `acceptsMerge` e `acceptsTransfer`.

---

## 4. Riscos, pontos de choque e paralelismo

**Riscos no código existente**
- **Coluna `tab_id` bloqueada no mapeamento** (`Tab.java`, `@JoinColumn(... updatable = false)`). Duas saídas:
  - (a) a recomendada: `TabItem` mapeia `tab_id` como coluna básica com `insertable = false`, gravada pelo `transferTo`. É preciso confirmar no Hibernate 7 que não aparece o erro de coluna repetida.
  - (b) o reserva: um `UPDATE tab_item SET tab_id = :to WHERE id IN (:ids) AND tab_id = :from` nativo no repositório, conferindo o número de linhas.

  Ligar `updatable = true` na lista funciona, mas soma um `UPDATE` redundante a cada item lançado. É exatamente o que a 2.2 evitou. Qualquer que seja a saída, um teste de integração precisa ler a linha no banco.
- **Trava do item filtra por `tab_id`** (`SpringDataTabRepository.lockItemForUpdate`). É a causa da limitação da 3.5. A nova tentativa em `findByItemIdForItemChange` corrige, e o teste (e) prova. `deliver` e `cancelItem` recebem o `tabId` do front, então item movido responde `TAB_ITEM_NOT_FOUND`: correto, a tela estava velha.
- **Deadlock com duas comandas.** Sem a ordem por UUID, juntar A em B ao mesmo tempo que B em A trava o banco (os dois `FOR UPDATE` cruzados com `FOR KEY SHARE`). A ordem fica no `JpaTabRepository`, não no service, que não pode ter `if` de regra. Transição do KDS, lançamento e cancelamento travam uma comanda só e depois o item, então não fecham ciclo com a transferência.
- **Carregar depois de travar.** A segunda comanda precisa ser lida do banco depois da sua trava. O padrão atual (trava nativa + `findById`) serve, desde que nenhuma das duas já esteja no contexto de persistência antes.
- **`updatedAt` do ticket.** Só avança se a entidade `TabItem` ficar suja. Os `transferred_*` garantem; sem eles, o cliente STOMP descartaria a mensagem como velha.
- **Crédito de comanda reaberta.** Transferir itens para fora de uma comanda reaberta com pagamento piora a limitação já aceita na 3.2: saldo negativo, a comanda não fecha, e o `ADMIN` faz `refund`. Registrar como limitação.
- **Choque com a 3.2:**
  - `Tab.java` (bloco novo; `TabStatus` só ganha predicados);
  - o `.http` 34 e os testes `TabClosing*` não mudam;
  - o `ck_tab_closing` não é afetado, porque só `OPEN` transfere;
  - a descrição `"Tab table <id>"` do `TabCharge` sai com a mesa nova depois de um `/move`, o que está certo.
- **Choque com a 3.5:**
  - `KitchenDisplayBroadcaster` e `KitchenDisplayMessage` ganham um ouvinte e um tipo (contrato novo para a 4.5);
  - `JpaTabRepository.findByItemIdForItemChange` passa a repetir a leitura;
  - `TabItemResponse` ganha campos.

  Nada no `KitchenDisplayService`.
- **Mesa desativada.** Não há caso novo: a 2.2 #8 impede desativar mesa com comanda ativa, e o `/move` recusa destino inativo pelo `openForTable`. O `/move` vira o caminho para liberar uma mesa antes de desativá-la. A corrida desativar × abrir continua como limitação da 2.2.

**Paralelismo**
- **Pode rodar em paralelo:**
  - hotel 1.1, 2.1, 2.3 (módulo `hotel`), **desde que a 3.6 fique sem migration**. Com migration, decida antes o número (V11) e renumere o hotel no `MIGRATIONS.md` antes de abrir as duas;
  - 2.5;
  - front 4.1, 4.2, 4.6, 4.7.
- **Não em paralelo:** a 3.3 (ponte `Tab` → `Folio`), que mexe em `Tab.close`, `TabBilling`, `TabClosingService` e `TabResponse`, o mesmo `Tab.java`. Na prática ela ainda nem é executável, porque depende da 3.1 (hotel). Se forem juntas, a 3.6 entra primeiro.
- **Contrato primeiro:** 4.3 (salão) e 4.5 (KDS) consomem as rotas `/transfer`, `/merge`, `/move` e o tipo `TRANSFERRED`. Podem correr em paralelo se o contrato da §6/§8 for congelado antes.
- **Billing:** nenhuma task pendente conflita. A 3.6 só usa `TabBilling.closeFolio`, que já existe.

---

### Arquivos críticos para a implementação
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/domain/Tab.java
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/domain/TabItem.java
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/infra/JpaTabRepository.java (com SpringDataTabRepository.java)
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/web/KitchenDisplayBroadcaster.java (com KitchenDisplayMessage.java)
- /home/ruan/castel/docs/schema-banco-de-dados.md (§11, §13) e /home/ruan/castel/docs/MIGRATIONS.md, só se houver V11
