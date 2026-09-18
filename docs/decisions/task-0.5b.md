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
| Build | ver a última linha da seção "Decisões confirmadas" |
| Testes | 884 herdados da 0.4 |

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
| 12 | 1 | O identificador de correlação do 500 trafega no corpo no campo `correlationId` (UUID v7) e aparece no log junto com a stack. `detail` do 500 é fixo: `Unexpected internal error` | pendente (DEV, aguarda Breno) |
| 13 | 1 | O handler global declara `@ExceptionHandler` explícito para `AccessDeniedException` (403 `ACCESS_DENIED`) e `AuthenticationException` (401 `AUTHENTICATION_REQUIRED`), com os **mesmos códigos** que o `SecurityConfig` já emite. Sem isso, o `@ExceptionHandler(Exception.class)` engoliria o `AccessDeniedException` do method security e o 403 da 0.4 viraria 500 | pendente (DEV, aguarda Breno) |
| 14 | 1 | O UUID reservado de sistema da decisão #6 é `00000000-0000-0000-0000-000000000001`, constante `SystemAuthor.SYSTEM_USER_ID`. Zeros à esquerda o tornam reconhecível numa consulta e ele não colide com UUID v7 gerado | pendente (DEV, aguarda Breno) |
| 15 | 1 | A auditoria de data lê o bean `Clock` do projeto, por um `DateTimeProvider`, em vez do relógio interno do Spring Data. Mantém a data de auditoria controlável no teste, como o resto do sistema | pendente (DEV, aguarda Breno) |
| 16 | 1 | `MALFORMED_REQUEST` cujo *root cause* é o limite de corpo (corpo sem `Content-Length`, `chunked`) responde **413 `REQUEST_BODY_TOO_LARGE`**, não 400. É o mesmo limite, detectado durante a leitura em vez de no cabeçalho | pendente (DEV, aguarda Breno) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Breno **entra aqui** e só sai por decisão
explícita dele.

### Vindo da Onda 0 (seção 6, task 0.5)
- [ ] `GlobalExceptionHandler` → `ProblemDetail` RFC 7807 com campo `code`
- [ ] Mapeamento de status: `DomainException` → 422 · conflito de estado → 409 · não encontrado → 404 · validação → 400
- [ ] JPA Auditing com `AuditorAware` lendo o usuário do JWT
- [ ] `Setting` com cache e leitura tipada

### Herdado da 0.4
- [ ] Corpo do 404 e demais erros fora do `identity` em RFC 7807 (#3)
- [ ] `instance` nos corpos de erro do filtro JWT e do `SecurityConfig` (#4)
- [ ] Auditoria de `app_user` (#5)
- [ ] Limite global de tamanho de corpo da requisição (#1)
- [ ] Exceção de `@Autowired` em teste escrita no `CLAUDE.md` (#2)

### Não faz parte da entrega
- Migration: a task não muda schema (#8). As colunas de auditoria e a tabela `setting` já vieram na `V1__baseline.sql`

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| | | | |

---

## Contrato com o front

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| | | |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| | |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| | | |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | **Onde mora a superclasse `@MappedSuperclass` de auditoria?** Ela não cabe em `shared-kernel` (a regra ArchUnit A4 da 0.5a proíbe `org.springframework..` e `jakarta.persistence..` lá, e o módulo não tem essas dependências no `pom`) e não cabe em `app` (nenhum módulo pode depender de `app`; entidade em `app` também é proibida pela regra D1). Colocá-la em `identity` faria `hotel`/`restaurant`/`billing` dependerem de `identity`, fora do grafo permitido. As saídas são: **(a)** módulo novo de plataforma ao lado do `shared-kernel`, do qual todos dependem; **(b)** abrir exceção na regra A4 e dar ao `shared-kernel` as dependências de `jakarta.persistence` e `spring-data-commons`. Nenhuma das duas é decisão do DEV, e a decisão #7 diz que nenhum pacote ou diretório novo nasce nesta task | 1 |
| 2 | **Onde mora o agregado `Setting` (`@Entity`)?** Mesmo impasse da pergunta 1: `@Entity` em `app` é proibida pela regra D1 e `shared-kernel` não pode ver JPA. A interface `Settings` no `shared-kernel` (decisão #7) resolve o lado do *consumidor*, não o da implementação. Opção de menor custo sem módulo novo: `Setting` em `identity` (o glossário agrupa `Setting` em "Identidade e transversais"), com os outros módulos dependendo só da interface — mas configuração de hotel e restaurante dentro do módulo de identidade é uma escolha de arquitetura, não de implementação | 1 |
| 3 | **Qual `property_id` a leitura de `setting` usa?** A tabela tem `property_id NOT NULL` e única `(property_id, setting_key)`, mas o contrato do item 3 é `asText(String key)`, sem propriedade. Não existe conceito de "propriedade corrente" no código (o `DevUserSeeder` cria uma sob demanda). A v1 é de uma só propriedade, então ler por `setting_key` funciona hoje; o que falta é a regra: ler pela propriedade do usuário autenticado, ou assumir propriedade única e falhar se houver mais de uma linha para a chave | 1 |
