# Decisões — Task billing-open-folios Lista de folios abertos

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/billing-open-folios` |
| Rodada atual | 1 |
| Build | `./mvnw clean install` passa, ArchUnit incluído (2026-10-19) |
| Testes | 1 novo em `FolioHttpIntegrationTest` |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | O caixa chega ao folio por uma lista de abertos no billing, não pela comanda: o folio de hospedagem não vem de comanda (Ruan, 2026-10-19). Origem: a tela do caixa chamava `GET /billing/folios/open`, rota que nunca existiu | implementado |
| 2 | 0 | Rota `GET /api/billing/folios?status=OPEN`, no mesmo recurso do `?referenceCode=` (Ruan, 2026-10-19). As duas convivem por `params`: com `status` responde a lista, sem ele continua a busca por localizador | implementado |
| 3 | 0 | DEV: só `OPEN` é aceito; outro valor ou vazio → 400 `VALIDATION_FAILED`. Listar `CLOSED` seria o histórico inteiro, sem paginação — fica para quando houver uso | implementado |
| 4 | 0 | DEV: cada item é o `FolioResponse` completo, igual ao `GET /{folioId}`; o front reusa o tipo. Os abertos são dezenas, e as coleções do folio já vêm por subselect, sem N+1 | implementado |
| 5 | 0 | DEV: escopo da propriedade corrente; ordem por `openedAt`, depois `id` — o mais antigo no topo, que é quem espera o caixa há mais tempo | implementado |
| 6 | 0 | DEV: mesma regra de acesso do resto do recurso, `ADMIN` e `FRONT_DESK` (decisão #3 da 1.3) | implementado |
| 7 | 0 | DEV: sem migration. Nenhum índice cobre `status = 'OPEN'` para o folio de comanda; com a tabela pequena do lançamento, a varredura não pesa. Ver pontos em aberto | implementado |
| 8 | 1 | Review: `?status=OPEN&referenceCode=X` responde a lista e ignora o código (a regra com `params` é mais específica); registrado no javadoc. `?status=` vazio → 400 provado no teste | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `GET /api/billing/folios?status=OPEN`
- [x] Teste de integração: aberto aparece, fechado não, papel sem acesso é barrado, status inválido é recusado
- [x] Cenários no `http/40-billing-folios.http`, felizes e negativos
- [x] `./mvnw clean install` passando

Fora desta task: ligar a tela do caixa à rota — vai na branch `feat/cancel-deliver-items`.

---

## Contrato com o front

`GET /api/billing/folios?status=OPEN` → `200`, lista de `FolioResponse` (mesmo formato do
`GET /api/billing/folios/{folioId}`), vazia quando não há aberto.

| Código | HTTP | Quando |
|---|---|---|
| `VALIDATION_FAILED` | 400 | `status` vazio ou diferente de `OPEN` |

Com `status` e `referenceCode` juntos, vale a lista: não há filtro combinado.
| `ACCESS_DENIED` | 403 | Perfil `WAITER` ou `KITCHEN` |

---

## Pontos em aberto

| # | Pergunta | Rodada |
|---|---|---|
| 1 | Índice parcial `folio (property_id, opened_at) WHERE status = 'OPEN'` quando a tabela crescer. Exige migration, e a numeração está disputada entre as tasks do hotel | 0 |
