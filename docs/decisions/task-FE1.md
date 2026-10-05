# Decisões — Task FE1 Front-end: tela de login

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/FE1-login` |
| Rodada atual | 1 |
| Build | passa (`npm run build` = `tsc --noEmit` + bundle) |
| Testes | 0 — verificação por percurso real no Chrome contra a API no perfil `dev` |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | As **duas** direções do zip (`A · Iluminura` e `B · Portal`) viram código, alternáveis por um seletor na própria tela — Ruan quer decidir vendo rodar | implementado |
| 2 | 1 | O front nasce em `frontend/`, Vite + React 18 + TypeScript. Fora do Maven: o `pom.xml` não ganha módulo nem plugin de build de front nesta task | implementado |
| 3 | 1 | A tela fala com a API real (`/api/auth/login`), não com o mock do protótipo. Vite faz proxy de `/api` para `localhost:8080` | implementado |
| 4 | 1 | Perfil → área de destino é mapa **do front** (`ADMIN` alcança as três áreas). A API só entrega `roles` | implementado |
| 5 | 1 | Token fica em `sessionStorage`, não em `localStorage`: o tablet do balcão é aparelho compartilhado e fechar a aba tem de encerrar o acesso | implementado |
| 6 | 1 | A moldura escolhida e o último destino ficam em `localStorage` (preferência do aparelho, não credencial) | implementado |
| 7 | 1 | O motivo da volta ao login chega por `recordSessionEnd(code)` em `sessionStorage`, escrito por quem tratar o 401 do refresh; `?sessionEnd=CODE` faz o mesmo e é como o estado se vê sem app shell | implementado |
| 8 | 1 | O subtítulo só promete "seu usuário já está preenchido" quando há usuário lembrado — aparelho novo não tem o que lembrar | implementado |

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

- [x] Projeto `frontend/` (Vite + React 18 + TS) com proxy de `/api`
- [x] Design system `classical` como CSS, com as duas molduras em tokens
- [x] Moldura A · Iluminura — pergaminho, capitular, painel vinho em tela larga
- [x] Moldura B · Portal — vinho lacre, escudo, arco românico
- [x] Seletor de moldura
- [x] Formulário: usuário, senha com mostrar/ocultar, alvos ≥ 48 px, sem autocorreção
- [x] Mensagens por código: `INVALID_CREDENTIALS`, `TOO_MANY_LOGIN_ATTEMPTS`, `MALFORMED_REQUEST`, falha de rede
- [x] Volta ao login com motivo: `TOKEN_EXPIRED`, `INVALID_TOKEN`, `USER_INACTIVE`, `SESSION_SUPERSEDED`, usuário já preenchido
- [x] Escolha de destino quando o perfil alcança mais de uma área, com marca de "última vez"
- [x] Confirmação de saída falando em "todo aparelho" + `POST /api/auth/logout`
- [x] Movimento quase nulo: mensagem em 360 ms, respeitando `prefers-reduced-motion`

### Movido para outra task

Só com aprovação explícita, e dizendo para qual task e por quê.

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Telas de salão, comanda, cozinha e caixa | — | Fora do escopo: esta task entrega a porta, não as salas. A tela de destino é um marcador | — |

---

## Contrato com o front

Tudo o que o front vai consumir. Depois do merge isso vira contrato e mudar
custa caro.

**Códigos de erro**

| Código | HTTP | Quando |
|---|---|---|
| `INVALID_CREDENTIALS` | 401 | Usuário ou senha não conferem; usuário inexistente |
| `TOO_MANY_LOGIN_ATTEMPTS` | 429 | 10 falhas por minuto no par (IP, username), ou 100 por IP |
| `MALFORMED_REQUEST` | 400 | Corpo inválido |
| `TOKEN_EXPIRED` | 401 | Access token vencido |
| `INVALID_TOKEN` | 401 | Token ou refresh token inválido |
| `USER_INACTIVE` | 401 | Usuário desativado |
| `SESSION_SUPERSEDED` | 401 | Refresh token substituído por login/logout em outro aparelho |
| `AUTHENTICATION_REQUIRED` | 401 | Rota protegida sem token |
| `ACCESS_DENIED` | 403 | Token válido, perfil insuficiente |

**Formatos e unidades**

| Campo | Formato |
|---|---|
| `accessToken` / `refreshToken` | string opaca |
| `expiresIn` | segundos (900) |
| `user.roles` | array de string do enum `Role` |
| Erro | RFC 7807, `application/problem+json`, com campo `code` |

---

## Limitações conhecidas e aceitas

Coisas que sabemos que não estão perfeitas e decidimos aceitar. Registrar evita
que a próxima rodada de review levante de novo como se fosse novidade.

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Sem arquivo `.http` novo | A task não cria nem muda endpoint; `http/00-auth.http` e `http/01-auth-rate-limit.http` já cobrem tudo que a tela consome | — |
| 429 sem contador | A API não documenta `Retry-After`; a tela diz "cerca de um minuto" | Expor `Retry-After` quando houver demanda |
| Nome da casa fixo em "Hospedaria" | `Setting` existe no backend, mas não há endpoint público de leitura antes do login | Ler de `setting` quando houver rota anônima |
| Duas molduras no código | Decisão #1 — o custo é CSS duplicado na tela de login, e é temporário até Ruan escolher | Apagar a moldura perdedora |
| Sem rotas (`react-router`) | A tela de destino é marcador; roteamento entra com a primeira sala de verdade | Entra na task do salão |
| Sem teste automatizado | O risco aqui é visual e de percurso, não de regra de negócio: não há cálculo monetário nem invariante de agregado. O percurso foi verificado no Chrome contra a API real, nas duas molduras | Teste de componente quando houver regra que pague |
| Perfil com duas áreas sem ser ADMIN não existe no seed | O seed `dev` tem um perfil por usuário; só `admin` cai na tela de escolha | — |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | Qual moldura fica, depois de ver as duas rodando? | 1 |
| 2 | Nome definitivo da casa (hoje "Hospedaria", provisório) | 1 |
