# Onda 0 — Divisão de tarefas e esteira de execução

Documento operacional. O plano técnico continua sendo a referência de arquitetura; este aqui diz **quem faz o quê e em que ordem**.

---

## 1. Papéis

### `DEV` — agente de implementação
Escreve o código de produção. Recebe a especificação da task, o glossário (seção 5 do plano) e, a partir da task 0.8, o módulo de referência.

**Nunca:** escreve os próprios testes de unidade · inventa nome fora do glossário · altera arquivo de outra task · decide regra de negócio que não está na spec (se faltar, pergunta).

### `TEST` — agente de testes de unidade
Escreve os testes a partir da **especificação e das assinaturas públicas**, não do corpo dos métodos.

**Regra dura:** roda em sessão separada, recebendo apenas a spec da task e as assinaturas públicas das classes. Não recebe a implementação.

O motivo: agente que lê a implementação escreve teste que descreve o que o código faz. Se o código está errado, o teste fica verde e o bug passa. Testes escritos a partir da invariante pegam esse caso.

**Nunca:** altera código de produção para o teste passar · escreve teste que só confirma getter · usa mock onde caberia objeto real.

### `REVIEW` — agente de code review
Roda o **checklist mecânico** (seção 5 deste documento). É o filtro que impede coisa óbvia de chegar até você.

**Nunca:** aprova nada — ele produz um relatório, quem aprova é você.

### `VOCÊ` — revisão de julgamento + teste real de rota
- Revisa o que exige entendimento do negócio, depois do `REVIEW` já ter limpado o mecânico.
- Executa os arquivos `.http` no IntelliJ contra a aplicação rodando.
- Aprova ou devolve a task.
- Faz o merge.

---

## 2. Esteira de uma task

```
  spec da task
       │
       ├──────────────────────┐
       ▼                      ▼
    DEV escreve           TEST escreve testes
    implementação         (sessão separada, só a spec)
       │                      │
       └──────────┬───────────┘
                  ▼
        ./mvnw clean install
                  │
        ┌─────────┴─────────┐
        │ testes vermelhos? │──> volta para DEV ou TEST,
        └─────────┬─────────┘    dependendo de quem está errado
                  ▼
           REVIEW: checklist mecânico
                  │
        ┌─────────┴─────────┐
        │ apontou problema? │──> volta para DEV
        └─────────┬─────────┘
                  ▼
        VOCÊ: revisão de julgamento
        VOCÊ: executa o .http no IntelliJ
                  │
                  ▼
               merge
```

Um ponto importante da esteira: **teste vermelho não significa que o DEV errou.** Pode ser o `TEST` que entendeu a regra errado. Quando os dois discordam, quem decide é a especificação — e se a spec estiver ambígua, o problema é da spec, e ela é corrigida antes de qualquer código.

Isso é uma vantagem real de separar os papéis: divergência entre implementação e teste vira sinal de que a regra não estava clara.

---

## 3. Contexto obrigatório de cada agente

Todo agente recebe, sem exceção:

1. Seção 5 do plano — **glossário e nomenclatura**
2. Seção 14 do plano — **estilo de código POO**
3. A especificação da task
4. A partir de 0.8: o **módulo de referência** como gabarito

O `DEV` recebe adicionalmente as seções 3 (concorrência), 6 (domínio) e 7 (banco) quando a task tocar nesses pontos.

---

## 4. Prompts base

### `DEV`

```
Você implementa código de produção para o sistema hotel/restaurante.

CONTEXTO OBRIGATÓRIO
- Glossário (seção 5 do plano): use exclusivamente esses nomes.
  Não invente sinônimo. Se um conceito não estiver no glossário, pergunte.
- Estilo POO (seção 14): modelo de domínio rico.
  Sem setter público em @Entity. Construtor privado + factory nomeada.
  Valor derivado é calculado, nunca campo mutável.
  Coleção exposta como unmodifiableList.
  Exceção de domínio específica com código estável, nunca RuntimeException.
- Código em inglês. Mensagem de usuário nunca vive no backend.

REGRAS
- Não escreva testes de unidade. Outro agente cuida disso.
- Não altere arquivo fora do escopo desta task.
- Se a spec estiver ambígua ou faltar regra, PARE e pergunte.
  Não invente comportamento de negócio.
- Ao terminar, liste os arquivos criados/alterados e as decisões que tomou.

TASK
[colar a especificação]
```

### `TEST`

```
Você escreve testes de unidade a partir da ESPECIFICAÇÃO, não da implementação.

Você recebeu a spec e as assinaturas públicas. Você NÃO tem o corpo dos
métodos e não deve pedir por ele. Escreva os testes que verificam as
invariantes descritas na spec.

REGRAS
- JUnit 5 + AssertJ.
- Nome de teste descreve a regra:
  shouldRejectItemWhenTabIsClosed(), não testAddItem().
- Um comportamento por teste.
- Cubra: caminho feliz, cada invariante violada, valores de borda
  (zero, negativo, vazio, nulo, limite exato), e arredondamento
  quando houver dinheiro.
- Não use mock para objeto de domínio puro. Mock só para porta externa.
- Não escreva teste que só exercita getter.
- Se a spec não disser qual é o comportamento esperado em algum caso,
  liste esse caso como AMBIGUIDADE em vez de assumir.

SPEC
[colar]

ASSINATURAS PÚBLICAS
[colar]
```

### `REVIEW`

```
Você faz code review mecânico. Produza um RELATÓRIO, não aprove nada.

Para cada item do checklist, marque OK ou PROBLEMA com arquivo e linha.
Não sugira refatoração de estilo pessoal. Só o que está no checklist.

CHECKLIST
[colar a seção 5 deste documento]

DIFF
[colar]
```

---

## 5. Checklist do agente `REVIEW`

**Nomenclatura**
- [ ] Todo identificador em inglês
- [ ] Termos batem com o glossário (`Tab`, `Folio`, `MenuItem`, `Charge`, `RoomNight`…)
- [ ] Sem abreviação (`qty`, `res`, `prod`, `val`)
- [ ] Booleano é predicado legível (`isOpen`, `acceptsItems`, `soldByWeight`)
- [ ] Sem classe `Manager`, `Helper`, `Util`, `Data`, `Info`
- [ ] Tabela em `snake_case`, singular, sem palavra reservada (`user`, `table`, `order`)
- [ ] Nome de teste descreve a regra, não o método

**Modelo rico**
- [ ] Nenhum setter público em classe `@Entity`
- [ ] Construtor público ausente; criação por factory nomeada
- [ ] Nenhum valor derivado persistido como campo mutável
- [ ] Coleção exposta como `unmodifiableList`
- [ ] Nenhum `if` de regra de negócio dentro de `@Service`
- [ ] Enum com comportamento onde havia condicional repetida

**Correção técnica**
- [ ] Nenhum `double`/`float` para dinheiro; só `Money`
- [ ] Nenhum `java.util.Date`; só `java.time`
- [ ] `@Enumerated(EnumType.STRING)`, nunca ordinal
- [ ] Sem injeção por campo (`@Autowired` em atributo); construtor
- [ ] Exceção de domínio específica com código estável
- [ ] Sem `System.out.println`; logger estruturado
- [ ] Sem segredo hardcoded

**Entregáveis**
- [ ] Arquivo `.http` presente, com caminho feliz e cenários negativos
- [ ] Migration Flyway com nome no padrão `V{n}__{descricao}.sql`
- [ ] Migration é forward-only (não altera migration já aplicada)
- [ ] `./mvnw clean install` passa
- [ ] ArchUnit passa

---

## 6. As tasks da Onda 0

### 0.1 — Esqueleto do projeto · `DEV`

**Objetivo:** repositório que compila, sobe e conecta no banco.

**Escopo**
- Maven multi-módulo, Java 21, Spring Boot 3.3.x, parent POM com versões centralizadas
- Módulos vazios: `shared-kernel`, `identity`, `hotel`, `restaurant`, `billing`, `tax-invoice`, `payment`, `app`
- Maven Wrapper versionado
- `docker-compose.dev.yml`: Postgres 16 + Adminer (backend **não** entra aqui)
- `Dockerfile` multi-stage do backend (build Maven → JRE slim)
- `application.yml` com perfis `dev`, `test`, `prod`
- `.gitignore` incluindo `http-client.private.env.json` e `.env`
- `.editorconfig`
- `README.md` com os comandos de terminal

**Fora do escopo:** qualquer entidade, endpoint ou regra.

**Aceite**
- `./mvnw clean install` passa
- `docker compose -f docker-compose.dev.yml up -d` sobe o Postgres
- App sobe na 8080, `/actuator/health` retorna `UP`
- App conecta no Postgres do container com o perfil `dev`

**Sua revisão:** estrutura de módulos bate com a seção 4 do plano; nenhum segredo commitado.

---

### 0.2 — `shared-kernel` · `DEV` + `TEST`

**Objetivo:** os value objects que sustentam o domínio inteiro. É a task com maior retorno de teste de unidade — lógica pura, zero infraestrutura.

**Escopo**

| Classe | Responsabilidade |
|---|---|
| `Money` | `BigDecimal` scale 2, `HALF_UP`, BRL. `plus`, `minus`, `multiply`, `percentage`, `isZero`, `isNegative`. Imutável. |
| `Quantity` | Inteiro positivo. Rejeita zero e negativo. |
| `Weight` | Gramas ou quilos, scale 3. `priceAt(pricePerKilo)` devolve `Money`. |
| `Percentage` | Aplicável sobre `Money`, arredondamento explícito. |
| `DateRange` | `nights()`, `overlaps()`, `contains()`, `datesStream()`. Rejeita fim ≤ início. |
| `Cpf` | Validação com dígitos verificadores, normalização, `equals` por valor. |
| `EntityId` | Base tipada de ID, para `TabId` não ser atribuível a `FolioId`. |
| `DomainEvent` | Interface base com `occurredAt`. |
| `DomainException` | Base com `code()` estável. |

**Invariantes para o `TEST` cobrir**
- `Money` nunca perde centavo em cadeia de operações; `percentage` arredonda `HALF_UP`
- `Money` é imutável: operação devolve nova instância, original intacta
- Somar moedas diferentes lança exceção
- `DateRange` de 01/10 a 04/10 tem exatamente 3 noites
- `DateRange` com fim igual ao início é rejeitado
- `Cpf` rejeita dígito verificador errado e sequência repetida
- `Weight.priceAt` arredonda corretamente em peso fracionário
- IDs tipados diferentes não são intercambiáveis (verificado em compilação)

**Aceite:** todos os testes verdes; nenhuma classe com setter; `Money` sem construtor público (`Money.of`, `Money.ZERO`).

**Sua revisão:** arredondamento de `Money` — é o lugar onde erro vira diferença de caixa no fim do mês.

---

### 0.3 — Baseline do banco · `DEV`

**Objetivo:** Flyway funcionando e teste de integração com Postgres real.

**Escopo**
- Flyway configurado; `clean` habilitado em `dev`, desabilitado em `prod`
- `V1__baseline.sql`: `property`, `setting`, `app_user`, `role`, `user_role`
- Colunas de auditoria (`created_at`, `created_by`, `updated_at`, `updated_by`) em todas as tabelas transacionais
- Testcontainers configurado para o perfil `test`
- Um teste de integração provando que a migration roda do zero

**Fora do escopo:** tabelas de `hotel`, `restaurant` e `billing`.

**Aceite:** migration roda em banco vazio; teste de integração sobe Postgres via Testcontainers e passa; H2 não é usado em lugar nenhum.

**Sua revisão:** nomes de tabela e coluna contra o glossário; `app_user` e não `user`.

---

### 0.4 — `identity` e autenticação · `DEV` + `TEST`

**Objetivo:** login com JWT e proteção por papel. **Primeira task com rota real para você testar.**

**Escopo**
- `User` (entidade rica: `activate()`, `deactivate()`, `changePassword()` — sem setter)
- `Role`: `ADMIN`, `FRONT_DESK`, `WAITER`, `KITCHEN`
- BCrypt para senha
- JWT: access token curto + refresh token
- Spring Security stateless, filter chain, autorização por papel
- `POST /api/auth/login`, `POST /api/auth/refresh`, `GET /api/auth/me`
- Seed do admin no perfil `dev`
- Arquivo `00-auth.http`

**Invariantes para o `TEST`**
- Senha nunca é persistida em texto puro
- Senha nunca aparece em resposta, log ou `toString()`
- Usuário inativo não autentica mesmo com senha correta
- Token expirado é rejeitado
- Refresh token não serve como access token

**Aceite (e seu roteiro de `.http`)**

| Cenário | Esperado |
|---|---|
| Login com credencial válida | 200 + `accessToken` |
| Login com senha errada | 401, código `INVALID_CREDENTIALS` |
| Login com usuário inexistente | 401, **mesma mensagem** da senha errada |
| Login com usuário inativo | 401 |
| Endpoint protegido sem token | 401 |
| Endpoint protegido com token válido | 200 |
| Endpoint de `ADMIN` com token de `WAITER` | 403 |
| Token expirado | 401, código `TOKEN_EXPIRED` |

O terceiro caso é intencional: mensagem diferente para "usuário não existe" e "senha errada" permite descobrir quais e-mails estão cadastrados. Verifique isso à mão.

**Sua revisão:** tempo de expiração do token · segredo do JWT vem de variável de ambiente, não do `application.yml` · resposta de login não vaza dado além do necessário.

---

### 0.5 — Fundação transversal · `DEV`

**Objetivo:** tratamento de erro, auditoria, configuração e as regras automatizadas de arquitetura.

**Escopo**
- `GlobalExceptionHandler` → RFC 7807 `ProblemDetail` com campo `code`
- Mapeamento: `DomainException` → 422 · conflito de estado → 409 · não encontrado → 404 · validação → 400
- JPA Auditing com `AuditorAware` lendo o usuário do JWT
- `Setting` com cache e leitura tipada
- **Testes ArchUnit:**
  - módulo de domínio só depende de `api/` de outro módulo
  - `@Entity` não tem setter público
  - nenhum `@Autowired` em campo
  - nenhum `java.util.Date` ou `Calendar`
  - nenhum `double`/`float` em classe de domínio
  - nenhuma classe terminando em `Manager`, `Helper`, `Util`, `Data`, `Info`
  - controller não depende de repositório diretamente

**Aceite:** exceção de domínio vira resposta com código estável; cada regra ArchUnit tem um teste que prova que ela **falha** quando violada (regra que nunca falha não protege nada).

**Sua revisão:** o mapeamento de status HTTP faz sentido; o `code` da exceção é estável o bastante para o front traduzir.

---

### 0.6 — Contratos entre módulos · `DEV`, com **portão de aprovação seu**

**Objetivo:** congelar as interfaces públicas antes de qualquer implementação paralela.

Esta é a task mais barata de fazer e a mais cara de errar. Todas as ondas seguintes programam contra estes contratos. Mudar depois significa mexer em vários módulos ao mesmo tempo.

**Escopo — apenas interfaces e DTOs, zero implementação**

```java
// billing/api
public interface FolioFacade {
    FolioId openStayFolio(ReservationId reservationId, GuestId guestId);
    FolioId openTabFolio(TabId tabId);
    void post(FolioId folioId, ChargeRequest charge);
    Money balanceOf(FolioId folioId);
    FolioView findById(FolioId folioId);
}

// tax-invoice/api
public interface TaxInvoiceIssuer { ... }

// payment/api
public interface PaymentProcessor { ... }

// identity/api
public interface CurrentUserProvider { UserId currentUserId(); }
```

Mais: os IDs tipados de cada módulo, os DTOs de leitura (`FolioView`, `ChargeRequest`) e os eventos de domínio compartilhados.

**Aceite:** compila; nenhuma classe concreta além de DTO imutável; ArchUnit garante que `api/` não depende de `domain/` nem de `infra/`.

**Seu portão:** **a Onda 1 não começa antes de você aprovar esta task.** Vale gastar tempo aqui.

---

### 0.7 — Infraestrutura de testes `.http` · `DEV`, depois **sua**

**Objetivo:** a bancada onde você vai trabalhar em todas as ondas.

**Escopo**
- Pasta `http/` com a estrutura da seção 13 do plano
- `http-client.env.json` versionado
- `http-client.private.env.json` no `.gitignore`, com arquivo de exemplo commitado
- `00-auth.http` completo, com captura de token em variável global
- `README` da pasta: como rodar no IntelliJ, como trocar de ambiente

**Aceite:** você abre o IntelliJ, roda `00-auth.http` e vê os testes verdes sem configurar nada além do arquivo privado.

---

### 0.8 — Módulo de referência · `DEV` + `TEST` + **revisão profunda sua**

**Objetivo:** uma fatia vertical completa que serve de gabarito para todos os agentes das ondas seguintes.

**Escopo:** `MenuCategory` e `MenuItem` atravessando todas as camadas.

- `MenuItem` como agregado rico: `AvailabilityWindow`, `isAvailableAt(Instant)`, `markUnavailable()`, preço ou `pricePerKilo`, `serviceChargeEligible`
- `MenuCategory`
- Repositório: interface no domínio, implementação JPA na infra
- Casos de uso: criar, atualizar, listar, marcar indisponível
- Controller REST em `/api/restaurant/menu-items`
- Migration `V2__menu.sql`
- Testes de unidade do agregado
- Teste de integração do fluxo completo
- `30-restaurant-menu.http`

**Invariantes para o `TEST`**
- Item vendido por peso exige `pricePerKilo` e rejeita preço unitário
- Item não vendido por peso exige preço unitário
- `isAvailableAt` respeita a janela: pizza às 18:29 indisponível, às 18:31 disponível
- Item sem janela cadastrada está disponível a qualquer hora
- Janela que cruza a meia-noite funciona
- Item marcado indisponível não fica disponível nem dentro da janela

**Aceite:** tudo verde e **você aprova explicitamente como gabarito**.

**Sua revisão — a mais importante da Onda 0.** Tudo o que você deixar passar aqui vira padrão replicado por dez agentes nas ondas seguintes. Vale ler linha a linha.

---

## 7. Ordem e paralelismo

A Onda 0 é quase toda sequencial. Não force paralelismo aqui.

```
0.1 ─┬─> 0.2 ─┬─> 0.6 ──> [portão: sua aprovação] ──> Onda 1
     │        │
     └─> 0.3 ─┴─> 0.4 ──> 0.5 ──> 0.8
                   │
                   └─> 0.7
```

- **0.2 e 0.3** podem rodar em paralelo (agentes diferentes)
- **0.7** pode rodar em paralelo com 0.5 assim que 0.4 terminar
- **0.6** precisa de 0.2 (os value objects aparecem nos DTOs)
- **0.8** precisa de tudo, porque é a demonstração de que tudo funciona junto

---

## 8. Seu checklist de revisão

O agente `REVIEW` já passou pelo mecânico. Você olha o que exige julgamento:

- [ ] **A regra de negócio está correta?** Confere contra a seção 2 do plano.
- [ ] **A invariante está no agregado certo?** Regra sobre `Tab` mora em `Tab`, não em `TabItem` nem num serviço.
- [ ] **O que acontece no caso que ninguém pensou?** Valor zero, lista vazia, data invertida, concorrência.
- [ ] **A modelagem vai aguentar a próxima feature?** Ou já dá para ver onde vai quebrar.
- [ ] **O `.http` cobre os cenários negativos?** Não só o caminho feliz.
- [ ] **Eu entendo esse código?** Se você não entende, um agente daqui a três semanas também não vai.

E o teste que importa: **rode o `.http` e veja com seus olhos.** Teste verde no CI e rota funcionando no IntelliJ não são a mesma coisa.

---

## 9. Definição de pronto da Onda 0

A Onda 0 está fechada quando, num clone limpo:

1. `docker compose -f docker-compose.dev.yml up -d` sobe o banco
2. `./mvnw clean install` passa, incluindo ArchUnit
3. A aplicação sobe e `/actuator/health` responde `UP`
4. `00-auth.http` roda verde no IntelliJ
5. `30-restaurant-menu.http` cria, lista e consulta um item de cardápio
6. Você aprovou os contratos da 0.6 e o gabarito da 0.8

Só então a Onda 1 abre, e aí sim com vários agentes em paralelo.
