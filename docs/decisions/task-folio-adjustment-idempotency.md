# Decisões — Task folio-adjustment-idempotency Ajuste do folio com chave de idempotência

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/folio-adjustment-idempotency` (worktree `../castel-adjust-idem`) |
| Rodada atual | 1 |
| Build | `./mvnw clean verify` passa (ArchUnit incluído) |
| Testes | 2135, 0 falhas; nenhum novo nesta rodada (ver ponto em aberto #1) |

---

## Origem

Lacuna L2 da FE4 (`docs/decisions/task-FE4.md`, rodada 2): `POST /api/billing/folios/{id}/adjustments`
não lê `Idempotency-Key`. Um retry depois de falha de rede pode lançar o mesmo ajuste duas vezes.
O pagamento já tem a chave. O Ruan mandou seguir com a correção em 2026-11-11.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | DEV: a migration desta task é a **V16**. A V15 está reservada para a `task/no-preparation-items`, que precisa entrar na `main` antes desta, senão o Flyway recusa a V15 fora de ordem | implementado |
| 2 | 1 | DEV: a chave é **obrigatória**, como no pagamento. Ausente, em branco ou com mais de 100 caracteres depois do trim → 422 `INVALID_IDEMPOTENCY_KEY`. O cabeçalho é lido como opcional na rota e quem recusa é o domínio, pelo mesmo motivo da decisão #19 da task 1.3 (cabeçalho obrigatório ausente cairia no catch-all como 500). A chave é validada antes de carregar o folio, como no pagamento: chave inválida em folio inexistente é 422, não 404 | implementado |
| 3 | 1 | DEV: "mesmo conteúdo" é o corpo inteiro: `amount`, `description` e `reason`, os dois textos comparados depois do trim, como são gravados. O pagamento compara o corpo inteiro (`method` e `amount`) e não compara quem recebeu; aqui também não se compara quem autorizou. Conteúdo diferente → 409 `IDEMPOTENCY_KEY_REUSED`, mesmo que o conteúdo novo seja inválido (o pagamento faz igual: a repetição é reconhecida antes de qualquer outra regra) | implementado |
| 4 | 1 | DEV: a repetição é reconhecida antes de qualquer outra regra, **inclusive com o folio fechado** (decisão #5 da task 1.3), e não grava nada. A resposta é a mesma da primeira vez no formato: 201 com o folio como está agora, contendo o ajuste uma vez só. A rota de ajuste devolve o folio, não o lançamento, e isso não muda nesta task | implementado |
| 5 | 1 | DEV: escopo de unicidade igual ao do pagamento, **global entre folios**: a chave de ajuste é única em toda a tabela `charge` (`uk_charge_idempotency`). Chave já usada em ajuste de outro folio → 409 `IDEMPOTENCY_KEY_REUSED`, checado no caso de uso e segurado pelo banco numa corrida. O espaço de chaves é **separado** do pagamento e da movimentação de caixa, como já são entre si hoje (`uk_payment_idempotency` e `uk_cash_movement_idempotency` são índices independentes): a mesma string usada num pagamento não colide com um ajuste | implementado |
| 6 | 1 | DEV: a coluna `idempotency_key VARCHAR(100)` fica em `charge`, nula, mapeada só em `AdjustmentCharge`. Os ajustes já gravados ficam sem chave. Dois CHECKs: só ajuste carrega chave (validado sobre as linhas existentes) e ajuste novo exige chave (`NOT VALID`, para não reprovar os ajustes antigos; o Postgres aplica a linhas novas e alteradas) | implementado |
| 7 | 1 | DEV: a assinatura de `Folio.postAdjustment` ganha a chave, na ordem do `receive`: `(amount, description, reason, idempotencyKey, authorizedBy)`. Os testes existentes que chamam o método ou a rota foram adaptados só para passar uma chave, sem mudar o que verificam | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `POST /folios/{id}/adjustments` lê `Idempotency-Key`, no mesmo padrão do pagamento
- [x] Retry com a mesma chave devolve o mesmo ajuste, sem lançar outro
- [x] Mesma chave com conteúdo diferente é recusada, como no pagamento
- [x] `.http` com caminho feliz e cenários negativos
- [x] Migration V16 com a chave e a unicidade em `charge`
- [ ] Testes densos da regra: retry não duplica, conteúdo diferente recusado, ajuste sem chave recusado (ver ponto em aberto #1)
- [ ] Front: o caixa manda a chave no ajuste (branch `task/fe4-cashier-folios`, depois do merge desta)

---

## Contrato com o front

**Códigos de erro**

Rota: `POST /api/billing/folios/{folioId}/adjustments`, `ADMIN`, cabeçalho `Idempotency-Key` **obrigatório**.

| Código | HTTP | Quando |
|---|---|---|
| — | 201 | Ajuste novo, ou repetição com a mesma chave e o mesmo corpo: devolve o folio, com o ajuste uma vez só. Vale com o folio já fechado |
| `INVALID_IDEMPOTENCY_KEY` | 422 | Sem cabeçalho, em branco ou com mais de 100 caracteres depois do trim |
| `IDEMPOTENCY_KEY_REUSED` | 409 | A chave já lançou neste folio um ajuste de outro valor, descrição ou motivo; ou já lançou um ajuste em outro folio |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Os testes novos da regra (retry não duplica, conteúdo diferente, sem chave, chave de outro folio) não foram escritos por mim: a instrução do agente de implementação proíbe escrever teste de unidade da própria implementação. Ficam para o agente de testes, a partir deste log | 1 |
| 2 | O `postFolioAdjustment` do front na `main` não manda a chave. Hoje nenhuma tela o chama, então nada quebra; a tela que chamar precisa mandar a chave (item do front no escopo) | 1 |
