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
| 1 | 0 | O `billing` recebe **referência genérica de origem**, não o ID tipado do módulo dono: `FolioOwner` (`OwnerType` + `UUID`), com `OwnerType` em `RESERVATION` · `TAB`. O grafo de dependências fica intacto — `billing → tax-invoice.api, payment.api, shared-kernel` — e o `billing` continua sem saber o que é uma reserva. Resolve a contradição entre a assinatura do plano (`openStayFolio(ReservationId, GuestId)`) e a regra A1 do ArchUnit. Custo aceito: some a checagem de tipo em compilação entre um `TabId` e um `ReservationId`; o `OwnerType` passa a ser a guarda, em tempo de execução | pendente |
| 2 | 0 | O garçom digita o **número do quarto** para achar a conta do hóspede. É o que o hóspede sabe de cor e fala em voz alta no restaurante. O `FolioReference` do glossário carrega isso: `code` = `"102"`, `label` = `"Quarto 102 — João Silva"` | pendente |
| 3 | 0 | `ChargeRequest` leva **valor total e descrição**, um lançamento por comanda (`"Restaurante — comanda #142"`), não item a item. A conta do quarto fica legível e o detalhe do consumo continua vivo na comanda. Custo aceito: quem quiser conferir item a item pede a segunda via da comanda | pendente |
| 4 | 0 | A colisão de nome se resolve do lado do pagamento: o adquirente recebe **`PaymentRequest`**, e `ChargeRequest` fica com o significado do glossário (Lançamento = `Charge`). Alinha com `Payment`, que já é o termo do glossário | pendente |

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

Os pontos 1 a 4 foram fechados pelas decisões #1 a #4.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 5 | **Estorno.** Comanda lançada no quarto por engano: o `FolioFacade` precisa de uma operação de estorno na v1, ou se resolve com `AdjustmentCharge` autorizado por `ADMIN`, que os invariantes do `Folio` já preveem? | 0 |
| 6 | **O que `FolioView` devolve.** Só cabeçalho e saldo, ou a lista de lançamentos junto? Se vier a lista, ela pagina — e uma conta de estadia longa com muitos consumos é justamente o caso que pesa | 0 |
| 7 | **Quais eventos de domínio nascem agora.** O plano diz que efeito colateral (notificar KDS, enviar e-mail) usa evento interno, não a chamada síncrona. Declarar quais eventos já nesta task congela o contrato; deixar para as ondas seguintes evita inventar evento que ninguém consome | 0 |
| 8 | **Quem escreve e atualiza o `FolioReference`** (derivado da #2). Para o `billing` achar o folio pelo número do quarto sem saber o que é um quarto, o `hotel` precisa gravar o `code` no folio — no check-in, quando o quarto é atribuído. Isso levanta duas perguntas: o que acontece na **troca de quarto** no meio da estadia (o `code` muda e o histórico se perde?), e o que impede o número `102` de achar a estadia **anterior** daquele quarto, já encerrada | 0 |
