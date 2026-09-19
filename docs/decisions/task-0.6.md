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

| 5 | 0 | **Estorno existe na v1.** O `FolioFacade` ganha uma operação de estorno, em vez de deixar tudo para o `AdjustmentCharge` do `ADMIN`: lançamento no quarto errado é erro de operação corriqueiro, e obrigar o gerente a abrir um ajuste manual toda vez transformaria o caso comum em exceção burocrática. O estorno **não apaga** o lançamento: gera um lançamento contrário, com autor e motivo, seguindo o mesmo princípio que o plano já aplica ao item de comanda cancelado (seção 6.1). O histórico do folio é append-only | pendente |
| 6 | 0 | `FolioView` devolve **a lista de lançamentos** junto com cabeçalho e saldo, não só o saldo. É o que a recepção precisa ver na tela de check-out. Sem paginação na v1: uma estadia lança uma diária por noite mais um lançamento por comanda (#3), o que mantém a conta na casa das dezenas de linhas | pendente |
| 7 | 0 | **Nenhum evento de domínio nasce nesta task.** Evento se declara quando existe quem consuma; declarar agora congelaria um contrato sobre suposição. O `DomainEvent` do `shared-kernel` já está pronto para quando o primeiro consumidor aparecer | pendente |
| 8 | 0 | **Na troca de quarto, o folio acompanha o hóspede.** O `code` do `FolioReference` passa a ser o quarto novo e o quarto anterior fica zerado — sem conta aberta atrelada a ele. O folio é o mesmo: os lançamentos feitos enquanto o hóspede estava no quarto antigo continuam nele, porque a conta é da estadia, não do quarto. Isso também responde ao quarto reaproveitado: a busca por número acha **apenas folio aberto**, e a estadia anterior, já fechada, nunca aparece | pendente |

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
| Depois de uma troca de quarto, o extrato mostra o quarto **atual** também nos lançamentos feitos enquanto o hóspede estava no anterior | O `FolioReference` é um só por folio, e a conta é da estadia, não do quarto (#8). O `created_at` de cada lançamento continua registrando quando ele aconteceu | Guardar o histórico de referências do folio, se a recepção sentir falta |
| `FolioOwner` não distingue em compilação um `TabId` de um `ReservationId` | Consequência aceita da #1, para manter o grafo de dependências intacto. O `OwnerType` é a guarda, em tempo de execução | — |
| `FolioView` não pagina | Uma estadia gera uma diária por noite mais um lançamento por comanda (#3, #6) | Paginar quando existir conta que justifique |

---

## Pontos em aberto

Nenhum. Os pontos 1 a 4 foram fechados pelas decisões #1 a #4, e os pontos 5 a 8
pelas decisões #5 a #8, todos na rodada 0.

| # | Pergunta | Desde a rodada |
|---|---|---|
