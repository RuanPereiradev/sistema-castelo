# Decisões — Task fe-customer-table Tela do cliente na mesa (cardápio e pedido)

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/fe-customer-table` (worktree `../castel-fe-customer`), a partir de `task/qr-table-payment` (PR #39) |
| Rodada atual | 2 — o garçom recebe, confirma e recusa o pedido do celular |
| Build | `npm run build` e `npx tsc -b` passam com a rodada 1 (2026-10-22) |
| Testes | nenhum no front (ferramenta ainda não decidida, `frontend/CLAUDE.md`) |

Consome o backend das tasks `qr-table-ordering` (PR #38) e `qr-table-payment` (PR #39).

---

## Origem

- Design aprovado: `design/Sistema medieval de login (1).zip` → `hospedaria-tela-cliente-portal/`
  (direção "Portal", só celular, 7 telas: boas-vindas, cardápio, carrinho, pedidos, conta, Pix,
  comprovante). Paleta vinho/salmão, Cormorant Garamond + Lora, filete duplo, arco, hedera ❧,
  alvo de toque ≥ 44 px
- Divergências do design já resolvidas no backend: taxa opcional (#38 da payment), pagar por unidade
  (#39), nome do cliente (#40)

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | Escopo desta rodada é "a parte do cardápio do cliente": boas-vindas com nome, cardápio, carrinho, envio do pedido e acompanhamento (Pedidos). Conta, Pix e comprovante ficam para a rodada seguinte (Ruan, 2026-10-22) | implementado |
| 2 | 1 | O texto do design "Enviar para a cozinha" / "vai direto para a cozinha" vira "Enviar pedido" e o estado "aguardando o garçom": todo pedido passa pelo garçom (#6 da `qr-table-ordering`; divergência D, resolvida no texto em 2026-10-22) | implementado |
| 3 | 1 | DEV: rota pública do front `/mesa/:orderingToken`, fora do shell logado e sem login. É o endereço que o QR da mesa abre | implementado |
| 4 | 1 | DEV: o nome digitado e o `customerDeviceId` (gerado uma vez, `crypto.randomUUID()`) ficam no `localStorage` do aparelho; a leitura falha em silêncio (aba anônima) e a tela pede o nome de novo | implementado |
| 5 | 1 | DEV: categorias, itens, preços e disponibilidade vêm do `GET /public/menu`; as categorias do design são fictícias. Item `availableNow` falso aparece esmaecido e sem "+"; item vendido por peso aparece sem "+", com aviso de que se serve no buffet (#20 da `qr-table-ordering`) | implementado |
| 6 | 1 | DEV: item com variação abre a escolha da variação antes de ir ao carrinho; adicionais, quando houver, entram na mesma folha, até o `maxQuantity` | implementado |
| 7 | 1 | DEV: o celular do cliente lê o estado do pedido e da comanda por intervalo (10 s) enquanto a tela está visível. A regra "sem polling" é do KDS; o cliente não tem STOMP (o tópico é só de `WAITER`/`ADMIN`) | implementado |
| 8 | 1 | DEV: o carrinho é estado local (não existe no servidor) e mostra preço unitário e quantidade de cada linha, **sem total**: o front não calcula dinheiro (`frontend/CLAUDE.md`). O total da mesa aparece na Conta, calculado pelo backend. Diverge do design, que soma no carrinho | implementado |
| 9 | 1 | DEV: as telas (boas-vindas, cardápio, carrinho, pedidos) são estado local da página `/mesa/:orderingToken`, como no design, sem sub-rota. Nome já salvo pula a boas-vindas | implementado |
| 10 | 1 | DEV: o carrinho fica em memória; recarregar a página o perde. Linhas com o mesmo item, variação e adicionais se somam | implementado |
| 11 | 1 | DEV: o "Enviar pedido" fica desligado, com aviso, quando a mesa diz `acceptsOrders` falso ou `pendingRequestId` presente (diferenciando "seu pedido anterior" de "alguém da mesa"). A mesa é relida a cada 10 s; o backend continua sendo quem recusa (`TABLE_ORDER_REQUEST_ALREADY_PENDING`, `TAB_CLOSING_REFUSES_ORDER_REQUEST`) | implementado |
| 12 | 1 | DEV: o id do último pedido fica no `localStorage` por mesa (`castel.customer.lastOrderRequest.{token}`); `TABLE_ORDER_REQUEST_NOT_FOUND` o esquece. O estado do pedido só é relido enquanto `PENDING` | implementado |
| 13 | 1 | DEV: o código do aparelho usa `crypto.randomUUID()` e, fora de HTTPS/localhost (celular abrindo o Vite pelo IP da rede), cai em `crypto.getRandomValues` (32 hex). Código salvo fora de 8–64 `[A-Za-z0-9_-]` é trocado por um novo | implementado |
| 14 | 1 | DEV: Pedidos mostra um card por item da comanda (a API devolve itens, não pedidos), com quem pediu ("Você" se `mine`, o `customerName`, ou "Pelo garçom" quando nulo) e o `lineTotal` do backend. Mais novo primeiro, servidos no fim. O card "Seu último pedido" fica acima | implementado |
| 15 | 1 | DEV: item com variação mostra "a partir de" o preço (o backend manda o da variação mais barata); item por peso mostra "R$ X o quilo". Variação indisponível aparece esmaecida e não se escolhe; nenhuma vem pré-selecionada | implementado |
| 16 | 1 | DEV: "Conta" fica na barra inferior, desligada, guardando o lugar da rodada 2 (#1) | implementado |
| 17 | 1 | DEV: o texto da boas-vindas tira "e paga sua parte por Pix" (fora do escopo desta rodada, #1) | implementado |
| 18 | 2 | Rodada 2: o lado do garçom do pedido pelo celular — aviso na mesa do mapa, lista dos pedidos aguardando, confirmar e recusar (#4, #10, #11 da `qr-table-ordering`). Origem: o Ruan pediu pelo celular e o garçom não viu nada (2026-10-22) | implementado |
| 19 | 2 | DEV: nesta branch o salão não tem cliente STOMP (ele está na `feat/cancel-deliver-items`, ainda não enviada); a lista de pedidos do celular é lida a cada 5 s, como o resto do salão desta base, com som quando chega pedido novo. O tempo real da #10 entra quando as duas branches se juntarem | implementado |
| 20 | 2 | DEV: recusar pede confirmação explícita (ação sem volta para o cliente); confirmar não pede, porque o garçom acabou de ler o pedido e o backend recusa o que não pode entrar | implementado |
| 21 | 2 | DEV: o mapa ganha o botão "Pedidos do celular · N" no cabeçalho (sempre visível) e, na mesa com pedido aguardando, a etiqueta "Pedido aguardando" (ou "N pedidos aguardando") com filete salmão, qualquer que seja o estado da mesa. A folha da mesa avisa e leva à lista filtrada por ela, com "Ver todos" | implementado |
| 22 | 2 | DEV: o prazo do pedido aparece como "Expira em N min · às HH:mm": a hora vem do `expiresAt` do servidor; os minutos usam o relógio do aparelho, como o "aberta há N min" do mapa (a rota não devolve `serverTime`). Abaixo de 5 min fica em destaque | implementado |
| 23 | 2 | DEV: a lista mostra só `PENDING` (um pedido que o backend já lê como `EXPIRED` sai, mesmo antes da tarefa gravar). Confirmação recusada mostra o motivo no próprio card e o pedido fica (#21 da `qr-table-ordering`); depois de confirmar ou recusar relê pedidos, comandas, mesas e comanda aberta | implementado |
| 24 | 2 | DEV: o som toca quando aparece um id que a leitura anterior não tinha; a primeira leitura não toca (é o que já esperava). O áudio destrava no primeiro toque ou tecla da página; sem isso, ou com falha, fica em silêncio | implementado |
| 25 | 2 | DEV: as cores Portal de `dining-room-portal.css` passam a valer também em `.dining-room-portal`: o `classical.css` é importado depois e o `:root` dele deixava o salão no tema claro (defeito anterior a esta task, visto no teste). Mudança de uma linha fora do pedido original; reverter se não for desejada | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

- [x] Boas-vindas: mesa, nome do cliente
- [x] Cardápio por categoria, +/− por item, variação e adicionais, barra "Ver pedido"
- [x] Carrinho: conferir, observação, enviar; recusas traduzidas (pedido já aguardando na mesa, conta em fechamento, item esgotado)
- [x] Pedidos: estado do pedido (aguardando garçom, confirmado, recusado, expirado) e etapas dos itens (Recebido, Em preparo, Pronto, Servido), filtro Mesa toda / Só os meus
- [x] Estados de carregando, erro e vazio em cada tela
- [x] Textos em arquivo de mensagens; códigos de erro traduzidos
- [ ] Garçom: aviso "pedido aguardando" na mesa do mapa, lista dos pedidos do celular com nome, itens e tempo restante, confirmar e recusar, som em pedido novo (#18–#20)
- [x] Testar no navegador contra o backend — Chrome headless a 390×844 contra o backend da `castel-qr-run` (banco `castel_qr`): boas-vindas, nome vazio, cardápio, item por peso, carrinho com observação, envio (três toques, um pedido só), Pedidos com etapas e filtro, envio bloqueado com pedido pendente, QR inválido. **Não testado ao vivo:** folha de variação e adicionais (o cardápio de dev não tem nenhum) e os códigos de recusa no envio (a tela bloqueia antes)

---

### Rodada 2 — lado do garçom

- [x] Leitura da fila `GET /api/restaurant/table-order-requests` a cada 5 s, com som de pedido novo
- [x] Etiqueta "Pedido aguardando" na mesa do mapa
- [x] Lista "Pedidos do celular" (mapa e folha da mesa): mesa, nome, linhas, observação, prazo
- [x] Confirmar e recusar (recusa com confirmação), erros traduzidos no card
- [x] Testar no navegador — Chrome headless contra o backend como `garcom`: mapa com etiqueta em Mesa 2 (pedido do Ruan, **não tocado**) e Mesa 3; confirmar um pedido de teste da Mesa 3 (lançado, abriu comanda); recusar outro pela folha da Mesa 3 com o diálogo. **Não testado ao vivo:** confirmação recusada pelo backend (precisaria esgotar um item) e o som (headless não toca áudio)

---

## Contrato com o front

Consumido, não criado aqui: `docs/decisions/task-qr-table-ordering.md` e
`docs/decisions/task-qr-table-payment.md`, seção "Contrato com o front".

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Recarregar a página perde o carrinho (#10) | O carrinho não existe no servidor e é curto; guardar no aparelho traria item velho de outra visita | `sessionStorage` por mesa, se o Ruan quiser |
| O voltar do celular sai da página em vez de voltar de tela (#9) | Telas como estado local, como no design | Sub-rotas `/mesa/:token/carrinho` etc. |
| Duas linhas iguais com observações diferentes não coexistem: a segunda entra na primeira (#10) | Caso raro; a observação é editada na linha | Chave da linha incluir a observação |
| Pedidos não mostra a hora de cada item | A rota pública não devolve `orderedAt` | Campo na `PublicTableTabResponse`, se fizer falta |
| O salão lê a fila a cada 5 s, não por STOMP (#19) | O cliente STOMP do salão está na `feat/cancel-deliver-items` | Assinar `/topic/restaurant/table-order-requests` quando as branches se juntarem |
| O som só toca depois de um toque na página e com a aba aberta | Regra do navegador para áudio; leitura parada em segundo plano | Notificação do sistema, se pedirem |
| O ConfirmDialog compartilhado tem estilo próprio (botão vermelho, cinza), fora do Portal | Componente existente, reusado como pedido (#20) | Portal no `confirm-dialog.css` numa task de estilo |
| Sem teste automatizado | Ferramenta de teste do front ainda não decidida (`frontend/CLAUDE.md`) | Task 4.1 |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | O rótulo da mesa vem livre (`diningTableLabel`); a tela escreve "Mesa {rótulo}", como o salão já faz. Com os rótulos do banco de dev ("Mesa 1") sai "Mesa Mesa 1". O rótulo cadastrado é só o número, ou a tela deve mostrá-lo cru? | 1 |
| 2 | Item vendido por peso só aparece com "Servido no buffet". Basta, ou o cliente deve ver outra instrução (chamar o garçom para pesar)? | 1 |
