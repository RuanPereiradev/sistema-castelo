# Tela de login — desenho aprovado

**Direção escolhida pelo Ruan em 2026-10-03: a B, "Portal".** Arquivo
[`login-portal.html`](login-portal.html).

Sala capitular: fundo vinho lacre `#3D0F1A`, escudo como monograma, e o formulário dentro de
um arco românico em filete salmão duplo. Mais solene e mais escura que a alternativa — o que
também a torna melhor no monitor da cozinha.

## Arquivos

| Arquivo | O que é |
|---|---|
| `login-portal.html` | **O desenho aprovado.** Sete estados renderizados |
| `login-iluminura.html` | A direção A, recusada. Fica como registro de que houve escolha |
| `direcoes.html` | A apresentação das duas direções, com paleta, tipografia, princípios e wireframes |
| `_ds/classical-…/` | O **design system** que o front vai consumir: `styles.css` é a fonte dos tokens, `readme.md` explica como usá-lo |
| `support.js` | Runtime da ferramenta de desenho, para os protótipos abrirem |

Abra os `.html` direto no navegador. Os protótipos são interativos: testam com `admin`,
`garcom`, `recepcao`, `cozinha` ou `gerente`, senha `1234`.

## A paleta e a tipografia, que o front vai implementar

| Nome | Hex | Papel |
|---|---|---|
| Vinho Tinto | `#6B1E2E` | dominante: títulos, bordas, botão, painel |
| Vinho Lacre | `#3D0F1A` | fundo da direção aprovada |
| Salmão | `#E8957F` | iluminura: capitular, filetes, foco. Nunca texto corrido sobre claro |
| Salmão Velado | `#F6D9CE` | fundo das mensagens |
| Pergaminho | `#F4ECE3` | texto sobre o fundo vinho |
| Tinta Ferrogálica | `#2A1A1C` | texto corrido |

Cormorant Garamond 400 nos títulos e na capitular; Cormorant 600 em caixa-alta espaçada nos
rótulos; Lora 400 no texto e nos campos, **a 19px** — acima do limite de 16px, abaixo do qual
o iPhone dá zoom no campo e não volta.

## Escopo: uma tela só

Sem cadastro, sem "esqueci a senha". Não é recorte de desenho: o backend tem **três** rotas de
escrita em identidade — entrar, renovar, sair — e **nenhuma** de criar usuário ou trocar senha.
O rodapé manda falar com o administrador, sem link para lugar nenhum.

## O que a implementação ainda precisa resolver

Conferido contra o backend. As três primeiras o próprio desenho já apontou:

| Ponto | Situação |
|---|---|
| Tempo de espera do 429 | **Não existe `Retry-After`** na API. A tela diz "cerca de um minuto", sem contador. Confirmado no código |
| Motivo da volta ao login | A API devolve o código (`TOKEN_EXPIRED`, `SESSION_SUPERSEDED`); guardar é do front |
| Perfil → tela | Mapa do front. A API só devolve os perfis |
| **Nome da casa** | **Lacuna de API.** A tabela `property` tem `legal_name` e **nenhuma rota o expõe**. "Hospedaria" está fixo no protótipo. Ou o front fixa, ou entra rota nova |
| **Usuário com dois perfis** | **Não existe no seed.** Há `admin`, `recepcao`, `garcom`, `cozinha` e `inativo`, um perfil cada. O `gerente` do protótipo é ficção, então **não há com quem testar** a pergunta "onde deseja entrar?". Precisa de um usuário multi-perfil no seed de dev |
| Senha do protótipo | `1234` no desenho; `admin123` no seed de dev |

## O contrato da API

Está escrito no prompt que gerou este desenho e vale como referência de implementação: as
rotas em `identity/.../web/AuthController.java`, os tempos em `JwtTokenIssuer` (token de acesso
15 minutos, refresh 7 dias), os limites em `app/src/main/resources/application.yml`, e os
códigos de erro exercitados em `http/00-auth.http` e `http/01-auth-rate-limit.http`.
