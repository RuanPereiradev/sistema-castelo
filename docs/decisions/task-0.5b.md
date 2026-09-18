# Decisões — Task 0.5b fundação transversal

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Vale a decisão #74 da 0.4: **teto de três rodadas de review**. Na terceira, o
que não for bloqueio crítico é registrado aqui e mergeado.

---

## Estado

| | |
|---|---|
| Branch | `task/0.5b-cross-cutting-foundation` |
| Rodada atual | 1 — implementação (DEV e TEST em paralelo a partir da spec) |
| Build | `./mvnw clean install` **verde** — 223 testes no `app`, 0 falhas (2026-09-18) |
| Testes | 884 herdados da 0.4 + os da 0.5b escritos pelo agente TEST |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Limite de tamanho de corpo da requisição é **global**, para toda a API, e nasce junto com o handler RFC 7807 — não um remendo só no login (herda #78 da 0.4) | pendente |
| 2 | 0 | A regra "nunca `@Autowired` em campo" vale **só para código de produção**; a exceção para teste passa a estar escrita no `CLAUDE.md` (herda #79 da 0.4) | pendente |
| 3 | 0 | Corpo do 404 e demais erros fora do `identity` passam a sair em RFC 7807 pelo handler global (herdado da 0.4, rodada 4) | pendente |
| 4 | 0 | Campo `instance` passa a estar presente nos corpos de erro escritos pelo filtro JWT e pelo `SecurityConfig` (herdado da 0.4, rodada 5) | pendente |
| 5 | 0 | Auditoria de `app_user` (`updated_at`/`updated_by`) entra com a infraestrutura de auditoria desta task (herdado da 0.4, rodada 2, confirmado na 4) | pendente |
| 6 | 0 | Quando não há usuário autenticado no contexto (seed do dev, migration, job futuro), `created_by`/`updated_by` recebem um **UUID reservado de sistema**, constante e documentado — não `null` nem uma string mágica. Mantém a coluna `NOT NULL`, é rastreável nos dados e não quebra FK futura | pendente |
| 7 | 0 | Interface `Settings` mora no **`shared-kernel`**, não em um `api/` novo no `app`: todos os módulos já dependem do shared-kernel e o projeto já tem diretório suficiente. Nenhum pacote novo nasce nesta task | pendente |
| 8 | 0 | `created_by`/`updated_by` seguem **nullable** no banco, sem migration. Tornar `NOT NULL` custaria renumerar V3 a V9, já reservadas por task, por uma restrição que o `AuditorAware` garante em código. Esta task **não muda schema** | pendente |
| 9 | 0 | Limite default de tamanho de corpo da requisição: **64 KB**, configurável por propriedade. O maior corpo legítimo previsto na v1 é uma comanda com muitos itens, na casa de poucos KB; 64 KB dá uma ordem de grandeza de folga e fecha o corpo de 2 MB aceito hoje no login | pendente |

| 10 | 1 | Prefixo da propriedade do limite de corpo: `castel.web.max-request-body-size`, tipo `DataSize`, default `64KB` no `application.yml`. Segue o prefixo `castel.*` já usado por `castel.auth.login-rate-limit` e `castel.dev` | pendente (DEV, aguarda Ruan) |
| 11 | 1 | Formato do `errors` do 400 `VALIDATION_FAILED`: lista de objetos `{ "field": "<camelCase>", "code": "<REGRA>" }`, em que `code` é o nome da anotação de Bean Validation em `UPPER_SNAKE_CASE` (`@NotBlank` → `NOT_BLANK`). Nenhuma mensagem do Hibernate Validator vai para o corpo. Erro que não é de campo usa o nome do objeto validado em `field` | pendente (DEV, aguarda Ruan) |
| 12 | 1 | O identificador de correlação do 500 trafega no corpo no campo `correlationId` (UUID aleatório, não v7: é um token de log, não identidade de agregado) e aparece no log junto com a stack. `detail` do 500 é fixo: `Unexpected internal error` | pendente (DEV, aguarda Ruan) |
| 13 | 1 | O handler global declara `@ExceptionHandler` explícito para `AccessDeniedException` (403 `ACCESS_DENIED`) e `AuthenticationException` (401 `AUTHENTICATION_REQUIRED`), com os **mesmos códigos** que o `SecurityConfig` já emite. Sem isso, o `@ExceptionHandler(Exception.class)` engoliria o `AccessDeniedException` do method security e o 403 da 0.4 viraria 500. Não são códigos novos: são os dois da 0.4, escritos agora em dois lugares porque o `@PreAuthorize` dentro do controller não passa pelos handlers da cadeia | pendente (DEV, aguarda Ruan) |
| 14 | 1 | O UUID reservado de sistema da decisão #6 é `00000000-0000-0000-0000-000000000001`, constante `SystemAuthor.SYSTEM_USER_ID`. Zeros à esquerda o tornam reconhecível numa consulta e ele não colide com UUID v7 gerado | pendente (DEV, aguarda Ruan) |
| 15 | 1 | A auditoria de data lê o bean `Clock` do projeto, por um `DateTimeProvider`, em vez do relógio interno do Spring Data. Mantém a data de auditoria controlável no teste, como o resto do sistema | pendente (DEV, aguarda Ruan) |
| 16 | 1 | `MALFORMED_REQUEST` cujo *root cause* é o limite de corpo (corpo sem `Content-Length`, `chunked`) responde **413 `REQUEST_BODY_TOO_LARGE`**, não 400. É o mesmo limite, detectado durante a leitura em vez de no cabeçalho | pendente (DEV, aguarda Ruan) |

| 17 | 1 | `Setting` **não** é `@Entity`. É um agregado de Java puro no `shared-kernel` (chave, valor, `valueType`, com os seis leitores tipados), lido e escrito por um adaptador JDBC no `app`. Motivo: a regra ArchUnit D1 da 0.5a proíbe `@Entity` no `app` e a A4 proíbe JPA no `shared-kernel` — ver ponto em aberto 2. Três colunas lidas por chave não pagam um mapeamento JPA, e assim a regra de tipo fica testável sem banco | pendente (DEV, aguarda Ruan) |
| 18 | 1 | Porta de persistência `SettingRepository` (`findByKey`, `save`) no `shared-kernel`, com o cache Caffeine no **decorador da porta** (`CachingSettingRepository`), não no leitor tipado. Miss **não** é cacheado (chave configurada depois da primeira leitura tem de aparecer) e `save` invalida a chave antes de retornar. `save` só atualiza o valor de uma chave existente: inserir exigiria resolver `property_id`, que é o ponto em aberto 3 | pendente (DEV, aguarda Ruan) |
| 19 | 1 | `Percentage` gravada em **pontos percentuais**: `"10.00"` com `value_type = DECIMAL` é dez por cento, não 1000%. A spec não diz; é a forma como o operador digita | pendente (DEV, aguarda Ruan) |
| 20 | 1 | A superclasse `@MappedSuperclass` de auditoria **não entra nesta rodada** (ponto em aberto 1, sem casa possível). As quatro colunas entram direto em `User`, satisfazendo a decisão #5 e o aceite 7. Extraí-las para a superclasse depois é mecânico | **revertida pela #30** |
| 21 | 1 | O handler global e o `AuthExceptionHandler` do `identity` precisam de precedência **explícita**: Spring ordena `@ControllerAdvice` só por `@Order`, e `basePackages` não torna um advice "mais específico". **Provado pelo build**: sem ordem, o advice global vence (é descoberto antes) e `INVALID_CREDENTIALS`/`TOO_MANY_LOGIN_ATTEMPTS` passaram a responder 422, quebrando 9 testes da 0.4. Correção: global em `@Order(LOWEST_PRECEDENCE)` **e** `AuthExceptionHandler` em `@Order(HIGHEST_PRECEDENCE)` — uma anotação e um parágrafo de javadoc, nenhum código nem código de erro alterado | pendente (DEV, aguarda Ruan) |
| 24 | 1 | `type` de todo corpo de erro é `about:blank`, escrito explicitamente. O Spring 7 deixa `type` nulo por padrão e o campo desaparece do JSON; a spec exige os seis campos sempre. `about:blank` é o valor que a RFC 9457 assume na ausência do campo, então nada de novo é inventado. Alternativa, se o Ruan preferir: uma URI por problema apontando para documentação | pendente (DEV, aguarda Ruan) |
| 22 | 1 | `spring-boot-starter-validation` entra no `pom` do `app`: Bean Validation não estava no classpath e as duas linhas de validação da tabela do item 2 não existiriam sem ele. Nenhum DTO existente ganha anotação (o login não valida campo obrigatório de propósito, contrato da 0.4) | pendente (DEV, aguarda Ruan) |
| 23 | 1 | `updated_at`/`updated_by` são preenchidos **também no insert**. Era o comportamento padrão do `AuditingHandler` do Spring Data e foi mantido de propósito no listener próprio da #32: a primeira versão da linha também é uma escrita, e a coluna nunca fica "alterada por ninguém, nunca" | pendente (DEV, aguarda Ruan) |
| 25 | 1 | `type` fica em **`about:blank`** (confirma a #24). É o valor que a RFC 9457 assume na ausência do campo; o `code` continua sendo o que o front traduz. URI por família de erro criaria uma segunda fonte da verdade além do `code` | implementado |
| 26 | 1 | `asPercentage` lê **pontos percentuais**: `"10.00"` em `setting_value` é dez por cento, convertido na leitura para a fração que o `Percentage` armazena. Mantém a coluna legível para quem dá suporte olhando o banco | implementado |
| 27 | 1 | A v1 é **mono-propriedade**. O `Settings` resolve a única `property` existente e **derruba a inicialização** se encontrar mais de uma — falha explícita em vez de ler a linha errada em silêncio. `propertyId` não entra na assinatura (contaminaria todos os chamadores das Ondas 1 a 3 com um parâmetro que a v1 nunca varia) e não nasce `CurrentPropertyProvider`, que é infraestrutura de multi-propriedade (backlog v2) | implementado (ver ponto em aberto 7) |
| 28 | 1 | Entram na tabela de mapeamento as duas linhas que faltavam: `HandlerMethodValidationException` → 400 `VALIDATION_FAILED` e `AccessDeniedException` → 403 `ACCESS_DENIED`. A segunda já foi implementada na rodada 1 (#13); o 403 funcionando por omissão do catch-all era regressão silenciosa esperando acontecer | implementado |
| 29 | 1 | A exceção de `@Autowired` em campo para código de teste está escrita no `CLAUDE.md`, na seção "Técnico" (fecha o ponto em aberto 6 e a decisão #2) | implementado |
| 30 | 1 | A superclasse `@MappedSuperclass` de auditoria mora no **`shared-kernel`**, que ganha `jakarta.persistence-api` (só anotações; nem Hibernate nem Spring). A regra ArchUnit A4 passa a proibir `org.springframework..` e `org.hibernate..` e a **liberar** `jakarta.persistence..`, com nova prova de violação 1:1. Sem módulo novo e sem colunas de auditoria repetidas à mão em ~20 entidades das Ondas 1 a 3 (fecha o ponto em aberto 1 e a decisão #20) | implementado (ver #32) |
| 31 | 1 | `Setting` **fica como está**: Java puro no `shared-kernel`, lido por adaptador `JdbcClient` no `app`, sem `@Entity`. Três colunas lidas por chave não pagam mapeamento JPA e a regra de tipo fica testável sem banco (fecha o ponto em aberto 2) | implementado |

| 32 | 1 | **Consequência direta da #30, não prevista por ela:** com `org.springframework..` proibido no `shared-kernel`, a auditoria do Spring Data JPA fica **impossível de usar**. `@CreatedDate`, `@CreatedBy`, `@LastModifiedDate`, `@LastModifiedBy` e `@EntityListeners(AuditingEntityListener.class)` são todos `org.springframework.data..` e teriam de estar nos campos da superclasse, que agora mora no `shared-kernel`. Substituído por auditoria de **JPA puro**: `AuditedEntity` (`@MappedSuperclass`) declara as quatro colunas e delega a `AuditingListener`, um entity listener de JPA com `@PrePersist`/`@PreUpdate`, registrado como bean no `app` para receber `AuditorAware` e `Clock` **por construtor** (o provedor de persistência resolve o listener pelo bean container do Spring, o mesmo mecanismo pelo qual o `AuditingEntityListener` do Spring Data era injetado). `@EnableJpaAuditing` sai; a porta `AuditorAware` passa a ser uma interface de Java puro no `shared-kernel`, com o mesmo nome que a spec usa. Comportamento externo idêntico: os 13 testes de auditoria passam sem alteração | pendente (DEV, aguarda Ruan) |
| 33 | 1 | `SettingRepository.save` virou **upsert** (`on conflict (property_id, setting_key) do update`) com o `property_id` resolvido pela #27, e não mais só atualização de chave existente. O `value_type` de uma chave existente **não** é reescrito: setting que muda de tipo é outro parâmetro | pendente (DEV, aguarda Ruan) |
| 34 | 1 | A leitura de `setting` continua filtrando **só por `setting_key`**, e isso agora é seguro por construção: a #27 já provou na inicialização que existe no máximo uma `property`. Filtrar também por `property_id` não acrescentaria garantia nenhuma e só faria a consulta mentir sobre o que protege | pendente (DEV, aguarda Ruan) |

| 35 | 1 | A `property` única dos testes de integração do `app` é **semeada uma vez**, em `AbstractIntegrationTest`, antes de qualquer contexto subir, com o id compartilhado `0b7e3b8e-3c52-4c1e-9d0e-4a1f00000001`. Cada classe de teste tinha a sua própria `property`, e como cada uma roda sob uma configuração de contexto diferente, a segunda a subir encontrava mais de uma linha e o `SinglePropertyId` da #27 derrubava o contexto — corretamente. Semear exige o schema, então o Flyway também roda ali; o Flyway de cada contexto passa a não achar nada pendente, que é o que o `BaselineMigrationTest` já afirmava (fecha o ponto em aberto 7) | implementado |
| 36 | 1 | O teste `shouldRejectWriteOfAKeyThatWasNeverConfigured` foi escrito contra a #18 (`save` só atualiza chave existente), que a **#33 substituiu** por upsert quando a #27 resolveu o `property_id`. Virou `shouldInsertTheKeyWhenAWriteFindsNoRow`: gravar chave ausente insere. Nenhum código de produção mudou; o teste é que estava uma decisão atrás | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

### Vindo da Onda 0 (seção 6, task 0.5)
- [x] `GlobalExceptionHandler` → `ProblemDetail` RFC 7807 com campo `code`
- [x] Mapeamento de status: `DomainException` → 422 · conflito de estado → 409 · não encontrado → 404 · validação → 400
- [x] JPA Auditing com `AuditorAware` lendo o usuário do JWT
- [x] `Setting` com cache e leitura tipada
- [x] Superclasse `@MappedSuperclass` de auditoria, no `shared-kernel` (#30), com auditoria de JPA puro (#32)
- [x] Regra ArchUnit A4 reescrita: proíbe Spring e Hibernate, libera `jakarta.persistence`, com prova 1:1 para cada metade
- [x] Propriedade única resolvida na inicialização (#27)
- [x] `HandlerMethodValidationException` → 400 `VALIDATION_FAILED` (#28)

### Herdado da 0.4
- [x] Corpo do 404 e demais erros fora do `identity` em RFC 7807 (#3)
- [x] `instance` nos corpos de erro do filtro JWT e do `SecurityConfig` (#4)
- [x] Auditoria de `app_user` (#5)
- [x] Limite global de tamanho de corpo da requisição (#1)
- [x] Exceção de `@Autowired` em teste escrita no `CLAUDE.md` (#2) — feita pelo coordenador com autorização do Ruan (#29)

### Não faz parte da entrega
- Migration: a task não muda schema (#8). As colunas de auditoria e a tabela `setting` já vieram na `V1__baseline.sql`

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

**Códigos de erro** (novos nesta task; os do `identity` seguem no registro da 0.4, inalterados)

| Código | HTTP | Quando |
|---|---|---|
| *code da própria exceção* | 422 | `DomainException`: regra de domínio violada por um pedido bem formado |
| *code da própria exceção* | 409 | `ConflictException`: o estado atual do agregado recusa a operação |
| *code da própria exceção* | 404 | `NotFoundException`: o recurso pedido não existe |
| `VALIDATION_FAILED` | 400 | Bean Validation reprovou um ou mais campos; os campos vão em `errors` |
| `MALFORMED_REQUEST` | 400 | Corpo ausente ou ilegível (mesmo código que o `identity` já usa) |
| `REQUEST_BODY_TOO_LARGE` | 413 | Corpo acima de 64 KB, em qualquer rota |
| `RESOURCE_NOT_FOUND` | 404 | Nenhuma rota e nenhum recurso estático responde o caminho |
| `METHOD_NOT_ALLOWED` | 405 | O método HTTP não é aceito pela rota |
| `INTERNAL_ERROR` | 500 | Qualquer falha não mapeada; corpo sem mensagem interna, com `correlationId` |
| `SETTING_NOT_FOUND` | 404 | Chave de `setting` não configurada |
| `SETTING_TYPE_MISMATCH` | 422 | `setting` lida como tipo diferente do gravado |
| `MALFORMED_SETTING_VALUE` | 422 | Valor gravado não parseia no `value_type` que declara |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| Corpo de erro | `application/problem+json` com `type`, `title`, `status`, `detail`, `instance` e `code`, sempre |
| `instance` | Só o caminho da requisição, sem query string (pode carregar token ou CPF) |
| `errors` | `[{ "field": "guestName", "code": "NOT_BLANK" }]` |
| `correlationId` | Presente apenas no 500; o mesmo valor aparece no log com a stack |
| Limite de corpo | 64 KB, em `castel.web.max-request-body-size` |
| UUID de sistema | `00000000-0000-0000-0000-000000000001` |
| `setting` de percentual | Pontos percentuais: `"10.00"` é dez por cento |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| `setting` é lido só por `setting_key`, sem `property_id` | A instalação é mono-propriedade e isso é **provado na inicialização** (#27), então o filtro por chave é exato | Multi-propriedade é backlog v2 |
| Não há endpoint de escrita de `setting`; só o `save` da porta | Tela de administração é Onda 4; o `save` existe para sustentar a invalidação do cache | Onda 4 |
| A auditoria não usa Spring Data JPA | Consequência da #30: as anotações do Spring Data teriam de morar no `shared-kernel` (#32). Comportamento externo idêntico | — |
| `AuditingListener` depende do bean container do provedor de persistência para receber suas dependências | É o mesmo mecanismo pelo qual o `AuditingEntityListener` do Spring Data já era injetado neste projeto, e os 13 testes de auditoria o exercitam de ponta a ponta | — |
| `ConstraintViolationException` lançada fora de um `@Valid` perde o prefixo do caminho (usa só o último nó) | O nome do campo em `camelCase` é o que o front precisa | — |

---

## Pontos em aberto

Nenhum. Os pontos 3 a 6 foram fechados pelas decisões #25 a #29, os dois de
arquitetura (onde moram a superclasse de auditoria e o `Setting`) pelas decisões
#30 e #31, e o ponto 7 (colisão de fixture de `property` entre classes de teste)
pela decisão #35.

| # | Pergunta | Desde a rodada |
|---|---|---|
