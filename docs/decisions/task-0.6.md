# Decisões — Task 0.6 contratos entre módulos

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Esta é a task mais barata de fazer e a mais cara de errar: todas as ondas
seguintes programam contra estes contratos, e mudá-los depois significa mexer em
vários módulos ao mesmo tempo. **A Onda 1 não começa sem a aprovação do Ruan.**

Vale o teto de três rodadas de review (decisão #74 da 0.4) e o orçamento de
teste do `CLAUDE.md` — que aqui quase não pesa: são interfaces, o aceite é
compilar e o ArchUnit segurar as fronteiras.

---

## Estado

| | |
|---|---|
| Branch | `task/0.6-module-contracts` |
| Rodada atual | 0 — levantamento, antes da primeira linha de código |
| Build | não iniciado |
| Testes | 976 herdados (shared-kernel 200, identity 552, app 224) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| | | | |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

Vindo da Onda 0, seção 6 (`docs/onda-0-execucao.md`, task 0.6):

- [ ] `billing/api`: `FolioFacade`, `FolioView`, `ChargeRequest`, IDs tipados do módulo
- [ ] `tax-invoice/api`: `TaxInvoiceIssuer` e seus DTOs (`TaxInvoiceRequest`, `IssuedInvoice`, `AccessKey`)
- [ ] `payment/api`: `PaymentProcessor` e seus DTOs (`PaymentIntent`, `PaymentStatus`)
- [ ] IDs tipados de cada módulo, sobre o `EntityId` do `shared-kernel`
- [ ] Eventos de domínio compartilhados, sobre o `DomainEvent` do `shared-kernel`
- [ ] Regra ArchUnit: `api/` não depende de `domain/` nem de `infra/` do próprio módulo

### Fora do escopo

- Qualquer implementação. Nenhuma classe concreta além de DTO imutável.
- `hotel/api` e `restaurant/api` com fachada própria: nada hoje consome uma
  fachada desses dois módulos. A ponte é só no sentido deles para o `billing`.

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Esta task não cria endpoint. O contrato aqui é entre módulos, não com o front.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| | | |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | **Onde moram os IDs que o `billing` precisa receber.** A assinatura do plano é `openStayFolio(ReservationId, GuestId)` e `openTabFolio(TabId)`, mas o grafo de dependências permite `billing → tax-invoice.api, payment.api, shared-kernel` — e **não** `billing → hotel.api` nem `→ restaurant.api`. Do jeito que está, o contrato não compila sob a regra A1 do ArchUnit. Três saídas: (a) `ReservationId`, `GuestId` e `TabId` descem para o `shared-kernel`; (b) o `billing` recebe uma referência genérica de origem, sem tipo do módulo dono; (c) o grafo ganha as duas setas e o `billing` passa a enxergar hotel e restaurante | 0 |
| 2 | **Como o garçom acha o folio do hóspede.** A `Tab` não conhece a `Reservation` (seção 6.2 do plano) e o `restaurant` não enxerga o `hotel`. Então, ao fechar uma comanda com destino `ROOM_ACCOUNT`, o que o garçom digita e o que o `billing` recebe para achar o folio certo? O glossário tem `FolioReference` (`code` + `label`), que parece existir exatamente para isso — falta dizer o que é o `code` na prática (número do quarto? localizador? código curto da estadia?) e se a busca é do `billing` | 0 |
| 3 | **O que `ChargeRequest` carrega.** Valor total já somado, ou item a item? Quantidade? Quem lançou? A taxa de serviço vai embutida no valor ou como `Charge` separado — o que muda o que o hóspede vê na conta do quarto | 0 |
| 4 | **Colisão de nome: `ChargeRequest`.** O nome aparece em dois contratos diferentes do plano: `FolioFacade.post(folioId, ChargeRequest)` (lançamento no folio) e `PaymentProcessor.createCharge(ChargeRequest)` (cobrança no adquirente). São coisas distintas e o glossário manda não inventar sinônimo. Um dos dois precisa de outro nome | 0 |
| 5 | **Estorno.** Comanda lançada no quarto por engano: o `FolioFacade` precisa de uma operação de estorno na v1, ou se resolve com `AdjustmentCharge` autorizado por `ADMIN`, que os invariantes do `Folio` já preveem? | 0 |
| 6 | **O que `FolioView` devolve.** Só cabeçalho e saldo, ou a lista de lançamentos junto? Se vier a lista, ela pagina — e uma conta de estadia longa com muitos consumos é justamente o caso que pesa | 0 |
| 7 | **Quais eventos de domínio nascem agora.** O plano diz que efeito colateral (notificar KDS, enviar e-mail) usa evento interno, não a chamada síncrona. Declarar quais eventos já nesta task congela o contrato; deixar para as ondas seguintes evita inventar evento que ninguém consome | 0 |
