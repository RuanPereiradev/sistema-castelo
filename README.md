# Castel — Sistema de Gestão Hoteleira e de Restaurante

Monolito modular. Java 21 · Spring Boot 4.1.1 · PostgreSQL 16 · React 18 · Docker.

As convenções do projeto (glossário, estilo de domínio rico, banco, API,
concorrência) estão em [`CLAUDE.md`](./CLAUDE.md).

## Estrutura de módulos

```
shared-kernel/   Money, Cpf, DateRange, Quantity, Weight, DomainEvent, IDs
identity/        User, Role, JWT
hotel/           RoomType, Room, Reservation, Guest, RatePlan, DailyInventory
restaurant/      MenuItem, MenuCategory, Tab, DiningTable, KDS
billing/         Folio, Charge, Payment, CashDrawerSession
tax-invoice/     porta TaxInvoiceIssuer + adaptador fake
payment/         porta PaymentProcessor + adaptador fake
app/             bootstrap, configuração, composição (único módulo executável)
```

## Ambiente de desenvolvimento

O Postgres roda em container. **O Spring Boot roda pelo IntelliJ**, fora de
container, para não quebrar o debugger.

```bash
# subir o banco (Postgres + Adminer)
docker compose -f docker-compose.dev.yml up -d

# build completo
./mvnw clean install

# rodar só os testes de um módulo
./mvnw test -pl restaurant

# validar fronteiras entre módulos
./mvnw test -pl app -Dtest=ArchitectureTest

# subir a aplicação (alternativa ao IntelliJ)
./mvnw spring-boot:run -pl app -Dspring-boot.run.profiles=dev
```

Após subir o banco e a aplicação com o perfil `dev`, o health check fica
disponível em `http://localhost:8080/actuator/health`.

O Adminer para inspeção do banco fica em `http://localhost:8081`
(usuário `castel`, senha `castel`, banco `castel_dev`, servidor `postgres`).

## Perfis Spring

| Perfil | Uso |
|---|---|
| `dev`  | Local. Flyway com `clean` habilitado, SQL logado, CORS aberto |
| `test` | Testes automatizados. Preparado para Testcontainers |
| `prod` | Produção. Flyway `clean` desabilitado, SQL não logado, CORS restrito |

## Build de produção (validação)

```bash
docker build -t castel-app .
```

O `Dockerfile` é multi-stage (build Maven → runtime JRE slim) e serve para
validar o build de produção. No dia a dia de desenvolvimento o backend não
roda em container.
