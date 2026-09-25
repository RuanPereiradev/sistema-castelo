# Decisões — Task 1.4 adaptadores fake (fiscal e pagamento)

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Roda em paralelo com a 1.3 e a 2.2 (decisão do Ruan de 2026-09-25: mais agentes
em tasks sem conflito). Não toca `billing` nem migration.

---

## Estado

| | |
|---|---|
| Branch | `task/1.4-fake-ports` |
| Rodada atual | 1 — DEV entregue (com testes de unidade, sem agente de teste separado), aguarda review |
| Build | `./mvnw clean verify` verde, ArchUnit incluído |
| Testes | `FakePaymentProcessorTest` (5), `FakeTaxInvoiceIssuerTest` (3), `FakePortsCompositionTest` (1) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Os fakes ficam **ligados em todos os perfis na v1**, inclusive produção, pela chave em `application.yml`, com `WARN` na subida. Nenhum fluxo da v1 chama as portas | implementado |
| 2 | 0 | O fake de pagamento cria o intent `PENDING`; `statusOf` responde `PAID`, ou `FAILED` quando os centavos do valor são `,01` | implementado |
| 3 | 0 | **Exceção à regra do `.http`**: portas internas, sem rota. A task que consumir a porta a exercita pelo seu `.http`. Precedentes: 0.2 e 0.6 | implementado |
| 4 | 0 | `AccessKey` fake = `FAKE-` + UUID, para nunca parecer chave de nota fiscal de verdade | implementado |
| 5 | 0 | Fake em `infra/` de cada módulo, `@Component` + `@ConditionalOnProperty` sem `matchIfMissing`; poms ganham `spring-boot-starter`; exceções de id/chave desconhecidos no `infra/`, estendendo `NotFoundException`; estado em memória; `cancel` repetido idempotente; URL do pagador em domínio `.invalid` | implementado (DEV, aguarda Ruan) |
| 6 | 1 | Os centavos `,01` da #2 são lidos do valor absoluto: `-10.01` também dá `FAILED`. O `PaymentRequest` não proíbe negativo, e o fake não é o lugar de inventar essa regra | implementado (DEV, aguarda Ruan) |
| 7 | 1 | Argumento nulo em `createPayment`, `statusOf`, `issue` e `cancel` é `NullPointerException` (`Objects.requireNonNull`), mesmo critério dos records do `api/` | implementado (DEV, aguarda Ruan) |
| 8 | 1 | O teste de composição (`app/src/test/.../ports`) importa as classes de `infra/` para conferir o tipo do bean; o ArchUnit só varre `main` | implementado (DEV, aguarda Ruan) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] `FakePaymentProcessor` com desfecho determinístico (#2)
- [x] `FakeTaxInvoiceIssuer` com chave `FAKE-` (#4)
- [x] Ativação por `application.yml` (#1)
- [x] Testes de unidade dos dois fakes e um teste de composição no `app`

### Fora do escopo

- `.http` (#3), migration, `billing`, mudança no `api/`

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

Nenhum: portas internas, sem rota.

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Estado do fake some ao reiniciar | Nenhum fluxo da v1 o consulta depois | Adaptador real na v1.1/v1.2 |
| Produção sobe com fake sem impedimento | Nenhum fluxo da v1 usa as portas (#1) | Guard que impede `prod` com fake quando a v1.1 chegar |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|

| # | Pergunta | Desde a rodada |
|---|---|---|
