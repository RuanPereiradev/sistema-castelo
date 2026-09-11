# Lote paralelo 1 — quatro frentes simultâneas

Estado: task 0.1 concluída e mergeada. Este documento abre as quatro tasks que
podem rodar **ao mesmo tempo**, em sessões diferentes do Claude Code.

---

## 1. Por que só quatro

O grafo da Onda 0 é quase linear:

```
0.1 (feito)
 ├──> 0.2  shared-kernel ────────┐
 ├──> 0.3  Flyway baseline ──> 0.4 auth ──> 0.5b transversal
 ├──> 0.5a regras ArchUnit       │              │
 └──> 0.7a estrutura .http       │              │
                                 └──> 0.6 contratos ──> [seu portão] ──> Onda 1
                                                              │
                                                        0.8 referência
```

As quatro do lote atual não dependem umas das outras e **não escrevem nos mesmos
arquivos** — com uma exceção, tratada na seção 4.

O que **não** entra e por quê:

| Task | Bloqueada por |
|---|---|
| 0.4 autenticação | precisa das tabelas da 0.3 |
| 0.5b erro e auditoria | precisa de `DomainException` (0.2) e do JWT (0.4) |
| 0.6 contratos | os DTOs usam `Money` e `DateRange` da 0.2 |
| 0.8 módulo de referência | precisa de tudo |

---

## 2. Convenção de branch

Uma branch por task, nomeada pelo código da task:

```
task/0.2-shared-kernel
task/0.3-flyway-baseline
task/0.5a-archunit-rules
task/0.7a-http-structure
```

Regra: **cada agente trabalha na sua branch e nunca commita na `main`.** Um PR
por task, mergeado por você após o review.

---

## 3. Convenção de commit

Conventional Commits no assunto, mais trailers identificando task e agente.

```
<tipo>(<escopo>): <descrição no imperativo, minúscula, sem ponto final>

<corpo opcional explicando o porquê, não o quê>

Task: <código>
Agent: <java-dev | unit-tester | code-reviewer>
```

**Tipos:** `feat`, `fix`, `test`, `refactor`, `chore`, `docs`
**Escopos:** `shared-kernel`, `identity`, `hotel`, `restaurant`, `billing`,
`payment`, `tax-invoice`, `app`, `infra`, `http`

### Exemplos

```
feat(shared-kernel): add Money and Percentage value objects

Money keeps BigDecimal scale 2 with HALF_UP on every operation, so a chain
of calculations never accumulates rounding error.

Task: 0.2
Agent: java-dev
```

```
test(shared-kernel): cover DateRange night counting and overlap rules

Written from the task spec without reading the implementation.

Task: 0.2
Agent: unit-tester
```

```
chore(app): add flyway baseline migration and testcontainers setup

Task: 0.3
Agent: java-dev
```

### Por que trailers e não prefixo no assunto

Trailer é chave-valor no fim da mensagem, e o Git trata isso como dado
estruturado. Dá para filtrar depois:

```bash
git log --grep="^Task: 0.2" --all
git log --grep="^Agent: unit-tester" --oneline
```

Colocar `[0.2]` no assunto polui a linha e não é consultável.

**Uma nota sobre autoria:** o Claude Code pode adicionar a própria assinatura
nos commits, dependendo da configuração. Se você não quiser essa linha junto com
os seus trailers, ajuste antes de começar o lote — mudar depois exigiria
reescrever histórico.

### Instrução a colar em toda task

```
Ao commitar, use Conventional Commits e adicione os trailers:

Task: <codigo>
Agent: java-dev

Uma linha em branco antes dos trailers. Nao commite na main:
crie e trabalhe na branch task/<codigo>-<slug>.
```

---

## 4. Regra de não colisão

Cada frente é dona de um conjunto de arquivos. Nenhum agente escreve fora dele.

| Task | Arquivos que pode tocar |
|---|---|
| 0.2 | `shared-kernel/**` |
| 0.3 | `app/src/main/resources/db/migration/**`, `app/src/test/resources/**`, config de Flyway |
| 0.5a | `app/src/test/java/**/architecture/**` |
| 0.7a | `http/**`, `.gitignore` |

**A única colisão:** 0.3 e 0.5a precisam adicionar dependência no `app/pom.xml`
(Flyway Postgres e ArchUnit). Solução: **mergeie a 0.3 primeiro**; quem for
segundo faz `git rebase main` e resolve o conflito, que é de duas linhas.

Se um agente disser que precisa alterar arquivo fora da sua lista, ele deve
parar e perguntar, não editar.

---

## 5. TASK 0.3 — Flyway baseline e Testcontainers

> Branch `task/0.3-flyway-baseline` · Agente `java-dev`

### Objetivo

Flyway configurado, primeira migration aplicada, e teste de integração rodando
contra Postgres real.

### Escopo

**Configuração do Flyway**
- Localização padrão `classpath:db/migration`
- `clean` habilitado em `dev`, **desabilitado** em `prod`
- `baseline-on-migrate: false`
- Validação ativa

**`V1__baseline.sql`** — copie o DDL da seção 4 do `docs/schema-banco-de-dados.md`,
sem alterar nomes, tipos ou constraints. As tabelas são: `property`, `setting`,
`app_user`, `user_role`. As extensões `pgcrypto` e `btree_gist` entram no topo
desta migration.

**Testcontainers**
- Perfil `test` usando container Postgres 16
- **Proibido H2.** Constraint de exclusão, índice parcial e tipo `TIMESTAMPTZ` se
  comportam diferente lá, e o teste passaria mentindo
- Container reaproveitado entre classes de teste, para o build não ficar lento

**Teste de integração**
- Sobe o Postgres, roda a migration do zero, e verifica que as quatro tabelas
  existem com as colunas esperadas

**`docs/MIGRATIONS.md`** — nota curta com a regra forward-only e a tabela de
numeração reservada (seção 3 do documento de schema).

### Fora do escopo

Entidades JPA, repositórios, as demais migrations, seed de dados.

### Critérios de aceite

- `./mvnw clean install` passa
- Migration roda em banco vazio sem erro
- Rodar a migration duas vezes não quebra
- Teste de integração usa Testcontainers, não H2
- `docker compose -f docker-compose.dev.yml up -d` seguido do app com perfil
  `dev` aplica a migration automaticamente

### Armadilha conhecida

O Flyway 10 em diante exige o artefato `flyway-database-postgresql` separado do
core. Ele já foi adicionado na task 0.1 — confirme que está presente antes de
começar, porque a falha acontece em runtime com mensagem pouco óbvia.

---

## 6. TASK 0.5a — Regras de arquitetura (ArchUnit)

> Branch `task/0.5a-archunit-rules` · Agente `java-dev`

### Objetivo

Colocar as regras de arquitetura no repositório **antes** do código chegar,
para que a primeira violação seja reprovada em vez de descoberta depois.

### Escopo

Uma classe de teste ArchUnit no módulo `app`, cobrindo:

**Fronteira entre módulos**
- Um módulo de domínio só importa `api/` de outro módulo, nunca `domain/`,
  `application/` ou `infra/`
- O grafo permitido está na seção 4 do plano técnico
- `shared-kernel` não importa nenhum outro módulo do projeto

**Pureza do `shared-kernel`**
- Nenhum `import org.springframework` no módulo
- Nenhum `jakarta.persistence`

**Modelo rico**
- Nenhuma classe `@Entity` com método público começando por `set`
- Nenhum campo anotado com `@Autowired`

**Correção técnica**
- Nenhum uso de `java.util.Date` ou `java.util.Calendar`
- Nenhum campo `double` ou `float` em classe de domínio
- Nenhuma classe terminando em `Manager`, `Helper`, `Util`, `Data`, `Info`
- Controller não depende de repositório diretamente

### O requisito que define esta task

**Cada regra precisa de um teste que prove que ela falha quando violada.**

Crie um pacote `architecture/violations` com classes propositalmente erradas —
uma entidade com setter, uma classe usando `java.util.Date`, uma chamada
`FooManager` — e verifique que a regra correspondente as reprova.

Sem isso, uma regra escrita errado passa verde para sempre e não protege nada.
Este é o critério de aceite mais importante da task.

As classes de violação ficam em escopo de teste e não são compiladas no jar de
produção.

### Fora do escopo

Handler de erro, auditoria, tabela de configuração — tudo isso é a 0.5b, que
depende de tasks ainda não concluídas.

### Critérios de aceite

- `./mvnw test -pl app` passa
- Toda regra tem seu teste de violação correspondente
- As regras rodam no `clean install`, reprovando o build quando violadas
- Nenhuma regra depende de classe que ainda não existe

---

## 7. TASK 0.7a — Estrutura de testes `.http`

> Branch `task/0.7a-http-structure` · Agente `java-dev`

### Objetivo

Montar a bancada de testes manuais que você vai usar em todas as ondas.

### Escopo

**Estrutura**
```
http/
├── README.md
├── http-client.env.json
├── http-client.private.env.json.example
└── 01-health.http
```

**`http-client.env.json`** (versionado) com ambientes `dev` e `local`, contendo
`host` e `username`.

**`http-client.private.env.json.example`** com a chave `password` e um valor
óbvio de placeholder. O arquivo real, sem `.example`, **nunca** é commitado.

**`.gitignore`** — verifique e complete. Precisa cobrir no mínimo:
`http-client.private.env.json`, `.env`, `target/`, `node_modules/`, `.idea/`,
`*.iml`.

**`01-health.http`** — requisição ao `/actuator/health` com asserção de que
retorna 200 e status `UP`. É pouco, mas prova que o encadeamento de variáveis de
ambiente funciona antes de existir autenticação.

**`README.md` da pasta** — como rodar no IntelliJ, como copiar o arquivo de
exemplo, como trocar de ambiente, e a convenção de numeração dos arquivos.

### Fora do escopo

Qualquer requisição autenticada. O `00-auth.http` nasce na task 0.4, junto com
os endpoints de login.

### Critérios de aceite

- `git check-ignore -v http-client.private.env.json` retorna a linha do
  `.gitignore`
- Com o app rodando, `01-health.http` executa verde no IntelliJ
- Nenhum segredo commitado

---

## 8. TASK 0.2 — `shared-kernel`

> Branch `task/0.2-shared-kernel` · Agentes `java-dev` e `unit-tester`

Spec completa em `docs/task-0.2-shared-kernel.md`.

Lembrete de execução: a **Parte A** vai para o `java-dev`; a **Parte B** vai para
o `unit-tester` em **sessão separada**, sem a implementação. Colar o arquivo
inteiro para o testador anula o efeito de separar os papéis.

---

## 9. Ordem de merge

```
1. 0.3  (primeiro, por causa do app/pom.xml)
2. 0.5a (rebase se conflitar no pom)
3. 0.2
4. 0.7a
```

Depois de mergear os quatro, abre a segunda leva: **0.4** (autenticação) e
**0.6** (contratos) podem rodar em paralelo, e a 0.6 é o seu portão antes da
Onda 1.

---

## 10. Checklist antes de abrir as sessões

- [ ] Token do GitHub revogado
- [ ] `main` atualizada com a 0.1 mergeada
- [ ] `docs/` contém plano técnico, execução da Onda 0, schema e spec da 0.2
- [ ] `claude agents` lista os três agentes
- [ ] Quatro sessões separadas do Claude Code abertas, uma por branch
