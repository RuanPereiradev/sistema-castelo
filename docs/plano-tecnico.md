# Plano Técnico — Sistema de Gestão Hoteleira e de Restaurante

**Versão:** 1.0 (consolidado após 3 rodadas de levantamento)
**Stack:** Java 21 · Spring Boot 3.x · PostgreSQL 16 · React 18 · Docker
**Arquitetura:** Monolito modular

---

## 1. Escopo e faseamento

### v1 — Núcleo operacional
O sistema roda a operação inteira do dia a dia, sem depender de nenhum fornecedor externo.

| Módulo | Entrega |
|---|---|
| Identidade | Login, JWT, perfis, permissões |
| Hotel | Reserva interna, disponibilidade, check-in/check-out, quartos |
| Restaurante | Cardápio, comanda de mesa, self-service por peso, mesas, KDS |
| Financeiro | Conta do hóspede (folio), lançamentos, pagamentos manuais, caixa |
| Integração | Consumo do restaurante lançado na conta do quarto |

### v1.1 — Pagamento online
Depende de decisão sobre adquirente/gateway.
- QR code na comanda (cliente vê a conta e paga sozinho)
- Portal público de reservas com sinal
- E-mail transacional

### v1.2 — Fiscal
Depende de descobrir como funciona hoje (ver Pendências).
- NFC-e (restaurante) e NFS-e (hospedagem), via gateway

### v1.3 — Eventos e espaços
Módulo independente. Salões, área da piscina, frente do restaurante.

### v2 — Backlog futuro
TEF/maquininha integrada · impressão térmica na cozinha · estoque e ficha técnica · delivery/takeaway no portal · integração com OTAs · operação offline · multi-propriedade

---

## 2. Regras de negócio consolidadas

### 2.1 Hotel

**Inventário e reserva**
- Reserva é feita por **tipo de quarto**, não por quarto específico. O quarto físico é atribuído no check-in.
- Todos os quartos são atualmente iguais (um único tipo cadastrado). A estrutura suporta N tipos sem alteração de código.
- Overbooking é **bloqueado** por padrão. `ADMIN` pode forçar, e o sistema registra quem autorizou.

**Máquina de estados da reserva**

```
PRE_RESERVA ──> CONFIRMADA ──> CHECK_IN ──> CHECK_OUT
     │               │              
     └──> CANCELADA <─┘              
                     └──> NO_SHOW
```

- `PRE_RESERVA`: segura inventário, ainda não confirmada
- `CONFIRMADA`: garantida (sinal pago ou confirmação manual da recepção)
- `CHECK_IN`: hóspede na casa, quarto físico atribuído, conta aberta
- `CHECK_OUT`: conta quitada, quarto liberado
- `NO_SHOW`: não apareceu até o horário limite

**Tarifação**
- Diária calculada **dia a dia**, permitindo variação por período (alta/baixa temporada).
- Preço varia por ocupação: valor individual, valor casal, e valor por pessoa adicional.
- Criança: faixas etárias configuráveis com percentual de cobrança.

**Quartos**
- Status: `DISPONIVEL` · `OCUPADO` · `MANUTENCAO`
- Sem etapa de limpeza no sistema. A recepção troca o status manualmente. Housekeeping é feito no boca a boca.
- No check-out o quarto volta a `DISPONIVEL` imediatamente.

**Conta do hóspede (folio)**
- Aberta no check-in, recebe diárias e consumo do restaurante.
- Fecha integralmente no check-out. Não há cobrança parcial durante a estadia.
- Não há limite de crédito. O hóspede informa o número do quarto e o consumo é lançado.

### 2.2 Restaurante

**Modalidades**
- À la carte com comanda por mesa
- Self-service por peso (comanda por cartão numerado)
- Sem room service

**Cardápio**
- Produtos com variações (P/M/G), adicionais pagos e observação livre.
- Disponibilidade por janela de horário e dia da semana, configurável por produto. Exemplo real: pizza a partir das 18:30.
- Produto pode ser marcado como esgotado manualmente.

**Self-service**
- Preço por quilo, peso digitado pelo operador.
- A tara do prato é descontada pelo próprio cliente na balança — não é modelada no sistema.
- Bebidas entram na mesma comanda do cartão.

**Comanda**
- Um único agregado atende mesa e cartão de self-service, distinguidos pelo campo `origem`.
- Suporta transferência de itens entre comandas, junção de comandas e divisão de conta.
- Itens são **append-only**. O total é sempre calculado, nunca armazenado como campo mutável.

**Taxa de serviço (10%)**
- Padrão definido pela origem: `MESA_ATENDIDA` nasce com taxa, `SELF_SERVICE` nasce sem.
- Produto pode ser marcado como nunca elegível.
- Operador pode ligar/desligar a taxa no fechamento, na comanda inteira ou item a item.

**KDS (Kitchen Display System)**
- Três setores de preparo, cada um com sua própria tela: **cozinha**, **pizzaria**, **bar**.
- O produto define para qual setor o item é roteado.
- Estados do item: `PENDENTE → EM_PREPARO → PRONTO → ENTREGUE`, mais `CANCELADO`.
- Comunicação em tempo real via WebSocket/STOMP, no mesmo processo Spring. Sem custo de infraestrutura adicional.
- Garçom pode cancelar item, inclusive após envio à cozinha. O cancelamento é registrado com autor e horário.

### 2.3 Financeiro

- `Pagamento` é sempre uma coleção de N registros vinculados a uma conta. Nunca um campo único.
- Suporta pagamento parcial e múltiplas formas na mesma conta.
- Meio de pagamento registrado manualmente na v1 (o operador informa "cartão, R$ 50").
- Controle de caixa por turno implementado mas **opcional** (abertura com fundo, sangria, suprimento, fechamento com conferência). O cliente ativa se quiser.
- Toda operação de pagamento carrega chave de idempotência.

### 2.4 Perfis e permissões

| Perfil | Pode |
|---|---|
| `ADMIN` | Tudo, incluindo override de overbooking, cadastros, relatórios, configurações |
| `RECEPCAO` | Reservas, check-in/out, status de quarto, conta do hóspede, pagamentos |
| `GARCOM` | Abrir/lançar/fechar comanda, cancelar item, receber pagamento, transferir mesa |
| `COZINHA` | Ver e atualizar status dos itens no KDS do seu setor |

Notas: o garçom recebe pagamento na mesa e o cliente também pode pagar no caixa. Não existe perfil de camareira.

---

## 3. Estratégia de concorrência

Dois pontos do sistema têm condição de corrida real. A estratégia é diferente em cada um.

### 3.1 Reservas — inventário materializado

Calcular disponibilidade com `SELECT COUNT(*)` nas reservas é o erro clássico: duas transações leem o mesmo número e ambas gravam. A solução é materializar:

```sql
daily_inventory (property_id, room_type_id, stay_date, total_rooms, booked_rooms)
```

Reservar N diárias = N `UPDATE` condicionais dentro de uma transação:

```sql
UPDATE daily_inventory
   SET booked_rooms = booked_rooms + 1
 WHERE room_type_id = ? AND stay_date = ? AND booked_rooms < total_rooms;
```

- O `UPDATE` condicional é atômico no Postgres — a própria linha serve de lock.
- As datas são travadas **em ordem crescente**, o que elimina deadlock entre reservas de períodos sobrepostos.
- Se qualquer `UPDATE` afetar 0 linhas, a transação inteira sofre rollback.
- Overbooking autorizado troca a condição para `booked_rooms < total_rooms + overbookingAllowance`.

### 3.2 Comanda — append-only, sem lock

Dois garçons lançando na mesma mesa **não é conflito**: são dois `INSERT` em `tab_item`. Lock otimista com `@Version` no agregado seria pior, porque geraria conflito falso e o garçom perderia o lançamento.

O lock aparece apenas no fechamento:
- `SELECT FOR UPDATE` na comanda durante o fechamento.
- Status `CLOSING` bloqueia novos lançamentos (com override do garçom, que cancela a cobrança pendente).
- Status `CLOSED` rejeita definitivamente.

### 3.3 Outros pontos

- **Atribuição de quarto no check-in:** lock pessimista na linha do quarto. Baixa contenção, é rápido.
- **Pagamento:** chave de idempotência no request, para o duplo clique não gerar dois registros.

---

## 4. Arquitetura de módulos

Monolito modular com módulos Maven independentes. A regra fundamental: **um módulo só enxerga a API pública dos outros**, nunca as classes internas.

```
sistema/
├── shared-kernel/     Money, Cpf, DateRange, DomainEvent, tipos de ID
├── identity/          User, Role, autenticação JWT
├── hotel/             RoomType, Room, Reservation, Guest, RatePlan, DailyInventory
├── restaurant/        MenuItem, MenuCategory, Tab, DiningTable, KDS
├── billing/           Folio, Charge, Payment, CashDrawerSession
├── tax-invoice/       porta TaxInvoiceIssuer + adaptador fake
├── payment/           porta PaymentProcessor + adaptador fake
└── app/               bootstrap, configuração, composição
```

Cada módulo de domínio tem a estrutura:

```
modulo/
├── api/          interfaces públicas + DTOs (o que outros módulos podem usar)
├── domain/       entidades, agregados, invariantes, value objects
├── application/  serviços de aplicação, casos de uso
├── infra/        repositórios JPA, adaptadores
└── web/          controllers REST
```

### Dependências permitidas

```
app ─────────> todos
restaurant ──> billing.api, shared-kernel
hotel ───────> billing.api, shared-kernel
billing ─────> tax-invoice.api, payment.api, shared-kernel
identity ────> shared-kernel
```

Nenhum módulo de domínio depende de outro fora de `api`. A regra é validada por teste automatizado com ArchUnit.

### Portas de terceiros

Fiscal e pagamento entram como **portas** no domínio, com adaptador fake na v1:

```java
public interface TaxInvoiceIssuer {
    IssuedInvoice issue(TaxInvoiceRequest request);
    void cancel(AccessKey accessKey, String reason);
}

public interface PaymentProcessor {
    PaymentIntent createCharge(ChargeRequest request);
    PaymentStatus statusOf(PaymentIntentId intentId);
}
```

Isso permite construir e operar o sistema inteiro sem nenhuma decisão sobre fornecedor. Quando a decisão vier, escreve-se só o adaptador.

---

## 5. Idioma e nomenclatura

### 5.1 A regra

**Todo o código é escrito em inglês.** Classes, métodos, variáveis, tabelas, colunas, endpoints, campos JSON, enums, branches e mensagens de commit.

**Toda a interface do usuário é em português.** Os garçons, recepcionistas e cozinheiros são brasileiros e nunca verão uma palavra em inglês. A separação é feita por arquivo de tradução no front e por mapeamento de código de erro para mensagem.

```java
// código em inglês
throw new TabAlreadyClosedException(tabId);

// resposta da API carrega um código, não uma frase
{ "code": "TAB_ALREADY_CLOSED", "status": 409 }

// front traduz o código para o operador
"TAB_ALREADY_CLOSED": "Esta comanda já foi fechada"
```

Isso evita o pior dos dois mundos: código bilíngue (`ComandaRepository.findByStatus`) e usuário lendo mensagem em inglês.

### 5.2 Por que não traduzir ao pé da letra

Hotelaria e food service têm vocabulário técnico consolidado em inglês. Traduzir literalmente produz nomes que não significam nada para quem lê:

| Tradução literal (ruim) | Termo da indústria (correto) | Por quê |
|---|---|---|
| `Command` | `Tab` | "Comanda" não é comando. `Tab` é a conta aberta que vai acumulando |
| `Account` | `Folio` | `Folio` é o termo hoteleiro exato para a conta do hóspede |
| `Daily` | `RoomNight` | A unidade cobrada é a noite ocupada, não "um dia" |
| `Additional` | `Modifier` | `Modifier` é o termo padrão de PDV para adicional de item |
| `Product` | `MenuItem` | Restaurante vende item de cardápio, não produto genérico |
| `Launch` | `Charge` | "Lançamento" financeiro é um débito lançado numa conta |
| `Bleeding` | `CashDrop` | "Sangria" é retirada de dinheiro do caixa |

### 5.3 Glossário oficial

Este glossário é a **linguagem ubíqua do projeto**. Todo subagente recebe esta tabela e não inventa sinônimo.

**Hotel**

| Português | Código | Observação |
|---|---|---|
| Reserva | `Reservation` | |
| Hóspede | `Guest` | |
| Quarto | `Room` | |
| Tipo de quarto | `RoomType` | |
| Tarifa | `RatePlan` | O plano de preço; o valor de uma noite é `nightlyRate` |
| Diária | `RoomNight` | Uma noite reservada, com sua data e valor |
| Disponibilidade diária | `DailyInventory` | A tabela materializada de concorrência |
| Ocupação | `Occupancy` | Value object: adultos + crianças |
| Pessoa adicional | `extraGuest` | Preço: `extraGuestRate` |
| Localizador | `confirmationCode` | |
| Sinal | `deposit` | |
| Fazer check-in | `checkIn()` | |
| Fazer check-out | `checkOut()` | |

**Restaurante**

| Português | Código | Observação |
|---|---|---|
| Comanda | `Tab` | Raiz do agregado |
| Item da comanda | `TabItem` | |
| Mesa | `DiningTable` | Tabela `dining_table`; `table` é palavra reservada em SQL |
| Cartão do self-service | `cardNumber` | Campo da própria `Tab` |
| Cardápio | `Menu` | |
| Item do cardápio | `MenuItem` | |
| Categoria | `MenuCategory` | |
| Variação (P/M/G) | `MenuItemVariant` | |
| Adicional | `Modifier` | |
| Observação do pedido | `specialInstructions` | |
| Setor de preparo | `PrepStation` | `KITCHEN`, `PIZZA`, `BAR` |
| Janela de horário | `AvailabilityWindow` | |
| Taxa de serviço | `serviceCharge` | |
| Vendido por peso | `soldByWeight` | Preço: `pricePerKilo` |
| Transferir itens | `transferItemsTo()` | |
| Juntar comandas | `mergeWith()` | |
| Dividir conta | `splitBill()` | |

**Financeiro**

| Português | Código | Observação |
|---|---|---|
| Conta / folio | `Folio` | Raiz do agregado |
| Lançamento | `Charge` | Classe base polimórfica |
| Diária lançada | `RoomNightCharge` | |
| Consumo do restaurante | `TabCharge` | |
| Ajuste / desconto | `AdjustmentCharge` | Exige autorização e motivo |
| Pagamento | `Payment` | |
| Saldo | `balance()` | Sempre calculado |
| Caixa | `CashDrawer` | |
| Turno de caixa | `CashDrawerSession` | |
| Fundo de troco | `openingFloat` | |
| Sangria | `CASH_DROP` | Tipo de `CashMovement` |
| Suprimento | `CASH_SUPPLY` | Tipo de `CashMovement` |
| Fechamento com conferência | `closeWithCount()` | |

**Identidade e transversais**

| Português | Código | Observação |
|---|---|---|
| Usuário | `User` | Tabela `app_user`; `user` é reservada no Postgres |
| Perfil | `Role` | `ADMIN`, `FRONT_DESK`, `WAITER`, `KITCHEN` |
| Propriedade | `Property` | O `propertyId` presente em tudo |
| Configuração | `Setting` | |
| Emissor fiscal | `TaxInvoiceIssuer` | Porta |
| Processador de pagamento | `PaymentProcessor` | Porta |

### 5.4 Enums

Valores sempre em `UPPER_SNAKE_CASE`, em inglês, persistidos como string (nunca ordinal — reordenar o enum corromperia o banco).

```java
ReservationStatus  PENDING · CONFIRMED · CHECKED_IN · CHECKED_OUT · CANCELLED · NO_SHOW
RoomStatus         AVAILABLE · OCCUPIED · MAINTENANCE
TabStatus          OPEN · CLOSING · CLOSED · CANCELLED
TabOrigin          TABLE_SERVICE · SELF_SERVICE
TabItemStatus      PENDING · IN_PREPARATION · READY · DELIVERED · CANCELLED
PrepStation        KITCHEN · PIZZA · BAR
FolioType          STAY · TAB
FolioStatus        OPEN · CLOSED
PaymentMethod      CASH · PIX · CREDIT_CARD · DEBIT_CARD · ROOM_ACCOUNT
CashMovementType   OPENING_FLOAT · CASH_DROP · CASH_SUPPLY · CLOSING_COUNT
Role               ADMIN · FRONT_DESK · WAITER · KITCHEN
```

### 5.5 Regras de nomenclatura

**Nomes revelam intenção, não implementação.** O nome responde "o que isso significa no negócio", não "como está guardado".

```java
// ruim
BigDecimal val;  int n;  List<Object> data;  boolean flag;
void process(Order o);  class TabHelper {}  class DataManager {}

// bom
Money subtotal;  int occupiedRooms;  List<TabItem> items;  boolean serviceChargeApplied;
void closeAndPost(Tab tab);  class ServiceChargeCalculator {}
```

**Sem abreviação.** `quantity` e não `qty`. `reservation` e não `res`. `product` e não `prod`. As únicas siglas aceitas são as universais do domínio: `id`, `url`, `pix`, `kds`, `cpf`, `qr`.

**Booleano é predicado que se lê como frase.** `isOpen()`, `hasBalance()`, `acceptsItems()`, `soldByWeight`. Nunca `flag`, `status2`, `check`.

**Método de agregado é ação de negócio.** `tab.close()`, `reservation.checkIn(room)`, `folio.post(charge)`. Nunca `setStatus()`, `update()`, `handle()`, `execute()`.

**Getter de domínio sem prefixo `get`.** Em modelo rico, `tab.total()` lê melhor que `tab.getTotal()` e reforça que é um valor calculado, não um campo. DTOs mantêm o padrão JavaBean por causa da serialização.

**Proibidos como nome de classe:** `Manager`, `Helper`, `Util`, `Data`, `Info`, `Processor` genérico. Se a única coisa que descreve a classe é "gerenciador", ela não tem responsabilidade definida.

**Nomes de teste descrevem a regra, não o método.**

```java
// ruim
void testAddItem()

// bom
void shouldRejectItemWhenTabIsClosed()
void shouldNotApplyServiceChargeOnSelfServiceTab()
void shouldReleaseInventoryWhenReservationExpires()
```

### 5.6 Banco de dados

`snake_case`, tabelas no **singular**, chaves primárias `id UUID`, chaves estrangeiras `{tabela}_id`.

Palavras reservadas do Postgres a evitar: `user`, `table`, `order`, `check`, `group`, `end`, `default`, `limit`. Daí `app_user`, `dining_table`, `tab`.

Booleanos com prefixo que se lê: `is_active`, `has_deposit`, `sold_by_weight`. Datas com sufixo: `created_at`, `checked_in_at`, `closed_at`.

### 5.7 API

Recursos no plural e em inglês, `kebab-case` no caminho, `camelCase` nos campos JSON.

```
GET  /api/hotel/room-types
POST /api/restaurant/tabs/{tabId}/items
GET  /api/billing/folios/{folioId}
```

```json
{ "confirmationCode": "A7K2M9", "nightlyRate": "180.00", "serviceCharge": "12.50" }
```

---

## 6. Modelo de domínio

### 6.1 Agregados

**`Reservation`** (hotel) — raiz
Período · `RoomType` · `Occupancy` · `Guest` titular · status · `RoomNight` calculadas · canal · `Room` atribuído (após check-in) · `confirmationCode`.

*Invariantes:* período com pelo menos uma noite · `Occupancy` não excede o máximo do `RoomType` · transições seguem a máquina de estados · `Room` só é atribuído em `checkIn()`.

**`Tab`** (restaurant) — raiz
`TabOrigin` (`TABLE_SERVICE` / `SELF_SERVICE`) · `DiningTable` **ou** `cardNumber` · `TabItem` · status · `serviceChargeApplied` · `publicToken` (QR code da v1.1).

*Invariantes:* itens só são adicionados com status `OPEN` · total sempre calculado · item cancelado permanece no histórico com autor e motivo · `publicToken` é UUID opaco, nunca o ID sequencial.

**`Folio`** (billing) — raiz
`FolioType` (`STAY` / `TAB`) · `Charge` · `Payment` · status.

*Invariantes:* `balance()` = soma dos `Charge` − soma dos `Payment` · não fecha com saldo diferente de zero, exceto por `AdjustmentCharge` autorizado por `ADMIN` · `post(charge)` em folio fechado é rejeitado.

**`CashDrawerSession`** (billing) — raiz
Operador · `openingFloat` · `CashMovement` · status.

**`MenuItem`** (restaurant) — raiz
`MenuCategory` · preço ou `pricePerKilo` · `MenuItemVariant` · `Modifier` · `PrepStation` · `AvailabilityWindow` · `serviceChargeEligible` · `available`.

### 6.2 A ponte hotel ↔ restaurante

Este é o ponto de integração mais importante do sistema.

```
Tab fechada com destino ROOM_ACCOUNT
        │
        │ restaurant chama billing.api.FolioFacade
        ▼
TabCharge lançado no Folio da Reservation
        │
        ▼
checkOut(): Folio reúne RoomNightCharge + TabCharge, fecha e recebe Payment
```

Pontos de desenho:
- `Tab` **não conhece** `Reservation`. Conhece apenas a interface `FolioFacade` do módulo `billing`.
- A chamada é **síncrona e transacional** — não pode haver `Tab` fechada sem `Charge` correspondente.
- Se a `Tab` é de cliente externo, o `billing` abre um `Folio` próprio do tipo `TAB`.
- Efeitos colaterais (notificar KDS, enviar e-mail) usam eventos internos do Spring, não a chamada síncrona.

### 6.3 Parâmetros configuráveis

Nenhum destes é regra fixa no código. Todos vivem em tabela de configuração editável pelo `ADMIN`, com defaults de mercado:

| Parâmetro | Default proposto |
|---|---|
| Horário de check-in | 14:00 |
| Horário de check-out | 12:00 |
| Cobrança de late check-out | Meia diária até 18h |
| Cancelamento gratuito até | 48h antes |
| Multa de cancelamento tardio | Retém o sinal |
| Percentual do sinal | 30% |
| Criança não paga até | 5 anos |
| Criança paga meia de | 6 a 11 anos |
| Taxa de serviço | 10% |
| Validade do localizador aguardando pagamento | 30 minutos |

---

## 7. Banco de dados

PostgreSQL 16, migrations com Flyway. Nomenclatura em `snake_case`, chaves primárias `UUID`.

Todas as tabelas carregam `property_id`, mesmo com uma única propriedade. Custa quase nada agora e é caríssimo adicionar depois.

### Tabelas principais

**identity**
`app_user` · `role` · `user_role`

**hotel**
`room_type` · `room` · `rate_plan` · `daily_inventory` · `guest` · `reservation` · `room_night` · `reservation_occupant`

**restaurant**
`menu_category` · `menu_item` · `menu_item_variant` · `modifier` · `menu_item_modifier` · `availability_window` · `dining_table` · `tab` · `tab_item` · `tab_item_modifier`

**billing**
`folio` · `charge` · `payment` · `cash_drawer_session` · `cash_movement`

**transversais**
`property` · `setting`

### Índices críticos

```sql
-- disponibilidade: consultada em toda busca de reserva
CREATE UNIQUE INDEX idx_daily_inventory_lookup
    ON daily_inventory (property_id, room_type_id, stay_date);

-- comanda aberta por mesa: consultada a cada lançamento
CREATE INDEX idx_open_tab_by_table
    ON tab (property_id, dining_table_id) WHERE status = 'OPEN';

-- KDS: consultado continuamente pelas telas de preparo
CREATE INDEX idx_kds_queue
    ON tab_item (prep_station, status, created_at)
    WHERE status IN ('PENDING', 'IN_PREPARATION');

-- localizador público da reserva
CREATE UNIQUE INDEX idx_reservation_confirmation_code
    ON reservation (confirmation_code);
```

### Decisões de modelagem

- **Valores monetários:** `NUMERIC(12,2)`. Nunca `float` ou `double`.
- **Histórico de preço:** `tab_item` e `room_night` gravam o valor praticado no momento. Alterar o cardápio nunca altera contas passadas.
- **Soft delete:** `menu_item` e `room` são inativados, nunca removidos, porque são referenciados por registros históricos.
- **Auditoria:** `created_at`, `created_by`, `updated_at`, `updated_by` em todas as tabelas transacionais.

---

## 8. API

REST sobre JSON. Autenticação por JWT no header `Authorization: Bearer`.

Convenções: verbos HTTP semânticos · erros no formato RFC 7807 (Problem Details) · paginação por `page`/`size` · datas em ISO-8601 · valores monetários como string decimal para evitar erro de ponto flutuante no JavaScript.

### Superfícies

```
/api/auth/*             público    login, refresh
/api/hotel/*            interno    reservas, quartos, check-in/out
/api/restaurant/*       interno    cardápio, comandas, mesas
/api/kitchen/*          interno    fila de preparo por setor (KDS)
/api/billing/*          interno    folios, pagamentos, caixa
/api/admin/*            admin      cadastros, configuração, relatórios
/public/*               público    portal de reservas (v1.1)
/ws/kitchen             websocket  atualizações do KDS em tempo real
```

### Endpoints centrais

```
GET    /api/hotel/availability?checkIn&checkOut&adults&children
POST   /api/hotel/reservations
POST   /api/hotel/reservations/{reservationId}/check-in
POST   /api/hotel/reservations/{reservationId}/check-out

POST   /api/restaurant/tabs
POST   /api/restaurant/tabs/{tabId}/items
DELETE /api/restaurant/tabs/{tabId}/items/{itemId}
POST   /api/restaurant/tabs/{tabId}/transfer
POST   /api/restaurant/tabs/{tabId}/close

GET    /api/kitchen/queue?station=KITCHEN
PATCH  /api/kitchen/items/{itemId}/status

POST   /api/billing/folios/{folioId}/payments
POST   /api/billing/cash-sessions
POST   /api/billing/cash-sessions/{sessionId}/close
```

---

## 9. Front-end

### Aplicação interna (v1)

React 18 + TypeScript + Vite. React Query para estado de servidor, Zustand para estado local. React Router.

**PWA responsivo, projetado para toque.** O garçom usa celular ou tablet, então isso não é adaptação posterior — é premissa de design desde a primeira tela.

Telas:
- Login
- Recepção: mapa de quartos, busca de disponibilidade, reserva, check-in, check-out
- Salão: mapa de mesas, comanda, lançamento de itens
- Self-service: abertura de cartão, lançamento por peso, fechamento
- KDS: três telas (cozinha, pizzaria, bar), otimizadas para monitor, atualização por WebSocket
- Caixa: recebimento, abertura e fechamento de turno
- Admin: cadastros, configuração, relatórios

Notas de UX operacional: alvos de toque grandes na tela do garçom · KDS legível a distância, com destaque por tempo de espera · confirmação explícita em toda ação irreversível.

### Portal público (v1.1)

Aplicação separada. Landing page + busca + reserva + pagamento do sinal + consulta por localizador. SSR ou pré-renderização para SEO.

---

## 10. Infraestrutura

### Docker Compose (desenvolvimento)

```yaml
services:
  postgres:   # 16-alpine, volume persistente
  backend:    # spring boot, hot reload via devtools
  frontend:   # vite dev server
  adminer:    # inspeção do banco
```

### Produção

- Multi-stage build no Dockerfile do backend (build Maven → runtime JRE slim).
- Front servido por Nginx, que também faz proxy reverso da API e termina TLS.
- Backup automatizado do Postgres (`pg_dump` diário com retenção).
- Logs estruturados em JSON.
- Healthcheck via Spring Actuator.
- VPS única é suficiente para o porte da operação. Não há necessidade de orquestração.

### Qualidade

- Testes de unidade nos agregados (regras de negócio e invariantes)
- Testes de integração com Testcontainers (Postgres real, não H2)
- **Testes de concorrência obrigatórios** para reserva do último quarto e fechamento de comanda
- ArchUnit validando as fronteiras entre módulos
- CI: build, testes, análise estática

---

## 11. Backlog para execução paralela

Tarefas organizadas em ondas. Dentro de cada onda, as tarefas são independentes e podem ser executadas em paralelo por subagentes distintos. Entre ondas existe dependência.

### Onda 0 — Fundação (sequencial, bloqueia tudo)

| # | Tarefa | Entrega |
|---|---|---|
| 0.1 | Esqueleto do projeto | Multi-módulo Maven, parent POM, Dockerfile, Compose, CI |
| 0.2 | `shared-kernel` | `Money`, `Cpf`, `DateRange`, `DomainEvent`, IDs tipados, exceções base |
| 0.3 | Baseline do banco | Flyway configurado, migration inicial, convenções, Testcontainers |
| 0.4 | `identity` e autenticação | `User`, `Role`, JWT, filtro de segurança, seed do admin |
| 0.5 | Fundação transversal | Handler de erro RFC 7807, auditoria, tabela de configuração, ArchUnit |
| 0.6 | **Contratos entre módulos** | Interfaces `api/` de todos os módulos, nomeadas conforme o glossário da seção 5 |
| 0.7 | Infra de testes `.http` | Estrutura da pasta, arquivos de ambiente, `00-auth.http` com captura de token |
| 0.8 | **Módulo de referência** | Fatia vertical do cardápio, feita manualmente, serve de gabarito de estilo |

### Onda 1 — Domínios base (paralela)

| # | Tarefa | Depende de |
|---|---|---|
| 1.1 | `hotel`: `Room`, `RoomType`, `RatePlan` | 0.x |
| 1.2 | `restaurant`: `MenuItem`, `MenuItemVariant`, `Modifier`, `AvailabilityWindow` | 0.x |
| 1.3 | `billing`: agregado `Folio`, `Charge`, `Payment` | 0.x |
| 1.4 | `TaxInvoiceIssuer` e `PaymentProcessor` com adaptadores fake | 0.x |
| 1.5 | `restaurant`: `DiningTable` e cartões de self-service | 0.x |

### Onda 2 — Núcleo transacional (paralela)

| # | Tarefa | Depende de |
|---|---|---|
| 2.1 | `DailyInventory` e motor de reserva (inclui a estratégia de lock) | 1.1 |
| 2.2 | Agregado `Tab`: abertura, `addItem`, cancelamento de item | 1.2, 1.5 |
| 2.3 | `RateCalculator` e geração de `RoomNight` por `Occupancy` | 1.1 |
| 2.4 | `CashDrawerSession`: abertura, `CASH_DROP`, `CASH_SUPPLY`, fechamento | 1.3 |
| 2.5 | Testes de concorrência de reserva | 2.1 |

### Onda 3 — Fluxos completos (paralela)

| # | Tarefa | Depende de |
|---|---|---|
| 3.1 | `checkIn()`: atribuição de `Room`, abertura de `Folio` | 2.1, 1.3 |
| 3.2 | Fechamento de `Tab`: `serviceCharge`, `splitBill` | 2.2, 1.3 |
| 3.3 | **Ponte `Tab` → `Folio` do hóspede (`TabCharge`)** | 3.2, 3.1 |
| 3.4 | `checkOut()`: consolidação, `Payment`, liberação do `Room` | 3.1, 3.3 |
| 3.5 | KDS: roteamento por `PrepStation`, WebSocket/STOMP, `TabItemStatus` | 2.2 |
| 3.6 | `transferItemsTo()` e `mergeWith()` | 2.2 |

### Onda 4 — Front-end (paralela, alta densidade)

| # | Tarefa | Depende de |
|---|---|---|
| 4.1 | Shell do front: build, roteamento, auth, design system, PWA | 0.4 |
| 4.2 | Telas de recepção (disponibilidade, reserva, check-in/out) | 4.1, 3.4 |
| 4.3 | Telas de salão e comanda (mobile-first) | 4.1, 3.2 |
| 4.4 | Tela de self-service com lançamento por peso | 4.1, 3.2 |
| 4.5 | Telas de KDS (três setores) | 4.1, 3.5 |
| 4.6 | Telas de caixa e pagamento | 4.1, 2.4 |
| 4.7 | Telas de administração e cadastros | 4.1, 1.1, 1.2 |

### Onda 5 — Fechamento da v1

| # | Tarefa |
|---|---|
| 5.1 | Relatórios operacionais (ocupação, faturamento, produtos mais vendidos) |
| 5.2 | Testes end-to-end dos fluxos críticos |
| 5.3 | Hardening: rate limit, validação, revisão de permissões |
| 5.4 | Deploy, backup, monitoramento, documentação de operação |

### Formato de cada task para o subagente

Cada tarefa deve ser entregue ao subagente com:

1. **Objetivo** em uma frase
2. **Escopo fechado** — o que está dentro e explicitamente o que está fora
3. **Contratos** — assinaturas de interface e DTOs que outros módulos vão consumir
4. **Modelo de dados** — tabelas e colunas a criar
5. **Invariantes** — as regras que o agregado precisa garantir
6. **Critérios de aceite** — testes que precisam passar
7. **Dependências** — o que precisa existir antes
8. **Arquivo `.http`** — caminho feliz e cenários negativos, executável no IntelliJ
9. **Conformidade de estilo** — segue o módulo de referência (seção 14)
10. **Conformidade de nomenclatura** — usa exclusivamente os termos do glossário (seção 5)

Os itens 8, 9 e 10 não são opcionais. Task entregue sem `.http` executável, com entidade anêmica ou com nome fora do glossário volta para correção. Nomenclatura inconsistente entre subagentes é o principal risco de qualidade da execução paralela, e corrigir depois exige refatoração que atravessa módulos.

O ponto crítico da paralelização: **as interfaces em `api/` de cada módulo precisam ser definidas antes da Onda 1**, para que os subagentes das ondas seguintes programem contra contratos estáveis em vez de esperar uns pelos outros.

---

## 12. Ambiente de desenvolvimento

Desenvolvimento pelo terminal com IntelliJ IDEA.

### Regra fundamental do ambiente dev

**O Postgres roda em container. O Spring Boot roda pelo IntelliJ, não em container.**

O motivo é prático: rodar o backend dentro do Docker durante o desenvolvimento quebra o debugger (exigiria configurar remote debug na porta 5005), torna o hot reload lento e dificulta ler stack trace. Container é para o banco, que precisa de isolamento e reprodutibilidade, e para validar o build de produção antes do deploy.

Dois arquivos de Compose:

```
docker-compose.dev.yml    postgres + adminer apenas
docker-compose.yml        stack completa, para validar produção
```

### Comandos de terminal

Maven Wrapper versionado no repositório, então nada precisa estar instalado globalmente.

```bash
# subir o banco
docker compose -f docker-compose.dev.yml up -d

# build completo
./mvnw clean install

# rodar só os testes de um módulo
./mvnw test -pl restaurante

# subir a aplicação (alternativa ao IntelliJ)
./mvnw spring-boot:run -pl app -Dspring-boot.run.profiles=dev

# nova migration
touch app/src/main/resources/db/migration/V{n}__{descricao}.sql

# validar fronteiras entre módulos
./mvnw test -pl app -Dtest=ArquiteturaTest

# front
cd frontend && npm run dev
```

### Configuração do IntelliJ

- **Run Configuration** do backend com perfil `dev` ativo e DevTools para hot reload.
- **Database tool window** conectada ao Postgres do container (`localhost:5432`), permitindo inspecionar dados sem sair da IDE.
- **HTTP Client** para os testes manuais de rota (seção 13).
- Um único **Compound Run Configuration** subindo banco, backend e front de uma vez.
- Um **Dictionary** customizado com os termos do glossário, para o corretor ortográfico da IDE não sublinhar `folio`, `tab`, `pix`.

### Perfis Spring

| Perfil | Uso |
|---|---|
| `dev` | Local. Flyway com `clean` habilitado, SQL logado, CORS aberto, seed de dados de exemplo |
| `test` | Testes automatizados. Testcontainers, banco efêmero |
| `prod` | Produção. Flyway `clean` desabilitado, SQL não logado, CORS restrito |

---

## 13. Testes manuais de rota (`.http`)

Todo endpoint entregue precisa vir acompanhado do seu arquivo `.http` executável no HTTP Client do IntelliJ. **Isso é critério de aceite de qualquer task, não item opcional.** Sem isso não há como acompanhar o desenvolvimento manualmente.

### Estrutura

```
http/
├── http-client.env.json           versionado (URLs, usuários de teste)
├── http-client.private.env.json   NO GITIGNORE (senhas, tokens)
├── 00-auth.http
├── 10-admin-setup.http
├── 20-hotel-availability.http
├── 21-hotel-reservation.http
├── 22-hotel-checkin-checkout.http
├── 30-restaurant-menu.http
├── 31-restaurant-tab.http
├── 32-restaurant-self-service.http
├── 40-kitchen-display.http
├── 50-billing-folio.http
├── 51-billing-cash-session.http
└── 90-end-to-end.http             fluxo completo, na ordem
```

A numeração não é decorativa: executando os arquivos em ordem, você percorre o sistema inteiro do zero até um check-out com consumo de restaurante lançado na conta.

### Ambiente

`http-client.env.json`:
```json
{
  "dev": {
    "host": "http://localhost:8080",
    "username": "admin@hotel.local"
  }
}
```

`http-client.private.env.json` (fora do Git):
```json
{
  "dev": { "password": "change-me-in-production" }
}
```

### Encadeamento automático

O ponto que torna isso realmente utilizável: capturar variáveis da resposta e reaproveitar nas requisições seguintes, sem copiar e colar ID.

`00-auth.http`:
```http
### Login
POST {{host}}/api/auth/login
Content-Type: application/json

{
  "email": "{{username}}",
  "password": "{{password}}"
}

> {%
    client.global.set("token", response.body.accessToken);
    client.test("Login retornou 200", function() {
        client.assert(response.status === 200);
    });
    client.test("Token presente", function() {
        client.assert(response.body.accessToken != null);
    });
%}
```

`21-hotel-reserva.http`:
```http
### Consultar disponibilidade
GET {{host}}/api/hotel/availability?checkIn=2026-10-01&checkOut=2026-10-04&adults=2
Authorization: Bearer {{token}}

> {% client.global.set("roomTypeId", response.body[0].roomTypeId); %}

### Criar reserva
POST {{host}}/api/hotel/reservations
Authorization: Bearer {{token}}
Content-Type: application/json

{
  "roomTypeId": "{{roomTypeId}}",
  "checkIn": "2026-10-01",
  "checkOut": "2026-10-04",
  "adults": 2,
  "children": [],
  "primaryGuest": {
    "fullName": "Maria Silva",
    "cpf": "12345678901",
    "email": "maria@exemplo.com"
  }
}

> {%
    client.global.set("reservationId", response.body.id);
    client.test("Reservation created as PENDING", function() {
        client.assert(response.status === 201);
        client.assert(response.body.status === "PENDING");
    });
%}

### Confirmar reserva
POST {{host}}/api/hotel/reservations/{{reservationId}}/confirm
Authorization: Bearer {{token}}
```

### Cenários negativos são obrigatórios

Cada arquivo `.http` cobre o caminho feliz **e** as violações de regra de negócio. É onde os bugs realmente aparecem:

```http
### Deve rejeitar: ocupação acima do máximo do tipo de quarto
POST {{host}}/api/hotel/reservations
Authorization: Bearer {{token}}
Content-Type: application/json

{ "roomTypeId": "{{roomTypeId}}", "checkIn": "2026-10-01",
  "checkOut": "2026-10-04", "adults": 99 }

> {% client.test("Rejects occupancy above room type limit", function() {
       client.assert(response.status === 422);
       client.assert(response.body.code === "OCCUPANCY_EXCEEDS_ROOM_TYPE");
   }); %}

### Deve rejeitar: lançar item em comanda já fechada
POST {{host}}/api/restaurant/tabs/{{closedTabId}}/items
Authorization: Bearer {{token}}
Content-Type: application/json

{ "menuItemId": "{{menuItemId}}", "quantity": 1 }

> {% client.test("Rejects item on closed tab", function() {
       client.assert(response.status === 409);
       client.assert(response.body.code === "TAB_ALREADY_CLOSED");
   }); %}

### Deve rejeitar: pizza fora da janela de horário
# executar antes das 18:30
POST {{host}}/api/restaurant/tabs/{{tabId}}/items
Authorization: Bearer {{token}}
Content-Type: application/json

{ "menuItemId": "{{pizzaMenuItemId}}", "quantity": 1 }

> {% client.test("Rejects menu item outside availability window", function() {
       client.assert(response.status === 422);
       client.assert(response.body.code === "MENU_ITEM_OUTSIDE_AVAILABILITY_WINDOW");
   }); %}
```

### O arquivo de fluxo completo

`90-end-to-end.http` é o mais importante dos onze. Ele executa, em sequência, o cenário que representa o sistema funcionando de verdade:

1. Login
2. Consultar disponibilidade
3. Criar e confirmar reserva
4. Check-in com atribuição de quarto
5. Abrir comanda em mesa
6. Lançar itens (incluindo um com adicional e observação)
7. Acompanhar o item no KDS até `READY`
8. Fechar a `Tab` com destino `ROOM_ACCOUNT`
9. Verificar que o `TabCharge` apareceu no `Folio` da `Reservation`
10. Check-out com pagamento em duas formas
11. Verificar que o `Room` voltou a `AVAILABLE`

Rodar esse arquivo inteiro e ver tudo verde é a definição prática de "a v1 está funcionando".

---

## 14. Estilo de código: POO de verdade

O Spring empurra naturalmente para um estilo que **não é orientado a objetos**: entidade anêmica com getter e setter públicos, e toda a regra de negócio espalhada em classes `Service`. Isso é código procedural usando sintaxe de classe. Como a decisão foi usar POO, o padrão abaixo vale para todos os módulos e para todos os subagentes.

### O que evitamos

```java
// ANÊMICO — não fazer
@Entity
public class Tab {
    @Id private UUID id;
    private String status;
    private BigDecimal total;
    // getters e setters para tudo
}

@Service
public class TabService {
    public void addItem(UUID tabId, ItemDTO dto) {
        Tab tab = repository.findById(tabId).orElseThrow();
        if (!tab.getStatus().equals("OPEN")) {          // regra fora do objeto
            throw new RuntimeException("closed");        // exceção genérica
        }
        tab.setTotal(tab.getTotal().add(dto.getValue())); // total mutável
        repository.save(tab);
    }
}
```

Os problemas: qualquer código pode chamar `setStatus("OPEN")` numa comanda já paga · o total é campo mutável e pode divergir dos itens · a regra "não lança em comanda fechada" precisa ser lembrada em todo lugar que toca em `Tab` · a exceção genérica não vira código de erro útil para o front nem para o teste `.http`.

### O que fazemos

```java
@Entity
public class Tab {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    private TabStatus status;

    @Enumerated(EnumType.STRING)
    private TabOrigin origin;

    @OneToMany(cascade = ALL, orphanRemoval = true)
    private List<TabItem> items = new ArrayList<>();

    protected Tab() {}   // exigido pelo JPA, nunca chamar

    // factory nomeada: o nome já diz o que está sendo criado
    public static Tab openForTable(DiningTable diningTable, User waiter) {
        Tab tab = new Tab();
        tab.id = UUID.randomUUID();
        tab.diningTable = requireNonNull(diningTable, "diningTable is required");
        tab.openedBy = requireNonNull(waiter, "waiter is required");
        tab.origin = TabOrigin.TABLE_SERVICE;
        tab.status = TabStatus.OPEN;
        tab.openedAt = Instant.now();
        tab.publicToken = UUID.randomUUID();
        return tab;
    }

    public static Tab openForSelfService(int cardNumber) {
        Tab tab = new Tab();
        tab.id = UUID.randomUUID();
        tab.cardNumber = cardNumber;
        tab.origin = TabOrigin.SELF_SERVICE;
        tab.status = TabStatus.OPEN;
        tab.openedAt = Instant.now();
        tab.publicToken = UUID.randomUUID();
        return tab;
    }

    // o próprio agregado garante sua invariante
    public TabItem addItem(MenuItem menuItem,
                           Quantity quantity,
                           List<Modifier> modifiers,
                           String specialInstructions,
                           Instant orderedAt) {

        if (!status.acceptsItems()) {
            throw new TabAlreadyClosedException(id, status);
        }
        if (!menuItem.isAvailableAt(orderedAt)) {
            throw new MenuItemOutsideAvailabilityWindowException(menuItem.name(), orderedAt);
        }

        TabItem item = TabItem.of(menuItem, quantity, modifiers,
                                  specialInstructions,
                                  serviceChargeAppliesTo(menuItem));
        items.add(item);
        return item;
    }

    // valores derivados: sempre calculados, nunca persistidos
    public Money subtotal() {
        return items.stream()
                    .filter(TabItem::isActive)
                    .map(TabItem::total)
                    .reduce(Money.ZERO, Money::plus);
    }

    public Money serviceCharge() {
        return items.stream()
                    .filter(TabItem::isActive)
                    .filter(TabItem::isServiceChargeable)
                    .map(TabItem::total)
                    .reduce(Money.ZERO, Money::plus)
                    .percentage(SERVICE_CHARGE_RATE);
    }

    public Money total() {
        return subtotal().plus(serviceCharge());
    }

    // coleção nunca vaza mutável
    public List<TabItem> items() {
        return Collections.unmodifiableList(items);
    }

    // transição de estado é método com significado, não setter
    public void startClosing() {
        if (status != TabStatus.OPEN) {
            throw new InvalidTabTransitionException(status, TabStatus.CLOSING);
        }
        if (items.stream().noneMatch(TabItem::isActive)) {
            throw new EmptyTabException(id);
        }
        this.status = TabStatus.CLOSING;
    }

    private boolean serviceChargeAppliesTo(MenuItem menuItem) {
        return origin.chargesServiceByDefault() && menuItem.isServiceChargeEligible();
    }
}
```

O enum carrega comportamento em vez de espalhar `if` pelo código:

```java
public enum TabStatus {
    OPEN      { public boolean acceptsItems() { return true;  } },
    CLOSING   { public boolean acceptsItems() { return false; } },
    CLOSED    { public boolean acceptsItems() { return false; } },
    CANCELLED { public boolean acceptsItems() { return false; } };

    public abstract boolean acceptsItems();
}
```

O serviço de aplicação apenas orquestra — nenhuma regra de negócio vive nele:

```java
@Service
@Transactional
public class AddItemToTabUseCase {

    public TabItemView execute(AddItemCommand command) {
        Tab tab = tabRepository.findById(command.tabId())
                               .orElseThrow(() -> new TabNotFoundException(command.tabId()));
        MenuItem menuItem = menuItemRepository.findById(command.menuItemId())
                               .orElseThrow(() -> new MenuItemNotFoundException(command.menuItemId()));

        TabItem item = tab.addItem(menuItem,
                                   command.quantity(),
                                   modifiersFrom(command),
                                   command.specialInstructions(),
                                   clock.instant());

        tabRepository.save(tab);
        events.publish(new TabItemOrdered(tab.id(), item.id(), menuItem.prepStation()));
        return TabItemView.from(item);
    }
}
```

Repare que o `if` de regra está dentro de `Tab.addItem`, não aqui. Se aparecer condicional de negócio dentro de um caso de uso, a regra está no lugar errado.

### Regras que valem para todo o código

**Sem setter público em entidade.** Mudança de estado acontece por método nomeado que expressa a operação de negócio: `confirm()`, `cancel(reason)`, `assignRoom(room)`. Nunca `setStatus()`.

**Construtor privado, criação por factory nomeada.** `Tab.openForTable(diningTable, waiter)` diz o que está acontecendo. `new Tab(a, b, c, d, e)` não diz nada.

**Value objects imutáveis para conceitos de negócio.** `Money`, `Periodo`, `Peso`, `CPF`, `Ocupacao`. Nunca `BigDecimal` solto passeando pelo código — `Money` carrega a moeda e o arredondamento correto, e impede somar reais com quilos.

**Valores derivados são calculados, nunca persistidos como campo mutável.** `Tab.total()`, `Folio.balance()`, `Reservation.totalAmount()`.

**Enum com comportamento.** `TabStatus.acceptsItems()` em vez de `if (status == X || status == Y)` espalhado.

**Coleções nunca vazam mutáveis.** Getter devolve `unmodifiableList`. Alteração só por método do agregado.

**Serviço de aplicação orquestra, não decide.** Ele carrega o agregado, chama um método de negócio, salva, publica evento. Se aparecer `if` de regra de negócio dentro de um `Service`, essa regra está no lugar errado.

**Exceção de domínio específica, não `RuntimeException`.** `TabAlreadyClosedException` carrega um código estável (`TAB_ALREADY_CLOSED`) que vira HTTP 409, alimenta o assert do arquivo `.http` e é traduzido pelo front. Uma exceção genérica não serve para nenhuma dessas três coisas.

### Padrões aplicados

| Padrão | Onde |
|---|---|
| Aggregate Root | `Reservation`, `Tab`, `Folio`, `CashDrawerSession` |
| Repository | Interface no domínio, implementação JPA na infra |
| Factory Method | Criação de todos os agregados |
| Strategy | `RateCalculator` (por ocupação, por temporada) |
| State | `ReservationStatus`, `TabStatus` com comportamento |
| Adapter | `TaxInvoiceIssuer`, `PaymentProcessor` |
| Polimorfismo | `Charge` → `RoomNightCharge`, `TabCharge`, `AdjustmentCharge` |

O caso de `Charge` merece nota: são tipos com comportamento realmente diferente (`RoomNightCharge` tem data de referência e depende do `RatePlan`; `TabCharge` aponta para uma `Tab`; `AdjustmentCharge` exige autorização e motivo). Isso é herança com propósito, não hierarquia decorativa. Mapeamento JPA por `@Inheritance(strategy = SINGLE_TABLE)` com coluna discriminadora.

### Módulo de referência

Antes de qualquer task ir para subagente, uma **fatia vertical completa** é implementada com cuidado e serve de gabarito: o módulo `restaurant`, parte de cardápio (task 1.2), atravessando entidade rica, value objects, repositório, serviço de aplicação, controller, migration Flyway, testes de unidade, teste de integração e arquivo `.http`.

Todo subagente recebe esse módulo **e o glossário da seção 5** como referência obrigatória. Isso resolve o problema de dez agentes inventarem dez convenções diferentes, e dá a você um padrão concreto para revisar o resto contra.

---

## 15. Pendências

Nenhuma delas bloqueia o início do desenvolvimento.

### A levantar com o cliente

| # | Pendência | Bloqueia |
|---|---|---|
| 1 | **Como funciona a nota fiscal hoje?** Sai NFC-e de verdade (com QR code e chave de 44 dígitos) ou é só comprovante? Quem emite, o sistema ou a maquininha? | v1.2 |
| 2 | Qual adquirente/maquininha usam | v1.1 |
| 3 | Regime tributário | v1.2 |
| 4 | Quanto pagam hoje pelo sistema atual, e qual é | Precificação |
| 5 | Tipos de quarto reais e ocupação máxima | Cadastro, não código |

Sobre a pendência 1: se a maquininha já emite NFC-e e o cliente está satisfeito, o sistema pode simplesmente **não emitir nota nenhuma** — registra o pagamento e a nota sai pela adquirente. Isso eliminaria o módulo fiscal inteiro. É um cenário plausível e vale confirmar antes de planejar a v1.2.

### Decididas como parâmetro

Horário de check-in/out · política de cancelamento · faixas etárias de criança · percentual do sinal. Todas com default de mercado, ajustáveis na tela de admin.

---

## 16. Riscos

| Risco | Mitigação |
|---|---|
| Concorrência em reserva e comanda | Estratégia definida na seção 3, com testes de concorrência obrigatórios na Onda 2 |
| Fiscal atrasar o projeto | Isolado atrás de porta com fake; v1 opera sem ele |
| Escopo crescendo | Faseamento explícito; eventos e portal já fora da v1 |
| Front do garçom mal dimensionado para toque | PWA mobile-first como premissa, não adaptação |
| Módulos vazando entre si | ArchUnit no CI, falhando o build |
| Subagentes inventando nomes divergentes | Glossário da seção 5 como entrada obrigatória de toda task, mais o módulo de referência |
| Perda de dados | Backup diário automatizado desde o deploy inicial |
