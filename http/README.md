# Bancada `.http`

Os arquivos desta pasta são os testes de rota executáveis do projeto. Todo
entregável passa por aqui: pelo `CLAUDE.md`, **task sem `.http` executável está
incompleta**.

Eles rodam no IntelliJ (HTTP Client, embutido no IDEA) e em Docker, sem IDE.

---

## Antes da primeira execução

O arquivo com senhas **não é versionado**. Crie o seu a partir do exemplo:

```bash
cp http/http-client.private.env.json.example http/http-client.private.env.json
```

E troque o `CHANGE_ME` pela senha do seed de desenvolvimento, que no perfil
`dev` vale `admin123` por padrão (`castel.dev.seed-password`).

O `.gitignore` já cobre `http-client.private.env.json`. **Nunca versione esse
arquivo**, nem cole senha dentro de um `.http`.

---

## Rodando no IntelliJ

1. Suba o Postgres: `docker compose -f docker-compose.dev.yml up -d`
2. Rode a aplicação no perfil `dev` (pelo IntelliJ, não em container — o
   debugger precisa disso)
3. Abra o `.http` e escolha o ambiente **`dev`** no seletor do topo
4. Rode o arquivo inteiro pelo ícone duplo (`Run all requests in file`), **em
   ordem**: o primeiro cenário de cada arquivo faz login e guarda o token numa
   variável global que os demais usam

O resultado aparece na aba *Services*, com um verde ou vermelho por `client.test`.

---

## Rodando em Docker, sem IDE

Mesma coisa, sem abrir o IntelliJ — é como a bancada é verificada antes de um PR:

```bash
docker run --rm --network host \
  -v "$PWD/http:/workdir" \
  jetbrains/intellij-http-client \
  -e dev \
  -v /workdir/http-client.env.json \
  -p /workdir/http-client.private.env.json \
  /workdir/03-errors.http
```

A saída termina em `N requests completed, 0 have failed tests` e `RUN SUCCESSFUL`.

O `--network host` existe porque a aplicação roda na sua máquina, não em
container: sem ele, o `localhost` do arquivo apontaria para dentro do container
do cliente.

---

## Os ambientes

`http-client.env.json` é versionado e guarda o que não é segredo — a URL e os
nomes de usuário do seed:

| Ambiente | Para quê |
|---|---|
| `dev` | A aplicação rodando na sua máquina, contra o Postgres do Compose |
| `local` | Mesmo endereço, separado para você apontar para outro banco sem mexer no `dev` |

Para um ambiente novo, acrescente uma chave nos **dois** arquivos: o público com
a URL, o privado com a senha.

---

## Os arquivos

| Arquivo | Task | O que cobre |
|---|---|---|
| `00-auth.http` | 0.4 | Login, refresh, logout, `/me`, sessão única, rota protegida |
| `01-auth-rate-limit.http` | 0.4 | Limite de falhas por par (IP, username) |
| `02-auth-rate-limit-ip.http` | 0.4 | Limite de falhas por IP, somando os usernames |
| `03-errors.http` | 0.5b | Corpos de erro RFC 7807: 404, 405, 400, 413, 401, 403 |
| `30-restaurant-menu.http` | 0.8 | Categorias e itens do cardápio, preço, esgotar, janela de horário, `/public/menu` |
| `31-restaurant-menu-variants-modifiers.http` | 1.2 | Variações, adicionais, esgotar variação, cardápio público com "a partir de" |
| `32-restaurant-dining-tables.http` | 1.5 | Cadastro de mesas, ordem natural da lista, leitura pelo garçom, inativas |

Numeração em ordem de execução sugerida. Cada task nova acrescenta o próximo
número — a 0.8 traz o `30-restaurant-menu.http`.

---

## Escrevendo um arquivo novo

- **Cenário negativo é obrigatório**, não enfeite. O `CLAUDE.md` pede caminho
  feliz *e* erro.
- Afirme o **`code`** do corpo, não a mensagem: o código é o contrato estável, a
  mensagem muda.
- Use `client.global.set` para passar token entre cenários, nunca cole um token
  no arquivo — ele expira em 15 minutos e vira um teste que só funcionou uma vez.
- Nada de senha, CPF ou token literal. O que é segredo mora no arquivo privado.
- Deixe o arquivo rodável **do zero**: quem clona o repositório roda sem
  configurar nada além do arquivo privado.

### Os dois arquivos de limite de tentativa

`01` e `02` gastam a cota de falhas de login de propósito. Rode cada um em uma
aplicação recém-subida, e não junto com os outros: o limite é por janela de um
minuto, e um arquivo deixa o outro vermelho sem que nada esteja quebrado.
