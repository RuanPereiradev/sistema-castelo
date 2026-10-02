# Decisões — Task 3.6 Comanda: transferência de itens e junção

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/3.6-tab-transfer-merge` |
| Rodada atual | 0 (decisões de negócio fechadas; spec final escrita) |
| Spec | `docs/task-3.6-tab-transfer-merge.md` — final, reescrita a partir das T1–T14 em 2026-10-02 |
| Build | não rodado — nenhuma linha de código escrita |
| Testes | 0 |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **T1** — Transferir de ou para comanda em `CLOSING` é recusado com `TAB_NOT_OPEN`: só comanda `OPEN` transfere. O caminho é `reopen` → transferir → fechar de novo, e cada passo fica registrado no folio. Nada de estornar e relançar `TabCharge` dentro da transferência — coerente com a F13 da 3.2. | pendente |
| 2 | 0 | **T2** — Juntar comanda com folio aberto: saldo zero fecha o folio da absorvida e a junção segue; saldo ≠ 0 recusa com `FOLIO_BALANCE_NOT_ZERO`, e o `ADMIN` faz `refund` antes. Mesma regra do cancelamento (invariante 17 / D4 da 3.2). Nada novo no billing: transferir pagamento entre folios fica fora. | pendente |
| 3 | 0 | **T3** — Transferir e juntar entre `TABLE_SERVICE` e `SELF_SERVICE` é permitido, com o `serviceChargeable` do item **congelado** no retrato do lançamento (2.2 #13, regra de preço 1.4 do schema). Mesa → cartão perde a taxa; cartão → mesa fica sem taxa. Atende o cliente do buffet que senta para ser servido. | pendente |
| 4 | 0 | **T4** — Transferência move **linha inteira**; partir a quantidade de uma linha fica fora. A linha segue imutável (2.2 #11) e a comanda append-only. *Decisão delegada a mim pelo Ruan ("sua recomendação"), não escolhida por ele — revisível sem ônus.* | pendente |
| 5 | 0 | **T5** — Trocar de mesa **entra na 3.6**, como `POST /api/restaurant/tabs/{tabId}/move`, implementada por dentro como `openForTable(mesa nova)` + `mergeWith(comanda atual)` numa transação. Rastro sem coluna nova: a antiga fica `MERGED` apontando para a nova, e os itens com `transferred_from_tab_id`. A comanda muda de `id` e o front segue o ponteiro; comanda com folio cai na regra da T2. | pendente |
| 6 | 0 | **T6** — Comanda de origem que fica sem item **continua `OPEN`**, ocupando a mesa; o garçom cancela (com motivo, como hoje) ou junta. Sem cancelamento automático, para manter a trava fraca na origem e não fazer lançamento esperar a transferência. **Ressalva:** no momento da escolha o outro argumento era evitar migration — a T11, decidida depois, trouxe migration de todo modo, então esse argumento caiu e sobrou só a contenção. O front sugere "juntar" quando todos os itens estão marcados. | pendente |
| 7 | 0 | **T7** — Item que sai de comanda com a taxa dispensada (`serviceChargeApplied = false`) e é `serviceChargeable` chega ao destino com `serviceChargeWaived = true`: a dispensa viaja com o item. O garçom do destino religa se quiser, sem motivo (F2 da 3.2). Religar é visível; a cobrança reaparecer sozinha não seria. | pendente |
| 8 | 0 | **T8** — `guestCount` na junção: **soma** quando as duas comandas têm número; se só uma tem, fica o da comanda que fica. Só serve de padrão para a divisão igual (F8 da 3.2) e o garçom corrige a qualquer momento. *Decisão delegada a mim pelo Ruan ("decida por mim") — revisível sem ônus.* | pendente |
| 9 | 0 | **T9** — O item transferido ou juntado **volta ao `splitGroup` 1**, o grupo padrão. O número do grupo só tem sentido dentro da comanda de origem; mantê-lo faria o item cair na conta de gente sem relação com ele no destino, e isso só apareceria na pré-conta. O garçom reagrupa no destino se quiser dividir. | pendente |
| 10 | 0 | **T10** — Não existe desfazer junção: `MERGED` é final, como `CLOSED`. O conserto é abrir comanda nova e transferir os itens de volta, e o rastro mostra os dois movimentos. Um desfazer automático erraria em silêncio nos casos encadeados, onde a origem do item já foi sobrescrita. | pendente |
| 11 | 0 | **T11** — Rastro **completo**: tabela nova `tab_item_transfer`, uma linha por movimento (item, comanda de origem, comanda de destino, autor, instante). A 3.6 passa a ter **migration**, o hotel precisa ser renumerado e a 3.6 entra na `main` antes das tasks dele. O campo `transferred_from_tab_id` da V7 **continua sendo gravado** como atalho do último salto, lido pela tela e pela pré-conta sem `JOIN` — *esta parte é desenho meu, não escolha do Ruan*. | pendente |
| 12 | 0 | **T11b** — A migration da 3.6 é a **`V11__tab_item_transfer.sql`**; o hotel desce para V12 (1.1) e V13 (2.1) no `docs/MIGRATIONS.md`. Com o `outOfOrder` desligado, a 3.6 tem de entrar na `main` antes das tasks de hotel, e a renumeração fica registrada antes de elas abrirem. | pendente |
| 13 | 0 | **T12** — As três rotas aceitam `WAITER`, `ADMIN` **e `FRONT_DESK`**; `KITCHEN` recebe 403. É a primeira rota de comanda aberta à recepção, contra o padrão da 2.2 #3, e entra como exceção anotada — a uniformização das permissões de comanda fica para task própria. *O Ruan escolheu incluir o `FRONT_DESK`; a forma (exceção anotada em vez de revisão geral agora) foi delegada a mim.* | pendente |
| 14 | 0 | **T13** — Transferência, junção e troca de mesa **não pedem motivo**. Nenhuma delas muda o total do estabelecimento, e são operação de rotina; autor e instante ficam registrados nas colunas de `tab_item_transfer` e nos `merged_*`. Motivo obrigatório segue só no cancelamento, que destrói receita. | pendente |
| 15 | 0 | **T14** — Na junção o garçom escolhe: em `POST /api/restaurant/tabs/{tabId}/merge` a comanda do caminho **fica** e a do corpo é **absorvida**. O front sugere a mais antiga como padrão. Mantém a mesa onde o grupo realmente está, em vez de o sistema decidir pela data de abertura. | pendente |
| 17 | 0 | **T15** — `tab_item_transfer` ganha a coluna **`kind`** (`TRANSFER` · `MERGE` · `MOVE`), `VARCHAR(20) NOT NULL` com `CHECK`. Sem ela as três operações gravariam linhas idênticas e uma troca de mesa de oito itens ficaria indistinguível de oito transferências avulsas. A operação sabe qual é: nada entra na API. Pergunta nascida da reescrita da spec, respondida pelo Ruan em 2026-10-02. | pendente |
| 16 | 0 | **Glossário** — aprovados para a seção Restaurante do `CLAUDE.md`: `transferItemsTo()` (transferir itens), `mergeWith()` (juntar comandas), `mergedTab` / `mergedIntoTabId` (comanda absorvida), `moveToTable()` (trocar de mesa, rota `/move`), `TabItemTransfer` / tabela `tab_item_transfer` (movimento de item), `TabItemTransferred` com a mensagem `TRANSFERRED` (evento). Técnicos, fora do glossário: `TabTransferService`, `TabTransferController`, `TabTransferResponse`, `INVALID_TAB_TRANSFER`, `INVALID_TAB_MERGE`. | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

- [ ] Glossário do `CLAUDE.md` com os seis termos novos
- [ ] `transferItemsTo()` — transferência de itens entre comandas
- [ ] `mergeWith()` — junção de comandas, com a absorvida indo para `MERGED`
- [ ] `moveToTable()` / `POST /tabs/{tabId}/move` — trocar de mesa, por dentro abrir + juntar (T5)
- [ ] Tabela `tab_item_transfer` com o histórico completo de movimentos (T11) + migration
- [ ] `V11__tab_item_transfer.sql` (T11b), com a coluna `kind` (T15)
- [ ] Renumerar o hotel no `docs/MIGRATIONS.md`: 1.1 → V12, 2.1 → V13 (T11b)

### Movido para outra task

Só com aprovação explícita, e dizendo para qual task e por quê.

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Transferir parte da quantidade de uma linha | task própria, a definir | T4: pede regra nova para rateio de adicionais e status de preparo das duas metades; é a primeira exceção ao append-only | Ruan (delegou a decisão), rodada 0 |

---

## Contrato com o front

Tudo o que o front vai consumir. Depois do merge isso vira contrato e mudar
custa caro.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `TAB_NOT_OPEN` | 409 | T1 — origem ou destino fora de `OPEN` (`CLOSING` incluído) |
| `FOLIO_BALANCE_NOT_ZERO` | 409 | T2 — junção de comanda cujo folio tem saldo ≠ 0 |

**Rotas**

Perfis: `WAITER`, `ADMIN` e `FRONT_DESK` (T12); `KITCHEN` recebe 403.

| Rota | O que faz |
|---|---|
| `POST /api/restaurant/tabs/{tabId}/transfer` | Transfere linhas inteiras para outra comanda (T4) |
| `POST /api/restaurant/tabs/{tabId}/merge` | T14 — a comanda do caminho fica, a do corpo vira `MERGED` |
| `POST /api/restaurant/tabs/{tabId}/move` | T5 — troca a comanda de mesa; responde a comanda **nova**, com `id` diferente |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| | |

---

## Limitações conhecidas e aceitas

Coisas que sabemos que não estão perfeitas e decidimos aceitar. Registrar evita
que a próxima rodada de review levante de novo como se fosse novidade.

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| A 3.6 fica com um padrão de permissão diferente do resto das rotas de comanda | T12 — o Ruan quer a recepção operando, e esperar a uniformização atrasaria isso | Task própria revisando as permissões de comanda da 2.2 e da 3.2 juntas |
| Mover um grupo de divisão inteiro para outra comanda perde o agrupamento | T9 — preferível a misturar conta de gente diferente | Garçom reagrupa no destino |
| Comanda de destino pode ter item isento que o garçom dela não isentou | T7 — preferível a reaparecer 10% que ninguém decidiu | Pré-conta mostra a isenção por item |
| Mesa pode ficar ocupada por comanda vazia até alguém limpar | T6 — a alternativa custava contenção no lançamento (o argumento da migration caiu com a T11) | Front sugerir "juntar" quando todos os itens estão marcados |
| Trocar de mesa muda o `id` da comanda | T5 — é o preço de ter rastro sem migration; o front segue o `mergedIntoTabId` | — |
| Separar 2 de 3 unidades da mesma linha exige cancelar e relançar, deixando dois registros de cancelamento | T4: partir linha é task própria | Lançar em linhas separadas quando o garçom já sabe que vão se separar |
| O valor efetivo do item muda ao atravessar mesa ↔ cartão, sem ninguém decidir | T3 — é consequência do preço congelado, que vale no resto do sistema; recalcular taxa retroativa apareceria só na conferência de caixa | — |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

As quatorze perguntas do rascunho estão respondidas (T1–T14), mais a T11b que
nasceu da T11, e os nomes do glossário foram aprovados. A reescrita da spec
(2026-10-02) levantou duas que a rodada 0 não cobriu, as duas nascidas da tabela
de rastro da T11. A primeira já foi respondida e virou a **T15**; resta uma:

| # | Pergunta | Por que bloqueia |
|---|---|---|
| A2 | Cenário `FOLIO_OWNED_BY_TAB` no `http/34` (pendência herdada da rodada 2 da 3.2) | Está no cabeçalho do rascunho, mas não na lista de escopo aprovada |

A T6 pode ser reaberta de graça: o custo que a descartava em parte — a migration —
já foi pago pela T11.

Três decisões foram **delegadas a mim** e estão marcadas como tal na tabela: a
T4 (linha inteira), a T8 (`guestCount` somado) e a forma da T12 (exceção anotada
em vez de revisão geral das permissões). São revisíveis sem ônus.
