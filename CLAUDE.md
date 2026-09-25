# Sistema de Gestão Hoteleira e de Restaurante

Monolito modular. Java 21 · **Spring Boot 4.1.1** · PostgreSQL 16 · React 18 · Docker.

Boot 4 roda sobre Spring Framework 7, Spring Security 7 e Hibernate 7. Boa parte
do material sobre Spring descreve a linha 6, cuja API de configuração difere.
Na dúvida sobre configuração de Security, contexto ou JPA, **consulte a
documentação da versão 4.1 antes de escrever**, em vez de seguir o padrão
conhecido.

Este arquivo carrega automaticamente em toda sessão e em todo subagente. É a
fonte da verdade de convenções. Não duplique estas regras nos arquivos de agente.

---

## Regra de idioma

**Código em inglês. Interface do usuário em português.**

Classes, métodos, variáveis, tabelas, colunas, endpoints, campos JSON, enums,
branches e commits: inglês. Nenhuma string em português vive no backend.

Exceções de domínio carregam um **código estável**, nunca uma frase:

```java
throw new TabAlreadyClosedException(tabId);   // vira code: "TAB_ALREADY_CLOSED"
```

O front traduz o código para o operador. Um único código serve para três coisas:
resposta HTTP, assert do arquivo `.http` e mensagem traduzida na tela.

---

## Glossário — linguagem ubíqua

Use exclusivamente estes termos. Não invente sinônimo. Se um conceito não estiver
aqui, **pergunte antes de nomear**.

### Hotel

| Português | Código |
|---|---|
| Reserva | `Reservation` |
| Hóspede | `Guest` |
| Quarto | `Room` |
| Tipo de quarto | `RoomType` |
| Tarifa | `RatePlan` (valor da noite: `nightlyRate`) |
| Diária | `RoomNight` |
| Disponibilidade diária | `DailyInventory` |
| Ocupação | `Occupancy` |
| Pessoa adicional | `extraGuest` / `extraGuestRate` |
| Localizador | `confirmationCode` |
| Sinal | `deposit` |

### Restaurante

| Português | Código |
|---|---|
| Comanda | `Tab` |
| Item da comanda | `TabItem` |
| Mesa | `DiningTable` (tabela `dining_table`) |
| Cartão do self-service | `cardNumber` |
| Item do cardápio | `MenuItem` |
| Categoria | `MenuCategory` |
| Variação (P/M/G) | `MenuItemVariant` |
| Adicional | `Modifier` |
| Adicional oferecido no item | `MenuItemModifier` (`maxQuantity`) |
| Observação do pedido | `specialInstructions` |
| Pedido de um item na comanda | `TabItemOrder` |
| Adicional escolhido no pedido | `ModifierChoice` (com `quantity`) |
| Setor de preparo | `PrepStation` |
| Janela de horário | `AvailabilityWindow` |
| Taxa de serviço | `serviceCharge` |
| Vendido por peso | `soldByWeight` / `pricePerKilo` |

### Financeiro

| Português | Código |
|---|---|
| Conta / folio | `Folio` |
| Lançamento | `Charge` |
| Diária lançada | `RoomNightCharge` |
| Consumo do restaurante | `TabCharge` |
| Ajuste / desconto | `AdjustmentCharge` |
| Pagamento | `Payment` |
| Saldo | `balance()` |
| Caixa | `CashDrawer` |
| Turno de caixa | `CashDrawerSession` |
| Fundo de troco | `openingFloat` |
| Sangria | `CASH_DROP` |
| Suprimento | `CASH_SUPPLY` |

### Identidade e transversais

| Português | Código |
|---|---|
| Usuário | `User` (tabela `app_user`) |
| Perfil | `Role` |
| Propriedade | `Property` |
| Configuração | `Setting` |
| Criança da reserva | `ReservationChild` |
| Intenção de pagamento | `PaymentIntent` |
| Referência do folio | `FolioReference` (`code` + `label`) |
| Emissor fiscal | `TaxInvoiceIssuer` (porta) |
| Processador de pagamento | `PaymentProcessor` (porta) |

### Enums

Sempre `UPPER_SNAKE_CASE`, persistidos como **string**, nunca ordinal.

```
ReservationStatus  PENDING · CONFIRMED · CHECKED_IN · CHECKED_OUT · CANCELLED · NO_SHOW
RoomStatus         AVAILABLE · OCCUPIED · MAINTENANCE
TabStatus          OPEN · CLOSING · CLOSED · CANCELLED · MERGED
TabOrigin          TABLE_SERVICE · SELF_SERVICE
TabItemStatus      PENDING · IN_PREPARATION · READY · DELIVERED · CANCELLED
PrepStation        KITCHEN · PIZZA · BAR
FolioType          STAY · TAB
FolioStatus        OPEN · CLOSED
PaymentMethod      CASH · PIX · CREDIT_CARD · DEBIT_CARD · ROOM_ACCOUNT
CashMovementType   OPENING_FLOAT · CASH_DROP · CASH_SUPPLY · CLOSING_COUNT
Role               ADMIN · FRONT_DESK · WAITER · KITCHEN
```

---

## Estilo: modelo de domínio rico

O Spring empurra para entidade anêmica com regra espalhada em `Service`. Isso é
código procedural com anotação, e **não é o padrão deste projeto**.

- Sem setter público em `@Entity`. Mudança de estado por método de negócio:
  `confirm()`, `cancel(reason)`, `assignRoom(room)`. Nunca `setStatus()`.
- Construtor privado. Criação por factory nomeada: `Tab.openForTable(table, waiter)`.
- Valor derivado é **calculado**, nunca campo mutável: `tab.total()`, `folio.balance()`.
- Coleção exposta como `Collections.unmodifiableList`.
- Enum com comportamento: `TabStatus.acceptsItems()`, não `if (status == X || ...)`.
- Getter de domínio sem prefixo `get`: `tab.total()`. DTO mantém padrão JavaBean.
- Invariante mora no agregado. **Nenhum `if` de regra de negócio dentro de `@Service`.**
- Caso de uso apenas orquestra: carrega agregado, chama método, salva, publica evento.
- Exceção de domínio específica com código estável, nunca `RuntimeException`.

### Nomenclatura

- Sem abreviação: `quantity` não `qty`, `reservation` não `res`.
- Booleano é predicado legível: `isOpen()`, `acceptsItems()`, `soldByWeight`.
- Proibido como nome de classe: `Manager`, `Helper`, `Util`, `Data`, `Info`.
- Nome de teste descreve a regra: `shouldRejectItemWhenTabIsClosed()`,
  não `testAddItem()`.

### Técnico

- Dinheiro é `Money`. Nunca `double`, `float` ou `BigDecimal` solto.
- Datas com `java.time`. Nunca `java.util.Date` ou `Calendar`.
- `@Enumerated(EnumType.STRING)`.
- Injeção por construtor. Nunca `@Autowired` em campo **em código de produção**.
  Em `src/test/java` o `@Autowired` em campo é aceito: é o padrão idiomático de
  teste Spring, e as regras estruturais do ArchUnit rodam só contra `main`.
- Migration Flyway é **forward-only**: nunca edite uma migration já aplicada.

---

## Estrutura de módulos

```
shared-kernel/   Money, Cpf, DateRange, Quantity, Weight, DomainEvent, IDs
identity/        User, Role, JWT
hotel/           RoomType, Room, Reservation, Guest, RatePlan, DailyInventory
restaurant/      MenuItem, MenuCategory, Tab, DiningTable, KDS
billing/         Folio, Charge, Payment, CashDrawerSession
tax-invoice/     porta TaxInvoiceIssuer + adaptador fake
payment/         porta PaymentProcessor + adaptador fake
app/             bootstrap, configuração, composição
```

Cada módulo: `api/` (interfaces públicas e DTOs) · `domain/` · `application/` ·
`infra/` · `web/`.

**Fronteira:** um módulo só enxerga o `api/` de outro. Nunca `domain/` nem
`infra/`. Validado por ArchUnit no CI.

```
app ─────────> todos
restaurant ──> billing.api, shared-kernel
hotel ───────> billing.api, shared-kernel
billing ─────> tax-invoice.api, payment.api, shared-kernel
identity ────> shared-kernel
```

---

## Banco

`snake_case`, tabelas no **singular**, PK `id UUID`, FK `{tabela}_id`.

Palavras reservadas do Postgres a evitar: `user`, `table`, `order`, `check`,
`group`, `end`, `limit`. Daí `app_user`, `dining_table`.

Booleano: `is_active`, `sold_by_weight`. Data: `created_at`, `checked_in_at`.
Dinheiro: `NUMERIC(12,2)`.

Auditoria em toda tabela transacional: `created_at`, `created_by`,
`updated_at`, `updated_by`.

---

## API

Recurso no plural, `kebab-case` no caminho, `camelCase` no JSON.
Erro no formato RFC 7807 com campo `code`.

```
GET  /api/hotel/room-types
POST /api/restaurant/tabs/{tabId}/items
GET  /api/billing/folios/{folioId}
```

Valor monetário trafega como **string decimal** (`"180.00"`), para não perder
precisão no JavaScript.

---

## Comandos

```bash
docker compose -f docker-compose.dev.yml up -d   # sobe Postgres + Adminer
./mvnw clean install                             # build + testes
./mvnw test -pl restaurant                       # testes de um módulo
./mvnw test -pl app -Dtest=ArchitectureTest      # fronteiras entre módulos
./mvnw spring-boot:run -pl app -Dspring-boot.run.profiles=dev
```

O Postgres roda em container. **O Spring Boot roda pelo IntelliJ**, não em
container, para não quebrar o debugger.

---

## Concorrência

Duas condições de corrida reais no sistema, com estratégias diferentes:

**Reserva** — inventário materializado em `daily_inventory`. Nunca calcular
disponibilidade com `COUNT(*)` sobre reservas.

```sql
UPDATE daily_inventory
   SET booked_rooms = booked_rooms + 1
 WHERE room_type_id = ? AND stay_date = ? AND booked_rooms < total_rooms;
```

Travar as datas **em ordem crescente** dentro da transação, para evitar deadlock.
`UPDATE` que afeta 0 linhas derruba a transação inteira.

**Comanda** — itens são append-only. Dois garçons lançando na mesma mesa não é
conflito, são dois `INSERT`. Lock otimista aqui seria pior: geraria conflito
falso e o garçom perderia o lançamento. Lock pessimista só no fechamento.

---

## Entregáveis de qualquer task

1. Código de produção
2. Migration Flyway, quando houver mudança de schema
3. Arquivo `.http` em `http/`, com caminho feliz **e cenários negativos**
4. `./mvnw clean install` passando, ArchUnit incluído

Task sem `.http` executável está incompleta.

---

## Orçamento de teste

O volume de teste segue o **risco de negócio**, não a busca por cobertura. A
referência é **uma linha de teste para cada linha de produção**. Passar disso
precisa de motivo; o dobro ou o triplo, como aconteceu na 0.4, é sinal de que se
testou o que não paga.

**Teste denso — onde errar custa dinheiro ou confiança do operador:**

- Invariante de agregado: comanda fechada não recebe item, folio não fecha com saldo
- Cálculo monetário: total, saldo, taxa de serviço, rateio, venda por peso
- Concorrência real: `daily_inventory` em reserva simultânea, fechamento de comanda
- Transição de estado: o que cada `Status` aceita e recusa
- Fechamento de caixa e conferência de valores

**Teste enxuto — um caso que prova a regra, e segue:**

- Cenário adversarial de segurança. **Ninguém vai fazer força bruta neste sistema:**
  é um hotel, não um banco, e o custo do ataque supera o ganho. Limite de tentativa
  e afins existem e continuam testados, mas com um caso por regra, não com uma
  suíte exaustiva.
- Encanamento que a fundação transversal já cobre: erro RFC 7807, auditoria,
  limite de corpo, leitura de `setting`. Já tem teste; não se reescreve por módulo.
- Variação de entrada que não muda a regra: não parametrize vinte valores quando
  três provam o limite, o meio e a borda.
- Getter, DTO e mapeamento sem lógica.

Cobertura não é meta. Teste que só descreve o que o código faz é custo de
manutenção sem retorno — o teste existe para pegar a regra errada, não para
repetir a implementação.

---

## Regra para agentes

Se a especificação estiver ambígua ou faltar uma regra de negócio, **pare e
pergunte**. Não invente comportamento de negócio. Não altere arquivo fora do
escopo da task.

## Registro de decisões

Toda task mantém `docs/decisions/task-<código>.md` na sua branch, criado a
partir de `docs/TEMPLATE-decisoes.md` **antes da primeira linha de código**.

Ele é atualizado **na mesma resposta** em que uma decisão é confirmada, nunca
reconstruído de memória no fim. Task longa passa por várias rodadas de review, e
o que foi decidido na rodada 1 vira névoa na rodada 4 — é assim que escopo
aprovado reaparece como "pendência futura".

Regras:

- Decisão confirmada pelo Ruan entra na tabela **imediatamente**
- Item aprovado como escopo **não sai** da lista sem aprovação explícita do Ruan,
  registrando para qual task vai e por quê
- Decisão revertida não é apagada: marque como revertida e adicione a nova
- Antes de reportar o fim de uma rodada, **releia o arquivo** e confirme que
  nada da lista de escopo ficou de fora

O arquivo vai junto no PR e permanece em `docs/decisions/` depois do merge. Ele
é a resposta para "por que isso foi feito assim" daqui a seis meses.
