# Front-end

React 18 · TypeScript · Vite. Fora do Maven: o build do front não entra no
`./mvnw clean install`.

```bash
npm install
npm run dev          # http://localhost:5173
npm run build        # tsc --noEmit + bundle em dist/
npm run typecheck
```

O servidor de desenvolvimento faz proxy de `/api` para `http://localhost:8080`,
onde o Spring Boot roda pelo IntelliJ. Nada de CORS, nada de URL absoluta no
bundle.

Para ver a tela com dados de verdade, suba o Postgres e o backend no perfil
`dev` — o seed cria `admin`, `recepcao`, `garcom` e `cozinha`, todos com a
mesma senha (`admin123` por padrão).

## Tela de entrada

As duas direções aprovadas do `design/` convivem no código enquanto o Ruan
decide entre elas, e trocam pelo seletor no canto. Veja
`docs/decisions/task-FE1.md`.

| Estado | Como ver |
|---|---|
| 401 · credenciais | senha errada |
| 429 · espera | 10 falhas no mesmo par (IP, usuário) dentro de um minuto |
| Sessão encerrada | `?sessionEnd=TOKEN_EXPIRED` (ou `INVALID_TOKEN`, `USER_INACTIVE`, `SESSION_SUPERSEDED`) |
| Escolher destino | entrar como `admin`, que alcança as três áreas |
| Sair de todos | entrar e usar **Sair** — chama `POST /api/auth/logout` de verdade |

Quem receber um 401 de refresh chama `recordSessionEnd(code)` antes de mandar o
usuário para cá; é assim que a tela sabe por que ele voltou.
