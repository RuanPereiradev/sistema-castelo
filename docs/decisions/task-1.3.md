# Decisões — Task 1.3 folio: lançamentos, estorno e pagamentos

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Base para a comanda fechar e receber (3.2). Roda em paralelo com a 1.4 e a 2.2.
**A 1.3 entra na `main` antes da 2.2**: a V6 precisa preceder a V7 no Flyway.

---

## Estado

| | |
|---|---|
| Branch | `task/1.3-billing-folio` |
| Rodada atual | 1 — spec aprovada, DEV e TEST em andamento |
| Build | — |
| Testes | — |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Pagamento acima do saldo: folio `TAB` **recusa** (o operador registra o devido; troco é físico); folio `STAY` **aceita**, e o saldo negativo é crédito do hóspede (sinal e adiantamento) | pendente |
| 2 | 0 | Estorno de pagamento **existe**, só `ADMIN`, com motivo: o pagamento vai de `CONFIRMED` a `REFUNDED` e deixa de abater o saldo. Nome no código: `refund`, que entra no glossário | pendente |
| 3 | 0 | Perfis: ler, receber, estornar lançamento e fechar = `ADMIN` e `FRONT_DESK`; ajuste e estorno de pagamento = só `ADMIN`; `WAITER` e `KITCHEN` = nada nesta task (o garçom recebe pelo fechamento da comanda, 3.2) | pendente |
| 4 | 0 | Fechar exige saldo **exatamente zero**. Diferença se resolve com `AdjustmentCharge` do `ADMIN`, com motivo, antes de fechar | pendente |
| 5 | 0 | Idempotência pelo header `Idempotency-Key` gerado pelo front: retry idêntico devolve o original (antes de qualquer regra, inclusive folio fechado); mesma chave com dados diferentes = `IDEMPOTENCY_KEY_REUSED` | pendente |
| 6 | 0 | `ROOM_ACCOUNT` **recusado** no registro manual de pagamento. A ponte comanda → quarto (3.3) decide o uso | pendente |
| 7 | 0 | Estorno de lançamento: só diária e consumo, uma vez, pelo valor integral. Estorno parcial = ajuste; ajuste errado se corrige com outro ajuste | pendente |
| 8 | 0 | Fechar é sempre explícito. Quem fecha o folio da comanda é o fechamento da comanda (3.2), na mesma transação | pendente |
| 9 | 0 | **Acréscimo ao `billing/api` congelado na 0.6:** `ChargeRequest` ganha `ChargeSource(ChargeSourceType {ROOM_NIGHT, TAB}, UUID id)` — o billing precisa saber se é diária ou consumo e de onde veio. Nenhum módulo consome ainda | pendente |
| 10 | 0 | **Acréscimo ao `billing/api`:** `FolioView.reference` passa a `Optional<FolioReference>`, porque folio `TAB` não tem referência (a #12 da 0.6 proíbe componente nulo) | pendente |
| 11 | 0 | **Acréscimo ao `billing/api`:** `FolioFacade.close(FolioId)`, para a 3.2 e a 3.4 fecharem o folio na mesma transação | pendente |
| 12 | 0 | O `.http` abre folio e lança por uma **rota só do perfil `dev`** (precedente: `DevUserSeeder`), porque nenhuma rota real abre folio nesta task | pendente |
| 13 | 0 | V6 difere do desenho do schema: `folio.owner_id` único com o tipo; `CHECK` de referência em `STAY`; `charge` com auditoria no lugar de `posted_*` (#9 da 0.6), `reversal_of_charge_id` único, `amount <> 0`, motivo no estorno; `payment` com auditoria e `refunded_*`. O schema doc é atualizado antes da migration | pendente (DEV, aguarda Ruan) |
| 14 | 0 | `payment_intent`: só a tabela, sem código até a v1.1. `payment.cash_drawer_session_id`: coluna nula, sem FK nem mapeamento, até a 2.4 | pendente (DEV, aguarda Ruan) |
| 15 | 0 | Lock pessimista (`FOR UPDATE`) em toda escrita no folio, sem `@Version`. Contenção baixa: um folio é uma conta | pendente (DEV, aguarda Ruan) |
| 16 | 0 | Chave de idempotência global (`uk_payment_idempotency`); corrida entre folios diferentes com a mesma chave vira `IDEMPOTENCY_KEY_REUSED`, traduzida no `infra`, não 500. Replay responde 201 com o corpo original | pendente (DEV, aguarda Ruan) |
| 17 | 0 | `idx_folio_open_ref` **único**: dois folios `STAY` abertos com o mesmo código deixariam `findOpenStayFolioByCode` ambíguo (`FOLIO_REFERENCE_ALREADY_IN_USE`). Afeta o hotel (2.1/3.1): a sugestão é abrir com o localizador e trocar pelo quarto no check-in | pendente (DEV, aguarda Ruan) |
| 18 | 0 | Dono do tipo errado e `changeReference` em folio `TAB` = `IllegalArgumentException` (erro entre módulos). Segundo folio para o mesmo dono = 409, não devolve o existente | pendente (DEV, aguarda Ruan) |
| 19 | 0 | `Idempotency-Key` lido como header opcional e recusado pelo domínio, porque header obrigatório ausente dá 500 no handler global | pendente (DEV, aguarda Ruan) |
| 20 | 0 | `PaymentMethod`, `PaymentStatus`, `PaymentId`, `ChargeType` em `billing.domain`. Se a 3.2 precisar do método, ele sobe para o `api/` lá | pendente (DEV, aguarda Ruan) |
| 21 | 0 | `.http` numerado `40-billing-folios.http` (a faixa 30 é do restaurante). Orçamento de teste até 1,3:1, excedente em saldo, estorno, fechamento e concorrência | pendente (DEV, aguarda Ruan) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] Agregado `Folio` com `RoomNightCharge`, `TabCharge`, `AdjustmentCharge` e `Payment`
- [ ] `FolioFacade` inteiro, com os acréscimos #9, #10, #11
- [ ] Rotas REST da spec e rota de `dev` (#12)
- [ ] `V6__billing.sql` e seção 7 do schema doc atualizada
- [ ] `http/40-billing-folios.http`
- [ ] Testes de unidade densos e os cinco testes de integração da spec

### Fora do escopo

- Caixa (2.4), `PaymentIntent` (v1.1), ligação comanda → folio (3.2), ponte para o quarto (3.3), check-out (3.4)

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Especificado em `docs/task-1.3-billing-folio.md`, seções 5 e 6.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Busca de folio por id não filtra por propriedade | Propriedade única; mesmo padrão do restaurante | Multipropriedade |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
