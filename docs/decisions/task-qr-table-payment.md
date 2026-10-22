# Decisões — Task qr-table-payment Conta e Pix pelo celular da mesa

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/qr-table-payment` (worktree `../castel-qr-ordering`), a partir de `task/qr-table-ordering` (PR #38) |
| Rodada atual | 5 — confirmados da comanda para o estorno pelo salão (#59–#61) |
| Build | Rodada 5 (2026-10-22), numa cópia fora do worktree: `./mvnw clean verify` passa inteiro, ArchUnit incluído — 2114 testes, 0 falhas |
| Testes | nenhum teste novo nesta rodada (o implementador não escreve teste); falta cobrir a lista de confirmados |
| `.http` | `38` (70 requisições, com a lista de confirmados: estornado com `refunded: true`, status inválido e sem `tabId` 400, comanda desconhecida vazia, recepção 403) verde contra um Postgres descartável (2026-10-22) |

Continua a `qr-table-ordering`. Ordem: backend primeiro, testar as rotas, depois o front (Ruan, 2026-10-22).

---

## Origem

Levantamento de 2026-10-22 no código:

- O pagamento da comanda só existe a partir do `CLOSING` (task 3.2): o `startClosing` congela o total,
  lança um único `TabCharge` num folio `TAB` aberto naquele momento, e os pagamentos entram nesse folio
  por `POST /api/restaurant/tabs/{tabId}/payments` (`ADMIN`, `WAITER`), com `Idempotency-Key`
- Com a comanda `OPEN` não há folio nem lançamento: não há onde registrar um pagamento
- A divisão por item já existe no fechamento (`split_group` por item, total por grupo, com a taxa
  de serviço); "grupo já pagou" ficou fora da 3.2 (F7)
- `PaymentMethod.PIX` existe; a porta `PaymentProcessor` existe só com adaptador fake, e nenhum fluxo da
  v1 a chama (1.4 #1)

---

## Decisões confirmadas

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | O celular da mesa mostra o que a mesa pediu: os itens da comanda, além do estado de cada pedido. **Reverte a #12 da `qr-table-ordering`**, que deixava a conta fora do celular (Ruan, 2026-10-22) | implementado |
| 2 | 0 | O cliente paga pelo celular, **só por Pix** (Ruan, 2026-10-22) | implementado |
| 3 | 0 | No início não há integração com banco: o cliente paga, mostra o comprovante ao garçom, e pronto (Ruan, 2026-10-22) | implementado |
| 4 | 0 | O cliente escolhe quais itens paga — ex.: mesa de 5, eu consumi x, y e z e pago esses (Ruan, 2026-10-22) | implementado |
| 5 | 0 | Backend primeiro, testar os endpoints, depois o front (Ruan, 2026-10-22) | implementado (backend; front a seguir) |
| 6 | 0 | O cliente paga com a mesa ainda pedindo (`OPEN`), sem esperar a pré-conta: quem quer ir embora paga a sua parte e sai (Ruan, 2026-10-22). Consequência: o pagamento deixa de depender do `startClosing`; o dinheiro da comanda passa a aceitar parte paga antes do fechamento | implementado |
| 7 | 0 | O celular mostra o QR do Pix e o código copia e cola **com o valor** dos itens escolhidos, gerado a partir da chave Pix do restaurante, sem integração com banco. O admin cadastra uma vez chave, nome do recebedor e cidade (exigidos pelo padrão do Pix). O sistema não sabe sozinho que o dinheiro caiu (Ruan, 2026-10-22) | implementado |
| 8 | 0 | O garçom confirma o pagamento ao ver o comprovante. O Pix gerado cria um pagamento **aguardando o garçom** que reserva os itens escolhidos (ninguém mais da mesa paga os mesmos); só na confirmação o pagamento entra na conta e os itens ficam pagos. Sem confirmação em 30 min, a reserva expira e os itens voltam a ficar livres (Ruan, 2026-10-22) | implementado |
| 9 | 0 | Item pago fica na lista de todos, marcado como pago, e não pode ser escolhido de novo. O garçom não cancela nem transfere item pago; para isso estorna antes o pagamento, e o item volta a ficar livre. Na pré-conta final o que já foi pago aparece abatido do total (Ruan, 2026-10-22) | implementado |
| 10 | 0 | A parte paga antes do fechamento inclui a taxa de serviço sobre os itens escolhidos, exceto item com taxa dispensada pelo garçom. A taxa paga fica congelada no pagamento: mudar a taxa da mesa depois não afeta quem já pagou (Ruan, 2026-10-22) | **revertida pela #38** |
| 11 | 0 | Vários pagamentos aguardando ao mesmo tempo na mesma mesa, um por pessoa, desde que não escolham o mesmo item; sem limite além disso (Ruan aprovou a proposta P11, 2026-10-22) | implementado |
| 12 | 0 | Qualquer item não cancelado pode ser pago, já entregue ou ainda na cozinha; item vendido por peso também (Ruan aprovou a proposta P12, 2026-10-22) | implementado |
| 13 | 0 | O garçom recusa (comprovante não confere): a reserva some e os itens voltam a ficar livres; o celular mostra "recusado" (Ruan aprovou a proposta P13, 2026-10-22) | implementado |
| 14 | 0 | No fechamento, a pré-conta mostra total, já pago e falta pagar; a comanda só fecha com tudo pago, como hoje. O garçom continua fechando: a mesa não fecha sozinha quando o último paga (Ruan aprovou a proposta P14, 2026-10-22) | implementado |
| 15 | 0 | Na tela de administração: chave Pix, nome do recebedor e cidade. Sem Pix cadastrado, o celular não oferece pagar (Ruan aprovou a proposta P15, 2026-10-22) | implementado |
| 16 | 0 | Glossário: `TablePaymentRequest` (tabela `table_payment_request`), `TablePaymentRequestStatus` `PENDING · CONFIRMED · REJECTED · EXPIRED`, `TablePaymentRequestItem`, `TabItem.isPaid()`, `pixPayload`, `PixReceiver` (`key`, `receiverName`, `city`) (Ruan, 2026-10-22). Entrou no `CLAUDE.md` | implementado |
| 17 | 1 | DEV — dinheiro com a comanda `OPEN`: a confirmação abre o folio `TAB` se ainda não há (o mesmo que o `startClosing` reaproveita), lança **um `TabCharge` da parte** com o valor congelado do pedido e registra o pagamento `PIX` desse valor, com `Idempotency-Key` = id do pedido. O saldo do folio fica zero. Quebra a F6 da 3.2 (um só `TabCharge` por comanda): passa a ser um por parte paga mais o do fechamento | implementado |
| 18 | 1 | DEV — dinheiro com a comanda `CLOSING`: o total já está lançado; a confirmação só registra o pagamento `PIX` contra o folio. O valor do pedido usa a taxa congelada no fechamento | **revertida pela #25** (a taxa congelada continua valendo) |
| 19 | 1 | DEV — o `startClosing` lança só o total dos itens **ainda não pagos** (com a taxa do momento); se não sobra nada, não lança e o saldo já é zero. O `reopen` estorna só esse lançamento do fechamento; as partes pagas ficam | implementado |
| 20 | 1 | DEV — valor da parte = soma dos itens escolhidos + taxa de serviço sobre os não dispensados, calculado e congelado na criação do pedido; é o valor do Pix e o que a confirmação lança. Mudança da taxa entre a criação e a confirmação não altera o valor (#10) | implementado |
| 21 | 1 | DEV — item reservado (pedido de pagamento `PENDING`) ou pago não é cancelado nem transferido: o garçom recebe um código próprio. Para liberar item pago, o garçom estorna: `refund` do pagamento + `reverse` do lançamento da parte + itens voltam a livres (#9) | implementado |
| 22 | 1 | DEV — o `pixPayload` é o BR Code estático do Pix (EMV, campos 26/52/53/54/58/59/60/62 e CRC16), com o valor e um `txid` derivado do pedido. Gerado no backend, não no celular: dinheiro não é calculado no front | implementado |
| 23 | 1 | DEV — `PixReceiver` mora em `setting` (`pix.key`, `pix.receiver-name`, `pix.city`), lido e gravado pelo `ADMIN`. Nome cortado em 25 e cidade em 15 caracteres, sem acento, como o padrão exige | implementado |
| 24 | 1 | DEV — migration `V14`; o hotel desce de novo, para V15/V16, pela mesma regra da #27 da `qr-table-ordering`. Aprovada junto com as recomendações (Ruan: "pode seguir com suas recomendações", 2026-10-22) | implementado |
| 25 | 1 | DEV (implementação): com a comanda `CLOSING`, a confirmação **também** lança o `TabCharge` da parte, registra o `PIX` e troca o lançamento do fechamento — estorna o em vigor e lança de novo o que falta pagar, com a taxa congelada. Invariante: os lançamentos vivos do folio = Σ partes confirmadas e não estornadas + um lançamento do fechamento sobre os itens ainda não pagos. Só assim o `reopen` (que estorna só o do fechamento, #19) e o estorno da parte (#21) deixam o saldo certo: com a #18 ao pé da letra, um Pix confirmado em `CLOSING` e depois um `reopen` deixaria o pagamento sem lançamento e o saldo negativo. O estorno em `CLOSING` faz o mesmo ao contrário. **Substitui a #18; precisa do OK do Ruan** Aprovada junto com as recomendações (Ruan: "pode seguir com suas recomendações", 2026-10-22) | implementado |
| 26 | 1 | DEV (implementação): a reserva e o pagamento moram no `tab_item` (`table_payment_request_id`, `paid_service_charge`): a taxa do pedido, arredondada uma vez sobre a base dele (#20), é repartida pelos itens pelo maior resto (o mesmo rateio da F4), e cada item pago guarda a sua parte. A comanda calcula `serviceCharge` = Σ partes congeladas dos itens pagos + base dos não pagos × taxa (HALF_UP, uma vez); `paidAmount` = Σ itens pagos; `amountDue` = total − `paidAmount`. Sem item pago, tudo igual à 3.2. O `startClosing` lança `amountDue` (#19) | implementado |
| 27 | 1 | DEV (implementação): toda escrita de pedido de pagamento (criar, confirmar, recusar, expirar, estornar) trava a comanda `FOR UPDATE`: dois celulares escolhendo o mesmo item se enfileiram e o segundo recebe `TAB_ITEM_RESERVED_FOR_PAYMENT`; confirmação × `startClosing` e × cancelar item também se enfileiram. Ordem das travas: pedido → comanda → folio | implementado |
| 28 | 1 | DEV (implementação): o `close` da comanda recusa com `TAB_ITEM_RESERVED_FOR_PAYMENT` enquanto houver item reservado: o Pix aguardando precisa ser confirmado ou recusado antes, senão o cliente que pagou ficaria sem confirmação possível | implementado |
| 29 | 1 | DEV (implementação): além da transferência (#21), juntar comandas e trocar de mesa também recusam item reservado ou pago (`TAB_ITEM_RESERVED_FOR_PAYMENT`, `TAB_ITEM_ALREADY_PAID`): o dinheiro da parte está no folio da comanda de origem, que a junção fecha. **Pergunta ao Ruan**: trocar de mesa depois de alguém pagar a sua parte obriga a estornar antes; aceitável? Aprovada junto com as recomendações (Ruan: "pode seguir com suas recomendações", 2026-10-22) | implementado |
| 30 | 1 | DEV (implementação): o estorno (`POST .../refund`) pede `{reason}`, porque o billing exige motivo no `refund` e no `reverse`. O pedido continua `CONFIRMED`, com `refundedAt`, `refundedBy` e `refundReason`, e a resposta ganha `refunded`. Estornar o que não está confirmado, ou já estornado: `TABLE_PAYMENT_REQUEST_NOT_REFUNDABLE` (409). Porta nova no billing: `FolioFacade.refundPayment` | implementado |
| 31 | 1 | DEV (implementação): pré-conta, `TabResponse` e a comanda pública ganham `paidAmount` (Σ itens pagos pelo celular) e `amountDue` (total − `paidAmount`); o `paid`/`balance` do folio continuam como estão. O item ganha `paid` e `reserved`. O celular vê só os itens ativos | implementado |
| 32 | 1 | DEV (implementação): BR Code estático com 00, 26 (00 `br.gov.bcb.pix`, 01 chave), 52 `0000`, 53 `986`, 54 valor com ponto e 2 casas, 58 `BR`, 59 nome, 60 cidade, 62 (05 `txid`) e 63 CRC16 CCITT-FALSE em 4 hex maiúsculos; sem o campo 01. `txid` = os 25 últimos dígitos hexadecimais do id do pedido. Nome e cidade sem acento, só ASCII, cortados em 25 e 15. O `pixPayload` é gerado na criação e guardado: mudar o recebedor depois não muda o Pix que o cliente já tem | implementado |
| 33 | 1 | DEV (implementação): `PixReceiver` validado ao gravar: chave de 1 a 77 caracteres, nome e cidade obrigatórios; senão `INVALID_PIX_RECEIVER` (422). Gravado como digitado (aparado); a normalização da #23 acontece na geração do payload. `GET` sem cadastro responde `200` com os três campos nulos | implementado |
| 34 | 1 | DEV (implementação): expiração igual à do pedido do QR (#29 e #37 da `qr-table-ordering`): leitura preguiçosa, confirmar ou recusar um vencido responde `TABLE_PAYMENT_REQUEST_EXPIRED` sem gravar, tarefa a cada minuto grava `EXPIRED`, libera os itens e avisa; liga e desliga em `castel.restaurant.table-payment-request-expiry-enabled` (o perfil `test` desliga) | implementado |
| 35 | 1 | DEV (implementação): evento `TablePaymentRequestStatusChanged`; o tópico leva `type` `SUBMITTED · CONFIRMED · REJECTED · EXPIRED · REFUNDED`. Nome aprovado pela #41 | implementado (nome aprovado pela #41) |
| 36 | 1 | DEV (implementação): comanda fora de `OPEN`/`CLOSING` recusa criar, confirmar e estornar com `TAB_NOT_OPEN`; a comanda pública sem comanda ativa responde `404 TAB_NOT_FOUND`. Criar com item cancelado, de outra comanda ou sem itens: `INVALID_TABLE_PAYMENT_REQUEST` | implementado |
| 37 | 1 | DEV (implementação): a V14 também afrouxa `ck_tab_closing`: `CLOSING`/`CLOSED` deixam de exigir `tab_charge_id`, porque o fechamento sem nada a pagar não lança (#19) | implementado |
| 38 | 2 | Design aprovado da tela do cliente (`design/Sistema medieval de login (1).zip`, direção Portal): a taxa de serviço é **opcional** no pagamento pelo celular — o cliente marca "Incluir 10% de serviço". O pedido de pagamento passa a levar `includeServiceCharge`; sem ela a parte é só a soma dos itens e a fatia de taxa congelada desses itens é zero. Reverte a #10 só para o celular (Ruan, 2026-10-22) | implementado |
| 39 | 2 | Design: o cliente paga **parte da quantidade** de um item (1 de 3 caipirinhas lançadas juntas). O item da comanda guarda quantas unidades estão pagas e quantas reservadas; a cozinha continua vendo um card só; `isPaid()` = todas as unidades pagas. Reserva, taxa congelada, travas de cancelar/transferir e estorno passam a ser por unidade; unidade não paga continua livre. Item vendido por peso é pago inteiro (Ruan, 2026-10-22) | implementado |
| 40 | 2 | Design: o cliente informa o nome na entrada; o celular guarda o nome e um código aleatório do aparelho, e os dois vão em cada pedido e pagamento. Na confirmação o item da comanda fica com o nome de quem pediu; "Só os meus" filtra pelo código do aparelho, não pelo nome; o garçom vê o nome. Não é login: trocar de celular perde o filtro, não o nome (Ruan, 2026-10-22) | implementado |
| 41 | 2 | Glossário: `customerName`, `customerDeviceId` (no pedido, no pagamento e no item da comanda), `TabItem.paidQuantity`, `TabItem.reservedQuantity`, `TablePaymentRequestItem.quantity`, evento `TablePaymentRequestStatusChanged` (Ruan, 2026-10-22). Entrou no `CLAUDE.md`; resolve também a #35 | implementado |
| 42 | 2 | DEV (implementação da #39): o `tab_item` guarda `paid_quantity`, `reserved_quantity` e `paid_service_charge` (Σ das fatias congeladas das partes confirmadas), e perde o vínculo único com um pedido: quem sabe quantas unidades e qual fatia é o `TablePaymentRequestItem` (`quantity`, valor, fatia), e recusar, expirar e estornar subtraem o que ele diz. Valor de uma unidade = `lineTotal` ÷ `quantity`, exato porque o `lineTotal` é a unidade vezes a quantidade. `isPaid()` = todas as unidades pagas; unidades livres = quantidade − pagas − reservadas | implementado |
| 43 | 2 | DEV (consequência da #39 com a #9, #21 e #29): cancelar, transferir, juntar e trocar de mesa operam sobre a linha inteira — não existe cancelar ou mover parte da quantidade —, então recusam a linha com **qualquer** unidade paga (`TAB_ITEM_ALREADY_PAID`) ou reservada (`TAB_ITEM_RESERVED_FOR_PAYMENT`). As unidades livres de uma linha parcialmente paga continuam podendo ser pagas, não canceladas; para cancelar, o garçom estorna antes | implementado |
| 44 | 2 | DEV: corpo do pagamento `{items: [{tabItemId, quantity}], includeServiceCharge, customerName, customerDeviceId}`. `quantity` de 1 à quantidade do item, e no máximo as unidades livres: acima disso `TAB_ITEM_ALREADY_PAID` quando o resto está todo pago, senão `TAB_ITEM_RESERVED_FOR_PAYMENT`; fora de 1 à quantidade, ou o mesmo item duas vezes, `INVALID_TABLE_PAYMENT_REQUEST`. Item por peso tem quantidade 1, então é pago inteiro (#39). `includeServiceCharge` é obrigatório (ausente = 400): sem padrão escondido | implementado |
| 45 | 2 | DEV (#38 + #39): a taxa do pedido é a base das unidades escolhidas que contam para a taxa × percentual, arredondada uma vez e repartida pelos itens do pedido pelo maior resto; com `includeServiceCharge` falso é zero e as fatias são zero. A comanda: `serviceCharge` = Σ fatias pagas + base das unidades não pagas × taxa (uma vez); o `startClosing` lança só as unidades não pagas | implementado |
| 46 | 2 | DEV (#40): `customerName` aparado de 1 a 40 caracteres (`INVALID_CUSTOMER_NAME`, 422) e `customerDeviceId` de 8 a 64 caracteres `[A-Za-z0-9_-]` (`INVALID_CUSTOMER_DEVICE_ID`, 422), obrigatórios no pedido e no pagamento do celular. Vão no corpo do `POST`; a leitura `GET /public/tables/{orderingToken}/tab` recebe o aparelho no header `X-Customer-Device` (opcional) — nunca na URL, que vai para log. A resposta pública traz o `customerName` de cada item e `mine` (verdadeiro quando o item é do aparelho do header), nunca o código de outro aparelho. O item da comanda guarda os dois, nulos no item lançado pelo garçom; as respostas internas mostram só o nome | implementado |
| 47 | 2 | DEV: `customer_name` e `customer_device_id` do `table_order_request` entram na V14 como colunas anuláveis (a V13 está no PR #38 e não é editada; as linhas antigas ficam sem); a regra de obrigatório mora no agregado. A V14 foi reescrita no lugar: nunca foi commitada nem aplicada fora de bancos descartáveis (conferido no `flyway_schema_history` do banco de dev, que está na V12) | implementado |
| 48 | 3 | DEV (review): na pré-conta, a taxa de cada grupo da divisão = Σ fatias congeladas das unidades pagas do grupo + a parte do grupo na taxa das unidades não pagas, repartida pelo maior resto sobre a base não paga de cada grupo. Os grupos sempre somam a taxa e o total da conta (F4), também com fatia congelada e taxa dispensada depois; sem nada pago é a divisão da 3.2 | implementado |
| 49 | 3 | DEV (review): estornar pelo restaurante um pagamento do celular cujo Pix já foi estornado no balcão não falha mais: a comanda pula o `refund` do billing, estorna o lançamento da parte e libera as unidades. Porta nova `TabBilling.isPaymentRefunded`, sobre `FolioFacade.isPaymentRefunded` | implementado |
| 50 | 3 | DEV (review): a tarefa de expiração dos pagamentos lê os ids vencidos sem trava e expira cada um na sua própria transação, travando o pedido e depois a comanda — a mesma ordem dos outros caminhos —, então nunca segura duas comandas ao mesmo tempo nem entra em deadlock com transferência ou KDS. Pedido respondido nesse meio é deixado como está | implementado |
| 51 | 3 | DEV (review): o envio relê a comanda depois da trava; comanda fechada ou juntada entre a leitura do id e a trava responde o mesmo `404 TAB_NOT_FOUND` sem id no `detail`, nunca `TAB_NOT_OPEN` com o UUID | implementado |
| 52 | 3 | DEV (review, sugestão): o envio e o fechamento da comanda expiram na hora os pagamentos vencidos dessa comanda, liberando as unidades, travando os pedidos antes da comanda; só um Pix ainda dentro da janela segura o fechamento (#28) | implementado |
| 53 | 3 | DEV (review): a chave do Pix é guardada na forma do DICT: CPF (dígitos, verificadores conferidos pelo `Cpf`) e CNPJ (14 dígitos, só o tamanho) sem pontuação, telefone como `+55` e 10 ou 11 dígitos (o `+` é o que distingue de um CPF), e-mail e chave aleatória (UUID) em minúsculas; outra forma, ou caractere fora do ASCII, `INVALID_PIX_RECEIVER`. Nome e cidade que ficariam vazios no padrão são recusados ao gravar, não no celular. Revê a #33 | implementado |
| 54 | 3 | DEV (review, sugestão): a pré-conta e a comanda pública mostram `unpaidServiceChargeBase`; vezes a taxa, mais as fatias congeladas, dá a taxa mostrada | implementado |
| 55 | 4 | Rota de **simular valor**: o celular manda as unidades escolhidas e `includeServiceCharge` e recebe o valor da parte (itens, taxa, total) calculado pelo backend, **sem reservar nada nem gerar Pix**. Serve para a Conta mostrar o valor enquanto o cliente marca, sem o navegador calcular dinheiro (Ruan, 2026-10-22) | implementado |
| 56 | 4 | DEV (#55): rota `POST /public/tables/{orderingToken}/payment-requests/quote`, corpo `{includeServiceCharge, items: [{tabItemId, quantity}]}` → `200 {subtotal, serviceCharge, amount, items: [{tabItemId, quantity, lineTotal, serviceCharge}]}`. `customerName` e `customerDeviceId` não são pedidos nem validados: a simulação não grava nada que precise de autor; se vierem, são ignorados | implementado |
| 57 | 4 | DEV (#55): a simulação usa a mesma conta do pedido — `Tab.priceForPayment` valida as escolhas e calcula valores e fatias; `reserveForPayment` é ela mais a reserva das unidades —, então o arredondamento é um só. Mesmas recusas e na mesma ordem do pedido, inclusive `PIX_RECEIVER_NOT_CONFIGURED` primeiro, para a tela saber cedo que não há Pix; não reserva, não grava, não gera Pix | implementado |
| 58 | 4 | DEV (#55): transação só de leitura, sem trava da comanda. O valor simulado pode diferir do pedido real se alguém pagar, reservar ou lançar entre os dois; o pedido real é a autoridade. A simulação também não expira pedido vencido (não grava): unidades de um pedido vencido que a tarefa ainda não gravou aparecem reservadas por até um minuto, embora o envio real as libere antes. Comanda fechada ou juntada entre a leitura do id e a da comanda responde `404 TAB_NOT_FOUND`, como no envio (#51) | implementado |
| 59 | 5 | O garçom estorna o Pix confirmado pelo salão (confirma a #9). Pede uma leitura dos pagamentos do celular já confirmados da comanda, para o salão ter de onde estornar (Ruan, 2026-10-22) | implementado |
| 60 | 5 | DEV (#59): `GET /api/restaurant/table-payment-requests?status=CONFIRMED&tabId={tabId}` (`ADMIN`, `WAITER`), ao lado da fila (o `GET` sem parâmetro continua sendo os pendentes). Responde a mesma lista de `TablePaymentRequestResponse`, estornados incluídos (`refunded`, `refundedAt`, `refundReason`), mais antigo primeiro pela hora da confirmação. `status` só aceita `CONFIRMED` e exige `tabId`; outro valor ou `tabId` ausente: `400 VALIDATION_FAILED` | implementado |
| 61 | 5 | DEV (#59): comanda desconhecida responde lista vazia, não 404: é um filtro de leitura, como os outros filtros de lista do restaurante, e não precisa ler a comanda | implementado |

Valores de status: `pendente` · `implementado` · `revertida pela #n`

---

## Escopo desta task

Rodada 1 entregou o backend; nenhum item saiu da lista. "Front pendente" marca o que falta na tela.

- [ ] Celular lê a comanda da mesa (itens, valores, pago, falta pagar) (#1) — backend feito (`GET /public/tables/{orderingToken}/tab`); front pendente
- [ ] Cliente escolhe itens e recebe o Pix copia e cola com o valor (#4, #7, #20) — backend feito (`pixPayload`); front pendente
- [x] Taxa de serviço opcional no pagamento pelo celular (#38)
- [x] Pagar parte da quantidade de um item, unidade a unidade (#39, #42–#45)
- [x] Simular o valor da parte sem reservar nem gerar Pix (#55–#58)
- [x] Leitura dos pagamentos confirmados da comanda, para o salão estornar (#59–#61)
- [ ] Nome do cliente e código do aparelho no pedido, no pagamento e no item; "Só os meus" pelo aparelho (#40, #46) — backend feito; front pendente
- [x] Pagamento aguardando reserva os itens; garçom confirma ou recusa; expira em 30 min (#8, #11, #13)
- [x] Pagamento com a comanda `OPEN` e `CLOSING` (#6, #17, #25); fechamento abate o pago (#14, #19)
- [x] Item pago marcado e travado; estorno libera (#9, #21)
- [ ] Cadastro do Pix pelo admin (#15, #23) — backend feito (`GET`/`PUT /api/admin/pix-receiver`); front pendente
- [ ] Aviso em tempo real para o garçom — backend feito (tópico); front pendente
- [x] `.http` com caminho feliz e cenários negativos (`http/38-restaurant-table-payment.http`)
- [ ] `./mvnw clean install` passando — passa com o `FakeTabBilling` ajustado; o ajuste é do agente de testes
- [ ] Testes de unidade e de integração — a cargo do agente de testes

---

## Contrato com o front

**Público, sem login**

| Rota | Resposta |
|---|---|
| `GET /public/tables/{orderingToken}/tab` — header opcional `X-Customer-Device` (#46) | `{diningTableLabel, status, items: [{id, itemName, variantName, modifiers: [{name, quantity}], quantity, weightGrams, lineTotal, status, paid, paidQuantity, reservedQuantity, customerName, mine}], subtotal, serviceChargeRate, serviceCharge, total, paidAmount, amountDue, acceptsPayment}` — só itens ativos (#31); `paid` = todas as unidades pagas; `mine` verdadeiro só para item do aparelho do header; nenhum código de aparelho volta; `acceptsPayment` falso sem Pix cadastrado; `404 TAB_NOT_FOUND` sem comanda ativa |
| `POST /public/tables/{orderingToken}/payment-requests` | corpo `{customerName, customerDeviceId, includeServiceCharge, items: [{tabItemId, quantity}]}` (#38, #39, #44, #46) → `201` `{id, status, refunded, subtotal, serviceCharge, amount, includeServiceCharge, pixPayload, items: [{tabItemId, quantity, lineTotal, serviceCharge}], requestedAt, expiresAt}` |
| `POST /public/tables/{orderingToken}/order-requests` (da `qr-table-ordering`) | corpo ganha `customerName` e `customerDeviceId`, obrigatórios (#46); a fila interna do pedido mostra `customerName`, e o item da comanda criado na confirmação guarda os dois |
| `POST /public/tables/{orderingToken}/payment-requests/quote` | simular valor (#55–#58): corpo `{includeServiceCharge, items: [{tabItemId, quantity}]}` → `200` `{subtotal, serviceCharge, amount, items: [{tabItemId, quantity, lineTotal, serviceCharge}]}`; não reserva, não grava, não gera Pix; mesmas recusas do pedido, `PIX_RECEIVER_NOT_CONFIGURED` primeiro |
| `GET /public/tables/{orderingToken}/payment-requests/{id}` | o mesmo formato, com o estado atual |

**Interno**

| Rota | Perfis |
|---|---|
| `GET /api/restaurant/table-payment-requests` | `ADMIN`, `WAITER` — pendentes, com mesa, itens e valor |
| `GET /api/restaurant/table-payment-requests?status=CONFIRMED&tabId={tabId}` | `ADMIN`, `WAITER` — os confirmados da comanda, estornados incluídos, pela hora da confirmação (#59–#61); mesmo `TablePaymentRequestResponse`; `status` diferente de `CONFIRMED` ou sem `tabId`: `400 VALIDATION_FAILED`; comanda desconhecida: `200 []` |
| `POST /api/restaurant/table-payment-requests/{id}/confirm` | `ADMIN`, `WAITER` |
| `POST /api/restaurant/table-payment-requests/{id}/reject` | `ADMIN`, `WAITER` |
| `POST /api/restaurant/table-payment-requests/{id}/refund` | `ADMIN`, `WAITER` — corpo `{reason}` (#30); estorna um confirmado e libera os itens (#21) |
| `GET`/`PUT /api/admin/pix-receiver` | `ADMIN` |

STOMP `/topic/restaurant/table-payment-requests` (`WAITER`, `ADMIN`): `{type: SUBMITTED·CONFIRMED·REJECTED·EXPIRED·REFUNDED, requestId, diningTableId, occurredAt}`.

A pré-conta (`GET /api/restaurant/tabs/{tabId}/bill`) e o `TabResponse` ganham `paidAmount` e `amountDue` (#31); o item ganha `paid`, `reserved`, `paidQuantity`, `reservedQuantity` e `customerName` (o código do aparelho não sai em resposta interna). A fila interna responde `{id, status, refunded, diningTableId, diningTableLabel, tabId, subtotal, serviceChargeRate, serviceCharge, amount, requestedAt, expiresAt, resolvedAt, resolvedBy, refundedAt, refundReason, customerName, includeServiceCharge, items: [{tabItemId, itemName, variantName, quantity, lineQuantity, lineTotal, serviceCharge}]}`. `GET`/`PUT /api/admin/pix-receiver`: `{key, receiverName, city}`.

**Códigos de erro** (nomes finais na implementação)

| Código | HTTP | Quando |
|---|---|---|
| `TAB_ITEM_ALREADY_PAID` | 409 | unidades pedidas além das livres, com o resto todo pago; também ao cancelar, transferir, juntar e trocar de mesa uma linha com qualquer unidade paga (#43, #44) |
| `TAB_ITEM_RESERVED_FOR_PAYMENT` | 409 | unidades pedidas além das livres, com alguma em outro pagamento aguardando; também ao cancelar, transferir, juntar, trocar de mesa uma linha com unidade reservada, e ao fechar a comanda (#28, #29, #43, #44) |
| `PIX_RECEIVER_NOT_CONFIGURED` | 409 | sem Pix cadastrado |
| `TABLE_PAYMENT_REQUEST_NOT_PENDING` · `_EXPIRED` · `_NOT_FOUND` | 409 · 409 · 404 | como no pedido |
| `INVALID_TABLE_PAYMENT_REQUEST` | 422 | sem itens, item cancelado, item de outra comanda, o mesmo item duas vezes, quantidade fora de 1 à quantidade da linha (#44) |
| `INVALID_CUSTOMER_NAME` · `INVALID_CUSTOMER_DEVICE_ID` | 422 · 422 | nome ausente, em branco ou acima de 40; código do aparelho fora de 8–64 `[A-Za-z0-9_-]` — no pedido e no pagamento (#46) |
| `VALIDATION_FAILED` | 400 | `includeServiceCharge` ou `tabItemId` ausente (#44) |
| `TABLE_PAYMENT_REQUEST_NOT_REFUNDABLE` | 409 | estornar o que não está confirmado, ou já estornado (#30) |
| `INVALID_PIX_RECEIVER` | 422 | Pix sem chave, nome ou cidade, ou chave acima de 77 (#33) |
| `TAB_NOT_FOUND` | 404 | mesa sem comanda ativa, na rota pública (#36) |
| `TAB_NOT_OPEN` | 409 | criar, confirmar ou estornar com a comanda fora de `OPEN`/`CLOSING` (#36) |
| do billing | os mesmos | `INVALID_PAYMENT_REFUND_REASON`, `PAYMENT_EXCEEDS_BALANCE` etc., na confirmação e no estorno |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| A taxa de cada parte é arredondada sobre a base dela, e a do fechamento sobre a base do que falta: a soma pode diferir em 1 centavo da taxa da comanda inteira arredondada de uma vez | Consequência direta da #10 e da #20 (taxa congelada por pagamento); a comanda soma partes + restante, então total, pago e falta pagar sempre fecham entre si e com o folio | Nenhuma |
| Confirmar um Pix quando o folio já tem crédito (pagamento manual além do que falta) responde `PAYMENT_EXCEEDS_BALANCE`, e o pedido fica `PENDING` | O dinheiro de fato sobrou na mesa; registrar o Pix deixaria o folio negativo sem ninguém decidir | O garçom recusa e acerta à mão |
| Trocar de mesa ou juntar comandas com item pago ou reservado é recusado (#29) | O dinheiro da parte mora no folio da comanda de origem, que a junção fecha | Aprovado pelo Ruan com a #29: trocar de mesa depois de alguém pagar a sua parte pede o estorno antes |
| Sem rota que apague o Pix cadastrado; `PIX_RECEIVER_NOT_CONFIGURED` só aparece em banco novo | O escopo (#15) pede cadastrar, não descadastrar | Rota `DELETE` se o admin precisar |
| A porta `TabBilling` ganhou `refundPayment`: o `FakeTabBilling` dos testes não compila até ganhar o método (código de teste, fora do papel do implementador) | Porta limpa, sem método `default` de mentira | O agente de testes acrescenta o método |

---

## Pontos em aberto

| # | Pergunta | Desde a rodada |
|---|---|---|

