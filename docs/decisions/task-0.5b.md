# Decisões — Task 0.5b fundação transversal

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Breno confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

Vale a decisão #74 da 0.4: **teto de três rodadas de review**. Na terceira, o
que não for bloqueio crítico é registrado aqui e mergeado.

---

## Estado

| | |
|---|---|
| Branch | `task/0.5b-cross-cutting-foundation` |
| Rodada atual | 1 — implementação (DEV e TEST em paralelo a partir da spec) |
| Build | `./mvnw clean install` verde ao fim da rodada 1 de implementação |
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

| 10 | 1 | Prefixo da propriedade do limite de corpo: `castel.web.max-request-body-size`, tipo `DataSize`, default `64KB` no `application.yml`. Segue o prefixo `castel.*` já usado por `castel.auth.login-rate-limit` e `castel.dev` | pendente (DEV, aguarda Breno) |
| 11 | 1 | Formato do `errors` do 400 `VALIDATION_FAILED`: lista de objetos `{ "field": "<camelCase>", "code": "<REGRA>" }`, em que `code` é o nome da anotação de Bean Validation em `UPPER_SNAKE_CASE` (`@NotBlank` → `NOT_BLANK`). Nenhuma mensagem do Hibernate Validator vai para o corpo. Erro que não é de campo usa o nome do objeto validado em `field` | pendente (DEV, aguarda Breno) |
| 12 | 1 | O identificador de correlação do 500 trafega no corpo no campo `correlationId` (UUID aleatório, não v7: é um token de log, não identidade de agregado) e aparece no log junto com a stack. `detail` do 500 é fixo: `Unexpected internal error` | pendente (DEV, aguarda Breno) |
| 13 | 1 | O handler global declara `@ExceptionHandler` explícito para `AccessDeniedException` (403 `ACCESS_DENIED`) e `AuthenticationException` (401 `AUTHENTICATION_REQUIRED`), com os **mesmos códigos** que o `SecurityConfig` já emite. Sem isso, o `@ExceptionHandler(Exception.class)` engoliria o `AccessDeniedException` do method security e o 403 da 0.4 viraria 500. Não são códigos novos: são os dois da 0.4, escritos agora em dois lugares porque o `@PreAuthorize` dentro do controller não passa pelos handlers da cadeia | pendente (DEV, aguarda Breno) |
| 14 | 1 | O UUID reservado de sistema da decisão #6 é `00000000-0000-0000-0000-000000000001`, constante `SystemAuthor.SYSTEM_USER_ID`. Zeros à esquerda o tornam reconhecível numa consulta e ele não colide com UUID v7 gerado | pendente (DEV, aguarda Breno) |
| 15 | 1 | A auditoria de data lê o bean `Clock` do projeto, por um `DateTimeProvider`, em vez do relógio interno do Spring Data. Mantém a data de auditoria controlável no teste, como o resto do sistema | pendente (DEV, aguarda Breno) |
| 16 | 1 | `MALFORMED_REQUEST` cujo *root cause* é o limite de corpo (corpo sem `Content-Length`, `chunked`) responde **413 `REQUEST_BODY_TOO_LARGE`**, não 400. É o mesmo limite, detectado durante a leitura em vez de no cabeçalho | pendente (DEV, aguarda Breno) |

| 17 | 1 | `Setting` **não** é `@Entity`. É um agregado de Java puro no `shared-kernel` (chave, valor, `valueType`, com os seis leitores tipados), lido e escrito por um adaptador JDBC no `app`. Motivo: a regra ArchUnit D1 da 0.5a proíbe `@Entity` no `app` e a A4 proíbe JPA no `shared-kernel` — ver ponto em aberto 2. Três colunas lidas por chave não pagam um mapeamento JPA, e assim a regra de tipo fica testável sem banco | pendente (DEV, aguarda Breno) |
| 18 | 1 | Porta de persistência `SettingRepository` (`findByKey`, `save`) no `shared-kernel`, com o cache Caffeine no **decorador da porta** (`CachingSettingRepository`), não no leitor tipado. Miss **não** é cacheado (chave configurada depois da primeira leitura tem de aparecer) e `save` invalida a chave antes de retornar. `save` só atualiza o valor de uma chave existente: inserir exigiria resolver `property_id`, que é o ponto em aberto 3 | pendente (DEV, aguarda Breno) |
| 19 | 1 | `Percentage` gravada em **pontos percentuais**: `"10.00"` com `value_type = DECIMAL` é dez por cento, não 1000%. A spec não diz; é a forma como o operador digita | pendente (DEV, aguarda Breno) |
| 20 | 1 | A superclasse `@MappedSuperclass` de auditoria **não entra nesta rodada** (ponto em aberto 1, sem casa possível). As quatro colunas entram direto em `User`, satisfazendo a decisão #5 e o aceite 7. Extraí-las para a superclasse depois é mecânico | pendente (DEV, aguarda Breno) |
| 21 | 1 | O handler global e o `AuthExceptionHandler` do `identity` precisam de precedência **explícita**: Spring ordena `@ControllerAdvice` só por `@Order`, e `basePackages` não torna um advice "mais específico". **Provado pelo build**: sem ordem, o advice global vence (é descoberto antes) e `INVALID_CREDENTIALS`/`TOO_MANY_LOGIN_ATTEMPTS` passaram a responder 422, quebrando 9 testes da 0.4. Correção: global em `@Order(LOWEST_PRECEDENCE)` **e** `AuthExceptionHandler` em `@Order(HIGHEST_PRECEDENCE)` — uma anotação e um parágrafo de javadoc, nenhum código nem código de erro alterado | pendente (DEV, aguarda Breno) |
| 24 | 1 | `type` de todo corpo de erro é `about:blank`, escrito explicitamente. O Spring 7 deixa `type` nulo por padrão e o campo desaparece do JSON; a spec exige os seis campos sempre. `about:blank` é o valor que a RFC 9457 assume na ausência do campo, então nada de novo é inventado. Alternativa, se o Breno preferir: uma URI por problema apontando para documentação | pendente (DEV, aguarda Breno) |
| 22 | 1 | `spring-boot-starter-validation` entra no `pom` do `app`: Bean Validation não estava no classpath e as duas linhas de validação da tabela do item 2 não existiriam sem ele. Nenhum DTO existente ganha anotação (o login não valida campo obrigatório de propósito, contrato da 0.4) | pendente (DEV, aguarda Breno) |
| 23 | 1 | Spring Data preenche `updated_at`/`updated_by` **também no insert** (comportamento padrão do `AuditingHandler`). Aceito: o schema permite, e a alternativa seria reimplementar o listener | pendente (DEV, aguarda Breno) |
| 25 | 1 | `type` fica em **`about:blank`** (confirma a #24). É o valor que a RFC 9457 assume na ausência do campo; o `code` continua sendo o que o front traduz. URI por família de erro criaria uma segunda fonte da verdade além do `code` | implementado |
| 26 | 1 | `asPercentage` lê **pontos percentuais**: `"10.00"` em `setting_value` é dez por cento, convertido na leitura para a fração que o `Percentage` armazena. Mantém a coluna legível para quem dá suporte olhando o banco | implementado |
| 27 | 1 | A v1 é **mono-propriedade**. O `Settings` resolve a única `property` existente e **derruba a inicialização** se encontrar mais de uma — falha explícita em vez de ler a linha errada em silêncio. `propertyId` não entra na assinatura (contaminaria todos os chamadores das Ondas 1 a 3 com um parâmetro que a v1 nunca varia) e não nasce `CurrentPropertyProvider`, que é infraestrutura de multi-propriedade (backlog v2) | pendente |
| 28 | 1 | Entram na tabela de mapeamento as duas linhas que faltavam: `HandlerMethodValidationException` → 400 `VALIDATION_FAILED` e `AccessDeniedException` → 403 `ACCESS_DENIED`. A segunda já foi implementada na rodada 1 (#13); o 403 funcionando por omissão do catch-all era regressão silenciosa esperando acontecer | parcial: `AccessDeniedException` implementado, `HandlerMethodValidationException` pendente |
| 29 | 1 | A exceção de `@Autowired` em campo para código de teste está escrita no `CLAUDE.md`, na seção "Técnico" (fecha o ponto em aberto 6 e a decisão #2) | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Breno **entra aqui** e só sai por decisão
explícita dele.

### Vindo da Onda 0 (seção 6, task 0.5)
- [x] `GlobalExceptionHandler` → `ProblemDetail` RFC 7807 com campo `code`
- [x] Mapeamento de status: `DomainException` → 422 · conflito de estado → 409 · não encontrado → 404 · validação → 400
- [x] JPA Auditing com `AuditorAware` lendo o usuário do JWT
- [x] `Setting` com cache e leitura tipada
- [ ] Superclasse `@MappedSuperclass` de auditoria — bloqueada pelo ponto em aberto 1 (#20)

### Herdado da 0.4
- [x] Corpo do 404 e demais erros fora do `identity` em RFC 7807 (#3)
- [x] `instance` nos corpos de erro do filtro JWT e do `SecurityConfig` (#4)
- [x] Auditoria de `app_user` (#5)
- [x] Limite global de tamanho de corpo da requisição (#1)
- [ ] Exceção de `@Autowired` em teste escrita no `CLAUDE.md` (#2) — o `CLAUDE.md` é configuração do projeto e não é alterado pelo agente DEV; precisa da mão do Breno

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
| `setting` é lido só por `setting_key`, sem `property_id` | A v1 roda uma única propriedade e o contrato do item 3 não tem propriedade. Se duas propriedades gravarem a mesma chave, a consulta falha alto em vez de escolher uma | Ponto em aberto 3 |
| Não há caminho de escrita de `setting` além do `save` da porta (atualização de chave existente) | Tela de administração é Onda 4; o `save` existe para sustentar a invalidação do cache | Onda 4 |
| Sem superclasse `@MappedSuperclass` de auditoria: as colunas estão em `User` | Nenhum módulo pode hospedá-la hoje (ponto em aberto 1) | Extração mecânica na task que decidir a casa |
| `ConstraintViolationException` lançada fora de um `@Valid` perde o prefixo do caminho (usa só o último nó) | O nome do campo em `camelCase` é o que o front precisa | — |

---

## Pontos em aberto

Os pontos 3, 4, 5 e 6 foram fechados na rodada 1 pelas decisões #25 a #29.
Sobram os dois de arquitetura, que dependem de onde as classes JPA transversais
podem morar.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | **Onde mora a superclasse `@MappedSuperclass` de auditoria?** Ela não cabe em `shared-kernel` (a regra ArchUnit A4 da 0.5a proíbe `org.springframework..` e `jakarta.persistence..` lá, e o módulo não tem essas dependências no `pom`) e não cabe em `app` (nenhum módulo pode depender de `app`; entidade em `app` também é proibida pela regra D1). Colocá-la em `identity` faria `hotel`/`restaurant`/`billing` dependerem de `identity`, fora do grafo permitido. As saídas são: **(a)** módulo novo de plataforma ao lado do `shared-kernel`, do qual todos dependem; **(b)** abrir exceção na regra A4 e dar ao `shared-kernel` as dependências de `jakarta.persistence` e `spring-data-commons`. Nenhuma das duas é decisão do DEV, e a decisão #7 diz que nenhum pacote ou diretório novo nasce nesta task | 1 |
| 2 | **Onde mora o agregado `Setting` (`@Entity`)?** Mesmo impasse da pergunta 1: `@Entity` em `app` é proibida pela regra D1 e `shared-kernel` não pode ver JPA. A interface `Settings` no `shared-kernel` (decisão #7) resolve o lado do *consumidor*, não o da implementação. Opção de menor custo sem módulo novo: `Setting` em `identity` (o glossário agrupa `Setting` em "Identidade e transversais"), com os outros módulos dependendo só da interface — mas configuração de hotel e restaurante dentro do módulo de identidade é uma escolha de arquitetura, não de implementação | 1 |
