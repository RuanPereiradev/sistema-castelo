# Brief para o agente de design — front-end do sistema

> Cole este arquivo inteiro como prompt. Ele descreve **o que as telas precisam ter para o
> sistema funcionar**, extraído do backend que já está na `main`, não de suposição.

---

## O que é o sistema

Hotel com restaurante, operado pelos próprios funcionários do estabelecimento. Não é produto
de consumidor: não há cadastro, onboarding, nem usuário descobrindo a interface sozinho. Quem
usa foi treinado em meia hora e vai repetir a mesma sequência trezentas vezes por dia.

O backend do **restaurante está completo**. O do **hotel não existe ainda** — ver "Fora do
escopo" no fim.

## Regras duras, não negociáveis

1. **Interface em português. Código em inglês.** Os campos que você vê neste brief
   (`serviceCharge`, `splitGroup`) são nomes de API; na tela aparece "taxa de serviço",
   "grupo da divisão".
2. **Dinheiro é string decimal** (`"180.00"`), nunca número. Vem assim da API para não perder
   precisão no JavaScript. A tela nunca calcula total: o backend manda calculado.
3. **Erro tem código estável.** A API responde RFC 7807 com um campo `code`
   (`TAB_NOT_OPEN`, `FOLIO_BALANCE_NOT_ZERO`). O front traduz código → frase para o operador.
   **Toda tela precisa de um lugar onde esse erro aparece** — há 110 códigos no sistema, e
   vários são conflito de operação simultânea, não erro de digitação.
4. **O perfil do usuário define a tela.** São quatro: `ADMIN`, `FRONT_DESK` (recepção),
   `WAITER` (garçom, que também é o caixa do restaurante) e `KITCHEN` (cozinha). O login
   devolve os papéis, e a navegação mostra só o que o papel alcança.

## Contexto físico de cada operador — isto decide mais do que a estética

| Quem | Onde | Como |
|---|---|---|
| Garçom | de pé, andando no salão | **celular**, uma mão, tela suja, pressa |
| Cozinha | bancada, calor, barulho | monitor grande na parede, **ninguém toca** (ou toca com a mão suja), lido a 2 m de distância |
| Caixa | balcão | celular ou tablet, cliente esperando na frente |
| Recepção / Admin | mesa | desktop, sem pressa |

---

# Tela 1 — Salão e comanda (a mais importante)

**Quem:** `WAITER` e `ADMIN`. **Onde:** celular, mobile-first, uma mão.

Esta é a tela que o produto é. Se ela for ruim o resto não salva.

## 1.1 Mapa do salão

Lista as mesas e os cartões de self-service com comanda ativa.
`GET /api/restaurant/tabs` → lista de comandas abertas; `GET /api/restaurant/dining-tables`
→ as mesas cadastradas.

Precisa mostrar, por mesa: **rótulo** da mesa, se tem comanda aberta, **subtotal** corrente,
**há quanto tempo** está aberta (`openedAt`), e **quantas pessoas** (`guestCount`, opcional).

Estados que a tela tem de distinguir visualmente:
- mesa **livre** (nenhuma comanda) → abre comanda
- mesa com comanda **`OPEN`** → lança item
- mesa com comanda **`CLOSING`** (pré-conta tirada, total congelado) → só recebe pagamento
- mesa **desativada** pelo admin

Ação de abrir: `POST /api/restaurant/tabs` com mesa **ou** número de cartão (1 a 999).

## 1.2 A comanda

`GET /api/restaurant/tabs/{tabId}` devolve a comanda com **todos** os itens, cancelados
inclusive — nada é removido, nunca. Cada item traz: nome, variação (P/M/G), quantidade **ou**
peso em gramas, preço unitário **ou** preço por quilo, adicionais com preço, observação do
pedido, setor de preparo, **status**, e os instantes de cada transição.

**Lançar item** (`POST .../items`) é a ação mais repetida do sistema. O item pode ter:
- **variação** obrigatória quando o item tem variações ativas
- **adicionais**, cada um com quantidade até um máximo por item
- **observação** livre, até 200 caracteres
- **peso em gramas** em vez de quantidade, quando é vendido por quilo

**Cancelar item** (`POST .../items/{itemId}/cancel`) **exige motivo**. Cancelamento destrói
receita, e é o único lugar do sistema que pede justificativa. O item cancelado **continua na
lista**, riscado, com autor e motivo visíveis.

Os cinco status de item, que a tela precisa diferenciar de longe:
`PENDING` · `IN_PREPARATION` · `READY` · `DELIVERED` · `CANCELLED`

O garçom entrega o item pronto: `POST .../items/{itemId}/deliver`.

## 1.3 Mover itens entre comandas

Três operações, perfis `WAITER`, `ADMIN` e **`FRONT_DESK`**:

- **Transferir** (`POST .../transfer`) — marca itens e manda para outra comanda. Move **linha
  inteira**, nunca parte da quantidade. Tudo ou nada. A resposta devolve **as duas comandas**,
  para a tela redesenhar ambas.
- **Juntar** (`POST .../merge`) — a comanda da rota **fica**, a outra é absorvida e vira
  `MERGED`. A tela deve sugerir a mais antiga como a que fica, e deixar claro qual
  desaparece. **Não existe desfazer.**
- **Trocar de mesa** (`POST .../move`) — a comanda inteira muda de mesa. **Responde uma
  comanda com `id` diferente**: a antiga fica `MERGED` apontando para a nova pelo
  `mergedIntoTabId`, e a tela tem de seguir esse ponteiro.

Item transferido traz `transferredFromTabId` — de onde veio. Vale mostrar, discretamente: é
o que responde "por que esta mesa fechou com menos".

**Sugestão que o backend espera do front:** quando o garçom marca *todos* os itens para
transferir, a tela deve oferecer "juntar" em vez de "transferir", porque a comanda de origem
fica vazia e continua ocupando a mesa.

## 1.4 Fechar a conta

Sequência de três passos, nesta ordem:

1. **Pré-conta** (`POST .../closing`) — congela o total, lança no folio, comanda vai a
   `CLOSING`. `GET .../bill` devolve: subtotal, base da taxa, taxa de serviço, total, pago,
   saldo, e os grupos da divisão.
2. **Pagamentos** (`POST .../payments`) — vários, parciais. Métodos: `CASH`, `PIX`,
   `CREDIT_CARD`, `DEBIT_CARD`. **Exige chave de idempotência** no cabeçalho
   `Idempotency-Key`: a tela gera uma por tentativa e **reusa no retry**, senão um toque duplo
   cobra duas vezes.
3. **Fechar** (`POST .../close`) — só com saldo zero.

**Reabrir** (`POST .../reopen`) volta a `CLOSING` → `OPEN`, **exige motivo**, e estorna o
lançamento.

### A divisão da conta, que é onde o desenho costuma falhar

Duas formas, e a tela precisa das duas:
- **Igual**: divide o total por N pessoas. `GET .../bill?parts=4`.
- **Por item**: cada item vai para um **grupo** (1 a 99), e cada grupo tem seu próprio
  subtotal, taxa e total. `PUT .../split-groups`.

A taxa de serviço **não é somada item por item** — é calculada uma vez sobre a soma. Então a
tela nunca deve exibir "taxa" numa linha de item.

A taxa pode ser dispensada na **comanda toda** (`PUT .../service-charge`) ou **item por item**
(`PUT .../items/{itemId}/service-charge`). Item dispensado traz `serviceChargeWaived: true`,
e **isso tem de ser visível na pré-conta** — é dinheiro que o cliente não vai pagar.

---

# Tela 2 — KDS, a tela da cozinha

**Quem:** `KITCHEN` e `ADMIN`. **Onde:** monitor na parede. **Ninguém toca com a mão limpa.**

Três setores independentes, cada um com sua tela: `KITCHEN`, `PIZZA`, `BAR`.

`GET /api/kitchen/queue?station=PIZZA` devolve a fila com, por ficha: nome do item, variação,
quantidade, adicionais, **observação do pedido**, mesa **ou** número do cartão, status e o
instante do pedido. A resposta traz também `serverTime` e dois limites em minutos:
`warningAfterMinutes` e `lateAfterMinutes`.

**O atraso é a informação principal da tela.** Cada ficha tem de mostrar quanto tempo espera,
e mudar de aparência ao passar de "atenção" para "atrasado" — comparando com o `serverTime`
do servidor, nunca com o relógio do navegador. Os limites são por setor: o bar avisa em 5
minutos, a pizzaria em 20.

Ações, botões grandes: `start` (começou), `ready` (pronto), `undo` (um passo atrás).
Um item pode ir direto de `PENDING` a `READY`, sem passar por preparo.

## Tempo real — obrigatório aqui

A tela **não faz polling**. Conecta em STOMP sobre WebSocket em `/ws/kitchen` e assina:
- `/topic/kitchen/{SETOR}` — toda mudança de item daquele setor
- `/topic/restaurant/ready-items` — item entrando ou saindo de `READY`, para os garçons

Cada mensagem tem um `type`, e a tela reage diferente a cada um:

| `type` | O que aconteceu | Como a tela reage |
|---|---|---|
| `ORDERED` | item novo | entra na fila |
| `STATUS_CHANGED` | avançou ou voltou | atualiza a ficha |
| `CANCELLED` | garçom cancelou | **sai da fila, com aviso** — alguém pode estar preparando |
| `TRANSFERRED` | mudou de comanda | **a ficha troca de mesa**, não sai da fila |

A mensagem traz a ficha inteira e um `updatedAt`. Quando duas chegam para o mesmo item, vale
a de `updatedAt` maior. Ao reconectar, a tela **recarrega a fila inteira** pelo `GET`, porque
pode ter perdido mensagem.

---

# Tela 3 — Self-service com balança

**Quem:** `WAITER`. **Onde:** balcão do buffet, fila esperando.

O cliente pega um cartão numerado (1 a 999). O prato vai na balança, e o operador digita
**gramas**. `POST .../items` com `weightGrams`.

O prato por peso é diferente de todo outro item: **nasce `DELIVERED`** (o cliente se serviu,
não passa pela cozinha), não aceita variação, nem adicional, nem quantidade. O total é peso ×
preço por quilo, arredondado ao centavo pelo backend.

Comanda de self-service **não cobra taxa de serviço**. Se um item vier de uma mesa para o
cartão, ele chega sem taxa, e vice-versa.

A tela precisa: digitação rápida de número (gramas e número de cartão), confirmação do valor
calculado antes de lançar, e o acumulado do cartão visível.

---

# Tela 4 — Caixa

**Quem:** `ADMIN` e `FRONT_DESK`.

Turno de caixa: abre com **fundo de troco**, recebe **sangria** (`CASH_DROP`) e **suprimento**
(`CASH_SUPPLY`), e fecha com **contagem cega**.

`GET /api/billing/cash-sessions/current` devolve o turno com: fundo, movimentos, total de
sangrias, total de suprimentos, total recebido em dinheiro, quantidade de pagamentos,
**esperado**, **contado** e **diferença**.

**O detalhe que o desenho tem de respeitar: o fechamento é cego.** Quem conta o dinheiro
**não pode ver o valor esperado antes de digitar o que contou** — senão não é conferência. O
campo `expectedAmount` vem oculto para quem não é `ADMIN`. A tela precisa de dois momentos
distintos: digitar a contagem, e só depois revelar a diferença.

O fechamento é **sempre aceito**, mesmo com quebra; a nota é opcional. A quebra é informação,
não impedimento.

## Folio (a conta)

`GET /api/billing/folios/{folioId}` → lançamentos, pagamentos, total de lançamentos, total
pago e **saldo**. Tipos de lançamento: diária, consumo do restaurante, ajuste.

O `ADMIN` pode **estornar lançamento** (`reversal`, que gera lançamento oposto, nunca apaga),
**estornar pagamento** (`refund`) e lançar **ajuste/desconto**. A tela deve deixar claro que
estorno **aparece como linha nova**, não como remoção.

Folio de comanda **não é fechado pelo balcão** — responde `FOLIO_OWNED_BY_TAB`. Quem fecha é
a comanda.

---

# Tela 5 — Administração do cardápio

**Quem:** `ADMIN`. **Onde:** desktop, sem pressa.

Categorias, itens, variações (P/M/G com preço próprio), adicionais e **janelas de horário**
(o item só é servido entre tais horas).

Dois eixos independentes que a tela costuma confundir:
- **ativo / inativo** — cadastro, decisão permanente
- **disponível / esgotado** — do dia, alternado rápido

O "esgotou" é a ação mais usada desta tela, e no meio do serviço: merece atalho, não
formulário.

Mesas (`dining-tables`): rótulo, lugares, área. Mesa com comanda aberta **não desativa**
(`DINING_TABLE_HAS_OPEN_TAB`) — o caminho é trocar a comanda de mesa primeiro.

---

# Shell: o que toda tela compartilha

- **Login** (`POST /api/auth/login`): usuário e senha, devolve `accessToken` (expira em 15
  minutos), `refreshToken`, e o usuário com seus papéis. O refresh é silencioso — **o garçom
  não pode ser deslogado no meio de um pedido**.
- Limite de tentativa de login existe: `TOO_MANY_LOGIN_ATTEMPTS` precisa de uma mensagem que
  não pareça bug.
- **PWA**: a cozinha e o salão rodam em aparelho dedicado; precisa instalar e abrir em tela
  cheia.
- **Offline não está resolvido no backend.** Se o garçom perder o Wi-Fi no meio do salão, o
  lançamento falha. Desenhe o estado "sem conexão" e o retry explícito; não invente fila
  offline, porque o backend não tem idempotência em lançamento de item (só em pagamento).

---

# Fora do escopo: o hotel não existe ainda

Não há `Room`, `RoomType`, `RatePlan`, reserva, disponibilidade, check-in nem check-out no
backend. **Não desenhe as telas de recepção do hotel** contra API imaginada — elas vêm
depois, e o contrato ainda não existe. O que existe de hotel é o vocabulário no glossário.

Quando chegarem, há um ponto já decidido que afeta o salão: o hóspede vai poder lançar o
almoço na **conta do quarto** (`ROOM_ACCOUNT`), então a tela de fechar comanda vai ganhar um
destino além de "pagamento direto". Deixe espaço para isso.

---

# O que entregar de volta

1. **Telas do salão e da comanda primeiro** — é onde o produto vive, e o backend está inteiro.
2. Para cada tela: os **estados vazios**, os **estados de erro** (com onde o `code` aparece) e
   o estado **carregando**. Operação simultânea gera conflito de verdade aqui; "a comanda que
   você está olhando acabou de ser fechada por outro garçom" é cena real, não exceção.
3. Um **design system mínimo**: cor, tipografia, espaçamento, e os componentes que repetem —
   linha de item, cartão de mesa, ficha da cozinha, campo de dinheiro, botão destrutivo (o que
   pede motivo).
4. Diga o que **não** desenhou e por quê. É mais útil que preencher tudo.

**Dúvida de regra de negócio: pergunte, não invente.** As regras deste sistema estão
decididas e escritas em `docs/decisions/`; inventar comportamento custa retrabalho no backend.
