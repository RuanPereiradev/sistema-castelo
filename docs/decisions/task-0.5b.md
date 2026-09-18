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
| Rodada atual | 0 — spec e briefing |
| Build | não rodado nesta branch |
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
| | | |
