# Decisões — Task 4.1–4.3 FE: Salão (Dining Room) com Portal Design

> Registro acumulado das decisões tomadas durante a refatoração da tela de mesas
> com design system Portal (paleta Medieval).
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/4.1-4.3-front-shell` |
| Rodada atual | 2 |
| Build | ✓ `npm run build` passa (tsc + vite) |
| Testes | 0 |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 0 | Usar CSS customizado em arquivo separado (`dining-room-portal.css`) em vez de inline styles | implementado |
| 2 | 0 | Mapear estados do backend (`occupiedTabCount`) para estados visuais do design (livre, ocupada, pronto, conta, reservada) | **revertida na rodada 2**: o campo `occupiedTabCount` nunca existiu na API |
| 3 | 0 | Implementar filtros por área (Salão/Varanda) e status em chips horizontais scrolláveis | implementado; áreas agora vêm do cadastro, não de lista fixa (#9) |
| 4 | 0 | Sheet modal inferior com border-radius arredondado (50% / 44px no topo) conforme design | implementado |
| 5 | 0 | Manter responsividade mobile como prioridade; grid de mesas 3 colunas | implementado |
| 6 | 0 | Usar hooks existentes (`useDiningTables()`, `useOpenTab()`, `useDiningTableTab()`) sem criar novos | **revertida na rodada 2**: os hooks liam campos inexistentes (`occupiedTabCount`, `{tabs:[]}`, `item.price`) |
| 7 | 2 | Ocupação da mesa vem do cruzamento `GET /dining-tables` × `GET /tabs` (comandas ativas), sem mudar a API. Mesa com comanda `OPEN` = ocupada; `CLOSING` = conta pedida; sem comanda = livre | implementado (`useActiveTabs`, `tableStatus.ts`) |
| 8 | 2 | Estados "pronto" e "reservada" do design **não são derivados**: a API não os expõe (nem na mesa nem no resumo da comanda). Chips reduzidos a Todas · Conta pedida · Ocupada · Livre | implementado; lacuna de API registrada abaixo |
| 9 | 2 | Áreas do salão vêm do campo `area` das mesas cadastradas; mesa sem área cai em "Sem área"; com uma área só, o seletor some | implementado |
| 10 | 2 | Toggle "Só as minhas" **removido** até o resumo da comanda trazer `openedBy` (hoje só o detalhe traz) | implementado; lacuna de API registrada abaixo |
| 11 | 2 | Abrir mesa = `POST /tabs` seguido de `PUT /tabs/{id}/guest-count`; falha do segundo não desfaz o primeiro, só avisa | implementado |
| 12 | 2 | Cardápio do garçom vem de `GET /public/menu` (sem token); `GET /api/restaurant/menu-items` é só ADMIN. Proxy do Vite ganhou `/public` | implementado |
| 13 | 2 | Item vendido por peso manda só `weightGrams`; o formulário esconde tamanho, adicional e quantidade para ele | implementado |
| 14 | 2 | Juntar comandas chama `POST /tabs/{destino}/merge {mergedTabId: esta}`: a comanda da mesa aberta no sheet é a absorvida, como o texto do diálogo já dizia | implementado |
| 15 | 2 | Toda string em português do salão mora em `diningRoomMessages.ts`, com mapa `code → frase` e fallback genérico que loga o código ausente | implementado |
| 16 | 2 | Dinheiro formatado só na borda, por operação de string (`lib/money.ts`); nenhum `Number` em valor | implementado |
| 17 | 2 | Código morto com contrato errado removido: `TabDetailsPanel`, `DiningTableCard`, `TabBillModal`, `useDiningTableTab`, `AddItemModal` fake com cardápio inventado, e os CSS órfãos | implementado |

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

- [x] Criar arquivo CSS com paleta Medieval do design Portal
- [x] Refazer componente `DiningRoomPage.tsx` com layout do design
- [x] Implementar header com marca, título "Mesas", relógio e avatar (inicial do usuário logado)
- [x] Implementar filtros por área
- [x] Implementar chips de filtro por status
- [x] Implementar grid de mesas (3 colunas) com estados visuais
- [x] Implementar sheet modal ao clicar na mesa com detalhe
- [x] Integrar com dados reais: mesas + comandas ativas + detalhe da comanda
- [x] Sheet mostra consumo real: itens com quantidade/peso, variação, adicionais, observação, status na cozinha, total da linha; subtotal, taxa de serviço e total do backend
- [x] Abrir mesa com número de pessoas
- [x] Lançar pedido com cardápio real (categorias, tamanhos, adicionais, peso, observação)
- [x] Pedir a conta e cancelar pedido de conta
- [x] Transferir itens, juntar comandas, trocar de mesa, cancelar comanda — com os corpos de requisição que o backend espera
- [x] Estado vazio, de erro e carregando em toda leitura (mesas, comanda, cardápio)
- [x] Build passa (TypeScript + Vite)
- [ ] Testar visualmente no navegador (FE1 design) — **pendente de Ruan**: sem ferramenta de browser nesta sessão
- [ ] Testes de integração com backend

### Movido para outra task

Só com aprovação explícita, e dizendo para qual task e por quê.

| Item | Vai para | Motivo | Quem aprovou |
|---|---|---|---|
| Estados "reservada" e "prato pronto" no mapa | a definir | Lacuna de API (ver abaixo), não de tela | — |
| Toggle "Só as minhas" | a definir | Lacuna de API: resumo da comanda sem `openedBy` | — |
| Página desktop (layout com sidebar "Contas pedidas") | FE3 | Mobile-first nesta onda; desktop vem depois | — |
| Cancelar item individual pela tela | a definir | Hook existe (`useCancelTabItem`); falta o gesto no sheet | — |

---

## Contrato com o backend

Três leituras compõem o mapa; nenhuma mudança de API foi feita.

| Chamada | Para quê | Hook |
|---|---|---|
| `GET /api/restaurant/dining-tables` | todas as mesas ativas (id, label, seats, area, isActive) | `useDiningTables` |
| `GET /api/restaurant/tabs` | comandas `OPEN`/`CLOSING` com `diningTableId`, `activeItemCount`, `subtotal`, `total`, `openedAt` → quem está ocupada | `useActiveTabs` |
| `GET /api/restaurant/tabs/{tabId}` | consumo completo ao tocar na mesa | `useTab` |
| `GET /public/menu` | cardápio para lançar pedido | `useMenuItems` |

Escritas: `POST /tabs`, `PUT /tabs/{id}/guest-count`, `POST /tabs/{id}/items`,
`POST /tabs/{id}/closing`, `POST /tabs/{id}/reopen`, `POST /tabs/{id}/cancel`,
`POST /tabs/{id}/transfer {toTabId, itemIds}`, `POST /tabs/{destino}/merge {mergedTabId}`,
`POST /tabs/{id}/move {diningTableId}`.

Todos os tipos TypeScript seguem os `*Response` do backend campo a campo
(`lineTotal`, `unitPrice`, `modifiers`, `openedAt`, nunca `price`/`createdAt`).

### Lacunas de API encontradas (não resolvidas aqui)

| Lacuna | Efeito na tela | Caminho sugerido |
|---|---|---|
| Resumo da comanda (`TabSummaryResponse`) não traz `openedBy` | "Só as minhas" não dá para fazer | adicionar `openedBy` ao resumo |
| Nada diz se a mesa tem prato `READY` sem abrir cada comanda | estado "prato pronto" ausente no mapa (aparece só dentro do sheet) | contador `readyItemCount` no resumo, ou assinar `/topic/restaurant/ready-items` |
| Não existe reserva de mesa no backend | estado "reservada" ausente | depende do módulo hotel/reserva |
| Resumo sem `guestCount` | card da mesa mostra "N itens · M min" em vez de "N pess." | adicionar `guestCount` ao resumo |

---

## Cores e estilos (Medieval/Portal)

**Paleta:**
```css
--color-bg: #3D0F1A;           /* Vinho Lacre */
--color-surface: #4A1522;       /* Vinho */
--color-text: #F4ECE3;          /* Pergaminho */
--color-accent: #E8957F;        /* Salmão */
--color-accent-7: #F6D9CE;      /* Salmão Velado */
```

**Tipografia:**
- Títulos: `Cormorant Garamond` 600, caixa-alta, espaçada
- Corpo: `Lora`

**Estados da mesa (os três que a API sustenta):**
| Status | Border | Background | Foreground |
|---|---|---|---|
| Livre | `1px dashed rgba(232,149,127,.55)` | transparent | `rgba(244,236,227,.82)` |
| Ocupada | `1.5px solid #E8957F` | `#4A1522` | `#F4ECE3` |
| Conta pedida | `1.5px solid #F6D9CE` | `#F4ECE3` | `#4A1220` |

---

## Arquivos criados/modificados (rodada 2)

| Arquivo | Alteração |
|---|---|
| `frontend/src/features/dining-room/DiningRoomPage.tsx` | **REFEITO** — mapa com dados reais |
| `frontend/src/features/dining-room/TableSheet.tsx` | **NOVO** — sheet da mesa: fatos, itens, totais, ações |
| `frontend/src/features/dining-room/tableStatus.ts` | **NOVO** — derivação de status e minutos |
| `frontend/src/features/dining-room/diningRoomMessages.ts` | **NOVO** — strings e mapa de códigos de erro |
| `frontend/src/features/dining-room/useActiveTabs.ts` | **NOVO** — `GET /tabs` |
| `frontend/src/features/dining-room/useDiningTables.ts` | tipo corrigido (sem `occupiedTabCount`) |
| `frontend/src/features/dining-room/useTab.ts` | tipos iguais ao `TabResponse` |
| `frontend/src/features/dining-room/useTabActions.ts` | + `useStartClosing`, `useReopenTab`, `useRecordGuestCount`; `TabBill` real |
| `frontend/src/features/dining-room/useTabOperations.ts` | corpos corrigidos (`toTabId`, `mergedTabId`, `diningTableId`) |
| `frontend/src/features/dining-room/useMenuItems.ts` | lê `GET /public/menu` |
| `frontend/src/features/dining-room/useAddTabItem.ts` | `modifiers` (não `modifierChoices`), `weightGrams` |
| `frontend/src/features/dining-room/AddItemModal.tsx` | **REFEITO** — cardápio real no sheet |
| `frontend/src/features/dining-room/AddItemForm.tsx` | **REFEITO** — peso, tamanho, adicionais, erro traduzido |
| `frontend/src/features/dining-room/{Transfer,Merge,Move}*Dialog.tsx` | destinos pelas comandas ativas; `onDone` |
| `frontend/src/lib/money.ts` | **NOVO** — formatação sem `Number` |
| `frontend/src/styles/dining-room-portal.css` | estados, sheet, stepper, cardápio, formulário |
| `frontend/vite.config.ts` | proxy `/public` |
| `frontend/src/router.tsx` | corrige tipo (`'ADMIN'` não é `Destination`; ADMIN já alcança tudo via `destinationsFor`) — bloqueava o `tsc` |
| removidos | `TabDetailsPanel.tsx`, `DiningTableCard.tsx`, `TabBillModal.tsx`, `useDiningTableTab.ts`, `tab-bill-modal.css`, `add-item-modal.css`, `dining-room.css` |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| "Aberta há N min" usa o relógio do aparelho | a regra de `serverTime` é da tela da cozinha, que fica dias ligada; o celular do garçom sincroniza | expor `serverTime` no `GET /tabs` se derrapar |
| Cabeçalho do shell ("Castel", azul) aparece acima do mapa vinho | fora do escopo do salão; é do shell | FE3 decide se o salão troca o shell pelo header do design |
| Cancelar item individual sem gesto na tela | hook pronto, falta UX de toque no item | próxima rodada |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

| # | Pergunta | Desde a rodada |
|---|---|---|
| 1 | ~~Comportamento se `occupiedTabCount` não bater com Tab ativa~~ — sem efeito: o campo não existe; ocupação vem das comandas | 0 → fechada na 2 |
| 2 | Os dados do backend já são filtrados por permissão (garçom não ver mesa de outro)? Hoje `GET /tabs` devolve todas as comandas ativas da propriedade | 0 |
| 3 | ~~Manter o input de "Pessoas" na modal?~~ — mantido como stepper na abertura (decisão #11) | 0 → fechada na 2 |
| 4 | Fechar as lacunas de API (`openedBy`, `guestCount`, `readyItemCount` no resumo) nesta onda ou em task própria? | 2 |

---

## Checklist de finalização

- [x] Build `npm run build` passa
- [x] Imports e tipos corretos (TypeScript)
- [x] CSS customizado em arquivo separado
- [x] Strings em arquivo de tradução; códigos de erro mapeados
- [x] Estados vazio/erro/carregando nas três leituras
- [ ] Abrir no navegador e verificar renderização — **Ruan**
- [ ] Testes de integração com backend
