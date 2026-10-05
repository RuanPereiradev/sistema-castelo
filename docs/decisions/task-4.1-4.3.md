# Decisões — Task 4.1-4.3 Shell e salão com comanda

> Registro acumulado das decisões tomadas durante a execução desta task.
> Atualizado **a cada rodada**, nunca reconstruído de memória no fim.
>
> Regra: toda decisão que o Ruan confirma entra aqui **na mesma resposta** em
> que foi confirmada. Se não está aqui, não foi decidido.

---

## Estado

| | |
|---|---|
| Branch | `task/4.1-4.3-front-shell` |
| Rodada atual | 1 |
| Build | passa (`npm run build` + `tsc --noEmit`) |
| Testes | 0 — verificação por percurso real contra a API no perfil `dev` |

---

## Decisões confirmadas

Ordem cronológica. Nunca apague uma linha — se uma decisão for revertida,
marque como revertida e adicione a nova embaixo.

| # | Rodada | Decisão | Status |
|---|---|---|---|
| 1 | 1 | Bibliotecas: React Router para roteamento, TanStack Query para cache e revalidação de dados | implementando |
| 2 | 1 | Estilo: herdar Portal (moldura moderna) do login FE1; shell nasce com Portal | implementando |
| 3 | 1 | Atualização entre garçons: polling simples (refetch a cada 3-5s). WebSocket fica para depois | implementando |
| 4 | 1 | PWA não entra na 4.1; será task de manutenção depois, quando UI estabilizar | implementando |

---

## Escopo desta task

Lista viva. Item aprovado pelo Ruan **entra aqui** e só sai por decisão
explícita do Ruan.

### 4.1 — Shell (roteador + client HTTP + moldura)

- [ ] Roteador React Router: `/` login, `/salao` salão/comanda (protegidos por sessão + papel)
- [ ] Cliente HTTP aprimorado: PUT/PATCH/DELETE, Bearer token em cada requisição, retry em 401 com refresh
- [ ] Cabeçalho da área logada: nome do usuário, role (icon), botão **Sair** (chama logout)
- [ ] Tradução de `code` → mensagem em português (`authMessages.ts` como base)
- [ ] Fallback visual pra 429, token expirado, usuário inativo

### 4.3 — Salão e comanda (mobile-first, entregável em fases)

#### Fase 1: Mapa de mesas
- [ ] GET `/api/restaurant/dining-tables` + polling (5s)
- [ ] Renderizar mesas: nome, estado (livre/ocupada), número de lugares
- [ ] Clique numa mesa livre abre comanda (POST `/api/restaurant/tabs`)
- [ ] Comanda aberta leva ao detalhe (Fase 2)

#### Fase 2: Detalhe da comanda
- [ ] GET `/api/restaurant/tabs/{id}` + polling (5s)
- [ ] Header: identificador da mesa, número de pessoas, tempo aberto
- [ ] Itens: nome, descrição, variação, adicionais, observação, preço, status (pendente/em prep/pronto/servido)
- [ ] Total (vem do backend)
- [ ] Botões: "Lançar item", "Cancelar item", "Solicitar conta", "Fechar"

#### Fase 3: Lançar item
- [ ] Cardápio por categoria (GET `/api/restaurant/menu-items`)
- [ ] Seleção de variação (P/M/G) se houver
- [ ] Seleção de adicionais e quantidade
- [ ] Campo observação (`specialInstructions`)
- [ ] POST `/api/restaurant/tabs/{id}/items`

#### Fase 4: Ações na comanda
- [ ] Cancelar item: POST `/tabs/{id}/items/{itemId}/cancel` + motivo
- [ ] Cancelar comanda: POST `/tabs/{id}/cancel` + motivo
- [ ] Solicitar pré-conta: GET `/tabs/{id}/bill`
- [ ] Fechar comanda: POST `/tabs/{id}/close`

#### Fase 5: Operações avançadas (por último)
- [ ] Transferir itens: POST `/tabs/{id}/transfer` + target tab
- [ ] Juntar comandas: POST `/tabs/{id}/merge` + target tab
- [ ] Trocar de mesa: POST `/tabs/{id}/move` + nova mesa

### Fora do escopo desta task
- [ ] PWA (manifest, service worker)
- [ ] WebSocket ou push de eventos
- [ ] Operações de admin (cadastro de itens, categorias, mesas)
- [ ] Tela de recepção, caixa, cozinha (cada uma é task separada)

---

## Contrato com a API

Tudo o que o front consome. Depois do merge isso vira contrato.

**Endpoints**

| Rota | Método | Autenticação | O que o front espera |
|---|---|---|---|
| `/api/restaurant/dining-tables` | GET | WAITER+ | Lista de mesas: `id`, `label`, `seats`, `isActive`, `area`, `occupiedTabCount` |
| `/api/restaurant/tabs` | POST | WAITER+ | Body: `{diningTableId, origin, guestCount}` → Resposta: `{id, diningTableId, status, createdAt}` |
| `/api/restaurant/tabs` | GET | WAITER+ | Lista paginada? No MVP, sem paginação |
| `/api/restaurant/tabs/{id}` | GET | WAITER+ | Detalhe completo: itens, total, saldo, status |
| `/api/restaurant/tabs/{id}/items` | POST | WAITER+ | Body: `{menuItemId, variantId?, modifierChoices, specialInstructions, quantity}` |
| `/api/restaurant/tabs/{id}/items/{itemId}/cancel` | POST | WAITER+ | Body: `{reason}` |
| `/api/restaurant/tabs/{id}/cancel` | POST | WAITER+ | Body: `{reason}` |
| `/api/restaurant/tabs/{id}/bill` | GET | WAITER+ | Pré-conta: itens, total, taxa de serviço, saldo |
| `/api/restaurant/tabs/{id}/close` | POST | WAITER+ | Fecha e transaciona folio |
| `/api/restaurant/tabs/{id}/transfer` | POST | WAITER+ | Transfere itens |
| `/api/restaurant/tabs/{id}/merge` | POST | WAITER+ | Junta comandas |
| `/api/restaurant/tabs/{id}/move` | POST | WAITER+ | Troca de mesa |

**Códigos de erro**

| Código | HTTP | Cenário |
|---|---|---|
| `DINING_TABLE_NOT_FOUND` | 404 | Mesa não existe |
| `DINING_TABLE_HAS_OPEN_TAB` | 409 | Mesa já tem comanda aberta |
| `INVALID_GUEST_COUNT` | 400 | Número de pessoas inválido |
| `TAB_NOT_FOUND` | 404 | Comanda não existe |
| `TAB_ALREADY_CLOSED` | 409 | Comanda já foi fechada |
| `MENU_ITEM_NOT_FOUND` | 404 | Item do cardápio não existe |
| `MENU_ITEM_UNAVAILABLE` | 409 | Item indisponível |
| `INVALID_TAB_ITEM_TRANSITION` | 409 | Estado do item não permite a ação |
| `INVALID_CANCELLATION_REASON` | 400 | Motivo vazio ou inválido |

---

## Limitações conhecidas e aceitas

| Limitação | Por que foi aceita | Mitigação futura |
|---|---|---|
| Polling simples (não WebSocket) | Arquitetura simples para MVP, suficiente pra 2-3 garçons | Adicionar WebSocket quando métricas mostrarem que 5s de delay é problema |
| Sem suporte offline | Conexão é pré-requisito (tablet na rede do restaurante) | Cache com TanStack Query já mitiga reloads parciais |
| Sem paginação de mesas/itens | Restaurante pequeno começa sem isso | Adicionar quando listar >50 itens se tornar slow |

---

## Pontos em aberto

Aguardando decisão do Ruan. Some daqui quando a resposta vier.

(Nenhum ponto aberto; todas as 4 decisões já foram confirmadas na rodada 1)
