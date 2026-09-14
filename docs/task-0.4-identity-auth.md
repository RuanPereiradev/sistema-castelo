# Task 0.4 — `identity` e autenticação

Branch `task/0.4-identity-auth`.

**A task mais delicada da Onda 0.** É segurança, é a primeira superfície HTTP, e
é a primeira rota que você testa de verdade no IntelliJ.

---

## Aviso: Spring Security 7

O projeto usa Spring Boot 4.1.1, que traz **Spring Security 7**. A maior parte
do material disponível sobre configuração de segurança em Spring descreve a
linha 6, cuja API difere.

**Antes de escrever o filter chain, consulte a documentação da versão 7.** Não
reproduza de memória o padrão que você conhece. Este é o ponto do projeto com
maior chance de sair código desatualizado que compila mas se comporta de forma
diferente do esperado.

---

## Decisões já tomadas

| Decisão | Valor |
|---|---|
| Access token | 15 minutos |
| Refresh token | 7 dias |
| Login | `username` curto + senha (não e-mail) |
| Sessão simultânea | Proibida. Novo login invalida a sessão anterior |
| Hash de senha | BCrypt, custo 12 |
| Segredo do JWT | Variável de ambiente, nunca no `application.yml` |
| Biblioteca JWT | jjwt **0.13.0** (versão em `jjwt.version` no `dependencyManagement` do pom raiz) |
| Limite de login | Duas camadas de **falhas** por minuto: 10 por par (IP, username) e 100 por IP, configuráveis. Mais um limite de 3 BCrypts simultâneos por IP, como proteção de CPU, com espera de até 2 s e teto de 30 requisições esperando por IP |
| Expiração no segundo exato do `exp` | Token **aceito** (comportamento do jjwt). A RFC 7519 admite tolerância de relógio, e sobrescrever o parser criaria código próprio em caminho crítico |
| Claim `type` | Minúsculas, comparação exata: `access` e `refresh` |
| `roles` no refresh token | Não vai. No refresh, os papéis são relidos do banco para o novo access token: remover um papel vale em até 15 min, e não em até 7 dias |
| Segredo do JWT (formato) | Texto em UTF-8 com no mínimo 32 bytes **após `trim()`**. Abaixo disso o boot falha com mensagem clara, sem mostrar o valor |
| Logout | `204` sem corpo |
| Limitador de login | Checagem antes do BCrypt, contagem da falha depois dele. Login correto só recebe `429` se o próprio par ou o IP estiver no limite, se a espera pela vaga de BCrypt estourar o timeout de 2 s, ou se o IP já tiver 30 requisições esperando vaga |
| Username no login | Validado **antes de tudo**: normalizado para minúsculas e, fora de `[a-z0-9._]` com 3 a 30 caracteres, tratado como usuário inexistente, sem consulta ao banco e com o BCrypt descartável |
| Lock no login | Leitura do usuário **sem lock**; BCrypt **fora de transação**; lock pessimista só no sucesso, para `registerLogin` |
| Senha | De 8 **code points** a 72 bytes em UTF-8 |
| Validação de domínio | `422` (`INVALID_USERNAME`, `WEAK_PASSWORD`, `PASSWORD_TOO_LONG`, `USER_WITHOUT_ROLES`) |

**Mudança de decisão: jjwt 0.12.x → 0.13.0 (2026-09-13).** A decisão original
era a linha 0.12.x. A 0.13.0 é release estável, e não milestone nem RC:
`latest` e `release` do `maven-metadata.xml` no Maven Central apontam para ela,
e a release no GitHub (`jwtk/jjwt`, publicada em 2025-08-20) tem
`prerelease: false`. Como o código já compilava e passava contra a 0.13.0, a
versão foi mantida em vez de voltar para a 0.12.6.

---

## Renumeração das migrations

Esta task adiciona colunas em `app_user`, e a V1 já está mergeada. Pela regra
forward-only, entra uma migration nova, que toma o **V2**. Todas as posteriores
deslocam em um:

| Versão | Conteúdo | Task |
|---|---|---|
| V1 | baseline | 0.3 (feito) |
| **V2** | **auth: `username`, `token_version`** | **0.4** |
| V3 | cardápio | 0.8 |
| V4 | billing | 1.3 |
| V5 | inventário do hotel | 1.1 |
| V6 | mesas | 1.5 |
| V7 | reservas | 2.1 |
| V8 | comandas | 2.2 |
| V9 | caixa | 2.4 |

Atualize `docs/MIGRATIONS.md` e a seção 3 de `docs/schema-banco-de-dados.md`
com essa numeração.

---

## `V2__auth.sql`

```sql
ALTER TABLE app_user
    ADD COLUMN username      VARCHAR(30),
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;

-- email deixa de ser obrigatório: cozinheiro pode não ter
ALTER TABLE app_user ALTER COLUMN email DROP NOT NULL;

-- unicidade do email só quando presente
ALTER TABLE app_user DROP CONSTRAINT uk_app_user_email;
CREATE UNIQUE INDEX uk_app_user_email ON app_user (email) WHERE email IS NOT NULL;

-- username é o novo campo de login
UPDATE app_user SET username = split_part(email, '@', 1) WHERE username IS NULL;
ALTER TABLE app_user ALTER COLUMN username SET NOT NULL;
CREATE UNIQUE INDEX uk_app_user_username ON app_user (lower(username));
```

O índice sobre `lower(username)` torna o login **insensível a maiúsculas** sem
guardar o valor normalizado — `Joao` e `joao` são o mesmo usuário.

---

## Escopo

### Agregado `User`

Entidade rica, sem setter público.

- `username`, `passwordHash`, `fullName`, `email` (opcional), `isActive`,
  `tokenVersion`, `lastLoginAt`, `roles`
- Factory: `User.create(propertyId, username, fullName, rawPassword, roles, encoder)`
- Métodos de negócio: `activate()`, `deactivate()`, `changePassword(...)`,
  `registerLogin(clock)` (incrementa `tokenVersion` e grava `lastLoginAt`),
  `revokeSessions()`
- `toString()` **nunca** inclui `passwordHash`

**Regras da senha:** no mínimo 8 **code points** e no máximo 72 bytes em UTF-8 (limite
do BCrypt), em `create` e em `changePassword`. Duas exceções de domínio, nunca o
`IllegalArgumentException` do BCrypt:

- `WeakPasswordException`, código `WEAK_PASSWORD`: menos de 8 code points;
- `PasswordTooLongException`, código `PASSWORD_TOO_LONG`: acima de 72 bytes em UTF-8.

O mínimo conta code points, e não unidades UTF-16: um emoji conta 1. O front precisa
contar do mesmo jeito (ver "Nota para o front").

**Papéis:** `create` exige ao menos um papel. Exceção de domínio
`UserWithoutRolesException`, código `USER_WITHOUT_ROLES`.

**Decisões de autenticação no agregado:** `matches(rawPassword, encoder)`,
`canAuthenticate()` (só usuário ativo) e `isSessionCurrent(tokenVersion)`. O
serviço e o filtro apenas reagem a essas respostas. `registerLogin(Clock)`
recebe o relógio injetado.

**Regras do `username`:** 3 a 30 caracteres, apenas letras minúsculas, dígitos,
ponto e sublinhado. Normalizado para minúsculas na entrada. Exceção de domínio
`InvalidUsernameException`, código `INVALID_USERNAME`.

### `Role`

Enum: `ADMIN`, `FRONT_DESK`, `WAITER`, `KITCHEN`.

### Sessão única via `tokenVersion`

Todo token emitido carrega a versão vigente no momento da emissão. O filtro
compara a versão do token com a do usuário no banco; se divergir, rejeita com
`SESSION_SUPERSEDED`. Antes disso, confere se o usuário ainda pode autenticar:
usuário desativado recebe `USER_INACTIVE`. A ordem importa, porque
`deactivate()` também incrementa `tokenVersion`.

`registerLogin(clock)` e `revokeSessions()` incrementam a versão, então login novo e
logout invalidam tudo o que foi emitido antes — imediatamente, sem tabela de
sessão e sem limpeza de registros expirados.

### Endpoints

```
POST /api/auth/login      público
POST /api/auth/refresh    público
POST /api/auth/logout     autenticado
GET  /api/auth/me         autenticado
```

`login` devolve `accessToken`, `refreshToken`, `expiresIn` (em **segundos**: `900`), e
os dados básicos do usuário com seus papéis. `refresh` devolve `accessToken` e
`expiresIn`, também em segundos. `logout` devolve `204` sem corpo.

### Configuração de segurança

- Stateless, sem sessão de servidor
- CSRF desabilitado (API com token, sem cookie)
- CORS restrito por perfil: liberado em `dev`, lista explícita em `prod`
- Rotas públicas: `/api/auth/login`, `/api/auth/refresh`, `/actuator/health`
- Todo o resto exige autenticação
- Autorização por papel via anotação de método

### Seed no perfil `dev`

Cinco usuários, todos com a mesma senha, vinda de `DEV_SEED_PASSWORD`
(`castel.dev.seed-password`), com valor padrão apenas em `dev`:

| username | Papel | Estado |
|---|---|---|
| `admin` | `ADMIN` | ativo |
| `recepcao` | `FRONT_DESK` | ativo |
| `garcom` | `WAITER` | ativo |
| `cozinha` | `KITCHEN` | ativo |
| `inativo` | `WAITER` | desativado via `deactivate()` |

Roda só com `@Profile("dev")` **e** `castel.dev.seed-enabled=true`. É idempotente:
username que já existe não é tocado. **Não pode existir seed no perfil `prod`.**

### `GET /api/admin/users`

Lista somente leitura, restrita a `ADMIN`. Existe em todos os perfis, e é o
endpoint que prova o 403. Cada item traz `id`, `username`, `fullName`, `email`,
`isActive`, `roles` e `lastLoginAt`, **nunca** o hash de senha. O 403 sai em
RFC 7807 (`application/problem+json`) com `code: ACCESS_DENIED`.

### `http/00-auth.http`

Todos os cenários da matriz abaixo.

---

## Regras de segurança que são critério de aceite

**Resposta idêntica para usuário inexistente, senha errada e usuário inativo.**
Mensagens diferentes permitem descobrir quais contas existem testando uma a uma.
Sempre `401` com código `INVALID_CREDENTIALS`.

**Tempo de resposta também precisa ser parecido.** Se o sistema devolve erro
instantâneo quando o usuário não existe e demora 300 ms quando existe (porque
calculou o BCrypt), a diferença de tempo entrega a informação que a mensagem
escondeu. Execute uma comparação de hash descartável mesmo quando o usuário não
é encontrado.

**Username validado antes de tudo.** A primeira coisa do login é normalizar o username
para minúsculas (`Locale.ROOT`) e aplicar a mesma regra do agregado (`[a-z0-9._]`, 3 a 30
caracteres). Username fora do formato não pertence a nenhum usuário, então é tratado como
**usuário inexistente, imediatamente**: sem consulta ao banco, com o BCrypt descartável dentro
da vaga, e com a mesma resposta `401 INVALID_CREDENTIALS`. Uma variante Unicode como `cozİnha`
nunca chega a `cozinha`, nem pelo banco (cujo `lower()` segue o locale do Postgres) nem pela
chave do limite. A consulta ao banco compara `lower(username)` com o valor já normalizado em
Java, usando o índice `uk_app_user_username`.

**Nenhum lock durante o BCrypt.** O usuário é lido sem lock, numa transação curta que termina
antes da comparação de senha; o BCrypt roda fora de qualquer transação, sem segurar conexão.
Assim, tentativas simultâneas contra uma conta existente rodam em paralelo, exatamente como
contra uma conta inexistente, e o tempo não denuncia a conta nem sob concorrência. Só a senha
correta de usuário ativo abre uma segunda transação curta: lock pessimista por id,
`registerLogin(clock)`, save e emissão dos tokens. Se nesse instante o usuário não existir mais
ou estiver inativo, o login falha com `INVALID_CREDENTIALS`. A senha não é reconferida sob o
lock: uma troca de senha concorrente poderia aceitar a senha antiga por milissegundos, o que foi
aceito.

**Senha nunca aparece em log, resposta, `toString()` ou mensagem de erro.**

**Refresh token não serve como access token, e vice-versa.** Cada um carrega um
claim `type` (`access` ou `refresh`, em minúsculas), verificado no uso com
comparação exata. Só o access token leva `roles`.

**Token no segundo exato do `exp` é aceito.** A expiração é decidida pelo parser do
jjwt, que só considera expirado depois do `exp`; nenhuma checagem própria é feita por
cima dele. Um token **sem** `exp` é `INVALID_TOKEN`: todo token emitido aqui tem `exp`.

**Limite de tentativas** no endpoint de login. Sem bloqueio de conta: travar a
recepcionista no meio do expediente é pior do que o risco que evita.

**Log de auditoria.** Toda falha e todo `429` do login vão para o log com o IP e **sempre** o
username tentado, inclusive de conta inexistente e de formato inválido (sem ele é impossível
investigar *password spraying*). O username é texto do cliente, então vai **entre aspas e
escapado**: `"` e `\`, CR, LF, TAB e demais caracteres de controle, de formatação (bidi,
largura zero) e separadores de linha viram sequências de escape, e o valor é truncado em 64
code points com marcador. Nenhuma senha, hash ou token no log.

Conta **apenas falhas**: senha errada, usuário inexistente e usuário inativo contam
igual. Login bem-sucedido não conta e **não zera** o contador: todos os aparelhos do
hotel saem pelo mesmo NAT.

Duas camadas, ambas em janela deslizante:

| Camada | Chave | Limite padrão | Efeito ao atingir |
|---|---|---|---|
| 1 | Par (IP, username) | 10 falhas em 1 minuto | `429` só para aquele par. O mesmo IP com outro username continua livre |
| 2 | IP | 100 falhas em 1 minuto, somando todos os usernames | `429` para o IP inteiro, qualquer username |

- O username da chave é normalizado como no login (minúsculas). Username inexistente
  conta igual, com resposta idêntica.
- Username em formato inválido não vira chave própria: **todos os inválidos de um IP dividem
  um único par sentinela**, e a camada 2 conta normalmente. Texto arbitrário do cliente nunca
  aumenta o número de chaves em memória.
- A camada 2 existe só contra *password spraying* (uma senha tentada em muitos
  usernames), que a camada 1 não pega. O valor é alto de propósito: troca de turno
  errando senha não chega perto, e spraying chega em segundos.
- A checagem acontece **antes** do BCrypt: par ou IP no limite recebe `429` sem
  calcular hash, mesmo com a senha correta. A falha é contada **depois** do BCrypt.
- Excedeu: `429` RFC 7807 com `code: TOO_MANY_LOGIN_ATTEMPTS`.

**Proteção de CPU.** No máximo 3 BCrypts simultâneos por IP. A 4ª tentativa simultânea
**espera** a vaga; só recebe `429 TOO_MANY_LOGIN_ATTEMPTS` se a espera passar do
timeout (padrão **2 s**). No máximo **30 requisições esperando** vaga por IP: a partir da 34ª
simultânea (3 rodando + 30 esperando), `429` imediato, sem esperar, porque segurar thread do
Tomcat também esgota o servidor. O hash descartável do usuário inexistente também ocupa vaga. A
checagem de limite é refeita depois de obter a vaga. Timeout de vaga e teto de espera **não
contam como falha** em nenhuma camada: não são tentativa de credencial. A vaga é sempre liberada,
e o semáforo do IP só sai da memória quando nenhuma requisição o usa.

**Limitação aceita.** A checagem antes e a contagem depois não são atômicas. Sob
concorrência, uma camada pode passar do limite pelas tentativas que já seguravam vaga
no mesmo instante, ou seja, em até 2 falhas extras por camada (vagas simultâneas menos 1):
no máximo 12 por par e 102 por IP.

**Fronteira da janela.** Uma falha com exatamente 60 s de idade já está **fora** da janela.

**Configuração** em `castel.auth.login-rate-limit.*`:

| Propriedade | Padrão |
|---|---|
| `max-failures-per-username` | `10` |
| `max-failures-per-ip` | `100` |
| `window` | `1m` |
| `max-tracked-pairs` | `10000` |
| `max-tracked-ips` | `10000` |
| `max-concurrent-password-checks-per-ip` | `3` |
| `max-waiting-password-checks-per-ip` | `30` |
| `password-check-slot-timeout` | `2s` |

Memória limitada: contadores em Caffeine com tamanho máximo e expiração. Os semáforos
de BCrypt ficam num mapa que só guarda o IP enquanto há login dele em andamento.

O IP é o `remoteAddr` do Tomcat. Em `prod`, `server.forward-headers-strategy: native`
com `server.tomcat.remoteip.internal-proxies` vindo de `TRUSTED_PROXIES`, de modo que
`X-Forwarded-For` só vale quando vem do proxy reverso. Nunca `framework`.
`TRUSTED_PROXIES` vazio, em branco ou com placeholder não resolvido derruba o boot com
mensagem clara.

**Token de usuário desativado** recebe `401 USER_INACTIVE`, no filtro e no
refresh. O login de usuário inativo continua `401 INVALID_CREDENTIALS`, idêntico
aos outros dois casos.

**Erro não vira 401.** O dispatch `ERROR` é liberado na cadeia de segurança:
JSON malformado dá 400, e rota inexistente com token válido dá 404.

**JSON malformado não vaza senha.** Corpo ilegível nos endpoints do `identity`
responde `400` RFC 7807 com `code: MALFORMED_REQUEST` e `detail` genérico, que
nunca inclui a mensagem do parser. O tratamento no `@RestControllerAdvice` impede
que o `DefaultHandlerExceptionResolver` registre em log o WARN com o token
inválido (`"password":secret` sem aspas).

**Filtro JWT não roda em `/api/auth/login` nem `/api/auth/refresh`.** Um access
token expirado esquecido no header não pode impedir o refresh.

**Boot falha alto, com mensagem clara, quando a configuração é perigosa:**

- segredo do JWT ausente, em branco (só espaços) ou abaixo de 32 bytes em UTF-8 **após `trim()`** (31 espaços e 1 caractere é rejeitado), sem mostrar o valor;
- perfis `dev` e `prod` ativos juntos;
- `TRUSTED_PROXIES` vazio, em branco ou com placeholder não resolvido, no perfil `prod`.

---

## Matriz de aceite (vai para o `.http`)

| Cenário | Esperado |
|---|---|
| Login válido | 200 + `accessToken` + `refreshToken` |
| Senha errada | 401 `INVALID_CREDENTIALS` |
| Usuário inexistente | 401 `INVALID_CREDENTIALS`, **resposta idêntica à anterior** |
| Usuário inativo | 401 `INVALID_CREDENTIALS` |
| `username` em formato inválido (ex.: `cozİnha`) | 401 `INVALID_CREDENTIALS`, **resposta idêntica à de usuário inexistente** |
| `username` com maiúsculas | 200, é o mesmo usuário |
| Rota protegida sem token | 401 |
| Rota protegida com token válido | 200 |
| Rota de `ADMIN` com token de `WAITER` | 403 `ACCESS_DENIED` |
| Token expirado | 401 `TOKEN_EXPIRED` |
| Token malformado | 401 `INVALID_TOKEN` |
| Token assinado com outro segredo | 401 `INVALID_TOKEN` |
| Refresh válido | 200 + novo access token |
| Refresh usando access token | 401 `INVALID_TOKEN` |
| Access token após novo login em outro aparelho | 401 `SESSION_SUPERSEDED` |
| Access token após logout | 401 `SESSION_SUPERSEDED` |
| Access token de usuário desativado | 401 `USER_INACTIVE` (só no teste de integração: nenhum endpoint desativa usuário nesta task) |
| Refresh de usuário desativado | 401 `USER_INACTIVE` (só no teste de integração: nenhum endpoint desativa usuário nesta task) |
| `GET /api/auth/me` autenticado | 200 com papéis, **sem hash de senha** |
| `GET /api/admin/users` com token de `ADMIN` | 200, lista sem hash de senha |
| Refresh com access token expirado no header | 200 |
| JSON malformado no login | 400 `MALFORMED_REQUEST` |
| Rota inexistente com token válido | 404 |
| Logout com token válido | 204 sem corpo |
| Token no segundo exato do `exp` | 200 (só no teste de integração: depende de relógio controlável) |
| 11ª tentativa do mesmo par (IP, username) após 10 falhas no mesmo minuto, mesmo com senha correta | 429 `TOO_MANY_LOGIN_ATTEMPTS` (em `01-auth-rate-limit.http`) |
| Outro username do mesmo IP, com o par anterior bloqueado | 200 (em `01-auth-rate-limit.http`) |
| Qualquer username do IP após 100 falhas no mesmo minuto, em usernames distintos | 429 `TOO_MANY_LOGIN_ATTEMPTS` (em `02-auth-rate-limit-ip.http`) |

---

## Códigos de erro

Todo erro sai em RFC 7807 (`application/problem+json`) com o campo `code`.

| Código | HTTP | Quando ocorre |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Login com usuário inexistente (inclusive username em formato inválido), senha errada ou usuário inativo. Resposta idêntica em todos os casos |
| `USER_INACTIVE` | 401 | Access token ou refresh token de usuário desativado |
| `TOKEN_EXPIRED` | 401 | Token bem formado e assinado, com o instante atual posterior ao `exp`. No segundo exato do `exp` o token ainda é aceito |
| `INVALID_TOKEN` | 401 | Token malformado, com assinatura inválida, com `type` diferente do esperado (refresh usado como access e vice-versa) ou com claims ausentes, `exp` incluído |
| `SESSION_SUPERSEDED` | 401 | Token emitido antes de um novo login ou de um logout, ou de usuário que não existe mais |
| `AUTHENTICATION_REQUIRED` | 401 | Rota protegida sem token |
| `ACCESS_DENIED` | 403 | Autenticado, mas sem o papel exigido pela rota |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | 10 falhas em 1 minuto para o par (IP, username); 100 falhas em 1 minuto para o IP; timeout de 2 s na espera por vaga de BCrypt do IP; ou mais de 30 requisições esperando vaga no IP. Vale mesmo com senha correta |
| `MALFORMED_REQUEST` | 400 | Corpo da requisição ausente ou ilegível (JSON malformado) nos endpoints do `identity` |
| `INVALID_USERNAME` | 422 | `username` fora das regras. Nenhum endpoint dispara hoje; mapeado para o contrato ficar completo |
| `WEAK_PASSWORD` | 422 | Senha com menos de 8 code points. Nenhum endpoint dispara hoje |
| `PASSWORD_TOO_LONG` | 422 | Senha acima de 72 bytes em UTF-8. Nenhum endpoint dispara hoje |
| `USER_WITHOUT_ROLES` | 422 | Usuário criado sem papel. Nenhum endpoint dispara hoje |

---

## Fora do escopo

Recuperação de senha, cadastro público, e-mail transacional, 2FA, telas de
front.

Auditoria de `app_user` (`created_by`, `updated_by`) fica para a task 0.5b.

---

## Nota para o front (registrar, não implementar)

Com access token de 15 minutos, o cliente precisa **renovar em background**,
antes de expirar. Se o refresh só acontecer depois de uma requisição falhar, o
garçom vê erro a cada 15 minutos no meio do atendimento.

`expiresIn` vem em **segundos** (`900`).

O mínimo de 8 da senha é contado em **code points**. No front, use
`[...senha].length`, nunca `senha.length`: `length` conta unidades UTF-16, e uma senha
com emoji validaria diferente nos dois lados.

---

## Testes — orientação ao `unit-tester`

Cubra a matriz acima e as invariantes do agregado:

- Senha nunca persistida em texto puro
- `registerLogin(clock)` incrementa `tokenVersion` e grava `lastLoginAt` do relógio
- Senha com menos de 8 code points é rejeitada com `WEAK_PASSWORD`, e acima de 72
  bytes em UTF-8 com `PASSWORD_TOO_LONG`, em `create` e `changePassword`
- Usuário sem papel é rejeitado em `create`
- `revokeSessions()` incrementa `tokenVersion`
- Usuário inativo não autentica mesmo com senha correta
- `username` fora das regras é rejeitado
- `username` normalizado para minúsculas
- `toString()` não contém o hash

**Não faça fuzzing de entrada.** O `shared-kernel` já protege os value objects,
e a camada web tem seu próprio limite de tamanho. Aqui o que importa é
comportamento de segurança, não entrada patológica.

Os testes de token (expirado, malformado, assinado com outro segredo) precisam
de um relógio controlável — injete `Clock`, não use `Instant.now()` direto.

---

## Critérios de aceite

- `./mvnw clean install` passa, ArchUnit incluído
- Migration V2 aplica sobre a V1 sem perda de dado
- Todos os cenários da matriz passam no `00-auth.http`, exceto os marcados como "só no
  teste de integração". Os de limite passam no `01-auth-rate-limit.http` e no
  `02-auth-rate-limit-ip.http`, cada um rodado isolado
- Segredo do JWT vem de variável de ambiente
- Nenhum seed no perfil `prod`
- Nenhuma senha em log ou resposta

---

## Commits

Três commits, conforme a decisão #53 de `docs/decisions/task-0.4.md`: separar as rodadas
entrelaçadas em mais commits geraria commits que não compilam. `.claude/agent-memory/**` fica
fora dos commits.

```
docs: add decision log process and reconstruct task 0.4 history

Task: 0.4
Agent: java-dev

feat(identity): add jwt authentication with session invalidation

Task: 0.4
Agent: java-dev

test(identity): cover authentication, rate limiting and log safety

Task: 0.4
Agent: unit-tester
```
