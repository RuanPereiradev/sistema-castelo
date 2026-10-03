# Task 3.6 — Comanda: transferência de itens, junção e troca de mesa

**Objetivo:** mover linhas inteiras de uma comanda `OPEN` para outra `OPEN`
(`transferItemsTo`), juntar uma comanda `OPEN` noutra (`mergeWith`, a absorvida
vai a `MERGED`) e trocar a comanda de mesa (`moveToTable`, abrir na mesa nova +
juntar, numa transação), gravando o histórico completo de cada movimento e
avisando o KDS da mesa nova.

**Decisões:** `docs/decisions/task-3.6.md` (T1–T14 e T11b, todas confirmadas na
rodada 0). As referências `T<n>` neste arquivo apontam para aquela tabela.

Herdadas de outras tasks:
- 2.2 #1: uma comanda ativa por mesa/cartão; grupos na mesma mesa = divisão (3.2) e transferência.
- 2.2 #8: mesa com comanda ativa não desativa (a troca de mesa passa a ser o caminho para liberar uma mesa).
- 2.2 #11: a linha do item é imutável depois de lançada.
- 2.2 #12: `transferred_*` e `merged_*` já existem na V7, sem mapeamento.
- 2.2 #13: `serviceChargeable` é o retrato do lançamento e nunca muda.
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
- `Tab.transferItemsTo(destination, itemIds, by, at)`: linhas inteiras, tudo ou nada (T4).
- `Tab.mergeWith(mergedTab, billing, by, at)`: todos os itens ativos da absorvida vão para esta; a absorvida vai a `MERGED` com `merged_into_tab_id`, `merged_at`, `merged_by`. A comanda da rota é a que **fica** (T14).
- `moveToTable` (caso de uso): abre comanda na mesa de destino e junta a atual nela, numa transação (T5).
- **Tabela nova `tab_item_transfer`**, uma linha por movimento, com o histórico completo de saltos (T11), e a migration **`V11__tab_item_transfer.sql`** (T11b).
- **Renumeração do hotel** no `docs/MIGRATIONS.md`: 1.1 passa a V12 e 2.1 a V13 (T11b).
- Mapear `tab_item.transferred_from_tab_id`, `transferred_at`, `transferred_by` — atalho do **último** salto, lido pela tela e pela pré-conta sem `JOIN` (T11) — e `tab.merged_into_tab_id`, `merged_at`, `merged_by`.
- `TabStatus.acceptsItemTransfer()` e `acceptsMerge()`; `TabItemStatus.acceptsTransfer()`.
- Evento `TabItemTransferred`, um por item movido (transferência, junção, troca de mesa); o broadcaster empurra `TRANSFERRED`.
- `findByItemIdForItemChange` repete a leitura do `tab_id` quando o item mudou de comanda no meio (limitação da 3.5).
- `TabTransferService`, `TabTransferController`, DTOs; `TabResponse`/`TabItemResponse` com os campos novos.
- §11 e §13 do schema atualizados (tabela nova; ciclo impossível pelo status).
- `http/36-restaurant-tab-transfer.http` e a linha no `http/README.md` (orquestrador).
- Cenário `FOLIO_OWNED_BY_TAB` numa comanda real no `http/34` (T16).
- Testes de unidade (agente de teste) e integração (DEV) com concorrência.

**Fora**
- Transferir **parte** da quantidade de uma linha (T4) — movido para task própria, a definir.
- Desfazer junção; reabrir `MERGED` (T10).
- Transferir de/para comanda `CLOSING`, `CLOSED`, `CANCELLED`, `MERGED` (T1).
- Mover crédito de folio entre comandas (T2).
- Relatório "itens que saíram desta comanda" — a tabela sustenta a consulta, a tela não entra aqui.
- Uniformização das permissões das rotas de comanda (T12) — task própria.
- Telas (4.3, 4.5). `CLAUDE.md`, `docs/MIGRATIONS.md`: orquestrador.

---

## 2. Modelo de dados

A 3.6 **tem migration** (T11, T11b): `V11__tab_item_transfer.sql`.

```sql
CREATE TABLE tab_item_transfer (
    id             UUID PRIMARY KEY,
    tab_item_id    UUID        NOT NULL REFERENCES tab_item(id),
    from_tab_id    UUID        NOT NULL REFERENCES tab(id),
    to_tab_id      UUID        NOT NULL REFERENCES tab(id),
    kind           VARCHAR(20) NOT NULL
                   CHECK (kind IN ('TRANSFER','MERGE','MOVE')),
    transferred_by UUID        NOT NULL,
    transferred_at TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by     UUID,
    updated_at     TIMESTAMPTZ,
    updated_by     UUID,
    CONSTRAINT ck_tab_item_transfer_distinct CHECK (from_tab_id <> to_tab_id)
);

CREATE INDEX idx_tab_item_transfer_by_item ON tab_item_transfer (tab_item_id, transferred_at);
CREATE INDEX idx_tab_item_transfer_from    ON tab_item_transfer (from_tab_id);
CREATE INDEX idx_tab_item_transfer_to      ON tab_item_transfer (to_tab_id);
```

Notas de desenho:
- A tabela é **append-only**: nenhuma linha é alterada ou apagada, nem quando a
  comanda é cancelada. É o livro do rastro, e a pergunta que ela responde é
  "por que a mesa 4 fechou com menos" depois de qualquer número de saltos.
- `transferred_by`/`transferred_at` são as colunas de negócio (o autor e o
  instante do movimento, T13). O bloco de auditoria dos quatro campos entra
  porque o `CLAUDE.md` o exige em toda tabela transacional, e numa tabela
  append-only ele repete o par acima; `updated_*` fica sempre nulo.
- Sem `ON DELETE CASCADE`: nada em `tab_item` é apagado no sistema.
- `kind` (T15) diz qual operação moveu o item: `TRANSFER`, `MERGE` ou `MOVE`.
  Sem ela uma troca de mesa de oito itens ficaria indistinguível de oito
  transferências avulsas. Quem chama sabe qual é — nada entra na API.

Colunas já existentes na V7, agora mapeadas:
- `tab.merged_into_tab_id`, `merged_at`, `merged_by`, `ck_tab_merged`, `ck_tab_no_self_merge`, `idx_tab_merged_into`;
- `tab_item.transferred_from_tab_id`, `transferred_at`, `transferred_by`, `ck_tab_item_transferred`.

Mapeamento:
- `Tab`: `mergedIntoTabId` (UUID), `mergedAt`, `mergedBy`.
- `TabItem`: `transferredFromTabId`, `transferredAt`, `transferredBy` e **`tabId` como coluna
  básica própria**, `@Column(name = "tab_id", nullable = false)` com `insertable` e `updatable`
  nos padrões. O `@JoinColumn` de `Tab.items` virou `insertable = false, updatable = false`:
  um único escritor da coluna, e mover uma linha é **um** `UPDATE` do filho. `Tab.addItem`
  chama `item.attachTo(id)` para o `INSERT` gravar a coluna. Ligar `updatable = true` na
  coleção também funcionaria, mas somaria um `UPDATE` redundante a cada item lançado, que é
  exatamente o que a 2.2 evitou.
- `TabItemTransfer`: entidade própria, **fora do agregado `Tab`**, com repositório
  próprio. O agregado devolve as linhas a gravar; o caso de uso as salva.

**Ordem de merge:** com o `outOfOrder` desligado, a V11 da 3.6 aplicada depois de
uma V11 de hotel faria o Flyway recusar. A 3.6 entra na `main` **antes** das
tasks de hotel, e a renumeração (1.1 → V12, 2.1 → V13) é registrada no
`MIGRATIONS.md` nesta task, antes de elas abrirem (T11b).

---

## 3. Invariantes

**Transferência — `source.transferItemsTo(destination, itemIds, by, at)`**, nesta ordem:
1. `destination` ≠ `this` → `INVALID_TAB_TRANSFER` (422).
2. `itemIds` não vazio (duplicados colapsam) → `INVALID_TAB_TRANSFER`.
3. Origem `acceptsItemTransfer()` (só `OPEN`) → `TAB_NOT_OPEN` (T1).
4. Destino `acceptsItemTransfer()` → `TAB_NOT_OPEN` (o detalhe diz qual comanda).
5. **Todos** os itens existem na origem → `TAB_ITEM_NOT_FOUND`.
6. **Todos** ativos (`TabItemStatus.acceptsTransfer()` = `isActive()`) → `TAB_ITEM_ALREADY_CANCELLED`.
   Item cancelado fica na comanda onde foi cancelado.
7. Aplica, item a item, sem tocar em nada congelado:
   - `lineTotal`, preço, adicionais, `serviceChargeable`, `prepStation`, `status`
     e os instantes do KDS viajam como estão. Item por peso (nasce `DELIVERED`) pode ir.
   - `transferredFromTabId = source`, `transferredAt`, `transferredBy` (sobrescreve a origem anterior: é o atalho do último salto, T11).
   - uma linha nova em `tab_item_transfer` por item movido, com o `kind` da operação (T15), que **não** é sobrescrita (T11).
   - `splitGroup` volta a `1` (T9) — **menos na troca de mesa**, onde a divisão por item
     sobrevive, porque é uma festa só e a comanda nova não tem outro grupo (T18).
   - `serviceChargeWaived`: mantém; e, se a origem está com a taxa desligada
     (`serviceChargeApplied = false`) e o item é `serviceChargeable`, passa a `true`,
     para o valor efetivo do item não mudar em silêncio (T7).
8. Devolve, por item, a linha de `tab_item_transfer` e um `TabItemTransferred`.
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
6. `guestCount`: soma quando as duas têm; senão fica o desta (T8). **Na troca de mesa** a
   comanda nova herda o da absorvida, porque nasce sem número e são as mesmas pessoas (T17).
   A soma respeita o teto de 999 da coluna: 600 + 600 fica 999.
7. `mergedTab` → `MERGED`, `merged_into_tab_id = this`, `merged_at`, `merged_by`.
8. `serviceChargeApplied` desta não muda; a absorvida com a taxa desligada leva os
   itens com `serviceChargeWaived` (regra 7, T7).
- **`MERGED` é final** (T10): recusa lançar, cancelar item, cancelar, taxa, grupos,
  pessoas, fechar, pagar, transferir, juntar (os predicados já existentes respondem
  `false`). Libera mesa e cartão (`holdsItsPlace = false`, fora dos índices parciais).
  Não sobra item ativo nela para o KDS avançar: todos saíram.
- **Sem ciclo:** o destino é sempre `OPEN` e a absorvida vira `MERGED`, que nunca é
  destino. A → B → A é impossível pelo status (fecha a linha da §13).

**Troca de mesa — `TabTransferService.moveToTable(tabId, diningTableId)`** (T5)
- `Tab.openForTable(destinationTable)` (`INACTIVE_DINING_TABLE`, e o índice parcial
  responde `TAB_ALREADY_OPEN_FOR_DINING_TABLE`) + `newTab.mergeWith(current)`, numa
  transação.
- Cartão → mesa usa o mesmo caminho.
- O rastro: a antiga fica `MERGED` apontando para a nova, os itens com
  `transferred_from_tab_id` e uma linha por item em `tab_item_transfer`.
- A comanda muda de `id`; o front segue o `mergedIntoTabId` (limitação aceita, T5).
- O que o operador escolheu vem junto: o `guestCount` (T17), a divisão por item e a taxa de
  serviço que ele desligou (T18). A comanda nova é artefato de implementação, não um grupo
  novo, então nada disso pode cair em silêncio — uma comanda de mesa nasce cobrando taxa, e
  sem isso a cobrança reapareceria sozinha no próximo item lançado.
- O status da comanda é checado **antes** da mesa de destino (T19): comanda em `CLOSING`
  responde `TAB_NOT_OPEN`, não `INACTIVE_DINING_TABLE`.
- O `openedAt`/`openedBy` vem da comanda antiga (T21): é quando a festa sentou, e a lista do
  salão ordena por isso. Quem trocou fica no `merged_by` e no rastro.
- **Um lugar só decide o que viaja** (T20): a fábrica privada `Tab.continuationOf(...)`. O
  `TabFieldCarryOverTest` classifica os 21 campos do `Tab` por reflexão e quebra quando
  aparece campo novo sem decisão, para não haver um quarto caso de valor perdido em silêncio.
- Comanda com folio cai na regra da T2.

**Taxa de serviço e origem** (T3)
- `serviceChargeable` é o retrato do lançamento (2.2 #13) e nunca muda.
  - Mesa → cartão: a comanda de self-service não cobra (`serviceChargeApplied = false`), e o item perde a taxa.
  - Cartão → mesa: o item nasceu sem taxa e continua sem. Religar dá `TAB_ITEM_NOT_SERVICE_CHARGEABLE` (F3).
- Taxa calculada uma vez sobre a soma no destino (3.2 #5): transferir não soma taxas por item.

---

## 4. Assinaturas públicas

```java
// Tab
TabTransferResult transferItemsTo(Tab destination, Set<TabItemId> itemIds, UUID by, Instant at);
TabTransferResult mergeWith(Tab mergedTab, TabBilling billing, UUID by, Instant at);
Optional<TabId> mergedIntoTabId(); Optional<Instant> mergedAt(); Optional<UUID> mergedBy();

// TabItem (pacote)
void transferTo(TabId destination, TabId source, boolean waiveServiceCharge, UUID by, Instant at);
// público
Optional<TabId> transferredFromTabId(); Optional<Instant> transferredAt(); Optional<UUID> transferredBy();

// TabItemTransfer (restaurant.domain) — entidade append-only, fora do agregado Tab
static TabItemTransfer record(TabItemId item, TabId from, TabId to, TabTransferKind kind, UUID by, Instant at);
TabItemTransferId id(); TabItemId tabItemId(); TabId fromTabId(); TabId toTabId();
TabTransferKind kind(); UUID transferredBy(); Instant transferredAt();

// TabTransferKind (restaurant.domain) — TRANSFER · MERGE · MOVE (T15), @Enumerated(STRING)

// TabStatus
acceptsItemTransfer() (OPEN) · acceptsMerge() (OPEN)
// TabItemStatus
acceptsTransfer() (tudo menos CANCELLED)

// Evento (restaurant.domain, DomainEvent)
record TabItemTransferred(TabId fromTabId, TabId toTabId, TabItemId itemId, PrepStation station,
                          TabItemStatus status, Instant occurredAt)
    boolean reachesKitchenQueue();   // status.isOnKitchenQueue()
    boolean isReady();               // status == READY

// Repositórios
TabsForTransfer findForTransfer(TabId source, TabId destination, Set<TabItemId> itemIds);  // TabRepository
TabsForTransfer findForMerge(TabId receiving, TabId merged);                                // TabRepository
void saveAll(List<TabItemTransfer> transfers);                                              // TabItemTransferRepository
// A leitura do rastro não entra aqui: o relatório que a consome está fora do escopo (§1),
// e um método de repositório sem chamador é código morto. Chega com aquele relatório.

// Exceções: InvalidTabTransferException ("INVALID_TAB_TRANSFER", 422),
//           InvalidTabMergeException ("INVALID_TAB_MERGE", 422)

// restaurant.application
class TabTransferService {
    TabTransferView transfer(TabId source, TabId destination, Set<TabItemId> itemIds);
    Tab merge(TabId receiving, TabId merged);
    Tab moveToTable(TabId tabId, DiningTableId diningTableId);
}
```

Nomes técnicos (fora do glossário, registrados na decisão #16): `TabTransferService`,
`TabTransferController`, `TabTransferResponse`, `TabTransferView`, `TabsForTransfer`,
`TabTransferResult` (o par "linhas de rastro + eventos" devolvido pelo agregado),
`TabItemTransferId`. `TabTransferKind` é de domínio e entra no glossário com a T15.

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

Perfis: `WAITER`, `ADMIN` e **`FRONT_DESK`**; `KITCHEN` → 403 (T12). É a primeira
rota de comanda aberta à recepção, exceção anotada ao padrão da 2.2 #3.

```
POST /api/restaurant/tabs/{tabId}/transfer  {toTabId, itemIds:[...]}  200 TabTransferResponse
POST /api/restaurant/tabs/{tabId}/merge     {mergedTabId}             200 TabResponse (a que fica)
POST /api/restaurant/tabs/{tabId}/move      {diningTableId}           201 TabResponse (a nova)
```

- `TabTransferResponse {source: TabResponse, destination: TabResponse}`: o front redesenha as duas.
- `TabResponse` ganha `mergedIntoTabId, mergedAt, mergedBy`. Um `GET` numa `MERGED`
  responde 200 com o ponteiro, e o front segue.
- Item ganha `transferredFromTabId, transferredAt, transferredBy` (último salto).
- Totais ao vivo (D20 da 3.2): o controller lê o percentual **antes** da escrita (D25).
- `toTabId`/`mergedTabId`/`diningTableId` ausentes → 400; `itemIds` ausente → `INVALID_TAB_TRANSFER`.
- Nenhuma das três rotas pede motivo (T13).
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
   que é compatível com a trava já tomada. O `INSERT` em `tab_item_transfer` vem depois,
   na mesma transação, e só toma `FOR KEY SHARE` nas duas comandas e no item, todos
   já travados — sem trava nova e sem ordem nova.
4. **KDS (limitação da 3.5).** `findByItemIdForItemChange` passa a repetir: lê o
   `tab_id`, trava a comanda, trava o item com `tab_id` = o lido. Se não vier linha,
   lê o `tab_id` de novo e repete (até 3 vezes); só então `TAB_ITEM_NOT_FOUND`.
   Depois de travado o item, ele não muda de comanda: a transferência precisa do
   `FOR UPDATE` do item e a junção do `FOR UPDATE` da comanda.
5. **Trava antes da recusa.** O repositório trava as duas comandas e os itens antes de o
   agregado checar destino ≠ origem e lista não vazia (§3, regras 1 e 2). Uma requisição
   malformada paga trava para receber 422. Fica assim: antecipar a checagem exigiria `if` de
   regra de negócio dentro do `@Service`, que o `CLAUDE.md` proíbe — a invariante mora no
   agregado. A transação cai e solta tudo, então não há bug, só contenção que ninguém legítimo
   paga.
6. **Acúmulo de travas na nova tentativa do KDS.** Uma tentativa perdida deixa o
   `FOR KEY SHARE` na comanda que ela olhou, então depois de duas corridas o caminho segura a
   trava de duas comandas fora da ordem crescente que o resto da task impõe. É inofensivo
   porque esse caminho **nunca** pede trava exclusiva de comanda, e `FOR KEY SHARE` não
   conflita com `FOR KEY SHARE`: ele nunca é a ponta que espera num ciclo. Mudança futura que
   o fizesse tomar `FOR UPDATE` numa comanda teria de passar pela ordem compartilhada.
7. **Compatibilidade:**
   - `startClosing`/`reopen`/`close`/`cancel` (`FOR UPDATE`) e transferência/junção se esperam.
     Quem chega depois vê o status novo (`TAB_NOT_OPEN`).
   - Pagamento (`FOR KEY SHARE`) numa `OPEN` é `TAB_NOT_CLOSING` de qualquer jeito.
   - A rota do billing trava só o folio, e a junção trava comanda → folio: sem ciclo.

---

## 8. Eventos e KDS

- `TabTransferService` publica os `TabItemTransferred` que o agregado devolve, e grava
  as linhas de `tab_item_transfer` na mesma transação.
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

**Preparação:** logins (`admin`, `garcom`, `recepcao`, `cozinha`); mesas A, B, C com
sufixo aleatório; itens: pizza (`PIZZA`), bebida (`BAR`), um sem taxa, buffet por peso;
comanda na mesa A com pizza, bebida ×2 e prato; comanda na mesa B com um item; cartão
aleatório.

**Caminho feliz:**
1. Transfere a pizza A → B: subtotais conferidos (A cai o `lineTotal`, B sobe), `transferredFromTabId = A`, `splitGroup = 1`.
2. `start` na pizza pela cozinha continua funcionando; a fila `PIZZA` mostra a mesa B.
3. Tira a taxa da comanda A, transfere a bebida A → B: o item chega com `serviceChargeWaived = true`.
4. `guestCount` 2 em A e 3 em B; junta A em B: A `MERGED` com `mergedIntoTabId = B`, B com `guestCount = 5`; `GET /tabs?diningTableId=A` vazio; abrir comanda nova na mesa A → 201.
5. Troca B para a mesa C (`/move`): nova comanda em C com os itens; B `MERGED`; mesa B livre.
6. Transfere mesa → cartão: taxa zero no cartão.
7. **Rastro de dois saltos:** o item que foi A → B → C tem `transferredFromTabId = B`
   (último salto) e **duas** linhas em `tab_item_transfer`; a recepção (`FRONT_DESK`)
   executa uma das transferências com 200.

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

**`http/34`, bloco aditivo no fim (T16):** cenário `FOLIO_OWNED_BY_TAB` numa comanda
real, fechando a pendência herdada da rodada 2 da 3.2 — hoje o código de erro só é
exercitado com folio sintético. Única alteração desta task em arquivo de outra; nada
nos blocos existentes muda.

---

## 10. Testes

**Unidade (agente de teste), densa, com `FakeTabBilling`:**
- *Matriz de status:* origem × destino em {OPEN, CLOSING, CLOSED, CANCELLED, MERGED} para transferir e juntar (só OPEN × OPEN passa); `MERGED` × toda operação existente (lançar, cancelar item, cancelar, taxa, grupo, pessoas, startClosing, pagar, reabrir, fechar, transições do KDS em item que ficou).
- *Item:* status × transferir (5 casos; cancelado recusa; por peso aceita); tudo ou nada (um item inexistente não move nenhum); ordem das checagens.
- *Dinheiro:* conservação de Σ subtotal; `lineTotal` e adicionais intactos; base da taxa no destino (mesa→mesa, mesa→cartão = 0, cartão→mesa sem taxa); origem com a taxa desligada → item chega dispensado; taxa sobre a soma no destino (3 × 3,35 → 1,01).
- *Junção:* `guestCount` (3+2, null+2, 3+null) e, na troca de mesa, o número herdado (T17); folio da absorvida fechado com saldo zero e recusado com saldo ≠ 0 (nada mudou); cancelados ficam; absorvida sem item ativo; `merged_*` gravados; ciclo impossível.
- *Rastro:* uma linha de `tab_item_transfer` por item movido, com `from`/`to`/`kind`/autor/instante; a transferência avulsa grava `TRANSFER`, a junção `MERGE` e a troca de mesa `MOVE`; dois saltos geram duas linhas e o `transferredFromTabId` fica com o último; a junção gera uma linha por item ativo; nada é sobrescrito.
- *Eventos:* um por item movido, `from`/`to` certos, `reachesKitchenQueue`/`isReady`.

**Integração (DEV, `app`):**
- Fatia: transferir, juntar, trocar de mesa pela rota real; linha de `tab_item.tab_id` e as linhas de `tab_item_transfer` conferidas no banco; 403 da cozinha e 200 da recepção.
- **Concorrência (Testcontainers):**
  (a) transferir × `startClosing` da origem: o `TabCharge` = total dos itens que ficaram, ou a transferência recebe `TAB_NOT_OPEN`;
  (b) 5 lançamentos na absorvida × junção: nenhum item ativo em comanda `MERGED`;
  (c) duas transferências do mesmo item para destinos diferentes: um 200 e um `TAB_ITEM_NOT_FOUND`, e **uma** linha de rastro;
  (d) juntar A em B × juntar B em A ao mesmo tempo: um 200, um `TAB_NOT_OPEN`, sem deadlock;
  (e) `ready` da cozinha × transferência do mesmo item: nenhum 500, a cozinha avança depois de repetir a leitura (§7.4).
- WebSocket: uma mensagem `TRANSFERRED` no setor certo, com o rótulo da mesa nova.

Orçamento: produção estimada ~830 linhas (agregado ~160, `TabItemTransfer` + id ~70,
repositórios ~110, migration ~25, serviço ~110, web/DTOs ~160, broadcaster/evento ~60,
exceções ~40, schema/MIGRATIONS à parte); testes ~830 (alvo 1:1 do `CLAUDE.md`).

---

## 11. Riscos e pontos de choque

**Riscos no código existente**
- **Coluna `tab_id` bloqueada no mapeamento** (`Tab.java`, `@JoinColumn(... updatable = false)`). Duas saídas:
  - (a) a recomendada: `TabItem` mapeia `tab_id` como coluna básica com `insertable = false`, gravada pelo `transferTo`. É preciso confirmar no Hibernate 7 que não aparece o erro de coluna repetida.
  - (b) o reserva: um `UPDATE tab_item SET tab_id = :to WHERE id IN (:ids) AND tab_id = :from` nativo no repositório, conferindo o número de linhas.

  Ligar `updatable = true` na lista funciona, mas soma um `UPDATE` redundante a cada item lançado. É exatamente o que a 2.2 evitou. Qualquer que seja a saída, um teste de integração precisa ler a linha no banco.
- **Trava do item filtra por `tab_id`** (`SpringDataTabRepository.lockItemForUpdate`). É a causa da limitação da 3.5. A nova tentativa em `findByItemIdForItemChange` corrige, e o teste (e) prova. `deliver` e `cancelItem` recebem o `tabId` do front, então item movido responde `TAB_ITEM_NOT_FOUND`: correto, a tela estava velha.
- **Deadlock com duas comandas.** Sem a ordem por UUID, juntar A em B ao mesmo tempo que B em A trava o banco (os dois `FOR UPDATE` cruzados com `FOR KEY SHARE`). A ordem fica no `JpaTabRepository`, não no service, que não pode ter `if` de regra. Transição do KDS, lançamento e cancelamento travam uma comanda só e depois o item, então não fecham ciclo com a transferência.
- **Carregar depois de travar.** A segunda comanda precisa ser lida do banco depois da sua trava. O padrão atual (trava nativa + `findById`) serve, desde que nenhuma das duas já esteja no contexto de persistência antes.
- **`updatedAt` do ticket.** Só avança se a entidade `TabItem` ficar suja. Os `transferred_*` garantem; sem eles, o cliente STOMP descartaria a mensagem como velha.
- **Crédito de comanda reaberta.** Transferir itens para fora de uma comanda reaberta com pagamento piora a limitação já aceita na 3.2: saldo negativo, a comanda não fecha, e o `ADMIN` faz `refund`. Registrada como limitação na 3.6.
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
- **Bloqueado pela migration (T11b):** hotel 1.1 e 2.1 **não** rodam antes da 3.6
  entrar na `main`. A renumeração para V12/V13 é feita nesta task; as tasks de hotel
  abrem depois do merge.
- **Pode rodar em paralelo:** 2.3 e 2.5 (sem migration nova), front 4.1, 4.2, 4.6, 4.7.
- **Não em paralelo:** a 3.3 (ponte `Tab` → `Folio`), que mexe em `Tab.close`, `TabBilling`, `TabClosingService` e `TabResponse`, o mesmo `Tab.java`. Na prática ela ainda nem é executável, porque depende da 3.1 (hotel). Se forem juntas, a 3.6 entra primeiro.
- **Contrato primeiro:** 4.3 (salão) e 4.5 (KDS) consomem as rotas `/transfer`, `/merge`, `/move` e o tipo `TRANSFERRED`. Podem correr em paralelo se o contrato da §6/§8 for congelado antes.
- **Billing:** nenhuma task pendente conflita. A 3.6 só usa `TabBilling.closeFolio`, que já existe.

---

## 12. Em aberto antes da primeira linha de código

Nenhum ponto em aberto. As duas perguntas que a reescrita da spec levantou foram
decididas: a coluna `kind` da tabela de rastro entra (T15) e o cenário
`FOLIO_OWNED_BY_TAB` numa comanda real entra no `http/34` (T16).

---

### Arquivos críticos para a implementação
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/domain/Tab.java
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/domain/TabItem.java
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/infra/JpaTabRepository.java (com SpringDataTabRepository.java)
- /home/ruan/castel/restaurant/src/main/java/br/com/castel/restaurant/web/KitchenDisplayBroadcaster.java (com KitchenDisplayMessage.java)
- /home/ruan/castel/app/src/main/resources/db/migration/V11__tab_item_transfer.sql (novo)
- /home/ruan/castel/docs/schema-banco-de-dados.md (§11, §13) e /home/ruan/castel/docs/MIGRATIONS.md
