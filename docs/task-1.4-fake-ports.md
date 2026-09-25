# Task 1.4 — Adaptadores fake de `TaxInvoiceIssuer` e `PaymentProcessor`

**Objetivo:** dar às duas portas congeladas na 0.6 uma implementação fake,
registrada como bean, para que o sistema inteiro suba e seja testado sem nenhum
fornecedor fiscal ou de pagamento.

Decisões: `docs/decisions/task-1.4.md`. Contratos: `tax-invoice/.../api/*` e
`payment/.../api/*`, **congelados na 0.6**. Esta task implementa esses contratos
e não muda nenhuma assinatura, DTO ou enum do `api/`. Gabarito de bean de infra:
`identity.infra` (`InMemoryLoginAttemptRateLimiter` como `@Component`,
`DevUserSeeder` com `@ConditionalOnProperty`).

Na v1 **ninguém chama essas portas**: o pagamento da v1 é lançado à mão (plano,
seção 2.3), o pagamento online é a v1.1 e o fiscal é a v1.2. O fake existe para
que (a) o contexto suba quando algum módulo passar a injetar a porta, (b) as
tasks v1.1/v1.2 e os testes tenham um adquirente e um emissor determinísticos, e
(c) a troca pelo adaptador real seja trocar um bean.

Risco de negócio baixo: nenhum dinheiro de verdade passa, e não há concorrência.
Teste enxuto.

---

## 1. Escopo

**Dentro**
- `payment.infra.FakePaymentProcessor` implementa `PaymentProcessor`
- `taxinvoice.infra.FakeTaxInvoiceIssuer` implementa `TaxInvoiceIssuer`
- Uma exceção "não encontrado" por módulo, no `infra/` do fake (seção 5)
- Chave de ativação em `application.yml` (seção 4)
- `pom.xml` de `payment` e `tax-invoice`: `spring-boot-starter` e
  `spring-boot-starter-test` (test). Hoje eles só dependem do `shared-kernel`
- Testes de unidade nos dois módulos e um teste de composição no `app`

**Fora**
- **Qualquer mudança no `api/`** dos dois módulos (congelado na 0.6)
- **Migration.** A `payment_intent` é da `V6__billing.sql`, task 1.3. O fake
  guarda estado em memória e não lê nem escreve essa tabela
- **Qualquer arquivo do `billing`** (1.3 roda em paralelo)
- Webhook, job de expiração, QR code: v1.1
- Tabelas do fiscal, formato de chave de NFC-e/NFS-e: v1.2
- Rota HTTP e `.http` (seção 7, decisão #3)
- `http/README.md`, `CLAUDE.md`, `docs/MIGRATIONS.md`: quem edita é o orquestrador

---

## 2. Modelo de dados

Nenhum. Estado do fake em memória (`ConcurrentHashMap`), perdido ao reiniciar.

---

## 3. Comportamento dos fakes

### `FakePaymentProcessor`

1. `createPayment(request)` cria um `PaymentIntentId.newId()` e responde
   `PaymentIntent(id, PENDING, request.amount(), Optional.of(payerUrl))`
2. `payerUrl` = `https://payment.invalid/intents/{id}`. O TLD `.invalid` é
   reservado (RFC 2606) e nunca resolve: coerente com `PENDING` e impossível de
   confundir com um checkout real
3. O desfecho é decidido **na criação**, pelo valor, e guardado no mapa (#2):
   - centavos `,01` (ex.: `10.01`) → `FAILED`
   - qualquer outro valor → `PAID`
4. `statusOf(id)` responde o desfecho guardado. Não há relógio nem transição por
   tempo: o mesmo pedido dá sempre a mesma resposta
5. `statusOf` de um id que o fake não criou → `UnknownPaymentIntentException`
6. Dois `createPayment` iguais geram dois intents (o contrato não tem chave de
   idempotência; ela mora no `billing`)
7. Thread-safe. Um log `WARN` na criação do bean:
   `"Fake PaymentProcessor active: no payment reaches an acquirer"`

### `FakeTaxInvoiceIssuer`

8. `issue(request)` responde `IssuedInvoice(accessKey, clock.instant(), Optional.empty())`
   — `Clock` injetado (o bean que o `app` já expõe), `documentUrl` vazio
9. `AccessKey` = `"FAKE-"` + 32 hex de um UUID. **Não** imita os 44 dígitos da
   NFC-e (#4): o `AccessKey` é opaco por contrato e um prefixo explícito impede
   que uma chave fake seja lida como nota de verdade num comprovante
10. Chaves distintas a cada emissão; o fake guarda as chaves emitidas
11. `cancel(key, reason)` de chave emitida pelo fake: aceito. Cancelar de novo:
    aceito em silêncio (mesmo critério de idempotência da 1.2/1.5)
12. `cancel` de chave desconhecida → `UnknownTaxInvoiceException`
13. `reason` não é validado além de não nulo: a regra real é do emissor real (v1.2)
14. Log `WARN` na criação do bean: `"Fake TaxInvoiceIssuer active: no tax invoice reaches the tax authority"`

Confira as assinaturas reais de `PaymentIntent`, `PaymentStatus`, `IssuedInvoice`
e `AccessKey` no `api/` antes de escrever: se algum construtor diferir do que
está acima, o `api/` vence.

---

## 4. Ativação

```java
@Component
@ConditionalOnProperty(name = "castel.payment.processor", havingValue = "fake")
public class FakePaymentProcessor implements PaymentProcessor { ... }

@Component
@ConditionalOnProperty(name = "castel.tax-invoice.issuer", havingValue = "fake")
public class FakeTaxInvoiceIssuer implements TaxInvoiceIssuer { ... }
```

Em `application.yml` (base: vale para `dev`, `test` e `prod`, decisão #1):

```yaml
castel:
  payment:
    # Adquirente. v1: fake (nenhum pagamento sai do sistema). O adaptador real da v1.1
    # entra com o seu proprio valor aqui.
    processor: fake
  tax-invoice:
    # Emissor fiscal. v1: fake. Real na v1.2.
    issuer: fake
```

Sem `matchIfMissing`: a escolha do adaptador fica escrita. Os beans nascem pelo
scan de `br.com.castel` do `CastelApplication`, como os de `identity.infra`.

---

## 5. Códigos de erro

As duas exceções moram no `infra/` do módulo, estendem `NotFoundException` do
`shared-kernel` e não entram no `api/` (congelado).

| Código | Exceção | Quando |
|---|---|---|
| `PAYMENT_INTENT_NOT_FOUND` | `UnknownPaymentIntentException` | `statusOf` de intent que o fake não criou |
| `TAX_INVOICE_NOT_FOUND` | `UnknownTaxInvoiceException` | `cancel` de chave que o fake não emitiu |

---

## 6. Dependências Maven

`payment/pom.xml` e `tax-invoice/pom.xml` ganham `spring-boot-starter` e
`spring-boot-starter-test` (test). Não `-web` nem `-data-jpa`.

---

## 7. API e `.http`

Nenhuma rota: são portas internas. **Exceção à regra do `.http` (#3)**, como a
0.2 e a 0.6. A task que consumir a porta (v1.1, v1.2) exercita pelo seu `.http`.

---

## 8. Testes

Enxuto.

- **Unidade, `payment`** (JUnit puro): intent `PENDING` com valor e URL;
  `PAID` para valor comum; `FAILED` para `,01`; `statusOf` desconhecido recusado
- **Unidade, `tax-invoice`** (`Clock.fixed`): chaves `FAKE-` distintas no
  instante do relógio; cancelar duas vezes aceito; cancelar desconhecida recusado
- **Composição, `app`:** um teste sobre `AbstractIntegrationTest` que injeta
  `PaymentProcessor` e `TaxInvoiceIssuer` e confere que são os fakes

---

## 9. Critérios de aceite

- `./mvnw clean verify` verde, ArchUnit incluído
- Nenhuma linha alterada em `payment/.../api` nem `tax-invoice/.../api`
