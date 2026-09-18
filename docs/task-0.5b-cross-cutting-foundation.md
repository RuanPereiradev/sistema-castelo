# Task 0.5b — Fundação transversal

**Branch:** `task/0.5b-cross-cutting-foundation`
**Depende de:** 0.4 (mergeada, PR #5) · 0.5a (mergeada, PR #4)
**Registro de decisões:** `docs/decisions/task-0.5b.md`
**Review:** teto de **três rodadas** (decisão #74 da 0.4)

---

## 1. Objetivo

Toda resposta de erro da API sai em RFC 7807 com um `code` estável, toda tabela
transacional se audita sozinha, e parâmetro de operação se lê de `setting` sem
ir ao banco a cada chamada.

---

## 2. Escopo fechado

### Dentro

**Handler global de erro**
- `GlobalExceptionHandler` como `@RestControllerAdvice` sem `basePackages`, no
  módulo `app`. O `AuthExceptionHandler` do `identity` já é escopado ao próprio
  pacote e **continua vencendo** nos endpoints dele (advice mais específico
  primeiro) — não mexer nele.
- Mapeamento:

  | Situação | HTTP | `code` |
  |---|---|---|
  | `DomainException` (regra de domínio violada) | 422 | `exception.code()` |
  | `ConflictException` (conflito de estado) | 409 | `exception.code()` |
  | `NotFoundException` (recurso inexistente) | 404 | `exception.code()` |
  | Bean Validation (`MethodArgumentNotValidException`, `ConstraintViolationException`) | 400 | `VALIDATION_FAILED` |
  | Corpo ilegível (`HttpMessageNotReadableException`) | 400 | `MALFORMED_REQUEST` |
  | Corpo acima do limite | 413 | `REQUEST_BODY_TOO_LARGE` |
  | Rota inexistente (`NoResourceFoundException`) | 404 | `RESOURCE_NOT_FOUND` |
  | Método não suportado | 405 | `METHOD_NOT_ALLOWED` |
  | Qualquer outra exceção | 500 | `INTERNAL_ERROR` |

- `ConflictException` e `NotFoundException` nascem no `shared-kernel`, abstratas,
  estendendo `DomainException` (que já exige `code` explícito). São **marcadores
  de tradução HTTP**, não tipos com comportamento.
- Todo corpo de erro carrega `type`, `title`, `status`, `detail`, `instance` e
  `code`. `instance` é o caminho da requisição.
- O 500 **nunca** vaza mensagem de exceção: `detail` fixo, stack no log com um
  identificador de correlação repetido no corpo.
- Erro de validação lista os campos inválidos em `errors`, com nome do campo em
  `camelCase` e o `code` da regra — nunca a mensagem default do Hibernate
  Validator em inglês, que o front não sabe traduzir.

**Limite de tamanho de corpo** (decisão #1 / #78 da 0.4)
- Filtro único, antes da cadeia de segurança, recusando corpo acima do limite com
  413 e `code` estável, sem ler o corpo inteiro na memória.
- Vale para toda a API, não só para o login.
- Limite configurável por propriedade, com default no `application.yml`.

**Auditoria JPA**
- `@EnableJpaAuditing` com `AuditorAware<UUID>` lendo o `CurrentUserProvider` do
  `identity/api`.
- Sem usuário no contexto (seed do dev, migration, job futuro): **UUID reservado
  de sistema**, constante e documentada (decisão #6). Não `null`, não string
  mágica.
- Superclasse `@MappedSuperclass` com `created_at`, `created_by`, `updated_at`,
  `updated_by`, para toda entidade transacional das ondas seguintes herdar.
- `app_user` passa a auditar `updated_at`/`updated_by` (decisão #5 / herdado da 0.4).

**`Setting`**
- Agregado `Setting` mapeando a tabela `setting` (já existe na V1): `settingKey`,
  `settingValue`, `valueType` (`STRING · INTEGER · DECIMAL · BOOLEAN · TIME`).
- Leitura **tipada** por porta no domínio: `asText`, `asInteger`, `asMoney`,
  `asPercentage`, `asBoolean`, `asLocalTime`. Tipo pedido diferente do
  `valueType` gravado é erro de domínio, não conversão silenciosa.
- Chave inexistente: exceção de domínio com `code`, nunca `null`.
- Cache por propriedade, invalidado na escrita.

**Herdado da 0.4**
- Corpo do 404 e demais erros fora do `identity` em RFC 7807 (decisão #3)
- `instance` nos corpos de erro escritos pelo `JwtAuthenticationFilter` e pelo
  `SecurityConfig` (decisão #4)
- Exceção de `@Autowired` em campo para código de teste escrita no `CLAUDE.md`
  (decisão #2 / #79)

### Fora

- Qualquer endpoint novo de escrita de `setting` (tela de admin é Onda 4)
- Tabela de trilha de auditoria (quem mudou o quê): auditoria aqui é só as quatro
  colunas por linha
- Rate limit genérico por rota: o do login já existe e o resto é Onda 5.3
- Migration: **esta task não muda schema** (ver item 4)
- Tocar no `AuthExceptionHandler` ou nos códigos de erro do `identity`, que já
  são contrato com o front

---

## 3. Contratos

```java
// shared-kernel
public abstract class NotFoundException extends DomainException { ... }   // -> 404
public abstract class ConflictException extends DomainException { ... }   // -> 409

// app/api de configuração — leitura tipada, consumida por todos os módulos
public interface Settings {
    String asText(String key);
    int asInteger(String key);
    Money asMoney(String key);
    Percentage asPercentage(String key);
    boolean asBoolean(String key);
    LocalTime asLocalTime(String key);
}
```

O módulo que ler configuração depende só desta interface. Onde ela mora
(`shared-kernel` ou um `api/` próprio) é decisão da rodada 0 — ver item 10.

---

## 4. Modelo de dados

**Nenhuma migration.** As quatro colunas de auditoria já existem em `property`,
`setting` e `app_user` desde a `V1__baseline.sql`, e a tabela `setting` também.

`created_by`/`updated_by` ficam **nullable** no banco. Torná-las `NOT NULL`
exigiria uma migration, e a numeração de V3 a V9 já está reservada por task em
`docs/MIGRATIONS.md` — renumerar por uma restrição que o `AuditorAware` já
garante em código não se paga. Registrado para aprovação.

---

## 5. Invariantes

- Resposta de erro **nunca** sai sem `code`.
- `code` de 500 é sempre `INTERNAL_ERROR`; nenhuma mensagem interna, nome de
  classe, SQL ou caminho de arquivo aparece no corpo.
- Duas exceções diferentes nunca compartilham `code` com HTTP diferente.
- `created_by` de uma linha nunca muda depois do insert.
- `updated_at`/`updated_by` mudam em todo update, sem exceção.
- Sem usuário autenticado, o autor é o UUID de sistema — nunca nulo.
- `Setting` lida como um tipo diferente do gravado falha; não converte.
- O cache de `setting` nunca devolve valor anterior a uma escrita já confirmada.

---

## 6. Critérios de aceite

1. `./mvnw clean install` passa, ArchUnit incluído.
2. Cada linha da tabela de mapeamento do item 2 tem um teste que prova o par
   (status, `code`).
3. Um `GET` em rota inexistente responde 404 em `application/problem+json` com
   `code` e `instance` — hoje não responde.
4. Erro escrito pelo filtro JWT e pelo `SecurityConfig` traz `instance`.
5. 500 forjado não vaza mensagem de exceção no corpo, e o log traz o mesmo
   identificador de correlação que o corpo.
6. Corpo acima do limite responde 413 com `code`, e o teste prova que o corpo
   **não** foi inteiramente carregado.
7. Entidade salva sem usuário no contexto grava o UUID de sistema; salva com
   usuário autenticado grava o `UserId` dele; update mexe em `updated_*` e não em
   `created_*`.
8. `Setting` lido como tipo errado falha com `code`; chave inexistente falha com
   `code`; escrita invalida o cache.
9. Os três itens herdados da 0.4 conferidos contra
   `docs/decisions/task-0.4.md`, seção "Movido para outra task".

---

## 7. Dependências

`identity/api` (`CurrentUserProvider`, `UserId`) · `shared-kernel` (`Money`,
`Percentage`, `DomainException`) · Caffeine, já no projeto pelo limitador de
login.

---

## 8. `.http`

Arquivo novo `http/03-errors.http`, todo ele cenário negativo, encadeado ao
token de `00-auth.http`:

| Cenário | Espera |
|---|---|
| `GET /api/does-not-exist` autenticado | 404 · `RESOURCE_NOT_FOUND` · `instance` presente |
| `DELETE` em rota que só aceita `GET` | 405 · `METHOD_NOT_ALLOWED` |
| `POST` com JSON malformado | 400 · `MALFORMED_REQUEST` · nada do corpo no log |
| `POST` com corpo acima do limite | 413 · `REQUEST_BODY_TOO_LARGE` |
| `POST` com campo obrigatório ausente | 400 · `VALIDATION_FAILED` · `errors` com o campo |
| `GET` sem token em rota protegida | 401 · `code` do `identity` · `instance` presente |
| Todo corpo de erro | `content-type: application/problem+json` |

---

## 9. Conformidade de estilo

Segue o `CLAUDE.md`: `Setting` é agregado rico, sem setter público, criado por
factory nomeada; leitura tipada é método de negócio, não `switch` em `Service`;
`valueType` é enum com comportamento, não `if` em cadeia. Injeção por construtor.
Nenhum `if` de regra de negócio em `@Service`.

O módulo de referência (0.8) ainda não existe, então esta task **não** é gabarito
de fatia vertical — não tem controller nem endpoint novo.

---

## 10. Decisões da rodada 0 a confirmar

Três pontos que mudam código e eu não decido sozinho:

1. **Onde mora a interface `Settings`** — `shared-kernel` (todos já dependem
   dele, mas configuração não é value object) ou um `api/` no `app`, que é a raiz
   de composição. Proposta: `shared-kernel`, pela dependência já existente.
2. **Valor default do limite de corpo.** Proposta: 64 KB. O maior corpo legítimo
   previsto na v1 é uma comanda com muitos itens, na casa de poucos KB.
3. **`created_by`/`updated_by` seguem nullable no banco** (item 4), sem migration.

---

## 11. Commits

Um commit por eixo, na ordem: `feat(shared-kernel)` marcadores de exceção ·
`feat(app)` handler global e limite de corpo · `feat(app)` auditoria ·
`feat(app)` `Setting` · `test(app)` cobertura · `docs` registro de decisões.
