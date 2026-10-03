# Decisões — Task 4.1 Shell do front

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | a abrir |
| Rodada atual | 0 (desenho aprovado; nenhuma linha de código) |
| Build | não rodado |
| Testes | 0 |

Nada de front existe ainda. Convenções em `frontend/CLAUDE.md`.

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | **D1 — O desenho da tela de login é a direção B, "Portal".** Escolhida pelo Ruan em 2026-10-03 entre duas que ele trouxe. Fundo vinho lacre `#3D0F1A`, escudo como monograma, formulário dentro de arco românico em filete salmão duplo. Arquivos versionados em `design/login/`, com a direção A recusada mantida como registro de que houve escolha. O design system é o **Classical** (`design/login/_ds/classical-…/styles.css`): editorial, Cormorant Garamond sobre Lora, cor como traço e nunca preenchimento. | pendente |
| 2 | 0 | **D2 — Uma tela só: entrar.** Sem cadastro e sem "esqueci a senha", porque o backend não tem rota para nenhum dos dois: identidade tem três rotas de escrita (entrar, renovar, sair) e **nenhuma** de criar usuário ou trocar senha. O rodapé manda falar com o administrador, sem link. | pendente |
| 3 | 0 | **D3 — Campos a 19px.** Acima do limite de 16px, abaixo do qual o iPhone dá zoom ao focar o campo e não volta. Vem do desenho e vale como regra do front. | pendente |
| 4 | 0 | **D4 — Destino depois do login:** um perfil com destino único entra direto (a cozinha cai na cozinha); `ADMIN` ou dois perfis ou mais vê "Onde deseja entrar?", com a última escolha daquele aparelho marcada. O mapa perfil → tela é do front; a API só devolve os perfis. | pendente |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [ ] Estrutura de pastas, build (Vite), roteamento, PWA
- [ ] Arquivo de tradução e o mapa de código de erro para frase
- [ ] Tokens do design system Classical, mais a paleta medieval do login
- [ ] Tela de login conforme `design/login/login-portal.html`
- [ ] Renovação silenciosa do token e o estado de volta ao login com motivo
- [ ] Destino por perfil (D4)
- [ ] Filtro de caminho no CI, para mudança de front não pagar os 6 minutos do backend

### A decidir antes da primeira linha

- Estilo: CSS Modules, Tailwind, ou o `styles.css` do design system direto
- Biblioteca de tradução, ou mapa próprio
- Ferramenta de teste e o que vale testar no front
- Onde o token é guardado
- Como o front é servido em produção (o `plano-tecnico.md` §10 diz Nginx)

---

## Pontos em aberto

| # | Pergunta | Quando bloqueia |
|---|---|---|
| A1 | **Nome da casa.** `property.legal_name` existe no banco e **nenhuma rota o expõe**. O front fixa no código, ou entra rota nova? | Na tela de login |
| A2 | **Usuário com dois perfis não existe no seed** (`admin`, `recepcao`, `garcom`, `cozinha`, `inativo`, um perfil cada). Sem ele não há como testar a D4. Acrescentar ao `DevUserSeeder`? | Ao implementar a D4 |
