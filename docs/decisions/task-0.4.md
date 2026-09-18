# Decisões — Task 0.4 identity e autenticação

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Breno confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.
>
> Exceção única: as rodadas 0 a 3 foram **reconstruídas** na rodada 4, quando
> este processo foi instalado. A partir da rodada 4 o registro é incremental.

Rodadas: **0** spec e briefing · **1** primeira implementação e revisão ·
**2** respostas à revisão 2 · **3** respostas à revisão 3 · **4** respostas à
revisão 4 e instalação deste registro · **5** respostas à revisão final
(última rodada de código da 0.4) · **6** fechamento dos pontos em aberto, já com a
task mergeada.

---

## Estado

| | |
|---|---|
| Branch | `task/0.4-identity-auth` |
| Rodada atual | 6 — só fechamento dos pontos em aberto #14 a #17 (#76 a #79), sem mudança de código |
| Build | passa (fim da rodada 5, ArchUnit incluído) |
| Testes | 884 (shared-kernel 193 · identity 552 · app 139, ArchUnit 30 incluído), todos passando no fim da rodada 5. Após #75, o teste dos 20 logins simultâneos virou `shouldAnswerConcurrentCorrectLoginsWithinSlotCapacityOfSameIpWithOk`. Ele calcula quantos logins simultâneos cabem pelos parâmetros: `vagas × floor(timeout / BCrypt mais lento de 5 medições × 1.5)`, com mínimo igual a `vagas` e máximo igual a `vagas + espera máxima`. Nesta máquina deu 12 a 15 e passou em 3 rodadas isoladas e no build. Antes disso, a correção da senha vazia trouxe: dublê `ObservingPasswordEncoder`, que conta os hashes BCrypt de fato computados; `EmptyPasswordLoginTest`, com senha nula ou vazia em 4 situações de conta e um caso de controle; e 5 testes HTTP de senha vazia ou ausente (antes: 804, identity 477 · app 134) |

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Access token de 15 min, refresh token de 7 dias | implementado |
| 2 | 0 | Login por `username` curto + senha, não e-mail | implementado |
| 3 | 0 | Sessão única via `tokenVersion`: novo login e logout invalidam tudo o que foi emitido antes | implementado |
| 4 | 0 | BCrypt custo 12 | implementado |
| 5 | 0 | jjwt 0.12.x | **revertida pela #26** |
| 6 | 0 | HS256, segredo em variável de ambiente, nunca no `application.yml` | implementado |
| 7 | 0 | Claims: `sub`, `roles`, `tokenVersion`, `type`, `iat`, `exp`. Nada de nome ou e-mail no token | implementado |
| 8 | 0 | Limite de 10 tentativas por minuto por IP, em memória, resposta 429 | **revertida pela #13** |
| 9 | 0 | Sem bloqueio de conta | implementado |
| 10 | 0 | `Clock` como bean do Spring, fixo nos testes; nunca `Instant.now()` | implementado |
| 11 | 0 | Logout apenas incrementa `tokenVersion`, sem corpo e sem lista negra | implementado |
| 12 | 0 | Migration V2 (`username`, `token_version`) desloca a numeração reservada em um | implementado |
| 13 | 2 | Limite: 10 **falhas** por minuto por IP, configurável por propriedade; login bem-sucedido não conta; sem limite por usuário | **revertida pela #56** |
| 14 | 2 | Estrutura do limite com evicção (Caffeine com tamanho máximo), nunca mapa que só cresce | implementado |
| 15 | 2 | `forward-headers-strategy: native` com `internal-proxies`; nunca `framework` | implementado |
| 16 | 2 | `DispatcherType.ERROR` liberado no filter chain: erro não vira 401 | implementado |
| 17 | 2 | Nenhum log de bind do Hibernate no perfil dev (hash de senha em log) | implementado |
| 18 | 2 | Filtro JWT não roda em `/api/auth/login` nem `/api/auth/refresh` | implementado |
| 19 | 2 | Regras no agregado: `User.matches`, `canAuthenticate`, `isSessionCurrent`. Nenhum `if` de regra em `@Service` | implementado |
| 20 | 2 | `User.registerLogin(Clock)` | implementado |
| 21 | 2 | `UserDetailsServiceAutoConfiguration` excluída (sem senha gerada no log) | implementado |
| 22 | 2 | `UserRepository` compara `lower(username)` | implementado |
| 23 | 2 | `GET /api/admin/users` como endpoint **real** de ADMIN, somente leitura, não endpoint de teste | implementado |
| 24 | 2 | Seed do dev com 5 usuários: `admin` (ADMIN), `recepcao` (FRONT_DESK), `garcom` (WAITER), `cozinha` (KITCHEN), `inativo` (WAITER, desativado) | implementado |
| 25 | 2 | `identity/api` com `CurrentUserProvider`, `UserId` e a view do usuário autenticado; `app` não importa `identity.infra`/`application`/`domain` | implementado |
| 26 | 2 | jjwt 0.13.0, por ser release estável; versão no pom raiz | implementado |
| 27 | 2 | `User.create` rejeita senha com menos de 8 e usuário sem nenhum papel | implementado |
| 28 | 2 | V2 igual à spec, com comentário: pressupõe banco sem dados de produção; colisão ou nome acima de 30 falham alto de propósito | implementado |
| 29 | 2 | Filtro checa usuário ativo; 401 do filtro em `application/problem+json` | implementado |
| 30 | 3 | Corpo JSON malformado → 400 `MALFORMED_REQUEST`, sem senha no log | implementado |
| 31 | 3 | Limite com reserva atômica antes do BCrypt; login correto num IP no limite recebe 429 durante rajada | **revertida pela #50** |
| 32 | 3 | `http/01-auth-rate-limit.http` com o 429 na 11ª tentativa usando a senha correta, contando por IP | **revertida pela #58** |
| 33 | 3 | Token de usuário desativado → `ACCOUNT_INACTIVE` (login de inativo continua `INVALID_CREDENTIALS`) | **revertida pela #41** |
| 34 | 3 | Senha fora da regra → `INVALID_PASSWORD`, de 8 caracteres a 72 bytes | **revertida pela #42** |
| 35 | 3 | Logout devolve 200 | **revertida pela #54** |
| 36 | 3 | Token no segundo exato do `exp` é REJEITADO | **revertida pela #44** |
| 37 | 3 | Claim `type` em minúsculas (`access`/`refresh`), comparação exata | implementado |
| 38 | 3 | Refresh token **não** leva `roles`: são relidos do banco no refresh, então remover papel vale em até 15 min em vez de 7 dias | implementado |
| 39 | 3 | Segredo UTF-8, mínimo de 32 bytes | **revertida pela #67** |
| 40 | 3 | Testes: guarda de log com `OutputCaptureExtension`; reset explícito do limiter e do relógio (sem avançar 1h por teste); testes dedicados de janela 59s/61s; `MutableClock` no test-jar do shared-kernel | implementado |
| 41 | 4 | `ACCOUNT_INACTIVE` renomeado para `USER_INACTIVE` (glossário usa `User`) | implementado |
| 42 | 4 | Senha: `WEAK_PASSWORD` (< 8 code points) e `PASSWORD_TOO_LONG` (> 72 bytes UTF-8), códigos separados | implementado |
| 43 | 4 | `USER_WITHOUT_ROLES` entra na lista de códigos | implementado |
| 44 | 4 | Token ACEITO no segundo exato do `exp` (comportamento do jjwt; a RFC permite tolerância de relógio e sobrescrever o parser criaria código customizado em caminho crítico). Ajustar spec, código e testes | implementado |
| 45 | 4 | Validação de domínio (`INVALID_USERNAME`, `WEAK_PASSWORD`, `PASSWORD_TOO_LONG`, `USER_WITHOUT_ROLES`) → **422** | implementado |
| 46 | 4 | Segredo JWT não pode ser em branco | implementado (endurecida pela #67) |
| 47 | 4 | Mínimo de 8 contado em **code points** | implementado |
| 48 | 4 | Exceção do ArchUnit para `java.util.Date` é **nominal**: só `JwtTokenIssuer` | implementado |
| 49 | 4 | Canário dos testes de log sem hífen (`Canary_...`) e verificação de que `Unrecognized token` não aparece | implementado |
| 50 | 4 | Limite atômico de BCrypts simultâneos por IP (máximo 3) com contador de falhas por IP incrementado depois do BCrypt, para login correto nunca receber 429 por ataque do mesmo IP | **revertida pela #56 e #57** (incoerente: proteger e não bloquear o mesmo IP não cabem juntos) |
| 51 | 4 | Na matriz da spec, os dois cenários de usuário desativado ficam só no teste de integração (nenhum endpoint desativa usuário nesta task) | implementado |
| 52 | 4 | Sugestões pequenas: `@Qualifier` no cache do limiter; relógio lido dentro do `compute`; teste de concorrência no nível do serviço; test-jar do shared-kernel limitado a `support/` | implementado |
| 53 | 4 | Commits: três (registro de decisões · implementação · testes), não os quatro da spec, porque separar as rodadas entrelaçadas gera commits que não compilam. Trailers `Task:` e `Agent:` em linhas separadas; `.claude/agent-memory/**` fora dos commits | implementado |
| 54 | 4 | Logout devolve **204**, sem corpo | implementado |
| 55 | 4 | Campos atuais de `/api/admin/users` aprovados: `id, username, fullName, email, isActive, roles, lastLoginAt` | implementado |
| 56 | 4 | Limite de falhas por **par (IP, username)**: 10 falhas em 1 minuto para o par → 429 para aquele par; o mesmo IP com outro username continua livre. Username normalizado como no login; username inexistente conta igual (resposta idêntica) | implementado |
| 57 | 4 | Limite de BCrypts simultâneos por IP (máximo 3) como **proteção de CPU**, não de segurança: a 4ª tentativa simultânea **espera** a vaga (semáforo com timeout curto); só se o timeout estourar recebe 429 | **revertida pela #68** (acrescenta teto de espera) |
| 58 | 4 | `http/01-auth-rate-limit.http`: 10 falhas no mesmo par (IP, username) → 429 para o par; e prova de que outro username do mesmo IP continua logando | implementado |
| 59 | 4 | Segunda camada do limite, acima da #56: **100 falhas por minuto por IP** (somando todos os usernames) → 429 para o IP inteiro. Existe só contra password spraying, que a camada 1 não pega; o valor é alto de propósito (troca de turno errando senha não chega perto, spraying chega em segundos). Os dois limites (10 por par, 100 por IP) configuráveis por propriedade | implementado |
| 60 | 4 | `http/01-auth-rate-limit.http` ganha um cenário da camada 2, com aviso de que bloqueia o IP inteiro por 1 minuto | **revertida pela #63** |
| 61 | 5 | Login: leitura do usuário **sem lock**, BCrypt **fora da transação**, lock pessimista só no sucesso para `registerLogin`. Efeito colateral aceito: troca de senha concorrente poderia aceitar a senha antiga por milissegundos — irrelevante (não há endpoint de troca de senha; quando houver, as duas ações partem do mesmo usuário). A alternativa derrubaria login legítimo simultâneo | implementado |
| 62 | 5 | Username validado **antes de tudo** no login: normalizado para minúsculas e, se tiver caractere fora de `[a-z0-9._]` ou tamanho fora de 3–30, é tratado como **usuário inexistente imediatamente**, **sem consulta ao banco** e **com o BCrypt descartável** (tempo idêntico). Corrige na raiz a variante Unicode (`cozİnha`) e elimina o corte da chave em 31 caracteres | implementado |
| 63 | 5 | Cenário da camada 2 em arquivo separado, `http/02-auth-rate-limit-ip.http` | implementado |
| 64 | 5 | Aprovadas as decisões técnicas da rodada 4: transação aberta só depois da vaga; propriedades `max-tracked-pairs`, `max-concurrent-password-checks-per-ip`, `password-check-slot-timeout`. Ajustes: timeout de vaga cai de 5 s para **2 s** (três BCrypts de ~300 ms; esperar mais significa que algo está errado); corte do username em 31 caracteres **removido** (#62) | implementado |
| 65 | 5 | Timeout de vaga **não** conta como falha: não é tentativa de credencial | implementado |
| 66 | 5 | Falha com exatamente 60 s fica **fora** da janela, com teste de fronteira | implementado |
| 67 | 5 | Segredo JWT exige **32 bytes UTF-8 após trim**: 31 espaços + 1 caractere é rejeitado. Validar, não só documentar | implementado |
| 68 | 5 | Semáforo de BCrypts por IP (máximo 3 rodando, timeout 2 s) com **teto de 30 requisições esperando por IP**; acima disso, 429 imediato. Segurar thread do Tomcat é vetor real de esgotamento | implementado |
| 69 | 5 | Log de auditoria grava **sempre** o username tentado, inclusive de conta inexistente (log interno com acesso controlado; sem ele é impossível investigar spraying, que a camada 2 existe para pegar). O 429 por timeout de vaga e por teto de espera também é logado | implementado |
| 70 | 5 | Testes do `app` importando `identity.domain`/`identity.infra` para criar usuários e forjar tokens: aceitável em código de teste | implementado |
| 71 | 5 | IPv6 chaveado pelo endereço completo: registrado como limitação, **a resolver antes do deploy em produção** (chavear por /64) | implementado (registro) |
| 72 | 5 | Sugestões pequenas da revisão final aplicadas: contrato do registro alinhado à spec; seção "Commits" da spec alinhada à #53; javadoc de `TooManyLoginAttemptsException`; testes de fronteira de 60 s e de interrupção do semáforo; `instance` ausente nos corpos de erro do filtro e do `SecurityConfig` movido para a 0.5b | implementado |
| 73 | 5 | Esta é a **última rodada** da 0.4. Corrigidos #61 e #62, a task mergeia. Achado novo da revisão final que não seja perda de dado ou falha de autenticação vira item registrado neste arquivo, não bloqueio | implementado (regra de processo) |
| 74 | 5 | A partir da 0.5b: **teto de três rodadas de review por task**. Na terceira, o que não for bloqueio crítico é registrado e mergeado. Autenticação justificou tratamento especial; tasks comuns não | implementado (regra de processo) |
| 75 | 5 | Timeout de vaga fica em **2 s** (#64). A prova "20 logins corretos simultâneos do mesmo IP passam" (rodada 4) não é requisito de negócio: o requisito é o funcionário não ver erro no uso normal. O teste passa a provar o garantido **derivado dos parâmetros**, `vagas × floor(timeout / duração do BCrypt)`, sem número fixo ancorado em tempo de parede; se ficar instável, custo de BCrypt reduzido no perfil de teste. Implementação: duração medida com o `PasswordEncoder` do contexto (pior de 5 amostras × 1,5), piso em `vagas` e teto técnico em `vagas + maxWaitingPasswordChecksPerIp` (acima dele a rejeição é imediata pelo teto de espera, #68, antes de qualquer timeout). Estável em 4 execuções (garantido 12–15) | implementado |
| 76 | 6 | Ponto em aberto #14 fechado: resíduo de ~1,4 ms do `select` EAGER de papéis **aceito como limitação**. Abaixo do que se mede pela rede de forma confiável; `join fetch` não entra agora | implementado (registro) |
| 77 | 6 | Ponto em aberto #15 fechado: sinal Kelvin (U+212A) como alias de `k` **aceito**. É alias, não bypass — cai no mesmo par (IP, username) do limitador e continua exigindo a senha correta | implementado (registro) |
| 78 | 6 | Ponto em aberto #16 fechado: limite de tamanho de corpo entra na **0.5b**, global para toda a API junto com o handler RFC 7807, não como remendo só no login | pendente (0.5b) |
| 79 | 6 | Ponto em aberto #17 fechado: a regra "nunca `@Autowired` em campo" vale **só para código de produção**. `@Autowired` em campo é idiomático em teste Spring e as regras estruturais já rodam apenas contra `main`. A exceção passa a estar escrita no `CLAUDE.md` (na 0.5b), para não virar dúvida a cada review | pendente (0.5b) |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Lista viva. Item aprovado pelo Breno **entra aqui** e só sai por decisão
explícita dele.

### Entregue
- [x] Migration V2 e renumeração em `docs/MIGRATIONS.md` e na seção 3 do schema
- [x] Agregado `User`, `Role`, `InvalidUsernameException`
- [x] Endpoints `login`, `refresh`, `logout`, `me`
- [x] Filter chain stateless, CSRF desabilitado, CORS por perfil
- [x] `GET /api/admin/users` (ADMIN)
- [x] Campos de `/api/admin/users` (aprovados como estão, #55)
- [x] Seed do dev com 5 usuários
- [x] `identity/api`
- [x] Bancada `.http`: `http-client.env.json`, `.example` privado, `.gitignore`, `00-auth.http`, `01-auth-rate-limit.http`, `02-auth-rate-limit-ip.http`

### Aprovado na rodada 4
- [x] Portas `application → infra`: `UserRepository` no `domain`, `LoginAttemptRateLimiter` atrás de porta, e regra ArchUnit proibindo `application → infra` no mesmo módulo (com prova de violação)
- [x] `ClockConfig`: bean `Clock` sai do `SecurityConfig`
- [x] Validação explícita de `TRUSTED_PROXIES` em prod, com mensagem clara
- [x] Hash descartável gerado pelo próprio encoder, não com custo fixo à parte
- [x] Guarda contra perfis `dev` e `prod` ativos juntos
- [x] Decisões #41, #42, #44, #45, #46, #49, #51, #52, #54, #56, #57, #58, #59, #63

### Aprovado na rodada 5 (última)
- [x] #61 login sem lock durante o BCrypt
- [x] #62 validação de formato do username antes de tudo
- [x] #64 timeout de vaga 2 s, sem corte em 31 caracteres
- [x] #65 timeout de vaga não conta como falha
- [x] #66 fronteira de 60 s
- [x] #67 segredo com 32 bytes após trim
- [x] #68 teto de 30 esperando por IP
- [x] #69 log com username tentado e 429 por vaga
- [x] #72 sugestões pequenas
- [x] Commits conforme #53

### Movido para outra task

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Corpo do 404 (e demais erros fora do identity) em RFC 7807 | 0.5b | Depende do handler global de erro, que nasce lá | Breno, rodada 4 |
| Auditoria de `app_user` (`updated_at`/`updated_by`) | 0.5b | Infraestrutura de auditoria é da 0.5b; `last_login_at` já registra o que importa | Breno, rodada 2 (confirmado na 4) |
| Campo `instance` ausente nos corpos de erro escritos pelo filtro JWT e pelo `SecurityConfig` | 0.5b | Mesma causa do 404: formato RFC 7807 unificado nasce com o handler global | Breno, rodada 5 |
| Limite de tamanho do corpo da requisição (login aceita 2 MB) | 0.5b | Limite global nasce com o handler de erro; remendo só no login deixaria os próximos endpoints sem proteção (#78) | Breno, rodada 6 |

---

## Contrato com o front

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Login com senha errada, usuário inexistente (inclusive username em formato inválido) ou inativo — resposta idêntica |
| `USER_INACTIVE` | 401 | Access ou refresh token de usuário desativado |
| `TOKEN_EXPIRED` | 401 | Token expirado |
| `INVALID_TOKEN` | 401 | Malformado, assinatura errada, tipo trocado, claims ausentes (`exp` incluído) |
| `SESSION_SUPERSEDED` | 401 | `tokenVersion` antiga (novo login, logout) ou usuário que não existe mais |
| `AUTHENTICATION_REQUIRED` | 401 | Rota protegida sem token |
| `ACCESS_DENIED` | 403 | Papel insuficiente |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | 10 falhas em 1 minuto para o par (IP, username); 100 falhas em 1 minuto para o IP; timeout de 2 s na espera por vaga de BCrypt; ou mais de 30 requisições esperando vaga no IP |
| `MALFORMED_REQUEST` | 400 | Corpo ausente ou JSON inválido |
| `INVALID_USERNAME` | 422 | Username fora da regra (criação de usuário) |
| `WEAK_PASSWORD` | 422 | Senha com menos de 8 code points |
| `PASSWORD_TOO_LONG` | 422 | Senha acima de 72 bytes UTF-8 |
| `USER_WITHOUT_ROLES` | 422 | Usuário sem nenhum papel |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `expiresIn` | Segundos (`900`) |
| Mínimo da senha | 8 **code points**. O front usa `[...senha].length`, nunca `senha.length`, senão senha com emoji valida diferente nos dois lados |
| `exp` | Token ACEITO no segundo exato do `exp` |
| Claim `type` | `access` / `refresh`, minúsculas |
| Refresh token | Sem `roles` |
| Renovação | O front renova o access token em background antes de expirar |
| Logout | **204**, sem corpo |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| `maxTrackedIps` sem teste | Evicção assíncrona é comportamento do Caffeine, não do nosso código | — |
| Tempo de resposta sem teste de tempo de parede | Seria instável e acabaria desligado; o teste prova que o BCrypt é chamado, que é o proxy correto | — |
| Até 2 falhas extras por camada sob concorrência (máximo 12 por par e 102 por IP) | A contagem não é atômica; vazar 2 a mais sob ataque não muda a ordem de grandeza da proteção, e contar atomicamente bloquearia login correto | O semáforo de 3 vagas por IP (#68) limita o excedente |
| Travar de propósito o login de um usuário específico | Limitado a 1 minuto e a um par (IP, username), muito menor que travar o hotel inteiro | — |
| Testes do `app` importam `identity.domain`/`identity.infra` | Só em código de teste, para criar usuários e forjar tokens; ArchUnit ignora testes (#70) | — |
| Rajada de logins do mesmo IP acima do que 3 vagas × 2 s comportam recebe 429 por timeout de vaga (#75) | 20 aparelhos logando no mesmo segundo não é uso normal num hotel com quatro perfis; aumentar o timeout reabriria o esgotamento de threads. Não conta como falha de credencial, e o usuário tenta de novo | — |
| IPv6 chaveado pelo endereço completo: um /64 contorna as duas camadas e o semáforo | Hoje a rede e o proxy do hotel são IPv4 | **Resolver antes do deploy em produção**: chavear IPv6 por /64 (#71) |
| Resíduo de ~1,4 ms no login de conta existente, do `select` EAGER de papéis | Muito abaixo do que um atacante mede de forma confiável pela rede; a diferença grande (BCrypt) já foi eliminada (#76) | `join fetch u.roles` se algum dia virar medida confiável |
| `Kelvin.test` (U+212A) autentica como `kelvin.test` sem consumir tentativa extra | É alias, não bypass: mesmo par no limitador e senha correta ainda exigida. U+212A é o único code point que normaliza para dentro de `[a-z0-9._]` (#77) | Rejeitar não-ASCII antes de normalizar, se aparecer motivo |

---

## Achados da revisão final (registrados conforme #73)

**Bloqueio** — cabe na regra da #73 (falha de autenticação), portanto corrigido antes do merge:

| Achado | Onde | Tratamento |
|---|---|---|
| Senha vazia ou ausente não executa BCrypt (Spring Security 7 responde `matches("")` com `false` sem hash): sobra só o tempo do banco e uma consulta a mais em conta existente, separando contas com ~95% de acerto em 10 amostras | `AuthenticationService` | Corrigido na rodada 5. `AuthenticationService.login` troca a senha nula ou vazia por um valor aleatório gerado no construtor, então todo caminho (usuário existente ou inativo, inexistente, username inválido) gasta um BCrypt completo e falha com `INVALID_CREDENTIALS` contando no limite. Cenários 2b e 2c no `00-auth.http` |

**Itens registrados, não bloqueiam o merge:**

| # | Item | Onde | Sugestão do revisor |
|---|---|---|---|
| R1 | Limitação "tempo de resposta sem teste de tempo de parede" desatualizada: existe teste HTTP de tempo relativo (2×), que só detecta serialização, não diferenças de ms | este arquivo; `AuthenticationHttpIntegrationTest` | Reescrever a limitação |
| R2 | Conta existente faz um select a mais (papéis EAGER), ~1,4 ms; hoje escondido na variação do BCrypt | `User`, `SpringDataUserRepository` | `join fetch u.roles` ou aceitar como limitação — ponto em aberto #14 |
| R3 | Username inválido não é "tempo idêntico" literal: sem consulta ao banco fica alguns ms mais rápido; revela só o formato, que é público | spec, #62 | Ajustar redação |
| R4 | Interrupção na espera por vaga também não conta como falha (código e testes), mas #65 cita só timeout e teto | #65, spec | Estender a redação |
| R5 | Notação de escape do log (`\uXXXX` e `\u{X}`) não documentada | `AuditLogText` | Documentar na spec |
| R6 | Truncamento em 64 code points da entrada, antes do escape (linha escapada pode passar de 64) | `AuditLogText` | Documentar |
| R7 | Marcador de truncamento conta chars UTF-16, não code points | `AuditLogText` | Usar `codePointCount` |
| R8 | `quote(null)` → `<null>`; `normalizedUsername(null)` → `""`; não especificado | `AuditLogText`, `User` | Documentar |
| R9 | `isWellFormedUsername` aceita valor cru e normaliza internamente | `User` | Documentar |
| R10 | Sinal Kelvin (U+212A) é o único code point que normaliza para `[a-z0-9._]`: `Kelvin.test` autentica como `kelvin.test`, no **mesmo par** (sem tentativa extra) | `User` | Aceitar — ponto em aberto #15 |
| R11 | Log grava o username cru (não normalizado), conforme #69 | `AuthenticationService` | Documentar na spec |
| R12 | IP logado sem escape; vem de `getRemoteAddr()`, mas o `RemoteIpValve` não valida o formato repassado por proxy confiável | `AuthenticationService`, `AuthController` | Passar o IP também por `AuditLogText.quote` |
| R13 | Corpo de login sem limite de tamanho (2 MB aceito e processado; log trunca) | `LoginRequest` | Limite de corpo — ponto em aberto #16 |
| R15 | Hash armazenado malformado faz `BCryptPasswordEncoder.matches` retornar `false` só com regex, sem calcular hash. Não é alcançável de fora (`password_hash NOT NULL`, todo hash vem de `encoder.encode`); só com edição manual no banco | `User.matches` | Aceitar, ou comparar contra o hash descartável quando o armazenado não tiver formato BCrypt |
| R15b | Complemento do R15, também só com edição manual no banco: hash vazio (`''`, aceito pelo `NOT NULL`) retorna `false` sem hash; hash com rounds fora de 04–31 lança `IllegalArgumentException` (provável 500, não 401) | `User.matches`, `V1__baseline.sql` | Se um dia corrigir: `CHECK` de formato BCrypt em migration nova |
| R16 | Teste HTTP de tempo relativo com senha vazia passaria também no código antigo (os dois caminhos levavam poucos ms); só detecta fila de lock. A prova estrutural no serviço (`hashesComputed().isOne()`) cobre o bug | `AuthenticationHttpIntegrationTest` | Piso absoluto derivado de um BCrypt medido |
| R17 | Nenhum teste garante que o log de senha vazia é igual ao de senha errada e não contém o valor substituto | `LoginAuditLogTest` | Teste parametrizado `null`/`""` comparando a linha logada |
| R18 | Dublê `ObservingPasswordEncoder` conta o hash antes de `checkpw`; hash com rounds fora de 04–31 seria contado sem computar (nenhum teste chega lá: todos os hashes vêm de `encode`) | `ObservingPasswordEncoder` | Contar só após `super.matchesNonNull` sem exceção |
| R19 | Valor substituto da senha vazia é fixo por instância (`SecureRandom`, 32 bytes, nunca logado); não é segredo de autenticação | `AuthenticationService` | Aceitar, ou gerar por tentativa |
| R14 | `@Autowired` em campo em código de teste; `CLAUDE.md` não abre exceção e ArchUnit ignora testes | `AuthMigrationTest`, `AuthenticationHttpIntegrationTest` | Decidir se a regra cobre teste — ponto em aberto #17 |

---

## Pontos em aberto

Nenhum. Os pontos 14 a 17 foram fechados na rodada 6 pelas decisões #76 a #79.
Os itens #78 e #79 saem como escopo da 0.5b, registrados em
`docs/decisions/task-0.5b.md`.
