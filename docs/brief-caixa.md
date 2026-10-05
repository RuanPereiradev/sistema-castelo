# Brief para o agente de design — tela do caixa

> Cole este arquivo inteiro como prompt.
>
> **Nada de hotel aqui.** Sem quarto, sem reserva, sem hóspede, sem check-in. Essa parte do
> sistema não existe e não entra nesta tela.

---

## A boa notícia: o backend desta tela está pronto e testado

Diferente de todo o resto, aqui você desenha contra **API que roda**. Leia o contrato
executável antes de qualquer coisa:

- `http/41-billing-cash-sessions.http` — o turno de caixa inteiro
- `http/40-billing-folios.http` — a conta e os pagamentos
- `http/50-finance-expenses.http` — a saída de dinheiro, que também mexe na gaveta

As convenções do front estão em `frontend/CLAUDE.md`; o glossário e o contrato de dinheiro e
de erro, no `CLAUDE.md` da raiz.

Carregue a skill `frontend-design` antes de decidir cor, tipografia ou layout.

**Uma tentativa anterior de desenho foi reprovada pelo Ruan, e ele não disse o que não
gostou.** Então: **entregue o plano e pare.** Paleta, tipografia, conceito de layout em
wireframe. Se puder, ofereça duas direções diferentes. Não construa nada antes de ele aprovar.

---

## Quem usa, e onde

`ADMIN` e `FRONT_DESK` operam o turno de caixa. O `WAITER` **não** alcança o turno, mas é o
caixa do restaurante: ele recebe o pagamento da comanda. `KITCHEN` não alcança nada.

Balcão, **cliente esperando na frente**, celular ou tablet. Conta de dinheiro em papel-moeda
acontece com as duas mãos, então o aparelho fica apoiado.

Interface em português, termos do glossário: "turno de caixa", "fundo de troco", "sangria",
"suprimento", "pré-conta", "folio", "lançamento".

---

## O que define esta tela: o fechamento é CEGO

Esta é a regra mais importante do brief, e a que o desenho mais costuma estragar.

**Quem conta o dinheiro não pode ver quanto o sistema espera antes de digitar o que contou.**
Se vê, não é conferência: a pessoa digita o valor esperado e a quebra nunca aparece.

O backend já força isso: o campo `expectedAmount` **vem nulo** na resposta para quem não é
`ADMIN` enquanto o turno está aberto. Depois de fechado, aparece para todos.

Então a tela precisa de **dois momentos visualmente separados**:

```
1. CONTAR            2. CONFERIR
┌──────────────┐    ┌──────────────┐
│ Quanto tem   │    │ Contado  300 │
│ na gaveta?   │ -> │ Esperado 320 │
│   [ ____ ]   │    │ Quebra   -20 │
│              │    │              │
│ (nada do     │    │ [nota        │
│  esperado    │    │  opcional]   │
│  na tela)    │    │              │
└──────────────┘    └──────────────┘
```

E duas coisas decorrentes, decididas na task 2.4:

- **O fechamento é SEMPRE aceito**, com quebra ou sem. A nota é **opcional** em qualquer caso.
  A quebra é informação para o `ADMIN` conferir depois, não impedimento.
- A nota não pode ser exigida só quando há quebra — isso **vazaria o esperado**, porque a
  recepção testaria valores até o sistema aceitar sem pedir nota.

---

## O turno de caixa

`GET /api/billing/cash-sessions/current` traz o turno aberto, ou 404 quando não há.

**Só existe um turno aberto por vez** — o banco garante com índice parcial. Tentar abrir o
segundo dá `CASH_DRAWER_SESSION_ALREADY_OPEN`. A tela nunca deve oferecer "abrir" quando já há
turno aberto; deve mostrar o que está aberto.

A resposta traz:

| Campo | O que é |
|---|---|
| `status` | `OPEN` ou `CLOSED` |
| `openingFloat` | o **fundo de troco** com que o turno abriu |
| `movements` | a lista, cada um com tipo, valor, motivo, instante e autor |
| `totalDrops` | soma das **sangrias** |
| `totalSupplies` | soma dos **suprimentos** |
| `cashPaymentsTotal` · `cashPaymentCount` | o que entrou em dinheiro, e quantos pagamentos |
| `expectedAmount` | **nulo** para não-`ADMIN` enquanto aberto |
| `countedAmount` · `difference` | só depois de fechado |
| `closingNote` | opcional |

A conta do esperado, que a tela **não** faz (o backend manda pronto):

```
fundo de troco
  + suprimentos
  − sangrias
  − despesas pagas em dinheiro
  + pagamentos recebidos em dinheiro
  = esperado na gaveta
```

### Os três tipos de movimento

| Tipo | Português | O que é |
|---|---|---|
| `CASH_SUPPLY` | suprimento | dinheiro **entrando** na gaveta, para troco |
| `CASH_DROP` | sangria | dinheiro **saindo** para o cofre — continua sendo do estabelecimento |
| `EXPENSE_PAYMENT` | despesa paga | dinheiro **saindo para fora** — fornecedor na porta, entregador |

Os três mexem no esperado, e **a tela precisa distinguir os dois que saem**: sangria é dinheiro
que foi guardado, despesa é dinheiro que foi gasto. Somar os dois como "saídas" apaga a
diferença que o dono quer ver.

Sangria e suprimento são lançados aqui. A **despesa** é lançada na área de finanças
(`POST /api/finance/expenses`, só `ADMIN`) e **aparece** aqui como movimento.

### A armadilha que custa dinheiro

Sangria, suprimento e pagamento exigem o cabeçalho `Idempotency-Key`. A chave é gerada **uma
vez por tentativa do operador** e **reusada no retry**. Chave nova no retry registra a sangria
duas vezes, e a gaveta passa a mentir.

Desenhe o estado de "enviando" e o de "falhou, tentar de novo" sabendo que o retry é a mesma
operação, não uma nova.

---

## Receber pagamento

Duas origens, e a tela do caixa precisa das duas:

| De onde | Rota | Quem |
|---|---|---|
| Comanda do restaurante | `POST /api/restaurant/tabs/{tabId}/payments` | `ADMIN`, `WAITER` |
| Folio (conta avulsa ou de estadia) | `POST /api/billing/folios/{folioId}/payments` | `ADMIN`, `FRONT_DESK` |

Formas: `CASH` · `PIX` · `CREDIT_CARD` · `DEBIT_CARD` · `ROOM_ACCOUNT`.

Regras que a tela precisa respeitar:

- **Pagamento parcial é normal.** Vários pagamentos numa conta, de formas diferentes. A tela
  mostra o total, o pago e o que falta.
- **Nunca acima do saldo** — `PAYMENT_EXCEEDS_BALANCE`.
- **Pagamento em dinheiro exige turno aberto**, se a configuração `billing.cash-drawer.required`
  estiver ligada. Se não houver turno, o pagamento é recusado — e a tela deve dizer o que fazer
  (abrir o turno), não só mostrar o código.
- **Estorno de pagamento** (`refund`) é só do `ADMIN`, e exige motivo.
- O `ROOM_ACCOUNT` existe no enum mas **não funciona ainda** — a ponte com o hotel é a task 3.3.
  Não desenhe fluxo para ela.

---

## Estados obrigatórios

Os três de sempre, e aqui o de erro é o que mais aparece:

- **Vazio:** nenhum turno aberto. É um convite a abrir, com o campo de fundo de troco.
- **Carregando**
- **Erro:** e neste caso específico é quase sempre conflito ou regra, não falha de rede —
  `CASH_DRAWER_SESSION_ALREADY_OPEN`, `CASH_DRAWER_SESSION_NOT_OPEN`,
  `CASH_DRAWER_SESSION_NOT_OWNED` (quem não abriu o turno e não é `ADMIN` não fecha),
  `PAYMENT_EXCEEDS_BALANCE`, `IDEMPOTENCY_KEY_REUSED`, `INVALID_OPENING_FLOAT`.
  Cada um precisa de frase que diga **o que fazer**, não o código cru.

---

## O que entregar

**Só o plano, nesta rodada.** Paleta em 4–6 hex nomeados, tipografia e papéis, conceito de
layout em wireframe ASCII, princípios, e a revisão da passagem dois dizendo o que você trocou
e por quê.

Comece pelos dois momentos do **fechamento cego** — é a parte que o desenho decide, e onde
errar transforma uma conferência em teatro.

Depois: o turno aberto com seus movimentos, e o recebimento de pagamento.

Diga também qual dado a tela precisaria e a API não devolve, se houver.
