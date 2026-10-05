# Plano de design — salão e comanda

## Passagem 1 — o plano

### O assunto, que é de onde vêm as escolhas

O objeto físico que esta tela substitui não é um dashboard: é o **bloco de comanda de papel**
que o garçom carrega no bolso — uma linha por item, o total somado a lápis no pé. A própria
linguagem do backend pensa assim: `lineTotal`, "mover linha inteira", "partir a linha". **A
linha é a unidade de negócio**, não um enfeite de layout.

Público: garçom treinado, de pé, uma mão, pressa, trezentas repetições por dia. A tela não
encanta ninguém — ela tem de deixar lançar item rápido e dizer quanto a mesa deve.

### Cor — 6 valores

A cor aqui **não decora: ela é a legenda de status**. O domínio tem cinco status de item que
precisam ser distinguíveis de braço estendido, e isso é o que gera a paleta.

| Nome | Hex | Papel |
|---|---|---|
| `tinta` | `#191512` | Superfície. Preto **quente**, não neutro: é a cor do salão à noite |
| `papel` | `#241F1A` | Superfície elevada — a "folha" da comanda |
| `giz` | `#EDE6DD` | Texto e dinheiro. Branco quente, cor de luz incandescente |
| `brasa` | `#E2902B` | Em preparo: o fogo está aceso |
| `cinza` | `#8A8178` | Pendente, e tudo que é secundário |
| `oxido` | `#8E3B32` | Cancelado. Vermelho queimado, sem saturação de alerta |

**Pronto** não ganha matiz: a linha **inverte** — fundo `giz`, texto `tinta`. É o único momento
alto da tela, e é inversão em vez de cor porque "pronto" é a única coisa que exige ação do
garçom agora.

**Entregue** é `cinza` a 45% — presente, dormente.

Fundo escuro é escolha funcional, não estética: a sala é penumbra e o celular é o objeto mais
claro nela. Tela branca às 21h queima a visão noturna de quem anda entre as mesas.

### Tipografia — uma família, usada no eixo de largura

**Archivo variável**, só ela, explorando o eixo `wdth` (62–125) como recurso ativo:

| Uso | Ajuste |
|---|---|
| Nome do item | `wdth 100`, `wght 500` |
| Dinheiro | `wdth 85`, `wght 600`, **cifras tabulares** — coluna de valor tem de alinhar |
| Total | `wdth 115`, `wght 700`, o maior corpo da tela |
| Detalhe e status | `wdth 92`, `wght 400`, corpo menor, `cinza` |

Uma família em dois extremos de largura substitui o par display/corpo e evita o clichê das
duas famílias. Sem versalete, sem caixa-alta em rótulo.

### Layout

Uma ideia estrutural para todo o app: **a linha com régua**. O mapa do salão é uma lista de
linhas onde o "item" é a mesa; a comanda é uma lista de linhas onde o item é o item. Nome à
esquerda, dinheiro à direita, alinhado por cifra tabular. Nada centralizado — lista operacional
se lê varrendo a borda esquerda.

Ação repetida mora na **zona do polegar**: barra inferior fixa, com recuo de área segura.

```
MAPA DO SALÃO                      COMANDA
┌──────────────────────────┐      ┌──────────────────────────┐
│ Salão         12 abertas │      │ ‹ Mesa 12    5 pessoas ⋯ │
├──────────────────────────┤      ├──────────────────────────┤
│ Mesa 12          112,10  │      │ Pizza Calabresa G  70,00 │
│ 1h20   5 pessoas         │      │   borda recheada ×2      │
├──────────────────────────┤      ├──────────────────────────┤
│ Mesa 4            68,00  │      │ Refrigerante       12,00 │
│ 12min  2 pessoas         │      │   2 unidades             │
├──────────────────────────┤      ├──────────────────────────┤
│ Cartão 37         26,18  │      │ Suco                9,00 │ ← invertida
│ 4min                     │      │   pronto para levar      │
├──────────────────────────┤      ├──────────────────────────┤
│ Mesa 7             livre │      │ Feijoada           55,00 │ ← riscada
│                          │      │   cancelado: desistiu    │
└──────────────────────────┘      ├──────────────────────────┤
                                  │ Subtotal          103,00 │
                                  │ Taxa de serviço     9,10 │
                                  │ Total             112,10 │ ← maior
                                  ├──────────────────────────┤
                                  │ [    Lançar item     ]   │
                                  └──────────────────────────┘
```

### Princípios

1. **A linha é a unidade.** A régua entre itens é o `TabItem`, não um divisor decorativo.
2. **Dinheiro é o dado mais alto**, à direita, tabular, sempre na mesma coluna.
3. **Cor só significa status.** Decoração não recebe cor.
4. **Pronto inverte**, não tinge. Um momento alto, e só um.
5. **Só a exceção ganha ênfase.** Mesa aberta há 3h se destaca; as outras não.
6. **O polegar manda** na barra inferior.

---

## Passagem 2 — a revisão contra o brief

Três partes do plano eram o que eu produziria para qualquer tela parecida. Trocadas:

**1. Grade de cartões no mapa do salão → lista de linhas.**
O plano original tinha as mesas em cartões arredondados dois por linha. É exatamente o
"kit de cartões SaaS": mesma borda, mesma sombra, conteúdo picado em retângulos iguais. Trocado
por lista de linhas com régua — o app passa a ter **uma** ideia estrutural em vez de duas, e a
mesa fica sendo "uma linha" igual ao item, o que também é verdade no domínio (uma comanda por
mesa).

**2. Barra de progresso do tempo aberto → número discreto, com ênfase só no atraso.**
Eu tinha posto uma barra que enche conforme a mesa envelhece. Barra de progresso para algo que
não tem alvo é decoração fingindo informação — e enfeitar *toda* linha mata a hierarquia.
Agora o tempo é texto apagado, e **só** a mesa fora do normal ganha peso. Enfatizar a exceção
é o que a tela precisa; enfatizar todo mundo é não enfatizar nada.

**3. Verde para "pronto" → inversão da linha.**
Verde-limão sobre fundo escuro é o segundo clichê da lista da skill, e gastaria o único momento
alto da tela num matiz emprestado. A inversão é mais forte a dois metros, não depende de
percepção de cor (daltonismo), e deixa a paleta com um eixo só de significado.

Mantive de propósito, com motivo: **fundo escuro**. É um dos clichês listados, mas aqui o brief
pinou o contexto — penumbra, celular como objeto mais claro da sala — e o próprio texto da
skill diz que a palavra do brief vence. Não gastei a liberdade num acento ácido: a cor virou
sistema de status de cinco valores.

### Acessório removido

Tirei o ícone de status em cada linha. O status já está dito pela cor do ponto, pelo texto de
detalhe e, no caso de "pronto", pela inversão da linha inteira. O ícone era a quarta repetição
da mesma informação.
